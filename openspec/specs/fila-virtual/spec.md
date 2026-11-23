# fila-virtual Specification

## Purpose
Ordena el acceso a la compra en eventos de alta demanda: los clientes entran a una fila virtual del evento, consultan su posición y quedan habilitados para reservar en orden de llegada, por tandas y durante una ventana de tiempo limitada.

## Requirements

### Requirement: Activación de la fila de un evento
Cuando un `ADMIN` o un `ORGANIZADOR` envíe `POST /fila/eventos/{eventoId}/activar` con `tamanoTanda` (1 a 1000) y `segundosEntreTandas` (1 a 3600), `ms-fila` SHALL dejar activa la fila del evento con esos parámetros y responder `200` con la configuración: `eventoId`, `activa`, `tamanoTanda`, `segundosEntreTandas`, `minutosVentana`, `proximaTandaEn`, `enEspera` y `habilitados`. La primera tanda SHALL habilitarse `segundosEntreTandas` después de la activación.

#### Scenario: Activar una fila con parámetros
- **WHEN** un `ADMIN` envía `POST /fila/eventos/42/activar` con `{ "tamanoTanda": 50, "segundosEntreTandas": 30 }` y la fila del evento 42 no está activa
- **THEN** la respuesta es `200` con `activa` `true`, `tamanoTanda` 50, `segundosEntreTandas` 30, `minutosVentana` 10, `enEspera` 0, `habilitados` 0 y `proximaTandaEn` 30 segundos después de la activación

#### Scenario: Activar sin cuerpo usa los valores por defecto
- **WHEN** un `ORGANIZADOR` envía `POST /fila/eventos/42/activar` sin cuerpo
- **THEN** la respuesta es `200` con `activa` `true`, `tamanoTanda` 100 y `segundosEntreTandas` 60

### Requirement: Reactivación de una fila activa
Cuando se active una fila que ya está activa, `ms-fila` SHALL actualizar `tamanoTanda` y `segundosEntreTandas`, conservar a los clientes en espera y a los habilitados con sus posiciones y ventanas, y responder `200` con la configuración actualizada.

#### Scenario: Cambiar el tamaño de tanda sin perder la fila
- **WHEN** la fila del evento 42 está activa con 3 clientes en espera y un `ADMIN` la activa de nuevo con `{ "tamanoTanda": 200, "segundosEntreTandas": 30 }`
- **THEN** la respuesta es `200` con `tamanoTanda` 200 y `enEspera` 3, y cada cliente conserva su posición

### Requirement: Validación de los parámetros de la fila
Si `tamanoTanda` o `segundosEntreTandas` están fuera de rango, o el cuerpo no es un JSON válido, `ms-fila` SHALL responder `400` con el error estándar y el código `VALIDACION`, y SHALL NOT cambiar el estado de la fila.

#### Scenario: Tamaño de tanda cero
- **WHEN** un `ADMIN` envía `POST /fila/eventos/42/activar` con `{ "tamanoTanda": 0, "segundosEntreTandas": 30 }`
- **THEN** la respuesta es `400` con código `VALIDACION` y la fila del evento 42 sigue inactiva

#### Scenario: Intervalo mayor al máximo
- **WHEN** un `ADMIN` envía `POST /fila/eventos/42/activar` con `{ "tamanoTanda": 10, "segundosEntreTandas": 7200 }`
- **THEN** la respuesta es `400` con código `VALIDACION` y la fila del evento 42 sigue inactiva

### Requirement: Desactivación de la fila de un evento
Cuando un `ADMIN` o un `ORGANIZADOR` envíe `POST /fila/eventos/{eventoId}/desactivar`, `ms-fila` SHALL dejar la fila inactiva, descartar a los clientes en espera y a los habilitados, y responder `204`. Desactivar una fila que no está activa SHALL responder `204` sin cambios.

#### Scenario: Desactivar una fila con clientes
- **WHEN** la fila del evento 42 está activa con clientes en espera y habilitados, y un `ORGANIZADOR` envía `POST /fila/eventos/42/desactivar`
- **THEN** la respuesta es `204` y la consulta de cualquiera de esos clientes informa `activa` `false` y `habilitado` `true`

#### Scenario: Desactivar una fila inactiva
- **WHEN** un `ADMIN` envía `POST /fila/eventos/99/desactivar` y la fila del evento 99 nunca se activó
- **THEN** la respuesta es `204`

### Requirement: Rol para administrar la fila
`ms-fila` SHALL permitir activar y desactivar la fila solo con `X-User-Rol` `ADMIN` u `ORGANIZADOR`. Con otro rol SHALL responder `403` con código `ACCESO_DENEGADO`, y sin los headers `X-User-Id` o `X-User-Rol`, `400` con código `VALIDACION`. En ningún caso SHALL cambiar el estado de la fila.

#### Scenario: Cliente intenta activar
- **WHEN** un usuario con `X-User-Rol` `CLIENTE` envía `POST /fila/eventos/42/activar`
- **THEN** la respuesta es `403` con código `ACCESO_DENEGADO` y la fila del evento 42 sigue inactiva

#### Scenario: Activar sin identidad
- **WHEN** se envía `POST /fila/eventos/42/activar` sin `X-User-Rol`
- **THEN** la respuesta es `400` con código `VALIDACION`

### Requirement: Ingreso a la fila
Cuando un `CLIENTE` envíe `POST /fila/eventos/{eventoId}/ingresar` con la fila activa y no esté en ella, `ms-fila` SHALL ubicarlo al final y responder `200` con `eventoId`, `activa` `true`, `estado` `EN_ESPERA`, `habilitado` `false` y `posicion`, que es 1 más la cantidad de clientes en espera delante de él.

#### Scenario: Primer cliente en una fila vacía
- **WHEN** el cliente `101` ingresa a la fila activa y vacía del evento 42
- **THEN** la respuesta es `200` con `estado` `EN_ESPERA`, `posicion` 1 y `habilitado` `false`

#### Scenario: Ingresos sucesivos
- **WHEN** los clientes `101`, `102` y `103` ingresan uno después del otro a la fila activa y vacía del evento 42
- **THEN** reciben las posiciones 1, 2 y 3 respectivamente

### Requirement: Ingreso idempotente
Si un `CLIENTE` que ya está en espera o habilitado vuelve a ingresar a la misma fila, `ms-fila` SHALL responder `200` con su estado actual, sin crearle otro lugar ni cambiar el orden. Si está en espera, SHALL devolver su posición; si está habilitado, `estado` `HABILITADO` y `habilitadoHasta`.

#### Scenario: Ingreso repetido en espera
- **WHEN** el cliente `102` está en espera en la posición 2 y vuelve a ingresar
- **THEN** la respuesta es `200` con `posicion` 2, y la fila sigue con la misma cantidad de clientes en espera

#### Scenario: Ingreso repetido de un habilitado
- **WHEN** el cliente `101` está habilitado y vuelve a ingresar
- **THEN** la respuesta es `200` con `estado` `HABILITADO`, `habilitado` `true` y el mismo `habilitadoHasta`, y no queda en espera

#### Scenario: Ingresos simultáneos del mismo cliente
- **WHEN** el cliente `101` envía 20 ingresos simultáneos a la fila activa y vacía del evento 42
- **THEN** las 20 respuestas son `200` con `posicion` 1 y la fila tiene un solo cliente en espera

### Requirement: Ingreso a una fila inactiva
Si un `CLIENTE` intenta ingresar a la fila de un evento que no tiene la fila activa, `ms-fila` SHALL responder `409` con código `FILA_NO_ACTIVA` y SHALL NOT registrar al cliente.

#### Scenario: Evento sin fila
- **WHEN** el cliente `101` envía `POST /fila/eventos/99/ingresar` y la fila del evento 99 no está activa
- **THEN** la respuesta es `409` con código `FILA_NO_ACTIVA`

### Requirement: Rol del cliente en la fila
`ms-fila` SHALL permitir ingresar y consultar la posición solo con `X-User-Rol` `CLIENTE`, identificando al cliente por `X-User-Id`. Con otro rol SHALL responder `403` con código `ACCESO_DENEGADO`, y sin los headers de identidad, `400` con código `VALIDACION`.

#### Scenario: Organizador intenta ingresar
- **WHEN** un usuario con `X-User-Rol` `ORGANIZADOR` envía `POST /fila/eventos/42/ingresar`
- **THEN** la respuesta es `403` con código `ACCESO_DENEGADO` y la fila no cambia

#### Scenario: Ingreso sin identidad
- **WHEN** se envía `POST /fila/eventos/42/ingresar` sin `X-User-Id`
- **THEN** la respuesta es `400` con código `VALIDACION`

### Requirement: Posiciones únicas con ingresos concurrentes
Cuando varios clientes distintos ingresen al mismo tiempo a la misma fila, `ms-fila` SHALL asignarle a cada uno un lugar distinto, en el orden en que el almacenamiento de la fila los reciba, sin perder ni duplicar ingresos, también con varias instancias del servicio.

#### Scenario: Muchos ingresos simultáneos
- **WHEN** 200 clientes distintos ingresan al mismo tiempo a la fila activa y vacía del evento 42
- **THEN** las 200 respuestas son `200`, sus posiciones son exactamente los números del 1 al 200 sin repetirse, y la fila tiene 200 clientes en espera

### Requirement: Consulta de la posición
Cuando un `CLIENTE` envíe `GET /fila/eventos/{eventoId}/posicion`, `ms-fila` SHALL responder `200` con su estado: `EN_ESPERA` con `posicion`; `HABILITADO` con `habilitado` `true` y `habilitadoHasta`; `FUERA_DE_FILA` si la fila está activa y el cliente no está en ella; o `SIN_FILA` con `activa` `false` y `habilitado` `true` si la fila no está activa.

#### Scenario: Cliente en espera
- **WHEN** el cliente `103` está tercero en la fila activa del evento 42 y consulta su posición
- **THEN** la respuesta es `200` con `estado` `EN_ESPERA`, `posicion` 3 y `habilitado` `false`

#### Scenario: Cliente habilitado
- **WHEN** el cliente `101` fue habilitado y consulta su posición
- **THEN** la respuesta es `200` con `estado` `HABILITADO`, `habilitado` `true` y `habilitadoHasta` igual al momento de la habilitación más `minutosVentana`

#### Scenario: Cliente que no ingresó
- **WHEN** el cliente `200` consulta su posición en la fila activa del evento 42 sin haber ingresado
- **THEN** la respuesta es `200` con `estado` `FUERA_DE_FILA` y `habilitado` `false`

#### Scenario: Evento sin fila activa
- **WHEN** el cliente `101` consulta su posición en el evento 99, que no tiene la fila activa
- **THEN** la respuesta es `200` con `activa` `false`, `estado` `SIN_FILA` y `habilitado` `true`

### Requirement: Habilitación por tandas
Cuando llegue `proximaTandaEn` de una fila activa, `ms-fila` SHALL habilitar a los primeros `tamanoTanda` clientes en espera en orden de llegada, darles una ventana de compra de `minutosVentana` minutos, adelantar las posiciones de los que siguen esperando y programar la próxima tanda `segundosEntreTandas` después.

#### Scenario: Tanda completa
- **WHEN** la fila del evento 42 tiene `tamanoTanda` 2 y los clientes `101`, `102` y `103` en espera, y llega `proximaTandaEn`
- **THEN** `101` y `102` quedan `HABILITADO` con `habilitadoHasta` 10 minutos después, y `103` queda `EN_ESPERA` en la posición 1

#### Scenario: Antes de tiempo no se habilita
- **WHEN** la fila del evento 42 tiene clientes en espera y todavía no llegó `proximaTandaEn`
- **THEN** ningún cliente queda habilitado

#### Scenario: Tanda con menos clientes que el tamaño
- **WHEN** la fila tiene `tamanoTanda` 100 y 3 clientes en espera, y llega `proximaTandaEn`
- **THEN** los 3 quedan `HABILITADO` y la fila queda sin clientes en espera

#### Scenario: Tandas sucesivas
- **WHEN** la fila tiene `tamanoTanda` 2, `segundosEntreTandas` 30 y 5 clientes en espera, y pasan dos intervalos de 30 segundos
- **THEN** quedan habilitados los 4 primeros en orden de llegada, y el quinto queda `EN_ESPERA` en la posición 1

### Requirement: Habilitación única con varias instancias
Cuando el proceso de habilitación corra al mismo tiempo en varias instancias de `ms-fila`, el sistema SHALL habilitar a cada cliente una sola vez por ingreso y SHALL habilitar a lo sumo una tanda por fila en cada intervalo.

#### Scenario: Procesos de habilitación concurrentes
- **WHEN** la fila tiene `tamanoTanda` 10 y 100 clientes en espera, llega `proximaTandaEn` y 8 ejecuciones del proceso de habilitación corren al mismo tiempo
- **THEN** quedan exactamente 10 clientes habilitados, los 10 primeros en orden de llegada, cada uno con un único registro de habilitación, y 90 siguen en espera

### Requirement: Vencimiento del turno
Cuando pase `habilitadoHasta` de un cliente habilitado, `ms-fila` SHALL dejar de considerarlo habilitado y quitarlo de la fila; su estado pasa a `FUERA_DE_FILA`. Si vuelve a ingresar, SHALL ubicarlo al final de la fila como un ingreso nuevo.

#### Scenario: Turno vencido sin comprar
- **WHEN** el cliente `101` fue habilitado y pasan los 10 minutos de su ventana
- **THEN** la consulta de su posición devuelve `estado` `FUERA_DE_FILA` y `habilitado` `false`, y la consulta interna devuelve `habilitado` `false`

#### Scenario: Reingreso después de perder el turno
- **WHEN** el cliente `101` perdió su turno y vuelve a ingresar a la fila, que tiene 4 clientes en espera
- **THEN** la respuesta es `200` con `estado` `EN_ESPERA` y `posicion` 5

### Requirement: Consulta interna del estado de un cliente
Cuando un servicio envíe `GET /internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado`, `ms-fila` SHALL responder `200` con `activa` y `habilitado`. Si la fila no está activa SHALL responder `activa` `false` y `habilitado` `true`. Con la fila activa, `habilitado` SHALL ser `true` solo si el cliente está habilitado y su ventana no venció al momento de la consulta.

#### Scenario: Fila inactiva
- **WHEN** se consulta el estado del cliente `101` en el evento 99, que no tiene la fila activa
- **THEN** la respuesta es `200` con `{ "activa": false, "habilitado": true }`

#### Scenario: Cliente en espera
- **WHEN** se consulta el estado del cliente `103`, que está en espera en la fila activa del evento 42
- **THEN** la respuesta es `200` con `{ "activa": true, "habilitado": false }`

#### Scenario: Cliente habilitado dentro de su ventana
- **WHEN** se consulta el estado del cliente `101`, habilitado hace 3 minutos con una ventana de 10
- **THEN** la respuesta es `200` con `{ "activa": true, "habilitado": true }`

#### Scenario: Ventana vencida aunque el proceso no haya corrido
- **WHEN** pasó `habilitadoHasta` del cliente `101` y todavía no corrió el proceso que quita los turnos vencidos
- **THEN** la respuesta es `200` con `{ "activa": true, "habilitado": false }`

### Requirement: Almacenamiento de la fila no disponible
Si el almacenamiento de la fila no está disponible, `ms-fila` SHALL responder `503` con el error estándar y el código `SERVICIO_NO_DISPONIBLE` en todos sus endpoints, incluida la consulta interna, y el proceso de habilitación SHALL reintentar en su próxima ejecución sin detenerse.

#### Scenario: Ingreso sin almacenamiento
- **WHEN** el almacenamiento de la fila está detenido y el cliente `101` intenta ingresar
- **THEN** la respuesta es `503` con código `SERVICIO_NO_DISPONIBLE`

#### Scenario: Recuperación del proceso de habilitación
- **WHEN** el almacenamiento de la fila se detiene durante una ejecución del proceso de habilitación y luego vuelve a estar disponible
- **THEN** el proceso sigue programado y habilita la tanda pendiente en una ejecución posterior

### Requirement: Identificador de correlación en ms-fila
`ms-fila` SHALL conservar el `X-Correlation-Id` recibido o generar uno si falta o no es válido, devolverlo en la respuesta e incluirlo en cada línea de registro de la solicitud. Cada ejecución del proceso de habilitación SHALL tener su propio identificador de correlación.

#### Scenario: Solicitud con identificador
- **WHEN** se envía `GET /fila/eventos/42/posicion` con `X-Correlation-Id` `abc-123`
- **THEN** la respuesta lleva `X-Correlation-Id` `abc-123` y el registro de la solicitud incluye `correlationId=abc-123`

#### Scenario: Solicitud sin identificador
- **WHEN** se envía `POST /fila/eventos/42/ingresar` sin `X-Correlation-Id`
- **THEN** la respuesta lleva un `X-Correlation-Id` generado y el registro de la solicitud usa ese mismo valor

### Requirement: Registro de solicitudes en ms-fila
`ms-fila` SHALL registrar cada solicitud HTTP con método, endpoint, usuario, rol, código de respuesta, tiempo de procesamiento y `correlationId`, con el mismo formato que los demás microservicios. Sin identidad, el usuario y el rol SHALL figurar como `ANONIMO`.

#### Scenario: Registro de un ingreso
- **WHEN** el cliente `101` ingresa a la fila del evento 42
- **THEN** queda una línea de registro con el método `POST`, el endpoint `/fila/eventos/42/ingresar`, el usuario `101`, el rol `CLIENTE`, el código `200`, la duración y el `correlationId`

### Requirement: Registro de operaciones críticas de la fila
`ms-fila` SHALL registrar una operación crítica, con `correlationId`, usuario e ids de negocio, por cada activación (`FILA_ACTIVADA`), desactivación (`FILA_DESACTIVADA`), ingreso nuevo (`INGRESO_FILA`), habilitación (`CLIENTE_HABILITADO`) y turno vencido (`TURNO_VENCIDO`). Los ingresos repetidos SHALL NOT registrarse, y cada habilitación y vencimiento SHALL registrarse una sola vez aunque haya varias instancias.

#### Scenario: Ingreso nuevo registrado
- **WHEN** el cliente `101` ingresa por primera vez a la fila del evento 42 y queda en la posición 1
- **THEN** queda una línea `OPERACION_CRITICA operacion=INGRESO_FILA` con el usuario `101`, el `correlationId`, `eventoId=42`, `clienteId=101` y `posicion=1`

#### Scenario: Ingreso repetido no registrado
- **WHEN** el cliente `101`, que ya está en espera, vuelve a ingresar
- **THEN** no se agrega otra línea `INGRESO_FILA`

#### Scenario: Habilitación y vencimiento registrados una vez
- **WHEN** el cliente `101` es habilitado mientras corren varias ejecuciones simultáneas del proceso, y luego vence su turno
- **THEN** hay exactamente una línea `CLIENTE_HABILITADO` y una línea `TURNO_VENCIDO` para el cliente `101` en el evento 42, con usuario `SISTEMA`

### Requirement: Acceso a la fila por el API Gateway
El API Gateway SHALL enrutar `/api/fila/**` a `ms-fila` quitando el prefijo `/api`, con la identidad del JWT en `X-User-Id` y `X-User-Rol`, sin desviar a `ms-fila` ninguna ruta de `/api/eventos/**`. Si `ms-fila` no responde, SHALL devolver `503` con código `SERVICIO_NO_DISPONIBLE`, igual que en las demás rutas.

#### Scenario: Ingreso a través del gateway
- **WHEN** un cliente autenticado envía `POST http://localhost:8080/api/fila/eventos/42/ingresar` con su JWT
- **THEN** `ms-fila` recibe `POST /fila/eventos/42/ingresar` con `X-User-Id` y `X-User-Rol` tomados del token

#### Scenario: Rutas de eventos sin cambios
- **WHEN** se envía `GET http://localhost:8080/api/eventos/42` con la ruta de la fila configurada
- **THEN** la solicitud llega a `ms-eventos` y no a `ms-fila`

#### Scenario: Fila sin token
- **WHEN** se envía `POST http://localhost:8080/api/fila/eventos/42/ingresar` sin header `Authorization`
- **THEN** el gateway responde `401`

#### Scenario: ms-fila no disponible
- **WHEN** `ms-fila` está detenido y un cliente autenticado consulta `GET http://localhost:8080/api/fila/eventos/42/posicion`
- **THEN** el gateway responde `503` con código `SERVICIO_NO_DISPONIBLE`

#### Scenario: La consulta interna no se expone
- **WHEN** se envía `GET http://localhost:8080/api/internal/fila/eventos/42/clientes/101/estado` o `GET http://localhost:8080/internal/fila/eventos/42/clientes/101/estado`
- **THEN** la solicitud no llega a la consulta interna de `ms-fila`
