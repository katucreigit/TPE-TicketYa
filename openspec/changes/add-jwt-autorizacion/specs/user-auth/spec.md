# Spec Delta

## Purpose

Define la autenticación de solicitudes mediante JWT y la autorización basada en roles para proteger los endpoints del sistema y establecer una identidad confiable entre el API Gateway y los microservicios.

## ADDED Requirements

### Requirement: Autenticación mediante JWT
El sistema MUST exigir un token JWT válido para acceder a los endpoints protegidos.

#### Scenario: Solicitud sin token
- **WHEN** un cliente solicita un endpoint protegido sin enviar un token JWT
- **THEN** el sistema rechaza la solicitud con HTTP 401

#### Scenario: Token inválido
- **WHEN** un cliente solicita un endpoint protegido enviando un token JWT inválido
- **THEN** el sistema rechaza la solicitud con HTTP 401

#### Scenario: Token vencido
- **WHEN** un cliente solicita un endpoint protegido enviando un token JWT vencido
- **THEN** el sistema rechaza la solicitud con HTTP 401

#### Scenario: Token válido
- **WHEN** un cliente solicita un endpoint protegido enviando un token JWT válido
- **THEN** el sistema permite continuar con la evaluación de permisos de la solicitud

### Requirement: Identificación del usuario autenticado
El sistema MUST obtener la identidad y el rol del usuario a partir de la información autenticada del token JWT.

#### Scenario: Identificación correcta
- **WHEN** una solicitud contiene un token JWT válido con identidad y rol
- **THEN** el sistema identifica al usuario y su rol a partir de ese token

#### Scenario: Headers de identidad manipulados
- **WHEN** una solicitud contiene un token JWT válido y headers de identidad que indican un usuario o rol diferente
- **THEN** el sistema utiliza la identidad y el rol autenticados del token y no los valores manipulados de los headers

### Requirement: Autorización según roles
El sistema MUST permitir el acceso a cada endpoint únicamente a los roles autorizados para esa operación: CLIENTE, ORGANIZADOR, ADMIN y PERSONAL_ACCESO.

#### Scenario: Rol autorizado
- **WHEN** un usuario autenticado solicita un endpoint para el cual su rol tiene permisos
- **THEN** el sistema permite ejecutar la operación

#### Scenario: Rol no autorizado
- **WHEN** un usuario autenticado solicita un endpoint para el cual su rol no tiene permisos
- **THEN** el sistema rechaza la solicitud con HTTP 403

### Requirement: Propagación segura de identidad
El sistema MUST conservar la identidad y el rol autenticados al propagar una solicitud desde el API Gateway hacia los microservicios, sin permitir que el cliente suplante esos datos.

#### Scenario: Solicitud autenticada hacia un microservicio
- **WHEN** el API Gateway deriva a un microservicio una solicitud con un token JWT válido
- **THEN** el microservicio recibe la identidad y el rol correspondientes al usuario autenticado

#### Scenario: Intento de suplantación de identidad
- **WHEN** un cliente envía datos de identidad distintos de los correspondientes a su token JWT
- **THEN** esos datos no modifican la identidad ni los permisos reconocidos por los microservicios
