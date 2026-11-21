-- Datos de demostración: 3 terrenos, 9 invernaderos, 9 lotes de tomate, 27 sensores y 7 reglas.
BEGIN;

INSERT INTO terreno (nombre, direccion, latitud, longitud, area_m2) VALUES
    ('Finca San José', 'Km 12 vía Cayambe, Pichincha', 0.040800, -78.145200, 15000),
    ('Hacienda El Rosal', 'Sector Lasso, Cotopaxi', -0.740200, -78.615500, 22500),
    ('Campo Verde', 'Valle de Tumbaco, Pichincha', -0.210400, -78.401200, 8400);

INSERT INTO invernadero (id_terreno, nombre, largo_m, ancho_m, fecha_instalacion)
SELECT t.id_terreno, i.nombre, i.largo, i.ancho, i.instalacion
FROM (VALUES
    ('Finca San José', 'Invernadero A1', 50, 20, DATE '2026-01-20'),
    ('Finca San José', 'Invernadero A2', 45, 18, DATE '2026-01-25'),
    ('Finca San José', 'Invernadero A3', 40, 18, DATE '2026-02-01'),
    ('Hacienda El Rosal', 'Invernadero B1', 60, 25, DATE '2026-02-05'),
    ('Hacienda El Rosal', 'Invernadero B2', 60, 25, DATE '2026-02-05'),
    ('Hacienda El Rosal', 'Invernadero B3', 55, 20, DATE '2026-02-10'),
    ('Campo Verde', 'Invernadero C1', 30, 12, DATE '2026-02-15'),
    ('Campo Verde', 'Invernadero C2', 30, 12, DATE '2026-02-15'),
    ('Campo Verde', 'Invernadero C3', 25, 10, DATE '2026-02-20')
) AS i (terreno, nombre, largo, ancho, instalacion)
JOIN terreno t ON t.nombre = i.terreno;

-- Un lote de tomate por invernadero; "A1" se toma del nombre del invernadero.
INSERT INTO cultivo (id_invernadero, id_tipo_cultivo, nombre, fecha_siembra, fecha_cosecha_estimada)
SELECT i.id_invernadero, tipo.id_catalogo, 'Lote tomate ' || right(i.nombre, 2),
       DATE '2026-08-01', DATE '2026-12-15'
FROM invernadero i JOIN catalogo tipo ON tipo.codigo = 'CUL_TOMATE';

-- Tres sensores por invernadero: temperatura, humedad del suelo y pH.
INSERT INTO sensor (id_invernadero, id_tipo_sensor, codigo, ubicacion, fecha_instalacion, metadatos)
SELECT i.id_invernadero, tipo.id_catalogo, s.prefijo || right(i.nombre, 2), s.ubicacion,
       i.fecha_instalacion, s.metadatos::jsonb
FROM invernadero i
CROSS JOIN (VALUES
    (1, 'SEN_TEMP', 'SEN-TEMP-', 'Pasillo central', '{"modelo": "DHT22"}'),
    (2, 'SEN_HUM_SUELO', 'SEN-HUM-', 'Zona radicular', '{"modelo": "YL-69", "prof_cm": 15}'),
    (3, 'SEN_PH_SUELO', 'SEN-PH-', 'Lecho de cultivo', '{"modelo": "PH-4502C"}')
) AS s (orden, tipo, prefijo, ubicacion, metadatos)
JOIN catalogo tipo ON tipo.codigo = s.tipo
ORDER BY i.id_invernadero, s.orden;

-- Límites para tomate bajo invernadero (valores académicos de referencia).
INSERT INTO regla (id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion)
SELECT cultivo.id_catalogo, magnitud.id_catalogo, severidad.id_catalogo, r.operador, r.umbral, r.recomendacion
FROM (VALUES
    ('MED_TEMP_AMB', 'SEV_ADVERTENCIA', '>', 30.0, 'Ventilar el invernadero.'),
    ('MED_TEMP_AMB', 'SEV_CRITICA', '>', 35.0, 'Abrir todas las ventanas y activar el sombreo de inmediato.'),
    ('MED_TEMP_AMB', 'SEV_ADVERTENCIA', '<', 10.0, 'Cerrar el invernadero y proteger las plantas del frío.'),
    ('MED_HUM_SUELO', 'SEV_ADVERTENCIA', '<', 35.0, 'Comprobar la humedad y revisar el riego.'),
    ('MED_HUM_SUELO', 'SEV_ADVERTENCIA', '>', 85.0, 'Suspender el riego y revisar el drenaje.'),
    ('MED_PH', 'SEV_ADVERTENCIA', '<', 5.5, 'Verificar la acidez con una segunda muestra.'),
    ('MED_PH', 'SEV_ADVERTENCIA', '>', 7.0, 'Verificar la alcalinidad con una segunda muestra.')
) AS r (magnitud, severidad, operador, umbral, recomendacion)
JOIN catalogo cultivo ON cultivo.codigo = 'CUL_TOMATE'
JOIN catalogo magnitud ON magnitud.codigo = r.magnitud
JOIN catalogo severidad ON severidad.codigo = r.severidad;

-- Una lectura alta de ejemplo y la alerta que provoca.
INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor, metadatos)
SELECT s.id_sensor, m.id_catalogo, TIMESTAMP '2026-11-14 12:00:00', 32.40, '{"origen": "MANUAL"}'
FROM sensor s JOIN catalogo m ON m.codigo = 'MED_TEMP_AMB'
WHERE s.codigo = 'SEN-TEMP-A1';

INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
SELECT r.id_regla, m.id_medicion, c.id_cultivo, estado.id_catalogo, m.fecha_hora
FROM medicion m
JOIN sensor s ON s.id_sensor = m.id_sensor
JOIN cultivo c ON c.id_invernadero = s.id_invernadero
JOIN regla r ON r.id_tipo_cultivo = c.id_tipo_cultivo AND r.id_tipo_medicion = m.id_tipo_medicion
JOIN catalogo estado ON estado.codigo = 'EST_PENDIENTE'
WHERE s.codigo = 'SEN-TEMP-A1' AND cumple_regla(m.valor, r.operador, r.umbral);

COMMIT;
