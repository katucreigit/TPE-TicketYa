# Design

## Context

ms-ventas coordina la compra sin transacciones distribuidas. Cada paso tiene su compensación. Ver `docs/secuencia-compra.md` y la tabla de saga en `docs/arquitectura.md`.

## Goals / Non-Goals

**Goals:**
- Compra consistente aunque falle un paso.
- Reintentos sin duplicar cobros ni entradas.
- Entrada con QR único y verificable.

**Non-Goals:**
- Pasarela de pago real.
- Orquestadores externos (Camunda, Temporal).

## Decisions

**Saga orquestada.** Pasos: reservar, cobrar, confirmar reserva, emitir entrada. Si el cobro falla se libera la reserva; si falla la confirmación se reembolsa.

**Idempotencia.** UNIQUE(cliente_id, idempotency_key) más hash del cuerpo. Misma clave y mismo cuerpo devuelve la respuesta guardada; mismo header con cuerpo distinto devuelve 422.

**Pasarela mock.** tok_ok aprueba, tok_rechazado rechaza y tok_timeout simula demora para probar compensaciones.

**Snapshots.** La orden guarda título, fecha y precios del evento al comprar, para no depender de cambios posteriores.

## Risks / Trade-offs

- Una caída entre pasos puede dejar una orden en estado intermedio; se registra el estado de la saga para poder reintentar.
