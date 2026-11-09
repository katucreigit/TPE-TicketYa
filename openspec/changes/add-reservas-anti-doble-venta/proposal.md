# Proposal

## Why

El núcleo del trabajo es que un mismo lugar nunca se venda dos veces aunque haya muchos compradores a la vez. Esta etapa crea ms-reservas con el inventario, la reserva temporal con vencimiento, el límite por cliente y el control de concurrencia en la base.

## What Changes

- Inicialización idempotente del inventario al publicar y consulta de disponibilidad (US-10, US-05).
- Reserva temporal todo o nada con vencimiento de 10 minutos (US-11).
- Cancelación y vencimiento de reservas (US-14).
- Límite de entradas por cliente y evento (US-15).
- Garantía de no doble venta y test de concurrencia (US-17).

## Capabilities

### New Capabilities
- `inventario-disponibilidad`: inventario de asientos y cupos y su disponibilidad
- `reservas-temporales`: reserva con vencimiento, confirmación y liberación
- `anti-doble-venta`: garantías de concurrencia y límite por cliente

### Modified Capabilities

## Impact

- Servicio afectado: `ms-reservas` (base `db_reservas`, puerto 5434).
- Historias: US-11, US-14, US-15 y US-17; prueba de concurrencia obligatoria.
- Fuera de alcance: pago y emisión de entradas (change `add-compra-con-pago`).
