# Proposal

## Why

Las entradas emitidas por `ms-ventas` ya tienen un `codigoQr` único y el estado `USADA` en el modelo, pero todavía no existe una operación que permita al personal de acceso validar un QR en el ingreso al evento ni garantizar que una misma entrada no ingrese dos veces ni compita con una devolución o cancelación simultánea. Implementar US-23 cierra el ciclo de vida de la entrada en `ms-ventas` con control atómico en base de datos.

## What Changes

- Exponer el endpoint `POST /entradas/validar` en `EntradaController` (enrutado por el gateway en `/api/entradas/validar`) restringido al rol `PERSONAL_ACCESO`, tomando al operador desde `Identidad` (`X-User-Id` / `X-User-Rol`) y nunca del body.
- Incorporar las columnas `usada_en` y `validada_por` en la entidad `Entrada` y exponerlas (nullable / omitidas cuando son `null`) en `EntradaResponse` y el schema `Entrada` de `docs/openapi.yaml`.
- Implementar la transición atómica `EMITIDA -> USADA` en `EntradaRepository` mediante `UPDATE` condicional por estado (`codigo_qr`, `evento_id`, `estado = 'EMITIDA'`), con clasificación explícita de rechazos cuando el `UPDATE` afecta 0 filas (`404 QR_INEXISTENTE`, `422 EVENTO_INCORRECTO`, `409 ENTRADA_USADA`, `409 ENTRADA_NO_VALIDA`).
- Convertir en `UPDATE` condicionales por estado (`WHERE estado = 'EMITIDA'`) las demás transiciones desde `EMITIDA` en `ms-ventas` (`DevolucionService.solicitar` y `CancelacionEventoService.marcarPendientes`), asegurando exclusión mutua en base de datos entre ingreso, devolución y cancelación.
- Actualizar la documentación y el contrato: criterios de rechazo y códigos de US-23 en `docs/user-stories.md`, endpoint y esquemas (incluyendo la alineación del rol `PERSONAL_ACCESO`) en `docs/openapi.yaml` y la API pública de `ms-ventas` en `docs/arquitectura.md`.
- Agregar pruebas unitarias (`EntradaService`), de controlador (`EntradaController`), de repositorio (0 filas por cada motivo) y de concurrencia contra PostgreSQL real con Testcontainers (N escaneos simultáneos del mismo QR e ingreso + devolución simultáneos sobre la misma entrada).

## Capabilities

### New Capabilities
- `control-acceso-qr`: validación de entradas por código QR en el ingreso al evento (`POST /entradas/validar`), transición atómica `EMITIDA -> USADA`, códigos de rechazo diferenciados y exclusión mutua con devoluciones y cancelaciones en `ms-ventas`.

### Modified Capabilities

## Impact

- **Servicio afectado**: `ms-ventas` (`Entrada`, `EntradaResponse`, `ValidarEntradaRequest`, `Identidad`, `EntradaController`, `EntradaService`, `EntradaRepository`, `DevolucionService`, `CancelacionEventoService`).
- **Base de datos (`db_ventas`)**: nuevas columnas `usada_en` y `validada_por` en la tabla `entrada` (actualizadas automáticamente con `ddl-auto: update`).
- **Dependencias de test (`ms-ventas/pom.xml`)**: incorporación de `spring-boot-testcontainers`, `testcontainers:junit-jupiter` y `testcontainers:postgresql` (versión `1.21.4`, igual que `ms-reservas`) para las pruebas de repositorio y concurrencia contra PostgreSQL 16 real.
- **Documentación y contratos**: `docs/user-stories.md` (US-23 y rol `PERSONAL_ACCESO`), `docs/openapi.yaml` (`POST /entradas/validar`, schema `Entrada` con `usadaEn`/`validadaPor`, errores y rol `PERSONAL_ACCESO`), `docs/arquitectura.md` (API pública de `ms-ventas`).
- **Enrutamiento**: el API Gateway ya enruta `/api/entradas/**` a `ms-ventas` y acepta el rol `PERSONAL_ACCESO` en `JwtAuthFilter`, por lo que no requiere cambios en `api-gateway`.
- **Fuera de alcance**: log centralizado de accesos (corresponde a `add-trazabilidad` / TS-02), generación de imagen PNG/SVG del QR y modo offline de escaneo.

