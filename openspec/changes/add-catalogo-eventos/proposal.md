# Proposal

## Why

Sin un catálogo de lugares y eventos no hay nada que vender. Esta etapa crea la base de ms-eventos: lugares con sectores y eventos que el organizador crea, configura, publica y administra a lo largo de su ciclo de vida.

## What Changes

- Alta y consulta de lugares con sectores numerados o no numerados (US-01, US-02).
- Alta y edición de eventos en BORRADOR y configuración de precios por sector (US-03, US-04).
- Publicación del evento con inicialización del inventario en ms-reservas (US-05).
- Suspensión, reanudación y cancelación por el administrador (US-06).
- Pasaje automático a AGOTADO y a FINALIZADO (US-07, US-08).
- Reglas generales de identidad por headers, códigos HTTP y formato de error.

## Capabilities

### New Capabilities
- `lugares-sectores`: alta y consulta de lugares con sectores
- `eventos-publicacion`: creación, precios y publicación de eventos
- `eventos-estados`: suspensión, cancelación, agotado y finalización de eventos

### Modified Capabilities

## Impact

- Servicio afectado: `ms-eventos` (base `db_eventos`, puerto 5433).
- Contrato: `docs/openapi.yaml` (tags Lugares y Eventos). Modelo: `docs/modelo-datos.md`.
- Historias: US-01 a US-08 y las reglas generales de `docs/user-stories.md`.
- Fuera de alcance: búsqueda (change `add-busqueda-eventos`), reservas, ventas y notificaciones.
