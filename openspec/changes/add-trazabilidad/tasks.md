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
