# Spec Delta

## Purpose
Define las transiciones de estado del evento después de publicarlo.

## ADDED Requirements

### Requirement: Suspensión
Cuando un ADMIN suspende un evento PUBLICADO o AGOTADO, el sistema SHALL pasarlo a SUSPENDIDO, ocultarlo de las búsquedas y cerrar nuevas reservas.

#### Scenario: Suspender
- **WHEN** un ADMIN suspende un evento PUBLICADO
- **THEN** pasa a SUSPENDIDO
- **AND** no admite nuevas reservas

### Requirement: Reanudación
Cuando un ADMIN reanuda un evento SUSPENDIDO, el sistema SHALL devolverlo a PUBLICADO y reabrir su inventario.

#### Scenario: Reanudar
- **WHEN** se reanuda un evento SUSPENDIDO
- **THEN** vuelve a PUBLICADO

### Requirement: Cancelación
Cuando un ADMIN cancela con motivo un evento no finalizado ni cancelado, el sistema SHALL pasarlo a CANCELADO, liberar sus reservas pendientes y disparar el reintegro de sus entradas.

#### Scenario: Cancelar
- **WHEN** un ADMIN cancela un evento PUBLICADO
- **THEN** pasa a CANCELADO
- **AND** se inicia el reintegro de las entradas

#### Scenario: Cancelar dos veces
- **WHEN** se cancela un evento ya CANCELADO
- **THEN** el sistema responde 409 sin iniciar reintegros nuevos

### Requirement: Permisos de estado
Si quien suspende o cancela no es ADMIN, entonces el sistema SHALL responder 403; cancelar un evento FINALIZADO SHALL responder 409.

#### Scenario: No administrador
- **WHEN** un ORGANIZADOR intenta suspender
- **THEN** el sistema responde 403

### Requirement: Agotado
Cuando ms-reservas informa que no queda ningún lugar disponible, el sistema SHALL pasar el evento a AGOTADO; cuando se libera alguno, SHALL volver a PUBLICADO.

#### Scenario: Reserva vencida
- **WHEN** un evento AGOTADO por reservas temporales recupera un lugar al vencer una reserva
- **THEN** vuelve a PUBLICADO

### Requirement: Finalización automática
Cuando corre el proceso programado, el sistema SHALL pasar a FINALIZADO los eventos PUBLICADO, AGOTADO o SUSPENDIDO cuya fecha y hora ya pasaron, sin modificar CANCELADOS ni repetir efectos.

#### Scenario: Evento futuro
- **WHEN** el evento es posterior a ahora
- **THEN** no se finaliza

#### Scenario: Corrida repetida
- **WHEN** el proceso corre dos veces
- **THEN** no hay efectos duplicados
