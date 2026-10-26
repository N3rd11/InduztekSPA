package cl.induztek.catalog;

import cl.induztek.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.net.URI;
import java.net.http.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import static org.junit.jupiter.api.Assertions.*;
import static cl.induztek.common.Json.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:catalog-test;DB_CLOSE_DELAY=-1")
class CatalogServiceTest {
    @Autowired CatalogService servicio;
    @Value("${local.server.port}") int puerto;
    @Test void autenticacionHttpReal() throws Exception {
        var cliente = HttpClient.newHttpClient();
        var uri = URI.create("http://localhost:"+puerto+"/api/session");
        assertEquals(401,cliente.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var cabecera = "Basic "+Base64.getEncoder().encodeToString("mfuentes@envpacifico.ejemplo:1234".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var respuesta = cliente.send(HttpRequest.newBuilder(uri).header("Authorization",cabecera).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,respuesta.statusCode());
        assertEquals("cliente1",s(obj(respuesta.body()),"id"));
    }
    @Test void clienteSoloConsultaSuEmpresa() {
        var cliente = Usuarios.TODOS.stream().filter(u -> u.id().equals("cliente1")).findFirst().orElseThrow();
        var datos = servicio.snapshot(cliente);
        assertEquals(1,datos.getAsJsonArray("clientes").size());
        assertEquals(2,datos.getAsJsonArray("equipos").size());
        var e = dato("id",UUID.randomUUID().toString(),"usuarioId",cliente.id(),"tipo","CLIENTE","payload",dato("id","nuevo","nombre","Empresa","rut","76.482.915-8").toString());
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> servicio.aplicar(cliente,e)).getStatusCode().value());
    }
    @Test void instrumentoSoloJefeYEventoIdempotente() {
        var tecnico = Usuarios.TODOS.get(0); var jefe = Usuarios.TODOS.stream().filter(u -> u.id().equals("jefe1")).findFirst().orElseThrow();
        String id = UUID.randomUUID().toString();
        var instrumento = dato("id",id,"nombre","Instrumento de prueba","marca","Ficticia","modelo","M1","serie",id);
        var cal = dato("id","cal-"+id,"instrumentoId",id,"fecha","2026-01-01","vencimiento","2027-01-01");
        var evento = dato("id",UUID.randomUUID().toString(),"usuarioId",tecnico.id(),"tipo","INSTRUMENTO","payload",dato("instrumento",instrumento,"calibracion",cal).toString());
        assertEquals(403,assertThrows(ResponseStatusException.class,() -> servicio.aplicar(tecnico,evento)).getStatusCode().value());
        evento.addProperty("usuarioId",jefe.id());
        servicio.aplicar(jefe,evento); servicio.aplicar(jefe,evento);
        assertTrue(servicio.snapshot(jefe).toString().contains("Instrumento de prueba"));
    }
    @Test void editarEquipoConservaReferenciaAunqueJsonRepresenteSesentaComoDecimal() {
        var tecnico = Usuarios.TODOS.get(0);
        var equipo = servicio.snapshot(tecnico).getAsJsonArray("equipos").asList().stream()
            .map(e -> e.getAsJsonObject()).filter(e -> s(e,"id").equals("cel03")).findFirst().orElseThrow().deepCopy();
        equipo.addProperty("maxContactos",60.0);
        var evento = dato("id",UUID.randomUUID().toString(),"usuarioId",tecnico.id(),"tipo","EQUIPO","payload",equipo.toString());
        assertDoesNotThrow(() -> servicio.aplicar(tecnico,evento));
    }
}
