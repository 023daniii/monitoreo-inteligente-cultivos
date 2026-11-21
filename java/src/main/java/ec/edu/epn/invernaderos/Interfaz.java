package ec.edu.epn.invernaderos;

import ec.edu.epn.invernaderos.dao.InventarioDao;
import ec.edu.epn.invernaderos.dao.InventarioDao.*;
import ec.edu.epn.invernaderos.modelo.Medicion;
import ec.edu.epn.invernaderos.servicio.Monitoreo;
import ec.edu.epn.invernaderos.servicio.SimuladorSensores;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Interfaz extends Application {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final InventarioDao inventario = new InventarioDao();
    private final SimuladorSensores simulador = new SimuladorSensores();
    private final ExecutorService consultas = Executors.newFixedThreadPool(2, tarea -> {
        var hilo = new Thread(tarea, "consulta-invernadero");
        hilo.setDaemon(true);
        return hilo;
    });
    private final ComboBox<Terreno> terrenos = new ComboBox<>();
    private final ListView<Invernadero> invernaderos = new ListView<>();
    private final Label red = etiqueta("", "sidebar-note");
    private final Label parcela = etiqueta("", "sidebar-note");
    private final Label titulo = etiqueta("Selecciona un invernadero", "page-title");
    private final Label ubicacion = etiqueta("", "muted");
    private final Label cultivos = etiqueta("", "muted");
    private final Label estado = etiqueta("", "status");
    private final FlowPane tarjetas = new FlowPane(12, 12);
    private final TableView<Lectura> historial = new TableView<>();
    private final ComboBox<Sensor> sensorManual = new ComboBox<>();
    private final ComboBox<Magnitud> magnitudManual = new ComboBox<>();
    private final Button medir = new Button("Medir sensores");
    private final Button registrar = new Button("Guardar lectura");
    private final Button recargar = new Button("Actualizar inventario");
    private final BuzonAlertas buzon = new BuzonAlertas();
    private final PanelFotos fotos = new PanelFotos();
    private final TabPane vistas = new TabPane();
    private final Timeline refresco = new Timeline(new KeyFrame(Duration.minutes(1), e -> actualizarVisible()));
    private int peticion;
    private boolean midiendo;
    private boolean cargando;
    private boolean detalleDisponible;
    private Map<Integer,String> cultivosVisibles = Map.of();

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage ventana) {
        var principal = new BorderPane();
        principal.setLeft(navegacion());
        vistas.setId("vistas");
        vistas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        vistas.getTabs().addAll(new Tab("Sensores", panelSensores()), new Tab("Buzón de alertas", buzon),
                                new Tab("Imágenes", fotos));
        vistas.getSelectionModel().selectedIndexProperty().addListener((o, antes, ahora) -> actualizarVisible());
        var contenido = new VBox(8, ubicacion, titulo, cultivos, vistas, estado);
        contenido.setPadding(new Insets(22));
        VBox.setVgrow(vistas, Priority.ALWAYS);
        principal.setCenter(contenido);
        var escena = new Scene(principal, 1170, 760);
        escena.getStylesheets().add(Objects.requireNonNull(getClass().getResource("monitoreo.css")).toExternalForm());
        ventana.setScene(escena);
        ventana.setTitle("Invernaderos · Centro de monitoreo");
        ventana.setMinWidth(800);
        ventana.setMinHeight(560);
        ventana.show();
        refresco.setCycleCount(Timeline.INDEFINITE);
        refresco.play();
        cargarInventario();
    }

    @Override public void stop() {
        refresco.stop();
        consultas.shutdownNow();
        fotos.cerrar();
    }

    private VBox navegacion() {
        parcela.setMinHeight(Region.USE_PREF_SIZE);
        terrenos.setId("terrenos");
        terrenos.setMaxWidth(Double.MAX_VALUE);
        mostrarNombre(terrenos, Terreno::nombre);
        invernaderos.setId("invernaderos");
        invernaderos.setPlaceholder(new Label("Sin invernaderos activos"));
        invernaderos.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Invernadero finca, boolean vacia) {
                super.updateItem(finca, vacia);
                setText(vacia || finca == null ? null : finca.nombre());
                setWrapText(true);
            }
        });
        terrenos.valueProperty().addListener((o, anterior, terreno) -> {
            invernaderos.getItems().setAll(terreno == null ? List.of() : terreno.invernaderos());
            parcela.setText(terreno == null ? "" : terreno.direccion() + "\n" + numero(terreno.area()) + " m²");
            invernaderos.getSelectionModel().selectFirst();
        });
        invernaderos.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> seleccionar());
        recargar.setOnAction(e -> cargarInventario());
        var menu = new VBox(12, etiqueta("Invernaderos", "brand"), red, new Separator(),
            etiqueta("Terreno", "sidebar-label"), terrenos, parcela, new Separator(),
            etiqueta("Invernaderos del terreno", "sidebar-label"), invernaderos, recargar);
        menu.getStyleClass().add("sidebar");
        menu.setPrefWidth(244);
        menu.setMinWidth(205);
        VBox.setVgrow(invernaderos, Priority.ALWAYS);
        return menu;
    }

    private VBox panelSensores() {
        medir.setId("medir");
        medir.getStyleClass().add("primary");
        medir.setOnAction(e -> medirTodos());
        var cabecera = new HBox(12, etiqueta("Sensores instalados", "section-title"), espacio(), medir);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        tarjetas.setPrefWrapLength(650);
        historial.setId("historial");
        historial.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        historial.setPlaceholder(new Label("Aún no hay mediciones en este invernadero."));
        columna("Fecha y hora", f -> FECHA.format(f.fecha()));
        columna("Sensor", Lectura::sensor);
        columna("Magnitud", Lectura::magnitud);
        columna("Valor", f -> numero(f.valor()));
        columna("Unidad", Lectura::unidad);
        columna("Origen", f -> switch (Objects.toString(f.origen(), "")) {
            case "MANUAL" -> "Manual";
            case "AUTOMATICA" -> "Automática";
            default -> "Sin registrar";
        });
        historial.setPrefHeight(270);
        var cuerpo = new VBox(12, cabecera, etiqueta("Ciclo automático cada hora · Sensores simulados", "muted"),
            tarjetas, formularioIndividual(), etiqueta("Historial reciente", "section-title"), historial);
        cuerpo.setPadding(new Insets(16, 6, 8, 4));
        var scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
        var panel = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return panel;
    }

    private TitledPane formularioIndividual() {
        mostrarNombre(sensorManual, Sensor::codigo);
        mostrarNombre(magnitudManual, Magnitud::nombre);
        // Solo se ofrecen las magnitudes que mide el sensor elegido.
        sensorManual.valueProperty().addListener((o, antes, sensor) -> {
            var previa = magnitudManual.getValue();
            magnitudManual.getItems().setAll(sensor == null ? List.of() : sensor.magnitudes());
            if (previa != null && magnitudManual.getItems().contains(previa)) magnitudManual.setValue(previa);
            else magnitudManual.getSelectionModel().selectFirst();
            botones();
        });
        var valor = new TextField();
        valor.setPromptText("Valor");
        valor.setPrefColumnCount(6);
        registrar.setOnAction(e -> {
            var sensor = sensorManual.getValue();
            var magnitud = magnitudManual.getValue();
            var finca = actual();
            if (sensor == null || magnitud == null || finca == null) return;
            try {
                var lectura = new Medicion(sensor.codigo(), magnitud.codigo(), LocalDateTime.now(java.time.ZoneId.of("America/Guayaquil")),
                    new BigDecimal(valor.getText().trim().replace(',', '.')));
                iniciarMedida();
                ejecutar(() -> new Monitoreo().registrar(lectura), r -> {
                    valor.clear();
                    finMedida(finca, "Lectura guardada · " + (r.alertas().isEmpty() ? "sin alerta nueva."
                        : r.alertas().size() + " alerta(s): " + r.alertas().get(0).recomendacion()));
                }, this::errorMedida);
            } catch (NumberFormatException | ArithmeticException ex) {
                estado.setText("Escribe un valor numérico con un máximo de cuatro decimales.");
            }
        });
        var pane = new TitledPane("Ingresar una lectura individual", new FlowPane(8, 8,
            sensorManual, magnitudManual, valor, registrar));
        pane.setExpanded(false);
        pane.setAnimated(false);
        return pane;
    }

    private void cargarInventario() {
        var previo = actual();
        var terrenoPrevio = terrenos.getValue();
        recargar.setDisable(true);
        estado.setText("Consultando terrenos e invernaderos…");
        ejecutar(inventario::terrenos, datos -> {
            recargar.setDisable(false);
            terrenos.getItems().setAll(datos);
            red.setText(datos.size() + " terrenos · " +
                datos.stream().mapToInt(t -> t.invernaderos().size()).sum() + " invernaderos");
            terrenos.setValue(datos.stream().filter(t -> terrenoPrevio != null && t.id() == terrenoPrevio.id())
                .findFirst().orElse(datos.stream().findFirst().orElse(null)));
            if (previo != null) invernaderos.getItems().stream().filter(i -> i.id() == previo.id())
                .findFirst().ifPresent(i -> invernaderos.getSelectionModel().select(i));
            if (actual() == null) seleccionar();
            else cargarDetalle("");
        }, error -> { recargar.setDisable(false); mostrarError(error); });
    }

    private Invernadero actual() { return invernaderos.getSelectionModel().getSelectedItem(); }

    private void seleccionar() {
        var i = actual();
        peticion++;
        cargando = false;
        detalleDisponible = false;
        botones();
        cultivosVisibles = Map.of();
        tarjetas.getChildren().clear();
        historial.getItems().clear();
        sensorManual.getItems().clear();
        cultivos.setText("");
        var terreno = terrenos.getValue();
        titulo.setText(i == null ? "Selecciona un invernadero" : i.nombre());
        ubicacion.setText(i == null || terreno == null ? "" : terreno.nombre() + "   /   " +
            numero(i.largo()) + " × " + numero(i.ancho()) + " m");
        fotos.setInvernadero(i == null ? "" : i.nombre(), Map.of());
        if (vistas.getSelectionModel().getSelectedIndex() == 1) buzon.actualizar(i == null ? null : i.id());
        if (i != null) cargarDetalle("");
        else estado.setText("Selecciona un terreno con invernaderos activos.");
    }

    private void actualizarVisible() {
        var i = actual();
        if (vistas.getSelectionModel().getSelectedIndex() == 1) {
            buzon.actualizar(i == null ? null : i.id());
        } else if (i != null && !cargando && !midiendo && vistas.getSelectionModel().getSelectedIndex() == 0) {
            cargarDetalle("");
        }
    }

    private void cargarDetalle(String mensaje) {
        var finca = actual();
        if (finca == null) return;
        int solicitud = ++peticion;
        cargando = true;
        ejecutar(() -> inventario.detalle(finca.id()), d -> {
            if (solicitud != peticion) return;
            cargando = false;
            detalleDisponible = true;
            tarjetas.getChildren().clear();
            for (var sensor : d.sensores()) tarjetas.getChildren().add(tarjeta(sensor));
            if (d.sensores().isEmpty()) tarjetas.getChildren().add(new Label("Sin sensores activos."));
            historial.getItems().setAll(d.historial());
            var elegido = sensorManual.getValue();
            sensorManual.getItems().setAll(d.sensores());
            var sensor = d.sensores().stream().filter(s -> elegido != null && s.id() == elegido.id()).findFirst();
            sensorManual.setValue(sensor.orElse(d.sensores().stream().findFirst().orElse(null)));
            Map<Integer,String> mapa = new LinkedHashMap<>();
            d.cultivos().forEach(c -> mapa.put(c.id(), c.nombre()));
            cultivos.setText(d.cultivos().isEmpty() ? "Sin cultivos vigentes" : "Cultivos · " + String.join(", ",
                d.cultivos().stream().map(c -> c.nombre() + (c.cosechaEstimada() == null ? ""
                    : " (cosecha estimada " + FECHA_CORTA.format(c.cosechaEstimada()) + ")")).toList()));
            if (!mapa.equals(cultivosVisibles)) {
                cultivosVisibles = mapa;
                fotos.setInvernadero(finca.nombre(), mapa);
            }
            botones();
            estado.setText(mensaje.isEmpty() ? "Última actualización · " + FECHA.format(LocalDateTime.now(java.time.ZoneId.of("America/Guayaquil"))) : mensaje);
        }, ex -> { if (solicitud == peticion) { cargando = false; mostrarError(ex); } });
    }

    private VBox tarjeta(Sensor sensor) {
        var tarjeta = new VBox(5, etiqueta(sensor.magnitud(), "sensor-name"),
            etiqueta(sensor.valor() == null ? "Sin lectura" : numero(sensor.valor()) + " " +
                Objects.toString(sensor.unidad(), ""), "sensor-value"),
            etiqueta(sensor.codigo() + " · " + sensor.ubicacion(), "muted"),
            etiqueta(sensor.fecha() == null ? "Esperando primera medición" : FECHA.format(sensor.fecha()), "muted"));
        tarjeta.setPrefWidth(205);
        tarjeta.getStyleClass().add("card");
        return tarjeta;
    }

    private void medirTodos() {
        var finca = actual();
        if (finca == null || midiendo) return;
        iniciarMedida();
        ejecutar(() -> simulador.medir(finca.id()), r ->
            finMedida(finca, r.mediciones() + " mediciones guardadas · " + r.alertas() + " alertas nuevas."), this::errorMedida);
    }

    private void iniciarMedida() {
        midiendo = true;
        botones();
        estado.setText("Guardando mediciones…");
    }

    private void finMedida(Invernadero finca, String mensaje) {
        midiendo = false;
        botones();
        if (actual() != null && actual().id() == finca.id()) {
            cargarDetalle(mensaje);
            if (vistas.getSelectionModel().getSelectedIndex() == 1) buzon.actualizar(finca.id());
        }
    }

    private void errorMedida(Throwable error) {
        midiendo = false;
        botones();
        mostrarError(error);
    }

    private void botones() {
        boolean bloquear = midiendo || !detalleDisponible || sensorManual.getItems().isEmpty();
        medir.setDisable(bloquear);
        registrar.setDisable(bloquear || magnitudManual.getItems().isEmpty());
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> exito, Consumer<Throwable> fallo) {
        Task<T> tarea = new Task<>() {
            @Override protected T call() throws Exception { return operacion.call(); }
        };
        tarea.setOnSucceeded(e -> exito.accept(tarea.getValue()));
        tarea.setOnFailed(e -> fallo.accept(tarea.getException()));
        consultas.execute(tarea);
    }

    private void mostrarError(Throwable ex) { estado.setText("No se pudo completar: " + PanelFotos.mensaje(ex)); }

    private void columna(String titulo, Function<Lectura,String> texto) {
        var col = new TableColumn<Lectura,String>(titulo);
        col.setCellValueFactory(f -> new ReadOnlyStringWrapper(texto.apply(f.getValue())));
        historial.getColumns().add(col);
    }

    private static <T> void mostrarNombre(ComboBox<T> combo, Function<T,String> nombre) {
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(T valor) { return valor == null ? "" : nombre.apply(valor); }
            @Override public T fromString(String texto) { throw new UnsupportedOperationException(); }
        });
    }

    private static Region espacio() { var r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    private static Label etiqueta(String texto, String estilo) {
        var label = new Label(texto);
        label.setWrapText(true);
        label.getStyleClass().add(estilo);
        return label;
    }
    private static String numero(BigDecimal valor) { return valor.stripTrailingZeros().toPlainString(); }
}
