# Proposal

## Why

La Entrega 1 identifica a los usuarios mediante los headers `X-User-Id` y `X-User-Rol`, sin autenticar su identidad. La Entrega 2 exige incorporar autenticación mediante JWT y autorización por roles para proteger los endpoints del sistema.

## What Changes

- Incorporar autenticación mediante tokens JWT válidos.
- Identificar al usuario y su rol a partir de la información contenida en el token.
- Incorporar autorización según los roles CLIENTE, ORGANIZADOR, ADMIN y ACCESO.
- Rechazar solicitudes sin token válido con HTTP 401.
- Rechazar solicitudes autenticadas que no tengan permisos para la operación con HTTP 403.
- Adaptar `api-gateway` y `ms-eventos` al nuevo mecanismo de identidad.
- Actualizar la documentación OpenAPI para describir el esquema de seguridad JWT.
- Definir el contrato de integración necesario para que los demás microservicios adopten el mismo mecanismo.

## Capabilities

### New Capabilities

- `user-auth`: autenticación de solicitudes mediante JWT, identificación del usuario y autorización basada en roles.

### Modified Capabilities

Ninguna. La autenticación es una capacidad nueva y transversal. La adaptación de los demás microservicios deberá coordinarse con sus responsables.

## Impact

- `api-gateway`: configuración de seguridad y validación de solicitudes.
- `ms-eventos`: protección de endpoints y obtención de identidad y rol desde el contexto autenticado.
- `docs/openapi.yaml`: documentación del esquema Bearer JWT y de los requisitos de seguridad.
- Dependencias de Spring Security y soporte JWT, según la estrategia tecnológica que se defina en el diseño.
- Contrato de seguridad compartido con `ms-reservas` y `ms-ventas`.
