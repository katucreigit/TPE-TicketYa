# TicketYa – Features y User Stories

Roles: **CLIENTE**, **ORGANIZADOR**, **ADMIN**, **ACCESO** (personal de acceso al evento) y **SISTEMA** (procesos automáticos).
Entregas: **E1** = core (esta entrega), **E2** = sistema completo (los criterios de las historias E2 son un borrador y se completan en la Entrega 2).
Criterios de aceptación en formato *Dado / Cuando / Entonces*.

## Mapa de features

| Feature | Req. | Historias | Entrega |
|---|---|---|---|
| F1 Lugares y sectores | 2 | US-01, US-02 | E1 |
| F2 Gestión y publicación de eventos | 1, 3 | US-03 a US-08 | E1 |
| F3 Búsqueda y consulta de eventos | 9 | US-09, US-10 | E1 |
| F4 Compra con reserva temporal y QR | 4 | US-11 a US-16 | E1 |
| F5 No doble venta | 5 | US-17 | E1 |
| F6 Devoluciones y cancelación de eventos | 7 | US-18 a US-20 | E1 |
| F7 Fila virtual | 6 | US-21, US-22 | E2 |
| F8 Control de acceso con QR | 8 | US-23 | E2 |
| F9 Preferencias y recomendaciones | 10 | US-24, US-25 | E2 |
| F10 Notificaciones | 11 | US-26, US-27, US-28 | E2 |
| F11 Reportes para organizadores | 12 | US-29 | E2 |
| F12 Seguridad y aspectos no funcionales | NFR | TS-01 a TS-05 | E2 |

---

## F1 – Lugares y sectores (E1)

### US-01 Crear un lugar con sus sectores
**Como** organizador o administrador, **quiero** registrar un lugar (estadio, teatro, sala) con sus sectores, **para** poder asociarle eventos.

Criterios de aceptación:
- **Dado** nombre, dirección, latitud y longitud válidos, **cuando** creo el lugar, **entonces** recibo `201` con el `id` del lugar.
- **Dado** un sector *numerado*, **cuando** lo creo, **entonces** debo informar `filas` y `asientosPorFila` mayores a 0; si falta alguno, recibo `400`.
- **Dado** un sector *no numerado*, **cuando** lo creo, **entonces** debo informar `capacidad` mayor a 0; si falta, recibo `400`.
- **Dado** una latitud fuera de [-90, 90] o una longitud fuera de [-180, 180], **cuando** creo el lugar, **entonces** recibo `400`.
- **Dado** un rol distinto de organizador o administrador, **cuando** intento crear un lugar, **entonces** recibo `403`.

### US-02 Consultar lugares y sectores
**Como** organizador, **quiero** listar y ver el detalle de los lugares, **para** elegir dónde hacer mi evento.

Criterios de aceptación:
- **Dado** lugares existentes, **cuando** consulto `GET /lugares`, **entonces** recibo una lista paginada, filtrable por ciudad.
- **Dado** un `id` inexistente, **cuando** consulto el detalle, **entonces** recibo `404`.
- **Dado** un lugar, **cuando** consulto su detalle, **entonces** veo sus sectores con tipo, capacidad (o filas y asientos por fila).

---

## F2 – Gestión y publicación de eventos (E1)

### US-03 Crear un evento en borrador
**Como** organizador, **quiero** crear un evento con sus datos básicos, **para** prepararlo antes de publicarlo.

Criterios de aceptación:
- **Dado** nombre, descripción, tipo, fecha y hora futuras, lugar existente y participantes, **cuando** creo el evento, **entonces** queda en estado `BORRADOR` con `201` y yo (por `X-User-Id`) figuro como organizador.
- **Dado** una fecha pasada, **cuando** creo el evento, **entonces** recibo `400`.
- **Dado** un lugar inexistente, **cuando** creo el evento, **entonces** recibo `404`.
- **Dado** un tipo fuera del catálogo (recital, teatro, deporte, conferencia, otro), **cuando** creo el evento, **entonces** recibo `400`.
- **Dado** un evento en `BORRADOR`, **cuando** lo edito, **entonces** los cambios se guardan; si el evento ya no está en `BORRADOR`, recibo `409`.

### US-04 Configurar sectores y precios del evento
**Como** organizador, **quiero** definir el precio de cada sector de mi evento, **para** poder venderlo.

Criterios de aceptación:
- **Dado** un evento en `BORRADOR` propio, **cuando** envío una lista de `{sectorId, precio}`, **entonces** se guarda y recibo `200`.
- **Dado** un `sectorId` que no pertenece al lugar del evento, **cuando** lo configuro, **entonces** recibo `400`.
- **Dado** un precio menor o igual a 0, **cuando** lo configuro, **entonces** recibo `400`.
- **Dado** un evento que no está en `BORRADOR`, **cuando** intento cambiar precios, **entonces** recibo `409`.
- **Dado** un evento de otro organizador, **cuando** intento configurarlo, **entonces** recibo `403`.

### US-05 Publicar un evento
**Como** organizador, **quiero** publicar mi evento, **para** que los clientes puedan encontrarlo y comprar entradas.

Criterios de aceptación:
- **Dado** un evento en `BORRADOR` con al menos un sector con precio, **cuando** lo publico, **entonces** pasa a `PUBLICADO` y se inicializa su inventario (asientos o cupos) en ms-reservas.
- **Dado** un evento sin sectores con precio, **cuando** lo publico, **entonces** recibo `422` y sigue en `BORRADOR`.
- **Dado** que ms-reservas no responde, **cuando** publico, **entonces** recibo `502`, el evento sigue en `BORRADOR` y puedo reintentar.
- **Dado** un evento ya publicado, **cuando** intento publicarlo de nuevo, **entonces** recibo `409`.
- **Dado** un evento de otro organizador, **cuando** intento publicarlo, **entonces** recibo `403`.

### US-06 Suspender o cancelar un evento
**Como** administrador, **quiero** suspender o cancelar cualquier evento, **para** resolver problemas operativos o de contenido.

Criterios de aceptación:
- **Dado** un evento `PUBLICADO` o `AGOTADO`, **cuando** lo suspendo, **entonces** pasa a `SUSPENDIDO`, deja de aparecer en búsquedas y no admite nuevas reservas.
- **Dado** un evento no finalizado ni cancelado, **cuando** lo cancelo con un motivo, **entonces** pasa a `CANCELADO` y se dispara el reintegro de todas sus entradas (US-20).
- **Dado** un evento `SUSPENDIDO`, **cuando** lo reanudo, **entonces** vuelve a `PUBLICADO` y se reabre su inventario.
- **Dado** un evento `FINALIZADO` o ya `CANCELADO`, **cuando** intento cancelarlo, **entonces** recibo `409`.
- **Dado** un usuario que no es administrador, **cuando** intenta suspender o cancelar, **entonces** recibo `403`.

### US-07 Marcar el evento como agotado
**Como** sistema, **quiero** pasar el evento a `AGOTADO` cuando no quedan lugares, **para** informar correctamente al público.

Criterios de aceptación:
- **Dado** un evento `PUBLICADO` cuyo último lugar se vende, **cuando** ms-reservas lo detecta, **entonces** notifica a ms-eventos y el evento pasa a `AGOTADO`.
- **Dado** un evento `AGOTADO`, **cuando** se libera un lugar (reserva vencida, devolución), **entonces** vuelve a `PUBLICADO`.

### US-08 Finalizar eventos pasados
**Como** sistema, **quiero** marcar como `FINALIZADO` los eventos cuya fecha ya pasó, **para** no ofrecerlos ni permitir operaciones sobre ellos.

Criterios de aceptación:
- **Dado** un evento `PUBLICADO` o `AGOTADO` con fecha y hora anteriores a ahora, **cuando** corre el proceso programado, **entonces** pasa a `FINALIZADO`.
- **Dado** un evento `FINALIZADO`, **cuando** un cliente intenta reservar, **entonces** recibe `409`.

---

## F3 – Búsqueda y consulta de eventos (E1)

### US-09 Buscar eventos por múltiples criterios
**Como** cliente, **quiero** buscar eventos combinando filtros, **para** encontrar rápido lo que me interesa.

Criterios de aceptación:
- **Dado** que aplico uno o más filtros (`tipo`, `organizadorId`, `participante`, `ciudad`, `fechaDesde`, `fechaHasta`, `q`), **cuando** busco, **entonces** recibo solo eventos que cumplen todos (AND).
- **Dado** cualquier búsqueda, **cuando** recibo resultados, **entonces** solo veo eventos `PUBLICADO` o `AGOTADO`; nunca borradores, suspendidos ni cancelados.
- **Dado** el parámetro `orden` con `fecha`, `precio` o `relevancia` y `dir` con `asc` o `desc`, **cuando** busco, **entonces** los resultados vienen ordenados en consecuencia (precio = precio mínimo entre sus sectores).
- **Dado** que no hay coincidencias, **cuando** busco, **entonces** recibo `200` con lista vacía.
- **Dado** `fechaDesde` posterior a `fechaHasta`, **cuando** busco, **entonces** recibo `400`.
- **Dado** `page` y `size`, **cuando** busco, **entonces** la respuesta es paginada e incluye el total.

### US-10 Ver el detalle de un evento con disponibilidad
**Como** cliente, **quiero** ver el detalle de un evento, sus sectores, precios y disponibilidad, **para** decidir qué comprar.

Criterios de aceptación:
- **Dado** un evento publicado, **cuando** consulto su detalle, **entonces** veo datos del evento, lugar, participantes y sectores con precio.
- **Dado** un evento publicado, **cuando** consulto la disponibilidad, **entonces** veo por sector la cantidad de lugares libres; para sectores numerados puedo ver el mapa de asientos con su estado.
- **Dado** un `id` inexistente, **cuando** consulto, **entonces** recibo `404`.

---

## F4 – Compra con reserva temporal y QR (E1)

### US-11 Reservar lugares temporalmente
**Como** cliente, **quiero** que el sistema me guarde los lugares elegidos mientras pago, **para** no perderlos.

Criterios de aceptación:
- **Dado** un evento publicado y asientos libres de un sector numerado, **cuando** los reservo, **entonces** recibo `201` con `reservaId`, los items y `expiraEn` (ahora + 10 minutos).
- **Dado** un sector no numerado con cupo suficiente, **cuando** reservo una `cantidad`, **entonces** el cupo disponible baja en esa cantidad.
- **Dado** que alguno de los asientos pedidos ya no está libre, **cuando** reservo, **entonces** recibo `409` y **ningún** asiento queda reservado (todo o nada).
- **Dado** un sector no numerado sin cupo suficiente, **cuando** reservo, **entonces** recibo `409`.
- **Dado** un evento que no está `PUBLICADO`, **cuando** reservo, **entonces** recibo `409`.
- **Dado** que pido asientos en un sector no numerado o `cantidad` en uno numerado, **cuando** reservo, **entonces** recibo `400`.

### US-12 Pagar la reserva y recibir las entradas
**Como** cliente, **quiero** pagar mi reserva y recibir mis entradas con QR, **para** poder ir al evento.

Criterios de aceptación:
- **Dado** una reserva vigente propia y un medio de pago válido, **cuando** pago y la pasarela aprueba, **entonces** la reserva queda `CONFIRMADA`, los lugares `VENDIDO`, y recibo `201` con una entrada por lugar, cada una con un `codigoQr` único.
- **Dado** que la compra se aprueba, **cuando** consulto la compra, **entonces** veo el detalle (items, precio unitario, total) y el medio de pago usado (tipo y últimos 4 dígitos).
- **Dado** que la pasarela rechaza el pago, **cuando** pago, **entonces** recibo `402`, la compra queda `RECHAZADA` y los lugares se liberan.
- **Dado** una reserva vencida, **cuando** intento pagar, **entonces** recibo `410`, no se cobra nada y la compra queda `FALLIDA`.
- **Dado** que el pago se aprobó pero la reserva venció durante el cobro, **cuando** ms-reservas rechaza la confirmación, **entonces** se reembolsa el pago (compensación) y recibo `410`.
- **Dado** una reserva de otro cliente, **cuando** intento pagarla, **entonces** recibo `403`.

### US-13 Reintentar el pago sin duplicar la compra (idempotencia)
**Como** cliente, **quiero** poder reintentar el pago si se cortó mi conexión, **para** no pagar dos veces ni perder mis entradas.

Criterios de aceptación:
- **Dado** que envío `POST /compras` con un `Idempotency-Key`, **cuando** repito la misma solicitud con la misma clave, **entonces** recibo la respuesta original (`200` o la del primer intento) y no se genera un segundo cobro ni segundas entradas.
- **Dado** la misma clave con un cuerpo distinto, **cuando** envío la solicitud, **entonces** recibo `422`.
- **Dado** que no envío `Idempotency-Key`, **cuando** pago, **entonces** recibo `400`.
- **Dado** dos solicitudes simultáneas con la misma clave, **cuando** se procesan, **entonces** solo una ejecuta el cobro.

### US-14 Liberar una reserva (manual o por vencimiento)
**Como** cliente o sistema, **quiero** liberar los lugares reservados que no se van a pagar, **para** que otros puedan comprarlos.

Criterios de aceptación:
- **Dado** una reserva `PENDIENTE` propia, **cuando** la cancelo con `DELETE /reservas/{id}`, **entonces** recibo `204` y sus lugares vuelven a `LIBRE`.
- **Dado** una reserva `PENDIENTE` cuyo `expiraEn` ya pasó, **cuando** corre el proceso programado, **entonces** pasa a `VENCIDA` y sus lugares vuelven a `LIBRE`.
- **Dado** una reserva `CONFIRMADA`, **cuando** intento cancelarla, **entonces** recibo `409`.

### US-15 Limitar entradas por cliente para un mismo evento
**Como** organizador, **quiero** que cada cliente pueda comprar una cantidad máxima de entradas por evento, **para** evitar el acaparamiento.

Criterios de aceptación:
- **Dado** un límite configurado (por defecto 4), **cuando** un cliente reserva más lugares que el límite entre reservas activas y entradas vigentes del mismo evento, **entonces** recibe `422`.
- **Dado** que devuelve una entrada o su reserva vence, **cuando** vuelve a reservar, **entonces** ese lugar deja de contar para el límite.

### US-16 Ver mis compras y mis entradas
**Como** cliente, **quiero** ver mis compras y mis entradas con su QR, **para** presentarlas en el acceso.

Criterios de aceptación:
- **Dado** mi `X-User-Id`, **cuando** consulto `GET /compras` o `GET /entradas`, **entonces** veo solo las mías.
- **Dado** una entrada o compra de otro cliente, **cuando** la consulto por id, **entonces** recibo `403`.
- **Dado** una entrada, **cuando** la consulto, **entonces** veo su estado (`EMITIDA`, `USADA`, `DEVOLUCION_SOLICITADA`, `REINTEGRADA`, `REINTEGRO_PENDIENTE`) y su `codigoQr`.

---

## F5 – No doble venta (E1)

### US-17 Garantizar que una entrada nunca se venda dos veces
**Como** sistema, **quiero** impedir que un mismo lugar se venda a más de un cliente, **para** garantizar la integridad del negocio.

Criterios de aceptación:
- **Dado** N clientes que reservan el mismo asiento en simultáneo, **cuando** se procesan, **entonces** exactamente uno recibe `201` y los demás `409`.
- **Dado** un sector no numerado con un único lugar disponible y N clientes que lo piden en simultáneo, **cuando** se procesan, **entonces** solo uno lo obtiene y el cupo nunca queda negativo.
- **Dado** varias instancias de ms-reservas ejecutándose a la vez contra la misma base, **cuando** compiten por un lugar, **entonces** el resultado es el mismo (el control vive en la base de datos, no en memoria).
- **Dado** un reintento de pago con la misma `Idempotency-Key`, **cuando** se procesa, **entonces** no se emite una segunda entrada para el mismo lugar.
- **Dado** el conjunto de pruebas automáticas, **cuando** corre el test de concurrencia, **entonces** verifica que solo una de las N compras simultáneas resulta exitosa.

---

## F6 – Devoluciones y cancelación de eventos (E1)

### US-18 Solicitar la devolución de una entrada
**Como** cliente, **quiero** pedir la devolución de una entrada, **para** recuperar mi dinero si no puedo asistir.

Criterios de aceptación:
- **Dado** una entrada `EMITIDA` propia y faltando 72 horas o más para el evento, **cuando** solicito la devolución, **entonces** recibo `201`, la devolución queda `SOLICITADA` y la entrada `DEVOLUCION_SOLICITADA`.
- **Dado** que faltan menos de 72 horas, **cuando** solicito la devolución, **entonces** recibo `422`.
- **Dado** una entrada `USADA`, ya devuelta o con una devolución en curso, **cuando** solicito la devolución, **entonces** recibo `409`.
- **Dado** una entrada de otro cliente, **cuando** solicito la devolución, **entonces** recibo `403`.

### US-19 Aprobar o rechazar una devolución
**Como** administrador, **quiero** resolver las solicitudes de devolución, **para** controlar los reintegros.

Criterios de aceptación:
- **Dado** una devolución `SOLICITADA`, **cuando** la apruebo, **entonces** el lugar se libera en ms-reservas, se solicita el reintegro a la pasarela y la devolución queda `REINTEGRADA`.
- **Dado** que la pasarela falla al reintegrar, **cuando** apruebo, **entonces** recibo `502` y la devolución sigue `SOLICITADA` para poder reintentar.
- **Dado** una devolución `SOLICITADA`, **cuando** la rechazo con un motivo, **entonces** queda `RECHAZADA` y la entrada vuelve a `EMITIDA`.
- **Dado** una devolución ya resuelta, **cuando** intento aprobarla o rechazarla, **entonces** recibo `409`.
- **Dado** un usuario que no es administrador, **cuando** intenta resolverla, **entonces** recibo `403`.
- **Dado** el listado `GET /devoluciones?estado=SOLICITADA`, **cuando** lo consulta un administrador, **entonces** ve las pendientes de resolver.

### US-20 Reintegrar todas las entradas de un evento cancelado
**Como** sistema, **quiero** reintegrar automáticamente las entradas de un evento cancelado, **para** cumplir con los compradores.

Criterios de aceptación:
- **Dado** un evento que se cancela, **cuando** ms-ventas recibe la cancelación, **entonces** todas las entradas `EMITIDA` pasan a `REINTEGRO_PENDIENTE` y se solicita el reintegro a la pasarela por cada una.
- **Dado** un reintegro exitoso, **cuando** se confirma, **entonces** la entrada pasa a `REINTEGRADA`.
- **Dado** reservas `PENDIENTE` del evento, **cuando** se cancela, **entonces** se liberan y no se aceptan nuevas reservas.
- **Dado** que la cancelación se procesa dos veces, **cuando** se reintenta, **entonces** no se reintegra dos veces la misma entrada (idempotente).
- **Dado** que la pasarela falla para algunas entradas, **cuando** termina el proceso, **entonces** esas quedan en `REINTEGRO_PENDIENTE` para reintento.
- La notificación a los compradores se implementa en E2 (US-27).

---

## F7 – Fila virtual (E2)

### US-21 Ingresar a la fila virtual de un evento de alta demanda
**Como** cliente, **quiero** entrar a una fila virtual cuando se abre la venta de un evento masivo, **para** comprar en orden de llegada.
- **Dado** un evento marcado como alta demanda, **cuando** abre la venta, **entonces** ingreso a la fila y veo mi posición.
- **Dado** que me llega el turno, **cuando** soy habilitado, **entonces** puedo reservar durante una ventana de tiempo limitada.
- **Dado** que hay muchos clientes, **cuando** se habilitan, **entonces** se hace por tandas y en orden de llegada.

### US-22 Marcar un evento como de alta demanda
**Como** organizador, **quiero** marcar un evento como de alta demanda, **para** que la venta use fila virtual.
- **Dado** un evento en borrador, **cuando** activo la bandera, **entonces** queda guardada (el campo existe desde E1).

## F8 – Control de acceso con QR (E2)

### US-23 Validar la entrada en el ingreso
**Como** personal de acceso, **quiero** escanear el QR, **para** permitir solo ingresos válidos.
- **Dado** un QR de una entrada `EMITIDA` del evento correcto, **cuando** lo escaneo, **entonces** se valida y se marca `USADA`.
- **Dado** un QR ya usado, de otro evento o inexistente, **cuando** lo escaneo, **entonces** se rechaza con el motivo.
- **Dado** dos escaneos simultáneos del mismo QR, **cuando** se procesan, **entonces** solo uno ingresa.

## F9 – Preferencias y recomendaciones (E2)

### US-24 Registrar mis preferencias
**Como** cliente, **quiero** indicar mis tipos de evento, artistas favoritos y ciudad, **para** recibir ofertas relevantes.
- **Dado** preferencias válidas, **cuando** las guardo, **entonces** quedan asociadas a mi usuario y puedo modificarlas.

### US-25 Recibir recomendaciones de eventos
**Como** cliente, **quiero** ver eventos sugeridos según mi historial, **para** descubrir cosas nuevas.
- **Dado** que compré entradas para un evento, **cuando** consulto recomendaciones, **entonces** veo eventos que compraron otros clientes con compras similares.
- **Dado** que no tengo historial, **cuando** consulto, **entonces** veo eventos según mis preferencias o los más populares.

## F10 – Notificaciones (E2)

### US-26 Notificación de compra confirmada
- **Dado** una compra aprobada, **cuando** se emiten las entradas, **entonces** recibo un email o push con el detalle.
- **Dado** que el servicio de notificaciones falla, **cuando** se confirma la compra, **entonces** la compra no se ve afectada.

### US-27 Notificación de cancelación o cambio de evento
- **Dado** un evento cancelado o modificado, **cuando** ocurre el cambio, **entonces** todos los compradores son notificados.

### US-28 Notificación de artista favorito en mi ciudad
- **Dado** un artista favorito y una ciudad en mis preferencias, **cuando** un organizador publica un evento de ese artista en mi ciudad, **entonces** recibo una notificación.

## F11 – Reportes (E2)

### US-29 Consultar reportes de mis eventos
**Como** organizador, **quiero** ver entradas vendidas y devueltas por sector, ocupación y recaudación, **para** evaluar el desempeño de mis eventos.
- **Dado** un evento propio, **cuando** consulto el reporte, **entonces** veo vendidas, devueltas, porcentaje de ocupación y recaudación por sector.
- **Dado** un evento de otro organizador, **cuando** lo consulto, **entonces** recibo `403`.

## F12 – Historias técnicas transversales (E2)

| ID | Historia técnica | Criterio principal |
|---|---|---|
| TS-01 | Autenticación y autorización con JWT y roles (cliente, organizador, administrador, acceso) | Cada endpoint solo es accesible por los roles que corresponden; sin token válido, `401` |
| TS-02 | Trazabilidad: log de cada request HTTP y log centralizado de operaciones críticas con identificador de correlación | Una compra se puede seguir de punta a punta por su `correlationId` en todos los servicios |
| TS-03 | Resiliencia: reintentos, timeouts y fallback ante fallas de pasarela y notificaciones | Si la pasarela no responde, la compra falla de forma controlada y se compensa |
| TS-04 | Cache de catálogo y detalle de eventos con TTL e invalidación; la disponibilidad de asientos no se cachea | Un cambio de evento invalida su entrada de cache |
| TS-05 | Despliegue con Docker: un contenedor por servicio, bases y gateway, y un único `docker-compose.yml`; prueba de carga del flujo de compra | `docker compose up` levanta todo el sistema; informe de JMeter con usuarios concurrentes y errores |
