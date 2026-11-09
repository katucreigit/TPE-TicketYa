# devolucion-lugares Specification

## Purpose
Define cómo ms-reservas libera los lugares de una compra cuando se aprueba una devolución.

## Requirements

### Requirement: Devolución de asientos
Cuando ms-ventas devuelve asientos de una reserva CONFIRMADA, el sistema SHALL pasarlos a LIBRE, quitarlos de la reserva y dejar de contarlos para el límite del cliente.

#### Scenario: Asiento devuelto
- **WHEN** se devuelve un asiento vendido
- **THEN** queda LIBRE y disponible para otros
- **AND** ya no cuenta para el límite del cliente

### Requirement: Devolución de cupos
Cuando ms-ventas devuelve una cantidad de un sector no numerado, el sistema SHALL sumarla al cupo disponible y descontarla de la reserva, sin superar lo comprado.

#### Scenario: Cupo devuelto
- **WHEN** se devuelven 2 cupos de una reserva de 3
- **THEN** el cupo disponible sube 2
- **AND** la reserva conserva 1

### Requirement: Asiento ya devuelto
Si un asiento ya fue devuelto, entonces el sistema SHALL responder 200 sin liberarlo otra vez; si no pertenece a la reserva, SHALL responder 409.

#### Scenario: Repetición
- **WHEN** se repite la devolución del mismo asiento
- **THEN** el sistema responde 200 sin cambios

#### Scenario: Asiento ajeno
- **WHEN** el asiento no pertenece a la reserva
- **THEN** el sistema responde 409

### Requirement: Reserva inexistente o no confirmada
Si la reserva no existe, entonces el sistema SHALL responder 404; si no está CONFIRMADA, 409.

#### Scenario: Reserva pendiente
- **WHEN** se devuelve un lugar de una reserva PENDIENTE
- **THEN** el sistema responde 409
