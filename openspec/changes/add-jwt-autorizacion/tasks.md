# Tasks

## 1. Autenticación y seguridad en el API Gateway

- [x] 1.1 Implementar la validación de JWT en endpoints protegidos y verificar mediante pruebas los casos de token ausente, inválido, vencido y válido.
- [x] 1.2 Obtener el identificador y el rol desde los claims autenticados del JWT, reemplazar los headers de identidad enviados por el cliente y verificar mediante pruebas que no sea posible suplantarlos.
- [x] 1.3 Propagar `X-User-Id` y `X-User-Rol` hacia los microservicios y verificar mediante pruebas que reciban los valores correspondientes al token validado.

## 2. Emisión y autorización en los servicios

- [ ] 2.1 Verificar que `ms-auth` genere JWT con identificador de usuario, rol y vencimiento, y comprobar mediante pruebas que los claims sean compatibles con el API Gateway.
- [ ] 2.2 Verificar que los microservicios reciban y utilicen `X-User-Id` y `X-User-Rol` para identificar al usuario autenticado, mediante pruebas de sus endpoints.
- [ ] 2.3 Implementar y verificar las restricciones de acceso por rol en los endpoints de cada microservicio, comprobando respuestas HTTP 403 para roles no autorizados.
- [ ] 2.4 Unificar los nombres de roles utilizados por los servicios y verificar que sean `CLIENTE`, `ORGANIZADOR`, `ADMIN` y `PERSONAL_ACCESO`.

## 3. Verificación integral y documentación

- [ ] 3.1 Ejecutar pruebas de integración desde el API Gateway hacia los microservicios y verificar que la identidad autenticada se conserve durante el recorrido.
- [ ] 3.2 Verificar mediante pruebas que las solicitudes sin token, con token inválido o vencido reciban HTTP 401 y que los usuarios sin permisos reciban HTTP 403.
- [ ] 3.3 Actualizar la documentación de arquitectura para describir la autenticación JWT, la propagación de identidad y la autorización por roles, y comprobar que coincida con la implementación.

## Workflow follow-up

- Archivar el cambio cuando la implementación y las verificaciones estén completas.
- Validar las especificaciones archivadas.

## Avance por servicio (sin marcar casillas: las tareas 2.x siguen abiertas en los demás servicios)

### ms-reservas (2026-11-23): tareas 2.2, 2.3 y 2.4 aplicadas

- **2.2 Identidad.** Los endpoints de `/reservas` toman el cliente de `X-User-Id` y el rol de `X-User-Rol`. Sin alguno de los dos responden `400 VALIDACION`.
- **2.3 Restricciones por rol.** El código ya cumplía el contrato de `docs/openapi.yaml` y `docs/user-stories.md`, así que no se cambió código de producción. `POST /reservas`, `GET /reservas/{id}` y `DELETE /reservas/{id}` son solo de `CLIENTE`, y los dos últimos solo del cliente dueño; ms-ventas consulta la reserva reenviando `X-User-Rol: CLIENTE`. Cualquier otro rol recibe `403 ACCESO_DENEGADO` sin efectos. `GET /eventos/{id}/disponibilidad` y `GET /eventos/{id}/sectores/{sid}/asientos` son públicos. Los `/internal/**` no se tocaron.
- **2.4 Nombres de roles.** ms-reservas solo compara contra `CLIENTE`. Se adoptó `PERSONAL_ACCESO` como nombre oficial del rol de acceso, el mismo de la tarea 2.4, el gateway y ms-auth. Se reemplazó el nombre anterior en `docs/user-stories.md`, `docs/openapi.yaml`, la colección de Postman y las pruebas de ms-reservas. Todavía aparece en la proposal de este cambio y en `ms-ventas/src/test/.../IdentidadTest.java`, que quedan a cargo de sus responsables.
- **Pruebas.** `controller/AutorizacionPorRolTest` (21) prueba cada rol (`CLIENTE`, `ORGANIZADOR`, `ADMIN`, `PERSONAL_ACCESO` y variantes inválidas) en cada endpoint, más la reserva de otro cliente, la falta de headers y los endpoints públicos con cualquier rol o sin identidad. Las 162 pruebas existentes siguen pasando; en `IdentidadTest` solo se cambió el nombre del rol en la lista de roles que reciben 403.
