package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.modelo.Medicion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimuladorSensoresTest {
    @Test
    void lecturaIndividualRequiereTerrenoYTipoDeSensorActivos() throws Exception {
        var lectura = new Medicion("SEN-TEMP-A1", "MED_TEMP_AMB", LocalDateTime.now(), BigDecimal.TEN);
        try (var conexion = Conexion.abrir(); var sql = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                var punto = conexion.setSavepoint();
                assertEquals(1, sql.executeUpdate("UPDATE catalogo SET estado = false WHERE codigo = 'SEN_TEMP'"));
                assertThrows(IllegalArgumentException.class,
                    () -> Monitoreo.registrarEnTransaccion(conexion, lectura, "MANUAL"));
                conexion.rollback(punto);
                assertEquals(1, sql.executeUpdate("""
                    UPDATE terreno SET estado = false WHERE id_terreno = (
                        SELECT id_terreno FROM invernadero JOIN sensor USING (id_invernadero) WHERE codigo = 'SEN-TEMP-A1'
                    )
                    """));
                assertThrows(IllegalArgumentException.class,
                    () -> Monitoreo.registrarEnTransaccion(conexion, lectura, "MANUAL"));
            } finally {
                conexion.rollback();
            }
        }
    }

    @Test
    void unaMagnitudInactivaPermiteMedirLosDemasSensores() throws Exception {
        try (var conexion = Conexion.abrir(); var sql = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                int invernadero;
                try (var fila = sql.executeQuery("SELECT id_invernadero FROM sensor WHERE codigo = 'SEN-TEMP-A1'")) {
                    fila.next();
                    invernadero = fila.getInt(1);
                }
                assertEquals(1, sql.executeUpdate("UPDATE catalogo SET estado = false WHERE codigo = 'MED_PH'"));
                var simulador = new SimuladorSensores();
                assertEquals(2, simulador.registrarLote(conexion, invernadero, false).mediciones());
                assertEquals(0, simulador.registrarLote(conexion, invernadero, true).mediciones());
            } finally {
                conexion.rollback();
            }
        }
    }

    @Test
    void unaHoraDesdeLaUltimaLecturaInclusoSiFueManual() throws Exception {
        try (var conexion = Conexion.abrir(); var sql = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                int invernadero;
                try (var fila = sql.executeQuery("""
                    INSERT INTO invernadero (id_terreno, nombre, largo_m, ancho_m, fecha_instalacion)
                    SELECT min(id_terreno), 'Prueba de horario', 10, 10, DATE '2020-01-01' FROM terreno
                    RETURNING id_invernadero
                    """)) {
                    fila.next();
                    invernadero = fila.getInt(1);
                }
                try (var alta = conexion.prepareStatement("""
                    INSERT INTO sensor (id_invernadero, id_tipo_sensor, codigo, ubicacion, fecha_instalacion)
                    SELECT ?, id_catalogo, ?, 'Prueba', DATE '2020-01-01' FROM catalogo WHERE codigo='SEN_TEMP'
                    """)) {
                    alta.setInt(1, invernadero);
                    alta.setString(2, UUID.randomUUID().toString());
                    assertEquals(1, alta.executeUpdate());
                }
                var simulador = new SimuladorSensores();
                assertEquals(1, simulador.registrarLote(conexion, invernadero, false).mediciones());
                assertEquals(0, simulador.registrarLote(conexion, invernadero, true).mediciones());

                try (var vencer = conexion.prepareStatement("""
                    UPDATE medicion SET fecha_hora = fecha_hora - interval '61 minutes'
                    WHERE id_sensor IN (SELECT id_sensor FROM sensor WHERE id_invernadero = ?)
                    """)) {
                    vencer.setInt(1, invernadero);
                    assertEquals(1, vencer.executeUpdate());
                }
                assertEquals(1, simulador.registrarLote(conexion, invernadero, true).mediciones());
                assertEquals(0, simulador.registrarLote(conexion, invernadero, true).mediciones());
            } finally {
                conexion.rollback();
            }
        }
    }

    @Test
    void lasLecturasSimuladasSonRealistas() {
        var simulador = new SimuladorSensores(new Random(7));
        BigDecimal temperatura = null, humedad = null, ph = null;
        int calurosas = 0, pasos = 24 * 30;
        var hora = LocalDateTime.of(2026, 11, 1, 0, 0);
        for (int i = 0; i < pasos; i++, hora = hora.plusHours(1)) {
            temperatura = simulador.simular("MED_TEMP_AMB", temperatura, hora);
            humedad = simulador.simular("MED_HUM_SUELO", humedad, hora);
            ph = simulador.simular("MED_PH", ph, hora);
            if (temperatura.doubleValue() > 30) calurosas++;
            assertTrue(temperatura.doubleValue() > 5 && temperatura.doubleValue() < 38, "temperatura " + temperatura);
            assertTrue(humedad.doubleValue() >= 0 && humedad.doubleValue() <= 100, "humedad " + humedad);
            assertTrue(ph.doubleValue() > 5 && ph.doubleValue() < 7.6, "pH " + ph);
        }
        // Algunas horas de calor al mediodía, no una alerta en cada ronda.
        assertTrue(calurosas > 0 && calurosas < pasos / 10, "horas sobre 30 °C: " + calurosas);
    }
}
