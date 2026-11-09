# Design

## Context

`ms-eventos` es el dueño del catálogo. Las demás piezas (reservas, ventas) consumen datos de eventos mediante endpoints internos y guardan snapshots cuando lo necesitan. Ver `docs/arquitectura.md` y `docs/modelo-datos.md`.

## Goals / Non-Goals

**Goals:**
- Que un ORGANIZADOR cree y publique eventos sobre lugares existentes.
- Que las transiciones de estado inválidas se rechacen con 409.
- Que el catálogo público solo muestre eventos PUBLICADO.

**Non-Goals:**
- Autenticación real (JWT): queda para la Entrega 2; la identidad llega por headers.
- Generación de asientos: la hace ms-reservas.
- Búsqueda por filtros combinados.

## Decisions

**Identidad por headers.** En la Entrega 1 el rol llega en X-User-Rol (CLIENTE, ORGANIZADOR, ADMIN, ACCESO). El servicio valida el rol por endpoint y devuelve 403 si no corresponde.

**Estado SUSPENDIDO.** Se agrega SUSPENDIDO además de los estados del enunciado, con endpoints /suspender y /reanudar, para frenar ventas sin cancelar el evento.

**Sectores numerados y generales.** Cada sector define si es numerado (filas y asientos) o de entrada general (solo cupo). Esto condiciona cómo ms-reservas controla el inventario.

## Risks / Trade-offs

- Un evento publicado con datos incompletos genera problemas aguas abajo; se valida antes de publicar.
- Cambiar la fecha de un evento con entradas vendidas afecta la regla de las 72 h; ms-ventas guarda un snapshot de la fecha.
