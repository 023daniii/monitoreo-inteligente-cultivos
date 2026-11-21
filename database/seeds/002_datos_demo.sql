BEGIN;

WITH nuevo_terreno AS (
    INSERT INTO terreno (nombre, direccion, latitud, longitud, area_m2)
    VALUES ('Terreno de prueba', 'Sector de demostración', -0.180653, -78.467834, 600)
    RETURNING id_terreno
)
INSERT INTO invernadero (id_terreno, nombre, largo_m, ancho_m, fecha_instalacion)
SELECT id_terreno, 'Invernadero de prueba', 20, 10, DATE '2026-01-05'
FROM nuevo_terreno;

INSERT INTO cultivo (id_invernadero, id_tipo_cultivo, nombre, fecha_siembra)
VALUES (
    (SELECT id_invernadero FROM invernadero WHERE nombre = 'Invernadero de prueba'),
    (SELECT id_catalogo FROM catalogo WHERE codigo = 'CUL_TOMATE'),
    'Tomate de prueba',
    DATE '2026-02-01'
);

INSERT INTO sensor (id_invernadero, id_tipo_sensor, codigo, ubicacion, fecha_instalacion)
VALUES (
    (SELECT id_invernadero FROM invernadero WHERE nombre = 'Invernadero de prueba'),
    (SELECT id_catalogo FROM catalogo WHERE codigo = 'SEN_TEMP'),
    'TEMP-001', 'Centro del invernadero', DATE '2026-01-05'
);

INSERT INTO regla (id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion)
VALUES (
    (SELECT id_catalogo FROM catalogo WHERE codigo = 'CUL_TOMATE'),
    (SELECT id_catalogo FROM catalogo WHERE codigo = 'MED_TEMP_AMB'),
    (SELECT id_catalogo FROM catalogo WHERE codigo = 'SEV_ADVERTENCIA'),
    '>', 30, 'Ventilar el invernadero.'
);

INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor)
VALUES (
    (SELECT id_sensor FROM sensor WHERE codigo = 'TEMP-001'),
    (SELECT id_catalogo FROM catalogo WHERE codigo = 'MED_TEMP_AMB'),
    TIMESTAMP '2026-02-02 13:00:00', 32.50
);

INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
SELECT r.id_regla, m.id_medicion, c.id_cultivo, estado.id_catalogo, m.fecha_hora
FROM medicion AS m
JOIN sensor AS s ON s.id_sensor = m.id_sensor
JOIN cultivo AS c ON c.id_invernadero = s.id_invernadero
JOIN regla AS r ON r.id_tipo_cultivo = c.id_tipo_cultivo
               AND r.id_tipo_medicion = m.id_tipo_medicion
JOIN catalogo AS estado ON estado.codigo = 'EST_PENDIENTE'
WHERE s.codigo = 'TEMP-001'
  AND c.nombre = 'Tomate de prueba'
  AND r.operador = '>'
  AND r.umbral = 30
  AND m.valor > r.umbral;

COMMIT;
