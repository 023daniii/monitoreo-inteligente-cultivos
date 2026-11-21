package ec.edu.epn.invernaderos;

import ec.edu.epn.invernaderos.dao.FotoDao;
import ec.edu.epn.invernaderos.dao.FotoDao.Diagnostico;
import ec.edu.epn.invernaderos.dao.FotoDao.Referencia;
import ec.edu.epn.invernaderos.servicio.MemoriaVisual;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Memoria visual: el agricultor sube una foto, Java pide su vector a Python y busca con pgvector
 * las 5 referencias más parecidas. El diagnóstico y la solución se muestran solo al elegir una de ellas.
 */
public final class PanelFotos extends VBox {
    private record Cultivo(int id, String nombre) {
        @Override public String toString() { return nombre; }
    }
    private record Busqueda(int total, List<Referencia> referencias) {}

    private static final Diagnostico SIN_DIAGNOSTICO = new Diagnostico(null, "Sin diagnóstico");
    private final MemoriaVisual memoria = new MemoriaVisual();
    private final FotoDao fotos = new FotoDao();
    private final Path raiz = memoria.raiz();

    private final ImageView vista = new ImageView();
    private final Label archivo = new Label("Foto JPG o PNG de la planta");
    private final Button elegir = new Button("Elegir foto");
    private final Button buscar = new Button("Buscar parecidas");
    private final Label invernadero = new Label();
    private final ComboBox<Cultivo> cultivos = new ComboBox<>();
    private final ComboBox<Diagnostico> diagnosticos = new ComboBox<>();
    private final TextField observacion = new TextField();
    private final Button registrar = new Button("Guardar foto del cultivo");
    private final Label estado = new Label();

    private final Label estadoBusqueda = new Label("Elige una foto y pulsa Buscar parecidas.");
    private final Button cargarReferencias = new Button("Cargar las 200 fotos de referencia");
    private final TilePane resultados = new TilePane(10, 10);
    private final VBox detalle = new VBox(8);
    private final ImageView vistaDetalle = new ImageView();
    private final Label tituloDiagnostico = new Label();
    private final Label descripcion = new Label();
    private final Label recomendacion = new Label();
    private final Label procedencia = new Label();

    private final BooleanProperty procesando = new SimpleBooleanProperty();
    private Path seleccionada;

    public PanelFotos() {
        getStyleClass().add("foto-panel");
        getStylesheets().add(PanelFotos.class.getResource("fotos.css").toExternalForm());

        var marco = new StackPane(vista);
        marco.getStyleClass().add("foto-marco");
        marco.setMinHeight(190);
        vista.setPreserveRatio(true);
        vista.setFitHeight(180);
        vista.fitWidthProperty().bind(marco.widthProperty().subtract(12));
        marco.setOnDragOver(e -> {
            if (!procesando.get() && e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        marco.setOnDragDropped(e -> {
            var lista = e.getDragboard().getFiles();
            e.setDropCompleted(!procesando.get() && lista.size() == 1 && seleccionar(lista.get(0).toPath()));
            e.consume();
        });
        archivo.setWrapText(true);
        var selector = new FileChooser();
        selector.setTitle("Elegir foto de la planta");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fotos JPG y PNG",
            "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
        var consultas = raiz.resolve("datos/consultas").toFile();
        if (consultas.isDirectory()) selector.setInitialDirectory(consultas);
        elegir.setOnAction(e -> {
            var elegido = selector.showOpenDialog(getScene().getWindow());
            if (elegido != null) seleccionar(elegido.toPath());
        });
        buscar.getStyleClass().add("foto-primario");
        buscar.setOnAction(e -> buscar());

        cultivos.setMaxWidth(Double.MAX_VALUE);
        diagnosticos.setMaxWidth(Double.MAX_VALUE);
        diagnosticos.getItems().setAll(SIN_DIAGNOSTICO);
        diagnosticos.setValue(SIN_DIAGNOSTICO);
        observacion.setPromptText("Observación (opcional)");
        registrar.setOnAction(e -> registrar());
        estado.setWrapText(true);
        invernadero.setWrapText(true);
        var formulario = tarjeta(titulo("Foto a consultar"), marco, archivo, new FlowPane(8, 8, elegir, buscar),
            new Separator(), titulo("Guardar en la colección"), invernadero, cultivos,
            new Label("Diagnóstico confirmado"), diagnosticos, observacion, registrar, estado);

        estadoBusqueda.setWrapText(true);
        cargarReferencias.managedProperty().bind(cargarReferencias.visibleProperty());
        cargarReferencias.setVisible(false);
        cargarReferencias.setOnAction(e -> cargar());
        resultados.setPrefColumns(5);
        resultados.setPrefTileWidth(118);
        vistaDetalle.setPreserveRatio(true);
        vistaDetalle.setFitHeight(200);
        vistaDetalle.setFitWidth(300);
        tituloDiagnostico.getStyleClass().add("foto-titulo");
        tituloDiagnostico.setWrapText(true);
        for (var texto : List.of(descripcion, recomendacion, procedencia)) texto.setWrapText(true);
        recomendacion.getStyleClass().add("foto-observacion");
        procedencia.getStyleClass().add("muted");
        var aviso = new Label("La similitud es visual y orienta la revisión; confirma el diagnóstico con un agrónomo.");
        aviso.setWrapText(true);
        aviso.getStyleClass().add("muted");
        detalle.getChildren().addAll(new Separator(), vistaDetalle, tituloDiagnostico, descripcion,
            etiquetaFuerte("Qué hacer"), recomendacion, procedencia, aviso);
        detalle.managedProperty().bind(detalle.visibleProperty());
        detalle.setVisible(false);
        var coincidencias = tarjeta(titulo("Las 5 fotos más parecidas"), estadoBusqueda, cargarReferencias,
            resultados, detalle);

        var cuerpo = new GridPane();
        cuerpo.setHgap(14);
        var izquierda = new ColumnConstraints();
        izquierda.setPercentWidth(38);
        var derecha = new ColumnConstraints();
        derecha.setPercentWidth(62);
        cuerpo.getColumnConstraints().addAll(izquierda, derecha);
        cuerpo.add(formulario, 0, 0);
        cuerpo.add(coincidencias, 1, 0);
        var scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("foto-scroll");
        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        elegir.disableProperty().bind(procesando);
        cargarReferencias.disableProperty().bind(procesando);
        procesando.addListener((o, a, b) -> actualizarBotones());
        cultivos.valueProperty().addListener((o, a, b) -> actualizarBotones());
        setInvernadero(null, Map.of());
        sceneProperty().addListener((o, antes, ahora) -> {
            if (ahora != null && antes == null) cargarDiagnosticos();
        });
    }

    /** Cultivos del invernadero seleccionado: destino posible de una foto propia. */
    public void setInvernadero(String nombre, Map<Integer, String> disponibles) {
        var anterior = cultivos.getValue();
        var nuevos = disponibles.entrySet().stream().map(e -> new Cultivo(e.getKey(), e.getValue())).toList();
        if (!cultivos.getItems().equals(nuevos)) {
            cultivos.getItems().setAll(nuevos);
            if (nuevos.contains(anterior)) cultivos.setValue(anterior);
            else cultivos.getSelectionModel().selectFirst();
        }
        invernadero.setText(nombre == null || nombre.isEmpty() ? "Selecciona un invernadero para guardar fotos."
            : "Cultivo de " + nombre);
        cultivos.setPromptText("Sin cultivos vigentes");
        actualizarBotones();
    }

    public void cerrar() { memoria.close(); }

    private boolean seleccionar(Path ruta) {
        String nombre = ruta.getFileName().toString();
        if (!nombre.toLowerCase(Locale.ROOT).matches(".+\\.(jpg|jpeg|png)")) {
            estado.setText("Elige una foto JPG o PNG.");
            return false;
        }
        var imagen = new Image(ruta.toUri().toString(), 480, 480, true, true);
        if (imagen.isError()) {
            estado.setText("No se pudo leer la imagen.");
            return false;
        }
        seleccionada = ruta.toAbsolutePath();
        vista.setImage(imagen);
        archivo.setText(nombre);
        observacion.clear();
        resultados.getChildren().clear();
        detalle.setVisible(false);
        estadoBusqueda.setText("Pulsa Buscar parecidas para comparar esta foto.");
        estado.setText("");
        actualizarBotones();
        return true;
    }

    private void actualizarBotones() {
        buscar.setDisable(procesando.get() || seleccionada == null);
        registrar.setDisable(procesando.get() || seleccionada == null || cultivos.getValue() == null);
    }

    private void cargarDiagnosticos() {
        ejecutar(fotos::diagnosticos, lista -> {
            diagnosticos.getItems().setAll(SIN_DIAGNOSTICO);
            diagnosticos.getItems().addAll(lista);
            diagnosticos.setValue(SIN_DIAGNOSTICO);
        }, error -> estado.setText("No se pudieron leer los diagnósticos: " + mensaje(error)), false);
    }

    private void buscar() {
        var foto = seleccionada;
        resultados.getChildren().clear();
        detalle.setVisible(false);
        cargarReferencias.setVisible(false);
        estadoBusqueda.setText("Calculando el vector de la foto y comparando con pgvector…");
        ejecutar(() -> {
            int total = fotos.totalReferencias();
            if (total == 0) return new Busqueda(0, List.of());
            return new Busqueda(total, fotos.buscarSimilares(memoria.vector(foto)));
        }, busqueda -> {
            if (busqueda.total() == 0) {
                estadoBusqueda.setText("La colección todavía no tiene fotos de referencia.");
                cargarReferencias.setVisible(true);
                return;
            }
            for (var referencia : busqueda.referencias()) resultados.getChildren().add(miniatura(referencia));
            estadoBusqueda.setText("Comparada con " + busqueda.total() + " fotos. Elige la que más se parezca "
                + "a tu planta para ver su diagnóstico.");
        }, error -> estadoBusqueda.setText("No se pudo buscar: " + mensaje(error)), true);
    }

    private ToggleButton miniatura(Referencia referencia) {
        var mini = new ImageView();
        mini.setFitWidth(106);
        mini.setFitHeight(90);
        mini.setPreserveRatio(true);
        var ruta = raiz.resolve(referencia.ruta()).normalize();
        if (ruta.startsWith(raiz) && Files.isRegularFile(ruta)) mini.setImage(new Image(ruta.toUri().toString(), 160, 160, true, true));
        var parecido = new Label(String.format(Locale.ROOT, "Similitud %.0f %%", referencia.similitud() * 100));
        var caja = new VBox(4, mini, parecido);
        caja.setAlignment(Pos.CENTER);
        var boton = new ToggleButton();
        boton.setGraphic(caja);
        boton.getStyleClass().add("foto-referencia");
        boton.setAccessibleText("Foto parecida, " + parecido.getText());
        boton.setOnAction(e -> {
            resultados.getChildren().forEach(n -> { if (n != boton) ((ToggleButton) n).setSelected(false); });
            boton.setSelected(true);
            mostrarDiagnostico(referencia, ruta);
        });
        return boton;
    }

    private void mostrarDiagnostico(Referencia referencia, Path ruta) {
        ejecutar(() -> fotos.detalle(referencia.id()), d -> {
            if (d == null) return;
            vistaDetalle.setImage(Files.isRegularFile(ruta) ? new Image(ruta.toUri().toString(), 600, 600, true, true) : null);
            tituloDiagnostico.setText(d.diagnostico() == null ? "Foto sin diagnóstico registrado" : d.diagnostico());
            descripcion.setText(d.descripcion() == null ? (d.etiqueta() == null ? "" : d.etiqueta()) : d.descripcion());
            recomendacion.setText(d.recomendacion() == null ? "Sin recomendación registrada." : d.recomendacion());
            procedencia.setText("DATASET".equals(d.origen())
                ? "Fuente: " + d.fuente() + " · Licencia " + d.licencia()
                : "Foto propia del cultivo " + d.cultivo() + (d.etiqueta() == null ? "" : " · " + d.etiqueta()));
            detalle.setVisible(true);
        }, error -> estadoBusqueda.setText("No se pudo leer el diagnóstico: " + mensaje(error)), false);
    }

    private void cargar() {
        estadoBusqueda.setText("Cargando las 200 fotos de referencia. La primera vez se descarga CLIP (338 MiB) "
            + "y puede tardar varios minutos…");
        ejecutar(memoria::cargarReferencias, r -> {
            estadoBusqueda.setText(r.get("nuevas").getAsInt() + " fotos registradas de " + r.get("total").getAsInt() + ".");
            if (seleccionada != null) buscar();
        }, error -> estadoBusqueda.setText("No se pudieron cargar: " + mensaje(error)), true);
    }

    private void registrar() {
        var destino = cultivos.getValue();
        var diagnostico = diagnosticos.getValue();
        String nota = observacion.getText().strip();
        if (nota.codePointCount(0, nota.length()) > 120) {
            estado.setText("La observación admite hasta 120 caracteres.");
            return;
        }
        estado.setText("Calculando el vector y guardando la foto…");
        var foto = seleccionada;
        ejecutar(() -> memoria.registrar(foto, destino.id(), diagnostico == null ? null : diagnostico.codigo(), nota),
            r -> estado.setText(r.get("nueva").getAsBoolean() ? "Foto guardada en " + destino.nombre() + "."
                : "Esa foto ya estaba en la colección (ID " + r.get("id").getAsLong() + ")."),
            error -> estado.setText("No se pudo guardar: " + mensaje(error)), true);
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> exito, Consumer<Throwable> fallo, boolean bloquear) {
        if (bloquear) procesando.set(true);
        var tarea = new Task<T>() {
            @Override protected T call() throws Exception { return operacion.call(); }
        };
        tarea.setOnSucceeded(e -> { if (bloquear) procesando.set(false); exito.accept(tarea.getValue()); });
        tarea.setOnFailed(e -> { if (bloquear) procesando.set(false); fallo.accept(tarea.getException()); });
        var hilo = new Thread(tarea, "memoria-visual");
        hilo.setDaemon(true);
        hilo.start();
    }

    static String mensaje(Throwable error) {
        String texto = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return texto.lines().findFirst().orElse(texto).replaceFirst("^ERROR: ", "");
    }

    private static Label titulo(String texto) {
        var titulo = new Label(texto);
        titulo.getStyleClass().add("foto-titulo");
        return titulo;
    }

    private static Label etiquetaFuerte(String texto) {
        var etiqueta = new Label(texto);
        etiqueta.setStyle("-fx-font-weight: bold;");
        return etiqueta;
    }

    private static VBox tarjeta(Node... nodos) {
        var tarjeta = new VBox(10, nodos);
        tarjeta.getStyleClass().add("foto-tarjeta");
        tarjeta.setMinWidth(0);
        return tarjeta;
    }
}
