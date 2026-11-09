# Design

## Context

La regla de 72 h usa la fecha del evento guardada como snapshot al comprar. Los reintegros son idempotentes. Ver `docs/modelo-datos.md` (tabla Reintegro).

## Goals / Non-Goals

**Goals:**
- Devolver una sola vez cada entrada.
- Cancelación masiva tolerante a fallas parciales.

**Non-Goals:**
- Cambios de fecha del evento con devolución automática.
- Reembolsos reales contra un banco.

## Decisions

**Plazo de 72 h.** Se compara la fecha del snapshot con la hora actual; si faltan menos de 72 h se rechaza con 422.

**Unicidad de reintegro.** UNIQUE(entrada_id, tipo) simple. Si un reintegro falla, el reintento reutiliza la fila existente en lugar de crear otra.

**Cancelación masiva.** Se recorren las entradas vendidas y se reintegra una por una; las que fallan quedan PENDIENTE para reintentar sin detener al resto.

## Risks / Trade-offs

- Una cancelación con miles de entradas puede tardar; se procesa por lotes y se puede reintentar.
