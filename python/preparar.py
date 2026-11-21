<<<<<<< HEAD
"""Crea .venv, instala CLIP y carga las 200 fotos de referencia en PostgreSQL."""
=======
"""Prepara el intérprete local que utiliza JavaFX para la memoria visual."""
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
from pathlib import Path
import subprocess
import sys
import venv

<<<<<<< HEAD
VERSIONES = ((3, 10), (3, 11), (3, 12), (3, 13))


def main():
    if sys.version_info[:2] not in VERSIONES:
        sys.exit("Ejecuta este archivo con Python 3.10 a 3.13 (recomendado 3.12).")
    carpeta = Path(__file__).resolve().parent
    entorno = carpeta.parent / ".venv"
    python = entorno / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
    if not python.is_file():
        if entorno.exists() and any(entorno.iterdir()):
            sys.exit(f"{entorno} existe pero no es un entorno de Python. Renómbralo y repite.")
=======

def main():
    if sys.version_info[:2] != (3, 12):
        sys.exit("Ejecuta este archivo con Python 3.12.")
    carpeta = Path(__file__).resolve().parent
    entorno = carpeta.parent / ".venv"
    if entorno.exists() and not (entorno / "pyvenv.cfg").is_file():
        if not entorno.is_dir() or any(entorno.iterdir()):
            sys.exit(f"{entorno} ya existe y no contiene un entorno virtual de Python.")
    python = entorno / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
    if python.is_file():
        version = subprocess.run([str(python), "--version"], capture_output=True, text=True)
        if version.returncode or not version.stdout.startswith("Python 3.12."):
            sys.exit("El intérprete de .venv no es Python 3.12 o no arranca. Renombra .venv y repite la preparación.")
    else:
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d
        venv.EnvBuilder(with_pip=True).create(entorno)

    pip = [str(python), "-m", "pip", "install"]
    requisitos = carpeta / "requirements.txt"
    subprocess.run([*pip, "setuptools==80.9.0", "wheel==0.45.1"], check=True)
    if sys.platform in ("win32", "linux"):
<<<<<<< HEAD
        # Versión de PyTorch solo para CPU: descarga mucho menos que la versión con CUDA.
        paquetes_cpu = [linea for linea in requisitos.read_text(encoding="utf-8").splitlines()
                        if linea.startswith(("torch==", "torchvision=="))]
        subprocess.run([*pip, "--index-url", "https://download.pytorch.org/whl/cpu", *paquetes_cpu], check=True)
    subprocess.run([*pip, "--no-build-isolation", "-r", requisitos], check=True)
    subprocess.run([str(python), "-c", "import clip, psycopg, torch, PIL"], check=True)

    print("Cargando las 200 fotos de referencia. La primera vez se descarga CLIP (unos 338 MiB)...")
    carga = subprocess.run([str(python), str(carpeta / "memoria_visual.py"), "cargar-referencias"])
    if carga.returncode:
        print("No se pudieron cargar ahora. Comprueba que 'docker compose up -d --wait' esté en marcha y repite "
              "este comando; también puedes cargarlas desde la pestaña Imágenes.")
    else:
        print(f"Memoria visual preparada en {entorno}.")
=======
        paquetes_cpu = [linea for linea in requisitos.read_text(encoding="utf-8").splitlines()
                        if linea.startswith(("torch==", "torchvision=="))]
        subprocess.run([*pip, "--index-url", "https://download.pytorch.org/whl/cpu",
                        *paquetes_cpu], check=True)
    subprocess.run([*pip, "--no-build-isolation", "-r", requisitos], check=True)
    subprocess.run([str(python), "-c", "import clip, psycopg, torch, torchvision, PIL"], check=True)
    print(f"Memoria visual preparada en {entorno}.")
>>>>>>> e114a493a3e10c81b1e1c495db7edd91f11a3a2d


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
