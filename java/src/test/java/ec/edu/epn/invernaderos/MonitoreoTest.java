package ec.edu.epn.invernaderos;

import ec.edu.epn.invernaderos.modelo.Medicion;
import ec.edu.epn.invernaderos.servicio.Monitoreo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitoreoTest {
    @Test
    void registraEvaluaReintentaYResuelve() throws Exception {
        var monitoreo = new Monitoreo();
        var inicio = LocalDateTime.of(2026, 2, 4, 10, 0);
        assertTrue(monitoreo.registrar(lectura(inicio, "25")).alertas().isEmpty());
        assertTrue(monitoreo.registrar(lectura(inicio.plusMinutes(1), "30")).alertas().isEmpty());
        var temperaturaAlta = lectura(inicio.plusMinutes(2), "34.2");
        var resultado = monitoreo.registrar(temperaturaAlta);
        assertEquals(1, resultado.alertas().size());
        var alerta = resultado.alertas().get(0);
        assertEquals("TEMP-001", alerta.sensor());
        assertEquals("Tomate de prueba", alerta.cultivo());
        assertTrue(alerta.regla() > 0);
        assertEquals("Advertencia preventiva", alerta.severidad());
        assertEquals("Ventilar el invernadero.", alerta.recomendacion());
        var repetida = monitoreo.registrar(temperaturaAlta);
        assertFalse(repetida.nueva());
        assertEquals(resultado.idMedicion(), repetida.idMedicion());
        assertEquals(resultado.alertas(), repetida.alertas());
        assertThrows(IllegalArgumentException.class,
            () -> monitoreo.registrar(lectura(temperaturaAlta.fecha(), "35")));

        assertEquals(alerta.fechaResolucion() == null, monitoreo.resolver(alerta.id()));
        assertFalse(monitoreo.resolver(alerta.id()));
        var resuelta = monitoreo.registrar(temperaturaAlta).alertas().get(0);
        assertEquals("EST_RESUELTA", resuelta.estado());
        assertNotNull(resuelta.fechaResolucion());
        assertFalse(resuelta.fechaResolucion().isBefore(temperaturaAlta.fecha()));
    }

    private Medicion lectura(LocalDateTime fecha, String valor) {
        return new Medicion("TEMP-001", "MED_TEMP_AMB", fecha, new BigDecimal(valor));
    }
}
