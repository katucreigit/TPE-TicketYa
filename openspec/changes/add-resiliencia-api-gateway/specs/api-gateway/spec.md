# api-gateway Specification

## Purpose

Establecer mecanismos de tolerancia a fallos en el API Gateway mediante límites de espera, Circuit Breaker y respuestas fallback ante la indisponibilidad de los microservicios.

## ADDED Requirements

### Requirement: Timeouts en solicitudes salientes

El API Gateway MUST establecer un límite de tiempo para conectar con los microservicios y otro para recibir sus respuestas.

#### Scenario: Tiempo máximo de conexión

- **WHEN** el API Gateway intenta establecer una conexión con un microservicio
- **THEN** MUST cancelar el intento cuando supere los 2000 milisegundos.

#### Scenario: Tiempo máximo de respuesta

- **WHEN** un microservicio no responde dentro del tiempo configurado
- **THEN** MUST finalizar la espera al alcanzar los 5 segundos.

### Requirement: Circuit Breaker por microservicio

El API Gateway MUST contar con un Circuit Breaker independiente para cada uno de los servicios de autenticación, reservas, eventos y ventas.

#### Scenario: Apertura del circuito por fallos

- **WHEN** un circuito registra al menos cinco llamadas y el porcentaje de fallos alcanza o supera el 50% de su ventana de evaluación
- **THEN** MUST pasar al estado `OPEN`.

#### Scenario: Solicitud con circuito abierto

- **WHEN** una solicitud se dirige a un servicio cuyo circuito está en estado `OPEN`
- **THEN** MUST evitar la llamada al servicio destino
- **AND** MUST ejecutar la respuesta fallback configurada para esa ruta.

#### Scenario: Recuperación del circuito

- **WHEN** transcurren diez segundos desde la apertura del circuito
- **THEN** MUST permitir hasta tres llamadas de prueba en estado `HALF_OPEN`
- **AND** MUST evaluar los resultados para determinar si corresponde cerrar o volver a abrir el circuito.

### Requirement: Respuesta fallback ante indisponibilidad

El API Gateway MUST proporcionar una respuesta controlada cuando una ruta protegida por Circuit Breaker no pueda completar la solicitud.

#### Scenario: Servicio destino no disponible

- **WHEN** el Circuit Breaker ejecuta el fallback de una ruta
- **THEN** MUST responder con HTTP 503
- **AND** MUST incluir el código `SERVICIO_NO_DISPONIBLE`
- **AND** MUST informar que el servicio destino no está disponible temporalmente.

### Requirement: Manejo uniforme de errores de conexión

El API Gateway MUST manejar los errores de conexión y timeout que lleguen al manejador global.

#### Scenario: Error de conexión con un microservicio

- **WHEN** ocurre un error de conexión, resolución de host o timeout no resuelto por el fallback de la ruta
- **THEN** MUST responder con HTTP 503 y un cuerpo de error estructurado.
