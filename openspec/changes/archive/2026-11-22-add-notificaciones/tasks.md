# Tasks

Cada escenario de `specs/notificaciones/spec.md` tiene su prueba, indicada entre paréntesis con el nombre del escenario. Las pruebas que usan MongoDB corren contra un `MongoDBContainer("mongo:7")` de Testcontainers 1.21.4; nunca Mongo embebido ni mocks de la base. Las esperas asíncronas se verifican con Awaitility.

## 1. Estructura del servicio

- [x] 1.1 Crear `ms-notificaciones/` con `pom.xml` (parent Boot 3.5.7, Java 21, `spring-boot-starter-web`, `-data-mongodb`, `-validation`, springdoc 2.8.13, Lombok; en test `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter` y `org.testcontainers:mongodb`, con `testcontainers.version` 1.21.4 y el mismo comentario que en `ms-reservas`), `MsNotificacionesApplication` con `@EnableScheduling` y los paquetes `controller`, `service`, `repository`, `model`, `dto`, `exception` y `config` bajo `com.ticketya.notificaciones`. Verificar con `mvn -q compile`.
- [x] 1.2 Crear `application.yml` con `server.port: 8085`, `spring.data.mongodb.uri: ${MONGO_URI:mongodb://ticketya:ticketya@localhost:27017/db_notificaciones?authSource=admin}`, springdoc como en los otros servicios, el patrón de log con `%X{correlationId}` y las propiedades `ticketya.notificaciones.procesador.intervalo` (1s), `.reserva` (30s), `.reintentos.max-intentos` (3), `.reintentos.espera-inicial` (2s), `.reintentos.factor` (2), `.simulacion.emails-que-fallan` (`falla@ticketya.test`) y `.simulacion.emails-que-fallan-una-vez` (`falla-una-vez@ticketya.test`), en una clase `@ConfigurationProperties`. Crear `src/test/resources/application-test.yml` con el intervalo en 100 ms y la espera inicial en 200 ms. Verificar con `mvn -q compile`.
- [x] 1.3 Crear `TestcontainersConfiguration` (`MongoDBContainer` `mongo:7` con `@ServiceConnection`) y una prueba de arranque del contexto que la importe. Verificar con `mvn test` (requiere Docker).

## 2. Modelo, persistencia e índices

- [x] 2.1 Crear los enums `TipoNotificacion` (4 valores) y `EstadoNotificacion` (`PENDIENTE`, `ENVIADA`, `FALLIDA`), el documento `Notificacion` con el subdocumento `Destinatario` y `datos` como `Map<String,Object>` (campos de design.md D2), `NotificacionRepository` y `ClockConfig`. Verificar con una prueba de repositorio que guarde y lea una notificación con `datos` anidados y la estructura quede igual.
- [x] 2.2 Crear al arrancar, con `MongoTemplate.indexOps`, el índice único parcial sobre `claveDeduplicacion` y los índices compuestos `{estado, proximoIntentoEn}` y `{destinatario.usuarioId, estado, creadaEn desc}`. Verificar con una prueba que liste los índices de la colección y compruebe que el de la clave es único y parcial, y que dos documentos sin clave se insertan sin error.
- [x] 2.3 Crear `ErrorResponse`, las excepciones (`BadRequestException`, `ForbiddenException`, `NotFoundException`) y `GlobalExceptionHandler`, iguales a los de `ms-ventas`, incluyendo el mapeo de errores de `@Valid` y de conversión de enums a `400 VALIDACION`. Se verifican con las pruebas del grupo 3.

## 3. Recepción de pedidos (`POST /internal/notificaciones`)

- [x] 3.1 Crear `NotificacionRequest` (`tipo`, `destinatario.usuarioId`, `destinatario.email` con `@Email`, `datos` opcional, `claveDeduplicacion` opcional), `NotificacionAceptadaResponse` (`id`, `estado`), `NotificacionService.recibir` y `InternoNotificacionController`. El controlador lee `X-Correlation-Id` o genera un UUID y responde `202`.
- [x] 3.2 Pruebas de controlador e integración con Mongo de "Recepción de pedidos de notificación": (Pedido válido) y (La respuesta no espera al envío). La segunda usa un `CanalEnvio` de prueba que se bloquea con un latch y comprueba que el `202` llega igual.
- [x] 3.3 Pruebas de "Tipos de notificación admitidos": (Cada tipo admitido se acepta), parametrizada con los 4 tipos; (Datos con forma distinta según el tipo), que relee el documento y compara los `datos` anidados; y (Pedido sin datos).
- [x] 3.4 Pruebas de "Validación del pedido": (Tipo desconocido), (Tipo ausente), (Destinatario sin usuario) y (Email inválido o ausente). Cada una verifica `400`, código `VALIDACION` y que la colección sigue vacía.
- [x] 3.5 Pruebas de "Identificador de correlación de la notificación": (Pedido con correlationId), que comprueba el documento y el log capturado con `OutputCaptureExtension` después del envío; y (Pedido sin correlationId).

## 4. Deduplicación

- [x] 4.1 Implementar la inserción directa con captura de `DuplicateKeyException` sobre `claveDeduplicacion`, que busca la notificación existente y devuelve su `id` y estado (design.md D5).
- [x] 4.2 Pruebas de "Deduplicación por clave": (Pedido repetido con la misma clave), que comprueba un solo documento y un solo envío en el log; (Pedidos sin clave); y (Claves distintas).
- [x] 4.3 Prueba de concurrencia (Pedidos simultáneos con la misma clave): 20 hilos con `CountDownLatch` envían `compra-77`. Todos reciben `202` con el mismo `id` y queda exactamente un documento.

## 5. Envío simulado, procesador y reintentos

- [x] 5.1 Implementar `CanalEnvio` y `EmailSimulado` (log INFO con destinatario, tipo, datos y `correlationId`, y fallas según las dos listas configurables, usando el número de intento; design.md D7).
- [x] 5.2 Pruebas unitarias de "Fallas simuladas configurables": (Destinatario que siempre falla) y (Destinatario que falla una sola vez), contra `EmailSimulado` con la configuración por defecto.
- [x] 5.3 Implementar `ProcesadorNotificaciones` con `@Scheduled`: toma de forma atómica con `findAndModify` y reserva (D3), pone el `correlationId` en el MDC, envía y actualiza el estado, los intentos, `ultimoError`, `proximoIntentoEn` con espera exponencial (D4) y `enviadaEn`.
- [x] 5.4 Prueba de "Envío simulado por email": (Envío exitoso al primer intento). Con Awaitility se espera `ENVIADA`, 1 intento y `enviadaEn`, y se comprueba la línea de log.
- [x] 5.5 Pruebas de "Reintentos con espera creciente": (Éxito en el segundo intento) de punta a punta con `falla-una-vez@ticketya.test`, y (La espera crece entre reintentos) con un `Clock` controlado, que verifica que `proximoIntentoEn` se corre 2 s y después 4 s con la configuración por defecto.
- [x] 5.6 Prueba de "Notificación fallida al agotar los intentos": (Intentos agotados). Se espera `FALLIDA` con 3 intentos y `ultimoError`, y después de varios ciclos del procesador los intentos siguen en 3.
- [x] 5.7 Prueba de "Aislamiento de fallas del envío": (Pedido para un destinatario que falla). La respuesta del `POST` es `202`, con el mismo cuerpo que para un destinatario normal.
- [x] 5.8 Prueba de "Envíos pendientes tras un reinicio": (Pendiente guardada antes de arrancar). Se inserta un documento `PENDIENTE` con `proximoIntentoEn` en el pasado directamente en la base y se espera `ENVIADA`.
- [x] 5.9 Prueba de la toma atómica: dos llamadas simultáneas al método de toma sobre una única notificación pendiente devuelven el documento a una sola de ellas.

## 6. Consulta administrativa (`GET /notificaciones`)

- [x] 6.1 Crear `Identidad` (solo `admin` y `validarPaginacion`, igual que en `ms-ventas`), `NotificacionResponse`, `PageNotificacionResponse`, la búsqueda con `MongoTemplate` y `Criteria` según los filtros presentes, ordenada por `creadaEn` descendente, y `NotificacionController` con `GET /notificaciones` y `GET /notificaciones/{id}`.
- [x] 6.2 Pruebas de "Listado de notificaciones para administradores": (Listado sin filtros), (Filtros combinados), (Paginación) con 25 documentos, y (Parámetros inválidos), parametrizada con los cinco casos.
- [x] 6.3 Pruebas de "Detalle de una notificación para administradores": (Notificación existente), que verifica todos los campos, y (Notificación inexistente), que verifica `404` con el error estándar, incluido un id que no es un `ObjectId` válido.
- [x] 6.4 Pruebas de "Autorización de la consulta": (Rol distinto de ADMIN), parametrizada con `CLIENTE`, `ORGANIZADOR` y `PERSONAL_ACCESO` sobre los dos endpoints, y (Headers de identidad ausentes).

## 7. Contrato y documentación

- [x] 7.1 Agregar a `docs/openapi.yaml` `POST /internal/notificaciones` (tag `Internos`, servidor `http://localhost:8085`, header `X-Correlation-Id` opcional, respuestas `202`, `400` y `500`), `GET /notificaciones` y `GET /notificaciones/{id}` (tag nuevo `Notificaciones`, servidor 8085 y la aclaración de que la ruta del gateway está pendiente), y los schemas `NotificacionRequest`, `NotificacionAceptada`, `Notificacion` y `PageNotificacion`. Verificar que el YAML carga sin errores, por ejemplo con `npx @redocly/cli lint docs/openapi.yaml` o abriéndolo en Swagger Editor, y que los nombres de los campos coinciden con los DTO.
- [x] 7.2 Actualizar el `README.md` (tabla de arquitectura con `ms-notificaciones` 8085 y `db_notificaciones` en MongoDB 27017, comandos de solo bases y de `mvn test`, variable `MONGO_URI`, Swagger 8085, emails de prueba que fallan y la estructura del repositorio) y `docs/arquitectura.md` (el servicio, la elección de MongoDB y el flujo asíncrono con reintentos). Verificar que los comandos documentados funcionan tal como están escritos.
- [x] 7.3 Agregar a la carpeta de internos de `postman/TicketYa.postman_collection.json` los requests directos a 8085: crear una notificación, crear una repetida con la misma clave, crear una para `falla@ticketya.test`, listar como `ADMIN` y ver el detalle. Verificar que la colección sigue siendo JSON válido y que la carpeta "Flujo completo (en orden)" no cambió.

## 8. Contenedores

- [x] 8.1 Crear `ms-notificaciones/Dockerfile` (igual al de `ms-ventas`, con `EXPOSE 8085`) y `.dockerignore`. Verificar con `docker build -t ticketya/ms-notificaciones ms-notificaciones`.
- [x] 8.2 Agregar al `docker-compose.yml` el servicio `mongo` (`mongo:7`, `ticketya-mongo`, usuario root `ticketya`, `27017:27017`, volumen `mongo-data:/data/db`, healthcheck con `mongosh` ping), el servicio `ms-notificaciones` (build, `MONGO_URI` con host `mongo`, `8085:8085`, `depends_on` `mongo` `service_healthy`) y el volumen `mongo-data`. Actualizar el comentario inicial. Verificar con `docker compose config`.
- [x] 8.3 Verificar los escenarios modificados de `despliegue-contenedores`: con `docker compose up -d --build`, `docker compose ps` muestra los 11 contenedores; `ms-notificaciones` arranca después de que `mongo` está healthy; un `POST` a `http://localhost:8085/internal/notificaciones` responde `202` y el documento aparece en `db_notificaciones`; `docker compose up -d db-eventos db-reservas db-ventas db-auth mongo` levanta solo las 5 bases; y el servicio ejecutado desde el IDE sin variables se conecta a `localhost:27017`.

## 9. Verificación integral

- [x] 9.1 Correr `mvn test` en `ms-notificaciones` (todo verde) y en los demás servicios, para confirmar que no cambió nada, y `openspec validate --all --strict`.
- [x] 9.2 Con el sistema en compose, ejecutar la carpeta "Flujo completo (en orden)" de Postman contra el gateway y comprobar que sigue pasando sus 15 pruebas.

## Pendientes de otros equipos (no se implementan en este cambio)

Estos pedidos se hacen a los responsables de cada servicio después de archivar el cambio. No son tareas de este cambio. Contrato común:

```
POST http://ms-notificaciones:8085/internal/notificaciones      (desde el IDE: http://localhost:8085)
Content-Type: application/json
X-Correlation-Id: <el de la solicitud en curso>                  (opcional)

Respuesta: 202 Accepted  { "id": "6741f0c2a1b2c3d4e5f60718", "estado": "PENDIENTE" }
Errores:   400 VALIDACION (pedido mal formado); 5xx si ms-notificaciones o MongoDB no están disponibles.
```

Recomendación para el que llama: llamar después de confirmar la propia transacción, con timeouts de 1 s para conectar y 2 s para leer, capturar cualquier error, registrarlo y seguir sin propagarlo. Mandar siempre `claveDeduplicacion` para poder reintentar sin duplicar.

- **ms-ventas (US-26, compra confirmada).** Al emitir las entradas de una compra aprobada:
  ```json
  {
    "tipo": "COMPRA_CONFIRMADA",
    "destinatario": { "usuarioId": "101", "email": "cliente@example.com" },
    "datos": { "compraId": 55, "eventoId": 42, "total": 30000, "entradas": [ { "entradaId": 901, "sector": "Platea", "asiento": "F12" } ] },
    "claveDeduplicacion": "compra-55"
  }
  ```
- **ms-eventos (US-27, evento cancelado).** Un pedido por comprador del evento (la lista de compradores la aporta `ms-ventas`):
  ```json
  {
    "tipo": "EVENTO_CANCELADO",
    "destinatario": { "usuarioId": "101", "email": "cliente@example.com" },
    "datos": { "eventoId": 42, "nombre": "Recital X", "motivo": "Suspensión por clima" },
    "claveDeduplicacion": "evento-42-cancelado-usuario-101"
  }
  ```
- **ms-eventos (US-27, evento modificado).** Un pedido por comprador cuando cambian la fecha, el lugar u otro dato relevante:
  ```json
  {
    "tipo": "EVENTO_MODIFICADO",
    "destinatario": { "usuarioId": "101", "email": "cliente@example.com" },
    "datos": { "eventoId": 42, "nombre": "Recital X", "cambios": { "fecha": { "antes": "2026-12-01T21:00", "despues": "2026-12-08T21:00" } } },
    "claveDeduplicacion": "evento-42-modificado-<version o timestamp del cambio>-usuario-101"
  }
  ```
- **ms-eventos (US-28, artista favorito en la ciudad).** Al publicar un evento, un pedido por cliente que tenga ese artista como favorito y esa ciudad en sus preferencias (depende de la funcionalidad de preferencias, US-24):
  ```json
  {
    "tipo": "ARTISTA_FAVORITO_PUBLICO_EVENTO",
    "destinatario": { "usuarioId": "101", "email": "cliente@example.com" },
    "datos": { "eventoId": 42, "artista": "Artista Y", "ciudad": "Tandil", "fecha": "2026-12-08T21:00" },
    "claveDeduplicacion": "evento-42-publicado-usuario-101"
  }
  ```
- **api-gateway.** Agregar la ruta `/api/notificaciones/**` → `http://ms-notificaciones:8085/notificaciones/**` (variable `MS_NOTIFICACIONES_URL`, por defecto `http://localhost:8085`), protegida con JWT y propagando `X-User-Id`, `X-User-Rol` y `X-Correlation-Id`. No exponer `/internal/notificaciones`. Agregar `ms-notificaciones` al `depends_on` del gateway en el compose.
- **ms-auth / datos del destinatario.** Los servicios que llaman necesitan el email del cliente. Si hoy no lo tienen, acordar con `ms-auth` cómo obtenerlo (un claim del JWT o un endpoint interno).

## Workflow follow-up

- Archivar el cambio con `openspec archive add-notificaciones` cuando la implementación y las verificaciones estén completas.
- Validar las especificaciones archivadas con `openspec validate --all --strict`.
- Enviar los pendientes de arriba a los responsables de cada servicio.
