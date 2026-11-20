# Spec Delta

## Purpose

Define la gestión de los lugares donde se realizan eventos y de sus sectores, incluyendo las reglas de validación, los permisos de acceso y las operaciones de consulta.

## ADDED Requirements

### Requirement: Crear lugares y sus sectores
El sistema SHALL permitir a los usuarios con rol ORGANIZADOR o ADMIN registrar lugares y sus sectores.

#### Scenario: Crear un lugar con datos válidos
- **WHEN** un usuario con rol ORGANIZADOR o ADMIN envía los datos válidos de un lugar
- **THEN** el sistema registra el lugar y devuelve su información identificatoria

#### Scenario: Crear un lugar sin permisos
- **WHEN** un usuario sin rol ORGANIZADOR o ADMIN intenta registrar un lugar
- **THEN** el sistema rechaza la operación con un error 403

### Requirement: Validar los datos geográficos del lugar
El sistema SHALL aceptar únicamente coordenadas geográficas dentro de los rangos válidos de latitud y longitud.

#### Scenario: Crear un lugar con coordenadas fuera de rango
- **WHEN** se intenta registrar un lugar con latitud fuera del rango [-90, 90] o longitud fuera del rango [-180, 180]
- **THEN** el sistema rechaza la solicitud por datos inválidos

### Requirement: Validar la configuración de los sectores
El sistema SHALL validar que los sectores de un mismo lugar no tengan nombres duplicados y que sus datos correspondan al tipo de sector declarado.

#### Scenario: Crear un sector numerado correctamente
- **WHEN** se registra un sector de tipo NUMERADO con filas y cantidad de asientos por fila
- **THEN** el sistema acepta la configuración del sector

#### Scenario: Crear un sector numerado sin datos requeridos
- **WHEN** se registra un sector de tipo NUMERADO sin filas o sin cantidad de asientos por fila
- **THEN** el sistema rechaza la solicitud por datos inválidos

#### Scenario: Crear un sector no numerado correctamente
- **WHEN** se registra un sector de tipo NO_NUMERADO con capacidad definida
- **THEN** el sistema acepta la configuración del sector

#### Scenario: Crear un sector no numerado sin capacidad
- **WHEN** se registra un sector de tipo NO_NUMERADO sin capacidad
- **THEN** el sistema rechaza la solicitud por datos inválidos

#### Scenario: Registrar sectores con nombres duplicados
- **WHEN** se intenta registrar más de un sector con el mismo nombre dentro de un lugar
- **THEN** el sistema rechaza la solicitud

### Requirement: Listar lugares de forma paginada
El sistema SHALL permitir consultar lugares mediante paginación y filtro opcional por ciudad.

#### Scenario: Listar lugares sin indicar paginación
- **WHEN** un usuario consulta el listado sin especificar página ni tamaño
- **THEN** el sistema devuelve la primera página con un tamaño predeterminado de 20 elementos

#### Scenario: Listar lugares con filtro por ciudad
- **WHEN** un usuario consulta los lugares indicando una ciudad
- **THEN** el sistema devuelve únicamente los lugares que coinciden con esa ciudad, respetando la paginación solicitada

#### Scenario: Solicitar un tamaño de página superior al máximo
- **WHEN** un usuario solicita un tamaño de página superior a 100 elementos
- **THEN** el sistema rechaza la solicitud por parámetros inválidos

### Requirement: Consultar el detalle de un lugar y sus sectores
El sistema SHALL permitir consultar un lugar por su identificador y obtener los sectores asociados.

#### Scenario: Consultar un lugar existente
- **WHEN** un usuario solicita el detalle de un lugar registrado
- **THEN** el sistema devuelve los datos del lugar y sus sectores asociados

#### Scenario: Consultar un lugar inexistente
- **WHEN** un usuario solicita el detalle de un identificador que no corresponde a ningún lugar
- **THEN** el sistema devuelve un error 404
