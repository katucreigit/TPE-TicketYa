# Proposal: Catálogo de eventos

## Why

El sistema necesita gestionar los lugares donde se realizan los eventos y permitir que los organizadores creen, configuren y administren sus eventos. Esta funcionalidad constituye la base del catálogo que luego podrán consultar los clientes.

## What Changes

- Incorporar la gestión de lugares y sectores, incluyendo sus validaciones.
- Incorporar la creación y edición de eventos en estado BORRADOR.
- Incorporar la configuración de precios por sector y el límite de entradas por cliente.
- Incorporar las operaciones de publicación, suspensión, reanudación y cancelación de eventos.
- Incorporar la actualización interna de disponibilidad y la finalización automática de eventos pasados.

## Capabilities

### New capabilities
- `lugares-sectores`: gestión de lugares y sus sectores.
- `catalogo-eventos`: creación, consulta y edición de eventos.
- `estados-eventos`: publicación y administración de los estados de los eventos.

### Modified capabilities
- Ninguna.

## Impact

- Microservicio `ms-eventos`: entidades, repositorios, servicios, controladores, validaciones y pruebas.
- API REST: endpoints de lugares y eventos.
- Integración con `ms-reservas` para inicializar y cerrar inventarios.
- Integración con `ms-ventas` para informar cancelaciones.
- Base de datos relacional de `ms-eventos`.
