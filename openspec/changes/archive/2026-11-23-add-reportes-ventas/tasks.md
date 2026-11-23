# Tasks

## 1. DTOs de respuesta y agregación en base de datos

- [x] 1.1 Crear los records `ReporteSectorVentasResponse` (con constructor auxiliar `(Long, Long, Long, Long, Long, BigDecimal)` para compatibilidad directa con `COUNT`/`SUM` en JPQL `SELECT new` de Hibernate 6) y `ReporteVentasEventoResponse` en `ms-ventas/src/main/java/com/ticketya/ventas/dto/`, con protección contra `null` en `recaudacionNeta` (`BigDecimal.ZERO`) y `sectores` (`List.of()`). Verificar compilación con `mvn -q compile` en `ms-ventas`.
- [x] 1.2 Agregar en `EntradaRepository` la consulta JPQL agregada (`GROUP BY e.sectorId ORDER BY e.sectorId ASC` y `COALESCE`) proyectada directamente a `ReporteSectorVentasResponse` filtrando `e.eventoId = :eventoId` y `c.estado = :aprobada`, junto con el método `default` de conveniencia `resumirVentasPorSector(Long eventoId)`. Verificar compilación con `mvn -q compile` en `ms-ventas`.
- [x] 1.3 Crear `ReporteVentasRepositoryTest` en `ms-ventas/src/test/java/com/ticketya/ventas/repository/` usando `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)` e `@Import(TestcontainersConfiguration.class)` contra PostgreSQL 16 real (requiere Docker activo), verificando: agrupación por múltiples sectores ordenada por `sectorId ASC`; conteo exacto de `EMITIDA`, `USADA`, `DEVOLUCION_SOLICITADA`, `REINTEGRO_PENDIENTE` y `REINTEGRADA`; cálculo decimal exacto de `recaudacionNeta` descontando solo `REINTEGRADA` (incluyendo un sector con todas sus entradas `REINTEGRADA` devolviendo `0` no nulo); exclusión de compras en estados `PENDIENTE`, `PAGO_INCIERTO`, `RECHAZADA` y `FALLIDA` y de ventas de otros eventos; y evento sin ventas devolviendo lista vacía. Verificar con `mvn test -Dtest=ReporteVentasRepositoryTest` en `ms-ventas`.

## 2. Servicio de reportes de ventas

- [x] 2.1 Implementar `ReporteVentasService` en `ms-ventas/src/main/java/com/ticketya/ventas/service/` anotado con `@Transactional(readOnly = true)` y método `obtenerPorEvento(Long eventoId)` que delegue en `EntradaRepository.resumirVentasPorSector(eventoId)`, sume los totales del evento en `BigDecimal` y devuelva totales en `0L` / `BigDecimal.ZERO` y `List.of()` cuando no haya ventas aprobadas. Verificar compilación con `mvn -q compile` en `ms-ventas`.
- [x] 2.2 Crear `ReporteVentasServiceTest` en `ms-ventas/src/test/java/com/ticketya/ventas/service/` cubriendo: consolidación de totales de múltiples sectores (contadores y suma exacta en `BigDecimal`), evento sin ventas aprobadas devolviendo `0L` / `BigDecimal.ZERO` y `sectores` vacío sin valores `null`, y sector con recaudación neta `0` tras reintegros totales. Verificar con `mvn test -Dtest=ReporteVentasServiceTest` en `ms-ventas`.

## 3. Controlador interno y contrato OpenAPI

- [x] 3.1 Implementar `ReporteVentasController` en `ms-ventas/src/main/java/com/ticketya/ventas/controller/` bajo `@RequestMapping("/internal/eventos")` exponiendo `@GetMapping("/{eventoId}/reporte-ventas")` sin exigir headers de identidad ni JWT. Verificar compilación con `mvn -q compile` en `ms-ventas`.
- [x] 3.2 Crear `ReporteVentasControllerTest` en `ms-ventas/src/test/java/com/ticketya/ventas/controller/` con `@WebMvcTest(ReporteVentasController.class)` e `@Import(GlobalExceptionHandler.class)` verificando: `200 OK` con payload completo (totales y desglose por sector) sin headers `X-User-Id`/`X-User-Rol`/`Authorization`; `200 OK` para evento sin ventas con ceros y `sectores: []`; y `400 Bad Request` con código `VALIDACION` cuando `eventoId` no es numérico. Verificar con `mvn test -Dtest=ReporteVentasControllerTest` en `ms-ventas`.
- [x] 3.3 Documentar `GET /internal/eventos/{eventoId}/reporte-ventas` (`operationId: internoReporteVentasEvento`, tag `Internos`, servidor `http://localhost:8083`, respuestas `200` y `400`) y los schemas `ReporteVentasEvento` y `ReporteSectorVentas` en `docs/openapi.yaml`, verificando que los nombres de campos y tipos coincidan exactamente con los DTOs de `ms-ventas` y que el archivo YAML sea sintácticamente válido.

## 4. Verificación integral

- [x] 4.1 Ejecutar la suite completa de pruebas de `ms-ventas` (`mvn test`) y validar las especificaciones con `openspec validate --all --strict`, confirmando que todas las pruebas y validaciones pasan sin errores.

## Workflow follow-up

- Archivar el cambio con `openspec archive add-reportes-ventas` una vez completadas y verificadas todas las tareas.
- Validar las especificaciones archivadas con `openspec validate --all --strict`.
