SELECT i.nombre AS invernadero,
       s.codigo AS sensor,
       c.nombre AS cultivo,
       r.id_regla,
       tipo.nombre AS tipo_medicion,
       r.operador,
       r.umbral,
       m.valor AS valor_medido,
       tipo.unidad,
       r.recomendacion
FROM alerta AS a
JOIN cultivo AS c ON c.id_cultivo = a.id_cultivo
JOIN invernadero AS i ON i.id_invernadero = c.id_invernadero
JOIN medicion AS m ON m.id_medicion = a.id_medicion
JOIN sensor AS s ON s.id_sensor = m.id_sensor
                AND s.id_invernadero = i.id_invernadero
JOIN regla AS r ON r.id_regla = a.id_regla
               AND r.id_tipo_cultivo = c.id_tipo_cultivo
               AND r.id_tipo_medicion = m.id_tipo_medicion
JOIN catalogo AS tipo ON tipo.id_catalogo = m.id_tipo_medicion
WHERE s.codigo = 'TEMP-001'
  AND c.nombre = 'Tomate de prueba';
