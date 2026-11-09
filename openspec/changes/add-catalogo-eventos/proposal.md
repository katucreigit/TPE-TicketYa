# Proposal

## Why

Sin un catálogo de lugares y eventos no hay nada que vender. Esta etapa crea la base de ms-eventos: lugares con sectores y capacidad, y eventos que el organizador crea, edita y publica para que queden visibles al público.

## What Changes

- Alta, consulta y edición de lugares con sus sectores (numerados o de entrada general) y capacidad.
- Alta y edición de eventos asociados a un lugar, con fecha, descripción, categoría y precio por sector.
- Ciclo de vida del evento: BORRADOR, PUBLICADO, SUSPENDIDO, CANCELADO y FINALIZADO, con transiciones válidas.
- Publicación de un evento (con validaciones previas) y listado público de eventos publicados.
- Formato de error común y control de rol por los headers X-User-Id y X-User-Rol.

## Capabilities

### New Capabilities
- `lugares-sectores`: alta y consulta de lugares con sectores y capacidad
- `eventos-publicacion`: alta, edición, estados y publicación de eventos

### Modified Capabilities

## Impact

- Servicio afectado: `ms-eventos` (base `eventos_db`, puerto 5433).
- Contrato: `docs/openapi.yaml` (tag Eventos y Lugares). Modelo: `docs/modelo-datos.md`.
- Historias de usuario: US-01 a US-08 en `docs/user-stories.md`.
- Fuera de alcance: búsqueda multicriterio (change `add-busqueda-eventos`), reservas y ventas.
