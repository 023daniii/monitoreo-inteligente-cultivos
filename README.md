
# Monitoreo Inteligente de Cultivos

Sistema de monitoreo inteligente de cultivos agrícolas mediante sensores simulados, datos meteorológicos y análisis de información ambiental.

## Objetivos del proyecto

1. **Monitoreo de cultivos:** Registrar temperatura, humedad ambiental y humedad del suelo.

2. **Simulación de sensores:** Generar mediciones automáticamente mediante scripts de Python.

3. **Integración de API:** Obtener información meteorológica mediante Open-Meteo.

4. **Prevención de riesgos:** Detectar condiciones ambientales desfavorables para los cultivos.

5. **Generación de recomendaciones:** Sugerir cuándo revisar el riego o aplicar medidas preventivas.

6. **Almacenamiento de información:** Utilizar PostgreSQL para guardar mediciones, metadatos, históricos y alertas.

7. **Diseño del MER:** Definir entidades, atributos, claves primarias, claves foráneas y relaciones.

8. **Desarrollo de interfaz:** Visualizar mediciones, alertas y gráficos históricos.

9. **Implementación con Docker:** Ejecutar la base de datos y el sistema mediante contenedores.

10. **Validación del funcionamiento:** Probar diferentes escenarios utilizando datos simulados.

## Etapa 1 · Base de datos

Necesitas Docker con Docker Compose v2. Desde la raíz del repositorio, ejecuta:

```sh
docker compose up -d --build --wait
```

Compose inicia PostgreSQL 17 con pgvector 0.8.2. Al crear el volumen por primera vez, PostgreSQL ejecuta los scripts de `db/init/` en orden, crea las nueve tablas y carga el ejemplo. El servicio usa la base `monitoreo_inteligente_cultivos` y el usuario local `invernadero`. Puedes definir `POSTGRES_PASSWORD` antes del primer arranque para sustituir la contraseña de desarrollo. La conexión desde el host se hace con `docker compose exec`; el servicio queda sin un puerto publicado.

### Verificar los datos

```sh
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 \
  -U invernadero -d monitoreo_inteligente_cultivos \
  < db/verificar-invernadero.sql
```

La consulta debe devolver una fila con **Invernadero de prueba**, el sensor **TEMP-001**, el cultivo **Tomate de prueba** y la regla de temperatura **> 30 °C**, asociada a una medición de **32,5 °C**. La regla se aplica por el tipo de cultivo y la magnitud medida. La alerta del ejemplo enlaza la regla con esa medición y con el cultivo, ambos del mismo invernadero.

### Recrear la base desde cero

`docker compose down` detiene el servicio y conserva los datos. Para volver a ejecutar la inicialización, elimina el volumen de desarrollo. **Este comando borra todos los datos de la base local.**

```sh
docker compose down -v
docker compose up -d --build --wait
```

### Referencia del esquema

El esquema conserva las nueve tablas de [ERD editable](docs/MER_Invernaderos_Final.erd.json) y aplica las restricciones adicionales de [MER final en PDF](docs/MER-FINAL.pdf). El PDF especifica los controles de rangos, fechas, evidencia fotográfica, categorías de catálogo y coherencia de alertas, además de los índices de medición y foto. Para la relación entre cultivo y foto se usa `ON DELETE RESTRICT`, como indica el PDF. El PDF también indica la unicidad del código de sensor, del código de catálogo y del hash de imagen, además de las tres unicidades compuestas registradas en el ERD.

`registro_fotografico.embedding` admite vectores de 512 dimensiones. Esta etapa crea la columna y habilita pgvector. La vectorización con CLIP y la búsqueda de imágenes quedan para una etapa posterior.

El [PDF con datos de ejemplo](docs/DATOS-INVERNADERO.pdf) también está disponible en `docs/`.
