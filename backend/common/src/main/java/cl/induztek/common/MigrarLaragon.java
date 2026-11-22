package cl.induztek.common;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Copia las bases existentes a tres esquemas MySQL vacíos, conservando sus claves y eventos. */
public final class MigrarLaragon {
    private static final String USUARIOS = "induztek_usuarios";
    private static final String CATALOGOS = "induztek_catalogos";
    private static final String PROTOCOLOS = "induztek_protocolos";

    public static void main(String[] args) throws SQLException {
        var origen = Path.of(System.getenv().getOrDefault("INDUZTEK_H2_ORIGEN", args[0]));
        String usuario = Objects.requireNonNull(System.getenv("INDUZTEK_MYSQL_USUARIO"));
        String clave = Objects.requireNonNull(System.getenv("INDUZTEK_MYSQL_CLAVE"));
        try (var mysql = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/" + USUARIOS + "?sslMode=REQUIRED", usuario, clave)) {
            preparar(mysql);
            mysql.setAutoCommit(false);
            try (var consulta = mysql.createStatement();
                 var marca = consulta.executeQuery("SELECT origen FROM migracion_h2 WHERE id=1 FOR UPDATE")) {
                if (marca.next()) {
                    mysql.rollback();
                    System.out.println("La copia desde H2 ya se aplicó.");
                    return;
                }
                migrar(mysql, origen, "usuarios", USUARIOS, List.of(
                    new Tabla("cuentas", "id,nombre,correo,rut,rol,cliente_id,activo,hash")));
                var documentos = List.of(new Tabla("documentos", "coleccion,id,contenido"),
                    new Tabla("eventos", "id,usuario,contenido"));
                migrar(mysql, origen, "catalogos", CATALOGOS, documentos);
                migrar(mysql, origen, "protocolos", PROTOCOLOS, documentos);
                consulta.executeUpdate("INSERT INTO migracion_h2(id,origen) VALUES (1,'H2')");
                mysql.commit();
                System.out.println("Migración a Laragon confirmada.");
            } catch (Exception error) {
                mysql.rollback();
                throw error;
            }
        }
    }

    private static void preparar(Connection mysql) throws SQLException {
        try (var sql = mysql.createStatement()) {
            sql.executeUpdate(Esquema.CUENTAS);
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS migracion_h2 (id INT PRIMARY KEY, origen VARCHAR(16) NOT NULL)");
        }
        for (String esquema : List.of(CATALOGOS, PROTOCOLOS)) {
            mysql.setCatalog(esquema);
            try (var sql = mysql.createStatement()) {
                sql.executeUpdate(Esquema.documentos(true));
                sql.executeUpdate(Esquema.eventos(true));
            }
        }
        mysql.setCatalog(USUARIOS);
    }

    private static void migrar(Connection mysql, Path origen, String archivo, String esquema,
                               List<Tabla> tablas) throws SQLException {
        if (!Files.isRegularFile(origen.resolve(archivo + ".mv.db"))) return;
        String ruta = origen.resolve(archivo).toAbsolutePath().toString().replace('\\', '/');
        try (var h2 = DriverManager.getConnection("jdbc:h2:file:" + ruta + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r", "sa", "")) {
            for (var tabla : tablas) {
                String destino = esquema + "." + tabla.nombre();
                var columnas = tabla.columnas().split(",");
                String parametros = String.join(",", Collections.nCopies(columnas.length, "?"));
                try (var lectura = h2.createStatement();
                     var consulta = mysql.createStatement();
                     var existentes = consulta.executeQuery("SELECT COUNT(*) FROM " + destino);
                     var filas = lectura.executeQuery("SELECT " + tabla.columnas() + " FROM " + tabla.nombre());
                     var guardar = mysql.prepareStatement("INSERT INTO " + destino + "(" + tabla.columnas() + ") VALUES (" + parametros + ")")) {
                    existentes.next();
                    if (existentes.getLong(1) != 0) throw new IllegalStateException("El destino ya tiene datos: " + destino);
                    int copiadas = 0;
                    while (filas.next()) {
                        for (int i = 0; i < columnas.length; i++) {
                            if (columnas[i].equals("activo")) guardar.setBoolean(i + 1, filas.getBoolean(i + 1));
                            else guardar.setString(i + 1, filas.getString(i + 1));
                        }
                        copiadas += guardar.executeUpdate();
                    }
                    System.out.printf("%s: %d filas copiadas%n", destino, copiadas);
                }
            }
        }
    }

    private record Tabla(String nombre, String columnas) {}
}
