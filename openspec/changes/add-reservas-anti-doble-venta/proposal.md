# Proposal

## Why

El núcleo del trabajo es que un mismo asiento nunca se venda dos veces aunque haya muchos compradores a la vez. Esta etapa crea ms-reservas con el inventario, la reserva temporal con vencimiento y el control de concurrencia.

## What Changes

- Generación de asientos al publicar el evento y de cupo para sectores generales.
- Consulta de disponibilidad por evento y sector.
- Reserva temporal con vencimiento (todo o nada) y límite de entradas por cliente.
- Confirmación y liberación de reservas, y vencimiento automático por tarea programada.
- Prueba de concurrencia que demuestra que solo una compra del mismo asiento tiene éxito.

## Capabilities

### New Capabilities
- `inventario-disponibilidad`: asientos, cupos y consulta de disponibilidad
- `reservas-temporales`: reserva con vencimiento, confirmación y liberación
- `anti-doble-venta`: garantías de concurrencia sobre el inventario

### Modified Capabilities

## Impact

- Servicio afectado: `ms-reservas` (base `reservas_db`, puerto 5434).
- Historias: US-11, US-14, US-15 y US-17. Prueba de concurrencia obligatoria.
- Fuera de alcance: pago y emisión de entradas (change `add-compra-con-pago`).
