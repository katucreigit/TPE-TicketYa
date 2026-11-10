# Spec Delta

## Purpose
Define la estructura de las entradas emitidas con código QR único y los endpoints de consulta de compras y entradas del cliente (US-16), garantizando el aislamiento de datos por usuario y la inmutabilidad de los tickets.

## ADDED Requirements

### Requirement: Generación y persistencia de entradas
Al confirmarse una compra, el sistema SHALL persistir una entrada individual por cada ubicación adquirida, asignándole un código QR único (UUID) y estado inicial EMITIDA, junto con el snapshot de precios y datos del evento.

#### Scenario: Emisión correcta de tickets
- **GIVEN** una compra aprobada con 2 lugares para un evento
- **WHEN** el sistema genera las entradas correspondientes
- **THEN** se crean 2 registros de entrada en estado "EMITIDA"
- **AND** cada entrada posee un "codigo_qr" único en formato UUID
- **AND** cada entrada congela inmutablemente: evento_id, fecha_hora_evento, sector, fila, asiento (si aplica) y precio_unitario.

---

### Requirement: Consulta de compras del cliente
El sistema SHALL exponer endpoints para listar el historial de compras del usuario autenticado y ver el detalle de una compra específica (`GET /api/compras` y `GET /api/compras/{id}`).

#### Scenario: Listar compras propias
- **GIVEN** que el cliente "cli-100" posee 3 compras registradas
- **WHEN** envía un GET /api/compras con su header X-User-Id "cli-100"
- **THEN** responde HTTP 200 con el listado de sus 3 compras
- **AND** cada compra incluye total, fecha, estado, tipo de pago y últimos 4 dígitos.

#### Scenario: Detalle de compra propia
- **GIVEN** la compra "cmp-001" perteneciente al cliente "cli-100"
- **WHEN** el cliente envía GET /api/compras/cmp-001 con X-User-Id "cli-100"
- **THEN** responde HTTP 200 con el desglose de ítems, entradas asociadas y montos.

#### Scenario: Acceso denegado a compra de otro cliente
- **GIVEN** la compra "cmp-002" perteneciente al cliente "cli-200"
- **WHEN** el cliente "cli-100" envía GET /api/compras/cmp-002 con su propio X-User-Id
- **THEN** el sistema responde HTTP 403 Forbidden.

#### Scenario: Compra inexistente
- **GIVEN** un ID de compra "cmp-999" que no existe en el sistema
- **WHEN** un cliente autenticado envía GET /api/compras/cmp-999
- **THEN** el sistema responde HTTP 404 Not Found.

---

### Requirement: Consulta de entradas del cliente
El sistema SHALL exponer endpoints para listar las entradas activas del usuario autenticado y consultar el detalle unitario de un ticket para su presentación (`GET /api/entradas` y `GET /api/entradas/{id}`).

#### Scenario: Listar entradas propias
- **GIVEN** que el cliente "cli-100" tiene 2 entradas en estado EMITIDA
- **WHEN** envía un GET /api/entradas con X-User-Id "cli-100"
- **THEN** responde HTTP 200 con la lista de sus 2 entradas y sus respectivos estados.

#### Scenario: Detalle de entrada propia con QR
- **GIVEN** la entrada "ent-500" perteneciente a una compra de "cli-100"
- **WHEN** el cliente envía GET /api/entradas/ent-500 con X-User-Id "cli-100"
- **THEN** responde HTTP 200 con los datos del evento, ubicación y el "codigo_qr".

#### Scenario: Intento de acceso a entrada de otro usuario
- **GIVEN** la entrada "ent-700" perteneciente al cliente "cli-200"
- **WHEN** el cliente "cli-100" envía GET /api/entradas/ent-700
- **THEN** el sistema responde HTTP 403 Forbidden.

#### Scenario: Entrada inexistente
- **GIVEN** un ID "ent-999" que no existe en la base de datos
- **WHEN** se envía GET /api/entradas/ent-999
- **THEN** el sistema responde HTTP 404 Not Found.