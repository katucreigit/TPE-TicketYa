# TicketYa

Plataforma de venta de entradas para eventos, construida como microservicios con Spring Boot.
Trabajo Práctico Especial de Arquitectura Web (TUDAI), **Entrega 2**.

## Integrantes

- Katrina Creimerman
- Facundo Napp
- Geronimo Moroni
- Fernando Barragán
- Benjamin Herrera

## Alcance de la Entrega 2 (sistema completo)

Incluye todo lo de la Entrega 1 más:

- Autenticación y autorización con JWT y roles (ms-auth).
- Fila virtual para eventos de alta demanda (US-xx).
- Control de acceso con QR (US-xx).
- Preferencias y recomendaciones de eventos (US-xx).
- Notificaciones por email/push (US-xx).
- Reportes para organizadores (US-xx).
- Base NoSQL (MongoDB) para preferencias y recomendaciones.
- Cache, resiliencia, log centralizado y comunicación asíncrona.
- Contenedores Docker para todos los servicios con `docker-compose.yml`.

## Arquitectura

| Componente | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | - | Punto de entrada único; valida JWT y enruta |
| `ms-auth` | 8084 | `db_auth` (5436) | Registro, login y emisión de tokens JWT |
| `ms-eventos` | 8081 | `db_eventos` (5433) | Lugares, sectores, eventos, estados y búsqueda |
| `ms-reservas` | 8082 | `db_reservas` (5434) | Inventario, reservas temporales y control de doble venta |
| `ms-ventas` | 8083 | `db_ventas` (5435) | Compras, pagos, entradas con QR, devoluciones y reintegros |
| `ms-notificaciones` | 8085 | `db_notificaciones` (MongoDB, 27017) | Notificaciones a clientes (email simulado) con reintentos y deduplicación |
| `frontend` | 4200 | - | Interfaz web en Angular 19 que consume la API del gateway |

Cada microservicio tiene su propia base (PostgreSQL, o MongoDB en el caso de `ms-notificaciones`) y no comparte datos con los demás.
Los endpoints `/internal/**` se usan solo entre servicios y el gateway no los expone.
El detalle de diseño está en [`docs/arquitectura.md`](docs/arquitectura.md).

## Frontend

Interfaz web en Angular 19 con Bootstrap 5. Consume la API del gateway en `localhost:8080`.

Páginas:

| Ruta | Descripción |
|---|---|
| `/login` | Inicio de sesión |
| `/register` | Registro con selección de rol (CLIENTE / ORGANIZADOR) |
| `/events` | Listado de eventos con filtros por nombre y ciudad |
| `/events/:id` | Detalle del evento, sectores y precios |
| `/events/:id/checkout` | Reserva y pago (requiere sesión como CLIENTE) |
| `/my-tickets` | Entradas compradas, QR y solicitud de devolución |

El JWT se adjunta automáticamente en cada request con un interceptor. Las rutas de checkout y mis entradas tienen un guard que redirige al login si no hay sesión activa.

### Levantar el frontend

```
cd frontend
npm install
ng serve
```

Disponible en `http://localhost:4200`.

## Requisitos

- Java 21
- Maven 3.9 o superior
- Docker y Docker Compose

## Cómo ejecutar

Hay dos formas: todo el sistema en contenedores, o solo las bases en contenedores y los servicios desde el IDE.

### Todo el sistema con Docker Compose

Desde la raíz del repositorio:

```
docker compose up -d --build
```

Levanta 11 contenedores: las 4 bases PostgreSQL (5433, 5434, 5435 y 5436), MongoDB (`mongo`, 27017), `ms-eventos` (8081), `ms-reservas` (8082), `ms-ventas` (8083), `ms-auth` (8084), `ms-notificaciones` (8085) y el gateway (8080). Cada servicio se construye con su propio `Dockerfile` (build con Maven y ejecución con JRE 21), así que no hace falta tener Maven ni Java instalados.

- Antes de levantarlo, detener los servicios que estén corriendo desde el IDE para que no choquen los puertos.
- Cada microservicio espera a que su base esté lista y el gateway arranca al final. Los servicios tardan en iniciar (`ms-auth`, alrededor de 90 segundos): conviene esperar a que `docker compose logs -f` muestre `Started` en los seis antes de usar la API.
- Dentro de compose los servicios se comunican por nombre de servicio (`http://ms-eventos:8081`, `db-eventos:5432`, etc.), configurado con las variables de la tabla de [Configuración](#configuración).
- `docker compose down` detiene todo y conserva los datos; `docker compose down -v` borra también las bases, para empezar de cero.

Cada imagen también se puede construir por separado, por ejemplo:

```
docker build -t ticketya/ms-eventos ms-eventos
```

### Servicios desde el IDE

1. Levantar solo las bases de datos (una por servicio):

   ```
   docker compose up -d db-eventos db-reservas db-ventas db-auth mongo
   ```

   Esto crea cuatro bases PostgreSQL 16 en los puertos 5433, 5434, 5435 y 5436 y un MongoDB 7 en el 27017 (usuario y contraseña `ticketya` en todas).

2. Iniciar cada servicio en una terminal distinta, desde la raíz del repositorio:

   ```
   cd ms-eventos   && mvn spring-boot:run
   cd ms-reservas  && mvn spring-boot:run
   cd ms-ventas    && mvn spring-boot:run
   cd ms-auth      && mvn spring-boot:run
   cd ms-notificaciones && mvn spring-boot:run
   cd api-gateway  && mvn spring-boot:run
   ```

   Conviene iniciar el gateway al final. También se puede ejecutar cada proyecto desde el IDE.

3. Usar la API a través del gateway: `http://localhost:8080/api`.

Las tablas (y la colección e índices de MongoDB) se crean automáticamente al iniciar cada servicio.

### Configuración

Los valores por defecto sirven para ejecución local desde el IDE. Se pueden cambiar con variables de entorno; `docker-compose.yml` las define para que apunten a los nombres de servicio:

| Variable | Servicio | Valor por defecto |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | los ms con PostgreSQL | `localhost` en el puerto de su base, usuario `ticketya` |
| `MONGO_URI` | `ms-notificaciones` | `mongodb://ticketya:ticketya@localhost:27017/db_notificaciones?authSource=admin` |
| `MS_AUTH_URL` | `api-gateway` | `http://localhost:8084` |
| `JWT_SECRET` | `api-gateway`, `ms-auth` | Clave de desarrollo (Base64) de sus `application.yml`; tiene que ser la misma en los dos. Para otro entorno, definirla en el shell o en un `.env` sin versionar |
| `MS_EVENTOS_URL` | `api-gateway`, `ms-reservas`, `ms-ventas` | `http://localhost:8081` |
| `MS_RESERVAS_URL` | `api-gateway`, `ms-eventos`, `ms-ventas` | `http://localhost:8082` |
| `MS_VENTAS_URL` | `api-gateway`, `ms-eventos` | `http://localhost:8083` |

### Documentación interactiva

Cada servicio publica su documentación Swagger una vez iniciado:

- ms-auth: <http://localhost:8084/swagger-ui.html>
- ms-eventos: <http://localhost:8081/swagger-ui.html>
- ms-reservas: <http://localhost:8082/swagger-ui.html>
- ms-ventas: <http://localhost:8083/swagger-ui.html>
- ms-notificaciones: <http://localhost:8085/swagger-ui.html>

El contrato completo de la API pública está en [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.0).

## Autenticación con JWT

Todos los endpoints protegidos requieren un token JWT en el header `Authorization`.

### 1. Registrar un usuario

```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "email": "cliente@example.com",
  "password": "miPassword123",
  "rol": "CLIENTE"
}
```

Roles disponibles: `CLIENTE`, `ORGANIZADOR`, `ADMIN`, `PERSONAL_ACCESO`.

Respuesta:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "rol": "CLIENTE"
}
```

### 2. Iniciar sesión

```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "cliente@example.com",
  "password": "miPassword123"
}
```

Respuesta igual a la del registro.

### 3. Usar el token

Incluir el token en el header `Authorization` de cada request:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

El gateway valida el token y propaga automáticamente los headers `X-User-Id` y `X-User-Rol` a los microservicios. No es necesario enviarlos manualmente.

### Rutas públicas (no requieren token)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/register` | Registro de usuario |
| `POST` | `/api/auth/login` | Login |
| `GET` | `/api/eventos/**` | Buscar y ver eventos |
| `GET` | `/api/lugares/**` | Ver lugares |

Cualquier otra ruta sin token devuelve `401 Unauthorized`.

## Pasarela de pago simulada

La pasarela es un mock que decide según el token del medio de pago:

| Token | Resultado |
|---|---|
| `tok_ok` | Pago aprobado |
| `tok_rechazado` | Pago rechazado (`402`) |
| `tok_timeout` | La pasarela cobra pero no responde a tiempo (`504`); al reintentar con la misma `Idempotency-Key` devuelve el cobro original, sin cobrar de nuevo |

## Notificaciones (email simulado)

`ms-notificaciones` recibe los pedidos en `POST http://localhost:8085/internal/notificaciones` (solo entre servicios), responde `202` y los envía en segundo plano escribiendo el email en el log. Si un envío falla, lo reintenta con espera creciente (2 s y 4 s) y, después de 3 intentos, la notificación queda `FALLIDA`. Si se repite un pedido con la misma `claveDeduplicacion`, devuelve la notificación existente en lugar de crear otra. Un administrador las consulta con `GET http://localhost:8085/notificaciones` (filtros `usuarioId`, `tipo` y `estado`) y `GET /notificaciones/{id}`, con `X-User-Rol: ADMIN`.

Para probar los reintentos, el envío simulado falla según el email del destinatario (se cambia en `ticketya.notificaciones.simulacion`):

| Email | Resultado |
|---|---|
| `falla@ticketya.test` | Falla siempre; queda `FALLIDA` con 3 intentos |
| `falla-una-vez@ticketya.test` | Falla el primer intento y se envía en el segundo |
| cualquier otro | Se envía en el primer intento |

Todavía no hay ruta en el gateway, y los demás servicios todavía no llaman a `ms-notificaciones`. Esas integraciones están pendientes (ver `openspec/changes/add-notificaciones/tasks.md`).

## Pruebas

Cada servicio se prueba por separado:

```
cd ms-auth     && mvn test
cd ms-eventos  && mvn test
cd ms-reservas && mvn test
cd ms-ventas   && mvn test
cd ms-notificaciones && mvn test
cd api-gateway && mvn test
```

La prueba de concurrencia de `ms-reservas` lanza solicitudes simultáneas por el mismo lugar y verifica que exactamente una resulte exitosa. Las pruebas de `ms-reservas` y `ms-notificaciones` usan Testcontainers (PostgreSQL 16 y MongoDB 7), así que necesitan Docker en ejecución.

## Documentación del proyecto

| Documento | Contenido |
|---|---|
| [`docs/user-stories.md`](docs/user-stories.md) | Historias de usuario, criterios de aceptación y reglas generales |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Microservicios, gateway, anti doble venta, saga, estados y decisiones |
| [`docs/modelo-datos.md`](docs/modelo-datos.md) | Modelo de datos por servicio y datos compartidos |
| [`docs/secuencia-compra.md`](docs/secuencia-compra.md) | Diagramas de secuencia de la compra y el vencimiento |
| [`docs/diagramas.md`](docs/diagramas.md) | Diagramas en Mermaid: arquitectura, modelo de datos, estados y secuencias |
| [`docs/openapi.yaml`](docs/openapi.yaml) | Contrato de la API en OpenAPI 3.0 |

## Datos de prueba

- `ms-eventos` carga al iniciar un conjunto inicial: organizadores (ids 1 y 2), lugares con sus sectores y algunos eventos.
- Para pagar se usan los tokens de la pasarela simulada: `tok_ok`, `tok_rechazado` y `tok_timeout`.
- Para probar sin registrarse a mano, la carpeta de preparación de la colección Postman crea un usuario por rol (contraseña `pass123`) y hace login de cada uno.

## Colección de requests (Postman)

En [`postman/TicketYa.postman_collection.json`](postman/TicketYa.postman_collection.json) hay una colección agrupada por recurso. La autenticación es siempre con JWT: cada request protegido manda `Authorization: Bearer {{token...}}` con el token del rol que corresponde, y las rutas públicas (como `GET /eventos`) van sin token.

Las carpetas se ejecutan en este orden con el Runner:

1. **0. Preparación (auth)**: registra un ORGANIZADOR, un CLIENTE, un segundo CLIENTE, un ADMIN y un usuario de acceso (si ya existen, el `409` se acepta) y hace login de cada uno, guardando `tokenOrganizador`, `tokenCliente`, `tokenCliente2`, `tokenAdmin` y `tokenAcceso` en variables de la colección. 10 pruebas. El ORGANIZADOR se registra primero a propósito: `ms-eventos` exige que el id del token exista en su tabla de organizadores, que tiene los ids 1 y 2 de los datos de prueba, y con `db-auth` vacía el primer usuario recibe el id 1. Si `db-auth` ya tenía otros usuarios, el login del organizador lo avisa; en ese caso, `docker compose down -v` y volver a levantar.
2. **Flujo completo (en orden)**: crear lugar y evento, configurar precios, publicar, ver disponibilidad, reservar, pagar, ver entradas, solicitar y aprobar una devolución. 15 pruebas.
3. **Casos de error**: asiento ya reservado por otro cliente (`409`), pago rechazado (`402`), pago sin `Idempotency-Key` (`400`), token inválido y sin token (`401`) y un CLIENTE intentando una operación de ORGANIZADOR (`403`). 7 pruebas.

El resto de las carpetas son requests sueltos por recurso, y la de internos va directo a cada servicio sin pasar por el gateway. La colección [`postman/TicketYa-Auth.postman_collection.json`](postman/TicketYa-Auth.postman_collection.json) tiene requests sueltos de registro y login.

Para usarla: levantar los 5 servicios (desde el IDE o con `docker compose up -d --build`) y ejecutar las tres carpetas en orden. `ms-auth` tarda alrededor de 90 segundos en arrancar (más en una máquina cargada); hasta que `docker compose logs ms-auth` muestre `Started MsAuthApplication`, la preparación falla. Funciona igual contra los contenedores, porque el gateway se publica en el mismo puerto 8080. Para repetir el flujo desde cero con compose: `docker compose down -v` y volver a levantar.

Desde la línea de comandos, con [newman](https://www.npmjs.com/package/newman):

```
npx newman run postman/TicketYa.postman_collection.json --folder "0. Preparación (auth)" --folder "Flujo completo (en orden)" --folder "Casos de error"
```

## Uso de inteligencia artificial

Usamos Claude (Anthropic) como asistente durante el trabajo:

- Claude Code, en la terminal, para implementar los microservicios y sus pruebas siguiendo las especificaciones de OpenSpec.
- El chat de Claude para planificar, revisar el código, diagramas y redactar la documentación.

Las especificaciones se escribieron antes del código y guiaron lo que generó la IA. El equipo revisó el código, corrió las pruebas y verificó el flujo completo contra bases PostgreSQL reales.

## Estado y limitaciones conocidas

- `ms-eventos` valida que el organizador exista en su tabla `organizador` (hay datos de prueba con los ids 1 y 2).
- La cancelación masiva de un evento no trata las entradas con devolución solicitada ni compras con pago en curso al momento de cancelar.

## Metodología: desarrollo guiado por especificaciones

El comportamiento del sistema se especifica antes de implementarlo, con [OpenSpec](https://github.com/Fission-AI/OpenSpec).
Los requisitos se escriben en notación EARS y cada uno tiene escenarios `WHEN` / `THEN` verificables.

```
openspec/
  config.yaml          contexto del proyecto y reglas de redacción
  specs/               especificaciones vigentes
  changes/             un cambio por bloque de trabajo
```

Para validar el formato de las especificaciones (opcional, requiere Node.js):

```
npm install -g @fission-ai/openspec
openspec validate --all --strict
```

## Estructura del repositorio

```
api-gateway/       API Gateway (Spring Cloud Gateway, validación JWT)
ms-auth/           Microservicio de autenticación (registro, login, JWT)
ms-eventos/        Microservicio de eventos
ms-reservas/       Microservicio de reservas
ms-ventas/         Microservicio de ventas
ms-notificaciones/ Microservicio de notificaciones (MongoDB)
frontend/          Interfaz web Angular 19
docs/              Historias de usuario, arquitectura, modelo de datos y OpenAPI
openspec/          Especificaciones y cambios
postman/           Colección de requests
docker-compose.yml Sistema completo: bases PostgreSQL y MongoDB, microservicios y gateway
AGENTS.md          Convenciones del proyecto
```
