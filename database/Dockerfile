FROM pgvector/pgvector:0.8.2-pg17-bookworm

COPY --chmod=644 01_esquema.sql 02_catalogos.sql 03_datos_demo.sql /docker-entrypoint-initdb.d/
