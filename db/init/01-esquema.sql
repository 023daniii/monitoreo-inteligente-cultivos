BEGIN;

CREATE EXTENSION vector;

CREATE TABLE terreno (
    id_terreno serial PRIMARY KEY,
    nombre varchar(100) NOT NULL,
    direccion varchar(200) NOT NULL,
    latitud numeric(9,6) NOT NULL CHECK (latitud BETWEEN -90 AND 90),
    longitud numeric(9,6) NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    area_m2 numeric(12,2) NOT NULL CHECK (area_m2 > 0),
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    fecha_creacion timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion timestamp
);

CREATE TABLE catalogo (
    id_catalogo serial PRIMARY KEY,
    id_padre integer REFERENCES catalogo,
    codigo varchar(50) NOT NULL UNIQUE,
    nombre varchar(100) NOT NULL,
    unidad varchar(20),
    estado boolean NOT NULL DEFAULT true,
    fecha_creacion timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    fecha_creacion timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    fecha_creacion timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    fecha_creacion timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

CREATE TABLE registro_fotografico (
    id_foto bigserial PRIMARY KEY,
    id_cultivo integer REFERENCES cultivo ON DELETE RESTRICT,
    ruta_imagen text NOT NULL,
    origen varchar(10) NOT NULL CHECK (origen IN ('PROPIA', 'DATASET')),
    fuente varchar(120),
    etiqueta varchar(120),
    licencia varchar(120),
    fecha_captura timestamp,
    fecha_registro timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    embedding vector(512) NOT NULL,
    modelo_embedding varchar(80) NOT NULL,
    estado boolean NOT NULL DEFAULT true,
    hash_imagen char(64) NOT NULL,
    CONSTRAINT registro_fotografico_uq4 UNIQUE (hash_imagen),
    CONSTRAINT foto_cultivo_segun_origen CHECK ((origen = 'PROPIA') = (id_cultivo IS NOT NULL)),
    CONSTRAINT foto_dataset_atribucion CHECK (
        origen <> 'DATASET' OR (fuente IS NOT NULL AND licencia IS NOT NULL)
    )
);

CREATE INDEX medicion_sensor_fecha_idx ON medicion (id_sensor, fecha_hora);
CREATE INDEX registro_fotografico_cultivo_idx ON registro_fotografico (id_cultivo);

-- Cada FK de negocio apunta a un hijo directo de la categoría indicada.
CREATE FUNCTION validar_categoria_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    id_hijo integer := (to_jsonb(NEW) ->> TG_ARGV[0])::integer;
BEGIN
    PERFORM 1
    FROM catalogo AS hijo
    JOIN catalogo AS padre ON padre.id_catalogo = hijo.id_padre
    WHERE hijo.id_catalogo = id_hijo
      AND padre.id_padre IS NULL
      AND padre.codigo = TG_ARGV[1];

    IF NOT FOUND THEN
        RAISE EXCEPTION '%.% debe apuntar a un hijo de %', TG_TABLE_NAME, TG_ARGV[0], TG_ARGV[1]
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER sensor_categoria BEFORE INSERT OR UPDATE ON sensor
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_sensor', 'CAT_TIPO_SENSOR');

CREATE TRIGGER medicion_categoria BEFORE INSERT OR UPDATE ON medicion
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_medicion', 'CAT_TIPO_MEDICION');

CREATE TRIGGER cultivo_categoria BEFORE INSERT OR UPDATE ON cultivo
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_cultivo', 'CAT_TIPO_CULTIVO');

CREATE TRIGGER regla_categoria_cultivo BEFORE INSERT OR UPDATE ON regla
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_cultivo', 'CAT_TIPO_CULTIVO');

CREATE TRIGGER regla_categoria_medicion BEFORE INSERT OR UPDATE ON regla
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_tipo_medicion', 'CAT_TIPO_MEDICION');

CREATE TRIGGER regla_categoria_severidad BEFORE INSERT OR UPDATE ON regla
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_severidad', 'CAT_SEVERIDAD');

CREATE TRIGGER alerta_categoria BEFORE INSERT OR UPDATE ON alerta
FOR EACH ROW EXECUTE FUNCTION validar_categoria_catalogo('id_estado', 'CAT_ESTADO_ALERTA');

CREATE FUNCTION validar_coherencia_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1
    FROM medicion AS m
    JOIN sensor AS s ON s.id_sensor = m.id_sensor
    JOIN cultivo AS c ON c.id_cultivo = NEW.id_cultivo
    JOIN regla AS r ON r.id_regla = NEW.id_regla
    WHERE m.id_medicion = NEW.id_medicion
      AND s.id_invernadero = c.id_invernadero
      AND c.id_tipo_cultivo = r.id_tipo_cultivo
      AND m.id_tipo_medicion = r.id_tipo_medicion;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La medición, el cultivo y la regla de la alerta no son coherentes'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER alerta_coherencia BEFORE INSERT OR UPDATE ON alerta
FOR EACH ROW EXECUTE FUNCTION validar_coherencia_alerta();

COMMIT;
