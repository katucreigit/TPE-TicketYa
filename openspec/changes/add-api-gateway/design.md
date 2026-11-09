# Design

## Context

El gateway es la única puerta pública. El orden de las rutas importa: las de disponibilidad y asientos van antes de /api/eventos/**.

## Goals / Non-Goals

**Goals:**
- Un solo punto de entrada.
- Endpoints internos inaccesibles desde afuera.
- Respuestas de error homogéneas.

**Non-Goals:**
- Autenticación y autorización reales.
- Balanceo y descubrimiento de servicios.

## Decisions

**Prefijo /api.** El gateway quita /api y reenvía al servicio correspondiente.

**Orden de rutas.** Las rutas más específicas (disponibilidad, asientos) se declaran antes que /api/eventos/** para que no queden tapadas.

**Formato de error.** Todos los servicios responden {timestamp, status, error, code, message, path, details}.

## Risks / Trade-offs

- Si dos rutas se superponen, una puede quedar inalcanzable; se prueba cada ruta.
