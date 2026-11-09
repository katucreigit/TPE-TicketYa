# Spec Delta

## Purpose
Define el comportamiento de la pasarela de pago simulada usada en la Entrega 1.

## ADDED Requirements

### Requirement: Resultado por token
Cuando se procesa un pago, el sistema SHALL aprobarlo con tok_ok, rechazarlo con tok_rechazado y simular demora con tok_timeout.

#### Scenario: Token desconocido
- **WHEN** se envía un token no previsto
- **THEN** el sistema responde 400

### Requirement: Registro del pago
El sistema SHALL guardar cada intento de pago con su resultado y la orden asociada.

#### Scenario: Intento fallido
- **WHEN** un pago es rechazado
- **THEN** queda registrado con estado RECHAZADO
