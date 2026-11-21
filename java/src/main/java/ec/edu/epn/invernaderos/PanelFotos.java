package ec.edu.epn.invernaderos;

<<<<<<< HEAD
import ec.edu.epn.invernaderos.dao.FotoDao;
import ec.edu.epn.invernaderos.dao.FotoDao.Diagnostico;
import ec.edu.epn.invernaderos.dao.FotoDao.Referencia;
import ec.edu.epn.invernaderos.servicio.MemoriaVisual;
=======
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
<<<<<<< HEAD
import javafx.scene.Node;
=======
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

<<<<<<< HEAD
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
=======
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.MalformedInputException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Registro de fotos del cultivo seleccionado y consulta de la colección compartida. */
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
public final class PanelFotos extends VBox {
    private record Cultivo(int id, String nombre) {
        @Override public String toString() { return nombre; }
    }
<<<<<<< HEAD
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
=======

    private final Label invernadero = new Label();
    private final ComboBox<Cultivo> cultivos = new ComboBox<>();
    private final TextField etiqueta = new TextField();
    private final ImageView vista = new ImageView();
    private final Label archivo = new Label("JPG o PNG");
    private final Button elegir = new Button("Elegir foto");
    private final Button buscar = new Button("Buscar similares");
    private final Button registrar = new Button("Registrar foto propia");
    private final Button cargarDemo = new Button("Cargar las 5 referencias de ejemplo");
    private final Label estadoBusqueda = new Label("Elige una foto para buscar referencias.");
    private final VBox referencias = new VBox(6);
    private final Label observacion = new Label();
    private final Label estado = new Label();
    private final BooleanProperty procesando = new SimpleBooleanProperty();
    private final Path raiz = encontrarRaiz();
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
    private Path seleccionada;

    public PanelFotos() {
        getStyleClass().add("foto-panel");
        getStylesheets().add(PanelFotos.class.getResource("fotos.css").toExternalForm());
<<<<<<< HEAD

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
=======
        var titulo = titulo("Foto del cultivo");
        var marco = new StackPane(vista);
        marco.getStyleClass().add("foto-marco");
        marco.setMinHeight(170);
        marco.setPrefHeight(170);
        vista.setPreserveRatio(true);
        vista.setFitHeight(164);
        vista.fitWidthProperty().bind(marco.widthProperty().subtract(12));
        archivo.setWrapText(true);
        var acciones = new FlowPane(8, 8, elegir, buscar);
        var selector = new FileChooser();
        selector.setTitle("Elegir foto de cultivo");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Imágenes JPEG y PNG", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
        elegir.setOnAction(evento -> {
            var archivoElegido = selector.showOpenDialog(getScene().getWindow());
            if (archivoElegido != null) seleccionar(archivoElegido.toPath());
        });
        marco.setOnDragOver(evento -> {
            if (!procesando.get() && evento.getDragboard().hasFiles())
                evento.acceptTransferModes(TransferMode.COPY);
            evento.consume();
        });
        marco.setOnDragDropped(evento -> {
            var archivos = evento.getDragboard().getFiles();
            boolean aceptada = !procesando.get() && archivos.size() == 1 && seleccionar(archivos.get(0).toPath());
            evento.setDropCompleted(aceptada);
            evento.consume();
        });
        buscar.getStyleClass().add("foto-primario");
        buscar.setOnAction(evento -> buscar(false));
        cargarDemo.setOnAction(evento -> buscar(true));
        cargarDemo.managedProperty().bind(cargarDemo.visibleProperty());
        cargarDemo.disableProperty().bind(procesando);
        cargarDemo.setVisible(false);
        registrar.setOnAction(evento -> registrar());
        cultivos.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setPromptText("Observación de la foto (opcional)");
        estado.setWrapText(true);
        estado.managedProperty().bind(estado.textProperty().isNotEmpty());
        var formulario = tarjeta(titulo, marco, archivo, acciones, new Separator(), invernadero,
            cultivos, etiqueta, registrar, estado);
        formulario.setAlignment(Pos.TOP_LEFT);

        observacion.setWrapText(true);
        observacion.getStyleClass().add("foto-observacion");
        observacion.managedProperty().bind(observacion.visibleProperty());
        observacion.setVisible(false);
        estadoBusqueda.setWrapText(true);
        var coincidencias = tarjeta(titulo("Referencias similares"), estadoBusqueda, cargarDemo, observacion, referencias);
        var cuerpo = new GridPane();
        cuerpo.setHgap(14);
        cuerpo.setVgap(14);
        var izquierda = new ColumnConstraints();
        var derecha = new ColumnConstraints();
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
        cuerpo.getColumnConstraints().addAll(izquierda, derecha);
        cuerpo.add(formulario, 0, 0);
        cuerpo.add(coincidencias, 1, 0);
        var scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
<<<<<<< HEAD
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
=======
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("foto-scroll");
        scroll.viewportBoundsProperty().addListener((obs, anterior, actual) -> {
            boolean apilar = actual.getWidth() < 640;
            izquierda.setPercentWidth(apilar ? 100 : 43);
            derecha.setPercentWidth(apilar ? 0 : 57);
            cuerpo.setHgap(apilar ? 0 : 14);
            GridPane.setColumnIndex(coincidencias, apilar ? 0 : 1);
            GridPane.setRowIndex(coincidencias, apilar ? 1 : 0);
        });

        elegir.disableProperty().bind(procesando);
        procesando.addListener((obs, anterior, actual) -> actualizarBotones());
        cultivos.valueProperty().addListener((obs, anterior, actual) -> actualizarBotones());
        setInvernadero(null, Map.of());
        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        actualizarBotones();
    }

    /** La selección actual del explorador determina el destino de una foto propia. */
    public void setInvernadero(String nombre, Map<Integer, String> disponibles) {
        var anterior = cultivos.getValue();
        var nuevos = disponibles.entrySet().stream()
            .map(entrada -> new Cultivo(entrada.getKey(), entrada.getValue())).toList();
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
        if (!cultivos.getItems().equals(nuevos)) {
            cultivos.getItems().setAll(nuevos);
            if (nuevos.contains(anterior)) cultivos.setValue(anterior);
            else cultivos.getSelectionModel().selectFirst();
        }
<<<<<<< HEAD
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
=======
        invernadero.setText(nombre == null ? "Selecciona un invernadero para registrar fotos."
            : "Cultivo en " + nombre);
        cultivos.setPromptText("Sin cultivos disponibles");
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
    }

    private static Label titulo(String texto) {
        var titulo = new Label(texto);
        titulo.getStyleClass().add("foto-titulo");
        return titulo;
    }

<<<<<<< HEAD
    private static Label etiquetaFuerte(String texto) {
        var etiqueta = new Label(texto);
        etiqueta.setStyle("-fx-font-weight: bold;");
        return etiqueta;
    }

    private static VBox tarjeta(Node... nodos) {
=======
    private static VBox tarjeta(javafx.scene.Node... nodos) {
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
        var tarjeta = new VBox(10, nodos);
        tarjeta.getStyleClass().add("foto-tarjeta");
        tarjeta.setMinWidth(0);
        return tarjeta;
    }
<<<<<<< HEAD
=======

    private boolean seleccionar(Path ruta) {
        String nombre = ruta.getFileName().toString();
        if (!nombre.toLowerCase(Locale.ROOT).matches(".+\\.(jpg|jpeg|png)")) {
            estado.setText("Elige una foto en formato JPG o PNG.");
            return false;
        }
        var imagen = imagen(ruta, 480);
        if (imagen.isError()) {
            estado.setText("No se pudo leer la imagen. Comprueba que sea una foto JPG o PNG válida.");
            return false;
        }
        seleccionada = ruta.toAbsolutePath();
        vista.setImage(imagen);
        archivo.setText(nombre);
        etiqueta.clear();
        referencias.getChildren().clear();
        observacion.setVisible(false);
        cargarDemo.setVisible(false);
        estadoBusqueda.setText("Pulsa Buscar similares para comparar esta foto.");
        estado.setText("");
        actualizarBotones();
        return true;
    }

    private static Image imagen(Path ruta, double ancho) {
        return new Image(ruta.toUri().toString(), ancho, ancho, true, true);
    }

    private void actualizarBotones() {
        buscar.setDisable(procesando.get() || seleccionada == null);
        registrar.setDisable(procesando.get() || seleccionada == null || cultivos.getValue() == null);
    }

    private void buscar(boolean conDemo) {
        referencias.getChildren().clear();
        observacion.setVisible(false);
        String mensaje = conDemo ? "Preparando las cinco referencias y comparando tu foto. "
            + "La primera vez, CLIP descarga el modelo (338 MiB)…"
            : "Consultando la colección de fotografías…";
        ejecutar(estadoBusqueda, mensaje, respuesta -> {
            JsonArray filas = respuesta.getAsJsonArray();
            var grupo = new ToggleGroup();
            for (var fila : filas) referencias.getChildren().add(opcion(fila.getAsJsonObject(), grupo));
            cargarDemo.setVisible(filas.size() < 5 && !conDemo);
            if (filas.isEmpty()) {
                estadoBusqueda.setText(conDemo
                    ? "Las imágenes de ejemplo ya están registradas, pero ninguna está activa para este modelo. "
                        + "Puedes registrar una foto propia."
                    : "La colección aún no tiene referencias activas para CLIP. Carga los ejemplos o registra una foto propia.");
            } else {
                estadoBusqueda.setText("Selecciona una referencia para ver su observación.");
            }
        }, conDemo ? "buscar-demo" : "buscar", seleccionada.toString());
    }

    private RadioButton opcion(JsonObject fila, ToggleGroup grupo) {
        var ruta = raiz.resolve(fila.get("ruta_imagen").getAsString()).normalize();
        var mini = new ImageView();
        mini.setFitWidth(74);
        mini.setFitHeight(66);
        mini.setPreserveRatio(true);
        if (ruta.startsWith(raiz) && Files.isRegularFile(ruta)) mini.setImage(imagen(ruta, 100));
        var observada = texto(fila, "etiqueta", "");
        var fuente = texto(fila, "fuente", fila.get("origen").getAsString());
        var nombre = ruta.getFileName().toString();
        var detalle = new Label(String.format("Distancia coseno %.4f", fila.get("distancia").getAsDouble()));
        var procedencia = new Label(fuente);
        var informacion = new VBox(3, new Label(nombre), detalle, procedencia);
        informacion.setAlignment(Pos.CENTER_LEFT);
        informacion.setMinWidth(0);
        var grafico = new HBox(10, mini, informacion);
        grafico.setAlignment(Pos.CENTER_LEFT);
        var opcion = new RadioButton();
        opcion.getStyleClass().add("foto-referencia");
        opcion.setMaxWidth(Double.MAX_VALUE);
        opcion.setToggleGroup(grupo);
        opcion.setGraphic(grafico);
        opcion.setAccessibleText(nombre + ", " + detalle.getText());
        opcion.setOnAction(evento -> {
            observacion.setText(nombre + "\n" + (observada.isBlank() ? "Sin observación registrada."
                : "Observación registrada: " + observada));
            observacion.setVisible(true);
        });
        return opcion;
    }

    private static String texto(JsonObject fila, String campo, String defecto) {
        var valor = fila.get(campo);
        return valor == null || valor.isJsonNull() ? defecto : valor.getAsString();
    }

    private void registrar() {
        var destino = cultivos.getValue();
        String nota = etiqueta.getText().strip();
        if (nota.codePointCount(0, nota.length()) > 120) {
            estado.setText("La observación admite hasta 120 caracteres.");
            return;
        }
        ejecutar(estado, "Generando el vector y guardando la foto…", respuesta -> {
            var fila = respuesta.getAsJsonObject();
            estado.setText(fila.get("nueva").getAsBoolean() ? "Foto registrada en " + destino.nombre() + "."
                : "Esa foto ya está en la colección (ID " + fila.get("id").getAsLong() + ").");
        }, "registrar", seleccionada.toString(), "--cultivo", Integer.toString(destino.id()), "--etiqueta", nota);
    }

    private void ejecutar(Label aviso, String mensaje, Consumer<JsonElement> terminado, String... argumentos) {
        procesando.set(true);
        aviso.setText(mensaje);
        var tarea = new Task<JsonElement>() {
            @Override protected JsonElement call() throws Exception { return python(argumentos); }
        };
        tarea.setOnSucceeded(evento -> {
            procesando.set(false);
            terminado.accept(tarea.getValue());
        });
        tarea.setOnFailed(evento -> {
            procesando.set(false);
            aviso.setText("Memoria visual: " + tarea.getException().getMessage());
        });
        var hilo = new Thread(tarea, "memoria-visual");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static Path encontrarRaiz() {
        var actual = Path.of("").toAbsolutePath().normalize();
        var lugares = new ArrayList<Path>();
        lugares.add(actual);
        try {
            lugares.add(Path.of(PanelFotos.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (URISyntaxException ignorada) {
            // El directorio de trabajo sigue disponible para localizar el proyecto.
        }
        for (var lugar : lugares) {
            for (var candidato = lugar; candidato != null; candidato = candidato.getParent()) {
                if (Files.isRegularFile(candidato.resolve("python/memoria_visual/escritorio.py")))
                    return candidato;
            }
        }
        return actual;
    }

    private JsonElement python(String... args) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        var local = raiz.resolve(windows ? ".venv/Scripts/python.exe" : ".venv/bin/python");
        String ejecutable = System.getenv().getOrDefault("INVERNADEROS_PYTHON",
            Files.isRegularFile(local) ? local.toString() : (windows ? "python" : "python3"));
        var comando = new ArrayList<>(List.of(ejecutable, "-m", "memoria_visual.escritorio"));
        comando.addAll(List.of(args));
        var proceso = new ProcessBuilder(comando).directory(raiz.toFile());
        proceso.environment().put("PYTHONPATH", raiz.resolve("python").toString());
        proceso.environment().put("PYTHONIOENCODING", "utf-8");
        String preparar = "Desde la carpeta del proyecto, ejecuta "
            + (windows ? "py -3.12 .\\python\\preparar.py" : "python3.12 python/preparar.py")
            + " y vuelve a intentarlo.";
        if (System.getenv("INVERNADEROS_PYTHON") != null) {
            preparar += " Revisa también el intérprete elegido en INVERNADEROS_PYTHON.";
        }
        var log = Files.createTempFile("memoria-visual-", ".log");
        try {
            Process hijo;
            try {
                hijo = proceso.redirectError(log.toFile()).start();
            } catch (IOException error) {
                throw new IOException("No se pudo iniciar " + ejecutable + ". " + preparar, error);
            }
            String salida = new String(hijo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int codigo = hijo.waitFor();
            if (codigo != 0) {
                String error = mensajePython(log);
                if (codigo == 9009 || error.contains("ModuleNotFoundError:")) error += "\n" + preparar;
                throw new IOException(error);
            }
            if (salida.isBlank()) throw new IOException(mensajePython(log) + "\n" + preparar);
            return JsonParser.parseString(salida);
        } finally {
            Files.deleteIfExists(log);
        }
    }

    static String mensajePython(Path log) throws IOException {
        String mensaje;
        try {
            mensaje = Files.readString(log, StandardCharsets.UTF_8);
        } catch (MalformedInputException error) {
            // El lanzador de Windows puede fallar antes de que Python aplique PYTHONIOENCODING.
            mensaje = new String(Files.readAllBytes(log), Charset.forName("windows-1252"));
        }
        return mensaje.lines().filter(linea -> !linea.isBlank()).reduce((a, b) -> b)
            .orElse("Python terminó sin devolver un resultado.");
    }
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
}
