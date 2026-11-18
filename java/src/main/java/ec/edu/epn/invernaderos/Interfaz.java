package ec.edu.epn.invernaderos;

import com.google.gson.*;
import ec.edu.epn.invernaderos.config.Conexion;
import ec.edu.epn.invernaderos.modelo.Medicion;
import ec.edu.epn.invernaderos.servicio.Monitoreo;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class Interfaz extends Application {
    private final TabPane vistas = new TabPane();
    private final Label estado = new Label();
    private final ComboBox<String> sensores = new ComboBox<>(), magnitudes = new ComboBox<>();
    private final ComboBox<List<String>> cultivos = new ComboBox<>();
    private final Monitoreo monitoreo = new Monitoreo();
    private final TableView<List<String>> lecturas = tabla("Fecha", "Invernadero", "Sensor", "Magnitud", "Valor", "Unidad");
    private final TableView<List<String>> alertas = tabla("ID", "Fecha", "Cultivo", "Sensor", "Severidad", "Recomendación", "Estado");
    private Path foto;
    private final Path raiz = Files.isDirectory(Path.of("python/memoria_visual"))
        ? Path.of("").toAbsolutePath() : Path.of("..").toAbsolutePath().normalize();

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage ventana) {
        vistas.getTabs().addAll(tab("Monitoreo", panelMonitoreo()), tab("Alertas", panelAlertas()),
                                tab("Memoria visual", panelFotos(ventana)));
        var actualizar = new Button("Actualizar datos");
        actualizar.disableProperty().bind(vistas.disableProperty());
        actualizar.setOnAction(e -> actualizar());
        var barra = new HBox(16, actualizar, estado);
        barra.setPadding(new Insets(12));
        var raizVentana = new BorderPane(vistas);
        raizVentana.setBottom(barra);
        ventana.setTitle("Monitoreo de invernaderos");
        ventana.setScene(new Scene(raizVentana, 1050, 650));
        ventana.show();
        actualizar();
    }

    private Tab tab(String titulo, VBox panel) {
        panel.setPadding(new Insets(18));
        var tab = new Tab(titulo, panel);
        tab.setClosable(false);
        return tab;
    }

    private static TableView<List<String>> tabla(String... titulos) {
        var tabla = new TableView<List<String>>();
        for (int i = 0; i < titulos.length; i++) {
            int columna = i;
            var campo = new TableColumn<List<String>, String>(titulos[i]);
            campo.setCellValueFactory(fila -> new ReadOnlyStringWrapper(fila.getValue().get(columna)));
            tabla.getColumns().add(campo);
        }
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("Sin registros"));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return tabla;
    }

    private VBox panelMonitoreo() {
        var valor = new TextField();
        valor.setPromptText("Valor medido");
        var guardar = new Button("Registrar lectura");
        guardar.setOnAction(e -> {
            try {
                if (sensores.getValue() == null || magnitudes.getValue() == null)
                    throw new IllegalArgumentException("Selecciona sensor y magnitud.");
                var lectura = new Medicion(sensores.getValue(), magnitudes.getValue(), LocalDateTime.now(),
                    new BigDecimal(valor.getText().trim().replace(',', '.')));
                tarea("Registrando lectura…", () -> monitoreo.registrar(lectura), r -> {
                    valor.clear();
                    cargarHistorial("Medición " + r.idMedicion() + " registrada. Alertas: " + r.alertas().size());
                });
            } catch (NumberFormatException | ArithmeticException error) {
                estado.setText("Ingresa un número con hasta cuatro decimales.");
            } catch (IllegalArgumentException error) { estado.setText(error.getMessage()); }
        });
        return new VBox(12, new Label("Simular lectura"), new FlowPane(8, 8,
            new Label("Sensor"), sensores, new Label("Magnitud"), magnitudes, valor, guardar),
            new Label("Últimas 200 mediciones"), lecturas);
    }

    private VBox panelAlertas() {
        var resolver = new Button("Resolver alerta seleccionada");
        resolver.setOnAction(e -> {
            var fila = alertas.getSelectionModel().getSelectedItem();
            if (fila == null) { estado.setText("Selecciona una alerta."); return; }
            tarea("Resolviendo alerta…", () -> monitoreo.resolver(Long.parseLong(fila.get(0))),
                ok -> cargarHistorial(ok ? "Alerta resuelta." : "La alerta ya está resuelta o no admite resolución."));
        });
        return new VBox(12, new Label("Alertas pendientes y últimas resueltas"), alertas, resolver);
    }

    private static List<List<String>> consultar(String sql) throws Exception {
        try (var c = Conexion.abrir(); var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            List<List<String>> filas = new ArrayList<>();
            while (rs.next()) {
                List<String> fila = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) fila.add(rs.getString(i));
                filas.add(fila);
            }
            return filas;
        }
    }

    private void actualizar() {
        tarea("Conectando a PostgreSQL…", () -> List.of(
            consultar("SELECT codigo FROM sensor WHERE estado ORDER BY codigo"),
            consultar("SELECT h.codigo FROM catalogo h JOIN catalogo p ON p.id_catalogo=h.id_padre WHERE p.codigo='CAT_TIPO_MEDICION' AND h.estado ORDER BY h.codigo"),
            consultar("SELECT id_cultivo, nombre FROM cultivo ORDER BY nombre, id_cultivo")
        ), datos -> {
            sensores.getItems().setAll(datos.get(0).stream().map(f -> f.get(0)).toList());
            magnitudes.getItems().setAll(datos.get(1).stream().map(f -> f.get(0)).toList());
            sensores.getSelectionModel().selectFirst();
            magnitudes.getSelectionModel().selectFirst();
            if (magnitudes.getItems().contains("MED_TEMP_AMB")) magnitudes.setValue("MED_TEMP_AMB");
            cultivos.getItems().setAll(datos.get(2));
            cultivos.getSelectionModel().selectFirst();
            cargarHistorial("Datos actualizados.");
        });
    }

    private void cargarHistorial(String mensaje) {
        tarea("Consultando historial…", () -> List.of(consultar("""
            SELECT m.fecha_hora, i.nombre, s.codigo, t.nombre, m.valor, t.unidad
            FROM medicion m JOIN sensor s USING(id_sensor) JOIN invernadero i USING(id_invernadero)
            JOIN catalogo t ON t.id_catalogo=m.id_tipo_medicion
            ORDER BY m.fecha_hora DESC, m.id_medicion DESC LIMIT 200
            """), consultar("""
            SELECT a.id_alerta, a.fecha_hora, c.nombre, s.codigo, sev.nombre, r.recomendacion, est.nombre
            FROM alerta a JOIN cultivo c USING(id_cultivo) JOIN regla r USING(id_regla)
            JOIN medicion m USING(id_medicion) JOIN sensor s USING(id_sensor)
            JOIN catalogo sev ON sev.id_catalogo=r.id_severidad JOIN catalogo est ON est.id_catalogo=a.id_estado
            ORDER BY (a.fecha_resolucion IS NULL) DESC, a.id_alerta DESC
            """)), datos -> {
            lecturas.getItems().setAll(datos.get(0));
            alertas.getItems().setAll(datos.get(1));
            estado.setText(mensaje);
        });
    }

    private VBox panelFotos(Stage ventana) {
        var elegir = new Button("Elegir foto");
        var buscar = new Button("Buscar similares");
        var guardar = new Button("Registrar foto propia");
        var etiqueta = new TextField();
        etiqueta.setPromptText("Etiqueta observada (opcional)");
        var vista = new ImageView();
        vista.setFitHeight(115);
        vista.setPreserveRatio(true);
        var resultados = new VBox(8);
        var scroll = new ScrollPane(resultados);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        var orientacion = new Label();
        orientacion.setWrapText(true);
        elegir.setOnAction(e -> {
            var selector = new FileChooser();
            selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fotos JPEG o PNG", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
            var archivo = selector.showOpenDialog(ventana);
            if (archivo == null) return;
            foto = archivo.toPath();
            vista.setImage(imagen(foto, 180));
            resultados.getChildren().clear();
            orientacion.setText("");
            estado.setText(archivo.getName());
        });
        buscar.setOnAction(e -> {
            if (foto == null) { estado.setText("Elige una foto para comparar."); return; }
            resultados.getChildren().clear();
            orientacion.setText("");
            tarea("Calculando similitudes con CLIP…", () -> python("buscar", foto.toString()).getAsJsonArray(), filas -> {
                for (var item : filas) {
                    var fila = item.getAsJsonObject();
                    var ruta = raiz.resolve(fila.get("ruta_imagen").getAsString()).normalize();
                    var mini = new ImageView();
                    if (ruta.startsWith(raiz) && Files.isRegularFile(ruta)) mini.setImage(imagen(ruta, 100));
                    mini.setFitWidth(90);
                    mini.setPreserveRatio(true);
                    var nombre = texto(fila, "etiqueta", "Sin etiqueta");
                    var distancia = fila.get("distancia").getAsDouble();
                    var detalle = new Label(String.format("%s · %s%n%s · Distancia coseno %.4f", nombre,
                        ruta.getFileName(), texto(fila, "fuente", fila.get("origen").getAsString()), distancia));
                    resultados.getChildren().add(new HBox(12, mini, detalle));
                    // Umbral ilustrativo de la demo, sin calibración clínica o agronómica.
                    if (resultados.getChildren().size() == 1 && distancia <= 0.10 && !fila.get("etiqueta").isJsonNull())
                        orientacion.setText("Orientación por semejanza: " + nombre + ". Etiqueta de la referencia más cercana; requiere revisión.");
                }
                if (orientacion.getText().isEmpty()) orientacion.setText(filas.isEmpty()
                    ? "Colección vacía. Registra fotos de referencia para comparar."
                    : "Sin referencia cercana etiquetada para sugerir un diagnóstico.");
                estado.setText(filas.size() + " coincidencias.");
            });
        });
        guardar.setOnAction(e -> {
            var cultivo = cultivos.getValue();
            if (foto == null || cultivo == null) { estado.setText("Selecciona una foto y su cultivo."); return; }
            String valor = etiqueta.getText().trim();
            tarea("Vectorizando y registrando…", () -> python("registrar", foto.toString(), "--cultivo", cultivo.get(0),
                "--etiqueta", valor).getAsJsonObject(), r -> estado.setText("Foto " + r.get("id") +
                (r.get("nueva").getAsBoolean() ? " registrada." : " ya registrada.")));
        });
        return new VBox(12, new HBox(10, elegir, buscar), vista,
            new FlowPane(8, 8, new Label("Cultivo"), cultivos, etiqueta, guardar), orientacion, scroll);
    }

    private static Image imagen(Path ruta, double ancho) {
        return new Image(ruta.toUri().toString(), ancho, 0, true, true);
    }

    private static String texto(JsonObject fila, String campo, String defecto) {
        return fila.get(campo).isJsonNull() ? defecto : fila.get(campo).getAsString();
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
                var lineas = Files.readAllLines(log, StandardCharsets.UTF_8);
                throw new IllegalStateException(lineas.isEmpty() ? "Error al procesar foto." : lineas.get(lineas.size()-1));
            }
            return JsonParser.parseString(salida);
        } finally { Files.deleteIfExists(log); }
    }

    private <T> void tarea(String mensaje, Callable<T> trabajo, Consumer<T> terminado) {
        vistas.setDisable(true);
        estado.setText(mensaje);
        var tarea = new Task<T>() {
            @Override protected T call() throws Exception { return trabajo.call(); }
        };
        tarea.setOnSucceeded(e -> { vistas.setDisable(false); terminado.accept(tarea.getValue()); });
        tarea.setOnFailed(e -> { vistas.setDisable(false); estado.setText(tarea.getException().getMessage()); });
        var hilo = new Thread(tarea);
        hilo.setDaemon(true);
        hilo.start();
    }
}
