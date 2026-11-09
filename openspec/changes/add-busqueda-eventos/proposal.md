# Proposal

## Why

El público necesita encontrar eventos y ver su detalle. Hace falta una búsqueda que combine criterios y un detalle que una datos de ms-eventos con la disponibilidad de ms-reservas, sin mezclar sus bases.

## What Changes

- Búsqueda de eventos con filtros combinables por AND, orden estable y paginación (US-09).
- Detalle del evento con lugar, sectores, precios y disponibilidad (US-10).
- Comportamiento definido cuando ms-reservas no responde.

## Capabilities

### New Capabilities
- `busqueda-eventos`: búsqueda multicriterio de eventos
- `detalle-evento`: detalle del evento con disponibilidad

### Modified Capabilities

## Impact

- Servicio afectado: `ms-eventos`; la disponibilidad se pide a `ms-reservas`.
- Historias: US-09 y US-10. Las rutas de disponibilidad van antes de `/api/eventos/**` en el gateway.
- Fuera de alcance: recomendaciones (E2), búsqueda geográfica y caché (E2).
