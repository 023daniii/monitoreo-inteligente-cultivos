package ec.edu.epn.invernaderos.dao;

import ec.edu.epn.invernaderos.config.Conexion;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Búsqueda con pgvector desde Java. Inserta fotos con vectores conocidos y lo revierte al final. */
class FotoDaoTest {
    @Test
    void devuelveLasCincoMasCercanasYElDiagnosticoSoloEnElDetalle() throws Exception {
        try (var conexion = Conexion.abrir()) {
            conexion.setAutoCommit(false);
            try {
                long[] ids = new long[7];
                for (int i = 0; i < ids.length; i++) ids[i] = insertar(conexion, i, angulo(i * 0.2));
                var resultado = FotoDao.buscarSimilares(conexion, angulo(0.05));
                assertEquals(5, resultado.size());
                assertEquals(ids[0], resultado.get(0).id());
                assertEquals(ids[1], resultado.get(1).id());
                for (int i = 1; i < resultado.size(); i++) {
                    assertTrue(resultado.get(i - 1).distancia() <= resultado.get(i).distancia());
                }
                var detalle = FotoDao.detalle(conexion, ids[0]);
                assertEquals("Tizón tardío (Phytophthora infestans)", detalle.diagnostico());
                assertNotNull(detalle.recomendacion());
                assertEquals("CC BY 4.0", detalle.licencia());
                assertThrows(IllegalArgumentException.class, () -> FotoDao.buscarSimilares(conexion, new double[3]));
            } finally {
                conexion.rollback();
            }
        }
    }

    /** Vector unitario de 512 dimensiones que gira en el plano de las dos primeras. */
    private static double[] angulo(double radianes) {
        double[] v = new double[512];
        v[0] = Math.cos(radianes);
        v[1] = Math.sin(radianes);
        return v;
    }

    private static long insertar(Connection conexion, int numero, double[] vector) throws SQLException {
        String sql = """
            INSERT INTO registro_fotografico (ruta_imagen, origen, id_diagnostico, fuente, licencia,
                                              embedding, modelo_embedding, hash_imagen)
            SELECT ?, 'DATASET', id_catalogo, 'Prueba', 'CC BY 4.0', ?::vector, ?, ?
            FROM catalogo WHERE codigo = 'DIAG_TIZON_TARDIO'
            RETURNING id_foto
            """;
        try (var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, "prueba-" + numero + ".jpg");
            sentencia.setString(2, FotoDao.literal(vector));
            sentencia.setString(3, FotoDao.MODELO);
            sentencia.setString(4, String.format("%064d", numero));
            try (var filas = sentencia.executeQuery()) {
                filas.next();
                return filas.getLong(1);
            }
        }
    }
}
