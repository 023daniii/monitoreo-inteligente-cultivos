import argparse
from pathlib import Path

from psycopg.rows import dict_row

from .conexion import conectar
from .embeddings import MODELO, generar_embedding, literal_vector


def buscar(ruta):
    vector = literal_vector(generar_embedding(Path(ruta).read_bytes()))
    with conectar() as conexion, conexion.cursor(row_factory=dict_row) as cursor:
        cursor.execute(
            """
            SELECT id_foto, ruta_imagen, etiqueta, origen, fuente, licencia,
                   embedding <=> %s::vector AS distancia
            FROM registro_fotografico
            WHERE estado AND modelo_embedding = %s
            ORDER BY distancia, id_foto
            LIMIT 5
            """,
            (vector, MODELO),
        )
        return cursor.fetchall()


def main():
    parser = argparse.ArgumentParser(description="Busca hasta cinco fotografías similares.")
    parser.add_argument("ruta", type=Path)
    args = parser.parse_args()
    try:
        resultados = buscar(args.ruta)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    if not resultados:
        print("No hay fotografías disponibles.")
    for resultado in resultados:
        print(f"{resultado['distancia']:.6f}  {resultado['ruta_imagen']}")


if __name__ == "__main__":
    main()
