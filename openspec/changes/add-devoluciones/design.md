# Design

## Context

La regla de 72 horas usa la fecha del evento guardada en la entrada al comprar. El administrador decide cada devolución; los reintegros son idempotentes por entrada. Ver `docs/modelo-datos.md` y la tabla de comunicación entre servicios.

## Goals / Non-Goals

**Goals:**
- Devolver una sola vez cada entrada.
- Poder reintentar solo la parte que falló.
- Cancelación masiva tolerante a fallas parciales.

**Non-Goals:**
- Cambiar la fecha del evento con devolución automática.
- Reembolsos reales.

## Decisions

**Estados de la devolución.** SOLICITADA espera la decisión del administrador; EN_PROCESO indica que se aprobó y se están ejecutando el reintegro y la liberación del lugar; REINTEGRADA, cuando ambos terminaron; RECHAZADA, cuando el administrador la rechazó.

**Reintento parcial.** Aprobar de nuevo una devolución EN_PROCESO reintenta solo lo pendiente. Si el reintegro ya se hizo y falló la liberación del lugar, solo se reintenta la liberación.

**Unicidad del reintegro.** UNIQUE(entrada_id, tipo) simple. Un reintento reutiliza la fila existente en lugar de crear otra.

**Cancelación de evento.** Repetir la cancelación responde 409 y no inicia nada. El aviso interno de ms-eventos a ms-ventas es idempotente: cada entrada se reintegra una vez y las que fallan quedan REINTEGRO_PENDIENTE para reintentar.

**Pago en curso.** Si se cancela el evento mientras una compra está en proceso de pago, ms-reservas rechaza la confirmación y ms-ventas reembolsa el pago, sin emitir entradas.

## Risks / Trade-offs

- Una cancelación con miles de entradas puede tardar; el reintegro por entrada permite procesar y reintentar de a una.
