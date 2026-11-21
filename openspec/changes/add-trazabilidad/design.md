# Design

## Context

Ver `proposal.md` para la motivación y `specs/trazabilidad/spec.md` para los requisitos.

El sistema está compuesto por un API Gateway y varios microservicios que se comunican mediante solicitudes HTTP. La trazabilidad requiere relacionar los registros generados durante esas comunicaciones.

La solución debe poder aplicarse a los distintos servicios sin depender de una herramienta específica de centralización de logs.

## Goals / Non-Goals

**Goals:**
- Mantener un identificador de correlación común durante el recorrido de una operación.
- Unificar el criterio de registro de solicitudes HTTP en los servicios.
- Permitir relacionar los registros generados por los distintos servicios.

**Non-Goals:**
- Definir una herramienta específica de centralización o visualización de logs.
- Incorporar métricas, monitoreo o alertas.
- Modificar la lógica funcional de las operaciones existentes.

## Decisions

### Identificador de correlación

Se utilizará un `correlationId` para identificar cada solicitud y relacionar las operaciones derivadas.

Cuando una solicitud ingrese sin identificador, el primer componente que la reciba generará uno. Si ya existe, se conservará.

**Alternativas consideradas:**
- Generar un identificador nuevo en cada servicio: se descarta porque impediría relacionar los registros de una misma operación.
- Mantener un identificador compartido: permite seguir el recorrido completo.

### Propagación entre servicios

El `correlationId` se propagará en las comunicaciones HTTP entre el API Gateway y los microservicios involucrados.

Cada servicio deberá conservar el identificador recibido al generar sus propios registros y al comunicarse con otros servicios.

**Alternativa considerada:**
- Generar identificadores independientes en cada servicio: se descarta porque fragmentaría la trazabilidad.

### Registro y centralización

Los servicios registrarán las solicitudes HTTP procesadas y las operaciones críticas asociadas a su ejecución.

Los registros incluirán el `correlationId` para permitir su relación y serán enviados a un mecanismo centralizado de almacenamiento y consulta.

La herramienta concreta de centralización se definirá posteriormente, sin modificar el criterio de registro ni la propagación del identificador.

**Alternativa considerada:**
- Mantener los registros únicamente en cada servicio: se descarta porque dificulta el seguimiento conjunto de una operación.

## Risks / Trade-offs

- [Pérdida del identificador durante una comunicación entre servicios] → Propagar el mismo `correlationId` en cada solicitud HTTP derivada.
- [Registros que no pueden relacionarse] → Incluir el identificador de correlación en los registros de solicitudes y operaciones críticas.
- [Dependencia de una herramienta de centralización] → Mantener el mecanismo de registro independiente de la herramienta elegida.

## Migration Plan

1. Incorporar el mecanismo de generación o recepción del `correlationId`.
2. Adaptar el API Gateway y los microservicios para propagarlo.
3. Incorporar el registro de solicitudes HTTP y operaciones críticas.
4. Integrar los registros con el mecanismo centralizado de consulta.
5. Verificar el seguimiento de una compra utilizando un único `correlationId`.

La reversión podrá realizarse retirando los mecanismos de trazabilidad incorporados, sin modificar la lógica funcional de las operaciones existentes.
