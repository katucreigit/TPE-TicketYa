# Spec Delta

## Purpose
Define cómo se registran y consultan los lugares con sus sectores, base para crear eventos.

## ADDED Requirements

### Requirement: Alta de lugar
Cuando un ORGANIZADOR o ADMIN envía un lugar válido con al menos un sector, el sistema SHALL crear el lugar y sus sectores y responder 201 con el id.

#### Scenario: Lugar válido
- **WHEN** un ORGANIZADOR envía un lugar con dos sectores válidos
- **THEN** el sistema responde 201 con el id del lugar

#### Scenario: Lugar sin sectores
- **WHEN** se envía un lugar sin sectores
- **THEN** el sistema responde 400

### Requirement: Validación de sectores
Si un sector numerado no informa filas y asientos por fila mayores a 0, o uno no numerado no informa capacidad mayor a 0, o repite nombre dentro del lugar, entonces el sistema SHALL responder 400.

#### Scenario: Numerado incompleto
- **WHEN** un sector numerado no informa asientosPorFila
- **THEN** el sistema responde 400

#### Scenario: Sector mixto
- **WHEN** un sector informa capacidad y también filas
- **THEN** el sistema responde 400

### Requirement: Coordenadas
Si la latitud está fuera de -90 a 90 o la longitud fuera de -180 a 180, entonces el sistema SHALL responder 400.

#### Scenario: Latitud inválida
- **WHEN** la latitud es 120
- **THEN** el sistema responde 400

### Requirement: Permiso de alta
Si el rol del solicitante no es ORGANIZADOR ni ADMIN, entonces el sistema SHALL responder 403 al crear un lugar.

#### Scenario: Cliente intenta crear
- **WHEN** X-User-Rol es CLIENTE y se hace POST de un lugar
- **THEN** el sistema responde 403

### Requirement: Consulta de lugares
Cuando se consulta el listado, el sistema SHALL devolver una página con el total, filtrable por ciudad sin distinguir mayúsculas; sin resultados SHALL devolver 200 con lista vacía.

#### Scenario: Sin lugares
- **WHEN** no existen lugares
- **THEN** el sistema responde 200 con lista vacía

#### Scenario: Página inválida
- **WHEN** page es negativo
- **THEN** el sistema responde 400

### Requirement: Detalle de lugar
Cuando se consulta un lugar existente, el sistema SHALL devolver sus sectores; si el id no existe, SHALL responder 404.

#### Scenario: Lugar inexistente
- **WHEN** se consulta un id que no existe
- **THEN** el sistema responde 404
