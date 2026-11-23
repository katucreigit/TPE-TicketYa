# Design

## Context

`ms-ventas` persiste las compras (`Compra`, con `EstadoCompra`: `PENDIENTE`, `PAGO_INCIERTO`, `APROBADA`, `RECHAZADA`, `FALLIDA`) y las entradas emitidas (`Entrada`, con `eventoId`, `sectorId`, `precioUnitario` `NUMERIC(12,2)` y `EstadoEntrada`: `EMITIDA`, `DEVOLUCION_SOLICITADA`, `REINTEGRO_PENDIENTE`, `REINTEGRADA`, `USADA`) en `db_ventas` (PostgreSQL 16). La tabla `entrada` ya posee el índice `idx_entrada_evento_estado (evento_id, estado)`.

Actualmente `ms-ventas` ya expone un endpoint interno bajo `/internal/eventos` (`CancelacionEventoController` con `POST /internal/eventos/{eventoId}/cancelacion`), sin exigir headers de identidad ni JWT porque `api-gateway` no enruta `/internal/**`. No existe todavía ningún cliente en `ms-eventos` ni contrato previo en `docs/openapi.yaml` para reportes de ventas (ver `proposal.md` para la motivación de US-29).

## Goals / Non-Goals

**Goals:**
- Proveer el endpoint interno `GET /internal/eventos/{eventoId}/reporte-ventas` con un contrato REST limpio, determinista y documentado en `docs/openapi.yaml` para que el orquestador de US-29 (`ms-eventos`) lo consuma directamente.
- Ejecutar una única consulta JPQL agregada en PostgreSQL (`GROUP BY` + `COALESCE` proyectada a DTO) para escalar a eventos masivos sin traer miles de `Entrada` a memoria.
- Garantizar exactitud contable (`BigDecimal`, cero `null`s, exclusión estricta de compras no `APROBADA`, totales consistentes con el desglose por sector) y verificación en PostgreSQL 16 real vía Testcontainers.

**Non-Goals:**
- Calcular el porcentaje de ocupación o devolver nombres/capacidades de sectores (datos que pertenecen a `ms-eventos` / `ms-reservas`).
- Validar existencia del evento o pertenencia del organizador (`403`/`404`) llamando a `ms-eventos`: esa validación corresponde al endpoint público de reportes en `ms-eventos`.
- Agregar filtros opcionales de fechas o paginación de sectores (no requeridos por US-29; un evento tiene un número acotado de sectores).

## Decisions

### 1. Ruta y estructura del controlador interno
- **Decisión**: Crear `ReporteVentasController` con `@RestController` y `@RequestMapping("/internal/eventos")` exponiendo `@GetMapping("/{eventoId}/reporte-ventas")`, sin headers `X-User-Id` / `X-User-Rol` ni JWT.
- **Justificación**: Sigue exactamente el patrón de `CancelacionEventoController` (`POST /internal/eventos/{eventoId}/cancelacion`) y evita mezclar responsabilidades de cancelación con consultas de solo lectura en la misma clase. `GlobalExceptionHandler` ya maneja `MethodArgumentTypeMismatchException` devolviendo `400 VALIDACION` si `eventoId` no es numérico.
- **Alternativas consideradas**:
  - Agregar el método dentro de `CancelacionEventoController`: descartado para mantener alta cohesión por caso de uso.
  - Usar `/internal/ventas/eventos/{eventoId}`: descartado porque rompe la convención existente `/internal/eventos/{eventoId}/...` ya usada por `ms-eventos` y `ms-reservas` en `docs/openapi.yaml`.

### 2. Modelo de respuesta (`ReporteVentasEventoResponse` y `ReporteSectorVentasResponse`)
- **Decisión**: Definir dos Java `record` en `com.ticketya.ventas.dto`:
  - `ReporteSectorVentasResponse(Long sectorId, long entradasVendidas, long entradasDevueltas, long entradasReintegroPendiente, long entradasDevolucionSolicitada, BigDecimal recaudacionNeta)`
    - Incluir un constructor auxiliar con wrappers `(Long, Long, Long, Long, Long, BigDecimal)` que normaliza posibles `null` a `0L` / `BigDecimal.ZERO` y delega al constructor canónico, garantizando resolución exacta del constructor en JPQL `SELECT new` de Hibernate 6 (`COUNT`/`SUM` devuelven `Long`) mientras los getters y el JSON preservan `long` primitivos nunca nulos.
  - `ReporteVentasEventoResponse(Long eventoId, long entradasVendidas, long entradasDevueltas, long entradasReintegroPendiente, long entradasDevolucionSolicitada, BigDecimal recaudacionNeta, List<ReporteSectorVentasResponse> sectores)`
- **Justificación**:
  - `entradasVendidas` refleja el volumen histórico emitido de compras `APROBADA`; `entradasDevueltas` cuenta exclusivamente las `REINTEGRADA` (efectivamente devueltas/reintegradas).
  - Exponer explícitamente `entradasReintegroPendiente` (`REINTEGRO_PENDIENTE`) y `entradasDevolucionSolicitada` (`DEVOLUCION_SOLICITADA`) evita distorsionar los contadores principales con estados transitorios (una `DEVOLUCION_SOLICITADA` todavía puede rechazarse y volver a `EMITIDA`, y una `REINTEGRO_PENDIENTE` aún no completó el reembolso en la pasarela).
  - `recaudacionNeta` en `BigDecimal` suma `precioUnitario` de todas las entradas de compras `APROBADA` con estado `<> REINTEGRADA`, coincidiendo con el dinero efectivamente retenido (descontando el 100% del `precioUnitario` de las entradas `REINTEGRADA`). Se verificó en `DevolucionService.nuevoReintegro` y `CancelacionEventoService.marcarPendientes` que en TicketYa todo reintegro es siempre por el 100% de `entrada.precioUnitario` (no existen reintegros parciales ni retenciones); esto permite resolver todo en una sola agregación sobre `entrada` sin joinear `reintegro`.
  - Si un consumidor necesita entradas activas/netas para ocupación, puede obtenerlas de forma inequívoca como `entradasVendidas - entradasDevueltas` (o combinando con `ms-reservas`).
- **Alternativas consideradas**:
  - Exponer también `recaudacionBruta` y `montoReintegrado`: descartado por decisión explícita para mantener la respuesta acotada a `recaudacionNeta`.

### 3. Consulta JPQL agregada única en `EntradaRepository`
- **Decisión**: Agregar un método de consulta en `EntradaRepository` con JPQL `SELECT new com.ticketya.ventas.dto.ReporteSectorVentasResponse(...)` sobre `Entrada e JOIN e.compra c`:
  ```jpql
  SELECT new com.ticketya.ventas.dto.ReporteSectorVentasResponse(
      e.sectorId,
      COUNT(e),
      COALESCE(SUM(CASE WHEN e.estado = :reintegrada THEN 1L ELSE 0L END), 0L),
      COALESCE(SUM(CASE WHEN e.estado = :reintegroPendiente THEN 1L ELSE 0L END), 0L),
      COALESCE(SUM(CASE WHEN e.estado = :devolucionSolicitada THEN 1L ELSE 0L END), 0L),
      COALESCE(SUM(CASE WHEN e.estado <> :reintegrada THEN e.precioUnitario ELSE :cero END), :cero)
  )
  FROM Entrada e
  JOIN e.compra c
  WHERE e.eventoId = :eventoId
    AND c.estado = :aprobada
  GROUP BY e.sectorId
  ORDER BY e.sectorId ASC
  ```
- **Justificación**:
  - Una sola query a PostgreSQL retorna únicamente `S` filas (cantidad de sectores con ventas), evitando traer potencialmente decenas de miles de entidades `Entrada` a la JVM.
  - Pasar `:cero` (`BigDecimal.ZERO`) y los valores de enum como parámetros tipados (`EstadoCompra.APROBADA`, `EstadoEntrada.REINTEGRADA`, `EstadoEntrada.REINTEGRO_PENDIENTE`, `EstadoEntrada.DEVOLUCION_SOLICITADA`) evita errores de inferencia de tipos de Hibernate 6 (`SemanticException` al mezclar `NUMERIC`/`BigDecimal` con literales enteros `0` en `CASE`/`COALESCE`) y elimina riesgo de typos en nombres de enum.
  - `ORDER BY e.sectorId ASC` asegura salidas deterministas para pruebas y clientes.
  - El método de conveniencia `default` en el repositorio (o llamada desde `ReporteVentasService`) puede pasar las constantes de enum y `BigDecimal.ZERO` para mantener una firma limpia hacia el servicio.
  - Además, en el constructor compacto de `ReporteSectorVentasResponse` (o en `ReporteVentasService`), se protege `recaudacionNeta = recaudacionNeta != null ? recaudacionNeta : BigDecimal.ZERO` como defensa en profundidad contra cualquier valor nulo.
- **Alternativas consideradas**:
  - SQL nativo con proyección por interfaz (`interface SectorVentasProjection`): válido, pero JPQL tipado con constructor expression mantiene portabilidad, validación de nombres de atributos en arranque y mapeo directo al `record` sin clase adaptadora intermedia.
  - Dos queries separadas (una por sector y otra de totales del evento): innecesario; sumar en Java sobre una lista pequeña de `S` sectores en `ReporteVentasService` (típicamente 1 a 20 sectores por lugar) es O(S), garantiza consistencia exacta entre desglose y totales, y ahorra un round-trip a PostgreSQL.

### 4. Comportamiento de solo lectura y eventos sin ventas
- **Decisión**: Anotar `ReporteVentasService.obtenerPorEvento(Long eventoId)` con `@Transactional(readOnly = true)`. Si el repositorio devuelve lista vacía, construir `new ReporteVentasEventoResponse(eventoId, 0L, 0L, 0L, 0L, BigDecimal.ZERO, List.of())`.
- **Justificación**: Evita llamadas de red circulares hacia `ms-eventos`, responde en tiempo constante y cumple el caso borde de devolver `200 OK` con ceros y lista vacía sin `null` ni `404`/`500`.

### 5. Documentación OpenAPI y pruebas
- **Decisión**:
  - Documentar `GET /internal/eventos/{eventoId}/reporte-ventas` en `docs/openapi.yaml` bajo la sección `INTERNOS` (junto a `/internal/eventos/{eventoId}/cancelacion`), con `servers: [http://localhost:8083]`, `tags: [Internos]`, `operationId: internoReporteVentasEvento`, parámetro `$ref: '#/components/parameters/EventoIdPath'`, respuesta `200` (`$ref: '#/components/schemas/ReporteVentasEvento'`) y respuesta `400` (`$ref: '#/components/responses/BadRequest'`), más los schemas `ReporteVentasEvento` y `ReporteSectorVentas`.
  - Crear `ReporteVentasRepositoryTest` con `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` e `@Import(TestcontainersConfiguration.class)` ejecutando contra PostgreSQL 16 real (conforme a la convención de `TestcontainersConfiguration` del proyecto).
  - Crear `ReporteVentasServiceTest` (Mockito unitario) y `ReporteVentasControllerTest` (`@WebMvcTest` con `GlobalExceptionHandler`).

## Risks / Trade-offs

- **[Tipado estricto de `CASE`/`COALESCE` y wrappers `Long` en Hibernate 6 con PostgreSQL]** → Pasar `BigDecimal.ZERO` como parámetro `:cero`, proveer constructor auxiliar `(Long, Long, Long, Long, Long, BigDecimal)` en `ReporteSectorVentasResponse` y validar la query real en `ReporteVentasRepositoryTest` contra PostgreSQL 16 con Testcontainers (requiere Docker activo al ejecutar `mvn test`).
- **[Eventual incorporación futura de reintegros parciales]** → Hoy tanto `DevolucionService` como `CancelacionEventoService` reintegran siempre el 100% de `entrada.precioUnitario`, por lo que descontar `e.precioUnitario` cuando `e.estado = REINTEGRADA` es exacto. Si en el futuro se introdujeran reintegros parciales o penalidades, la fórmula deberá cambiar para restar `SUM(r.monto)` de `reintegro` con `r.estado = COMPLETADO` (evitando multiplicar filas en el `JOIN`).
- **[Diferencia de escala en `BigDecimal` entre `0` (`BigDecimal.ZERO`) y `0.00` de PostgreSQL `NUMERIC(12,2)`]** → En las pruebas comparar montos con `compareTo` / `isEqualByComparingTo` y en el servicio devolver `BigDecimal.ZERO` cuando no hay ventas (y el valor exacto devuelto por PostgreSQL cuando sí hay filas); documentar explícitamente en OpenAPI el formato decimal.
- **[Sectores del evento sin ventas no aparecen en `sectores`]** → `ms-ventas` no conoce el catálogo completo de sectores del lugar; el servicio orquestador (`ms-eventos`) cruzará la lista de sectores del evento con la respuesta de `ms-ventas` completando con ceros los sectores sin ventas.
- **[Escaneo de filas en eventos muy masivos]** → El índice existente `idx_entrada_evento_estado (evento_id, estado)` ya tiene `evento_id` como columna líder y acelera el filtrado por evento; no se requieren cambios de DDL. Si en el futuro se detectara cuello de botella por volumen extremo, puede agregarse un índice compuesto `(evento_id, sector_id, estado)` sin cambiar código ni contrato.
