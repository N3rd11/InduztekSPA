package cl.induztek.protocol;

import cl.induztek.common.Json;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestCatalogGateway implements CatalogGateway {
    private final RestClient cliente;
    public RestCatalogGateway(@Value("${induztek.catalog-url}") String url) { cliente = RestClient.builder().baseUrl(url).build(); }
    @Override public JsonObject snapshot(String authorization) {
        return Json.obj(cliente.get().uri("/api/snapshot").header("Authorization",authorization).retrieve().body(String.class));
    }
}
