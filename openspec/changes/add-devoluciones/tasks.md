# Tasks

## 1. Devolución

- [ ] 1.1 Endpoint para solicitar devolución con validación de dueño, estado y plazo de 72 horas.
- [ ] 1.2 Entidad Devolucion y enum de estados SOLICITADA, EN_PROCESO, REINTEGRADA y RECHAZADA.
- [ ] 1.3 Pasar la entrada a DEVOLUCION_SOLICITADA y bloquear su uso para ingresar.

## 2. Resolución

- [ ] 2.1 Endpoint de aprobar con reintegro a la pasarela y liberación en ms-reservas.
- [ ] 2.2 Registrar Reintegro con UNIQUE(entrada_id, tipo).
- [ ] 2.3 Reintento de la parte pendiente al aprobar de nuevo.
- [ ] 2.4 Endpoint de rechazar con motivo, que devuelve la entrada a EMITIDA.
- [ ] 2.5 Listado de devoluciones filtrable por estado (ADMIN).

## 3. Cancelación de evento

- [ ] 3.1 Endpoint interno que recibe el aviso de cancelación y reintegra las entradas EMITIDA.
- [ ] 3.2 Marcar REINTEGRO_PENDIENTE las que fallen y permitir reintentarlas.
- [ ] 3.3 Manejar la compra en proceso de pago al cancelar.

## 4. Pruebas

- [ ] 4.1 Tests de la regla de 72 horas, incluido el límite exacto.
- [ ] 4.2 Test de doble devolución de la misma entrada.
- [ ] 4.3 Test de falla del reintegro y reintento sin devolver el dinero dos veces.
- [ ] 4.4 Test de falla de la liberación con reintegro ya hecho.
- [ ] 4.5 Test de cancelación con un reintegro fallido y reintento.
