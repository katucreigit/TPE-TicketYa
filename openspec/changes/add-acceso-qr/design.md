# Design

## Context

Ver `proposal.md` para la motivación y el alcance. En `ms-ventas`:

- `Entrada` ya define `codigo_qr` (`VARCHAR(36)`, `UNIQUE`), `evento_id`, `estado` (`EstadoEntrada` ya incluye `USADA`) y `evento_fecha_hora`. No tiene `@Version` ni columnas de auditoría de uso (`usada_en`, `validada_por`).
- `Identidad` valida `X-User-Id` y `X-User-Rol` con los roles `CLIENTE` y `ADMIN`. En `ms-auth` (`Rol.java`) y `api-gateway` (`JwtAuthFilter`), el cuarto rol ya implementado y propagado es `PERSONAL_ACCESO`.
- `api-gateway` ya enruta `/api/entradas/**` a `ms-ventas` con `StripPrefix=1`, por lo que `POST /api/entradas/validar` llega directamente a `POST /entradas/validar` en `ms-ventas` con los headers confiables `X-User-Id` y `X-User-Rol`.
- `DevolucionService.solicitar` y `CancelacionEventoService.marcarPendientes` hoy usan `SELECT ... FOR UPDATE` (`bloquearConCompra` / `bloquearPorEventoYEstado`) seguido de `setEstado(...)` + `saveAndFlush(...)`.
- `ms-ventas/pom.xml` todavía no incluye Testcontainers; `ms-reservas/pom.xml` sí lo usa con `testcontainers.version = 1.21.4` y `postgres:16-alpine`.

## Goals / Non-Goals

**Goals:**
- Garantizar exclusión mutua a nivel de base de datos entre escaneos simultáneos del mismo QR y entre ingreso, devolución y cancelación de evento.
- Mantener la validación del endpoint coherente con el resto de los controladores de `ms-ventas` (`@Valid @RequestBody` + `Identidad`).
- Mantener el ingreso completamente local a `ms-ventas` (sin llamadas HTTP a `ms-eventos` ni `ms-reservas`).
- Probar la concurrencia real contra PostgreSQL 16 con Testcontainers.

**Non-Goals:**
- Log centralizado o auditoría distribuida de accesos (pertenece a `add-trazabilidad` / TS-02).
- Generación de imagen PNG/SVG del código QR o validación offline.
- Sincronización de eventos `SUSPENDIDO` o `FINALIZADO` desde `ms-eventos` hacia `ms-ventas`.

## Decisions

### 1. Rol `PERSONAL_ACCESO` y alineación documental
Se adopta `PERSONAL_ACCESO` en `Identidad.personalAcceso(userId, rol)` y se actualiza `docs/openapi.yaml` y `docs/user-stories.md` (que todavía mencionaban `ACCESO`) para que coincidan con `ms-auth`, `api-gateway`, `docs/arquitectura.md` y la colección de Postman.
- *Alternativa considerada:* usar `ACCESO` en `ms-ventas`. Descartada porque `JwtAuthFilter` solo acepta `PERSONAL_ACCESO` y cualquier request real vía gateway recibiría `403`.

### 2. Validación estándar con `@Valid @RequestBody` en `EntradaController.validar`
- **Decisión:** validación estándar con `@Valid @RequestBody`, igual que el resto de los controladores de `ms-ventas`; el orden resultante es identidad, luego body (`400`) y luego rol (`403`).
- **Justificación:** se prioriza la simplicidad técnica y la coherencia con las convenciones del servicio por sobre un código de error ligeramente más prolijo. La diferencia solo se observa cuando un usuario sin rol `PERSONAL_ACCESO` envía además un body inválido, y no expone información sensible ni afecta la garantía de no doble ingreso.
- **Alternativa descartada:** deserializar y validar manualmente después de `Identidad` (`ObjectMapper` + `Validator` o interceptor), por agregar código distinto al del resto del servicio, más superficie de pruebas y mantenimiento, sin beneficio funcional.
- **Trade-off aceptado:** un usuario sin permisos que envía un body inválido recibe `400` en lugar de `403`.

### 3. Columnas de auditoría en `Entrada` y esquema `EntradaResponse`
Se agregan a `Entrada`:
- `usada_en` (`LocalDateTime`, nullable)
- `validada_por` (`String`, nullable)

No se agrega `@Version` porque `Entrada` no usa bloqueo optimista actualmente; toda la exclusión mutua se resuelve mediante `UPDATE` condicional por estado en PostgreSQL. Si en el futuro se incorporara `@Version`, las sentencias `@Modifying` deberán incrementar `version = version + 1`.

En `EntradaResponse` se agregan `usadaEn` y `validadaPor` con `@JsonInclude(JsonInclude.Include.NON_NULL)` para que:
- En `POST /entradas/validar` (`200`), la respuesta exponga todos los datos de la entrada marcada `USADA` incluyendo `usadaEn` y `validadaPor`.
- En consultas de entradas aún no usadas (`GET /entradas`, `GET /compras`), esos campos nulos se omitan y no rompan clientes ni pruebas existentes.

`usadaEn` se genera con `LocalDateTime.now(clock)` (reloj UTC inyectado vía `ClockConfig`, coherente con `hibernate.jdbc.time_zone: UTC` y `DevolucionService`).

### 4. `UPDATE` atómico para `EMITIDA -> USADA` y diagnóstico con 0 filas
En `EntradaRepository`:
```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
        UPDATE Entrada e
           SET e.estado = com.ticketya.ventas.model.enums.EstadoEntrada.USADA,
               e.usadaEn = :usadaEn,
               e.validadaPor = :validadaPor
         WHERE e.codigoQr = :codigoQr
           AND e.eventoId = :eventoId
           AND e.estado = com.ticketya.ventas.model.enums.EstadoEntrada.EMITIDA
        """)
int marcarUsadaSiEmitida(@Param("codigoQr") String codigoQr,
                         @Param("eventoId") Long eventoId,
                         @Param("usadaEn") LocalDateTime usadaEn,
                         @Param("validadaPor") String validadaPor);
```

Flujo en `EntradaService.validar(String codigoQr, Long eventoId, String operadorId)` dentro de una transacción (`TransactionTemplate` o `@Transactional`):
1. Normalizar `codigoQr = codigoQr.trim()`.
2. Ejecutar `marcarUsadaSiEmitida(codigoQr, eventoId, ahora, operadorId)`. Como `codigo_qr` es `VARCHAR(36)` (no columna `uuid` de PostgreSQL), pasar un string que no sea UUID nunca produce error de casteo ni `500`: el `UPDATE` devuelve `0` y la consulta posterior `findByCodigoQr(codigoQr)` devuelve `Optional.empty()` -> `404 QR_INEXISTENTE`. (Adicionalmente, si `codigoQr.length() > 36`, directamente no puede existir y puede devolverse `404 QR_INEXISTENTE` o dejar que el `WHERE` no coincida; comprobar `findByCodigoQr` sin lanzar error por longitud asegura `404` siempre).
3. Si `actualizadas == 1`: leer la entrada actualizada (`findByCodigoQr(codigoQr).orElseThrow()`) y devolver `EntradaResponse.from(entrada)`.
4. Si `actualizadas == 0`: consultar `findByCodigoQr(codigoQr)` (solo por `codigo_qr`) y evaluar en orden:
   - Ausente -> `NotFoundException("QR_INEXISTENTE", ...)` (`404`).
   - `!entrada.getEventoId().equals(eventoId)` -> `BusinessRuleException("EVENTO_INCORRECTO", ...)` (`422`). Se evalúa antes del estado para no revelar información de uso/devolución de otro evento.
   - `entrada.getEstado() == EstadoEntrada.USADA` -> `ConflictException("ENTRADA_USADA", "... " + entrada.getUsadaEn())` (`409`).
   - Cualquier otro estado (`DEVOLUCION_SOLICITADA`, `REINTEGRO_PENDIENTE`, `REINTEGRADA`) -> `ConflictException("ENTRADA_NO_VALIDA", "... " + entrada.getEstado())` (`409`).
   - Nota de concurrencia: en PostgreSQL con `READ COMMITTED` (aislamiento por defecto), si dos transacciones ejecutan el `UPDATE` en simultáneo, la segunda queda bloqueada sobre la fila hasta que la primera hace commit; al reevaluar el `WHERE`, ve `estado = 'USADA'`, devuelve `0`, y el `SELECT` inmediato en la misma transacción ya lee el valor comprometido (`USADA` + `usadaEn`) y devuelve `409 ENTRADA_USADA`.

### 5. Transiciones condicionales en `DevolucionService` y `CancelacionEventoService`
Para que todas las salidas desde `EMITIDA` compitan con el mismo mecanismo atómico en PostgreSQL:
- Agregar en `EntradaRepository`:
  ```java
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
          UPDATE Entrada e
             SET e.estado = :nuevoEstado
           WHERE e.id = :id
             AND e.estado = com.ticketya.ventas.model.enums.EstadoEntrada.EMITIDA
          """)
  int transicionarDesdeEmitida(@Param("id") UUID id, @Param("nuevoEstado") EstadoEntrada nuevoEstado);
  ```
- **`DevolucionService.solicitar`**:
  1. Leer la entrada con su compra (`findWithCompraById`, sin `FOR UPDATE`, evitando deadlocks con accesos por `codigo_qr`).
  2. Validar existencia (`404`), titularidad (`403`), estado devolvible (`409 ENTRADA_NO_DEVOLVIBLE`) y plazo de 72 horas (`422 FUERA_DE_PLAZO`).
  3. Ejecutar `entradaRepository.transicionarDesdeEmitida(entrada.getId(), EstadoEntrada.DEVOLUCION_SOLICITADA)`.
  4. Si retorna `0` (una validación de ingreso, cancelación u otra devolución ganó la carrera entre la lectura y el `UPDATE`), releer la entrada actualizada y volver a invocar `validarDevolvible(actualizada)` (o lanzar `ConflictException("ENTRADA_NO_DEVOLVIBLE", ...)`), abortando la creación de la `Devolucion`.
  5. Si retorna `1`, persistir la `Devolucion` usando `entradaRepository.findById(entrada.getId()).orElseThrow()` (ya en `DEVOLUCION_SOLICITADA` tras `clearAutomatically = true`).
- **`CancelacionEventoService.marcarPendientes`**:
  1. Consultar los IDs/entradas candidatas del evento en estado `EMITIDA` ordenadas por `id`.
  2. Para cada entrada, ejecutar `entradaRepository.transicionarDesdeEmitida(entrada.getId(), EstadoEntrada.REINTEGRO_PENDIENTE)`.
  3. Solo si retorna `1`, crear (si no existe) el `Reintegro` de tipo `CANCELACION_EVENTO`. Si retorna `0` (por ejemplo, la entrada pasó a `USADA` o `DEVOLUCION_SOLICITADA` en simultáneo), se omite sin error ni reintegro.
  4. *Impacto en tests unitarios existentes*: actualizar `DevolucionServiceTest` y `CancelacionEventoServiceTest` para stubbear `transicionarDesdeEmitida` (y `findWithCompraById` / consulta de candidatas) manteniendo intactas las aserciones de negocio.

### 6. Comportamiento ante eventos `SUSPENDIDO` o `FINALIZADO` (sin consulta a `ms-eventos`)
`POST /entradas/validar` no consulta a `ms-eventos` para mantener baja latencia y alta disponibilidad en puerta (si `ms-eventos` está caído o lento, el ingreso no se detiene). Consecuencias documentadas:
- **Evento `CANCELADO`**: al cancelarse, `ms-eventos` invoca `POST /internal/eventos/{eventoId}/cancelacion` en `ms-ventas`, pasando las entradas `EMITIDA` a `REINTEGRO_PENDIENTE` / `REINTEGRADA`. El ingreso las rechaza localmente con `409 ENTRADA_NO_VALIDA`.
- **Evento `SUSPENDIDO`**: `ms-eventos` solo cierra el inventario en `ms-reservas` (para frenar nuevas reservas), sin notificar a `ms-ventas` ni alterar entradas ya emitidas porque la suspensión es reversible. Por lo tanto, una entrada `EMITIDA` de un evento `SUSPENDIDO` seguiría siendo válida en `ms-ventas` si alguien la escaneara; la restricción es operativa (no se habilitan puertas en un evento suspendido).
- **Evento `FINALIZADO`**: el job de finalización de `ms-eventos` no modifica las entradas no usadas en `ms-ventas`, y `POST /entradas/validar` no bloquea por `entrada.eventoFechaHora` porque el ingreso real puede continuar después de la hora programada de inicio. En consecuencia, una entrada `EMITIDA` de un evento `FINALIZADO` también sería aceptada técnicamente si se escaneara posteriormente; queda bajo control operativo de cierre de puertas (una ventana de tolerancia basada en `eventoFechaHora` queda fuera de alcance).

### 7. Pruebas con Testcontainers en `ms-ventas`
Se incorporan en `ms-ventas/pom.xml` las mismas dependencias de test que ya usa `ms-reservas` (`testcontainers.version = 1.21.4`, `spring-boot-testcontainers`, `testcontainers:junit-jupiter`, `testcontainers:postgresql`) y se crea `TestcontainersConfiguration` con `postgres:16-alpine`.
- `EntradaRepositoryTest`: valida sobre PostgreSQL real `marcarUsadaSiEmitida` (éxito + 0 filas por QR inexistente, QR no UUID, evento incorrecto, `USADA`, `DEVOLUCION_SOLICITADA`, `REINTEGRO_PENDIENTE`, `REINTEGRADA`) y `transicionarDesdeEmitida`.
- `ConcurrenciaAccesoQrTest`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` con `HttpClient` real y `CountDownLatch` sobre PostgreSQL real:
  1. N hilos escaneando el mismo QR -> exactamente 1 HTTP `200` y N-1 HTTP `409` con `code = "ENTRADA_USADA"`, 0 errores `500`.
  2. Escaneo (`POST /entradas/validar`) y devolución (`POST /devoluciones`) simultáneos (repetido varias veces) -> exactamente una operación gana y la otra recibe `409` (`ENTRADA_NO_DEVOLVIBLE` o `ENTRADA_NO_VALIDA`), verificando consistencia final en DB.

## Risks / Trade-offs

- **Escaneo en el ingreso de entradas `EMITIDA` pertenecientes a eventos `SUSPENDIDO` o `FINALIZADO`** → Aceptado deliberadamente para evitar acoplamiento síncrono con `ms-eventos` en la puerta (ver Decisión 6); no afecta la emisión de entradas sino únicamente la validación al escanear el QR. Si más adelante se requiere bloqueo automático en el ingreso, se puede agregar una notificación interna de estado de evento o una ventana de tolerancia sobre `eventoFechaHora`.
- **`clearAutomatically = true` en `@Modifying`** → Limpia el persistence context tras el `UPDATE`; tanto `EntradaService`, `DevolucionService` como `CancelacionEventoService` deben volver a obtener referencias gestionadas (`findById` / `findByCodigoQr`) si necesitan persistir relaciones (`Devolucion`, `Reintegro`) o devolver datos actualizados después del `UPDATE`.

