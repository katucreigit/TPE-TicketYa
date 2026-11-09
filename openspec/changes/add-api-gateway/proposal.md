# Proposal

## Why

Los clientes no deben conocer cada microservicio. Se necesita un punto de entrada único que enrute, oculte los endpoints internos y unifique convenciones de errores e identidad.

## What Changes

- API Gateway con Spring Cloud Gateway en el puerto de entrada público.
- Reglas de enrutamiento hacia ms-eventos, ms-reservas y ms-ventas, quitando el prefijo /api.
- Bloqueo de las rutas /internal/** hacia el exterior.
- Convenciones comunes: formato de error, headers de identidad y paginación.

## Capabilities

### New Capabilities
- `enrutamiento-gateway`: rutas del API Gateway
- `convenciones-api`: formato de error, identidad y paginación

### Modified Capabilities

## Impact

- Servicio afectado: `api-gateway`; convenciones aplicables a todos los servicios.
- Contrato en `docs/openapi.yaml` y tabla de rutas en `docs/arquitectura.md`.
- Fuera de alcance: JWT, rate limiting y circuit breaker (Entrega 2).
