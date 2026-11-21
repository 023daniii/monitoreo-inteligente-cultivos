# Sistema de monitoreo inteligente de cultivos e invernaderos

Proyecto académico de Fundamentos de Bases de Datos de la Escuela Politécnica Nacional.

## Objetivo

Registrar condiciones ambientales en invernaderos, aplicar reglas por tipo de cultivo y magnitud, y consultar fotografías similares. Los umbrales dependen de cada regla configurada.

## Flujos implementados

- **Monitoreo ambiental.** Java simula lecturas y usa JDBC para guardar la medición y sus alertas en una transacción. Evalúa todos los cultivos vigentes del invernadero del sensor y las reglas activas de la magnitud medida. Una lectura repetida recupera su historial.
- **Memoria visual.** Python usa CLIP ViT-B/32 para representar imágenes con 512 dimensiones. PostgreSQL conserva la procedencia, el hash SHA-256 y los vectores. pgvector ordena hasta cinco imágenes por distancia coseno.
- **Base compartida.** La extensión y las nueve tablas se crean desde la migración SQL, que conserva las dieciséis FK del MER. Los catálogos y el escenario de prueba se cargan por separado.

Consulta el [diagrama](../modelo/MER-FINAL.svg), el [MER editable](../modelo/MER_Invernaderos_Final.erd.json) y los [datos ilustrativos del diseño](DATOS-INVERNADERO.pdf).

La demostración usa datos simulados y fotos sintéticas. La similitud es visual y no proporciona un diagnóstico de enfermedades. Los sensores físicos, las APIs meteorológicas, las notificaciones, el control de equipos y las aplicaciones web o móviles quedan fuera del alcance.
