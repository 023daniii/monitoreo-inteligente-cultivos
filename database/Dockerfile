FROM pgvector/pgvector:0.8.2-pg17-bookworm

COPY --chmod=644 migrations/001_esquema_inicial.sql /docker-entrypoint-initdb.d/01-esquema.sql
COPY --chmod=644 seeds/001_catalogos.sql /docker-entrypoint-initdb.d/02-catalogos.sql
COPY --chmod=644 seeds/002_datos_demo.sql /docker-entrypoint-initdb.d/03-demo.sql
COPY --chmod=644 seeds/003_red_invernaderos.sql /docker-entrypoint-initdb.d/04-red.sql
