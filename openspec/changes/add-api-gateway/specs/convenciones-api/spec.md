# Spec Delta

## Purpose
Define las convenciones que comparten todos los servicios para identidad, códigos HTTP y errores.

## ADDED Requirements

### Requirement: Headers de identidad
Si falta X-User-Id o X-User-Rol, o el rol no es CLIENTE, ORGANIZADOR, ADMIN o ACCESO, entonces el servicio SHALL responder 400.

#### Scenario: Sin header
- **WHEN** falta X-User-Id
- **THEN** el servicio responde 400

#### Scenario: Rol inválido
- **WHEN** X-User-Rol vale SUPERUSUARIO
- **THEN** el servicio responde 400

### Requirement: Rol incorrecto
Si el rol no corresponde a la operación, entonces el servicio SHALL responder 403.

#### Scenario: Cliente publica
- **WHEN** un CLIENTE intenta publicar un evento
- **THEN** el servicio responde 403

### Requirement: Códigos uniformes
El sistema SHALL usar en todos los servicios 200, 201, 204, 400, 402, 403, 404, 409, 410, 422, 502 y 503 o 504 con el significado definido en las reglas generales.

#### Scenario: Reserva vencida
- **WHEN** se opera sobre una reserva vencida
- **THEN** cualquier servicio responde 410

### Requirement: Formato de error
Cuando un servicio responde un error, el sistema SHALL incluir timestamp, status, error, code, message, path y details.

#### Scenario: Validación
- **WHEN** se envía un cuerpo inválido
- **THEN** la respuesta incluye code y details por campo

### Requirement: Paginación
Los listados SHALL aceptar page y size con un tamaño máximo, e incluir el total; un page o size inválido SHALL responder 400.

#### Scenario: Tamaño excesivo
- **WHEN** se pide un size mayor al máximo
- **THEN** se aplica el tope definido
