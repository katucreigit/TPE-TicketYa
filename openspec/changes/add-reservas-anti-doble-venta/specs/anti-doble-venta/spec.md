# Spec Delta

## Purpose
Garantiza que un mismo asiento nunca quede asignado a más de una compra, aun con solicitudes simultáneas.

## ADDED Requirements

### Requirement: Un solo ganador
Cuando varias solicitudes concurrentes reservan el mismo asiento, el sistema SHALL permitir que exactamente una tenga éxito y rechace las demás con 409.

#### Scenario: Carrera por un asiento
- **WHEN** 20 hilos reservan el mismo asiento a la vez
- **THEN** exactamente una reserva es exitosa
- **AND** las otras 19 reciben 409

### Requirement: Sin sobreventa en general
El sistema SHALL mantener el cupo disponible de un sector general mayor o igual a cero.

#### Scenario: Cupo agotado
- **WHEN** quedan 2 lugares y 5 solicitudes piden 1 cada una
- **THEN** 2 tienen éxito y 3 reciben 409
- **AND** el cupo queda en 0

### Requirement: Confirmar o vencer
Si una reserva ya fue confirmada, entonces el sistema SHALL ignorar su vencimiento; si ya venció, SHALL rechazar su confirmación.

#### Scenario: Carrera confirmar y vencer
- **WHEN** confirmación y vencimiento ocurren a la vez
- **THEN** solo una de las dos operaciones se aplica
