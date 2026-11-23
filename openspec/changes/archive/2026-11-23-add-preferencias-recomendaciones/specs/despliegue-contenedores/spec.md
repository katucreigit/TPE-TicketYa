# Spec Delta

## MODIFIED Requirements

### Requirement: Imagen de contenedor por servicio
Cada servicio (`api-gateway`, `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`, `ms-notificaciones`, `ms-fila`, `ms-recomendaciones`) SHALL poder construirse como una imagen de contenedor propia a partir de su directorio, sin requerir Maven ni un JDK instalados en la máquina anfitriona, y la imagen SHALL ejecutar el servicio sobre Java 21.

#### Scenario: Construir la imagen de un servicio por separado
- **WHEN** se construye la imagen de un servicio usando solo su directorio como contexto
- **THEN** la construcción termina sin errores y produce una imagen que contiene el servicio compilado

#### Scenario: Ejecutar la imagen de un servicio por separado
- **WHEN** se ejecuta la imagen de un microservicio indicando por variables de entorno una base accesible (PostgreSQL, MongoDB en el caso de `ms-notificaciones` y `ms-recomendaciones` o Redis en el caso de `ms-fila`)
- **THEN** el servicio arranca, escucha en su puerto habitual y responde solicitudes

#### Scenario: La imagen final no incluye herramientas de build
- **WHEN** se inspecciona la imagen final de un servicio
- **THEN** contiene un entorno de ejecución Java 21 y el artefacto del servicio, sin Maven ni el código fuente

### Requirement: Levantamiento del sistema con un solo comando
El sistema completo (cuatro bases PostgreSQL, una base MongoDB, un Redis, siete microservicios y el gateway) SHALL levantarse con un único comando de Docker Compose ejecutado desde la raíz del repositorio, sin pasos manuales previos.

#### Scenario: Levantar el sistema completo
- **WHEN** se ejecuta `docker compose up -d --build` en la raíz del repositorio con Docker disponible
- **THEN** quedan en ejecución los catorce contenedores: `db-eventos`, `db-reservas`, `db-ventas`, `db-auth`, `mongo`, `redis`, `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`, `ms-notificaciones`, `ms-fila`, `ms-recomendaciones` y `api-gateway`

#### Scenario: Detener el sistema completo
- **WHEN** se ejecuta `docker compose down` en la raíz del repositorio
- **THEN** se detienen y eliminan los catorce contenedores, conservando los volúmenes de datos de las bases

### Requirement: Orden de arranque
Cada microservicio SHALL arrancar solo cuando su base de datos informe estar saludable, y el gateway SHALL arrancar después de `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`, `ms-fila` y `ms-recomendaciones`.

#### Scenario: Microservicio espera a su base
- **WHEN** se levanta el sistema y la base de un microservicio todavía no acepta conexiones
- **THEN** ese microservicio no se inicia hasta que el healthcheck de su base resulte saludable

#### Scenario: ms-notificaciones espera a MongoDB
- **WHEN** se levanta el sistema y el contenedor `mongo` todavía no responde a su healthcheck
- **THEN** `ms-notificaciones` no se inicia hasta que `mongo` resulte saludable

#### Scenario: ms-recomendaciones espera a MongoDB
- **WHEN** se levanta el sistema y el contenedor `mongo` todavía no responde a su healthcheck
- **THEN** `ms-recomendaciones` no se inicia hasta que `mongo` resulte saludable

#### Scenario: ms-fila espera a Redis
- **WHEN** se levanta el sistema y el contenedor `redis` todavía no responde a su healthcheck
- **THEN** `ms-fila` no se inicia hasta que `redis` resulte saludable

#### Scenario: ms-reservas no depende de ms-fila para arrancar
- **WHEN** se levanta el sistema
- **THEN** `ms-reservas` se inicia sin esperar a `ms-fila`

#### Scenario: ms-ventas no depende de ms-recomendaciones para arrancar
- **WHEN** se levanta el sistema
- **THEN** `ms-ventas` se inicia sin esperar a `ms-recomendaciones`

#### Scenario: Gateway arranca al final
- **WHEN** se levanta el sistema
- **THEN** el contenedor del gateway se inicia después de que se hayan iniciado los contenedores de `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`, `ms-fila` y `ms-recomendaciones`

### Requirement: Comunicación por nombre de servicio
Dentro del entorno de Compose, los servicios SHALL comunicarse entre sí y con sus bases usando el nombre de servicio de Compose, configurado mediante `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MONGO_URI`, `REDIS_HOST`, `REDIS_PORT`, `MS_EVENTOS_URL`, `MS_RESERVAS_URL`, `MS_VENTAS_URL`, `MS_AUTH_URL`, `MS_NOTIFICACIONES_URL`, `MS_FILA_URL` y `MS_RECOMENDACIONES_URL`.

#### Scenario: Gateway enruta a los microservicios en contenedores
- **WHEN** un cliente envía una solicitud a `http://localhost:8080/api/eventos` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-eventos` por su nombre de servicio y devuelve su respuesta

#### Scenario: Gateway enruta la autenticación a ms-auth
- **WHEN** un cliente envía una solicitud a `http://localhost:8080/api/auth/login` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-auth` por su nombre de servicio y devuelve su respuesta

#### Scenario: Gateway enruta la fila a ms-fila
- **WHEN** un cliente autenticado envía una solicitud a `http://localhost:8080/api/fila/eventos/42/posicion` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-fila` en el puerto `8086` mediante `MS_FILA_URL` y devuelve su respuesta

#### Scenario: Gateway enruta las recomendaciones a ms-recomendaciones
- **WHEN** un cliente autenticado envía una solicitud a `http://localhost:8080/api/recomendaciones` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-recomendaciones` en el puerto `8087` mediante `MS_RECOMENDACIONES_URL` y devuelve su respuesta

#### Scenario: Llamada interna entre microservicios
- **WHEN** un microservicio en Compose invoca un endpoint interno de otro microservicio
- **THEN** la llamada se dirige al nombre de servicio del destino y no a `localhost`

#### Scenario: ms-ventas se comunica con ms-notificaciones en Compose
- **WHEN** `ms-ventas` arranca en Compose y emite una notificación hacia `ms-notificaciones`
- **THEN** la solicitud se dirige al servicio `ms-notificaciones` en el puerto `8085` mediante `MS_NOTIFICACIONES_URL` y no a `localhost`

#### Scenario: ms-ventas avisa compras a ms-recomendaciones en Compose
- **WHEN** `ms-ventas` en Compose confirma una compra
- **THEN** el aviso se dirige al servicio `ms-recomendaciones` en el puerto `8087` mediante `MS_RECOMENDACIONES_URL` y no a `localhost`

#### Scenario: ms-recomendaciones consulta el catálogo en Compose
- **WHEN** `ms-recomendaciones` en Compose necesita el catálogo de eventos
- **THEN** la consulta se dirige al servicio `ms-eventos` en el puerto `8081` mediante `MS_EVENTOS_URL` y no a `localhost`

#### Scenario: ms-reservas consulta a ms-fila en Compose
- **WHEN** `ms-reservas` en Compose recibe un pedido de reserva
- **THEN** la consulta de estado se dirige al servicio `ms-fila` en el puerto `8086` mediante `MS_FILA_URL` y no a `localhost`

#### Scenario: Microservicio se conecta a su propia base
- **WHEN** un microservicio arranca en Compose
- **THEN** se conecta a la base de su servicio de Compose por el puerto interno 5432

#### Scenario: ms-notificaciones se conecta a MongoDB
- **WHEN** `ms-notificaciones` arranca en Compose
- **THEN** se conecta por `MONGO_URI` a la base `db_notificaciones` del servicio `mongo` en el puerto interno 27017

#### Scenario: ms-recomendaciones se conecta a MongoDB
- **WHEN** `ms-recomendaciones` arranca en Compose
- **THEN** se conecta por `MONGO_URI` a la base `db_recomendaciones` del servicio `mongo` en el puerto interno 27017

#### Scenario: ms-fila se conecta a Redis
- **WHEN** `ms-fila` arranca en Compose
- **THEN** se conecta por `REDIS_HOST` y `REDIS_PORT` al servicio `redis` en el puerto interno 6379

### Requirement: Compatibilidad con la ejecución local
Cuando las variables de conexión no estén definidas, cada servicio SHALL usar `localhost` con los puertos publicados como valor por defecto, de modo que la ejecución desde el IDE siga funcionando sin configuración adicional.

#### Scenario: Ejecutar un servicio desde el IDE
- **WHEN** solo las bases corren en Compose y un servicio se ejecuta desde el IDE sin variables de entorno
- **THEN** el servicio se conecta a su base en `localhost` (5433, 5434, 5435, 5436, 27017 o 6379) y a los demás servicios en `localhost` (8081, 8082, 8083, 8084, 8085, 8086 o 8087)

#### Scenario: Levantar solo las bases
- **WHEN** se ejecuta `docker compose up -d db-eventos db-reservas db-ventas db-auth mongo redis`
- **THEN** se levantan únicamente las seis bases, sin construir ni iniciar los servicios

### Requirement: Puertos publicados
El entorno de Compose SHALL publicar en la máquina anfitriona los mismos puertos usados en la ejecución local: 8080 para el gateway; 8081, 8082, 8083, 8084, 8085, 8086 y 8087 para `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`, `ms-notificaciones`, `ms-fila` y `ms-recomendaciones`; 5433, 5434, 5435 y 5436 para sus bases PostgreSQL, 27017 para MongoDB y 6379 para Redis.

#### Scenario: Acceso a los puertos desde el anfitrión
- **WHEN** el sistema está levantado en Compose
- **THEN** el gateway responde en `localhost:8080`, cada microservicio en su puerto 8081–8087, cada base PostgreSQL acepta conexiones en su puerto 5433–5436, MongoDB en `localhost:27017` y Redis en `localhost:6379`
