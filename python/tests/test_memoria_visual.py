from contextlib import chdir
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import psycopg

from memoria_visual.buscar_similares import buscar
from memoria_visual.conexion import conectar
from memoria_visual.embeddings import generar_embedding
from memoria_visual.escritorio import importar
from memoria_visual.registrar_foto import registrar

FOTOS = Path("datos/imagenes_demo")


class MemoriaVisualTest(unittest.TestCase):
    def setUp(self):
        with conectar() as conexion:
            total = conexion.execute("SELECT count(*) FROM registro_fotografico").fetchone()[0]
        self.assertEqual(total, 0, "Ejecuta las pruebas con una base de demostración sin fotos.")
        self.ids = []

    def tearDown(self):
        if self.ids:
            with conectar() as conexion:
                conexion.execute("DELETE FROM registro_fotografico WHERE id_foto = ANY(%s)", (self.ids,))

    def comprobar_flujo(self):
        opciones = dict(origen="DATASET", fuente="Imágenes sintéticas del proyecto",
                        licencia="Uso en la demostración académica")
        consulta = FOTOS / "foto-5.jpg"
        self.assertEqual(buscar(consulta), [])

        primera = FOTOS / "foto-0.jpg"
        id_foto, nueva = registrar(primera, **opciones)
        self.assertTrue(nueva)
        self.ids.append(id_foto)
        self.assertEqual(registrar(primera, **opciones), (id_foto, False))
        self.assertEqual([fila["id_foto"] for fila in buscar(consulta)], [id_foto])

        with conectar() as conexion:
            id_cultivo = conexion.execute(
                "SELECT id_cultivo FROM cultivo WHERE nombre = %s", ("Tomate de prueba",)
            ).fetchone()[0]
        propia, nueva = registrar(FOTOS / "foto-1.jpg", origen="PROPIA", cultivo=id_cultivo)
        self.assertTrue(nueva)
        self.ids.append(propia)
        for i in range(2, 5):
            otra, nueva = registrar(FOTOS / f"foto-{i}.jpg", **opciones)
            self.assertTrue(nueva)
            self.ids.append(otra)

        filas = buscar(consulta)
        self.assertEqual({fila["id_foto"] for fila in filas}, set(self.ids))
        distancias = [fila["distancia"] for fila in filas]
        self.assertEqual(distancias, sorted(distancias))
        self.assertTrue(all(0 <= distancia <= 2 for distancia in distancias))
        with conectar() as conexion:
            registros = conexion.execute(
                """
                SELECT id_foto, vector_dims(embedding), id_cultivo, origen
                FROM registro_fotografico WHERE id_foto = ANY(%s)
                """,
                (self.ids,),
            ).fetchall()
        self.assertEqual(len(registros), 5)
        self.assertTrue(all(dimensiones == 512 for _, dimensiones, _, _ in registros))
        self.assertIn((id_foto, 512, None, "DATASET"), registros)
        self.assertIn((propia, 512, id_cultivo, "PROPIA"), registros)

    def test_galeria_vacia_no_carga_clip(self):
        with patch("memoria_visual.buscar_similares.generar_embedding") as modelo:
            self.assertEqual(buscar(FOTOS / "foto-5.jpg"), [])
            modelo.assert_not_called()

    def test_registro_fallido_no_deja_archivo_ni_fila(self):
        foto = (FOTOS / "foto-0.jpg").resolve()
        with conectar() as conexion:
            cultivo = conexion.execute("SELECT min(id_cultivo) FROM cultivo").fetchone()[0]
        with TemporaryDirectory() as temporal, chdir(temporal):
            with patch("memoria_visual.registrar_foto.generar_embedding", return_value=[1.0] * 512):
                with self.assertRaises(psycopg.errors.ForeignKeyViolation):
                    importar(foto, -1, None)
                self.assertFalse(Path("datos/fotos").exists())
                with patch("memoria_visual.registrar_foto.guardar_copia", side_effect=OSError("disco lleno")):
                    with self.assertRaisesRegex(OSError, "disco lleno"):
                        importar(foto, cultivo, None)
            with conectar() as conexion:
                self.assertEqual(conexion.execute("SELECT count(*) FROM registro_fotografico").fetchone()[0], 0)

    def test_importar_una_vez_y_conservar_la_ruta_registrada(self):
        demo = FOTOS / "foto-0.jpg"
        externa = demo.resolve()
        propia = (FOTOS / "foto-1.jpg").resolve()
        with conectar() as conexion:
            cultivo = conexion.execute("SELECT min(id_cultivo) FROM cultivo").fetchone()[0]
        with patch("memoria_visual.registrar_foto.generar_embedding", return_value=[1.0] * 512) as modelo:
            id_demo, _ = registrar(demo, origen="DATASET", fuente="Prueba", licencia="Prueba")
            self.ids.append(id_demo)
            with TemporaryDirectory() as temporal, chdir(temporal):
                self.assertEqual(importar(externa, cultivo, None), {"id": id_demo, "nueva": False})
                self.assertFalse(Path("datos/fotos").exists())
                alta = importar(propia, cultivo, "Gotas de agua")
                self.assertTrue(alta["nueva"])
                self.ids.append(alta["id"])
                with conectar() as conexion:
                    ruta = Path(conexion.execute("SELECT ruta_imagen FROM registro_fotografico WHERE id_foto = %s",
                                                 (alta["id"],)).fetchone()[0])
                self.assertEqual(ruta.read_bytes(), propia.read_bytes())
                ruta.unlink()
                self.assertEqual(importar(propia, cultivo, None), {"id": alta["id"], "nueva": False})
                self.assertEqual(list(Path("datos/fotos").iterdir()), [ruta])
                self.assertEqual(ruta.read_bytes(), propia.read_bytes())
                self.assertEqual(modelo.call_count, 2)

    def test_postgres_pgvector_y_deduplicacion(self):
        def vector_de_prueba(contenido):
            from hashlib import sha256

            huella = sha256(contenido).digest()
            return [valor / 255 for valor in huella] * 16

        with patch("memoria_visual.registrar_foto.generar_embedding", vector_de_prueba), patch(
            "memoria_visual.buscar_similares.generar_embedding", vector_de_prueba
        ):
            self.comprobar_flujo()

    @unittest.skipUnless(os.getenv("PROBAR_CLIP") == "1", "Descarga real de pesos habilitada con PROBAR_CLIP=1")
    def test_clip_real_y_postgres(self):
        self.assertEqual(len(generar_embedding((FOTOS / "foto-5.jpg").read_bytes())), 512)
        self.comprobar_flujo()
