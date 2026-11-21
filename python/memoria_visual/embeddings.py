from functools import lru_cache
from io import BytesIO
import os

from PIL import Image, ImageOps

MODELO = "CLIP ViT-B/32"


@lru_cache(maxsize=1)
def cargar_modelo():
    import clip

    return clip.load("ViT-B/32", device="cpu", download_root=os.getenv("CLIP_CACHE"))


def generar_embedding(contenido):
    import torch

    with Image.open(BytesIO(contenido)) as imagen:
        rgb = ImageOps.exif_transpose(imagen).convert("RGB")
    modelo, preprocesar = cargar_modelo()
    with torch.inference_mode():
        vector = modelo.encode_image(preprocesar(rgb).unsqueeze(0)).float()
        vector = torch.nn.functional.normalize(vector, dim=-1)
    return vector[0].tolist()


def literal_vector(valores):
    return "[" + ",".join(str(valor) for valor in valores) + "]"
