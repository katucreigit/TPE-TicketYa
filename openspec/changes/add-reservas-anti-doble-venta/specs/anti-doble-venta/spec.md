# Spec Delta

## Purpose
Garantiza que un lugar nunca quede asignado a más de una compra y que se respete el límite por cliente, aun con solicitudes simultáneas.

## ADDED Requirements

### Requirement: Un solo ganador
Cuando varias solicitudes simultáneas reservan el mismo asiento, el sistema SHALL permitir que exactamente una tenga éxito y rechace las demás con 409.

#### Scenario: Carrera por un asiento
- **WHEN** 20 solicitudes reservan el mismo asiento a la vez
- **THEN** una recibe 201
- **AND** las otras 19 reciben 409

### Requirement: Último cupo
Cuando varias solicitudes simultáneas piden el último lugar de un sector no numerado, el sistema SHALL otorgarlo a una sola y mantener el cupo mayor o igual a cero.

#### Scenario: Último lugar
- **WHEN** quedan 1 lugar y 10 solicitudes piden 1
- **THEN** una tiene éxito y 9 reciben 409
- **AND** el cupo queda en 0

### Requirement: Varias instancias
El sistema SHALL resolver el control de concurrencia en la base de datos, de modo que el resultado sea el mismo con varias instancias de ms-reservas.

#### Scenario: Dos instancias
- **WHEN** dos instancias compiten por un asiento
- **THEN** solo una lo obtiene

### Requirement: Sin bloqueos huérfanos
Si una operación de reserva falla a mitad de camino, entonces el sistema SHALL revertirla sin dejar lugares bloqueados.

#### Scenario: Falla parcial
- **WHEN** falla la reserva del segundo asiento
- **THEN** el primero queda LIBRE

### Requirement: Límite por cliente
El sistema SHALL contar para el límite las reservas PENDIENTE no vencidas, las entradas emitidas y las devoluciones aún no aprobadas, y responder 422 al superarlo, incluso con reservas simultáneas.

#### Scenario: Excede el límite
- **WHEN** un cliente con 3 entradas pide 2 más y el límite es 4
- **THEN** el sistema responde 422

#### Scenario: Reservas simultáneas
- **WHEN** el mismo cliente envía dos reservas de 3 a la vez con límite 4
- **THEN** a lo sumo una tiene éxito

### Requirement: Confirmar o vencer
Si una reserva ya fue confirmada, entonces el sistema SHALL ignorar su vencimiento; si ya venció, SHALL rechazar su confirmación.

#### Scenario: Carrera
- **WHEN** confirmación y vencimiento ocurren a la vez
- **THEN** solo una de las dos operaciones se aplica
