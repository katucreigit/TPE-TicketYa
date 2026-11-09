# Spec Delta

## Purpose
Define el pago de una reserva coordinado entre servicios, con compensaciones e idempotencia.

## ADDED Requirements

### Requirement: Compra exitosa
Cuando el cliente dueño paga una reserva vigente y la pasarela aprueba, el sistema SHALL confirmar la reserva, marcar la compra APROBADA, emitir una entrada por lugar y responder 201.

#### Scenario: Flujo feliz
- **WHEN** se paga con tok_ok una reserva vigente
- **THEN** el sistema responde 201
- **AND** se emite una entrada por lugar

### Requirement: Pago rechazado
Si la pasarela rechaza el pago, entonces el sistema SHALL liberar la reserva, marcar la compra RECHAZADA y responder 402.

#### Scenario: Rechazo
- **WHEN** se paga con tok_rechazado
- **THEN** el sistema responde 402
- **AND** los lugares vuelven a LIBRE

### Requirement: Reserva vencida
Si la reserva venció antes del pago, entonces el sistema SHALL responder 410 sin cobrar y marcar la compra FALLIDA; si venció durante el cobro con el pago ya aprobado, SHALL reembolsarlo y responder 410.

#### Scenario: Vencida antes
- **WHEN** se paga una reserva vencida
- **THEN** el sistema responde 410
- **AND** no se cobra

#### Scenario: Vencida durante el cobro
- **WHEN** el pago se aprueba pero ms-reservas rechaza la confirmación
- **THEN** se reembolsa el pago
- **AND** el sistema responde 410

### Requirement: Falla de la pasarela
Si la pasarela no responde o no se puede conectar, entonces el sistema SHALL responder 504 (o 502/503 si está caída) sin confirmar nada y conservar la reserva vigente para reintentar con la misma Idempotency-Key.

#### Scenario: Timeout
- **WHEN** se paga con tok_timeout
- **THEN** el sistema responde 504
- **AND** la reserva sigue vigente

#### Scenario: Reintento tras falla
- **WHEN** se reintenta con la misma clave y la reserva sigue vigente
- **THEN** el pago se procesa una sola vez

#### Scenario: Reintento con reserva vencida
- **WHEN** se reintenta después del vencimiento
- **THEN** el sistema responde 410 sin cobrar

### Requirement: Reserva ajena
Si la reserva pertenece a otro cliente, entonces el sistema SHALL responder 403.

#### Scenario: Otro cliente
- **WHEN** un cliente paga la reserva de otro
- **THEN** el sistema responde 403

### Requirement: Idempotencia
Cuando llega una compra con una Idempotency-Key ya usada por el mismo cliente y el mismo cuerpo, el sistema SHALL devolver el resultado original sin cobrar ni emitir otra vez; sin clave SHALL responder 400 y con otro cuerpo 422.

#### Scenario: Reintento
- **WHEN** se repite la solicitud con la misma clave
- **THEN** se devuelve el resultado original
- **AND** no hay segundo cobro ni nuevas entradas

#### Scenario: Clave con otro cuerpo
- **WHEN** se reutiliza la clave con un cuerpo distinto
- **THEN** el sistema responde 422

#### Scenario: Sin clave
- **WHEN** falta el header Idempotency-Key
- **THEN** el sistema responde 400

### Requirement: Solicitudes simultáneas
Cuando dos solicitudes simultáneas comparten la misma clave y cuerpo, el sistema SHALL ejecutar un único cobro.

#### Scenario: Doble envío
- **WHEN** llegan dos solicitudes iguales a la vez
- **THEN** se cobra una sola vez
