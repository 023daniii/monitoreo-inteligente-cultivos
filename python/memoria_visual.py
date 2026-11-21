"""Memoria visual: convierte fotos en vectores CLIP de 512 números y los guarda en PostgreSQL.

Python solo genera y guarda embeddings. La búsqueda de las 5 fotos más parecidas la hace Java
con pgvector. Comandos:

    python python/memoria_visual.py cargar-referencias      # registra las 200 fotos del dataset
    python python/memoria_visual.py vector FOTO.jpg         # imprime el vector en JSON
    python python/memoria_visual.py registrar FOTO.jpg --cultivo 1 [--diagnostico DIAG_SANA]
    python python/memoria_visual.py servidor                # modo que usa JavaFX (una petición JSON por línea)
"""
import argparse
import csv
import json
import os
import sys
from functools import lru_cache
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory

import psycopg
from PIL import Image, ImageOps

MODELO = "CLIP ViT-B/32"
RAIZ = Path(__file__).resolve().parent.parent
REFERENCIAS = RAIZ / "datos" / "referencias"
FOTOS_PROPIAS = RAIZ / "datos" / "fotos"
FORMATOS = {"JPEG": ".jpg", "PNG": ".png"}


def conectar():
    return psycopg.connect(
        host=os.getenv("DB_HOST") or "127.0.0.1",
        port=os.getenv("DB_PORT") or "5432",
        dbname=os.getenv("POSTGRES_DB") or "monitoreo_inteligente_cultivos",
        user=os.getenv("POSTGRES_USER") or "invernadero",
        password=os.getenv("POSTGRES_PASSWORD") or "invernadero_local",
        options="-c timezone=America/Guayaquil",
        connect_timeout=5,
    )


@lru_cache(maxsize=1)
def cargar_modelo():
    import clip

    return clip.load("ViT-B/32", device="cpu", download_root=os.getenv("CLIP_CACHE"))


def extension(contenido):
    """Comprueba que los bytes sean una foto JPEG o PNG y devuelve su extensión."""
    try:
        with Image.open(BytesIO(contenido)) as foto:
            formato = foto.format
            foto.verify()
    except Exception as error:
        raise ValueError("El archivo no es una imagen válida.") from error
    if formato not in FORMATOS:
        raise ValueError("Usa una foto JPEG o PNG.")
    return FORMATOS[formato]


def generar_embedding(contenido):
    import torch

    with Image.open(BytesIO(contenido)) as imagen:
        rgb = ImageOps.exif_transpose(imagen).convert("RGB")
    modelo, preprocesar = cargar_modelo()
    with torch.inference_mode():
        vector = modelo.encode_image(preprocesar(rgb).unsqueeze(0)).float()
        vector = torch.nn.functional.normalize(vector, dim=-1)
    return vector[0].tolist()


def vector_de(ruta):
    contenido = Path(ruta).read_bytes()
    extension(contenido)
    return generar_embedding(contenido)


def literal_vector(valores):
    return "[" + ",".join(str(valor) for valor in valores) + "]"


def ruta_relativa(ruta):
    try:
        return Path(ruta).resolve().relative_to(RAIZ).as_posix()
    except ValueError as error:
        raise ValueError(f"{ruta} debe estar dentro de la carpeta del proyecto.") from error


def guardar_copia(destino, contenido):
    destino.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(dir=destino.parent) as temporal:
        archivo = Path(temporal) / destino.name
        archivo.write_bytes(contenido)
        archivo.replace(destino)


def registrar(ruta, *, origen, cultivo=None, diagnostico=None, fuente=None, licencia=None, etiqueta=None):
    """Guarda la foto con su vector. Devuelve (id_foto, True si es nueva). Una foto repetida se reconoce por SHA-256."""
    if origen == "PROPIA" and cultivo is None:
        raise ValueError("Una foto propia debe asociarse a un cultivo.")
    if origen == "DATASET" and (cultivo is not None or not (fuente and licencia and diagnostico)):
        raise ValueError("Una referencia del dataset necesita fuente, licencia y diagnóstico, sin cultivo.")
    if origen not in ("PROPIA", "DATASET"):
        raise ValueError("Origen inválido.")

    contenido = Path(ruta).read_bytes()
    huella = sha256(contenido).hexdigest()
    ext = extension(contenido)
    destino = FOTOS_PROPIAS / (huella + ext) if origen == "PROPIA" else None
    ruta_bd = ruta_relativa(destino if destino else ruta)

    with conectar() as conexion:
        existente = conexion.execute(
            "SELECT id_foto, ruta_imagen FROM registro_fotografico WHERE hash_imagen = %s", (huella,)
        ).fetchone()
        if existente:
            # Si se borró la copia local de una foto propia, se vuelve a guardar.
            archivo = RAIZ / existente[1]
            if destino and archivo == destino and not archivo.is_file():
                guardar_copia(destino, contenido)
            return existente[0], False

        id_diagnostico = None
        if diagnostico:
            fila = conexion.execute(
                """SELECT d.id_catalogo FROM catalogo d JOIN catalogo c ON c.id_catalogo = d.id_padre
                   WHERE d.codigo = %s AND c.codigo = 'CAT_DIAGNOSTICO'""", (diagnostico,)
            ).fetchone()
            if fila is None:
                raise ValueError(f"No existe el diagnóstico {diagnostico}.")
            id_diagnostico = fila[0]

        vector = literal_vector(generar_embedding(contenido))
        nueva = conexion.execute(
            """
            INSERT INTO registro_fotografico (ruta_imagen, origen, id_cultivo, id_diagnostico, fuente,
                                              licencia, etiqueta, embedding, modelo_embedding, hash_imagen)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s::vector, %s, %s)
            ON CONFLICT ON CONSTRAINT registro_fotografico_uq4 DO NOTHING
            RETURNING id_foto
            """,
            (ruta_bd, origen, cultivo, id_diagnostico, fuente, licencia, etiqueta, vector, MODELO, huella),
        ).fetchone()
        if nueva is None:
            return conexion.execute(
                "SELECT id_foto FROM registro_fotografico WHERE hash_imagen = %s", (huella,)
            ).fetchone()[0], False
        # Si falla la copia, la excepción revierte el INSERT.
        if destino:
            guardar_copia(destino, contenido)
        return nueva[0], True


def cargar_referencias(avisar=lambda mensaje: None):
    """Registra las fotos de datos/referencias/referencias.csv que aún no están en la base."""
    with open(REFERENCIAS / "referencias.csv", encoding="utf-8", newline="") as archivo:
        filas = list(csv.DictReader(archivo))
    nuevas = 0
    for numero, fila in enumerate(filas, start=1):
        _, nueva = registrar(REFERENCIAS / fila["archivo"], origen="DATASET", diagnostico=fila["diagnostico"],
                             fuente=fila["fuente"], licencia=fila["licencia"], etiqueta=fila["etiqueta"])
        nuevas += nueva
        if numero % 20 == 0 or numero == len(filas):
            avisar(f"Referencias procesadas: {numero} de {len(filas)}")
    return {"total": len(filas), "nuevas": nuevas}


def atender(pedido):
    accion = pedido.get("accion")
    if accion == "vector":
        return {"vector": vector_de(pedido["ruta"])}
    if accion == "registrar":
        id_foto, nueva = registrar(pedido["ruta"], origen="PROPIA", cultivo=int(pedido["cultivo"]),
                                   diagnostico=pedido.get("diagnostico") or None,
                                   etiqueta=pedido.get("etiqueta") or None)
        return {"id": id_foto, "nueva": nueva}
    if accion == "cargar":
        return cargar_referencias(lambda mensaje: print(mensaje, file=sys.stderr, flush=True))
    raise ValueError(f"Acción desconocida: {accion}")


def servidor(entrada=sys.stdin, salida=sys.stdout):
    """Atiende peticiones JSON, una por línea. El modelo se carga una sola vez para toda la sesión."""
    for linea in entrada:
        if not linea.strip():
            continue
        try:
            respuesta = {"ok": True, **atender(json.loads(linea))}
        except Exception as error:  # El error se devuelve a JavaFX en lugar de cerrar el proceso.
            respuesta = {"ok": False, "error": str(error) or type(error).__name__}
        print(json.dumps(respuesta), file=salida, flush=True)


def main():
    parser = argparse.ArgumentParser(description="Memoria visual de cultivos (CLIP + pgvector).")
    comandos = parser.add_subparsers(dest="comando", required=True)
    comandos.add_parser("cargar-referencias", help="Registra las 200 fotos de referencia.")
    comandos.add_parser("servidor", help="Modo de intercambio JSON con JavaFX.")
    vector = comandos.add_parser("vector", help="Imprime el vector de una foto.")
    vector.add_argument("ruta", type=Path)
    alta = comandos.add_parser("registrar", help="Registra una foto propia de un cultivo.")
    alta.add_argument("ruta", type=Path)
    alta.add_argument("--cultivo", type=int, required=True)
    alta.add_argument("--diagnostico")
    alta.add_argument("--etiqueta")
    args = parser.parse_args()
    try:
        if args.comando == "servidor":
            servidor()
        elif args.comando == "cargar-referencias":
            resultado = cargar_referencias(print)
            print(f"Memoria visual: {resultado['nuevas']} referencias nuevas de {resultado['total']}.")
        elif args.comando == "vector":
            print(json.dumps(vector_de(args.ruta)))
        else:
            id_foto, nueva = registrar(args.ruta, origen="PROPIA", cultivo=args.cultivo,
                                       diagnostico=args.diagnostico, etiqueta=args.etiqueta)
            print(f"Foto {id_foto}: {'registrada' if nueva else 'ya estaba registrada (SHA-256)'}.")
    except (OSError, ValueError, psycopg.Error) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
