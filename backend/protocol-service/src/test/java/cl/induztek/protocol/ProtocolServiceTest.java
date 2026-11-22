package cl.induztek.protocol;

import cl.induztek.common.*;
import com.google.gson.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static cl.induztek.common.Json.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:protocol-test;DB_CLOSE_DELAY=-1")
class ProtocolServiceTest {
    @Autowired ProtocolService servicio;
    @Autowired Documentos db;
    @MockitoBean CatalogGateway catalogos;
    @MockitoBean PersonalGateway personal;
    private final Usuarios.Usuario tecnico = Usuarios.TODOS.get(0), ingeniero = Usuarios.TODOS.get(1);
    @BeforeEach void preparar() {
        when(catalogos.snapshot(anyString())).thenReturn(Semilla.catalogos());
        when(personal.rolesActivos(anyString())).thenReturn(Map.of(tecnico.id(), "TECNICO", ingeniero.id(), "INGENIERO"));
    }
    private JsonObject evento(Usuarios.Usuario u, String tipo, JsonObject payload) {
        return dato("id",UUID.randomUUID().toString(),"usuarioId",u.id(),"tipo",tipo,"payload",payload.toString());
    }
    private void enviar(Usuarios.Usuario u,String tipo,JsonObject payload) { servicio.aplicar(u,"Basic demo",evento(u,tipo,payload)); }
    private JsonObject crear() {
        String id = UUID.randomUUID().toString();
        var v = dato("id","v-"+id,"instalacionId","molienda","ot","OT-"+id,"fecha",LocalDate.now().toString(),"ingenieroId",ingeniero.id(),"tipo","SOLO_PRUEBAS","creadorId",tecnico.id());
        enviar(tecnico,"VISITA",dato("visita",v,"participantes",List.of(tecnico.id())));
        var p = dato("id",id,"numero","PT-"+id,"visitaId","v-"+id,"equipoId","rel50","autorId",tecnico.id(),"fecha",System.currentTimeMillis(),"estado","BORRADOR","revision",0);
        enviar(tecnico,"PROTOCOLO",dato("protocolo",p,"historial",dato("id",UUID.randomUUID().toString(),"fecha",System.currentTimeMillis())));
        return p;
    }
    private void estado(Usuarios.Usuario u, JsonObject p, String hacia) {
        var nuevo = p.deepCopy(); nuevo.addProperty("estado",hacia);
        enviar(u,"ESTADO",dato("protocolo",nuevo,"revisionEsperada",l(p,"revision"),"historial",dato("id",UUID.randomUUID().toString(),"fecha",System.currentTimeMillis(),"motivo","Revisado")));
    }
    @Test void recorridoYProteccionesDelServidor() {
        var p = crear(); String id = s(p,"id");
        assertEquals("BORRADOR",s(db.obtener("protocolos",id),"estado"));
        p.addProperty("temperatura",20); p.addProperty("humedad",50);
        enviar(tecnico,"DATOS_PROTOCOLO",dato("protocolo",p,"revisionEsperada",0));
        var t = dato("id","t-"+id,"protocoloId",id,"tipo","OPERACION_RELE","elemento","50/51","momento","UNICA","realizada",true,"motivo","","instrumentoId","inyeccion","opera",true);
        var ms = new JsonArray();
        Map.of("ajuste",5.0,"corriente",5.1,"esperado",100.0,"tiempo",104.0).forEach((codigo,valor) -> ms.add(dato("pruebaId","t-"+id,"codigo",codigo,"valor",valor,"unidad",codigo.equals("ajuste")||codigo.equals("corriente")?"A":"ms")));
        var e = evento(tecnico,"PRUEBA",dato("prueba",t,"mediciones",ms,"revisionEsperada",1));
        servicio.aplicar(tecnico,"Basic demo",e); servicio.aplicar(tecnico,"Basic demo",e);
        assertEquals(2,l(db.obtener("protocolos",id),"revision"));
        assertEquals("APROBADO",s(db.obtener("pruebas","t-"+id),"resultado"));
        p = db.obtener("protocolos",id); estado(tecnico,p,"REGISTRADO_TERRENO");
        var registrado = db.obtener("protocolos",id);
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> estado(tecnico,registrado,"FORMALIZADO")).getStatusCode().value());
        enviar(tecnico,"CIERRE",dato("id",s(p,"visitaId"),"receptor","Marcela Fuentes","receptorRut","14.226.791-8","receptorCargo","Jefa","cierre",System.currentTimeMillis(),"recepcion",System.currentTimeMillis(),"acta","Acta emitida offline · Pendiente de formalización"));
        estado(ingeniero,registrado,"FORMALIZADO");
        assertEquals("FORMALIZADO",s(db.obtener("protocolos",id),"estado"));
        var bloqueado = db.obtener("protocolos",id);
        assertThrows(ResponseStatusException.class,() -> enviar(ingeniero,"DATOS_PROTOCOLO",dato("protocolo",bloqueado,"revisionEsperada",l(bloqueado,"revision"))));
        assertThrows(ResponseStatusException.class,() -> estado(ingeniero,bloqueado,"ANULADO"));
    }
    @Test void revisionEnConflictoNoSobrescribe() {
        var p = crear(); p.addProperty("temperatura",20); p.addProperty("humedad",40);
        enviar(tecnico,"DATOS_PROTOCOLO",dato("protocolo",p,"revisionEsperada",0));
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> enviar(tecnico,"DATOS_PROTOCOLO",dato("protocolo",p,"revisionEsperada",0))).getStatusCode().value());
        assertEquals(1,l(db.obtener("protocolos",s(p,"id")),"revision"));
    }
    @Test void eliminaPruebaConSusMedicionesYReintentaSinDuplicar() {
        var protocolo = crear();
        String protocoloId = s(protocolo, "id");
        String pruebaId = "t-" + protocoloId;
        var prueba = dato("id", pruebaId, "protocoloId", protocoloId, "tipo", "OPERACION_RELE",
            "elemento", "50/51", "momento", "UNICA", "realizada", true, "instrumentoId", "inyeccion", "opera", true);
        var mediciones = new JsonArray();
        Map.of("ajuste", 5.0, "corriente", 5.1, "esperado", 100.0, "tiempo", 104.0).forEach((codigo, valor) ->
            mediciones.add(dato("pruebaId", pruebaId, "codigo", codigo, "valor", valor,
                "unidad", Set.of("ajuste", "corriente").contains(codigo) ? "A" : "ms")));
        enviar(tecnico, "PRUEBA", dato("prueba", prueba, "mediciones", mediciones, "revisionEsperada", 0));

        var eliminacion = dato("protocoloId", protocoloId, "pruebaId", pruebaId, "revisionEsperada", 0);
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> enviar(tecnico, "ELIMINAR_PRUEBA", eliminacion)).getStatusCode().value());
        assertNotNull(db.buscar("pruebas", pruebaId));
        eliminacion.addProperty("revisionEsperada", 1);
        var evento = evento(tecnico, "ELIMINAR_PRUEBA", eliminacion);
        servicio.aplicar(tecnico, "Basic demo", evento);
        servicio.aplicar(tecnico, "Basic demo", evento);

        assertNull(db.buscar("pruebas", pruebaId));
        assertTrue(db.todos("mediciones").stream().noneMatch(m -> s(m, "pruebaId").equals(pruebaId)));
        assertEquals(2, l(db.obtener("protocolos", protocoloId), "revision"));
    }
    @Test void clienteNoRegistraYServidorFiltraPorEmpresa() {
        var cliente = Usuarios.TODOS.stream().filter(u -> u.id().equals("cliente1")).findFirst().orElseThrow();
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> enviar(cliente,"VISITA",new JsonObject())).getStatusCode().value());
        var c = Semilla.catalogos(); var instalaciones = new JsonArray();
        c.getAsJsonArray("instalaciones").forEach(i -> { if (s(i.getAsJsonObject(),"clienteId").equals(cliente.clienteId())) instalaciones.add(i); });
        c.add("instalaciones",instalaciones); when(catalogos.snapshot("cliente")).thenReturn(c);
        var salida = servicio.snapshot(cliente,"cliente");
        salida.getAsJsonArray("visitas").forEach(v -> assertEquals("lampa",s(v.getAsJsonObject(),"instalacionId")));
        salida.getAsJsonArray("protocolos").forEach(p -> assertFalse(List.of("BORRADOR","ANULADO").contains(s(p.getAsJsonObject(),"estado"))));
    }
    @Test void reglasYCalibracionSeCompruebanEnServidor() {
        assertEquals("OBSERVADO",ReglasElectricas.evaluar("TRANSFORMADOR","AISLACION",Map.of("tension",5.0,"lectura1",6100.0,"lectura10",10980.0),Double.NaN,true));
        assertEquals("RECHAZADO",ReglasElectricas.evaluar("RELE","OPERACION_RELE",Map.of(),Double.NaN,false));
        assertEquals("OBSERVADO",ReglasElectricas.evaluar("TRANSFORMADOR","TTR",Map.of("teorica",30.0,"medida",30.3,"tap",3.0),Double.NaN,true));
        var p = crear(); var t = dato("id",UUID.randomUUID().toString(),"protocoloId",s(p,"id"),"tipo","OPERACION_RELE","elemento","50/51","momento","UNICA","realizada",true,"instrumentoId","micro","opera",false);
        assertThrows(ResponseStatusException.class,() -> enviar(tecnico,"PRUEBA",dato("prueba",t,"mediciones",new JsonArray(),"revisionEsperada",0)));
    }
}
