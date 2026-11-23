# Tasks

Cada escenario de `specs/` tiene su prueba, indicada entre paréntesis con el nombre del escenario. Las pruebas que usan MongoDB corren contra `MongoDBContainer("mongo:7")` de Testcontainers 1.21.4, nunca contra Mongo embebido ni mocks de la base. `ms-eventos` se simula con un `com.sun.net.httpserver.HttpServer`. No se modifican pruebas existentes, salvo la que cuenta las rutas del gateway. No se hace commit ni push.

## 1. Estructura de `ms-recomendaciones`

- [x] 1.1 Crear `ms-recomendaciones/` con `pom.xml` copiado de `ms-notificaciones` (artifact `ms-recomendaciones`, mismas dependencias), `MsRecomendacionesApplication` con `@ConfigurationPropertiesScan`, `ClockConfig` y los paquetes `controller`, `service`, `repository`, `model`, `dto`, `exception`, `client`, `config` y `trazabilidad` bajo `com.ticketya.recomendaciones`. Verificar con `mvn -q compile`.
- [x] 1.2 Crear `application.yml` con `server.port: 8087`, `spring.data.mongodb.uri: ${MONGO_URI:mongodb://ticketya:ticketya@localhost:27017/db_recomendaciones?authSource=admin}`, `auto-index-creation: false`, springdoc, el patrón de log con `%X{correlationId:-}`, `ticketya.services.eventos-url: ${MS_EVENTOS_URL:http://localhost:8081}` con sus timeouts (1000 y 2000 ms) y `ticketya.catalogo.ttl: 30s` y `max-paginas: 20`, en una clase `@ConfigurationProperties`. Crear `src/test/resources/application-test.yml`. Verificar con `mvn -q compile`.
- [x] 1.3 Crear `TestcontainersConfiguration` (`MongoDBContainer` `mongo:7` con `@ServiceConnection`), un reloj controlable y una prueba de arranque del contexto que haga `ping` a Mongo. Verificar con `mvn test` (requiere Docker).
- [x] 1.4 Crear `Dockerfile` (dos etapas, `EXPOSE 8087`) y `.dockerignore`. Se verifica con el `docker compose up --build` de 7.1.

## 2. Errores, identidad y trazabilidad

- [x] 2.1 Copiar de `ms-fila` `ErrorResponse`, `ApiException` y `GlobalExceptionHandler` (con `InvalidFormatException` a `400 VALIDACION` y `DataAccessResourceFailureException` a `503 SERVICIO_NO_DISPONIBLE`), y crear `Identidad` con `cliente(...)` y `autenticado(...)` (design.md D6 y D7).
- [x] 2.2 Copiar de `ms-fila` el paquete `trazabilidad/` (`Correlacion`, `CorrelationIdFilter`, `RegistroSolicitudesFilter`) y de `ms-reservas` `PropagacionCorrelacionConfig`, ajustando el paquete. Se verifica con la prueba de 4.6.

## 3. Preferencias y registro de compras

- [x] 3.1 Crear el documento `Preferencias` (`_id` clienteId) y su repositorio, `PreferenciasRequest` y `PreferenciasResponse` validados (D6), la normalización de texto `Texto.normalizar` y la deduplicación al guardar (D2). Crear `PreferenciasService` y `PreferenciasController` (`GET` y `PUT /preferencias`).
- [x] 3.2 Prueba unitaria de `Texto.normalizar` con mayúsculas, tildes y espacios. Pruebas de integración de "Guardar preferencias": (Guardar preferencias completas), (Reemplazar preferencias anteriores) y (Valores repetidos); y de "Consultar preferencias": (Cliente con preferencias) y (Cliente sin preferencias).
- [x] 3.3 Pruebas de "Validación de preferencias": (Tipo de evento inexistente), (Artista vacío), (Demasiados artistas) y (Cuerpo mal formado), verificando que lo guardado no cambia. Pruebas de "Identidad y roles": (Organizador consulta recomendaciones), (Preferencias sin identidad) y (Organizador consulta también compraron).
- [x] 3.4 Crear el documento `Compra` (`_id` compraId) con los índices `clienteId` y `eventoIds`, creados al arrancar con `MongoTemplate.indexOps`. Crear `CompraRegistradaRequest`, el registro idempotente con captura de `DuplicateKeyException` (D2) e `InternoCompraController` (`POST /internal/compras`, `204`).
- [x] 3.5 Pruebas de "Registro de compras confirmadas": (Compra nueva), (Compra repetida), (Compra sin eventos) y (Compras simultáneas repetidas), esta última con 10 hilos y un `CountDownLatch`. Una prueba adicional lista los índices de la colección.

## 4. Catálogo, puntaje y recomendaciones

- [x] 4.1 Crear `client/EventosClient`, que pagina `GET /eventos?page=&size=100` hasta `totalPages` o `max-paginas`, y `CatalogoEventos` con la copia en memoria, el TTL, el `Clock`, el filtro `PUBLICADO` y el fallback con `degradado` (D5).
- [x] 4.2 Pruebas de "Catálogo con fallback" contra el `HttpServer` simulado y el reloj controlable: (Catálogo disponible); (ms-eventos caído con copia en memoria), que adelanta el reloj más de 30 s y hace que el simulado responda `500` y luego corte la conexión; y (ms-eventos caído sin copia), para los dos endpoints. Además, una prueba de que una página fallida no reemplaza la copia anterior y otra de que dentro del TTL no se vuelve a consultar.
- [x] 4.3 Crear `CalculadoraPuntaje` como función pura (D3). Pruebas unitarias de "Puntaje de recomendación": (Coincidencia con todas las preferencias), (Popularidad relativa) y (Compradores en común). Agregar pruebas del desempate por fecha y por id, del redondeo y de `motivos` vacío cuando el puntaje es 0.
- [x] 4.4 Crear `CompraRepositoryCustom` (quedó como `CompraRepository` con `MongoTemplate`, sin interfaz de Spring Data) con las tres agregaciones de D4. Pruebas de integración con Mongo: compradores distintos por evento (un cliente con dos compras del mismo evento cuenta una sola vez), compradores en común excluyendo al propio cliente, y también compraron.
- [x] 4.5 Crear `RecomendacionService` y `RecomendacionController` (`GET /recomendaciones` y `GET /recomendaciones/eventos/{eventoId}/tambien-compraron`, con `limite` validado). Pruebas de "Recomendaciones personalizadas": (Eventos ordenados por puntaje), (Eventos ya comprados excluidos), (Solo eventos publicados) y (Límite fuera de rango). Pruebas de "Recomendaciones sin preferencias ni historial": (Cliente nuevo con compras de otros) y (Sistema sin compras). Pruebas de "También compraron": (Eventos comprados en común) y (Evento sin compradores).
- [x] 4.6 Prueba de "Identificador de correlación en ms-recomendaciones": (Propagación a ms-eventos), que verifica el header de la respuesta, la línea `HTTP_REQUEST` y el header recibido por el simulado.
- [x] 4.7 Ejecutar `mvn test` en `ms-recomendaciones` con Docker en ejecución y verificar 0 fallos.

## 5. Integración en `ms-ventas`

- [x] 5.1 Crear `client/RecomendacionesClient` best-effort (D8), con las propiedades `ticketya.services.recomendaciones-url`, `recomendaciones-connect-timeout-ms` (500), `recomendaciones-read-timeout-ms` (1000) y `ticketya.recomendaciones.avisar` (`true`), y con `avisar: false` en `src/test/resources/application-test.yml`. Engancharlo en `CompraService.notificarCompraConfirmada` con un setter `@Autowired(required = false)`, sin cambiar los constructores.
- [x] 5.2 Crear la clase de prueba nueva `AvisoRecomendacionesTest`, con PostgreSQL de Testcontainers, `avisar=true` y un `HttpServer` que simula `ms-recomendaciones`. Pruebas de "Aviso de compra desde ms-ventas": (Compra confirmada avisada), verificando el cuerpo, la ruta y el `X-Correlation-Id`; (ms-recomendaciones caído), con un puerto cerrado y la advertencia verificada; y (ms-recomendaciones lento), con una demora de 5 s y la compra `APROBADA` en menos de 2,5 s. Una prueba unitaria de `RecomendacionesClient` verifica que no lanza ante `500`.
- [x] 5.3 Verificar que la reconciliación también avisa. Una prueba nueva, en `AvisoRecomendacionesTest`, deja una compra en `PAGO_INCIERTO` con `tok_timeout` y ejecuta `ReconciliacionPagosJob`. Comprueba que la compra queda `APROBADA` y que el simulado recibe el aviso con ese `compraId`.
- [x] 5.4 Ejecutar `mvn test` en `ms-ventas` y verificar que pasan todas las pruebas existentes sin modificarlas, más las nuevas. Indicar la cantidad en el resumen.

## 6. API Gateway

- [x] 6.1 En `application.yml` agregar la ruta `recomendaciones` y las instancias `recomendaciones` de `circuitbreaker` y `timelimiter` (D9). En `ApiGatewayApplicationTests` cambiar `hasSize(6)` por `hasSize(7)`, agregar `recomendaciones` a la lista exacta y actualizar el comentario.
- [x] 6.2 Crear las clases de prueba nuevas `RecomendacionesRutaGatewayTest` (servidor simulado) y `RecomendacionesNoDisponibleGatewayTest` (puerto sin servicio). Pruebas de "Acceso por el API Gateway": (Recomendaciones a través del gateway), (Preferencias sin token), (ms-recomendaciones no disponible) y (El registro interno no se expone); además, una prueba de que `PUT /api/preferencias` llega con el path sin `/api` y un circuito `recomendaciones` registrado. Ejecutar `mvn test` en `api-gateway` y verificar 0 fallos.

## 7. Despliegue, documentación y verificación

- [x] 7.1 En `docker-compose.yml` agregar `ms-recomendaciones` (`MONGO_URI` a `db_recomendaciones`, `MS_EVENTOS_URL`, puerto `8087:8087`, `depends_on: mongo (service_healthy)`), `MS_RECOMENDACIONES_URL` en `ms-ventas` (sin `depends_on`) y en `api-gateway` (con `depends_on`), y actualizar la cabecera (14 contenedores). Verificar con `docker compose config` y con `docker compose up -d --build`: los 14 contenedores en ejecución y `GET http://localhost:8087/preferencias` con identidad de cliente respondiendo `200`.
- [x] 7.2 Actualizar `docs/openapi.yaml`: los tags `Preferencias` y `Recomendaciones` (ms-recomendaciones, 8087), los 4 endpoints públicos, `POST /internal/compras` con su servidor y los schemas `PreferenciasRequest`, `Preferencias`, `Recomendaciones`, `EventoRecomendado`, `TambienCompraron`, `EventoTambienComprado` y `CompraRegistradaRequest`. Verificar que el YAML parsea y que todas las `$ref` resuelven (script con SnakeYAML).
- [x] 7.3 Actualizar `README.md`: `ms-recomendaciones` en la tabla de componentes, 14 contenedores, `MS_RECOMENDACIONES_URL` en Configuración, Swagger, `cd ms-recomendaciones && mvn test` y una sección breve "Preferencias y recomendaciones". Actualizar `docs/arquitectura.md`: la tabla de servicios (funcionalidad 10), la API pública, las rutas del gateway, las dependencias `ms-ventas → ms-recomendaciones` (best-effort) y `ms-recomendaciones → ms-eventos` (con fallback), el circuito nuevo y una sección "Preferencias y recomendaciones (E2)" con el resumen de D1, D3, D5 y D10.
- [x] 7.4 Con el sistema levantado en Compose, recorrer el flujo por el gateway con tokens reales:
  - Dos clientes compran un mismo evento publicado y uno de ellos compra otro evento.
  - Se verifica que "también compraron" devuelve el segundo evento.
  - Un cliente guarda preferencias y sus recomendaciones las reflejan en el puntaje.
  - Con `ms-eventos` detenido y la copia vencida, `/api/recomendaciones` responde `200` con `degradado: true`.

  Anotar el resultado en este archivo.

  Resultado (2026-11-23, Compose con 14 contenedores, por el gateway con JWT reales). Las imágenes se construyeron de a una (`COMPOSE_PARALLEL_LIMIT=1`), porque un primer intento con las 8 en paralelo llenó el disco `C:` y Docker Desktop se cayó.

  | Paso | Resultado |
  |---|---|
  | E1 (`RECITAL`) y E2 (`TEATRO`) publicados en Olavarría; A compra E1 y E2, B compra E1 | 3 compras `201 APROBADA` |
  | `GET /api/recomendaciones/eventos/E1/tambien-compraron` | `200`, E2 con `compradoresEnComun` 1 |
  | C guarda `{ tipos: [TEATRO], ciudad: Olavarria }` y consulta | E2 con puntaje 5.5 (`TIPO`, `CIUDAD`, `POPULAR`) y E1 con 3.0 (`CIUDAD`, `POPULAR`) |
  | B, sin preferencias, consulta | E2 primero con puntaje 3.0 (`TAMBIEN_COMPRARON`, `POPULAR`), sin E1 porque ya lo compró |
  | `ms-eventos` detenido y copia vencida (32 s), C consulta con `X-Correlation-Id: e2e-rec-…` | `200` con `degradado: true` y los eventos de la última copia; `ms-recomendaciones` registra `CATALOGO_NO_DISPONIBLE copiaEnMemoria=true … correlationId=e2e-rec-…` |
- [x] 7.5 Ejecutar `openspec validate --all --strict` y verificar que no hay errores.

## Workflow follow-up

- Archivar el cambio con `/opsx:archive` cuando la implementación y las verificaciones estén completas.
- El commit y el push quedan a cargo del usuario.
