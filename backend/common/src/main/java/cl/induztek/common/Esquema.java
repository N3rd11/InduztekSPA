package cl.induztek.common;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

public final class Esquema {
    private Esquema() {}

    public static boolean mysql(JdbcTemplate jdbc) {
        return Objects.requireNonNull(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
            conexion -> conexion.getMetaData().getDatabaseProductName().equals("MySQL")));
    }

    public static String documentos(boolean mysql) {
        return """
            CREATE TABLE IF NOT EXISTS documentos (
                coleccion VARCHAR(50) NOT NULL, id VARCHAR(150) NOT NULL, contenido %s NOT NULL,
                PRIMARY KEY (coleccion,id)
            )
            """.formatted(mysql ? "LONGTEXT" : "CLOB");
    }

    public static String eventos(boolean mysql) {
        return """
            CREATE TABLE IF NOT EXISTS eventos (
                id VARCHAR(150) PRIMARY KEY, usuario VARCHAR(80) NOT NULL, contenido %s NOT NULL
            )
            """.formatted(mysql ? "LONGTEXT" : "CLOB");
    }

    public static final String CUENTAS = """
        CREATE TABLE IF NOT EXISTS cuentas (
            id VARCHAR(80) PRIMARY KEY, nombre VARCHAR(120) NOT NULL,
            correo VARCHAR(120) NOT NULL UNIQUE, rut VARCHAR(12) UNIQUE,
            rol VARCHAR(20) NOT NULL, cliente_id VARCHAR(150),
            activo BOOLEAN NOT NULL, hash VARCHAR(100) NOT NULL
        )
        """;
}
