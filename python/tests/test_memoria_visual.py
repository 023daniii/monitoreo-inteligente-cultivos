import os
from pathlib import Path
import unittest
from unittest.mock import patch

from memoria_visual.buscar_similares import buscar
from memoria_visual.conexion import conectar
from memoria_visual.embeddings import generar_embedding
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
