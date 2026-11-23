# Proposal

## Why

`ms-notificaciones` ya expone `POST /internal/notificaciones` para recibir, deduplicar y enviar notificaciones asíncronas con reintentos (US-26 y US-27), pero `ms-ventas` todavía no emite ningún aviso cuando confirma una compra o procesa los reintegros de un evento cancelado. Integrar ambos servicios ahora cierra el flujo de notificaciones de ventas (compra normal, resolución de `PAGO_INCIERTO` y cancelación de evento) manteniendo el aislamiento de fallos exigido por US-26 y TS-03 para que una caída o demora en `ms-notificaciones` nunca afecte la operación principal.

## What Changes

- Crear `NotificacionesClient` en `ms-ventas` siguiendo las convenciones de `EventosClient` y `ReservasClient`, consumiendo `POST /internal/notificaciones` con el contrato exacto `NotificacionRequest` (`tipo`, `destinatario`, `datos`, `claveDeduplicacion`), esperando `202 Accepted`, con timeouts cortos configurables y propagando `X-Correlation-Id` desde el MDC cuando esté presente.
- Configurar la URL base de `ms-notificaciones` en `ms-ventas/src/main/resources/application.yml` (`ticketya.services.notificaciones-url: ${MS_NOTIFICACIONES_URL:http://localhost:8085}`), timeouts cortos y dominio de email por defecto; agregar `MS_NOTIFICACIONES_URL: http://ms-notificaciones:8085` al servicio `ms-ventas` en `docker-compose.yml`.
- Resolver el campo obligatorio `destinatario.email` (`@NotBlank @Email` en `DestinatarioDto` de `ms-notificaciones`) de forma compatible con el estado actual del repositorio: aceptar el header opcional `X-User-Email` en `POST /compras`, persistirlo en la columna nullable `compra.cliente_email` cuando tenga formato de email válido, y caer a un email determinista derivado de `clienteId` (o usar `clienteId` directamente si ya tiene formato de email) cuando no esté presente. Documentar el contrato opcional en `docs/openapi.yaml` y en `design.md` para futura propagación desde `api-gateway`/`ms-auth`.
- Disparar notificaciones únicamente de los tipos `COMPRA_CONFIRMADA` y `EVENTO_CANCELADO`, siempre **fuera de `@Transactional`** (después del commit en `db_ventas`) y en modo **best-effort** (loguear `WARN` y continuar sin lanzar excepción ni revertir):
  1. `COMPRA_CONFIRMADA` en `CompraService` al aprobar una compra nueva (excluyendo repeticiones idempotentes de compras ya `APROBADA`), con `claveDeduplicacion` determinista `compra-confirmada-<compraId>`.
  2. `COMPRA_CONFIRMADA` al resolver una compra `PAGO_INCIERTO` como `APROBADA`, tanto en el reintento del cliente en `CompraService` como en el proceso periódico `ReconciliacionPagosJob`, usando la misma `claveDeduplicacion` `compra-confirmada-<compraId>`.
  3. `EVENTO_CANCELADO` en `CancelacionEventoService` agrupado por comprador (`clienteId`) afectado tras registrar los reintegros del evento, con `claveDeduplicacion` determinista `evento-cancelado-<eventoId>-<clienteId>`.
- Agregar pruebas unitarias de `NotificacionesClient` (payload, `claveDeduplicacion`, `X-Correlation-Id` y tolerancia a fallos) y ampliar `CompraServiceTest`, `ReconciliacionPagosJobTest` y `CancelacionEventoServiceTest` para verificar emisión, claves deterministas, no emisión en repeticiones idempotentes y resiliencia ante fallos del cliente.

Fuera de alcance: no se modifican `ms-notificaciones`, `ms-auth`, `ms-eventos`, `ms-reservas` ni `api-gateway`; tampoco se emiten los tipos `EVENTO_MODIFICADO` ni `ARTISTA_FAVORITO_PUBLICO_EVENTO` (responsabilidad de `ms-eventos`), ni se implementa una cola local/outbox de reintentos en `ms-ventas`.

## Capabilities

### New Capabilities

- `notificaciones-ventas`: integración de `ms-ventas` con `ms-notificaciones` para emitir notificaciones `COMPRA_CONFIRMADA` y `EVENTO_CANCELADO` fuera de transacción, con resolución de destinatario, claves de deduplicación deterministas, propagación de `X-Correlation-Id` y aislamiento total de fallos (US-26, US-27, TS-03).

### Modified Capabilities

- `despliegue-contenedores`: el requisito `Comunicación por nombre de servicio` incorpora la variable `MS_NOTIFICACIONES_URL` en `docker-compose.yml` para la comunicación de `ms-ventas` con `ms-notificaciones`.

## Impact

- **Servicio afectado**: `ms-ventas` (`client/NotificacionesClient`, DTOs del cliente, entidad `Compra` con columna nullable `cliente_email`, `controller/Identidad`, `controller/CompraController`, `service/CompraService`, `service/ReconciliacionPagosJob`, `service/CancelacionEventoService` y `application.yml`).
- **Base de datos (`db_ventas`)**: nueva columna nullable `cliente_email` en la tabla `compra` (compatible con filas existentes bajo `ddl-auto: update`, sin romper migraciones ni datos previos).
- **Contenedores**: `docker-compose.yml` agrega `MS_NOTIFICACIONES_URL: http://ms-notificaciones:8085` en el entorno de `ms-ventas`.
- **Contratos**: `docs/openapi.yaml` documenta el header opcional `X-User-Email` en `POST /compras` (sin hacerlo obligatorio ni romper clientes existentes); el contrato de `POST /internal/notificaciones` se consume sin modificaciones.
- **Pruebas**: nuevo `NotificacionesClientTest` y ampliación de `CompraServiceTest`, `ReconciliacionPagosJobTest` y `CancelacionEventoServiceTest` (más ajuste de constructores en pruebas existentes que instancian estos servicios).

