package cl.induztek.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DocumentacionApi {
    private static final String AUTENTICACION = "cuentaInduztek";
    private static final Set<String> LISTAS = Set.of("/api/personal", "/api/cuentas", "/api/cuentas/clientes");

    @Bean OpenAPI apiInduztek(@Value("${induztek.openapi.titulo}") String titulo) {
        return new OpenAPI().info(new Info().title(titulo).version("0.1.0"))
            .schemaRequirement(AUTENTICACION, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
            .addSecurityItem(new SecurityRequirement().addList(AUTENTICACION));
    }

    @Bean OpenApiCustomizer contratosJson() {
        return api -> {
            var rutas = api.getPaths();
            // Los controladores serializan algunos cuerpos con Gson antes de devolverlos.
            rutas.forEach((ruta, recurso) -> recurso.readOperations().forEach(operacion -> {
                if (operacion.getParameters() != null) {
                    operacion.getParameters().removeIf(p -> "header".equals(p.getIn()) &&
                        "Authorization".equalsIgnoreCase(p.getName()));
                }
                operacion.getResponses().values().forEach(respuesta -> {
                    if (respuesta.getContent() == null) return;
                    var json = respuesta.getContent().get("application/json");
                    if (json != null && json.getSchema() instanceof StringSchema) {
                        boolean lista = LISTAS.contains(ruta) && operacion == recurso.getGet();
                        json.setSchema(lista ? new ArraySchema().items(new ObjectSchema()) : new ObjectSchema());
                    }
                });
            }));
            var registro = rutas.get("/api/registro");
            if (registro != null) registro.getPost().setSecurity(List.of());

            var eventos = rutas.get("/api/events");
            if (eventos != null) {
                var evento = new ObjectSchema()
                    .addProperty("id", new StringSchema().description("Identificador idempotente del evento"))
                    .addProperty("usuarioId", new StringSchema().description("ID devuelto por el servicio Usuarios"))
                    .addProperty("tipo", new StringSchema())
                    .addProperty("entidadId", new StringSchema())
                    .addProperty("payload", new StringSchema().description("Datos de la operación en JSON serializado"))
                    .addProperty("fecha", new IntegerSchema().format("int64").description("Fecha en milisegundos Unix"))
                    .required(List.of("id", "usuarioId", "tipo", "payload"));
                eventos.getPost().setRequestBody(new RequestBody().required(true).content(new Content()
                    .addMediaType("application/json", new MediaType().schema(evento))));
            }
            var foto = rutas.get("/api/perfil/foto");
            if (foto != null) {
                foto.getPut().setRequestBody(new RequestBody().required(true).content(new Content()
                    .addMediaType("image/jpeg", new MediaType().schema(new StringSchema().format("binary")))));
            }
        };
    }
}
