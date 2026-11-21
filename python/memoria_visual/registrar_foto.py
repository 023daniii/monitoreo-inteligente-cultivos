import argparse
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image

from .conexion import conectar
from .embeddings import MODELO, generar_embedding, literal_vector


def guardar_copia(destino, contenido):
    destino.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(dir=destino.parent) as temporal:
        archivo = Path(temporal) / destino.name
        archivo.write_bytes(contenido)
        archivo.replace(destino)


def registrar(ruta, *, origen, cultivo=None, fuente=None, licencia=None, etiqueta=None, copiar=False):
    ruta = Path(ruta)
    if not copiar and (ruta.is_absolute() or ".." in ruta.parts):
        raise ValueError("Usa una ruta relativa a la raíz del proyecto.")
    if origen == "PROPIA" and cultivo is None:
        raise ValueError("Una foto propia exige un cultivo.")
    if origen == "DATASET" and (cultivo is not None or not fuente or not licencia):
        raise ValueError("Una foto de dataset exige fuente y licencia y se registra sin cultivo.")
    if origen not in {"PROPIA", "DATASET"}:
        raise ValueError("Origen inválido.")

    contenido = ruta.read_bytes()
    huella = sha256(contenido).hexdigest()
    if copiar:
        with Image.open(BytesIO(contenido)) as foto:
            extension = {"JPEG": ".jpg", "PNG": ".png"}.get(foto.format)
            if not extension:
                raise ValueError("Selecciona una foto JPEG o PNG.")
            foto.verify()
        ruta = Path("datos/fotos") / (huella + extension)

    buscar_id = "SELECT id_foto, ruta_imagen FROM registro_fotografico WHERE hash_imagen = %s"
    with conectar() as conexion:
        existente = conexion.execute(buscar_id, (huella,)).fetchone()
        if existente:
            if copiar and existente[1] == ruta.as_posix() and not ruta.is_file():
                guardar_copia(ruta, contenido)
            return existente[0], False

    vector = literal_vector(generar_embedding(contenido))
    with conectar() as conexion:
        nueva = conexion.execute(
            """
            INSERT INTO registro_fotografico (
                ruta_imagen, origen, id_cultivo, fuente, licencia, etiqueta,
                embedding, modelo_embedding, hash_imagen
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s::vector, %s, %s)
            ON CONFLICT ON CONSTRAINT registro_fotografico_uq4 DO NOTHING
            RETURNING id_foto
            """,
            (ruta.as_posix(), origen, cultivo, fuente, licencia, etiqueta, vector, MODELO, huella),
        ).fetchone()
        if nueva:
            # El INSERT ya fue aceptado. Si falla la copia, la transacción se revierte.
            if copiar:
                guardar_copia(ruta, contenido)
            return nueva[0], True
        return conexion.execute(buscar_id, (huella,)).fetchone()[0], False


def main():
    parser = argparse.ArgumentParser(description="Registra una fotografía en la memoria visual.")
    parser.add_argument("ruta")
    parser.add_argument("--origen", choices=("PROPIA", "DATASET"), required=True)
    parser.add_argument("--cultivo", type=int)
    parser.add_argument("--fuente")
    parser.add_argument("--licencia")
    parser.add_argument("--etiqueta")
    args = parser.parse_args()
    try:
        id_foto, nueva = registrar(
            args.ruta, origen=args.origen, cultivo=args.cultivo, fuente=args.fuente,
            licencia=args.licencia, etiqueta=args.etiqueta,
        )
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    print(f"Foto {id_foto}: {'registrada' if nueva else 'duplicada (SHA-256)'}.")


if __name__ == "__main__":
    main()
