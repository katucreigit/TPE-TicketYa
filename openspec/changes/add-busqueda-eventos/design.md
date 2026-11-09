# Design

## Context

La búsqueda vive en ms-eventos sobre su propia base. La disponibilidad no se replica: se consulta a ms-reservas por endpoint interno.

## Goals / Non-Goals

**Goals:**
- Combinar filtros con AND y devolver resultados paginados.
- Responder rápido con índices en fecha, categoría y estado.

**Non-Goals:**
- Motores externos de búsqueda.
- Búsqueda geográfica.

## Decisions

**Consulta dinámica.** Se arma la consulta con Specifications de JPA para combinar solo los filtros enviados.

**Disponibilidad bajo demanda.** Para el filtro "solo con disponibilidad" se consulta a ms-reservas; si no responde, se devuelve el resultado sin ese dato y se marca como desconocido.

## Risks / Trade-offs

- Consultar disponibilidad por cada evento puede ser lento; se hace en lote para la página actual.
