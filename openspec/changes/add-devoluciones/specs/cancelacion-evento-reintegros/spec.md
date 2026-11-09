# Spec Delta

## Purpose
Define qué ocurre con las entradas vendidas cuando el organizador cancela un evento.

## ADDED Requirements

### Requirement: Cancelación de evento
Cuando un ORGANIZADOR dueño o ADMIN cancela un evento, el sistema SHALL pasarlo a CANCELADO y reintegrar todas las entradas vendidas.

#### Scenario: Evento con ventas
- **WHEN** se cancela un evento con 50 entradas vendidas
- **THEN** el evento queda CANCELADO
- **AND** se genera un reintegro por cada entrada

### Requirement: Bloqueo de nuevas ventas
Mientras un evento esté CANCELADO, el sistema SHALL rechazar nuevas reservas y compras.

#### Scenario: Compra posterior
- **WHEN** se intenta comprar un evento cancelado
- **THEN** el sistema responde 409

### Requirement: Tolerancia a fallas
Si el reintegro de una entrada falla, entonces el sistema SHALL dejarlo PENDIENTE y continuar con las demás.

#### Scenario: Falla parcial
- **WHEN** falla un reintegro entre 50
- **THEN** los otros 49 se completan
- **AND** el fallido queda PENDIENTE para reintento

### Requirement: Reintento sin duplicar
Cuando se reintenta un reintegro fallido, el sistema SHALL reutilizar el registro existente de esa entrada y tipo.

#### Scenario: Reintento
- **WHEN** se reintenta el reintegro pendiente
- **THEN** se actualiza el mismo registro
- **AND** no se crea un segundo reintegro
