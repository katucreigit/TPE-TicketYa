# Proposal

## Why

Con el inventario protegido falta completar la compra: cobrar, emitir la entrada con QR y no dejar nada inconsistente si algo falla. ms-ventas orquesta el proceso con compensaciones.

## What Changes

- Endpoint de compra que reserva, cobra con pasarela simulada y emite entradas con código QR.
- Saga orquestada con compensaciones ante fallas de pago o de confirmación.
- Idempotencia por Idempotency-Key para reintentos seguros.
- Consulta de entradas y órdenes del cliente.
- Pasarela de pago mock con tokens tok_ok, tok_rechazado y tok_timeout.

## Capabilities

### New Capabilities
- `compra-entradas`: orden de compra, saga, idempotencia y consulta
- `pago-simulado`: pasarela de pago mock
- `emision-entradas`: entradas con código QR

### Modified Capabilities

## Impact

- Servicio afectado: `ms-ventas` (base `ventas_db`, puerto 5435); llama a ms-reservas y ms-eventos.
- Historias: US-12, US-13 y US-16. Diagramas en `docs/secuencia-compra.md`.
- Fuera de alcance: pasarela real, envío de mails, devoluciones (change `add-devoluciones`).
