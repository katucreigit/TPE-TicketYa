# Design

## Context

La motivación está en `proposal.md` y el comportamiento en `specs/`. Este documento resume solo el estado actual que condiciona el diseño:

- **Servicios.** Son proyectos Spring Boot 3.5.7 independientes, en Java 21. El servicio nuevo más reciente es `ms-notificaciones`, con `Dockerfile` en dos etapas, `.dockerignore`, `application.yml` con valores por defecto de `localhost`, `ClockConfig` y `TestcontainersConfiguration` con `@ServiceConnection`. `ms-fila` se arma con ese molde.
- **Trazabilidad.** `ms-reservas` y `ms-ventas` tienen el paquete `trazabilidad/` (`Correlacion`, `CorrelationIdFilter`, `RegistroSolicitudesFilter`, `RegistroOperaciones`, `PropagacionCorrelacionConfig`). El `RestClientCustomizer` propaga `X-Correlation-Id` a cualquier `RestClient` creado desde el `RestClient.Builder` inyectado. Un cliente nuevo en `ms-reservas` lo hereda sin configurar nada.
- **Creación de reservas.** `ReservaService.crear` abre la transacción con `TransactionTemplate`: advisory lock, inventario `FOR SHARE`, límite por cliente y `UPDATE` condicionales. La anti doble venta vive ahí y este cambio no la toca. `CodigoError` exige que cada código figure en `docs/openapi.yaml`. `ForbiddenException` hoy fija el código `ACCESO_DENEGADO`.
- **Gateway.** El gateway quita `/api`, no enruta `/internal/**` y aplica `JwtAuthFilter` global: fuera de `/api/auth/**` y de los `GET` de eventos y lugares, exige JWT y sobrescribe `X-User-Id` y `X-User-Rol`. `FallbackController` atiende `/fallback/{servicio}` de forma genérica, así que una ruta nueva no necesita código Java.
- **Pruebas existentes de `ms-reservas`** (más de 160). Corren con `@ActiveProfiles("test")` contra PostgreSQL de Testcontainers y no saben nada de `ms-fila`.

## Goals / Non-Goals

**Goals:**

- Orden de llegada estricto y posiciones únicas con cualquier cantidad de instancias de `ms-fila`, sin bloqueos en la JVM.
- Habilitación por tandas que no habilita dos veces al mismo cliente ni adelanta tandas, aunque el proceso corra en varias instancias a la vez.
- Que `ms-fila` nunca frene la venta: un costo acotado (1 s como máximo) y fail-open en `ms-reservas`.
- No cambiar el comportamiento ni las pruebas de `ms-reservas` para eventos sin fila.

**Non-Goals:**

- Garantizar el orden más allá de la reserva. La fila controla quién puede reservar; quién se queda con cada lugar lo sigue decidiendo la base de `ms-reservas`.
- Alta disponibilidad de Redis (réplicas, Sentinel o Cluster). El diseño de claves es compatible con Cluster, pero se despliega un solo nodo.
- Notificar en tiempo real (WebSocket o push). El cliente consulta su posición periódicamente.

## Decisions

### D1. Redis como almacenamiento de la fila

Una fila virtual es estado efímero y de mucha escritura: cientos de ingresos por segundo en la apertura de la venta. Después del evento, ese estado no sirve para nada. Redis cubre las cuatro necesidades:

- **Atomicidad.** Redis ejecuta los comandos y los scripts Lua de a uno. Un script que lee y escribe varias claves es atómico frente a todas las instancias de `ms-fila`, sin locks distribuidos ni transacciones con reintentos.
- **Estructuras ordenadas.** Un *sorted set* (`ZSET`) mantiene a los clientes ordenados por número de llegada. `ZRANK` da la posición en O(log N), `ZPOPMIN` saca la tanda en orden y `ZADD` con un puntaje de vencimiento modela las ventanas de compra.
- **TTL.** La expiración nativa limpia las filas abandonadas sin un job adicional (D5).
- **Rendimiento.** Trabaja en memoria con latencia de submilisegundos. La consulta de estado que `ms-reservas` hace en cada reserva no agrega carga a PostgreSQL justo en el pico.

Alternativas descartadas:

- **PostgreSQL**, con una tabla y `SELECT ... FOR UPDATE SKIP LOCKED`. Sirve, pero pone la carga del pico sobre una base relacional, y la posición exige un `COUNT` por consulta. Además, ningún servicio tiene base propia para la fila: habría que sumar otra instancia de PostgreSQL.
- **MongoDB**, que ya existe. No tiene un equivalente directo de `ZRANK` ni de `ZPOPMIN` atómico por lote. La posición exigiría `countDocuments` en cada consulta, y la tanda, un `findAndModify` por cliente.
- **Memoria de la JVM.** No funciona con varias instancias y se pierde al reiniciar.

Redis 7 (`redis:7-alpine`) corre con `appendonly yes` sobre un volumen, así que la fila sobrevive a un reinicio del contenedor. Se usa con `spring-boot-starter-data-redis` (Lettuce), `StringRedisTemplate` y `DefaultRedisScript`. El timeout de comando es de 500 ms (`spring.data.redis.timeout`), para que una caída de Redis falle rápido.

### D2. Modelo de claves

Todas las claves de una fila llevan el `eventoId` como *hash tag* (`{42}`). Así, un script que toca varias claves de la misma fila sigue siendo válido si algún día se usa Redis Cluster.

| Clave | Tipo | Contenido |
|---|---|---|
| `fila:{e}:config` | HASH | `activa`, `tamanoTanda`, `segundosEntreTandas`, `minutosVentana`, `proximaTandaEn` (epoch ms) |
| `fila:{e}:secuencia` | STRING | contador `INCR`; el número de llegada |
| `fila:{e}:espera` | ZSET | miembro `clienteId`, puntaje = número de llegada |
| `fila:{e}:habilitados` | ZSET | miembro `clienteId`, puntaje = `habilitadoHasta` (epoch ms) |
| `fila:activas` | SET | `eventoId` de las filas activas, para que el proceso sepa qué recorrer |

`minutosVentana` se copia de la propiedad `ticketya.fila.minutos-ventana` (10) al activar. Así todas las instancias usan el mismo valor aunque tengan configuraciones distintas.

### D3. Ingreso: un script Lua atómico

`ingresar(config, espera, habilitados, secuencia; clienteId, ahora)` hace todo en un solo paso atómico:

1. Si la fila no está activa, devuelve `NO_ACTIVA` (lleva a `409 FILA_NO_ACTIVA`).
2. Si el cliente está en `habilitados` con un puntaje mayor que `ahora`, devuelve `HABILITADO` y `habilitadoHasta` (idempotente).
3. Si está en `habilitados` pero la ventana venció, lo quita y marca `vencido=1`. Sigue en el paso 5 como ingreso nuevo.
4. Si ya está en `espera`, devuelve `EN_ESPERA`, `ZRANK + 1` y `nuevo=0` (idempotente).
5. Si no, hace `INCR secuencia`, `ZADD espera <n> clienteId` y devuelve `EN_ESPERA`, `ZRANK + 1` y `nuevo=1`.

Como Redis ejecuta los scripts de a uno, 200 ingresos simultáneos reciben los números de llegada 1 a 200 sin huecos ni repeticiones. Con la fila vacía, sus posiciones son exactamente 1 a 200. El servicio registra `INGRESO_FILA` solo si `nuevo=1`, y `TURNO_VENCIDO` si `vencido=1`. Como el `ZREM` del paso 3 es atómico, ese vencimiento no lo vuelve a registrar el proceso periódico.

*Alternativa descartada:* `ZADD NX` + `ZRANK` desde Java, en dos llamadas. La idempotencia sale bien, pero la verificación de "fila activa" y la del vencimiento quedan fuera de la operación atómica.

### D4. Tandas: un script con compuerta de tiempo, sin locks

`habilitarTanda(config, espera, habilitados; ahora)`:

1. Si la fila no está activa, o si `ahora < proximaTandaEn`, devuelve una lista vacía.
2. Hace `ZPOPMIN espera tamanoTanda` y, por cada cliente, `ZADD habilitados (ahora + minutosVentana) clienteId`.
3. Actualiza `proximaTandaEn = ahora + segundosEntreTandas * 1000`.
4. Devuelve los clientes habilitados.

La exclusión entre instancias sale de la atomicidad del script. La primera instancia que lo ejecuta después de `proximaTandaEn` saca la tanda y corre `proximaTandaEn` hacia adelante. Las demás ven `ahora < proximaTandaEn` y no hacen nada. `ZPOPMIN` saca a cada cliente una sola vez, así que nadie queda habilitado dos veces. Solo la instancia que recibió la lista registra `CLIENTE_HABILITADO`, una línea por cliente.

`proximaTandaEn` avanza aunque la fila esté vacía. Así, el ritmo nunca supera `tamanoTanda` clientes cada `segundosEntreTandas`, que es el límite que eligió el organizador. Al activar, la primera tanda queda `segundosEntreTandas` después, para que los clientes que llegan en la apertura alcancen a entrar a la fila antes de la primera habilitación.

*Alternativas descartadas:*

- **Lock distribuido** (`SET NX PX` o Redlock) alrededor de una lógica en Java. Agrega vencimientos del lock, una instancia que muere con el lock tomado y relojes desfasados, y no aporta nada que el script no resuelva.
- **ShedLock o líder único.** Agrega una dependencia y un punto único de falla.

### D5. Ventana de compra, vencimiento y TTL

- La ventana es el puntaje del cliente en `habilitados`. La consulta interna y la de posición comparan ese puntaje con `ahora` **en el momento de la consulta**. Así, un turno vencido deja de habilitar aunque el proceso periódico no haya corrido todavía.
- El proceso periódico también ejecuta `vencerTurnos(habilitados; ahora)`: `ZRANGEBYSCORE -inf ahora` y `ZREM` en el mismo script. Devuelve los clientes quitados para registrar `TURNO_VENCIDO`. Como el quitado es atómico, cada vencimiento se registra una sola vez entre todas las instancias.
- **TTL de seguridad.** Las claves de cada fila llevan `EXPIRE` de 24 horas (`ticketya.fila.ttl-horas`), que se renueva al activar y en cada tanda. Si nadie desactiva la fila y todas las instancias se detienen, Redis la limpia solo. Si el proceso encuentra un `eventoId` en `fila:activas` sin su `config`, lo quita del set.
- *Alternativa descartada:* una clave por turno con TTL y *keyspace notifications* para enterarse del vencimiento. Las notificaciones se pierden si no hay nadie suscripto y llegan a todas las instancias, así que el registro sería "a lo sumo una vez" o "varias veces", nunca "exactamente una".

### D6. Fuente de la hora: `Clock` inyectado

Los scripts reciben `ahora` (epoch ms) como argumento, tomado de un `Clock` de Spring (como `ClockConfig` en `ms-notificaciones`). Las pruebas usan un reloj controlable para atravesar tandas y ventanas sin esperar.

*Alternativa:* `redis.call('TIME')` dentro del script, que da una hora única para todas las instancias. Se descartó porque las pruebas tendrían que esperar en tiempo real. El costo de usar la hora de cada JVM es que, con relojes desfasados, una tanda puede adelantarse o atrasarse unos milisegundos. Nunca se duplica, porque la exclusión no depende de la hora (D4).

### D7. Proceso periódico

`ProcesadorFila`, con `@Scheduled(fixedDelayString = "${ticketya.fila.procesador.intervalo:1s}")`, se apaga con `ticketya.fila.procesador.habilitado=false`. En cada corrida recorre `fila:activas` y, por cada evento, ejecuta `vencerTurnos` y `habilitarTanda`. Cada corrida tiene su propio `correlationId` (`Correlacion.enProceso`), y las operaciones que registra usan el usuario `SISTEMA`. Captura las excepciones de cada evento y las de Redis caído, y las registra como advertencia. La corrida siguiente vuelve a intentar: el `@Scheduled` nunca se detiene. Las pruebas apagan el scheduler y llaman al método directamente.

### D8. API y respuestas

- **Administración.** Activar y desactivar quedan en `/fila/eventos/{id}/activar|desactivar`, no en `/internal`. Lo decidió el usuario, para que `ADMIN` y `ORGANIZADOR` puedan llegar por `/api/fila/**`. `activar` acepta un cuerpo opcional `{ tamanoTanda, segundosEntreTandas }` (1–1000 y 1–3600; por defecto 100 y 60) y responde `200` con `FilaConfiguracion`. `desactivar` responde `204`, es idempotente y borra las claves de la fila con `DEL`.
- **Cliente.** `ingresar` y `posicion` responden `FilaEstadoCliente`: `eventoId`, `activa`, `estado` (`EN_ESPERA`, `HABILITADO`, `FUERA_DE_FILA`, `SIN_FILA`), `posicion` (solo en espera), `habilitado` y `habilitadoHasta` (solo habilitado). Las dos responden siempre `200`, salvo el ingreso a una fila inactiva (`409 FILA_NO_ACTIVA`), para que el frontend pueda consultar periódicamente sin manejar errores.
- **Interno.** `GET /internal/fila/eventos/{e}/clientes/{c}/estado` responde `{ activa, habilitado }` sin control de identidad, como los demás `/internal/**`.
- **Errores** con el formato estándar. Códigos: `VALIDACION`, `ACCESO_DENEGADO`, `FILA_NO_ACTIVA` y `SERVICIO_NO_DISPONIBLE`. Este último sale con `503` ante `RedisConnectionFailureException`, `RedisSystemException` o `QueryTimeoutException`.

### D9. Identidad y roles en `ms-fila`

`ms-fila` usa una clase `Identidad` como la de `ms-reservas`: valida `X-User-Id` y `X-User-Rol` (`400 VALIDACION` si faltan) y el rol (`403 ACCESO_DENEGADO`). `cliente(...)` exige `CLIENTE`, y `administrador(...)` acepta `ADMIN` u `ORGANIZADOR`. No se verifica que el organizador sea el dueño del evento: haría falta consultar a `ms-eventos`, y queda como simplificación.

### D10. Integración en `ms-reservas`: consulta con fail-open

- `client/FilaClient` se construye desde el `RestClient.Builder` inyectado, así que hereda la propagación de `X-Correlation-Id`. Usa `SimpleClientHttpRequestFactory` con connect y read timeout de `ticketya.services.fila-timeout-ms` (1000) y la URL `ticketya.services.fila-url: ${MS_FILA_URL:http://localhost:8086}`.
- `ReservaService.crear` llama a `FilaClient` **antes** de abrir la transacción. Una llamada HTTP dentro de la transacción retendría una conexión de Hikari hasta 1 s en pleno pico. El controlador ya resolvió la identidad y `@Valid` antes. Además, `ReglasReserva.validarPedido` se mueve antes de la consulta, para que un pedido inválido responda `400` aunque el cliente no esté habilitado. Es una función pura que hoy se ejecuta al inicio de la transacción, así que moverla no cambia ninguna respuesta.
- Si la respuesta es `activa=true` y `habilitado=false`, se lanza `CodigoError.filaNoHabilitado()`: `403` con código `FILA_NO_HABILITADO`. Para eso, `ForbiddenException` suma un constructor `(code, message)`.
- **Fail-open (el fallback).** Cualquier excepción de la llamada se captura: conexión rechazada, timeout, respuesta no 2xx o cuerpo ilegible. El servicio registra `WARN FILA_NO_DISPONIBLE eventoId= clienteId= causa= correlationId=` y la reserva sigue como si no hubiera fila. La justificación:
  - La fila es una medida de equidad y de control de carga. No es una garantía de integridad: la anti doble venta y el límite por cliente siguen en PostgreSQL. Si `ms-fila` cae, se pierde el orden durante la caída, pero no se vende dos veces un lugar.
  - Con fail-closed, una caída de `ms-fila` (o de Redis) detendría todas las ventas, incluidas las de eventos sin fila, porque `ms-reservas` no puede saber si un evento tiene fila sin preguntar. El servicio auxiliar se volvería un punto único de falla de la venta.
  - Es el mismo criterio que con `ms-notificaciones`: un servicio auxiliar nunca frena la compra.
  - El timeout de 1 s acota el costo. Una conexión rechazada falla al instante, y un `ms-fila` colgado agrega como máximo 1 s por reserva.
- *Alternativas descartadas:*
  - **Circuit breaker de Resilience4j en `ms-reservas`.** Evitaría el segundo de espera cuando `ms-fila` está colgado, pero suma una dependencia y configuración a un servicio que hoy no la tiene. Queda como mejora.
  - **Caché del estado.** Un cliente recién habilitado tendría que esperar a que la caché venza.
- **Pruebas existentes.** En `src/test/resources/application-test.yml` se agrega `ticketya.fila.consultar: false`, que saltea la consulta. Es necesario porque, en Windows, conectarse a un puerto cerrado de `localhost` tarda alrededor de 1 s antes del rechazo: sin la propiedad, cada reserva de las más de 160 pruebas sumaría hasta 1 s y las de concurrencia cambiarían sus tiempos. Además, un `ms-fila` corriendo localmente podría alterar los resultados. Las pruebas nuevas activan la consulta (`ticketya.fila.consultar=true`) y apuntan `fila-url` a un `HttpServer` propio. La propiedad vale `true` por defecto en `application.yml`. No se modifica ninguna prueba existente.

### D11. Ruta en el gateway

En `api-gateway/src/main/resources/application.yml` se agregan solo tres cosas:

- La ruta `fila`: `uri: ${MS_FILA_URL:http://localhost:8086}`, `order: 0`, `Path=/api/fila/**`, `StripPrefix=1` y `CircuitBreaker` con `name: fila` y `fallbackUri: forward:/fallback/fila`.
- La instancia `fila` del circuit breaker, con los mismos valores que las otras.
- La instancia `fila` del time limiter (20 s).

`/api/fila/**` no se superpone con `/api/eventos/**`. Igual se declara con `order: 0`, antes de la ruta general de eventos (`order: 2`), como las rutas de disponibilidad. `JwtAuthFilter` ya exige token en `/api/fila/**` porque no es una ruta pública: no cambia. `FallbackController` es genérico: no cambia. `/internal/fila/**` sigue sin ruta. Para probar la ruta se agrega una clase de prueba nueva en el gateway, sin tocar las existentes.

### D12. Trazabilidad en `ms-fila`

Se copia el paquete `trazabilidad/` de `ms-reservas`: `Correlacion`, `CorrelationIdFilter`, `RegistroSolicitudesFilter` y `RegistroOperaciones`. La única diferencia está en `RegistroOperaciones`: sin transacciones de base, escribe la línea apenas el script devuelve su resultado. Operaciones: `FILA_ACTIVADA`, `FILA_DESACTIVADA`, `INGRESO_FILA`, `CLIENTE_HABILITADO` y `TURNO_VENCIDO`, con `eventoId`, `clienteId`, `posicion`, `habilitadoHasta`, `tamanoTanda` y `segundosEntreTandas`, según corresponda. `ms-fila` no hace llamadas salientes, así que no necesita `PropagacionCorrelacionConfig`.

### D13. Despliegue

- **`redis`** en `docker-compose.yml`: `image: redis:7-alpine`, `command: ["redis-server", "--appendonly", "yes"]`, puerto `6379:6379`, volumen `redis-data:/data`, `healthcheck: ["CMD", "redis-cli", "ping"]` con `interval: 5s`, `timeout: 5s`, `retries: 10` y `start_period: 10s`.
- **`ms-fila`**: build `./ms-fila`, `REDIS_HOST: redis`, `REDIS_PORT: "6379"`, puerto `8086:8086` y `depends_on: redis (service_healthy)`.
- **`ms-reservas`** recibe `MS_FILA_URL: http://ms-fila:8086`, sin `depends_on` (fail-open).
- **`api-gateway`** recibe `MS_FILA_URL` y suma `ms-fila` a su `depends_on`.
- **Pruebas de `ms-fila`**: `GenericContainer("redis:7-alpine")` con puerto 6379 y `@ServiceConnection(name = "redis")`, con Testcontainers 1.21.4 (mismo comentario que en los otros pom). Cada prueba vacía Redis con `FLUSHDB` en `@BeforeEach`.

### D14. Simplificaciones frente a un sistema real

| Sistema real | Esta versión | Por qué alcanza |
|---|---|---|
| El turno es un token firmado de corta vida que el checkout valida sin llamar a la fila | `ms-reservas` consulta a `ms-fila` en cada reserva | Una consulta a Redis cuesta poco, y no hay que gestionar claves ni rotación |
| El turno se consume al comprar | El turno dura hasta que vence su ventana | El límite por cliente sigue en `ms-reservas`; durante la ventana, el cliente solo puede reservar hasta ese límite |
| Ritmo adaptativo según la capacidad del checkout | Tandas fijas que configura el organizador | Es predecible y fácil de probar |
| Sorteo de posiciones entre los que llegan antes de la apertura | Orden estricto de llegada a Redis | El enunciado pide orden de llegada |
| Protección anti-bots (captcha, huella del dispositivo) | Ninguna; un cliente por `X-User-Id` autenticado | El JWT ya identifica al cliente; la idempotencia impide ocupar varios lugares |
| Posición y tiempo estimado por push o WebSocket | Consulta periódica de `GET /posicion` | Sin infraestructura nueva |
| Redis replicado (Sentinel o Cluster) | Un nodo con AOF | Con fail-open, una caída de Redis no detiene la venta |
| Activación automática al abrir la venta y validación del dueño del evento | Activación manual por `ADMIN` u `ORGANIZADOR`, sin validar dueño | Evita acoplar `ms-fila` a `ms-eventos` en esta entrega |
| Control también en el pago | Solo al reservar | Para comprar hace falta una reserva, así que controlar la reserva controla la compra |

## Risks / Trade-offs

- [Durante una caída de `ms-fila`, cualquier cliente puede reservar y saltear la fila] → Es la decisión de fail-open de D10. La integridad la sigue garantizando PostgreSQL, y la advertencia en el log deja ver cuánto duró la caída.
- [Un `ms-fila` colgado (acepta la conexión pero no responde) suma 1 s a cada reserva] → El timeout lo acota. El circuit breaker local queda como mejora (D10).
- [Relojes desfasados entre instancias adelantan o atrasan una tanda unos milisegundos] → No afecta la unicidad (D4 y D6). Los contenedores comparten el reloj del anfitrión.
- [Las tandas fijas pueden habilitar más clientes que los lugares que quedan] → Los habilitados de más reciben `409` por cupo o asiento, como hoy. La anti doble venta no cambia.
- [Redis de un solo nodo; si se pierde el volumen, se pierde la fila] → AOF sobre un volumen. La fila es efímera: se puede volver a activar y los clientes vuelven a ingresar.
- [`ticketya.fila.consultar=false` en el perfil de prueba podría ocultar una regresión del chequeo] → Las pruebas nuevas de `ms-reservas` lo activan y cubren cada escenario de la spec, incluido el fail-open.
- [Desactivar la fila borra la espera sin aviso] → Es lo esperado: con la fila desactivada todos pueden reservar, y la consulta de posición devuelve `SIN_FILA`.

## Migration Plan

1. Levantar `redis` y `ms-fila` (o `docker compose up -d --build`). Ninguna fila está activa, así que el sistema se comporta igual que antes.
2. Desplegar `ms-reservas` con `MS_FILA_URL`. Mientras no haya filas activas, cada reserva hace una consulta que responde `activa=false`.
3. Desplegar el gateway con la ruta nueva.
4. **Rollback:** desactivar las filas, o detener `ms-fila`, y el fail-open deja pasar todas las reservas. Para quitar el cambio por completo, revertir `ms-reservas` y la ruta del gateway. No hay migraciones de base.
