# Spec Delta

## Purpose
Define el detalle del evento, que combina datos de ms-eventos con la disponibilidad informada por ms-reservas.

## ADDED Requirements

### Requirement: Datos del evento
Cuando se consulta un evento publicado, el sistema SHALL devolver evento, lugar, participantes y sectores con precio; si el id no existe, SHALL responder 404.

#### Scenario: Evento inexistente
- **WHEN** se consulta un id que no existe
- **THEN** el sistema responde 404

### Requirement: Disponibilidad por sector
Cuando se consulta la disponibilidad, el sistema SHALL obtenerla de ms-reservas y mostrar por sector los lugares libres, y el estado de cada asiento en los sectores numerados.

#### Scenario: Mapa de asientos
- **WHEN** se consulta un sector numerado
- **THEN** se ve el estado de cada asiento

### Requirement: Separación de responsabilidades
El sistema SHALL obtener la disponibilidad solo mediante el endpoint de ms-reservas, sin que ms-eventos consulte las tablas de ese servicio.

#### Scenario: Origen del dato
- **WHEN** se arma el detalle
- **THEN** la disponibilidad proviene de ms-reservas por HTTP

### Requirement: Reservas no disponible
Si ms-reservas no responde, entonces el sistema SHALL responder 503 indicando el error de disponibilidad, sin informar lugares libres no verificados.

#### Scenario: Servicio caído
- **WHEN** ms-reservas no responde
- **THEN** el sistema responde 503
- **AND** no se muestran asientos como libres
