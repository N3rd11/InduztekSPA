package cl.induztek.access;

import cl.induztek.common.Esquema;
import cl.induztek.common.Json;
import cl.induztek.common.Usuarios;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
class PerfilController {
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_LADO = 512;
    private final JdbcTemplate jdbc;
    private final String guardarFoto;

    PerfilController(JdbcTemplate jdbc, Cuentas cuentas) {
        this.jdbc = jdbc;
        boolean mysql = Esquema.mysql(jdbc);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS fotos_perfil (
                usuario_id VARCHAR(80) PRIMARY KEY,
                jpeg %s NOT NULL, actualizado_ms BIGINT NOT NULL,
                FOREIGN KEY (usuario_id) REFERENCES cuentas(id)
            )
            """.formatted(mysql ? "MEDIUMBLOB" : "BLOB"));
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ingresos (
                usuario_id VARCHAR(80) NOT NULL,
                id VARCHAR(36) NOT NULL, rol VARCHAR(20) NOT NULL, fecha_ms BIGINT NOT NULL,
                PRIMARY KEY (usuario_id,id),
                FOREIGN KEY (usuario_id) REFERENCES cuentas(id)
            )
            """);
        guardarFoto = mysql
            ? """
                INSERT INTO fotos_perfil(usuario_id,jpeg,actualizado_ms) VALUES (?,?,?) AS nuevo
                ON DUPLICATE KEY UPDATE jpeg=nuevo.jpeg, actualizado_ms=nuevo.actualizado_ms
                """
            : "MERGE INTO fotos_perfil(usuario_id,jpeg,actualizado_ms) KEY(usuario_id) VALUES (?,?,?)";
    }

    record Ingreso(UUID id) {}

    @PostMapping("/api/ingresos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void ingreso(Authentication autenticacion, @RequestBody Ingreso ingreso) {
        Json.exigir(ingreso.id() != null, "Falta el identificador del ingreso");
        var usuario = Usuarios.actual(autenticacion);
        try {
            jdbc.update("INSERT INTO ingresos(usuario_id,id,rol,fecha_ms) VALUES (?,?,?,?)",
                usuario.id(), ingreso.id().toString(), usuario.rol(), System.currentTimeMillis());
        } catch (DuplicateKeyException repetido) {
            // El cliente puede reenviar un ingreso sin duplicarlo.
        }
    }

    @GetMapping(value = "/api/perfil/foto", produces = "image/jpeg")
    ResponseEntity<byte[]> foto(Authentication autenticacion) {
        var fotos = jdbc.query("SELECT jpeg FROM fotos_perfil WHERE usuario_id=?",
            (rs, fila) -> rs.getBytes(1), Usuarios.actual(autenticacion).id());
        return fotos.isEmpty() ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
            : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fotos.get(0));
    }

    @PutMapping(value = "/api/perfil/foto", consumes = "image/jpeg")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subir(Authentication autenticacion, HttpServletRequest solicitud) throws IOException {
        byte[] jpeg = solicitud.getInputStream().readNBytes(MAX_BYTES + 1);
        if (jpeg.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "La foto supera 512 KiB");
        }
        Json.exigir(jpegValido(jpeg), "Sube una foto JPEG de hasta 512 por 512 píxeles");
        jdbc.update(guardarFoto, Usuarios.actual(autenticacion).id(), jpeg, System.currentTimeMillis());
    }

    private static boolean jpegValido(byte[] jpeg) {
        try (var entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg))) {
            var lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) return false;
            var lector = lectores.next();
            try {
                if (!lector.getFormatName().equalsIgnoreCase("JPEG")) return false;
                lector.setInput(entrada);
                return lector.getWidth(0) <= MAX_LADO && lector.getHeight(0) <= MAX_LADO && lector.read(0) != null;
            } finally {
                lector.dispose();
            }
        } catch (IOException invalido) {
            return false;
        }
    }
}
