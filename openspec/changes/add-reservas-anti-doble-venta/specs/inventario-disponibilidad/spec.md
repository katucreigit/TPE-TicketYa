# Spec Delta

## Purpose
Define cómo se representa y consulta el inventario de asientos y cupos de cada evento.

## ADDED Requirements

### Requirement: Generación de inventario
Cuando ms-eventos publica un evento, el sistema SHALL generar un asiento LIBRE por cada ubicación de los sectores numerados y un cupo para los sectores generales.

#### Scenario: Publicación genera asientos
- **WHEN** se publica un evento con un sector numerado de 100 asientos
- **THEN** existen 100 asientos LIBRE para ese evento

### Requirement: Unicidad de asiento
El sistema SHALL impedir dos asientos con la misma combinación de evento, sector, fila y número.

#### Scenario: Duplicado
- **WHEN** se intenta insertar un asiento repetido
- **THEN** la base rechaza la inserción

### Requirement: Consulta de disponibilidad
Cuando se consulta la disponibilidad de un evento, el sistema SHALL devolver por sector la cantidad de asientos libres.

#### Scenario: Disponibilidad por sector
- **WHEN** se consulta un evento con reservas activas
- **THEN** los libres excluyen los reservados y vendidos
