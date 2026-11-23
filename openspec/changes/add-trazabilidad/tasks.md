# Tasks

## 1. Identificador de correlación en el API Gateway

- [x] 1.1 Implementar la recepción o generación del `correlationId` en el API Gateway y verificar mediante pruebas que se genere cuando no se recibe uno.
- [x] 1.2 Propagar el mismo `correlationId` en las solicitudes dirigidas a los microservicios y verificarlo mediante pruebas.

## 2. Trazabilidad en los microservicios

- [ ] 2.1 Incorporar la recepción y propagación del `correlationId` en los microservicios y verificar mediante pruebas que se conserve durante las comunicaciones entre servicios.
- [ ] 2.2 Incorporar el registro de cada solicitud HTTP con método, endpoint, usuario, código de respuesta y tiempo de procesamiento; verificar los datos mediante pruebas.
- [ ] 2.3 Incorporar el registro de operaciones críticas asociado al `correlationId` y verificar que los registros puedan relacionarse con las solicitudes correspondientes.

## 3. Centralización de registros

- [ ] 3.1 Integrar los registros de los servicios con el mecanismo centralizado de almacenamiento y consulta, y verificar que estén disponibles.
- [ ] 3.2 Verificar que los registros centralizados permitan buscar y relacionar operaciones mediante el `correlationId`.

## 4. Verificación integral

- [ ] 4.1 Ejecutar una compra de prueba y verificar que el mismo `correlationId` permita seguir las solicitudes y operaciones registradas en los servicios participantes.
- [ ] 4.2 Actualizar la documentación de arquitectura para describir la propagación del identificador y el mecanismo de trazabilidad, y verificar que coincida con el funcionamiento implementado.

## Workflow follow-up

- Archivar el cambio cuando la implementación y las verificaciones estén completas.
- Validar las especificaciones archivadas.

## Avance por servicio (sin marcar casillas: las tareas 2.x siguen abiertas en los demás servicios)

### ms-reservas (2026-11-23): tareas 2.1, 2.2 y 2.3 aplicadas

- **2.1 correlationId.** `trazabilidad/CorrelationIdFilter` (primer filtro, también en `/internal/**`) conserva el `X-Correlation-Id` recibido o genera un UUID. Si el valor recibido tiene caracteres de control o más de 128 caracteres, lo reemplaza para evitar inyección en los logs. Lo deja en el MDC con `X-User-Id` y `X-User-Rol`, lo devuelve en la respuesta y limpia el MDC al terminar. `PropagacionCorrelacionConfig` es un `RestClientCustomizer` que agrega el header a todas las llamadas salientes; hoy son los avisos de agotado a ms-eventos. `VencimientoJob` usa un correlationId propio por corrida. El patrón de log incluye `[%X{correlationId}]` en cada línea.
- **2.2 Registro de solicitudes.** `trazabilidad/RegistroSolicitudesFilter` escribe una línea por solicitud con el mismo formato que el `HTTP_REQUEST` del gateway, más el rol: `HTTP_REQUEST method= endpoint= user= rol= status= durationMs= correlationId=`. Sin identidad, el usuario y el rol figuran como `ANONIMO`.
- **2.3 Operaciones críticas.** `trazabilidad/RegistroOperaciones` escribe `OPERACION_CRITICA operacion= usuario= correlationId=` seguido de los ids de negocio (`reservaId`, `eventoId`, `sectorId`, `clienteId`, `cantidad`, `asientos`), para `RESERVA_CREADA`, `RESERVA_CONFIRMADA`, `RESERVA_VENCIDA`, `RESERVA_LIBERADA` (con `motivo` `CANCELACION_CLIENTE`, `COMPENSACION` o `CIERRE_INVENTARIO`) y `RESERVA_DEVUELTA` (solo los lugares que se liberaron en esa llamada). Se escribe después del commit, así que una operación revertida no queda registrada, y las repeticiones sin cambios no se registran. Sin `X-User-Id` (job o llamada interna), el usuario es `SISTEMA`.
- **Centralización (3.x).** No se hizo. `design.md` deja la herramienta para más adelante; los registros salen por la salida estándar del contenedor en formato `clave=valor`, listos para cualquier recolector.
- **Pruebas.** `trazabilidad/TrazabilidadTest` (21) y `trazabilidad/PropagacionCorrelacionTest` (3, contra un servidor HTTP real que hace de ms-eventos). `mvn test`: 207 pruebas, las 162 existentes y 45 nuevas, incluidas las de autorización de add-jwt-autorizacion.
