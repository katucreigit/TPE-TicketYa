# Proposal

## Why

La funcionalidad 6 del enunciado pide que, en eventos de alta demanda, los clientes entren a una fila virtual al abrirse la venta, que el sistema les informe su posición y que los deje comprar en orden. Hoy todos los clientes llegan a `POST /reservas` al mismo tiempo. La anti doble venta impide vender dos veces un lugar, pero no ordena el acceso: gana quien tiene la conexión más rápida, y todo el pico de carga golpea la base de `ms-reservas`. Esta propuesta es una versión simplificada que cumple la funcionalidad sin rediseñar el flujo de compra.

## What Changes

- Nuevo microservicio `ms-fila` (puerto 8086, paquete `com.ticketya.fila`) con la estructura de `ms-notificaciones`: `Dockerfile`, `.dockerignore`, `application.yml` con valores por defecto de `localhost` y trazabilidad (`X-Correlation-Id`, registro de solicitudes y de operaciones críticas, como en `ms-reservas`).
- Nuevo contenedor `redis` (Redis 7) en `docker-compose.yml` con healthcheck y `start_period`. `ms-fila` arranca cuando `redis` está healthy. La conexión se configura con `REDIS_HOST` y `REDIS_PORT` (por defecto `localhost:6379`).
- `POST /fila/eventos/{eventoId}/activar` y `POST /fila/eventos/{eventoId}/desactivar` (roles `ADMIN` y `ORGANIZADOR`): activan la fila con un tamaño de tanda y los segundos entre tandas, o la desactivan. Se exponen por el gateway. Al definir el alcance se decidió no usar `/internal`, porque el gateway no enruta `/internal/**` y el organizador no podría usarlos.
- `POST /fila/eventos/{eventoId}/ingresar` (rol `CLIENTE`): el cliente entra a la fila y recibe su posición. Es idempotente: si ya está, devuelve el mismo lugar.
- `GET /fila/eventos/{eventoId}/posicion` (rol `CLIENTE`): posición, si está habilitado y hasta cuándo.
- `GET /internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado` (entre servicios): `{ activa, habilitado }`. Si la fila no está activa, responde `activa=false, habilitado=true`.
- Habilitación en orden de llegada, por tandas, con un proceso periódico que puede correr en varias instancias sin habilitar dos veces al mismo cliente. Cada habilitado tiene una ventana de compra (10 minutos por defecto). Si no compra en ese tiempo, pierde el turno.
- `ms-reservas`: antes de crear una reserva consulta a `ms-fila` el estado del cliente (timeout de 1 s, URL `MS_FILA_URL`, con `X-Correlation-Id` propagado). Si la fila está activa y el cliente no está habilitado, responde `403 FILA_NO_HABILITADO`. Si `ms-fila` no responde o falla, deja pasar la reserva (fail-open) y registra una advertencia. Para eventos sin fila activa el comportamiento no cambia.
- `api-gateway`: solo una ruta `/api/fila/**` hacia `ms-fila` (`MS_FILA_URL`), con circuit breaker y fallback igual que las demás.
- Contrato en `docs/openapi.yaml` (endpoints de la fila, código `FILA_NO_HABILITADO` en `POST /reservas`), y documentación en el `README.md` (puerto 8086, `MS_FILA_URL`) y en `docs/arquitectura.md`.

Fuera de alcance: interfaz en el frontend, verificar que el organizador sea dueño del evento, activar la fila automáticamente al publicar, controlar la fila en `POST /compras` y terminar el turno al comprar. Las simplificaciones están justificadas en `design.md`.

## Capabilities

### New Capabilities

- `fila-virtual`: activación y desactivación de la fila de un evento, ingreso idempotente, consulta de posición, habilitación por tandas con ventana de compra, vencimiento del turno, consulta interna de estado y acceso por el gateway.

### Modified Capabilities

- `reservas-temporales`: crear una reserva pasa a requerir que el cliente esté habilitado cuando el evento tiene la fila activa (`403 FILA_NO_HABILITADO`), con fail-open si `ms-fila` no está disponible.
- `despliegue-contenedores`: el sistema suma los contenedores `redis` y `ms-fila`. Cambian las imágenes, la cantidad de contenedores (13), el orden de arranque, las variables de conexión (`REDIS_HOST`, `REDIS_PORT`, `MS_FILA_URL`), los valores por defecto locales y los puertos publicados (8086 y 6379).

## Impact

- Código nuevo: carpeta `ms-fila/` (pom, código, pruebas, `Dockerfile`, `.dockerignore`).
- Dependencias de `ms-fila`: `spring-boot-starter-data-redis`. En las pruebas, Testcontainers 1.21.4 con un `GenericContainer` `redis:7-alpine`.
- `ms-reservas`: nuevo `client/FilaClient`, chequeo en `ReservaService.crear`, código `FILA_NO_HABILITADO` en `CodigoError`, propiedades `ticketya.services.fila-url` y `fila-timeout-ms`. Las pruebas existentes no se modifican.
- `api-gateway`: solo `src/main/resources/application.yml` (ruta `fila` e instancias `fila` del circuit breaker y del time limiter).
- `docker-compose.yml`: servicios `redis` y `ms-fila`, `MS_FILA_URL` en `ms-reservas` y `api-gateway`, y el gateway pasa a esperar también a `ms-fila`.
- Documentación: `docs/openapi.yaml`, `README.md` y `docs/arquitectura.md`.
