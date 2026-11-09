# Spec Delta

## Purpose
Define cómo se registran y consultan los lugares con sus sectores y capacidades, base para publicar eventos.

## ADDED Requirements

### Requirement: Alta de lugar con sectores
Cuando un ORGANIZADOR o ADMIN envía un lugar con al menos un sector, el sistema SHALL crear el lugar y sus sectores y responder 201 con el identificador.

#### Scenario: Lugar válido
- **WHEN** un ORGANIZADOR envía un lugar con dos sectores válidos
- **THEN** el sistema responde 201 con el lugar creado
- **AND** los sectores quedan asociados al lugar

#### Scenario: Lugar sin sectores
- **WHEN** se envía un lugar sin sectores
- **THEN** el sistema responde 400 con detalle del campo faltante

### Requirement: Capacidad y tipo de sector
Si un sector tiene capacidad menor o igual a cero o no indica su tipo (numerado o general), entonces el sistema SHALL rechazar la solicitud con 400.

#### Scenario: Capacidad inválida
- **WHEN** se envía un sector con capacidad 0
- **THEN** el sistema responde 400

### Requirement: Consulta de lugar
Cuando se consulta un lugar existente, el sistema SHALL devolver sus datos y sectores; si no existe, SHALL responder 404.

#### Scenario: Lugar inexistente
- **WHEN** se consulta un id que no existe
- **THEN** el sistema responde 404 con code LUGAR_NO_ENCONTRADO

### Requirement: Permiso de alta
Si el rol del solicitante es CLIENTE, entonces el sistema SHALL rechazar el alta de lugares con 403.

#### Scenario: Cliente intenta crear
- **WHEN** X-User-Rol es CLIENTE y se hace POST de un lugar
- **THEN** el sistema responde 403
