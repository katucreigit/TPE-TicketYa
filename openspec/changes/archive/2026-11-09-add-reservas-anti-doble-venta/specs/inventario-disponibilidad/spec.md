# Spec Delta

## Purpose
Define cómo se crea, consulta, cierra y avisa el inventario de asientos y cupos de cada evento.

## ADDED Requirements

### Requirement: Inicialización del inventario
Cuando ms-eventos solicita crear el inventario de un evento, el sistema SHALL guardar el evento como abierto con su maxPorCliente (4 si no se informa), crear un asiento LIBRE por cada fila y número de los sectores numerados, y un cupo igual a la capacidad para los no numerados, y responder 201.

#### Scenario: Sector numerado
- **WHEN** se inicializa un evento con un sector de 20 filas y 30 asientos por fila
- **THEN** existen 600 asientos LIBRE para ese sector

#### Scenario: Sector no numerado
- **WHEN** se inicializa un sector con capacidad 5000
- **THEN** su cupo disponible es 5000
- **AND** no se crean asientos

### Requirement: Inventario existente
Si el inventario del evento ya existe, entonces el sistema SHALL responder 409 sin modificar los asientos, cupos ni reservas existentes.

#### Scenario: Segunda inicialización
- **WHEN** se repite la solicitud para el mismo evento
- **THEN** el sistema responde 409
- **AND** los asientos y reservas siguen intactos

### Requirement: Datos de inicialización inválidos
Si un sector numerado no informa filas y asientosPorFila mayores a 0, o uno no numerado no informa capacidad mayor a 0, o hay sectorId repetidos, o maxPorCliente es menor a 1, entonces el sistema SHALL responder 400 y no crear nada.

#### Scenario: Sector inválido
- **WHEN** el segundo de dos sectores no informa capacidad
- **THEN** el sistema responde 400
- **AND** tampoco se crean los asientos del primer sector

### Requirement: Unicidad de asiento
El sistema SHALL impedir, a nivel de base de datos, dos asientos con el mismo evento, sector, fila y número.

#### Scenario: Duplicado
- **WHEN** se intenta insertar un asiento repetido
- **THEN** la base lo rechaza

### Requirement: Disponibilidad por evento
Cuando se consulta la disponibilidad de un evento, el sistema SHALL devolver por sector su tipo, capacidad y lugares libres, sin requerir identidad ni usar caché; si el evento no tiene inventario, SHALL responder 404.

#### Scenario: Con reservas activas
- **WHEN** hay asientos reservados y vendidos
- **THEN** los libres no los incluyen

#### Scenario: Evento sin inventario
- **WHEN** se consulta un evento nunca publicado
- **THEN** el sistema responde 404

### Requirement: Mapa de asientos
Cuando se consulta un sector numerado, el sistema SHALL devolver cada asiento con fila, número y estado, ordenados por fila y número, y permitir filtrar por estado; en un sector no numerado SHALL responder 400.

#### Scenario: Filtro por estado
- **WHEN** se pide estado=LIBRE
- **THEN** solo se devuelven asientos libres

#### Scenario: Sector no numerado
- **WHEN** se pide el mapa de un sector no numerado
- **THEN** el sistema responde 400

### Requirement: Cierre del inventario
Cuando ms-eventos cierra el inventario por suspensión o cancelación, el sistema SHALL marcarlo como no abierto, pasar a LIBERADA las reservas PENDIENTE y devolver sus lugares, de forma repetible.

#### Scenario: Cierre con reservas pendientes
- **WHEN** se cierra un evento con dos reservas PENDIENTE
- **THEN** ambas quedan LIBERADA
- **AND** sus lugares vuelven a LIBRE

#### Scenario: Cierre repetido
- **WHEN** se cierra dos veces el mismo evento
- **THEN** el segundo cierre responde 200 sin cambios

### Requirement: Reapertura
Cuando ms-eventos reanuda un evento suspendido, el sistema SHALL marcar su inventario como abierto para admitir nuevas reservas; si el evento no tiene inventario, SHALL responder 404.

#### Scenario: Reanudación
- **WHEN** se abre el inventario de un evento suspendido
- **THEN** las nuevas reservas se aceptan de nuevo

### Requirement: Aviso de agotado
Cuando la suma de lugares libres de un evento pasa a 0, el sistema SHALL avisar a ms-eventos con agotado=true, y con agotado=false cuando vuelve a haber alguno; si el aviso falla, SHALL conservar la operación y reintentarlo después.

#### Scenario: Último lugar
- **WHEN** se reserva el último lugar libre
- **THEN** ms-eventos recibe agotado=true

#### Scenario: Reserva vencida
- **WHEN** vence una reserva de un evento agotado
- **THEN** ms-eventos recibe agotado=false

#### Scenario: Falla del aviso
- **WHEN** ms-eventos no responde al aviso
- **THEN** la reserva se mantiene
- **AND** el aviso se reintenta más tarde
