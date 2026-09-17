# Mini SPI - Simulador de Transferencias Inmediatas

Simulador de un sistema de transferencias inmediatas inspirado en el funcionamiento de un sistema de pagos interbancarios.

El proyecto implementa transferencias entre cuentas bancarias con:

* Control transaccional de saldos.
* Idempotencia mediante `X-Idempotency-Key`.
* Persistencia mediante PostgreSQL.
* Lógica crítica de débito, crédito y registro de transferencia en una función PL/pgSQL.
* Simulación de una red SPI externa con un 20% de probabilidad de fallo.
* Retry con backoff exponencial.
* Compensación ante fallo definitivo.
* API REST documentada con OpenAPI/Swagger.
* Frontend SPA desarrollado con Vue 3.
* Docker Compose para levantar todo el entorno con un único comando.
* Colección Postman con pruebas funcionales y de validación.

---

## Código fuente

El código fuente completo del proyecto se encuentra disponible en el siguiente repositorio:

**GitHub:** https://github.com/richar3990/mini-spi

El repositorio contiene:

* `backend/` — API REST desarrollada con Java y Spring Boot.
* `frontend/` — SPA desarrollada con Vue 3.
* `database/` — Scripts de inicialización y funciones PostgreSQL.
* `postman/` — Colección de pruebas de la API.
* `docker-compose.yml` — Orquestación del entorno completo.
* `AI_USAGE.md` — Documentación del uso de herramientas de IA durante el desarrollo.

---

## 1. Arquitectura

El proyecto utiliza una arquitectura monolítica modular, manteniendo separadas las responsabilidades principales sin introducir complejidad innecesaria para el alcance del desafío.

```text
                    ┌─────────────────────┐
                    │      Frontend       │
                    │      Vue 3 + Vite   │
                    └──────────┬──────────┘
                               │ HTTP
                               ▼
                    ┌─────────────────────┐
                    │       Backend       │
                    │ Java 21             │
                    │ Spring Boot 4.1.1   │
                    │ REST + Swagger      │
                    └──────────┬──────────┘
                               │
                  ┌────────────┴────────────┐
                  │                         │
                  ▼                         ▼
        ┌──────────────────┐     ┌──────────────────┐
        │    PostgreSQL    │     │    Simulated SPI │
        │                  │     │                  │
        │ Transactional DB │     │ 20% failure rate │
        │ PL/pgSQL         │     │ Retry + Backoff  │
        └──────────────────┘     └──────────────────┘
```

### Componentes

| Componente           | Tecnología              |
| -------------------- | ----------------------- |
| Backend              | Java 21                 |
| Framework            | Spring Boot 4.1.1       |
| API                  | REST                    |
| Documentación        | OpenAPI / Swagger       |
| Persistencia         | Spring Data JPA + JDBC  |
| Base de datos        | PostgreSQL 17           |
| Lógica transaccional | PL/pgSQL                |
| Frontend             | Vue 3 + Vite            |
| HTTP Frontend        | Axios                   |
| UI                   | Bootstrap               |
| Contenedores         | Docker + Docker Compose |
| Pruebas API          | Postman                 |
| Pruebas backend      | JUnit + Mockito         |

---

## 2. Estructura del proyecto

```text
mini-spi/
├── backend/
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
│
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── .dockerignore
│
├── database/
│   └── init.sql
│
├── postman/
│   └── Mini-SPI.postman_collection.json
│
├── docker-compose.yml
├── README.md
└── AI_USAGE.md
```

---

# 3. Ejecución con Docker

## Requisitos

Se necesita:

* Docker Desktop o Docker Engine con Docker Compose.
* Git.
* Copiar /frontend .env.example como /frontend/.env

No es necesario instalar Java, Maven, Node.js ni PostgreSQL para ejecutar el proyecto mediante Docker.

## Levantar todo

Desde la raíz del proyecto:

```bash
docker compose up --build -d
```

Este comando construye las imágenes del backend y frontend y levanta los tres servicios de la aplicación.

Los servicios utilizados son:

```text
mini-spi-postgres
mini-spi-backend
mini-spi-frontend
```

Docker Compose permite reconstruir las imágenes cuando cambia el código o la configuración y recrear los contenedores correspondientes manteniendo los volúmenes existentes.

## Verificar los contenedores

```bash
docker compose ps
```

PostgreSQL debe aparecer como:

```text
Up (healthy)
```

## Ver logs

Todos los servicios:

```bash
docker compose logs -f
```

Solo backend:

```bash
docker compose logs -f backend
```

Solo PostgreSQL:

```bash
docker compose logs -f postgres
```

## Detener la aplicación

```bash
docker compose down
```

Esto detiene y elimina los contenedores, pero mantiene el volumen de PostgreSQL.

## Reinicializar completamente la base de datos

Si se modifica `database/init.sql` y se necesita volver a ejecutar desde cero:

```bash
docker compose down -v
docker compose up --build -d
```

> **Importante:** `-v` elimina el volumen de PostgreSQL y, por lo tanto, todos los datos almacenados.

---

# 4. URLs

Con Docker levantado:

### Frontend

```text
http://localhost:5173
```

### Backend

```text
http://localhost:8080
```

### Swagger UI

```text
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON

```text
http://localhost:8080/v3/api-docs
```

### Health check

```text
http://localhost:8080/actuator/health
```

---

# 5. API

## Crear transferencia

```http
POST /api/v1/transferencias
```

Header obligatorio:

```http
Content-Type: application/json
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

Request:

```json
{
    "sourceAccount": "10000001",
    "destinationAccount": "10000002",
    "amount": 1000.00,
    "description": "Pago de servicios"
}
```

Respuesta exitosa:

```json
{
    "id": 1,
    "sourceAccount": "10000001",
    "destinationAccount": "10000002",
    "amount": 1000.00,
    "description": "Pago de servicios",
    "status": "EXITOSA",
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2026-09-17T10:30:00"
}
```

## Consultar transferencias

```http
GET /api/v1/transferencias
```

Devuelve las últimas transferencias registradas.

Los estados posibles son:

```text
PENDIENTE
EXITOSA
RECHAZADA
```

---

# 6. Idempotencia

Cada transferencia requiere el header:

```http
X-Idempotency-Key
```

La clave identifica de manera única una solicitud de transferencia.

Si el cliente realiza nuevamente la misma solicitud utilizando la misma clave:

```text
Primera solicitud
       │
       ▼
Crear transferencia
       │
       ▼
Procesar débito/crédito
       │
       ▼
Transferencia #10
```

Una segunda solicitud con la misma clave:

```text
Segunda solicitud
       │
       ▼
X-Idempotency-Key existente
       │
       ▼
Retornar transferencia #10
       │
       ▼
NO volver a descontar fondos
```

La base de datos posee una restricción `UNIQUE` sobre `idempotency_key`.

Además, la función de procesamiento utiliza:

```sql
ON CONFLICT (idempotency_key) DO NOTHING
```

Esto evita depender exclusivamente de una validación previa en Java y protege también frente a solicitudes concurrentes.

---

# 7. Consistencia transaccional

La operación crítica de transferencia se encuentra implementada en PostgreSQL mediante la función:

```sql
procesar_transferencia(...)
```

Esta función realiza:

1. Registro de la transferencia en estado `PENDIENTE`.
2. Bloqueo de las cuentas involucradas.
3. Validación de existencia.
4. Validación de fondos suficientes.
5. Débito de la cuenta origen.
6. Crédito de la cuenta destino.

Las operaciones de débito y crédito forman parte de la misma transacción de base de datos.

La función bloquea las cuentas en un orden determinístico basado en sus IDs:

```text
cuenta con menor ID
        ↓
cuenta con mayor ID
```

Esto reduce el riesgo de deadlocks cuando existen transferencias concurrentes entre las mismas cuentas.

Además, la tabla `cuentas` posee una restricción:

```sql
CHECK (saldo >= 0)
```

como protección adicional contra saldos negativos.

---

# 8. Procesamiento SPI

Después de completar la transacción interna, el backend simula la comunicación con una red SPI externa.

La simulación utiliza una probabilidad fija de fallo del:

```text
20%
```

Por lo tanto, aproximadamente:

```text
80% → éxito
20% → fallo
```

El flujo implementado es:

```text
              ┌──────────────┐
              │   PENDIENTE  │
              └───────┬──────┘
                      │
                      ▼
                Simular SPI
                      │
              ┌───────┴───────┐
              │               │
             OK             ERROR
              │               │
              ▼               ▼
          EXITOSA          Retry
                              │
                         ┌────┴────┐
                         │         │
                        OK       ERROR
                         │         │
                         ▼         ▼
                     EXITOSA   Compensación
                                    │
                                    ▼
                                RECHAZADA
```

---

# 9. Retry

Ante un fallo del SPI se realiza un retry automático.

Configuración:

```properties
spi.retry.max-attempts=3
spi.retry.initial-delay-ms=200
```

Se utiliza backoff exponencial:

```text
Intento 1 → 200 ms
Intento 2 → 400 ms
Intento 3 → 800 ms
```

Si alguno de los intentos tiene éxito, la transferencia pasa a:

```text
EXITOSA
```

Si todos los intentos fallan, se ejecuta el proceso de compensación.

---

# 10. Compensación

La llamada al SPI externo no se mantiene dentro de la transacción de base de datos.

El flujo es:

```text
Transacción interna
       │
       ├── Registrar PENDIENTE
       ├── Debitar origen
       ├── Acreditar destino
       │
       └── COMMIT
              │
              ▼
          Llamada SPI
              │
              ▼
        Fallos definitivos
              │
              ▼
       Nueva transacción
              │
              ├── Devolver monto al origen
              ├── Descontar monto del destino
              └── Marcar RECHAZADA
```

La compensación utiliza una transacción independiente mediante:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void compensateTransfer(Long transferId) {
    // ...
}
```

Esto evita mantener una conexión y locks de base de datos durante la comunicación con un sistema externo.

---

# 11. Base de datos

La base utiliza PostgreSQL.

## Tabla `cuentas`

```text
id
numero_cuenta
saldo
created_at
updated_at
```

## Tabla `transferencias`

```text
id
cuenta_origen
cuenta_destino
monto
concepto
estado
idempotency_key
created_at
updated_at
```

## Estados

```text
PENDIENTE
EXITOSA
RECHAZADA
```

## Datos iniciales

La base incluye cuentas de prueba:

| Cuenta   | Saldo inicial |
| -------- | ------------: |
| 10000001 |  1,000,000.00 |
| 10000002 |    500,000.00 |
| 10000003 |    250,000.00 |
| 10000004 |    100,000.00 |

---

# 12. Frontend

El frontend es una SPA desarrollada con Vue 3.

Incluye:

* Formulario de transferencia.
* Validaciones.
* Estado de carga.
* Mensajes de éxito y error.
* Generación de `X-Idempotency-Key` mediante `crypto.randomUUID()`.
* Historial de transferencias.
* Estado visual de las transferencias.
* Actualización del historial después de una transferencia.

El frontend se sirve mediante Nginx dentro de Docker.

La API utilizada por el navegador es:

```text
http://localhost:8080
```

Esto es intencional.

Dentro de Docker, el backend se conecta a PostgreSQL utilizando el nombre del servicio:

```text
postgres
```

Por ejemplo:

```text
jdbc:postgresql://postgres:5432/mini_spi
```

Pero el navegador no puede utilizar:

```text
http://backend:8080
```

porque esa resolución corresponde a la red interna de Docker. Por eso el frontend utiliza:

```text
http://localhost:8080
```

---

# 13. CORS

El backend permite solicitudes provenientes del frontend:

```text
http://localhost:5173
```

La configuración utiliza la propiedad:

```properties
app.cors.allowed-origins=http://localhost:5173
```

y permite:

```text
GET
POST
OPTIONS
```

con los headers necesarios para la API:

```text
Content-Type
X-Idempotency-Key
```

---

# 14. Postman

La colección se encuentra en:

```text
postman/Mini-SPI.postman_collection.json
```

Incluye pruebas para:

* Health check.
* Transferencia exitosa.
* Idempotencia.
* Fondos insuficientes.
* Cuenta inexistente.
* Cuenta origen y destino iguales.
* Monto inválido.
* Consulta de transferencias.

La colección contiene scripts de prueba para validar automáticamente códigos HTTP y estructuras básicas de las respuestas.

# 15. Pruebas

El backend cuenta con tests unitarios y de integración para validar los principales escenarios de negocio.

Cobertura actual:

- Transferencia exitosa
- Fondos insuficientes y rollback
- Idempotencia
- Compensación de transferencias
- Retry ante fallos del SPI
- Persistencia y funciones transaccionales de PostgreSQL
- Integración contra PostgreSQL mediante Testcontainers

Resultado actual:

11 tests ejecutados
11 tests exitosos
0 fallos
0 errores

Para ejecutar los tests:

cd backend
mvn clean test

# 16. Decisiones técnicas

## ¿Por qué PostgreSQL?

PostgreSQL permite implementar la lógica transaccional requerida mediante PL/pgSQL y proporciona mecanismos de bloqueo de filas adecuados para proteger las cuentas durante una transferencia.

## ¿Por qué una función de base de datos?

El desafío requiere explícitamente que la operación crítica sea realizada mediante una función/procedimiento almacenado e invocada desde el backend.

Por este motivo, el backend utiliza JDBC para ejecutar:

```sql
procesar_transferencia(...)
```

mientras que JPA se mantiene para las consultas normales de entidades.

## ¿Por qué no mantener la transacción abierta durante el SPI?

Una llamada externa puede tardar, fallar o quedar temporalmente indisponible.

Mantener una transacción de base de datos abierta durante esa comunicación implicaría mantener conexiones y locks durante un proceso externo que no participa de la transacción de PostgreSQL.

Por eso se separan:

```text
Transacción interna
        ↓
COMMIT
        ↓
SPI externo
        ↓
Compensación si es necesario
```

## ¿Por qué no utilizar microservicios?

El alcance del desafío no requiere distribución entre servicios.

Introducir Kafka, Redis, Eureka, API Gateway u otros componentes agregaría complejidad operacional sin aportar un beneficio proporcional al objetivo del ejercicio.

Se optó por un monolito modular con responsabilidades claramente separadas.

---

# 17. Riesgos y limitaciones para producción

Este proyecto es un simulador y no pretende representar una implementación bancaria lista para producción.

Entre las principales limitaciones:

### SPI simulado

La comunicación con el SPI es simulada localmente mediante una probabilidad aleatoria de fallo.

En producción debería existir un cliente real con:

* Timeouts.
* Circuit breaker.
* Observabilidad.
* Autenticación.
* TLS/mTLS según el protocolo.
* Manejo de códigos de respuesta.
* Correlation IDs.

### Idempotencia

La implementación garantiza la idempotencia a nivel de base de datos para este flujo.

En un sistema distribuido real podrían ser necesarios mecanismos adicionales para gestionar solicitudes provenientes de múltiples instancias y diferentes componentes.

### Compensación

La compensación implementada funciona dentro del modelo simplificado del desafío.

Un sistema financiero real podría requerir un ledger contable, movimientos inmutables, auditoría y procesos de reconciliación.

### Seguridad

El proyecto no implementa autenticación/autorización bancaria real.

En producción deberían agregarse mecanismos como:

* OAuth2/OIDC.
* JWT o sesiones seguras.
* TLS.
* Gestión segura de secretos.
* Rate limiting.
* Auditoría.
* Controles antifraude.

### Observabilidad

Para producción sería recomendable incorporar:

* Logs estructurados.
* Métricas.
* Distributed tracing.
* Alertas.
* Correlation IDs.

### Base de datos

La configuración de PostgreSQL incluida en Docker está orientada al desarrollo y evaluación del desafío.

En producción deberían utilizarse:

* Secretos fuera del repositorio.
* Backups.
* Alta disponibilidad.
* Replicación según necesidades.
* Monitoreo.
* Políticas de recuperación ante desastres.

---

# 18. Variables de configuración

El backend utiliza propiedades para la configuración del entorno.

Principales propiedades:

```properties
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/mini_spi
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD

spi.retry.max-attempts=3
spi.retry.initial-delay-ms=200

app.cors.allowed-origins=http://localhost:5173
```

Cuando se ejecuta mediante Docker Compose, las propiedades de conexión son sobrescritas mediante variables de entorno para utilizar el servicio PostgreSQL de Docker.

---

# 19. Prueba rápida

Con los contenedores levantados:

```bash
docker compose ps
```

Luego acceder a:

```text
Frontend:
http://localhost:5173

Swagger:
http://localhost:8080/swagger-ui.html

Health:
http://localhost:8080/actuator/health
```

O ejecutar una transferencia desde Swagger/Postman.

Ejemplo:

```json
{
    "sourceAccount": "10000001",
    "destinationAccount": "10000002",
    "amount": 1000.00,
    "description": "Transferencia de prueba"
}
```

con:

```http
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

---

# 20. Entregables

El repositorio contiene:

* [x] Backend Java 21 + Spring Boot.
* [x] API REST.
* [x] Swagger/OpenAPI.
* [x] PostgreSQL.
* [x] Funciones PL/pgSQL.
* [x] Idempotencia.
* [x] Control transaccional.
* [x] Retry del SPI.
* [x] Compensación.
* [x] Frontend Vue 3.
* [x] Docker Compose.
* [x] Postman Collection.
* [x] AI_USAGE.md.
* [x] Pruebas de integración con Testcontainers.

---

# 21. Comando principal

Para levantar todo el sistema:

```bash
docker compose up --build -d
```

Para comprobar el estado:

```bash
docker compose ps
```

Para detenerlo:

```bash
docker compose down
```

Para eliminar también los datos de PostgreSQL:

```bash
docker compose down -v
```

---

## Autor

Richar Daniel Meza Silva

Desarrollador Fullstack.

GitHub: https://github.com/richar3990
