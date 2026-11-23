# Spec Delta

## Purpose

Define el control de acceso con código QR en el ingreso al evento (`POST /entradas/validar` en `ms-ventas`, US-23): autorización por rol `PERSONAL_ACCESO`, validación de entrada, transición atómica `EMITIDA -> USADA` sin dependencia de `ms-eventos`, códigos de rechazo diferenciados y exclusión mutua en base de datos frente a devoluciones y cancelaciones de evento.

## ADDED Requirements

### Requirement: Autorización y validación de entrada del ingreso
El endpoint `POST /entradas/validar` SHALL exigir headers de identidad (`X-User-Id` y `X-User-Rol`), restringir el acceso al rol `PERSONAL_ACCESO` y validar los campos obligatorios del cuerpo (`codigoQr` no vacío y `eventoId` no nulo). El identificador del operador que valida la entrada SHALL obtenerse siempre de `X-User-Id` y nunca del cuerpo de la solicitud.

#### Scenario: Headers de identidad ausentes o vacíos
- **GIVEN** una solicitud a `POST /entradas/validar` con cuerpo válido pero sin `X-User-Id`, sin `X-User-Rol` o con alguno en blanco
- **WHEN** el servicio procesa la solicitud
- **THEN** responde HTTP `400` con `code = "VALIDACION"` y detalle de los headers faltantes.

#### Scenario: Rol distinto de PERSONAL_ACCESO
- **GIVEN** una solicitud a `POST /entradas/validar` con cuerpo válido, `X-User-Id` presente y `X-User-Rol` distinto de `PERSONAL_ACCESO` (por ejemplo `CLIENTE`, `ORGANIZADOR`, `ADMIN` u otro valor)
- **WHEN** el servicio procesa la solicitud
- **THEN** responde HTTP `403` con `code = "ACCESO_DENEGADO"`.

#### Scenario: Cuerpo ausente o campos obligatorios inválidos
- **GIVEN** una solicitud a `POST /entradas/validar` con cuerpo ausente, JSON mal formado, `codigoQr` nulo/vacío o `eventoId` nulo
- **WHEN** el servicio procesa la solicitud
- **THEN** responde HTTP `400` con `code = "VALIDACION"`.

#### Scenario: Operador tomado exclusivamente de la identidad
- **GIVEN** una solicitud válida a `POST /entradas/validar` con `X-User-Id: "acc-7"` y `X-User-Rol: PERSONAL_ACCESO`
- **WHEN** la entrada es validada exitosamente
- **THEN** `validadaPor` se registra con `"acc-7"` independientemente de cualquier campo adicional en el cuerpo.

---

### Requirement: Validación exitosa de una entrada emitida
Cuando personal con rol `PERSONAL_ACCESO` escanea un `codigoQr` correspondiente a una entrada en estado `EMITIDA` del `eventoId` indicado, el sistema SHALL transicionarla atómicamente a `USADA`, registrar la marca temporal `usadaEn` y el operador `validadaPor`, y responder HTTP `200` con los datos actualizados de la entrada sin consultar a `ms-eventos`.

#### Scenario: Ingreso válido de entrada emitida
- **GIVEN** una entrada en estado `EMITIDA` con `codigoQr = "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64"` perteneciente al evento `42`
- **WHEN** el operador `"acc-7"` con rol `PERSONAL_ACCESO` envía `POST /entradas/validar` con `{"codigoQr": "9f3a6c52-47c1-4b0e-a7d9-3c5e8f1b2d64", "eventoId": 42}`
- **THEN** responde HTTP `200` con los datos de la entrada
- **AND** el estado es `"USADA"`, `usadaEn` no es nulo y `validadaPor` es `"acc-7"`
- **AND** no se realiza ninguna llamada HTTP a `ms-eventos` ni a `ms-reservas`.

---

### Requirement: Rechazos diferenciados por motivo
Cuando la transición atómica no afecte ninguna fila, el sistema SHALL consultar la entrada únicamente por `codigoQr` y responder con el código HTTP y `ErrorResponse.code` específico del motivo. Un `codigoQr` no vacío con formato distinto de UUID SHALL tratarse como inexistente (`404 QR_INEXISTENTE`) y nunca producir HTTP `500`.

#### Scenario: Código QR inexistente
- **GIVEN** un `codigoQr` con formato UUID que no pertenece a ninguna entrada registrada
- **WHEN** personal con rol `PERSONAL_ACCESO` envía `POST /entradas/validar`
- **THEN** responde HTTP `404` con `code = "QR_INEXISTENTE"`.

#### Scenario: Código QR con formato no UUID
- **GIVEN** un `codigoQr` no vacío que no tiene formato UUID (por ejemplo `"no-es-un-uuid"` o una cadena arbitraria)
- **WHEN** personal con rol `PERSONAL_ACCESO` envía `POST /entradas/validar`
- **THEN** responde HTTP `404` con `code = "QR_INEXISTENTE"` y nunca HTTP `500`.

#### Scenario: Entrada perteneciente a otro evento
- **GIVEN** una entrada registrada para el evento `42`
- **WHEN** personal con rol `PERSONAL_ACCESO` envía `POST /entradas/validar` con ese `codigoQr` y `eventoId = 99`
- **THEN** responde HTTP `422` con `code = "EVENTO_INCORRECTO"`
- **AND** la entrada no cambia de estado.

#### Scenario: Entrada ya usada
- **GIVEN** una entrada del evento `42` ya marcada `USADA` en un instante `usadaEn`
- **WHEN** personal con rol `PERSONAL_ACCESO` vuelve a enviar `POST /entradas/validar` con ese `codigoQr` y `eventoId = 42`
- **THEN** responde HTTP `409` con `code = "ENTRADA_USADA"`
- **AND** el mensaje de error incluye el valor de `usadaEn`.

#### Scenario: Entrada en devolución o reintegrada
- **GIVEN** una entrada del evento `42` en estado `DEVOLUCION_SOLICITADA`, `REINTEGRO_PENDIENTE` o `REINTEGRADA`
- **WHEN** personal con rol `PERSONAL_ACCESO` envía `POST /entradas/validar` con ese `codigoQr` y `eventoId = 42`
- **THEN** responde HTTP `409` con `code = "ENTRADA_NO_VALIDA"`
- **AND** el mensaje de error incluye el estado actual de la entrada.

---

### Requirement: Exclusión mutua en escaneos concurrentes
La transición `EMITIDA -> USADA` SHALL resolverse de forma atómica en la base de datos. Ante múltiples escaneos simultáneos del mismo QR para el mismo evento, exactamente uno SHALL tener éxito y todos los demás SHALL ser rechazados como entrada ya usada.

#### Scenario: N escaneos simultáneos del mismo código QR
- **GIVEN** una entrada en estado `EMITIDA` para el evento `42`
- **WHEN** N solicitudes concurrentes envían `POST /entradas/validar` con el mismo `codigoQr` y `eventoId = 42`
- **THEN** exactamente 1 solicitud responde HTTP `200` con estado `"USADA"`
- **AND** las N-1 solicitudes restantes responden HTTP `409` con `code = "ENTRADA_USADA"` (ninguna responde HTTP `500`)
- **AND** en la base de datos la entrada queda en estado `USADA` con un único `usadaEn` y `validadaPor`.

---

### Requirement: Exclusión mutua entre ingreso, devolución y cancelación
Todas las transiciones que parten del estado `EMITIDA` en `ms-ventas` (validación de ingreso `EMITIDA -> USADA`, solicitud de devolución `EMITIDA -> DEVOLUCION_SOLICITADA` y cancelación de evento `EMITIDA -> REINTEGRO_PENDIENTE`) SHALL ejecutarse mediante `UPDATE` condicional por estado `EMITIDA` en la base de datos, de modo que ante operaciones simultáneas sobre la misma entrada a lo sumo una pueda ganar.

#### Scenario: Ingreso y solicitud de devolución simultáneos sobre la misma entrada
- **GIVEN** una entrada en estado `EMITIDA` elegible para devolución (a más de 72 horas del evento) y para ingreso
- **WHEN** llegan en simultáneo `POST /entradas/validar` y `POST /devoluciones` para esa misma entrada
- **THEN** exactamente una de las dos operaciones tiene éxito y la otra es rechazada con HTTP `409` (`ENTRADA_NO_DEVOLVIBLE` si ganó el ingreso, o `ENTRADA_NO_VALIDA` si ganó la devolución)
- **AND** la base de datos queda consistente: si la entrada quedó `USADA` no existe devolución creada; si quedó `DEVOLUCION_SOLICITADA`, `usadaEn` y `validadaPor` siguen siendo nulos.

#### Scenario: Ingreso y cancelación de evento simultáneos sobre la misma entrada
- **GIVEN** una entrada en estado `EMITIDA` perteneciente al evento `42`
- **WHEN** se ejecutan en simultáneo `POST /entradas/validar` y `POST /internal/eventos/42/cancelacion`
- **THEN** a lo sumo una transición gana sobre esa fila: si ganó el ingreso la entrada queda `USADA` y no se crea un reintegro de cancelación para ella; si ganó la cancelación el ingreso recibe HTTP `409` con `code = "ENTRADA_NO_VALIDA"`.

