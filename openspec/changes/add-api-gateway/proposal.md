# Proposal

## Why

Los clientes no deben conocer cada microservicio. Se necesita un punto de entrada único que enrute, oculte los endpoints internos y unifique las convenciones de errores e identidad que los cuatro servicios comparten.

## What Changes

- API Gateway con Spring Cloud Gateway como entrada única.
- Rutas hacia ms-eventos, ms-reservas y ms-ventas, quitando el prefijo /api.
- Bloqueo de /internal/** hacia el exterior.
- Reglas generales: identidad por headers, códigos HTTP y formato de error.

## Capabilities

### New Capabilities
- `enrutamiento-gateway`: rutas del API Gateway
- `convenciones-api`: identidad, códigos HTTP y formato de error

### Modified Capabilities

## Impact

- Servicio afectado: `api-gateway`; las convenciones valen para los cuatro servicios.
- Contrato en `docs/openapi.yaml`, rutas en `docs/arquitectura.md` y reglas en `docs/user-stories.md`.
- Fuera de alcance: JWT, rate limiting y resiliencia (Entrega 2).
