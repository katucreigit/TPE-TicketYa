# Proposal

## Why

Una compra puede deshacerse: el cliente devuelve entradas hasta cierto plazo o el organizador cancela el evento y hay que reintegrar a todos. Debe hacerse sin devolver dinero dos veces.

## What Changes

- Devolución de una entrada por el cliente dentro del plazo (hasta 72 h antes del evento).
- Cancelación de evento con reintegro masivo a todas las entradas vendidas.
- Registro de reintegros con unicidad por entrada y tipo, y reintento de los fallidos.
- Liberación del asiento devuelto al inventario.

## Capabilities

### New Capabilities
- `devoluciones`: devolución de entradas por el cliente
- `cancelacion-evento-reintegros`: reintegro masivo por cancelación del evento

### Modified Capabilities

## Impact

- Servicios afectados: `ms-ventas` (reintegros), `ms-reservas` (liberación) y `ms-eventos` (estado CANCELADO).
- Historias: US-18, US-19 y US-20.
- Fuera de alcance: reintegros parciales, penalidades, cambio de entrada.
