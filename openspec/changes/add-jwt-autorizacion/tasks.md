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
