# Proposal

## Why

La funcionalidad 11 del enunciado (US-26, US-27 y US-28) pide avisar a los clientes por email o push cuando se confirma una compra, cuando un evento se cancela o cambia, y cuando un artista favorito publica un evento en su ciudad. La Entrega 2 además exige una base NoSQL. Hoy ningún servicio envía notificaciones, y US-26 exige que una falla al notificar no afecte la compra. Hace falta un servicio propio que reciba los pedidos, los guarde y los envíe con reintentos sin bloquear al que llama.

## What Changes

- Nuevo microservicio `ms-notificaciones` (puerto 8085, paquete `com.ticketya.notificaciones`) con la misma estructura y convenciones que los demás servicios.
- Nueva base MongoDB 7 (`db_notificaciones`) en un contenedor `mongo` del `docker-compose.yml`, con volumen, healthcheck y puerto 27017. La conexión se configura con `MONGO_URI` (por defecto apunta a `localhost`).
- `POST /internal/notificaciones` (solo entre servicios): recibe tipo, destinatario, datos libres, clave de deduplicación opcional y `X-Correlation-Id` opcional. Responde `202` y envía la notificación en segundo plano.
- Un documento por notificación con tipo, destinatario, datos, estado (`PENDIENTE`, `ENVIADA`, `FALLIDA`), cantidad de intentos, fechas y `correlationId`.
- Envío simulado que escribe el email en el log. Se puede configurar para que falle con ciertos destinatarios, para probar los reintentos.
- Reintentos con espera creciente y un máximo de intentos. Si se agotan, la notificación queda `FALLIDA`.
- Deduplicación: si el que llama manda una clave que ya existe, no se crea una notificación nueva. Lo garantiza un índice único en MongoDB.
- `GET /notificaciones` (solo `ADMIN`) con filtros por usuario, tipo y estado, y paginación. También `GET /notificaciones/{id}`.
- `Dockerfile`, `.dockerignore` y servicio `ms-notificaciones` en el compose, que espera a que `mongo` esté healthy.
- Contrato en `docs/openapi.yaml` y documentación en el README.

Fuera de alcance: no se modifican `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth` ni `api-gateway`. Las llamadas de esos servicios a `ms-notificaciones` y la ruta `/api/notificaciones` del gateway quedan como pedidos pendientes a sus responsables, con el contrato en `tasks.md`.

## Capabilities

### New Capabilities

- `notificaciones`: recepción, almacenamiento, envío simulado con reintentos, deduplicación y consulta administrativa de las notificaciones a clientes.

### Modified Capabilities

- `despliegue-contenedores`: el sistema suma el contenedor `mongo` y el servicio `ms-notificaciones`. Cambian la lista de imágenes, la cantidad de contenedores, el orden de arranque, las variables de conexión (`MONGO_URI`), los valores por defecto locales y los puertos publicados (8085 y 27017).

## Impact

- Código nuevo: carpeta `ms-notificaciones/` (pom, código, pruebas, `Dockerfile` y `.dockerignore`).
- Dependencias: `spring-boot-starter-data-mongodb`, y para las pruebas Testcontainers `mongodb` 1.21.4 con `spring-boot-testcontainers`.
- `docker-compose.yml`: servicios `mongo` y `ms-notificaciones`, y volumen `mongo-data`.
- `docs/openapi.yaml`: endpoints de notificaciones y sus schemas.
- `README.md` y `docs/arquitectura.md`: servicio, puerto, base y variable `MONGO_URI`.
- Pendientes de otros equipos: `ms-ventas` (compra confirmada), `ms-eventos` (cancelación, modificación y publicación por artista favorito) y `api-gateway` (ruta administrativa).
