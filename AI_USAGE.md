# AI Usage

## 1. Herramientas de IA utilizadas

Durante el desarrollo del proyecto se utilizó **ChatGPT** como herramienta de asistencia técnica.

La IA se utilizó principalmente para:

* Analizar alternativas de diseño.
* Revisar decisiones de arquitectura.
* Generar borradores de código.
* Revisar código Java, SQL y Vue.
* Proponer casos de prueba.
* Ayudar con configuraciones de Docker y Docker Compose.
* Revisar documentación técnica.
* Identificar posibles problemas de concurrencia, transaccionalidad e idempotencia.

La IA fue utilizada como herramienta de apoyo y no como sustituto de la validación técnica. Las propuestas fueron revisadas, ejecutadas y modificadas durante el desarrollo.

---

## 2. Partes del proyecto donde se utilizó IA

### Backend

Se utilizó IA como apoyo para diseñar y revisar:

* Estructura modular del proyecto Spring Boot.
* DTOs y validaciones.
* Controladores REST.
* Manejo global de excepciones.
* Servicios de transferencia.
* Integración de JPA y JDBC.
* Configuración de OpenAPI/Swagger.
* Configuración de CORS.
* Retry del procesamiento SPI.

La implementación final fue adaptada al código y a las necesidades específicas del desafío.

### Base de datos

La IA ayudó a analizar y diseñar:

* Estructura de las tablas `cuentas` y `transferencias`.
* Restricciones de integridad.
* Índices.
* Función `procesar_transferencia`.
* Función `marcar_transferencia_exitosa`.
* Función `compensar_transferencia`.
* Estrategia de bloqueo de cuentas.
* Manejo de idempotencia mediante `UNIQUE` y `ON CONFLICT`.

Estas funciones fueron ejecutadas y verificadas directamente sobre PostgreSQL.

### Frontend

La IA se utilizó como apoyo para:

* Estructura inicial de la SPA.
* Componentes Vue.
* Formulario de transferencia.
* Estados de carga y error.
* Historial de transferencias.
* Integración con Axios.
* Generación de `X-Idempotency-Key`.
* Diseño de la interfaz utilizando Bootstrap.

El frontend fue ejecutado y probado contra el backend real.

### Docker

La IA ayudó con:

* Dockerfiles para backend y frontend.
* Configuración de Nginx.
* Docker Compose.
* Comunicación entre los contenedores.
* Configuración de PostgreSQL.
* Configuración de variables de entorno.
* Configuración de CORS entre frontend y backend.

La configuración final fue probada mediante:

```text
docker compose up --build -d
```

verificando que los tres servicios quedaran funcionando correctamente.

### Postman

La IA ayudó a estructurar la colección de Postman y los scripts de prueba para validar:

* Health check.
* Transferencias exitosas.
* Idempotencia.
* Fondos insuficientes.
* Cuenta inexistente.
* Cuentas iguales.
* Monto inválido.
* Consulta de transferencias.

Las solicitudes y pruebas fueron ejecutadas manualmente y verificadas contra la implementación real.

### Documentación

La IA también fue utilizada como apoyo para estructurar:

* `README.md`
* `AI_USAGE.md`

La documentación fue revisada y adaptada al estado final del proyecto.

---

# 3. Caso donde una propuesta de IA fue corregida

Durante el diseño inicial del procesamiento de transferencias se consideró una estrategia en la que la transacción de base de datos permanecía abierta mientras el backend realizaba la llamada al SPI externo.

El enfoque inicialmente propuesto conceptualmente era:

```text
BEGIN TRANSACTION
    Registrar transferencia
    Debitar cuenta origen
    Acreditar cuenta destino

    Llamar SPI externo

    Si SPI OK:
        marcar EXITOSA
    Si SPI falla:
        rollback
COMMIT
```

Después de analizar el comportamiento de una llamada externa dentro de una transacción, se identificó un problema importante.

El SPI externo no forma parte de la transacción de PostgreSQL. Por lo tanto, mantener abierta la transacción mientras se espera la respuesta del SPI puede provocar:

* Locks mantenidos durante más tiempo.
* Conexiones de base de datos ocupadas.
* Mayor tiempo de transacción.
* Mayor impacto ante latencia o indisponibilidad del servicio externo.
* Dificultades para garantizar una coordinación real entre ambos sistemas.

Por este motivo, esta propuesta fue descartada.

La implementación final utiliza:

```text
Transacción interna
        │
        ├── Crear PENDIENTE
        ├── Debitar origen
        ├── Acreditar destino
        │
        └── COMMIT
              │
              ▼
          SPI externo
              │
        ┌─────┴─────┐
        │           │
       OK         ERROR
        │           │
        ▼           ▼
    EXITOSA       RETRY
                    │
              ┌─────┴─────┐
              │           │
             OK          ERROR
              │           │
              ▼           ▼
          EXITOSA    COMPENSACIÓN
                          │
                          ▼
                      RECHAZADA
```

La compensación se ejecuta posteriormente en una transacción independiente utilizando:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void compensateTransfer(Long transferId) {
    // ...
}
```

Esta modificación permitió separar correctamente la transacción interna del sistema de la comunicación con el sistema externo simulado.

---

# 4. Otro ejemplo de validación y corrección

Durante el desarrollo también se utilizaron propuestas generadas por IA para la configuración y ejecución del proyecto.

Las propuestas no fueron asumidas automáticamente como correctas. Por ejemplo, durante la configuración del entorno se encontraron diferencias entre:

* El entorno local.
* El entorno Docker.
* La resolución de nombres entre contenedores.
* La URL utilizada por el navegador para acceder al backend.

La configuración final distingue correctamente ambos contextos:

```text
Backend → PostgreSQL
jdbc:postgresql://postgres:5432/mini_spi
```

mientras que el navegador utiliza:

```text
http://localhost:8080
```

Esto es necesario porque `postgres` y otros nombres de servicios corresponden a la red interna de Docker, mientras que el navegador se ejecuta fuera de esa red.

---

# 5. Validación humana

Las respuestas generadas mediante IA fueron consideradas propuestas iniciales y fueron sometidas a validación mediante:

* Compilación del proyecto.
* Ejecución de pruebas unitarias.
* Ejecución de PostgreSQL.
* Pruebas de concurrencia.
* Pruebas mediante Postman.
* Ejecución del frontend.
* Ejecución mediante Docker Compose.
* Revisión manual del código.
* Verificación de los resultados obtenidos.

Cuando una propuesta no coincidió con el comportamiento real del sistema, fue modificada o descartada.

---

# 6. Principio utilizado

La IA se utilizó como **asistente de desarrollo**, principalmente para acelerar tareas de análisis, implementación y documentación.

Las decisiones finales de diseño, integración, pruebas y corrección del sistema fueron realizadas y validadas durante el desarrollo del proyecto.

En particular, las decisiones relacionadas con:

* Consistencia transaccional.
* Idempotencia.
* Concurrencia.
* Retry.
* Compensación.
* Integración con PostgreSQL.
* Configuración de Docker.

fueron verificadas contra el comportamiento real de la aplicación antes de considerarse parte de la solución final.
