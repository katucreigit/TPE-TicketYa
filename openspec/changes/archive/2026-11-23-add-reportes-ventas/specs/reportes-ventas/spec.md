# Spec Delta

## Purpose

Define el contrato interno de reportes de ventas por evento en `ms-ventas`, proveyendo métricas agregadas por sector y totales de entradas vendidas, devueltas, estados de devolución/reintegro en curso y recaudación neta para los consumidores internos que arman los reportes de organizadores (US-29).

## ADDED Requirements

### Requirement: Consulta interna de reporte de ventas por evento
`ms-ventas` SHALL exponer `GET /internal/eventos/{eventoId}/reporte-ventas` sin requerir JWT ni headers de identidad (`X-User-Id`, `X-User-Rol`). Ante un `eventoId` numérico válido SHALL responder `200 OK` con `eventoId`, los totales del evento y `sectores` ordenado por `sectorId` ascendente. Ante un `eventoId` no numérico SHALL responder `400 Bad Request` con código `VALIDACION`.

#### Scenario: Consulta exitosa sin headers de identidad
- **GIVEN** un evento `42` con compras aprobadas registradas en dos sectores (`11` y `12`)
- **WHEN** un servicio interno invoca `GET /internal/eventos/42/reporte-ventas` sin headers `X-User-Id`, `X-User-Rol` ni `Authorization`
- **THEN** `ms-ventas` responde `200 OK` con `eventoId` igual a `42`, los totales consolidados del evento y la lista `sectores` ordenada con el sector `11` seguido del sector `12`.

#### Scenario: Identificador de evento no numérico
- **WHEN** se invoca `GET /internal/eventos/abc/reporte-ventas`
- **THEN** `ms-ventas` responde `400 Bad Request` con código de error `VALIDACION`.

### Requirement: Exclusión de compras no aprobadas y aislamiento por evento
Al calcular el reporte de ventas de un evento, `ms-ventas` SHALL incluir únicamente las entradas asociadas a compras en estado `APROBADA` pertenecientes al `eventoId` solicitado. `ms-ventas` SHALL excluir cualquier compra o entrada vinculada a compras en estado `PENDIENTE`, `PAGO_INCIERTO`, `RECHAZADA` o `FALLIDA`, así como ventas pertenecientes a otros eventos.

#### Scenario: Compras no aprobadas y de otros eventos quedan excluidas
- **GIVEN** un evento `42` con una compra `APROBADA` de 2 entradas en el sector `11` y compras adicionales en estados `PENDIENTE`, `PAGO_INCIERTO`, `RECHAZADA` y `FALLIDA` para el mismo evento
- **AND** una compra `APROBADA` de 3 entradas para otro evento `99`
- **WHEN** se consulta `GET /internal/eventos/42/reporte-ventas`
- **THEN** el reporte contabiliza exclusivamente las 2 entradas de la compra `APROBADA` del evento `42`.

### Requirement: Conteo de entradas vendidas, devueltas y estados en curso por sector
Por cada sector con ventas aprobadas y en los totales del evento, `ms-ventas` SHALL informar: `entradasVendidas` (todas las entradas de compras `APROBADA` en cualquier estado), `entradasDevueltas` (`REINTEGRADA`), `entradasReintegroPendiente` (`REINTEGRO_PENDIENTE`) y `entradasDevolucionSolicitada` (`DEVOLUCION_SOLICITADA`). Cada total del evento SHALL igualar la suma de sus sectores.

#### Scenario: Desglose de todos los estados de entrada en múltiples sectores
- **GIVEN** un evento `42` con compras `APROBADA` que contienen:
  - en el sector `11`: 2 entradas `EMITIDA`, 1 `USADA`, 1 `DEVOLUCION_SOLICITADA`, 1 `REINTEGRO_PENDIENTE` y 2 `REINTEGRADA`
  - en el sector `12`: 3 entradas `EMITIDA` y 1 `REINTEGRADA`
- **WHEN** se consulta `GET /internal/eventos/42/reporte-ventas`
- **THEN** el sector `11` informa `entradasVendidas: 7`, `entradasDevueltas: 2`, `entradasReintegroPendiente: 1`, `entradasDevolucionSolicitada: 1`
- **AND** el sector `12` informa `entradasVendidas: 4`, `entradasDevueltas: 1`, `entradasReintegroPendiente: 0`, `entradasDevolucionSolicitada: 0`
- **AND** los totales del evento informan `entradasVendidas: 11`, `entradasDevueltas: 3`, `entradasReintegroPendiente: 1`, `entradasDevolucionSolicitada: 1`.

### Requirement: Cálculo de recaudación neta con precisión decimal exacta
`ms-ventas` SHALL calcular `recaudacionNeta` por sector y en el total del evento con precisión decimal exacta (`BigDecimal`, nunca punto flotante), sumando el `precioUnitario` de las entradas de compras `APROBADA` cuyo estado sea distinto de `REINTEGRADA` (descontando únicamente las entradas efectivamente reintegradas). Ningún monto de `recaudacionNeta` SHALL ser `null`.

#### Scenario: Recaudación neta descuenta únicamente entradas reintegradas
- **GIVEN** un evento `42` con compras `APROBADA` en el sector `11` (precio unitario `45000.50`): 1 entrada `EMITIDA`, 1 `USADA`, 1 `DEVOLUCION_SOLICITADA`, 1 `REINTEGRO_PENDIENTE` y 1 `REINTEGRADA`
- **AND** en el sector `12` (precio unitario `28000.25`): 2 entradas `EMITIDA` y 1 `REINTEGRADA`
- **WHEN** se consulta `GET /internal/eventos/42/reporte-ventas`
- **THEN** el sector `11` informa `recaudacionNeta` igual a `180002.00` (4 entradas no reintegradas por `45000.50`)
- **AND** el sector `12` informa `recaudacionNeta` igual a `56000.50` (2 entradas no reintegradas por `28000.25`)
- **AND** el total del evento informa `recaudacionNeta` igual a `236002.50`.

#### Scenario: Sector con todas sus entradas reintegradas devuelve recaudación neta cero
- **GIVEN** un evento `42` con compras `APROBADA` en el sector `11` cuyas 2 entradas pasaron todas a estado `REINTEGRADA`
- **WHEN** se consulta `GET /internal/eventos/42/reporte-ventas`
- **THEN** el sector `11` aparece en `sectores` con `entradasVendidas: 2`, `entradasDevueltas: 2` y `recaudacionNeta` igual a `0` (nunca `null`)
- **AND** el total del evento informa `entradasVendidas: 2`, `entradasDevueltas: 2` y `recaudacionNeta` igual a `0`.

### Requirement: Evento sin ventas aprobadas registradas
Cuando el `eventoId` consultado no tenga ventas aprobadas registradas en `ms-ventas` (ya sea porque no tiene compras, porque el evento no existe en `ms-ventas` o porque solo existen compras en estados distintos de `APROBADA`), `ms-ventas` SHALL responder `200 OK` con `eventoId` igual al solicitado, todos los contadores en `0`, `recaudacionNeta` en `0` (`BigDecimal.ZERO`) y `sectores` como lista vacía `[]`. En ningún caso SHALL devolver `null` en los montos ni responder `404` o `500`.

#### Scenario: Evento sin compras registradas
- **GIVEN** que no existen compras ni entradas registradas para el evento `999` en `ms-ventas`
- **WHEN** se consulta `GET /internal/eventos/999/reporte-ventas`
- **THEN** `ms-ventas` responde `200 OK` con `eventoId: 999`, `entradasVendidas: 0`, `entradasDevueltas: 0`, `entradasReintegroPendiente: 0`, `entradasDevolucionSolicitada: 0`, `recaudacionNeta: 0` y `sectores: []`.

#### Scenario: Evento con únicamente compras no aprobadas
- **GIVEN** un evento `77` que solo tiene compras en estados `PENDIENTE`, `PAGO_INCIERTO`, `RECHAZADA` y `FALLIDA`
- **WHEN** se consulta `GET /internal/eventos/77/reporte-ventas`
- **THEN** `ms-ventas` responde `200 OK` con todos los contadores en `0`, `recaudacionNeta: 0` y `sectores: []`.

### Requirement: Agregación delegada en la base de datos
`ms-ventas` SHALL resolver la agrupación por sector, los conteos condicionales por estado y la suma de `recaudacionNeta` directamente en la base de datos mediante una consulta agregada (`GROUP BY` y `COALESCE`) proyectada a DTO, sin cargar en memoria el listado completo de entradas del evento.

#### Scenario: Reporte calculado mediante proyección agregada por sector
- **GIVEN** un evento con ventas distribuidas en múltiples sectores y estados
- **WHEN** se ejecuta la consulta del reporte de ventas en el repositorio
- **THEN** la base de datos retorna exactamente una fila agregada por cada `sectorId` con ventas aprobadas, con todos los contadores y la `recaudacionNeta` ya calculados y sin valores nulos.
