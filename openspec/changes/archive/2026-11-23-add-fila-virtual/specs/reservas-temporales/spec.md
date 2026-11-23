# Spec Delta

## ADDED Requirements

### Requirement: Fila virtual al reservar
Antes de reservar, `ms-reservas` SHALL consultar a `ms-fila` el estado del cliente para el evento. Si la respuesta es `activa` `true` y `habilitado` `false`, SHALL responder `403` con código `FILA_NO_HABILITADO` y SHALL NOT tomar ningún lugar. Los errores de identidad, rol y validación del pedido SHALL responderse antes de consultar a `ms-fila`.

#### Scenario: Cliente en espera intenta reservar
- **WHEN** el evento 42 tiene la fila activa, `ms-fila` informa `habilitado` `false` para el cliente `101` y ese cliente reserva dos asientos libres
- **THEN** el sistema responde `403` con código `FILA_NO_HABILITADO`
- **AND** los dos asientos siguen `LIBRE` y no se crea ninguna reserva

#### Scenario: Cliente habilitado reserva
- **WHEN** el evento 42 tiene la fila activa, `ms-fila` informa `habilitado` `true` para el cliente `101` y ese cliente reserva dos asientos libres
- **THEN** el sistema responde `201` con la reserva `PENDIENTE`

#### Scenario: Evento sin fila activa
- **WHEN** `ms-fila` informa `activa` `false` para el evento 42 y el cliente `101` reserva dos asientos libres
- **THEN** el sistema responde `201` igual que sin fila virtual

#### Scenario: Organizador reserva en un evento con fila
- **WHEN** un usuario con `X-User-Rol` `ORGANIZADOR` intenta reservar en el evento 42
- **THEN** el sistema responde `403` con código `ACCESO_DENEGADO` sin consultar a `ms-fila`

#### Scenario: Pedido inválido en un evento con fila
- **WHEN** el cliente `101`, no habilitado en la fila activa del evento 42, envía un pedido con un asiento repetido
- **THEN** el sistema responde `400` con código `VALIDACION`

### Requirement: Reserva sin bloqueo cuando la fila no responde
Si `ms-fila` no responde dentro de 1 segundo, devuelve un error o una respuesta ilegible, `ms-reservas` SHALL continuar con la reserva como si la fila no estuviera activa (fail-open) y SHALL registrar una advertencia con el evento, el cliente, la causa y el `correlationId`.

#### Scenario: ms-fila detenido
- **WHEN** `ms-fila` no acepta conexiones y el cliente `101` reserva dos asientos libres del evento 42
- **THEN** el sistema responde `201` con la reserva `PENDIENTE`
- **AND** queda registrada una advertencia con `eventoId=42`, `clienteId=101` y el `correlationId` de la solicitud

#### Scenario: ms-fila lento
- **WHEN** `ms-fila` tarda 5 segundos en responder y el cliente `101` reserva dos asientos libres del evento 42
- **THEN** el sistema responde `201` sin esperar más de 1 segundo a `ms-fila`

#### Scenario: ms-fila responde con error
- **WHEN** `ms-fila` responde `503` y el cliente `101` reserva dos asientos libres del evento 42
- **THEN** el sistema responde `201` y registra la advertencia

### Requirement: Correlación en la consulta a la fila
La consulta de `ms-reservas` a `ms-fila` SHALL llevar el mismo `X-Correlation-Id` de la solicitud de reserva que la originó.

#### Scenario: Propagación del identificador a ms-fila
- **WHEN** el cliente `101` reserva con `X-Correlation-Id` `abc-123`
- **THEN** `ms-fila` recibe la consulta de estado con `X-Correlation-Id` `abc-123`
