# Design

## Context

La garantía se resuelve en la base de datos, no en memoria. Detalle de SQL y estados en `docs/arquitectura.md`, `docs/modelo-datos.md` y `docs/secuencia-compra.md`.

## Goals / Non-Goals

**Goals:**
- Cero ventas dobles bajo concurrencia.
- Reservas todo o nada.
- Confirmar y vencer son mutuamente excluyentes.

**Non-Goals:**
- Colas o locks distribuidos externos.
- Sorteo o lista de espera.

## Decisions

**Asientos pregenerados.** Los asientos se crean al publicar con UNIQUE(evento_id, sector_id, fila, numero). Reservar es un UPDATE condicional WHERE estado = LIBRE.

**Todo o nada.** Si algún asiento del pedido no se actualiza, se revierte la transacción y se responde 409.

**Sectores generales.** UPDATE inventario_sector SET disponibles = disponibles - n WHERE disponibles >= n, con CHECK (disponibles >= 0).

**Versión optimista.** Se usa @Version como defensa adicional en las entidades.

**Vencimiento exclusivo.** Confirmar exige expira_en > now(); el job de vencimiento exige expira_en <= now(). Una reserva no puede pasar por ambos caminos.

**Límite por cliente.** El máximo de entradas por cliente y evento (por defecto 4) se valida en ms-reservas porque es quien conoce las reservas activas.

## Risks / Trade-offs

- Muchos UPDATE concurrentes sobre pocas filas generan contención; se acepta a cambio de simplicidad.
- El job de vencimiento depende del reloj de la base; se usa now() de PostgreSQL.
