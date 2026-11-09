# Spec Delta

## Purpose
Define el ciclo de vida de los eventos y las reglas para crearlos, editarlos y publicarlos al público.

## ADDED Requirements

### Requirement: Creación de evento en borrador
Cuando un ORGANIZADOR crea un evento sobre un lugar existente con fecha futura, el sistema SHALL guardarlo en estado BORRADOR y responder 201.

#### Scenario: Evento válido
- **WHEN** un ORGANIZADOR crea un evento con lugar existente y fecha futura
- **THEN** el sistema responde 201
- **AND** el estado es BORRADOR

#### Scenario: Fecha pasada
- **WHEN** la fecha del evento es anterior a hoy
- **THEN** el sistema responde 400

### Requirement: Precio por sector
Mientras un evento esté en BORRADOR, el sistema SHALL permitir definir y modificar el precio de cada sector; el precio SHALL ser mayor o igual a cero.

#### Scenario: Precio negativo
- **WHEN** se envía un precio negativo
- **THEN** el sistema responde 400

### Requirement: Publicación
Cuando el organizador dueño publica un evento en BORRADOR con precio definido en todos sus sectores, el sistema SHALL pasarlo a PUBLICADO.

#### Scenario: Publicación exitosa
- **WHEN** se publica un evento completo
- **THEN** el estado pasa a PUBLICADO

#### Scenario: Publicación incompleta
- **WHEN** falta el precio de un sector
- **THEN** el sistema responde 409 con code EVENTO_INCOMPLETO

### Requirement: Transiciones válidas
Si se solicita una transición de estado no permitida, entonces el sistema SHALL responder 409 sin modificar el evento.

#### Scenario: Cancelado no se publica
- **WHEN** se intenta publicar un evento CANCELADO
- **THEN** el sistema responde 409
- **AND** el estado no cambia

### Requirement: Suspensión y reanudación
Cuando un evento PUBLICADO se suspende, el sistema SHALL pasarlo a SUSPENDIDO; al reanudarlo SHALL volver a PUBLICADO.

#### Scenario: Suspender y reanudar
- **WHEN** se suspende un evento PUBLICADO
- **THEN** el estado es SUSPENDIDO
- **AND** al reanudar vuelve a PUBLICADO

### Requirement: Listado público
Cuando un usuario consulta el catálogo, el sistema SHALL devolver únicamente eventos PUBLICADO, paginados.

#### Scenario: Borradores ocultos
- **WHEN** existen eventos en BORRADOR y PUBLICADO
- **THEN** el listado solo incluye los PUBLICADO

### Requirement: Propiedad del evento
Si un ORGANIZADOR intenta modificar un evento de otro organizador, entonces el sistema SHALL responder 403.

#### Scenario: Evento ajeno
- **WHEN** un organizador edita un evento que no creó
- **THEN** el sistema responde 403
