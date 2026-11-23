# Design

## Context

El API Gateway centraliza las solicitudes dirigidas a `ms-auth`, `ms-reservas`, `ms-eventos` y `ms-ventas`. Si alguno de estos servicios deja de responder, las solicitudes pueden quedar esperando o fallar sin una respuesta uniforme.

Se incorpora tolerancia a fallos mediante Spring Cloud CircuitBreaker y Resilience4j.

## Decisions

### 1. Timeouts HTTP

Se configuran límites para las solicitudes salientes del Gateway:

- `connect-timeout`: 2000 milisegundos.
- `response-timeout`: 5 segundos.

Esto evita esperas indefinidas al establecer conexiones o recibir respuestas.

### 2. Circuit Breaker por servicio

Se configura un circuito independiente para cada servicio:

- `auth`
- `reservas`
- `eventos`
- `ventas`

Cada circuito utiliza:

- Ventana de evaluación basada en cantidad de llamadas (`COUNT_BASED`).
- Tamaño de ventana: 10 llamadas.
- Mínimo de llamadas para evaluar: 5.
- Umbral de fallos: 50%.
- Tiempo en estado abierto: 10 segundos.
- Llamadas permitidas en estado semiabierto: 3.
- Transición automática de abierto a semiabierto habilitada.

Cuando se alcanza el umbral de fallos, el circuito pasa a estado `OPEN` y evita temporalmente nuevas llamadas al servicio. Luego pasa a `HALF_OPEN` para evaluar si el servicio se recuperó.

### 3. Fallback

Cada ruta protegida por Circuit Breaker define un destino fallback interno mediante `forward:/fallback/{servicio}`.

El `FallbackController` devuelve HTTP 503 (`Service Unavailable`) con una respuesta JSON estructurada que incluye:

- Fecha y hora.
- Código HTTP.
- Descripción del error.
- Código funcional `SERVICIO_NO_DISPONIBLE`.
- Mensaje para el cliente.
- Ruta.
- Detalles adicionales.

El fallback no reemplaza la recuperación del servicio: informa que el destino no está disponible temporalmente.

### 4. Manejo de errores de conexión

`GatewayErrorHandler` captura errores de conexión, resolución de host y timeout que llegan hasta el filtro global. Cuando corresponde, devuelve HTTP 503 con el mismo código funcional.

El Circuit Breaker y el manejador global cubren caminos de error distintos: el primero permite ejecutar el fallback configurado en la ruta; el segundo maneja excepciones de conexión que llegan al nivel global.

## Testing

Se incorpora una prueba de integración que simula la indisponibilidad de `ms-eventos` apuntando su URL a un puerto sin servicio.

La prueba verifica:
- Que las solicitudes fallidas devuelvan HTTP 503.
- Que el cuerpo contenga `SERVICIO_NO_DISPONIBLE`.
- Que, después de cinco fallos consecutivos, el circuito `eventos` pase a estado `OPEN`.

También se ejecutan las pruebas existentes del API Gateway para comprobar que los cambios no afecten las funcionalidades previas.

## Risks and Limitations

- La prueba de integración verifica el circuito `eventos`; los otros circuitos comparten configuración, pero requieren pruebas específicas para confirmar su comportamiento individual.
- Los timeouts y umbrales son valores iniciales y pueden ajustarse según las mediciones de carga y los tiempos reales de respuesta.
- El fallback informa la indisponibilidad, pero no reemplaza mecanismos de reintento ni garantiza la recuperación del microservicio.
