package cl.induztek.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
@Import(DocumentacionApi.class)
public class Seguridad {
    public interface Verificador {
        Usuarios.Usuario autenticar(String correo, String clave);
    }
    private Verificador demo() {
        var encoder = new BCryptPasswordEncoder();
        var hash = encoder.encode("1234");
        return (correo, clave) -> clave.getBytes(StandardCharsets.UTF_8).length <= 72 && encoder.matches(clave, hash)
            ? Usuarios.TODOS.stream().filter(u -> u.correo().equalsIgnoreCase(correo)).findFirst().orElse(null)
            : null;
    }
    @Bean SecurityFilterChain filtro(HttpSecurity http, ObjectProvider<Verificador> local,
                                     @Value("${induztek.access-url:}") String url,
                                     @Value("${induztek.demo:false}") boolean demo) throws Exception {
        Verificador verificador = local.getIfAvailable();
        if (verificador == null) verificador = !url.isBlank() ? remoto(url) : demo ? demo() : (correo, clave) -> null;
        var origen = verificador;
        http.authenticationManager(solicitud -> {
            var usuario = origen.autenticar(solicitud.getName(), String.valueOf(solicitud.getCredentials()));
            if (usuario == null || !usuario.activo()) throw new BadCredentialsException("Credenciales inválidas");
            return UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())));
        });
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> {
                a.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll();
                if (local.getIfAvailable() != null) a.requestMatchers(HttpMethod.POST, "/api/registro").permitAll();
                a.anyRequest().authenticated();
            })
            .httpBasic(Customizer.withDefaults()).build();
    }

    private Verificador remoto(String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(4000);
        var cliente = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        return (correo, clave) -> {
            try {
                var cuerpo = cliente.get().uri("/api/session")
                    .headers(h -> h.setBasicAuth(correo, clave, StandardCharsets.UTF_8))
                    .retrieve().body(String.class);
                return Json.GSON.fromJson(cuerpo, Usuarios.Usuario.class);
            } catch (RestClientResponseException error) {
                if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403) return null;
                throw new AuthenticationServiceException("Servicio de usuarios no disponible", error);
            } catch (RestClientException error) {
                throw new AuthenticationServiceException("Servicio de usuarios no disponible", error);
            }
        };
    }
}
