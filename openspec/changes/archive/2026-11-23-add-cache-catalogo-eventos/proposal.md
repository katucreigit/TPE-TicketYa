# Proposal

## Why

Las consultas del catálogo y del detalle de eventos pueden repetirse y generar
carga innecesaria sobre ms-eventos y su base de datos.

La Entrega 2 exige incorporar caché con tiempo de vida e invalidación para
reducir consultas repetidas sin comprometer la actualización de la información
ni la disponibilidad real de entradas.

## What Changes

- Incorporar caché para las consultas públicas del catálogo de eventos.
- Incorporar caché para los detalles de eventos públicos.
- Excluir los eventos en estado BORRADOR de la caché de detalle.
- Establecer un tiempo de vida (TTL) configurable para las entradas.
- Invalidar las entradas correspondientes cuando se modifica información
  pública o el estado de un evento.
- Invalidar las cachés cuando el job automático finaliza eventos.
- Ejecutar las invalidaciones después de confirmar las transacciones.
- Excluir de la caché la disponibilidad, el inventario, los sectores,
  las capacidades, los mapas y los estados de los asientos.

## Capabilities

### New Capabilities

- `catalogo-cache`: almacenamiento temporal en caché de consultas del catálogo
  público y del detalle de eventos, con expiración e invalidación.

### Modified Capabilities

Ninguna. La disponibilidad ya establece expresamente que no debe utilizar caché.

## Impact

- `ms-eventos`: consultas del catálogo, detalle, operaciones que modifican
  eventos y job automático de finalización.
- `ms-eventos/pom.xml`: incorporación de Spring Cache y Caffeine.
- `ms-eventos/application.yml`: configuración del TTL de las cachés.
- Pruebas de reutilización, expiración, invalidación y exclusión de datos
  dinámicos de disponibilidad.

La solución no requiere modificaciones en api-gateway ni en docker-compose.yml.
