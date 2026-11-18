package ec.edu.epn.invernaderos.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Conexion {
    private Conexion() {}

    public static Connection abrir() throws SQLException {
        String host = variable("DB_HOST", "127.0.0.1");
        String puerto = variable("DB_PORT", "5432");
        String base = variable("POSTGRES_DB", "monitoreo_inteligente_cultivos");
        Properties credenciales = new Properties();
        credenciales.setProperty("user", variable("POSTGRES_USER", "invernadero"));
        credenciales.setProperty("password", variable("POSTGRES_PASSWORD", "invernadero_local"));
        return DriverManager.getConnection("jdbc:postgresql://" + host + ":" + puerto + "/" + base, credenciales);
    }

    private static String variable(String nombre, String defecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isEmpty() ? defecto : valor;
    }
}
