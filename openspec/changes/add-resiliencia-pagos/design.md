# Design

## Context

En `ms-ventas`, la saga de compra actual (`CompraService`) crea una `Compra` en estado `PENDIENTE`, deriva una clave de cobro (`"cobro-" + compraId`) y llama directamente a `PasarelaPagoService.cobrar(...)`. Ante un `GatewayTimeoutException`, la excepción se propaga sin consulta previa a la pasarela ni transición a un estado explícito de incertidumbre, dejando la compra en `PENDIENTE` sin registro `Pago` y dependiendo de un reintento manual del cliente. Tampoco existe configuración de scheduling ni un proceso de reconciliación automática en `ms-ventas` (a diferencia de `ms-reservas`, que ya utiliza `SchedulingConfig` con `@EnableScheduling` condicional por propiedad y un job `@Scheduled`).

Ver [proposal.md](proposal.md) para la motivación y alcance general, y [specs/resiliencia-pagos/spec.md](specs/resiliencia-pagos/spec.md) para los requisitos observables.

## Goals / Non-Goals

**Goals:**
- Garantizar que toda operación de cobro y consulta de estado hacia `PasarelaPagoService` use una clave de idempotencia única por compra sin posibilidad de doble cobro.
- Consultar el estado del cobro en la pasarela antes de reintentar tras un `GatewayTimeoutException`, con límite máximo de intentos configurable.
- Persistir `Compra` en `PAGO_INCIERTO` junto con su `Pago` en `INCIERTO` cuando la incertidumbre persista, sin liberar todavía la reserva y respondiendo HTTP `504` (`PASARELA_TIMEOUT`).
- Habilitar scheduling en `ms-ventas/config/` y reconciliar automáticamente las compras en `PAGO_INCIERTO` hacia `APROBADA` (emitiendo entradas con QR), `RECHAZADA` o `FALLIDA` (con reembolso compensatorio si el pago se aprobó pero la reserva expiró).
- Proveer simulación determinista de timeouts e incertidumbre en `PasarelaPagoService` para tests unitarios rápidos y repetibles.

**Non-Goals:**
- Reemplazar la pasarela simulada por un proveedor externo HTTP real o introducir Resilience4j en `ms-ventas` (la resiliencia HTTP del gateway ya fue cubierta en `add-resiliencia-api-gateway`).
- Modificar `ms-reservas` o `ms-eventos`: todos los endpoints internos necesarios (`GET /reservas/{id}`, `POST /internal/reservas/{id}/confirmar`, `POST /internal/reservas/{id}/liberar`, `GET /eventos/{id}`, `GET /eventos/{id}/sectores`) ya existen y son idempotentes donde corresponde.
- Implementar notificaciones al cliente cuando la reconciliación finaliza (corresponde a F10 / `ms-notificaciones`).

## Decisions

### 1. Clave de idempotencia de cobro derivada de `Compra.id`
- **Decisión**: Mantener `CompraService.claveCobroDe(UUID compraId)` (`"cobro-" + compraId`) como clave única de idempotencia hacia `PasarelaPagoService`, tanto en `cobrar` como en `consultarEstado`.
- **Justificación**: `Compra` ya se persiste con su `UUID` antes de invocar a la pasarela, la relación es 1:1 e inmutable, respeta `docs/modelo-datos.md` ("La clave de cobro que se envía a la pasarela se deriva del `id` de la compra, por lo que no necesita una columna propia") y permite que el job de reconciliación reconstruya la clave a partir de cualquier `Compra` en `PAGO_INCIERTO` sin alterar el esquema SQL.
- **Alternativa descartada**: Agregar una columna `clave_cobro` en `compra` o `pago`. Redundante porque sería determinísticamente igual a `"cobro-" + compra.id` y obligaría a manejar valores nulos en registros previos.

### 2. Nuevos valores en `EstadoCompra` y `EstadoPago` y persistencia de `Pago` incierto
- **Decisión**:
  - `EstadoCompra`: agregar `PAGO_INCIERTO` (`PENDIENTE, PAGO_INCIERTO, APROBADA, RECHAZADA, FALLIDA`).
  - `EstadoPago`: agregar `INCIERTO` y `FALLIDO` (`INCIERTO, APROBADO, RECHAZADO, FALLIDO, REEMBOLSADO`).
  - Al persistir una compra en `PAGO_INCIERTO`, crear/actualizar también su `Pago` con estado `INCIERTO`, `medioPagoTipo`, `ultimos4`, `monto` y `transaccionPasarela = null` (sin guardar nunca el `token`).
  - Al resolver posteriormente la compra (`APROBADA`, `RECHAZADA`, `FALLIDA`), actualizar la entidad `Pago` existente si `compra.getPago() != null` en lugar de reemplazar la referencia con una instancia nueva.
- **Justificación**: El job `@Scheduled` no dispone de un `CompraRequest`, por lo que necesita que `medioPagoTipo`, `ultimos4` y `monto` ya estén persistidos en `pago` para completar la compra sin perder esos metadatos. Actualizar el `Pago` existente evita colisiones con la restricción `UNIQUE(compra_id)` en PostgreSQL cuando JPA hace flush con `orphanRemoval = true`. Además, las columnas `estado` de `compra` y `pago` ya tienen `length = 20`, por lo que `PAGO_INCIERTO` (13), `INCIERTO` (8) y `FALLIDO` (7) no requieren migración destructiva.
- **Alternativa descartada**: Persistir `medioPagoTipo` y `ultimos4` directamente en `compra` y crear `Pago` recién al reconciliar. Duplicaría columnas ya modeladas en `pago` y rompería la consistencia con `MedioPagoResponse.from(compra.getPago())`.

### 3. Consulta de estado y simulación determinista en `PasarelaPagoService`
- **Decisión**:
  - Incorporar un enum `EstadoConsulta` (`APROBADO`, `RECHAZADO`, `INCIERTO`, `NO_ENCONTRADO`) y un record `ResultadoConsultaCobro(EstadoConsulta estado, String transaccionId)` con métodos auxiliares (`esDefinitivo()`, `aprobado()`, `rechazado()`, `incierto()`, `noEncontrado()`).
  - Agregar `public synchronized ResultadoConsultaCobro consultarEstado(String claveCobro)`:
    - Valida que `claveCobro` no sea nula ni vacía (`BadRequestException` 400).
    - Si la clave tiene una simulación de incertidumbre activa con contador > 0, decrementa el contador y devuelve `INCIERTO` (o lanza `GatewayTimeoutException` si la simulación fue configurada como timeout en consulta).
    - Si la clave existe en `cobrosPorClave`, devuelve `APROBADO` (con `transaccionId`) o `RECHAZADO`.
    - Si no existe en `cobrosPorClave`, devuelve `NO_ENCONTRADO`.
  - Mantener soporte para los tokens existentes y sumar tokens deterministas + métodos programáticos para testing:
    - `tok_ok` → aprueba de inmediato.
    - `tok_rechazado` → rechaza de inmediato.
    - `tok_timeout` → registra internamente `APROBADO` en `cobrosPorClave`, marca `consultasInciertasRestantes` con el valor configurable `ticketya.pagos.pasarela.consultas-inciertas-timeout` (default `2`, alineado con `max-reintentos: 2`) y lanza `GatewayTimeoutException` en el primer `cobrar`. Así, el primer `POST /compras` con `tok_timeout` agota las consultas iniciales, guarda `PAGO_INCIERTO` y responde `504` (preservando la compatibilidad HTTP de `FlujoCompraIntegracionTest`), mientras que el reintento del cliente o el job de reconciliación posterior recibe `APROBADO` sin volver a cobrar. Además, una llamada directa repetida a `cobrar(clave, "tok_timeout", monto)` continúa devolviendo el `ResultadoCobro` almacenado (preservando `PasarelaPagoServiceTest`).
    - `tok_timeout_rechazado` → registra internamente `RECHAZADO`, configura las consultas inciertas y lanza `GatewayTimeoutException` en el primer `cobrar`; luego resuelve como `RECHAZADO`.
    - `tok_timeout_no_concretado` → lanza `GatewayTimeoutException` sin registrar cobro, configura consultas inciertas y luego resuelve como `NO_ENCONTRADO`.
    - Métodos programáticos deterministas (ej. `programarSimulacion(String claveCobro, ...)` / `resolverIncertidumbre(String claveCobro, EstadoPago resultadoFinal)` / `limpiarSimulaciones()`) para que tests unitarios controlen exactamente cuántos timeouts en `cobrar` y cuántas consultas inciertas ocurren antes del resultado final sin `Thread.sleep` ni aleatoriedad.
- **Alternativa descartada**: Hacer que `tok_timeout` devuelva `APROBADO` inmediatamente en la primera llamada a `consultarEstado`. Eso haría que el primer `POST /compras` con `tok_timeout` se auto-resolviera en `201 APROBADA`, rompiendo el contrato HTTP `504` de `tok_timeout` en `docs/openapi.yaml` y `FlujoCompraIntegracionTest`, y dificultando probar la transición a `PAGO_INCIERTO` vía HTTP.

### 4. Flujo de timeout, consulta previa y reintentos en `CompraService`
- **Decisión**:
  - Introducir propiedad `ticketya.pagos.max-reintentos` (default `2`), con sobrecarga de constructor en `CompraService` para no romper la instanciación directa en tests existentes.
  - Extraer la lógica de cobro resiliente:
    ```
    try cobrar(claveCobro, token, total) -> retorna ResultadoCobro
    catch GatewayTimeoutException:
      for intento = 1 .. maxReintentos:
        consulta = consultarEstadoSeguro(claveCobro) // GatewayTimeoutException -> INCIERTO
        if consulta.aprobado()      -> retornar ResultadoCobro(APROBADO, txId)
        if consulta.rechazado()     -> retornar ResultadoCobro(RECHAZADO, null)
        if consulta.noEncontrado()  -> reintentar cobrar(claveCobro, token, total)
                                       (si aprueba/rechaza, retornar; si timeout, seguir loop)
        if consulta.incierto()      -> NO llamar a cobrar; continuar hasta agotar intentos
      guardarPagoIncierto(compraId, medioPago, total) // Compra=PAGO_INCIERTO, Pago=INCIERTO, sin liberar reserva
      throw new GatewayTimeoutException("La pasarela de pagos no respondio a tiempo...")
    ```
  - En `ejecutarSaga`, si `existente.getEstado() == EstadoCompra.PAGO_INCIERTO`:
    - Verificar `requestHash` (`422` si difiere).
    - Consultar primero `pasarelaPagoService.consultarEstado(claveCobroDe(existente.getId()))`:
      - `APROBADO`: confirmar reserva (usando `obtener` + `confirmar`, o compensar con reembolso + `FALLIDA` + `410`/`409` si la reserva venció o fue liberada), emitir entradas y pasar a `APROBADA` (`201`, `repeticion = false`).
      - `RECHAZADO`: liberar reserva, marcar `RECHAZADA` y lanzar `402 PaymentRequiredException`.
      - `NO_ENCONTRADO`: validar reserva (si vencida → `FALLIDA` + `410`) y si sigue vigente ejecutar cobro resiliente con la misma `claveCobro`.
      - `INCIERTO` / timeout: mantener `PAGO_INCIERTO` y lanzar `504 GatewayTimeoutException`.
  - **Exclusión mutua en vuelo**: mantener un `Set<UUID> comprasEnResolucion` (en `CompraService`, consultado tanto por reintentos HTTP de compras en `PAGO_INCIERTO` como por la reconciliación) para evitar que una ejecución concurrente del job y un reintento HTTP del cliente procesen a la vez la misma compra `PAGO_INCIERTO`.
- **Alternativa descartada**: Reintentar `cobrar` directamente sin consultar `consultarEstado` primero. Incumple explícitamente TS-03 y aumenta la superficie de error si la pasarela no respondiera de forma idempotente ante incertidumbre.

### 5. Scheduling y reconciliación automática (`SchedulingConfig`, `ReconciliacionPagosJob` y `ReservasClient.obtener`)
- **Decisión**:
  - Crear `com.ticketya.ventas.config.SchedulingConfig` siguiendo exactamente la convención de `ms-reservas`: `@Configuration`, `@EnableScheduling` y `@ConditionalOnProperty(name = "ticketya.jobs.habilitados", havingValue = "true", matchIfMissing = true)`.
  - Agregar en `CompraRepository`: `@EntityGraph(attributePaths = {"pago", "entradas"}) List<Compra> findByEstadoOrderByCreadaEnAsc(EstadoCompra estado)`.
  - Agregar en `ReservasClient` un método `obtener(UUID reservaId, String clienteId)` que devuelva `ReservaRemota` sin rechazar el estado `CONFIRMADA` (mantiene `validar(...)` intacto para el flujo inicial).
  - Crear `ReconciliacionPagosJob` en `com.ticketya.ventas.service` con `@Scheduled(fixedDelayString = "${ticketya.pagos.reconciliacion.intervalo-ms:30000}", initialDelayString = "${ticketya.pagos.reconciliacion.intervalo-ms:30000}")` y un método público `reconciliarPagosInciertos()` invocable directamente desde tests:
    - Recorre las compras en `PAGO_INCIERTO` en un `try/catch` individual por compra (aislamiento de fallos).
    - Delega la resolución de cada compra en métodos transaccionales de `CompraService` (o un colaborador dedicado compartido) para reutilizar exactamente la misma emisión de `Entrada` con `UUID.randomUUID()` como `codigoQr` y las mismas transiciones de estado:
      - `APROBADO`:
        - Llama a `reservasClient.obtener(reservaId, clienteId)`.
        - Si reserva `PENDIENTE`: obtiene snapshot en `EventosClient`, llama `reservasClient.confirmar(reservaId)`, emite entradas y pasa a `APROBADA` (`Pago.APROBADO`). Si `confirmar` o `obtenerSnapshotParaCompra` lanza `GoneException`/`ConflictException`, compensa con `pasarelaPagoService.reembolsar(txId)` y pasa a `FALLIDA` (`Pago.REEMBOLSADO`).
        - Si reserva ya `CONFIRMADA` (recuperación ante caída previa tras confirmar y antes de commit en `db_ventas`): obtiene snapshot en `EventosClient`, emite entradas si aún no tiene y pasa a `APROBADA` sin reembolsar ni duplicar entradas.
        - Si reserva `VENCIDA` o `LIBERADA` (o `NotFoundException`): compensa con `pasarelaPagoService.reembolsar(txId)` y pasa a `FALLIDA` (`Pago.REEMBOLSADO`).
        - Si `BadGatewayException` (502 temporal de `ms-reservas`/`ms-eventos`): mantiene `PAGO_INCIERTO` para la próxima corrida.
      - `RECHAZADO`: intenta `reservasClient.liberar(reservaId)` (si `BadGatewayException` mantiene `PAGO_INCIERTO` para reintentar la liberación; si `NotFoundException` continúa) y pasa a `RECHAZADA` (`Pago.RECHAZADO`).
      - `NO_ENCONTRADO`: intenta `reservasClient.liberar(reservaId)` y pasa a `FALLIDA` (`Pago.FALLIDO`).
      - `INCIERTO` o `GatewayTimeoutException`: mantiene `PAGO_INCIERTO`.
- **Alternativa descartada**: Hacer que `ReconciliacionPagosJob` use `reservasClient.validar(...)` para el caso aprobado. Si una corrida anterior hubiera confirmado la reserva y fallado antes del commit local, `validar(...)` lanzaría `ConflictException` y dispararía un reembolso erróneo sobre una reserva confirmada. `obtener(...)` elimina ese caso borde.

### 6. Respuesta HTTP y contrato OpenAPI
- **Decisión**:
  - `GlobalExceptionHandler` ya mapea `ApiException` (superclase de `GatewayTimeoutException`) a `ErrorResponse` con HTTP `504 Gateway Timeout` y código `PASARELA_TIMEOUT`; se valida mediante tests sin duplicar handlers.
  - Actualizar `docs/openapi.yaml` para incluir `PAGO_INCIERTO` en `components.schemas.EstadoCompra` y documentar en `POST /compras` (`504`) que la compra queda en `PAGO_INCIERTO` (con reserva preservada) para reconciliación automática o reintento con la misma `Idempotency-Key`.

## Risks / Trade-offs

- **[Estado en memoria de la pasarela simulada]** → Si `ms-ventas` se reinicia mientras hay compras en `PAGO_INCIERTO`, `PasarelaPagoService` pierde el mapa en memoria y la reconciliación las verá como `NO_ENCONTRADO`, marcándolas `FALLIDA` y liberando la reserva. Es una limitación inherente al mock del TPE y se documenta explícitamente.
- **[Evolución de tests E1 en `CompraServiceTest`]** → Dos tests existentes de Entrega 1 (`timeoutDePasarelaMantieneCompraPendienteYReintentoReutilizaLaMismaClaveDeCobro` y `reintentoTrasTimeoutConReservaVencidaPasaDePendienteAFallidaCon410SinNuevoCobro`) afirmaban `EstadoCompra.PENDIENTE` tras `tok_timeout` (comportamiento pre-TS-03). Al implementar TS-03, esas expectativas se actualizan a `PAGO_INCIERTO` (y compensación con reembolso cuando `tok_timeout` había aprobado y la reserva venció), manteniendo intacto el caso `502` que sigue quedando en `PENDIENTE`.
- **[Ventana entre expiración de reserva (10 min) e intervalo de reconciliación]** → Si el job tardara demasiado en correr, un pago aprobado en pasarela podría encontrarse con la reserva ya vencida. Mitigado con intervalo por defecto de 30 segundos (`ticketya.pagos.reconciliacion.intervalo-ms: 30000`) y reembolso compensatorio automático si la reserva venció.
- **[Reconciliación concurrente con reintento HTTP del cliente]** → Mitigado con exclusión en vuelo por `compraId` y verificación del estado `PAGO_INCIERTO` dentro de las transacciones de escritura en `CompraRepository`.

## Migration Plan

1. Desplegar los nuevos valores de enum (`EstadoCompra.PAGO_INCIERTO`, `EstadoPago.INCIERTO`, `EstadoPago.FALLIDO`); Hibernate (`ddl-auto: update`) no requiere DDL destructivo porque las columnas son `VARCHAR(20)`.
2. Incorporar las propiedades con valores por defecto en `ms-ventas/src/main/resources/application.yml`:
   - `ticketya.jobs.habilitados: true`
   - `ticketya.pagos.max-reintentos: 2`
   - `ticketya.pagos.reconciliacion.intervalo-ms: 30000`
   - `ticketya.pagos.pasarela.consultas-inciertas-timeout: 2`
3. Desactivar el scheduler en tests que carguen contexto completo (`ticketya.jobs.habilitados=false` en propiedades de test cuando aplique) e invocar `ReconciliacionPagosJob.reconciliarPagosInciertos()` de forma determinista.
4. Rollback: dado que no se agregan ni eliminan columnas en `db_ventas`, el rollback solo requeriría resolver manualmente (o migrar a `PENDIENTE`/`FALLIDA`) cualquier registro que hubiera quedado en `PAGO_INCIERTO`.

