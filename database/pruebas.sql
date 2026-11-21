-- Pruebas de integridad. Todo se revierte al final: se puede ejecutar sobre la base de trabajo.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
    esperado text;
BEGIN
    IF (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') <> 9
       OR (SELECT count(*) FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype = 'f') <> 17
       OR NOT EXISTS (SELECT FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'Esquema incompleto: se esperaban 9 tablas, 17 FK y pgvector';
    END IF;
    IF current_setting('TimeZone') <> 'America/Guayaquil' THEN
        RAISE EXCEPTION 'La base no está en hora de Ecuador (%)', current_setting('TimeZone');
    END IF;
    IF (SELECT count(*) FROM catalogo c JOIN catalogo p ON p.id_catalogo = c.id_padre
        WHERE p.codigo = 'CAT_DIAGNOSTICO' AND c.descripcion IS NOT NULL AND c.recomendacion IS NOT NULL) <> 8 THEN
        RAISE EXCEPTION 'Faltan diagnósticos con descripción y recomendación';
    END IF;

    -- Cada bloque debe fallar con el error indicado; si no falla, la prueba se detiene.
    FOREACH esperado IN ARRAY ARRAY[
        'FK inválida', 'medición duplicada', 'categoría incorrecta', 'magnitud ajena al sensor',
        'valor imposible', 'regla duplicada', 'alerta duplicada', 'alerta sin regla incumplida',
        'alerta de cultivo cosechado', 'resuelta sin fecha', 'foto de dataset sin diagnóstico',
        'diagnóstico de otra categoría'
    ] LOOP
        BEGIN
            CASE esperado
            WHEN 'FK inválida' THEN
                INSERT INTO invernadero (id_terreno, nombre, largo_m, ancho_m, fecha_instalacion)
                VALUES (-1, 'x', 1, 1, CURRENT_DATE);
            WHEN 'medición duplicada' THEN
                INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor)
                SELECT id_sensor, id_tipo_medicion, fecha_hora, valor FROM medicion LIMIT 1;
            WHEN 'categoría incorrecta' THEN
                UPDATE sensor SET id_tipo_sensor = (SELECT id_catalogo FROM catalogo WHERE codigo = 'CUL_TOMATE')
                WHERE codigo = 'SEN-TEMP-A1';
            WHEN 'magnitud ajena al sensor' THEN
                INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor)
                SELECT s.id_sensor, m.id_catalogo, LOCALTIMESTAMP, 6.5
                FROM sensor s, catalogo m WHERE s.codigo = 'SEN-TEMP-A1' AND m.codigo = 'MED_PH';
            WHEN 'valor imposible' THEN
                INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor)
                SELECT s.id_sensor, m.id_catalogo, LOCALTIMESTAMP, -500
                FROM sensor s, catalogo m WHERE s.codigo = 'SEN-HUM-A1' AND m.codigo = 'MED_HUM_SUELO';
            WHEN 'regla duplicada' THEN
                INSERT INTO regla (id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion)
                SELECT id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion
                FROM regla LIMIT 1;
            WHEN 'alerta duplicada' THEN
                INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
                SELECT id_regla, id_medicion, id_cultivo, id_estado, fecha_hora FROM alerta LIMIT 1;
            WHEN 'alerta sin regla incumplida' THEN
                -- 32,4 °C no incumple la regla "< 10 °C".
                INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
                SELECT r.id_regla, a.id_medicion, a.id_cultivo, a.id_estado, a.fecha_hora
                FROM alerta a, regla r WHERE r.operador = '<' AND r.umbral = 10 LIMIT 1;
            WHEN 'alerta de cultivo cosechado' THEN
                -- Lectura de 36 °C el 15/11 para un lote cosechado el 01/11.
                UPDATE cultivo SET fecha_cosecha_real = DATE '2026-11-01' WHERE nombre = 'Lote tomate A1';
                INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor)
                SELECT s.id_sensor, m.id_catalogo, TIMESTAMP '2026-11-15 12:00', 36
                FROM sensor s, catalogo m WHERE s.codigo = 'SEN-TEMP-A1' AND m.codigo = 'MED_TEMP_AMB';
                INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
                SELECT r.id_regla, m.id_medicion, c.id_cultivo, e.id_catalogo, m.fecha_hora
                FROM medicion m, regla r, cultivo c, catalogo e
                WHERE m.fecha_hora = TIMESTAMP '2026-11-15 12:00' AND r.operador = '>' AND r.umbral = 30
                  AND c.nombre = 'Lote tomate A1' AND e.codigo = 'EST_PENDIENTE';
            WHEN 'resuelta sin fecha' THEN
                UPDATE alerta SET id_estado = (SELECT id_catalogo FROM catalogo WHERE codigo = 'EST_RESUELTA');
            WHEN 'foto de dataset sin diagnóstico' THEN
                INSERT INTO registro_fotografico (ruta_imagen, origen, fuente, licencia, embedding,
                                                  modelo_embedding, hash_imagen)
                VALUES ('x.jpg', 'DATASET', 'x', 'x', array_fill(0.1, ARRAY[512])::vector, 'm', repeat('a', 64));
            WHEN 'diagnóstico de otra categoría' THEN
                INSERT INTO registro_fotografico (ruta_imagen, origen, fuente, licencia, id_diagnostico,
                                                  embedding, modelo_embedding, hash_imagen)
                SELECT 'x.jpg', 'DATASET', 'x', 'x', id_catalogo, array_fill(0.1, ARRAY[512])::vector, 'm', repeat('b', 64)
                FROM catalogo WHERE codigo = 'SEV_CRITICA';
            END CASE;
            RAISE EXCEPTION 'Se aceptó: %', esperado USING ERRCODE = 'P0001';
        EXCEPTION
            WHEN foreign_key_violation OR unique_violation OR check_violation THEN NULL;
        END;
    END LOOP;

    IF (SELECT count(*) FROM terreno) <> 3 OR (SELECT count(*) FROM invernadero) <> 9
       OR (SELECT count(*) FROM sensor) <> 27 OR (SELECT count(*) FROM regla) <> 7 THEN
        RAISE EXCEPTION 'Los datos de demostración no están completos';
    END IF;
END;
$$;
ROLLBACK;
\echo Integridad verificada

-- Recorrido de la alerta de ejemplo: alerta -> medición -> sensor -> cultivo -> regla.
SELECT i.nombre AS invernadero, s.codigo AS sensor, c.nombre AS cultivo,
       mag.nombre AS magnitud, m.valor, mag.unidad, r.operador, r.umbral,
       sev.nombre AS severidad, r.recomendacion
FROM alerta a
JOIN medicion m ON m.id_medicion = a.id_medicion
JOIN sensor s ON s.id_sensor = m.id_sensor
JOIN cultivo c ON c.id_cultivo = a.id_cultivo
JOIN invernadero i ON i.id_invernadero = c.id_invernadero
JOIN regla r ON r.id_regla = a.id_regla
JOIN catalogo mag ON mag.id_catalogo = m.id_tipo_medicion
JOIN catalogo sev ON sev.id_catalogo = r.id_severidad
WHERE s.codigo = 'SEN-TEMP-A1'
ORDER BY a.fecha_hora;
