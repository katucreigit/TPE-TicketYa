# Spec Delta

## Purpose
Define el pago de una reserva coordinado entre servicios (`ms-reservas`, `ms-eventos`), con pasarela simulada, compensaciones, datos de pago e idempotencia.

## ADDED Requirements

### Requirement: Compra exitosa y emisión
Cuando el cliente dueño paga una reserva vigente y la pasarela aprueba, el sistema SHALL obtener el snapshot del evento, confirmar la reserva en `ms-reservas`, marcar la compra como APROBADA, emitir una entrada por lugar con QR único y responder 201.

#### Scenario: Flujo feliz completo
- **GIVEN** una reserva PENDIENTE y vigente del cliente "cli-100"
- **AND** datos válidos de evento obtenidos de ms-eventos
- **WHEN** el cliente envía POST /compras con token "tok_ok"
- **THEN** el sistema responde HTTP 201
- **AND** la reserva pasa a CONFIRMADA en ms-reservas
- **AND** la compra queda en estado APROBADA
- **AND** se emite una entrada con QR único por cada asiento o cupo.

### Requirement: Manejo de datos del medio de pago
El sistema SHALL persistir y responder únicamente el tipo de medio de pago y los últimos cuatro dígitos recibidos, descartando el token y sin almacenar nunca numeraciones completas.

#### Scenario: Registro seguro de tarjeta
- **GIVEN** un intento de compra con token válido y tarjeta terminada en "4242"
- **WHEN** se procesa y consulta la compra
- **THEN** la respuesta expone tipo y "4242"
- **AND** ningún registro expone el token transaccional.

### Requirement: Pago rechazado
Si la pasarela rechaza el pago, el sistema SHALL solicitar la liberación de la reserva en `ms-reservas`, marcar la compra como RECHAZADA y responder 402.

#### Scenario: Cobro denegado por pasarela
- **GIVEN** una reserva vigente del cliente "cli-100"
- **WHEN** se envía POST /compras con token "tok_rechazado"
- **THEN** el sistema responde HTTP 402 Payment Required
- **AND** la compra queda en estado RECHAZADA
- **AND** se libera la reserva devolviendo los lugares a LIBRE.

### Requirement: Reserva vencida y compensación
Si la reserva venció antes del cobro, el sistema SHALL responder 410 sin invocar a la pasarela y marcar la compra FALLIDA. Si la reserva vence durante el cobro con el pago ya aprobado, el sistema SHALL reembolsar el cobro en la pasarela, marcar la compra FALLIDA y responder 410.

#### Scenario: Vencida previo al cobro
- **GIVEN** una reserva cuya fecha de expiración ya pasó
- **WHEN** el cliente envía el cobro
- **THEN** el sistema responde HTTP 410 Gone
- **AND** no se envía solicitud de cobro a la pasarela.

#### Scenario: Vencida en vuelo (Compensación con reembolso)
- **GIVEN** una reserva que vence exactamente mientras la pasarela procesa el cobro
- **AND** la pasarela aprobó el pago con un ID de transacción
- **WHEN** ms-reservas rechaza la confirmación por expiración
- **THEN** el sistema solicita el reembolso inmediato a la pasarela
- **AND** la compra queda en estado FALLIDA
- **AND** responde HTTP 410 Gone.

### Requirement: Falla de comunicación con la pasarela
Si la pasarela no responde a tiempo, el sistema SHALL responder 504, mantener la compra PENDIENTE y conservar la reserva vigente. Si la pasarela es inalcanzable, SHALL responder 502 o 503 manteniendo la compra PENDIENTE.

#### Scenario: Timeout de pasarela
- **GIVEN** una reserva vigente
- **WHEN** se intenta cobrar con token "tok_timeout"
- **THEN** el sistema responde HTTP 504 Gateway Timeout
- **AND** la compra permanece en estado PENDIENTE
- **AND** la reserva no se libera.

#### Scenario: Reintento exitoso tras timeout
- **GIVEN** una compra previa en estado PENDIENTE por timeout con reserva aún vigente
- **WHEN** se reintenta el cobro con la misma Idempotency-Key
- **THEN** se reutiliza la misma compra y clave de cobro hacia la pasarela
- **AND** se confirma la reserva y emiten las entradas sin duplicar cobros.

#### Scenario: Reintento tras timeout con reserva vencida
- **GIVEN** una compra previa en PENDIENTE por timeout cuya reserva ya venció
- **WHEN** el cliente reintenta con la misma Idempotency-Key
- **THEN** el sistema responde HTTP 410
- **AND** la compra pasa a estado FALLIDA sin emitir cobros nuevos.

### Requirement: Validación de titularidad de la reserva
Si el cliente autenticado intenta pagar una reserva que pertenece a otro usuario, el sistema SHALL responder 403.

#### Scenario: Intento de pago de reserva ajena
- **GIVEN** una reserva perteneciente al cliente "cli-200"
- **WHEN** el cliente "cli-100" intenta pagarla enviando su propio X-User-Id
- **THEN** el sistema responde HTTP 403 Forbidden
- **AND** no se ejecuta cobro ni cambio de estado.

### Requirement: Control de idempotencia
Toda solicitud POST /compras SHALL exigir el header `Idempotency-Key`. Ante una clave ya registrada para el mismo cliente con cuerpo idéntico, el sistema SHALL retornar la respuesta original almacenada sin invocar pasarela ni duplicar cobros o entradas; si la clave se repite con un cuerpo distinto, el sistema SHALL responder 422 Unprocessable Entity; y si el header está ausente o vacío, el sistema SHALL responder 400 Bad Request.

#### Scenario: Reintento legítimo con respuesta cacheada
- **GIVEN** una compra previa exitosa asociada a la clave "k-100"
- **WHEN** ingresa una nueva solicitud con la clave "k-100" y body idéntico
- **THEN** responde con el código y payload original
- **AND** no se realizan llamadas externas ni se duplican entradas.

#### Scenario: Conflicto por cambio de payload
- **GIVEN** una clave "k-100" previamente usada para la reserva "res-1"
- **WHEN** ingresa la misma clave "k-100" para la reserva "res-2"
- **THEN** el sistema responde HTTP 422 Unprocessable Entity.

#### Scenario: Solicitud sin header de idempotencia
- **WHEN** se envía POST /compras sin Idempotency-Key
- **THEN** el sistema responde HTTP 400 Bad Request.

### Requirement: Rechazo inmediato de solicitudes concurrentes
Cuando dos o más peticiones ingresan simultáneamente compartiendo la misma clave de idempotencia y cliente, el sistema SHALL procesar una sola y rechazar de inmediato las concurrentes con HTTP 409 Conflict.

#### Scenario: Colisión simultánea por la misma clave
- **GIVEN** una clave no registrada "k-simultanea"
- **WHEN** ingresan dos peticiones idénticas en paralelo
- **THEN** una de ellas es aceptada para procesamiento
- **AND** la otra es rechazada inmediatamente con HTTP 409 Conflict.
