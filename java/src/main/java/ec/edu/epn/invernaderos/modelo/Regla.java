package ec.edu.epn.invernaderos.modelo;

import java.math.BigDecimal;

public record Regla(int id, int cultivo, String operador, BigDecimal umbral) {
    public boolean seActivaCon(BigDecimal valor) {
        int comparacion = valor.compareTo(umbral);
        return switch (operador) {
            case ">" -> comparacion > 0;
            case ">=" -> comparacion >= 0;
            case "<" -> comparacion < 0;
            case "<=" -> comparacion <= 0;
            case "=" -> comparacion == 0;
            default -> throw new IllegalStateException("Operador desconocido: " + operador);
        };
    }
}
