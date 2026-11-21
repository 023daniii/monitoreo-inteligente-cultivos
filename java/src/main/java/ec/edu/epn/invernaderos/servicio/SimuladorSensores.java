package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.modelo.Medicion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simula lecturas realistas. Las magnitudes de cada sensor salen del catálogo (hijas de su tipo de sensor);
 * el valor nuevo parte de la lectura anterior y sigue un ciclo diario, así que no salta de 18 a 36 °C.
 */
public final class SimuladorSensores {
    public record Resumen(int mediciones, int alertas) {}

    private final Random azar;

    public SimuladorSensores() {
        this(ThreadLocalRandom.current());
    }

    SimuladorSensores(Random azar) {
        this.azar = azar;
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
            try {
                // Serializa el botón y el medidor automático para este invernadero.
                try (var lock = conexion.prepareStatement("SELECT pg_advisory_xact_lock(73124, ?)")) {
                    lock.setInt(1, idInvernadero);
                    lock.execute();
                }
                var resultado = registrarLote(conexion, idInvernadero, soloPendientes);
                conexion.commit();
                return resultado;
            } catch (SQLException | RuntimeException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** Una lectura por cada sensor y magnitud activos. Con soloPendientes, solo si pasó una hora desde la última. */
    Resumen registrarLote(Connection conexion, int idInvernadero, boolean soloPendientes) throws SQLException {
        String consulta = """
            SELECT s.codigo, mag.codigo AS magnitud, LOCALTIMESTAMP AS ahora, ultima.valor AS anterior
            FROM sensor s
            JOIN invernadero i USING (id_invernadero)
            JOIN terreno t USING (id_terreno)
            JOIN catalogo tipo ON tipo.id_catalogo = s.id_tipo_sensor
            JOIN catalogo mag ON mag.id_padre = s.id_tipo_sensor
            LEFT JOIN LATERAL (
                SELECT m.valor, m.fecha_hora FROM medicion m
                WHERE m.id_sensor = s.id_sensor AND m.id_tipo_medicion = mag.id_catalogo
                ORDER BY m.fecha_hora DESC LIMIT 1
            ) ultima ON true
            WHERE i.id_invernadero = ? AND s.estado AND i.estado AND t.estado AND tipo.estado AND mag.estado
              AND greatest(s.fecha_instalacion, i.fecha_instalacion) <= CURRENT_DATE
              AND (NOT ? OR ultima.fecha_hora IS NULL OR ultima.fecha_hora <= LOCALTIMESTAMP - interval '1 hour')
            ORDER BY s.id_sensor, mag.codigo
            FOR SHARE OF mag
            """;
        int lecturas = 0;
        int alertas = 0;
        try (var sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setInt(1, idInvernadero);
            sentencia.setBoolean(2, soloPendientes);
            try (var filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    var ahora = filas.getObject("ahora", LocalDateTime.class);
                    var valor = simular(filas.getString("magnitud"), filas.getBigDecimal("anterior"), ahora);
                    if (valor == null) continue;
                    var lectura = new Medicion(filas.getString("codigo"), filas.getString("magnitud"), ahora, valor);
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

    /** Valor siguiente según la magnitud. Devuelve null si no hay un perfil de simulación para ella. */
    BigDecimal simular(String magnitud, BigDecimal anterior, LocalDateTime ahora) {
        Double previo = anterior == null ? null : anterior.doubleValue();
        double hora = ahora.getHour() + ahora.getMinute() / 60.0;
        double valor;
        switch (magnitud) {
            case "MED_TEMP_AMB" -> {
                // Ciclo diario de 12 a 28 °C con máximo a las 14:00; ocasionalmente pasa de 30 °C al mediodía.
                double objetivo = 20 + 8 * Math.cos(2 * Math.PI * (hora - 14) / 24);
                valor = previo == null ? objetivo : previo + 0.6 * (objetivo - previo);
                valor += azar.nextGaussian() * 1.3;
            }
            case "MED_HUM_SUELO" -> {
                // El suelo se seca poco a poco y el riego lo recupera cuando baja de 45 %.
                valor = previo == null ? 65 : previo - 1 - azar.nextDouble() * 3;
                if (valor < 45 && azar.nextDouble() < 0.6) valor += 22 + azar.nextDouble() * 8;
            }
            case "MED_PH" -> {
                valor = previo == null ? 6.3 : previo + 0.3 * (6.3 - previo);
                valor += azar.nextGaussian() * 0.15;
            }
            default -> {
                // Magnitud nueva del catálogo sin perfil: varía un 2 % alrededor de su última lectura.
                if (previo == null) return null;
                return BigDecimal.valueOf(previo * (1 + azar.nextGaussian() * 0.02)).setScale(2, RoundingMode.HALF_UP);
            }
        }
        double maximo = switch (magnitud) {
            case "MED_PH" -> 14;
            case "MED_HUM_SUELO" -> 100;
            default -> 70;
        };
        valor = Math.max(magnitud.equals("MED_TEMP_AMB") ? -50 : 0, Math.min(maximo, valor));
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
