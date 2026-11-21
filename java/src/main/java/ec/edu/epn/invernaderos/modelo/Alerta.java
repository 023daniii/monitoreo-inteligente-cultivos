package ec.edu.epn.invernaderos.modelo;

import java.time.LocalDateTime;

public record Alerta(long id, int regla, String sensor, String cultivo, String severidad,
                     String recomendacion, String estado, LocalDateTime fechaResolucion) {}
