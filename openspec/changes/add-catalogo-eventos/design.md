# Design

## Context

Ver `proposal.md` para la motivación y los archivos de `specs/` para los requisitos funcionales.

El sistema está organizado en microservicios Spring Boot independientes:

- `ms-eventos` (puerto 8081) administra lugares, sectores y eventos. Utiliza PostgreSQL.
- `ms-reservas` (puerto 8082) administra inventarios y disponibilidad.
- `ms-ventas` (puerto 8083) administra compras y debe recibir las cancelaciones de eventos.
- `api-gateway` (puerto 8080) es el punto de entrada de las solicitudes públicas.

Cada microservicio administra su propia base de datos. Las referencias entre servicios son identificadores lógicos y no claves foráneas entre bases.

La comunicación entre servicios se realiza mediante API REST. Las rutas internas no se exponen a través del Gateway.

## Goals / Non-Goals

**Goals:**

- Mantener la responsabilidad de lugares, sectores y eventos dentro de `ms-eventos`.
- Mantener la propiedad del inventario dentro de `ms-reservas`.
- Mantener la responsabilidad de compras y devoluciones dentro de `ms-ventas`.
- Definir las interacciones necesarias para publicar, suspender, reanudar y cancelar eventos.
- Mantener compatibles las rutas públicas existentes a través del Gateway.

**Non-Goals:**

- Implementar la lógica interna de reservas o ventas.
- Incorporar una base de datos compartida entre microservicios.
- Modificar el mecanismo de autenticación existente en esta capacidad.
- Incorporar nuevos servicios o patrones arquitectónicos que no sean necesarios para el catálogo.

## Decisions

### 1. Mantener la propiedad de los datos por microservicio

`ms-eventos` es la única fuente de verdad para lugares, sectores, eventos, participantes, precios y estados de los eventos.

`ms-reservas` mantiene la información de inventario y disponibilidad. `ms-ventas` mantiene las compras y entradas.

**Alternativas consideradas:**
- Compartir una base de datos entre servicios: descartada porque genera acoplamiento y permite que un servicio modifique datos que pertenecen a otro.
- Duplicar las bases de datos completas: descartada porque genera inconsistencias y dificulta mantener una fuente de verdad.

### 2. Utilizar API REST síncrona para las integraciones

`ms-eventos` utiliza llamadas REST a `ms-reservas` para inicializar, cerrar y reabrir inventarios.

Cuando se cancela un evento, `ms-eventos` informa la cancelación a `ms-ventas` para que ese servicio gestione las operaciones correspondientes sobre las compras.

Cada servicio conserva sus propias reglas de negocio y responde por sus propios datos.

**Alternativas consideradas:**
- Acceder directamente a la base de datos de otro microservicio: descartada porque rompe el aislamiento de datos.
- Incorporar mensajería asíncrona para estas operaciones: no se incorpora en esta capacidad porque la implementación existente utiliza REST síncrono.

### 3. Mantener el API Gateway como punto de entrada público

Las solicitudes públicas llegan a `api-gateway` mediante rutas bajo `/api`. El Gateway elimina ese prefijo y reenvía la solicitud al microservicio correspondiente.

Las rutas específicas de disponibilidad de reservas deben tener prioridad sobre la ruta general de eventos. Las rutas `/internal/**` no deben exponerse públicamente.

**Alternativas consideradas:**
- Permitir que los clientes invoquen directamente cada microservicio: descartada porque elimina el punto de entrada único.
- Exponer las rutas internas a través del Gateway: descartada por razones de aislamiento y seguridad.

### 4. Mantener las transiciones de estado dentro de ms-eventos

`ms-eventos` valida las transiciones permitidas y conserva el estado del evento.

La publicación requiere que los sectores del lugar tengan sus precios configurados y que `ms-reservas` inicialice el inventario. Si reservas informa que el inventario ya existe (HTTP 409), se considera una inicialización previamente realizada y se puede completar la publicación.

La suspensión y reanudación coordinan el cambio de estado con el cierre o reapertura del inventario. La cancelación requiere permisos de ADMIN, un motivo y la notificación a `ms-ventas`.

**Alternativas consideradas:**
- Permitir que reservas modifique directamente los estados de los eventos: descartada porque el estado del evento pertenece a `ms-eventos`.
- Centralizar todas las transiciones en el Gateway: descartada porque el Gateway no debe contener reglas de negocio.

### 5. Mantener la persistencia relacional existente

Los datos de lugares, sectores y eventos se almacenan en PostgreSQL, utilizando el esquema relacional propio de `ms-eventos`.

La estructura existente de entidades y relaciones se mantiene. Los cambios de esquema necesarios se gestionan dentro de la base de datos de este microservicio.

**Alternativas consideradas:**
- Incorporar MongoDB a `ms-eventos`: no se justifica para los datos relacionales de esta capacidad.
- Utilizar una base compartida con reservas o ventas: descartada por el aislamiento entre servicios.

## Risks / Trade-offs

- **Falla de ms-reservas durante la publicación** → No completar la publicación y devolver el error de integración correspondiente (502).
- **Inventario ya inicializado por un intento anterior** → Tratar la respuesta 409 como una inicialización ya realizada para permitir un reintento seguro.
- **Falla de comunicación entre servicios durante una transición** → Informar el error y evitar considerar exitosa una operación cuya integración requerida no pudo completarse.
- **Falla al notificar una cancelación a ms-ventas** → La cancelación y la gestión de devoluciones involucran bases de datos independientes; no existe una transacción distribuida automática. La recuperación de notificaciones fallidas debe contemplarse en la estrategia general de consistencia del sistema.
- **Cambios en el modelo de datos** → Aplicar los cambios únicamente en la base de `ms-eventos` y verificar su compatibilidad con los datos existentes.

## Migration Plan

1. Conservar la estructura actual de `ms-eventos` y su base PostgreSQL.
2. Aplicar los cambios de esquema necesarios de forma compatible con los datos existentes.
3. Verificar la configuración de las URLs de `ms-reservas` y `ms-ventas` mediante variables de entorno.
4. Verificar las rutas públicas del Gateway y que las rutas internas no sean accesibles desde él.
5. Ejecutar las pruebas unitarias y de integración de lugares, eventos y transiciones de estado.
6. Desplegar la versión actualizada de `ms-eventos` y comprobar las integraciones con los otros servicios.

**Rollback:** volver a desplegar la versión anterior de `ms-eventos`. Si hubo cambios incompatibles en la base de datos, restaurar previamente una copia de seguridad compatible. No eliminar datos de eventos ni inventarios como parte automática del rollback.
