# Proposal

## Why

El núcleo del trabajo es que un mismo lugar nunca se venda dos veces, aunque haya muchos compradores a la vez y varias instancias del servicio. Esta etapa implementa ms-reservas: inventario, reserva temporal con vencimiento, límite por cliente y el control de concurrencia, todo resuelto en la base de datos.

## What Changes

- Inicialización del inventario al publicar el evento, consulta de disponibilidad y mapa de asientos.
- Cierre y reapertura del inventario cuando el evento se suspende, se cancela o se reanuda.
- Aviso de agotado a ms-eventos.
- Reserva temporal todo o nada con vencimiento de 10 minutos (US-11).
- Consulta, cancelación, confirmación y liberación de reservas (US-12, US-14).
- Devolución de lugares aprobada por el administrador (US-19).
- Límite de entradas por cliente y evento (US-15).
- Garantía de no doble venta y test de concurrencia (US-17).

## Capabilities

### New Capabilities
- `inventario-disponibilidad`: inventario, disponibilidad, cierre del evento y aviso de agotado
- `reservas-temporales`: reserva con vencimiento, consulta, cancelación, confirmación y liberación
- `devolucion-lugares`: liberación de lugares por devolución aprobada
- `anti-doble-venta`: garantías de concurrencia y límite por cliente

### Modified Capabilities

## Impact

- Servicio afectado: `ms-reservas` (base `db_reservas`, puerto 5434, paquete `com.ticketya.reservas`).
- Contrato: `docs/openapi.yaml` (tags Disponibilidad, Reservas e Internos). Modelo: `docs/modelo-datos.md` y su diagrama ER.
- Historias: US-10, US-11, US-14, US-15 y US-17, más las reglas generales de `docs/user-stories.md`.
- Fuera de alcance: pago y emisión de entradas (change `add-compra-con-pago`), decisión de devoluciones (change `add-devoluciones`) y fila virtual (E2).
