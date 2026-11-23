# notificaciones Specification

## Purpose

Recibe de los demás microservicios los pedidos para notificar a un cliente (compra confirmada, evento cancelado o modificado, artista favorito que publica en su ciudad). Los guarda, los envía por email simulado con reintentos sin afectar al servicio que llamó y permite que un administrador los consulte.

## Requirements

### Requirement: Recepción de pedidos de notificación
Cuando un servicio envíe un pedido válido a `POST /internal/notificaciones`, `ms-notificaciones` SHALL guardar una notificación en estado `PENDIENTE` con 0 intentos y su fecha de creación. SHALL responder `202 Accepted` con el `id` y el `estado` de la notificación, sin esperar a que se envíe.

#### Scenario: Pedido válido
- **WHEN** se envía a `POST /internal/notificaciones` un cuerpo con `tipo` `COMPRA_CONFIRMADA`, `destinatario` `{ "usuarioId": "101", "email": "cliente@example.com" }` y `datos` `{ "compraId": 55, "total": 30000 }`
- **THEN** la respuesta es `202` con un `id` y `estado` `PENDIENTE`, y queda guardada una notificación con ese tipo, destinatario y datos, 0 intentos y fecha de creación

#### Scenario: La respuesta no espera al envío
- **WHEN** se envía un pedido válido y el envío simulado de ese destinatario tarda o falla
- **THEN** la respuesta `202` llega sin esperar el resultado del envío

### Requirement: Tipos de notificación admitidos
`ms-notificaciones` SHALL aceptar solo los tipos `COMPRA_CONFIRMADA`, `EVENTO_CANCELADO`, `EVENTO_MODIFICADO` y `ARTISTA_FAVORITO_PUBLICO_EVENTO`. Los `datos` SHALL ser un objeto JSON libre, cuya forma depende del tipo, y se guardan tal como llegan.

#### Scenario: Cada tipo admitido se acepta
- **WHEN** se envía un pedido válido con cada uno de los cuatro tipos admitidos
- **THEN** cada uno recibe `202` y su notificación guarda el tipo correspondiente

#### Scenario: Datos con forma distinta según el tipo
- **WHEN** se envía un `EVENTO_MODIFICADO` con `datos` `{ "eventoId": 42, "cambios": { "fecha": { "antes": "2026-12-01T21:00", "despues": "2026-12-08T21:00" } } }`
- **THEN** la notificación guarda `datos` con la misma estructura anidada que se envió

#### Scenario: Pedido sin datos
- **WHEN** se envía un pedido válido sin el campo `datos`
- **THEN** la respuesta es `202` y la notificación guarda `datos` como un objeto vacío

### Requirement: Validación del pedido
Si el pedido no tiene un `tipo` admitido, no tiene `destinatario.usuarioId` o no tiene un `destinatario.email` con formato válido, `ms-notificaciones` SHALL responder `400` con el error estándar y el código `VALIDACION`, y SHALL NOT guardar ninguna notificación.

#### Scenario: Tipo desconocido
- **WHEN** se envía un pedido con `tipo` `OFERTA_ESPECIAL`
- **THEN** la respuesta es `400` con código `VALIDACION` y no se guarda ninguna notificación

#### Scenario: Tipo ausente
- **WHEN** se envía un pedido sin `tipo`
- **THEN** la respuesta es `400` con código `VALIDACION` y no se guarda ninguna notificación

#### Scenario: Destinatario sin usuario
- **WHEN** se envía un pedido cuyo `destinatario` no tiene `usuarioId`
- **THEN** la respuesta es `400` con código `VALIDACION` y no se guarda ninguna notificación

#### Scenario: Email inválido o ausente
- **WHEN** se envía un pedido cuyo `destinatario.email` falta o vale `no-es-un-email`
- **THEN** la respuesta es `400` con código `VALIDACION` y no se guarda ninguna notificación

### Requirement: Identificador de correlación de la notificación
Cuando el pedido traiga el header `X-Correlation-Id`, `ms-notificaciones` SHALL guardar ese valor en la notificación y SHALL incluirlo en los registros del envío. Cuando no lo traiga, SHALL generar un identificador nuevo y usarlo de la misma forma.

#### Scenario: Pedido con correlationId
- **WHEN** se envía un pedido válido con `X-Correlation-Id: abc-123`
- **THEN** la notificación guarda `correlationId` `abc-123` y el registro del envío incluye `abc-123`

#### Scenario: Pedido sin correlationId
- **WHEN** se envía un pedido válido sin el header `X-Correlation-Id`
- **THEN** la notificación guarda un `correlationId` generado y no vacío

### Requirement: Envío simulado por email
`ms-notificaciones` SHALL enviar cada notificación `PENDIENTE` registrando en el log el destinatario, el tipo, los datos y el `correlationId`. Cuando el envío funcione, la notificación SHALL pasar a `ENVIADA` con su cantidad de intentos y la fecha de envío.

#### Scenario: Envío exitoso al primer intento
- **WHEN** se acepta un pedido para `cliente@example.com` y el envío simulado no falla
- **THEN** en un plazo breve la notificación queda `ENVIADA`, con 1 intento y fecha de envío, y el log registra el envío a `cliente@example.com` con su tipo y `correlationId`

### Requirement: Fallas simuladas configurables
El envío simulado SHALL fallar siempre para los destinatarios de una lista configurable de emails, y SHALL fallar solo en el primer intento para los de otra lista configurable, para poder probar los reintentos. Por defecto, la primera lista contiene `falla@ticketya.test` y la segunda `falla-una-vez@ticketya.test`.

#### Scenario: Destinatario que siempre falla
- **WHEN** se envía una notificación a `falla@ticketya.test` con la configuración por defecto
- **THEN** todos los intentos de envío fallan

#### Scenario: Destinatario que falla una sola vez
- **WHEN** se envía una notificación a `falla-una-vez@ticketya.test` con la configuración por defecto
- **THEN** el primer intento falla y el segundo funciona

### Requirement: Reintentos con espera creciente
Cuando un intento de envío falle y no se haya llegado al máximo de intentos (configurable, 3 por defecto), `ms-notificaciones` SHALL dejar la notificación `PENDIENTE`, sumar el intento, guardar el último error y programar un nuevo intento. La espera antes de cada reintento SHALL ser mayor que la anterior.

#### Scenario: Éxito en el segundo intento
- **WHEN** se envía una notificación a `falla-una-vez@ticketya.test`
- **THEN** la notificación queda finalmente `ENVIADA` con 2 intentos

#### Scenario: La espera crece entre reintentos
- **WHEN** una notificación falla en sus tres intentos
- **THEN** el tiempo entre el segundo y el tercer intento es mayor que el tiempo entre el primero y el segundo

### Requirement: Notificación fallida al agotar los intentos
Cuando falle el último intento permitido, `ms-notificaciones` SHALL dejar la notificación `FALLIDA`, con la cantidad de intentos y el último error, y SHALL NOT volver a intentar enviarla.

#### Scenario: Intentos agotados
- **WHEN** se envía una notificación a `falla@ticketya.test` con un máximo de 3 intentos
- **THEN** la notificación queda `FALLIDA` con 3 intentos y un último error, y no se hace un cuarto intento

### Requirement: Aislamiento de fallas del envío
Una falla del envío SHALL NOT cambiar la respuesta que recibe el servicio que llamó, que SHALL ser siempre `202` para un pedido válido, porque el envío ocurre después de responder.

#### Scenario: Pedido para un destinatario que falla
- **WHEN** se envía un pedido válido para `falla@ticketya.test`
- **THEN** la respuesta es `202`, igual que para un destinatario que no falla

### Requirement: Envíos pendientes tras un reinicio
Las notificaciones `PENDIENTE` SHALL quedar guardadas en la base con la fecha de su próximo intento, de modo que un reinicio de `ms-notificaciones` no las pierda: al volver a arrancar, el servicio SHALL seguir procesándolas.

#### Scenario: Pendiente guardada antes de arrancar
- **WHEN** al arrancar `ms-notificaciones` la base tiene una notificación `PENDIENTE` cuyo próximo intento ya venció
- **THEN** el servicio la procesa y la notificación queda `ENVIADA`

### Requirement: Deduplicación por clave
Cuando el pedido traiga una `claveDeduplicacion` que ya tiene otra notificación, `ms-notificaciones` SHALL responder `202` con el `id` y el `estado` actual de la notificación existente, y SHALL NOT crear ni volver a enviar otra. La base SHALL garantizarlo también con pedidos simultáneos.

#### Scenario: Pedido repetido con la misma clave
- **WHEN** se envían dos pedidos válidos con `claveDeduplicacion` `compra-55`, uno después del otro
- **THEN** los dos reciben `202` con el mismo `id`, queda una sola notificación con esa clave y se envía una sola vez

#### Scenario: Pedidos simultáneos con la misma clave
- **WHEN** se envían 20 pedidos simultáneos con `claveDeduplicacion` `compra-77`
- **THEN** todos reciben `202` con el mismo `id` y queda exactamente una notificación con esa clave

#### Scenario: Pedidos sin clave
- **WHEN** se envían dos pedidos válidos iguales sin `claveDeduplicacion`
- **THEN** se guardan dos notificaciones distintas

#### Scenario: Claves distintas
- **WHEN** se envían pedidos con `claveDeduplicacion` `compra-55` y `compra-56`
- **THEN** se guardan dos notificaciones distintas

### Requirement: Listado de notificaciones para administradores
Cuando un `ADMIN` consulte `GET /notificaciones`, `ms-notificaciones` SHALL devolver una página de notificaciones (`content`, `page`, `size`, `totalElements`, `totalPages`) de la más nueva a la más vieja. SHALL permitir filtrar por `usuarioId`, `tipo` y `estado`, combinables, con `page` desde 0 y `size` de 1 a 100 (20 por defecto).

#### Scenario: Listado sin filtros
- **WHEN** un `ADMIN` consulta `GET /notificaciones` y hay 3 notificaciones guardadas
- **THEN** la respuesta es `200` con las 3 notificaciones de la más nueva a la más vieja y `totalElements` 3

#### Scenario: Filtros combinados
- **WHEN** un `ADMIN` consulta `GET /notificaciones?usuarioId=101&tipo=COMPRA_CONFIRMADA&estado=FALLIDA`
- **THEN** la respuesta solo incluye notificaciones del usuario 101, de tipo `COMPRA_CONFIRMADA` y en estado `FALLIDA`

#### Scenario: Paginación
- **WHEN** hay 25 notificaciones y un `ADMIN` consulta `GET /notificaciones?page=1&size=10`
- **THEN** la respuesta tiene 10 notificaciones, `page` 1, `size` 10, `totalElements` 25 y `totalPages` 3

#### Scenario: Parámetros inválidos
- **WHEN** un `ADMIN` consulta con `size=0`, `size=101`, `page=-1`, `estado=PERDIDA` o `tipo=OTRO`
- **THEN** la respuesta es `400` con código `VALIDACION`

### Requirement: Detalle de una notificación para administradores
Cuando un `ADMIN` consulte `GET /notificaciones/{id}`, `ms-notificaciones` SHALL devolver la notificación con su tipo, destinatario, datos, estado, intentos, último error, fechas, `correlationId` y `claveDeduplicacion`. Si el id no existe, SHALL responder `404` con el error estándar.

#### Scenario: Notificación existente
- **WHEN** un `ADMIN` consulta `GET /notificaciones/{id}` con el id de una notificación guardada
- **THEN** la respuesta es `200` con todos los campos de esa notificación

#### Scenario: Notificación inexistente
- **WHEN** un `ADMIN` consulta `GET /notificaciones/{id}` con un id que no existe
- **THEN** la respuesta es `404` con el error estándar

### Requirement: Autorización de la consulta
Los endpoints `GET /notificaciones` y `GET /notificaciones/{id}` SHALL exigir los headers `X-User-Id` y `X-User-Rol`, como el resto de los servicios. Si falta alguno SHALL responder `400` con código `VALIDACION`, y si `X-User-Rol` no es `ADMIN` SHALL responder `403`.

#### Scenario: Rol distinto de ADMIN
- **WHEN** se consulta `GET /notificaciones` o `GET /notificaciones/{id}` con `X-User-Rol` `CLIENTE`, `ORGANIZADOR` o `PERSONAL_ACCESO`
- **THEN** la respuesta es `403` con el error estándar

#### Scenario: Headers de identidad ausentes
- **WHEN** se consulta `GET /notificaciones` sin `X-User-Id` o sin `X-User-Rol`
- **THEN** la respuesta es `400` con código `VALIDACION`
