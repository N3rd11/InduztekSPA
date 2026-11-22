package cl.induztek.protocol;

import cl.induztek.common.Json;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PersonalGateway {
    private final RestClient acceso;

    public PersonalGateway(@Value("${induztek.access-url}") String url) {
        var peticiones = new SimpleClientHttpRequestFactory();
        peticiones.setConnectTimeout(2000);
        peticiones.setReadTimeout(4000);
        acceso = RestClient.builder().baseUrl(url).requestFactory(peticiones).build();
    }

    public Map<String, String> rolesActivos(String autorizacion) {
        try {
            String cuerpo = acceso.get().uri("/api/personal").header("Authorization", autorizacion)
                .retrieve().body(String.class);
            Map<String, String> roles = new HashMap<>();
            JsonParser.parseString(cuerpo).getAsJsonArray().forEach(fila -> {
                var cuenta = fila.getAsJsonObject();
                if (Json.b(cuenta, "activo")) roles.put(Json.s(cuenta, "id"), Json.s(cuenta, "rol"));
            });
            return roles;
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Usuarios no disponible", error);
        }
    }
}
