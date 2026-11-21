package ec.edu.epn.invernaderos.dao;

import ec.edu.epn.invernaderos.config.Conexion;
<<<<<<< HEAD
=======
import ec.edu.epn.invernaderos.servicio.SimuladorSensores;
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
<<<<<<< HEAD
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas de solo lectura para la navegación: terrenos, invernaderos, sensores, cultivos e historial. */
=======
import java.util.HashMap;
import java.util.List;
import java.util.Map;

>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
public final class InventarioDao {
    public record Terreno(int id, String nombre, String direccion, BigDecimal area,
                          List<Invernadero> invernaderos) {}
    public record Invernadero(int id, String nombre, BigDecimal largo, BigDecimal ancho) {}
    public record Detalle(List<Sensor> sensores, List<Cultivo> cultivos, List<Lectura> historial) {}
<<<<<<< HEAD
    /** Un sensor con las magnitudes que mide su tipo y su última lectura. */
    public record Sensor(int id, String codigo, String tipo, String ubicacion, List<Magnitud> magnitudes,
                         String magnitud, String unidad, BigDecimal valor, LocalDateTime fecha) {}
    public record Cultivo(int id, String nombre, String tipo, LocalDate siembra, LocalDate cosechaEstimada) {}
    public record Lectura(String sensor, String magnitud, String unidad, BigDecimal valor, LocalDateTime fecha,
                          String origen) {}
=======
    public record Sensor(int id, String codigo, String tipo, String ubicacion, String magnitud,
                         String unidad, BigDecimal valor, LocalDateTime fecha) {}
    public record Cultivo(int id, String nombre, String tipo, LocalDate siembra) {}
    public record Lectura(String sensor, String magnitud, String unidad, BigDecimal valor, LocalDateTime fecha, String origen) {}
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
    public record Magnitud(String codigo, String nombre, String unidad) {}

    public List<Terreno> terrenos() throws SQLException {
        String consulta = """
            SELECT t.id_terreno, t.nombre AS terreno, t.direccion, t.area_m2,
                   i.id_invernadero, i.nombre AS invernadero, i.largo_m, i.ancho_m
            FROM terreno t
            LEFT JOIN invernadero i ON i.id_terreno = t.id_terreno AND i.estado
            WHERE t.estado
<<<<<<< HEAD
            ORDER BY t.id_terreno, i.nombre, i.id_invernadero
=======
            ORDER BY t.id_terreno, i.id_invernadero
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
            """;
        List<Terreno> terrenos = new ArrayList<>();
        try (var conexion = Conexion.abrir(); var orden = conexion.createStatement();
             var filas = orden.executeQuery(consulta)) {
            Terreno actual = null;
            while (filas.next()) {
                int id = filas.getInt("id_terreno");
                if (actual == null || actual.id() != id) {
                    actual = new Terreno(id, filas.getString("terreno"), filas.getString("direccion"),
                        filas.getBigDecimal("area_m2"), new ArrayList<>());
                    terrenos.add(actual);
                }
                Integer invernadero = filas.getObject("id_invernadero", Integer.class);
                if (invernadero != null) {
                    actual.invernaderos().add(new Invernadero(invernadero, filas.getString("invernadero"),
                        filas.getBigDecimal("largo_m"), filas.getBigDecimal("ancho_m")));
                }
            }
        }
        return terrenos;
    }

<<<<<<< HEAD
=======
    public List<Magnitud> magnitudes() throws SQLException {
        try (var conexion = Conexion.abrir()) {
            return magnitudes(conexion);
        }
    }

    private List<Magnitud> magnitudes(Connection conexion) throws SQLException {
        String consulta = """
            SELECT m.codigo, m.nombre, m.unidad
            FROM catalogo m JOIN catalogo categoria ON categoria.id_catalogo = m.id_padre
            WHERE categoria.codigo = 'CAT_TIPO_MEDICION' AND categoria.id_padre IS NULL AND m.estado
            ORDER BY m.nombre, m.codigo
            """;
        List<Magnitud> magnitudes = new ArrayList<>();
        try (var orden = conexion.createStatement(); var filas = orden.executeQuery(consulta)) {
            while (filas.next()) {
                magnitudes.add(new Magnitud(filas.getString("codigo"), filas.getString("nombre"),
                    filas.getString("unidad")));
            }
        }
        return magnitudes;
    }

>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
    public Detalle detalle(int idInvernadero) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            conexion.setReadOnly(true);
            conexion.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conexion.setAutoCommit(false);
<<<<<<< HEAD
            // Una sola foto de la base para las tarjetas y el historial, aunque haya una ronda en curso.
=======
            // Un mismo instante para la ficha del sensor y el historial, aunque haya una ronda en curso.
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
            var detalle = new Detalle(sensores(conexion, idInvernadero), cultivos(conexion, idInvernadero),
                historial(conexion, idInvernadero));
            conexion.commit();
            return detalle;
        }
    }

<<<<<<< HEAD
    /** Magnitudes activas de cada tipo de sensor (en el catálogo cuelgan del tipo que las mide). */
    private static Map<Integer, List<Magnitud>> magnitudesPorTipo(Connection conexion) throws SQLException {
        String consulta = """
            SELECT m.id_padre, m.codigo, m.nombre, m.unidad
            FROM catalogo m JOIN catalogo tipo ON tipo.id_catalogo = m.id_padre
            JOIN catalogo categoria ON categoria.id_catalogo = tipo.id_padre
            WHERE categoria.codigo = 'CAT_TIPO_SENSOR' AND m.estado
            ORDER BY m.nombre
            """;
        Map<Integer, List<Magnitud>> mapa = new LinkedHashMap<>();
        try (var orden = conexion.createStatement(); var filas = orden.executeQuery(consulta)) {
            while (filas.next()) {
                mapa.computeIfAbsent(filas.getInt(1), k -> new ArrayList<>())
                    .add(new Magnitud(filas.getString(2), filas.getString(3), filas.getString(4)));
            }
        }
        return mapa;
    }

    private List<Sensor> sensores(Connection conexion, int idInvernadero) throws SQLException {
        String consulta = """
            SELECT s.id_sensor, s.codigo, s.ubicacion, s.id_tipo_sensor, tipo.nombre AS tipo,
=======
    private List<Sensor> sensores(Connection conexion, int idInvernadero) throws SQLException {
        String consulta = """
            SELECT s.id_sensor, s.codigo, s.ubicacion, tipo.nombre AS tipo, tipo.codigo AS codigo_tipo,
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
                   medida.nombre AS magnitud, medida.unidad, ultima.valor, ultima.fecha_hora
            FROM sensor s JOIN catalogo tipo ON tipo.id_catalogo = s.id_tipo_sensor
            LEFT JOIN LATERAL (
                SELECT m.valor, m.fecha_hora, m.id_tipo_medicion FROM medicion m
                WHERE m.id_sensor = s.id_sensor
                ORDER BY m.fecha_hora DESC, m.id_medicion DESC LIMIT 1
            ) ultima ON true
            LEFT JOIN catalogo medida ON medida.id_catalogo = ultima.id_tipo_medicion
            WHERE s.estado AND tipo.estado AND s.id_invernadero = ?
            ORDER BY s.id_sensor
            """;
<<<<<<< HEAD
        var porTipo = magnitudesPorTipo(conexion);
=======
        Map<String, Magnitud> catalogo = new HashMap<>();
        for (var magnitud : magnitudes(conexion)) catalogo.put(magnitud.codigo(), magnitud);
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
        List<Sensor> sensores = new ArrayList<>();
        try (var orden = conexion.prepareStatement(consulta)) {
            orden.setInt(1, idInvernadero);
            try (var filas = orden.executeQuery()) {
                while (filas.next()) {
<<<<<<< HEAD
                    var magnitudes = porTipo.getOrDefault(filas.getInt("id_tipo_sensor"), List.of());
                    String magnitud = filas.getString("magnitud");
                    String unidad = filas.getString("unidad");
                    if (magnitud == null && !magnitudes.isEmpty()) {
                        magnitud = magnitudes.get(0).nombre();
                        unidad = magnitudes.get(0).unidad();
                    }
                    sensores.add(new Sensor(filas.getInt("id_sensor"), filas.getString("codigo"),
                        filas.getString("tipo"), filas.getString("ubicacion"), magnitudes,
                        magnitud == null ? filas.getString("tipo") : magnitud, unidad,
=======
                    String magnitud = filas.getString("magnitud");
                    String unidad = filas.getString("unidad");
                    if (magnitud == null) {
                        var prevista = catalogo.get(SimuladorSensores.magnitudPara(filas.getString("codigo_tipo")));
                        magnitud = prevista != null ? prevista.nombre() : filas.getString("tipo");
                        unidad = prevista != null ? prevista.unidad() : null;
                    }
                    sensores.add(new Sensor(filas.getInt("id_sensor"), filas.getString("codigo"),
                        filas.getString("tipo"), filas.getString("ubicacion"), magnitud, unidad,
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
                        filas.getBigDecimal("valor"), filas.getObject("fecha_hora", LocalDateTime.class)));
                }
            }
        }
        return sensores;
    }

    private List<Cultivo> cultivos(Connection conexion, int idInvernadero) throws SQLException {
        String consulta = """
<<<<<<< HEAD
            SELECT c.id_cultivo, c.nombre, tipo.nombre AS tipo, c.fecha_siembra, c.fecha_cosecha_estimada
=======
            SELECT c.id_cultivo, c.nombre, tipo.nombre AS tipo, c.fecha_siembra
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
            FROM cultivo c JOIN catalogo tipo ON tipo.id_catalogo = c.id_tipo_cultivo
            WHERE c.id_invernadero = ? AND c.fecha_siembra <= CURRENT_DATE
              AND (c.fecha_cosecha_real IS NULL OR c.fecha_cosecha_real >= CURRENT_DATE)
            ORDER BY c.nombre, c.id_cultivo
            """;
        List<Cultivo> cultivos = new ArrayList<>();
        try (var orden = conexion.prepareStatement(consulta)) {
            orden.setInt(1, idInvernadero);
            try (var filas = orden.executeQuery()) {
                while (filas.next()) {
                    cultivos.add(new Cultivo(filas.getInt("id_cultivo"), filas.getString("nombre"),
<<<<<<< HEAD
                        filas.getString("tipo"), filas.getObject("fecha_siembra", LocalDate.class),
                        filas.getObject("fecha_cosecha_estimada", LocalDate.class)));
=======
                        filas.getString("tipo"), filas.getObject("fecha_siembra", LocalDate.class)));
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
                }
            }
        }
        return cultivos;
    }

    private List<Lectura> historial(Connection conexion, int idInvernadero) throws SQLException {
        String consulta = """
            SELECT s.codigo, tipo.nombre, tipo.unidad, m.valor, m.fecha_hora, m.metadatos->>'origen' AS origen
            FROM medicion m JOIN sensor s USING (id_sensor)
            JOIN catalogo tipo ON tipo.id_catalogo = m.id_tipo_medicion
            WHERE s.id_invernadero = ?
            ORDER BY m.fecha_hora DESC, m.id_medicion DESC LIMIT 200
            """;
        List<Lectura> lecturas = new ArrayList<>();
        try (var orden = conexion.prepareStatement(consulta)) {
            orden.setInt(1, idInvernadero);
            try (var filas = orden.executeQuery()) {
                while (filas.next()) {
                    lecturas.add(new Lectura(filas.getString("codigo"), filas.getString("nombre"),
                        filas.getString("unidad"), filas.getBigDecimal("valor"),
                        filas.getObject("fecha_hora", LocalDateTime.class), filas.getString("origen")));
                }
            }
        }
        return lecturas;
    }
}
