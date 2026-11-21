package ec.edu.epn.invernaderos.dao;

import ec.edu.epn.invernaderos.config.Conexion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Búsqueda por similitud con pgvector. El vector de la foto consultada lo calcula Python con CLIP. */
public final class FotoDao {
    public static final String MODELO = "CLIP ViT-B/32";

    /** Resultado de la búsqueda: solo la foto y su parecido; el diagnóstico se lee al seleccionarla. */
    public record Referencia(long id, String ruta, double distancia) {
        public double similitud() { return 1 - distancia; }
    }
    public record Detalle(long id, String ruta, String diagnostico, String descripcion, String recomendacion,
                          String etiqueta, String origen, String fuente, String licencia, String cultivo) {}
    public record Diagnostico(String codigo, String nombre) {
        @Override public String toString() { return nombre; }
    }

    public int totalReferencias() throws SQLException {
        try (var conexion = Conexion.abrir(); var sentencia = conexion.prepareStatement(
                "SELECT count(*) FROM registro_fotografico WHERE estado AND modelo_embedding = ?")) {
            sentencia.setString(1, MODELO);
            try (var filas = sentencia.executeQuery()) {
                filas.next();
                return filas.getInt(1);
            }
        }
    }

    public List<Referencia> buscarSimilares(double[] vector) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            return buscarSimilares(conexion, vector);
        }
    }

    /** Las 5 fotos activas más cercanas por distancia coseno (operador <=> de pgvector). */
    static List<Referencia> buscarSimilares(Connection conexion, double[] vector) throws SQLException {
        if (vector.length != 512) throw new IllegalArgumentException("El vector debe tener 512 valores.");
        String consulta = """
            SELECT id_foto, ruta_imagen, embedding <=> ?::vector AS distancia
            FROM registro_fotografico
            WHERE estado AND modelo_embedding = ?
            ORDER BY distancia, id_foto
            LIMIT 5
            """;
        List<Referencia> referencias = new ArrayList<>();
        try (var sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setString(1, literal(vector));
            sentencia.setString(2, MODELO);
            try (var filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    referencias.add(new Referencia(filas.getLong(1), filas.getString(2), filas.getDouble(3)));
                }
            }
        }
        return referencias;
    }

    public Detalle detalle(long idFoto) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            return detalle(conexion, idFoto);
        }
    }

    static Detalle detalle(Connection conexion, long idFoto) throws SQLException {
        String consulta = """
            SELECT f.id_foto, f.ruta_imagen, d.nombre, d.descripcion, d.recomendacion, f.etiqueta,
                   f.origen, f.fuente, f.licencia, c.nombre AS cultivo
            FROM registro_fotografico f
            LEFT JOIN catalogo d ON d.id_catalogo = f.id_diagnostico
            LEFT JOIN cultivo c ON c.id_cultivo = f.id_cultivo
            WHERE f.id_foto = ?
            """;
        try (var sentencia = conexion.prepareStatement(consulta)) {
            sentencia.setLong(1, idFoto);
            try (var f = sentencia.executeQuery()) {
                if (!f.next()) return null;
                return new Detalle(f.getLong(1), f.getString(2), f.getString(3), f.getString(4), f.getString(5),
                    f.getString(6), f.getString(7), f.getString(8), f.getString(9), f.getString(10));
            }
        }
    }

    public List<Diagnostico> diagnosticos() throws SQLException {
        String consulta = """
            SELECT d.codigo, d.nombre FROM catalogo d JOIN catalogo c ON c.id_catalogo = d.id_padre
            WHERE c.codigo = 'CAT_DIAGNOSTICO' AND d.estado ORDER BY d.nombre
            """;
        List<Diagnostico> lista = new ArrayList<>();
        try (var conexion = Conexion.abrir(); var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(consulta)) {
            while (filas.next()) lista.add(new Diagnostico(filas.getString(1), filas.getString(2)));
        }
        return lista;
    }

    static String literal(double[] vector) {
        var texto = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) texto.append(',');
            texto.append(vector[i]);
        }
        return texto.append(']').toString();
    }
}
