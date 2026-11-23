# notificaciones-ventas Specification

## Purpose
Define la integración de `ms-ventas` con `ms-notificaciones` para emitir notificaciones asíncronas de compra confirmada (`COMPRA_CONFIRMADA`) y cancelación de evento (`EVENTO_CANCELADO`) fuera de transacción, con deduplicación determinista, resolución de destinatario, propagación de correlación y aislamiento total de fallos.

## Requirements

### Requirement: Emisión bajo el contrato interno de notificaciones
`ms-ventas` SHALL enviar notificaciones exclusivamente mediante `POST /internal/notificaciones` con el cuerpo `{ tipo, destinatario, datos, claveDeduplicacion }`, aceptando respuesta `202 Accepted` y emitiendo únicamente los tipos `COMPRA_CONFIRMADA` y `EVENTO_CANCELADO`.

#### Scenario: Solicitud de compra confirmada cumple el contrato
- **WHEN** `ms-ventas` emite una notificación por una compra aprobada
- **THEN** envía `POST /internal/notificaciones` con `tipo` `COMPRA_CONFIRMADA`, `destinatario` con `usuarioId` y `email` no vacíos y con formato de email válido, `datos` con el resumen de la compra y `claveDeduplicacion` determinista de a lo sumo 200 caracteres
- **AND** trata una respuesta `202 Accepted` como envío aceptado.

#### Scenario: Solicitud de evento cancelado cumple el contrato
- **WHEN** `ms-ventas` emite una notificación por un evento cancelado para un comprador afectado
- **THEN** envía `POST /internal/notificaciones` con `tipo` `EVENTO_CANCELADO`, `destinatario` con `usuarioId` y `email` válidos, `datos` con el detalle de la cancelación del comprador y `claveDeduplicacion` determinista de a lo sumo 200 caracteres.

### Requirement: Resolución del correo electrónico del destinatario
`POST /compras` SHALL aceptar el header opcional `X-User-Email` sin volverlo obligatorio. Si llega un correo con formato válido, `ms-ventas` SHALL persistirlo asociado a la compra y usarlo en `destinatario.email`; si falta, es inválido o no fue persistido, `ms-ventas` SHALL usar el `clienteId` cuando ya tenga formato de email válido o derivar un email determinista válido con un dominio configurable.

#### Scenario: Compra con header X-User-Email válido
- **WHEN** un cliente realiza `POST /compras` incluyendo `X-User-Email: cliente@example.com` y la compra resulta aprobada
- **THEN** `ms-ventas` persiste `cliente@example.com` en la compra y envía `destinatario.email` igual a `cliente@example.com` tanto al confirmar la compra como ante una eventual cancelación del evento.

#### Scenario: Compra sin header X-User-Email usa fallback determinista
- **WHEN** un cliente con `X-User-Id: 101` realiza `POST /compras` sin header `X-User-Email` y la compra resulta aprobada
- **THEN** `ms-ventas` completa la compra normalmente y envía `destinatario.usuarioId` `"101"` junto con un `destinatario.email` válido derivado de forma determinista a partir de `"101"`.

#### Scenario: Compra con header X-User-Email con formato inválido
- **WHEN** un cliente realiza `POST /compras` con `X-User-Email: no-es-un-email`
- **THEN** `ms-ventas` no rechaza la compra y utiliza el mecanismo de fallback determinista para construir un `destinatario.email` válido.

### Requirement: Notificación COMPRA_CONFIRMADA en flujo normal
Cuando una compra nueva se aprueba y sus entradas quedan confirmadas en base de datos, `ms-ventas` SHALL emitir `COMPRA_CONFIRMADA` fuera de la transacción de persistencia con `claveDeduplicacion` `compra-confirmada-<compraId>` y `datos` con `compraId`, `eventoId`, `total` y las entradas emitidas. En repeticiones idempotentes de una compra ya `APROBADA`, `ms-ventas` SHALL NOT emitir una nueva notificación.

#### Scenario: Compra nueva aprobada dispara notificación tras el commit
- **GIVEN** una compra nueva cuya reserva se confirma y cuyas entradas se persisten en `APROBADA`
- **WHEN** concluye la transacción de aprobación en `db_ventas`
- **THEN** `ms-ventas` invoca `POST /internal/notificaciones` con `tipo` `COMPRA_CONFIRMADA`, `claveDeduplicacion` `compra-confirmada-<compraId>` y `datos` con `compraId`, `eventoId`, `total` y la lista de entradas emitidas.

#### Scenario: Repetición idempotente de compra aprobada no vuelve a notificar
- **GIVEN** una compra previamente registrada en estado `APROBADA` para una `Idempotency-Key`
- **WHEN** el cliente repite `POST /compras` con la misma `Idempotency-Key` y el mismo cuerpo
- **THEN** `ms-ventas` responde `200 OK` con la compra existente
- **AND** no realiza ninguna llamada a `ms-notificaciones`.

#### Scenario: Compra rechazada o fallida no dispara notificación
- **WHEN** `POST /compras` termina en estado `RECHAZADA`, `FALLIDA` o `PAGO_INCIERTO`
- **THEN** `ms-ventas` no emite ninguna notificación `COMPRA_CONFIRMADA`.

### Requirement: Notificación COMPRA_CONFIRMADA al resolver PAGO_INCIERTO
Cuando una compra en estado `PAGO_INCIERTO` se resuelve como `APROBADA`, ya sea por reintento del cliente o por el proceso periódico de reconciliación, `ms-ventas` SHALL emitir `COMPRA_CONFIRMADA` fuera de la transacción tras confirmar la persistencia, usando la clave determinista `compra-confirmada-<compraId>`.

#### Scenario: Reintento del cliente resuelve PAGO_INCIERTO como aprobada
- **GIVEN** una compra en estado `PAGO_INCIERTO` cuyo pago en la pasarela resulta aprobado
- **WHEN** el cliente reenvía `POST /compras` con la misma `Idempotency-Key` y la compra pasa a `APROBADA`
- **THEN** tras el commit en `db_ventas`, `ms-ventas` emite `COMPRA_CONFIRMADA` con `claveDeduplicacion` `compra-confirmada-<compraId>`.

#### Scenario: Reconciliación periódica resuelve PAGO_INCIERTO como aprobada
- **GIVEN** una compra en estado `PAGO_INCIERTO` cuyo pago en la pasarela resulta aprobado y cuya reserva sigue vigente o confirmada
- **WHEN** el proceso de reconciliación confirma la compra y emite sus entradas en `db_ventas`
- **THEN** tras el commit, `ms-ventas` emite `COMPRA_CONFIRMADA` con `claveDeduplicacion` `compra-confirmada-<compraId>`.

#### Scenario: Reconciliación que rechaza, compensa o mantiene PAGO_INCIERTO no notifica
- **GIVEN** una compra en estado `PAGO_INCIERTO`
- **WHEN** la reconciliación la resuelve como `RECHAZADA`, la compensa como `FALLIDA` o la mantiene en `PAGO_INCIERTO`
- **THEN** `ms-ventas` no emite ninguna notificación `COMPRA_CONFIRMADA`.

### Requirement: Notificación EVENTO_CANCELADO a compradores afectados
Al procesar la cancelación de un evento y registrar los reintegros en `db_ventas`, `ms-ventas` SHALL emitir una notificación `EVENTO_CANCELADO` por cada comprador (`clienteId`) afectado, fuera de transacción, con `claveDeduplicacion` `evento-cancelado-<eventoId>-<clienteId>` y `datos` con `eventoId` y el resumen de entradas y reintegros del comprador.

#### Scenario: Evento cancelado con múltiples compradores afectados
- **GIVEN** un evento con entradas emitidas pertenecientes a dos compradores distintos, uno de ellos con dos compras en el mismo evento
- **WHEN** `ms-ventas` procesa la cancelación del evento y registra los reintegros
- **THEN** emite exactamente dos notificaciones `EVENTO_CANCELADO`, una por cada `clienteId` afectado, con `claveDeduplicacion` `evento-cancelado-<eventoId>-<clienteId>` y los datos consolidados de sus compras/reintegros del evento.

#### Scenario: Evento cancelado sin entradas afectadas no emite notificaciones
- **GIVEN** un evento sin entradas en estado `EMITIDA` ni reintegros de cancelación asociados
- **WHEN** `ms-ventas` procesa la cancelación del evento
- **THEN** responde con contadores en cero y no realiza llamadas a `ms-notificaciones`.

#### Scenario: Repetición idempotente de cancelación usa la misma clave determinista
- **GIVEN** un evento cuya cancelación ya registró reintegros y vuelve a invocarse para completar pendientes o por reintento
- **WHEN** `ms-ventas` procesa nuevamente la cancelación del evento
- **THEN** toda notificación emitida utiliza la misma `claveDeduplicacion` `evento-cancelado-<eventoId>-<clienteId>` para que `ms-notificaciones` deduplice sin generar envíos duplicados.

### Requirement: Propagación del identificador de correlación
Cuando exista un identificador de correlación activo en el contexto de ejecución (MDC `correlationId`), `ms-ventas` SHALL propagarlo en el header `X-Correlation-Id` de cada llamada a `POST /internal/notificaciones`. Cuando no esté presente, SHALL omitir el header.

#### Scenario: Propagación cuando correlationId está presente en el contexto
- **GIVEN** un valor `corr-123` presente en la clave `correlationId` del contexto de logs
- **WHEN** `ms-ventas` envía una notificación a `POST /internal/notificaciones`
- **THEN** la solicitud HTTP incluye el header `X-Correlation-Id: corr-123`.

#### Scenario: Omisión del header cuando no hay correlationId activo
- **GIVEN** que no existe valor en `correlationId` en el contexto de logs
- **WHEN** `ms-ventas` envía una notificación a `POST /internal/notificaciones`
- **THEN** la solicitud HTTP se envía normalmente sin el header `X-Correlation-Id`.

### Requirement: Aislamiento de fallos en modo best-effort
La comunicación de `ms-ventas` con `ms-notificaciones` SHALL usar timeouts cortos configurables y ejecutar en modo best-effort. Ante caída del servicio, timeout o respuesta de error (`4xx` o `5xx`), `ms-ventas` SHALL registrar una advertencia (`WARN`) y continuar sin revertir transacciones ni hacer fallar la compra, la reconciliación ni la cancelación de evento.

#### Scenario: Fallo de notificaciones no afecta una compra aprobada
- **GIVEN** una compra válida cuyo pago y reserva se confirman en `db_ventas`
- **AND** `ms-notificaciones` devuelve error `503` o sufre un timeout de conexión/lectura
- **WHEN** `ms-ventas` intenta emitir `COMPRA_CONFIRMADA`
- **THEN** registra una advertencia en el log, mantiene la compra `APROBADA` con sus entradas persistidas y devuelve `201 Created` al cliente.

#### Scenario: Fallo de notificaciones no interrumpe la reconciliación periódica
- **GIVEN** una compra en `PAGO_INCIERTO` que se resuelve como `APROBADA` durante la reconciliación
- **AND** `ms-notificaciones` no responde o falla
- **WHEN** el proceso de reconciliación emite `COMPRA_CONFIRMADA`
- **THEN** registra una advertencia, conserva la compra `APROBADA` con sus entradas y continúa procesando las demás compras pendientes.

#### Scenario: Fallo de notificaciones no revierte ni detiene la cancelación de evento
- **GIVEN** un evento cancelado con dos compradores afectados cuyos reintegros se procesan
- **AND** el envío de notificación falla para el primer comprador
- **WHEN** `ms-ventas` notifica `EVENTO_CANCELADO` a los compradores afectados
- **THEN** registra una advertencia, conserva los reintegros completados, intenta notificar al segundo comprador y devuelve la respuesta exitosa de cancelación.
