# Tasks

## 1. Configuración de resiliencia

- [x] 1.1 Configurar timeout de conexión de 2000 milisegundos y timeout de respuesta de 5 segundos.
- [x] 1.2 Incorporar Circuit Breaker en las rutas hacia autenticación, reservas, eventos y ventas.
- [x] 1.3 Configurar ventanas de evaluación, umbrales de fallos y tiempos de recuperación para cada circuito.

## 2. Manejo de indisponibilidad

- [x] 2.1 Implementar un endpoint fallback que devuelva HTTP 503 con un error estructurado.
- [x] 2.2 Integrar el fallback en las rutas protegidas por Circuit Breaker.
- [x] 2.3 Mantener el manejo global de errores de conexión y timeout.

## 3. Pruebas

- [x] 3.1 Agregar una prueba de integración que simule la indisponibilidad de `ms-eventos`.
- [x] 3.2 Verificar que el fallback responda HTTP 503 con el código `SERVICIO_NO_DISPONIBLE`.
- [x] 3.3 Verificar que el circuito `eventos` se abra después de acumular cinco fallos.
- [x] 3.4 Verificar las transiciones automáticas de recuperación `OPEN`, `HALF_OPEN` y `CLOSED`.
- [x] 3.5 Verificar el comportamiento de los circuitos de autenticación, reservas y ventas.

## 4. Documentación y validación

- [x] 4.1 Documentar la configuración de resiliencia del API Gateway en la documentación general de arquitectura.
- [x] 4.2 Validar los cambios OpenSpec con `openspec validate --all --strict`.
- [x] 4.3 Ejecutar todas las pruebas del API Gateway (27 pruebas, sin fallos ni errores).
