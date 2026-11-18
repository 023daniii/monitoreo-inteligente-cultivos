"""Intercambio JSON con la interfaz JavaFX."""
import argparse
import json
import os
from hashlib import sha256
from pathlib import Path
from tempfile import NamedTemporaryFile

from PIL import Image
from .buscar_similares import buscar
from .registrar_foto import registrar


def importar(ruta, cultivo, etiqueta):
    contenido = ruta.read_bytes()
    carpeta = Path("datos/fotos")
    carpeta.mkdir(exist_ok=True)
    with NamedTemporaryFile(dir=carpeta, delete=False) as archivo:
        temporal = Path(archivo.name)
        archivo.write(contenido)
    try:
        with Image.open(temporal) as foto:
            extension = {"JPEG": ".jpg", "PNG": ".png"}.get(foto.format)
            if not extension:
                raise ValueError("Selecciona una foto JPEG o PNG.")
            foto.verify()
        destino = carpeta / (sha256(contenido).hexdigest() + extension)
        # Publicar el archivo completo antes de guardar su referencia en la base.
        os.replace(temporal, destino)
        id_foto, nueva = registrar(destino, origen="PROPIA", cultivo=cultivo, etiqueta=etiqueta or None)
        return {"id": id_foto, "nueva": nueva}
    finally:
        temporal.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("accion", choices=["buscar", "registrar"])
    parser.add_argument("ruta", type=Path)
    parser.add_argument("--cultivo", type=int)
    parser.add_argument("--etiqueta")
    args = parser.parse_args()
    if args.accion == "registrar" and args.cultivo is None:
        parser.error("Selecciona un cultivo.")
    resultado = (buscar(args.ruta) if args.accion == "buscar"
                 else importar(args.ruta, args.cultivo, args.etiqueta))
    print(json.dumps(resultado))


if __name__ == "__main__":
    main()
