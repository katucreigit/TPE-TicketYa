# Proposal

## Why

El API Gateway necesita tolerar fallos temporales de los microservicios para evitar que su indisponibilidad se propague al resto del sistema. También debe limitar los tiempos de espera de las solicitudes y ofrecer respuestas controladas cuando un servicio destino no está disponible.

## What Changes

- Configurar timeouts de conexión y respuesta HTTP en el API Gateway.
- Incorporar Circuit Breaker en las rutas hacia los microservicios de autenticación, reservas, eventos y ventas.
- Configurar umbrales de fallos y tiempos de recuperación para cada circuito.
- Implementar respuestas fallback HTTP 503 con un formato de error estructurado.
- Agregar pruebas de integración que verifiquen el fallback y la apertura del circuito.

## Capabilities

### New Capabilities

- `api-gateway-resilience`: tolerancia a fallos y gestión de indisponibilidad de microservicios desde el API Gateway.

### Modified Capabilities

- Ninguna.

## Impact

- `api-gateway/src/main/resources/application.yml`
- `api-gateway/src/main/java/com/ticketya/gateway/FallbackController.java`
- `api-gateway/src/main/java/com/ticketya/gateway/GatewayErrorHandler.java`
- `api-gateway/src/test/java/com/ticketya/gateway/CircuitBreakerGatewayIntegrationTest.java`
- Dependencia Spring Cloud CircuitBreaker con Resilience4j.
