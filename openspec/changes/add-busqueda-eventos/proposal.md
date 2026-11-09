# Proposal

## Why

El público necesita encontrar eventos rápido. Hace falta una búsqueda que combine criterios (texto, categoría, fecha, lugar, precio y disponibilidad) con paginación y orden.

## What Changes

- Endpoint de búsqueda de eventos publicados con filtros combinables.
- Filtros por texto, categoría, rango de fechas, lugar y rango de precio.
- Paginación y ordenamiento (fecha, precio).
- Indicador de disponibilidad obtenido de ms-reservas sin acoplar los esquemas.

## Capabilities

### New Capabilities
- `busqueda-eventos`: búsqueda multicriterio de eventos publicados

### Modified Capabilities

## Impact

- Servicio afectado: `ms-eventos`; consulta de disponibilidad a `ms-reservas`.
- Historias: US-09 y US-10. Ruta del gateway de disponibilidad antes de `/api/eventos/**`.
- Fuera de alcance: recomendaciones, búsqueda full-text avanzada, caché distribuida.
