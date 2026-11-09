# Spec Delta

## Purpose
Define la emisión de entradas con QR y su consulta por el dueño.

## ADDED Requirements

### Requirement: Una entrada por lugar
Cuando la compra se aprueba, el sistema SHALL emitir una entrada EMITIDA por cada lugar comprado, con el precio unitario y la fecha del evento de ese momento.

#### Scenario: Tres lugares
- **WHEN** se compran tres lugares
- **THEN** se emiten tres entradas EMITIDA

### Requirement: QR único
El sistema SHALL generar para cada entrada un código QR único que no se pueda modificar ni reutilizar en otra entrada.

#### Scenario: Unicidad
- **WHEN** se emiten varias entradas
- **THEN** no hay dos códigos iguales

### Requirement: Consulta propia
Cuando un cliente consulta sus compras o entradas, el sistema SHALL devolver solo las suyas con su estado actual y su QR; si consulta una ajena SHALL responder 403.

#### Scenario: Entrada ajena
- **WHEN** otro cliente consulta la entrada por id
- **THEN** el sistema responde 403

### Requirement: Entradas no válidas
El sistema SHALL tratar como no válidas para ingresar las entradas DEVOLUCION_SOLICITADA, REINTEGRO_PENDIENTE y REINTEGRADA.

#### Scenario: Devuelta
- **WHEN** una entrada fue reintegrada
- **THEN** no figura como válida para ingresar
