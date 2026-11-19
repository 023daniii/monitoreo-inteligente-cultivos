package ec.edu.epn.invernaderos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

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
public final class PanelFotos extends VBox {
    private record Cultivo(int id, String nombre) {
        @Override public String toString() { return nombre; }
    }

    private final Label invernadero = new Label();
    private final ComboBox<Cultivo> cultivos = new ComboBox<>();
    private final TextField etiqueta = new TextField();
    private final ImageView vista = new ImageView();
    private final Label archivo = new Label("JPG o PNG");
    private final Button elegir = new Button("Elegir foto");
    private final Button buscar = new Button("Buscar similares");
    private final Button registrar = new Button("Registrar foto propia");
    private final VBox referencias = new VBox(6);
    private final Label observacion = new Label();
    private final Label estado = new Label();
    private final BooleanProperty procesando = new SimpleBooleanProperty();
    private final Path raiz = encontrarRaiz();
    private Path seleccionada;

    public PanelFotos() {
        getStyleClass().add("foto-panel");
        getStylesheets().add(PanelFotos.class.getResource("fotos.css").toExternalForm());
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
        buscar.setOnAction(evento -> buscar());
        registrar.setOnAction(evento -> registrar());
        cultivos.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setPromptText("Observación de la foto (opcional)");
        var formulario = tarjeta(titulo, marco, archivo, acciones, new Separator(), invernadero,
            cultivos, etiqueta, registrar);
        formulario.setAlignment(Pos.TOP_LEFT);

        observacion.setWrapText(true);
        observacion.getStyleClass().add("foto-observacion");
        observacion.managedProperty().bind(observacion.visibleProperty());
        observacion.setVisible(false);
        referencias.getChildren().add(new Label("Elige una foto para buscar referencias."));
        var coincidencias = tarjeta(titulo("Referencias similares"), observacion, referencias);
        var cuerpo = new GridPane();
        cuerpo.setHgap(14);
        cuerpo.setVgap(14);
        var izquierda = new ColumnConstraints();
        var derecha = new ColumnConstraints();
        cuerpo.getColumnConstraints().addAll(izquierda, derecha);
        cuerpo.add(formulario, 0, 0);
        cuerpo.add(coincidencias, 1, 0);
        var scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
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

        estado.setWrapText(true);
        elegir.disableProperty().bind(procesando);
        procesando.addListener((obs, anterior, actual) -> actualizarBotones());
        cultivos.valueProperty().addListener((obs, anterior, actual) -> actualizarBotones());
        setInvernadero(null, Map.of());
        getChildren().addAll(scroll, estado);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        actualizarBotones();
    }

    /** La selección actual del explorador determina el destino de una foto propia. */
    public void setInvernadero(String nombre, Map<Integer, String> disponibles) {
        var anterior = cultivos.getValue();
        var nuevos = disponibles.entrySet().stream()
            .map(entrada -> new Cultivo(entrada.getKey(), entrada.getValue())).toList();
        if (!cultivos.getItems().equals(nuevos)) {
            cultivos.getItems().setAll(nuevos);
            if (nuevos.contains(anterior)) cultivos.setValue(anterior);
            else cultivos.getSelectionModel().selectFirst();
        }
        invernadero.setText(nombre == null ? "Selecciona un invernadero para registrar fotos."
            : "Cultivo en " + nombre);
        cultivos.setPromptText("Sin cultivos disponibles");
    }

    private static Label titulo(String texto) {
        var titulo = new Label(texto);
        titulo.getStyleClass().add("foto-titulo");
        return titulo;
    }

    private static VBox tarjeta(javafx.scene.Node... nodos) {
        var tarjeta = new VBox(10, nodos);
        tarjeta.getStyleClass().add("foto-tarjeta");
        tarjeta.setMinWidth(0);
        return tarjeta;
    }

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
        estado.setText("Foto lista para buscar o registrar.");
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

    private void buscar() {
        referencias.getChildren().clear();
        observacion.setVisible(false);
        ejecutar("Comparando con la colección mediante CLIP…", respuesta -> {
            JsonArray filas = respuesta.getAsJsonArray();
            var grupo = new ToggleGroup();
            for (var fila : filas) referencias.getChildren().add(opcion(fila.getAsJsonObject(), grupo));
            if (filas.isEmpty()) {
                estado.setText("La colección está vacía. Registra una foto de referencia.");
            } else {
                estado.setText("Selecciona una referencia para ver su observación.");
            }
        }, "buscar", seleccionada.toString());
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
        ejecutar("Generando el vector y guardando la foto…", respuesta -> {
            var fila = respuesta.getAsJsonObject();
            estado.setText(fila.get("nueva").getAsBoolean() ? "Foto registrada en " + destino.nombre() + "."
                : "Esa foto ya está en la colección (ID " + fila.get("id").getAsLong() + ").");
        }, "registrar", seleccionada.toString(), "--cultivo", Integer.toString(destino.id()), "--etiqueta", nota);
    }

    private void ejecutar(String mensaje, Consumer<JsonElement> terminado, String... argumentos) {
        procesando.set(true);
        estado.setText(mensaje);
        var tarea = new Task<JsonElement>() {
            @Override protected JsonElement call() throws Exception { return python(argumentos); }
        };
        tarea.setOnSucceeded(evento -> {
            procesando.set(false);
            terminado.accept(tarea.getValue());
        });
        tarea.setOnFailed(evento -> {
            procesando.set(false);
            estado.setText("Memoria visual: " + tarea.getException().getMessage());
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
        var log = Files.createTempFile("memoria-visual-", ".log");
        try {
            var hijo = proceso.redirectError(log.toFile()).start();
            String salida = new String(hijo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (hijo.waitFor() != 0) {
                throw new IOException(mensajePython(log));
            }
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
}
