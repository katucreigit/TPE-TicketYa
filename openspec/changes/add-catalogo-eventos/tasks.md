# Tasks

## 1. Lugares y sectores

- [x] 1.1 Entidades Lugar y Sector con tipo numerado o no numerado y validaciones.
- [x] 1.2 Endpoints de alta, listado paginado con filtro de ciudad y detalle.
- [x] 1.3 Validar sectores únicos por lugar, rangos de latitud y longitud y roles.

## 2. Eventos

- [x] 2.1 Entidad Evento con enum de estados y transiciones válidas.
- [x] 2.2 Alta de evento en BORRADOR con organizador tomado de X-User-Id.
- [x] 2.3 Edición de datos y configuración de precios por sector en BORRADOR.
- [x] 2.4 Campo maxEntradasPorCliente (mínimo 1, por defecto 4) en alta y edición, y envío del valor a ms-reservas al publicar.
- [x] 2.5 Publicación: validar precios, llamar a ms-reservas y manejar el 502.
- [x] 2.6 Si ms-reservas responde 409 al crear el inventario, tratarlo como ya inicializado y completar la publicación (reintento seguro).

## 3. Estados

- [x] 3.1 Endpoints de suspender, reanudar y cancelar (solo ADMIN).
- [x] 3.2 Endpoint interno para pasar a AGOTADO y volver a PUBLICADO.
- [x] 3.3 Job programado de finalización de eventos pasados.

## 4. Transversal

- [x] 4.1 Manejador global de errores con el formato común.
- [x] 4.2 Verificación de headers X-User-Id y X-User-Rol.
- [ ] 4.3 Datos de prueba de lugares y eventos.

## 5. Pruebas

- [x] 5.1 Tests unitarios de transiciones de estado.
- [x] 5.2 Tests de publicación (precios incompletos, ms-reservas caído, republicación).
- [x] 5.3 Tests de controlador para 201, 400, 403, 404, 409 y 422.
- [x] 5.4 Test del job de finalización repetido.
