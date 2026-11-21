package ec.edu.epn.invernaderos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MensajePythonTest {
    @Test void conservaErrorDelLanzadorWindows(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("error.log");
        Files.writeString(log, "Aviso\r\nNo se encontró Python\r\n", Charset.forName("windows-1252"));
        assertEquals("No se encontró Python", PanelFotos.mensajePython(log));
    }
}
