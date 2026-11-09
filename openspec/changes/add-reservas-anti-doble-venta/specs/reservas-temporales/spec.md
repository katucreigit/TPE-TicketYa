# Spec Delta

## Purpose
Define la reserva temporal de lugares con vencimiento, cancelación y confirmación.

## ADDED Requirements

### Requirement: Creación
Cuando un cliente reserva lugares libres de un evento PUBLICADO, el sistema SHALL marcarlos reservados, registrar el precio de cada uno y responder 201 con vencimiento de 10 minutos desde la creación.

#### Scenario: Reserva exitosa
- **WHEN** se reservan dos asientos libres
- **THEN** el sistema responde 201 con expiraEn
- **AND** cada lugar guarda su precio

### Requirement: Datos inválidos
Si la cantidad es menor o igual a 0, hay asientos repetidos, o se mezclan asientos con cantidad según el tipo de sector, entonces el sistema SHALL responder 400.

#### Scenario: Asiento repetido
- **WHEN** el mismo asiento aparece dos veces
- **THEN** el sistema responde 400

### Requirement: Todo o nada
Si algún lugar pedido no está disponible, entonces el sistema SHALL responder 409 sin reservar ninguno.

#### Scenario: Un asiento ocupado
- **WHEN** se piden tres asientos y uno está reservado
- **THEN** el sistema responde 409
- **AND** los otros dos siguen LIBRE

### Requirement: Evento no reservable
Si el evento no está PUBLICADO, entonces el sistema SHALL responder 409 al reservar.

#### Scenario: Evento suspendido
- **WHEN** se reserva en un evento SUSPENDIDO
- **THEN** el sistema responde 409

### Requirement: Vencimiento
Mientras una reserva esté PENDIENTE y llegue su vencimiento, el sistema SHALL marcarla VENCIDA y liberar sus lugares.

#### Scenario: Reserva vencida
- **WHEN** pasan 10 minutos sin pagar
- **THEN** la reserva queda VENCIDA
- **AND** los lugares vuelven a LIBRE

### Requirement: Cancelación
Cuando el cliente dueño cancela una reserva PENDIENTE, el sistema SHALL responder 204 y liberar sus lugares; si no existe SHALL responder 404, si es de otro cliente 403 y si está CONFIRMADA 409.

#### Scenario: Reserva ajena
- **WHEN** otro cliente cancela la reserva
- **THEN** el sistema responde 403

#### Scenario: Confirmada
- **WHEN** se cancela una reserva CONFIRMADA
- **THEN** el sistema responde 409

### Requirement: Liberación idempotente
Si una reserva ya está VENCIDA o liberada, entonces el sistema SHALL ignorar una nueva liberación sin modificar el inventario.

#### Scenario: Doble liberación
- **WHEN** se libera dos veces la misma reserva
- **THEN** la disponibilidad aumenta una sola vez

### Requirement: Confirmación
Cuando ms-ventas confirma una reserva PENDIENTE antes de su vencimiento, el sistema SHALL pasar los lugares a VENDIDO; si ya venció, SHALL responder 410.

#### Scenario: A tiempo
- **WHEN** se confirma antes del vencimiento
- **THEN** los lugares pasan a VENDIDO

#### Scenario: Vencida
- **WHEN** se confirma después del vencimiento
- **THEN** el sistema responde 410
