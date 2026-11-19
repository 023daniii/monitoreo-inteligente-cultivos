# Monitoreo inteligente de cultivos e invernaderos

Proyecto de Fundamentos de Bases de Datos de la Escuela Politécnica Nacional. Java simula lecturas, evalúa reglas y genera alertas. Python registra fotografías y busca imágenes similares con CLIP ViT-B/32. Ambos programas usan la misma base PostgreSQL con pgvector. La demostración funciona sin una API meteorológica.

## Requisitos

- Docker con Compose v2
- JDK 17 o posterior y Maven 3.9
- Python 3.12 para la memoria visual
- Internet en la primera instalación de dependencias y descarga de los pesos de CLIP

Ejecuta los comandos desde la carpeta que contiene `compose.yaml`.

## Levantar la base desde cero

En Windows, abre Docker Desktop, espera a que indique **Engine running** y ejecuta en PowerShell

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
Get-Content .env | ForEach-Object {
    if ($_ -match '^(DB_HOST|DB_PORT|POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD)=(.*)$') {
        Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2]
    }
}
docker compose up -d --build --wait
```

En Bash

```sh
[ -f .env ] || cp .env.example .env
set -a
. ./.env
set +a
docker compose up -d --build --wait
```

Usa `.env.example` como plantilla. Java y Python leen `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` del entorno. Abre la aplicación desde esa misma terminal para que reciba los valores que cargaste. Compose publica PostgreSQL solo en la interfaz local. Cambia `DB_PORT` si el 5432 ya está ocupado. Las credenciales de ejemplo son para uso local.

En un volumen nuevo, PostgreSQL 17 habilita pgvector 0.8.2 y carga el esquema y las semillas. En un volumen de la versión anterior, el servicio `preparar-red` amplía el escenario al arrancar. Puedes ejecutar el mismo comando de Compose para actualizar tu instalación con los datos existentes. Cuando termine la preparación, el servicio `medidor` comenzará las mediciones automáticas.

`docker compose down` conserva el volumen. Para recrearlo, usa `docker compose down -v` y vuelve a levantar Compose. **La opción `-v` borra los datos locales.**

## Demostración de monitoreo en Java

```sh
mvn -f java/pom.xml compile exec:java -Dexec.args=demo
mvn -f java/pom.xml compile exec:java
```

El primer comando registra lecturas de 25, 30 y 34,2 °C con el sensor `TEMP-001`. Solo la última activa la regla `> 30 °C` para el tomate. Si repites el comando, se recuperan los mismos registros. El segundo comando abre un menú para registrar otra lectura o resolver una alerta por su ID.

## Demostración de memoria visual en Python

Prepara el entorno de la IA una vez, desde PowerShell

```powershell
py -3.12 .\python\preparar.py
```

Si Windows no reconoce `py -3.12`, instala Python 3.12 con `winget install -e --id Python.Python.3.12` y vuelve a abrir PowerShell.

En Bash usa

```sh
python3.12 python/preparar.py
```

El script instala las dependencias en `.venv` y verifica que se puedan importar. JavaFX detecta ese intérprete al usar el apartado **Imágenes**. Si configuras `INVERNADEROS_PYTHON`, se usará la ruta que indiques. CLIP descarga y verifica los pesos de ViT-B/32 la primera vez que genera un vector. Puedes indicar su carpeta de caché con `CLIP_CACHE`.

Para probar desde la interfaz, elige `datos/imagenes_demo/foto-5.jpg` y pulsa **Buscar similares**. Si aparecen menos de cinco resultados, pulsa **Cargar las 5 referencias de ejemplo**. La aplicación registra las imágenes 0 a 4 que falten en PostgreSQL y muestra las coincidencias. Pulsa una miniatura para leer su observación. La foto que consultas solo se guarda si eliges **Registrar foto propia**.

También puedes ejecutar la demostración desde Bash

```sh
. .venv/bin/activate
export PYTHONPATH="$PWD/python"
python -m memoria_visual.escritorio buscar-demo datos/imagenes_demo/foto-5.jpg
python -m memoria_visual.buscar_similares datos/imagenes_demo/foto-5.jpg
```

Para registrar una foto por consola usa `python -m memoria_visual.registrar_foto RUTA --origen PROPIA --cultivo ID`, con una ruta relativa al proyecto. Las fotos de dataset requieren fuente y licencia. Los duplicados exactos se detectan por SHA-256. La búsqueda devuelve hasta cinco filas por distancia coseno ascendente.

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

## Aplicación de escritorio JavaFX

Desde la raíz, con Docker Desktop abierto y el JDK y Maven disponibles, ejecuta:

```sh
docker compose up -d --build --wait
mvn -f java/pom.xml javafx:run
```

La barra lateral permite elegir entre cuatro terrenos, cada uno con cuatro invernaderos. Cada ficha muestra las dimensiones, el cultivo y tres sensores: temperatura ambiental (°C), humedad del suelo (%) y pH del sustrato.

- **Sensores.** Elige un invernadero y pulsa **Medir sensores** para simular y guardar las tres lecturas en una transacción. Las tarjetas y el historial se actualizan al terminar. Para probar un valor exacto abre **Ingresar una lectura individual**. El historial distingue las mediciones manuales y automáticas y muestra hasta 200 registros.
- **Medición automática.** Mientras Docker y el servicio `medidor` estén en ejecución, se revisa cada minuto si cada sensor lleva una hora sin registrar su magnitud. Una medición manual reinicia la espera de ese sensor. Al arrancar, el servicio mide los sensores pendientes y utiliza el historial de PostgreSQL para retomar el ciclo. La ventana puede estar cerrada. Se usa la hora de Ecuador continental.
- **Buzón de alertas.** Muestra las alertas del invernadero seleccionado. Desmarca el filtro local para consultar toda la red. Puedes buscar por sensor, invernadero, terreno o cultivo, filtrar por estado, revisar la lectura y la recomendación y marcar una alerta como resuelta.
- **Imágenes.** Selecciona o arrastra un archivo JPEG o PNG. Puedes registrar la foto en uno de los cultivos del invernadero elegido o buscar las cinco referencias más próximas en la colección compartida. Pulsa una miniatura para consultar su observación.

Conserva `datos/fotos/` junto con las copias de seguridad de la base. Contiene los archivos de las fotos propias importadas. La observación pertenece a la referencia seleccionada. Un diagnóstico de la planta requiere revisión agronómica.
