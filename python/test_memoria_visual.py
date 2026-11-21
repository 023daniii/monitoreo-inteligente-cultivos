"""Pruebas de la memoria visual. Requieren la base de Docker sin fotos registradas.

    python -m unittest discover -s python -v
Con PROBAR_CLIP=1 también se prueba el modelo real (descarga unos 338 MiB la primera vez).
"""
from hashlib import sha256
import io
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import memoria_visual as mv

CONSULTAS = mv.RAIZ / "datos" / "consultas"


def vector_falso(contenido):
    """Vector determinista de 512 valores derivado del hash de la foto."""
    return [byte / 255 for byte in sha256(contenido).digest()] * 16


class MemoriaVisualTest(unittest.TestCase):
    def setUp(self):
        with mv.conectar() as conexion:
            total = conexion.execute("SELECT count(*) FROM registro_fotografico").fetchone()[0]
        self.assertEqual(total, 0, "Ejecuta las pruebas con una base sin fotos registradas.")
        self.clip_real = mv.generar_embedding
        parche = patch.object(mv, "generar_embedding", side_effect=vector_falso)
        self.modelo = parche.start()
        self.addCleanup(parche.stop)

    def tearDown(self):
        with mv.conectar() as conexion:
            conexion.execute("DELETE FROM registro_fotografico")

    def test_cargar_las_200_referencias_con_diagnostico(self):
        self.assertEqual(mv.cargar_referencias(), {"total": 200, "nuevas": 200})
        self.assertEqual(mv.cargar_referencias(), {"total": 200, "nuevas": 0})
        with mv.conectar() as conexion:
            filas = conexion.execute("""
                SELECT d.codigo, count(*), min(vector_dims(f.embedding)), bool_and(f.licencia = 'CC BY 4.0')
                FROM registro_fotografico f JOIN catalogo d ON d.id_catalogo = f.id_diagnostico
                GROUP BY d.codigo ORDER BY d.codigo""").fetchall()
        self.assertEqual(len(filas), 8)
        self.assertTrue(all(total == 25 and dims == 512 and licencia for _, total, dims, licencia in filas))

    def test_foto_propia_se_copia_y_no_se_duplica(self):
        with mv.conectar() as conexion:
            cultivo = conexion.execute("SELECT min(id_cultivo) FROM cultivo").fetchone()[0]
        foto = CONSULTAS / "sana-01.jpg"
        with TemporaryDirectory(dir=mv.RAIZ) as temporal, patch.object(mv, "FOTOS_PROPIAS", Path(temporal)):
            id_foto, nueva = mv.registrar(foto, origen="PROPIA", cultivo=cultivo, diagnostico="DIAG_SANA")
            self.assertTrue(nueva)
            copia = next(Path(temporal).iterdir())
            self.assertEqual(copia.read_bytes(), foto.read_bytes())
            copia.unlink()
            self.assertEqual(mv.registrar(foto, origen="PROPIA", cultivo=cultivo), (id_foto, False))
            self.assertTrue(copia.is_file())
        self.assertEqual(self.modelo.call_count, 1)

    def test_registro_fallido_no_deja_fila(self):
        foto = CONSULTAS / "mosaico-01.jpg"
        with TemporaryDirectory(dir=mv.RAIZ) as temporal, patch.object(mv, "FOTOS_PROPIAS", Path(temporal)):
            with patch.object(mv, "guardar_copia", side_effect=OSError("disco lleno")):
                with self.assertRaisesRegex(OSError, "disco lleno"):
                    mv.registrar(foto, origen="PROPIA", cultivo=1)
            with self.assertRaisesRegex(ValueError, "No existe el diagnóstico"):
                mv.registrar(foto, origen="PROPIA", cultivo=1, diagnostico="DIAG_INVENTADO")
            with self.assertRaises(ValueError):
                mv.registrar(Path(__file__), origen="PROPIA", cultivo=1)
        with mv.conectar() as conexion:
            self.assertEqual(conexion.execute("SELECT count(*) FROM registro_fotografico").fetchone()[0], 0)

    def test_servidor_responde_una_linea_json_por_peticion(self):
        entrada = io.StringIO(
            json.dumps({"accion": "vector", "ruta": str(CONSULTAS / "septoria-01.jpg")}) + "\n"
            + json.dumps({"accion": "vector", "ruta": "no-existe.jpg"}) + "\n"
            + json.dumps({"accion": "otra"}) + "\n")
        salida = io.StringIO()
        mv.servidor(entrada, salida)
        respuestas = [json.loads(linea) for linea in salida.getvalue().splitlines()]
        self.assertEqual([r["ok"] for r in respuestas], [True, False, False])
        self.assertEqual(len(respuestas[0]["vector"]), 512)

    @unittest.skipUnless(os.getenv("PROBAR_CLIP") == "1", "Activa PROBAR_CLIP=1 para usar el modelo real")
    def test_clip_real(self):
        vector = self.clip_real((CONSULTAS / "tizon-tardio-01.jpg").read_bytes())
        self.assertEqual(len(vector), 512)
        self.assertAlmostEqual(sum(v * v for v in vector), 1.0, places=4)


if __name__ == "__main__":
    unittest.main()
