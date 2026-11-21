\set ON_ERROR_STOP on
BEGIN;
DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public') <> 9
       OR (SELECT count(*) FROM pg_constraint WHERE connamespace = 'public'::regnamespace
           AND contype = 'f') <> 16
       OR NOT EXISTS (SELECT FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'Esquema incompleto';
    END IF;

    -- Limitar la prueba a los dispositivos demo para admitir sensores propios del equipo.
    IF (WITH modulos AS (
        SELECT i.id_terreno, i.id_invernadero
        FROM sensor s JOIN invernadero i USING (id_invernadero)
        JOIN catalogo tipo ON tipo.id_catalogo = s.id_tipo_sensor
        WHERE (s.codigo = 'TEMP-001' OR s.codigo ~ '^RED-[1-4]-[1-4]-(TEMP|HUM|PH)$')
          AND tipo.codigo IN ('SEN_TEMP', 'SEN_HUM_SUELO', 'SEN_PH_SUELO')
          AND EXISTS (SELECT FROM cultivo c WHERE c.id_invernadero = i.id_invernadero)
        GROUP BY i.id_terreno, i.id_invernadero
        HAVING count(*) = 3 AND count(DISTINCT tipo.codigo) = 3
    ), parcelas AS (
        SELECT id_terreno FROM modulos GROUP BY id_terreno HAVING count(*) = 4
    )
    SELECT count(*) FROM parcelas) <> 4 THEN
        RAISE EXCEPTION 'Se esperaban 4 terrenos con 4 invernaderos, cultivo y 3 sensores por invernadero';
    END IF;

    BEGIN
        INSERT INTO invernadero (id_terreno, nombre, largo_m, ancho_m, fecha_instalacion)
        VALUES (-1, 'FK inválida', 1, 1, CURRENT_DATE);
        RAISE EXCEPTION 'Se aceptó una FK inválida';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO medicion (id_sensor, id_tipo_medicion, fecha_hora, valor)
        SELECT id_sensor, id_tipo_medicion, fecha_hora, valor
        FROM medicion LIMIT 1;
        RAISE EXCEPTION 'Se aceptó una medición duplicada';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        UPDATE sensor SET id_tipo_sensor = (SELECT id_catalogo FROM catalogo WHERE codigo = 'CUL_TOMATE')
        WHERE codigo = 'TEMP-001';
        RAISE EXCEPTION 'Se aceptó una categoría incorrecta';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO regla (id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion)
        SELECT id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion
        FROM regla LIMIT 1;
        RAISE EXCEPTION 'Se aceptó una regla duplicada';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO alerta (id_regla, id_medicion, id_cultivo, id_estado, fecha_hora)
        SELECT id_regla, id_medicion, id_cultivo, id_estado, fecha_hora FROM alerta LIMIT 1;
        RAISE EXCEPTION 'Se aceptó una alerta duplicada';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    IF NOT EXISTS (
        SELECT FROM alerta a JOIN medicion m USING (id_medicion)
        JOIN sensor s USING (id_sensor) JOIN cultivo c USING (id_cultivo)
        JOIN regla r USING (id_regla)
        WHERE s.codigo = 'TEMP-001' AND s.id_invernadero = c.id_invernadero
          AND r.id_tipo_cultivo = c.id_tipo_cultivo AND r.id_tipo_medicion = m.id_tipo_medicion
    ) THEN
        RAISE EXCEPTION 'Falta el vínculo entre sensor, cultivo y regla de demostración';
    END IF;
END;
$$;
ROLLBACK;
\echo Integridad verificada
