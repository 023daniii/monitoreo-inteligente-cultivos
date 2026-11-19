# Monitoreo inteligente de cultivos e invernaderos

Proyecto de Fundamentos de Bases de Datos de la Escuela Politécnica Nacional. Construimos una aplicación de escritorio alrededor de dos preguntas que se hace la persona encargada del cultivo. **¿Qué está pasando en mi invernadero? ¿Qué fotos de nuestra colección se parecen a lo que estoy viendo?**

Para la primera pregunta, el programa simula sensores, conserva las mediciones y abre alertas cuando se cumple una regla del cultivo. Para la segunda, convierte una foto en un vector, busca hasta cinco referencias en PostgreSQL y deja que la persona elija cuál examinar. Ambas funciones comparten la base de datos y se usan desde la misma ventana.

[Recorrer la aplicación](#1-recorrido-por-la-aplicación) · [Entender la IA](#3-cómo-funciona-la-memoria-visual) · [Ejecutar en Windows](#5-ejecutar-en-windows-con-powershell) · [Probar la conexión](#7-comprobar-lo-que-se-guardó) · [Resolver problemas de arranque](#9-si-algo-falla)

## 1. Recorrido por la aplicación

Al abrir **Invernaderos · Centro de monitoreo**, la barra lateral permite elegir un terreno y, dentro de él, un invernadero. La demostración incluye **Terreno de prueba**, **La Ribera**, **El Mirador** y **Las Terrazas**. Cada uno tiene cuatro invernaderos. En total hay **16 invernaderos, 16 lotes de tomate y 48 sensores simulados**. El primero, **Invernadero de prueba**, mide 20 × 10 m y contiene el cultivo **Tomate de prueba**.

| Pestaña | Qué se ve | Qué puedes hacer |
| --- | --- | --- |
| **Sensores** | Sensores del invernadero seleccionado, última lectura e historial de hasta 200 mediciones con fecha, magnitud, valor, unidad y origen. | Pulsar **Medir sensores** para tomar una ronda o abrir **Ingresar una lectura individual** para introducir un valor concreto. |
| **Buzón de alertas** | Medición que activó la alerta, cultivo afectado, umbral, severidad, recomendación y estado. | Buscar por terreno, invernadero, cultivo o sensor. Alternar entre **Pendientes**, **Resueltas** y **Todas**. Desmarcar **Solo el invernadero seleccionado** para ver la red completa. |
| **Imágenes** | Foto elegida, referencias ordenadas por similitud y observación de la referencia seleccionada. | Elegir o arrastrar un JPEG o PNG, buscar coincidencias y pulsar una miniatura. También se puede registrar una foto propia para el cultivo seleccionado. |

**Una demostración de principio a fin**

1. Elige **Terreno de prueba → Invernadero de prueba**. En **Sensores**, pulsa **Medir sensores**. Aparecerán lecturas nuevas de temperatura ambiental, humedad del suelo y pH del sustrato. Los valores de la simulación varían en cada ronda.
2. Para producir una alerta predecible, abre **Ingresar una lectura individual**. Elige el sensor `TEMP-001`, selecciona **Temperatura ambiental**, escribe `34.2` y registra la lectura. El tomate tiene una regla activa de temperatura superior a 30 °C.
3. Abre **Buzón de alertas → Pendientes** y selecciona la alerta de esa hora y ese sensor. Verás el valor medido y la recomendación **Ventilar el invernadero.** Pulsa **Marcar como resuelta** y cambia el filtro a **Resueltas**. La alerta conserva su medición original y queda registrada la fecha de resolución.
4. Abre **Imágenes**, pulsa **Elegir foto**, selecciona `datos/imagenes_demo/foto-5.jpg` y pulsa **Buscar similares**. Si hay menos de cinco resultados, pulsa **Cargar las 5 referencias de ejemplo**. Esto registra las fotos de ejemplo que falten y repite la búsqueda.
5. Pulsa dos miniaturas distintas. La observación cambia con la selección. Si quieres conservar tu propia foto como referencia, selecciona el cultivo, añade una observación opcional y pulsa **Registrar foto propia**. Buscar por sí solo deja la foto de consulta fuera de la colección.

## 2. Cómo se convierte una lectura en una alerta

Cada sensor pertenece a un invernadero. Cada lote de cultivo también pertenece a un invernadero. Una regla se configura para un **tipo de cultivo y una magnitud**, por lo que la misma regla de temperatura puede servir a los tomates de varios terrenos. Los valores de la tabla son los de la demostración académica.

| Sensor | Valores que genera el simulador | Condición de alerta para tomate | Recomendación registrada |
| --- | --- | --- | --- |
| Temperatura ambiental | 18 a 36 °C | Mayor que 30 °C | Ventilar el invernadero. |
| Humedad del suelo | 25 a 85 % | Menor que 35 % | Comprobar la humedad y revisar el riego. |
| pH del sustrato | 4,80 a 7,80 | Menor que 5,5 | Verificar la acidez con una segunda muestra. |
| pH del sustrato | 4,80 a 7,80 | Mayor que 7,0 | Verificar la alcalinidad con una segunda muestra. |

**Al pulsar el botón**, Java busca los sensores habilitados del invernadero y genera un valor para cada magnitud activa. Guarda la ronda en una transacción. Para cada lectura nueva, localiza los cultivos vigentes en la fecha de la medición y las reglas activas que coinciden con su tipo de cultivo y magnitud. Si se cumple una condición, guarda una alerta pendiente que enlaza **esa medición, esa regla y ese cultivo**. Por eso 30,0 °C queda en el historial y 30,1 °C además genera una alerta. Resolverla registra la atención que le dio la persona encargada.

**Con la ventana cerrada**, el servicio `medidor` sigue funcionando en Docker. Revisa la red aproximadamente cada minuto. Para cada sensor y magnitud consulta si ya existe una lectura de la última hora. Por ejemplo, si mides temperatura a las 09:20, la siguiente lectura automática de esa magnitud puede entrar en la primera revisión a partir de las 10:20. Los otros sensores conservan su propio horario. El servicio usa la hora de Ecuador continental y consulta PostgreSQL al reiniciar para saber qué sensores están pendientes.

El botón y el medidor coordinan las rondas del mismo invernadero mediante un bloqueo en PostgreSQL. Una magnitud deshabilitada se omite y las demás siguen midiéndose. Una lectura repetida con el mismo sensor, magnitud, instante y valor recupera el registro existente. Si se reintenta con otro valor para ese mismo instante, el sistema indica el conflicto. [Simulador de sensores](java/src/main/java/ec/edu/epn/invernaderos/servicio/SimuladorSensores.java) · [Evaluación y registro](java/src/main/java/ec/edu/epn/invernaderos/servicio/Monitoreo.java)

## 3. Cómo funciona la memoria visual

1. **Elegir.** JavaFX muestra una vista previa del JPEG o PNG. Al pulsar **Buscar similares**, inicia el módulo Python `memoria_visual.escritorio` y le pasa la ruta del archivo. La búsqueda se ejecuta en segundo plano.
2. **Vectorizar.** Python consulta si hay referencias activas del mismo modelo. Con la colección vacía devuelve la opción de cargar ejemplos. Cuando hay referencias, corrige la orientación de la foto, la convierte a RGB y aplica el preprocesamiento de **CLIP ViT-B/32**. El modelo preentrenado se ejecuta en la CPU del equipo y produce 512 números que se normalizan y representan las características visuales de la imagen.
3. **Comparar.** Python envía el vector de consulta a PostgreSQL. La extensión **pgvector** calcula la distancia coseno frente a los vectores activos del mismo modelo en `registro_fotografico`. La colección es compartida entre invernaderos. Las distancias menores indican mayor cercanía visual. Se devuelven hasta cinco resultados, ordenados por distancia y por identificador en caso de empate.
4. **Interpretar.** Python devuelve los resultados a JavaFX en formato JSON. Cada miniatura muestra su distancia y procedencia. Al pulsarla se lee la observación guardada con **esa referencia**, por ejemplo «Hojas de tomate con gotas de agua en la superficie». Si carece de etiqueta, la aplicación lo indica. La distancia mide semejanza visual. Para diagnosticar una enfermedad hace falta una evaluación agronómica de la planta.
5. **Registrar.** Al pulsar **Registrar foto propia**, Python calcula la huella SHA-256 de los bytes para detectar duplicados exactos. Para una foto nueva guarda el archivo en `datos/fotos/` y registra en la base su ruta, cultivo, origen, observación, huella, nombre del modelo y vector. La misma foto puede servir de referencia en futuras búsquedas. Las imágenes de ejemplo tienen origen `DATASET`, fuente y licencia de demostración.

En el código, la parte que decide el orden de las cinco referencias es esta consulta. Python entrega el vector calculado y el nombre del modelo a través de los dos parámetros `%s`.

```sql
SELECT id_foto, ruta_imagen, etiqueta, origen, fuente, licencia,
       embedding <=> %s::vector AS distancia
FROM registro_fotografico
WHERE estado AND modelo_embedding = %s
ORDER BY distancia, id_foto
LIMIT 5
```

El operador `<=>` calcula la distancia coseno. Al consultar `foto-5.jpg` en una colección con solo las cinco referencias de ejemplo, obtuvimos primero `foto-0.jpg` (0,0967) y después `foto-4.jpg` (0,0973). Al pulsar la primera, se lee «Hoja verde de tomate con las nervaduras visibles». Estos números sirven para ordenar la colección. La aplicación muestra las referencias más cercanas disponibles incluso si todas se parecen poco. La huella SHA-256 permite reconocer otro caso distinto, el de intentar registrar dos veces el mismo archivo.

Los seis archivos de ejemplo son imágenes sintéticas. Las fotos 0 a 4 forman la colección inicial que se puede cargar desde la interfaz. La foto 5 se reserva como consulta para probar con una imagen distinta de las referencias. [Imágenes de demostración](datos/imagenes_demo/README.md) · [Generación del vector](python/memoria_visual/embeddings.py) · [Consulta de similitud](python/memoria_visual/buscar_similares.py)

## 4. Arquitectura y modelo de datos

```text
PC del usuario                                      Docker Compose
JavaFX ── Java / JDBC ───────────────────────────► PostgreSQL 17 + pgvector 0.8.2
  │                                                     ▲             ▲
  └── proceso Python / CLIP ── psycopg ──────────────────┘             │
                                                      medidor Java ──┘
```

JavaFX reúne la navegación, los sensores, el buzón y las fotos. En Java, el paquete `config` abre la conexión JDBC, `modelo` representa mediciones, reglas y alertas, `dao` contiene el acceso a datos y `servicio` coordina las operaciones. Python abre su propia conexión a **la misma base** para registrar y consultar vectores. Esto permite cerrar la interfaz y volver a abrirla conservando el historial.

**Las nueve tablas del MER**

| Tabla | Qué representa y con qué se relaciona |
| --- | --- |
| `terreno` | Parcela física con nombre, dirección, coordenadas y área. Agrupa invernaderos. |
| `invernadero` | Instalación con dimensiones, fecha de instalación y clave del terreno. |
| `catalogo` | Jerarquía de categorías y valores. Proporciona tipos de sensor, medición y cultivo, unidades, severidades y estados de alerta. |
| `sensor` | Dispositivo con código único, ubicación, tipo e invernadero donde está instalado. |
| `medicion` | Valor de una magnitud en un instante, asociado al sensor que lo produjo. |
| `cultivo` | Lote sembrado en un invernadero, con tipo y fechas de siembra y cosecha. |
| `regla` | Operador, umbral, severidad y recomendación para un tipo de cultivo y magnitud. |
| `alerta` | Relación entre la medición, la regla activada y el cultivo, con estado y fecha de resolución. |
| `registro_fotografico` | Ruta, origen, etiqueta, huella y vector de 512 componentes. Una foto propia se vincula a un cultivo. Una referencia de dataset guarda fuente y licencia. |

El esquema tiene **16 claves foráneas**. Además de proteger las relaciones, las restricciones impiden duplicar una medición, una regla equivalente, una alerta para la misma combinación o una foto con el mismo hash. Un disparador comprueba que el sensor y el cultivo de una alerta pertenezcan al mismo invernadero y que la regla corresponda a ese tipo de cultivo y medición. [Esquema SQL](database/migrations/001_esquema_inicial.sql) · [MER editable](docs/modelo/MER_Invernaderos_Final.erd.json) · [Diagrama SVG](docs/modelo/MER-FINAL.svg) · [MER anotado en PDF](docs/modelo/MER-FINAL.pdf)

## 5. Ejecutar en Windows con PowerShell

### Preparar el equipo

Instala Docker Desktop con contenedores Linux, un **JDK 17 o posterior**, Maven 3.9 o posterior y **Python 3.12**. Docker aporta PostgreSQL y pgvector. Maven descarga JavaFX y el controlador JDBC. Se necesita internet para la instalación inicial y la primera descarga del modelo. Abre Docker Desktop y espera a que muestre **Engine running**.

Si ya tienes el proyecto, abre en VS Code la carpeta que contiene `compose.yaml` y abre una terminal **PowerShell** allí. Si empiezas con una copia nueva, ejecuta desde la carpeta donde quieras guardarla estos dos comandos, uno por línea.

```powershell
git clone https://github.com/023daniii/monitoreo-inteligente-cultivos.git
Set-Location .\monitoreo-inteligente-cultivos
```

Comprueba que `Test-Path` devuelve `True`, que `docker version` muestra **Client** y **Server**, y que Maven utiliza el JDK esperado.

```powershell
Test-Path .\compose.yaml
docker version
docker compose version
java -version
mvn -version
py -3.12 --version
```

Si Maven está descomprimido en Descargas y PowerShell dice que no reconoce `mvn`, añade su carpeta `bin` a la terminal actual. Este ejemplo usa la carpeta de Maven 3.10.1. Ajusta el nombre a tu instalación. Si falta Python 3.12, puedes instalarlo con `winget install -e --id Python.Python.3.12` y abrir una nueva terminal.

```powershell
$mavenBin = Join-Path $env:USERPROFILE 'Downloads\apache-maven-3.10.1-bin\apache-maven-3.10.1\bin'
$env:Path = "$mavenBin;$env:Path"
mvn -version
```

### Crear la configuración y arrancar Docker

Copia la plantilla la primera vez. El bloque siguiente carga sus valores en PowerShell para que Java y Python reciban la misma configuración que Compose. Ejecuta el bloque completo desde la raíz. Si abres otra terminal y usas valores personalizados, vuelve a cargar el bloque.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
Get-Content .env | ForEach-Object {
    if ($_ -match '^(DB_HOST|DB_PORT|POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD)=(.*)$') {
        Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2]
    }
}
docker compose up -d --build --wait
docker compose ps -a
```

| Servicio | Función | Estado esperado tras el arranque |
| --- | --- | --- |
| `db` | Ejecuta PostgreSQL. En un volumen nuevo carga la extensión, las nueve tablas, los catálogos y las semillas. | En ejecución y saludable (`healthy`). |
| `preparar-red` | Completa catálogos y la red de demostración al arrancar, incluido un volumen de la primera etapa. | Finalizado con código cero (`Exited (0)`). |
| `medidor` | Revisa las mediciones pendientes y genera lecturas y alertas. | En ejecución. Puedes consultar la actividad con `docker compose logs --tail 30 medidor`. |

Los valores locales de ejemplo son host `127.0.0.1`, puerto `5432`, base `monitoreo_inteligente_cultivos`, usuario `invernadero` y contraseña `invernadero_local`. Con ellos, Java abre `jdbc:postgresql://127.0.0.1:5432/monitoreo_inteligente_cultivos`. Dentro de Docker, el medidor usa `db:5432`. Para inspeccionar las tablas con pgAdmin puedes crear una conexión con los mismos valores locales que usa Java. [Plantilla de configuración](.env.example) · [Servicios de Compose](compose.yaml)

### Preparar la IA y abrir la interfaz

```powershell
py -3.12 .\python\preparar.py
mvn -f .\java\pom.xml javafx:run
```

Ejecuta la primera línea y espera a que indique **Memoria visual preparada**. Crea `.venv` e instala las dependencias. La segunda línea abre la ventana JavaFX y ocupa la terminal hasta que la cierres. JavaFX detecta `.venv\Scripts\python.exe` automáticamente. Para elegir otro intérprete, configura `INVERNADEROS_PYTHON` con su ruta completa antes de lanzar Maven. La primera vez que se genera un vector, CLIP descarga unos **338 MiB**. Los pesos se reutilizan en las siguientes consultas. Puedes elegir su directorio con `CLIP_CACHE`.

En sesiones posteriores, abre Docker Desktop, carga tus variables si las personalizaste, ejecuta `docker compose up -d --wait` y abre la interfaz con el mismo comando Maven. Para cerrar los servicios usa `docker compose down`. El volumen `postgres_data` conserva los registros. **Añadir `-v` a ese comando elimina el volumen y sus datos.** Las fotos propias importadas residen en `datos/fotos/` y deben acompañar las copias de seguridad de la base.

### Equivalente en Linux o macOS con Bash

Ejecuta desde la raíz, con Docker, Maven, JDK y Python 3.12 disponibles.

```bash
[ -f .env ] || cp .env.example .env
set -a
. ./.env
set +a
docker compose up -d --build --wait
python3.12 python/preparar.py
mvn -f java/pom.xml javafx:run
```

## 6. Ejecutar los módulos por consola

**Monitoreo Java.** En la terminal donde cargaste las variables, el primer comando ejecuta la demostración de `TEMP-001`. Usa valores de 25, 30 y 34,2 °C en instantes fijos. Solo el último cumple la regla de temperatura para tomate. Al repetir la demo se recuperan los mismos registros. El segundo comando abre el menú interactivo para introducir otra lectura o resolver una alerta por su ID.

```powershell
mvn -f java/pom.xml compile exec:java "-Dexec.args=demo"
mvn -f java/pom.xml compile exec:java
```

**Memoria visual Python.** En PowerShell, el primer comando configura dónde buscar el paquete del proyecto. El segundo registra las referencias de ejemplo que falten y devuelve las coincidencias en JSON. El último realiza solo la búsqueda e imprime la distancia y la ruta de cada referencia.

```powershell
$env:PYTHONPATH = (Resolve-Path .\python).Path
.\.venv\Scripts\python.exe -m memoria_visual.escritorio buscar-demo datos/imagenes_demo/foto-5.jpg
.\.venv\Scripts\python.exe -m memoria_visual.buscar_similares datos/imagenes_demo/foto-5.jpg
```

En Bash, usa `export PYTHONPATH="$PWD/python"` y sustituye `.\.venv\Scripts\python.exe` por `.venv/bin/python`. El módulo `memoria_visual.registrar_foto` también permite registrar archivos existentes mediante rutas relativas al proyecto. Usa `--origen PROPIA --cultivo ID` para un cultivo existente, o `--origen DATASET --fuente FUENTE --licencia LICENCIA` para una referencia externa. Puedes consultar los argumentos con `-m memoria_visual.registrar_foto --help` después del ejecutable de Python.

## 7. Comprobar lo que se guardó

Desde PowerShell, con las variables de la sección 5 cargadas, ejecuta esta línea completa. Lee una consulta SQL del repositorio y la ejecuta con `psql` dentro de Docker.

```powershell
Get-Content -Raw .\database\tests\verificar_invernadero.sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB
```

Entre las filas debe aparecer la medición inicial del **Invernadero de prueba**, con sensor **TEMP-001**, cultivo **Tomate de prueba**, regla **> 30 °C**, valor **32,50 °C** y recomendación **Ventilar el invernadero.** Al tomar nuevas lecturas que activen esa regla pueden aparecer más filas. Puedes seguir las uniones entre alerta, medición, sensor, cultivo y regla en la [consulta de verificación](database/tests/verificar_invernadero.sql).

Para inspeccionar los datos tú mismo, abre una sesión SQL con la siguiente línea.

```powershell
docker compose exec db psql -X -U $env:POSTGRES_USER -d $env:POSTGRES_DB
```

Ya dentro de `psql`, pega estas consultas. La primera identifica la base y la versión de pgvector. En una instalación de demostración recién creada, la segunda devuelve cuatro terrenos con cuatro invernaderos cada uno. La tercera permite comprobar que las fotos registradas tienen un vector de 512 dimensiones. Antes de cargar referencias, esa tabla está vacía. Para volver a PowerShell, escribe `\q`.

```sql
SELECT current_database(), extversion AS version_pgvector
FROM pg_extension WHERE extname = 'vector';

SELECT t.nombre AS terreno, count(i.id_invernadero) AS invernaderos
FROM terreno t LEFT JOIN invernadero i USING (id_terreno)
GROUP BY t.id_terreno, t.nombre ORDER BY t.id_terreno;

SELECT id_foto, origen, ruta_imagen, vector_dims(embedding) AS dimensiones, etiqueta
FROM registro_fotografico ORDER BY id_foto;
```

## 8. Pruebas del proyecto

Usa una **base de demostración separada**, recién inicializada, para las pruebas automatizadas. Las de Java escriben lecturas y resuelven alertas. Las de Python comprueban al empezar que la tabla de fotos está vacía y retiran los registros que crean. Abre una segunda terminal PowerShell en la raíz y carga ahí las variables de `.env` según la sección 5. A continuación asigna un nombre de proyecto nuevo y un puerto libre. Compose creará un volumen independiente para ese nombre.

```powershell
$env:COMPOSE_PROJECT_NAME = "invernaderos-pruebas"
$env:DB_PORT = "55432"
docker compose up -d --build --wait
```

Ejecuta el siguiente bloque en esa misma terminal. Java y Python usarán el puerto de prueba y Compose dirigirá los comandos al proyecto `invernaderos-pruebas`.

```powershell
Get-Content -Raw .\database\tests\integridad.sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB
mvn -f java/pom.xml test
$env:PYTHONPATH = (Resolve-Path .\python).Path
$env:PROBAR_CLIP = "1"
.\.venv\Scripts\python.exe -m unittest discover -s python/tests -v
docker compose down
```

La prueba SQL termina con **Integridad verificada**. Comprueba la estructura del MER, la red de sensores y el rechazo de relaciones o duplicados inválidos. JUnit prueba el registro y la resolución de alertas, los reintentos, el intervalo de una hora y los sensores o magnitudes deshabilitados. Python comprueba la galería vacía, los duplicados, el registro fallido y el orden de similitud en PostgreSQL. Con `PROBAR_CLIP=1` incluye la generación de vectores con el modelo real. La interfaz permite repetir el recorrido manual de la sección 1 en la base de trabajo.

## 9. Si algo falla

| Mensaje o síntoma | Qué comprobar |
| --- | --- |
| PowerShell no encuentra `.env.example` o Compose no encuentra la configuración | Ejecuta `Get-Location` y `Test-Path .\compose.yaml`. Abre la carpeta del repositorio. Las rutas con espacios funcionan entre comillas con `Set-Location`. |
| Docker informa que falta `dockerDesktopLinuxEngine` | Abre Docker Desktop y espera a que arranque el motor Linux. Comprueba que `docker version` muestre la sección **Server** y vuelve a levantar Compose. |
| PowerShell no reconoce `mvn` o Maven indica que falta un objetivo | Usa la carpeta `bin` de Maven como se indica en la sección 5. Ejecuta la línea completa `mvn -f java/pom.xml javafx:run` para abrir la interfaz. |
| Maven no encuentra el prefijo `javafx` | Desde la raíz verifica `Test-Path .\java\pom.xml`. Ese archivo contiene el complemento JavaFX. Pásalo con `-f` y usa una copia del proyecto que incluya la interfaz. |
| La interfaz no conecta a PostgreSQL | Comprueba `docker compose ps -a` y `docker compose port db 5432`. Verifica host, puerto, base y credenciales en la terminal que lanza Java. Si el puerto está ocupado, cambia `DB_PORT` en `.env`, recarga las variables y vuelve a ejecutar Compose. |
| El panel de imágenes indica que falta Python o un módulo | Ejecuta `py -3.12 .\python\preparar.py` desde la raíz. Si elegiste un intérprete mediante `INVERNADEROS_PYTHON`, comprueba que apunte al entorno preparado. |
| Se muestra la foto elegida pero faltan coincidencias | Pulsa **Buscar similares** y lee el mensaje en **Referencias similares**. Si ofrece cargar los ejemplos, pulsa el botón. En la primera vectorización espera a que termine la descarga del modelo. |

## 10. Dónde está cada parte del trabajo

| Ubicación | Contenido |
| --- | --- |
| `database/migrations/`, `database/seeds/`, `database/tests/` | Esquema derivado del MER, carga del catálogo, escenario inicial, ampliación a cuatro terrenos y pruebas SQL. |
| `java/` | Proyecto Maven con la ventana JavaFX, menú de consola, acceso JDBC, servicio horario y pruebas JUnit. |
| `python/` | Preparación de dependencias, conexión a PostgreSQL, enlace con JavaFX, cálculo de vectores, registro, búsqueda y pruebas. |
| `datos/imagenes_demo/` y `datos/fotos/` | Fotos de muestra incluidas con el proyecto y carpeta local que se crea al importar fotos propias. |
| `docs/modelo/` y `docs/informe/` | Fuentes del diseño, diagrama e informe académico. |

La entrega reúne el modelo relacional, la base reproducible con Docker, el simulador con alertas, la interfaz de operación y la búsqueda visual con un modelo preentrenado. Para revisar las decisiones del diseño y los datos de partida, consulta el [informe del proyecto](docs/informe/informe.md) y los [ejemplos del diseño original](docs/informe/DATOS-INVERNADERO.pdf).
