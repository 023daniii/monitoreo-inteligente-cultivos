package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimuladorSensoresTest {
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
}
