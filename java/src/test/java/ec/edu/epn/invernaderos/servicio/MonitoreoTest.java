package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.dao.MonitoreoDao;
import ec.edu.epn.invernaderos.modelo.Medicion;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Registro de lecturas y alertas. Todo ocurre en una transacción que se revierte al final. */
class MonitoreoTest {
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 16, 10, 0);

    @Test
    void registraEvaluaEvitaRepetidasYResuelve() throws Exception {
        try (var conexion = Conexion.abrir()) {
            conexion.setAutoCommit(false);
            try {
                assertTrue(registrar(conexion, 0, "25").alertas().isEmpty());
                assertTrue(registrar(conexion, 1, "30").alertas().isEmpty());
                var alta = registrar(conexion, 2, "34.2");
                assertEquals(1, alta.alertas().size());
                var alerta = alta.alertas().get(0);
                assertEquals("SEN-TEMP-A2", alerta.sensor());
                assertEquals("Lote tomate A2", alerta.cultivo());
                assertEquals("Ventilar el invernadero.", alerta.recomendacion());

                var repetida = registrar(conexion, 2, "34.2");
                assertFalse(repetida.nueva());
                assertEquals(alta.idMedicion(), repetida.idMedicion());
                assertThrows(IllegalArgumentException.class, () -> registrar(conexion, 2, "35"));

                // Mientras la alerta sigue pendiente no se repite cada hora; la regla crítica sí se avisa.
                assertTrue(registrar(conexion, 3, "34.5").alertas().isEmpty());
                var critica = registrar(conexion, 4, "36");
                assertEquals(1, critica.alertas().size());
                assertEquals("Crítica", critica.alertas().get(0).severidad());

                var dao = new MonitoreoDao(conexion);
                byte[] foto = {(byte) 0x89, 'P', 'N', 'G'};
                assertTrue(dao.adjuntarEvidencia(alerta.id(), foto, "png"));
                assertArrayEquals(foto, dao.evidencia(alerta.id()));
                assertTrue(dao.resolver(alerta.id()));
                assertFalse(dao.resolver(alerta.id()));
                assertEquals(1, registrar(conexion, 5, "31").alertas().size());
            } finally {
                conexion.rollback();
            }
        }
    }

    @Test
    void rechazaMagnitudAjenaYValoresImposibles() throws Exception {
        try (var conexion = Conexion.abrir()) {
            conexion.setAutoCommit(false);
            try {
                var ph = new Medicion("SEN-TEMP-A2", "MED_PH", INICIO, new BigDecimal("6.5"));
                assertThrows(IllegalArgumentException.class, () -> Monitoreo.registrarEnTransaccion(conexion, ph, "MANUAL"));
                var punto = conexion.setSavepoint();
                var humedad = new Medicion("SEN-HUM-A2", "MED_HUM_SUELO", INICIO, new BigDecimal("150"));
                var error = assertThrows(SQLException.class,
                    () -> Monitoreo.registrarEnTransaccion(conexion, humedad, "MANUAL"));
                assertTrue(error.getMessage().contains("fuera del rango"));
                conexion.rollback(punto);
            } finally {
                conexion.rollback();
            }
        }
    }

    private static Monitoreo.Resultado registrar(Connection conexion, int minuto, String valor) throws SQLException {
        var lectura = new Medicion("SEN-TEMP-A2", "MED_TEMP_AMB", INICIO.plusMinutes(minuto), new BigDecimal(valor));
        return Monitoreo.registrarEnTransaccion(conexion, lectura, "MANUAL");
    }
}
