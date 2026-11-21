package ec.edu.epn.invernaderos.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record Medicion(String sensor, String magnitud, LocalDateTime fecha, BigDecimal valor) {
    public Medicion {
        fecha = fecha.truncatedTo(ChronoUnit.MICROS);
        valor = valor.setScale(4, RoundingMode.UNNECESSARY);
    }
}
