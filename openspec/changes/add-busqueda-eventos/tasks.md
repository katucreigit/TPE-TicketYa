# Tasks

## 1. Búsqueda

- [ ] 1.1 Definir parámetros y validaciones del endpoint de búsqueda.
- [ ] 1.2 Implementar Specifications por filtro y la búsqueda de texto en cuatro campos.
- [ ] 1.3 Implementar los órdenes fecha, precio mínimo y relevancia con desempate estable.
- [ ] 1.4 Paginación con total de resultados.

## 2. Detalle

- [ ] 2.1 Endpoint de detalle con evento, lugar, participantes y sectores con precio.
- [ ] 2.2 Cliente HTTP al endpoint de disponibilidad de ms-reservas con timeout.
- [ ] 2.3 Responder 503 si ms-reservas no responde.

## 3. Pruebas

- [ ] 3.1 Tests de cada filtro y de filtros combinados.
- [ ] 3.2 Test de fechaDesde posterior a fechaHasta (400) y de lista vacía (200).
- [ ] 3.3 Test de orden estable entre páginas.
- [ ] 3.4 Test de que borradores, suspendidos y cancelados no aparecen.
- [ ] 3.5 Test del detalle con ms-reservas caído (503).
