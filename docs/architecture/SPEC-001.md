# Architecture — SPEC-001 (Gestión de Clientes)

## 1. Feature

CRUD de clientes del banco (alta, consulta, edición, listado) sobre el módulo
`com.banco`, en un backend hexagonal + DDD construido desde cero (el backend es
un scaffold vacío: no hay `pom.xml`, código ni migraciones).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-001-clientes.md` (APROBADA — fuente de verdad; BR-001..BR-005,
  FR-001..FR-005, ERR-001..ERR-005, AC-001..AC-025, asunciones A-001..A-005).
- `docs/adr/ADR-001`, `ADR-002`, `ADR-003`, y el nuevo `ADR-004` (sección 11).

---

## 3. Affected Modules

- **`backend/`** — proyecto Maven completo (se crea desde cero): `pom.xml`,
  `application.yml`, clase principal, migración Flyway, código de dominio,
  aplicación e infraestructura, y tests.
- **`docs/adr/`** — nuevo `ADR-004` (autenticación mínima Sprint 1).
- **No afectado:** frontend, `docker/` (solo se usa el Postgres local existente).

---

## 4. Application Flow

```text
REST (ClienteController)                       infrastructure.adapter.web
        ↓  request DTOs (records, sin anotaciones de validación)
Security (JwtAuthenticationFilter → JwtService) infrastructure.security
        ↓  valida token HS256; si es válido, setea Authentication
Use Case (Crear/Actualizar/Obtener/Listar)     application.usecase
        ↓  valida con ClienteValidator (CoR) y chequear propiedad (Obtener)
Domain (Cliente, DNI, ClienteRepository port)  domain
        ↓
Persistence (ClienteRepositoryAdapter → JPA)   infrastructure.adapter.persistence
        ↓
PostgreSQL 16 (Flyway V1, constraints UNIQUE)  db
```

Flujo concreto del alta (`POST /api/v1/clientes`):

1. El filtro JWT valida el token (firma HS256 + expiración) y carga
   `AuthenticatedUser(rol, clienteId)` en el `SecurityContext`. Sin token o con
   token inválido → el `authenticationEntryPoint` responde `401` (ERR-005).
2. Spring Security autoriza por rol: `POST`/`PUT`/`GET /api/v1/clientes` solo
   `ADMIN`; `GET /api/v1/clientes/{id}` `ADMIN` o `CLIENTE`. Rol incorrecto →
   `accessDeniedHandler` responde `403` (ERR-003).
3. El controller construye el command/query y delega en el use case (sin
   reglas de negocio en el controller).
4. El use case normaliza (`trim` de nombre/apellido/email vía `DatosCliente`),
   ejecuta la cadena de validación (CoR), verifica unicidad vía el puerto
   `ClienteRepository`, construye el agregado `Cliente` con `fechaAlta` y lo
   persiste.
5. El controller mapea `Cliente` → `ClienteDto` y responde `201 Created`.

**Nota de transaccionalidad:** los use cases son Java puro (sin Spring). Las
operaciones se apoyan en la transacción por operación de Spring Data JPA y en
los constraints `UNIQUE` de la base como backstop ante race conditions (ver
§5.4). No se necesita `@Transactional` explícito en Sprint 1.

---

## 5. Components

### 5.1 Entry points / presentación — `infrastructure.adapter.web`

| Método | Ruta | Rol requerido | Status OK | Error esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/clientes` | `ADMIN` | `201 Created` + `Location` | 400, 401, 403, 409 |
| `GET` | `/api/v1/clientes/{id}` | `ADMIN` o `CLIENTE` (propio) | `200 OK` | 400, 401, 403, 404 |
| `PUT` | `/api/v1/clientes/{id}` | `ADMIN` | `200 OK` | 400, 401, 403, 404, 409 |
| `GET` | `/api/v1/clientes` | `ADMIN` | `200 OK` (lista ordenada por `id` asc) | 401, 403 |

Los DTOs de entrada son **records planos sin anotaciones de validación**: toda
regla de negocio (obligatoriedad, formatos, longitudes — BR-001..BR-005) se
valida en `application.validator` (CoR). Un JSON malformado o con tipo inválido
dispara `HttpMessageNotReadableException` → `400` (transporte, no negocio).

### 5.2 Aplicación — `application`

Cuatro use cases (orquestan dominio; Java puro, sin Spring):

- `CrearClienteUseCase.ejecutar(CrearClienteCommand)` → `Cliente`.
- `ActualizarClienteUseCase.ejecutar(ActualizarClienteCommand)` → `Cliente`.
- `ObtenerClienteUseCase.ejecutar(ObtenerClienteQuery)` → `Cliente` (incluye
  verificación de propiedad para rol `CLIENTE`).
- `ListarClientesUseCase.ejecutar(ListarClientesQuery)` → `List<Cliente>`.

Validación: cadena de responsabilidad `ClienteValidator` sobre `DatosCliente`
(ver §6.2). Los beans de los use cases y del validador se declaran en
`infrastructure.config.ClienteBeansConfig` (la aplicación no usa anotaciones
Spring).

### 5.3 Dominio — `domain`

- `Cliente` (agregado/entidad raíz) + VO `DNI`.
- Puerto `ClienteRepository`.
- Excepciones de dominio (ver §6.3).
- **Sin domain events en Sprint 1** (el alta/edición de cliente no genera
  eventos; el paquete `domain.event` queda vacío, ver §12 de la spec).

### 5.4 Persistencia — `infrastructure.adapter.persistence`

- `ClienteJpaEntity` (`@Entity @Table(name = "clientes")`), `ddl-auto: validate`
  (el esquema lo define Flyway, no Hibernate).
- `ClienteJpaRepository extends JpaRepository<ClienteJpaEntity, Long>` con
  queries derivadas para las verificaciones de unicidad y el listado ordenado.
- `ClienteRepositoryAdapter implements ClienteRepository` (`@Component`): mapea
  `Cliente` ↔ `ClienteJpaEntity` (el DNI se guarda como `String`; el mapeo
  explícito vive en el adapter, sin `AttributeConverter` — menos clases).

**Unicidad (BR-001, BR-003) — doble barrera, race-condition safe:**

1. Chequeo en el use case vía puerto (`existsByDni`, `existsByEmail`,
   `existsByDniAndIdNot`, `existsByEmailAndIdNot`) → `ClienteDuplicadoException`
   → `409` con el campo duplicado.
2. Backstop: constraints `UNIQUE (dni)` y `UNIQUE (email)` en la BD. Si dos
   requests concurrentes pasan el chequeo, el segundo `INSERT`/`UPDATE` falla
   con `DataIntegrityViolationException` → `409` genérico (no se puede
   determinar el campo desde la excepción; aceptable: es un caso de carrera).

### 5.5 Seguridad — `infrastructure.security` (ver ADR-004)

- `JwtService` — **solo validación** de tokens HS256 (firma + expiración) y
  extracción de claims `role` y `clienteId` (null si no está). No emite tokens.
- `JwtAuthenticationFilter extends OncePerRequestFilter` — lee
  `Authorization: Bearer <JWT>`; si el token es válido setea
  `UsernamePasswordAuthenticationToken` con principal `AuthenticatedUser` y
  autoridad `ROLE_<rol>`; si es inválido/ausente, deja el contexto vacío
  (el `authenticationEntryPoint` responde `401`).
- `SecurityConfig` — `SecurityFilterChain`: CSRF off, sesiones stateless,
  matchers por método/ruta (orden específico, ver §7.3), entry point `401` y
  access-denied handler `403` con envelope JSON.
- **Emisión de tokens para tests:** `JwtTokenFactory` vive en
  `src/test/java/com/banco/support/` (scope de test, fuera del código de
  producción; ver §7.4 y ADR-004).

### 5.6 Configuración — `infrastructure.config`

`ClienteBeansConfig` (`@Configuration`): declara los beans de
`CrearClienteUseCase`, `ActualizarClienteUseCase`, `ObtenerClienteUseCase`,
`ListarClientesUseCase` y `ClienteValidator`, inyectando `ClienteRepository` y
los validadores. Mantiene `application` libre de Spring (regla ArchUnit).

### 5.7 Async work

Ninguno. No hay jobs, colas ni eventos en Sprint 1.

---

## 6. Data Changes

### 6.1 Migración Flyway

`backend/src/main/resources/db/migration/V1__schema_inicial.sql`:

```sql
CREATE TABLE clientes (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    apellido    VARCHAR(100) NOT NULL,
    dni         VARCHAR(8)   NOT NULL,
    email       VARCHAR(254) NOT NULL,
    telefono    VARCHAR(16),
    fecha_alta  TIMESTAMP    NOT NULL
);

ALTER TABLE clientes ADD CONSTRAINT uq_clientes_dni   UNIQUE (dni);
ALTER TABLE clientes ADD CONSTRAINT uq_clientes_email UNIQUE (email);
```

- `dni` `VARCHAR(8)`: BR-002 limita a 7–8 dígitos.
- `telefono` nullable (A-002; máx. 15 dígitos + `+` opcional → 16).
- `fecha_alta` `TIMESTAMP`: se guarda en UTC (los `Instant` de Java se
  serializan como ISO-8601 UTC).

### 6.2 Entidad de dominio

`Cliente` (id, nombre, apellido, DNI dni, email, telefono nullable,
Instant fechaAlta). **Sin `@Version`**: el locking optimista es para `Cuenta`
(transferencias); en clientes la integridad de unicidad la garantiza la BD.

---

## 7. External Integrations

- **PostgreSQL 16** (única integración): local `jdbc:postgresql://localhost:5433/banco`
  (usuario/contraseña `banco/banco`, ver `docker/docker-compose.yml`).
- Tests de integración: Testcontainers `postgres:16` con `@ServiceConnection`.
- **Sin** proveedores externos, mensajería ni frontend.

---

## 8. Detailed Design

### 8.1 File map completo

El developer crea todo el proyecto Maven desde cero. Lista exhaustiva
(packages bajo `backend/src/main/java/com/banco` salvo indicación):

**Raíz del proyecto**

| Archivo | Propósito |
| --- | --- |
| `backend/pom.xml` | Ver §9 (dependencias y plugins). |
| `backend/src/main/resources/application.yml` | Datasource, JPA, Flyway, secret JWT (ver §8.7). |
| `backend/src/main/resources/db/migration/V1__schema_inicial.sql` | Migración (§6.1). |
| `backend/src/main/java/com/banco/BancoApplication.java` | `@SpringBootApplication`, clase principal. |

**Domain (`com.banco.domain.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `model/Cliente` | constructor público `Cliente(Long id, String nombre, String apellido, DNI dni, String email, String telefono, Instant fechaAlta)`; factory `static Cliente crear(String nombre, String apellido, DNI dni, String email, String telefono, Instant fechaAlta)` (id null); `void actualizar(String nombre, String apellido, DNI dni, String email, String telefono)`; getters `getId/getNombre/getApellido/getDni/getEmail/getTelefono/getFechaAlta` | Agregado raíz. `crear` para alta (id asignado por BD), constructor para reconstrucción desde persistencia (adapter), `actualizar` para edición (muta in-place, A-004). |
| `vo/DNI` | `record DNI(String valor)`; valida en el constructor compacto `^[0-9]{7,8}$`; lanza `DniInvalidoException` | VO inmutable (BR-002). Único VO de Sprint 1. |
| `port/ClienteRepository` | `Cliente save(Cliente)`, `Optional<Cliente> findById(Long)`, `List<Cliente> findAll()` (javadoc: ordenado por id asc), `boolean existsByDni(DNI)`, `boolean existsByDniAndIdNot(DNI, Long)`, `boolean existsByEmail(String)`, `boolean existsByEmailAndIdNot(String, Long)` | Puerto de persistencia (dominio puro). |
| `exception/ClienteNoEncontradoException` | `RuntimeException`; mensaje "Cliente no encontrado" | ERR-004 → 404. |
| `exception/ClienteDuplicadoException` | `RuntimeException`; campo `String campo` ("dni" \| "email") + `String valor` + `getCampo()`; mensaje "Ya existe un cliente con ese DNI/email" | ERR-001 → 409 indicando el campo. |
| `exception/DniInvalidoException` | `RuntimeException`; mensaje "El DNI debe contener entre 7 y 8 dígitos" | Lanzada por el VO `DNI`; el handler la mapea a 400 (defensivo). |
| `exception/DatosInvalidosException` | `RuntimeException`; campo `String campo` + `String mensaje` + getters; mensaje general "Datos inválidos" | ERR-002 → 400 con detalle de campo. Lanzada por la cadena de validación. |
| `exception/AccesoDenegadoException` | `RuntimeException`; mensaje "No tiene permisos para realizar esta operación" | ERR-003 → 403 (propiedad en `ObtenerClienteUseCase`). |

**Application (`com.banco.application.*`)** — Java puro, sin imports de Spring.

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `command/CrearClienteCommand` | `record CrearClienteCommand(String nombre, String apellido, String dni, String email, String telefono)` | Entrada del alta. |
| `command/ActualizarClienteCommand` | `record ActualizarClienteCommand(Long id, String nombre, String apellido, String dni, String email, String telefono)` | Entrada de la edición (PUT total, A-004). |
| `query/ObtenerClienteQuery` | `record ObtenerClienteQuery(Long id, String rol, Long clienteIdClaim)` — `rol` ∈ {"ADMIN","CLIENTE"}; `clienteIdClaim` null para ADMIN | Consulta con datos del sujeto autenticado (el controller los resuelve del `SecurityContext`). |
| `query/ListarClientesQuery` | `record ListarClientesQuery()` | Marcador para el listado. |
| `usecase/CrearClienteUseCase` | `Cliente ejecutar(CrearClienteCommand)`; dependencias: `ClienteRepository`, `ClienteValidator` | Normaliza (trim vía `DatosCliente`), valida, chequear `existsByDni`/`existsByEmail`, construye `Cliente.crear(..., Instant.now())`, `save`. |
| `usecase/ActualizarClienteUseCase` | `Cliente ejecutar(ActualizarClienteCommand)`; dependencias: `ClienteRepository`, `ClienteValidator` | `findById` → `ClienteNoEncontradoException`; valida; chequea `existsByDniAndIdNot`/`existsByEmailAndIdNot`; `actualizar(...)`; `save`. |
| `usecase/ObtenerClienteUseCase` | `Cliente ejecutar(ObtenerClienteQuery)`; dependencia: `ClienteRepository` | Si `rol == "CLIENTE"` y (`clienteIdClaim` null o `!= id`) → `AccesoDenegadoException`; luego `findById` → 404. Verificación de propiedad en capa de aplicación (ARCHITECTURE.md §8). |
| `usecase/ListarClientesUseCase` | `List<Cliente> ejecutar(ListarClientesQuery)`; dependencia: `ClienteRepository` | Delega en `findAll()` (orden garantizado por el adapter). |
| `validator/DatosCliente` | `record DatosCliente(String nombre, String apellido, String dni, String email, String telefono)` con constructor compacto que hace `trim` de nombre/apellido/email (null-safe) | Entrada única de la cadena; normalización en un solo lugar (BR-004: "luego de recortar espacios"). |
| `validator/Validador` | `@FunctionalInterface` con `void validar(DatosCliente datos)` (lanza `DatosInvalidosException`) | Contrato de cada eslabón de la cadena. |
| `validator/CamposObligatoriosValidador` | implementa `Validador` | `nombre`, `apellido`, `email`, `dni` no null y no blank tras trim (BR-004 parcial). |
| `validator/LongitudValidador` | implementa `Validador` | `nombre`/`apellido` ≤ 100, `email` ≤ 254 (BR-004, BR-003, A-005). |
| `validator/FormatoValidador` | implementa `Validador` | `email` formato `^[^@\s]+@[^@\s]+\.[^@\s]+$`; `telefono` (si no blank) `^\+?[0-9]{6,15}$` (BR-005); `dni` construyendo `new DNI(datos.dni())` y traduciendo `DniInvalidoException` → `DatosInvalidosException("dni", msg)` (BR-002; la regla vive en el VO, el validador solo la expone como error de campo). |
| `validator/ClienteValidator` | `void validar(DatosCliente datos)`; mantiene `List<Validador>` en orden: obligatorios → longitudes → formatos; itera y corta ante la primera `DatosInvalidosException` | Orquesta la CoR (ARCHITECTURE.md §5: "valida en orden y corta ante fallo"). |

**Infrastructure (`com.banco.infrastructure.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `adapter/persistence/ClienteJpaEntity` | `@Entity @Table(name="clientes")`; `@Id @GeneratedValue(strategy=IDENTITY) Long id`; `nombre` (len 100), `apellido` (len 100), `dni` (len 8), `email` (len 254) `@Column(nullable=false)`; `telefono` (len 16, nullable); `@Column(name="fecha_alta") Instant fechaAlta`; getters/setters | Proyección JPA (sin lógica de negocio). |
| `adapter/persistence/ClienteJpaRepository` | `interface ... extends JpaRepository<ClienteJpaEntity, Long>`; `boolean existsByDni(String)`, `boolean existsByDniAndIdNot(String, Long)`, `boolean existsByEmail(String)`, `boolean existsByEmailAndIdNot(String, Long)`, `List<ClienteJpaEntity> findAllByOrderByIdAsc()` | Acceso Spring Data. |
| `adapter/persistence/ClienteRepositoryAdapter` | `@Component implements ClienteRepository`; métodos `toEntity(Cliente)` / `toDomain(ClienteJpaEntity)` (usa el constructor de `Cliente` con id y `new DNI(...)`) | Implementa el puerto. |
| `adapter/web/ClienteDto` | `record ClienteDto(Long id, String nombre, String apellido, String dni, String email, String telefono, Instant fechaAlta)` + `static ClienteDto from(Cliente)` | Salida REST (dominio nunca expone DTOs). |
| `adapter/web/CrearClienteRequest` | `record CrearClienteRequest(String nombre, String apellido, String dni, String email, String telefono)` — sin anotaciones de validación | Entrada `POST`. |
| `adapter/web/ActualizarClienteRequest` | ídem `CrearClienteRequest` | Entrada `PUT`. |
| `adapter/web/ErrorResponse` | `record ErrorResponse(String code, String message, List<DetalleError> details)` | Envelope `{ code, message, details? }` (ARCHITECTURE.md §7). |
| `adapter/web/DetalleError` | `record DetalleError(String campo, String mensaje)` | Entradas de `details` (campo de error). |
| `adapter/web/GlobalExceptionHandler` | `@RestControllerAdvice`; mapeos de §8.6 | Traduce excepciones al envelope. |
| `adapter/web/ClienteController` | `@RestController @RequestMapping("/api/v1/clientes")`; 4 endpoints; resuelve `AuthenticatedUser` del `SecurityContext` para `ObtenerClienteQuery`; `POST` responde `201` + `Location` | Coordina; sin reglas de negocio. |
| `security/AuthenticatedUser` | `record AuthenticatedUser(Long clienteId, String rol)` — `clienteId` null para ADMIN | Principal del `SecurityContext`. |
| `security/JwtService` | `@Component`; constructor con `@Value("${banco.security.jwt-secret}")` (construye `SecretKey` HS256, requiere ≥ 32 bytes); `AuthenticatedUser validar(String token)` parseando claims (`role`, `clienteId` opcional); deja que jjwt lance `JwtException`/`IllegalArgumentException` | Validación de tokens (no emite). |
| `security/JwtAuthenticationFilter` | `@Component extends OncePerRequestFilter`; lee header `Authorization`, valida, setea `UsernamePasswordAuthenticationToken(new AuthenticatedUser(...), null, List.of(new SimpleGrantedAuthority("ROLE_"+rol)))`; ante excepción limpia el contexto | Convierte el token en autenticación. |
| `security/SecurityConfig` | `@Configuration @EnableWebSecurity`; `SecurityFilterChain` de §8.5 | Reglas RBAC, entry point y handler 403. |
| `config/ClienteBeansConfig` | `@Configuration`; `@Bean` de los 4 use cases y `ClienteValidator` | Wiring (aplicación sin Spring). |

**Tests (`backend/src/test/...`)**

| Archivo | Cubre |
| --- | --- |
| `resources/application-test.yml` | Secret JWT de test + config (ver §8.7). |
| `java/com/banco/support/JwtTokenFactory.java` | Emisión de tokens de test (ver §8.4). |
| `java/com/banco/integration/BaseIntegrationTest.java` | Contenedor Postgres + `MockMvc` + bean de `JwtTokenFactory`. |
| `java/com/banco/integration/ClienteApiIntegrationTest.java` | Endpoints REST y códigos de estado (ver §10). |
| `java/com/banco/domain/DNITest.java`, `java/com/banco/domain/ClienteTest.java` | VOs y entidad. |
| `java/com/banco/application/ClienteValidatorTest.java`, `.../CrearClienteUseCaseTest.java`, `.../ActualizarClienteUseCaseTest.java`, `.../ObtenerClienteUseCaseTest.java`, `.../ListarClientesUseCaseTest.java` | Use cases y cadena de validación. |
| `java/com/banco/architecture/LayerArchitectureTest.java` | Reglas ArchUnit (AC-025). |

Los `.gitkeep` existentes se eliminan a medida que los archivos reales los
reemplazan (dejar `.gitkeep` solo en paquetes que queden vacíos, p. ej.
`domain/event/`).

### 8.2 Domain design (resumen ejecutivo)

- `DNI` es el **único VO** de Sprint 1: `email` y `telefono` se modelan como
  `String` y sus reglas (BR-003, BR-005) las aplica la cadena de validación.
  Razón: un VO por cada campo sería abstracción innecesaria (AGENTS.md §11 —
  "simplest correct solution"); la regla del DNI vive en el VO porque el issue
  lo exige explícitamente y es un identificador del dominio.
- `Cliente` no valida más allá del VO `DNI` en su construcción: la cadena de
  validación es la dueña de obligatoriedad/formato/longitud (BR-001..BR-005) y
  garantiza que solo valores válidos lleguen al agregado. Esto evita duplicar
  reglas entre dominio y aplicación.
- Excepciones de dominio en `domain.exception` (convención de ARCHITECTURE.md
  §3): `ClienteNoEncontradoException` (404), `ClienteDuplicadoException` (409,
  con `campo`), `DniInvalidoException` (400 defensivo), `DatosInvalidosException`
  (400 con campo), `AccesoDenegadoException` (403). Todas `RuntimeException`.

### 8.3 Application design (flujo de cada use case)

**Crear:**

```text
CrearClienteUseCase.ejecutar(command)
  1. DatosCliente datos = new DatosCliente(command.nombre(), ..., command.telefono())  // trims
  2. validator.validar(datos)                    // CoR; corta ante el primer error → 400
  3. DNI dni = new DNI(datos.dni())              // garantizado válido por el paso 2
  4. existsByDni(dni)        → ClienteDuplicadoException("dni", ...)
  5. existsByEmail(email)    → ClienteDuplicadoException("email", ...)
  6. Cliente c = Cliente.crear(..., Instant.now())   // fechaAlta automática (FR-005)
  7. return repository.save(c)
```

**Actualizar (A-004 — PUT total, mismas reglas que alta):**

```text
ActualizarClienteUseCase.ejecutar(command)
  1. Cliente cliente = findById(command.id())  o  ClienteNoEncontradoException (ERR-004)
  2. DatosCliente datos = new DatosCliente(...)  // trims
  3. validator.validar(datos)
  4. DNI dni = new DNI(datos.dni())
  5. existsByDniAndIdNot(dni, id)    → ClienteDuplicadoException("dni", ...)   // excluye propio (BR-001)
  6. existsByEmailAndIdNot(email, id) → ClienteDuplicadoException("email", ...) // excluye propio (BR-003)
  7. cliente.actualizar(...)          // id y fechaAlta NO se tocan
  8. return repository.save(cliente)
```

**Obtener (propiedad en aplicación, ARCHITECTURE.md §8):**

```text
ObtenerClienteUseCase.ejecutar(query)
  1. si rol == "CLIENTE" y (clienteIdClaim == null || !clienteIdClaim.equals(id))
       → AccesoDenegadoException (ERR-003, AF-001)
  2. return findById(id)  o  ClienteNoEncontradoException (ERR-004)
```

**Listar:** delega en `findAll()` (orden por id asc en el adapter — AC-020).

### 8.4 Test token minting (producción limpia)

- `JwtService` (producción) **solo valida**; la emisión de tokens no existe en
  código de producción (ver ADR-004). SPEC-003 agregará la emisión cuando
  exista el login.
- `JwtTokenFactory` (en `src/test/java/com/banco/support/`) firma tokens HS256
  con el **mismo secret** de `application-test.yml` y el **mismo contrato de
  claims** que `JwtService` espera:

```text
sub:        rol ("ADMIN" | "CLIENTE")   // provisional hasta SPEC-003 (sub = username)
role:       "ADMIN" | "CLIENTE"
clienteId:  solo en tokens CLIENTE (Long)
iat, exp:   exp = now + banco.security.jwt-expiration-minutes (default 60)
```

- Métodos: `String tokenAdmin()`, `String tokenCliente(Long clienteId)`.
- Se provee como bean en `BaseIntegrationTest` mediante una clase
  `@TestConfiguration` anidada que lo construye con
  `@Value("${banco.security.jwt-secret}")` y
  `@Value("${banco.security.jwt-expiration-minutes:60}")`. Así los tests de
  integración inyectan `JwtTokenFactory` sin tocar código de producción.

### 8.5 SecurityConfig (reglas RBAC y orden de matchers)

```text
authorizeHttpRequests:
  1. POST   /api/v1/clientes        → hasRole("ADMIN")
  2. PUT    /api/v1/clientes/**     → hasRole("ADMIN")
  3. GET    /api/v1/clientes        → hasRole("ADMIN")        // el listado EXACTO va antes que /{id}
  4. GET    /api/v1/clientes/**     → hasAnyRole("ADMIN", "CLIENTE")  // propiedad en el use case
  5. anyRequest()                   → authenticated()
```

- `csrf.disable()`, `sessionManagement(STATELESS)`,
  `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`.
- `exceptionHandling`: `authenticationEntryPoint` escribe `401` con
  `{"code":"NO_AUTENTICADO","message":"Token ausente o inválido"}`;
  `accessDeniedHandler` escribe `403` con `{"code":"ACCESO_DENEGADO",...}`.
  Ambos como lambdas dentro de `SecurityConfig` (helper privado que serializa
  el envelope con `ObjectMapper`).
- **Sin** endpoints `/api/v1/auth`, sin `Usuario`, sin `PasswordEncoder`
  (BCrypt llega con SPEC-003). Sin CORS (no hay frontend en Sprint 1).

### 8.6 GlobalExceptionHandler — tabla de mapeo

Envelope: `{ "code": "...", "message": "...", "details": [ { "campo": "...", "mensaje": "..." } ] }`
(`details` se omite cuando es null — `spring.jackson.default-property-inclusion: non_null`).

| Excepción | HTTP | `code` | `details` |
| --- | --- | --- | --- |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | `[{campo, mensaje}]` (ERR-002) |
| `DniInvalidoException` (defensivo) | 400 | `DATOS_INVALIDOS` | `[{campo:"dni", mensaje}]` |
| `HttpMessageNotReadableException` (JSON malformado) | 400 | `DATOS_INVALIDOS` | null |
| `MethodArgumentTypeMismatchException` (id no numérico) | 400 | `DATOS_INVALIDOS` | null |
| `AccesoDenegadoException` | 403 | `ACCESO_DENEGADO` | null (ERR-003) |
| `ClienteNoEncontradoException` | 404 | `CLIENTE_NO_ENCONTRADO` | null (ERR-004) |
| `ClienteDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo:"dni"\|"email", mensaje}]` (ERR-001) |
| `DataIntegrityViolationException` (backstop race) | 409 | `CONFLICTO_UNICIDAD` | null, mensaje "Conflicto de unicidad de datos" |
| `Exception` (fallback) | 500 | `ERROR_INTERNO` | null (sin leak de stack) |

Los `401`/`403` de Spring Security (rol incorrecto, token ausente/inválido) los
escriben el entry point y el access-denied handler de `SecurityConfig` con el
mismo envelope (ERR-005, ERR-003).

### 8.7 Configuración (`application.yml` / `application-test.yml`)

`backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/banco
    username: banco
    password: banco
  jpa:
    hibernate:
      ddl-auto: validate        # el esquema lo define Flyway
    open-in-view: false
  flyway:
    enabled: true
  jackson:
    default-property-inclusion: non_null

banco:
  security:
    jwt-secret: ${JWT_SECRET:dev-only-secret-clave-hs256-de-32-bytes-minimo}
    jwt-expiration-minutes: 60
```

- `jwt-secret`: **≥ 32 bytes** (requisito HS256 de jjwt). El default es solo
  para desarrollo local; en CI/tests se usa el de `application-test.yml`.
- `application-test.yml` (`src/test/resources`): mismo `banco.security.jwt-secret`
  (valor fijo de test, también ≥ 32 bytes) y `jwt-expiration-minutes`. El
  datasource lo provee Testcontainers vía `@ServiceConnection`.

---

## 9. Build & Dependencies (`backend/pom.xml`)

- Parent: `org.springframework.boot:spring-boot-starter-parent:3.3.x` (última
  patch estable 3.3), `java.version` 21.
- Dependencias (compilación): `spring-boot-starter-web`,
  `spring-boot-starter-data-jpa`, `spring-boot-starter-security`,
  `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`
  (módulo requerido por Flyway ≥ 10 para PostgreSQL), `org.postgresql:postgresql`
  (runtime), `io.jsonwebtoken:jjwt-api:0.12.6` + `jjwt-impl` + `jjwt-jackson`
  (ambas runtime). **No** se incluye `spring-boot-starter-validation` (los DTOs
  no usan bean validation; las reglas viven en la CoR).
- Dependencias (test): `spring-boot-starter-test`, `spring-security-test`,
  `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`
  (versiones gestionadas por el BOM de Boot),
  `com.tngtech.archunit:archunit-junit5:1.3.x`.
- Plugins: `spring-boot-maven-plugin`.
- La migración Flyway se ejecuta automáticamente contra la BD local y contra
  el contenedor de tests (`@ServiceConnection`).

---

## 10. Testing Strategy

### Unit (JUnit 5 + Mockito; sin Spring)

| Clase | Cobertura | AC |
| --- | --- | --- |
| `domain/DNITest` | válidos 7 y 8 dígitos; inválidos: no numérico, 6 o 9 dígitos, null/vacío; excepción `DniInvalidoException` | AC-004, AC-023 |
| `domain/ClienteTest` | `crear` asigna id null y fechaAlta dada; `actualizar` cambia los 5 campos y no toca id/fechaAlta | AC-023 |
| `application/ClienteValidatorTest` | CoR: obligatorios (nombre/apellido/email/dni null o blank → 400), longitudes (101 chars, email 255), formatos (email, telefono, dni), cortocircuito (primer error gana), trim de `DatosCliente` | AC-004..AC-007, AC-023 |
| `application/CrearClienteUseCaseTest` | happy path (fechaAlta seteada, save llamado); dni duplicado → `ClienteDuplicadoException("dni")`; email duplicado → `ClienteDuplicadoException("email")`; validador invocado antes del chequeo de unicidad | AC-001 (lógica), AC-002, AC-003 |
| `application/ActualizarClienteUseCaseTest` | happy path; id inexistente → `ClienteNoEncontradoException`; dni/email duplicados de otro → 409; mantener propio dni/email (exclusión `AndIdNot`) → OK | AC-014 (lógica), AC-015, AC-016, AC-017, AC-018 |
| `application/ObtenerClienteUseCaseTest` | ADMIN consulta cualquier id; CLIENTE con su id → OK; CLIENTE con id ajeno o sin claim → `AccesoDenegadoException`; inexistente → `ClienteNoEncontradoException` | AC-010, AC-011, AC-012, AC-013 |
| `application/ListarClientesUseCaseTest` | delega en `repository.findAll()` y devuelve la lista | AC-020 (lógica) |

### Integración (Spring Boot Test + Testcontainers + MockMvc)

`BaseIntegrationTest`:

```java
@Testcontainers(disabledWithoutDocker = true)   // sin Docker local: se omiten; en CI corren
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @Autowired protected MockMvc mockMvc;
    @Autowired protected JwtTokenFactory tokens;
    @TestConfiguration
    static class TokenConfig { /* @Bean JwtTokenFactory con @Value del secret y expiración */ }
}
```

**Decisión documentada:** `disabledWithoutDocker = true` hace que `mvn test`
local sin Docker omita los tests de integración (no falla el build), mientras
CI (ubuntu-latest con Docker) los ejecuta de verdad (AC-024).

`ClienteApiIntegrationTest` (un método por criterio, nombre legible):

| Cobertura | AC |
| --- | --- |
| `POST` con token ADMIN y datos válidos → `201`, body con `id` y `fechaAlta` | AC-001 |
| `POST` con dni duplicado → `409` con `details[0].campo == "dni"` | AC-002 |
| `POST` con email duplicado → `409` con `details[0].campo == "email"` | AC-003 |
| `POST` dni no numérico / 6 o 9 dígitos → `400` | AC-004 |
| `POST` email malformado → `400` | AC-005 |
| `POST` nombre o apellido vacío → `400` | AC-006 |
| `POST` sin telefono → `201` | AC-007 |
| `POST` sin token → `401` con envelope | AC-008 |
| `POST` con token CLIENTE → `403` | AC-009 |
| `GET /{id}` ADMIN → `200` | AC-010 |
| `GET /{id}` CLIENTE con su propio id → `200` | AC-011 |
| `GET /{id}` CLIENTE con id ajeno → `403` | AC-012 |
| `GET /{id}` id inexistente → `404` | AC-013 |
| `PUT` ADMIN con datos válidos → `200` con representación actualizada | AC-014 |
| `PUT` id inexistente → `404` | AC-015 |
| `PUT` dni duplicado (de otro) → `409` | AC-016 |
| `PUT` email duplicado (de otro) → `409` | AC-017 |
| `PUT` manteniendo propio dni/email → `200` | AC-018 |
| `PUT` con token CLIENTE → `403` | AC-019 |
| `GET /api/v1/clientes` ADMIN → `200`, lista ordenada por id asc | AC-020 |
| `GET /api/v1/clientes` CLIENTE → `403` | AC-021 |
| `GET /api/v1/clientes` sin token → `401` | AC-022 |

Además: verificación de persistencia real (los datos sobreviven entre
requests) y del envelope JSON (`code`/`message`/`details`) en los códigos
400/401/403/404/409 — AC-024.

### Arquitectura (ArchUnit) — `architecture/LayerArchitectureTest`

`@AnalyzeClasses(packages = "com.banco..", importOptions = ImportOption.DoNotIncludeTests.class)`.
Reglas (AC-025):

1. **domain no depende de Spring/JPA/infra/app:**
   `noClasses().that().resideInAPackage("com.banco.domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "com.banco.infrastructure..", "com.banco.application..")`.
2. **application depende solo de domain + java:**
   `classes().that().resideInAPackage("com.banco.application..").should().onlyDependOnClassesThat().resideInAnyPackage("com.banco.domain..", "java..")`.
3. **nada depende de infrastructure salvo infrastructure:**
   `noClasses().that().resideOutsideOfPackage("com.banco.infrastructure..").should().dependOnClassesThat().resideInAPackage("com.banco.infrastructure..")`.
4. **Spring/controllers solo en infrastructure:**
   `noClasses().that().resideOutsideOfPackage("com.banco.infrastructure..").should().dependOnClassesThat().areAnnotatedWith("org.springframework.stereotype.Controller")` (y/o `org.springframework.web.bind.annotation.RestController`).

---

## 11. ADR

Se crea **`docs/adr/ADR-004-autenticacion-minima-jwt-sprint1.md`**: la
autenticación mínima de Sprint 1 (JwtService solo-valida en producción, emisión
de tokens en scope de test, resolución de propiedad por claim `clienteId` en la
capa de aplicación) condiciona el diseño de SPEC-003 (Sprint 2) y por eso se
registra como decisión arquitectónica.

---

## 12. Risks

- **Secret JWT corto:** jjwt (HS256) exige clave ≥ 32 bytes; el default de
  `application.yml` y el de `application-test.yml` deben cumplirlo o la
  validación fallará en runtime/tests.
- **Drift de claims entre `JwtTokenFactory` y `JwtService`:** el contrato de
  claims está documentado en §8.4 y es responsabilidad del developer mantenerlo
  en ambos lados; la revisión debe verificar que el factory use exactamente
  `role`/`clienteId`.
- **`DataIntegrityViolationException` sin campo:** el backstop 409 no puede
  identificar el campo duplicado; se reporta genérico (solo ocurre en carreras
  entre requests concurrentes — el camino normal reporta el campo).
- **Regla ArchUnit `onlyDependOnClassesThat`:** debe importar solo clases main
  (`DoNotIncludeTests`) para no fallar por dependencias de test.
- **Tests omitidos sin Docker:** `disabledWithoutDocker = true` omite la
  cobertura de integración localmente si no hay Docker; CI la cubre. Riesgo
  residual: un developer sin Docker no ejecuta los tests de integración.

---

## 13. Alternatives Considered

- **VOs para `email`/`telefono`:** descartados — reglas simples de formato que
  la CoR ya aplica; un VO por campo sería sobreingeniería (AGENTS.md §11). El
  VO `DNI` se mantiene por exigencia explícita del issue (identificador del
  dominio).
- **Excepciones separadas por campo duplicado (`DniDuplicadoException` /
  `EmailDuplicadoException`):** descartadas — un solo `ClienteDuplicadoException`
  con `campo` ("dni" | "email") reduce clases y el handler ya distingue el campo
  para el envelope (ERR-001).
- **Emisión de tokens en producción (método `generar` en `JwtService`):**
  descartada para Sprint 1 — la producción solo debe validar; SPEC-003 agregará
  la emisión cuando exista login (ver ADR-004). El factory de test evita
  "test-only code" en producción.
- **Verificación de propiedad en el filtro/controller:** descartada —
  ARCHITECTURE.md §8 exige la verificación de propiedad en la capa de
  aplicación; el use case `ObtenerClienteUseCase` es testeable sin HTTP.
- **`@Transactional` en use cases:** descartado — introduciría Spring en
  `application` (violaría la regla ArchUnit "application depende solo de
  domain"); las operaciones de Sprint 1 son de una sola escritura y la
  consistencia de unicidad la garantiza la BD.
- **Bean validation en DTOs:** descartado — duplicaría BR-001..BR-005 en la
  capa web; la CoR es la única fuente de reglas de negocio.

---

## 14. Decision

Implementar SPEC-001 con:

- Backend Maven completo desde cero (hexagonal + DDD, paquetes
  `com.banco.{domain,application,infrastructure}` según ARCHITECTURE.md §3).
- Un VO `DNI`; entidad `Cliente`; puerto `ClienteRepository` con 7 métodos
  (save, findById, findAll, existsByDni, existsByDniAndIdNot, existsByEmail,
  existsByEmailAndIdNot).
- Cadena de responsabilidad `ClienteValidator` (obligatorios → longitudes →
  formatos; corta ante el primer error) reportando `DatosInvalidosException`
  con campo.
- Unicidad por doble barrera (chequeo de aplicación + constraint UNIQUE de BD)
  → `409` con campo; backstop `DataIntegrityViolationException` → `409`.
- Autenticación mínima JWT (ADR-004): `JwtService` solo-valida, filtro JWT,
  `SecurityConfig` con RBAC, `JwtTokenFactory` en scope de test para emitir
  tokens.
- Propiedad `CLIENTE` resuelta por claim `clienteId` en `ObtenerClienteUseCase`
  → `403`.
- Envelope de error `{ code, message, details? }` según §8.6.
- Tests: unit (dominio/aplicación), integración (Testcontainers
  `disabledWithoutDocker = true`), ArchUnit (AC-023..AC-025).
