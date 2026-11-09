# Spec Delta

## Purpose
Define el comportamiento de la pasarela de pago simulada de la Entrega 1.

## ADDED Requirements

### Requirement: Resultado por token
Cuando se procesa un pago, el sistema SHALL aprobarlo con tok_ok, rechazarlo con tok_rechazado y simular una falla de la pasarela con tok_timeout.

#### Scenario: Token desconocido
- **WHEN** se envía un token no previsto
- **THEN** el sistema responde 400

### Requirement: Datos del medio de pago
El sistema SHALL guardar de cada compra el tipo de medio de pago y los últimos cuatro dígitos, y nunca el token ni el número completo.

#### Scenario: Consulta
- **WHEN** se consulta una compra
- **THEN** se ven tipo y últimos 4 dígitos
