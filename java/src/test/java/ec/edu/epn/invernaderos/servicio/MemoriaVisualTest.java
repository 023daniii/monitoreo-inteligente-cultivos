package ec.edu.epn.invernaderos.servicio;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoriaVisualTest {
    @Test void conservaElErrorDelLanzadorDeWindows(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("error.log");
        Files.writeString(log, "Aviso\r\nNo se encontró Python\r\n", Charset.forName("windows-1252"));
        assertEquals("No se encontró Python", MemoriaVisual.ultimaLinea(log));
    }

    @Test void sugierePrepararCuandoFaltaUnModulo(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("error.log");
        Files.writeString(log, "ModuleNotFoundError: No module named 'clip'\n");
        assertTrue(MemoriaVisual.ultimaLinea(log).contains("preparar.py"));
    }

    @Test void encuentraLaCarpetaDelProyecto() {
        assertTrue(Files.isRegularFile(MemoriaVisual.encontrarRaiz().resolve("python/memoria_visual.py")));
    }
}
