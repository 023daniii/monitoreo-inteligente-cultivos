-- Ampliación repetible para una base con las semillas 001 y 002.
BEGIN;

-- Dos clientes pueden actualizar al mismo tiempo un volumen de una versión anterior.
SELECT pg_advisory_xact_lock(10344, 3);

INSERT INTO catalogo (id_padre, codigo, nombre)
SELECT padre.id_catalogo, tipo.codigo, tipo.nombre
FROM (VALUES
    ('SEN_HUM_SUELO', 'Sonda de humedad del suelo'),
    ('SEN_PH_SUELO', 'Sensor de pH del sustrato')
) AS tipo (codigo, nombre)
JOIN catalogo AS padre ON padre.codigo = 'CAT_TIPO_SENSOR'
ON CONFLICT (codigo) DO NOTHING;

DO $$
DECLARE
    sectores text[] := ARRAY['Terreno de prueba', 'La Ribera', 'El Mirador', 'Las Terrazas'];
    terreno_actual integer;
    invernadero_actual integer;
    ancla text;
    prefijo text;
    lote text;
BEGIN
    FOR t IN 1..4 LOOP
        -- El código del termómetro permite recuperar el terreno aunque el equipo le cambie el nombre.
        ancla := CASE WHEN t = 1 THEN 'TEMP-001' ELSE format('RED-%s-1-TEMP', t) END;
        SELECT i.id_terreno INTO terreno_actual
        FROM sensor s JOIN invernadero i USING (id_invernadero)
        WHERE s.codigo = ancla;

        IF terreno_actual IS NULL THEN
            IF t = 1 THEN
                RAISE EXCEPTION 'Falta TEMP-001 del escenario inicial. Verifica la semilla 002.';
            END IF;
            INSERT INTO terreno (nombre, direccion, latitud, longitud, area_m2)
            VALUES (sectores[t], format('Parcela de demostración %s', t),
                    -0.180653 + t * 0.002, -78.467834 + t * 0.002, 900)
            RETURNING id_terreno INTO terreno_actual;
        END IF;

        FOR i IN 1..4 LOOP
            prefijo := format('RED-%s-%s-', t, i);
            ancla := CASE WHEN t = 1 AND i = 1 THEN 'TEMP-001' ELSE prefijo || 'TEMP' END;
            SELECT id_invernadero INTO invernadero_actual FROM sensor WHERE codigo = ancla;

            IF invernadero_actual IS NULL THEN
                INSERT INTO invernadero (id_terreno, nombre, largo_m, ancho_m, fecha_instalacion)
                VALUES (terreno_actual, format('Invernadero %s · %s', i, sectores[t]), 15, 8,
                        DATE '2026-01-05')
                RETURNING id_invernadero INTO invernadero_actual;
            END IF;

            lote := format('Tomate · lote %s.%s', t, i);
            -- El invernadero de TEMP-001 ya tiene su propio lote y termómetro.
            IF NOT (t = 1 AND i = 1) AND NOT EXISTS (
                SELECT FROM cultivo WHERE id_invernadero = invernadero_actual
            ) THEN
                INSERT INTO cultivo (id_invernadero, id_tipo_cultivo, nombre, fecha_siembra)
                SELECT invernadero_actual, id_catalogo, lote, DATE '2026-02-01'
                FROM catalogo WHERE codigo = 'CUL_TOMATE';
            END IF;

            INSERT INTO sensor (id_invernadero, id_tipo_sensor, codigo, ubicacion, fecha_instalacion)
            SELECT invernadero_actual, tipo.id_catalogo, equipo.codigo, equipo.ubicacion, DATE '2026-01-05'
            FROM (VALUES
                ('SEN_TEMP', ancla, 'Centro del invernadero'),
                ('SEN_HUM_SUELO', prefijo || 'HUM', 'Zona radicular'),
                ('SEN_PH_SUELO', prefijo || 'PH', 'Lecho de cultivo')
            ) AS equipo (tipo, codigo, ubicacion)
            JOIN catalogo tipo ON tipo.codigo = equipo.tipo
            WHERE NOT EXISTS (SELECT FROM sensor existente WHERE existente.codigo = equipo.codigo)
            ON CONFLICT (codigo) DO NOTHING;
        END LOOP;
    END LOOP;
END;
$$;

-- Umbrales de demostración para tomate. La regla de temperatura ya existe.
INSERT INTO regla (id_tipo_cultivo, id_tipo_medicion, id_severidad, operador, umbral, recomendacion)
SELECT cultivo.id_catalogo, magnitud.id_catalogo, severidad.id_catalogo,
       condicion.operador, condicion.umbral, condicion.recomendacion
FROM (VALUES
    ('MED_HUM_SUELO', '<', 35.0, 'Comprobar la humedad y revisar el riego.'),
    ('MED_PH', '<', 5.5, 'Verificar la acidez con una segunda muestra.'),
    ('MED_PH', '>', 7.0, 'Verificar la alcalinidad con una segunda muestra.')
) AS condicion (magnitud, operador, umbral, recomendacion)
JOIN catalogo AS magnitud ON magnitud.codigo = condicion.magnitud
JOIN catalogo AS cultivo ON cultivo.codigo = 'CUL_TOMATE'
JOIN catalogo AS severidad ON severidad.codigo = 'SEV_ADVERTENCIA'
ON CONFLICT ON CONSTRAINT regla_uq2 DO NOTHING;

COMMIT;
