# Proposal

## Why

La funcionalidad 10 del enunciado (US-24 y US-25) pide que el cliente registre sus preferencias y reciba recomendaciones de eventos según su historial y lo que compraron otros clientes. La Entrega 2 está casi completa y estas recomendaciones son lo que falta. Hoy ningún servicio guarda preferencias ni el historial de compras con un formato apto para recomendar. Esta propuesta es una versión simple y determinística, sin machine learning, que se puede terminar en el día.

## What Changes

- Nuevo microservicio `ms-recomendaciones` (puerto 8087, paquete `com.ticketya.recomendaciones`) con la estructura de `ms-notificaciones`: `Dockerfile`, `.dockerignore`, `application.yml` con valores por defecto de `localhost`, MongoDB (`db_recomendaciones` en el contenedor `mongo` existente) y trazabilidad con `X-Correlation-Id` como en `ms-reservas` y `ms-fila`.
- `PUT /preferencias` y `GET /preferencias` (rol `CLIENTE`, identificado por `X-User-Id`). El PUT reemplaza los tipos de evento, los artistas favoritos y la ciudad, con validación (`400 VALIDACION`). El GET devuelve listas vacías si el cliente no guardó nada.
- `GET /recomendaciones` (rol `CLIENTE`): eventos `PUBLICADO` ordenados por un puntaje que suma la coincidencia con las preferencias (tipo, artista, ciudad), las compras de clientes con historial parecido y la popularidad. Si el cliente no tiene preferencias ni historial, devuelve los más populares.
- `GET /recomendaciones/eventos/{eventoId}/tambien-compraron` (cualquier rol autenticado): "quienes compraron X también compraron Y", a partir de las compras registradas.
- `POST /internal/compras` (entre servicios): registra una compra confirmada (`compraId`, `clienteId`, `eventoIds`). Es idempotente por `compraId`.
- `ms-ventas`: al confirmar una compra avisa a `ms-recomendaciones` de forma best-effort. Usa un timeout corto, nunca hace fallar ni demora de más la compra y, si falla, registra una advertencia con el mismo `X-Correlation-Id`. Sigue el enfoque de `NotificacionesClient`.
- Catálogo: `ms-recomendaciones` lee los eventos de `ms-eventos` por HTTP, con una caché en memoria. Si `ms-eventos` no responde, usa lo último cacheado o, si no hay nada, una lista vacía. En los dos casos responde `200` con `"degradado": true`, nunca `500`.
- `api-gateway`: rutas `/api/preferencias/**` y `/api/recomendaciones/**` hacia `ms-recomendaciones`, con JWT obligatorio y circuit breaker como las demás. La prueba que cuenta las rutas pasa de 6 a 7.
- `docker-compose.yml`: servicio `ms-recomendaciones`, 14 contenedores en total. Documentación en `docs/openapi.yaml`, `README.md` y `docs/arquitectura.md`.

Fuera de alcance: machine learning, cambios en el frontend y la notificación de artista favorito en la ciudad (US-28, pertenece a notificaciones).

## Capabilities

### New Capabilities

- `preferencias-recomendaciones`: preferencias del cliente, registro de compras confirmadas, recomendaciones por puntaje, "también compraron", fallback del catálogo y acceso por el gateway.

### Modified Capabilities

- `despliegue-contenedores`: el sistema suma `ms-recomendaciones`. Cambian las imágenes, la cantidad de contenedores (14), el orden de arranque, las variables (`MS_RECOMENDACIONES_URL`, `MONGO_URI` del servicio nuevo), los valores por defecto locales y los puertos publicados (8087).

## Impact

- Código nuevo: carpeta `ms-recomendaciones/` (pom, código, pruebas, `Dockerfile`, `.dockerignore`).
- `ms-ventas`: nuevo `client/RecomendacionesClient` y una llamada en el mismo punto donde ya se notifica la compra confirmada. Hay propiedades nuevas y la integración se apaga en el perfil de prueba. Los constructores de `CompraService` que usan las pruebas no cambian.
- `api-gateway`: `application.yml` (ruta e instancias de circuit breaker y time limiter), la prueba de cantidad de rutas y pruebas nuevas de la ruta.
- `docker-compose.yml`, `docs/openapi.yaml`, `README.md` y `docs/arquitectura.md`.
- Sin cambios en `ms-eventos`: se usa su búsqueda pública `GET /eventos`.
