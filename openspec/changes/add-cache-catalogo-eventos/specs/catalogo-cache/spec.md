# Spec Delta

## Purpose

Definir el almacenamiento temporal en caché de las consultas del catálogo y del detalle de eventos, reduciendo consultas repetidas a la base de datos sin comprometer la actualización de la información ni la disponibilidad real de entradas.

## ADDED Requirements

### Requirement: Caché del catálogo de eventos
El sistema SHALL almacenar temporalmente los resultados de las consultas del catálogo para reutilizarlos durante su tiempo de vida.

#### Scenario: Consulta repetida del catálogo
- **WHEN** se realiza una consulta del catálogo cuya respuesta ya está almacenada y no venció
- **THEN** el sistema devuelve el resultado almacenado sin repetir la consulta a la base de datos

#### Scenario: Consulta con filtros diferentes
- **WHEN** se consulta el catálogo utilizando criterios de búsqueda diferentes
- **THEN** el sistema devuelve los resultados correspondientes a esos criterios sin mezclar respuestas de otras consultas

### Requirement: Caché del detalle de eventos
El sistema SHALL almacenar temporalmente el detalle de cada evento consultado, identificándolo de manera independiente.

#### Scenario: Consulta repetida del detalle
- **WHEN** se consulta el detalle de un evento cuya entrada de caché sigue vigente
- **THEN** el sistema devuelve el detalle almacenado

#### Scenario: Detalle de otro evento
- **WHEN** se consulta un evento diferente
- **THEN** el sistema devuelve el detalle correspondiente a ese evento y no reutiliza el de otro

### Requirement: Expiración de entradas de caché
El sistema SHALL establecer un tiempo de vida (TTL) para las entradas de caché y dejar de utilizarlas cuando expire.

#### Scenario: Entrada vigente
- **WHEN** se consulta un dato cuya entrada de caché no alcanzó su TTL
- **THEN** el sistema puede reutilizar esa entrada

#### Scenario: Entrada vencida
- **WHEN** se consulta un dato cuya entrada de caché alcanzó su TTL
- **THEN** el sistema obtiene información actualizada y reemplaza la entrada vencida

### Requirement: Invalidación al modificar eventos
El sistema SHALL invalidar las entradas de caché relacionadas con un evento cuando se modifica su información.

#### Scenario: Evento modificado
- **WHEN** se modifica correctamente un evento
- **THEN** se invalidan las entradas de caché del detalle de ese evento y las consultas del catálogo que puedan contenerlo

#### Scenario: Consulta posterior a la modificación
- **WHEN** se consulta el detalle o el catálogo después de una modificación
- **THEN** el sistema devuelve información actualizada y no los datos anteriores almacenados

### Requirement: Exclusión de datos de disponibilidad
El sistema SHALL excluir de la caché la disponibilidad de asientos y cupos, así como los mapas y estados individuales de los asientos.

#### Scenario: Consulta de disponibilidad
- **WHEN** se consulta la disponibilidad de un evento
- **THEN** el sistema obtiene los datos actualizados del inventario sin utilizar resultados almacenados en caché

#### Scenario: Consulta del mapa de asientos
- **WHEN** se consulta el mapa y estado de los asientos de un sector
- **THEN** el sistema obtiene los estados actuales sin utilizar resultados almacenados en caché
