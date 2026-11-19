"""Prepara el intérprete local que utiliza JavaFX para la memoria visual."""
from pathlib import Path
import subprocess
import sys
import venv


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
        venv.EnvBuilder(with_pip=True).create(entorno)

    pip = [str(python), "-m", "pip", "install"]
    requisitos = carpeta / "requirements.txt"
    subprocess.run([*pip, "setuptools==80.9.0", "wheel==0.45.1"], check=True)
    if sys.platform in ("win32", "linux"):
        paquetes_cpu = [linea for linea in requisitos.read_text(encoding="utf-8").splitlines()
                        if linea.startswith(("torch==", "torchvision=="))]
        subprocess.run([*pip, "--index-url", "https://download.pytorch.org/whl/cpu",
                        *paquetes_cpu], check=True)
    subprocess.run([*pip, "--no-build-isolation", "-r", requisitos], check=True)
    subprocess.run([str(python), "-c", "import clip, psycopg, torch, torchvision, PIL"], check=True)
    print(f"Memoria visual preparada en {entorno}.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
