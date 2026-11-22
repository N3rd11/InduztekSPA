package cl.induztek.protocol;

import cl.induztek.common.*;
import com.google.gson.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cl.induztek.common.Json.*;

@Service
public class ProtocolService {
    private final Documentos db;
    private final CatalogGateway catalogos;
    private final PersonalGateway personal;
    private final TransactionTemplate transaccion;

    public ProtocolService(Documentos db, CatalogGateway catalogos, PersonalGateway personal, PlatformTransactionManager gestor) {
        this.db = db; this.catalogos = catalogos;
        this.personal = personal;
        this.transaccion = new TransactionTemplate(gestor);
        if (db.todos("protocolos").isEmpty()) Semilla.cargar(db,Semilla.protocolos());
    }
    private List<JsonObject> lista(JsonObject datos, String tabla) {
        var salida = new ArrayList<JsonObject>(); datos.getAsJsonArray(tabla).forEach(v -> salida.add(v.getAsJsonObject())); return salida;
    }
    private JsonObject catalogo(JsonObject c, String tabla, String id) {
        return lista(c,tabla).stream().filter(o -> s(o,"id").equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No existe "+tabla+": "+id));
    }
    private Set<String> identificadores(List<JsonObject> documentos) {
        return documentos.stream().map(o -> s(o, "id")).collect(Collectors.toSet());
    }

    public JsonObject acta(Usuarios.Usuario usuario, String autorizacion, String visitaId) {
        var visita = db.obtener("visitas", visitaId);
        if (l(visita, "cierre") == 0 || s(visita, "acta").isBlank() ||
            !usuario.interno() && !identificadores(lista(catalogos.snapshot(autorizacion), "instalaciones")).contains(s(visita, "instalacionId"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no disponible");
        }
        return dato("visitaId", visitaId, "ot", s(visita, "ot"), "texto", s(visita, "acta"), "cierre", l(visita, "cierre"));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JsonObject snapshot(Usuarios.Usuario u, String authorization) {
        var c = catalogos.snapshot(authorization);
        var instalaciones = identificadores(lista(c, "instalaciones"));
        var visitas = db.todos("visitas").stream().filter(v -> instalaciones.contains(s(v, "instalacionId"))).toList();
        var idsVisitas = identificadores(visitas);
        var protocolos = db.todos("protocolos").stream().filter(p -> idsVisitas.contains(s(p, "visitaId")) && (u.interno() || !List.of("BORRADOR","ANULADO").contains(s(p,"estado")))).toList();
        var idsProtocolos = identificadores(protocolos);
        var pruebas = db.todos("pruebas").stream().filter(t -> idsProtocolos.contains(s(t, "protocoloId"))).toList();
        var idsPruebas = identificadores(pruebas);
        var salida = new JsonObject();
        salida.add("visitas",GSON.toJsonTree(visitas)); salida.add("protocolos",GSON.toJsonTree(protocolos)); salida.add("pruebas",GSON.toJsonTree(pruebas));
        salida.add("participantes",GSON.toJsonTree(db.todos("participantes").stream().filter(a -> idsVisitas.contains(s(a, "visitaId"))).toList()));
        salida.add("mediciones",GSON.toJsonTree(db.todos("mediciones").stream().filter(m -> idsPruebas.contains(s(m, "pruebaId"))).toList()));
        salida.add("historial",GSON.toJsonTree(db.todos("historial").stream().filter(h -> idsProtocolos.contains(s(h, "protocoloId"))).toList()));
        return salida;
    }
    private void editable(Usuarios.Usuario u, JsonObject p) {
        permiso(u.interno() && (!u.rol().equals("TECNICO") || s(p,"autorId").equals(u.id())));
        exigir(List.of("BORRADOR","OBSERVADO").contains(s(p,"estado")),"El protocolo está bloqueado");
    }
    private List<JsonObject> pruebas(String protocoloId) { return db.todos("pruebas").stream().filter(t -> s(t,"protocoloId").equals(protocoloId)).toList(); }
    private List<JsonObject> efectivas(String protocoloId) {
        var grupos = pruebas(protocoloId).stream().collect(Collectors.groupingBy(t -> s(t,"tipo")+":"+s(t,"elemento")));
        return grupos.values().stream().map(g -> g.stream().filter(t -> s(t,"momento").equals("POSTERIOR") && b(t,"realizada")).findFirst()
            .orElseGet(() -> g.stream().filter(t -> s(t,"momento").equals("UNICA") && b(t,"realizada")).findFirst()
                .orElseGet(() -> g.stream().filter(t -> b(t,"realizada")).findFirst().orElse(g.get(0))))).toList();
    }
    private Map<String,Double> medidas(String pruebaId) {
        var salida = new HashMap<String,Double>();
        db.todos("mediciones").stream().filter(m -> s(m,"pruebaId").equals(pruebaId)).forEach(m -> salida.put(s(m,"codigo"),d(m,"valor"))); return salida;
    }
    private boolean variacion(JsonObject p) {
        var anteriores = db.todos("protocolos").stream().filter(a -> s(a,"equipoId").equals(s(p,"equipoId")) && l(a,"fecha")<l(p,"fecha") && !List.of("BORRADOR","ANULADO").contains(s(a,"estado"))).sorted(Comparator.comparingLong((JsonObject o) -> l(o,"fecha")).reversed()).toList();
        for (var t : efectivas(s(p,"id"))) if (b(t,"realizada")) {
            var previa = anteriores.stream().flatMap(a -> efectivas(s(a,"id")).stream().filter(x -> b(x,"realizada") && s(x,"tipo").equals(s(t,"tipo")) && s(x,"elemento").equals(s(t,"elemento")))).findFirst().orElse(null);
            if (previa != null) {
                var antes = medidas(s(previa,"id"));
                for (var m : medidas(s(t,"id")).entrySet()) if (!List.of("tension","tap","teorica","ajuste","esperado").contains(m.getKey()) && antes.containsKey(m.getKey()) && antes.get(m.getKey()) != 0 && Math.abs((m.getValue()-antes.get(m.getKey()))/antes.get(m.getKey())*100)>25) return true;
            }
        }
        return false;
    }
    public synchronized void aplicar(Usuarios.Usuario u, String authorization, JsonObject evento) {
        transaccion.executeWithoutResult(estado -> aplicarEvento(u, authorization, evento));
    }

    private void aplicarEvento(Usuarios.Usuario u, String authorization, JsonObject evento) {
        permiso(u.interno()); if (db.repetido(evento,u)) return;
        var c = catalogos.snapshot(authorization);
        var payload = obj(s(evento,"payload")); String tipo = s(evento,"tipo");
        switch (tipo) {
            case "VISITA" -> {
                var v = payload.getAsJsonObject("visita");
                catalogo(c,"instalaciones",s(v,"instalacionId")); LocalDate.parse(s(v,"fecha"));
                exigir(!s(v,"ot").isBlank() && List.of("SOLO_PRUEBAS","PRUEBAS_Y_MANT").contains(s(v,"tipo")),"OT o tipo inválido");
                exigir(db.buscar("visitas",s(v,"id")) == null && db.todos("visitas").stream().noneMatch(a -> s(a,"ot").equals(s(v,"ot"))),"Visita u OT duplicada");
                var roles = personal.rolesActivos(authorization);
                exigir("INGENIERO".equals(roles.get(s(v,"ingenieroId"))),"Ingeniero a cargo obligatorio");
                exigir(s(v,"creadorId").equals(u.id()) && l(v,"cierre")==0,"Autor o estado inválido");
                var participantes = payload.getAsJsonArray("participantes"); exigir(!participantes.isEmpty(),"Participantes obligatorios");
                participantes.forEach(a -> {
                    String usuario = a.getAsString();
                    String rol = roles.get(usuario);
                    exigir("TECNICO".equals(rol) || "INGENIERO".equals(rol), "Participante inválido");
                    db.guardar("participantes",s(v,"id")+":"+usuario,dato("visitaId",s(v,"id"),"usuarioId",usuario));
                });
                db.guardar("visitas",s(v,"id"),v);
            }
            case "PROTOCOLO" -> {
                var p = payload.getAsJsonObject("protocolo");
                var v = db.obtener("visitas",s(p,"visitaId")); var equipo = catalogo(c,"equipos",s(p,"equipoId"));
                exigir(l(v,"cierre")==0 && s(v,"instalacionId").equals(s(equipo,"instalacionId")),"Visita cerrada o equipo ajeno a la instalación");
                exigir(db.buscar("protocolos",s(p,"id"))==null && !s(p,"numero").isBlank() && db.todos("protocolos").stream().noneMatch(a -> s(a,"numero").equals(s(p,"numero")) || s(a,"visitaId").equals(s(p,"visitaId")) && s(a,"equipoId").equals(s(p,"equipoId"))),"Número duplicado o segundo protocolo del equipo en la visita");
                exigir(s(p,"estado").equals("BORRADOR") && s(p,"autorId").equals(u.id()) && l(p,"revision")==0,"El protocolo debe nacer en Borrador con su autor");
                if (!s(p,"reemplazaId").isBlank()) { var anterior = db.obtener("protocolos",s(p,"reemplazaId")); permiso(u.revisor()); exigir(s(anterior,"estado").equals("FORMALIZADO") && s(anterior,"equipoId").equals(s(p,"equipoId")),"Reemplazo inválido"); }
                db.guardar("protocolos",s(p,"id"),p); historial(u,p,payload.getAsJsonObject("historial"),null,"");
            }
            case "DATOS_PROTOCOLO" -> {
                var entrada = payload.getAsJsonObject("protocolo"); var p = db.obtener("protocolos",s(entrada,"id")); editable(u,p); db.revision(p,l(payload,"revisionEsperada"));
                double temperatura = d(entrada,"temperatura"), humedad = d(entrada,"humedad");
                exigir(Double.isFinite(temperatura) && temperatura>=-80 && temperatura<=100 && Double.isFinite(humedad) && humedad>=0 && humedad<=100,"Datos ambientales inválidos");
                for (var campo : List.of("temperatura","humedad","mantenimiento","comentario")) p.add(campo,entrada.get(campo));
                actualizar(p);
            }
            case "PRUEBA" -> {
                var t = payload.getAsJsonObject("prueba"); var p = db.obtener("protocolos",s(t,"protocoloId")); editable(u,p); db.revision(p,l(payload,"revisionEsperada"));
                var equipo = catalogo(c,"equipos",s(p,"equipoId")); String te = s(equipo,"tipo"), tp = s(t,"tipo");
                exigir(ReglasElectricas.tipos(te).contains(tp) && ReglasElectricas.elementos(te,tp).contains(s(t,"elemento")),"Prueba o elemento no aplicable");
                exigir(List.of("UNICA","PREVIA","POSTERIOR").contains(s(t,"momento")),"Momento inválido");
                exigir(pruebas(s(p,"id")).stream().noneMatch(x -> s(x,"tipo").equals(tp) && s(x,"elemento").equals(s(t,"elemento")) && s(x,"momento").equals("UNICA") != s(t,"momento").equals("UNICA")),"No mezclar medición única con previa/posterior");
                exigir(s(t,"momento").equals("UNICA") || s(db.obtener("visitas",s(p,"visitaId")),"tipo").equals("PRUEBAS_Y_MANT"),"La visita no contempla mantenimiento");
                var previa = db.buscar("pruebas",s(t,"id")); exigir(previa==null || s(previa,"protocoloId").equals(s(p,"id")) && s(previa,"tipo").equals(tp) && s(previa,"elemento").equals(s(t,"elemento")) && s(previa,"momento").equals(s(t,"momento")),"No se puede reasignar una prueba");
                exigir(pruebas(s(p,"id")).stream().noneMatch(x -> !s(x,"id").equals(s(t,"id")) && s(x,"tipo").equals(tp) && s(x,"elemento").equals(s(t,"elemento")) && s(x,"momento").equals(s(t,"momento"))),"Prueba duplicada");
                var mediciones = payload.getAsJsonArray("mediciones"); var valores = new HashMap<String,Double>();
                mediciones.forEach(m -> { var medida = m.getAsJsonObject(); String codigo = s(medida,"codigo"); exigir(s(medida,"pruebaId").equals(s(t,"id")) && ReglasElectricas.parametros(te,tp).containsKey(codigo) && ReglasElectricas.parametros(te,tp).get(codigo).equals(s(medida,"unidad")) && !valores.containsKey(codigo),"Parámetro o unidad inválida"); valores.put(codigo,d(medida,"valor")); });
                if (b(t,"realizada")) {
                    catalogo(c,"instrumentos",s(t,"instrumentoId"));
                    var fecha = Instant.ofEpochMilli(l(p,"fecha")).atZone(ZoneId.of("America/Santiago")).toLocalDate();
                    exigir(lista(c,"calibraciones").stream().anyMatch(cal -> s(cal,"instrumentoId").equals(s(t,"instrumentoId")) && !LocalDate.parse(s(cal,"fecha")).isAfter(fecha) && !LocalDate.parse(s(cal,"vencimiento")).isBefore(fecha)),"Calibración vencida o no vigente en la fecha de prueba");
                    String resultado = ReglasElectricas.evaluar(te,tp,valores,d(equipo,"maxContactos"),b(t,"opera"));
                    if (resultado==null) t.remove("resultado"); else t.addProperty("resultado",resultado);
                } else { exigir(!s(t,"motivo").isBlank() && mediciones.isEmpty(),"Prueba no realizada requiere motivo y no tiene mediciones"); t.remove("resultado"); t.remove("instrumentoId"); }
                quitarMediciones(s(t,"id"));
                mediciones.forEach(m -> db.guardar("mediciones",s(t,"id")+":"+s(m.getAsJsonObject(),"codigo"),m.getAsJsonObject()));
                db.guardar("pruebas",s(t,"id"),t); actualizar(p);
            }
            case "ELIMINAR_PRUEBA" -> {
                var protocolo = db.obtener("protocolos", s(payload, "protocoloId"));
                editable(u, protocolo);
                db.revision(protocolo, l(payload, "revisionEsperada"));
                String pruebaId = s(payload, "pruebaId");
                var prueba = db.obtener("pruebas", pruebaId);
                exigir(s(prueba, "protocoloId").equals(s(protocolo, "id")),
                    "La prueba no pertenece al protocolo");
                quitarMediciones(pruebaId);
                db.quitar("pruebas", pruebaId);
                actualizar(protocolo);
            }
            case "EVALUACION" -> {
                permiso(u.revisor()); var entrada = payload.getAsJsonObject("prueba"); var t = db.obtener("pruebas",s(entrada,"id")); var p = db.obtener("protocolos",s(t,"protocoloId"));
                db.revision(p,l(payload,"revisionEsperada")); exigir(!List.of("FORMALIZADO","ANULADO").contains(s(p,"estado")) && b(t,"realizada"),"Protocolo bloqueado");
                String equipo = s(catalogo(c,"equipos",s(p,"equipoId")),"tipo");
                exigir(s(t,"tipo").equals("DEVANADOS") || s(t,"tipo").equals("AISLACION") && List.of("INTERRUPTOR","CELDA_MT").contains(equipo),"La prueba tiene resultado automático");
                exigir(List.of("APROBADO","OBSERVADO","RECHAZADO").contains(s(entrada,"resultado")) && !s(entrada,"comentario").isBlank(),"Resultado y justificación obligatorios");
                t.addProperty("resultado",s(entrada,"resultado")); t.addProperty("comentario",s(entrada,"comentario")); db.guardar("pruebas",s(t,"id"),t); actualizar(p);
            }
            case "MEDICION_AISLACION" -> guardarMedicionAislacion(u, evento, payload);
            case "ESTADO" -> cambiarEstado(u,c,payload);
            case "CIERRE" -> cerrar(u,c,payload);
            default -> exigir(false,"Operación desconocida");
        }
        db.confirmar(evento,u);
    }

    private void guardarMedicionAislacion(Usuarios.Usuario usuario, JsonObject evento, JsonObject datos) {
        double lectura1 = d(datos, "lectura1");
        double lectura10 = d(datos, "lectura10");
        double indice = lectura10 / lectura1;
        long fecha = l(datos, "fechaHora");
        exigir(Double.isFinite(lectura1) && Double.isFinite(lectura10) &&
            lectura1 > 0 && lectura10 > 0 && Double.isFinite(indice) && fecha > 0, "Lecturas de aislación inválidas");
        String resultado = lectura1 < 100 || indice < 1 ? "RECHAZADO" : indice < 2 ? "OBSERVADO" : "APROBADO";
        String id = s(evento, "id");
        db.guardar("mediciones_aislacion", id, dato("id", id, "usuarioId", usuario.id(), "equipoTag", "TR-01",
            "instalacion", "Planta Lampa", "lectura1", lectura1, "lectura10", lectura10,
            "indice", indice, "resultado", resultado, "fechaHora", fecha));
    }
    private void quitarMediciones(String pruebaId) {
        db.todos("mediciones").stream().filter(m -> s(m, "pruebaId").equals(pruebaId))
            .forEach(m -> db.quitar("mediciones", pruebaId + ":" + s(m, "codigo")));
    }
    private void actualizar(JsonObject p) { p.addProperty("revision",l(p,"revision")+1); db.guardar("protocolos",s(p,"id"),p); }
    private void historial(Usuarios.Usuario u, JsonObject p, JsonObject entrada, String anterior, String motivo) {
        exigir(entrada != null && !s(entrada,"id").isBlank() && db.buscar("historial",s(entrada,"id"))==null,"ID de trazabilidad duplicado");
        db.guardar("historial",s(entrada,"id"),dato("id",s(entrada,"id"),"protocoloId",s(p,"id"),"anterior",anterior,"nuevo",s(p,"estado"),"usuarioId",u.id(),"fecha",l(entrada,"fecha"),"motivo",motivo));
    }
    private void cambiarEstado(Usuarios.Usuario u, JsonObject c, JsonObject entrada) {
        var pedido = entrada.getAsJsonObject("protocolo"); var p = db.obtener("protocolos",s(pedido,"id"));
        db.revision(p,l(entrada,"revisionEsperada")); String desde = s(p,"estado"), hacia = s(pedido,"estado");
        exigir(!List.of("FORMALIZADO","ANULADO").contains(desde),"El protocolo está bloqueado");
        var h = entrada.getAsJsonObject("historial"); String motivo = s(h,"motivo");
        switch (hacia) {
            case "REGISTRADO_TERRENO" -> {
                editable(u,p); exigir(Double.isFinite(d(p,"temperatura")) && Double.isFinite(d(p,"humedad")),"Datos ambientales obligatorios");
                String equipo = s(catalogo(c,"equipos",s(p,"equipoId")),"tipo"); var pruebas = efectivas(s(p,"id"));
                for (String tipo : ReglasElectricas.tipos(equipo)) for (String elemento : ReglasElectricas.elementos(equipo,tipo)) exigir(pruebas.stream().anyMatch(t -> s(t,"tipo").equals(tipo) && s(t,"elemento").equals(elemento)),"Falta prueba o motivo: "+tipo+" "+elemento);
                exigir(pruebas.stream().anyMatch(t -> b(t,"realizada")),"Se requiere al menos una prueba completa");
                exigir(!s(db.obtener("visitas",s(p,"visitaId")),"tipo").equals("PRUEBAS_Y_MANT") || !s(p,"mantenimiento").isBlank(),"Describe el mantenimiento");
            }
            case "FORMALIZADO","OBSERVADO" -> {
                permiso(u.revisor()); exigir(desde.equals("REGISTRADO_TERRENO"),"Solo se revisan protocolos registrados en terreno");
                if (hacia.equals("OBSERVADO")) exigir(!motivo.isBlank(),"Motivo obligatorio");
                else {
                    exigir(l(db.obtener("visitas",s(p,"visitaId")),"cierre")>0,"Primero debe cerrarse la visita y entregarse el acta");
                    var realizadas = efectivas(s(p,"id")).stream().filter(t -> b(t,"realizada")).toList();
                    exigir(!realizadas.isEmpty() && realizadas.stream().allMatch(t -> !s(t,"resultado").isBlank()),"Falta evaluación manual del ingeniero");
                    exigir(!variacion(p) || !motivo.isBlank(),"Comenta la variación significativa");
                }
            }
            case "ANULADO" -> { permiso(u.revisor()); exigir(!motivo.isBlank(),"Motivo obligatorio"); }
            default -> exigir(false,"Transición inválida");
        }
        p.addProperty("estado",hacia); actualizar(p); historial(u,p,h,desde,motivo);
    }
    private String global(String id) {
        var resultados = efectivas(id).stream().filter(t -> b(t,"realizada")).map(t -> s(t,"resultado")).toList();
        if (resultados.stream().anyMatch(String::isBlank)) return "Pendiente de evaluación";
        return resultados.contains("RECHAZADO")?"RECHAZADO":resultados.contains("OBSERVADO")?"OBSERVADO":resultados.contains("APROBADO")?"APROBADO":"Pendiente de evaluación";
    }
    private void cerrar(Usuarios.Usuario u, JsonObject c, JsonObject entrada) {
        permiso(List.of("TECNICO","INGENIERO").contains(u.rol())); var v = db.obtener("visitas",s(entrada,"id"));
        exigir(l(v,"cierre")==0,"Visita ya cerrada");
        var ps = db.todos("protocolos").stream().filter(p -> s(p,"visitaId").equals(s(v,"id"))).toList();
        exigir(!ps.isEmpty() && ps.stream().allMatch(p -> List.of("REGISTRADO_TERRENO","ANULADO").contains(s(p,"estado"))),"Faltan protocolos por registrar");
        exigir(!s(entrada,"receptor").isBlank() && !s(entrada,"receptorCargo").isBlank() && rut(s(entrada,"receptorRut")) && l(entrada,"cierre")>0 && l(entrada,"recepcion")>0,"Recepción inválida");
        for (String campo : List.of("receptor","receptorRut","receptorCargo","cierre","recepcion")) v.add(campo,entrada.get(campo));
        var instalacion = catalogo(c,"instalaciones",s(v,"instalacionId")); var cliente = catalogo(c,"clientes",s(instalacion,"clienteId"));
        var acta = new StringBuilder("INDUZTEK INGENIERÍA SpA\nACTA DE PRUEBA EN TERRENO\nPENDIENTE DE FORMALIZACIÓN\n");
        acta.append(s(v,"ot")).append(" · ").append(s(v,"fecha")).append("\nCliente: ").append(s(cliente,"nombre")).append("\nInstalación: ").append(s(instalacion,"nombre")).append('\n');
        for (var p : ps) if (!s(p,"estado").equals("ANULADO")) {
            acta.append("\nProtocolo: ").append(s(p,"numero")).append("\nEquipo: ").append(s(catalogo(c,"equipos",s(p,"equipoId")),"tag")).append("\nResponsable: ").append(s(p,"autorId")).append("\nResultado: ").append(global(s(p,"id"))).append('\n');
            for (var t : pruebas(s(p,"id"))) { acta.append(s(t,"tipo")).append(" · ").append(s(t,"elemento")).append(" · ").append(s(t,"momento")).append("\n"); if (!b(t,"realizada")) acta.append("No realizada: ").append(s(t,"motivo")); else { acta.append(medidas(s(t,"id"))).append("\nResultado: ").append(s(t,"resultado")); } acta.append('\n'); }
        }
        acta.append("\nRecibe: ").append(s(v,"receptor")).append("\nRUT: ").append(s(v,"receptorRut")).append("\nCargo: ").append(s(v,"receptorCargo")).append("\nRecepción: ").append(l(v,"recepcion"));
        // Se conserva el documento emitido offline; el servidor valida la recepción y el cierre.
        v.addProperty("acta",s(entrada,"acta").isBlank()?acta.toString():s(entrada,"acta"));
        db.guardar("visitas",s(v,"id"),v);
    }
}
