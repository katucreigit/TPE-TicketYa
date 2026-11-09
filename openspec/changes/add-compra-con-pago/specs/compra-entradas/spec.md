# Spec Delta

## Purpose
Define el proceso de compra coordinado entre servicios, con compensaciones e idempotencia.

## ADDED Requirements

### Requirement: Compra exitosa
Cuando un cliente compra con un medio de pago aprobado, el sistema SHALL reservar, cobrar, confirmar y emitir las entradas, y responder 201.

#### Scenario: Flujo feliz
- **WHEN** se compra con token tok_ok
- **THEN** el sistema responde 201
- **AND** se emiten las entradas con QR

### Requirement: Pago rechazado
Si la pasarela rechaza el pago, entonces el sistema SHALL liberar la reserva, marcar la orden como fallida y responder 402.

#### Scenario: Rechazo
- **WHEN** se compra con tok_rechazado
- **THEN** el sistema responde 402
- **AND** los asientos vuelven a LIBRE

### Requirement: Timeout de pago
Si la pasarela no responde en el tiempo límite, entonces el sistema SHALL liberar la reserva y responder 504.

#### Scenario: Timeout
- **WHEN** se compra con tok_timeout
- **THEN** el sistema responde 504
- **AND** no queda cobro ni reserva activa

### Requirement: Idempotencia
Cuando llega una compra con una Idempotency-Key ya usada por el mismo cliente y el mismo cuerpo, el sistema SHALL devolver la respuesta original sin repetir el cobro.

#### Scenario: Reintento
- **WHEN** se repite la misma solicitud con la misma clave
- **THEN** se devuelve la misma respuesta
- **AND** no hay segundo cobro

#### Scenario: Clave con otro cuerpo
- **WHEN** se reutiliza la clave con un cuerpo distinto
- **THEN** el sistema responde 422

### Requirement: Asiento no disponible
Si ms-reservas informa conflicto por un asiento, entonces el sistema SHALL responder 409 sin intentar el cobro.

#### Scenario: Conflicto
- **WHEN** el asiento ya fue reservado
- **THEN** el sistema responde 409
- **AND** no se llama a la pasarela

### Requirement: Consulta de compras
Cuando un cliente consulta sus órdenes, el sistema SHALL devolver únicamente las propias.

#### Scenario: Orden ajena
- **WHEN** un cliente pide la orden de otro
- **THEN** el sistema responde 404
