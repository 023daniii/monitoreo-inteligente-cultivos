package ec.edu.epn.invernaderos;

import ec.edu.epn.invernaderos.modelo.Medicion;
import ec.edu.epn.invernaderos.servicio.Monitoreo;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Scanner;

public final class Main {
    private static final Monitoreo MONITOREO = new Monitoreo();

    private Main() {}

    public static void main(String[] args) throws SQLException {
        if (args.length == 1 && args[0].equals("demo")) {
            demo();
            return;
        }
        try (var entrada = new Scanner(System.in)) {
            while (true) {
                System.out.println("1. Ejecutar demo | 2. Simular lectura | 3. Resolver alerta | 0. Salir");
                if (!entrada.hasNextLine()) {
                    break;
                }
                try {
                    switch (entrada.nextLine().trim()) {
                        case "0" -> { return; }
                        case "1" -> demo();
                        case "2" -> simular(entrada);
                        case "3" -> {
                            System.out.println("ID de alerta:");
                            boolean resuelta = MONITOREO.resolver(Long.parseLong(entrada.nextLine().trim()));
                            System.out.println(resuelta ? "Alerta resuelta." : "No hay una alerta resoluble con ese ID.");
                        }
                        default -> System.out.println("Opción inválida.");
                    }
                } catch (IllegalArgumentException | ArithmeticException | SQLException error) {
                    System.err.println(error.getMessage());
                }
            }
        }
    }

    private static void demo() throws SQLException {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 16, 10, 0);
        String[] valores = {"25", "30", "34.2"};
        for (int i = 0; i < valores.length; i++) {
            imprimir(new Medicion("SEN-TEMP-B1", "MED_TEMP_AMB", inicio.plusMinutes(i), new BigDecimal(valores[i])));
        }
    }

    private static void simular(Scanner entrada) throws SQLException {
        System.out.println("Código de sensor (ejemplo SEN-TEMP-A2):");
        String sensor = entrada.nextLine().trim();
        System.out.println("Código de magnitud (ejemplo MED_TEMP_AMB):");
        String magnitud = entrada.nextLine().trim();
        System.out.println("Valor de la lectura:");
        var valor = new BigDecimal(entrada.nextLine().trim().replace(',', '.'));
        imprimir(new Medicion(sensor, magnitud, LocalDateTime.now(java.time.ZoneId.of("America/Guayaquil")), valor));
    }

    private static void imprimir(Medicion lectura) throws SQLException {
        var resultado = MONITOREO.registrar(lectura);
        System.out.printf("%s: medición %d (%s), %d alerta(s)%n", lectura.valor(), resultado.idMedicion(),
                          resultado.nueva() ? "nueva" : "existente", resultado.alertas().size());
        resultado.alertas().forEach(System.out::println);
    }
}
