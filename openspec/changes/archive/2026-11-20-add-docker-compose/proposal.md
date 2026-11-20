# Proposal: Despliegue con Docker Compose

## Why

En la Entrega 1 solo las bases PostgreSQL corren en contenedores y los cuatro servicios se ejecutan a mano desde el IDE. La Entrega 2 exige que cada microservicio se despliegue en su propio contenedor y que todo el sistema (3 bases, 3 microservicios y el gateway) se levante con un único `docker-compose.yml`.

## What Changes

- Agregar un `Dockerfile` multi-stage por servicio (`api-gateway`, `ms-eventos`, `ms-reservas`, `ms-ventas`): etapa de build con Maven y etapa de ejecución con JRE 21.
- Agregar un `.dockerignore` por servicio para no enviar `target/` ni archivos del IDE al contexto de build.
- Extender el `docker-compose.yml` existente con los cuatro servicios, manteniendo las tres bases y sus healthchecks:
  - cada microservicio espera a que su base esté saludable (`depends_on` con `service_healthy`);
  - el gateway arranca al final, después de los tres microservicios.
- Configurar la comunicación por nombre de servicio dentro de la red de compose mediante las variables ya documentadas (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `MS_EVENTOS_URL`, `MS_RESERVAS_URL`, `MS_VENTAS_URL`). Los `application.yml` ya usan `localhost` como valor por defecto, por lo que la ejecución local desde el IDE no cambia.
- Mantener los puertos publicados: 8080 (gateway), 8081–8083 (microservicios) y 5433–5435 (bases).
- Actualizar el README con el levantamiento completo, el levantamiento de solo las bases para desarrollo local y la ejecución de la colección de Postman contra los contenedores.

## Non-goals

- No se agregan JWT, resiliencia ni observabilidad (se tratan en sus propios cambios).
- No se agrega Spring Boot Actuator a los microservicios ni healthchecks HTTP para ellos.
- No se cambian contratos (`docs/openapi.yaml`), reglas de negocio ni tests.
- No se publican imágenes en un registry ni se configura CI/CD.

## Capabilities

### New Capabilities
- `despliegue-contenedores`: empaquetado de cada servicio como imagen Docker independiente y orquestación del sistema completo con Docker Compose (orden de arranque, red interna, configuración por entorno y puertos publicados).

### Modified Capabilities
- Ninguna.

## Impact

- Nuevos archivos: `api-gateway/Dockerfile`, `ms-eventos/Dockerfile`, `ms-reservas/Dockerfile`, `ms-ventas/Dockerfile` y sus `.dockerignore`.
- `docker-compose.yml`: pasa de "solo bases" a sistema completo.
- `README.md`: instrucciones de despliegue y de desarrollo local.
- Sin cambios en código Java, `application.yml`, contratos ni base de datos.
- `AGENTS.md` declara el alcance como Entrega 1; este cambio es de la Entrega 2 y fue pedido explícitamente. Se deja a criterio del equipo actualizar esa línea de alcance.
