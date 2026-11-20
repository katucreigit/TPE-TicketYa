# Proposal

## Why

Las consultas del catálogo y del detalle de eventos pueden repetirse y generar carga innecesaria sobre ms-eventos y su base de datos. La Entrega 2 exige incorporar caché con tiempo de vida e invalidación para mantener actualizados los datos consultados.

## What Changes

- Incorporar caché para las consultas del catálogo de eventos.
- Incorporar caché para las consultas del detalle de un evento.
- Establecer un tiempo de vida (TTL) para las entradas de caché.
- Invalidar la entrada correspondiente cuando se modifica un evento.
- Excluir de la caché la disponibilidad de asientos y cupos.
- Excluir de la caché los mapas y estados de los asientos.

## Capabilities

### New Capabilities

- `catalogo-cache`: almacenamiento temporal en caché de consultas del catálogo y del detalle de eventos, con expiración e invalidación.

### Modified Capabilities

Ninguna. La disponibilidad ya establece expresamente que no debe utilizar caché.

## Impact

- `ms-eventos`: consultas del catálogo, detalle y operaciones que modifican eventos.
- `api-gateway`: rutas de consulta del catálogo, únicamente si la estrategia de caché definida en el diseño requiere su participación.
- Dependencia de caché y configuración del TTL, a definir en el diseño.
- Pruebas de expiración, invalidación y exclusión de datos de disponibilidad.
