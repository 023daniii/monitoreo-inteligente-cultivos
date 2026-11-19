package ec.edu.epn.invernaderos;

import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.servicio.Monitoreo;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Buzón de alertas con contexto de la lectura, su regla y su resolución. */
public final class BuzonAlertas extends VBox {
    private static final int TAMANO_PAGINA = 40;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final TableView<Fila> tabla = new TableView<>();
    private final CheckBox soloSeleccionado = new CheckBox("Solo el invernadero seleccionado");
    private final ComboBox<String> filtro = new ComboBox<>();
    private final TextField busqueda = new TextField();
    private final Label estado = new Label();
    private final Label tituloDetalle = new Label();
    private final Label ubicacion = new Label(), lectura = new Label(), recomendacion = new Label();
    private final VBox detalle;
    private final Button resolver = new Button("Marcar como resuelta");
    private final Button anterior = new Button("Anterior"), siguiente = new Button("Siguiente");
    private Integer idInvernadero;
    private int pagina, revision;
    private boolean resolviendo;

    private record Fila(long id, String fecha, String terreno, String invernadero, String cultivo,
                        String sensor, String magnitud, String valor, String unidad, String regla,
                        String severidad, String recomendacion, String estado, String resuelta) {}

    public BuzonAlertas() {
        setSpacing(12);
        filtro.setId("buzon-estado");
        soloSeleccionado.setId("buzon-local");
        busqueda.setId("buzon-busqueda");
        tabla.setId("buzon-tabla");
        resolver.setId("buzon-resolver");
        var titulo = new Label("Buzón de alertas");
        titulo.getStyleClass().add("page-title");
        filtro.getItems().addAll("Pendientes", "Resueltas", "Todas");
        filtro.setValue("Pendientes");
        filtro.setAccessibleText("Filtrar por estado");
        filtro.setOnAction(e -> primeraPagina());
        soloSeleccionado.setDisable(true);
        soloSeleccionado.setOnAction(e -> primeraPagina());
        busqueda.setPromptText("Terreno, invernadero, cultivo o sensor");
        busqueda.setPrefColumnCount(25);
        busqueda.setOnAction(e -> primeraPagina());
        var buscar = new Button("Buscar");
        buscar.setOnAction(e -> primeraPagina());
        var actualizar = new Button("Actualizar buzón");
        actualizar.setOnAction(e -> cargar(""));
        var filtros = new FlowPane(8, 8, filtro, soloSeleccionado, busqueda, buscar, actualizar);

        columna("Fecha", Fila::fecha, 125);
        columna("Invernadero", Fila::invernadero, 150);
        columna("Sensor", Fila::sensor, 90);
        columna("Lectura", f -> f.valor + " " + f.unidad, 85);
        columna("Severidad", Fila::severidad, 120);
        columna("Estado", Fila::estado, 100);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("Sin alertas para estos filtros"));
        tabla.setMinHeight(100);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, previa, nueva) -> mostrar(nueva));

        estado.getStyleClass().add("muted");
        estado.setWrapText(true);
        var margen = new Region();
        HBox.setHgrow(margen, Priority.ALWAYS);
        anterior.setOnAction(e -> { pagina--; cargar(""); });
        siguiente.setOnAction(e -> { pagina++; cargar(""); });

        tituloDetalle.getStyleClass().add("section-title");
        ubicacion.setWrapText(true);
        lectura.setWrapText(true);
        recomendacion.setWrapText(true);
        resolver.getStyleClass().add("primary");
        resolver.setOnAction(e -> resolverSeleccion());
        detalle = new VBox(5, tituloDetalle, ubicacion, lectura, recomendacion, resolver);
        detalle.getStyleClass().add("card");

        getChildren().addAll(titulo, filtros, tabla, new HBox(8, estado, margen, anterior, siguiente), detalle);
        mostrar(null);
        anterior.setDisable(true);
        siguiente.setDisable(true);
    }

    public void actualizar(Integer idInvernadero) {
        if (!Objects.equals(this.idInvernadero, idInvernadero)) {
            pagina = 0;
            if (this.idInvernadero == null || idInvernadero == null) {
                soloSeleccionado.setSelected(idInvernadero != null);
            }
        }
        this.idInvernadero = idInvernadero;
        soloSeleccionado.setDisable(idInvernadero == null);
        cargar("");
    }

    private void columna(String nombre, Function<Fila, String> valor, double ancho) {
        var columna = new TableColumn<Fila, String>(nombre);
        columna.setCellValueFactory(f -> new ReadOnlyStringWrapper(valor.apply(f.getValue())));
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        tabla.getColumns().add(columna);
    }

    private void primeraPagina() {
        pagina = 0;
        cargar("");
    }

    private void mostrar(Fila f) {
        detalle.setManaged(f != null);
        detalle.setVisible(f != null);
        if (f == null) return;
        tituloDetalle.setText("Alerta " + f.id + " · " + f.severidad);
        ubicacion.setText(f.terreno + " / " + f.invernadero + " · " + f.cultivo + " · " + f.sensor);
        lectura.setText(f.magnitud + " " + f.valor + " " + f.unidad + " · Regla " + f.regla
            + " · " + f.fecha + (f.resuelta == null ? "" : " · Resuelta el " + f.resuelta));
        recomendacion.setText(f.recomendacion);
        resolver.setDisable(resolviendo || f.resuelta != null);
    }

    private void cargar(String mensaje) {
        int solicitud = ++revision;
        Integer alcance = soloSeleccionado.isSelected() ? idInvernadero : null;
        String filtroEstado = filtro.getValue();
        String texto = busqueda.getText().strip();
        int offset = pagina * TAMANO_PAGINA;
        var previa = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().clear();
        anterior.setDisable(true);
        siguiente.setDisable(true);
        estado.setText("Consultando alertas…");
        var tarea = new Task<List<Fila>>() {
            @Override protected List<Fila> call() throws SQLException {
                return consultar(alcance, filtroEstado, texto, offset);
            }
        };
        tarea.setOnSucceeded(e -> {
            if (solicitud != revision) return;
            var filas = tarea.getValue();
            if (filas.isEmpty() && pagina > 0) {
                pagina--;
                cargar(mensaje);
                return;
            }
            int visibles = Math.min(TAMANO_PAGINA, filas.size());
            tabla.getItems().setAll(filas.subList(0, visibles));
            if (previa != null) tabla.getItems().stream().filter(f -> f.id == previa.id)
                .findFirst().ifPresent(tabla.getSelectionModel()::select);
            if (tabla.getSelectionModel().isEmpty()) tabla.getSelectionModel().selectFirst();
            estado.setText(mensaje + (visibles == 0 ? "Sin coincidencias." :
                "Alertas " + (offset + 1) + "–" + (offset + visibles)));
            anterior.setDisable(pagina == 0);
            siguiente.setDisable(filas.size() <= TAMANO_PAGINA);
        });
        tarea.setOnFailed(e -> {
            if (solicitud == revision) estado.setText("No se pudo cargar el buzón. " + tarea.getException().getMessage());
        });
        ejecutar(tarea);
    }

    private static List<Fila> consultar(Integer id, String filtro, String busqueda, int offset)
            throws SQLException {
        String sql = """
            SELECT a.id_alerta, a.fecha_hora, t.nombre AS terreno, i.nombre AS invernadero,
                   c.nombre AS cultivo, s.codigo, mag.nombre AS magnitud, m.valor, mag.unidad,
                   r.operador, r.umbral, sev.nombre AS severidad, r.recomendacion,
                   est.nombre AS estado, a.fecha_resolucion
            FROM alerta a
            JOIN medicion m ON m.id_medicion = a.id_medicion
            JOIN sensor s ON s.id_sensor = m.id_sensor
            JOIN invernadero i ON i.id_invernadero = s.id_invernadero
            JOIN terreno t ON t.id_terreno = i.id_terreno
            JOIN cultivo c ON c.id_cultivo = a.id_cultivo
            JOIN regla r ON r.id_regla = a.id_regla
            JOIN catalogo mag ON mag.id_catalogo = m.id_tipo_medicion
            JOIN catalogo sev ON sev.id_catalogo = r.id_severidad
            JOIN catalogo est ON est.id_catalogo = a.id_estado
            WHERE (CAST(? AS integer) IS NULL OR i.id_invernadero = ?)
              AND (? = 'Todas' OR (? = 'Resueltas') = (a.fecha_resolucion IS NOT NULL))
              AND strpos(lower(concat_ws(' ', t.nombre, i.nombre, c.nombre, s.codigo)), lower(?)) > 0
            ORDER BY a.fecha_hora DESC, a.id_alerta DESC LIMIT ? OFFSET ?
            """;
        try (var conexion = Conexion.abrir(); var consulta = conexion.prepareStatement(sql)) {
            consulta.setObject(1, id, Types.INTEGER);
            consulta.setObject(2, id, Types.INTEGER);
            consulta.setString(3, filtro);
            consulta.setString(4, filtro);
            consulta.setString(5, busqueda);
            consulta.setInt(6, TAMANO_PAGINA + 1);
            consulta.setInt(7, offset);
            try (var rs = consulta.executeQuery()) {
                List<Fila> filas = new ArrayList<>();
                while (rs.next()) {
                    var fin = rs.getObject("fecha_resolucion", LocalDateTime.class);
                    filas.add(new Fila(rs.getLong("id_alerta"), FECHA.format(rs.getObject("fecha_hora", LocalDateTime.class)),
                        rs.getString("terreno"), rs.getString("invernadero"), rs.getString("cultivo"),
                        rs.getString("codigo"), rs.getString("magnitud"), rs.getBigDecimal("valor").stripTrailingZeros().toPlainString(),
                        Objects.toString(rs.getString("unidad"), ""), rs.getString("operador") + " " + rs.getBigDecimal("umbral").stripTrailingZeros().toPlainString(),
                        rs.getString("severidad"), rs.getString("recomendacion"), rs.getString("estado"),
                        fin == null ? null : FECHA.format(fin)));
                }
                return filas;
            }
        }
    }

    private void resolverSeleccion() {
        Fila fila = tabla.getSelectionModel().getSelectedItem();
        if (fila == null || fila.resuelta != null || resolviendo) return;
        resolviendo = true;
        resolver.setDisable(true);
        var tarea = new Task<Boolean>() {
            @Override protected Boolean call() throws SQLException { return new Monitoreo().resolver(fila.id); }
        };
        tarea.setOnSucceeded(e -> {
            resolviendo = false;
            cargar(tarea.getValue() ? "Alerta " + fila.id + " resuelta. " : "No se pudo resolver la alerta. ");
        });
        tarea.setOnFailed(e -> {
            resolviendo = false;
            mostrar(tabla.getSelectionModel().getSelectedItem());
            estado.setText("Error al resolver la alerta. " + tarea.getException().getMessage());
        });
        ejecutar(tarea);
    }

    private static void ejecutar(Task<?> tarea) {
        var hilo = new Thread(tarea, "buzon-alertas");
        hilo.setDaemon(true);
        hilo.start();
    }
}
