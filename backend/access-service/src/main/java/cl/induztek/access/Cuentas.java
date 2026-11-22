package cl.induztek.access;

import cl.induztek.common.Esquema;
import cl.induztek.common.Json;
import cl.induztek.common.Seguridad;
import cl.induztek.common.Usuarios.Usuario;
import cl.induztek.common.Usuarios;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class Cuentas implements Seguridad.Verificador {
    record Alta(String nombre, String correo, String rut, String clave, String clienteId) {}
    record Persona(String id, String nombre, String rol, boolean activo) {}
    private record Credencial(Usuario usuario, String hash) {}

    private static final Pattern CORREO = Pattern.compile("[^\\s@.]+(?:\\.[^\\s@.]+)*@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+");
    private static final RowMapper<Usuario> PERFIL = (rs, fila) -> new Usuario(rs.getString("id"),
        rs.getString("nombre"), rs.getString("correo"), rs.getString("rol"), rs.getString("cliente_id"),
        rs.getString("rut"), rs.getBoolean("activo"));
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final boolean demo;
    private final Set<String> semillasInhabilitadas = new HashSet<>();

    Cuentas(JdbcTemplate jdbc, Environment entorno, @Value("${induztek.demo:false}") boolean demo) {
        this.jdbc = jdbc;
        this.demo = demo;
        jdbc.execute(Esquema.CUENTAS);
        var rutsDemo = List.of("172845193", "159063348", "138719057", "142267918", "194120877",
            "261045834", "201376483", "165532708", "183904523", "126453086");
        for (int i = 0; i < Usuarios.TODOS.size(); i++) {
            var cuenta = Usuarios.TODOS.get(i);
            String clave = cuenta.interno() ? entorno.getProperty("INDUZTEK_CLAVE_" + cuenta.id().toUpperCase(Locale.ROOT)) : null;
            if (clave != null) {
                if (!claveValida(clave)) throw new IllegalArgumentException("Contraseña inicial inválida para " + cuenta.id());
                String hash = bcrypt.encode(clave);
                int actualizadas = jdbc.update("""
                    UPDATE cuentas SET nombre=?,correo=?,rut=?,rol=?,cliente_id=?,activo=TRUE,hash=? WHERE id=?
                    """, cuenta.nombre(), cuenta.correo(), rutsDemo.get(i), cuenta.rol(), cuenta.clienteId(), hash, cuenta.id());
                if (actualizadas == 0) {
                    jdbc.update("""
                        INSERT INTO cuentas(id,nombre,correo,rut,rol,cliente_id,activo,hash) VALUES (?,?,?,?,?,?,TRUE,?)
                        """, cuenta.id(), cuenta.nombre(), cuenta.correo(), rutsDemo.get(i), cuenta.rol(), cuenta.clienteId(), hash);
                }
            } else if (demo) {
                jdbc.update("""
                    INSERT INTO cuentas(id,nombre,correo,rut,rol,cliente_id,activo,hash)
                    SELECT ?,?,?,?,?,?,TRUE,? WHERE NOT EXISTS (SELECT 1 FROM cuentas WHERE id=?)
                    """, cuenta.id(), cuenta.nombre(), cuenta.correo(), rutsDemo.get(i), cuenta.rol(), cuenta.clienteId(),
                    bcrypt.encode("1234"), cuenta.id());
            }
        }
        if (!demo) {
            jdbc.queryForList("SELECT id, hash FROM cuentas").forEach(fila -> {
                String id = (String) fila.get("id");
                if (Usuarios.TODOS.stream().anyMatch(u -> u.id().equals(id)) && bcrypt.matches("1234", (String) fila.get("hash"))) {
                    semillasInhabilitadas.add(id);
                }
            });
        }
    }

    @Override public Usuario autenticar(String correo, String clave) {
        if (clave.getBytes(StandardCharsets.UTF_8).length > 72) return null;
        var fila = jdbc.query("SELECT * FROM cuentas WHERE correo=?", (rs, n) ->
            new Credencial(PERFIL.mapRow(rs, n), rs.getString("hash")), normalizarCorreo(correo)).stream().findFirst().orElse(null);
        // Las bases anteriores pueden conservar cuentas con la contraseña de demostración.
        if (!demo && "1234".equals(clave)) return null;
        return fila != null && fila.usuario.activo() && bcrypt.matches(clave, fila.hash) ? fila.usuario : null;
    }

    List<Usuario> clientes() {
        return listar().stream().filter(u -> !u.interno()).toList();
    }

    List<Usuario> listar() {
        return jdbc.query("SELECT * FROM cuentas ORDER BY nombre, id", PERFIL).stream()
            .filter(u -> !semillasInhabilitadas.contains(u.id())).toList();
    }

    List<Persona> personal() {
        return listar().stream().filter(Usuario::interno)
            .map(u -> new Persona(u.id(), u.nombre(), u.rol(), u.activo())).toList();
    }

    void validarDatos(Alta alta) {
        String correo = normalizarCorreo(alta.correo());
        String nombre = limpio(alta.nombre());
        Json.exigir(!nombre.isEmpty() && nombre.length() <= 120, "Nombre obligatorio (máximo 120 caracteres)");
        Json.exigir(correo.length() <= 120 && correo.indexOf(':') < 0 &&
            correo.chars().noneMatch(Character::isISOControl) && CORREO.matcher(correo).matches(), "Correo inválido");
        Json.exigir(Json.rut(limpio(alta.rut())), "RUT inválido");
        Json.exigir(claveValida(alta.clave()),
            "La contraseña debe tener al menos 8 caracteres y un máximo de 72 bytes");
    }

    void validarAlta(Alta alta) {
        validarDatos(alta);
        Json.exigir(!limpio(alta.clienteId()).isEmpty() && alta.clienteId().length() <= 150, "Selecciona una empresa existente");
    }

    Usuario crear(Alta alta) {
        return crear(alta, "CLIENTE", limpio(alta.clienteId()));
    }

    Usuario crear(Alta alta, String rol, String empresa) {
        return guardar(alta, rol, empresa, true);
    }

    void registrar(Alta alta) {
        validarDatos(alta);
        guardar(alta, "CLIENTE", null, false);
    }

    private Usuario guardar(Alta alta, String rol, String empresa, boolean activo) {
        String rut = limpio(alta.rut()).replace(".", "").replace("-", "").toUpperCase(Locale.ROOT);
        var cuenta = new Usuario(UUID.randomUUID().toString(), limpio(alta.nombre()), normalizarCorreo(alta.correo()),
            rol, empresa, rut, activo);
        try {
            jdbc.update("INSERT INTO cuentas(id,nombre,correo,rut,rol,cliente_id,activo,hash) VALUES (?,?,?,?,?,?,?,?)",
                cuenta.id(), cuenta.nombre(), cuenta.correo(), cuenta.rut(), cuenta.rol(), cuenta.clienteId(), activo, bcrypt.encode(alta.clave()));
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo o RUT ya está registrado");
        }
        return cuenta;
    }

    synchronized Usuario modificar(String administrador, String id, String rol, String empresa, boolean activo) {
        Json.permiso(!administrador.equals(id) && Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM cuentas WHERE id=? AND rol='JEFE_TECNICO' AND activo=TRUE)
            """, Boolean.class, administrador)));
        var actual = jdbc.query("SELECT * FROM cuentas WHERE id=?", PERFIL, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        int actualizadas = jdbc.update("""
            UPDATE cuentas SET rol=?, cliente_id=?, activo=? WHERE id=? AND rol=? AND activo=?
            AND (cliente_id=? OR (cliente_id IS NULL AND ? IS NULL))
            """, rol, empresa, activo, id, actual.rol(), actual.activo(), actual.clienteId(), actual.clienteId());
        if (actualizadas == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta cambió. Actualiza la lista.");
        return new Usuario(id, actual.nombre(), actual.correo(), rol, empresa, actual.rut(), activo);
    }

    void estado(String id, boolean activo, String empresa) {
        var cuenta = jdbc.query("SELECT * FROM cuentas WHERE id=? AND rol='CLIENTE'", PERFIL, id).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta cliente no encontrada"));
        Json.exigir(empresa == null || cuenta.clienteId() == null || empresa.equals(cuenta.clienteId()),
            "La cuenta ya está vinculada a una empresa");
        Json.exigir(!activo || cuenta.clienteId() != null || empresa != null, "Asigna una empresa para activar la cuenta");
        int actualizadas = jdbc.update("""
            UPDATE cuentas SET activo=?, cliente_id=COALESCE(cliente_id, ?) WHERE id=?
            AND (cliente_id=? OR (cliente_id IS NULL AND ? IS NULL))
            """, activo, empresa, id, cuenta.clienteId(), cuenta.clienteId());
        if (actualizadas == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "La asignación cambió. Actualiza la lista.");
    }

    private static boolean claveValida(String clave) {
        return clave != null && !clave.isBlank() && clave.length() >= 8 && clave.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    private static String normalizarCorreo(String correo) { return limpio(correo).toLowerCase(Locale.ROOT); }
    private static String limpio(String valor) { return valor == null ? "" : valor.strip(); }
}
