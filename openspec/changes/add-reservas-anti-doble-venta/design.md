# Design

## Context

La garantía se resuelve en la base de datos, no en memoria, para que valga con varias instancias. ms-reservas no conoce precios, pagos ni entradas: solo lugares, reservas y límites. Las entidades JPA ya existen (Asiento, InventarioEvento, InventarioSector, Reserva, ReservaItem); este change las completa con lógica, restricciones y endpoints. Referencias: `docs/arquitectura.md` (secciones 3 y 4), `docs/modelo-datos.md` y `docs/secuencia-compra.md`.

## Goals / Non-Goals

**Goals:**
- Cero ventas dobles con solicitudes simultáneas y varias instancias.
- Reservas todo o nada, sin lugares bloqueados tras una falla.
- El límite por cliente se respeta aun con reservas simultáneas del mismo cliente.
- Todos los endpoints del contrato implementados con los códigos y errores definidos.

**Non-Goals:**
- Locks en memoria (`synchronized`, `ConcurrentHashMap`) o colas externas: no valen con varias instancias.
- Precios e importes: los guarda ms-ventas al pagar.
- Notificaciones y fila virtual (E2).

## Decisions

**Asientos pregenerados.** Al inicializar el inventario se inserta una fila de Asiento LIBRE por cada fila y número, con UNIQUE(evento_id, sector_id, fila, numero). Reservar es un UPDATE condicional dentro de una transacción: `UPDATE asiento SET estado='RESERVADO', reserva_id=:r, version=version+1 WHERE evento_id=:e AND sector_id=:s AND fila=:f AND numero=:n AND estado='LIBRE'`. Si el UPDATE afecta 0 filas, ese asiento no estaba libre.

**Todo o nada.** Si algún UPDATE del pedido afecta 0 filas, se lanza una excepción y la transacción se revierte, de modo que ningún asiento queda reservado. La respuesta es 409 ASIENTO_NO_DISPONIBLE con los asientos conflictivos en details.

**Sectores no numerados.** `UPDATE inventario_sector SET disponibles=disponibles-:n, version=version+1 WHERE evento_id=:e AND sector_id=:s AND disponibles>=:n`; si afecta 0 filas responde 409 CUPO_INSUFICIENTE. La tabla lleva CHECK (disponibles >= 0) como segunda barrera.

**Tiempo siempre de la base.** El vencimiento se calcula con `now() + interval '10 minutes'` en SQL y todas las comparaciones usan `now()` de PostgreSQL, no el reloj de la JVM. La cantidad de minutos sale de `ticketya.reserva.minutos-expiracion`.

**Confirmar y vencer son excluyentes.** Confirmar: `UPDATE reserva SET estado='CONFIRMADA' WHERE id=:id AND estado='PENDIENTE' AND expira_en > now()`. Vencer: `UPDATE reserva SET estado='VENCIDA' WHERE estado='PENDIENTE' AND expira_en <= now()`. Una reserva no puede recorrer ambos caminos.

**Job de vencimiento.** Un método `@Scheduled` (cada 30 segundos, configurable) vence las reservas con `FOR UPDATE SKIP LOCKED`, devuelve sus asientos a LIBRE y suma de nuevo el cupo de los sectores no numerados, todo en una transacción. Con varias instancias, SKIP LOCKED evita que dos procesen la misma reserva. Los tests invocan el método directamente en lugar de esperar.

**Límite por cliente.** Suma `cantidad` de las reservas del cliente en el evento que estén PENDIENTE con expira_en > localtimestamp o CONFIRMADA. Como devolver descuenta cantidad de la reserva, las devoluciones aprobadas dejan de contar. Para resistir dos reservas simultáneas del mismo cliente, la transacción toma `pg_advisory_xact_lock(hashtext(evento_id || ':' || cliente_id))` antes de contar y reservar.

**Sin precios en ms-reservas.** El precio de cada lugar lo toma ms-ventas de ms-eventos al pagar y lo guarda en la compra. Como los precios no se pueden modificar con el evento publicado (US-04), no cambian entre la reserva y el pago.

**Avisos a ms-eventos.** Cuando la suma de lugares libres del evento pasa a 0 se llama a `PUT /internal/eventos/{id}/disponibilidad` con agotado=true, y con agotado=false cuando vuelve a haber alguno. `inventario_evento.agotado_notificado` (Boolean, nullable; `agotadoNotificado` en InventarioEvento) guarda el último valor que ms-eventos confirmó; null significa que todavía no se notificó nada y equivale a no agotado, que es el estado en que ms-eventos deja el evento al publicarlo. El aviso se hace después del commit y solo si el estado real (libres = 0) difiere de agotado_notificado; si falla, no se revierte la operación ni se actualiza el campo, y el job de vencimiento lo reintenta comparando el estado real contra agotado_notificado. Mientras el inventario está cerrado no se avisa, porque agotado=false devolvería a PUBLICADO un evento suspendido o cancelado; al reabrirlo, agotado_notificado vuelve a null (ms-eventos lo reanuda como PUBLICADO) y se evalúa el aviso.

**Restricciones en las tablas.** Ya están declaradas en las entidades (uk_asiento_lugar, ck_inventario_sector_disponibles, uk_inventario_sector). Como `ddl-auto: update` no agrega restricciones a tablas ya creadas, si la base se creó antes de que existieran hay que recrearla con `docker compose down -v`.

**Mapeo al código existente.** Tablas y columnas: inventario_evento(evento_id, abierto, max_por_cliente, agotado_notificado nulo, creado_en), asiento(inventario_sector_id, evento_id, sector_id, fila, numero, estado, reserva_id UUID, version), inventario_sector(evento_id FK a inventario_evento, sector_id, tipo NUMERADO o NO_NUMERADO, capacidad_total, disponibles, version), reserva(id UUID, evento_id, sector_id, cliente_id texto, estado, cantidad, creada_en, expira_en) y reserva_item(reserva_id, asiento_id nulo, cantidad). Una reserva es de un solo sector. Las columnas version son NOT NULL: las consultas nativas que modifican asiento o inventario_sector hacen version=version+1. Los asientos se crean con inserción por lotes (version 0).

**Fechas sin reloj de la JVM.** Las columnas son timestamp sin zona y las entidades usan LocalDateTime. Al crear una reserva se lee `SELECT localtimestamp` en la misma transacción y con ese valor se fijan creadaEn y expiraEn (+10 minutos); no se usa `LocalDateTime.now()`. Las comparaciones usan `localtimestamp` en SQL.

**Errores.** Se lanza ApiException con el código del contrato (ASIENTO_NO_DISPONIBLE, CUPO_INSUFICIENTE, EVENTO_NO_DISPONIBLE, LIMITE_POR_CLIENTE, RESERVA_CONFIRMADA, RESERVA_VENCIDA, ACCESO_DENEGADO, NO_ENCONTRADO). Un DataIntegrityViolationException o un fallo de @Version se traduce a 409 por el handler global, pero el flujo normal no debe depender de eso.

## Risks / Trade-offs

- Mucha contención sobre pocas filas cuando se agota un sector; se acepta a cambio de simplicidad.
- El advisory lock serializa solo las reservas del mismo cliente y evento, no las de clientes distintos.
- Un asiento de una reserva vencida sigue RESERVADO hasta la siguiente corrida del job (hasta 30 segundos).
- La devolución de cupo en sectores no numerados descuenta del ítem sin identificar cada unidad, por lo que repetir la misma llamada podría liberar de más. ms-ventas debe llamar una sola vez por entrada devuelta.
