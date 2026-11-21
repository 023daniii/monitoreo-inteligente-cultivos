-- Valores de catálogo. Nivel 1 = hijo de una categoría; nivel 2 = magnitud de un tipo de sensor.
BEGIN;

INSERT INTO catalogo (codigo, nombre) VALUES
    ('CAT_TIPO_CULTIVO', 'Tipos de cultivo'),
    ('CAT_TIPO_SENSOR', 'Tipos de sensor y sus magnitudes'),
    ('CAT_SEVERIDAD', 'Niveles de severidad'),
    ('CAT_ESTADO_ALERTA', 'Estados de alerta'),
    ('CAT_DIAGNOSTICO', 'Diagnósticos de la memoria visual')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO catalogo (id_padre, codigo, nombre)
SELECT padre.id_catalogo, hijo.codigo, hijo.nombre
FROM (VALUES
    ('CAT_TIPO_CULTIVO', 'CUL_TOMATE', 'Tomate'),
    ('CAT_TIPO_SENSOR', 'SEN_TEMP', 'Sensor de temperatura'),
    ('CAT_TIPO_SENSOR', 'SEN_HUM_SUELO', 'Sonda de humedad del suelo'),
    ('CAT_TIPO_SENSOR', 'SEN_PH_SUELO', 'Sensor de pH del sustrato'),
    ('CAT_SEVERIDAD', 'SEV_ADVERTENCIA', 'Advertencia'),
    ('CAT_SEVERIDAD', 'SEV_CRITICA', 'Crítica'),
    ('CAT_ESTADO_ALERTA', 'EST_PENDIENTE', 'Pendiente'),
    ('CAT_ESTADO_ALERTA', 'EST_RESUELTA', 'Resuelta')
) AS hijo (categoria, codigo, nombre)
JOIN catalogo AS padre ON padre.codigo = hijo.categoria
ON CONFLICT (codigo) DO NOTHING;

-- Cada magnitud cuelga del tipo de sensor que la mide.
INSERT INTO catalogo (id_padre, codigo, nombre, unidad)
SELECT sensor.id_catalogo, magnitud.codigo, magnitud.nombre, magnitud.unidad
FROM (VALUES
    ('SEN_TEMP', 'MED_TEMP_AMB', 'Temperatura ambiental', '°C'),
    ('SEN_HUM_SUELO', 'MED_HUM_SUELO', 'Humedad del suelo', '%'),
    ('SEN_PH_SUELO', 'MED_PH', 'pH del sustrato', 'pH')
) AS magnitud (tipo_sensor, codigo, nombre, unidad)
JOIN catalogo AS sensor ON sensor.codigo = magnitud.tipo_sensor
ON CONFLICT (codigo) DO NOTHING;

-- Diagnósticos de las 200 fotos de referencia (8 condiciones frecuentes del tomate).
-- La descripción y la recomendación solo se muestran cuando el agricultor elige una foto.
INSERT INTO catalogo (id_padre, codigo, nombre, descripcion, recomendacion)
SELECT padre.id_catalogo, d.codigo, d.nombre, d.descripcion, d.recomendacion
FROM (VALUES
    ('DIAG_SANA', 'Planta sana',
     'Hojas de color verde uniforme, sin manchas, amarillamiento, moho ni deformaciones.',
     'Mantener el riego, la ventilación y el monitoreo habituales. Revisar el envés de las hojas una vez por semana.'),
    ('DIAG_TIZON_TEMPRANO', 'Tizón temprano (Alternaria solani)',
     'Hongo. Manchas marrones con anillos concéntricos, como una diana, a menudo con un halo amarillo. Empieza en las hojas viejas de la parte baja.',
     'Retirar y sacar del invernadero las hojas afectadas. Regar por goteo sin mojar el follaje y mejorar la ventilación. Si avanza, aplicar un fungicida registrado para tomate según la etiqueta y la indicación de un agrónomo.'),
    ('DIAG_TIZON_TARDIO', 'Tizón tardío (Phytophthora infestans)',
     'Avanza muy rápido. Manchas grandes e irregulares de aspecto aceitoso que se vuelven marrón oscuro; con humedad alta aparece un moho blanquecino en el envés.',
     'Actuar de inmediato: eliminar las plantas o partes afectadas y destruirlas (no compostar). Bajar la humedad relativa y evitar agua sobre las hojas. Consultar a un agrónomo para un fungicida registrado y revisar las plantas vecinas a diario.'),
    ('DIAG_SEPTORIA', 'Mancha foliar por Septoria (Septoria lycopersici)',
     'Hongo. Muchas manchas pequeñas y redondas con centro gris claro y borde oscuro, a veces con puntos negros en el centro. Comienza en las hojas bajas.',
     'Quitar las hojas inferiores afectadas, evitar el riego por aspersión y dejar espacio entre plantas. Desinfectar las herramientas. Si se extiende, usar un fungicida registrado según la etiqueta.'),
    ('DIAG_MANCHA_BACTERIANA', 'Mancha bacteriana (Xanthomonas spp.)',
     'Bacteria. Manchas pequeñas, oscuras y de aspecto húmedo, a veces con halo amarillo. Las hojas pueden amarillear y caer, y el fruto también puede mancharse.',
     'Retirar el material afectado y no manipular las plantas cuando están mojadas. Desinfectar herramientas y manos. Usar semilla y plántulas sanas. Los productos a base de cobre solo frenan el avance; consultar a un agrónomo.'),
    ('DIAG_MOHO_HOJA', 'Moho de la hoja (Passalora fulva)',
     'Hongo típico de invernaderos con humedad alta. Manchas amarillo pálido en el haz y un moho aterciopelado verde oliva a marrón en el envés.',
     'Reducir la humedad relativa: ventilar y evitar riegos al final del día. Mejorar la circulación de aire y eliminar las hojas muy afectadas. En siguientes siembras usar variedades resistentes; aplicar un fungicida registrado si hace falta.'),
    ('DIAG_MOSAICO', 'Virus del mosaico (ToMV / TMV)',
     'Virus. Hojas con un mosaico de zonas verde claro y verde oscuro, arrugadas o deformadas, y crecimiento reducido. Se transmite por contacto, herramientas y manos.',
     'No tiene cura: arrancar las plantas afectadas y sacarlas en una bolsa. Lavarse las manos y desinfectar las herramientas entre plantas. No fumar cerca del cultivo. Usar semilla certificada y variedades resistentes.'),
    ('DIAG_RIZADO_AMARILLO', 'Virus del rizado amarillo (TYLCV)',
     'Virus transmitido por la mosca blanca. Hojas pequeñas, enrolladas hacia arriba y con bordes amarillos; la planta se detiene y produce poco fruto.',
     'Retirar las plantas afectadas. Controlar la mosca blanca con mallas antiinsectos, trampas amarillas y el control que indique un agrónomo. Eliminar malezas hospederas y usar variedades tolerantes.')
) AS d (codigo, nombre, descripcion, recomendacion)
JOIN catalogo AS padre ON padre.codigo = 'CAT_DIAGNOSTICO'
ON CONFLICT (codigo) DO NOTHING;

COMMIT;
