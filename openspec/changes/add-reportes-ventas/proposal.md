# Proposal

## Why

La historia de usuario US-29 (F11 – Reportes para organizadores) requiere que los organizadores consulten entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación de sus eventos. `ms-ventas` es la única fuente de verdad de compras, entradas emitidas, devoluciones y reintegros, pero hoy no expone ningún endpoint interno de métricas ni agregaciones de ventas para que el servicio orquestador de reportes (por ejemplo `ms-eventos`, que aporta sectores y capacidades para calcular la ocupación) pueda armar el reporte del evento.

## What Changes

- Exponer el endpoint interno `GET /internal/eventos/{eventoId}/reporte-ventas` en `ms-ventas` (sin requerir JWT ni headers de identidad `X-User-Id` / `X-User-Rol`, y sin enrutarse por `api-gateway`, siguiendo la convención de `POST /internal/eventos/{eventoId}/cancelacion`).
- Resolver el cálculo de métricas por sector en una única consulta JPQL agregada con `GROUP BY` y `COALESCE` en `EntradaRepository` proyectada directamente a DTO, evitando cargar entidades `Entrada` en memoria.
- Aplicar las reglas de negocio sobre compras y estados de entrada:
  - Incluir exclusivamente entradas pertenecientes a compras en estado `APROBADA` (excluyendo `PENDIENTE`, `PAGO_INCIERTO`, `RECHAZADA` y `FALLIDA`).
  - Contabilizar por sector y en totales del evento: `entradasVendidas` (total emitido de compras `APROBADA`, abarcando todos los estados de `Entrada`), `entradasDevueltas` (solo `REINTEGRADA`), `entradasReintegroPendiente` (`REINTEGRO_PENDIENTE`), `entradasDevolucionSolicitada` (`DEVOLUCION_SOLICITADA`) y `recaudacionNeta` en `BigDecimal` (suma de `precioUnitario` descontando únicamente las entradas `REINTEGRADA`).
  - Para eventos sin ventas aprobadas registradas (evento inexistente en `ms-ventas` o solo con compras no aprobadas), responder `200 OK` con totales en `0` (`0L` y `BigDecimal.ZERO`), sin montos `null` y con `sectores: []`, sin devolver `404` ni `500`.
- Documentar `GET /internal/eventos/{eventoId}/reporte-ventas` y sus esquemas de respuesta en `docs/openapi.yaml` bajo el tag `Internos` (servidor directo `http://localhost:8083`).
- Incorporar pruebas de repositorio contra PostgreSQL 16 real (Testcontainers) validando `GROUP BY`, exclusión de compras no aprobadas, clasificación de estados, `recaudacionNeta` y evento sin ventas, además de pruebas unitarias de servicio y pruebas `MockMvc` de controlador.

Fuera de alcance: no se modifica `api-gateway`, `ms-eventos`, `ms-reservas`, `ms-auth` ni `ms-notificaciones`, ni se implementa el endpoint público `GET /eventos/{id}/reporte` con cálculo de porcentaje de ocupación y control de pertenencia `403` del organizador (responsabilidad de `ms-eventos`, que consumirá este endpoint interno).

## Capabilities

### New Capabilities
- `reportes-ventas`: consulta interna de métricas y reportes de ventas por evento desglosados por sector en `ms-ventas` (`GET /internal/eventos/{eventoId}/reporte-ventas`), con agregación en base de datos, filtrado estricto por compras `APROBADA`, desglose de estados de devolución/reintegro, `recaudacionNeta` en `BigDecimal` y manejo de eventos sin ventas.

### Modified Capabilities
<!-- No existing spec requirements change. -->

## Impact

- **Servicio afectado**: `ms-ventas` (`EntradaRepository`, nuevos DTOs `ReporteVentasEventoResponse` y `ReporteSectorVentasResponse`, nuevo servicio `ReporteVentasService` y nuevo controlador interno `ReporteVentasController`).
- **Base de datos (`db_ventas`)**: sin cambios de esquema; reutiliza las tablas `entrada` y `compra` existentes y el índice `idx_entrada_evento_estado (evento_id, estado)`.
- **Contratos**: `docs/openapi.yaml` agrega el path `/internal/eventos/{eventoId}/reporte-ventas` (`operationId: internoReporteVentasEvento`) y los schemas `ReporteVentasEvento` y `ReporteSectorVentas`.
- **Seguridad y enrutamiento**: ruta bajo `/internal/**`, accesible únicamente entre microservicios; `api-gateway` ya bloquea `/internal/**` (`404`) y no requiere modificaciones.
- **Pruebas**: nuevos `ReporteVentasRepositoryTest` (Testcontainers PostgreSQL 16), `ReporteVentasServiceTest` y `ReporteVentasControllerTest`.
