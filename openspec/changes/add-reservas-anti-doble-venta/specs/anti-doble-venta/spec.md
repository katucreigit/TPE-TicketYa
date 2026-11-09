# Spec Delta

## Purpose
Garantiza que un lugar nunca quede asignado a más de una compra y que se respete el límite por cliente, aun con solicitudes simultáneas y varias instancias.

## ADDED Requirements

### Requirement: Un solo ganador
Cuando varias solicitudes simultáneas reservan el mismo asiento, el sistema SHALL permitir que exactamente una tenga éxito y rechazar las demás con 409.

#### Scenario: Carrera por un asiento
- **WHEN** 50 solicitudes reservan el mismo asiento a la vez
- **THEN** una recibe 201
- **AND** las otras 49 reciben 409

### Requirement: Último cupo
Cuando varias solicitudes simultáneas piden el último lugar de un sector no numerado, el sistema SHALL otorgarlo a una sola y mantener el cupo disponible mayor o igual a cero.

#### Scenario: Último lugar
- **WHEN** queda 1 lugar y 20 solicitudes piden 1
- **THEN** una tiene éxito y 19 reciben 409
- **AND** el cupo queda en 0 y nunca es negativo

### Requirement: Control en la base de datos
El sistema SHALL resolver la concurrencia con UPDATE condicionales y restricciones de la base, sin locks ni estructuras en memoria, de modo que el resultado sea el mismo con varias instancias.

#### Scenario: Dos instancias
- **WHEN** dos instancias compiten por un asiento
- **THEN** solo una lo obtiene

### Requirement: Sin bloqueos huérfanos
Si una operación de reserva falla a mitad de camino, entonces el sistema SHALL revertir la transacción completa sin dejar lugares bloqueados.

#### Scenario: Falla parcial
- **WHEN** falla la reserva del segundo asiento
- **THEN** el primero queda LIBRE

### Requirement: Cómputo del límite
El sistema SHALL contar para el límite del cliente las reservas PENDIENTE no vencidas y las unidades vendidas del evento, incluidas las de devoluciones aún no aprobadas, y no contar las reservas vencidas ni las unidades devueltas.

#### Scenario: Reserva vencida
- **WHEN** una reserva del cliente venció
- **THEN** sus lugares ya no cuentan para el límite

### Requirement: Superar el límite
Si la cantidad pedida más lo ya contado supera maxPorCliente del evento, entonces el sistema SHALL responder 422 LIMITE_POR_CLIENTE sin reservar nada.

#### Scenario: Excede el límite
- **WHEN** un cliente con 3 entradas pide 2 más y el límite es 4
- **THEN** el sistema responde 422

### Requirement: Límite con solicitudes simultáneas
Cuando el mismo cliente envía reservas simultáneas que juntas superan el límite, el sistema SHALL aceptar solo las que entren en el límite y rechazar las demás, serializando por cliente y evento en la base de datos.

#### Scenario: Dos reservas a la vez
- **WHEN** el mismo cliente envía dos reservas de 3 a la vez con límite 4
- **THEN** a lo sumo una tiene éxito

### Requirement: Confirmar o vencer
Si una reserva ya fue confirmada, entonces el sistema SHALL ignorar su vencimiento; si ya venció, SHALL rechazar su confirmación.

#### Scenario: Carrera
- **WHEN** confirmación y vencimiento ocurren a la vez
- **THEN** solo una de las dos operaciones se aplica
