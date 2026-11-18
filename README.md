# Monitoreo inteligente de cultivos e invernaderos

Proyecto de Fundamentos de Bases de Datos de la Escuela Politécnica Nacional. Java simula lecturas, evalúa reglas y genera alertas. Python registra fotografías y busca imágenes similares con CLIP ViT-B/32. Ambos programas usan la misma base PostgreSQL con pgvector. La demostración funciona sin una API meteorológica.

## Requisitos

- Docker con Compose v2
- JDK 17 o posterior y Maven 3.9
- Python 3.12 para la memoria visual
- Internet en la primera instalación de dependencias y descarga de los pesos de CLIP

Los comandos se ejecutan desde la raíz del repositorio en una terminal tipo Bash.

## Levantar la base desde cero

```sh
cp .env.example .env
set -a
. ./.env
set +a
docker compose up -d --build --wait
```

Usa `.env.example` como plantilla. Java y Python leen `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` del entorno. Compose publica PostgreSQL solo en la interfaz local. Cambia `DB_PORT` si el 5432 ya está ocupado. Las credenciales de ejemplo son para uso local.

En un volumen nuevo, PostgreSQL 17 habilita pgvector 0.8.2 y carga la migración, los catálogos y los datos demo en ese orden. Si conservas el volumen de la primera etapa, puedes añadir los nuevos catálogos sin borrar los datos:

```sh
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" < database/seeds/001_catalogos.sql
```

`docker compose down` conserva el volumen. Para recrearlo, usa `docker compose down -v` y vuelve a levantar Compose. **La opción `-v` borra los datos locales.**

## Demostración de monitoreo en Java

```sh
mvn -f java/pom.xml compile exec:java -Dexec.args=demo
mvn -f java/pom.xml compile exec:java
```

El primer comando registra lecturas de 25, 30 y 34,2 °C con el sensor `TEMP-001`. Solo la última activa la regla `> 30 °C` para el tomate. Si repites el comando, se recuperan los mismos registros. El segundo comando abre un menú para registrar otra lectura o resolver una alerta por su ID.

## Demostración de memoria visual en Python

```sh
python3.12 -m venv .venv
. .venv/bin/activate
python -m pip install setuptools==80.9.0 wheel==0.45.1
python -m pip install --index-url https://download.pytorch.org/whl/cpu torch==2.7.1 torchvision==0.22.1
python -m pip install --no-build-isolation -r python/requirements.txt
export PYTHONPATH="$PWD/python"
```

El paquete oficial de CLIP descarga y verifica los pesos de ViT-B/32 desde `openaipublic.azureedge.net` al procesar la primera foto. Puedes indicar un directorio de caché con `CLIP_CACHE`. El equipo debe poder descargar los pesos o contar con el archivo oficial en esa caché.

```sh
for i in 0 1 2 3 4; do
  python -m memoria_visual.registrar_foto "datos/imagenes_demo/foto-$i.jpg" \
    --origen DATASET --fuente "Imágenes sintéticas del proyecto" \
    --licencia "Uso en la demostración académica"
done
python -m memoria_visual.buscar_similares datos/imagenes_demo/foto-5.jpg
```

Para una foto propia usa `--origen PROPIA --cultivo ID`. Las fotos de dataset requieren fuente y licencia. Registra rutas relativas a la raíz del proyecto. Los duplicados exactos se detectan por SHA-256. La búsqueda devuelve hasta cinco filas por distancia coseno ascendente.

## Pruebas

Ejecuta las pruebas en una base de demostración recién inicializada. Las pruebas de Python necesitan una tabla de fotos vacía y retiran las fotos que insertan.

```sh
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" < database/tests/integridad.sql
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" < database/tests/verificar_invernadero.sql
mvn -f java/pom.xml test
PYTHONPATH=python python -m unittest discover -s python/tests -v
PROBAR_CLIP=1 PYTHONPATH=python python -m unittest discover -s python/tests -v
```

La primera prueba de Python usa vectores de prueba para comprobar la conexión, la deduplicación y el orden de resultados en PostgreSQL real. `PROBAR_CLIP=1` ejecuta además el flujo con el modelo preentrenado y cinco fotos.

## Modelo e informe

- [MER editable](docs/modelo/MER_Invernaderos_Final.erd.json) y [diagrama SVG](docs/modelo/MER-FINAL.svg)
- [MER final anotado](docs/modelo/MER-FINAL.pdf) con restricciones del esquema
- [Informe del proyecto](docs/informe/informe.md) y [ejemplos de datos del diseño](docs/informe/DATOS-INVERNADERO.pdf)

La migración conserva el esquema de la primera etapa. El MER editable enumera las nueve tablas y dieciséis claves foráneas. El PDF especifica restricciones adicionales sobre catálogo, alertas y fotos que el SQL también aplica.
