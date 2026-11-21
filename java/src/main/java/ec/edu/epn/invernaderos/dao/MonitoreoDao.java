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
        // La magnitud debe colgar del tipo del sensor en el catálogo: un termómetro no registra pH.
        String insertar = """
            INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor, metadatos)
            SELECT s.id_sensor, t.id_catalogo, ?, ?,
                   NULLIF(jsonb_strip_nulls(jsonb_build_object('origen', ?::text)), '{}'::jsonb)
            FROM sensor s JOIN invernadero i USING (id_invernadero) JOIN terreno USING (id_terreno)
            JOIN catalogo tipo_sensor ON tipo_sensor.id_catalogo = s.id_tipo_sensor
            JOIN catalogo t ON t.id_padre = s.id_tipo_sensor
            WHERE s.codigo = ? AND t.codigo = ? AND s.estado AND i.estado AND t.estado
              AND terreno.estado AND tipo_sensor.estado
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
                    throw new IllegalArgumentException(
                        "El sensor no mide esa magnitud o no está habilitado para esa fecha");
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

    /** ¿Ya hay una alerta pendiente de esta regla para este cultivo y este sensor? Evita repetir el mismo aviso cada hora. */
    public boolean hayPendiente(long medicion, Regla regla) throws SQLException {
        String consulta = """
            SELECT 1 FROM alerta a
            JOIN medicion m ON m.id_medicion = a.id_medicion
            JOIN catalogo estado ON estado.id_catalogo = a.id_estado
            WHERE a.id_regla = ? AND a.id_cultivo = ? AND estado.codigo = 'EST_PENDIENTE'
              AND m.id_sensor = (SELECT id_sensor FROM medicion WHERE id_medicion = ?)
              AND a.id_medicion <> ?
            LIMIT 1
            """;
        try (var sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setInt(1, regla.id());
            sentencia.setInt(2, regla.cultivo());
            sentencia.setLong(3, medicion);
            sentencia.setLong(4, medicion);
            try (var filas = sentencia.executeQuery()) {
                return filas.next();
            }
        }
    }

    public boolean resolver(long idAlerta) throws SQLException {
        String actualizar = """
            UPDATE alerta SET fecha_resolucion = LOCALTIMESTAMP, fecha_modificacion = LOCALTIMESTAMP,
                id_estado = (SELECT id_catalogo FROM catalogo WHERE codigo = 'EST_RESUELTA')
            WHERE id_alerta = ? AND fecha_resolucion IS NULL AND fecha_hora <= LOCALTIMESTAMP
            """;
        try (var sentencia = conexion.prepareStatement(actualizar)) {
            sentencia.setLong(1, idAlerta);
            return sentencia.executeUpdate() == 1;
        }
    }

    /** Guarda la foto que respalda la alerta (evidencia opcional del MER). */
    public boolean adjuntarEvidencia(long idAlerta, byte[] foto, String formato) throws SQLException {
        String actualizar = """
            UPDATE alerta SET evidencia_foto = ?, formato_evidencia = ?,
                fecha_captura_evidencia = LOCALTIMESTAMP, fecha_modificacion = LOCALTIMESTAMP
            WHERE id_alerta = ?
            """;
        try (var sentencia = conexion.prepareStatement(actualizar)) {
            sentencia.setBytes(1, foto);
            sentencia.setString(2, formato);
            sentencia.setLong(3, idAlerta);
            return sentencia.executeUpdate() == 1;
        }
    }

    public byte[] evidencia(long idAlerta) throws SQLException {
        try (var sentencia = conexion.prepareStatement("SELECT evidencia_foto FROM alerta WHERE id_alerta = ?")) {
            sentencia.setLong(1, idAlerta);
            try (var filas = sentencia.executeQuery()) {
                return filas.next() ? filas.getBytes(1) : null;
            }
        }
    }
}
