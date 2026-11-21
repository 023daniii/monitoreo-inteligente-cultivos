package ec.edu.epn.invernaderos.servicio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Puente con python/memoria_visual.py. El proceso se abre una vez y conserva CLIP cargado,
 * así cada búsqueda solo calcula un vector. Se intercambia una línea JSON por petición.
 */
public final class MemoriaVisual implements AutoCloseable {
    private final Path raiz;
    private Process proceso;
    private BufferedWriter entrada;
    private BufferedReader salida;
    private Path log;

    public MemoriaVisual() {
        this(encontrarRaiz());
    }

    public MemoriaVisual(Path raiz) {
        this.raiz = raiz;
    }

    public Path raiz() { return raiz; }

    /** Vector CLIP de 512 valores de la foto indicada. */
    public double[] vector(Path foto) throws IOException {
        var pedido = new JsonObject();
        pedido.addProperty("accion", "vector");
        pedido.addProperty("ruta", foto.toAbsolutePath().toString());
        var valores = pedir(pedido).getAsJsonArray("vector");
        double[] vector = new double[valores.size()];
        for (int i = 0; i < vector.length; i++) vector[i] = valores.get(i).getAsDouble();
        return vector;
    }

    /** Guarda una foto propia del cultivo. Devuelve {"id", "nueva"}. */
    public JsonObject registrar(Path foto, int cultivo, String diagnostico, String etiqueta) throws IOException {
        var pedido = new JsonObject();
        pedido.addProperty("accion", "registrar");
        pedido.addProperty("ruta", foto.toAbsolutePath().toString());
        pedido.addProperty("cultivo", cultivo);
        pedido.addProperty("diagnostico", diagnostico);
        pedido.addProperty("etiqueta", etiqueta);
        return pedir(pedido);
    }

    /** Registra las 200 fotos de datos/referencias que falten. Devuelve {"total", "nuevas"}. */
    public JsonObject cargarReferencias() throws IOException {
        var pedido = new JsonObject();
        pedido.addProperty("accion", "cargar");
        return pedir(pedido);
    }

    private synchronized JsonObject pedir(JsonObject pedido) throws IOException {
        iniciar();
        try {
            entrada.write(pedido.toString());
            entrada.newLine();
            entrada.flush();
            String linea;
            // Se ignora cualquier texto que no sea la respuesta JSON.
            while ((linea = salida.readLine()) != null && !linea.startsWith("{")) { }
            if (linea == null) throw new IOException("Python se cerró. " + ultimaLinea(log));
            var respuesta = JsonParser.parseString(linea).getAsJsonObject();
            if (!respuesta.get("ok").getAsBoolean()) throw new IOException(respuesta.get("error").getAsString());
            return respuesta;
        } catch (IOException error) {
            if (!proceso.isAlive()) cerrarProceso();
            throw error;
        }
    }

    private void iniciar() throws IOException {
        if (proceso != null && proceso.isAlive()) return;
        cerrarProceso();
        String ejecutable = interprete();
        var comando = List.of(ejecutable, raiz.resolve("python/memoria_visual.py").toString(), "servidor");
        var constructor = new ProcessBuilder(comando).directory(raiz.toFile());
        constructor.environment().put("PYTHONIOENCODING", "utf-8");
        log = Files.createTempFile("memoria-visual-", ".log");
        constructor.redirectError(log.toFile());
        try {
            proceso = constructor.start();
        } catch (IOException error) {
            throw new IOException("No se pudo iniciar Python (" + ejecutable + "). " + ayuda(), error);
        }
        entrada = new BufferedWriter(new OutputStreamWriter(proceso.getOutputStream(), StandardCharsets.UTF_8));
        salida = new BufferedReader(new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8));
    }

    private String interprete() {
        String elegido = System.getenv("INVERNADEROS_PYTHON");
        if (elegido != null && !elegido.isBlank()) return elegido;
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        var local = raiz.resolve(windows ? ".venv/Scripts/python.exe" : ".venv/bin/python");
        return Files.isRegularFile(local) ? local.toString() : (windows ? "python" : "python3");
    }

    static String ayuda() {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        return "Desde la carpeta del proyecto ejecuta "
            + (windows ? "py -3.12 .\\python\\preparar.py" : "python3.12 python/preparar.py") + " y vuelve a intentarlo.";
    }

    /** Última línea del registro de errores de Python, con o sin UTF-8 (el lanzador de Windows usa windows-1252). */
    static String ultimaLinea(Path archivo) {
        if (archivo == null || !Files.exists(archivo)) return ayuda();
        String texto;
        try {
            try {
                texto = Files.readString(archivo, StandardCharsets.UTF_8);
            } catch (MalformedInputException error) {
                texto = new String(Files.readAllBytes(archivo), Charset.forName("windows-1252"));
            }
        } catch (IOException error) {
            return ayuda();
        }
        String ultima = texto.lines().filter(l -> !l.isBlank()).reduce((a, b) -> b).orElse("");
        if (ultima.isEmpty()) return ayuda();
        return ultima.contains("ModuleNotFoundError") ? ultima + "\n" + ayuda() : ultima;
    }

    private void cerrarProceso() {
        if (proceso != null) proceso.destroy();
        proceso = null;
        if (log != null) {
            try {
                Files.deleteIfExists(log);
            } catch (IOException ignorada) {
                // Es un archivo temporal; el sistema lo limpiará.
            }
        }
        log = null;
    }

    @Override public synchronized void close() {
        cerrarProceso();
    }

    /** Carpeta del proyecto: la que contiene python/memoria_visual.py. */
    public static Path encontrarRaiz() {
        var actual = Path.of("").toAbsolutePath().normalize();
        var lugares = new ArrayList<Path>(List.of(actual));
        try {
            lugares.add(Path.of(MemoriaVisual.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (URISyntaxException | SecurityException ignorada) {
            // Basta con el directorio de trabajo.
        }
        for (var lugar : lugares) {
            for (var candidato = lugar; candidato != null; candidato = candidato.getParent()) {
                if (Files.isRegularFile(candidato.resolve("python/memoria_visual.py"))) return candidato;
            }
        }
        return actual;
    }
}
