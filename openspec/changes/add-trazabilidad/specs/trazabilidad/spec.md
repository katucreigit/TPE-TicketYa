# Spec Delta

## Purpose

Permite registrar y relacionar las solicitudes HTTP y las operaciones críticas de los microservicios para seguir el recorrido completo de una compra mediante un identificador de correlación compartido.

## ADDED Requirements

### Requirement: Registro de solicitudes HTTP
El sistema SHALL registrar cada solicitud HTTP procesada, incluyendo método, endpoint, usuario, código de respuesta HTTP y tiempo de procesamiento.

#### Scenario: Registro de una solicitud HTTP
- **WHEN** un servicio procesa una solicitud HTTP
- **THEN** queda registrado el método, endpoint, usuario, código de respuesta y tiempo de procesamiento de esa solicitud

### Requirement: Identificador de correlación compartido
El sistema SHALL asignar o conservar un `correlationId` para cada solicitud y propagar el mismo identificador entre los servicios que participan en una operación.

#### Scenario: Solicitud sin identificador de correlación
- **WHEN** una solicitud HTTP ingresa sin un `correlationId`
- **THEN** el sistema genera un identificador y lo utiliza para relacionar los registros de esa solicitud y de las operaciones derivadas

#### Scenario: Solicitud con identificador de correlación
- **WHEN** una solicitud HTTP ingresa con un `correlationId`
- **THEN** el sistema conserva ese identificador y lo utiliza para relacionar los registros de la solicitud y de las operaciones derivadas

#### Scenario: Propagación del identificador de correlación
- **WHEN** una solicitud provoca una comunicación con otro servicio
- **THEN** el servicio receptor recibe el mismo `correlationId` de la operación original

### Requirement: Registro centralizado de operaciones críticas
El sistema SHALL registrar de forma centralizada las operaciones críticas e incluir el `correlationId` correspondiente para relacionarlas con las solicitudes que las originaron.

#### Scenario: Registro de una operación crítica
- **WHEN** se ejecuta una operación crítica del sistema
- **THEN** queda disponible un registro centralizado de la operación asociado a su `correlationId`

### Requirement: Seguimiento de una compra de punta a punta
El sistema SHALL permitir relacionar los registros de todos los servicios que participan en una compra mediante un único `correlationId`.

#### Scenario: Seguimiento completo de una compra
- **WHEN** se consulta la trazabilidad de una compra utilizando su `correlationId`
- **THEN** se pueden seguir en conjunto los registros de las solicitudes HTTP y de las operaciones críticas generados por los servicios que participaron en esa compra
