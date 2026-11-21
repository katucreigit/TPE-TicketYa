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

Cada microservicio tiene su propia base PostgreSQL y no comparte tablas con los demás.
Los endpoints `/internal/**` se usan solo entre servicios y el gateway no los expone.
El detalle de diseño está en [`docs/arquitectura.md`](docs/arquitectura.md).

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

Levanta 9 contenedores: las 4 bases PostgreSQL (5433, 5434, 5435 y 5436), `ms-eventos` (8081), `ms-reservas` (8082), `ms-ventas` (8083), `ms-auth` (8084) y el gateway (8080). Cada servicio se construye con su propio `Dockerfile` (build con Maven y ejecución con JRE 21), así que no hace falta tener Maven ni Java instalados.

- Antes de levantarlo, detener los servicios que estén corriendo desde el IDE para que no choquen los puertos.
- Cada microservicio espera a que su base esté lista y el gateway arranca al final. Los servicios tardan unos segundos en iniciar: conviene esperar a que `docker compose logs -f` muestre `Started` en los cinco antes de usar la API.
- Dentro de compose los servicios se comunican por nombre de servicio (`http://ms-eventos:8081`, `db-eventos:5432`, etc.), configurado con las variables de la tabla de [Configuración](#configuración).
- `docker compose down` detiene todo y conserva los datos; `docker compose down -v` borra también las bases, para empezar de cero.

Cada imagen también se puede construir por separado, por ejemplo:

```
docker build -t ticketya/ms-eventos ms-eventos
```

### Servicios desde el IDE

1. Levantar solo las bases de datos (una por servicio):

   ```
   docker compose up -d db-eventos db-reservas db-ventas db-auth
   ```

   Esto crea cuatro bases PostgreSQL 16 en los puertos 5433, 5434, 5435 y 5436 (usuario y contraseña `ticketya`).

2. Iniciar cada servicio en una terminal distinta, desde la raíz del repositorio:

   ```
   cd ms-eventos   && mvn spring-boot:run
   cd ms-reservas  && mvn spring-boot:run
   cd ms-ventas    && mvn spring-boot:run
   cd ms-auth      && mvn spring-boot:run
   cd api-gateway  && mvn spring-boot:run
   ```

   Conviene iniciar el gateway al final. También se puede ejecutar cada proyecto desde el IDE.

3. Usar la API a través del gateway: `http://localhost:8080/api`.

Las tablas se crean automáticamente al iniciar cada servicio.

### Configuración

Los valores por defecto sirven para ejecución local desde el IDE. Se pueden cambiar con variables de entorno; `docker-compose.yml` las define para que apunten a los nombres de servicio:

| Variable | Servicio | Valor por defecto |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | todos los ms | `localhost` en el puerto de su base, usuario `ticketya` |
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

## Pruebas

Cada servicio se prueba por separado:

```
cd ms-auth     && mvn test
cd ms-eventos  && mvn test
cd ms-reservas && mvn test
cd ms-ventas   && mvn test
cd api-gateway && mvn test
```

La prueba de concurrencia de `ms-reservas` lanza solicitudes simultáneas por el mismo lugar y verifica que exactamente una resulte exitosa.

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
- Para probar sin registrarse, usar los datos de ejemplo de la colección Postman (ya incluyen el flujo de login).

## Colección de requests (Postman)

En [`postman/TicketYa.postman_collection.json`](postman/TicketYa.postman_collection.json) hay una colección agrupada por recurso:

- La carpeta **Auth** contiene los requests de registro y login. El token se guarda automáticamente en una variable de entorno y se usa en el resto de los requests.
- La carpeta **Flujo completo (en orden)** recorre el caso de uso completo: registrarse, crear lugar y evento, publicar, reservar, pagar, ver entradas, solicitar y aprobar una devolución.
- Hay carpetas para casos de error y para los endpoints internos entre servicios.

Para usarla: importarla en Postman, levantar los 5 servicios (desde el IDE o con `docker compose up -d --build`) y ejecutar la carpeta con el Runner. Funciona igual contra los contenedores, porque el gateway se publica en el mismo puerto 8080. Para repetir el flujo desde cero con compose: `docker compose down -v` y volver a levantar.

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
docs/              Historias de usuario, arquitectura, modelo de datos y OpenAPI
openspec/          Especificaciones y cambios
postman/           Colección de requests
docker-compose.yml Sistema completo: bases PostgreSQL, microservicios y gateway
AGENTS.md          Convenciones del proyecto
```
