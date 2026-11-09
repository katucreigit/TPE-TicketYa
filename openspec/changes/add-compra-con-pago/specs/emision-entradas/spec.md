# Spec Delta

## Purpose
Define cómo se emiten las entradas con código QR una vez confirmada la compra.

## ADDED Requirements

### Requirement: Una entrada por lugar
Cuando la compra se confirma, el sistema SHALL emitir una entrada por cada asiento o cupo comprado, en estado VALIDA.

#### Scenario: Tres asientos
- **WHEN** se compran tres asientos
- **THEN** se emiten tres entradas VALIDA

### Requirement: QR único
El sistema SHALL generar para cada entrada un código QR único y no predecible.

#### Scenario: Unicidad
- **WHEN** se emiten varias entradas
- **THEN** no hay dos códigos iguales

### Requirement: Consulta de entrada
Cuando el dueño consulta su entrada, el sistema SHALL devolver sus datos y el QR; si no es suya, SHALL responder 404.

#### Scenario: Entrada ajena
- **WHEN** otro cliente consulta la entrada
- **THEN** el sistema responde 404
