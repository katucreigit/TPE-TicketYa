# Spec Delta

## Purpose
Define las convenciones que comparten todos los servicios para errores, identidad y paginación.

## ADDED Requirements

### Requirement: Formato de error
Cuando un servicio responde un error, el sistema SHALL incluir timestamp, status, error, code, message, path y details.

#### Scenario: Error de validación
- **WHEN** se envía un cuerpo inválido
- **THEN** la respuesta incluye code y details por campo

### Requirement: Identidad por headers
El sistema SHALL tomar el usuario y su rol de los headers X-User-Id y X-User-Rol; si faltan en un endpoint protegido, SHALL responder 401.

#### Scenario: Sin header
- **WHEN** falta X-User-Id en una compra
- **THEN** el sistema responde 401

### Requirement: Paginación
Los listados SHALL aceptar page y size, con un tamaño máximo; si size lo supera, SHALL aplicarse el máximo.

#### Scenario: Tamaño excesivo
- **WHEN** se pide size=10000
- **THEN** se aplica el tope definido
