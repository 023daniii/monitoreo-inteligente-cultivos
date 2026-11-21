"""Intercambio JSON con la interfaz JavaFX."""
import argparse
import json
from pathlib import Path

from .buscar_similares import buscar
from .registrar_foto import registrar


def importar(ruta, cultivo, etiqueta):
    id_foto, nueva = registrar(ruta, origen="PROPIA", cultivo=cultivo, etiqueta=etiqueta or None, copiar=True)
    return {"id": id_foto, "nueva": nueva}


def cargar_demo():
    observaciones = (
        "Hoja verde de tomate con las nervaduras visibles.",
        "Hojas de tomate con gotas de agua en la superficie.",
        "Tomate verde en la planta.",
        "Tomate rojo en la planta.",
        "Hoja de tres folíolos con bordes dentados.",
    )
    for numero, nota in enumerate(observaciones):
        registrar(Path("datos/imagenes_demo") / f"foto-{numero}.jpg", origen="DATASET",
                  fuente="Imágenes sintéticas del proyecto", licencia="Uso en la demostración académica",
                  etiqueta=nota)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("accion", choices=["buscar", "buscar-demo", "registrar"])
    parser.add_argument("ruta", type=Path)
    parser.add_argument("--cultivo", type=int)
    parser.add_argument("--etiqueta")
    args = parser.parse_args()
    if args.accion == "registrar":
        if args.cultivo is None:
            parser.error("Selecciona un cultivo.")
        resultado = importar(args.ruta, args.cultivo, args.etiqueta)
    else:
        if args.accion == "buscar-demo":
            cargar_demo()
        resultado = buscar(args.ruta)
    print(json.dumps(resultado))


if __name__ == "__main__":
    main()
