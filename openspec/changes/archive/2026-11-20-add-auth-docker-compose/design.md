# Design: ms-auth y db-auth en Docker Compose

## Context

Motivación en `proposal.md`; requisitos en `specs/despliegue-contenedores/spec.md`. El diseño base del despliegue (Dockerfile multi-stage, un solo compose, orden de arranque con `depends_on`) está en el cambio archivado `2026-11-20-add-docker-compose`; acá solo se describe lo que agrega `ms-auth`.

Estado observado tras el merge con `origin/main`:

- `ms-auth` es un proyecto Maven independiente (Spring Boot 3.5.7, Java 21) en el puerto 8084, con base `db_auth` en 5436.
- Su `application.yml` traía la URL de la base y `jwt.secret` escritos directamente, sin variables de entorno.
- El gateway lee `jwt.secret` (`JwtAuthFilter`) y enruta `/api/auth/**` a `${MS_AUTH_URL:http://localhost:8084}`. `ms-auth` firma los tokens con `jwt.secret` (`JwtService`). Ambos decodifican el secreto como Base64 para una clave HMAC.
- `GET /api/eventos` y `/api/lugares` son públicas en el filtro; para verificar el JWT hace falta una ruta protegida (por ejemplo `/api/entradas`).

## Goals / Non-Goals

**Goals:**
- Que `ms-auth` se despliegue igual que los demás servicios y que el JWT funcione entre contenedores.

**Non-Goals:**
- Gestión de secretos (Docker secrets, vault); se usa una variable de entorno.
- Arreglar la colección de Postman o el test de rutas del gateway (ver `proposal.md`, Non-goals).

## Decisions

### D1. Dockerfile de ms-auth idéntico al de los demás
Misma estructura multi-stage, `EXPOSE 8084`, mismo `.dockerignore`. Mantiene una sola forma de construir imágenes.

### D2. Placeholders con valores por defecto en ms-auth
`url: ${DB_URL:jdbc:postgresql://localhost:5436/db_auth}`, `username: ${DB_USER:ticketya}`, `password: ${DB_PASSWORD:ticketya}`, igual que los otros microservicios.
*Alternativa*: usar el binding de Spring (`SPRING_DATASOURCE_URL`) sin tocar el yml. Se descarta porque rompe la convención de variables documentada en el README (`DB_URL`, etc.).

### D3. Secreto JWT por `JWT_SECRET`, con la clave de desarrollo como valor por defecto
- `jwt.secret: ${JWT_SECRET:<clave de desarrollo>}` en el gateway y en `ms-auth`, con el mismo valor que ya traía main.
- En el compose: `JWT_SECRET: ${JWT_SECRET:-<clave de desarrollo>}` en ambos servicios. Compose toma el valor del shell o de un `.env` (que está en `.gitignore`); si no existe, usa la clave de desarrollo. Así el sistema levanta sin configuración y no se versiona ningún secreto real.
- *Alternativas*: `env_file` obligatorio (falla si no existe el `.env` y obliga a un paso manual, contra el requisito de un solo comando); repetir la clave solo en el compose sin default en los yml (rompe la ejecución desde el IDE).

### D4. Orden de arranque
`ms-auth` depende de `db-auth` con `service_healthy`. El gateway suma `ms-auth` a su `depends_on` con `service_started`, igual que los otros microservicios (sin Actuator no hay healthcheck HTTP).

## Risks / Trade-offs

- [La clave de desarrollo está en el repositorio] → Ya estaba en main; es explícitamente de desarrollo y se documenta cómo sobreescribirla. Un despliegue real debe definir `JWT_SECRET`.
- [Si alguien define `JWT_SECRET` solo para uno de los dos servicios, el gateway rechaza todos los tokens] → En compose ambos leen la misma variable del entorno; el README indica que tienen que coincidir.
- [`ms-auth` tarda en arrancar (~85 s en la máquina de prueba) y el gateway puede responder 503 al principio] → Mismo riesgo y mitigación que en el cambio base: esperar a que los logs muestren `Started`.

## Migration Plan

La implementación ya está en el working tree desde la resolución del merge. Se verifica con las tareas y, al cerrar el merge, se commitea junto con él. Rollback: revertir el commit del merge.
