# Design: Despliegue con Docker Compose

## Context

Motivación en `proposal.md`; requisitos en `specs/despliegue-contenedores/spec.md`.

Estado actual observado:

- `docker-compose.yml` define solo `db-eventos`, `db-reservas` y `db-ventas` (`postgres:16-alpine`, usuario/clave `ticketya`), con healthcheck `pg_isready` y volúmenes nombrados.
- Los cuatro `application.yml` ya leen `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MS_EVENTOS_URL`, `MS_RESERVAS_URL` y `MS_VENTAS_URL` con valores por defecto en `localhost`. No hace falta tocar configuración Java.
- Cada servicio es un proyecto Maven independiente (Spring Boot 3.5.7, Java 21), sin Maven Wrapper.
- Solo `api-gateway` tiene Actuator; los microservicios no exponen un endpoint de salud.
- Los tests de `ms-reservas` usan Testcontainers (necesitan un daemon Docker).
- La colección de Postman usa `baseUrl = http://localhost:8080/api`.

## Goals / Non-Goals

**Goals:**
- Imágenes reproducibles por servicio construidas solo con Docker.
- Un único `docker-compose.yml` que sirva tanto para el sistema completo como para levantar solo las bases.

**Non-Goals:**
- Healthchecks HTTP de los microservicios, reintentos o circuit breakers (resiliencia/observabilidad van en otros cambios).
- Optimizar el tamaño de imagen más allá de usar una JRE alpine.
- Perfiles de Spring específicos para Docker.

## Decisions

### D1. Dockerfile multi-stage idéntico en estructura para los cuatro servicios
- Etapa `build`: `maven:3.9-eclipse-temurin-21`. Copia primero `pom.xml` y ejecuta `mvn -B dependency:go-offline` para cachear dependencias en una capa propia; luego copia `src/` y ejecuta `mvn -B -DskipTests package`.
- Etapa final: `eclipse-temurin:21-jre-alpine`, copia el jar de `target/` como `app.jar`, `EXPOSE` del puerto del servicio y `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.
- Contexto de build = directorio del servicio, así cada imagen se construye por separado (`docker build -t ticketya/ms-eventos ms-eventos`).
- *Alternativas*: un Dockerfile único parametrizado en la raíz (acopla los servicios y contradice el despliegue independiente); Jib o `spring-boot:build-image` (agrega plugin al `pom.xml` o requiere Docker dentro del build; menos explícito para la cátedra).

### D2. Tests fuera del build de la imagen
El build de la imagen usa `-DskipTests`. Los tests siguen corriéndose con `mvn test` como hoy. Motivo: los tests con Testcontainers necesitan acceso a un daemon Docker, que no está disponible dentro de `docker build`. No se modifica ningún test.

### D3. Copia del jar sin depender del nombre de versión
Para no acoplar el Dockerfile a `<version>` del `pom.xml`, la etapa de build renombra el jar ejecutable (`target/*.jar` excluyendo `*-plain.jar` / `*.original`) a `app.jar` antes de copiarlo. Spring Boot 3.5 genera un único jar repackaged en `target/` con `spring-boot-maven-plugin`; se verifica en la tarea correspondiente.

### D4. Compose: un solo archivo, servicios agregados al existente
Se conserva todo lo actual (bases, healthchecks, volúmenes, `container_name`) y se agregan `ms-eventos`, `ms-reservas`, `ms-ventas` y `api-gateway` con `build: ./<servicio>` y `image: ticketya/<servicio>`. Con un solo archivo, `docker compose up -d db-eventos db-reservas db-ventas` sigue sirviendo para el desarrollo local desde el IDE.
*Alternativa*: `docker-compose.override.yml` o perfiles de Compose. Se descarta para cumplir literalmente "un único docker-compose.yml" y mantener el comando simple.

### D5. Orden de arranque
- Cada microservicio: `depends_on: { db-<x>: { condition: service_healthy } }`.
- `api-gateway`: `depends_on` sobre los tres microservicios con `condition: service_started`.
- No se exige que los microservicios estén "healthy" porque no tienen Actuator y agregarlo es observabilidad (fuera de alcance). El gateway resuelve rutas por solicitud, así que arrancar antes de que los microservicios terminen de inicializarse no rompe nada; las solicitudes de los primeros segundos pueden devolver 5xx.
- Las llamadas entre microservicios (`/internal/**`) ocurren en tiempo de solicitud, no al arrancar, por lo que no se agregan dependencias entre microservicios.

### D6. Variables de entorno en Compose
| Servicio | Variables |
|---|---|
| `ms-eventos` | `DB_URL=jdbc:postgresql://db-eventos:5432/db_eventos`, `DB_USER`, `DB_PASSWORD`, `MS_RESERVAS_URL=http://ms-reservas:8082`, `MS_VENTAS_URL=http://ms-ventas:8083` |
| `ms-reservas` | `DB_URL=jdbc:postgresql://db-reservas:5432/db_reservas`, `DB_USER`, `DB_PASSWORD`, `MS_EVENTOS_URL=http://ms-eventos:8081` |
| `ms-ventas` | `DB_URL=jdbc:postgresql://db-ventas:5432/db_ventas`, `DB_USER`, `DB_PASSWORD`, `MS_EVENTOS_URL=http://ms-eventos:8081`, `MS_RESERVAS_URL=http://ms-reservas:8082` |
| `api-gateway` | `MS_EVENTOS_URL=http://ms-eventos:8081`, `MS_RESERVAS_URL=http://ms-reservas:8082`, `MS_VENTAS_URL=http://ms-ventas:8083` |

`DB_USER`/`DB_PASSWORD` usan `ticketya`, los mismos valores de desarrollo que ya están en el compose y en los defaults de `application.yml`; no se agrega `.env` (está en `.gitignore` y no se suben archivos de entorno). Se usa la red por defecto de Compose; no se declara red propia.

### D7. Puertos
Se publican `8080:8080`, `8081:8081`, `8082:8082`, `8083:8083`; las bases mantienen `5433–5435:5432`. Internamente los servicios se hablan por el puerto del contenedor (5432 para las bases).

### D8. `.dockerignore` por servicio
Excluye `target/`, `.idea/`, `*.iml`, `.vscode/` y `*.log` para que el contexto sea chico y el build no dependa de artefactos locales.

## Risks / Trade-offs

- [El gateway arranca antes de que los microservicios acepten conexiones] → El README indica esperar a que los logs muestren `Started` (o a que `GET /api/eventos` responda) antes de correr Postman. Healthchecks HTTP quedan para el cambio de observabilidad.
- [Conflicto de puertos si los servicios siguen corriendo en el IDE] → Documentar que hay que detener las ejecuciones locales antes de `docker compose up` completo.
- [Datos de corridas anteriores en los volúmenes hacen fallar el flujo de Postman (p. ej. límites por cliente)] → Para la verificación se usa `docker compose down -v` antes de levantar, y el README lo menciona.
- [Huso horario del contenedor en UTC] → Ya está cubierto por la configuración existente: `hibernate.jdbc.time_zone: UTC`, sesión `SET TIME ZONE 'UTC'` en `ms-reservas` y zona explícita `America/Argentina/Buenos_Aires` para devoluciones en `ms-ventas`. No se cambia.
- [Primer build lento por descarga de dependencias Maven] → Capa de `dependency:go-offline` separada para cachear entre builds.

## Migration Plan

1. Agregar Dockerfiles y `.dockerignore`; verificar `docker build` de cada servicio por separado.
2. Extender `docker-compose.yml`; verificar levantamiento completo y Postman 15/15.
3. Actualizar README.

Rollback: el cambio es aditivo; volver a la versión anterior de `docker-compose.yml` restaura el modo "solo bases". La ejecución desde el IDE no se ve afectada en ningún momento.
