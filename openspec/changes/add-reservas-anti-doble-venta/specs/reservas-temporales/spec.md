# Spec Delta

## Purpose
Define la reserva temporal de entradas con vencimiento, confirmación y liberación.

## ADDED Requirements

### Requirement: Creación de reserva
Cuando se solicita reservar asientos libres, el sistema SHALL marcarlos RESERVADO y crear una reserva PENDIENTE con vencimiento.

#### Scenario: Reserva exitosa
- **WHEN** se reservan dos asientos libres
- **THEN** quedan RESERVADO
- **AND** la reserva tiene fecha de vencimiento

### Requirement: Todo o nada
Si alguno de los asientos pedidos no está libre, entonces el sistema SHALL rechazar toda la reserva con 409 sin reservar ninguno.

#### Scenario: Un asiento ocupado
- **WHEN** se piden tres asientos y uno ya está reservado
- **THEN** el sistema responde 409
- **AND** los otros dos siguen LIBRE

### Requirement: Límite por cliente
Si la cantidad pedida más las entradas activas del cliente para el evento supera el límite configurado, entonces el sistema SHALL responder 422.

#### Scenario: Excede el límite
- **WHEN** un cliente con 3 entradas pide 2 más y el límite es 4
- **THEN** el sistema responde 422

### Requirement: Vencimiento
Mientras una reserva esté PENDIENTE y llegue su vencimiento, el sistema SHALL marcarla EXPIRADA y liberar sus asientos.

#### Scenario: Reserva vencida
- **WHEN** pasa el tiempo de vencimiento sin confirmar
- **THEN** la reserva queda EXPIRADA
- **AND** los asientos vuelven a LIBRE

### Requirement: Confirmación
Cuando se confirma una reserva PENDIENTE dentro de su plazo, el sistema SHALL pasar los asientos a VENDIDO y la reserva a CONFIRMADA.

#### Scenario: Confirmar a tiempo
- **WHEN** se confirma antes del vencimiento
- **THEN** los asientos pasan a VENDIDO

#### Scenario: Confirmar vencida
- **WHEN** se confirma después del vencimiento
- **THEN** el sistema responde 409 con code RESERVA_EXPIRADA

### Requirement: Liberación
Cuando se libera una reserva PENDIENTE por una compensación, el sistema SHALL devolver sus asientos a LIBRE.

#### Scenario: Compensación
- **WHEN** falla el pago y ms-ventas libera la reserva
- **THEN** los asientos vuelven a LIBRE
