# reservas-temporales Specification

## Purpose
Define la reserva temporal de lugares con vencimiento, consulta, cancelación, confirmación y liberación.

## Requirements

### Requirement: Reserva de asientos
Cuando un CLIENTE reserva asientos libres de un sector numerado de un evento abierto, el sistema SHALL marcarlos RESERVADO, crear una reserva PENDIENTE con vencimiento a 10 minutos desde su creación y responder 201 con reservaId, items y expiraEn.

#### Scenario: Reserva exitosa
- **WHEN** se reservan dos asientos libres
- **THEN** el sistema responde 201 con estado PENDIENTE
- **AND** expiraEn es 10 minutos después de la creación

### Requirement: Reserva por cantidad
Cuando un CLIENTE reserva una cantidad en un sector no numerado con cupo suficiente, el sistema SHALL descontar esa cantidad del cupo y responder 201 con la reserva PENDIENTE.

#### Scenario: Cupo descontado
- **WHEN** se reservan 3 lugares de un sector con 10 disponibles
- **THEN** quedan 7 disponibles

#### Scenario: Cupo insuficiente
- **WHEN** se piden 5 lugares y quedan 3
- **THEN** el sistema responde 409 CUPO_INSUFICIENTE
- **AND** el cupo no cambia

### Requirement: Pedido inválido
Si la cantidad es menor o igual a 0, hay asientos repetidos, un asiento no existe en el sector, o se envían asientos en un sector no numerado o cantidad en uno numerado, entonces el sistema SHALL responder 400.

#### Scenario: Asiento repetido
- **WHEN** el mismo asiento aparece dos veces
- **THEN** el sistema responde 400

#### Scenario: Cantidad en sector numerado
- **WHEN** se envía cantidad en un sector numerado
- **THEN** el sistema responde 400

### Requirement: Todo o nada
Si algún asiento pedido no está libre, entonces el sistema SHALL responder 409 ASIENTO_NO_DISPONIBLE con los asientos en conflicto y no dejar ninguno reservado.

#### Scenario: Un asiento ocupado
- **WHEN** se piden tres asientos y uno está reservado
- **THEN** el sistema responde 409 con el asiento en details
- **AND** los otros dos siguen LIBRE

### Requirement: Evento no reservable
Si el evento no tiene inventario, el sistema SHALL responder 404; si su inventario está cerrado, SHALL responder 409 EVENTO_NO_DISPONIBLE.

#### Scenario: Evento cerrado
- **WHEN** se reserva en un evento suspendido
- **THEN** el sistema responde 409

### Requirement: Rol del solicitante
El sistema SHALL aceptar reservar solo con rol CLIENTE; con otro rol SHALL responder 403 y sin los headers de identidad, 400.

#### Scenario: Organizador reserva
- **WHEN** X-User-Rol es ORGANIZADOR
- **THEN** el sistema responde 403

### Requirement: Consulta de reserva
Cuando el cliente dueño consulta su reserva, el sistema SHALL devolver su estado, cantidad, ítems y vencimiento; si no existe, SHALL responder 404 y si es de otro cliente, 403.

#### Scenario: Reserva ajena
- **WHEN** otro cliente consulta la reserva
- **THEN** el sistema responde 403

### Requirement: Cancelación
Cuando el cliente dueño cancela una reserva PENDIENTE, el sistema SHALL pasarla a LIBERADA, devolver sus lugares y responder 204; si no existe, 404; si es de otro cliente, 403; si está CONFIRMADA, 409 RESERVA_CONFIRMADA.

#### Scenario: Cancelación propia
- **WHEN** el dueño cancela una reserva PENDIENTE
- **THEN** el sistema responde 204
- **AND** los lugares vuelven a LIBRE

#### Scenario: Reserva confirmada
- **WHEN** se cancela una reserva CONFIRMADA
- **THEN** el sistema responde 409 RESERVA_CONFIRMADA

### Requirement: Cancelación repetida
Si la reserva ya está LIBERADA o VENCIDA, entonces el sistema SHALL responder 204 sin modificar el inventario por segunda vez.

#### Scenario: Doble cancelación
- **WHEN** se cancela dos veces la misma reserva
- **THEN** la disponibilidad aumenta una sola vez

### Requirement: Vencimiento
Mientras una reserva esté PENDIENTE y llegue su vencimiento, el sistema SHALL pasarla a VENCIDA, devolver sus asientos a LIBRE y sumar de nuevo el cupo de los sectores no numerados, en una única transacción.

#### Scenario: Reserva vencida
- **WHEN** pasan 10 minutos sin pagar y corre el proceso
- **THEN** la reserva queda VENCIDA
- **AND** los lugares vuelven a LIBRE

#### Scenario: Reserva confirmada no vence
- **WHEN** corre el proceso sobre una reserva CONFIRMADA
- **THEN** no se modifica

### Requirement: Confirmación
Cuando ms-ventas confirma una reserva PENDIENTE dentro de su plazo, el sistema SHALL pasar sus lugares a VENDIDO y la reserva a CONFIRMADA y responder 200; si ya estaba CONFIRMADA, SHALL responder 200 sin cambios.

#### Scenario: A tiempo
- **WHEN** se confirma antes del vencimiento
- **THEN** los lugares pasan a VENDIDO

#### Scenario: Confirmación repetida
- **WHEN** se confirma una reserva ya CONFIRMADA
- **THEN** el sistema responde 200 sin cambios

### Requirement: Confirmación rechazada
Si la reserva venció, entonces el sistema SHALL responder 410 RESERVA_VENCIDA; si fue liberada, SHALL responder 409; si no existe, 404.

#### Scenario: Vencida
- **WHEN** se confirma después del vencimiento
- **THEN** el sistema responde 410

#### Scenario: Liberada por cierre
- **WHEN** se confirma una reserva liberada porque el evento se canceló
- **THEN** el sistema responde 409

### Requirement: Liberación interna
Cuando ms-ventas libera una reserva PENDIENTE por una compensación, el sistema SHALL devolver sus lugares y dejarla LIBERADA; sobre una reserva ya LIBERADA o VENCIDA SHALL responder 200 sin cambios y sobre una CONFIRMADA, 409.

#### Scenario: Liberación repetida
- **WHEN** se libera dos veces la misma reserva
- **THEN** el inventario se modifica una sola vez

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
