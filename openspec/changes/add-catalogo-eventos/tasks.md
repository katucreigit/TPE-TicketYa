# Tasks

## 1. Lugares y sectores

- [ ] 1.1 Entidades Lugar y Sector con tipo numerado o no numerado y validaciones.
- [ ] 1.2 Endpoints de alta, listado paginado con filtro de ciudad y detalle.
- [ ] 1.3 Validar sectores únicos por lugar, rangos de latitud y longitud y roles.

## 2. Eventos

- [ ] 2.1 Entidad Evento con enum de estados y transiciones válidas.
- [ ] 2.2 Alta de evento en BORRADOR con organizador tomado de X-User-Id.
- [ ] 2.3 Edición de datos y configuración de precios por sector en BORRADOR.
- [ ] 2.4 Campo maxEntradasPorCliente (mínimo 1, por defecto 4) en alta y edición, y envío del valor a ms-reservas al publicar.
- [ ] 2.5 Publicación: validar precios, llamar a ms-reservas y manejar el 502.
- [ ] 2.6 Si ms-reservas responde 409 al crear el inventario, tratarlo como ya inicializado y completar la publicación (reintento seguro).

## 3. Estados

- [ ] 3.1 Endpoints de suspender, reanudar y cancelar (solo ADMIN).
- [ ] 3.2 Endpoint interno para pasar a AGOTADO y volver a PUBLICADO.
- [ ] 3.3 Job programado de finalización de eventos pasados.

## 4. Transversal

- [ ] 4.1 Manejador global de errores con el formato común.
- [ ] 4.2 Verificación de headers X-User-Id y X-User-Rol.
- [ ] 4.3 Datos de prueba de lugares y eventos.

## 5. Pruebas

- [ ] 5.1 Tests unitarios de transiciones de estado.
- [ ] 5.2 Tests de publicación (precios incompletos, ms-reservas caído, republicación).
- [ ] 5.3 Tests de controlador para 201, 400, 403, 404, 409 y 422.
- [ ] 5.4 Test del job de finalización repetido.
