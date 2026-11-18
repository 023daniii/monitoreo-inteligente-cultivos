import os

import psycopg


def conectar():
    return psycopg.connect(
        host=os.getenv("DB_HOST") or "127.0.0.1",
        port=os.getenv("DB_PORT") or "5432",
        dbname=os.getenv("POSTGRES_DB") or "monitoreo_inteligente_cultivos",
        user=os.getenv("POSTGRES_USER") or "invernadero",
        password=os.getenv("POSTGRES_PASSWORD") or "invernadero_local",
        connect_timeout=5,
    )
