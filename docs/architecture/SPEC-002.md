# Architecture — SPEC-002 (Gestión de Cuentas)

## 1. Feature

Apertura y consulta de cuentas bancarias asociadas a un `Cliente` (módulo
`com.banco`, backend hexagonal + DDD ya existente con clientes y autenticación
implementados). Cada cuenta tiene `CBU` único auto-generado (22 dígitos),
tipo (`CAJA_AHORRO` | `CUENTA_CORRIENTE`), saldo inicial `0` nunca negativo,
moneda (`ARS`) y estado (`ACTIVA` | `BLOQUEADA`). Se implementan apertura
(`POST /api/v1/cuentas`), consulta por `id`, consulta por `cbu` y listado
(`GET /api/v1/cuentas`), con autorización por rol (`ADMIN`/`CLIENTE`) y
propiedad (un `CLIENTE` solo opera sus propias cuentas). El modelo de dominio
incluye la transición `bloquear()` para sustentar SPEC-004/SPEC-005; no se
exponen endpoints REST de bloqueo en este sprint.

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-002-cuentas.md` (APROBADA — fuente de verdad; FR-001..FR-008,
  BR-001..BR-006, ERR-001..ERR-010, AC-001..AC-030, asunciones A-001..A-005).
- `docs/architecture/SPEC-001.md` (patrones: use cases, CoR, puertos, doble
  barrera de unicidad, envelope de errores, Testcontainers, ArchUnit).
- `docs/architecture/SPEC-003.md` (claims JWT `role`/`clienteId`,
  `AuthenticatedUser(clienteId, rol)`, matchers de `SecurityConfig`,
  `JwtTokenFactory` de test).
- `ARCHITECTURE.md` §4 (agregado `Cuenta`, VOs `Money`/`CBU`/`Moneda`), §5
  (Factory por tipo, `@Version`), §7 (API REST y envelope de errores), §8
  (seguridad: propiedad en capa de aplicación), §9 (Flyway).
- ADRs 001–005 (no se requiere ADR nuevo — ver §11).
- **Sin cambios:** `ClienteRepository` (se reutiliza `findById` para ERR-002 y
  ERR-004; no se agregan métodos), `JwtService`, `JwtAuthenticationFilter`,
  `AuthenticatedUser`, `ClienteController`, `application.yml`/`application-test.yml`,
  `pom.xml`, `BaseIntegrationTest`, `JwtTokenFactory`, `LayerArchitectureTest`.

---

## 3. Affected Modules

- **`backend/src/main/java/com/banco/domain`** — nuevo agregado `Cuenta`,
  enums `TipoCuenta`/`EstadoCuenta`, VOs `CBU`/`Money`/`Moneda`, puerto
  `CuentaRepository`, `CuentaFactory`, excepciones nuevas (ver §8.1).
- **`backend/src/main/java/com/banco/application`** — commands/queries y
  use cases nuevos (`AbrirCuenta`, `ObtenerCuenta`, `ObtenerCuentaPorCbu`,
  `ListarCuentas`) + `AperturaValidator`.
- **`backend/src/main/java/com/banco/infrastructure`** — `CuentaController` +
  DTOs (`adapter.web`), `CuentaJpaEntity`/`CuentaJpaRepository`/
  `CuentaRepositoryAdapter` (`adapter.persistence`), `CuentaBeansConfig`
  (`config`); **modificados:** `SecurityConfig` (matchers nuevos) y
  `GlobalExceptionHandler` (mapeos nuevos).
- **`backend/src/main/resources/db/migration`** — nueva migración
  `V3__cuentas.sql` (V1 y V2 ya existen en esta rama).
- **`backend/src/test`** — unit tests de dominio/aplicación, nuevo
  `CuentaApiIntegrationTest`, nuevo `GlobalExceptionHandlerTest` (mapeo 422).
- **No afectado:** frontend, `docker/`, `pom.xml` (sin dependencias nuevas),
  `application.yml`/`application-test.yml`, `JwtTokenFactory`,
  `BaseIntegrationTest`, código existente de clientes/auth.

---

## 4. Application Flow

```text
REST (CuentaController: /api/v1/cuentas...)          infrastructure.adapter.web
        ↓  request DTOs (records, sin anotaciones de validación)
Security (JwtAuthenticationFilter → JwtService)      infrastructure.security
        ↓  valida token HS256; setea AuthenticatedUser(clienteId, rol)
Use Cases (Abrir/Obtener/ObtenerPorCbu/Listar)       application.usecase  (Java puro)
        ↓  validación (AperturaValidator), generación de CBU, propiedad (CLIENTE)
Domain (Cuenta, CBU, Money, Moneda, CuentaFactory,
        CuentaRepository port)                       domain
        ↓
Persistence (CuentaRepositoryAdapter → JPA)          infrastructure.adapter.persistence
        ↓
PostgreSQL 16 (Flyway V3, UNIQUE cbu, FK cliente_id) db
```

**Flujo concreto de la apertura (`POST /api/v1/cuentas` — ADMIN):**

1. El filtro JWT valida el token y carga `AuthenticatedUser(rol, clienteId)`;
   sin token o inválido → entry point `401` (ERR-006). Rol distinto de `ADMIN`
   → access-denied handler `403` (ERR-007).
2. El controller construye `AbrirCuentaCommand(clienteId, tipo, moneda)` y
   delega en `AbrirCuentaUseCase` (sin reglas de negocio).
3. El use case valida forma con `AperturaValidator` (`tipo` ∈ enum, `moneda`
   formato `^[A-Z]{3}$` si viene) → `400` (ERR-001, AC-004).
4. Aplica el default de `moneda` (`ARS` si ausente — FR-008, A-005) y construye
   `Moneda`; si la moneda no es `ARS` → `MonedaNoSoportadaException` → `422`
   (BR-005, ERR-009, AC-003).
5. Verifica que el `Cliente` titular exista vía `ClienteRepository.findById`
   → si no, `ClienteNoEncontradoException` → `404` (ERR-002, AC-005).
6. Genera un `CBU` único en la capa de aplicación (SecureRandom, 22 dígitos:
   8 banco fijo + 4 sucursal + 10 cuenta), verificando `existsByCbu` y
   regenerando ante colisión (AC-028, A-002).
7. La `CuentaFactory` crea la `Cuenta` según el tipo (BR-004) con saldo `0`
   (FR-003), moneda dada, estado `ACTIVA` (FR-007) y `createdAt = Instant.now()`.
8. Persiste vía `CuentaRepository.save`; el constraint `UNIQUE (cbu)` es la
   garantía final (BR-001): una carrera → `DataIntegrityViolationException` →
   `409` (ERR-010).
9. Responde `201 Created` + `Location` con `CuentaDto` (id, clienteId, cbu,
   tipo, saldo 0, moneda, estado ACTIVA, createdAt).

**Flujo de consulta por `id` (`GET /api/v1/cuentas/{id}`):**

1. `ObtenerCuentaUseCase`: `findById(id)` → si no existe,
   `CuentaNoEncontradaException` → `404` (ERR-003, AC-011).
2. Propiedad en capa de aplicación (BR-006, ARCHITECTURE.md §8): si
   `rol == "CLIENTE"` y (`clienteIdClaim` null o `!= cuenta.clienteId`) →
   `AccesoDenegadoException` → `403` (AF-002, AC-010).
3. → `200` con `CuentaDto` (AC-008, AC-009).

**Nota de orden 404 vs 403:** a diferencia de `ObtenerClienteUseCase` (donde el
chequeo de propiedad precede al `findById` porque el `id` ES el `clienteId`),
aquí el `id` es el de la cuenta: la propiedad solo se conoce tras cargar la
cuenta, así que el orden es `findById` (404) → propiedad (403). Un `CLIENTE`
con claim null que consulta un id inexistente recibe `404` (el recurso no
existe), y uno que consulta una cuenta existente ajena recibe `403`. Ver §13.

**Flujo de consulta por `cbu` (`GET /api/v1/cuentas/cbu/{cbu}`):**

1. `ObtenerCuentaPorCbuUseCase`: construye `new CBU(cbu)` → si el formato es
   inválido, `CbuInvalidoException` → `400` `CBU_INVALIDO` (ERR-005, AC-017).
2. `findByCbu(cbu)` → si no existe, `CuentaNoEncontradaException` → `404`
   (ERR-003, AC-016).
3. Propiedad (ídem consulta por id) → `403` (AC-015).
4. → `200` (AC-013, AC-014).

**Flujo de listado (`GET /api/v1/cuentas`):**

1. `ListarCuentasUseCase` con `ListarCuentasQuery(rol, clienteIdClaim,
   clienteIdFiltro)` (el parámetro `clienteId` opcional lo resuelve el
   controller solo como dato; el rol decide su validez — A-004).
2. `CLIENTE`: claim null → `403` (AF-001, AC-023); parámetro `clienteId`
   presente → `403` (A-004, AC-019); si no → `findByClienteId(claim)` →
   `200` con solo sus cuentas (FR-006, AC-018).
3. `ADMIN`: con `clienteId` → verifica `ClienteRepository.findById(filtro)`;
   si no existe → `404` (AF-004, ERR-004, AC-021); si existe →
   `findByClienteId(filtro)` → `200` (AC-020). Sin parámetro → `findAll()`
   (todas, ordenadas por id asc — AC-022).

**Nota de transaccionalidad:** los use cases son Java puro (sin Spring). La
apertura es una sola escritura; la unicidad del `CBU` se apoya en el chequeo
de aplicación (regeneración) + constraint `UNIQUE (cbu)` como backstop
(estrategia de doble barrera de SPEC-001 §5.4). Sin `@Transactional` explícito.
El `@Version` del agregado queda preparado para las operaciones de dinero
(SPEC-004/005) que sí requerirán transacciones multi-escritura.

---

## 5. Components

### 5.1 Entry points / presentación — `infrastructure.adapter.web`

| Método | Ruta | Rol requerido | Status OK | Error esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/cuentas` | `ADMIN` | `201 Created` + `Location` | 400, 401, 403, 404, 409, 422 |
| `GET` | `/api/v1/cuentas/{id}` | `ADMIN` o `CLIENTE` (propio) | `200 OK` | 400, 401, 403, 404 |
| `GET` | `/api/v1/cuentas/cbu/{cbu}` | `ADMIN` o `CLIENTE` (propio) | `200 OK` | 400, 401, 403, 404 |
| `GET` | `/api/v1/cuentas` | `ADMIN` o `CLIENTE` (propio) | `200 OK` (lista ordenada por `id` asc) | 401, 403, 404 |

`CuentaController` (`@RestController @RequestMapping("/api/v1/cuentas")`):
construye commands/queries, resuelve `AuthenticatedUser` del `SecurityContext`
y mapea a DTOs. Sin reglas de negocio (AGENTS.md §11). El `POST` responde
`201` + `Location` (convención de `ClienteController`).

DTOs de entrada: records planos sin anotaciones de validación (convención del
proyecto — SPEC-001 §5.1). JSON malformado o tipo inválido →
`HttpMessageNotReadableException` → `400` (mapeo existente, ERR-001).

**Mapeo de rutas en el controller:** Spring MVC resuelve por sí mismo la
preferencia de la ruta literal `/cbu/{cbu}` sobre la plantilla `/{id}` (no hay
conflicto). El orden crítico es el de los matchers de `SecurityConfig` (§8.4).

### 5.2 Aplicación — `application`

Cuatro use cases (orquestan dominio; Java puro, sin Spring):

- `AbrirCuentaUseCase.ejecutar(AbrirCuentaCommand)` → `Cuenta`. Dependencias:
  `CuentaRepository`, `ClienteRepository` (ERR-002), `AperturaValidator`.
  Contiene la generación del `CBU` (AC-028).
- `ObtenerCuentaUseCase.ejecutar(ObtenerCuentaQuery)` → `Cuenta`. Dependencia:
  `CuentaRepository`. Propiedad para rol `CLIENTE` (BR-006).
- `ObtenerCuentaPorCbuUseCase.ejecutar(ObtenerCuentaPorCbuQuery)` → `Cuenta`.
  Dependencia: `CuentaRepository`. Parsea `CBU` (400) y verifica propiedad.
- `ListarCuentasUseCase.ejecutar(ListarCuentasQuery)` → `List<Cuenta>`.
  Dependencias: `CuentaRepository`, `ClienteRepository` (ERR-004).

Validación de apertura: `AperturaValidator` (clase única con chequeos
secuenciales — ver §8.2 para la decisión de no usar CoR). Los beans se
declaran en `infrastructure.config.CuentaBeansConfig` (la aplicación no usa
anotaciones Spring).

### 5.3 Dominio — `domain`

- `model/Cuenta` (agregado raíz) + enums `TipoCuenta`, `EstadoCuenta`.
- `vo/CBU`, `vo/Money`, `vo/Moneda` (VOs inmutables que validan en
  construcción).
- `factory/CuentaFactory` — creación de la cuenta por tipo (BR-004,
  ARCHITECTURE.md §5; ver decisión en §13).
- `port/CuentaRepository` — puerto de persistencia.
- Excepciones nuevas en `domain.exception` (ver §8.1): `CuentaNoEncontradaException`
  (404), `CuentaBloqueadaException` (422), `CbuInvalidoException` (400),
  `MonedaNoSoportadaException` (422), `MonedaInvalidaException` (400 defensivo),
  `MoneyInvalidoException` (invariante interno, sin mapeo — ver §8.5).
- **Sin domain events en este sprint** (SPEC-002 §12: los eventos de dinero
  llegan con SPEC-004/005; el paquete `domain.event` sigue vacío).

### 5.4 Persistencia — `infrastructure.adapter.persistence`

- `CuentaJpaEntity` (`@Entity @Table(name = "cuentas")`), `ddl-auto: validate`
  (el esquema lo define Flyway V3, no Hibernate).
- `CuentaJpaRepository extends JpaRepository<CuentaJpaEntity, Long>` con
  queries derivadas: `findByCbu`, `findAllByOrderByIdAsc`,
  `findByClienteIdOrderByIdAsc`, `existsByCbu`.
- `CuentaRepositoryAdapter implements CuentaRepository` (`@Component`): mapeo
  explícito `Cuenta` ↔ `CuentaJpaEntity` (enums como `String` = `.name()` /
  `valueOf(...)`; `Money` ↔ `BigDecimal`; sin `AttributeConverter` —
  convención de SPEC-001 §5.4).

**Unicidad del CBU (BR-001) — doble barrera, race-condition safe:**

1. Chequeo en el use case vía puerto (`existsByCbu`) con regeneración ante
   colisión (AC-028, A-002).
2. Backstop: constraint `UNIQUE (cbu)` en la BD. Carrera concurrente →
   `DataIntegrityViolationException` → `409` genérico (ERR-010, mapeo ya
   existente).

### 5.5 Seguridad — `infrastructure.security`

Sin cambios de mecanismo (ADR-004 §5). `JwtAuthenticationFilter` resuelve
`AuthenticatedUser(clienteId, rol)` como hoy; `SecurityConfig` solo agrega los
matchers de cuentas (§8.4). `AuthenticatedUser` no cambia (`clienteId` es null
para `ADMIN` — SPEC-003 §5.5).

### 5.6 Configuración — `infrastructure.config`

`CuentaBeansConfig` (`@Configuration`): beans de los 4 use cases y de
`AperturaValidator`, inyectando `CuentaRepository` y `ClienteRepository`.
Mantiene `application` libre de Spring (regla ArchUnit).

### 5.7 Async work

Ninguno. No hay jobs, colas ni eventos en este sprint (SPEC-002 §12).

---

## 6. Data Changes

### 6.1 Migración Flyway — `backend/src/main/resources/db/migration/V3__cuentas.sql`

```sql
-- V3__cuentas.sql
-- Entidad Cuenta (SPEC-002): CBU único (BR-001, UNIQUE), saldo nunca negativo
-- (BR-002, NUMERIC), moneda ARS (BR-005, A-005), estado ACTIVA por defecto
-- (FR-007), version para optimistic locking (ARCHITECTURE.md §5) y FK al
-- Cliente titular (ERR-002).
--
-- NOTA created_at: la spec (SPEC-002 §10) define `TIMESTAMP`. Sin embargo,
-- Hibernate 6 mapea java.time.Instant por defecto a "timestamp(6) with time
-- zone" y `ddl-auto: validate` fallaría con un TIMESTAMP simple ("found
-- timestamp, expecting timestamptz"). Se usa `TIMESTAMP WITH TIME ZONE`
-- (timestamptz), misma desviación documentada que V1 (fecha_alta) y
-- coherente con guardar Instants en UTC.

CREATE TABLE cuentas (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  BIGINT NOT NULL REFERENCES clientes(id),
    cbu         VARCHAR(22) NOT NULL,
    tipo        VARCHAR(20) NOT NULL,
    saldo       NUMERIC(19,2) NOT NULL DEFAULT 0,
    moneda      VARCHAR(3)  NOT NULL DEFAULT 'ARS',
    estado      VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE cuentas ADD CONSTRAINT uq_cuentas_cbu UNIQUE (cbu);
CREATE INDEX idx_cuentas_cliente_id ON cuentas (cliente_id);
```

- `id BIGSERIAL` ↔ `Long @Id` (`@GeneratedValue(IDENTITY)`), como V1/V2.
- `cliente_id BIGINT NOT NULL REFERENCES clientes(id)`: FK inline estilo V2
  (sin nombre explícito). Garantía final de ERR-002; el chequeo de aplicación
  (`ClienteRepository.findById`) es la vía normal.
- `cbu VARCHAR(22)`: BR-001 (exactamente 22 dígitos) + `UNIQUE` con nombre
  `uq_cuentas_cbu` (convención de nombres de V1/V2: `uq_<tabla>_<columna>`).
- `tipo VARCHAR(20)`: almacena `TipoCuenta.name()` — "CAJA_AHORRO" (11) /
  "CUENTA_CORRIENTE" (16). `VARCHAR(20)` explícito para que `ddl-auto:
  validate` valide contra el `@Column(length=20)` de la entidad (estilo de
  `rol VARCHAR(7)` en V2).
- `saldo NUMERIC(19,2) NOT NULL DEFAULT 0`: FR-003 (saldo inicial 0); mapea a
  `BigDecimal` en la entidad. El `DEFAULT 0` es defensivo; la factory siempre
  construye `Money(0)`.
- `moneda VARCHAR(3) NOT NULL DEFAULT 'ARS'`: A-005 ("ARS" = 3 caracteres).
- `estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'`: almacena
  `EstadoCuenta.name()` — "ACTIVA" / "BLOQUEADA" (9). FR-007.
- `version BIGINT NOT NULL DEFAULT 0`: `@Version` (optimistic locking,
  ARCHITECTURE.md §5); `Long` ↔ `BIGINT`.
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`: desviación documentada del
  `TIMESTAMP` literal de la spec §10 (lección de V1; ver comentario del SQL).
- `idx_cuentas_cliente_id`: el listado por `clienteId` (FR-006) es un query
  real en este sprint; un índice de una columna es la solución correcta y no
  especulativa (a diferencia de V2, donde ningún query usaba `cliente_id`).

### 6.2 Entidad de dominio

`Cuenta` (id, clienteId, CBU cbu, TipoCuenta tipo, Money saldo, Moneda moneda,
EstadoCuenta estado, Instant createdAt, Long version) — ver §8.2. **Con
`@Version`** (a diferencia de `Cliente`/`Usuario`): es el agregado que moverá
dinero en SPEC-004/005; el campo `version` del dominio se persiste y se
reconstruye por el adapter.

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

**Domain (`com.banco.domain.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `vo/CBU` | `record CBU(String valor)`; constructor compacto: null o `!valor.matches("^[0-9]{22}$")` → `CbuInvalidoException` | VO del CBU (BR-001): exactamente 22 dígitos numéricos. El formato y la regla viven en el VO (como `DNI`). |
| `vo/Moneda` | `record Moneda(String codigo)`; constructor compacto: null o `!codigo.matches("^[A-Z]{3}$")` → `MonedaInvalidaException` | VO de moneda (código ISO 4217 alpha-3). **Record con String, no enum**: distingue "formato inválido" (400) de "no soportada" (422, `USD`) — ver §13. |
| `vo/Money` | `record Money(BigDecimal monto, Moneda moneda)`; constructor compacto: `monto` null, `moneda` null o `monto.signum() < 0` → `MoneyInvalidoException`; `static Money cero(Moneda moneda)` → `new Money(BigDecimal.ZERO, moneda)` | VO monetario inmutable (BR-002): saldo nunca negativo. Sin operaciones aritméticas en este sprint (llegan con SPEC-004/005). |
| `model/TipoCuenta` | `enum TipoCuenta { CAJA_AHORRO, CUENTA_CORRIENTE }` | Tipo de cuenta (FR-001). Enum plano; el parsing lo hace `AperturaValidator`. |
| `model/EstadoCuenta` | `enum EstadoCuenta { ACTIVA, BLOQUEADA }` | Estado de la cuenta (FR-007). |
| `model/Cuenta` | constructor público `Cuenta(Long id, Long clienteId, CBU cbu, TipoCuenta tipo, Money saldo, Moneda moneda, EstadoCuenta estado, Instant createdAt, Long version)` (reconstrucción); `Long getClienteId()`, `CBU getCbu()`, `TipoCuenta getTipo()`, `Money getSaldo()`, `Moneda getMoneda()`, `EstadoCuenta getEstado()`, `Instant getCreatedAt()`, `Long getVersion()`; `void bloquear()` | Agregado raíz (ARCHITECTURE.md §4). Sin factory estática propia (la creación la centraliza `CuentaFactory` — BR-004); el constructor público queda para reconstrucción desde persistencia (convención de `Cliente`). Invariante: `saldo.moneda()` == `moneda` (garantizado por la factory; el adapter mapea ambas a columnas separadas). |
| `model/Cuenta#bloquear` | `public void bloquear() { verificarActiva(); this.estado = EstadoCuenta.BLOQUEADA; }`; guarda privada `verificarActiva()`: si `estado != ACTIVA` → `CuentaBloqueadaException` | Transición `ACTIVA → BLOQUEADA` (FR-007, A-003) y guarda de BR-003 (A-001): **cualquier** operación de negocio sobre una cuenta `BLOQUEADA` (incluido volver a `bloquear()`) lanza `CuentaBloqueadaException`. Los métodos de dinero de SPEC-004/005 invocarán esta misma guarda primero. |
| `factory/CuentaFactory` | `public static Cuenta crear(Long clienteId, TipoCuenta tipo, CBU cbu, Moneda moneda, Instant createdAt)`; `switch (tipo)` sobre `TipoCuenta` (ambas ramas construyen `new Cuenta(null, clienteId, cbu, tipo, Money.cero(moneda), moneda, EstadoCuenta.ACTIVA, createdAt, null)`); rama default → `IllegalArgumentException` (defensivo) | Factory por tipo (BR-004, ARCHITECTURE.md §5). Hoy ambas ramas producen la misma estructura (saldo 0, estado ACTIVA); el punto de dispatch queda explícito para la divergencia futura (comisiones/descubierto, SPEC-005). Decisión documentada en §13. |
| `port/CuentaRepository` | `Cuenta save(Cuenta)`; `Optional<Cuenta> findById(Long)`; `Optional<Cuenta> findByCbu(CBU)`; `List<Cuenta> findByClienteId(Long)` (javadoc: ordenado por id asc); `List<Cuenta> findAll()` (javadoc: ordenado por id asc); `boolean existsByCbu(CBU)` | Puerto de persistencia (dominio puro). **No** se incluye `existsByClienteId`: no tiene consumidor en este sprint y AF-004/ERR-004 se resuelve con `ClienteRepository.findById` (ver §13). |
| `exception/CuentaNoEncontradaException` | `RuntimeException`; mensaje "Cuenta no encontrada" | ERR-003 → 404. |
| `exception/CuentaBloqueadaException` | `RuntimeException`; mensaje "La cuenta está bloqueada" | BR-003, ERR-008 → 422 (A-001). |
| `exception/CbuInvalidoException` | `RuntimeException`; mensaje "El CBU debe contener exactamente 22 dígitos numéricos" | ERR-005 → 400 (lanzada por el VO `CBU`, también en la consulta por `cbu`). |
| `exception/MonedaNoSoportadaException` | `RuntimeException`; mensaje "Moneda no soportada" | ERR-009 → 422 (BR-005, A-005). |
| `exception/MonedaInvalidaException` | `RuntimeException`; mensaje "Formato de moneda inválido" | Defensiva (lanzada por el VO `Moneda`); el handler la mapea a 400 (igual que `DniInvalidoException`). |
| `exception/MoneyInvalidoException` | `RuntimeException`; mensaje "El monto no es válido" | Invariante interno de `Money` (BR-002, AC-026). **Sin mapeo** en el handler: inalcanzable desde entradas de usuario en este sprint (solo se construye `Money` con 0); si ocurriera, cae en el fallback 500 — ver §8.5. |

**Application (`com.banco.application.*`)** — Java puro, sin imports de Spring.

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `command/AbrirCuentaCommand` | `record AbrirCuentaCommand(Long clienteId, String tipo, String moneda)` — `moneda` nullable | Entrada de la apertura (FR-001). |
| `query/ObtenerCuentaQuery` | `record ObtenerCuentaQuery(Long id, String rol, Long clienteIdClaim)` — `clienteIdClaim` null para ADMIN | Consulta por id con el sujeto autenticado (misma forma que `ObtenerClienteQuery`). |
| `query/ObtenerCuentaPorCbuQuery` | `record ObtenerCuentaPorCbuQuery(String cbu, String rol, Long clienteIdClaim)` | Consulta por cbu con el sujeto autenticado. |
| `query/ListarCuentasQuery` | `record ListarCuentasQuery(String rol, Long clienteIdClaim, Long clienteIdFiltro)` — `clienteIdFiltro` es el parámetro opcional (solo ADMIN lo puede enviar — A-004) | Listado con el sujeto autenticado y el filtro opcional. |
| `validator/AperturaValidator` | `void validar(Long clienteId, String tipo, String moneda)`: `clienteId` null → `DatosInvalidosException("clienteId", ...)` (FR-001, ERR-001); `tipo` null/blank o no parseable a `TipoCuenta` (`valueOf` capturando `IllegalArgumentException`) → `DatosInvalidosException("tipo", ...)`; `moneda` no null/blank y `!moneda.matches("^[A-Z]{3}$")` → `DatosInvalidosException("moneda", ...)`. Corta ante el primer error | Validación de forma de la apertura (ERR-001). Clase única, no CoR (2 campos no justifican 4 clases — decisión en §8.2). |
| `usecase/AbrirCuentaUseCase` | `Cuenta ejecutar(AbrirCuentaCommand)`; dependencias: `CuentaRepository`, `ClienteRepository`, `AperturaValidator` | Flujo de apertura (ver §8.3) incl. generación de CBU con regeneración (AC-028). |
| `usecase/ObtenerCuentaUseCase` | `Cuenta ejecutar(ObtenerCuentaQuery)`; dependencia: `CuentaRepository` | `findById` → 404; propiedad CLIENTE → 403 (orden: 404 antes que 403 — ver §4). |
| `usecase/ObtenerCuentaPorCbuUseCase` | `Cuenta ejecutar(ObtenerCuentaPorCbuQuery)`; dependencia: `CuentaRepository` | `new CBU(...)` → 400; `findByCbu` → 404; propiedad → 403. |
| `usecase/ListarCuentasUseCase` | `List<Cuenta> ejecutar(ListarCuentasQuery)`; dependencias: `CuentaRepository`, `ClienteRepository` | Listado por rol (ver §8.3). |

**Infrastructure (`com.banco.infrastructure.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `adapter/persistence/CuentaJpaEntity` | `@Entity @Table(name="cuentas")`; `@Id @GeneratedValue(IDENTITY) Long id`; `@Column(name="cliente_id", nullable=false) Long clienteId`; `@Column(nullable=false, length=22) String cbu`; `@Column(nullable=false, length=20) String tipo`; `@Column(nullable=false, precision=19, scale=2) BigDecimal saldo`; `@Column(nullable=false, length=3) String moneda`; `@Column(nullable=false, length=20) String estado`; `@Version Long version`; `@Column(name="created_at", nullable=false) Instant createdAt`; getters/setters | Proyección JPA (sin lógica de negocio). `@Version` sobre `Long` (Hibernate 6 lo soporta; columnas `version BIGINT`). |
| `adapter/persistence/CuentaJpaRepository` | `interface ... extends JpaRepository<CuentaJpaEntity, Long>`; `Optional<CuentaJpaEntity> findByCbu(String)`; `List<CuentaJpaEntity> findAllByOrderByIdAsc()`; `List<CuentaJpaEntity> findByClienteIdOrderByIdAsc(Long)`; `boolean existsByCbu(String)` | Acceso Spring Data (FR-006: orden por id asc en las dos lecturas de listado). |
| `adapter/persistence/CuentaRepositoryAdapter` | `@Component implements CuentaRepository`; `toEntity(Cuenta)` / `toDomain(CuentaJpaEntity)`: `cbu` ↔ `new CBU(...)`, `tipo` ↔ `TipoCuenta.valueOf(...)`, `saldo` ↔ `new Money(bigDecimal, new Moneda(moneda))`, `moneda` ↔ `new Moneda(...)`, `estado` ↔ `EstadoCuenta.valueOf(...)`, `version` mapeada en ambas direcciones | Implementa el puerto. |
| `adapter/web/AbrirCuentaRequest` | `record AbrirCuentaRequest(Long clienteId, String tipo, String moneda)` — sin anotaciones de validación | Entrada `POST /api/v1/cuentas`. |
| `adapter/web/CuentaDto` | `record CuentaDto(Long id, Long clienteId, String cbu, String tipo, BigDecimal saldo, String moneda, String estado, Instant createdAt)` + `static CuentaDto from(Cuenta)` (`saldo` = `getSaldo().monto()`, `moneda` = `getMoneda().codigo()`, enums como `name()`) | Salida REST (el dominio nunca expone DTOs). |
| `adapter/web/CuentaController` | `@RestController @RequestMapping("/api/v1/cuentas")`; `POST` → `201` + `Location`; `GET /{id}` → `200`; `GET /cbu/{cbu}` → `200`; `GET` (con `@RequestParam(required=false) Long clienteId`) → `200` + lista; helper `usuarioAutenticado()` (patrón de `ClienteController`) | Coordina; sin reglas de negocio. |
| `config/CuentaBeansConfig` | `@Configuration`; beans de los 4 use cases y `AperturaValidator` | Wiring (aplicación sin Spring). |

**Recursos**

| Archivo | Propósito |
| --- | --- |
| `backend/src/main/resources/db/migration/V3__cuentas.sql` | Migración (§6.1). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `infrastructure/security/SecurityConfig.java` | Matchers de cuentas (§8.4). Resto del chain intacto. |
| `infrastructure/adapter/web/GlobalExceptionHandler.java` | Mapeos nuevos (§8.5). |

**Tests (`backend/src/test/...`) — NUEVOS**

| Archivo | Cubre |
| --- | --- |
| `java/com/banco/domain/CBUTest.java` | VO `CBU` (AC-025). |
| `java/com/banco/domain/MoneyTest.java` | VO `Money` (AC-026). |
| `java/com/banco/domain/MonedaTest.java` | VO `Moneda` (validación defensiva). |
| `java/com/banco/domain/CuentaTest.java` | Agregado `Cuenta`: reconstrucción y `bloquear()`/guarda (AC-027). |
| `java/com/banco/domain/CuentaFactoryTest.java` | Factory por tipo (AC-024). |
| `java/com/banco/application/AperturaValidatorTest.java` | Validación de apertura. |
| `java/com/banco/application/AbrirCuentaUseCaseTest.java` | Apertura incl. regeneración de CBU (AC-028). |
| `java/com/banco/application/ObtenerCuentaUseCaseTest.java` | Consulta por id + propiedad. |
| `java/com/banco/application/ObtenerCuentaPorCbuUseCaseTest.java` | Consulta por cbu + propiedad. |
| `java/com/banco/application/ListarCuentasUseCaseTest.java` | Listado por rol. |
| `java/com/banco/infrastructure/adapter/web/GlobalExceptionHandlerTest.java` | Mapeo de las excepciones nuevas (incl. `CuentaBloqueadaException` → 422, AC-027). |
| `java/com/banco/integration/CuentaApiIntegrationTest.java` | Endpoints REST + persistencia + constraints (AC-001..AC-023, AC-029). |

**Sin cambios:** `application.yml` / `application-test.yml`, `pom.xml`,
`BaseIntegrationTest`, `JwtTokenFactory`, `LayerArchitectureTest`,
`ClienteRepository`, `JwtService`, `JwtAuthenticationFilter`,
`AuthenticatedUser`, `ClienteController` y todo el código de clientes/auth.

### 8.2 Domain design (resumen ejecutivo)

- **`Cuenta` valida solo a través de sus VOs y enums** (`CBU`, `Money`,
  `Moneda` son VOs que validan en construcción; `TipoCuenta`/`EstadoCuenta`
  son enums que no admiten valores inválidos). La validación de forma de la
  apertura (`tipo` parseable, `moneda` formato) vive en `AperturaValidator`
  (capa de aplicación), consistente con SPEC-001 §8.2 y SPEC-003 §8.2.
- **`AperturaValidator` como clase única, no CoR:** el patrón CoR de
  `ClienteValidator` se justifica para 6 campos en 3 etapas; para 2 reglas de
  apertura (`tipo`, `moneda`) una clase con chequeos secuenciales que corta
  ante el primer error es la solución más simple (AGENTS.md §11), misma
  decisión que `RegistroValidator` en SPEC-003 §8.2. Se mantiene en
  `application.validator` y lanza `DatosInvalidosException(campo, mensaje)`.
- **`Moneda` como record con `String`, no enum:** un enum `{ ARS }` no podría
  distinguir `USD` (moneda con formato válido pero no soportada → **422**,
  AC-003, ERR-009) de `xyz` (formato inválido → **400**, ERR-001). El record
  valida el formato (`^[A-Z]{3}$`, defensivo) y la decisión de soporte
  (ARS-only) vive en `AbrirCuentaUseCase` → `MonedaNoSoportadaException`.
- **`Cuenta` con `saldo: Money` y `moneda: Moneda` como campos separados:**
  la spec (§10) y FR-001 listan ambos en el agregado y en la respuesta; el VO
  `Money` también porta su moneda (ARCHITECTURE.md §4). La redundancia es
  mínima y especificada: la factory garantiza el invariante
  `saldo.moneda() == moneda`; el adapter mapea `saldo` (NUMERIC) y `moneda`
  (VARCHAR) a columnas separadas sin lógica de derivación.
- **Sin domain events** en este sprint (SPEC-002 §12; llegan con SPEC-004/005).
- Excepciones en `domain.exception` (convención ARCHITECTURE.md §3). Todas
  `RuntimeException`. `ERR-002`/`ERR-004` reutilizan
  `ClienteNoEncontradoException` (ya existe); los 403 reutilizan
  `AccesoDenegadoException`; los 400 de forma reutilizan
  `DatosInvalidosException`.

### 8.3 Application design (flujo de cada use case)

**Abrir:**

```text
AbrirCuentaUseCase.ejecutar(command)
  1. validator.validar(command.tipo(), command.moneda())      // 400: tipo inválido (ERR-001, AC-004),
                                                              // moneda formato inválido (ERR-001, A-005)
  2. TipoCuenta tipo = TipoCuenta.valueOf(command.tipo())     // garantizado por el paso 1
  3. String codigoMoneda = (command.moneda() == null || command.moneda().isBlank())
          ? "ARS" : command.moneda()                          // default FR-008/A-005 (sin trim:
                                                              // " ars " es formato inválido → 400)
  4. Moneda moneda = new Moneda(codigoMoneda)                 // formato garantizado por el paso 1
  5. if (!moneda.codigo().equals("ARS")) → MonedaNoSoportadaException   // 422 (BR-005, AC-003)
  6. if (clienteRepository.findById(command.clienteId()).isEmpty())
        → ClienteNoEncontradoException                        // ERR-002 → 404 (AC-005)
  7. CBU cbu = generarCbuUnico()                              // AC-028 (ver algoritmo abajo)
  8. Cuenta cuenta = CuentaFactory.crear(command.clienteId(), tipo, cbu, moneda, Instant.now())
  9. return cuentaRepository.save(cuenta)                     // controller → 201 + Location;
                                                              // UNIQUE (cbu) como backstop → 409 (ERR-010)
```

**Generación de CBU (AC-028, A-002) — método privado del use case:**

```text
private static final String BANCO = "00000001";   // banco ficticio, 8 dígitos (A-002)
private static final int MAX_INTENTOS = 5;
private static final SecureRandom RANDOM = new SecureRandom();   // thread-safe, JDK (sin dependencias)

private CBU generarCbuUnico() {
    for (int i = 0; i < MAX_INTENTOS; i++) {
        StringBuilder sb = new StringBuilder(BANCO);            // 8 dígitos (banco)
        for (int d = 0; d < 14; d++) sb.append(RANDOM.nextInt(10));  // 4 (sucursal) + 10 (cuenta)
        CBU cbu = new CBU(sb.toString());                       // 22 dígitos; el VO valida
        if (!repository.existsByCbu(cbu)) return cbu;           // unicidad antes de persistir
    }
    throw new IllegalStateException("No se pudo generar un CBU único");
    // Prácticamente inalcanzable: espacio de 10^14 candidatos. Se falla de forma
    // determinista (500) antes que persistir a ciegas; la BD queda como backstop
    // de carreras reales (ERR-010 → 409), no de colisiones de pre-chequeo.
}
```

- Generación dígito a dígito (no `nextLong` + padding) para preservar ceros a
  la izquierda ("0000..." de sucursal/cuenta).
- Testabilidad (AC-028): `existsByCbu` con stubbing consecutivo
  (`thenReturn(true, false)`) + `ArgumentCaptor` — ver §10.

**Obtener por id (propiedad en aplicación, BR-006; orden 404 → 403, ver §4):**

```text
ObtenerCuentaUseCase.ejecutar(query)
  1. Cuenta cuenta = findById(query.id())  o  CuentaNoEncontradaException (ERR-003 → 404)
  2. si rol == "CLIENTE" y (query.clienteIdClaim() == null
         || !query.clienteIdClaim().equals(cuenta.getClienteId()))
       → AccesoDenegadoException (AF-002 → 403)
  3. return cuenta
```

**Obtener por cbu:**

```text
ObtenerCuentaPorCbuUseCase.ejecutar(query)
  1. CBU cbu = new CBU(query.cbu())        // formato inválido → CbuInvalidoException → 400 (ERR-005, AC-017)
  2. Cuenta cuenta = findByCbu(cbu)  o  CuentaNoEncontradaException (ERR-003 → 404, AC-016)
  3. si rol == "CLIENTE" y (claim null || !claim.equals(cuenta.getClienteId()))
       → AccesoDenegadoException (AF-002 → 403, AC-015)
  4. return cuenta
```

**Listar (A-004):**

```text
ListarCuentasUseCase.ejecutar(query)
  1. si rol == "CLIENTE":
       1a. si query.clienteIdClaim() == null → AccesoDenegadoException   (AF-001, AC-023)
       1b. si query.clienteIdFiltro() != null → AccesoDenegadoException  (A-004, AC-019:
           el CLIENTE no envía parámetro)
       1c. return findByClienteId(query.clienteIdClaim())                (FR-006, AC-018)
  2. // rol == ADMIN
     2a. si query.clienteIdFiltro() != null:
           if (clienteRepository.findById(filtro).isEmpty())
               → ClienteNoEncontradoException                            (AF-004, ERR-004 → 404, AC-021)
           return findByClienteId(filtro)                                (AC-020)
     2b. return findAll()                                                (todas, id asc — AC-022)
```

### 8.4 SecurityConfig (matchers nuevos y orden)

El orden de matchers es **crítico** (regla de SPEC-001 §8.5): el listado
EXACTO va antes que el comodín `/{id}`, y la sub-ruta `/cbu/**` se declara
antes que el comodín para que nunca sea capturada por él. Se insertan entre
los matchers de clientes y el `anyRequest`:

```text
authorizeHttpRequests:
  0. POST   /api/v1/auth/register     → permitAll()                  // SPEC-003, sin cambios
  1. POST   /api/v1/auth/login        → permitAll()                  // SPEC-003, sin cambios
  2. POST   /api/v1/clientes          → hasRole("ADMIN")             // SPEC-001, sin cambios
  3. PUT    /api/v1/clientes/**       → hasRole("ADMIN")             // SPEC-001, sin cambios
  4. GET    /api/v1/clientes          → hasRole("ADMIN")             // SPEC-001, sin cambios
  5. GET    /api/v1/clientes/**       → hasAnyRole("ADMIN", "CLIENTE")  // SPEC-001, sin cambios
  6. POST   /api/v1/cuentas           → hasRole("ADMIN")             // NUEVO: apertura solo ADMIN (FR-001)
  7. GET    /api/v1/cuentas           → hasAnyRole("ADMIN", "CLIENTE")  // NUEVO: listado EXACTO antes que /{id}
  8. GET    /api/v1/cuentas/cbu/**    → hasAnyRole("ADMIN", "CLIENTE")  // NUEVO: /cbu/ ANTES que /{id}
  9. GET    /api/v1/cuentas/**        → hasAnyRole("ADMIN", "CLIENTE")  // NUEVO: /{id}; propiedad en el use case
  10. anyRequest()                    → authenticated()              // sin cambios
```

- Los matchers 7/8/9 comparten hoy la misma regla; el desglose explícito
  (7 y 8 antes de 9) sigue la convención de SPEC-001 §8.5 y protege contra
  divergencias futuras (p. ej., si el listado pasara a ADMIN-only). La
  propiedad de `CLIENTE` se verifica SIEMPRE en el use case (BR-006,
  ARCHITECTURE.md §8), nunca en el matcher.
- `csrf.disable()`, `sessionManagement(STATELESS)`,
  `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`,
  entry point `401` y access-denied handler `403` con envelope JSON: **sin
  cambios** (ERR-006, ERR-007).
- Sin CORS (no hay frontend en este sprint).

### 8.5 GlobalExceptionHandler — tabla de mapeo

Envelope: `{ "code", "message", "details?" }` (omisión de null configurada).
Tabla completa resultante (filas NUEVAS en **negrita**; `ERR-010` reutiliza el
mapeo existente de `DataIntegrityViolationException`):

| Excepción | HTTP | `code` | `details` |
| --- | --- | --- | --- |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | `[{campo, mensaje}]` (ERR-001: tipo/moneda formato) |
| `DniInvalidoException` (defensivo) | 400 | `DATOS_INVALIDOS` | `[{campo:"dni", mensaje}]` |
| **`CbuInvalidoException`** | **400** | **`CBU_INVALIDO`** | **`[{campo:"cbu", mensaje}]` (ERR-005)** |
| **`MonedaInvalidaException`** (defensivo) | **400** | **`DATOS_INVALIDOS`** | **`[{campo:"moneda", mensaje}]` (ERR-001/A-005)** |
| `HttpMessageNotReadableException` (JSON malformado) | 400 | `DATOS_INVALIDOS` | null |
| `MethodArgumentTypeMismatchException` (id no numérico) | 400 | `DATOS_INVALIDOS` | null |
| `CredencialesInvalidasException` | 401 | `NO_AUTENTICADO` | null |
| `AccesoDenegadoException` | 403 | `ACCESO_DENEGADO` | null (AF-001, AF-002, ERR-007) |
| `ClienteNoEncontradoException` | 404 | `CLIENTE_NO_ENCONTRADO` | null (ERR-002 apertura, ERR-004 listado) |
| **`CuentaNoEncontradaException`** | **404** | **`CUENTA_NO_ENCONTRADA`** | **null (ERR-003)** |
| `ClienteDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo:"dni"\|"email", mensaje}]` |
| `UsernameDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo:"username", mensaje}]` |
| `DataIntegrityViolationException` (backstop race) | 409 | `CONFLICTO_UNICIDAD` | null, mensaje "Conflicto de unicidad de datos" (ERR-010) |
| **`CuentaBloqueadaException`** | **422** | **`CUENTA_BLOQUEADA`** | **null (BR-003, ERR-008, A-001)** |
| **`MonedaNoSoportadaException`** | **422** | **`MONEDA_NO_SOPORTADA`** | **null (BR-005, ERR-009)** |
| `Exception` (fallback) | 500 | `ERROR_INTERNO` | null (sin leak de stack) |

Notas:

- **`MoneyInvalidoException` NO se mapea** (cae en el fallback 500): es un
  invariante interno del VO (BR-002, AC-026) inalcanzable desde entradas de
  usuario en este sprint — solo la factory construye `Money`, siempre con 0.
  Se documenta aquí para que no se agregue un mapeo especulativo.
- Los `401`/`403` de Spring Security (token ausente/inválido, rol incorrecto)
  los escriben el entry point y el access-denied handler de `SecurityConfig`
  con el mismo envelope (ERR-006, ERR-007) — intactos.
- `CbuInvalidoException` usa el código propio `CBU_INVALIDO` exigido por
  ERR-005 (a diferencia de `DniInvalidoException`, que usa `DATOS_INVALIDOS`);
  el `details` con `campo:"cbu"` sigue la simetría del mapeo de `DNI`.

### 8.6 Configuración (`application.yml` / `application-test.yml`)

**Sin cambios.** No hay propiedades nuevas (la generación de CBU usa JDK
`SecureRandom`; no hay knobs configurables en este sprint).

---

## 9. Build & Dependencies

**Ninguna dependencia nueva** (misma conclusión que SPEC-003 §9):

- CBU: `SecureRandom` (JDK 21) — sin dependencias.
- `Money`: `BigDecimal` (JDK) — sin dependencias.
- Tests: `spring-boot-starter-test`, `spring-security-test`, Testcontainers,
  ArchUnit — ya presentes.
- No se agrega `spring-boot-starter-validation` (los DTOs no usan bean
  validation; las reglas viven en `AperturaValidator` y en los VOs).

---

## 10. Testing Strategy

### Unit (JUnit 5 + Mockito; sin Spring)

| Clase | Cobertura | AC |
| --- | --- | --- |
| `domain/CBUTest` | 22 dígitos → OK; inválidos: 21/23 dígitos, no numérico, null/vacío → `CbuInvalidoException` | AC-025 |
| `domain/MoneyTest` | `Money(0, ARS)` y montos positivos → OK; monto negativo → `MoneyInvalidoException`; null monto/moneda → `MoneyInvalidoException`; `Money.cero(ARS)` → 0 | AC-026 |
| `domain/MonedaTest` | "ARS"/"USD" → OK (formato); "ars"/"AR"/"abc1"/null → `MonedaInvalidaException` (defensivo; el 422 de USD se prueba en aplicación) | — |
| `domain/CuentaTest` | constructor de reconstrucción conserva todos los campos (incl. version); **`bloquear()`**: ACTIVA → BLOQUEADA; `bloquear()` sobre BLOQUEADA → `CuentaBloqueadaException` (guarda BR-003, A-001) | AC-027 (dominio) |
| `domain/CuentaFactoryTest` | `crear` con `CAJA_AHORRO` y con `CUENTA_CORRIENTE` → id null, version null, saldo 0, estado ACTIVA, moneda/cbu/createdAt dados; `tipo` null → `IllegalArgumentException` | AC-024 |
| `application/AperturaValidatorTest` | tipo null/blank/"AHORRO"/"caja_ahorro" → `DatosInvalidosException("tipo")`; moneda "ar"/"ars"/"AR S" → `DatosInvalidosException("moneda")`; tipo y moneda válidos (o moneda null) → OK; cortocircuito (primer error gana) | AC-004 (lógica) |
| `application/AbrirCuentaUseCaseTest` | happy path `CAJA_AHORRO` sin moneda → se persiste con moneda ARS (default FR-008) y `ClienteRepository.findById` consultado; happy path `CUENTA_CORRIENTE` con "ARS" explícito; moneda "USD" → `MonedaNoSoportadaException` (AC-003); `clienteId` inexistente → `ClienteNoEncontradoException` (AC-005) y NO se genera CBU ni se persiste; **regeneración de CBU (AC-028)**: `existsByCbu` con `thenReturn(true, false)` → `ArgumentCaptor` sobre `existsByCbu` captura dos CBUs distintos, `save` llamado exactamente una vez con el segundo CBU (22 dígitos); validador invocado ANTES de los chequeos de repositorio | AC-001 (lógica), AC-002, AC-003, AC-005, AC-028 |
| `application/ObtenerCuentaUseCaseTest` | ADMIN consulta cualquier id → OK; CLIENTE con su id → OK (AC-009); CLIENTE con id ajeno → `AccesoDenegadoException` (AC-010); CLIENTE con claim null → `AccesoDenegadoException`; id inexistente (cualquier rol) → `CuentaNoEncontradaException` (AC-011); **orden 404 → 403**: id inexistente + CLIENTE con claim ajeno → 404 (no 403) | AC-008, AC-009, AC-010, AC-011 |
| `application/ObtenerCuentaPorCbuUseCaseTest` | ADMIN con cbu existente → OK (AC-013); CLIENTE cbu propio → OK (AC-014); CLIENTE cbu ajeno → `AccesoDenegadoException` (AC-015); cbu inexistente → `CuentaNoEncontradaException` (AC-016); cbu malformado (21 dígitos) → `CbuInvalidoException` (AC-017) | AC-013..AC-017 |
| `application/ListarCuentasUseCaseTest` | CLIENTE con claim → `findByClienteId(claim)` y NO `findAll` (AC-018); CLIENTE con claim null → `AccesoDenegadoException` (AC-023); CLIENTE con `clienteIdFiltro` → `AccesoDenegadoException` (AC-019); ADMIN con filtro válido → `findByClienteId(filtro)` + `ClienteRepository.findById` consultado (AC-020); ADMIN con filtro inexistente → `ClienteNoEncontradoException` (AC-021); ADMIN sin filtro → `findAll` (AC-022) | AC-018..AC-023 |
| `infrastructure/adapter/web/GlobalExceptionHandlerTest` | Instancia el handler (constructor sin args; los métodos no usan Spring) y verifica: `handleCuentaNoEncontrada` → `ErrorResponse("CUENTA_NO_ENCONTRADA", ...)` y anotación `@ResponseStatus` = 404; `handleCbuInvalido` → 400 `CBU_INVALIDO` con `details[0].campo == "cbu"`; `handleCuentaBloqueada` → 422 `CUENTA_BLOQUEADA` (AC-027 mapeo); `handleMonedaNoSoportada` → 422 `MONEDA_NO_SOPORTADA`; `handleMonedaInvalida` → 400 `DATOS_INVALIDOS` con campo "moneda" (la anotación se lee con reflexión sobre el método: `@ResponseStatus` + `value()`) | AC-027 (mapeo), ERR-003/005/008/009 |

### Integración (Spring Boot Test + Testcontainers + MockMvc)

`CuentaApiIntegrationTest extends BaseIntegrationTest`, con su propia
`@TestConfiguration TokenConfig` anidada (patrón de `ClienteApiIntegrationTest`
— Spring Boot solo auto-registra `@TestConfiguration` anidadas en la clase de
test ejecutada, no en superclases; `BaseIntegrationTest` no cambia).

Helper: `crearClienteAdmin(...)` (POST `/api/v1/clientes` con
`tokens.tokenAdmin("admin-test")`, reutiliza el patrón existente) y
`crearCuentaAdmin(clienteId, tipo, moneda)` (POST `/api/v1/cuentas`).

Un método por criterio de aceptación:

| Cobertura | AC |
| --- | --- |
| `POST` ADMIN, `CAJA_AHORRO`, sin `moneda` → `201`, `Location`, body: `cbu` de 22 dígitos (regex `^[0-9]{22}$`), `saldo` 0, `estado` "ACTIVA", `moneda` "ARS", `createdAt` presente | AC-001 |
| `POST` ADMIN, `CUENTA_CORRIENTE`, `moneda` "ARS" explícita → `201` | AC-002 |
| `POST` con `moneda` "USD" → `422` con `code == MONEDA_NO_SOPORTADA` | AC-003 |
| `POST` con `tipo` inválido ("ahorro") → `400` con `details[0].campo == "tipo"` | AC-004 |
| `POST` con `clienteId` inexistente → `404` con `code == CLIENTE_NO_ENCONTRADO` | AC-005 |
| `POST` sin token → `401` con envelope `NO_AUTENTICADO` | AC-006 |
| `POST` con token CLIENTE → `403` con `ACCESO_DENEGADO` | AC-007 |
| `GET /{id}` ADMIN (cuenta creada antes) → `200` con todos los campos | AC-008 |
| `GET /{id}` CLIENTE con `clienteId` del claim == dueño → `200` | AC-009 |
| `GET /{id}` CLIENTE con claim ajeno → `403` `ACCESO_DENEGADO` | AC-010 |
| `GET /{id}` id inexistente → `404` `CUENTA_NO_ENCONTRADA` | AC-011 |
| `GET /{id}` con id no numérico ("abc") → `400` | AC-012 |
| `GET /cbu/{cbu}` ADMIN con cbu existente → `200` | AC-013 |
| `GET /cbu/{cbu}` CLIENTE con cbu propio → `200` | AC-014 |
| `GET /cbu/{cbu}` CLIENTE con cbu ajeno → `403` | AC-015 |
| `GET /cbu/{cbu}` cbu inexistente (22 dígitos válidos) → `404` `CUENTA_NO_ENCONTRADA` | AC-016 |
| `GET /cbu/{cbu}` cbu malformado (21 dígitos) → `400` `CBU_INVALIDO` | AC-017 |
| `GET /api/v1/cuentas` CLIENTE → `200`, lista con solo sus cuentas | AC-018 |
| `GET /api/v1/cuentas?clienteId=x` CLIENTE → `403` | AC-019 |
| `GET /api/v1/cuentas?clienteId=x` ADMIN con x válido → `200`, solo cuentas de x | AC-020 |
| `GET /api/v1/cuentas?clienteId=x` ADMIN con x inexistente → `404` | AC-021 |
| `GET /api/v1/cuentas` ADMIN sin parámetro → `200`, todas las cuentas ordenadas por id asc (verificación de orden como AC-020 de clientes) | AC-022 |
| `GET /api/v1/cuentas` CLIENTE sin `clienteId` en el claim → `403` | AC-023 |

Verificación de persistencia y constraints (AC-029):

| Cobertura | AC |
| --- | --- |
| Persistencia real: la cuenta creada sobrevive entre requests (consulta posterior por id/cbu) y el listado refleja datos creados en tests anteriores | AC-029 |
| Constraint `UNIQUE (cbu)` con `@Autowired JdbcTemplate`: insert directo `INSERT INTO cuentas (cliente_id, cbu, tipo, saldo, moneda, estado, version, created_at) VALUES (…)` con un `cbu` ya persistido → `assertThrows(DataIntegrityViolationException.class)` | AC-029 (BR-001) |
| Constraint FK: insert directo con `cliente_id` inexistente → `DataIntegrityViolationException` | AC-029 (ERR-002) |
| Mapeo `DataIntegrityViolationException` → 409: invocación directa del bean `GlobalExceptionHandler` (autowired) con una instancia de la excepción → envelope `CONFLICTO_UNICIDAD` (ERR-010; no existe camino HTTP que la dispare en este sprint — el `cbu` nunca llega por request) | AC-029 (409) |
| Envelope JSON en 400/401/403/404/422 (verificación de `code`/`message`/`details` en los tests de arriba) | AC-029 |

### Arquitectura (ArchUnit) — `LayerArchitectureTest`

**Sin cambios de reglas** (AC-030). Las clases nuevas deben cumplir las 4
reglas existentes:

1. `domain` (`Cuenta`, `TipoCuenta`, `EstadoCuenta`, `CBU`, `Money`,
   `Moneda`, `CuentaFactory`, `CuentaRepository`, excepciones) sin
   dependencias de Spring/JPA/otras capas (los VOs usan solo `java.math`/
   `java.time`/`java.util.regex`/`java.security` — todos bajo `java..`).
2. `application` (`AbrirCuentaCommand`, queries, `AperturaValidator`, use
   cases) dependiendo solo de `domain`/`application`/`java` — `SecureRandom`
   es `java.security`, permitido; los use cases reciben los repositorios como
   puertos (interfaces de dominio), nunca clases de infraestructura.
3. Spring/controllers solo en `infrastructure` (`CuentaController`,
   `CuentaJpaEntity`, `CuentaRepositoryAdapter`, `CuentaBeansConfig`).
4. La regla existente de `application` ya contempla la dependencia entre
   clases del mismo paquete `application` (comentario en el test: se permite
   `com.banco.application..` para no falsear la regla) — los use cases
   dependen de commands/queries/validators del mismo eslabón, como hoy.

---

## 11. ADR

**No se crea ADR nuevo.** Las decisiones de este diseño son todas consistentes
con ADR-001..ADR-005 y con ARCHITECTURE.md §4/§5/§7/§8:

- La **Factory por tipo** está sancionada por ARCHITECTURE.md §5 y la spec
  BR-004; la implementación elegida (una clase `CuentaFactory` con dispatch
  por `switch`, sin subclases) no introduce un patrón nuevo ni condiciona
  specs futuras (SPEC-004/005 consumen el agregado y sus métodos, no la
  factory).
- La **generación de CBU** (formato y unicidad con regeneración) es una
  asunción documentada de la spec (A-002); la implementación es un detalle de
  la capa de aplicación (SecureRandom + `existsByCbu`), sin impacto
  arquitectónico.
- No se introducen puertos, frameworks ni mecanismos que los ADRs existentes
  no cubran; `application` sigue libre de Spring (regla ArchUnit intacta).

Ver §13 (Alternativas) para el razonamiento de las decisiones menores.

---

## 12. Risks

- **Orden 404 vs 403 en consulta por id/cbu:** el `id`/`cbu` no es el
  `clienteId`, así que la propiedad solo se conoce tras cargar la cuenta
  (orden invertido respecto a `ObtenerClienteUseCase`). Comportamiento
  documentado en §4 y cubierto por tests (un `CLIENTE` con claim ajeno que
  consulta un id inexistente recibe `404`, no `403`).
- **Colisión de CBU:** espacio de `10^14` candidatos → probabilidad
  despreciable; regeneración con tope de 5 intentos (AC-028) + constraint
  `UNIQUE (cbu)` como garantía final (ERR-010 → 409). El `IllegalStateException`
  del tope es prácticamente inalcanzable y falla de forma determinista.
- **`existsByClienteId` excluido del puerto:** decisión deliberada (§13); si
  un reviewer espera el método, la justificación está documentada (AF-004 se
  resuelve con `ClienteRepository.findById`; agregar el método sería dead
  code).
- **`created_at TIMESTAMP WITH TIME ZONE` vs `TIMESTAMP` de la spec §10:**
  desviación necesaria para `ddl-auto: validate` con `Instant` (lección de
  V1); documentada en el SQL y en §6.1.
- **Longitudes `VARCHAR` (`tipo`/`estado`) vs `@Column(length)`:** la entidad
  JPA debe declarar exactamente `length=20` para `tipo` y `estado`, y
  `length=22` para `cbu`, o `ddl-auto: validate` fallará (misma mecánica que
  `rol VARCHAR(7)` de V2).
- **`@Version` en la entidad JPA:** el adapter debe mapear `version` en ambas
  direcciones (el dominio la conserva; `save` con id null → INSERT con
  version 0). Si se omitiera el mapeo, el optimistic lock no funcionaría en
  SPEC-004/005.
- **`DataIntegrityViolationException` sin campo (backstop 409):** idéntico a
  SPEC-001 §12 — solo ocurre en carreras concurrentes; el camino normal de
  unicidad lo resuelve la regeneración.
- **Tests omitidos sin Docker:** `disabledWithoutDocker = true` omite la
  cobertura de integración localmente sin Docker; CI la cubre (riesgo residual
  ya documentado en SPEC-001 §12).
- **Usernames duplicados entre métodos de integración:** cada test usa
  usernames dummy únicos en `JwtTokenFactory` ("admin-test" etc.); los
  contenedores Testcontainers son por clase, no por método (riesgo ya
  documentado en SPEC-003 §12).

---

## 13. Alternatives Considered

- **Subclases de factory (`CajaAhorroFactory` / `CuentaCorrienteFactory`):**
  descartadas — ambos tipos son idénticos en este sprint (saldo 0, estado
  ACTIVA; solo difiere el campo `tipo`). Una sola `CuentaFactory` con dispatch
  por `switch` es la solución más simple (AGENTS.md §11) y cumple BR-004 y
  ARCHITECTURE.md §5; el punto de dispatch queda explícito para la divergencia
  futura (comisiones/descubierto de cuenta corriente, SPEC-005 — Strategy).
- **Factory como método estático de `Cuenta` (estilo `Cliente.crear`):**
  descartada — AC-024 nombra "la Factory" como unidad testeable y BR-004 exige
  explícitamente "una Factory según el tipo"; una clase `CuentaFactory` hace el
  patrón explícito y deja el constructor público de `Cuenta` solo para
  reconstrucción (convención del proyecto).
- **`existsByClienteId` en `CuentaRepository`:** descartado — AF-004/ERR-004
  ("cliente inexistente en el listado") es una consulta al `Cliente`, no a
  `cuentas`: se resuelve con el `ClienteRepository.findById` existente (un
  cliente sin cuentas debe devolver lista vacía `200`, nunca `404`; un método
  `existsByClienteId` en cuentas daría `404` incorrecto). Agregarlo sería dead
  code.
- **CoR para la validación de apertura (`AperturaValidador` por regla):**
  descartado — 2 campos no justifican 4 clases + orquestador; una clase con
  chequeos secuenciales (corte ante el primer error) es más simple (misma
  decisión que `RegistroValidator` en SPEC-003 §8.2). La CoR de clientes se
  mantiene por su tamaño (6 campos, 3 etapas).
- **`Moneda` como enum `{ ARS }`:** descartado — un enum no distinguiría `USD`
  (formato válido, no soportada → **422** AC-003/ERR-009) de `xyz` (formato
  inválido → **400** ERR-001); el record con validación de formato `^[A-Z]{3}$`
  permite ambos caminos, y la decisión ARS-only vive en el use case.
- **`CbuGenerator` como clase inyectable separada:** descartado — la
  generación es un método privado del use case (SecureRandom + loop con
  `existsByCbu`); AC-028 se prueba con stubbing consecutivo y `ArgumentCaptor`
  sin necesidad de una clase nueva (AGENTS.md §11).
- **Verificación de propiedad en el controller/filtro:** descartada —
  ARCHITECTURE.md §8 exige la verificación en la capa de aplicación; los use
  cases son testeables sin HTTP (misma decisión que SPEC-001 §13).
- **`@Transactional` en use cases:** descartado — introduciría Spring en
  `application` (violaría la regla ArchUnit); la apertura es una sola
  escritura y la unicidad la garantiza la BD (SPEC-001 §13).
- **Bean validation en DTOs:** descartado — duplicaría las reglas en la capa
  web; `AperturaValidator` + VOs son la única fuente de reglas (SPEC-001 §13).
- **Index sobre `cliente_id`:** incluido (no descartado) — FR-006 consulta por
  `clienteId`; un índice de una columna es práctica estándar, no especulación
  (a diferencia de V2, donde ningún query usaba la columna).

---

## 14. Decision

Implementar SPEC-002 con:

- **Dominio:** agregado `Cuenta` (id, clienteId, CBU cbu, TipoCuenta tipo,
  Money saldo, Moneda moneda, EstadoCuenta estado, Instant createdAt, Long
  version) con constructor público de reconstrucción, `bloquear()` y guarda
  `verificarActiva()` que lanza `CuentaBloqueadaException` (BR-003, A-001);
  enums `TipoCuenta`/`EstadoCuenta`; VOs `CBU` (22 dígitos), `Money`
  (BigDecimal + Moneda, nunca negativo), `Moneda` (record String `^[A-Z]{3}$`);
  `CuentaFactory` (dominio, dispatch por `switch` sobre el tipo, saldo 0 y
  estado ACTIVA); puerto `CuentaRepository` (save, findById, findByCbu,
  findByClienteId, findAll, existsByCbu — sin `existsByClienteId`, ver §13);
  excepciones `CuentaNoEncontradaException` (404), `CuentaBloqueadaException`
  (422), `CbuInvalidoException` (400), `MonedaNoSoportadaException` (422),
  `MonedaInvalidaException` (400 defensivo), `MoneyInvalidoException` (sin
  mapeo).
- **Aplicación:** `AbrirCuentaUseCase` (validación `AperturaValidator` clase
  única, default ARS, `MonedaNoSoportadaException` para no-ARS, chequeo del
  titular vía `ClienteRepository.findById`, CBU por SecureRandom con
  regeneración ante colisión y tope de 5 intentos, `CuentaFactory.crear`,
  `save`), `ObtenerCuentaUseCase` y `ObtenerCuentaPorCbuUseCase` (404 antes
  que 403; propiedad de CLIENTE en aplicación), `ListarCuentasUseCase`
  (CLIENTE: claim obligatorio y sin parámetro → 403; ADMIN: filtro opcional
  con validación del cliente → 404). Beans en `CuentaBeansConfig`.
- **Infraestructura:** `CuentaController` (POST /api/v1/cuentas → 201 +
  Location; GET /{id}; GET /cbu/{cbu}; GET ?clienteId=), `AbrirCuentaRequest`,
  `CuentaDto`, `CuentaJpaEntity` (con `@Version`), `CuentaJpaRepository`,
  `CuentaRepositoryAdapter`; `SecurityConfig` con los matchers 6–9 de §8.4
  (listado exacto y `/cbu/**` antes que `/{id}`); `GlobalExceptionHandler`
  con los mapeos de §8.5.
- **Migración `V3__cuentas.sql`** (tabla `cuentas` con `UNIQUE (cbu)`, FK a
  `clientes(id)`, índice por `cliente_id`, `created_at TIMESTAMP WITH TIME
  ZONE` — desviación documentada del §10 de la spec).
- **Tests:** unit (CBU/Money/Moneda/Cuenta/CuentaFactory/AperturaValidator/
  4 use cases/GlobalExceptionHandlerTest), integración Testcontainers
  (`CuentaApiIntegrationTest`, AC-001..AC-023 + persistencia + constraints vía
  JdbcTemplate, AC-029), ArchUnit sin cambios de reglas (AC-030).
- **Sin ADR nuevo** (§11) y sin dependencias nuevas (§9).
