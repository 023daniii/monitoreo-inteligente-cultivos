package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public final class MedidorHorario {
    private MedidorHorario() {}

    public static void main(String[] args) throws InterruptedException {
        var simulador = new SimuladorSensores();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                revisar(simulador);
            } catch (SQLException error) {
                System.err.println("No se pudo consultar la red de sensores: " + error.getMessage());
            }
            TimeUnit.MINUTES.sleep(1);
        }
    }

    private static void revisar(SimuladorSensores simulador) throws SQLException {
        String sql = """
            SELECT id_invernadero FROM invernadero JOIN terreno USING (id_terreno)
            WHERE invernadero.estado AND terreno.estado ORDER BY id_invernadero
            """;
        try (var conexion = Conexion.abrir(); var consulta = conexion.createStatement();
             var filas = consulta.executeQuery(sql)) {
            while (filas.next()) {
                int id = filas.getInt(1);
                try {
                    var resultado = simulador.medirPendientes(id);
                    if (resultado.mediciones() > 0) {
                        System.out.printf("Invernadero %d: %d lecturas automáticas, %d alertas%n",
                            id, resultado.mediciones(), resultado.alertas());
                    }
                } catch (SQLException | IllegalArgumentException error) {
                    System.err.printf("No se pudo medir el invernadero %d: %s%n", id, error.getMessage());
                }
            }
        }
    }
}
