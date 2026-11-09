# Spec Delta

## Purpose
Define cómo se crea y consulta el inventario de asientos y cupos de cada evento.

## ADDED Requirements

### Requirement: Inicialización
Cuando ms-eventos publica un evento, el sistema SHALL crear un asiento LIBRE por cada ubicación de los sectores numerados y un cupo por sector no numerado, sin duplicar si se repite la llamada.

#### Scenario: Publicación
- **WHEN** se inicializa un evento con un sector de 100 asientos
- **THEN** existen 100 asientos LIBRE

#### Scenario: Reintento
- **WHEN** la inicialización se repite
- **THEN** no se duplican los asientos

### Requirement: Unicidad de asiento
El sistema SHALL impedir dos asientos con el mismo evento, sector, fila y número.

#### Scenario: Duplicado
- **WHEN** se intenta insertar un asiento repetido
- **THEN** la base lo rechaza

### Requirement: Disponibilidad
Cuando se consulta la disponibilidad de un evento, el sistema SHALL devolver por sector la cantidad de lugares libres y, en sectores numerados, el estado de cada asiento.

#### Scenario: Con reservas activas
- **WHEN** hay asientos reservados y vendidos
- **THEN** los libres no los incluyen

### Requirement: Cierre del inventario
Cuando un evento se suspende o se cancela, el sistema SHALL rechazar nuevas reservas, y al reanudarlo SHALL volver a aceptarlas.

#### Scenario: Evento suspendido
- **WHEN** se cierra el inventario
- **THEN** reservar responde 409
