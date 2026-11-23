# resiliencia-pagos Specification

## Purpose
Define el comportamiento resiliente del flujo de pagos en `ms-ventas` frente a timeouts e incertidumbre de la pasarela (TS-03): idempotencia por clave de cobro, consulta previa antes de reintentar, estado `PAGO_INCIERTO` y reconciliación automática periódica con compensación.

## Requirements

### Requirement: Idempotencia de cobros hacia la pasarela
Cada intento de cobro asociado a una compra SHALL enviarse a la pasarela con una clave de idempotencia única derivada de esa compra. Reintentar un cobro o consultar su resultado usando la misma clave SHALL NOT generar un segundo débito ni una segunda transacción de cobro.

#### Scenario: Reintento con la misma clave de cobro no duplica el débito
- **GIVEN** una compra con una clave de cobro única ya procesada en la pasarela
- **WHEN** se repite el envío del cobro con la misma clave de cobro
- **THEN** la pasarela devuelve el resultado y el identificador de transacción originales
- **AND** no se genera un segundo cobro.

#### Scenario: Compras distintas generan claves de cobro distintas
- **GIVEN** dos compras diferentes realizadas por uno o más clientes
- **WHEN** cada compra envía su solicitud de cobro a la pasarela
- **THEN** cada solicitud utiliza una clave de cobro distinta e independiente.

### Requirement: Consulta de estado en la pasarela y simulación determinista
La pasarela SHALL permitir consultar el estado de un cobro por su clave de idempotencia, informando si el cobro fue aprobado (con su identificador de transacción), rechazado, no concretado (inexistente en la pasarela) o si su resultado continúa incierto. La pasarela simulada SHALL proveer mecanismos deterministas para reproducir timeouts y respuestas inciertas que posteriormente resuelven como aprobadas, rechazadas o no concretadas.

#### Scenario: Consulta de un cobro ya aprobado
- **GIVEN** una clave de cobro cuyo pago fue aprobado en la pasarela
- **WHEN** se consulta el estado de esa clave en la pasarela
- **THEN** la consulta devuelve estado aprobado junto con el identificador de transacción original.

#### Scenario: Consulta de un cobro nunca registrado
- **GIVEN** una clave de cobro que nunca se registró en la pasarela
- **WHEN** se consulta el estado de esa clave en la pasarela
- **THEN** la consulta informa que el cobro no se concretó.

#### Scenario: Simulación determinista de timeout con resolución posterior
- **GIVEN** un escenario determinista configurado para responder con timeout e incertidumbre durante una cantidad definida de intentos y luego resolver como aprobado o rechazado
- **WHEN** se ejecuta el cobro y las consultas sucesivas con la misma clave de cobro
- **THEN** las consultas iniciales reproducen la incertidumbre de forma predecible
- **AND** al agotarse la ventana simulada la consulta devuelve el resultado final esperado sin duplicar el cobro.

### Requirement: Consulta previa antes de reintentar ante timeout de pasarela
Ante un timeout de la pasarela, el sistema SHALL consultar primero el estado del cobro con la clave de idempotencia de la compra antes de reintentar, dentro de un límite máximo de intentos configurado. Si obtiene un resultado definitivo (aprobado o rechazado), SHALL resolver sin reenviar el cobro; solo MAY reintentar el cobro con la misma clave si la pasarela confirma que no fue registrado, y SHALL NOT enviar cobros duplicados mientras el estado siga incierto.

#### Scenario: Timeout resuelto por consulta inmediata sin reenviar cobro
- **GIVEN** una reserva vigente y un cobro inicial que falla por timeout pero quedó aprobado en la pasarela
- **WHEN** el sistema consulta el estado del cobro en la pasarela antes de reintentar
- **THEN** recibe la aprobación con el identificador de transacción existente
- **AND** no envía un segundo cobro a la pasarela
- **AND** confirma la reserva, emite las entradas con QR único y completa la compra como `APROBADA`.

#### Scenario: Timeout con cobro no registrado permite reintento controlado
- **GIVEN** una reserva vigente y un cobro inicial que falla por timeout sin haberse registrado en la pasarela
- **WHEN** el sistema consulta el estado, verifica que el cobro no se concretó y reintenta con la misma clave dentro del límite máximo de reintentos
- **THEN** si el reintento aprueba, confirma la reserva, emite las entradas y completa la compra como `APROBADA` sin doble cobro.

#### Scenario: Incertidumbre durante todos los intentos respeta el límite sin cobrar a ciegas
- **GIVEN** una reserva vigente cuyo cobro inicial falla por timeout y cuyas consultas posteriores continúan inciertas o con timeout
- **WHEN** se alcanza el límite máximo de intentos configurado
- **THEN** el sistema detiene las consultas
- **AND** no envía cobros adicionales mientras el estado sea incierto.

### Requirement: Estado PAGO_INCIERTO y preservación de reserva
Cuando la incertidumbre sobre el resultado de un cobro persiste tras agotar las consultas y reintentos permitidos, el sistema SHALL guardar la compra en estado `PAGO_INCIERTO` (registrando el intento de pago en estado incierto con el tipo de medio de pago, últimos 4 dígitos y monto, sin exponer el token), SHALL NOT liberar todavía la reserva asociada y SHALL responder HTTP `504 Gateway Timeout` con código `PASARELA_TIMEOUT` y el formato de error estándar.

#### Scenario: Timeout persistente transiciona la compra a PAGO_INCIERTO con HTTP 504
- **GIVEN** una reserva vigente del cliente
- **WHEN** se envía `POST /compras` y la pasarela permanece en timeout o estado incierto hasta agotar los intentos configurados
- **THEN** el sistema responde HTTP `504 Gateway Timeout` con código `PASARELA_TIMEOUT`
- **AND** la compra queda persistida en estado `PAGO_INCIERTO` con su pago registrado en estado `INCIERTO`
- **AND** la reserva no se libera ni se emiten entradas.

#### Scenario: Reintento del cliente con misma Idempotency-Key sobre compra en PAGO_INCIERTO
- **GIVEN** una compra previamente guardada en estado `PAGO_INCIERTO` cuya reserva sigue vigente y cuyo pago ya figura aprobado al consultar la pasarela
- **WHEN** el cliente reenvía `POST /compras` con la misma `Idempotency-Key` y el mismo cuerpo
- **THEN** el sistema consulta el estado en la pasarela usando la misma clave de cobro sin generar un segundo cobro
- **AND** confirma la reserva, emite las entradas con QR único, pasa la compra a `APROBADA` y responde HTTP `201`.

#### Scenario: Reintento del cliente con cuerpo distinto sobre compra en PAGO_INCIERTO
- **GIVEN** una compra en estado `PAGO_INCIERTO` registrada con una `Idempotency-Key`
- **WHEN** el cliente envía `POST /compras` con la misma `Idempotency-Key` y un cuerpo distinto
- **THEN** el sistema responde HTTP `422 Unprocessable Entity` con código `IDEMPOTENCY_KEY_REUTILIZADA`.

### Requirement: Reconciliación automática de compras en PAGO_INCIERTO
Un proceso programado periódico SHALL consultar en la pasarela el estado de cada compra en `PAGO_INCIERTO`, aislando fallos entre compras. Si el pago fue aprobado SHALL confirmar la reserva, emitir entradas con `codigoQr` único y pasar a `APROBADA` (o reembolsar y pasar a `FALLIDA` si la reserva expiró); si fue rechazado o no se concretó SHALL liberar la reserva y pasar a `RECHAZADA` o `FALLIDA`; si sigue incierto SHALL mantener `PAGO_INCIERTO`.

#### Scenario: Reconciliación resuelve compra aprobada
- **GIVEN** una compra persistida en estado `PAGO_INCIERTO` con su reserva vigente
- **AND** la pasarela informa al consultar estado que el cobro fue `APROBADO` con un identificador de transacción
- **WHEN** se ejecuta el proceso de reconciliación
- **THEN** el sistema confirma la reserva
- **AND** emite una entrada con `codigoQr` único por cada lugar reservado
- **AND** actualiza el pago a `APROBADO` con el identificador de transacción y la compra a `APROBADA`.

#### Scenario: Reconciliación compensa con reembolso cuando la reserva venció
- **GIVEN** una compra persistida en estado `PAGO_INCIERTO` cuya reserva ya venció o fue liberada
- **AND** la pasarela informa que el cobro fue `APROBADO` con un identificador de transacción
- **WHEN** se ejecuta el proceso de reconciliación
- **THEN** el sistema solicita el reembolso de la transacción en la pasarela
- **AND** no emite entradas
- **AND** actualiza el pago a `REEMBOLSADO` y la compra a `FALLIDA`.

#### Scenario: Reconciliación resuelve compra rechazada y libera reserva
- **GIVEN** una compra persistida en estado `PAGO_INCIERTO`
- **AND** la pasarela informa que el cobro fue `RECHAZADO`
- **WHEN** se ejecuta el proceso de reconciliación
- **THEN** el sistema libera la reserva
- **AND** no emite entradas
- **AND** actualiza el pago a `RECHAZADO` y la compra a `RECHAZADA`.

#### Scenario: Reconciliación resuelve cobro no concretado y libera reserva
- **GIVEN** una compra persistida en estado `PAGO_INCIERTO`
- **AND** la pasarela informa que el cobro no se concretó
- **WHEN** se ejecuta el proceso de reconciliación
- **THEN** el sistema libera la reserva
- **AND** no emite entradas
- **AND** actualiza el pago a `FALLIDO` y la compra a `FALLIDA`.

#### Scenario: Reconciliación preserva PAGO_INCIERTO si persiste la incertidumbre y aísla errores
- **GIVEN** dos compras en estado `PAGO_INCIERTO`, donde la primera sigue con timeout/incertidumbre en la pasarela y la segunda ya figura `APROBADO`
- **WHEN** se ejecuta el proceso de reconciliación
- **THEN** la primera compra permanece en `PAGO_INCIERTO` sin liberar su reserva
- **AND** la segunda compra se resuelve normalmente a `APROBADA` con sus entradas emitidas.
