# Proposal

## Why

Actualmente en `ms-ventas`, cuando un cobro contra `PasarelaPagoService` falla por timeout (`GatewayTimeoutException`), la compra queda en estado `PENDIENTE` sin consultar el resultado real en la pasarela ni contar con un proceso automático de resolución. Esto expone al flujo de compra a incertidumbre prolongada, obliga a depender de un reintento manual del cliente y puede dejar cobros efectivos sin entradas o reservas bloqueadas sin resolver; implementar TS-03 aporta consulta previa antes de reintentar, estado explícito `PAGO_INCIERTO` y reconciliación automática periódica.

## What Changes

- Garantizar la idempotencia de cobros en `ms-ventas` asociando de forma consistente una clave de idempotencia única por compra (`cobro-<compraId>`) tanto al invocar el cobro como al consultar su estado en `PasarelaPagoService`, evitando dobles cobros ante reintentos o reconciliaciones.
- Incorporar en `PasarelaPagoService` un método de consulta de estado de cobro por clave de idempotencia y mecanismos deterministas para simular timeouts y respuestas inciertas (tanto con tokens como con configuración programática para tests).
- Agregar el estado `PAGO_INCIERTO` en `EstadoCompra` y los estados correspondientes en `EstadoPago` (`INCIERTO` y `FALLIDO`), actualizando el enum `EstadoCompra` y la documentación de la respuesta `504` de `POST /compras` en `docs/openapi.yaml`.
- Actualizar la orquestación de `CompraService` ante `GatewayTimeoutException`: consultar primero el estado del cobro en la pasarela antes de reintentar (con un límite máximo de reintentos configurable); si la incertidumbre persiste, persistir la compra en estado `PAGO_INCIERTO` (junto con su `Pago` en estado `INCIERTO` conservando tipo de medio de pago, últimos 4 dígitos y monto), no liberar todavía la reserva en `ms-reservas` y devolver HTTP `504 Gateway Timeout` (`PASARELA_TIMEOUT`) conforme a `docs/user-stories.md`.
- Habilitar scheduling en `ms-ventas/config/` (`SchedulingConfig` con `@EnableScheduling` controlable por propiedad para tests) e implementar un job `@Scheduled` de reconciliación automática que busque compras en `PAGO_INCIERTO` y consulte su resultado en `PasarelaPagoService`:
  - Si el pago fue aprobado: confirmar la reserva vía `ReservasClient`, emitir las entradas con `codigoQr` único y pasar la compra a `APROBADA` (o reembolsar el pago y marcar `FALLIDA` si la reserva venció o no se puede confirmar).
  - Si el pago fue rechazado o no se concretó: marcar la compra como `RECHAZADA` o `FALLIDA` respectivamente y liberar la reserva vía `ReservasClient`.
  - Si la pasarela sigue sin responder o devuelve estado incierto: mantener la compra en `PAGO_INCIERTO` para la siguiente corrida.
- Incorporar pruebas unitarias en `PasarelaPagoServiceTest`, `CompraServiceTest` y para el job de reconciliación cubriendo idempotencia (sin doble cobro), timeout hacia `PAGO_INCIERTO` y resolución posterior como aprobada (con y sin compensación) y como rechazada o no concretada.

## Capabilities

### New Capabilities

- `resiliencia-pagos`: resiliencia de cobros frente a timeouts e incertidumbre de la pasarela de pagos en `ms-ventas` (TS-03), incluyendo idempotencia por clave de cobro, consulta previa antes de reintentar con límite de intentos, estado `PAGO_INCIERTO` y reconciliación automática con compensación.

### Modified Capabilities

<!-- Ninguna capability existente en openspec/specs/ se modifica; este cambio evoluciona el manejo de timeouts en ms-ventas introducido en el cambio no archivado add-compra-con-pago. -->

## Impact

- **Servicio afectado**: `ms-ventas` (`EstadoCompra`, `EstadoPago`, `Compra`, `Pago`, `CompraRepository`, `PasarelaPagoService`, `CompraService`, `ReservasClient`, `GlobalExceptionHandler`, `config/SchedulingConfig`, servicio/job de reconciliación y `application.yml`).
- **Base de datos (`db_ventas`)**: soporte para el nuevo valor `PAGO_INCIERTO` en `compra.estado` (13 caracteres, entra en `length = 20`) y `INCIERTO`/`FALLIDO` en `pago.estado` (entran en `length = 20`); sin columnas nuevas obligatorias ya que la clave de cobro se deriva de `compra.id`.
- **Contratos**: `docs/openapi.yaml` (agregar `PAGO_INCIERTO` al schema `EstadoCompra` y actualizar la descripción de `504` en `POST /compras` y tokens simulados).
- **Tests**: nuevos tests unitarios en `PasarelaPagoServiceTest`, `CompraServiceTest` y tests del job de reconciliación; actualización explícita en `CompraServiceTest` de las aserciones de Entrega 1 que esperaban `PENDIENTE` tras `tok_timeout` para alinearse con TS-03 (`PAGO_INCIERTO`).
- **Fuera de alcance**: resiliencia del API Gateway (cubierta en `add-resiliencia-api-gateway`), resiliencia de notificaciones (cubierta en `ms-notificaciones`), trazabilidad con `correlationId` (`add-trazabilidad`) e integración con una pasarela de pagos real externa.

