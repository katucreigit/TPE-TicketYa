# Spec Delta

## Purpose
Define cuándo y cómo el cliente puede devolver una entrada y recuperar su dinero.

## ADDED Requirements

### Requirement: Devolución en plazo
Cuando el dueño devuelve una entrada VALIDA con más de 72 horas de anticipación, el sistema SHALL reintegrar el importe, marcar la entrada DEVUELTA y liberar el asiento.

#### Scenario: Devolución exitosa
- **WHEN** faltan 5 días y el cliente devuelve su entrada
- **THEN** la entrada queda DEVUELTA
- **AND** el asiento vuelve a LIBRE

### Requirement: Fuera de plazo
Si faltan menos de 72 horas para el evento, entonces el sistema SHALL rechazar la devolución con 422.

#### Scenario: Menos de 72 h
- **WHEN** faltan 48 horas
- **THEN** el sistema responde 422 con code FUERA_DE_PLAZO

### Requirement: Una sola vez
Si la entrada ya fue devuelta, entonces el sistema SHALL responder 409 y no generar un segundo reintegro.

#### Scenario: Doble devolución
- **WHEN** se devuelve una entrada DEVUELTA
- **THEN** el sistema responde 409
- **AND** existe un solo reintegro

### Requirement: Solo el dueño
Si quien devuelve no es el dueño de la entrada, entonces el sistema SHALL responder 404.

#### Scenario: Entrada ajena
- **WHEN** otro cliente intenta devolverla
- **THEN** el sistema responde 404
