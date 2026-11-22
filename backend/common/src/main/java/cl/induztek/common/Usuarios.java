package cl.induztek.common;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class Usuarios {
    public record Usuario(String id, String nombre, String correo, String rol, String clienteId, String rut, boolean activo) {
        public Usuario(String id, String nombre, String correo, String rol, String clienteId) {
            this(id, nombre, correo, rol, clienteId, "", true);
        }
        public boolean interno() { return !rol.equals("CLIENTE"); }
        public boolean revisor() { return rol.equals("INGENIERO") || rol.equals("JEFE_TECNICO"); }
    }
    public static final List<Usuario> TODOS = List.of(
        new Usuario("tecnico1", "Matías Godoy Herrera", "mgodoy@induztek.ejemplo", "TECNICO", null),
        new Usuario("ingeniero1", "Andrea Contreras Silva", "acontreras@induztek.ejemplo", "INGENIERO", null),
        new Usuario("jefe1", "Ricardo Espinoza Lagos", "respinoza@induztek.ejemplo", "JEFE_TECNICO", null),
        new Usuario("cliente1", "Marcela Fuentes Rojas", "mfuentes@envpacifico.ejemplo", "CLIENTE", "cliente-pacifico"),
        new Usuario("tecnico2", "Javiera Tapia Muñoz", "jtapia@induztek.ejemplo", "TECNICO", null),
        new Usuario("tecnico3", "Carlos Rondón Pérez", "crondon@induztek.ejemplo", "TECNICO", null),
        new Usuario("tecnico4", "Diego Valenzuela Ríos", "dvalenzuela@induztek.ejemplo", "TECNICO", null),
        new Usuario("ingeniero2", "Sebastián Morales Gutiérrez", "smorales@induztek.ejemplo", "INGENIERO", null),
        new Usuario("cliente2", "Tomás Leiva Araya", "tleiva@losaromos.ejemplo", "CLIENTE", "cliente-clinica"),
        new Usuario("cliente3", "Patricio Navarro Soto", "pnavarro@cerroalto.ejemplo", "CLIENTE", "cliente-minera")
    );
    public static Usuario actual(Authentication auth) {
        if (auth.getPrincipal() instanceof Usuario usuario) return usuario;
        return TODOS.stream().filter(u -> u.correo().equalsIgnoreCase(auth.getName())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    private Usuarios() {}
}
