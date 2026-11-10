# Spec Delta

## Purpose
Define el contrato y comportamiento de la pasarela de pago simulada (Mock) y sus compensaciones para la Entrega 1.

## ADDED Requirements

### Requirement: Procesamiento de cobro por token
La pasarela SHALL procesar peticiones de cobro evaluando el token recibido:
- `tok_ok`: Aprueba la transacción inmediatamente (HTTP 200 con ID de transacción).
- `tok_rechazado`: Rechaza el pago (HTTP 402 / estado RECHAZADO).
- `tok_timeout`: Registra internamente la transacción como cobrada pero demora la respuesta hasta forzar un timeout (HTTP 504).
- Token no reconocido: Retorna error de validación (HTTP 400).

#### Scenario: Cobro exitoso
- **GIVEN** un token de pago válido "tok_ok" y un monto positivo
- **WHEN** se envía la solicitud de cobro a la pasarela
- **THEN** la pasarela responde HTTP 200 con estado "APROBADO" y un ID de transacción único.

#### Scenario: Cobro rechazado
- **GIVEN** un token de pago denegado "tok_rechazado"
- **WHEN** se envía la solicitud de cobro a la pasarela
- **THEN** la pasarela responde HTTP 402 con estado "RECHAZADO".

#### Scenario: Cobro con respuesta demorada (Timeout)
- **GIVEN** un token "tok_timeout" y una clave de cobro "cobro-123"
- **WHEN** se envía la solicitud de cobro por primera vez
- **THEN** la pasarela persiste internamente el cobro como exitoso
- **AND** no responde antes del tiempo límite configurado (responde HTTP 504 o fuerza timeout).

#### Scenario: Token no soportado
- **GIVEN** un token arbitrario "tok_tarjeta_invalida"
- **WHEN** se envía la solicitud de cobro
- **THEN** la pasarela responde HTTP 400 Bad Request.

---

### Requirement: Idempotencia de cobro
La pasarela SHALL registrar cada transacción vinculada a una clave de cobro única enviada por el llamante. Si recibe una solicitud con una clave ya registrada, devolverá el resultado almacenado sin generar un cobro adicional.

#### Scenario: Reintento de cobro previamente aprobado
- **GIVEN** una clave de cobro "cobro-100" previamente procesada con "tok_ok"
- **WHEN** se envía una nueva solicitud de cobro con la misma clave "cobro-100"
- **THEN** devuelve la respuesta HTTP 200 original
- **AND** no se duplica la transacción en la pasarela.

#### Scenario: Reintento tras timeout
- **GIVEN** una clave de cobro "cobro-123" previamente registrada bajo "tok_timeout"
- **WHEN** se reintenta el cobro enviando la misma clave "cobro-123"
- **THEN** la pasarela devuelve inmediatamente HTTP 200 con el cobro ya registrado
- **AND** no genera un segundo débito.

---

### Requirement: Reembolso de cobro (Compensación de Saga)
La pasarela SHALL soportar una operación de reembolso referenciando el ID de transacción original.

#### Scenario: Reembolso exitoso de una transacción existente
- **GIVEN** una transacción de cobro previa en estado "APROBADO" con ID "tx-789"
- **WHEN** se solicita el reembolso indicando "tx-789"
- **THEN** la pasarela responde HTTP 200 confirmando el reintegro.

#### Scenario: Reintento de reembolso sobre una transacción ya reembolsada
- **GIVEN** una transacción con ID "tx-789" que ya fue reembolsada
- **WHEN** se vuelve a solicitar el reembolso de "tx-789"
- **THEN** la pasarela responde HTTP 200 con el resultado original sin duplicar el reintegro.