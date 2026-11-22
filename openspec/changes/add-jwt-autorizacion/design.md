# Design

## Context

El sistema utiliza una arquitectura de microservicios con un API Gateway como punto de entrada para las solicitudes HTTP.

El servicio `ms-auth` autentica usuarios y genera tokens JWT. El token contiene el identificador del usuario en el campo `sub` y su rol en el claim `rol`. También incluye las fechas de emisión y vencimiento.

El servicio `api-gateway` utiliza la misma clave secreta configurada en `ms-auth` para validar los tokens. Actualmente, el Gateway utiliza Spring Cloud Gateway WebFlux y un filtro global para realizar la autenticación.

Los microservicios reciben la identidad autenticada mediante los headers `X-User-Id` y `X-User-Rol`.

Los roles definidos por el sistema son `CLIENTE`, `ORGANIZADOR`, `ADMIN` y `PERSONAL_ACCESO`.

## Goals / Non-Goals

**Goals:**

- Centralizar la validación de los tokens JWT en el API Gateway.
- Obtener la identidad y el rol exclusivamente de los claims autenticados del token.
- Evitar la suplantación de identidad mediante headers enviados por el cliente.
- Propagar la identidad autenticada hacia los microservicios.
- Mantener la autorización de las operaciones en los servicios responsables de ejecutarlas.

**Non-Goals:**

- Implementar un mecanismo de autenticación diferente de JWT.
- Modificar el formato de los tokens emitidos por `ms-auth`.
- Reemplazar las verificaciones de permisos propias de cada microservicio.
- Incorporar un sistema de gestión de usuarios o roles nuevo.

## Decisions

### Validación de JWT en el API Gateway

El API Gateway utiliza un filtro global (`JwtAuthFilter`) para validar las solicitudes dirigidas a endpoints protegidos.

El filtro obtiene el token del header `Authorization`, verifica que tenga el formato Bearer y valida su firma utilizando la clave secreta compartida con `ms-auth`.

La biblioteca JJWT permite verificar la firma y el vencimiento del token durante su análisis. También se comprueba que el token contenga un identificador de usuario y un rol válido.

Se responde con HTTP 401 cuando falta el token, su formato es incorrecto, su firma no es válida, está vencido o contiene datos de identidad incompletos o inválidos.

Se mantienen las excepciones de acceso público configuradas para las rutas de autenticación y las consultas públicas del catálogo.

**Alternativa considerada:** validar los tokens individualmente en cada microservicio. Se elige centralizar la validación en el Gateway para evitar duplicar ese procesamiento, manteniendo la autorización de las operaciones en cada servicio.

### Obtención y propagación de identidad

Una vez validado el token, el Gateway obtiene el identificador desde `sub` y el rol desde `rol`.

Antes de reenviar la solicitud, elimina los headers `X-User-Id` y `X-User-Rol` que pudiera haber enviado el cliente. Luego establece esos headers con los valores obtenidos del token validado.

De esta manera, los microservicios reciben una identidad confiable y no los valores manipulados por el cliente.

**Alternativa considerada:** conservar los headers originales y utilizarlos cuando estén presentes. Se descarta porque permitiría la suplantación de identidad.

### Autorización según roles

El Gateway realiza la autenticación y propaga la identidad. Cada microservicio conserva la responsabilidad de verificar qué roles pueden ejecutar sus operaciones.

Los nombres de roles utilizados deben coincidir en `ms-auth`, `api-gateway` y los microservicios. Se utiliza `PERSONAL_ACCESO` como nombre del rol de personal de acceso.

**Alternativa considerada:** implementar todas las reglas de autorización en el Gateway. Se descarta porque los permisos dependen de las operaciones y responsabilidades de cada microservicio.

### Configuración de la clave secreta

La clave JWT se obtiene mediante la propiedad `jwt.secret`, configurable a través de la variable de entorno `JWT_SECRET`.

Los servicios que validan los tokens deben utilizar la misma clave y el mismo formato de codificación que `ms-auth`.

La clave de desarrollo configurada por defecto no debe utilizarse en un despliegue productivo.

## Risks / Trade-offs

- **Clave secreta diferente entre servicios** → Los tokens emitidos por `ms-auth` no podrán validarse en el Gateway. Se debe configurar la misma clave en todos los servicios involucrados.
- **Clave secreta expuesta** → Un tercero podría generar tokens válidos. Se debe utilizar una variable de entorno y evitar guardar secretos reales en el repositorio.
- **Diferencias en los nombres de roles** → Podrían rechazarse usuarios válidos o aplicarse permisos incorrectos. Se deben mantener los mismos nombres de roles en todos los servicios.
- **Acceso directo a los microservicios** → Podría evitarse la validación realizada por el Gateway. En despliegue, los microservicios deben protegerse para que las solicitudes externas ingresen por el Gateway.
- **Cambios en las rutas públicas** → Una ruta protegida podría quedar accesible sin token si se configura incorrectamente. Las excepciones públicas deben limitarse a los endpoints que realmente lo requieren.

## Migration Plan

1. Mantener la generación de JWT en `ms-auth` con el identificador de usuario, el rol y la fecha de vencimiento.
2. Configurar en el API Gateway la misma clave secreta utilizada por `ms-auth`.
3. Incorporar y habilitar el filtro global de validación JWT en el Gateway.
4. Verificar que las solicitudes protegidas sin token, con token inválido o vencido sean rechazadas con HTTP 401.
5. Verificar que las solicitudes autenticadas propaguen `X-User-Id` y `X-User-Rol` con los valores obtenidos del token.
6. Verificar que los microservicios apliquen los permisos correspondientes y rechacen los roles no autorizados con HTTP 403.

Para revertir la implementación, se puede restaurar la configuración y el filtro del Gateway a la versión anterior. La generación de tokens en `ms-auth` no requiere cambios de datos ni migraciones de base de datos.
