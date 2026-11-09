# Design

## Context

`ms-eventos` es dueño del catálogo. Al publicar le pide a ms-reservas que inicialice el inventario; al suspender o cancelar le pide cerrarlo. Ver `docs/arquitectura.md` y `docs/modelo-datos.md`.

## Goals / Non-Goals

**Goals:**
- Un ORGANIZADOR crea y publica eventos solo sobre lugares existentes.
- Transiciones de estado válidas; las inválidas responden 409.
- La publicación es repetible sin duplicar el inventario.

**Non-Goals:**
- Autenticación real (JWT): queda para la Entrega 2.
- Generar asientos: lo hace ms-reservas.
- Notificaciones a compradores (E2).

## Decisions

**Identidad por headers.** El rol llega en X-User-Rol y el usuario en X-User-Id. Sin headers válidos se responde 400; con rol incorrecto, 403. El organizador de un evento sale siempre de X-User-Id.

**Estados del evento.** BORRADOR, PUBLICADO, AGOTADO, SUSPENDIDO, CANCELADO y FINALIZADO. SUSPENDIDO se agrega al enunciado y es reversible con /reanudar.

**Publicación.** Exige que todos los sectores vendibles tengan precio. Si ms-reservas no responde se devuelve 502 y el evento sigue en BORRADOR; la inicialización es idempotente, así que se puede reintentar.

**Agotado.** Un evento está AGOTADO cuando no queda ningún lugar disponible para reservar, contando los reservados temporalmente. Vuelve a PUBLICADO cuando se libera alguno.

**Precios.** Se guardan como NUMERIC/BigDecimal, nunca como punto flotante.

## Risks / Trade-offs

- Cambiar la fecha de un evento con entradas vendidas afecta la regla de 72 horas; ms-ventas usa un snapshot de la fecha tomado al comprar.
- El aviso de agotado depende de ms-reservas; si se pierde, el evento figura PUBLICADO aunque no haya lugares.
