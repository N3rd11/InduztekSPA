package cl.induztek.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Seguridad {
    @Bean UserDetailsService usuarios() {
        var encoder = new BCryptPasswordEncoder();
        var cuentas = Usuarios.TODOS.stream().map(u -> User.withUsername(u.correo())
            .password("{bcrypt}" + encoder.encode("1234")).roles(u.rol()).build()).toList();
        return new InMemoryUserDetailsManager(cuentas);
    }
    @Bean SecurityFilterChain filtro(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults()).build();
    }
}
