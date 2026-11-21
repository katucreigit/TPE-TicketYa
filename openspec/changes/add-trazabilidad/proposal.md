# Proposal

## Why

El sistema necesita permitir el seguimiento de cada solicitud HTTP y de las operaciones críticas que se ejecutan en sus distintos servicios. Sin un identificador de correlación compartido y registros centralizados, no es posible reconstruir el recorrido completo de una compra ni relacionar los eventos generados por los distintos microservicios.

## What Changes

- Incorporar el registro de cada request HTTP con su método, endpoint, usuario, código de respuesta HTTP y tiempo de procesamiento.
- Incorporar un identificador de correlación (`correlationId`) para relacionar las solicitudes y operaciones que forman parte de un mismo flujo.
- Propagar el mismo `correlationId` entre el gateway y los microservicios involucrados en cada operación.
- Registrar de forma centralizada las operaciones críticas del sistema.
- Permitir consultar y seguir una operación de punta a punta utilizando su `correlationId`.

## Capabilities

### New Capabilities

- `trazabilidad`: registro y seguimiento de solicitudes HTTP y operaciones críticas mediante un identificador de correlación compartido entre los servicios.

### Modified Capabilities

## Impact

- `api-gateway`: recepción, generación o propagación del identificador de correlación.
- Microservicios `ms-eventos`, `ms-reservas`, `ms-ventas` y `ms-auth`: propagación del identificador y registro de solicitudes y operaciones críticas.
- Sistema de logs: centralización y consulta de los registros asociados a un mismo `correlationId`.
