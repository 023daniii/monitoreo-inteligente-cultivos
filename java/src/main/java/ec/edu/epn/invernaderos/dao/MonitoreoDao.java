package ec.edu.epn.invernaderos.dao;

import ec.edu.epn.invernaderos.modelo.Alerta;
import ec.edu.epn.invernaderos.modelo.Medicion;
import ec.edu.epn.invernaderos.modelo.Regla;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

public final class MonitoreoDao {
    private final Connection conexion;

    public MonitoreoDao(Connection conexion) {
        this.conexion = conexion;
    }

    public record Registro(long id, boolean nuevo) {}

    public Registro registrar(Medicion lectura) throws SQLException {
        return registrar(lectura, null);
    }

    public Registro registrar(Medicion lectura, String origen) throws SQLException {
        String insertar = """
            INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor, metadatos)
            SELECT s.id_sensor, t.id_catalogo, ?, ?,
                   NULLIF(jsonb_strip_nulls(jsonb_build_object('origen', ?::text)), '{}'::jsonb)
            FROM sensor s JOIN invernadero i USING (id_invernadero)
            CROSS JOIN catalogo t JOIN catalogo categoria ON categoria.id_catalogo = t.id_padre
            WHERE s.codigo = ? AND t.codigo = ? AND s.estado AND i.estado AND t.estado
              AND categoria.codigo = 'CAT_TIPO_MEDICION' AND categoria.id_padre IS NULL
              AND s.fecha_instalacion <= ? AND i.fecha_instalacion <= ?
            ON CONFLICT ON CONSTRAINT medicion_uq3 DO NOTHING
            RETURNING id_medicion
            """;
        try (var sentencia = conexion.prepareStatement(insertar)) {
            sentencia.setObject(1, lectura.fecha());
            sentencia.setBigDecimal(2, lectura.valor());
            sentencia.setString(3, origen);
            sentencia.setString(4, lectura.sensor());
            sentencia.setString(5, lectura.magnitud());
            sentencia.setObject(6, lectura.fecha().toLocalDate());
            sentencia.setObject(7, lectura.fecha().toLocalDate());
            try (var filas = sentencia.executeQuery()) {
                if (filas.next()) {
                    return new Registro(filas.getLong(1), true);
                }
            }
        }

        String buscar = """
            SELECT m.id_medicion, m.valor FROM medicion m
            JOIN sensor s USING (id_sensor)
            JOIN catalogo t ON t.id_catalogo = m.id_tipo_medicion
            WHERE s.codigo = ? AND t.codigo = ? AND m.fecha_hora = ?
            """;
        try (var sentencia = conexion.prepareStatement(buscar)) {
            sentencia.setString(1, lectura.sensor());
            sentencia.setString(2, lectura.magnitud());
            sentencia.setObject(3, lectura.fecha());
            try (var filas = sentencia.executeQuery()) {
                if (!filas.next()) {
                    throw new IllegalArgumentException("Sensor o magnitud sin habilitar para esa fecha");
                }
                if (filas.getBigDecimal("valor").compareTo(lectura.valor()) != 0) {
                    throw new IllegalArgumentException("La lectura ya existe con otro valor");
                }
                return new Registro(filas.getLong("id_medicion"), false);
            }
        }
    }

    public List<Regla> reglasAplicables(long medicion) throws SQLException {
        String consulta = """
            SELECT r.id_regla, c.id_cultivo, r.operador, r.umbral
            FROM medicion m JOIN sensor s USING (id_sensor)
            JOIN cultivo c USING (id_invernadero)
            JOIN regla r ON r.id_tipo_cultivo = c.id_tipo_cultivo
                        AND r.id_tipo_medicion = m.id_tipo_medicion
            WHERE m.id_medicion = ? AND r.estado
              AND c.fecha_siembra <= m.fecha_hora::date
              AND (c.fecha_cosecha_real IS NULL OR c.fecha_cosecha_real >= m.fecha_hora::date)
            ORDER BY r.id_regla, c.id_cultivo
            """;
        List<Regla> reglas = new ArrayList<>();
        try (var sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setLong(1, medicion);
            try (var filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    reglas.add(new Regla(filas.getInt(1), filas.getInt(2), filas.getString(3),
                                         filas.getBigDecimal(4)));
                }
            }
        }
        return reglas;
    }

    public void crearAlerta(long medicion, Regla regla, LocalDateTime fecha) throws SQLException {
        String insertar = """
            INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
            VALUES (?, ?, ?, (SELECT id_catalogo FROM catalogo WHERE codigo = 'EST_PENDIENTE'), ?)
            """;
        try (var sentencia = conexion.prepareStatement(insertar)) {
            sentencia.setInt(1, regla.id());
            sentencia.setLong(2, medicion);
            sentencia.setInt(3, regla.cultivo());
            sentencia.setObject(4, fecha);
            sentencia.executeUpdate();
        }
    }

    public List<Alerta> alertas(long medicion) throws SQLException {
        String consulta = """
            SELECT a.id_alerta, r.id_regla, s.codigo, c.nombre, sev.nombre, r.recomendacion,
                   estado.codigo, a.fecha_resolucion
            FROM alerta a JOIN medicion m USING (id_medicion) JOIN sensor s USING (id_sensor)
            JOIN cultivo c USING (id_cultivo) JOIN regla r USING (id_regla)
            JOIN catalogo sev ON sev.id_catalogo = r.id_severidad
            JOIN catalogo estado ON estado.id_catalogo = a.id_estado
            WHERE a.id_medicion = ? ORDER BY a.id_alerta
            """;
        List<Alerta> alertas = new ArrayList<>();
        try (var sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setLong(1, medicion);
            try (var filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    alertas.add(new Alerta(filas.getLong(1), filas.getInt(2), filas.getString(3),
                        filas.getString(4), filas.getString(5), filas.getString(6),
                        filas.getString(7), filas.getObject(8, LocalDateTime.class)));
                }
            }
        }
        return alertas;
    }

    public boolean resolver(long idAlerta) throws SQLException {
        String actualizar = """
            UPDATE alerta SET fecha_resolucion = (CURRENT_TIMESTAMP AT TIME ZONE 'America/Guayaquil'),
                fecha_modificacion = (CURRENT_TIMESTAMP AT TIME ZONE 'America/Guayaquil'),
                id_estado = (SELECT id_catalogo FROM catalogo WHERE codigo = 'EST_RESUELTA')
            WHERE id_alerta = ? AND fecha_resolucion IS NULL AND fecha_hora <= (CURRENT_TIMESTAMP AT TIME ZONE 'America/Guayaquil')
            """;
        try (var sentencia = conexion.prepareStatement(actualizar)) {
            sentencia.setLong(1, idAlerta);
            return sentencia.executeUpdate() == 1;
        }
    }
}
