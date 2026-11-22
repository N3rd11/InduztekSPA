package cl.induztek.access;

import cl.induztek.common.Json;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:usuarios-test;DB_CLOSE_DELAY=-1",
        "INDUZTEK_CLAVE_JEFE1=PruebaJefe-91", "INDUZTEK_CLAVE_TECNICO1=PruebaTecnico-91", "induztek.demo=false"
    })
class AccessHttpTest {
    private static final String JEFE = "respinoza@induztek.ejemplo:PruebaJefe-91";
    private static final String TECNICO = "mgodoy@induztek.ejemplo:PruebaTecnico-91";
    private static final HttpServer CATALOGO = catalogo();
    @Value("${local.server.port}") int puerto;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("induztek.catalog-url", () -> "http://127.0.0.1:" + CATALOGO.getAddress().getPort());
    }

    @AfterAll static void pararCatalogo() { CATALOGO.stop(0); }

    @Test void altaValidacionesRolesYDesactivacion() throws Exception {
        String jefe = JEFE;
        String ruta = "/api/cuentas/clientes";
        String correo = "cuenta.prueba@induztek.ejemplo";
        String nueva = correo + ":UnaClaveDemo9";
        var alta = Json.dato("nombre", "Cliente de prueba", "correo", correo, "rut", "12.345.678-5",
            "clave", "UnaClaveDemo9", "clienteId", "empresa", "rol", "JEFE_TECNICO");
        assertEquals(401, enviar("POST", ruta, null, alta.toString()).statusCode());
        assertEquals(403, enviar("POST", ruta, TECNICO, alta.toString()).statusCode());
        var invalida = alta.deepCopy();
        invalida.addProperty("rut", "12.345.678-0");
        assertEquals(422, enviar("POST", ruta, jefe, invalida.toString()).statusCode());
        invalida = alta.deepCopy();
        invalida.addProperty("correo", "cuenta:alias@induztek.ejemplo");
        assertEquals(422, enviar("POST", ruta, jefe, invalida.toString()).statusCode());
        invalida = alta.deepCopy();
        invalida.addProperty("clienteId", "ausente");
        assertEquals(422, enviar("POST", ruta, jefe, invalida.toString()).statusCode());
        var creada = enviar("POST", ruta, jefe, alta.toString());
        assertEquals(201, creada.statusCode());
        var perfil = Json.obj(creada.body());
        assertEquals("CLIENTE", Json.s(perfil, "rol"));
        assertEquals("empresa", Json.s(perfil, "clienteId"));
        assertFalse(creada.body().contains("UnaClaveDemo9"));
        assertFalse(perfil.has("hash"));
        alta.addProperty("correo", correo.toUpperCase(java.util.Locale.ROOT));
        assertEquals(409, enviar("POST", ruta, jefe, alta.toString()).statusCode());
        assertEquals(200, enviar("GET", "/api/session", nueva, null).statusCode());
        assertEquals(401, enviar("GET", "/api/session", correo + ":incorrecta", null).statusCode());
        assertEquals(403, enviar("GET", ruta, nueva, null).statusCode());
        String estado = ruta + "/" + Json.s(perfil, "id") + "/estado";
        assertEquals(204, enviar("PATCH", estado, jefe, "{\"activo\":false}").statusCode());
        assertEquals(401, enviar("GET", "/api/session", nueva, null).statusCode());
        assertEquals(204, enviar("PATCH", estado, jefe, "{\"activo\":true}").statusCode());
        assertEquals(200, enviar("GET", "/api/session", nueva, null).statusCode());
    }

    @Test void registroRequiereAprobacionYEmpresaValidadaPorCatalogos() throws Exception {
        String correo = "registro@cliente.ejemplo";
        String credencial = correo + ":ClaveRegistro-91";
        String registro = Json.dato("nombre", "Solicitud cliente", "correo", correo, "rut", "11.111.111-1",
            "clave", "ClaveRegistro-91", "rol", "JEFE_TECNICO", "clienteId", "empresa").toString();
        assertEquals(401, enviar("GET", "/api/session", "respinoza@induztek.ejemplo:1234", null).statusCode());
        assertEquals(201, enviar("POST", "/api/registro", null, registro).statusCode());
        assertEquals(409, enviar("POST", "/api/registro", null, registro).statusCode());
        assertEquals(401, enviar("GET", "/api/session", credencial, null).statusCode());
        var lista = com.google.gson.JsonParser.parseString(enviar("GET", "/api/cuentas/clientes", JEFE, null).body());
        var cuenta = lista.getAsJsonArray().asList().stream().map(e -> e.getAsJsonObject())
            .filter(e -> correo.equals(Json.s(e, "correo"))).findFirst().orElseThrow();
        assertFalse(cuenta.get("activo").getAsBoolean());
        assertFalse(cuenta.has("clienteId"));
        String estado = "/api/cuentas/clientes/" + Json.s(cuenta, "id") + "/estado";
        assertEquals(403, enviar("PATCH", estado, TECNICO, "{\"activo\":true,\"clienteId\":\"empresa\"}").statusCode());
        assertEquals(422, enviar("PATCH", estado, JEFE, "{\"activo\":true}").statusCode());
        assertEquals(422, enviar("PATCH", estado, JEFE, "{\"activo\":true,\"clienteId\":\"ausente\"}").statusCode());
        assertEquals(204, enviar("PATCH", estado, JEFE, "{\"activo\":true,\"clienteId\":\"empresa\"}").statusCode());
        var sesion = enviar("GET", "/api/session", credencial, null);
        assertEquals(200, sesion.statusCode());
        assertEquals("CLIENTE", Json.s(Json.obj(sesion.body()), "rol"));
        assertEquals("empresa", Json.s(Json.obj(sesion.body()), "clienteId"));
    }

    @Test void jefeCreaYModificaRolesYElDirectorioReflejaElCambio() throws Exception {
        String correo = "personal.prueba@induztek.ejemplo";
        String credencial = correo + ":ClavePersonal-91";
        String alta = Json.dato("nombre", "Personal de prueba", "correo", correo, "rut", "22.222.222-2",
            "clave", "ClavePersonal-91", "rol", "TECNICO").toString();
        assertEquals(403, enviar("POST", "/api/cuentas", TECNICO, alta).statusCode());
        var creada = enviar("POST", "/api/cuentas", JEFE, alta);
        assertEquals(201, creada.statusCode());
        String id = Json.s(Json.obj(creada.body()), "id");
        assertEquals("TECNICO", Json.s(Json.obj(enviar("GET", "/api/session", credencial, null).body()), "rol"));
        String cambios = "{\"rol\":\"INGENIERO\",\"activo\":true}";
        assertEquals(403, enviar("PATCH", "/api/cuentas/jefe1/permisos", JEFE, cambios).statusCode());
        assertEquals(200, enviar("PATCH", "/api/cuentas/" + id + "/permisos", JEFE, cambios).statusCode());
        var directorio = enviar("GET", "/api/personal", TECNICO, null);
        assertEquals(200, directorio.statusCode());
        assertFalse(directorio.body().contains(correo));
        assertTrue(directorio.body().contains("\"rol\":\"INGENIERO\""));
        assertEquals("INGENIERO", Json.s(Json.obj(enviar("GET", "/api/session", credencial, null).body()), "rol"));
        assertEquals(200, enviar("PATCH", "/api/cuentas/" + id + "/permisos", JEFE,
            "{\"rol\":\"INGENIERO\",\"activo\":false}").statusCode());
        assertEquals(401, enviar("GET", "/api/session", credencial, null).statusCode());
    }

    @Test void ingresoIdempotenteYFotoPrivadaPorCuenta() throws Exception {
        String id = UUID.randomUUID().toString();
        String ingreso = Json.dato("id", id).toString();
        assertEquals(401, enviar("POST", "/api/ingresos", null, ingreso).statusCode());
        assertEquals(204, enviar("POST", "/api/ingresos", JEFE, ingreso).statusCode());
        assertEquals(204, enviar("POST", "/api/ingresos", JEFE, ingreso).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ingresos WHERE usuario_id='jefe1' AND id=?", Integer.class, id));

        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "JPEG", jpeg);
        assertEquals(401, enviarFoto("PUT", null, jpeg.toByteArray()).statusCode());
        assertEquals(422, enviarFoto("PUT", JEFE, new byte[] { 1, 2, 3 }).statusCode());
        assertEquals(413, enviarFoto("PUT", JEFE, new byte[512 * 1024 + 1]).statusCode());
        assertEquals(204, enviarFoto("PUT", JEFE, jpeg.toByteArray()).statusCode());
        assertArrayEquals(jpeg.toByteArray(), enviarFoto("GET", JEFE, null).body());
        assertEquals(204, enviarFoto("GET", TECNICO, null).statusCode());
    }

    private HttpResponse<byte[]> enviarFoto(String metodo, String credencial, byte[] foto) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/perfil/foto"));
        if (credencial != null) req.header("Authorization", "Basic " +
            Base64.getEncoder().encodeToString(credencial.getBytes(StandardCharsets.UTF_8)));
        if (foto != null) req.header("Content-Type", "image/jpeg");
        return http.send(req.method(metodo, foto == null ? HttpRequest.BodyPublishers.noBody() :
            HttpRequest.BodyPublishers.ofByteArray(foto)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String credencial, String json) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta));
        if (credencial != null) req.header("Authorization", "Basic " +
            Base64.getEncoder().encodeToString(credencial.getBytes(StandardCharsets.UTF_8)));
        if (json != null) req.header("Content-Type", "application/json");
        return http.send(req.method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() :
            HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer catalogo() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/clientes", intercambio -> {
                try (intercambio) {
                    intercambio.sendResponseHeaders(intercambio.getRequestURI().getPath().equals("/api/clientes/empresa") ? 200 : 404, -1);
                }
            });
            servidor.start();
            return servidor;
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
