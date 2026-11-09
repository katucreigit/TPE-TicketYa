# Proposal

## Why

Con el inventario protegido falta completar la compra: cobrar, emitir las entradas con QR y no dejar nada inconsistente si algo falla. ms-ventas orquesta el proceso con compensaciones y garantiza que un reintento no cobre dos veces.

## What Changes

- Pago de una reserva con pasarela simulada y emisión de una entrada con QR por lugar (US-12).
- Idempotencia con Idempotency-Key persistida (US-13).
- Compensaciones: pago rechazado, reserva vencida antes o durante el cobro.
- Consulta de compras y entradas del cliente (US-16).

## Capabilities

### New Capabilities
- `compra-entradas`: pago de la reserva, saga e idempotencia
- `pago-simulado`: pasarela de pago mock
- `emision-entradas`: entradas con QR y su consulta

### Modified Capabilities

## Impact

- Servicio afectado: `ms-ventas` (base `db_ventas`, puerto 5435); llama a ms-reservas y a ms-eventos.
- Historias: US-12, US-13 y US-16. Diagramas en `docs/secuencia-compra.md`.
- Fuera de alcance: pasarela real, notificaciones (E2) y devoluciones (change `add-devoluciones`).
