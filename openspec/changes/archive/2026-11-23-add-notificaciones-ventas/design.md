# Design

## Context

Ver `proposal.md` para la motivación y `specs/notificaciones-ventas/spec.md` para los requisitos observables.

Estado actual verificado en el repositorio:
- **`ms-notificaciones`** (`POST /internal/notificaciones`, puerto `8085`) recibe `NotificacionRequest(tipo, destinatario, datos, claveDeduplicacion)` y valida `@NotNull TipoNotificacion`, `@NotNull @Valid DestinatarioDto(@NotBlank usuarioId, @NotBlank @Email email)` y `@Size(max = 200) String claveDeduplicacion`. Responde `202 Accepted` con `{ id, estado }`, lee el header opcional `X-Correlation-Id` (o genera un UUID), guarda `datos` como `Map<String, Object>` libre (sin plantillas fijas) y garantiza deduplicación en MongoDB mediante índice único parcial sobre `claveDeduplicacion`.
- **`ms-ventas`** ya implementa clientes HTTP con `RestClient` + `SimpleClientHttpRequestFactory` (`EventosClient`, `ReservasClient`) y tests con `MockRestServiceServer`. Sus flujos de escritura (`CompraService`, `ReconciliacionPagosJob`, `CancelacionEventoService`) usan `TransactionTemplate` explícito en lugar de `@Transactional` a nivel de método, lo que permite aislar llamadas HTTP fuera de transacciones de base de datos.
- **Identidad y correo del cliente**: hoy `api-gateway` (`JwtAuthFilter`) solo reenvía `X-User-Id` y `X-User-Rol`, `ms-auth` no expone `/internal/usuarios/{id}` y `Compra` solo persiste `clienteId`. Además, `ReconciliacionPagosJob` y `CancelacionEventoService` se ejecutan sin solicitud HTTP del cliente.
- **Correlación**: `ms-notificaciones` y `ms-reservas` utilizan la clave MDC `"correlationId"` y el header HTTP `"X-Correlation-Id"`. `ms-ventas` todavía no tiene implementado el filtro HTTP de `add-trazabilidad`, pero sus llamadas salientes deben propagar `MDC.get("correlationId")` si está presente.

## Goals / Non-Goals

**Goals:**
- Consumir exactamente el contrato existente de `POST /internal/notificaciones` sin modificar `ms-notificaciones`.
- Garantizar que toda llamada a `ms-notificaciones` ocurra fuera de transacción, después del commit en `db_ventas`, y nunca provoque rollback ni falle la operación principal.
- Resolver `destinatario.email` cumpliendo `@NotBlank @Email` en todos los flujos (síncrono, reconciliación periódica y cancelación masiva), permitiendo probar emails reales o simulados (`falla@ticketya.test`, `falla-una-vez@ticketya.test`) vía header opcional sin romper compatibilidad con el flujo actual.
- Generar claves de deduplicación deterministas (`<= 200` caracteres) para que reintentos o ejecuciones repetidas no dupliquen notificaciones.
- Propagar `X-Correlation-Id` desde el MDC cuando exista.

**Non-Goals:**
- Modificar `ms-notificaciones`, `ms-auth`, `api-gateway`, `ms-eventos` o `ms-reservas`.
- Implementar una cola local o patrón outbox de reintentos en `ms-ventas` si `ms-notificaciones` no responde.
- Emitir `EVENTO_MODIFICADO` o `ARTISTA_FAVORITO_PUBLICO_EVENTO` (corresponden a `ms-eventos`).
- Implementar el filtro de entrada `X-Correlation-Id` en `ms-ventas` (pertenece al cambio `add-trazabilidad`).

## Decisions

### D1. Cliente HTTP `NotificacionesClient` con timeouts cortos y aislamiento best-effort

Se crea `com.ticketya.ventas.client.NotificacionesClient` siguiendo el mismo patrón estructural que `EventosClient` y `ReservasClient`:
- Constructor `@Autowired` con `RestClient.Builder`, `@Value("${ticketya.services.notificaciones-url}")`, `@Value("${ticketya.services.notificaciones-connect-timeout-ms:1000}")` y `@Value("${ticketya.services.notificaciones-read-timeout-ms:2000}")` configurados sobre `SimpleClientHttpRequestFactory` (alineado con la recomendación de `add-notificaciones/design.md` D6: 1 s conectar, 2 s leer).
- Constructor package-private `NotificacionesClient(RestClient restClient)` para pruebas unitarias con `MockRestServiceServer`.
- DTOs internos/del paquete `client` con nombres exactos del contrato de `ms-notificaciones`:
  - `NotificacionRequest(TipoNotificacion tipo, Destinatario destinatario, Map<String, Object> datos, String claveDeduplicacion)`
  - `Destinatario(String usuarioId, String email)`
  - Enum `TipoNotificacion { COMPRA_CONFIRMADA, EVENTO_CANCELADO }` (restringido exclusivamente a los dos tipos que puede emitir `ms-ventas`).
- Invoca `POST /internal/notificaciones` con `MediaType.APPLICATION_JSON` y verifica respuesta `202 Accepted`. Si la respuesta tiene otro código HTTP (`4xx`, `5xx` o inesperado) o ocurre cualquier `RestClientException` / `ResourceAccessException` / `RuntimeException`, el cliente registra un `log.warn(...)` con tipo, `usuarioId`, `claveDeduplicacion` y detalle del fallo, y retorna normalmente sin lanzar excepción.
- El aislamiento de fallos queda centralizado únicamente en `NotificacionesClient`; los servicios (`CompraService`, `ReconciliacionPagosJob`, `CancelacionEventoService`) invocan al cliente directamente sin duplicar bloques `try/catch` defensivos.

**Alternativa considerada**: lanzar `BadGatewayException` desde `NotificacionesClient` como `EventosClient`/`ReservasClient` y obligar a cada invocador a capturarla, o duplicar `try/catch` en cada servicio. Se descarta porque el envío de notificaciones es intrínsecamente best-effort en todos los casos y centralizar el aislamiento en `NotificacionesClient` mantiene simples los servicios sin riesgo de fugas.

### D2. Propagación de `X-Correlation-Id` desde MDC

Al construir la solicitud `POST /internal/notificaciones`, `NotificacionesClient` consulta `MDC.get("correlationId")` (misma clave que usan `ms-notificaciones` y `ms-reservas`). Si el valor no es `null` ni blanco tras `trim()`, agrega el header `X-Correlation-Id`. Si no existe en el MDC, no envía el header para que `ms-notificaciones` genere un identificador propio.

**Alternativa considerada**: registrar un `RestClientCustomizer` global en `ms-ventas`. Se pospone al cambio `add-trazabilidad` para no alterar el comportamiento de `EventosClient`/`ReservasClient` fuera de este alcance; además, enviar el header explícitamente en `NotificacionesClient` facilita verificarlo directamente en `NotificacionesClientTest` con `MockRestServiceServer` sin acoplar el test a la configuración global de Spring Boot.

### D3. Resolución de `destinatario.email`: persistencia opcional vía `X-User-Email` + fallback determinista

`DestinatarioDto` en `ms-notificaciones` valida `@NotBlank @Email String email`. Para resolver este dato sin tocar `ms-auth` ni `api-gateway` y manteniendo compatibilidad en flujos asíncronos (reconciliación y cancelación):
1. `Identidad` define `HEADER_EMAIL = "X-User-Email"` y un helper para normalizar/validar formato básico de email (compatible con `@Email` de Jakarta Validation).
2. `CompraController.crear(...)` acepta `@RequestHeader(value = Identidad.HEADER_EMAIL, required = false) String userEmail` (opcional; no falla si falta o es inválido).
3. La entidad `Compra` incorpora la columna nullable `@Column(name = "cliente_email", length = 254) private String clienteEmail`. Se persiste al crear la compra `PENDIENTE` (y se preserva en los reintentos de `PENDIENTE` / `PAGO_INCIERTO`). `clienteEmail` **no** forma parte de `requestHash` para que un reintento idempotente con o sin `X-User-Email` no produzca `422 IDEMPOTENCY_KEY_REUTILIZADA`.
4. Un método privado simple dentro de `NotificacionesClient` (usando el dominio configurable `ticketya.services.notificaciones-email-dominio: ticketya.local`, sin crear una clase helper aparte) resuelve el email en orden de precedencia:
   - Email persistido en `compra.getClienteEmail()` si no es nulo y es válido.
   - `clienteId` si ya tiene formato de email válido.
   - Email determinista derivado `<local-sanitizado>@<dominio>` (donde `local-sanitizado` conserva caracteres seguros `[a-zA-Z0-9._-]` de `clienteId`, reemplaza el resto por `-`, recorta a 64 caracteres y usa `"usuario"` si quedara vacío; p. ej., `"101"` → `"101@ticketya.local"`, `"cli-100"` → `"cli-100@ticketya.local"`).
5. En `CancelacionEventoService`, al agrupar por `clienteId`, se toma el primer `clienteEmail` válido encontrado entre las compras afectadas del cliente (en orden determinista por `compra.id`), o se cae al fallback determinista si ninguna compra lo tiene.

**Integración futura documentada**: cuando `ms-auth` incorpore el claim `email` al JWT y `api-gateway` inyecte `X-User-Email` (igual que `X-User-Id` y `X-User-Rol`), `ms-ventas` recibirá y persistirá el email real sin cambios adicionales.

**Alternativas consideradas**:
- Consultar a `ms-auth` por HTTP desde `ms-ventas`: descartado por ampliar el cambio a `ms-auth` (que hoy no expone `GET /internal/usuarios/{id}`) y acoplar `ms-ventas` a un servicio adicional en runtime.
- Derivar siempre un email sintético sin persistencia: descartado porque impediría probar destinatarios reales o los emails de falla simulada de `ms-notificaciones` (`falla@ticketya.test`, `falla-una-vez@ticketya.test`) desde `POST /compras`.

### D4. Estructura de `datos` y claves de deduplicación deterministas

`ms-notificaciones` almacena `datos` como documento libre y no exige claves rígidas; se utilizan únicamente datos reales ya disponibles en `ms-ventas`, sin inventar campos ausentes (como nombre de sector o nombre/motivo de evento):

- **`COMPRA_CONFIRMADA`**:
  - `claveDeduplicacion`: `"compra-confirmada-" + compraId` (54 caracteres para UUID, siempre `<= 200`).
  - `datos`:
    - `compraId`: `UUID` como `String`
    - `eventoId`: `Long`
    - `total`: `BigDecimal`
    - `entradas`: lista de mapas con `entradaId` (`String`), `sectorId` (`Long`), `precioUnitario` (`BigDecimal`) y, cuando estén presentes (sectores numerados), `fila` (`Integer`) y `numero` (`Integer`).

- **`EVENTO_CANCELADO`** (agrupado por `clienteId`):
  - `claveDeduplicacion`: `"evento-cancelado-" + eventoId + "-" + clienteId`, acotada a `<= 200` caracteres mediante truncado simple (`substring(0, 200)` si excediera 200 caracteres).
  - `datos`:
    - `eventoId`: `Long`
    - `comprasIds`: lista ordenada de `UUID` (como `String`) de las compras del cliente afectadas en el evento
    - `entradasAfectadas`: cantidad total de entradas del cliente alcanzadas por la cancelación
    - `reintegrosCompletados`: cantidad de reintegros en estado `COMPLETADO` para ese cliente en el evento
    - `reintegrosPendientes`: cantidad de reintegros en estado `PENDIENTE` para ese cliente en el evento
    - `montoReintegrado`: suma de `monto` de los reintegros completados del cliente en el evento

### D5. Puntos de disparo estrictamente post-commit

Ninguno de los flujos de escritura abre `@Transactional` sobre el método orquestador; todas las escrituras corren dentro de `TransactionTemplate`:

1. **`CompraService.completarTrasCobro(...)`** (flujo normal): inmediatamente después de `guardarAprobadaConEntradas(...)` (que ejecuta su `transaccion.execute(...)` y confirma el commit en `db_ventas`), invoca `notificarCompraConfirmada(...)`.
   - En los casos `case APROBADA` de `ejecutarSaga(...)` y `reintentarPagoIncierto(...)` (repetición idempotente con `repeticion = true`, HTTP `200`), NO se dispara notificación.
2. **`CompraService.resolverPagoInciertoAprobado(...)`** (reintento del cliente sobre `PAGO_INCIERTO`): inmediatamente después de `guardarAprobadaConEntradas(...)` (post-commit), invoca `notificarCompraConfirmada(...)` con la misma clave `"compra-confirmada-" + compra.getId()`.
3. **`ReconciliacionPagosJob.reconciliarAprobada(...)`** (job periódico): `compraService.guardarAprobadaConEntradas(...)` devuelve el `CompraResponse` ya comiteado; inmediatamente después, fuera de transacción, se invoca `compraService.notificarCompraConfirmada(...)` (o directamente `notificacionesClient`) usando `compra.getClienteId()`, `compra.getClienteEmail()`, `compra.getEventoId()` y el `CompraResponse` aprobado, con la misma clave `"compra-confirmada-" + compra.getId()`.
4. **`CancelacionEventoService.procesar(Long eventoId)`**:
   - Paso 1 (tx): `marcarPendientes(eventoId)`.
   - Paso 2 (fuera de tx, con commit por entrada): reintegros contra `PasarelaPagoService` y `completar(...)`.
   - Paso 3 (tx de lectura): calcula `CancelacionEventoResponse` y obtiene en la misma lectura los reintegros de cancelación del evento (`reintegroRepository.buscarTodosPorEvento(eventoId, TIPO)` con `JOIN FETCH` a `entrada` y `compra`) para construir una lista inmutable de resúmenes por `clienteId` (ordenados de forma determinista por `clienteId`).
   - Paso 4 (fuera de tx, tras finalizar el paso 3): si la lista de compradores afectados no está vacía, emite una notificación `EVENTO_CANCELADO` por cada comprador con `claveDeduplicacion = evento-cancelado-<eventoId>-<clienteId>`.
   - Si el evento no tuvo ventas/reintegros (`totalEntradas == 0`), la lista queda vacía y no se llama a `NotificacionesClient`. Si se repite `procesar(eventoId)`, se usa la misma `claveDeduplicacion` determinista, que `ms-notificaciones` deduplica respondiendo `202` sin volver a enviar el email.

### D6. Configuración y despliegue

- `ms-ventas/src/main/resources/application.yml`:
  ```yaml
  ticketya:
    services:
      eventos-url: ${MS_EVENTOS_URL:http://localhost:8081}
      reservas-url: ${MS_RESERVAS_URL:http://localhost:8082}
      notificaciones-url: ${MS_NOTIFICACIONES_URL:http://localhost:8085}
      notificaciones-connect-timeout-ms: 1000
      notificaciones-read-timeout-ms: 2000
      notificaciones-email-dominio: ticketya.local
  ```
- `docker-compose.yml`: agregar `MS_NOTIFICACIONES_URL: http://ms-notificaciones:8085` en `services.ms-ventas.environment`. No se agrega `depends_on: ms-notificaciones` obligatorio porque `ms-ventas` debe poder arrancar y operar incluso si `ms-notificaciones` está caído (aislamiento de fallos).
- `docs/openapi.yaml`: documentar el header opcional `X-User-Email` en `POST /compras` indicando su uso para notificaciones y compatibilidad si se omite.

## Risks / Trade-offs

- [Caída prolongada de `ms-notificaciones` durante una compra] → Al operar en modo best-effort sin cola local/outbox, esa notificación no se reintentará automáticamente desde `ms-ventas`, pero se preserva la disponibilidad y consistencia de las compras (requisito explícito de US-26 y `add-notificaciones/design.md` D6).
- [Ausencia de `X-User-Email` desde `api-gateway` hasta actualizar `ms-auth`/`api-gateway`] → El fallback determinista `<clienteId>@ticketya.local` garantiza validez ante `DestinatarioDto` (`@Email`) y trazabilidad por `usuarioId`, permitiendo probar emails reales o de falla simulada mediante `X-User-Email` desde hoy.
- [Cancelación de un evento con muchos compradores] → Se realiza un `POST` síncrono con timeout corto por comprador afectado; para el volumen del TP es adecuado, y la propagación best-effort aísla cualquier fallo por destinatario para no bloquear a los demás.
- [Cambios en constructores de servicios en pruebas existentes] → `CompraService`, `ReconciliacionPagosJob` y `CancelacionEventoService` incorporan `NotificacionesClient` por constructor; todas las pruebas unitarias e integración existentes en `ms-ventas` se actualizan para inyectarlo sin relajar aserciones previas.

## Migration Plan

1. Agregar columna nullable `cliente_email` a `Compra` (compatible con tablas existentes vía Hibernate `ddl-auto: update`).
2. Incorporar propiedades con valores por defecto locales en `ms-ventas/src/main/resources/application.yml` y variable `MS_NOTIFICACIONES_URL` en `docker-compose.yml`.
3. Desplegar con `docker compose up -d --build` (sin migración de datos ni downtime requerido).
4. Rollback: revertir las llamadas a `NotificacionesClient` y la variable `MS_NOTIFICACIONES_URL`; la columna nullable `cliente_email` no afecta versiones previas.

