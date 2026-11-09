# Design

## Context

ms-ventas coordina la compra sin transacciones distribuidas: cada paso tiene su compensación. Ver `docs/secuencia-compra.md` y la tabla de saga de `docs/arquitectura.md`.

## Goals / Non-Goals

**Goals:**
- Compra consistente aunque falle un paso.
- Reintentos sin cobrar dos veces ni duplicar entradas.
- Un QR único por entrada.

**Non-Goals:**
- Pasarela real.
- Orquestadores externos.

## Decisions

**Saga.** Validar reserva, cobrar, confirmar en ms-reservas y emitir entradas. Si la pasarela rechaza, se libera la reserva (402). Si la reserva venció, no se cobra (410). Si el pago se aprobó pero ms-reservas rechaza la confirmación por vencida, se reembolsa (410).

**Estados de compra.** PENDIENTE, APROBADA, RECHAZADA y FALLIDA.

**Idempotencia.** UNIQUE(cliente_id, idempotency_key) y hash del cuerpo, persistidos junto con el resultado. Dos solicitudes simultáneas con la misma clave ejecutan un solo cobro; misma clave con otro cuerpo responde 422; sin clave, 400.

**Pasarela mock.** tok_ok aprueba, tok_rechazado rechaza y tok_timeout simula una falla (504); en ese caso la reserva sigue vigente y se reintenta con la misma clave.

**Datos de la compra.** Se guardan items, precio unitario, total, tipo de medio de pago y últimos 4 dígitos; nunca el token ni el número completo.

## Risks / Trade-offs

- Una caída entre pasos puede dejar una compra PENDIENTE; la clave de idempotencia permite reintentar sin duplicar.
