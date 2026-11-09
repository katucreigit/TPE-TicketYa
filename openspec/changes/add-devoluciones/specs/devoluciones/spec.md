# Spec Delta

## Purpose
Define cuándo y cómo se solicita y se resuelve la devolución de una entrada.

## ADDED Requirements

### Requirement: Solicitud en plazo
Cuando el dueño de una entrada EMITIDA solicita la devolución con 72 horas o más de anticipación, el sistema SHALL crear la devolución SOLICITADA, pasar la entrada a DEVOLUCION_SOLICITADA y responder 201.

#### Scenario: En plazo
- **WHEN** faltan 5 días
- **THEN** la devolución queda SOLICITADA
- **AND** la entrada queda DEVOLUCION_SOLICITADA

#### Scenario: Exactamente 72 horas
- **WHEN** faltan exactamente 72 horas
- **THEN** la solicitud se permite

### Requirement: Fuera de plazo
Si faltan menos de 72 horas respecto de la fecha y hora exactas del evento, entonces el sistema SHALL responder 422.

#### Scenario: Menos de 72 horas
- **WHEN** faltan 48 horas
- **THEN** el sistema responde 422

### Requirement: Entrada no devolvible
Si la entrada está USADA, ya devuelta o con una devolución en curso, entonces el sistema SHALL responder 409; si es de otro cliente, 403.

#### Scenario: Devolución en curso
- **WHEN** se solicita otra devolución de la misma entrada
- **THEN** el sistema responde 409

#### Scenario: Entrada ajena
- **WHEN** otro cliente solicita la devolución
- **THEN** el sistema responde 403

### Requirement: Uso bloqueado
Mientras la entrada tenga una devolución solicitada o en proceso, el sistema SHALL rechazar su uso para ingresar al evento.

#### Scenario: Ingreso
- **WHEN** se intenta usar una entrada DEVOLUCION_SOLICITADA
- **THEN** se rechaza

### Requirement: Aprobación
Cuando un ADMIN aprueba una devolución SOLICITADA, el sistema SHALL pasarla a EN_PROCESO, reintegrar el importe y liberar el lugar, y dejarla REINTEGRADA al completar ambos pasos.

#### Scenario: Aprobar
- **WHEN** un ADMIN aprueba
- **THEN** pasa a EN_PROCESO
- **AND** al terminar queda REINTEGRADA y el lugar LIBRE

### Requirement: Falla del reintegro
Si la pasarela falla al reintegrar, entonces el sistema SHALL responder 502 y dejar la devolución EN_PROCESO; al aprobar de nuevo SHALL reintentar sin devolver el dinero dos veces.

#### Scenario: Reintento
- **WHEN** la pasarela falla y se aprueba otra vez
- **THEN** el reintegro se completa una sola vez

### Requirement: Falla de la liberación
Si el reintegro se hizo pero falla la liberación del lugar, entonces el sistema SHALL reintentar solo la liberación al aprobar de nuevo.

#### Scenario: Reintento parcial
- **WHEN** ms-reservas no responde tras el reintegro
- **THEN** al reintentar solo se libera el lugar

### Requirement: Rechazo
Cuando un ADMIN rechaza con motivo una devolución SOLICITADA, el sistema SHALL dejarla RECHAZADA y devolver la entrada a EMITIDA.

#### Scenario: Rechazar
- **WHEN** un ADMIN rechaza con motivo
- **THEN** la entrada vuelve a EMITIDA

### Requirement: Resolución repetida y permisos
Si la devolución ya está REINTEGRADA o RECHAZADA, el sistema SHALL responder 409; si quien resuelve no es ADMIN, 403.

#### Scenario: Ya resuelta
- **WHEN** se aprueba una devolución REINTEGRADA
- **THEN** el sistema responde 409
