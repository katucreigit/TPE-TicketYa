# Spec Delta

## Purpose
Define cómo se crean, configuran y publican los eventos.

## ADDED Requirements

### Requirement: Creación en borrador
Cuando un ORGANIZADOR crea un evento con lugar y participantes existentes y fecha y hora futuras, el sistema SHALL guardarlo en BORRADOR con el organizador de X-User-Id y responder 201.

#### Scenario: Evento válido
- **WHEN** un ORGANIZADOR crea un evento válido
- **THEN** el sistema responde 201
- **AND** el estado es BORRADOR y el organizador es el de X-User-Id

#### Scenario: Organizador en el cuerpo
- **WHEN** el cuerpo trae otro organizadorId
- **THEN** se ignora y se usa X-User-Id

### Requirement: Datos inválidos
Si la fecha y hora no son futuras o el tipo está fuera del catálogo, entonces el sistema SHALL responder 400; si el lugar o un participante no existe, SHALL responder 404.

#### Scenario: Fecha pasada
- **WHEN** la fecha del evento ya pasó
- **THEN** el sistema responde 400

#### Scenario: Lugar inexistente
- **WHEN** el lugar no existe
- **THEN** el sistema responde 404

### Requirement: Edición en borrador
Mientras el evento esté en BORRADOR, el sistema SHALL permitir editarlo; si ya no está en BORRADOR, SHALL responder 409.

#### Scenario: Editar publicado
- **WHEN** se edita un evento PUBLICADO
- **THEN** el sistema responde 409

### Requirement: Precios por sector
Cuando el organizador dueño envía precios por sector de un evento en BORRADOR, el sistema SHALL guardarlos con precisión decimal; precios menores o iguales a 0, sectores ajenos al lugar o repetidos SHALL responder 400.

#### Scenario: Precio cero
- **WHEN** se envía un precio 0
- **THEN** el sistema responde 400

#### Scenario: Sector repetido
- **WHEN** el mismo sectorId aparece dos veces
- **THEN** el sistema responde 400

### Requirement: Publicación
Cuando el organizador dueño publica un evento en BORRADOR con precio válido en todos sus sectores vendibles, el sistema SHALL inicializar el inventario en ms-reservas una sola vez y pasarlo a PUBLICADO.

#### Scenario: Publicación exitosa
- **WHEN** se publica un evento con todos los precios
- **THEN** el estado pasa a PUBLICADO
- **AND** el inventario queda inicializado

#### Scenario: Falta un precio
- **WHEN** un sector vendible no tiene precio
- **THEN** el sistema responde 422
- **AND** el evento sigue en BORRADOR

### Requirement: Falla de ms-reservas
Si ms-reservas no responde al publicar, entonces el sistema SHALL responder 502 y mantener el evento en BORRADOR para poder reintentar sin duplicar el inventario.

#### Scenario: Reintento
- **WHEN** ms-reservas estaba caído y se vuelve a publicar
- **THEN** el evento pasa a PUBLICADO
- **AND** no se duplican los lugares

### Requirement: Publicación repetida y ajena
Si el evento ya está publicado, el sistema SHALL responder 409; si pertenece a otro organizador, SHALL responder 403.

#### Scenario: Ya publicado
- **WHEN** se publica de nuevo un evento PUBLICADO
- **THEN** el sistema responde 409

#### Scenario: Evento ajeno
- **WHEN** otro organizador intenta publicar
- **THEN** el sistema responde 403
