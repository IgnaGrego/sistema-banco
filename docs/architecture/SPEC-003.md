# Architecture — SPEC-003 (Autenticación y Autorización)

## 1. Feature

Registro y login de usuarios (`POST /api/v1/auth/register` y
`POST /api/v1/auth/login`) con emisión de JWT HS256 de expiración corta, sobre
el backend hexagonal + DDD existente (`com.banco`, Sprint 1 ya implementado:
clientes, filtro JWT solo-valida, RBAC de `/api/v1/clientes`). El JWT identifica
al usuario por `username` (`sub`), con `role` y, para `CLIENTE`, el `clienteId`
vinculado. Los endpoints protegidos existentes resuelven rol/`clienteId` desde
el token sin cambios de mecanismo (ADR-004 §5).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-003-autenticacion.md` (APROBADA — fuente de verdad; FR-001..FR-005,
  BR-001..BR-004, ERR-001..ERR-007, AC-001..AC-020, asunciones A-001..A-005).
- `docs/adr/ADR-003` (JWT + Spring Security + BCrypt + RBAC), `ADR-004` (§5 —
  camino de extensión), y el nuevo `ADR-005` (sección 11).

---

## 3. Affected Modules

- **`backend/src/main/java/com/banco/domain`** — nueva entidad `Usuario`, enum
  `Rol`, puertos `UsuarioRepository`, `PasswordHasher`, `TokenEmisor`,
  excepciones `CredencialesInvalidasException`, `UsernameDuplicadoException`.
- **`backend/src/main/java/com/banco/application`** — nuevos commands
  (`RegistrarUsuarioCommand`, `LoginCommand`), use cases
  (`RegistrarUsuarioUseCase`, `AutenticarUsuarioUseCase`), validación de
  registro (`DatosRegistro`, `RegistroValidator`).
- **`backend/src/main/java/com/banco/infrastructure`** — `AuthController` +
  DTOs (`adapter.web`), `UsuarioJpaEntity`/`UsuarioJpaRepository`/
  `UsuarioRepositoryAdapter` (`adapter.persistence`), `JwtService` (emisión),
  `BcryptPasswordHasher`, `SecurityConfig` (matchers públicos),
  `GlobalExceptionHandler` (mapeos nuevos), `AuthBeansConfig` (wiring).
- **`backend/src/main/resources/db/migration`** — nueva migración
  `V2__usuarios.sql`.
- **`backend/src/test`** — `JwtTokenFactory` (nuevo contrato de claims),
  `ClienteApiIntegrationTest` (actualización mecánica de call-sites), nuevo
  `AuthApiIntegrationTest`, unit tests nuevos.
- **No afectado:** frontend, `docker/`, `application.yml`/`application-test.yml`
  (el secret y la expiración ya existen), `pom.xml` (sin dependencias nuevas).

---

## 4. Application Flow

```text
REST (AuthController: /api/v1/auth/register|login)   infrastructure.adapter.web
        ↓  request DTOs (records, sin anotaciones de validación)
Use Cases (RegistrarUsuarioUseCase / AutenticarUsuarioUseCase)
                                                   application.usecase  (Java puro)
        ↓  validación (RegistroValidator) + puertos de dominio
Domain (Usuario, Rol, UsuarioRepository port,
        PasswordHasher port, TokenEmisor port)      domain
        ↓
Persistence (UsuarioRepositoryAdapter → JPA)        infrastructure.adapter.persistence
Security (JwtService: emitir + validar)             infrastructure.security
        ↓
PostgreSQL 16 (Flyway V1 + V2, UNIQUE username)     db
```

**Endpoints protegidos (cómo se resuelve rol/`clienteId` desde el token)** —
mecanismo sin cambios respecto a SPEC-001 (ADR-004 §5: "la validación, el
filtro y la verificación de propiedad no cambian de lugar"):

1. `JwtAuthenticationFilter` lee `Authorization: Bearer <JWT>`, llama
   `JwtService.validar(token)` (firma HS256 + expiración) y setea
   `UsernamePasswordAuthenticationToken` con principal
   `AuthenticatedUser(clienteId, rol)` y autoridad `ROLE_<rol>`. Token
   ausente/inválido → contexto vacío → entry point `401` (ERR-002).
2. `SecurityConfig` autoriza por rol (matchers de §8.5); rol insuficiente →
   access-denied handler `403` (ERR-003).
3. El controller resuelve `AuthenticatedUser` del `SecurityContext` y lo pasa
   al use case (`ObtenerClienteUseCase` verifica propiedad BR-004).
4. **El cambio de contrato (`sub` = username) no afecta este flujo**: nada del
   código de producción lee `sub` (ver §6.3).

**Flujo concreto del registro (`POST /api/v1/auth/register` — AF-001):**

1. `AuthController` construye `RegistrarUsuarioCommand` y delega en
   `RegistrarUsuarioUseCase` (sin reglas de negocio en el controller).
2. El use case normaliza (trim de `username` vía `DatosRegistro`), valida con
   `RegistroValidator` (obligatoriedad/longitud de `username`, password ≥ 8,
   rol válido, `clienteId` requerido si `CLIENTE`) → `400` (ERR-004, ERR-007).
3. Verifica unicidad de `username` vía puerto → `409` (ERR-005).
4. Para `CLIENTE`, verifica que el `clienteId` referencie un `Cliente`
   existente (`ClienteRepository.findById`) → `404` (ERR-006).
5. Hashea la password con BCrypt vía el puerto `PasswordHasher` (BR-001),
   construye `Usuario` y persiste vía `UsuarioRepository`.
6. Responde `201 Created` con `UsuarioDto` (id, username, rol — **nunca** la
   password, BR-001).

**Flujo concreto del login (`POST /api/v1/auth/login` — main flow):**

1. `AuthController` construye `LoginCommand` y delega en
   `AutenticarUsuarioUseCase`.
2. El use case normaliza `username` (trim, defensivo) y busca el usuario vía
   puerto. Username null/blank → `CredencialesInvalidasException` directo
   (garantiza `401` idéntico, A-004).
3. Verifica `passwordHasher.matches(password, hash)`; si falla →
   `CredencialesInvalidasException`. La respuesta es **idéntica** para username
   inexistente o password incorrecta (A-004, ERR-001).
4. Emite el JWT vía el puerto `TokenEmisor` (`JwtService.emitir(Usuario)`):
   `sub` = username, `role`, `clienteId` (solo `CLIENTE`), expiración corta
   (FR-002).
5. Responde `200 OK` con `LoginResponse(token)`.

**Nota de transaccionalidad:** los use cases son Java puro (sin Spring). El
registro es una sola escritura; la consistencia de unicidad se apoya en el
constraint `UNIQUE (username)` de la BD como backstop ante carreras (misma
estrategia de doble barrera que SPEC-001 §5.4). Sin `@Transactional` explícito.

---

## 5. Components

### 5.1 Entry points / presentación — `infrastructure.adapter.web`

| Método | Ruta | Rol requerido | Status OK | Error esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | público (sin token) | `201 Created` + `UsuarioDto` | 400, 404, 409 |
| `POST` | `/api/v1/auth/login` | público (sin token) | `200 OK` + `LoginResponse` | 401 |

`AuthController` (`@RestController @RequestMapping("/api/v1/auth")`): solo
construye commands, delega en use cases y mapea resultados a DTOs. Sin reglas
de negocio. El registro responde `201` con el body `UsuarioDto` (sin
`Location`: no existe un `GET /usuarios/{id}`; la spec no lo exige). El login
responde `200` con `{"token": "..."}` (AF-001/§8 de la spec: "responde 200 OK
con el token").

DTOs de entrada: records planos sin anotaciones de validación (convención del
proyecto — SPEC-001 §5.1). JSON malformado → `HttpMessageNotReadableException`
→ `400` (mapeo existente).

`SecurityConfig`: se agregan dos matchers `permitAll()` (ver §8.5). El resto
del chain (CSRF off, stateless, entry point `401`, handler `403`, filtro JWT)
no cambia.

### 5.2 Aplicación — `application`

- `RegistrarUsuarioUseCase.ejecutar(RegistrarUsuarioCommand)` → `Usuario`.
  Dependencias: `UsuarioRepository`, `ClienteRepository` (existente, para
  ERR-006), `PasswordHasher`, `RegistroValidator`.
- `AutenticarUsuarioUseCase.ejecutar(LoginCommand)` → `String` (el JWT).
  Dependencias: `UsuarioRepository`, `PasswordHasher`, `TokenEmisor`.
- Validación de registro: `DatosRegistro` (record, normaliza `username` con
  trim) + `RegistroValidator` (clase única con chequeos secuenciales — ver
  §8.2 para la desviación deliberada del patrón CoR).
- Los beans se declaran en `infrastructure.config.AuthBeansConfig` (la
  aplicación no usa anotaciones Spring).

### 5.3 Dominio — `domain`

- `model/Usuario` — entidad (id, username, passwordHash, Rol rol, clienteId
  nullable).
- `model/Rol` — enum `CLIENTE` | `ADMIN`.
- `port/UsuarioRepository` — `save`, `findByUsername`, `existsByUsername`.
- `port/PasswordHasher` — `hash(String)` / `matches(String, String)`
  (abstracción de BCrypt para mantener `application` libre de Spring).
- `port/TokenEmisor` — `emitir(Usuario)` → `String` (abstracción de la emisión
  JWT; la implementa `JwtService`, ver §5.5).
- `exception/CredencialesInvalidasException` (ERR-001 → 401),
  `exception/UsernameDuplicadoException` (ERR-005 → 409, con `campo`).

### 5.4 Persistencia — `infrastructure.adapter.persistence`

- `UsuarioJpaEntity` (`@Entity @Table(name = "usuarios")`), `ddl-auto: validate`
  (el esquema lo define Flyway V2, no Hibernate).
- `UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, Long>`:
  `Optional<UsuarioJpaEntity> findByUsername(String)`,
  `boolean existsByUsername(String)`.
- `UsuarioRepositoryAdapter implements UsuarioRepository` (`@Component`): mapeo
  explícito `Usuario` ↔ `UsuarioJpaEntity` (rol como `String` = `rol.name()` /
  `Rol.valueOf(...)`; sin `AttributeConverter` — convención de SPEC-001).

**Unicidad de `username` (BR-003) — doble barrera, race-condition safe:**

1. Chequeo en el use case vía puerto (`existsByUsername`) →
   `UsernameDuplicadoException` → `409` con `details[0].campo = "username"`.
2. Backstop: constraint `UNIQUE (username)` en la BD. Carrera concurrente →
   `DataIntegrityViolationException` → `409` genérico (mapeo existente).

### 5.5 Seguridad — `infrastructure.security`

- `JwtService` — **agrega emisión**: implementa el puerto `TokenEmisor` con
  `emitir(Usuario)` (ver §6.3 y ADR-005). La validación (`validar`) no cambia.
  Constructor pasa a recibir también
  `@Value("${banco.security.jwt-expiration-minutes:60}") long` para fijar `exp`.
- `BcryptPasswordHasher` (`@Component`) — implementa `PasswordHasher`
  envolviendo un bean `PasswordEncoder` (`BCryptPasswordEncoder`). BCrypt llega
  con `spring-boot-starter-security` (sin dependencias nuevas).
- `JwtAuthenticationFilter` — **sin cambios** (ADR-004 §5).
- `AuthenticatedUser` — **sin cambios** (`record AuthenticatedUser(Long
  clienteId, String rol)`): ningún consumidor necesita el `username` en este
  sprint; no se agrega para evitar tocar controller/filtro/tests sin necesidad
  (AGENTS.md §11).

### 5.6 Configuración — `infrastructure.config`

`AuthBeansConfig` (`@Configuration`): beans `PasswordEncoder`,
`PasswordHasher`, `RegistroValidator`, `RegistrarUsuarioUseCase`,
`AutenticarUsuarioUseCase`. `TokenEmisor` se resuelve automáticamente al bean
`JwtService` (único `@Component` que lo implementa). Mantiene `application`
libre de Spring (regla ArchUnit).

### 5.7 Async work

Ninguno. Sin jobs, colas ni eventos (SPEC-003 §12: sin refresh tokens).

---

## 6. Data Changes

### 6.1 Migración Flyway

Nuevo archivo `backend/src/main/resources/db/migration/V2__usuarios.sql`:

```sql
-- V2__usuarios.sql
-- Entidad Usuario (SPEC-003): username único (BR-003), password_hash BCrypt
-- (BR-001), rol CLIENTE|ADMIN y cliente_id nullable con FK a clientes(id)
-- (FR-005; null para ADMIN).

CREATE TABLE usuarios (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    rol           VARCHAR(7)  NOT NULL,
    cliente_id    BIGINT      REFERENCES clientes(id)
);

ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_username UNIQUE (username);
```

- `username VARCHAR(50)`: A-005 (obligatorio, hasta 50 caracteres).
- `password_hash VARCHAR(60)`: BCrypt genera exactamente 60 caracteres.
- `rol VARCHAR(7)`: "CLIENTE" (7) | "ADMIN" (5), almacenado como `String`.
- `cliente_id` nullable, `REFERENCES clientes(id)`: solo usuarios `CLIENTE`
  (FR-005); la FK existe porque V1 ya creó `clientes`. Sin índice adicional
  (ningún query por `cliente_id` en este sprint — simplest correct solution).
- **Sin `created_at`**: la spec (§10 de SPEC-003) no lo define; no se inventa.
- **Compatibilidad con `ddl-auto: validate` (lección de V1/`TIMESTAMPTZ`):**
  tipos mapeados: `BIGSERIAL` ↔ `Long @Id`, `VARCHAR(n)` ↔ `@Column(length=n)`,
  `BIGINT` ↔ `Long`. No hay columnas `Instant` en esta tabla, así que no
  reaparece el problema de `timestamptz`. Hibernate `validate` no valida
  constraints UNIQUE/FK (los define Flyway, como en V1).

### 6.2 Entidad de dominio

`Usuario` (id, username, passwordHash, Rol rol, clienteId nullable). Sin
`@Version`: la unicidad la garantiza la BD (misma lógica que `Cliente` en
SPEC-001 §6.2). La password en claro nunca se persiste ni se expone (BR-001).

### 6.3 Contrato de claims del JWT — cambio y su impacto

**Nuevo contrato (producción `JwtService.emitir` y test `JwtTokenFactory`):**

```text
sub:        username                  // ANTES (Sprint 1): rol (provisional, ADR-004)
role:       "ADMIN" | "CLIENTE"       // sin cambios
clienteId:  solo en tokens CLIENTE    // sin cambios
iat, exp:   exp = now + banco.security.jwt-expiration-minutes (default 60)
```

- **`JwtService.validar` no cambia**: nunca leyó `sub` (solo `role` y
  `clienteId`), por lo que la validación, el filtro y la resolución de
  rol/`clienteId` son agnósticos al cambio (ADR-004 §5). Solo se actualiza el
  javadoc del contrato.
- **`JwtTokenFactory` (src/test) SÍ se actualiza** al nuevo contrato (decisión
  documentada, ver §8.4): los métodos pasan a recibir el `username`
  (`tokenAdmin(String username)`, `tokenCliente(String username, Long
  clienteId)`). `ClienteApiIntegrationTest` actualiza sus ~20 call-sites de
  forma mecánica con usernames dummy ("admin-test", "cliente-test"): el
  comportamiento de los tests no cambia porque la validación no consume `sub`.
  Mantener el factory alineado al contrato evita el riesgo de drift de claims
  señalado en ADR-004.

---

## 7. External Integrations

- **PostgreSQL 16** (única integración): local
  `jdbc:postgresql://localhost:5433/banco` (usuario/contraseña `banco/banco`,
  ver `docker/docker-compose.yml`). Sin cambios.
- Tests de integración: Testcontainers `postgres:16` con `@ServiceConnection`.
  Sin cambios.
- **Sin** proveedores externos, mensajería ni frontend.

---

## 8. Detailed Design

### 8.1 File map completo

Packages bajo `backend/src/main/java/com/banco` salvo indicación.

**Archivos NUEVOS:**

**Domain (`com.banco.domain.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `model/Rol` | `enum Rol { CLIENTE, ADMIN }` | Rol del usuario (RBAC, ADR-003). Enum plano; el parsing desde String lo hace `RegistroValidator` (ver abajo). |
| `model/Usuario` | constructor público `Usuario(Long id, String username, String passwordHash, Rol rol, Long clienteId)`; factory `static Usuario crear(String username, String passwordHash, Rol rol, Long clienteId)` (id null); getters `getId/getUsername/getPasswordHash/getRol/getClienteId` | Entidad. `crear` para alta (id asignado por BD), constructor para reconstrucción (adapter). La password se guarda **ya hasheada** (BR-001); el entity no recibe nunca la password en claro. |
| `port/UsuarioRepository` | `Usuario save(Usuario)`, `Optional<Usuario> findByUsername(String)`, `boolean existsByUsername(String)` | Puerto de persistencia (dominio puro). |
| `port/PasswordHasher` | `String hash(String password)`, `boolean matches(String rawPassword, String passwordHash)` | Puerto de hashing (BCrypt). Aísla a `application` de Spring (`BCryptPasswordEncoder`). |
| `port/TokenEmisor` | `String emitir(Usuario usuario)` | Puerto de emisión de JWT. Lo implementa `JwtService` (infra). Permite que el login sea orquestado por `application` sin depender de infraestructura. |
| `exception/CredencialesInvalidasException` | `RuntimeException`; mensaje "Credenciales inválidas" | ERR-001 → 401. Mismo mensaje para username inexistente o password incorrecta (A-004). |
| `exception/UsernameDuplicadoException` | `RuntimeException`; campo `String campo` (= "username") + `getCampo()`; mensaje "Ya existe un usuario con ese username" | ERR-005 → 409 indicando el campo (misma semántica que SPEC-001 ERR-001). |

**Application (`com.banco.application.*`)** — Java puro, sin imports de Spring.

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `command/RegistrarUsuarioCommand` | `record RegistrarUsuarioCommand(String username, String password, String rol, Long clienteId)` | Entrada del registro. `rol` como String (se parsea en la validación). |
| `command/LoginCommand` | `record LoginCommand(String username, String password)` | Entrada del login. |
| `validator/DatosRegistro` | `record DatosRegistro(String username, String password, String rol, Long clienteId)` con constructor compacto que hace `trim` de `username` (null-safe) | Entrada del `RegistroValidator`; normalización en un solo lugar (A-005: "no vacío tras recortar espacios"). |
| `validator/RegistroValidator` | `void validar(DatosRegistro datos)`; chequeos secuenciales que lanzan `DatosInvalidosException(campo, mensaje)`: `username` obligatorio y ≤ 50 (A-005); `password` ≥ 8 caracteres (BR-002, ERR-004); `rol` ∈ {CLIENTE, ADMIN} (`Rol.valueOf` capturando `IllegalArgumentException`); si `rol == CLIENTE` y `clienteId == null` → `DatosInvalidosException("clienteId", ...)` (ERR-007) | Validación de forma/formato del registro (errores 400). Ver §8.2 (clase única, no CoR). |
| `usecase/RegistrarUsuarioUseCase` | `Usuario ejecutar(RegistrarUsuarioCommand)`; dependencias: `UsuarioRepository`, `ClienteRepository`, `PasswordHasher`, `RegistroValidator` | Flujo de AF-001 (ver §8.3). |
| `usecase/AutenticarUsuarioUseCase` | `String ejecutar(LoginCommand)`; dependencias: `UsuarioRepository`, `PasswordHasher`, `TokenEmisor` | Flujo de login (ver §8.3). |

**Infrastructure (`com.banco.infrastructure.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `adapter/persistence/UsuarioJpaEntity` | `@Entity @Table(name="usuarios")`; `@Id @GeneratedValue(IDENTITY) Long id`; `@Column(nullable=false, length=50) String username`; `@Column(name="password_hash", nullable=false, length=60) String passwordHash`; `@Column(nullable=false, length=7) String rol`; `@Column(name="cliente_id") Long clienteId` (nullable); getters/setters | Proyección JPA (sin lógica de negocio). |
| `adapter/persistence/UsuarioJpaRepository` | `interface ... extends JpaRepository<UsuarioJpaEntity, Long>`; `Optional<UsuarioJpaEntity> findByUsername(String)`, `boolean existsByUsername(String)` | Acceso Spring Data. |
| `adapter/persistence/UsuarioRepositoryAdapter` | `@Component implements UsuarioRepository`; métodos `toEntity`/`toDomain` (rol: `entity.setRol(usuario.getRol().name())` / `Rol.valueOf(entity.getRol())`) | Implementa el puerto. |
| `adapter/web/AuthController` | `@RestController @RequestMapping("/api/v1/auth")`; `POST /register` → `201` + `UsuarioDto`; `POST /login` → `200` + `LoginResponse` | Coordina; sin reglas de negocio. |
| `adapter/web/RegistrarUsuarioRequest` | `record RegistrarUsuarioRequest(String username, String password, String rol, Long clienteId)` | Entrada `POST /register`. |
| `adapter/web/LoginRequest` | `record LoginRequest(String username, String password)` | Entrada `POST /login`. |
| `adapter/web/LoginResponse` | `record LoginResponse(String token)` | Salida `POST /login` (FR-002: "200 OK con el token"). |
| `adapter/web/UsuarioDto` | `record UsuarioDto(Long id, String username, String rol)` + `static from(Usuario)` | Salida `POST /register`. **Nunca** incluye `passwordHash` (BR-001, AC-007). |
| `security/BcryptPasswordHasher` | `@Component implements PasswordHasher`; envuelve `PasswordEncoder` (BCrypt); `hash` delega en `encode`, `matches` en `matches` | Implementa el puerto `PasswordHasher`. |
| `config/AuthBeansConfig` | `@Configuration`; beans de §5.6 | Wiring (aplicación sin Spring). |

**Resources**

| Archivo | Propósito |
| --- | --- |
| `backend/src/main/resources/db/migration/V2__usuarios.sql` | Migración (§6.1). |

**Tests (`backend/src/test/...`) — NUEVOS**

| Archivo | Cubre |
| --- | --- |
| `java/com/banco/domain/UsuarioTest.java` | Entidad `Usuario` (ver §10). |
| `java/com/banco/application/RegistroValidatorTest.java` | Validación de registro (ver §10). |
| `java/com/banco/application/RegistrarUsuarioUseCaseTest.java` | Registro (ver §10). |
| `java/com/banco/application/AutenticarUsuarioUseCaseTest.java` | Login (ver §10). |
| `java/com/banco/infrastructure/security/JwtServiceTest.java` | Emisión + round-trip de claims (ver §10). |
| `java/com/banco/infrastructure/security/BcryptPasswordHasherTest.java` | Hashing BCrypt real (ver §10). |
| `java/com/banco/integration/AuthApiIntegrationTest.java` | Endpoints `/api/v1/auth` + autorización (ver §10). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `infrastructure/security/JwtService.java` | `implements TokenEmisor`; constructor agrega `@Value("${banco.security.jwt-expiration-minutes:60}") long expirationMinutes`; método `emitir(Usuario)`; javadoc del contrato actualizado (`sub` = username). `validar` intacto. |
| `infrastructure/security/SecurityConfig.java` | Dos matchers `permitAll()` para `/api/v1/auth/register` y `/api/v1/auth/login` (§8.5). Resto intacto. |
| `infrastructure/adapter/web/GlobalExceptionHandler.java` | Mapeos nuevos (§8.6): `CredencialesInvalidasException` → 401, `UsernameDuplicadoException` → 409. |
| `test/.../support/JwtTokenFactory.java` | Nuevo contrato: `tokenAdmin(String username)`, `tokenCliente(String username, Long clienteId)`; `sub` = username (§8.4). |
| `test/.../integration/ClienteApiIntegrationTest.java` | Call-sites de `JwtTokenFactory` con username dummy (mecánico; comportamiento sin cambios). |

**Sin cambios:** `application.yml` / `application-test.yml` (secret + expiración
ya presentes), `pom.xml` (ver §9), `BaseIntegrationTest`, `JwtAuthenticationFilter`,
`AuthenticatedUser`, `ClienteController`, `ObtenerClienteUseCase`,
`ClienteRepository` (se reutiliza `findById` para ERR-006 — no se agregan
métodos), `LayerArchitectureTest`.

### 8.2 Domain design (resumen ejecutivo)

- `Usuario` no valida en construcción más allá del tipo: la validación de
  forma/formato (username, password ≥ 8, rol, clienteId condicional) vive en
  `RegistroValidator` (capa de aplicación), consistente con la filosofía de
  SPEC-001 §8.2 ("la cadena de validación es la dueña de
  obligatoriedad/formato/longitud"). El rol es un `enum` tipado, así que no
  admite valores inválidos una vez parseado.
- **`RegistroValidator` como clase única (no CoR):** el patrón CoR de
  `ClienteValidator` se justifica para 6 campos en 3 etapas; para 4 reglas de
  registro (username, password, rol, clienteId condicional) una clase con
  chequeos secuenciales que corta ante el primer error es la solución más
  simple (AGENTS.md §11 — no introducir patrones solo por ser teóricamente
  apropiados). Se mantiene en `application.validator` y lanza
  `DatosInvalidosException(campo, mensaje)` como la CoR.
- **Puertos `PasswordHasher` y `TokenEmisor`:** abstracciones de
  infraestructura que `application` necesita (hashear/verificar password y
  emitir el JWT). Sin ellas, `application` dependería de
  `org.springframework.security...` (violaría la regla ArchUnit
  "application depende solo de domain"). Son el mecanismo hexagonal estándar,
  no polimorfismo especulativo (hay exactamente una implementación de cada una:
  `BcryptPasswordHasher` y `JwtService`).
- Excepciones nuevas en `domain.exception` (convención): `CredencialesInvalidasException`
  (401), `UsernameDuplicadoException` (409 con `campo`). `ERR-004`/`ERR-007`
  reutilizan `DatosInvalidosException`; `ERR-006` reutiliza
  `ClienteNoEncontradoException` (misma semántica que SPEC-001 ERR-004). Todas
  `RuntimeException`.

### 8.3 Application design (flujo de cada use case)

**Registrar:**

```text
RegistrarUsuarioUseCase.ejecutar(command)
  1. DatosRegistro datos = new DatosRegistro(command.username(), command.password(),
                                             command.rol(), command.clienteId())  // trim de username
  2. validator.validar(datos)          // 400: username obligatorio/≤50 (A-005), password ≥ 8
                                       //     (BR-002/ERR-004), rol inválido, CLIENTE sin
                                       //     clienteId (ERR-007)
  3. if (repository.existsByUsername(datos.username()))
         → UsernameDuplicadoException("username", ...)         // ERR-005 → 409
  4. Rol rol = Rol.valueOf(datos.rol())                        // garantizado válido por el paso 2
  5. Long clienteId = null;
     if (rol == CLIENTE) {
         clienteId = datos.clienteId();                        // no null: lo garantizó el paso 2
         if (clienteRepository.findById(clienteId).isEmpty())
             → ClienteNoEncontradoException                    // ERR-006 → 404
     }
     // si rol == ADMIN, un clienteId informado se IGNORA (el Usuario se persiste con
     // cliente_id null): la spec solo define el vínculo para CLIENTE (FR-005, A-003).
  6. String hash = passwordHasher.hash(datos.password())       // BCrypt (BR-001)
  7. Usuario usuario = Usuario.crear(datos.username(), hash, rol, clienteId)
  8. return repository.save(usuario)                           // controller → 201 + UsuarioDto
```

**Login:**

```text
AutenticarUsuarioUseCase.ejecutar(command)
  1. String username = command.username() == null ? null : command.username().trim()
     String password = command.password()                      // NUNCA se recorta la password
  2. if (username == null || username.isBlank() || password == null)
         → CredencialesInvalidasException                      // guard defensivo: 401 idéntico (A-004)
  3. Optional<Usuario> opt = repository.findByUsername(username)
  4. if (opt.isEmpty()) → CredencialesInvalidasException       // A-004: misma respuesta
  5. if (!passwordHasher.matches(password, opt.get().getPasswordHash()))
         → CredencialesInvalidasException                      // A-004: misma respuesta (AC-011:
                                                                // se usa matches; nunca comparación en claro)
  6. return tokenEmisor.emitir(opt.get())                      // JWT sub=username, role,
                                                               // clienteId si CLIENTE (FR-002)
```

El login no necesita validador propio: cualquier entrada no válida (username
null/blank, password incorrecta, usuario inexistente) converge en el mismo
`401` idéntico (ERR-001, A-004).

### 8.4 Test token minting — `JwtTokenFactory` alineado al nuevo contrato

- `JwtTokenFactory` (en `src/test/java/com/banco/support/`) se actualiza al
  contrato de claims de §6.3 (fin del `sub` = rol provisional):

```text
sub:        username              // nuevo contrato (SPEC-003)
role:       "ADMIN" | "CLIENTE"
clienteId:  solo en tokens CLIENTE (Long)
iat, exp:   exp = now + banco.security.jwt-expiration-minutes (default 60)
```

- API: `String tokenAdmin(String username)`, `String tokenCliente(String
  username, Long clienteId)` (antes sin username). El claim `clienteId` se
  escribe como `Long` (evita la conversión numérica ambigua en `JwtService`,
  como ya hace el factory actual).
- **Decisión documentada:** los call-sites de `ClienteApiIntegrationTest`
  (AC-001..AC-024) se actualizan de forma mecánica con usernames dummy
  ("admin-test" / "cliente-test"). El comportamiento de esos tests NO cambia:
  la validación no lee `sub`. Mantener el factory en el contrato real evita el
  drift de claims señalado en ADR-004 §Consequences y documentado en
  SPEC-001 §8.4.
- El bean se sigue proveyendo con la `@TestConfiguration TokenConfig` anidada
  en cada test de integración concreto (patrón intacto; `BaseIntegrationTest`
  no cambia y mantiene el campo `@Autowired JwtTokenFactory`).

### 8.5 SecurityConfig (matchers nuevos y orden)

```text
authorizeHttpRequests:
  0. POST   /api/v1/auth/register  → permitAll()              // FR-001: registro público
  1. POST   /api/v1/auth/login     → permitAll()              // FR-002: login público
  2. POST   /api/v1/clientes       → hasRole("ADMIN")         // sin cambios
  3. PUT    /api/v1/clientes/**    → hasRole("ADMIN")         // sin cambios
  4. GET    /api/v1/clientes       → hasRole("ADMIN")         // sin cambios (listado EXACTO antes que /{id})
  5. GET    /api/v1/clientes/**    → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios; propiedad en el use case
  6. anyRequest()                  → authenticated()          // sin cambios
```

- Se agregan los dos matchers públicos al INICIO (específicos por método+ruta;
  el orden entre ellos y los de clientes es irrelevante por tener paths
  distintos, pero van primero por legibilidad).
- `csrf.disable()`, `sessionManagement(STATELESS)`,
  `addFilterBefore(jwtAuthenticationFilter, ...)`, entry point `401` y
  access-denied handler `403` con envelope JSON: **sin cambios** (FR-003;
  ERR-002, ERR-003). Sobre `/api/v1/auth/login` con un Bearer inválido: el
  filtro limpia el contexto pero `permitAll` deja pasar la request — no aplica,
  el login no requiere token.
- Sin CORS (no hay frontend en este sprint).

### 8.6 GlobalExceptionHandler — mapeos nuevos

Envelope: `{ "code", "message", "details?" }` (omisión de null configurada).
Tabla completa resultante (nuevas filas en **negrita**):

| Excepción | HTTP | `code` | `details` |
| --- | --- | --- | --- |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | `[{campo, mensaje}]` (ERR-002, ERR-004 password, ERR-007 clienteId) |
| `DniInvalidoException` (defensivo) | 400 | `DATOS_INVALIDOS` | `[{campo:"dni", mensaje}]` |
| `HttpMessageNotReadableException` (JSON malformado) | 400 | `DATOS_INVALIDOS` | null |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | null |
| **`CredencialesInvalidasException`** | **401** | **`NO_AUTENTICADO`** | **null (ERR-001)** |
| `AccesoDenegadoException` | 403 | `ACCESO_DENEGADO` | null (ERR-003) |
| `ClienteNoEncontradoException` | 404 | `CLIENTE_NO_ENCONTRADO` | null (ERR-004 y ERR-006) |
| `ClienteDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo:"dni"\|"email", mensaje}]` (ERR-001) |
| **`UsernameDuplicadoException`** | **409** | **`CONFLICTO_UNICIDAD`** | **`[{campo:"username", mensaje}]` (ERR-005)** |
| `DataIntegrityViolationException` (backstop race) | 409 | `CONFLICTO_UNICIDAD` | null, mensaje "Conflicto de unicidad de datos" |
| `Exception` (fallback) | 500 | `ERROR_INTERNO` | null (sin leak de stack) |

- **Decisión de código para ERR-001:** `CredencialesInvalidasException` usa el
  código existente `NO_AUTENTICADO` (mismo patrón que ACCESO_DENEGADO, usado
  por el handler de Spring y por `AccesoDenegadoException`): el código indica
  la categoría (401 = no autenticado), el mensaje el detalle. El mensaje
  ("Credenciales inválidas") es **idéntico** para username inexistente y
  password incorrecta (A-004).
- Los `401`/`403` de Spring Security (token ausente/inválido, rol incorrecto)
  los siguen escribiendo el entry point y el access-denied handler de
  `SecurityConfig` con el mismo envelope (ERR-002, ERR-003) — intactos.

### 8.7 Configuración (`application.yml` / `application-test.yml`)

**Sin cambios.** `banco.security.jwt-secret` (≥ 32 bytes) y
`banco.security.jwt-expiration-minutes` (default 60) ya existen en ambos
archivos. `JwtService` los lee vía `@Value` (el segundo con default `:60`).
BCrypt no requiere configuración adicional (cost factor default de
`BCryptPasswordEncoder`).

---

## 9. Build & Dependencies

**Ninguna dependencia nueva.**

- BCrypt: `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`
  viene con `spring-boot-starter-security` (ya presente — ADR-003).
- JWT: `jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6 ya presentes (emisión y
  validación usan las mismas API `Jwts.builder()` / `Jwts.parser()`).
- Tests: `spring-boot-starter-test`, `spring-security-test`, Testcontainers,
  ArchUnit — ya presentes.
- No se agrega `spring-boot-starter-validation` (los DTOs no usan bean
  validation; las reglas viven en `RegistroValidator`).

---

## 10. Testing Strategy

### Unit (JUnit 5 + Mockito; sin Spring)

| Clase | Cobertura | AC |
| --- | --- | --- |
| `domain/UsuarioTest` | `crear` asigna id null y conserva username/hash/rol/clienteId; constructor de reconstrucción; getters | — (refuerza AC-007) |
| `application/RegistroValidatorTest` | username null/blank → `DatosInvalidosException("username")`; username 51 chars → 400; password 7 chars → `("password", ...)` (ERR-004); password 8 chars → OK; rol inválido → `("rol", ...)`; CLIENTE sin clienteId → `("clienteId", ...)` (ERR-007); CLIENTE con clienteId → OK; ADMIN sin clienteId → OK; trim de `DatosRegistro` | AC-003, AC-005 (lógica), AC-018 |
| `application/RegistrarUsuarioUseCaseTest` | happy path CLIENTE (validator pasa, `existsByUsername` false, `clienteRepository.findById` presente, hash vía `PasswordHasher` mock, `save` llamado con el hash y clienteId); happy path ADMIN (sin clienteId, `findById` NO invocado); username duplicado → `UsernameDuplicadoException`; CLIENTE con clienteId inexistente → `ClienteNoEncontradoException` (ERR-006); validador invocado ANTES de unicidad; ADMIN con clienteId informado → se ignora (se persiste null) | AC-001, AC-002, AC-004, AC-006 (lógica), AC-018 |
| `application/AutenticarUsuarioUseCaseTest` | happy path (matches true → `tokenEmisor.emitir(usuario)` llamado y su resultado devuelto); password incorrecta → `CredencialesInvalidasException`; username inexistente → misma excepción (A-004: mismo tipo y mensaje); username null/blank o password null → excepción sin tocar el repositorio; `matches` recibe (passwordEnClaro, hashAlmacenado) — nunca se compara la password en claro (AC-011) | AC-008 (lógica), AC-009, AC-010, AC-011 |
| `infrastructure/security/JwtServiceTest` | `emitir(usuario CLIENTE)` → token cuyo `validar` devuelve `AuthenticatedUser(clienteId, "CLIENTE")` y cuyos claims decodificados son `sub`=username, `role`=CLIENTE, `clienteId` presente; `emitir(usuario ADMIN)` → `clienteId` ausente y `validar` devuelve `(null, "ADMIN")`; expiración = now + configuración; token firmado con secret de test (round-trip emitir→validar) | AC-008 (claims), AC-020 |
| `infrastructure/security/BcryptPasswordHasherTest` | `hash(p)` ≠ p (nunca en claro), longitud 60, `matches(p, hash)` true, `matches("otra", hash)` false, dos `hash` del mismo valor difieren (salt) | AC-007, AC-011 (BCrypt real) |

### Integración (Spring Boot Test + Testcontainers + MockMvc)

`AuthApiIntegrationTest extends BaseIntegrationTest`, con su `@TestConfiguration
TokenConfig` anidada (patrón de `ClienteApiIntegrationTest`; `BaseIntegrationTest`
autowirea `JwtTokenFactory`). Usa registro+login REALES para los flujos de
autenticación y el factory para crear clientes con token ADMIN (helper) y para
casos negativos de autorización.

Helpers: `crearClienteAdmin(...)` (POST `/api/v1/clientes` con
`tokens.tokenAdmin("admin-test")`), `registerCliente(username, password, rol,
clienteId)`, `login(username, password) → token`.

| Cobertura | AC |
| --- | --- |
| Register CLIENTE con `clienteId` de un Cliente existente → `201`, body con `id`/`username`/`rol` y **sin** campo password | AC-001, AC-007 |
| Register ADMIN (sin `clienteId`) → `201` | AC-002 |
| Register password de 7 caracteres → `400` con `details[0].campo == "password"` | AC-003 (ERR-004) |
| Register username duplicado → `409` con `details[0].campo == "username"` | AC-004 (ERR-005) |
| Register CLIENTE sin `clienteId` → `400` con `details[0].campo == "clienteId"` | AC-005 (ERR-007) |
| Register CLIENTE con `clienteId` inexistente → `404` con envelope `CLIENTE_NO_ENCONTRADO` | AC-006 (ERR-006) |
| Login correcto (CLIENTE) → `200` con token; se decodifica el JWT: `sub` == username, `role` == CLIENTE, `clienteId` == el vinculado | AC-008 |
| Login con password incorrecta → `401` con `code == NO_AUTENTICADO` | AC-009 |
| Login con username inexistente → `401` con **mismo** body que AC-009 | AC-010 |
| `GET /api/v1/clientes/{id}` sin token → `401` con envelope | AC-012 |
| `GET /api/v1/clientes/{id}` con token malformado ("Bearer abc") → `401` | AC-013 |
| CLIENTE registrado+logueado consulta su propio `Cliente` → `200` | AC-014 |
| CLIENTE consulta `Cliente` ajeno → `403` con `ACCESO_DENEGADO` | AC-015 |
| CLIENTE sobre `POST /api/v1/clientes` (solo ADMIN) → `403` | AC-016 |
| ADMIN registrado+logueado consulta cualquier `Cliente` → `200`, y crea un cliente → `201` | AC-017 |
| Persistencia real: usuario registrado puede loguearse en un request posterior; envelope JSON en 400/401/403/404/409 | AC-019 |

`ClienteApiIntegrationTest` (AC-001..AC-024 existentes): solo se actualizan los
call-sites de `JwtTokenFactory` (username dummy) — sin cambios de assertions.

### Arquitectura (ArchUnit) — `LayerArchitectureTest`

Sin cambios de reglas (AC-020). Las clases nuevas deben cumplir:

1. `domain` (`Usuario`, `Rol`, `UsuarioRepository`, `PasswordHasher`,
   `TokenEmisor`, excepciones) sin dependencias de Spring/JPA/otras capas.
2. `application` (`RegistrarUsuarioCommand`, `LoginCommand`, `DatosRegistro`,
   `RegistroValidator`, use cases) dependiendo solo de `domain`/`application`/`java`
   — los use cases reciben `PasswordHasher`/`TokenEmisor`/repositorios como
   puertos (interfaces de dominio), nunca clases de infraestructura.
3. Spring/controllers solo en `infrastructure` (`AuthController`,
   `BcryptPasswordHasher`, `JwtService implements TokenEmisor` — infra → domain,
   permitido).

---

## 11. ADR

Se crea **`docs/adr/ADR-005-emision-tokens-y-password-bcrypt-spec-003.md`**: la
emisión de tokens pasa a producción (método `emitir` en `JwtService`
implementando el puerto `TokenEmisor`, según la primera opción del camino de
ADR-004 §5), se introduce el puerto `PasswordHasher` (BCrypt) para mantener
`application` libre de Spring, y el contrato de claims pasa a `sub` = username
(fin del `sub` = rol provisional). ADR-004 sancionaba ambas opciones de emisión
pero no elegía; la decisión condiciona SPEC-002..005 (que dependen de los
claims) y por eso se registra. No es redundante con ADR-004: lo extiende
(§5) y supera en parte sus decisiones 1 y 2 (solo-valida + emisión en test).

---

## 12. Risks

- **Drift de claims entre `JwtTokenFactory` y `JwtService`:** el contrato
  cambió (`sub` = username); el factory (test) y `JwtService.emitir`
  (producción) deben mantenerlo alineado. Mitigación: contrato documentado en
  §6.3/§8.4, round-trip `emitir→validar` en `JwtServiceTest`, y revisión
  verificando ambos lados (riesgo ya conocido de ADR-004).
- **`JwtService` con dos responsabilidades (emitir + validar):** mitigado por
  ADR-005 (decisión explícita): un solo dueño del `SecretKey` y del contrato
  evita el drift; si creciera, se extrae un `TokenIssuer` (alternativa
  documentada, §13).
- **`DataIntegrityViolationException` sin campo (backstop 409):** idéntico a
  SPEC-001 §12 — solo ocurre en carreras concurrentes; el camino normal
  reporta `campo = "username"`.
- **`RegistroValidator` con rol String:** el `Rol.valueOf` captura
  `IllegalArgumentException` → `DatosInvalidosException`; si se propagara sin
  capturar caería en el fallback `500` (no debe ocurrir — cubierto por tests).
- **Testcontainers `disabledWithoutDocker = true`:** los tests de integración
  nuevos se omiten localmente sin Docker; CI los cubre (riesgo residual ya
  documentado en SPEC-001 §12).
- **Carrera registro→login en tests de integración:** cada test registra
  usernames únicos (no reutilizar el mismo username entre métodos; los
  contenedores Testcontainers son por clase, no por método).

---

## 13. Alternatives Considered

- **`TokenIssuer` como clase separada (ADR-004 §5, 2ª opción):** descartado —
  `JwtService` ya posee el `SecretKey` y el contrato de validación; un método
  `emitir` en el mismo servicio centraliza emisión+validación y elimina el
  riesgo de drift de claims que motivó la alternativa A de ADR-004. El puerto
  `TokenEmisor` (interfaz de dominio) ya desacopla a `application` de la
  implementación concreta.
- **Emisión en el controller (el use case de login devuelve `Usuario` y el
  controller llama `jwtService.emitir`):** descartada — FR-002 define el login
  como "iniciar sesión y devolver un JWT": el use case orquesta la operación
  completa y es testeable de punta a punta sin HTTP; el controller queda
  reducido a construir el command y envolver la respuesta.
- **`PasswordEncoder` de Spring inyectado directo en los use cases:**
  descartado — violaría la regla ArchUnit "application depende solo de domain"
  (importaría `org.springframework.security.crypto`). El puerto `PasswordHasher`
  resuelve el hashing sin acoplar la capa.
- **CoR para la validación de registro (`RegistroValidador` por regla):**
  descartado — 4 reglas no justifican 4 clases + orquestador; una clase con
  chequeos secuenciales (corte ante el primer error) es más simple y cumple la
  misma función (AGENTS.md §11). La CoR de clientes se mantiene por su tamaño
  (6 campos, 3 etapas).
- **Extender `AuthenticatedUser` con `username`:** descartado — ningún
  consumidor lo necesita en este sprint (SPEC-003 §12: matrices específicas de
  cuentas/movimientos quedan para SPEC-002..005); agregarlo tocaría
  controller/filtro/tests sin necesidad.
- **Devolver el JWT del registro:** descartado — la spec (AF-001) define el
  registro como `201` sin token; solo el login emite.
- **`created_at` en `usuarios`:** descartado — no está en la spec (§10 de
  SPEC-003); no se inventan columnas.

---

## 14. Decision

Implementar SPEC-003 con:

- Entidad `Usuario` + enum `Rol` + puertos `UsuarioRepository`,
  `PasswordHasher`, `TokenEmisor` (dominio puro, sin Spring).
- `RegistrarUsuarioUseCase` y `AutenticarUsuarioUseCase` (Java puro, beans en
  `AuthBeansConfig`); validación de registro en `RegistroValidator`
  (`DatosInvalidosException` con campo → 400).
- Emisión de tokens en producción: `JwtService implements TokenEmisor` con
  `emitir(Usuario)` (ADR-005); contrato de claims `sub` = username, `role`,
  `clienteId` (solo CLIENTE), `exp` corta configurable. `validar`, filtro y
  `AuthenticatedUser` sin cambios.
- Hashing BCrypt vía `PasswordHasher` → `BcryptPasswordHasher` (envuelve
  `BCryptPasswordEncoder`); sin dependencias nuevas.
- `AuthController` (`/api/v1/auth/register` → 201 + `UsuarioDto`, `/api/v1/auth/login`
  → 200 + `LoginResponse`); `SecurityConfig` agrega dos `permitAll()`; resto de
  reglas RBAC y handlers 401/403 intactos.
- Migración `V2__usuarios.sql` (username UNIQUE NOT NULL, password_hash
  VARCHAR(60) NOT NULL, rol VARCHAR(7) NOT NULL, cliente_id BIGINT NULL FK →
  clientes(id)); unicode por doble barrera (chequeo de aplicación + constraint).
- `GlobalExceptionHandler`: `CredencialesInvalidasException` → 401
  `NO_AUTENTICADO` (mensaje idéntico, A-004); `UsernameDuplicadoException` →
  409 `CONFLICTO_UNICIDAD` con `campo` "username"; ERR-004/ERR-007 vía
  `DatosInvalidosException`; ERR-006 vía `ClienteNoEncontradoException`.
- `JwtTokenFactory` (test) alineado al nuevo contrato (`sub` = username);
  `ClienteApiIntegrationTest` con call-sites mecánicamente actualizados.
- Tests: unit (dominio/aplicación/seguridad), integración Testcontainers
  (`AuthApiIntegrationTest` con registro+login reales),
  ArchUnit sin cambios de reglas (AC-020).
