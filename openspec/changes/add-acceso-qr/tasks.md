# Tasks

## 1. Modelo, repositorio y soporte Testcontainers

- [x] 1.1 Agregar las dependencias de Testcontainers (`spring-boot-testcontainers`, `testcontainers:junit-jupiter`, `testcontainers:postgresql`, versión `1.21.4`) en `ms-ventas/pom.xml` y crear `TestcontainersConfiguration` (`postgres:16-alpine`), verificando que `mvn -f ms-ventas/pom.xml test-compile` compila sin errores.
- [ ] 1.2 Agregar las columnas `usada_en` y `validada_por` en `Entrada`, exponer `usadaEn` y `validadaPor` (omitidos cuando son `null`) en `EntradaResponse`, y crear `ValidarEntradaRequest` (`codigoQr`, `eventoId`), verificando que los tests existentes de `ms-ventas` compilan y pasan.
- [ ] 1.3 Agregar en `EntradaRepository` `findByCodigoQr`, el `UPDATE` atómico `marcarUsadaSiEmitida` (`@Modifying(clearAutomatically = true, flushAutomatically = true)`) y el `UPDATE` condicional `transicionarDesdeEmitida`, y verificar en `EntradaRepositoryTest` (PostgreSQL real vía Testcontainers) tanto el éxito como 0 filas por cada motivo (QR inexistente, formato no UUID, evento incorrecto, `USADA`, `DEVOLUCION_SOLICITADA`, `REINTEGRO_PENDIENTE`, `REINTEGRADA`).

## 2. Validación de QR en servicio y controlador

- [ ] 2.1 Incorporar `Identidad.personalAcceso(userId, rol)` exigiendo `X-User-Id` y `X-User-Rol = PERSONAL_ACCESO` (`400 VALIDACION` si faltan headers y `403 ACCESO_DENEGADO` para cualquier otro rol) y verificar todos los casos en `IdentidadTest`.
- [ ] 2.2 Implementar `EntradaService.validar(codigoQr, eventoId, operadorId)` usando `marcarUsadaSiEmitida` y el diagnóstico por `findByCodigoQr` (`404 QR_INEXISTENTE`, `422 EVENTO_INCORRECTO`, `409 ENTRADA_USADA` con `usadaEn`, `409 ENTRADA_NO_VALIDA` con el estado actual, sin consultar a `ms-eventos`), y verificar todos los escenarios en `EntradaServiceTest`.
- [ ] 2.3 Exponer `POST /entradas/validar` en `EntradaController` usando `@Valid @RequestBody` estándar y `Identidad.personalAcceso(userId, rol)`, tomando el operador exclusivamente de `X-User-Id` y tratando `codigoQr` no UUID como `404 QR_INEXISTENTE`, y verificar todos los casos en `EntradaControllerTest`.

## 3. Consistencia de transiciones desde EMITIDA en ms-ventas

- [ ] 3.1 Actualizar `DevolucionService.solicitar` para ejecutar el `UPDATE` condicional `transicionarDesdeEmitida` (`WHERE estado = 'EMITIDA'`) y rechazar con `409 ENTRADA_NO_DEVOLVIBLE` sin crear `Devolucion` si afecta 0 filas, verificando que `DevolucionServiceTest` (incluido el caso de pérdida de carrera) pasa.
- [ ] 3.2 Actualizar `CancelacionEventoService.marcarPendientes` para ejecutar el `UPDATE` condicional `transicionarDesdeEmitida` (`WHERE estado = 'EMITIDA'`) y crear `Reintegro` únicamente cuando el `UPDATE` afecta 1 fila, verificando que `CancelacionEventoServiceTest` (incluida la omisión de entradas que ya no estaban `EMITIDA`) pasa.

## 4. Documentación y contrato

- [ ] 4.1 Actualizar US-23 (criterios de rechazo y códigos) y el rol `PERSONAL_ACCESO` en `docs/user-stories.md`, documentar `POST /entradas/validar`, `usadaEn`/`validadaPor` en `Entrada`, el rol `PERSONAL_ACCESO` y los errores (`QR_INEXISTENTE`, `EVENTO_INCORRECTO`, `ENTRADA_USADA`, `ENTRADA_NO_VALIDA`) en `docs/openapi.yaml`, y sumar `/entradas/validar` a la API pública de `ms-ventas` en `docs/arquitectura.md`, verificando coherencia con `specs/control-acceso-qr/spec.md`.

## 5. Concurrencia contra base real y verificación integral

- [ ] 5.1 Implementar `ConcurrenciaAccesoQrTest` (`@SpringBootTest(RANDOM_PORT)` + Testcontainers PostgreSQL 16) verificando: (a) N hilos escaneando el mismo QR con exactamente 1 HTTP `200` y N-1 HTTP `409 ENTRADA_USADA` (sin `500`), y (b) un ingreso (`POST /entradas/validar`) y una devolución (`POST /devoluciones`) simultáneos sobre la misma entrada donde solo uno gana y la base queda consistente.
- [ ] 5.2 Ejecutar `mvn -f ms-ventas/pom.xml test` y `openspec validate add-acceso-qr --strict` y verificar que toda la suite y la validación pasan sin errores.

## Workflow follow-up

- Archivar el cambio (`openspec archive add-acceso-qr`) una vez revisada y completada la implementación.
- Validar las especificaciones sincronizadas con `openspec validate --all --strict`.

