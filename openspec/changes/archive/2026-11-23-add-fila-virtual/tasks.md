# Tasks

Cada escenario de `specs/` tiene su prueba, indicada entre paréntesis con el nombre del escenario. Las pruebas de `ms-fila` corren contra Redis 7 real (`GenericContainer("redis:7-alpine")` de Testcontainers 1.21.4); nunca Redis embebido ni mocks de Redis. El tiempo se controla con un `Clock` controlable y el scheduler está apagado en el perfil `test`: las pruebas llaman al proceso directamente. Ninguna prueba existente se modifica. No se hace commit ni push.

## 1. Estructura de `ms-fila`

- [x] 1.1 Crear `ms-fila/` con `pom.xml` copiado de `ms-notificaciones` (parent Boot 3.5.7, Java 21, `spring-boot-starter-web`, `-data-redis`, `-validation`, springdoc 2.8.13, Lombok; en test `spring-boot-starter-test`, `spring-boot-testcontainers` y `org.testcontainers:junit-jupiter`, con `testcontainers.version` 1.21.4 y el mismo comentario), `MsFilaApplication` con `@EnableScheduling`, `ClockConfig` y los paquetes `controller`, `service`, `repository`, `dto`, `exception`, `config` y `trazabilidad` bajo `com.ticketya.fila`. Verificar con `mvn -q compile`.
- [x] 1.2 Crear `application.yml` con `server.port: 8086`, `spring.data.redis.host: ${REDIS_HOST:localhost}`, `port: ${REDIS_PORT:6379}` y `timeout: 500ms`, springdoc como en los otros servicios, el patrón de log `%5p [%X{correlationId:-}]` y las propiedades `ticketya.fila.minutos-ventana` (10), `.tamano-tanda-default` (100), `.segundos-entre-tandas-default` (60), `.ttl-horas` (24), `.procesador.habilitado` (true) y `.procesador.intervalo` (1s), en una clase `@ConfigurationProperties`. Crear `src/test/resources/application-test.yml` con `procesador.habilitado: false`. Verificar con `mvn -q compile`.
- [x] 1.3 Crear `TestcontainersConfiguration` (`GenericContainer` `redis:7-alpine`, puerto 6379, `@ServiceConnection(name = "redis")`), el reloj de prueba controlable y una prueba de arranque del contexto que haga `PING`. Verificar con `mvn test` (requiere Docker).
- [x] 1.4 Crear `Dockerfile` (dos etapas, igual al de `ms-notificaciones`, con `EXPOSE 8086`) y `.dockerignore`. Verificar con `docker build ms-fila`.

## 2. Errores, identidad y trazabilidad de `ms-fila`

- [x] 2.1 Crear `ErrorResponse`, `ApiException` y sus subclases, y `GlobalExceptionHandler`, como en `ms-notificaciones`, con el mapeo de `@Valid` y JSON ilegible a `400 VALIDACION` y de `RedisConnectionFailureException`, `RedisSystemException` y `QueryTimeoutException` a `503 SERVICIO_NO_DISPONIBLE` (design.md D8). Crear `Identidad` con `cliente(...)` y `administrador(...)` (D9). Se verifica con las pruebas de los grupos 3 a 5.
- [x] 2.2 Copiar de `ms-reservas` el paquete `trazabilidad/` (`Correlacion`, `CorrelationIdFilter`, `RegistroSolicitudesFilter`, `RegistroOperaciones` sin sincronización de transacciones), con las operaciones `FILA_ACTIVADA`, `FILA_DESACTIVADA`, `INGRESO_FILA`, `CLIENTE_HABILITADO` y `TURNO_VENCIDO` (D12). Pruebas con `OutputCaptureExtension` de "Identificador de correlación en ms-fila": (Solicitud con identificador) y (Solicitud sin identificador); y de "Registro de solicitudes en ms-fila": (Registro de un ingreso).

## 3. Activación y desactivación

- [x] 3.1 Implementar `FilaRepository` con los scripts Lua `activar` y `desactivar` sobre las claves de design.md D2 (hash tag `{eventoId}`, `SADD`/`SREM fila:activas`, `EXPIRE` de `ttl-horas`, `proximaTandaEn = ahora + segundosEntreTandas` solo cuando la fila no estaba activa). Implementar `FilaService.activar/desactivar` y `FilaAdminController` (`POST /fila/eventos/{eventoId}/activar` con cuerpo opcional `ActivarFilaRequest` validado, `200 FilaConfiguracion`; `POST .../desactivar`, `204`), registrando `FILA_ACTIVADA` y `FILA_DESACTIVADA`.
- [x] 3.2 Pruebas de "Activación de la fila de un evento": (Activar una fila con parámetros) y (Activar sin cuerpo usa los valores por defecto). Además, una prueba que verifique que las claves tienen TTL.
- [x] 3.3 Pruebas de "Reactivación de una fila activa": (Cambiar el tamaño de tanda sin perder la fila); y de "Validación de los parámetros de la fila": (Tamaño de tanda cero) e (Intervalo mayor al máximo).
- [x] 3.4 Pruebas de "Desactivación de la fila de un evento": (Desactivar una fila con clientes) y (Desactivar una fila inactiva); y de "Rol para administrar la fila": (Cliente intenta activar) y (Activar sin identidad).

## 4. Ingreso y consulta de posición

- [x] 4.1 Implementar el script `ingresar` (D3) y la lectura de estado (`ZRANK`/`ZSCORE` comparando con `ahora`), `FilaService.ingresar/posicion` y `FilaClienteController` (`POST /fila/eventos/{eventoId}/ingresar`, `GET .../posicion`, `200 FilaEstadoCliente`, `409 FILA_NO_ACTIVA`). Registrar `INGRESO_FILA` solo si el ingreso es nuevo, y `TURNO_VENCIDO` si el script quitó un turno vencido.
- [x] 4.2 Pruebas de "Ingreso a la fila": (Primer cliente en una fila vacía) e (Ingresos sucesivos); de "Ingreso a una fila inactiva": (Evento sin fila); y de "Rol del cliente en la fila": (Organizador intenta ingresar) e (Ingreso sin identidad).
- [x] 4.3 Pruebas de "Ingreso idempotente": (Ingreso repetido en espera) e (Ingreso repetido de un habilitado); y de "Registro de operaciones críticas de la fila": (Ingreso nuevo registrado) e (Ingreso repetido no registrado).
- [x] 4.4 Pruebas de concurrencia con `CountDownLatch` y un pool de hilos contra el controlador. (Muchos ingresos simultáneos): 200 clientes distintos; se verifica que las posiciones son exactamente {1..200}, que `ZCARD` de la espera es 200 y que hay 200 líneas `INGRESO_FILA`. (Ingresos simultáneos del mismo cliente): 20 ingresos del cliente `101`; todos con posición 1, `ZCARD` 1 y una sola línea `INGRESO_FILA`.
- [x] 4.5 Pruebas de "Consulta de la posición": (Cliente en espera), (Cliente habilitado), (Cliente que no ingresó) y (Evento sin fila activa).

## 5. Tandas, vencimiento y consulta interna

- [x] 5.1 Implementar los scripts `habilitarTanda` (D4) y `vencerTurnos` (D5), y `ProcesadorFila` con `@Scheduled`, condicionado a `procesador.habilitado`, con un `correlationId` por corrida, usuario `SISTEMA` y captura de excepciones por evento (D7). Registrar `CLIENTE_HABILITADO` y `TURNO_VENCIDO` con los clientes que devuelven los scripts. Quitar de `fila:activas` los eventos sin `config`.
- [x] 5.2 Pruebas de "Habilitación por tandas", con el reloj controlable: (Tanda completa), (Antes de tiempo no se habilita), (Tanda con menos clientes que el tamaño) y (Tandas sucesivas).
- [x] 5.3 Prueba de concurrencia (Procesos de habilitación concurrentes): 100 clientes en espera, `tamanoTanda` 10 y 8 hilos que ejecutan el proceso a la vez, simulando 8 instancias. Se verifica que hay exactamente 10 habilitados (los 10 primeros números de llegada), 90 en espera y exactamente una línea `CLIENTE_HABILITADO` por cliente. Una segunda variante repite la carrera en 5 intervalos seguidos y verifica que no hay clientes habilitados dos veces y que se habilitaron 50 en orden.
- [x] 5.4 Pruebas de "Vencimiento del turno": (Turno vencido sin comprar) y (Reingreso después de perder el turno); y de "Registro de operaciones críticas de la fila": (Habilitación y vencimiento registrados una vez), con varias ejecuciones simultáneas de `vencerTurnos`.
- [x] 5.5 Implementar `InternoFilaController` (`GET /internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado` → `{ activa, habilitado }`). Pruebas de "Consulta interna del estado de un cliente": (Fila inactiva), (Cliente en espera), (Cliente habilitado dentro de su ventana) y (Ventana vencida aunque el proceso no haya corrido).
- [x] 5.6 Pruebas de "Almacenamiento de la fila no disponible": (Ingreso sin almacenamiento), deteniendo el contenedor en una clase de prueba propia con su propio contenedor; y (Recuperación del proceso de habilitación), que pausa el contenedor con `docker pause` (`DockerClient`) durante una ejecución y verifica que después se habilita la tanda pendiente.
- [x] 5.7 Ejecutar `mvn test` en `ms-fila` con Docker en ejecución y verificar 0 fallos.

## 6. Integración en `ms-reservas`

- [x] 6.1 Agregar a `CodigoError` el código `FILA_NO_HABILITADO` (403) con su fábrica `filaNoHabilitado()`, y a `ForbiddenException` el constructor `(code, message)`. Verificar que `CodigoErrorTest` sigue pasando.
- [x] 6.2 Crear `client/FilaClient` (D10): `RestClient` desde el builder inyectado, connect y read timeout `ticketya.services.fila-timeout-ms` (1000), URL `ticketya.services.fila-url: ${MS_FILA_URL:http://localhost:8086}`, y método `estado(eventoId, clienteId)` que devuelve `{ activa, habilitado }` o un resultado "no disponible" con la causa. Agregar las propiedades y `ticketya.fila.consultar: true` a `application.yml`, y `ticketya.fila.consultar: false` a `src/test/resources/application-test.yml`.
- [x] 6.3 En `ReservaService.crear`, antes de `transaccion.execute`: ejecutar `ReglasReserva.validarPedido`, luego consultar `FilaClient` si `consultar` es `true`, y lanzar `filaNoHabilitado()` si la fila está activa y el cliente no está habilitado. Si la consulta falla, registrar `WARN FILA_NO_DISPONIBLE eventoId= clienteId= causa= correlationId=` y seguir.
- [x] 6.4 Crear `FilaVirtualReservaTest` (Testcontainers PostgreSQL más un `com.sun.net.httpserver.HttpServer` que simula `ms-fila`, con `ticketya.fila.consultar=true` y `fila-url` apuntando a él). Pruebas de "Fila virtual al reservar": (Cliente en espera intenta reservar), que verifica que los asientos siguen `LIBRE` y no hay reserva; (Cliente habilitado reserva); (Evento sin fila activa); (Organizador reserva en un evento con fila), que verifica que el servidor falso no recibió ninguna consulta; y (Pedido inválido en un evento con fila).
- [x] 6.5 Pruebas de "Reserva sin bloqueo cuando la fila no responde": (ms-fila detenido), con el puerto cerrado y la advertencia verificada con `CapturaLogs`; (ms-fila lento), con una demora de 5 s en el servidor falso y una respuesta en menos de 2 s; y (ms-fila responde con error), con un `503`. Prueba de "Correlación en la consulta a la fila": (Propagación del identificador a ms-fila).
- [x] 6.6 Ejecutar `mvn test` en `ms-reservas` con Docker en ejecución. Verificar que pasan todas las pruebas existentes sin modificarlas, más las nuevas, con 0 fallos, e indicar la cantidad en el resumen.

## 7. API Gateway

- [x] 7.1 En `api-gateway/src/main/resources/application.yml` agregar solo la ruta `fila` (`${MS_FILA_URL:http://localhost:8086}`, `order: 0`, `Path=/api/fila/**`, `StripPrefix=1`, `CircuitBreaker` con `name: fila` y `fallbackUri: forward:/fallback/fila`) y las instancias `fila` de `resilience4j.circuitbreaker` y `timelimiter`, con los mismos valores que las demás (D11). No modificar ningún otro archivo del gateway.
- [x] 7.2 Crear la clase de prueba nueva `FilaRutaGatewayTest`, siguiendo el patrón de `CircuitBreakerGatewayIntegrationTest`, sin tocar las pruebas existentes. Pruebas de "Acceso a la fila por el API Gateway": (Ingreso a través del gateway), que verifica el path sin `/api` y los headers del token; (Rutas de eventos sin cambios); (Fila sin token); (ms-fila no disponible); y (La consulta interna no se expone). Ejecutar `mvn test` en `api-gateway` y verificar 0 fallos.

  Nota de implementación: el escenario (ms-fila no disponible) quedó en una segunda clase nueva, `FilaNoDisponibleGatewayTest`, porque necesita otro valor de `MS_FILA_URL` y por lo tanto su propio contexto. Además, la prueba existente `ApiGatewayApplicationTests.debeTenerConfiguradasLasRutasDeTodosLosServicios` fijaba exactamente 5 rutas. Con autorización del usuario se actualizó a 6 (se agregó `fila`), como ya se había hecho al sumar `auth`. Resultado: `mvn test` en `api-gateway`, 34 pruebas, 0 fallos.

## 8. Despliegue y documentación

- [x] 8.1 En `docker-compose.yml` agregar `redis` y `ms-fila` (D13), el volumen `redis-data`, `MS_FILA_URL` en `ms-reservas` y en `api-gateway`, `ms-fila` en el `depends_on` del gateway, y actualizar los comentarios de cabecera (13 contenedores; solo bases con `redis`). Verificar con `docker compose config` y con `docker compose up -d --build`: los 13 contenedores en ejecución, `redis` healthy y `GET http://localhost:8086/fila/eventos/1/posicion` con identidad de cliente respondiendo `SIN_FILA`.
- [x] 8.2 Actualizar `docs/openapi.yaml`: el tag `Fila` (ms-fila, 8086) en la tabla de la descripción y en `tags`; `POST /fila/eventos/{eventoId}/activar` y `.../desactivar`, `POST .../ingresar` y `GET .../posicion`; `GET /internal/fila/eventos/{eventoId}/clientes/{clienteId}/estado` con su servidor `http://localhost:8086`; los schemas `ActivarFilaRequest`, `FilaConfiguracion`, `FilaEstadoCliente`, `EstadoFilaCliente` y `FilaEstadoInterno`; y la respuesta `403` con `FILA_NO_HABILITADO` y `ACCESO_DENEGADO` en `POST /reservas`. Verificar que el YAML parsea y que los ejemplos coinciden con las respuestas de las pruebas.
- [x] 8.3 Actualizar `README.md`: `ms-fila` (8086, Redis 6379) en la tabla de componentes, los contenedores (13) y el comando de solo bases con `redis`, la variable `MS_FILA_URL` (`api-gateway`, `ms-reservas`, por defecto `http://localhost:8086`) y `REDIS_HOST`/`REDIS_PORT` en Configuración, el Swagger de `ms-fila`, `cd ms-fila && mvn test`, y una sección breve "Fila virtual" con el flujo de activar, ingresar, consultar y reservar. Verificar que los comandos documentados funcionan tal como están escritos.
- [x] 8.4 Actualizar `docs/arquitectura.md`: `ms-fila` en la tabla de microservicios (funcionalidad 6), en la API pública por servicio y en las rutas del gateway (`/api/fila/**`), la dependencia `ms-reservas → ms-fila` con su fail-open en la tabla de dependencias, el circuito `fila` en la sección de resiliencia, y una sección "Fila virtual (E2)" con el resumen de D1, D4, D10 y D14. Verificar que coincide con lo implementado.

## 9. Verificación integral

- [x] 9.1 Con el sistema levantado en Compose, recorrer el flujo por el gateway con tokens reales. El organizador activa la fila con `tamanoTanda` 1 y `segundosEntreTandas` 10. Dos clientes ingresan y quedan en las posiciones 1 y 2. El segundo intenta reservar y recibe `403 FILA_NO_HABILITADO`. Después de la tanda, el primero reserva y recibe `201`. Al detener `ms-fila`, una reserva responde `201` y `ms-reservas` registra `FILA_NO_DISPONIBLE` con el mismo `correlationId`. Anotar el resultado en este archivo.
  Resultado (2026-11-23, Compose con 13 contenedores, por el gateway con JWT reales):

  | Paso | Resultado |
  |---|---|
  | Lugar, evento, precios y publicación | `201`, `201`, `200`, `200` (`PUBLICADO`) |
  | Organizador activa la fila (`tamanoTanda` 1, `segundosEntreTandas` 10) | `200`, `proximaTandaEn` 10 s después |
  | Cliente A y cliente B ingresan | `200` `EN_ESPERA`, posiciones 1 y 2 |
  | Cliente B intenta reservar | `403 FILA_NO_HABILITADO` |
  | Después de la tanda (12 s) | A `HABILITADO` con `habilitadoHasta` 10 min después; B `EN_ESPERA` en la posición 1 |
  | Cliente A reserva | `201` `PENDIENTE` |
  | `ms-fila` detenido; cliente B reserva con `X-Correlation-Id: e2e-fila-caida-…` | `201` `PENDIENTE`, y `ms-reservas` registra `WARN FILA_NO_DISPONIBLE eventoId=9 clienteId=15 causa=ResourceAccessException… correlationId=e2e-fila-caida-…` |

  Observación ajena a este cambio: `ms-eventos` solo conoce a los organizadores 1 y 2 (`data.sql`), así que el evento se creó con el organizador de la colección de Postman (id 1). Un organizador recién registrado recibe `404 ORGANIZADOR_NO_ENCONTRADO`.
- [x] 9.2 Ejecutar `openspec validate --all --strict` y verificar que no hay errores.

## Workflow follow-up

- Archivar el cambio con `/opsx:archive` cuando la implementación y las verificaciones estén completas.
- El commit y el push quedan a cargo del usuario.
