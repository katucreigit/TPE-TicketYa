# Spec Delta

## MODIFIED Requirements

### Requirement: Imagen de contenedor por servicio
Cada servicio (`api-gateway`, `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth`) SHALL poder construirse como una imagen de contenedor propia a partir de su directorio, sin requerir Maven ni un JDK instalados en la máquina anfitriona, y la imagen SHALL ejecutar el servicio sobre Java 21.

#### Scenario: Construir la imagen de un servicio por separado
- **WHEN** se construye la imagen de un servicio usando solo su directorio como contexto
- **THEN** la construcción termina sin errores y produce una imagen que contiene el servicio compilado

#### Scenario: Ejecutar la imagen de un servicio por separado
- **WHEN** se ejecuta la imagen de un microservicio indicando por variables de entorno una base PostgreSQL accesible
- **THEN** el servicio arranca, escucha en su puerto habitual y responde solicitudes

#### Scenario: La imagen final no incluye herramientas de build
- **WHEN** se inspecciona la imagen final de un servicio
- **THEN** contiene un entorno de ejecución Java 21 y el artefacto del servicio, sin Maven ni el código fuente

### Requirement: Levantamiento del sistema con un solo comando
El sistema completo (cuatro bases PostgreSQL, cuatro microservicios y el gateway) SHALL levantarse con un único comando de Docker Compose ejecutado desde la raíz del repositorio, sin pasos manuales previos.

#### Scenario: Levantar el sistema completo
- **WHEN** se ejecuta `docker compose up -d --build` en la raíz del repositorio con Docker disponible
- **THEN** quedan en ejecución los nueve contenedores: `db-eventos`, `db-reservas`, `db-ventas`, `db-auth`, `ms-eventos`, `ms-reservas`, `ms-ventas`, `ms-auth` y `api-gateway`

#### Scenario: Detener el sistema completo
- **WHEN** se ejecuta `docker compose down` en la raíz del repositorio
- **THEN** se detienen y eliminan los nueve contenedores, conservando los volúmenes de datos de las bases

### Requirement: Orden de arranque
Cada microservicio SHALL arrancar solo cuando su base de datos informe estar saludable, y el gateway SHALL arrancar después de los cuatro microservicios.

#### Scenario: Microservicio espera a su base
- **WHEN** se levanta el sistema y la base de un microservicio todavía no acepta conexiones
- **THEN** ese microservicio no se inicia hasta que el healthcheck de su base resulte saludable

#### Scenario: Gateway arranca al final
- **WHEN** se levanta el sistema
- **THEN** el contenedor del gateway se inicia después de que se hayan iniciado los contenedores de `ms-eventos`, `ms-reservas`, `ms-ventas` y `ms-auth`

### Requirement: Comunicación por nombre de servicio
Dentro del entorno de Compose, los servicios SHALL comunicarse entre sí y con sus bases usando el nombre de servicio de Compose, configurado mediante `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MS_EVENTOS_URL`, `MS_RESERVAS_URL`, `MS_VENTAS_URL` y `MS_AUTH_URL`.

#### Scenario: Gateway enruta a los microservicios en contenedores
- **WHEN** un cliente envía una solicitud a `http://localhost:8080/api/eventos` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-eventos` por su nombre de servicio y devuelve su respuesta

#### Scenario: Gateway enruta la autenticación a ms-auth
- **WHEN** un cliente envía una solicitud a `http://localhost:8080/api/auth/login` con el sistema levantado en Compose
- **THEN** el gateway la reenvía al contenedor `ms-auth` por su nombre de servicio y devuelve su respuesta

#### Scenario: Llamada interna entre microservicios
- **WHEN** un microservicio en Compose invoca un endpoint interno de otro microservicio
- **THEN** la llamada se dirige al nombre de servicio del destino y no a `localhost`

#### Scenario: Microservicio se conecta a su propia base
- **WHEN** un microservicio arranca en Compose
- **THEN** se conecta a la base de su servicio de Compose por el puerto interno 5432

### Requirement: Compatibilidad con la ejecución local
Cuando las variables de conexión no estén definidas, cada servicio SHALL usar `localhost` con los puertos publicados como valor por defecto, de modo que la ejecución desde el IDE siga funcionando sin configuración adicional.

#### Scenario: Ejecutar un servicio desde el IDE
- **WHEN** solo las bases corren en Compose y un servicio se ejecuta desde el IDE sin variables de entorno
- **THEN** el servicio se conecta a su base en `localhost` (5433, 5434, 5435 o 5436) y a los demás servicios en `localhost` (8081, 8082, 8083 o 8084)

#### Scenario: Levantar solo las bases
- **WHEN** se ejecuta `docker compose up -d db-eventos db-reservas db-ventas db-auth`
- **THEN** se levantan únicamente las cuatro bases, sin construir ni iniciar los servicios

### Requirement: Puertos publicados
El entorno de Compose SHALL publicar en la máquina anfitriona los mismos puertos usados en la ejecución local: 8080 para el gateway, 8081, 8082, 8083 y 8084 para `ms-eventos`, `ms-reservas`, `ms-ventas` y `ms-auth`, y 5433, 5434, 5435 y 5436 para sus bases.

#### Scenario: Acceso a los puertos desde el anfitrión
- **WHEN** el sistema está levantado en Compose
- **THEN** el gateway responde en `localhost:8080`, cada microservicio en su puerto 8081–8084 y cada base acepta conexiones en su puerto 5433–5436

## ADDED Requirements

### Requirement: Secreto JWT compartido
En Compose, el gateway y `ms-auth` SHALL recibir el mismo secreto JWT por la variable `JWT_SECRET`, de modo que el gateway acepte los tokens que emite `ms-auth`. Si la variable no está definida, ambos SHALL usar la misma clave de desarrollo de sus `application.yml`. El repositorio no SHALL contener un secreto distinto del de desarrollo.

#### Scenario: Token emitido por ms-auth aceptado por el gateway
- **WHEN** con el sistema levantado en Compose un usuario registrado inicia sesión en `/api/auth/login` y usa el token recibido en un endpoint protegido a través del gateway
- **THEN** el gateway acepta el token y reenvía la solicitud al microservicio correspondiente

#### Scenario: Solicitud protegida sin token o con token inválido
- **WHEN** con el sistema levantado en Compose se llama a un endpoint protegido sin header `Authorization` o con un token que no fue firmado con el secreto compartido
- **THEN** el gateway responde 401

#### Scenario: Secreto definido fuera del repositorio
- **WHEN** se define `JWT_SECRET` en el entorno del shell o en un `.env` no versionado antes de levantar Compose
- **THEN** el gateway y `ms-auth` usan ese valor en lugar de la clave de desarrollo
