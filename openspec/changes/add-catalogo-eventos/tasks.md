# Tasks

## 1. Lugares y sectores

- [ ] 1.1 Crear entidades Lugar y Sector con enum de tipo de sector y validaciones.
- [ ] 1.2 Endpoints POST/GET de lugares y GET por id con sectores.
- [ ] 1.3 Validar capacidad positiva y nombres de sector únicos por lugar.

## 2. Eventos

- [ ] 2.1 Crear entidad Evento con enum de estados y transiciones válidas.
- [ ] 2.2 Endpoints de alta y edición de eventos (solo ORGANIZADOR dueño o ADMIN).
- [ ] 2.3 Endpoint de publicación con validaciones previas.
- [ ] 2.4 Endpoints /suspender, /reanudar y /cancelar.
- [ ] 2.5 Listado público de eventos PUBLICADO con paginación.

## 3. Transversal

- [ ] 3.1 Manejador global de errores con el formato común.
- [ ] 3.2 Verificación de roles por header en los controladores.
- [ ] 3.3 Datos de prueba (seed) de lugares y eventos.

## 4. Pruebas

- [ ] 4.1 Tests unitarios de transiciones de estado.
- [ ] 4.2 Tests de servicio para publicación y validaciones.
- [ ] 4.3 Tests de controlador para códigos 201, 400, 403, 404 y 409.
