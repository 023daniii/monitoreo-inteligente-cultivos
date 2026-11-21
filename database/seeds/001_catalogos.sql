BEGIN;

INSERT INTO catalogo (codigo, nombre) VALUES
    ('CAT_TIPO_CULTIVO', 'Tipos de cultivo'),
    ('CAT_TIPO_SENSOR', 'Tipos de sensor'),
    ('CAT_TIPO_MEDICION', 'Variables de medición'),
    ('CAT_SEVERIDAD', 'Niveles de severidad'),
    ('CAT_ESTADO_ALERTA', 'Estados de alerta')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO catalogo (id_padre, codigo, nombre, unidad)
SELECT padre.id_catalogo, hijo.codigo, hijo.nombre, hijo.unidad
FROM (VALUES
    ('CAT_TIPO_CULTIVO', 'CUL_TOMATE', 'Tomate', NULL),
    ('CAT_TIPO_SENSOR', 'SEN_TEMP', 'Sensor de temperatura', NULL),
    ('CAT_TIPO_MEDICION', 'MED_TEMP_AMB', 'Temperatura ambiental', '°C'),
    ('CAT_SEVERIDAD', 'SEV_ADVERTENCIA', 'Advertencia preventiva', NULL),
    ('CAT_TIPO_MEDICION', 'MED_HUM_SUELO', 'Humedad del suelo', '%'),
    ('CAT_TIPO_MEDICION', 'MED_PH', 'pH del sustrato', 'pH'),
    ('CAT_ESTADO_ALERTA', 'EST_PENDIENTE', 'Pendiente de atención', NULL),
    ('CAT_ESTADO_ALERTA', 'EST_RESUELTA', 'Resuelta', NULL)
) AS hijo (categoria, codigo, nombre, unidad)
JOIN catalogo AS padre ON padre.codigo = hijo.categoria
ON CONFLICT (codigo) DO NOTHING;

COMMIT;
