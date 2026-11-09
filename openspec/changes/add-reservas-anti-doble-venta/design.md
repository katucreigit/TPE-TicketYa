# Design

## Context

La garantía se resuelve en la base de datos, no en memoria, para que valga con varias instancias de ms-reservas. SQL y estados en `docs/arquitectura.md`, `docs/modelo-datos.md` y `docs/secuencia-compra.md`.

## Goals / Non-Goals

**Goals:**
- Cero ventas dobles con solicitudes simultáneas y varias instancias.
- Reservas todo o nada, sin lugares bloqueados tras una falla.
- El límite por cliente se respeta con reservas simultáneas.

**Non-Goals:**
- Locks distribuidos o colas externas.
- Fila virtual (E2).

## Decisions

**Asientos pregenerados.** Se crean al publicar con UNIQUE(evento_id, sector_id, fila, numero). Reservar es un UPDATE condicional WHERE estado = LIBRE dentro de una transacción.

**Todo o nada.** Si algún lugar del pedido no se actualiza, se revierte la transacción y se responde 409.

**Sectores no numerados.** UPDATE inventario_sector SET disponibles = disponibles - n WHERE disponibles >= n, con CHECK (disponibles >= 0).

**Vencimiento exclusivo.** Confirmar exige expira_en > now() y el job de vencimiento exige expira_en <= now(); una reserva no puede pasar por ambos caminos.

**Límite por cliente.** Cuentan las reservas PENDIENTE no vencidas, las entradas EMITIDA y las devoluciones solicitadas aún no aprobadas. El control se hace en la base para resistir reservas simultáneas.

**Precio congelado.** Cada lugar guarda el precio al reservar, de modo que un cambio posterior no altere el importe.

## Risks / Trade-offs

- Mucha contención sobre pocas filas; se acepta a cambio de simplicidad.
- El job de vencimiento usa now() de PostgreSQL para no depender del reloj de cada instancia.
