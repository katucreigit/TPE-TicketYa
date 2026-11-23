# Design

## Context

La motivación está en `proposal.md` y el comportamiento en `specs/`. Este documento resume solo el estado actual que condiciona el diseño:

- **Molde del servicio.** `ms-notificaciones` (Spring Boot 3.5.7, Java 21, MongoDB 7 con Testcontainers 1.21.4, `Dockerfile` en dos etapas) es el molde del servicio nuevo. `ms-fila` ya trae una copia del paquete `trazabilidad/` de `ms-reservas`: `CorrelationIdFilter`, `RegistroSolicitudesFilter` y `Correlacion`. El servicio nuevo la reutiliza, junto con `PropagacionCorrelacionConfig` de `ms-reservas`, el `RestClientCustomizer` que propaga `X-Correlation-Id` a las llamadas salientes.
- **Catálogo.** `ms-eventos` expone `GET /eventos`: es pública, está paginada (`size` máximo 100) y devuelve `EventoResumenResponse` con `id`, `nombre`, `tipo`, `estado`, `ciudad`, `fechaHora`, `precioDesde` y `participantes` (nombres). Devuelve los eventos `PUBLICADO` y `AGOTADO`; las recomendaciones usan solo los `PUBLICADO`. No hace falta cambiar `ms-eventos`.
- **Confirmación de compras en `ms-ventas`.** Una compra se confirma por tres caminos: la compra directa, el reintento de un pago incierto y `ReconciliacionPagosJob`. Los tres terminan en `CompraService.notificarCompraConfirmada`, que llama a `NotificacionesClient`, un cliente best-effort que nunca lanza excepciones. Las pruebas construyen `CompraService` con constructores explícitos (`CompraServiceTest` y `ReconciliacionPagosJobTest`), así que sus firmas no pueden cambiar. `ms-ventas` ya propaga el `X-Correlation-Id` con su `PropagacionCorrelacionConfig`.
- **Gateway.** `JwtAuthFilter` exige JWT en todo lo que no sea `/api/auth/**` ni los `GET` de eventos y lugares, así que las rutas nuevas quedan protegidas sin tocar el filtro. `FallbackController` es genérico (`/fallback/{servicio}`). `ApiGatewayApplicationTests` fija la cantidad de rutas: hoy son 6.

## Goals / Non-Goals

**Goals:**

- Recomendaciones explicables y determinísticas: cada evento trae su puntaje y los motivos.
- Que `ms-recomendaciones` nunca afecte una compra y nunca responda `500` por culpa de `ms-eventos`.
- Que las pruebas existentes de `ms-ventas` y del gateway sigan pasando sin cambios, salvo la que cuenta las rutas, que sube a 7 a propósito.

**Non-Goals:**

- Machine learning, filtrado colaborativo con factorización o recomendaciones en tiempo real.
- Importar las compras confirmadas antes de este cambio (ver D10).
- Restar las compras devueltas o los eventos cancelados del historial.

## Decisions

### D1. MongoDB para preferencias y compras

Se reutiliza el contenedor `mongo`, con una base propia (`db_recomendaciones`). Las razones:

- **Documentos flexibles.** Las preferencias de un cliente son un solo documento con dos listas (`tipos` y `artistas`) y un campo opcional (`ciudad`). Agregar otro criterio más adelante (por ejemplo, rango de precio) no requiere migrar un esquema.
- **Listas anidadas.** Una compra guarda `eventoIds` como un arreglo, y un índice multikey sobre ese arreglo resuelve "quiénes compraron el evento X" sin tablas intermedias.
- **Sin joins.** Todas las lecturas son por `clienteId` o por `eventoId`, y los conteos de popularidad y de "también compraron" se hacen con el *aggregation pipeline* (`$match`, `$unwind`, `$group`) dentro de la base.
- **Infraestructura existente.** El proyecto ya tiene MongoDB (`ms-notificaciones`), así que no suma un motor nuevo.

*Alternativa descartada:* PostgreSQL. Necesitaría una tabla para las preferencias, otra para tipos y artistas o columnas `text[]`, y otra para las compras con su detalle por evento, para un dato que solo este servicio lee.

### D2. Modelo de datos

| Colección | `_id` | Campos | Índices |
|---|---|---|---|
| `preferencias` | `clienteId` | `tipos` (enum), `artistas` (texto tal como lo escribió el cliente), `ciudad`, `actualizadaEn` | el `_id` |
| `compras` | `compraId` | `clienteId`, `eventoIds`, `registradaEn` | `clienteId`; `eventoIds` (multikey) |

- **Idempotencia.** `POST /internal/compras` hace un `insert` y captura `DuplicateKeyException`, igual que la deduplicación de `ms-notificaciones`. Como `compraId` es el `_id`, la unicidad la garantiza la base, también con pedidos simultáneos.
- **Reemplazo de preferencias.** `PUT /preferencias` hace un `save` del documento completo con `_id = clienteId` (upsert): reemplaza todo, como pide la spec.
- **Limpieza al guardar.** Los repetidos se quitan antes de guardar. Dos artistas son iguales si coinciden después de normalizarlos (D3), y se conserva la primera forma escrita.

### D3. Puntaje: función pura

`CalculadoraPuntaje` no depende de Spring ni de Mongo, así que se prueba con pruebas unitarias. Recibe:

- el evento candidato,
- las preferencias del cliente,
- un mapa `eventoId → compradores` (clientes distintos),
- un mapa `eventoId → compradores en común` (clientes distintos que compraron algún evento del cliente y también este).

| Componente | Peso | Motivo |
|---|---|---|
| `tipo` en las preferencias | 3 | `TIPO` |
| algún participante coincide con un artista favorito | 3 | `ARTISTA` |
| `ciudad` igual a la de las preferencias | 2 | `CIUDAD` |
| compradores en común / máximo de compradores en común entre los candidatos | 2 × (0 a 1) | `TAMBIEN_COMPRARON` (si es mayor que 0) |
| compradores / máximo de compradores entre los candidatos | 1 × (0 a 1) | `POPULAR` (si es mayor que 0) |

- **Normalización de texto.** Pasa a minúsculas, quita los espacios de los extremos y las tildes (`Normalizer` NFD sin marcas diacríticas). Así `Tandil` coincide con `tandil ` y `Andariegos` con `ANDARIEGOS`.
- **Componentes normalizados.** La popularidad y los compradores en común se dividen por el máximo entre los candidatos. Así una preferencia explícita (de 2 a 3 puntos) pesa más que la popularidad (1 como máximo), y el resultado no depende de cuántas compras haya en total.
- **Orden y desempate.** El resultado se redondea a dos decimales (`HALF_UP`). Se ordena por puntaje descendente, luego por `fechaHora` ascendente y luego por `eventoId`.
- **Casos sin datos.** Si el cliente no tiene preferencias ni compras, solo cuenta la popularidad, que da "los más populares". Si además nadie compró nada, todos quedan en 0 y el desempate por fecha devuelve los próximos eventos.

*Alternativa descartada:* pesos configurables por propiedades. Agrega superficie sin necesidad en esta entrega.

### D4. Consultas sobre las compras

`CompraRepositoryCustom`, con `MongoTemplate`, resuelve tres consultas:

1. **Compradores por evento:** `$unwind eventoIds`, luego `$group {eventoId, clienteId}` y luego `$group eventoId` con conteo. Cuenta clientes distintos.
2. **Compradores en común del cliente C:**
   - Se leen los eventos que compró C, el conjunto E.
   - Se buscan los clientes que compraron algún evento de E, sin contar a C: el conjunto K.
   - Se cuentan los clientes distintos de K que compraron cada evento que no está en E.
3. **También compraron X:** igual que la consulta 2, pero E es solo `{X}` y no se excluye a nadie.

Cada consulta usa el índice multikey de `eventoIds` o el de `clienteId` para el primer `$match`.

### D5. Catálogo con caché y fallback

`CatalogoEventos` mantiene una sola copia en memoria: un `AtomicReference` con los eventos y la hora en que se obtuvieron, tomada del `Clock` inyectado.

- **Copia vigente.** Si tiene menos de 30 segundos (`ticketya.catalogo.ttl`), se usa sin consultar a nadie (`degradado=false`).
- **Copia vencida o inexistente.** Pide `GET /eventos?page=n&size=100` hasta `totalPages`, con un tope de 20 páginas (2000 eventos), y se queda con los `PUBLICADO`. Usa `connect-timeout` de 1 s y `read-timeout` de 2 s. Si responde, reemplaza la copia y sigue con `degradado=false`.
- **`ms-eventos` falla.** Ante cualquier excepción o respuesta que no sea 2xx en cualquier página, se descarta la consulta entera, sin guardar copias parciales, y se registra `WARN CATALOGO_NO_DISPONIBLE causa= correlationId=`. Se usa la última copia aunque esté vencida o, si no hay, una lista vacía, en los dos casos con `degradado=true`.
- **Pedidos simultáneos con la copia vencida.** Pueden consultar a la vez sin romper nada: el último en terminar deja su copia. No vale la pena un lock para esta escala.
- **Correlación.** El cliente HTTP se arma desde el `RestClient.Builder` inyectado, así que propaga `X-Correlation-Id`.

*Alternativas descartadas:*

- **Caché de Spring o Caffeine.** Para una sola entrada con fallback a la versión vencida, el `AtomicReference` es más simple y deja explícita la regla "vencida pero útil".
- **Circuit breaker de Resilience4j.** Los timeouts acotan la espera, y la copia en memoria cubre la mayoría de los pedidos durante una caída. Queda como mejora.

### D6. API y respuestas

- **`GET /preferencias` y `PUT /preferencias`.** El body es `PreferenciasRequest(List<TipoEvento> tipos, List<@NotBlank @Size(max=100) String> artistas, @Size(max=100) String ciudad)`, con `@Size(max=20)` en la lista de artistas. Responden `PreferenciasResponse(clienteId, tipos, artistas, ciudad, actualizadaEn)`. Un tipo inválido llega como `HttpMessageNotReadableException` (`InvalidFormatException`) y sale como `400 VALIDACION`, como en `ms-notificaciones`. `ciudad` vacía o en blanco se guarda como `null`.
- **`GET /recomendaciones?limite=10`.** Responde `RecomendacionesResponse(degradado, eventos)`. Cada `EventoRecomendado` trae `eventoId`, `nombre`, `tipo`, `ciudad`, `fechaHora`, `participantes`, `puntaje` y `motivos`.
- **`GET /recomendaciones/eventos/{eventoId}/tambien-compraron?limite=10`.** Responde `TambienCompraronResponse(eventoId, degradado, eventos)`. Cada `EventoTambienComprado` trae los datos del evento y `compradoresEnComun`.
- **`POST /internal/compras`.** Recibe `CompraRegistradaRequest(@NotBlank compraId, @NotBlank clienteId, @NotEmpty List<@NotNull @Positive Long> eventoIds)` y responde `204`.
- **`limite`.** Se valida con `@Min(1) @Max(50)` y `@Validated` en el controlador; un valor fuera de rango responde `400 VALIDACION`.
- **Errores.** Formato estándar, con `ErrorResponse` y `GlobalExceptionHandler` copiados de `ms-fila`. Si MongoDB no está disponible (`DataAccessResourceFailureException`), responde `503 SERVICIO_NO_DISPONIBLE`.

### D7. Identidad y roles

Hay una clase `Identidad`, como la de `ms-fila`:

- `cliente(userId, rol)` exige `CLIENTE` y se usa en las preferencias y en `GET /recomendaciones`.
- `autenticado(userId, rol)` acepta los cuatro roles y se usa en "también compraron", que no es personal.

Sin los headers responde `400 VALIDACION`; con otro rol, `403 ACCESO_DENEGADO`. `/internal/compras` no controla identidad, como los demás `/internal/**`.

### D8. Aviso de compra desde `ms-ventas`

- **`client/RecomendacionesClient`.** Copia el enfoque de `NotificacionesClient`: `RestClient` desde el builder inyectado (hereda la propagación de `X-Correlation-Id`), `connect-timeout` de 500 ms, `read-timeout` de 1000 ms y un `try/catch` que nunca lanza. Si falla registra `WARN RECOMENDACIONES_NO_DISPONIBLE compraId= clienteId= causa= correlationId=`. Las propiedades son `ticketya.services.recomendaciones-url: ${MS_RECOMENDACIONES_URL:http://localhost:8087}`, `recomendaciones-connect-timeout-ms` y `recomendaciones-read-timeout-ms`.
- **Punto de enganche.** La llamada va dentro de `CompraService.notificarCompraConfirmada`, después del aviso a notificaciones, porque es el único punto por el que pasan los tres caminos de confirmación, incluida la reconciliación. Envía `compraId`, `clienteId` y `eventoIds = [eventoId]`, ya que una compra corresponde a una reserva de un solo evento.
- **Inyección sin tocar los constructores.** El cliente entra por un setter `@Autowired(required = false)`. Los cuatro constructores que usan las pruebas no cambian y, si el cliente es `null`, no se llama.
- **Interruptor.** La propiedad `ticketya.recomendaciones.avisar` vale `true` por defecto y `false` en `src/test/resources/application-test.yml` de `ms-ventas`. Es el mismo criterio de `add-fila-virtual`: en Windows, conectarse a un puerto cerrado tarda cerca de 1 s, y sin la propiedad cada compra de las pruebas existentes sumaría ese tiempo y una advertencia. Las pruebas nuevas la activan y apuntan a un `HttpServer` propio.
- **Por qué sincrónico y best-effort.** Es el mismo patrón que la notificación, que ya está probado. El costo está acotado por los timeouts (1,5 s en el peor caso) y no hace falta una cola. Si el aviso se pierde, la consecuencia es menor: esa compra no cuenta para las recomendaciones.

### D9. Gateway

En `api-gateway/src/main/resources/application.yml` se agregan:

- La ruta `recomendaciones`: `uri: ${MS_RECOMENDACIONES_URL:http://localhost:8087}`, `order: 1`, `Path=/api/preferencias,/api/preferencias/**,/api/recomendaciones,/api/recomendaciones/**`, `StripPrefix=1` y `CircuitBreaker` con `name: recomendaciones` y `fallbackUri: forward:/fallback/recomendaciones`.
- La instancia `recomendaciones` del circuit breaker y del time limiter, con los mismos valores que las demás.

`JwtAuthFilter` y `FallbackController` no cambian. En `ApiGatewayApplicationTests` la cuenta pasa de 6 a 7 y se agrega `recomendaciones` a la lista: es un cambio intencional y la prueba sigue exigiendo el conjunto exacto. Las pruebas de la ruta van en clases nuevas.

### D10. Simplificaciones frente a un sistema real

| Sistema real | Esta versión | Por qué alcanza |
|---|---|---|
| Modelos de ML (factorización, embeddings) | Puntaje lineal con pesos fijos y co-compras contadas en Mongo | El enunciado pide coincidencia con preferencias y "otros clientes con compras similares"; además es explicable con `motivos` |
| Eventos de dominio por un broker (Kafka) | Llamada HTTP best-effort desde `ms-ventas` | Reutiliza un patrón probado; perder un aviso solo empobrece un poco las recomendaciones |
| Backfill del historial de ventas | Solo cuentan las compras confirmadas desde que se despliega el cambio | Evita acoplar el servicio a la base de `ms-ventas` |
| Devoluciones y cancelaciones descuentan el historial | No se descuentan | El evento cancelado ya no está `PUBLICADO`, así que no se recomienda igual |
| Popularidad con decaimiento temporal | Conteo histórico de compradores distintos | Alcanza para el volumen del TPE |
| Catálogo sincronizado por eventos | Catálogo completo en memoria con TTL de 30 s y tope de 2000 eventos | Escala suficiente; el fallback cubre las caídas |
| Resultados precalculados por cliente | Cálculo en cada pedido | Pocos datos: la agregación es instantánea con los índices |

## Risks / Trade-offs

- [Con más de 2000 eventos publicados, el catálogo queda incompleto] → Tope explícito en la configuración, con una advertencia en el log si se alcanza.
- [Si `ms-recomendaciones` está caído al confirmar una compra, esa compra no se registra nunca] → Se acepta (D8 y D10). La advertencia `RECOMENDACIONES_NO_DISPONIBLE` permite reconstruir los avisos perdidos a mano a partir del `compraId`.
- [Cada pedido de recomendaciones puede consultar a `ms-eventos` cuando vence la copia] → El TTL de 30 s limita las consultas a una cada 30 s en régimen normal.
- [Las co-compras favorecen a los eventos con muchos compradores] → El peso 2 está normalizado al máximo, y las preferencias explícitas (de 3 a 8 puntos) siguen dominando.
- [`ticketya.recomendaciones.avisar=false` en el perfil de prueba de `ms-ventas` podría ocultar una regresión] → Las pruebas nuevas activan el aviso y cubren cada escenario.

## Migration Plan

1. Desplegar `ms-recomendaciones`. Arranca con las colecciones vacías y los índices se crean al iniciar.
2. Desplegar `ms-ventas` con `MS_RECOMENDACIONES_URL`. Desde ese momento se registran las compras nuevas.
3. Desplegar el gateway con la ruta nueva.
4. **Rollback:** quitar la ruta del gateway y poner `ticketya.recomendaciones.avisar=false` en `ms-ventas`. No hay migraciones de base que revertir en los servicios existentes.
