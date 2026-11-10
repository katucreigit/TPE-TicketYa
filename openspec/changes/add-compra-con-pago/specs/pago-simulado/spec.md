# Spec Delta

## Purpose
Define el comportamiento de la pasarela de pago simulada de la Entrega 1.

## ADDED Requirements

### Requirement: Resultado por token
Cuando se procesa un pago, el sistema SHALL aprobarlo con tok_ok, rechazarlo con tok_rechazado y, con tok_timeout, registrar el cobro pero no responder a tiempo.

#### Scenario: Token desconocido
- **WHEN** se envía un token no previsto
- **THEN** el sistema responde 400

### Requirement: Cobro por clave
Cuando la pasarela recibe un cobro con una clave ya registrada, el sistema SHALL devolver el resultado original sin cobrar de nuevo.

#### Scenario: Clave repetida
- **WHEN** se envía dos veces el cobro con la misma clave
- **THEN** se devuelve el resultado original
- **AND** no hay un segundo cobro

#### Scenario: Reintento tras tok_timeout
- **WHEN** se reintenta un cobro que hizo timeout
- **THEN** la pasarela devuelve el cobro ya registrado

### Requirement: Datos del medio de pago
El sistema SHALL guardar de cada compra el tipo de medio de pago y los últimos cuatro dígitos, y nunca el token ni el número completo.

#### Scenario: Consulta
- **WHEN** se consulta una compra
- **THEN** se ven tipo y últimos 4 dígitos
