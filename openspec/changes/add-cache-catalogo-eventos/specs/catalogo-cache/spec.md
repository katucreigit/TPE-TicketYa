# Spec Delta

## Purpose

Definir el almacenamiento temporal en caché de las consultas del catálogo y del
detalle de eventos, reduciendo consultas repetidas a la base de datos sin comprometer
la actualización de la información ni la disponibilidad real de entradas.

## ADDED Requirements

### Requirement: Caché del catálogo de eventos

El sistema SHALL almacenar temporalmente los resultados de las consultas del catálogo
público para reutilizarlos durante su tiempo de vida.

- Los filtros, el orden, la dirección de ordenamiento y la paginación SHALL formar
  parte de la clave de caché.
- Las consultas con parámetros diferentes SHALL utilizar entradas independientes.

#### Scenario: Consulta repetida del catálogo

- **WHEN** se realiza una consulta del catálogo cuya respuesta ya está almacenada y no venció
- **THEN** el sistema devuelve el resultado almacenado sin repetir la consulta a la base de datos

#### Scenario: Consulta con filtros diferentes

- **WHEN** se consulta el catálogo utilizando criterios de búsqueda diferentes
- **THEN** el sistema devuelve los resultados correspondientes a esos criterios sin mezclar respuestas de otras consultas

#### Scenario: Consulta con diferente orden o paginación

- **WHEN** se consulta el catálogo utilizando un orden, dirección o página diferente
- **THEN** el sistema utiliza una entrada independiente para esos parámetros

### Requirement: Caché del detalle de eventos

El sistema SHALL almacenar temporalmente el detalle de cada evento consultado públicamente,
identificándolo de manera independiente mediante su identificador.

- Los eventos en estado BORRADOR SHALL NOT almacenarse en caché.
- Las respuestas almacenadas SHALL ser independientes de la identidad del usuario solicitante.

#### Scenario: Consulta repetida del detalle

- **WHEN** se consulta el detalle de un evento público cuya entrada de caché sigue vigente
- **THEN** el sistema devuelve el detalle almacenado

#### Scenario: Detalle de otro evento

- **WHEN** se consulta un evento diferente
- **THEN** el sistema devuelve el detalle correspondiente a ese evento y no reutiliza el de otro

#### Scenario: Consulta de un evento en borrador

- **WHEN** se consulta el detalle de un evento en estado BORRADOR
- **THEN** el sistema obtiene la información aplicando las reglas de autorización correspondientes y no almacena la respuesta en caché

### Requirement: Expiración de entradas de caché

El sistema SHALL establecer un tiempo de vida (TTL) para las entradas de caché y dejar de utilizarlas cuando expire.

- El TTL SHALL poder configurarse mediante application.yml.
- El valor inicial SHALL ser de cinco minutos para el catálogo y el detalle de eventos.

#### Scenario: Entrada vigente

- **WHEN** se consulta un dato cuya entrada de caché no alcanzó su TTL
- **THEN** el sistema puede reutilizar esa entrada

#### Scenario: Entrada vencida

- **WHEN** se consulta un dato cuya entrada de caché alcanzó su TTL
- **THEN** el sistema obtiene información actualizada y reemplaza la entrada vencida

### Requirement: Invalidación al modificar eventos

El sistema SHALL invalidar las entradas de caché relacionadas con un evento cuando una operación modifica su información pública o su estado.

- Las operaciones de publicación, suspensión, reanudación y cancelación SHALL invalidar el catálogo y el detalle del evento correspondiente.
- Un cambio efectivo de disponibilidad que modifique el estado del evento SHALL invalidar el catálogo y el detalle correspondiente.
- Las invalidaciones SHALL ejecutarse después de confirmar la transacción.
- Las modificaciones de eventos en estado BORRADOR que no afectan información pública no requieren invalidación.

#### Scenario: Evento modificado

- **WHEN** se modifica correctamente la información pública o el estado de un evento
- **THEN** se invalidan las entradas de caché del detalle de ese evento y las consultas del catálogo que puedan contenerlo

#### Scenario: Consulta posterior a la modificación

- **WHEN** se consulta el detalle o el catálogo después de una modificación confirmada
- **THEN** el sistema devuelve información actualizada y no los datos anteriores almacenados

#### Scenario: Transacción cancelada

- **WHEN** una operación que modificaría un evento no confirma su transacción
- **THEN** el sistema no ejecuta la invalidación correspondiente a esa modificación

#### Scenario: Modificación de un borrador

- **WHEN** se crea, actualiza o configuran los precios de un evento que permanece en estado BORRADOR
- **THEN** el sistema no invalida la caché pública porque esa información no forma parte de ella

### Requirement: Finalización automática de eventos

Cuando el job automático finalice uno o más eventos, el sistema SHALL invalidar las entradas de caché del catálogo y los detalles almacenados.

#### Scenario: Finalización de eventos

- **WHEN** el job automático modifica uno o más eventos para establecer su estado FINALIZADO
- **THEN** el sistema invalida las entradas del catálogo y los detalles almacenados

#### Scenario: Job sin eventos para finalizar

- **WHEN** el job automático no modifica ningún evento
- **THEN** el sistema no realiza invalidaciones de caché

### Requirement: Exclusión de datos de disponibilidad

El sistema SHALL excluir de la caché la disponibilidad de asientos y cupos, el inventario de reservas, los sectores y sus capacidades, así como los mapas y estados individuales de los asientos.

#### Scenario: Consulta de disponibilidad

- **WHEN** se consulta la disponibilidad de un evento
- **THEN** el sistema obtiene los datos actualizados del inventario sin utilizar resultados almacenados en caché

#### Scenario: Consulta del mapa de asientos

- **WHEN** se consulta el mapa y estado de los asientos de un sector
- **THEN** el sistema obtiene los estados actuales sin utilizar resultados almacenados en caché

#### Scenario: Consulta de sectores y capacidades

- **WHEN** se consultan los sectores o sus capacidades
- **THEN** el sistema obtiene la información correspondiente sin utilizar resultados almacenados en caché
