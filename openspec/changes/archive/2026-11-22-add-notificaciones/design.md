# Design

## Context

- Los cuatro microservicios actuales usan Spring Boot 3.5.7, Java 21, Lombok y springdoc. Sus paquetes son `controller`, `service`, `repository`, `model`, `dto`, `exception` y `config`, y todos devuelven el mismo `ErrorResponse`. La identidad llega en los headers `X-User-Id` y `X-User-Rol` y se valida en una clase `Identidad`, con `400 VALIDACION` si faltan los headers y `403` si el rol no corresponde. Las páginas tienen el formato `content`, `page`, `size`, `totalElements` y `totalPages`.
- Cada servicio lee su conexión de variables de entorno, con `localhost` como valor por defecto, y tiene un `Dockerfile` en dos etapas (Maven y JRE 21 alpine) con `-DskipTests`.
- El gateway ya genera y propaga `X-Correlation-Id` (cambio `add-trazabilidad`).
- `ms-reservas` usa Testcontainers 1.21.4, fijado porque la 1.21.3 que trae Boot 3.5.7 no funciona con Docker Engine 29.
- Los endpoints `/internal/**` no pasan por el gateway.
- No se puede tocar a los servicios que van a llamar a este (ver proposal.md, fuera de alcance). El contrato tiene que quedar estable y documentado para que lo adopten después.

## Goals / Non-Goals

**Goals:**
- Que ningún pedido aceptado se pierda: primero se guarda en MongoDB y después se envía.
- Que un envío lento o fallido nunca demore ni haga fallar al servicio que llamó.
- Que la deduplicación siga funcionando con pedidos simultáneos, igual que la garantía anti doble venta, que se resuelve en la base y no en memoria.
- Que las pruebas no dependan del azar: las fallas del envío se deciden por destinatario y las esperas se configuran.

**Non-Goals:**
- Envío real de email o push. Hay un único canal, el email simulado en el log. El push queda para un cambio futuro, que agregaría otra implementación del mismo componente de envío.
- Decidir a quién notificar. El servicio que llama arma un pedido por destinatario: `ms-ventas` uno por compra, y `ms-eventos` uno por comprador o por cliente con ese artista favorito. `ms-notificaciones` no consulta compras ni preferencias.
- Plantillas de mensaje o internacionalización. El "email" es una línea de log con los datos.
- Broker de mensajería (RabbitMQ o Kafka).
- Ruta en el gateway y JWT propio. Se confía en los headers que propaga el gateway, como en los demás servicios.

## Decisions

### D1. MongoDB para las notificaciones

Este dato se adapta mejor a un modelo de documentos que al relacional de los otros servicios:

- **Forma variable según el tipo.** `COMPRA_CONFIRMADA` lleva compra, total y entradas; `EVENTO_MODIFICADO` lleva los cambios con su valor anterior y el nuevo; `ARTISTA_FAVORITO_PUBLICO_EVENTO` lleva artista, ciudad y evento. En un documento, `datos` se guarda como un subdocumento tal como llega, sin migrar el esquema cada vez que aparece un tipo o un campo nuevo. En PostgreSQL la alternativa sería una columna `jsonb` o una tabla por tipo, y las dos traen más rigidez o más mapeo sin ningún beneficio.
- **Escritura intensa.** Cancelar un evento grande genera miles de pedidos de golpe, y cada uno es una inserción más dos o tres actualizaciones de estado. Las operaciones son sobre un solo documento, sin transacciones entre tablas.
- **Sin joins.** La notificación tiene todo lo que necesita: destinatario, datos e historial de intentos. No se relaciona con otras entidades de este servicio.
- **Consultas por usuario y estado.** El administrador filtra por `usuarioId`, `tipo` y `estado`, y el procesador busca por `estado` y `proximoIntentoEn`. Las dos consultas se cubren con índices compuestos.

Alternativa descartada: PostgreSQL con `jsonb`. Funcionaría, pero no cumple el requisito de base NoSQL de la Entrega 2 y no aprovecha que el dato ya es un documento.

### D2. Modelo del documento e índices

Colección `notificaciones` en `db_notificaciones`:

```json
{
  "_id": "ObjectId",
  "tipo": "COMPRA_CONFIRMADA",
  "destinatario": { "usuarioId": "101", "email": "cliente@example.com" },
  "datos": { "compraId": 55, "total": 30000 },
  "estado": "PENDIENTE | ENVIADA | FALLIDA",
  "intentos": 0,
  "ultimoError": null,
  "claveDeduplicacion": "compra-55",
  "correlationId": "abc-123",
  "creadaEn": "ISODate", "actualizadaEn": "ISODate",
  "proximoIntentoEn": "ISODate", "enviadaEn": null
}
```

Índices, creados de forma explícita al arrancar con `MongoTemplate.indexOps` y no con `auto-index-creation`, para que queden a la vista en el código:
- `claveDeduplicacion` único y **parcial** (`partialFilterExpression: { claveDeduplicacion: { $type: "string" } }`), para que muchas notificaciones sin clave no choquen entre sí.
- `{ estado: 1, proximoIntentoEn: 1 }`, para el procesador.
- `{ "destinatario.usuarioId": 1, estado: 1, creadaEn: -1 }`, para la consulta del administrador.

El `id` público es el `ObjectId` como string. Las fechas se guardan como `Instant` (UTC) y se toman de un `Clock` inyectable, como el `ClockConfig` de `ms-ventas`, para que las pruebas las controlen.

### D3. Asincronía: se guarda primero y un procesador periódico envía

`POST /internal/notificaciones` valida el pedido, inserta el documento `PENDIENTE` con `proximoIntentoEn = ahora` y responde `202`. Un procesador con `@Scheduled` (cada 1 s por defecto, configurable) toma las notificaciones vencidas y las envía.

Cada notificación se toma de forma atómica con `findAndModify`: el filtro es `{ estado: PENDIENTE, proximoIntentoEn <= ahora }` y la actualización corre `proximoIntentoEn` a `ahora + reserva` (30 s), sin cambiar el estado. Si hay dos instancias, o si el procesador se solapa consigo mismo, cada notificación la toma una sola. Si el proceso se cae a mitad de un envío, la reserva vence y la notificación se vuelve a intentar. La entrega es "al menos una vez", que alcanza para un email simulado.

Alternativas descartadas:
- **`@Async` en memoria sin persistir antes.** Es lo más simple, pero un reinicio pierde los pedidos aceptados y los reintentos quedan en hilos dormidos. No cumple el requisito de pendientes tras un reinicio.
- **`@Async` para el primer intento y procesador para los reintentos.** Baja la latencia, pero hay dos caminos de envío que pueden competir por la misma notificación. Un segundo de latencia es aceptable para un email.
- **Broker de mensajes.** Es la solución natural a escala, pero suma infraestructura y obliga a cambiar a los servicios que llaman, que están fuera de alcance. El endpoint HTTP puede pasar a ser un consumidor más adelante sin cambiar el modelo.

### D4. Reintentos con espera exponencial

Al fallar el intento `n`:
- si `n < maxIntentos`, la notificación sigue `PENDIENTE`, con `intentos = n`, `ultimoError` y `proximoIntentoEn = ahora + esperaInicial × factor^(n-1)`;
- si `n = maxIntentos`, pasa a `FALLIDA` y el procesador ya no la toma, porque filtra por `PENDIENTE`.

Configuración en `ticketya.notificaciones.reintentos`: `max-intentos: 3`, `espera-inicial: 2s` y `factor: 2`. Con esos valores, las esperas son de 2 s y 4 s. Las pruebas usan valores en milisegundos. No se agrega variación aleatoria a la espera: con una sola instancia no hay avalanchas sincronizadas, y así las pruebas son deterministas.

La lógica de reintento es propia y no de Spring Retry o Resilience4j, porque la espera tiene que guardarse en el documento para que sobreviva a un reinicio. Esas librerías reintentan en memoria, dentro del mismo hilo.

### D5. Deduplicación garantizada por la base

Se inserta directamente, sin consultar antes. Si MongoDB lanza `DuplicateKeyException` por `claveDeduplicacion`, se busca la notificación existente por esa clave y se responde `202` con su `id` y estado. Consultar antes de insertar tiene una carrera entre la consulta y la inserción: dos pedidos simultáneos verían "no existe" y los dos insertarían. Con el índice único, uno solo gana siempre.

Se responde `202` y no `409` porque la clave sirve para que el servicio que llama pueda reintentar sin miedo: un reintento de un pedido que ya se aceptó es un éxito para él.

### D6. Aislamiento de fallas

Del lado de `ms-notificaciones`, el envío ocurre en el procesador y nunca en el hilo del pedido. Lo único que puede hacer fallar el `POST` es un error de validación o que MongoDB no esté disponible, y en ese caso responde `5xx`.

Del lado de los servicios que llaman, que se implementa en otros cambios, el contrato pendiente recomienda timeouts cortos (1 s para conectar y 2 s para leer), capturar cualquier error, registrarlo y seguir, y llamar después de confirmar la propia transacción. Así se cumple US-26: "la compra no se ve afectada".

### D7. Envío simulado y fallas configurables

Una interfaz `CanalEnvio` con una sola implementación, `EmailSimulado`, que escribe en el log con nivel INFO: destinatario, tipo, datos y `correlationId`. Lanza `EnvioFallidoException` si el email está en `ticketya.notificaciones.simulacion.emails-que-fallan` (por defecto `falla@ticketya.test`), o si está en `emails-que-fallan-una-vez` (por defecto `falla-una-vez@ticketya.test`) y es el primer intento. La decisión usa el número de intento que viene en la notificación, sin estado en memoria, así que es determinista.

### D8. Correlación y logs

El controlador lee `X-Correlation-Id` o genera un UUID, y lo guarda en el documento. El procesador pone el `correlationId` en el MDC mientras procesa cada notificación, y el patrón de log lo muestra, para alinearse con `add-trazabilidad`.

### D9. Identidad, errores y paginación

Se copian del resto de los servicios, sin compartir código entre ellos porque los servicios son independientes: `Identidad.admin(userId, rol)`, `ErrorResponse`, `GlobalExceptionHandler` y la validación `page >= 0` y `1 <= size <= 100`. Los filtros `tipo` y `estado` inválidos dan `400 VALIDACION` en lugar del error genérico de conversión. El listado usa `MongoTemplate` con `Criteria` armado según los filtros presentes y orden `creadaEn` descendente.

### D10. Contenedores

- `mongo`: imagen `mongo:7`, `container_name: ticketya-mongo`, `MONGO_INITDB_ROOT_USERNAME`/`PASSWORD` `ticketya` (credenciales de desarrollo, igual que las de PostgreSQL), puerto `27017:27017`, volumen `mongo-data:/data/db` y healthcheck `mongosh --quiet --eval "db.adminCommand('ping').ok"`. Es un contenedor genérico y no `db-notificaciones`, porque la Entrega 2 también prevé MongoDB para preferencias. Cada servicio usará su propia base dentro de él.
- `MONGO_URI`: por defecto `mongodb://ticketya:ticketya@localhost:27017/db_notificaciones?authSource=admin`. En compose es la misma URI con el host `mongo`.
- `ms-notificaciones`: el `Dockerfile` y el `.dockerignore` son iguales a los de `ms-ventas`, con `EXPOSE 8085`, y el servicio usa `depends_on: mongo: condition: service_healthy`. El gateway no depende de él hasta que exista su ruta.

### D11. Pruebas

- Testcontainers 1.21.4 (`testcontainers.version` en el pom, como en `ms-reservas`), con los módulos `mongodb` y `junit-jupiter` y `spring-boot-testcontainers`. Una `TestcontainersConfiguration` con `MongoDBContainer("mongo:7")` y `@ServiceConnection`. No se usa Mongo embebido ni se simula el repositorio en las pruebas de persistencia.
- Las esperas asíncronas se verifican con Awaitility, que ya viene en `spring-boot-starter-test`, y no con `Thread.sleep`.
- Perfil de pruebas con el procesador cada 100 ms y la espera inicial en 200 ms. Las pruebas del servicio de reintentos usan un `Clock` fijo y avanzan el tiempo, sin esperar de verdad.
- La prueba de concurrencia de deduplicación sigue el patrón de `ConcurrenciaReservasTest`: `CountDownLatch` y N hilos.

## Risks / Trade-offs

- [Con la reserva de 30 s, si el proceso se cae a mitad de un envío, esa notificación puede enviarse dos veces.] → Se acepta la entrega "al menos una vez", porque el email es simulado. La reserva dura bastante más que un envío simulado.
- [El procesador consulta MongoDB cada segundo aunque no haya pendientes.] → La consulta usa el índice `{estado, proximoIntentoEn}` y es barata. El intervalo es configurable.
- [Si MongoDB no está disponible, el `POST` responde `5xx` y el servicio que llama pierde la notificación.] → El contrato pide a los servicios que llaman que registren el error y sigan. Una cola local de reintentos del lado del que llama queda fuera de alcance.
- [Cancelar un evento grande genera muchos pedidos HTTP, uno por comprador.] → Es aceptable para el volumen del TP. Si hiciera falta, se agregaría un endpoint de lote en otro cambio, sin cambiar el modelo.
- [Hasta que se agreguen la ruta del gateway y las llamadas de los demás servicios, la funcionalidad solo se ejercita llamando directo al puerto 8085.] → Los pendientes quedan en `tasks.md` con el contrato exacto, y la colección de Postman incluye los requests directos.
- [Se confía en `X-User-Rol` sin validar JWT en el servicio.] → Es el mismo modelo que los demás servicios. El puerto 8085 solo se expone en el entorno de desarrollo.

## Migration Plan

Es un servicio nuevo y no hay datos que migrar. Para desplegar alcanza con `docker compose up -d --build`. Para volver atrás, se quitan los servicios `mongo` y `ms-notificaciones` del compose. Ningún otro servicio depende de él todavía.
