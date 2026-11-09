# TicketYa

Plataforma de venta de entradas para eventos (TPE Arquitectura Web, TUDAI). Alcance actual: Entrega 1.

## Estructura
- `api-gateway`, `ms-eventos`, `ms-reservas`, `ms-ventas`: servicios Spring Boot independientes.
- `docs/`: historias de usuario, arquitectura, modelo de datos, secuencias y `openapi.yaml`.
- `openspec/`: especificaciones y cambios (fuente de verdad del comportamiento).

## Stack y comandos
- Java 21, Spring Boot 3.5.x, Maven, PostgreSQL 16.
- Bases de datos: `docker compose up -d` (puertos 5433, 5434 y 5435).
- Por servicio: `mvn -q compile` y `mvn test`.
- Paquete base: `com.ticketya.<servicio>`.

## Flujo de trabajo
1. El comportamiento se define en `openspec/changes/<cambio>/` antes de implementar.
2. Implementar siguiendo `tasks.md` y marcar cada tarea completada.
3. Validar con `openspec validate --all --strict` y archivar el cambio al terminar.

## Principios
- Los contratos viven en `docs/openapi.yaml`; no cambiarlos sin actualizar la spec.
- Las reglas de negocio llevan pruebas; nunca modificar un test para que pase.
- La garantía anti doble venta se resuelve en la base de datos, no en memoria.
- No subir credenciales ni archivos de entorno.
- No implementar nada de la Entrega 2 (JWT, resiliencia, observabilidad) todavía.
