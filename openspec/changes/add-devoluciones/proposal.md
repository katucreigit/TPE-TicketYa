# Proposal

## Why

Una compra puede deshacerse: el cliente pide devolver una entrada y un administrador la resuelve, o el organizador cancela el evento y hay que reintegrar a todos. Ningún caso puede devolver el dinero dos veces ni dejar el lugar sin liberar.

## What Changes

- Solicitud de devolución por el cliente hasta 72 horas antes del evento (US-18).
- Resolución por el administrador con estados SOLICITADA, EN_PROCESO, REINTEGRADA y RECHAZADA (US-19).
- Reintento de solo la parte fallida: reintegro o liberación del lugar.
- Reintegro masivo al cancelar un evento, repetible e idempotente (US-20).

## Capabilities

### New Capabilities
- `devoluciones`: solicitud y resolución de devoluciones
- `cancelacion-evento-reintegros`: reintegro masivo por cancelación de evento

### Modified Capabilities

## Impact

- Servicios afectados: `ms-ventas` (devoluciones y reintegros), `ms-reservas` (liberación) y `ms-eventos` (aviso de cancelación).
- Historias: US-18, US-19 y US-20; reglas de comunicación entre servicios de `docs/user-stories.md`.
- Fuera de alcance: reintegros parciales, penalidades y notificaciones (E2).
