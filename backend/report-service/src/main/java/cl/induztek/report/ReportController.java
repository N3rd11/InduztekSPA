package cl.induztek.report;

import cl.induztek.common.Json;
import cl.induztek.common.Usuarios;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ReportController {
    private final RestClient protocolos;

    ReportController(@Value("${induztek.protocol-url}") String url) {
        var peticiones = new SimpleClientHttpRequestFactory();
        peticiones.setConnectTimeout(2500);
        peticiones.setReadTimeout(5000);
        protocolos = RestClient.builder().baseUrl(url).requestFactory(peticiones).build();
    }

    @GetMapping(value = "/api/servicios/actas", produces = "application/json")
    String sesion(Authentication autenticacion) {
        return Json.GSON.toJson(Usuarios.actual(autenticacion));
    }

    @GetMapping(value = "/api/actas/{visitaId}", produces = "application/json")
    String acta(@PathVariable String visitaId, @RequestHeader("Authorization") String autorizacion) {
        try {
            var acta = Json.obj(protocolos.get().uri("/api/actas/{visitaId}", visitaId)
                .header("Authorization", autorizacion).retrieve().body(String.class));
            acta.addProperty("sha256", huella(Json.s(acta, "texto")));
            return acta.toString();
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no disponible");
            }
            if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso al acta denegado");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Servicio de protocolos no disponible");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Servicio de protocolos no disponible");
        }
    }

    private String huella(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
