package cl.induztek.common;

import com.google.gson.JsonObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Persistencia duradera por servicio. Las relaciones y revisiones se validan en la capa de dominio. */
@Repository
public class Documentos {
    private final JdbcTemplate jdbc;
    public Documentos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("CREATE TABLE IF NOT EXISTS documentos (coleccion VARCHAR(50) NOT NULL, id VARCHAR(150) NOT NULL, contenido CLOB NOT NULL, PRIMARY KEY (coleccion,id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS eventos (id VARCHAR(150) PRIMARY KEY, usuario VARCHAR(80) NOT NULL, contenido CLOB NOT NULL)");
        // Compatibilidad con claves de medición del primer conjunto semilla del MVP.
        var antiguas = jdbc.query("SELECT id,contenido FROM documentos WHERE coleccion='mediciones' AND id LIKE ':%'", (rs,n) -> List.of(rs.getString(1),rs.getString(2)));
        antiguas.forEach(fila -> {
            String correcta = fila.get(0).substring(1);
            if (buscar("mediciones",correcta)==null) guardar("mediciones",correcta,Json.obj(fila.get(1)));
            quitar("mediciones",fila.get(0));
        });
    }
    public List<JsonObject> todos(String coleccion) {
        return jdbc.query("SELECT contenido FROM documentos WHERE coleccion=? ORDER BY id", (rs,n) -> Json.obj(rs.getString(1)), coleccion);
    }
    public JsonObject buscar(String coleccion, String id) {
        return jdbc.query("SELECT contenido FROM documentos WHERE coleccion=? AND id=?", (rs,n) -> Json.obj(rs.getString(1)), coleccion,id).stream().findFirst().orElse(null);
    }
    public JsonObject obtener(String coleccion, String id) {
        var o = buscar(coleccion,id);
        if (o == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe " + coleccion + ": " + id);
        return o;
    }
    public void guardar(String coleccion, String id, JsonObject contenido) {
        jdbc.update("MERGE INTO documentos (coleccion,id,contenido) KEY(coleccion,id) VALUES (?,?,?)", coleccion,id,contenido.toString());
    }
    public void quitar(String coleccion, String id) { jdbc.update("DELETE FROM documentos WHERE coleccion=? AND id=?", coleccion,id); }
    public boolean repetido(JsonObject evento, Usuarios.Usuario usuario) {
        String id = Json.s(evento,"id");
        Json.exigir(!id.isBlank() && Json.s(evento,"usuarioId").equals(usuario.id()), "Evento sin ID o autor inválido");
        var existentes = jdbc.query("SELECT usuario,contenido FROM eventos WHERE id=?", (rs,n) -> List.of(rs.getString(1),rs.getString(2)), id);
        if (existentes.isEmpty()) return false;
        if (!existentes.get(0).get(0).equals(usuario.id()) || !existentes.get(0).get(1).equals(evento.toString()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ID de evento reutilizado con otro contenido");
        return true;
    }
    public void confirmar(JsonObject evento, Usuarios.Usuario usuario) {
        jdbc.update("INSERT INTO eventos(id,usuario,contenido) VALUES (?,?,?)", Json.s(evento,"id"), usuario.id(), evento.toString());
    }
    public void revision(JsonObject p, long esperada) {
        if (Json.l(p,"revision") != esperada) throw new ResponseStatusException(HttpStatus.CONFLICT, "El protocolo tiene cambios remotos; se conserva la copia local para resolver el conflicto");
    }
}
