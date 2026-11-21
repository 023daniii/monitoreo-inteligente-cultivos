# Monitoreo inteligente de cultivos e invernaderos

Proyecto de Fundamentos de Bases de Datos, Escuela Politécnica Nacional. El informe está en [docs/Informe_BD1.pdf](docs/Informe_BD1.pdf).

El sistema responde dos preguntas del agricultor:

1. **¿Qué está pasando en mi invernadero?** Los sensores (simulados) guardan mediciones. Cuando una medición incumple una regla del cultivo se abre una alerta con su recomendación.
2. **¿Qué tiene mi planta?** El agricultor sube una foto y el sistema devuelve las **5 fotos más parecidas** de una colección de **200 fotos de tomate con diagnóstico**. El diagnóstico y la solución de cada foto **solo se muestran cuando el agricultor la selecciona**.

## Quién hace qué

```text
JavaFX (Java) ── JDBC ──────────────► PostgreSQL 17 + pgvector
   │  1. pide el vector de la foto        ▲   3. Java busca las 5 más cercanas (<=>)
   ▼                                      │
Python + CLIP ── 2. vector de 512 ────────┘   (Python también guarda los vectores de las 200 referencias)
```

| Parte | Responsabilidad |
| --- | --- |
| **Java + JavaFX** | Programa principal: interfaz, simulación de mediciones, alertas, buzón y búsqueda por similitud con pgvector (`FotoDao`). |
| **Python** (`python/memoria_visual.py`) | Solo convierte fotos en vectores CLIP ViT-B/32 y los guarda. JavaFX lo abre una vez como proceso auxiliar, así el modelo se carga una sola vez por sesión. |
| **PostgreSQL + pgvector** | Las 9 tablas del MER, restricciones, disparadores de coherencia y la distancia coseno entre vectores. |

## Estructura

```text
database/   01_esquema.sql · 02_catalogos.sql · 03_datos_demo.sql · pruebas.sql · Dockerfile
java/       Proyecto Maven (interfaz, DAO, servicios, pruebas JUnit) · Dockerfile del medidor horario
python/     memoria_visual.py · preparar.py · requirements.txt · test_memoria_visual.py
datos/      referencias/ (200 fotos + referencias.csv) · consultas/ (8 fotos para probar) · fotos/ (fotos propias, local)
docs/       Informe, MER editable (.erd.json), diagrama y datos ilustrativos del diseño
```

## Ejecutar en Windows (PowerShell)

Necesitas Docker Desktop, JDK 17 o posterior, Maven 3.9 o posterior y Python 3.12. Desde la carpeta del repositorio:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --build --wait
py -3.12 .\python\preparar.py
mvn -f .\java\pom.xml javafx:run
```

- `docker compose up` crea la base con el esquema, los catálogos (incluidos los 8 diagnósticos) y los datos de demostración, y arranca el **medidor**, que toma una lectura por sensor cada hora aunque la ventana esté cerrada.
- `preparar.py` crea `.venv`, instala CLIP y **carga las 200 fotos de referencia**. La primera vez descarga el modelo (unos 338 MiB) y tarda unos minutos.
- Si ya tenías una versión anterior del proyecto, borra una sola vez la base antigua con `docker compose down -v` antes de `up`: el esquema cambió.

En Linux o macOS: `cp .env.example .env`, `docker compose up -d --build --wait`, `python3.12 python/preparar.py` y `mvn -f java/pom.xml javafx:run`.

## Recorrido de demostración

1. **Sensores.** Elige *Finca San José → Invernadero A2* y pulsa **Medir sensores**. Los valores siguen un ciclo diario realista (más calor al mediodía).
2. **Alerta.** Abre **Ingresar una lectura individual**, elige `SEN-TEMP-A2`, escribe `34.2` y guarda. Se crea una alerta por la regla *temperatura > 30 °C*. Si vuelves a registrar otra temperatura alta, no se repite la alerta mientras siga pendiente. Una lectura de más de 35 °C sí abre la alerta crítica.
3. **Buzón de alertas.** Selecciona la alerta, adjunta una foto de evidencia si quieres y pulsa **Marcar como resuelta**.
4. **Imágenes.** Pulsa **Elegir foto** (se abre `datos/consultas`), elige por ejemplo `tizon-tardio-01.jpg` y pulsa **Buscar parecidas**. Aparecen 5 fotos con su porcentaje de similitud, sin diagnóstico. Al pulsar una se muestran su **diagnóstico, descripción, qué hacer** y su fuente.
5. Para guardar una foto propia, elige el cultivo, el diagnóstico si ya lo confirmaste y pulsa **Guardar foto del cultivo**.

## Modelo de datos

Nueve tablas y 17 claves foráneas ([esquema](database/01_esquema.sql), [MER editable](docs/MER_Invernaderos_Final.erd.json)):

- `catalogo` es jerárquico. Las magnitudes cuelgan del tipo de sensor que las mide (`CAT_TIPO_SENSOR → SEN_TEMP → MED_TEMP_AMB`), así la base impide registrar pH con un termómetro. Los diagnósticos (`CAT_DIAGNOSTICO`) guardan su descripción y su recomendación una sola vez, no repetidas en cada foto.
- `registro_fotografico` guarda la ruta, el origen, la fuente, la licencia, el hash SHA-256, el vector `vector(512)` y el diagnóstico (`id_diagnostico`, obligatorio para las fotos del dataset).
- Los disparadores verifican que:
  - cada valor de catálogo pertenezca a la categoría correcta;
  - la medición sea de una magnitud del sensor y esté en un rango físico posible (% entre 0 y 100, pH entre 0 y 14);
  - la alerta corresponda a una regla que realmente se incumple, para un cultivo vigente del mismo invernadero;
  - el estado *Resuelta* tenga fecha de resolución.
- La base trabaja en hora de Ecuador (`America/Guayaquil`) y tiene índices en las claves foráneas usadas en las uniones.

## Pruebas

Usa una base recién creada (por ejemplo con `docker compose down -v` y `up` de nuevo, sin cargar referencias):

```powershell
Get-Content -Raw .\database\pruebas.sql | docker compose exec -T db psql -X -U invernadero -d monitoreo_inteligente_cultivos
mvn -f java/pom.xml test
.\.venv\Scripts\python.exe -m unittest discover -s python -v
```

`pruebas.sql` termina con **Integridad verificada** y comprueba 12 casos que la base debe rechazar. Las pruebas JUnit y SQL revierten sus cambios. Las de Python exigen que no haya fotos registradas; con `$env:PROBAR_CLIP = "1"` también prueban el modelo real.

## Procedencia de las fotos

Las 200 referencias y las 8 fotos de consulta provienen de **PlantDoc** (Singh et al., 2020, *PlantDoc: A Dataset for Visual Plant Disease Detection*, CoDS-COMAD), publicado con licencia **CC BY 4.0**. Hay 25 fotos por diagnóstico: planta sana, tizón temprano, tizón tardío, Septoria, mancha bacteriana, moho de la hoja, virus del mosaico y virus del rizado amarillo. Se redujeron a 640 px como máximo. Se descartaron diagramas, collages y fotos que no eran de tomate o tenían la etiqueta equivocada. `referencias.csv` conserva la ruta original de cada foto en el dataset. PlantDoc reunió fotos publicadas en internet; algunas conservan la marca de agua de su sitio original.

La similitud es visual y sirve para orientar la revisión. El diagnóstico final debe confirmarlo una persona con formación agronómica.

## Si algo falla

| Síntoma | Qué hacer |
| --- | --- |
| La interfaz no conecta a PostgreSQL | `docker compose ps`; revisa `DB_PORT` en `.env` si el puerto 5432 está ocupado. |
| Error de columnas o tablas al abrir | La base es de una versión anterior: `docker compose down -v` y `docker compose up -d --build --wait`. |
| "No se pudo iniciar Python" o falta un módulo | Ejecuta `py -3.12 .\python\preparar.py`. Para otro intérprete, define `INVERNADEROS_PYTHON`. |
| La búsqueda dice que no hay referencias | Pulsa **Cargar las 200 fotos de referencia** o repite `preparar.py` con Docker encendido. |
