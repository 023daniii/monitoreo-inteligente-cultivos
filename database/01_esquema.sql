-- Esquema físico del MER: 9 tablas, 17 claves foráneas y la extensión pgvector.
BEGIN;

CREATE EXTENSION vector;

-- Todas las fechas del sistema se guardan en hora de Ecuador continental.
SET LOCAL timezone TO 'America/Guayaquil';
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', current_database(), 'America/Guayaquil');
END;
$$;

CREATE TABLE terreno (
    id_terreno serial PRIMARY KEY,
    nombre varchar(100) NOT NULL,
    direccion varchar(200) NOT NULL,
    latitud numeric(9,6) NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud numeric(9,6) NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    area_m2 numeric(12,2) NOT NULL CHECK (area_m2 > 0),
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    fecha_modificacion timestamp
);

CREATE TABLE invernadero (
    id_invernadero serial PRIMARY KEY,
    id_terreno integer NOT NULL REFERENCES terreno,
    nombre varchar(100) NOT NULL,
    largo_m numeric(8,2) NOT NULL CHECK (largo_m > 0),
    ancho_m numeric(8,2) NOT NULL CHECK (ancho_m > 0),
    fecha_instalacion date NOT NULL,
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    fecha_modificacion timestamp
);

-- Jerarquía de valores: categoría raíz -> valor -> subvalor.
-- Las magnitudes cuelgan del tipo de sensor que las mide (CAT_TIPO_SENSOR -> SEN_TEMP -> MED_TEMP_AMB).
-- descripcion y recomendacion se usan, por ejemplo, en los diagnósticos de las fotos.
CREATE TABLE catalogo (
    id_catalogo serial PRIMARY KEY,
    id_padre integer REFERENCES catalogo CHECK (id_padre <> id_catalogo),
    codigo varchar(50) NOT NULL UNIQUE,
    nombre varchar(100) NOT NULL,
    unidad varchar(20),
    descripcion text,
    recomendacion text,
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    fecha_modificacion timestamp
);

CREATE TABLE sensor (
    id_sensor serial PRIMARY KEY,
    id_invernadero integer NOT NULL REFERENCES invernadero,
    id_tipo_sensor integer NOT NULL REFERENCES catalogo,
    codigo varchar(50) NOT NULL UNIQUE,
    ubicacion varchar(150) NOT NULL,
    fecha_instalacion date NOT NULL,
    metadatos jsonb,
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    fecha_modificacion timestamp
);

CREATE TABLE medicion (
    id_medicion bigserial PRIMARY KEY,
    id_sensor integer NOT NULL REFERENCES sensor,
    id_tipo_medicion integer NOT NULL REFERENCES catalogo,
    fecha_hora timestamp NOT NULL,
    valor numeric(12,4) NOT NULL,
    metadatos jsonb,
    CONSTRAINT medicion_uq3 UNIQUE (id_sensor, id_tipo_medicion, fecha_hora)
);

CREATE TABLE cultivo (
    id_cultivo serial PRIMARY KEY,
    id_invernadero integer NOT NULL REFERENCES invernadero,
    id_tipo_cultivo integer NOT NULL REFERENCES catalogo,
    nombre varchar(100) NOT NULL,
    fecha_siembra date NOT NULL,
    fecha_cosecha_estimada date CHECK (fecha_cosecha_estimada >= fecha_siembra),
    fecha_cosecha_real date CHECK (fecha_cosecha_real >= fecha_siembra),
    fecha_creacion timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    fecha_modificacion timestamp
);

CREATE TABLE regla (
    id_regla serial PRIMARY KEY,
    id_tipo_cultivo integer NOT NULL REFERENCES catalogo,
    id_tipo_medicion integer NOT NULL REFERENCES catalogo,
    id_severidad integer NOT NULL REFERENCES catalogo,
    operador varchar(2) NOT NULL CHECK (operador IN ('>', '>=', '<', '<=', '=')),
    umbral numeric(12,4) NOT NULL,
    recomendacion text NOT NULL,
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    fecha_modificacion timestamp,
    CONSTRAINT regla_uq2 UNIQUE (id_tipo_cultivo, id_tipo_medicion, operador, umbral)
);

CREATE TABLE alerta (
    id_alerta bigserial PRIMARY KEY,
    id_regla integer NOT NULL REFERENCES regla,
    id_medicion bigint NOT NULL REFERENCES medicion,
    id_cultivo integer NOT NULL REFERENCES cultivo,
    id_estado integer NOT NULL REFERENCES catalogo,
    evidencia_foto bytea,
    formato_evidencia varchar(20) CHECK (formato_evidencia IN ('jpg', 'jpeg', 'png', 'webp')),
    fecha_captura_evidencia timestamp,
    fecha_hora timestamp NOT NULL,
    fecha_resolucion timestamp CHECK (fecha_resolucion >= fecha_hora),
    fecha_modificacion timestamp,
    CONSTRAINT alerta_uq1 UNIQUE (id_regla, id_medicion, id_cultivo),
    CONSTRAINT alerta_evidencia_completa CHECK (
        num_nonnulls(evidencia_foto, formato_evidencia, fecha_captura_evidencia) IN (0, 3)
    )
);

-- Memoria visual: cada referencia del dataset apunta a su diagnóstico en el catálogo.
CREATE TABLE registro_fotografico (
    id_foto bigserial PRIMARY KEY,
    id_cultivo integer REFERENCES cultivo ON DELETE RESTRICT,
    id_diagnostico integer REFERENCES catalogo,
    ruta_imagen text NOT NULL,
    origen varchar(10) NOT NULL CHECK (origen IN ('PROPIA', 'DATASET')),
    fuente varchar(200),
    etiqueta varchar(120),
    licencia varchar(120),
    fecha_captura timestamp,
    fecha_registro timestamp NOT NULL DEFAULT LOCALTIMESTAMP,
    embedding vector(512) NOT NULL,
    modelo_embedding varchar(80) NOT NULL,
    estado boolean NOT NULL DEFAULT true,
    hash_imagen char(64) NOT NULL,
    CONSTRAINT registro_fotografico_uq4 UNIQUE (hash_imagen),
    CONSTRAINT foto_cultivo_segun_origen CHECK ((origen = 'PROPIA') = (id_cultivo IS NOT NULL)),
    CONSTRAINT foto_dataset_completa CHECK (
        origen <> 'DATASET' OR (fuente IS NOT NULL AND licencia IS NOT NULL AND id_diagnostico IS NOT NULL)
    )
);

-- Índices para las uniones frecuentes. Las FK hacia catalogo no se indexan: es una tabla pequeña
-- que se consulta por su clave primaria. Con 200 referencias la búsqueda vectorial exacta
-- (sin índice HNSW) es rápida y no pierde resultados.
CREATE INDEX medicion_sensor_fecha_idx ON medicion (id_sensor, fecha_hora);
CREATE INDEX invernadero_terreno_idx ON invernadero (id_terreno);
CREATE INDEX sensor_invernadero_idx ON sensor (id_invernadero);
CREATE INDEX cultivo_invernadero_idx ON cultivo (id_invernadero);
CREATE INDEX alerta_medicion_idx ON alerta (id_medicion);
CREATE INDEX alerta_cultivo_idx ON alerta (id_cultivo);
CREATE INDEX registro_fotografico_cultivo_idx ON registro_fotografico (id_cultivo);

-- Categoría raíz y nivel de un valor del catálogo (1 = hijo directo de la categoría).
CREATE FUNCTION catalogo_categoria(integer, OUT raiz varchar, OUT nivel integer)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE camino AS (
        SELECT id_catalogo, id_padre, codigo, 0 AS nivel FROM catalogo WHERE id_catalogo = $1
        UNION ALL
        SELECT p.id_catalogo, p.id_padre, p.codigo, c.nivel + 1
        FROM catalogo p JOIN camino c ON p.id_catalogo = c.id_padre
        WHERE c.nivel < 10
    )
    SELECT codigo, nivel FROM camino WHERE id_padre IS NULL;
$$;

CREATE FUNCTION cumple_regla(valor numeric, operador varchar, umbral numeric) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE operador
        WHEN '>' THEN valor > umbral WHEN '>=' THEN valor >= umbral
        WHEN '<' THEN valor < umbral WHEN '<=' THEN valor <= umbral
        WHEN '=' THEN valor = umbral END;
$$;

-- Cada FK de negocio hacia catalogo debe apuntar a la categoría y al nivel indicados.
CREATE FUNCTION validar_categoria_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    id_valor integer := (to_jsonb(NEW) ->> TG_ARGV[0])::integer;
    categoria record;
BEGIN
    IF id_valor IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT * INTO categoria FROM catalogo_categoria(id_valor);
    IF categoria.raiz IS DISTINCT FROM TG_ARGV[1] OR categoria.nivel <> TG_ARGV[2]::integer THEN
        RAISE EXCEPTION '%.% debe ser un valor de %', TG_TABLE_NAME, TG_ARGV[0], TG_ARGV[1]
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER sensor_categoria BEFORE INSERT OR UPDATE ON sensor
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_sensor', 'CAT_TIPO_SENSOR', 1);

CREATE TRIGGER medicion_categoria BEFORE INSERT OR UPDATE ON medicion
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_medicion', 'CAT_TIPO_SENSOR', 2);

CREATE TRIGGER cultivo_categoria BEFORE INSERT OR UPDATE ON cultivo
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_cultivo', 'CAT_TIPO_CULTIVO', 1);

CREATE TRIGGER regla_categoria_cultivo BEFORE INSERT OR UPDATE ON regla
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_cultivo', 'CAT_TIPO_CULTIVO', 1);

CREATE TRIGGER regla_categoria_medicion BEFORE INSERT OR UPDATE ON regla
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_medicion', 'CAT_TIPO_SENSOR', 2);

CREATE TRIGGER regla_categoria_severidad BEFORE INSERT OR UPDATE ON regla
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_severidad', 'CAT_SEVERIDAD', 1);

CREATE TRIGGER alerta_categoria BEFORE INSERT OR UPDATE ON alerta
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_estado', 'CAT_ESTADO_ALERTA', 1);

CREATE TRIGGER foto_categoria BEFORE INSERT OR UPDATE ON registro_fotografico
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_diagnostico', 'CAT_DIAGNOSTICO', 1);

-- Una medición debe ser de una magnitud que mide ese tipo de sensor y estar en un rango físico posible.
CREATE FUNCTION validar_medicion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    tipo_sensor integer;
    magnitud record;
    posible boolean;
BEGIN
    SELECT id_tipo_sensor INTO tipo_sensor FROM sensor WHERE id_sensor = NEW.id_sensor;
    SELECT id_padre, nombre, unidad INTO magnitud FROM catalogo WHERE id_catalogo = NEW.id_tipo_medicion;
    IF magnitud.id_padre IS DISTINCT FROM tipo_sensor THEN
        RAISE EXCEPTION 'Ese sensor no mide %', magnitud.nombre USING ERRCODE = '23514';
    END IF;
    posible := CASE magnitud.unidad
        WHEN '%' THEN NEW.valor BETWEEN 0 AND 100
        WHEN 'pH' THEN NEW.valor BETWEEN 0 AND 14
        WHEN '°C' THEN NEW.valor BETWEEN -50 AND 70
        ELSE true END;
    IF NOT posible THEN
        RAISE EXCEPTION 'El valor % % está fuera del rango posible para %', NEW.valor, magnitud.unidad,
            magnitud.nombre USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER medicion_valida BEFORE INSERT OR UPDATE ON medicion
FOR EACH ROW EXECUTE FUNCTION validar_medicion();

-- La alerta debe unir una medición, un cultivo vigente del mismo invernadero y una regla que se cumple.
-- Además, el estado y la fecha de resolución deben decir lo mismo.
CREATE FUNCTION validar_coherencia_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    estado_alerta varchar;
BEGIN
    IF TG_OP = 'INSERT' OR (NEW.id_regla, NEW.id_medicion, NEW.id_cultivo)
                           IS DISTINCT FROM (OLD.id_regla, OLD.id_medicion, OLD.id_cultivo) THEN
        PERFORM 1
        FROM medicion AS m
        JOIN sensor AS s ON s.id_sensor = m.id_sensor
        JOIN cultivo AS c ON c.id_cultivo = NEW.id_cultivo AND c.id_invernadero = s.id_invernadero
        JOIN regla AS r ON r.id_regla = NEW.id_regla
                       AND r.id_tipo_cultivo = c.id_tipo_cultivo
                       AND r.id_tipo_medicion = m.id_tipo_medicion
        WHERE m.id_medicion = NEW.id_medicion
          AND c.fecha_siembra <= m.fecha_hora::date
          AND (c.fecha_cosecha_real IS NULL OR c.fecha_cosecha_real >= m.fecha_hora::date)
          AND cumple_regla(m.valor, r.operador, r.umbral);
        IF NOT FOUND THEN
            RAISE EXCEPTION 'La alerta no corresponde a una regla incumplida por esa medición y ese cultivo'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT codigo INTO estado_alerta FROM catalogo WHERE id_catalogo = NEW.id_estado;
    IF (estado_alerta = 'EST_RESUELTA') <> (NEW.fecha_resolucion IS NOT NULL) THEN
        RAISE EXCEPTION 'Una alerta resuelta necesita fecha de resolución y una pendiente no debe tenerla'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER alerta_coherencia BEFORE INSERT OR UPDATE ON alerta
FOR EACH ROW EXECUTE FUNCTION validar_coherencia_alerta();

COMMIT;
