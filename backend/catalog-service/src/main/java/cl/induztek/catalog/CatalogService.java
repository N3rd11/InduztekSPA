package cl.induztek.catalog;

import cl.induztek.common.*;
import com.google.gson.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static cl.induztek.common.Json.*;

@Service
public class CatalogService {
    public JsonObject cliente(Usuarios.Usuario usuario, String id) {
        if (!usuario.interno() && !id.equals(usuario.clienteId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return db.obtener("clientes", id);
    }
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO = Pattern.compile("[+0-9() \\-]{7,20}");
    private final Documentos db;
    private final TransactionTemplate transaccion;

    public CatalogService(Documentos db, PlatformTransactionManager gestor) {
        this.db = db;
        this.transaccion = new TransactionTemplate(gestor);
        if (db.todos("clientes").isEmpty()) Semilla.cargar(db,Semilla.catalogos());
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JsonObject snapshot(Usuarios.Usuario u) {
        var salida = new JsonObject();
        var instalaciones = db.todos("instalaciones").stream().filter(i -> u.interno() || s(i,"clienteId").equals(u.clienteId())).toList();
        var idsInstalaciones = instalaciones.stream().map(i -> s(i, "id")).collect(Collectors.toSet());
        salida.add("clientes",GSON.toJsonTree(db.todos("clientes").stream().filter(c -> u.interno() || s(c,"id").equals(u.clienteId())).toList()));
        salida.add("instalaciones",GSON.toJsonTree(instalaciones));
        salida.add("equipos",GSON.toJsonTree(db.todos("equipos").stream().filter(e -> idsInstalaciones.contains(s(e, "instalacionId"))).toList()));
        salida.add("instrumentos",GSON.toJsonTree(db.todos("instrumentos")));
        salida.add("calibraciones",GSON.toJsonTree(db.todos("calibraciones")));
        return salida;
    }
    public synchronized void aplicar(Usuarios.Usuario u, JsonObject evento) {
        transaccion.executeWithoutResult(estado -> aplicarEvento(u, evento));
    }

    private void aplicarEvento(Usuarios.Usuario u, JsonObject evento) {
        permiso(u.interno());
        if (db.repetido(evento,u)) return;
        var tipo = s(evento,"tipo"); var payload = obj(s(evento,"payload"));
        switch (tipo) {
            case "CLIENTE" -> {
                exigir(!s(payload,"nombre").isBlank() && rut(s(payload,"rut")),"Razón social y RUT válido obligatorios");
                String correo = s(payload, "correo").trim();
                String telefono = s(payload, "telefono").trim();
                // Este contrato también recibe eventos encolados por versiones anteriores de la app.
                exigir(correo.isEmpty() || correo.length() <= 120 && CORREO.matcher(correo).matches(), "Correo de contacto inválido");
                exigir(telefono.isEmpty() || TELEFONO.matcher(telefono).matches(), "Teléfono de contacto inválido");
                payload.addProperty("correo", correo);
                payload.addProperty("telefono", telefono);
                exigir(db.todos("clientes").stream().noneMatch(c -> !s(c,"id").equals(s(payload,"id")) && s(c,"rut").replace(".","").replace("-","").equalsIgnoreCase(s(payload,"rut").replace(".","").replace("-",""))),"RUT duplicado");
                guardar("clientes",payload);
            }
            case "INSTALACION" -> {
                db.obtener("clientes",s(payload,"clienteId")); exigir(!s(payload,"nombre").isBlank(),"Nombre obligatorio");
                var previa = db.buscar("instalaciones",s(payload,"id"));
                exigir(previa == null || s(previa,"clienteId").equals(s(payload,"clienteId")),"Una instalación no puede cambiar de empresa");
                guardar("instalaciones",payload);
            }
            case "EQUIPO" -> {
                payload.addProperty("tag",s(payload,"tag").trim().toUpperCase(java.util.Locale.ROOT));
                db.obtener("instalaciones",s(payload,"instalacionId"));
                exigir(!s(payload,"tag").isBlank() && List.of("TRANSFORMADOR","INTERRUPTOR","CELDA_MT","CABLE","RELE").contains(s(payload,"tipo")),"TAG o tipo inválido");
                exigir(db.todos("equipos").stream().noneMatch(e -> !s(e,"id").equals(s(payload,"id")) && s(e,"instalacionId").equals(s(payload,"instalacionId")) && s(e,"tag").equalsIgnoreCase(s(payload,"tag"))),"TAG duplicado en la instalación; las mediciones locales se conservan para resolver el conflicto");
                var previo = db.buscar("equipos",s(payload,"id"));
                exigir(s(payload,"maxContactos").isBlank() || Double.isFinite(d(payload,"maxContactos")) && d(payload,"maxContactos")>0,"Máximo de contactos inválido");
                boolean mismoMaximo = previo == null || s(previo,"maxContactos").isBlank() && s(payload,"maxContactos").isBlank() || Double.compare(d(previo,"maxContactos"),d(payload,"maxContactos"))==0;
                exigir(previo == null || s(previo,"instalacionId").equals(s(payload,"instalacionId")) && s(previo,"tipo").equals(s(payload,"tipo")) && s(previo,"placa").equals(s(payload,"placa")) && mismoMaximo,"La identidad y referencias eléctricas del equipo no pueden cambiar");
                guardar("equipos",payload);
            }
            case "INSTRUMENTO" -> {
                permiso(u.rol().equals("JEFE_TECNICO"));
                var i = payload.getAsJsonObject("instrumento"); var c = payload.getAsJsonObject("calibracion");
                exigir(!s(i,"nombre").isBlank() && !s(i,"serie").isBlank() && s(c,"instrumentoId").equals(s(i,"id")),"Instrumento inválido");
                exigir(db.todos("instrumentos").stream().noneMatch(a -> !s(a,"id").equals(s(i,"id")) && s(a,"serie").equals(s(i,"serie"))),"Serie duplicada");
                exigir(!LocalDate.parse(s(c,"vencimiento")).isBefore(LocalDate.parse(s(c,"fecha"))),"Fechas de calibración inválidas");
                exigir(db.buscar("calibraciones",s(c,"id")) == null,"No se modifica una calibración histórica");
                guardar("instrumentos",i); guardar("calibraciones",c);
            }
            default -> exigir(false,"Operación de catálogo desconocida");
        }
        db.confirmar(evento,u);
    }
    private void guardar(String coleccion, JsonObject o) { exigir(!s(o,"id").isBlank(),"ID obligatorio"); db.guardar(coleccion,s(o,"id"),o); }
}
