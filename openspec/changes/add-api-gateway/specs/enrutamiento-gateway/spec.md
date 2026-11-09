# Spec Delta

## Purpose
Define cómo el API Gateway recibe las solicitudes públicas y las reenvía a cada microservicio.

## ADDED Requirements

### Requirement: Enrutamiento
Cuando llega una solicitud a /api/eventos, /api/reservas o /api/ordenes, el gateway SHALL reenviarla al servicio correspondiente sin el prefijo /api.

#### Scenario: Ruta de eventos
- **WHEN** llega GET /api/eventos
- **THEN** ms-eventos recibe GET /eventos

### Requirement: Rutas específicas primero
El gateway SHALL evaluar las rutas de disponibilidad y asientos antes que la ruta general de eventos.

#### Scenario: Disponibilidad
- **WHEN** llega GET /api/eventos/1/disponibilidad
- **THEN** se enruta a ms-reservas

### Requirement: Endpoints internos
Si una solicitud externa apunta a una ruta /internal/**, entonces el gateway SHALL rechazarla con 404.

#### Scenario: Acceso interno
- **WHEN** llega POST /api/internal/reservas
- **THEN** el gateway responde 404

### Requirement: Servicio caído
Si el servicio destino no responde, entonces el gateway SHALL responder 503 con el formato de error común.

#### Scenario: Destino caído
- **WHEN** ms-ventas no está disponible
- **THEN** el gateway responde 503
