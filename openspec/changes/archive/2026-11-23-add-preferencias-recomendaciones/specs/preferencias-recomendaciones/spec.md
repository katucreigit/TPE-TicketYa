# Spec Delta

## Purpose

Permite que cada cliente registre sus preferencias (tipos de evento, artistas favoritos y ciudad) y reciba recomendaciones de eventos publicados, calculadas a partir de esas preferencias, de su historial, de lo que compraron otros clientes y de la popularidad de cada evento.

## ADDED Requirements

### Requirement: Guardar preferencias
Cuando un `CLIENTE` envíe `PUT /preferencias` con un cuerpo válido, `ms-recomendaciones` SHALL reemplazar por completo sus preferencias (`tipos`, `artistas` y `ciudad`) y responder `200` con las preferencias guardadas y `actualizadaEn`. Un campo ausente SHALL guardarse vacío (`[]` o `null`), y los valores repetidos SHALL guardarse una sola vez.

#### Scenario: Guardar preferencias completas
- **WHEN** el cliente `101` envía `PUT /preferencias` con `{ "tipos": ["RECITAL", "TEATRO"], "artistas": ["Los Andariegos"], "ciudad": "Tandil" }`
- **THEN** la respuesta es `200` con `clienteId` `101`, esos `tipos`, `artistas` y `ciudad`, y un `actualizadaEn`

#### Scenario: Reemplazar preferencias anteriores
- **WHEN** el cliente `101` tenía `tipos` `["RECITAL"]` y `ciudad` `Tandil`, y envía `PUT /preferencias` con `{ "artistas": ["Soda"] }`
- **THEN** sus preferencias quedan con `tipos` `[]`, `artistas` `["Soda"]` y `ciudad` `null`

#### Scenario: Valores repetidos
- **WHEN** el cliente `101` envía `{ "tipos": ["RECITAL", "RECITAL"], "artistas": ["Soda", "soda "] }`
- **THEN** se guardan `tipos` `["RECITAL"]` y `artistas` `["Soda"]`

### Requirement: Consultar preferencias
Cuando un `CLIENTE` envíe `GET /preferencias`, `ms-recomendaciones` SHALL responder `200` con sus preferencias. Si nunca las guardó, SHALL responder `200` con `tipos` y `artistas` vacíos, `ciudad` `null` y `actualizadaEn` `null`.

#### Scenario: Cliente con preferencias
- **WHEN** el cliente `101` guardó preferencias y envía `GET /preferencias`
- **THEN** la respuesta es `200` con las mismas preferencias que guardó

#### Scenario: Cliente sin preferencias
- **WHEN** el cliente `202`, que nunca guardó preferencias, envía `GET /preferencias`
- **THEN** la respuesta es `200` con `{ "clienteId": "202", "tipos": [], "artistas": [], "ciudad": null, "actualizadaEn": null }`

### Requirement: Validación de preferencias
Si algún `tipo` no es `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA` u `OTRO`, si hay más de 20 artistas, si un artista está vacío o supera 100 caracteres, si `ciudad` supera 100 caracteres o si el cuerpo no es un JSON válido, `ms-recomendaciones` SHALL responder `400` con el error estándar y el código `VALIDACION`, y SHALL NOT cambiar las preferencias guardadas.

#### Scenario: Tipo de evento inexistente
- **WHEN** el cliente `101` envía `PUT /preferencias` con `{ "tipos": ["OPERA"] }`
- **THEN** la respuesta es `400` con código `VALIDACION` y sus preferencias anteriores no cambian

#### Scenario: Artista vacío
- **WHEN** el cliente `101` envía `PUT /preferencias` con `{ "artistas": ["  "] }`
- **THEN** la respuesta es `400` con código `VALIDACION`

#### Scenario: Demasiados artistas
- **WHEN** el cliente `101` envía `PUT /preferencias` con 21 artistas distintos
- **THEN** la respuesta es `400` con código `VALIDACION`

#### Scenario: Cuerpo mal formado
- **WHEN** el cliente `101` envía `PUT /preferencias` con `{ "tipos": `
- **THEN** la respuesta es `400` con código `VALIDACION`

### Requirement: Identidad y roles
`ms-recomendaciones` SHALL identificar al usuario por `X-User-Id` y `X-User-Rol`. Las preferencias y `GET /recomendaciones` SHALL aceptar solo el rol `CLIENTE`. "También compraron" SHALL aceptar cualquiera de los cuatro roles. Con otro rol SHALL responder `403` con código `ACCESO_DENEGADO`, y sin los headers de identidad, `400` con código `VALIDACION`.

#### Scenario: Organizador consulta recomendaciones
- **WHEN** un usuario con `X-User-Rol` `ORGANIZADOR` envía `GET /recomendaciones`
- **THEN** la respuesta es `403` con código `ACCESO_DENEGADO`

#### Scenario: Preferencias sin identidad
- **WHEN** se envía `GET /preferencias` sin `X-User-Id`
- **THEN** la respuesta es `400` con código `VALIDACION`

#### Scenario: Organizador consulta también compraron
- **WHEN** un usuario con `X-User-Rol` `ORGANIZADOR` envía `GET /recomendaciones/eventos/42/tambien-compraron`
- **THEN** la respuesta es `200`

### Requirement: Registro de compras confirmadas
Cuando un servicio envíe `POST /internal/compras` con `compraId`, `clienteId` y una lista no vacía de `eventoIds`, `ms-recomendaciones` SHALL guardar la compra y responder `204`. Si el `compraId` ya estaba registrado, SHALL responder `204` sin crear otro registro. Un cuerpo inválido SHALL responder `400` con código `VALIDACION`.

#### Scenario: Compra nueva
- **WHEN** se envía `POST /internal/compras` con `{ "compraId": "c-1", "clienteId": "101", "eventoIds": [42] }`
- **THEN** la respuesta es `204` y queda registrada una compra del cliente `101` para el evento 42

#### Scenario: Compra repetida
- **WHEN** se envía dos veces `POST /internal/compras` con el `compraId` `c-1`
- **THEN** las dos respuestas son `204` y hay un solo registro de esa compra

#### Scenario: Compras simultáneas repetidas
- **WHEN** se envían 10 veces al mismo tiempo `POST /internal/compras` con el `compraId` `c-1`
- **THEN** las 10 respuestas son `204` y hay un solo registro de esa compra

#### Scenario: Compra sin eventos
- **WHEN** se envía `POST /internal/compras` con `{ "compraId": "c-2", "clienteId": "101", "eventoIds": [] }`
- **THEN** la respuesta es `400` con código `VALIDACION` y no se registra nada

### Requirement: Aviso de compra desde ms-ventas
Cuando `ms-ventas` confirme una compra, por cualquiera de sus caminos (compra directa, reintento o reconciliación), SHALL enviar `POST /internal/compras` a `ms-recomendaciones` con el `compraId`, el `clienteId` y el `eventoId` de la compra, y con el `X-Correlation-Id` de la operación. El envío SHALL ser best-effort: con un timeout de conexión de 500 ms y de lectura de 1000 ms, ninguna falla de `ms-recomendaciones` SHALL cambiar la respuesta ni el estado de la compra.

#### Scenario: Compra confirmada avisada
- **WHEN** el cliente `101` confirma una compra del evento 42 con `X-Correlation-Id` `abc-123`
- **THEN** `ms-recomendaciones` recibe `POST /internal/compras` con ese `compraId`, `clienteId` `101`, `eventoIds` `[42]` y `X-Correlation-Id` `abc-123`

#### Scenario: ms-recomendaciones caído
- **WHEN** `ms-recomendaciones` no acepta conexiones y el cliente `101` confirma una compra
- **THEN** la compra queda `APROBADA` con su respuesta habitual, y `ms-ventas` registra una advertencia con el `compraId` y el `correlationId`

#### Scenario: ms-recomendaciones lento
- **WHEN** `ms-recomendaciones` tarda 5 segundos en responder y el cliente `101` confirma una compra
- **THEN** la compra queda `APROBADA` sin esperar más de 1 segundo la respuesta de `ms-recomendaciones`

### Requirement: Puntaje de recomendación
Para cada evento `PUBLICADO` que el cliente no compró, el puntaje SHALL ser la suma de: 3 si su `tipo` está en las preferencias, 3 si algún participante coincide con un artista favorito, 2 si su `ciudad` coincide con la de las preferencias, 2 × (compradores en común / máximo de compradores en común) y 1 × (compradores del evento / máximo de compradores). La comparación de artistas y ciudad SHALL ignorar mayúsculas, acentos y espacios de los extremos. El puntaje SHALL redondearse a dos decimales.

#### Scenario: Coincidencia con todas las preferencias
- **WHEN** el cliente prefiere `RECITAL`, el artista `Los Andariegos` y la ciudad `Tandil`, y el evento 42 es un `RECITAL` en `tandil` con el participante `LOS ANDARIEGOS`, sin compras de nadie
- **THEN** el puntaje del evento 42 es `8.0` y sus `motivos` son `TIPO`, `ARTISTA` y `CIUDAD`

#### Scenario: Popularidad relativa
- **WHEN** el cliente no tiene preferencias ni compras, el evento 42 tiene 4 compradores y el evento 43 tiene 2, y son los más comprados
- **THEN** el puntaje del evento 42 es `1.0` y el del evento 43 es `0.5`

#### Scenario: Compradores en común
- **WHEN** el cliente `101` compró el evento 10, el evento 42 lo compraron 3 clientes que también compraron el 10, y el evento 43 lo compró 1 de ellos
- **THEN** el componente de compradores en común aporta `2.0` al evento 42 y `0.67` al evento 43, con el motivo `TAMBIEN_COMPRARON`

### Requirement: Recomendaciones personalizadas
Cuando un `CLIENTE` envíe `GET /recomendaciones?limite=N` (1 a 50, por defecto 10), `ms-recomendaciones` SHALL responder `200` con `degradado` y hasta `N` eventos `PUBLICADO`, sin los que el cliente ya compró. Cada evento SHALL llevar `eventoId`, `nombre`, `tipo`, `ciudad`, `fechaHora`, `participantes`, `puntaje` y `motivos`, ordenados por `puntaje` descendente, luego por `fechaHora` y luego por `eventoId`.

#### Scenario: Eventos ordenados por puntaje
- **WHEN** el cliente prefiere `TEATRO` y hay publicados un `TEATRO` sin compras y un `RECITAL` con la mayor cantidad de compradores
- **THEN** el `TEATRO` aparece primero con puntaje `3.0` y el `RECITAL` después con puntaje `1.0`

#### Scenario: Eventos ya comprados excluidos
- **WHEN** el cliente `101` compró el evento 42 y consulta sus recomendaciones
- **THEN** el evento 42 no aparece

#### Scenario: Solo eventos publicados
- **WHEN** el catálogo tiene el evento 50 `AGOTADO` que coincide con las preferencias del cliente
- **THEN** el evento 50 no aparece

#### Scenario: Límite fuera de rango
- **WHEN** el cliente envía `GET /recomendaciones?limite=0`
- **THEN** la respuesta es `400` con código `VALIDACION`

### Requirement: Recomendaciones sin preferencias ni historial
Si el cliente no tiene preferencias ni compras, `GET /recomendaciones` SHALL devolver los eventos publicados más populares, es decir, los que más clientes distintos compraron. Si nadie compró nada, SHALL devolverlos por fecha más próxima.

#### Scenario: Cliente nuevo con compras de otros
- **WHEN** el cliente `202` no tiene preferencias ni compras, el evento 43 tiene 5 compradores y el 42 tiene 2
- **THEN** la respuesta empieza con el 43, luego el 42, con el motivo `POPULAR`

#### Scenario: Sistema sin compras
- **WHEN** el cliente `202` no tiene preferencias ni compras y nadie compró nada
- **THEN** la respuesta lista los eventos publicados ordenados por `fechaHora` ascendente, con puntaje `0.0`

### Requirement: También compraron
Cuando se envíe `GET /recomendaciones/eventos/{eventoId}/tambien-compraron?limite=N` (1 a 50, por defecto 10), `ms-recomendaciones` SHALL responder `200` con `eventoId`, `degradado` y los otros eventos `PUBLICADO` comprados por clientes que compraron `eventoId`. Cada uno SHALL llevar `compradoresEnComun` (clientes distintos), ordenados por esa cantidad descendente, luego por `fechaHora` y luego por `eventoId`. Si nadie compró `eventoId`, la lista SHALL estar vacía.

#### Scenario: Eventos comprados en común
- **WHEN** los clientes `101` y `102` compraron los eventos 42 y 43, y el `101` compró también el 44
- **THEN** `GET /recomendaciones/eventos/42/tambien-compraron` devuelve el 43 con `compradoresEnComun` 2 y luego el 44 con 1, sin el 42

#### Scenario: Evento sin compradores
- **WHEN** nadie compró el evento 99
- **THEN** la respuesta es `200` con la lista de eventos vacía

### Requirement: Catálogo con fallback
`ms-recomendaciones` SHALL obtener los eventos de `ms-eventos` por HTTP y guardar en memoria la última respuesta completa durante 30 segundos. Si `ms-eventos` no responde o falla, SHALL usar la última copia, aunque haya vencido, o una lista vacía si nunca la obtuvo. En ese caso SHALL responder `200` con `degradado` `true` y SHALL NOT responder `500`.

#### Scenario: Catálogo disponible
- **WHEN** `ms-eventos` responde y un cliente consulta recomendaciones
- **THEN** la respuesta es `200` con `degradado` `false`

#### Scenario: ms-eventos caído con copia en memoria
- **WHEN** el catálogo se obtuvo antes, pasaron más de 30 segundos y `ms-eventos` deja de responder
- **THEN** la respuesta es `200` con `degradado` `true` y los eventos de la última copia

#### Scenario: ms-eventos caído sin copia
- **WHEN** `ms-eventos` nunca respondió desde que arrancó el servicio
- **THEN** `GET /recomendaciones` y `GET /recomendaciones/eventos/42/tambien-compraron` responden `200` con la lista vacía y `degradado` `true`

### Requirement: Identificador de correlación en ms-recomendaciones
`ms-recomendaciones` SHALL conservar el `X-Correlation-Id` recibido o generar uno, devolverlo en la respuesta, registrar cada solicitud con método, endpoint, usuario, rol, código, duración y `correlationId`, y propagarlo en sus llamadas a `ms-eventos`.

#### Scenario: Propagación a ms-eventos
- **WHEN** un cliente consulta recomendaciones con `X-Correlation-Id` `abc-123` y el catálogo no está en memoria
- **THEN** la respuesta lleva `X-Correlation-Id` `abc-123` y `ms-eventos` recibe la consulta con ese mismo valor

### Requirement: Acceso por el API Gateway
El API Gateway SHALL enrutar `/api/preferencias/**` y `/api/recomendaciones/**` a `ms-recomendaciones`, quitando el prefijo `/api`, con JWT obligatorio y la identidad del token en `X-User-Id` y `X-User-Rol`. Si `ms-recomendaciones` no responde, SHALL devolver `503` con código `SERVICIO_NO_DISPONIBLE`, como las demás rutas.

#### Scenario: Recomendaciones a través del gateway
- **WHEN** un cliente autenticado envía `GET http://localhost:8080/api/recomendaciones`
- **THEN** `ms-recomendaciones` recibe `GET /recomendaciones` con `X-User-Id` y `X-User-Rol` tomados del token

#### Scenario: Preferencias sin token
- **WHEN** se envía `GET http://localhost:8080/api/preferencias` sin header `Authorization`
- **THEN** el gateway responde `401`

#### Scenario: ms-recomendaciones no disponible
- **WHEN** `ms-recomendaciones` está detenido y un cliente autenticado envía `GET http://localhost:8080/api/recomendaciones`
- **THEN** el gateway responde `503` con código `SERVICIO_NO_DISPONIBLE`

#### Scenario: El registro interno no se expone
- **WHEN** se envía `POST http://localhost:8080/api/internal/compras` con un token válido
- **THEN** la solicitud no llega a `ms-recomendaciones`
