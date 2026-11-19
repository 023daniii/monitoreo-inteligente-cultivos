package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.modelo.Medicion;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

public final class SimuladorSensores {
    public record Resumen(int mediciones, int alertas) {}

    private enum Canal {
        TEMPERATURA("SEN_TEMP", "MED_TEMP_AMB", 1800, 3600),
        HUMEDAD("SEN_HUM_SUELO", "MED_HUM_SUELO", 2500, 8500),
        PH("SEN_PH_SUELO", "MED_PH", 480, 780);

        final String tipo;
        final String magnitud;
        final int minimo;
        final int maximo;

        Canal(String tipo, String magnitud, int minimo, int maximo) {
            this.tipo = tipo;
            this.magnitud = magnitud;
            this.minimo = minimo;
            this.maximo = maximo;
        }

        static Canal deTipo(String tipo) {
            for (var canal : values()) {
                if (canal.tipo.equals(tipo)) return canal;
            }
            return null;
        }

        BigDecimal simular() {
            return BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(minimo, maximo + 1), 2);
        }
    }

    public static String magnitudPara(String tipoSensor) {
        var canal = Canal.deTipo(tipoSensor);
        return canal == null ? null : canal.magnitud;
    }

    public Resumen medir(int idInvernadero) throws SQLException {
        return medir(idInvernadero, false);
    }

    Resumen medirPendientes(int idInvernadero) throws SQLException {
        return medir(idInvernadero, true);
    }

    private Resumen medir(int idInvernadero, boolean soloPendientes) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            conexion.setAutoCommit(false);
            // Serializa el botón y los trabajadores concurrentes de este invernadero.
            try (var lock = conexion.prepareStatement("SELECT pg_advisory_xact_lock(73124, ?)")) {
                lock.setInt(1, idInvernadero);
                lock.execute();
            }
            var resultado = registrarLote(conexion, idInvernadero, soloPendientes);
            conexion.commit();
            return resultado;
        }
    }

    Resumen registrarLote(Connection conexion, int idInvernadero, boolean soloPendientes) throws SQLException {
        String sensores = """
            SELECT s.id_sensor, s.codigo, tipo.codigo,
                   clock_timestamp() AT TIME ZONE 'America/Guayaquil' AS ahora
            FROM sensor s
            JOIN invernadero i USING (id_invernadero)
            JOIN terreno t USING (id_terreno)
            JOIN catalogo tipo ON tipo.id_catalogo = s.id_tipo_sensor
            WHERE i.id_invernadero = ? AND s.estado AND i.estado AND t.estado AND tipo.estado
              AND greatest(s.fecha_instalacion, i.fecha_instalacion)
                  <= (clock_timestamp() AT TIME ZONE 'America/Guayaquil')::date
            ORDER BY s.id_sensor
            """;
        int lecturas = 0;
        int alertas = 0;
        try (var consulta = conexion.prepareStatement(sensores)) {
            consulta.setInt(1, idInvernadero);
            try (var filas = consulta.executeQuery()) {
                while (filas.next()) {
                    var canal = Canal.deTipo(filas.getString(3));
                    var ahora = filas.getObject(4, LocalDateTime.class);
                    if (canal == null || !admiteLectura(conexion, filas.getInt(1), canal.magnitud, ahora, soloPendientes)) {
                        continue;
                    }
                    var lectura = new Medicion(filas.getString(2), canal.magnitud, ahora, canal.simular());
                    var resultado = Monitoreo.registrarEnTransaccion(conexion, lectura,
                        soloPendientes ? "AUTOMATICA" : "MANUAL");
                    if (resultado.nueva()) {
                        lecturas++;
                        alertas += resultado.alertas().size();
                    }
                }
            }
        }
        return new Resumen(lecturas, alertas);
    }

    private boolean admiteLectura(Connection conexion, int idSensor, String magnitud,
                                  LocalDateTime ahora, boolean soloPendientes) throws SQLException {
        String sql = """
            SELECT FROM catalogo t
            WHERE t.codigo = ? AND t.estado AND (NOT ? OR NOT EXISTS (
                SELECT FROM medicion m WHERE m.id_sensor = ? AND m.id_tipo_medicion = t.id_catalogo
                  AND m.fecha_hora > ? AND m.fecha_hora <= ?
            ))
            FOR SHARE OF t
            """;
        try (var consulta = conexion.prepareStatement(sql)) {
            consulta.setString(1, magnitud);
            consulta.setBoolean(2, soloPendientes);
            consulta.setInt(3, idSensor);
            consulta.setObject(4, ahora.minusHours(1));
            consulta.setObject(5, ahora);
            try (var filas = consulta.executeQuery()) {
                return filas.next();
            }
        }
    }
}
