package ec.edu.epn.invernaderos.servicio;

import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.dao.MonitoreoDao;
import ec.edu.epn.invernaderos.modelo.Alerta;
import ec.edu.epn.invernaderos.modelo.Medicion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class Monitoreo {
    public record Resultado(long idMedicion, boolean nueva, List<Alerta> alertas) {}

    public Resultado registrar(Medicion lectura) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            conexion.setAutoCommit(false);
            try {
                var resultado = registrarEnTransaccion(conexion, lectura, "MANUAL");
                conexion.commit();
                return resultado;
            } catch (SQLException | RuntimeException error) {
                try {
                    conexion.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                throw error;
            }
        }
    }

    static Resultado registrarEnTransaccion(Connection conexion, Medicion lectura, String origen) throws SQLException {
        var dao = new MonitoreoDao(conexion);
        var registro = dao.registrar(lectura, origen);
        if (registro.nuevo()) {
            for (var regla : dao.reglasAplicables(registro.id())) {
                if (regla.seActivaCon(lectura.valor()) && !dao.hayPendiente(registro.id(), regla)) {
                    dao.crearAlerta(registro.id(), regla, lectura.fecha());
                }
            }
        }
        return new Resultado(registro.id(), registro.nuevo(), dao.alertas(registro.id()));
    }

    public boolean resolver(long idAlerta) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            return new MonitoreoDao(conexion).resolver(idAlerta);
        }
    }

    public boolean adjuntarEvidencia(long idAlerta, byte[] foto, String formato) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            return new MonitoreoDao(conexion).adjuntarEvidencia(idAlerta, foto, formato);
        }
    }

    public byte[] evidencia(long idAlerta) throws SQLException {
        try (var conexion = Conexion.abrir()) {
            return new MonitoreoDao(conexion).evidencia(idAlerta);
        }
    }
}
