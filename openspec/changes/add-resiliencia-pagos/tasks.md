# Tasks

## 1. Modelo de estados, repositorio y contrato

- [ ] 1.1 Agregar `PAGO_INCIERTO` en `EstadoCompra` e `INCIERTO` y `FALLIDO` en `EstadoPago` (`ms-ventas/src/main/java/com/ticketya/ventas/model/enums/`), incorporar `findByEstadoOrderByCreadaEnAsc(EstadoCompra estado)` con `@EntityGraph(attributePaths = {"pago", "entradas"})` en `CompraRepository`, y verificar que `mvn -q compile` en `ms-ventas` finaliza sin errores.
- [ ] 1.2 Actualizar `docs/openapi.yaml` para agregar `PAGO_INCIERTO` en `components.schemas.EstadoCompra` y documentar la transición a `PAGO_INCIERTO` y reconciliación automática en la respuesta `504` de `POST /compras`, verificando consistencia con `docs/user-stories.md` y sintaxis YAML válida.

## 2. Pasarela de pagos con consulta de estado y simulación determinista

- [ ] 2.1 Incorporar en `PasarelaPagoService` el resultado de consulta (`EstadoConsulta` / `ResultadoConsultaCobro`), el método `consultarEstado(String claveCobro)` con validación de clave obligatoria y mecanismos deterministas para simular timeouts e incertidumbre (tokens `tok_timeout`, `tok_timeout_rechazado`, `tok_timeout_no_concretado` con ventana configurable y métodos programáticos para tests) garantizando idempotencia sin doble cobro; verificar con `mvn -q compile` en `ms-ventas`.
- [ ] 2.2 Agregar pruebas unitarias en `PasarelaPagoServiceTest` cubriendo idempotencia de cobro (sin doble débito), consulta de estado para cobros aprobados, rechazados y no encontrados, validación de clave vacía y simulación determinista de timeout/incertidumbre con resolución posterior aprobada, rechazada y no concretada; verificar que `mvn test -Dtest=PasarelaPagoServiceTest` en `ms-ventas` pasa en verde.

## 3. Orquestación resiliente en CompraService y soporte en ReservasClient

- [ ] 3.1 Agregar `obtener(UUID reservaId, String clienteId)` en `ReservasClient` para recuperar una reserva sin rechazar el estado `CONFIRMADA` (preservando el comportamiento actual de `validar`) y agregar tests unitarios en `ReservasClientTest` cubriendo estados `PENDIENTE`, `CONFIRMADA`, `VENCIDA`, `LIBERADA` y traducción de errores (`403`, `404`, `502`); verificar que `mvn test -Dtest=ReservasClientTest` en `ms-ventas` pasa en verde.
- [ ] 3.2 Actualizar `CompraService` con el límite configurable `ticketya.pagos.max-reintentos` (default `2`) para que, ante `GatewayTimeoutException` en `cobrar(...)`, consulte primero `PasarelaPagoService.consultarEstado(claveCobro)` antes de reintentar (reintentando `cobrar` únicamente si la pasarela informa `NO_ENCONTRADO` y sin reenviar cobros mientras sea `INCIERTO`), persista la compra en `PAGO_INCIERTO` junto con su `Pago` en `INCIERTO` (actualizando el `Pago` existente sin violar `UNIQUE(compra_id)`) sin liberar la reserva y responda con `GatewayTimeoutException` (`504 PASARELA_TIMEOUT`) si la incertidumbre persiste; soportar además reintentos HTTP sobre compras en `PAGO_INCIERTO`, exclusión en vuelo por `compraId` y métodos transaccionales compartidos de resolución, declarando las propiedades en `ms-ventas/src/main/resources/application.yml`. Verificar con `mvn -q compile`.
- [ ] 3.3 Actualizar y ampliar `CompraServiceTest` (ajustando las aserciones E1 de `tok_timeout` a `PAGO_INCIERTO` conforme a TS-03) para cubrir: consulta previa que resuelve como aprobado sin segundo cobro, reintento controlado cuando la pasarela responde `NO_ENCONTRADO`, timeout persistente hacia `PAGO_INCIERTO` sin liberar reserva y con `GatewayTimeoutException`, reintento del cliente con misma `Idempotency-Key` sobre `PAGO_INCIERTO` (resolución aprobada, rechazada y compensación con reembolso si la reserva venció) y rechazo `422` por cambio de payload; verificar que `mvn test -Dtest=CompraServiceTest` en `ms-ventas` pasa en verde.

## 4. Scheduling y reconciliación automática de compras en PAGO_INCIERTO

- [ ] 4.1 Crear `ms-ventas/src/main/java/com/ticketya/ventas/config/SchedulingConfig.java` habilitando `@EnableScheduling` condicionado a `ticketya.jobs.habilitados` (default `true`, siguiendo la convención de `ms-reservas`) y agregar la configuración de intervalo en `ms-ventas/src/main/resources/application.yml`; verificar con `mvn -q compile` en `ms-ventas`.
- [ ] 4.2 Implementar `ReconciliacionPagosJob` en `ms-ventas/src/main/java/com/ticketya/ventas/service/` con `@Scheduled(fixedDelayString = "${ticketya.pagos.reconciliacion.intervalo-ms:30000}", initialDelayString = "${ticketya.pagos.reconciliacion.intervalo-ms:30000}")` y método público `reconciliarPagosInciertos()` que procese compras en `PAGO_INCIERTO` con aislamiento de errores por compra: si `APROBADO` confirma reserva (o recupera si ya estaba `CONFIRMADA`), emite entradas con QR único y marca `APROBADA` (o reembolsa y marca `FALLIDA` si venció/fue liberada); si `RECHAZADO` marca `RECHAZADA` y libera reserva; si `NO_ENCONTRADO` marca `FALLIDA` y libera reserva; si `INCIERTO` o falla temporal mantiene `PAGO_INCIERTO`. Verificar con `mvn -q compile`.
- [ ] 4.3 Crear `ReconciliacionPagosJobTest` en `ms-ventas/src/test/java/com/ticketya/ventas/service/` cubriendo resolución posterior como `APROBADA` (confirmación de reserva + emisión de entradas con QR único), compensación con reembolso cuando la reserva venció (`FALLIDA` + `Pago.REEMBOLSADO`), resolución como `RECHAZADA` (liberación de reserva), resolución como no concretado (`FALLIDA` + liberación de reserva), permanencia en `PAGO_INCIERTO` ante incertidumbre/timeout persistente y aislamiento de errores entre compras; verificar que `mvn test -Dtest=ReconciliacionPagosJobTest` en `ms-ventas` pasa en verde.

## 5. Verificación integral

- [ ] 5.1 Ejecutar la suite completa de pruebas de `ms-ventas` (`mvn test`) verificando que `FlujoCompraIntegracionTest` (`504 PASARELA_TIMEOUT` desde `GlobalExceptionHandler` y reintento exitoso hacia `201 APROBADA`), `CompraControllerTest` y todas las pruebas unitarias pasan sin regresiones.

## Workflow follow-up

- Ejecutar `openspec validate --all --strict` tras completar la implementación.
- Archivar el cambio con `/opsx-archive` una vez revisado y verificado.

