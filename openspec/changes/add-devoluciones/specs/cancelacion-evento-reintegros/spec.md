# Spec Delta

## Purpose
Define el reintegro de las entradas cuando se cancela un evento.

## ADDED Requirements

### Requirement: Reintegro masivo
Cuando ms-ventas recibe la cancelación de un evento, el sistema SHALL pasar sus entradas EMITIDA a REINTEGRO_PENDIENTE y solicitar el reintegro de cada una, que pasa a REINTEGRADA al confirmarse.

#### Scenario: Evento con ventas
- **WHEN** se cancela un evento con 50 entradas
- **THEN** se solicita el reintegro de las 50
- **AND** las confirmadas quedan REINTEGRADA

### Requirement: Idempotencia
Si ms-ventas recibe más de una vez el aviso de cancelación del mismo evento, entonces el sistema SHALL reintegrar cada entrada una sola vez.

#### Scenario: Aviso repetido
- **WHEN** llega dos veces el mismo aviso
- **THEN** no hay reintegros duplicados

### Requirement: Cancelación repetida
Si un ADMIN cancela un evento ya CANCELADO, entonces el sistema SHALL responder 409 y no iniciar reintegros nuevos.

#### Scenario: Segunda cancelación
- **WHEN** se cancela de nuevo el evento
- **THEN** el sistema responde 409

### Requirement: Fallas parciales
Si la pasarela falla para algunas entradas, entonces el sistema SHALL dejarlas REINTEGRO_PENDIENTE, identificadas para reintento, y continuar con las demás.

#### Scenario: Falla parcial
- **WHEN** falla un reintegro entre 50
- **THEN** los otros 49 se completan
- **AND** el fallido queda REINTEGRO_PENDIENTE

### Requirement: Reservas y ventas posteriores
Cuando el evento se cancela, el sistema SHALL liberar las reservas pendientes y rechazar con 409 nuevas reservas o compras.

#### Scenario: Compra posterior
- **WHEN** se intenta reservar en un evento CANCELADO
- **THEN** el sistema responde 409

### Requirement: Compra en proceso de pago
Si se cancela el evento mientras una compra está en proceso de pago, entonces el sistema SHALL reembolsar el pago al rechazarse la confirmación y no emitir entradas.

#### Scenario: Cancelación durante el pago
- **WHEN** el pago se aprueba y el evento se cancela antes de confirmar
- **THEN** se reembolsa el pago
- **AND** no se emiten entradas
