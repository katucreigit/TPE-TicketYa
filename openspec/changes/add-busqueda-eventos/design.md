# Design

## Context

La búsqueda vive en ms-eventos sobre su propia base. La disponibilidad no se replica: la informa ms-reservas.

## Goals / Non-Goals

**Goals:**
- Combinar filtros con AND y paginar de forma estable.
- Que ms-eventos nunca lea tablas de ms-reservas.

**Non-Goals:**
- Motores de búsqueda externos.
- Caché del catálogo (E2).

## Decisions

**Filtros.** tipo, organizadorId, participante, ciudad, fechaDesde, fechaHasta y q. Solo se consideran eventos PUBLICADO o AGOTADO.

**Texto q.** Se busca sin distinguir mayúsculas en nombre y descripción del evento, tipo y nombres de participantes.

**Relevancia.** Primero coincidencia en el nombre, luego en participante y por último solo en la descripción. Sin q equivale a ordenar por fecha.

**Orden estable.** Ante igual valor de orden se desempata por fecha y luego por id. El orden por precio usa el precio mínimo entre los sectores.

**Consulta dinámica.** Se arma con Specifications de JPA para combinar solo los filtros enviados, con índices en estado, fecha y tipo.

## Risks / Trade-offs

- Los datos de lugar y participantes se consultan por joins en la base de eventos, que puede crecer; los índices previstos alcanzan para esta entrega.
