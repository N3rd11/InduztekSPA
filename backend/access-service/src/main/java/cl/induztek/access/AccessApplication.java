package cl.induztek.access;

import cl.induztek.common.Errores;
import cl.induztek.common.Seguridad;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({Seguridad.class, Errores.class})
public class AccessApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccessApplication.class, args);
    }
}
