# Architecture — SPEC-004 (Transferencias entre Cuentas)

## 1. Feature

Transferencia atómica de dinero entre cuentas internas (`POST
/api/v1/transferencias`): un `CLIENTE` debita una cuenta propia y acredita otra
cuenta (propia o de terceros) identificada por `CBU`, en una única transacción
de base de datos (FR-002), registrando exactamente dos `Movimiento` (FR-003) y
emitiendo el evento de dominio `TransferenciaRealizada` (FR-004). Además,
consulta del historial de movimientos de una cuenta (`GET
/api/v1/cuentas/{id}/movimientos`, FR-005).

SPEC-002 (cuentas) **sí está implementada, revisada y mergeada** (PR #20, rama
`testing`): existen el agregado `Cuenta` (con `@Version`, `bloquear()` y guarda
`verificarActiva()`), los VOs `CBU`/`Money`/`Moneda`, el puerto
`CuentaRepository` y la migración `V3__cuentas.sql`. SPEC-004 **extiende** ese
modelo aprobado (ADR-007): `Cuenta` gana los métodos de dominio
`debitar`/`acreditar`, `Money` gana las operaciones aritméticas y la factory
`ars`, `CuentaRepository` gana la agregación del límite diario, y se agregan
`Movimiento`, `TipoMovimiento` y `V4__movimientos.sql`. **No** se implementan
los endpoints de apertura/consulta/listado de cuentas (ya existen en SPEC-002;
out of scope de esta spec).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-004-transferencias.md` (APROBADA — fuente de verdad;
  FR-001..FR-005, BR-001..BR-007, AF-001..AF-003, ERR-001..ERR-009,
  AC-001..AC-023, asunciones A-001..A-007).
- `docs/adr/ADR-001` (monolito hexagonal + DDD), `ADR-002` (PostgreSQL +
  Flyway), `ADR-003` (JWT + Spring Security + RBAC), y los nuevos `ADR-006`
  (sección 11): frontera transaccional y estrategia de concurrencia (su
  decisión 1 — agregado `Cuenta` mínimo desde cero — queda reemplazada por
  ADR-007) y `ADR-007` (sección 11): reconciliación de SPEC-004 sobre los
  VOs/agregado de SPEC-002 (implementada, PR #20).

---

## 3. Affected Modules

- **`backend/src/main/java/com/banco/domain`** — **modificados (ya existen
  desde SPEC-002, aprobados e implementados):** `model/Cuenta` (se agregan
  `debitar`/`acreditar`; `saldo` deja de ser `final`), `vo/Money` (se agregan
  `sumar`/`restar`/`esMayorQue`/`esMayorOIgualQue`/`esCero` y la factory
  `ars`), `port/CuentaRepository` (se agrega
  `montoTotalTransferenciasSalientesDelDia`). **Nuevos de SPEC-004:**
  `model/Movimiento`, enum `model/TipoMovimiento`, `port/MovimientoRepository`,
  `port/TransferenciaEventPublisher`, evento `event/TransferenciaRealizada`,
  excepciones `exception/SaldoInsuficienteException`,
  `exception/LimiteDiarioExcedidoException`,
  `exception/AutoTransferenciaException`,
  `exception/MonedaIncompatibleException`. VOs/enums `vo/CBU`, `vo/Moneda`,
  `model/TipoCuenta`, `model/EstadoCuenta` y `factory/CuentaFactory` provienen
  de SPEC-002 sin cambios (ADR-007); se reutilizan `CuentaNoEncontradaException`,
  `CuentaBloqueadaException` y `CbuInvalidoException` (SPEC-002).
- **`backend/src/main/java/com/banco/application`** — nuevos:
  `command/TransferirCommand`, `query/ObtenerMovimientosQuery`,
  `validator/DatosTransferencia`, `validator/TransferenciaValidada`,
  `validator/TransferValidator`, `usecase/TransferirUseCase`,
  `usecase/ObtenerMovimientosUseCase`, `usecase/TransferenciaConfirmacion`
  (resultado de aplicación — ver §8.3).
- **`backend/src/main/java/com/banco/infrastructure`** — **modificados (ya
  existen desde SPEC-002):** `adapter/persistence/CuentaJpaRepository` (sin
  cambios), `adapter/persistence/CuentaRepositoryAdapter` (se agrega la
  delegación del límite diario a `MovimientoJpaRepository` — §8.10, ADR-007),
  `security/SecurityConfig` (matchers nuevos — §8.5),
  `adapter/web/GlobalExceptionHandler` (mapeos nuevos — §8.6). **Nuevos de
  SPEC-004:** `adapter/web/TransferenciaController`,
  `adapter/web/MovimientoController`, `adapter/web/TransferirRequest`,
  `adapter/web/MovimientoDto`, `adapter/persistence/MovimientoJpaEntity`,
  `adapter/persistence/MovimientoJpaRepository`,
  `adapter/persistence/MovimientoRepositoryAdapter`,
  `service/TransferenciaService` (frontera transaccional — §8.8),
  `service/TransferenciaEventPublisherNoop`, `config/TransferenciaBeansConfig`.
- **`backend/src/main/resources`** — nueva migración
  `db/migration/V4__movimientos.sql` (§6.1; la tabla `cuentas` proviene de la
  `V3__cuentas.sql` de SPEC-002); `application.yml` agrega
  `banco.negocio.limite-diario-transferencias` (§8.7).
- **`backend/src/test`** — nuevos: `support/CuentaTestHelper` (A-003),
  `domain/MovimientoTest`, `application/TransferValidatorTest`,
  `application/TransferirUseCaseTest`,
  `application/ObtenerMovimientosUseCaseTest`,
  `integration/TransferenciaApiIntegrationTest` (AC-001..AC-015),
  `integration/MovimientosApiIntegrationTest` (AC-016..AC-020).
  Modificados: `domain/MoneyTest`, `domain/CuentaTest` (operaciones nuevas de
  SPEC-004) y `src/test/resources/application-test.yml` (nueva propiedad §8.7).
- **No afectado:** frontend, `docker/`, `pom.xml` (sin dependencias nuevas),
  `LayerArchitectureTest` (reglas sin cambios — AC-023),
  `JwtService`/`JwtAuthenticationFilter`/`AuthenticatedUser`/`JwtTokenFactory`
  (mecanismo de autenticación intacto).

---

## 4. Application Flow

```text
REST (TransferenciaController: POST /api/v1/transferencias)   infrastructure.adapter.web
        ↓  TransferirRequest (record plano, sin anotaciones)
Security (JwtAuthenticationFilter → JwtService)               infrastructure.security
        ↓  resuelve AuthenticatedUser(clienteId, rol); matcher POST → hasRole("CLIENTE")
TransferenciaService.ejecutar(...)  [@Transactional — UNA transacción]  infrastructure.service
        ↓  delega en el use case (Java puro)
TransferirUseCase.ejecutar(TransferirCommand, clienteIdClaim)  application.usecase
        ↓  TransferValidator.validar(DatosTransferencia)  — CoR de 10 chequeos en orden
Domain (Cuenta.debitar/acreditar, Movimiento, CBU, Money,   domain
        CuentaRepository, MovimientoRepository, TransferenciaEventPublisher)
        ↓
Persistence (CuentaRepositoryAdapter → CuentaJpaEntity@Version,
             MovimientoRepositoryAdapter → MovimientoJpaEntity)  infrastructure.adapter.persistence
        ↓
PostgreSQL 16 (Flyway: V1+V2 de SPEC-001/003, V3 cuentas de SPEC-002,
             V4 movimientos de SPEC-004)                          db
```

**Flujo concreto de la transferencia (main flow de la spec, §6):**

1. `TransferenciaController` construye `TransferirCommand(cuentaOrigenId,
   cbuDestino, monto)` y resuelve `AuthenticatedUser` del `SecurityContext`
   (mismo mecanismo que `ClienteController`); delega en `TransferenciaService`.
2. `TransferenciaService` (`@Transactional`) delega en `TransferirUseCase`
   (toda la operación corre en una única transacción de BD — FR-002).
3. El use case construye `DatosTransferencia` (trim de `cbuDestino`) y ejecuta
   `TransferValidator.validar(...)`, que corre la cadena de 10 chequeos en
   orden y corta ante el primer error (§8.4), cargando las cuentas origen y
   destino y devolviendo `TransferenciaValidada(origen, destino, monto)`.
4. `Instant fecha = Instant.now()` (una sola fecha compartida — FR-003);
   `origen.debitar(monto)` (BR-001), `destino.acreditar(monto)` (BR-001).
5. Se persisten en el mismo tx: `save(origen)`, `save(destino)`, y los dos
   `Movimiento` (`TRANSFERENCIA_SALIENTE` en origen con contraparte = destino;
   `TRANSFERENCIA_ENTRANTE` en destino con contraparte = origen — FR-003).
6. Se emite `TransferenciaRealizada(monto, cbuOrigen, cbuDestino, fecha,
   idMovimientoSaliente)` vía el puerto `TransferenciaEventPublisher` (FR-004;
   sin suscriptores en este sprint — el emisor no-op loguea).
7. Se responde `201 Created` con `TransferenciaConfirmacion(idTransferencia =
   id del Movimiento saliente, monto, cbuDestino, fechaHora)` (FR-001, A-007).

**Flujo del historial (FR-005):**

1. `MovimientoController` construye `ObtenerMovimientosQuery(cuentaId, rol,
   clienteIdClaim)` desde `AuthenticatedUser`; delega en
   `ObtenerMovimientosUseCase`.
2. El use case carga la cuenta → 404 si no existe (ERR-002, AC-019); si el rol
   es `CLIENTE` y la cuenta no es suya → 403 (ERR-006, AC-017 — A-005).
3. Devuelve `movimientoRepository.findByCuentaIdOrderByFechaDesc(cuentaId)`
   (orden por fecha descendente, sin paginación — A-006).

**Nota de transaccionalidad:** los use cases son Java puro (sin Spring), por lo
que el `@Transactional` que garantiza FR-002 vive en `TransferenciaService`
(infraestructura) — ver §8.8 y ADR-006.

---

## 5. Components

### 5.1 Entry points / presentación — `infrastructure.adapter.web`

| Método | Ruta | Rol requerido | Status OK | Error esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/transferencias` | `CLIENTE` (solo; A-005) | `201 Created` + `TransferenciaConfirmacion` | 400, 401, 403, 404, 409, 422 |
| `GET` | `/api/v1/cuentas/{id}/movimientos` | `ADMIN` (cualquiera) o `CLIENTE` (propias) | `200 OK` + `List<MovimientoDto>` | 401, 403, 404 |

- `TransferenciaController` (`@RestController @RequestMapping("/api/v1/transferencias")`):
  solo construye el command, resuelve el `AuthenticatedUser` y delega en
  `TransferenciaService`; mapea el resultado a la respuesta `201`. Sin reglas
  de negocio. Sin `Location` header (no existe `GET /transferencias/{id}`; la
  spec no lo exige — misma decisión que `AuthController` en SPEC-003).
- `MovimientoController` (`@RestController @RequestMapping("/api/v1/cuentas")`):
  `@GetMapping("/{id}/movimientos")`; construye el query y delega en
  `ObtenerMovimientosUseCase`; mapea `List<Movimiento>` → `List<MovimientoDto>`.
- DTOs de entrada: records planos sin anotaciones de validación (convención —
  SPEC-001 §5.1). JSON malformado → `HttpMessageNotReadableException` → `400`
  (mapeo existente).
- `SecurityConfig`: dos matchers nuevos (§8.5). Resto del chain sin cambios.

### 5.2 Aplicación — `application`

- `TransferirUseCase.ejecutar(TransferirCommand, Long clienteIdAutenticado)` →
  `TransferenciaConfirmacion`. Dependencias: `CuentaRepository`,
  `MovimientoRepository`, `TransferValidator`, `TransferenciaEventPublisher`.
- `ObtenerMovimientosUseCase.ejecutar(ObtenerMovimientosQuery)` →
  `List<Movimiento>`. Dependencias: `CuentaRepository`, `MovimientoRepository`.
- Validación de la transferencia: `DatosTransferencia` (record, normaliza
  `cbuDestino` con trim) + `TransferValidator` (**clase única** con 10 chequeos
  secuenciales que corta ante el primer error — ver §8.2/§8.4; decisión de no
  usar la CoR multi-clase, ver §13).
- Resultado: `TransferenciaConfirmacion(Long idTransferencia, BigDecimal monto,
  String cbuDestino, Instant fechaHora)` — record de la capa de aplicación que
  es a la vez el contrato de salida del use case y el body de la respuesta 201
  (desviación documentada en §8.3).
- Los beans se declaran en `infrastructure.config.TransferenciaBeansConfig`
  (la aplicación no usa anotaciones Spring).

### 5.3 Dominio — `domain`

- `model/Cuenta` — agregado raíz **de SPEC-002** (id, clienteId, CBU cbu,
  TipoCuenta tipo, Money saldo, Moneda moneda, EstadoCuenta estado, Instant
  createdAt, Long version; constructor público de reconstrucción, `bloquear()`
  y guarda privada `verificarActiva()` — BR-003/A-001; creación centralizada en
  `CuentaFactory`, sin factory estática). **Extendido por SPEC-004 (ADR-007):**
  el campo `saldo` deja de ser `final`; nuevos métodos de dominio
  `debitar(Money)` (invoca primero `verificarActiva()` — BR-002/ERR-003; si
  monto > saldo → `SaldoInsuficienteException` — BR-001) y `acreditar(Money)`
  (invoca primero `verificarActiva()`).
- `model/Movimiento` — entidad **nueva** (id, cuentaId, TipoMovimiento tipo,
  Money monto, Instant fecha, Long cuentaContraparteId nullable) + factory
  `crear(...)`.
- `model/TipoCuenta` { `CAJA_AHORRO`, `CUENTA_CORRIENTE` } y
  `model/EstadoCuenta` { `ACTIVA`, `BLOQUEADA` } — enums de SPEC-002, sin
  cambios; `model/TipoMovimiento` { `DEPOSITO`, `RETIRO`,
  `TRANSFERENCIA_ENTRANTE`, `TRANSFERENCIA_SALIENTE` } — nuevo de SPEC-004.
  Enums planos (almacenados como String, convención de SPEC-001/003).
- `vo/CBU` — record de SPEC-002 que valida en el constructor: exactamente 22
  dígitos (formato real del CBU argentino; ver §8.2 y nota sobre el ejemplo de
  la spec en §12). Lanza `CbuInvalidoException`.
- `vo/Money` — record `(BigDecimal monto, Moneda moneda)` **de SPEC-002**
  (valida monto ≥ 0 → `MoneyInvalidoException`; `Money.cero(Moneda)`).
  **Agregado por SPEC-004 (ADR-007):** operaciones `sumar`/`restar` con
  `MathContext.DECIMAL128` (sin `double` — `ARCHITECTURE.md` §6),
  comparaciones `esMayorQue`, `esMayorOIgualQue`, `esCero` (por `compareTo`) y
  factory `static Money ars(BigDecimal)` (construye `Moneda("ARS")`). La escala
  ≤ 2 del monto (BR-003) la verifica el validador (§8.4), no el VO.
- `vo/Moneda` — record `(String codigo)` **de SPEC-002** (valida `^[A-Z]{3}$`
  → `MonedaInvalidaException`; decisión "record con String, no enum" — SPEC-002
  §13). BR-007 (misma moneda) se evalúa por igualdad de `Money.moneda()`
  (código ISO 4217 alpha-3; ADR-007). MVP: solo ARS.
- `port/CuentaRepository` — **de SPEC-002** (`save`, `findById`, `findByCbu`,
  `findByClienteId`, `findAll`, `existsByCbu`); SPEC-004 **agrega**
  `montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia)` →
  `Money` (BR-004, §8.10, ADR-007).
- `port/MovimientoRepository` — `save`, `findByCuentaIdOrderByFechaDesc`
  (FR-005). **Puerto separado** (decisión en §8.2/§13).
- `port/TransferenciaEventPublisher` — `void publicar(TransferenciaRealizada
  evento)` (FR-004; abstracción para que `application` emita el evento sin
  depender de infraestructura — mismo patrón que `TokenEmisor`/`PasswordHasher`).
- `event/TransferenciaRealizada` — `record (Money monto, CBU cbuOrigen, CBU
  cbuDestino, Instant fechaHora, Long idMovimientoSaliente)` (FR-004).
- `exception/*` — **nuevas de SPEC-004:** `SaldoInsuficienteException`
  (ERR-001 → 422), `LimiteDiarioExcedidoException` (ERR-007 → 422),
  `AutoTransferenciaException` (ERR-008 → 422), `MonedaIncompatibleException`
  (ERR-009 → 422). **Reutilizadas de SPEC-002:** `CuentaNoEncontradaException`
  (ERR-002 → 404), `CuentaBloqueadaException` (ERR-003 → 422; la lanza la
  guarda `verificarActiva()`), `CbuInvalidoException` (defensiva del VO `CBU`;
  ver §8.6). Se reutilizan además `DatosInvalidosException` (ERR-004 monto y
  cbuDestino, 400) y `AccesoDenegadoException` (ERR-006, 403).

### 5.4 Persistencia — `infrastructure.adapter.persistence`

- `CuentaJpaEntity` — **ya existe (SPEC-002)**: `@Entity @Table(name =
  "cuentas")` con `@Version Long version` (BR-006) — sin cambios en SPEC-004
  (ver §6.1/§8.8 para los detalles de `ddl-auto: validate`).
- `CuentaJpaRepository extends JpaRepository<CuentaJpaEntity, Long>` — **ya
  existe (SPEC-002)** (`findByCbu`, `findAllByOrderByIdAsc`,
  `findByClienteIdOrderByIdAsc`, `existsByCbu`); SPEC-004 usa `findById`/
  `findByCbu` — sin cambios.
- `MovimientoJpaEntity` (`@Entity @Table(name = "movimientos")`).
- `MovimientoJpaRepository extends JpaRepository<MovimientoJpaEntity, Long>`:
  `List<MovimientoJpaEntity> findByCuentaIdOrderByFechaDesc(Long)` y la
  `@Query` JPQL de agregación del límite diario (§8.10).
- `CuentaRepositoryAdapter implements CuentaRepository` (`@Component`) — **ya
  existe (SPEC-002)**: mapeo explícito (enums como String, `CBU`/`Money`/
  `Moneda` como String/BigDecimal, **version mapeada en ambos sentidos** —
  crítica para el lock optimista, §8.8). **Modificado por SPEC-004 (ADR-007):**
  inyecta además `MovimientoJpaRepository` para implementar
  `montoTotalTransferenciasSalientesDelDia` (dato vive en `movimientos`; ver
  §8.10).
- `MovimientoRepositoryAdapter implements MovimientoRepository` (`@Component`).

### 5.5 Seguridad — `infrastructure.security`

Sin cambios de mecanismo: el filtro JWT, `JwtService` y `AuthenticatedUser`
quedan intactos (ADR-004 §5). Solo `SecurityConfig` agrega dos matchers
(§8.5).

### 5.6 Configuración — `infrastructure.config` y `infrastructure.service`

- `TransferenciaBeansConfig` (`@Configuration`): bean `Money
  limiteDiarioTransferencias` desde `@Value("${banco.negocio.limite-diario-transferencias:200000}")`,
  bean `TransferValidator`, beans de los dos use cases (§8.7).
- `service/TransferenciaService` (`@Service`, método `@Transactional`): frontera
  transaccional que delega en `TransferirUseCase` (§8.8). No necesita
  declaración de bean (component scanning).
- `service/TransferenciaEventPublisherNoop` (`@Component implements
  TransferenciaEventPublisher`): emisor sin suscriptores (loguea a nivel debug;
  FR-004: la emisión se verifica en tests, AC-003).

### 5.7 Async work

Ninguno: sin jobs ni colas. El evento `TransferenciaRealizada` se publica de
forma **síncrona en memoria** dentro de la transacción (sin suscriptores; sin
outbox en el MVP — la auditoría/notificación queda para una evolución).

---

## 6. Data Changes

### 6.1 Migración Flyway

La tabla `cuentas` **ya existe**: la crea la migración de SPEC-002
`backend/src/main/resources/db/migration/V3__cuentas.sql` (con
`version BIGINT NOT NULL DEFAULT 0` para el `@Version` y `uq_cuentas_cbu`).
SPEC-004 **no la toca** (ADR-007). SPEC-004 agrega un único archivo:

Nuevo archivo `backend/src/main/resources/db/migration/V4__movimientos.sql`:

```sql
-- V4__movimientos.sql
-- Entidad Movimiento (SPEC-004, FR-003). La tabla cuentas proviene de la
-- migración V3__cuentas.sql de SPEC-002 (version ya incluida). Compatible con
-- ddl-auto: validate (lecciones de V1/V2/V3):
--   * Instant -> TIMESTAMP WITH TIME ZONE (Hibernate 6 mapea Instant a
--     "timestamp(6) with time zone"; un TIMESTAMP simple fallaría).
--   * BIGSERIAL <-> Long @Id, BIGINT <-> Long, DECIMAL(19,2)/NUMERIC(19,2)
--     <-> BigDecimal, VARCHAR(n) <-> @Column(length=n). Hibernate validate no
--     valida constraints UNIQUE/FK (los define Flyway, como en V1/V2/V3).

CREATE TABLE movimientos (
    id                     BIGSERIAL PRIMARY KEY,
    cuenta_id              BIGINT           NOT NULL REFERENCES cuentas(id),
    tipo                   VARCHAR(22)      NOT NULL,  -- TRANSFERENCIA_ENTRANTE/SALIENTE (22), DEPOSITO (8), RETIRO (6)
    monto                  DECIMAL(19,2)    NOT NULL,
    fecha                  TIMESTAMP WITH TIME ZONE NOT NULL,
    cuenta_contraparte_id  BIGINT           REFERENCES cuentas(id)  -- nullable (A-001)
);

CREATE INDEX idx_movimientos_cuenta_fecha ON movimientos (cuenta_id, fecha);
```

Notas:

- La columna `version` de `cuentas` (`BIGINT NOT NULL DEFAULT 0` ↔ `@Version
  @Column(nullable = false) Long version`) ya existe desde SPEC-002; SPEC-004
  solo la consume (mapeo en ambos sentidos en `CuentaRepositoryAdapter` —
  §8.8). Hibernate gestiona el valor (0 en insert, +1 por UPDATE); `validate`
  verifica tipo/ausencia de null.
- `cuenta_id` FK → `cuentas(id)` y `cuenta_contraparte_id` FK → `cuentas(id)`:
  las FK las define Flyway (Hibernate no las valida). Sin relación JPA mapeada
  (`Long` plano, convención de SPEC-001/003 — sin `AttributeConverter`).
- Índice `(cuenta_id, fecha)`: sirve al historial (FR-005) y al rango del día
  del límite diario (BR-004, §8.10).
- **Ojo del developer:** el borrador previo `V3__cuentas_y_movimientos.sql`
  (WIP pre-reconciliación) debe **eliminarse**: duplicaría la tabla `cuentas` y
  colisionaría con la `V3__cuentas.sql` de SPEC-002 (Flyway falla con dos
  migraciones del mismo número).

### 6.2 Entidades de dominio

`Cuenta` (agregado de SPEC-002, extendido con `debitar`/`acreditar` — ADR-007)
y `Movimiento` (nuevo) según §5.3. `Cuenta` lleva `version` como `Long` en el
modelo de dominio (se mapea en ambos sentidos en el adapter — sin esto el lock
optimista no funciona, §8.8).

### 6.3 Configuración

Nueva propiedad global `banco.negocio.limite-diario-transferencias` (default
`200000`, en ARS) en `application.yml` y `application-test.yml` (A-002,
§8.7), consistente con el naming `banco.security.*`.

---

## 7. External Integrations

- **PostgreSQL 16** (única integración): local
  `jdbc:postgresql://localhost:5433/banco` (usuario/contraseña `banco/banco`,
  ver `docker/docker-compose.yml`). Sin cambios.
- Tests de integración: Testcontainers `postgres:16` con `@ServiceConnection`
  (Postgres real — necesario para AC-012, concurrencia real). Sin cambios.
- **Sin** proveedores externos, mensajería ni frontend.

---

## 8. Detailed Design

### 8.1 File map completo

Packages bajo `backend/src/main/java/com/banco` salvo indicación.

**Archivos NUEVOS:**

**Domain (`com.banco.domain.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `model/TipoCuenta` | `enum TipoCuenta { CAJA_AHORRO, CUENTA_CORRIENTE }` | Tipo de cuenta — **ya existe (SPEC-002)**, sin cambios. |
| `model/EstadoCuenta` | `enum EstadoCuenta { ACTIVA, BLOQUEADA }` | Estado (BR-002) — **ya existe (SPEC-002)**, sin cambios. |
| `model/TipoMovimiento` | `enum TipoMovimiento { DEPOSITO, RETIRO, TRANSFERENCIA_ENTRANTE, TRANSFERENCIA_SALIENTE }` | Tipo de operación (ARCHITECTURE.md §4) — **nuevo de SPEC-004**. |
| `vo/Moneda` | `record Moneda(String codigo)` — **ya existe (SPEC-002)**; valida `^[A-Z]{3}$` → `MonedaInvalidaException` (400 defensivo); decisión "record con String, no enum" (SPEC-002 §13) | VO de moneda (ISO 4217 alpha-3). BR-007 se evalúa por igualdad de código (ADR-007); MVP: solo ARS. |
| `vo/CBU` | `record CBU(String valor)`; constructor compacto valida `^[0-9]{22}$` → `CbuInvalidoException` — **ya existe (SPEC-002)** | VO inmutable, único (BR de SPEC-002 §5.1; formato definido en §8.2). |
| `vo/Money` | `record Money(BigDecimal monto, Moneda moneda)` — **ya existe (SPEC-002)**: valida monto ≠ null, moneda ≠ null y `monto.signum() >= 0` → `MoneyInvalidoException`; `static Money cero(Moneda)`. **Agregadas por SPEC-004 (ADR-007):** `static Money ars(BigDecimal)` (`new Money(monto, new Moneda("ARS"))`); `Money sumar(Money)` / `Money restar(Money)` (con `MathContext.DECIMAL128`); `boolean esMayorQue(Money)`, `esMayorOIgualQue(Money)`, `esCero()` (comparaciones por `compareTo`; sin `double`) | VO monetario (ARCHITECTURE.md §4/§6; BR-001, BR-007). La escala ≤ 2 del monto de transferencia (BR-003) la verifica el `TransferValidator` (§8.4), no el VO. |
| `model/Cuenta` | constructor público `Cuenta(Long id, Long clienteId, CBU cbu, TipoCuenta tipo, Money saldo, Moneda moneda, EstadoCuenta estado, Instant createdAt, Long version)`, `bloquear()`/guarda `verificarActiva()` y getters `getId/getClienteId/getCbu/getTipo/getSaldo/getMoneda/getEstado/getCreatedAt/getVersion` — **ya existen (SPEC-002)**; la creación la centraliza `factory/CuentaFactory` (SPEC-002; no hay factory estática en `Cuenta`). **Modificado por SPEC-004 (ADR-007):** `saldo` deja de ser `final`; nuevos `void debitar(Money monto)` (invoca `verificarActiva()` — BR-002/ERR-003; si `monto.esMayorQue(saldo)` → `SaldoInsuficienteException` — BR-001; `saldo = saldo.restar(monto)`) y `void acreditar(Money monto)` (invoca `verificarActiva()`; `saldo = saldo.sumar(monto)`) | Agregado raíz. Invariante de saldo ≥ 0 (BR-001). |
| `model/Movimiento` | constructor público `Movimiento(Long id, Long cuentaId, TipoMovimiento tipo, Money monto, Instant fecha, Long cuentaContraparteId)`; factory `static Movimiento crear(Long cuentaId, TipoMovimiento tipo, Money monto, Instant fecha, Long cuentaContraparteId)` (id null); getters | Entidad que registra cada operación (FR-003). |
| `port/CuentaRepository` | **ya existe (SPEC-002):** `Cuenta save(Cuenta)`, `Optional<Cuenta> findById(Long)`, `Optional<Cuenta> findByCbu(CBU)`, `List<Cuenta> findByClienteId(Long)`, `List<Cuenta> findAll()`, `boolean existsByCbu(CBU)`. **Agregado por SPEC-004:** `Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia)` | Puerto (dominio puro). La agregación del límite diario vive aquí por mandato de la spec §10 (§8.10; ADR-007). |
| `port/MovimientoRepository` | `Movimiento save(Movimiento)`, `List<Movimiento> findByCuentaIdOrderByFechaDesc(Long cuentaId)` | Puerto del historial (FR-005; §8.2). |
| `port/TransferenciaEventPublisher` | `void publicar(TransferenciaRealizada evento)` | Abstracción de la emisión del evento (FR-004). Una implementación (no-op) en infra. |
| `event/TransferenciaRealizada` | `record TransferenciaRealizada(Money monto, CBU cbuOrigen, CBU cbuDestino, Instant fechaHora, Long idMovimientoSaliente)` | Evento de dominio (FR-004; A-007: id = id del saliente). |
| `exception/SaldoInsuficienteException` | `RuntimeException`; mensaje "Saldo insuficiente" | ERR-001 → 422. |
| `exception/CuentaNoEncontradaException` | `RuntimeException`; mensaje "Cuenta no encontrada" — **ya existe (SPEC-002)**, se reutiliza | ERR-002 → 404. |
| `exception/CuentaBloqueadaException` | `RuntimeException`; mensaje "La cuenta está bloqueada" — **ya existe (SPEC-002)**, se reutiliza (la lanzan la guarda `verificarActiva()` y los pasos 3/6 del validador) | ERR-003 → 422. |
| `exception/LimiteDiarioExcedidoException` | `RuntimeException`; mensaje "Límite diario de transferencias excedido" | ERR-007 → 422. |
| `exception/AutoTransferenciaException` | `RuntimeException`; mensaje "No se puede transferir a la misma cuenta" | ERR-008 → 422. |
| `exception/MonedaIncompatibleException` | `RuntimeException`; mensaje "Las monedas de las cuentas son incompatibles" | ERR-009 → 422. |
| `exception/CbuInvalidoException` | `RuntimeException`; mensaje "El CBU debe contener exactamente 22 dígitos numéricos" — **ya existe (SPEC-002)**; el handler ya la mapea a `400 CBU_INVALIDO` con campo `cbu` (SPEC-002 §8.5, para `GET /cbu/{cbu}`) | Defensiva (VO `CBU`); en el flujo de transferencia el validador la envuelve en `DatosInvalidosException("cbuDestino", ...)` (§8.4 paso 4) → 400 `DATOS_INVALIDOS` (ver §8.6). |

**Application (`com.banco.application.*`)** — Java puro, sin imports de Spring.

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `command/TransferirCommand` | `record TransferirCommand(Long cuentaOrigenId, String cbuDestino, BigDecimal monto)` | Entrada del alta (FR-001). |
| `query/ObtenerMovimientosQuery` | `record ObtenerMovimientosQuery(Long cuentaId, String rol, Long clienteIdClaim)` — `clienteIdClaim` null para ADMIN | Consulta con el sujeto autenticado (patrón de `ObtenerClienteQuery`). |
| `validator/DatosTransferencia` | `record DatosTransferencia(Long cuentaOrigenId, String cbuDestino, BigDecimal monto, Long clienteIdClaim)`; constructor compacto hace `trim` de `cbuDestino` (null-safe) | Entrada del `TransferValidator`; normalización en un solo lugar. |
| `validator/TransferenciaValidada` | `record TransferenciaValidada(Cuenta origen, Cuenta destino, Money monto)` | Salida del validador: cuentas ya cargadas + monto validado (evita recargar en el use case). |
| `validator/TransferValidator` | `TransferenciaValidada validar(DatosTransferencia)`; 10 chequeos secuenciales en el orden de la spec §6 (ver §8.4) | Validación de la transferencia (BR-002..BR-007, §9 de la spec). Clase única, no CoR multi-clase (decisión §8.2). |
| `usecase/TransferirUseCase` | `TransferenciaConfirmacion ejecutar(TransferirCommand, Long clienteIdAutenticado)`; dependencias: `CuentaRepository`, `MovimientoRepository`, `TransferValidator`, `TransferenciaEventPublisher` | Orquesta el main flow (§4). |
| `usecase/ObtenerMovimientosUseCase` | `List<Movimiento> ejecutar(ObtenerMovimientosQuery)`; dependencias: `CuentaRepository`, `MovimientoRepository` | Historial (FR-005, §9, A-005/A-006). |
| `usecase/TransferenciaConfirmacion` | `record TransferenciaConfirmacion(Long idTransferencia, BigDecimal monto, String cbuDestino, Instant fechaHora)` | Resultado del use case = contrato de la respuesta 201 (FR-001, A-007). Ver §8.3. |

**Infrastructure (`com.banco.infrastructure.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `adapter/persistence/CuentaJpaEntity` | **ya existe (SPEC-002)** — `@Entity @Table(name="cuentas")`; `@Id @GeneratedValue(IDENTITY) Long id`; `@Column(name="cliente_id", nullable=false) Long clienteId`; `@Column(nullable=false, length=22) String cbu`; `@Column(nullable=false, length=20) String tipo`; `@Column(nullable=false, precision=19, scale=2) BigDecimal saldo`; `@Column(nullable=false, length=3) String moneda`; `@Column(nullable=false, length=20) String estado`; `@Version @Column(nullable=false) Long version`; `@Column(name="created_at", nullable=false) Instant createdAt`; getters/setters — sin cambios en SPEC-004 | Proyección JPA (sin lógica de negocio). |
| `adapter/persistence/CuentaJpaRepository` | **ya existe (SPEC-002)** — `interface ... extends JpaRepository<CuentaJpaEntity, Long>`; `findByCbu`, `findAllByOrderByIdAsc`, `findByClienteIdOrderByIdAsc`, `existsByCbu` — sin cambios (SPEC-004 usa `findById`/`findByCbu`) | Acceso Spring Data. |
| `adapter/persistence/CuentaRepositoryAdapter` | **ya existe (SPEC-002)** — `@Component implements CuentaRepository`; `toEntity`/`toDomain` (enums como String, `CBU`/`Money`/`Moneda` mapeados explícitamente, **version en ambos sentidos**). **Modificado por SPEC-004 (ADR-007):** inyecta `MovimientoJpaRepository` y delega la agregación del día en él (§8.10) | Implementa el puerto. |
| `adapter/persistence/MovimientoJpaEntity` | `@Entity @Table(name="movimientos")`; `@Id IDENTITY Long id`; `@Column(name="cuenta_id", nullable=false) Long cuentaId`; `@Column(nullable=false, length=22) String tipo`; `@Column(nullable=false) BigDecimal monto`; `@Column(nullable=false) Instant fecha`; `@Column(name="cuenta_contraparte_id") Long cuentaContraparteId` (nullable); getters/setters | Proyección JPA. |
| `adapter/persistence/MovimientoJpaRepository` | `interface ... extends JpaRepository<MovimientoJpaEntity, Long>`; `List<MovimientoJpaEntity> findByCuentaIdOrderByFechaDesc(Long)`; `@Query` de agregación (§8.10) | Acceso Spring Data. |
| `adapter/persistence/MovimientoRepositoryAdapter` | `@Component implements MovimientoRepository`; `toEntity`/`toDomain` | Implementa el puerto. |
| `adapter/web/TransferenciaController` | `@RestController @RequestMapping("/api/v1/transferencias")`; `POST` → `201` + `TransferenciaConfirmacion` | Coordina; sin reglas de negocio. |
| `adapter/web/TransferirRequest` | `record TransferirRequest(Long cuentaOrigenId, String cbuDestino, BigDecimal monto)` | Entrada `POST /api/v1/transferencias` (FR-001). |
| `adapter/web/MovimientoController` | `@RestController @RequestMapping("/api/v1/cuentas")`; `GET /{id}/movimientos` → `200` + `List<MovimientoDto>` | Coordina; sin reglas de negocio. |
| `adapter/web/MovimientoDto` | `record MovimientoDto(Long id, Long cuentaId, String tipo, BigDecimal monto, String moneda, Instant fecha, Long cuentaContraparteId)` + `static from(Movimiento)` | Salida `GET /api/v1/cuentas/{id}/movimientos`. |
| `service/TransferenciaService` | `@Service`; `@Transactional public TransferenciaConfirmacion ejecutar(TransferirCommand, Long clienteId)`; delega en `TransferirUseCase` | **Frontera transaccional** (FR-002) — único `@Transactional` del sistema (§8.8, ADR-006). |
| `service/TransferenciaEventPublisherNoop` | `@Component implements TransferenciaEventPublisher`; `publicar` loguea a nivel debug | Emisor sin suscriptores (FR-004; AC-003 se cubre con mock en unit test). |
| `config/TransferenciaBeansConfig` | `@Configuration`; beans de §8.7 | Wiring (aplicación sin Spring). |

**Resources**

| Archivo | Propósito |
| --- | --- |
| `backend/src/main/resources/db/migration/V4__movimientos.sql` | Migración (§6.1; `cuentas` ya existe en `V3__cuentas.sql` de SPEC-002). |

**Tests (`backend/src/test/...`) — NUEVOS**

| Archivo | Cubre |
| --- | --- |
| `java/com/banco/support/CuentaTestHelper.java` | Helper de creación de cuentas vía `CuentaRepository` (A-003): `CuentaFactory.crear(...)` (SPEC-002) + `acreditar` para fondear; CBU de 22 dígitos generado secuencialmente (ver §10). |
| `java/com/banco/domain/CBUTest.java`, `domain/MoneyTest.java`, `domain/CuentaTest.java`, `domain/MovimientoTest.java` | VOs y agregado (ver §10). |
| `java/com/banco/application/TransferValidatorTest.java`, `application/TransferirUseCaseTest.java`, `application/ObtenerMovimientosUseCaseTest.java` | Validación y use cases (ver §10). |
| `java/com/banco/integration/TransferenciaApiIntegrationTest.java` | Endpoint de transferencias + autorización + concurrencia (AC-001..AC-015, AC-012). |
| `java/com/banco/integration/MovimientosApiIntegrationTest.java` | Endpoint de historial (AC-016..AC-020). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `domain/vo/Money.java` (SPEC-002) | Agrega `sumar`/`restar`/`esMayorQue`/`esMayorOIgualQue`/`esCero` y la factory `ars` (ADR-007). |
| `domain/model/Cuenta.java` (SPEC-002) | `saldo` deja de ser `final`; agrega `debitar`/`acreditar` (ambos con la guarda `verificarActiva()`) (ADR-007). |
| `domain/port/CuentaRepository.java` (SPEC-002) | Agrega `montoTotalTransferenciasSalientesDelDia` (§8.10). |
| `infrastructure/adapter/persistence/CuentaRepositoryAdapter.java` (SPEC-002) | Inyecta `MovimientoJpaRepository` y delega la agregación del límite diario (§8.10, ADR-007). |
| `infrastructure/security/SecurityConfig.java` | Dos matchers nuevos (§8.5). Resto intacto. |
| `infrastructure/adapter/web/GlobalExceptionHandler.java` | Cinco mapeos nuevos (§8.6); `CuentaNoEncontrada`/`CuentaBloqueada`/`CbuInvalido` ya se mapean desde SPEC-002. |
| `backend/src/main/resources/application.yml` | `banco.negocio.limite-diario-transferencias: 200000` (§8.7). |
| `backend/src/test/resources/application-test.yml` | Ídem (§8.7). |
| `backend/src/main/resources/db/migration/V3__cuentas_y_movimientos.sql` | **Eliminar** (WIP pre-reconciliación; duplica `V3__cuentas.sql` de SPEC-002 — ver §6.1, ADR-007). |

**Sin cambios:** `pom.xml` (ver §9), `BaseIntegrationTest`, `JwtTokenFactory`,
`JwtAuthenticationFilter`, `AuthenticatedUser`, `JwtService`, controllers
existentes, `ClienteBeansConfig`/`AuthBeansConfig`, `LayerArchitectureTest`
(las clases nuevas deben cumplir las reglas existentes — AC-023),
`V1__schema_inicial.sql`, `V2__usuarios.sql`, `V3__cuentas.sql` (SPEC-002),
`CuentaFactory`, `CuentaJpaEntity`/`CuentaJpaRepository` (SPEC-002), y los VOs
`CBU`/`Moneda` (SPEC-002).

### 8.2 Domain design (resumen ejecutivo)

- **Formato de `CBU`: exactamente 22 dígitos (`^[0-9]{22}$`).** Es el estándar
  real del CBU argentino (8 dígitos de banco + 14 de cuenta) y el requisito
  fijado para este diseño. **Discrepancia con la spec:** el ejemplo de FR-001
  (`"00000031000000000001"`) tiene 20 dígitos — ver §12. Los tests y la
  documentación usan CBUs de 22 dígitos (p. ej. `"00000031" + 14 dígitos`).
- **`Money` como VO obligatorio** (no `BigDecimal` pelado): `ARCHITECTURE.md`
  §4/§6 lo exige y BR-007 (moneda compatible) se evalúa por igualdad de
  `Money.moneda()` (código ISO 4217 alpha-3 — ADR-007). El VO **ya existe desde
  SPEC-002** (`record Money(BigDecimal monto, Moneda moneda)` con validación
  `monto ≥ 0` → `MoneyInvalidoException`); SPEC-004 le agrega las operaciones de
  transferencia (`sumar`/`restar` con `MathContext.DECIMAL128` sin `double`,
  `esMayorQue`/`esMayorOIgualQue`/`esCero` por `compareTo`, factory `ars`). La
  escala ≤ 2 y el `> 0` del monto (BR-003) los exige el validador (paso 7 de
  §8.4), no el VO (SPEC-002 no valida escala).
- **`Cuenta` se extiende, no se reescribe (ADR-007):** los métodos de dinero
  `debitar`/`acreditar` invocan primero la guarda existente `verificarActiva()`
  (BR-002/ERR-003) y mutan el saldo (el campo deja de ser `final`); el
  invariante de saldo ≥ 0 (BR-001) se re-verifica en `debitar` (doble barrera
  con el paso 9 del validador). El constructor de reconstrucción, `bloquear()`,
  `CuentaFactory` y el mapeo JPA (incluido `version` en ambos sentidos) quedan
  como en SPEC-002.
- **Factory de `Cuenta`: clase `CuentaFactory` con dispatch por `switch` (ya
  existe — SPEC-002, BR-004).** SPEC-002 descartó el factory estático
  `Cuenta.crear` (decisión documentada en SPEC-002 §13: AC-024 nombra "la
  Factory" como unidad testeable y el constructor público de `Cuenta` queda
  solo para reconstrucción). SPEC-004 consume `CuentaFactory` (tests,
  `CuentaTestHelper`) y el constructor de reconstrucción sin cambios
  (ADR-007).
- **`Movimiento` como puerto separado (`MovimientoRepository`), no colgado del
  agregado `Cuenta`.** La spec llama a `Movimiento` "parte del agregado" y
  coloca la agregación del límite diario en `CuentaRepository`, pero el
  historial (FR-005) es un read model (CQRS ligero, `ARCHITECTURE.md` §5):
  cargar la colección de movimientos a través del agregado (colección no
  acotada, sin paginación — A-006) complica el mapeo sin beneficio. Un puerto
  propio por tabla sigue la convención un-puerto-por-tabla del repo
  (`ClienteRepository`/`UsuarioRepository`). El **límite diario** queda en
  `CuentaRepository` por mandato explícito de la spec §10; el adapter
  correspondiente delega en `MovimientoJpaRepository` (los datos viven en
  `movimientos`) — ver §8.10.
- **`TransferValidator` como clase única (no CoR multi-clase).** La CoR de
  clientes se justifica para 6 campos puros en 3 etapas; `RegistroValidator`
  (SPEC-003) sentó el precedente de clase única para chequeos secuenciales. La
  transferencia tiene ~10 chequeos **heterogéneos** (formato + reglas de
  negocio con repositorio) que **comparten estado** (las cuentas cargadas):
  10 clases + orquestador + contexto compartido sería sobreingeniería
  (AGENTS.md §11). Una clase con métodos privados secuenciales que corta ante
  el primer error y devuelve `TransferenciaValidada` (origen/destino/monto ya
  cargados) es la solución más simple. Ver §13.
- **Excepciones nuevas en `domain.exception`** (convención): una por error de
  negocio de §8 (ERR-001/002/003/007/008/009) + `CbuInvalidoException`
  (defensiva del VO, paralela a `DniInvalidoException`). ERR-004 (monto) y
  ERR-006 (propiedad/permiso) reutilizan `DatosInvalidosException` y
  `AccesoDenegadoException`. Todas `RuntimeException`.

### 8.3 Application design (flujo de cada use case)

**Transferir (main flow; dentro de la transacción de `TransferenciaService`):**

```text
TransferirUseCase.ejecutar(command, clienteIdAutenticado)
  1. DatosTransferencia datos = new DatosTransferencia(command.cuentaOrigenId(),
        command.cbuDestino(), command.monto(), clienteIdAutenticado)   // trim de cbuDestino
  2. TransferenciaValidada v = transferValidator.validar(datos)        // 10 chequeos (§8.4);
                                                                       // carga origen y destino
  3. Instant fecha = Instant.now()                                     // misma fecha FR-003
  4. v.origen().debitar(v.monto())                                     // BR-001 (doble barrera:
                                                                       // el paso 9 del validador ya
                                                                       // verificó el saldo)
  5. v.destino().acreditar(v.monto())                                  // BR-001
  6. cuentaRepository.save(v.origen())
  7. cuentaRepository.save(v.destino())
  8. Movimiento saliente = movimientoRepository.save(Movimiento.crear(
        v.origen().getId(), TRANSFERENCIA_SALIENTE, v.monto(), fecha, v.destino().getId()))
  9. Movimiento entrante = movimientoRepository.save(Movimiento.crear(
        v.destino().getId(), TRANSFERENCIA_ENTRANTE, v.monto(), fecha, v.origen().getId()))
 10. eventPublisher.publicar(new TransferenciaRealizada(v.monto(),
        v.origen().getCbu(), v.destino().getCbu(), fecha, saliente.getId()))  // FR-004
 11. return new TransferenciaConfirmacion(saliente.getId(), v.monto().monto(),
        v.destino().getCbu().valor(), fecha)                                  // FR-001, A-007
```

- Si cualquier paso lanza excepción, la transacción hace rollback total: no
  queda débito, crédito ni movimientos parciales (FR-002, ERR-001/004/005).
- `saliente.getId()` es el id asignado por la BD en el `save` (IDENTITY): el
  mock del unit test debe devolver un `Movimiento` con id (AC-003).
- **`TransferenciaConfirmacion` en `application` (no en web):** es el resultado
  del use case y su forma coincide 1:1 con el body de la respuesta 201
  (FR-001). Un DTO web duplicado (`TransferenciaConfirmacionDto`) sería una
  copia sin valor; el controller la devuelve directamente (ver §13). El
  dominio sigue sin exponer DTOs.

**Historial (FR-005):**

```text
ObtenerMovimientosUseCase.ejecutar(query)
  1. Cuenta cuenta = cuentaRepository.findById(query.cuentaId())
        .orElseThrow(CuentaNoEncontradaException::new)     // ERR-002 → 404 (AC-019)
  2. if ("CLIENTE".equals(query.rol())
        && (query.clienteIdClaim() == null
            || !query.clienteIdClaim().equals(cuenta.getClienteId())))
        → AccesoDenegadoException                          // ERR-006 → 403 (AC-017, A-005)
  3. return movimientoRepository.findByCuentaIdOrderByFechaDesc(cuenta.getId())  // A-006
```

- Orden 404-antes-403 (inverso al de `ObtenerClienteUseCase`): aquí la
  propiedad exige cargar la cuenta (no hay claim de cuenta en el token), así
  que la existencia se verifica primero. Semántica idéntica a la spec
  (AC-017 vs AC-019).
- `ADMIN` con `clienteIdClaim == null` pasa el chequeo (paso 2 no aplica).

### 8.4 TransferValidator — orden de los 10 chequeos (spec §6, ERR-001..009)

```text
TransferValidator.validar(DatosTransferencia datos) → TransferenciaValidada
  1. Cuenta origen = cuentaRepository.findById(datos.cuentaOrigenId())
        .orElseThrow(CuentaNoEncontradaException::new)          // ERR-002 → 404
  2. if (datos.clienteIdClaim() == null
        || !datos.clienteIdClaim().equals(origen.getClienteId()))
        → AccesoDenegadoException                               // ERR-006 → 403 (cuenta ajena)
  3. if (origen.getEstado() != ACTIVA) → CuentaBloqueadaException  // ERR-003 → 422
  4. CBU cbuDestino;
     try { cbuDestino = new CBU(datos.cbuDestino()); }
     catch (CbuInvalidoException e) → DatosInvalidosException("cbuDestino", e.getMessage())
                                                                 // formato CBU → 400 (ERR-004 semántica)
     Cuenta destino = cuentaRepository.findByCbu(cbuDestino)
        .orElseThrow(CuentaNoEncontradaException::new)          // ERR-002 → 404 (AF-001)
  5. if (destino.getId().equals(origen.getId()))
        → AutoTransferenciaException                            // ERR-008 → 422 (BR-005)
  6. if (destino.getEstado() != ACTIVA) → CuentaBloqueadaException  // ERR-003 → 422
  7. if (datos.monto() == null || datos.monto().signum() <= 0
        || datos.monto().scale() > 2)
        → DatosInvalidosException("monto", "El monto debe ser mayor a 0 y tener hasta 2 decimales")
                                                                // ERR-004 → 400 (BR-003)
     Money monto = Money.ars(datos.monto());                    // factory de SPEC-004 → Moneda("ARS");
                                                                // el VO (SPEC-002) valida monto ≥ 0,
                                                                // inalcanzable tras el chequeo anterior
  8. if (!origen.getSaldo().moneda().equals(destino.getSaldo().moneda()))
        → MonedaIncompatibleException                           // ERR-009 → 422 (BR-007; ADR-007)
  9. if (monto.esMayorQue(origen.getSaldo()))
        → SaldoInsuficienteException                            // ERR-001 → 422 (BR-001)
     // (debitar re-verifica el invariante — doble barrera)
 10. Money totalDia = cuentaRepository.montoTotalTransferenciasSalientesDelDia(
          datos.clienteIdClaim(), LocalDate.now(ZoneOffset.UTC));
     if (totalDia.sumar(monto).esMayorOIgualQue(limiteDiario))
        → LimiteDiarioExcedidoException                         // ERR-007 → 422 (BR-004, AF-002)
 11. return new TransferenciaValidada(origen, destino, monto)
```

Notas:

- El orden es **exactamente** el del main flow de la spec (§6 paso 2) y corta
  ante el primer error (convención CoR).
- El chequeo 4 valida el formato del CBU ANTES de buscar (el VO `CBU` valida en
  el constructor): CBU malformado → 400 con campo `cbuDestino`; CBU bien
  formado inexistente → 404 (ERR-002).
- Los pasos 7 y 8 usan las formas reconciliadas (ADR-007): `Money.ars(...)`
  construye `Moneda("ARS")` y el VO de SPEC-002 solo valida `monto >= 0`
  (`MoneyInvalidoException`, sin mapeo en el handler — §8.6), por eso el
  chequeo de signo/escala (BR-003) ocurre ANTES de construir el `Money`; la
  compatibilidad de moneda (BR-007) se evalúa por igualdad de `Money.moneda()`
  (código ISO 4217 alpha-3).
- `limiteDiario` es un `Money` inyectado por constructor (bean de
  `TransferenciaBeansConfig` desde `@Value`, §8.7) — `application` no lee
  propiedades de Spring.
- `LocalDate.now(ZoneOffset.UTC)`: "día calendario" en UTC, consistente con la
  convención de almacenamiento UTC del repo (V1) — ver §12.
- Condición del límite: "alcanza o supera" (`>=`) según AF-002/AC-010.

### 8.5 SecurityConfig (matchers nuevos y orden)

```text
authorizeHttpRequests:
  0. POST   /api/v1/auth/register        → permitAll()               // sin cambios
  1. POST   /api/v1/auth/login           → permitAll()               // sin cambios
  2. POST   /api/v1/clientes             → hasRole("ADMIN")          // sin cambios
  3. PUT    /api/v1/clientes/**          → hasRole("ADMIN")          // sin cambios
  4. GET    /api/v1/clientes             → hasRole("ADMIN")          // sin cambios
  5. GET    /api/v1/clientes/**          → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios
  6. POST   /api/v1/transferencias       → hasRole("CLIENTE")        // NUEVO (FR-001, §9, A-005)
  7. GET    /api/v1/cuentas/*/movimientos → hasAnyRole("ADMIN", "CLIENTE") // NUEVO (FR-005, §9)
  8. anyRequest()                        → authenticated()           // sin cambios
```

- `POST /api/v1/transferencias` → solo `CLIENTE` (el `ADMIN` no inicia
  transferencias — A-005; AC-014: token ADMIN → 403 por el handler de
  acceso-denegado). La propiedad de la cuenta origen se verifica en el use
  case (paso 2 del validador) → 403 (ERR-006, AC-013).
- `GET /api/v1/cuentas/*/movimientos` → `ADMIN` (cualquier cuenta) o `CLIENTE`
  (solo propias; la propiedad se verifica en `ObtenerMovimientosUseCase` →
  403, AC-017). Ant pattern: `*` matchea exactamente un segmento
  (`{id}`).
- Sin conflicto con los matchers de `/api/v1/clientes` (segmentos distintos).
  El resto del chain (CSRF off, stateless, entry point 401, handler 403,
  filtro JWT) no cambia.

### 8.6 GlobalExceptionHandler — mapeos nuevos

Envelope `{ code, message, details? }` (omisión de null configurada). Tabla
completa resultante — **cinco filas nuevas de SPEC-004** en **negrita**
(`SaldoInsuficiente`, `LimiteDiarioExcedido`, `AutoTransferencia`,
`MonedaIncompatible` y `ObjectOptimisticLockingFailure`); `CuentaNoEncontrada`/
`CuentaBloqueada`/`CbuInvalido` **ya se mapean desde SPEC-002** (sin cambios):

| Excepción | HTTP | `code` | `details` |
| --- | --- | --- | --- |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | `[{campo, mensaje}]` (ERR-004 monto, cbuDestino malformado, y los usos existentes) |
| `DniInvalidoException` (defensivo) | 400 | `DATOS_INVALIDOS` | `[{campo:"dni", mensaje}]` (existente) |
| `CbuInvalidoException` (defensivo) | 400 | `CBU_INVALIDO` | `[{campo:"cbu", mensaje}]` — **ya mapeada por SPEC-002** (§8.5) para `GET /cbu/{cbu}`; en el flujo de transferencia el validador la envuelve en `DatosInvalidosException("cbuDestino", ...)` (paso 4, §8.4) → 400 `DATOS_INVALIDOS` |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | null (existente) |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | null (existente; cubre `{id}` no numérico) |
| `CredencialesInvalidasException` | 401 | `NO_AUTENTICADO` | null (existente) |
| `AccesoDenegadoException` | 403 | `ACCESO_DENEGADO` | null (ERR-006) |
| `ClienteNoEncontradoException` | 404 | `CLIENTE_NO_ENCONTRADO` | null (existente) |
| `CuentaNoEncontradaException` | 404 | `CUENTA_NO_ENCONTRADA` | null (ERR-002) — **ya mapeada por SPEC-002**, sin cambios |
| `ClienteDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo, mensaje}]` (existente) |
| `UsernameDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo:"username", mensaje}]` (existente) |
| **`ObjectOptimisticLockingFailureException`** | **409** | **`CONFLICTO_CONCURRENCIA`** | **null (ERR-005)** |
| `DataIntegrityViolationException` (backstop race) | 409 | `CONFLICTO_UNICIDAD` | null (existente) |
| **`SaldoInsuficienteException`** | **422** | **`SALDO_INSUFICIENTE`** | **null (ERR-001)** |
| `CuentaBloqueadaException` | 422 | `CUENTA_BLOQUEADA` | null (ERR-003) — **ya mapeada por SPEC-002**, sin cambios (la lanzan la guarda `verificarActiva()` y los pasos 3/6 del validador) |
| **`LimiteDiarioExcedidoException`** | **422** | **`LIMITE_DIARIO_EXCEDIDO`** | **null (ERR-007)** |
| **`AutoTransferenciaException`** | **422** | **`AUTO_TRANSFERENCIA`** | **null (ERR-008)** |
| **`MonedaIncompatibleException`** | **422** | **`MONEDA_INCOMPATIBLE`** | **null (ERR-009)** |
| `Exception` (fallback) | 500 | `ERROR_INTERNO` | null (existente) |

- **ERR-005 (409):** Hibernate lanza `StaleObjectStateException`, que Spring
  envuelve como `org.springframework.orm.ObjectOptimisticLockingFailureException`
  (superclase de `JpaOptimisticLockingFailureException`). El handler mapea esa
  excepción → `409 CONFLICTO_CONCURRENCIA` con mensaje orientativo
  ("Conflicto de concurrencia: reintente la operación"). Sin reintento
  automático (A-004).
- **`MoneyInvalidoException` (SPEC-002) sigue sin mapeo** (caería en el fallback
  500): es un invariante interno del VO (`monto < 0` o null). En el flujo de
  transferencia es **inalcanzable** porque el paso 7 del validador (§8.4)
  pre-chequea signo y escala del monto antes de construir el `Money` (BR-003 →
  400). Se documenta aquí para que no se agregue un mapeo especulativo (misma
  decisión que SPEC-002 §8.5).
- Los `401`/`403` de Spring Security los siguen escribiendo el entry point y
  el access-denied handler de `SecurityConfig` (sin cambios).

### 8.7 Configuración (`application.yml` / `application-test.yml`)

```yaml
banco:
  security:
    jwt-secret: ...
    jwt-expiration-minutes: 60
  negocio:
    limite-diario-transferencias: 200000     # NUEVO (A-002): límite diario global en ARS
```

Ambos archivos (main y test) declaran la propiedad con el mismo default `200000`
(consistente con el naming `banco.security.*`). `TransferenciaBeansConfig` la
lee con default defensivo:

```java
@Bean
public Money limiteDiarioTransferencias(
        @Value("${banco.negocio.limite-diario-transferencias:200000}") BigDecimal limite) {
    return Money.ars(limite);
}
```

Beans del config: `Money limiteDiarioTransferencias`, `TransferValidator`
(`CuentaRepository`, `limiteDiarioTransferencias`), `TransferirUseCase`
(`CuentaRepository`, `MovimientoRepository`, `TransferValidator`,
`TransferenciaEventPublisher`), `ObtenerMovimientosUseCase` (`CuentaRepository`,
`MovimientoRepository`). `TransferenciaEventPublisher` se resuelve al único
`@Component` que lo implementa (`TransferenciaEventPublisherNoop`).
`TransferenciaService` no necesita declaración (component scanning).

### 8.8 Frontera transaccional (FR-002) y funcionamiento del lock optimista

**Decisión: `@Transactional` en `infrastructure/service/TransferenciaService`**
(un `@Service` delgado que delega en `TransferirUseCase`). Alternativas
descartadas en §13 y en ADR-006.

- `TransferenciaService.ejecutar(command, clienteId)` está anotado
  `@Transactional` (propagación REQUIRED por defecto). El use case corre dentro
  de ese tx: las lecturas (validación) y las 4 escrituras (`save(origen)`,
  `save(destino)`, `save(saliente)`, `save(entrante)`) comparten la misma
  transacción de BD; cualquier fallo → rollback total (FR-002).
- Los repositorios Spring Data (`SimpleJpaRepository`) son `@Transactional`
  por método, pero con REQUIRED se unen al tx externo del service — no abren
  tx propios.
- **Por qué no `@Transactional` en el use case:** la regla ArchUnit
  "application depende solo de domain" prohíbe importar
  `org.springframework.transaction.annotation.Transactional` en `application`
  (AC-023). El service es el mecanismo hexagonal estándar para poner la
  frontera transaccional en infraestructura.
- **Cómo funciona el lock optimista (BR-006):**
  1. `findById`/`findByCbu` cargan las cuentas (version N) y el adapter las
     mapea al dominio **incluyendo el `version`**.
  2. El use case muta el saldo; `save(origen)` mapea de vuelta a
     `CuentaJpaEntity` **con el mismo version N** → `JpaRepository.save` =
     `merge` (entidad con id).
  3. Al flush/commit, Hibernate ejecuta `UPDATE ... WHERE id = ? AND version =
     N`. Si otro tx ya commiteó version N+1 → 0 filas → `StaleObjectStateException`
     → `ObjectOptimisticLockingFailureException` → `409 CONFLICTO_CONCURRENCIA`
     (ERR-005). Sin reintento server-side (A-004); nunca se pierde consistencia
     de saldo (el UPDATE falla entero).
  4. **Condición crítica para el developer:** el mapeo `version` debe hacerse
     en AMBOS sentidos en `CuentaRepositoryAdapter` (si se omite, el merge
     usará version 0/null y el lock no dispara). Cubierto por el test
     determinista de §10 (AC-012 parte 2).
- El evento se publica dentro del tx (paso 10 del use case): sin suscriptores,
  no requiere outbox (evolución documentada en ADR-006).

### 8.9 Test de concurrencia (AC-012) — diseño

**Objetivo:** dos transferencias concurrentes sobre la misma cuenta origen con
montos que juntos exceden el saldo → una `201`, la otra `409
CONFLICTO_CONCURRENCIA`; saldo final consistente y nunca negativo.

**Riesgo de determinismo:** con locking optimista puro y una app sin estado
entre requests, el resultado depende del interleaving real: si la 2ª request
lee DESPUÉS del commit de la 1ª, verá el saldo remanente y fallará con `422
SALDO_INSUFICIENTE` (montos elegidos para que juntos excedan el saldo ⇒ cada
uno por separado es válido, pero el remanente ya no alcanza). El `409` solo
ocurre si ambas leyeron version N antes de que cualquiera commitee. Diseño en
dos partes:

**Parte 1 — concurrencia real (HTTP, threads + latch):**

1. Crear cliente + cuenta origen fondear con `S = 100000` y cuenta destino con
   saldo 0 (helper `CuentaTestHelper`, A-003).
2. Dos threads con `CountDownLatch`/`CyclicBarrier` que lanzan SIMULTÁNEAMENTE
   dos `POST /api/v1/transferencias` (MockMvc es thread-safe) con montos
   `A = 60000` y `B = 60000` (A ≤ S, B ≤ S, A + B > S — AC-012).
3. Assert: exactamente una respuesta `201` y la otra `409` con
   `code == CONFLICTO_CONCURRENCIA`.
4. Assert final: vía `cuentaRepository.findById` (el test tiene acceso al
   puerto) saldo origen == `S - 60000` (el monto ganador), nunca negativo; y
   exactamente 2 movimientos de transferencia por operación exitosa (o los
   esperados según el ganador).
5. **Mitigación de flake:** el único schedule que produce `422` en vez de `409`
   exige que la 2ª request lea después del commit de la 1ª; con la barrera
   ambos threads arrancan en microsegundos y ambas lecturas ocurren antes de
   cualquier commit (probabilidad residual ínfima con Postgres real). Si el
   schedule se serializara, el test reintenta con datos frescos (límite de 3
   intentos, `@RepeatedTest`/loop documentado) — ver §12.

**Parte 2 — conflicto determinista a nivel de persistencia (sin HTTP):**

Complementa la Parte 1 probando el mecanismo del `@Version` sin depender del
scheduler:

1. Crear + fondear cuenta (version 0).
2. Cargar la `Cuenta` de dominio vía `cuentaRepository.findById` (version 0 —
   la "copia vieja").
3. Simular la operación concurrente: `jdbcTemplate.update("UPDATE cuentas SET
   version = version + 1 WHERE id = ?", id)` (Postgres real del contenedor).
4. `cuentaRepository.save(cuenta)` (debitar/acreditar previamente en memoria)
   → assert que lanza `ObjectOptimisticLockingFailureException` (→ 409 por el
   handler).

**Por qué no "dos requests secuenciales con bump manual de version":** con una
app stateless, un bump en la BD ANTES de la request es simplemente el estado
que la request lee (no genera conflicto); el bump solo produce conflicto si
ocurre ENTRE la lectura y el commit de la misma request, lo que desde afuera
solo se logra con concurrencia real (Parte 1) o con control de transacción
explícito (Parte 2). Ver §13.

### 8.10 Consulta del límite diario (BR-004) — query

**Puerto (dominio):**

```java
/**
 * Suma de los montos de los movimientos TRANSFERENCIA_SALIENTE del cliente
 * (TODAS sus cuentas) en el día calendario dado. Día interpretado en UTC
 * (convención de almacenamiento del repo — V1). Devuelve 0 (ARS) si no hay
 * movimientos.
 */
Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia);
```

**Implementación (adapter):**

```java
// MovimientoJpaRepository — JPQL (el dato vive en movimientos; el filtro
// por cliente va por subquery sobre cuentas):
@Query("""
        SELECT COALESCE(SUM(m.monto), 0)
        FROM MovimientoJpaEntity m
        WHERE m.tipo = 'TRANSFERENCIA_SALIENTE'
          AND m.cuentaId IN (SELECT c.id FROM CuentaJpaEntity c WHERE c.clienteId = :clienteId)
          AND m.fecha >= :inicio AND m.fecha < :fin
        """)
BigDecimal sumarTransferenciasSalientesDelDia(@Param("clienteId") Long clienteId,
                                              @Param("inicio") Instant inicio,
                                              @Param("fin") Instant fin);

// CuentaRepositoryAdapter (implementa el puerto; delega):
@Override
public Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia) {
    Instant inicio = dia.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant fin = dia.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return Money.ars(movimientoJpaRepository.sumarTransferenciasSalientesDelDia(clienteId, inicio, fin));
}
```

- **"Por cliente, todas sus cuentas"** (BR-004): subquery `cuentas.cliente_id`.
- **"Día calendario"** en UTC (zona documentada — §12).
- La consulta corre **dentro del mismo tx** que la transferencia (MVCC): solo
  ve movimientos commiteados, así que no incluye la transferencia en curso
  (el chequeo ocurre antes de crear los movimientos). **Limitación conocida:**
  dos transferencias concurrentes que individualmente respetan el límite
  pueden superarlo en conjunto (el límite es un control blando; el `@Version`
  solo protege el saldo). Documentado en §12.
- El índice `movimientos(cuenta_id, fecha)` (§6.1) cubre el rango del día por
  cuenta.

---

## 9. Build & Dependencies

**Ninguna dependencia nueva.**

- `spring-boot-starter-data-jpa` (ya presente): `@Version`, `@Transactional`,
  Spring Data queries.
- Postgres + Flyway + Testcontainers + ArchUnit: ya presentes (SPEC-001/003).
- No se agrega `spring-boot-starter-validation` (los DTOs no usan bean
  validation; las reglas viven en `TransferValidator` y en los VOs).

---

## 10. Testing Strategy

### Unit (JUnit 5 + Mockito; sin Spring)

| Clase | Cobertura | AC |
| --- | --- | --- |
| `domain/CBUTest` | 22 dígitos válido; null/vacío, letras, 21/23 dígitos → `CbuInvalidoException` | AC-021 |
| `domain/MoneyTest` | constructor (SPEC-002): monto/moneda null o monto negativo → `MoneyInvalidoException`; `Money.cero(ARS)` → 0; **operaciones de SPEC-004:** `sumar`/`restar` correctos (con `MathContext.DECIMAL128`, sin `double`); `esMayorQue`/`esMayorOIgualQue`/`esCero` (por `compareTo`); factory `Money.ars(...)` → moneda `"ARS"` | AC-021 |
| `domain/CuentaTest` | **ya cubre (SPEC-002):** reconstrucción (todos los campos, incl. `version`) y `bloquear()`/guarda `verificarActiva()`; **SPEC-004 agrega:** `debitar` OK decrementa; `debitar` > saldo → `SaldoInsuficienteException` y saldo intacto; `acreditar` incrementa; `debitar`/`acreditar` sobre cuenta `BLOQUEADA` → `CuentaBloqueadaException` (guarda `verificarActiva()`, BR-002/ERR-003); saldo nunca negativo | AC-021, BR-001 |
| `domain/MovimientoTest` | `crear` (id null) y getters | AC-021 |
| `application/TransferValidatorTest` | **Orden y corte** (un caso por chequeo, §8.4): origen inexistente → `CuentaNoEncontradaException`; origen ajeno (clienteIdClaim distinto o null) → `AccesoDenegadoException`; origen BLOQUEADA → `CuentaBloqueadaException`; CBU malformado → `DatosInvalidosException("cbuDestino")`; destino inexistente → `CuentaNoEncontradaException`; mismo CBU que origen → `AutoTransferenciaException`; destino BLOQUEADA → `CuentaBloqueadaException`; monto 0/negativo/escala 3 → `DatosInvalidosException("monto")`; moneda distinta (`new Moneda("USD")` vs `new Moneda("ARS")` vía `Money`) → `MonedaIncompatibleException` (AC-015); saldo insuficiente → `SaldoInsuficienteException`; total del día + monto ≥ límite → `LimiteDiarioExcedidoException`; happy path → `TransferenciaValidada` con cuentas cargadas y monto `Money`; trim de `cbuDestino` | AC-004..AC-015 (lógica), AC-021 |
| `application/TransferirUseCaseTest` | happy path: valida, `debitar`/`acreditar` llamados, 4 `save`, evento publicado **una vez** con (monto, cbuOrigen, cbuDestino, fechaHora, idMovimientoSaliente = id del saliente guardado) — AC-003; confirmación con `idTransferencia` = id del saliente (A-007); saldo insuficiente → excepción propagada sin saves; ambos movimientos con **misma fecha y monto** y contrapartes cruzadas (FR-003, AC-002 lógica) | AC-001, AC-002, AC-003, AC-004 (lógica) |
| `application/ObtenerMovimientosUseCaseTest` | CLIENTE cuenta propia → lista ordenada (mock del repo); CLIENTE ajena o sin claim → `AccesoDenegadoException`; ADMIN cualquier cuenta → lista; inexistente → `CuentaNoEncontradaException`; orden 404-antes-403 | AC-016..AC-019 (lógica) |

### Integración (Spring Boot Test + Testcontainers + MockMvc)

Ambos tests extienden `BaseIntegrationTest` con su `@TestConfiguration TokenConfig`
anidada (patrón de `AuthApiIntegrationTest`). Helpers reutilizados:
`crearClienteAdmin(...)` (POST `/api/v1/clientes` con token ADMIN), `register` +
`login` (registro/login REALES de SPEC-003), y **`CuentaTestHelper`** (A-003):

```java
// support/CuentaTestHelper — crea cuentas vía el PUERTO (no HTTP, A-003).
// Usa la API de dominio de SPEC-002: CuentaFactory.crear + acreditar (fondeo).
public class CuentaTestHelper {
    private final CuentaRepository cuentaRepository;
    private long contadorCbu = 0;

    public Cuenta crearCuenta(Long clienteId, TipoCuenta tipo, BigDecimal saldoInicial) {
        CBU cbu = new CBU("00000031" + String.format("%014d", contadorCbu++)); // 8 + 14 = 22 dígitos
        Cuenta cuenta = CuentaFactory.crear(clienteId, tipo, cbu, new Moneda("ARS"), Instant.now());
        if (saldoInicial.signum() > 0) {
            cuenta.acreditar(Money.ars(saldoInicial));   // fondeo (saldo inicial 0 + acreditar)
        }
        return cuentaRepository.save(cuenta);
    }
}
```

`TransferenciaApiIntegrationTest` (un método por criterio):

| Cobertura | AC |
| --- | --- |
| Transferencia válida → `201` con `idTransferencia`/`monto`/`cbuDestino`/`fechaHora`; saldo origen debitado y destino acreditado exactamente por el monto (verificado vía `CuentaRepository`) | AC-001 |
| Se registran exactamente 2 movimientos con mismo monto/fecha y contrapartes cruzadas (verificado vía `MovimientoRepository`) | AC-002 |
| Saldo insuficiente → `422` `SALDO_INSUFICIENTE`; sin cambios de saldo ni movimientos | AC-004 |
| CBU destino inexistente (22 dígitos válidos, sin cuenta) → `404` `CUENTA_NO_ENCONTRADA`; sin débito | AC-005 |
| Origen `BLOQUEADA` → `422` `CUENTA_BLOQUEADA` (cuenta creada y bloqueada vía helper + mutación directa del estado con `CuentaRepository.save`) | AC-006 |
| Destino `BLOQUEADA` → `422` `CUENTA_BLOQUEADA` | AC-007 |
| `monto <= 0` o con más de 2 decimales → `400` `DATOS_INVALIDOS` con `details[0].campo == "monto"` | AC-008 |
| Destino = CBU de la propia cuenta origen → `422` `AUTO_TRANSFERENCIA` | AC-009 |
| Suma del día `>=` límite (fondear 250000, transferir 200000) → `422` `LIMITE_DIARIO_EXCEDIDO`; sin débito | AC-010 |
| Límite por cliente sobre TODAS sus cuentas (2 cuentas del mismo cliente: 120000 + 90000 → segunda rechazada con default 200000); default verificado con `@Value("${banco.negocio.limite-diario-transferencias:200000}")` | AC-011 |
| Concurrencia: Parte 1 (threads + latch → `201` + `409`; saldo final consistente vía repo) + Parte 2 (conflicto determinista vía bump de version + `save` → `ObjectOptimisticLockingFailureException`) | AC-012 |
| CLIENTE transfiere desde `cuentaOrigenId` ajeno → `403` `ACCESO_DENEGADO` | AC-013 |
| Sin token → `401`; token ADMIN → `403` (solo CLIENTE transfiere) | AC-014 |
| Envelope JSON verificado en todos los códigos (400/401/403/404/409/422) | AC-022 |

`MovimientosApiIntegrationTest`:

| Cobertura | AC |
| --- | --- |
| CLIENTE historial de cuenta propia → `200`, ordenado por fecha descendente, incluye movimientos de transferencia (generados con un POST previo) | AC-016 |
| CLIENTE historial de cuenta ajena → `403` `ACCESO_DENEGADO` | AC-017 |
| ADMIN historial de cualquier cuenta → `200` | AC-018 |
| Historial de cuenta inexistente → `404` `CUENTA_NO_ENCONTRADA` | AC-019 |
| Sin token → `401` | AC-020 |

### Arquitectura (ArchUnit) — `LayerArchitectureTest`

Sin cambios de reglas (AC-023). Las clases nuevas deben cumplir:

1. `domain` (`Cuenta`, `Movimiento`, `CBU`, `Money`, `Moneda`, enums, puertos,
   evento, excepciones) sin dependencias de Spring/JPA/otras capas (los VOs y
   la entidad usan solo `java.time`/`java.math`/`java.util`).
2. `application` (`TransferirCommand`, `ObtenerMovimientosQuery`,
   `DatosTransferencia`, `TransferenciaValidada`, `TransferValidator`,
   `TransferirUseCase`, `ObtenerMovimientosUseCase`, `TransferenciaConfirmacion`)
   dependiendo solo de `domain`/`application`/`java`. **Ojo del developer:** el
   `@Transactional` NO puede importarse en `application` (vive en
   `TransferenciaService`).
3. Spring/controllers solo en `infrastructure` (`TransferenciaService`,
   `TransferenciaEventPublisherNoop`, adapters, controllers).

---

## 11. ADR

Se crea **`docs/adr/ADR-006-agregado-cuenta-minimo-frontera-transaccional-concurrencia-spec-004.md`**:
tres decisiones significativas: (1) incorporación del agregado `Cuenta` mínimo
(resolución del gap de SPEC-002, A-001) — **decisión reemplazada por ADR-007**
(SPEC-002 ya está implementada; SPEC-004 la extiende); (2) frontera
transaccional en `infrastructure.service.TransferenciaService` (único
`@Transactional` del sistema; `application` permanece Spring-free — AC-023);
(3) estrategia de concurrencia: `@Version` + `409 CONFLICTO_CONCURRENCIA` sin
reintento automático (A-004, BR-006). La decisión 2 rompe el precedente "sin
`@Transactional` explícito" de SPEC-001/SPEC-003 y por eso se registra. Las
decisiones 2 y 3 siguen vigentes.

Se crea además **`docs/adr/ADR-007-reconciliacion-spec-004-sobre-spec-002.md`**:
SPEC-002 fue implementada, revisada y mergeada (PR #20) con formas aprobadas
distintas a las que asumió esta arquitectura — no existen `Money(BigDecimal,
Currency)` ni `enum Moneda`; hay `record Money(BigDecimal monto, Moneda
moneda)`, `record Moneda(String codigo)` y un agregado `Cuenta` ya con
`@Version`, `bloquear()` y guarda `verificarActiva()`. ADR-007 resuelve que
SPEC-004 se implementa **sobre** esos VOs/agregado (`Money` gana operaciones;
`Cuenta` gana `debitar`/`acreditar`; `CuentaRepository` gana la agregación del
límite diario; migración `V4__movimientos.sql`), sin reescribir el código
mergeado (AGENTS.md §14).

---

## 12. Risks

- **Discrepancia del ejemplo de CBU en la spec (FR-001):** el ejemplo
  `"00000031000000000001"` tiene 20 dígitos; el formato definido es de **22**
  dígitos (estándar real + requisito del diseño). Mitigación: tests y docs usan
  CBUs de 22 dígitos; si la spec se corrige, solo cambian las cadenas de los
  tests (ningún cambio estructural). **Pendiente de validación con el Product
  Owner** (ver reporte al orchestrator).
- **`@Version` silencioso si el adapter no mapea `version` en ambos sentidos:**
  el `merge` usaría version 0/null y el lock nunca dispararía (ER-005 no
  ocurriría). Mitigación: mapeo explícito documentado (§8.8) + test
  determinista de la Parte 2 de AC-012 (falla si el mapeo falta).
- **Flake del test de concurrencia (AC-012 Parte 1):** un schedule serializado
  produce `422` en vez de `409`. Mitigación: barrera/latch (ambas lecturas
  ocurren antes de cualquier commit), reintento acotado con datos frescos, y la
  Parte 2 determinista que valida el mecanismo sin depender del scheduler.
- **Límite diario superable bajo concurrencia:** dos transferencias
  concurrentes que individualmente respetan el límite pueden superarlo en
  conjunto (el chequeo es soft; solo el saldo está protegido por `@Version`).
  La spec no exige serializar el límite; documentado como limitación conocida.
- **`timestamptz` en `ddl-auto: validate` (lección V1):** `created_at` y
  `fecha` son `Instant` → columnas `TIMESTAMP WITH TIME ZONE` (§6.1); un
  `TIMESTAMP` simple rompería la validación de Hibernate 6.
- **Zona horaria del "día calendario" (BR-004):** se interpreta en UTC
  (convención de almacenamiento del repo). Si el negocio exigiera otra zona,
  el cálculo de `LocalDate.now(ZoneOffset.UTC)` en `TransferValidator` es el
  único punto a cambiar (documentado).
- **`save` = `merge` con `IDENTITY`:** el `saliente.getId()` se conoce tras el
  `save`; el unit test debe stubear el retorno con id (AC-003). Sin impacto en
  producción.
- **Testcontainers `disabledWithoutDocker = true`:** los tests de integración
  nuevos (incluida la concurrencia) se omiten localmente sin Docker; CI los
  cubre (riesgo residual ya documentado en SPEC-001 §12).
- **Usernames/CBUs únicos por método en tests:** el contenedor Testcontainers
  es por clase; `CuentaTestHelper` garantiza CBUs únicos (contador) y los
  usernames de registro se generan por método (convención de SPEC-003 §12).

---

## 13. Alternatives Considered

- **CoR multi-clase para la validación de la transferencia (10 validadores +
  orquestador):** descartada — los chequeos son heterogéneos (formato + reglas
  con repositorio) y comparten estado (cuentas cargadas); una clase única con
  chequeos secuenciales (precedente `RegistroValidator`, SPEC-003 §8.2) es más
  simple y el resultado `TransferenciaValidada` evita recargas (AGENTS.md §11).
- **`@Transactional` en el use case (`TransferirUseCase`):** descartado —
  importaría `org.springframework.transaction.annotation` en `application` y
  violaría la regla ArchUnit "application depende solo de domain" (AC-023).
  La frontera vive en infraestructura (§8.8, ADR-006).
- **`@Transactional` en los métodos del adapter/repositorio:** descartado —
  cada `save` abriría su propio tx; un fallo posterior no haría rollback de los
  anteriores → viola FR-002 (atomicidad).
- **`@Transactional` en el controller:** descartado — acopla la capa web a la
  gestión de transacciones y oculta la frontera; el service la hace explícita y
  testeable sin HTTP.
- **Locking pesimista (`SELECT ... FOR UPDATE`) en la cuenta origen:**
  descartado — la spec manda `@Version` (BR-006) y el pesimista serializaría
  las transferencias (la 2ª vería el saldo nuevo → `422`, nunca `409`; peor
  latencia).
- **Reintento automático server-side ante `409`:** descartado — A-004 lo
  prohíbe explícitamente (predecibilidad/testabilidad).
- **Plegar `MovimientoRepository` en `CuentaRepository`:** descartado — el
  historial es un read model (CQRS ligero, `ARCHITECTURE.md` §5); un puerto por
  tabla sigue la convención del repo y evita cargar la colección no acotada a
  través del agregado. (La agregación del límite diario SÍ queda en
  `CuentaRepository` por mandato de la spec §10, con delegación a
  `MovimientoJpaRepository`.)
- **`CuentaFactory` como clase separada (Strategy):** **adoptada por SPEC-002**
  (ya implementada: clase `CuentaFactory` con dispatch por `switch` sobre el
  tipo — SPEC-002 §13). El diseño original de SPEC-004 preveía un factory
  estático `Cuenta.crear` (convención `Cliente.crear`/`Usuario.crear`);
  ADR-007 lo descarta: SPEC-004 consume `CuentaFactory` (tests,
  `CuentaTestHelper`) y el constructor de reconstrucción sin cambios.
- **`Money` como `BigDecimal` + `String` moneda (sin VO):** descartado —
  `ARCHITECTURE.md` §4/§6 exige el VO (operaciones con `MathContext`, sin
  `double`) y BR-007 se evalúa por igualdad de `Moneda` (código ISO 4217
  alpha-3, ADR-007).
- **Generación de `CBU` en producción (SPEC-002 FR-002):** fuera de alcance de
  SPEC-004 — la apertura de cuentas **ya está implementada por SPEC-002**
  (generador con `SecureRandom` + regeneración ante colisión en
  `AbrirCuentaUseCase`, AC-028). Los tests de SPEC-004 generan CBUs de 22
  dígitos vía `CuentaTestHelper` (A-003).
- **`TransferenciaConfirmacionDto` en `adapter.web` (duplicado del resultado
  del use case):** descartado — el record `TransferenciaConfirmacion` de
  `application` es el contrato de salida del use case y su forma ES el body de
  la respuesta 201 (FR-001); un DTO web 1:1 sería duplicación (se mantiene la
  regla "el dominio nunca expone DTOs": el record vive en `application`, no en
  `domain`).
- **Test de concurrencia solo con "dos requests secuenciales + bump manual de
  version":** descartado — con una app stateless el bump previo a la request es
  simplemente el estado leído (nunca genera `409` por HTTP); el conflicto solo
  se produce con concurrencia real (Parte 1) o control de transacción explícito
  (Parte 2) — §8.9.

---

## 14. Decision

Implementar SPEC-004 con:

- **Dominio:** agregado `Cuenta` **de SPEC-002, extendido** con
  `debitar`/`acreditar` (ambos con la guarda `verificarActiva()`; `saldo` deja
  de ser `final` — ADR-007); VOs `CBU` (22 dígitos) y `Money` (`BigDecimal` +
  `Moneda`; operaciones nuevas con `MathContext`, sin `double`), VO `Moneda`
  (record `String`), `TipoMovimiento`, entidad `Movimiento`, puertos
  `CuentaRepository` (SPEC-002 + `montoTotalTransferenciasSalientesDelDia`) y
  `MovimientoRepository` (save/findByCuentaIdOrderByFechaDesc), puerto
  `TransferenciaEventPublisher`, evento `TransferenciaRealizada`, excepciones
  nuevas `SaldoInsuficiente`/`LimiteDiarioExcedido`/`AutoTransferencia`/
  `MonedaIncompatible` + reuso de `CuentaNoEncontrada`/`CuentaBloqueada`/
  `CbuInvalido`/`DatosInvalidos`/`AccesoDenegado` (SPEC-002).
- **Aplicación (Java puro):** `TransferValidator` (clase única, 10 chequeos en
  el orden de la spec §6, corta ante el primero, devuelve
  `TransferenciaValidada`), `TransferirUseCase` (orquesta el main flow y la
  emisión del evento), `ObtenerMovimientosUseCase` (historial con propiedad en
  aplicación), `TransferenciaConfirmacion` como resultado del use case.
- **Frontera transaccional (FR-002):** `infrastructure.service.TransferenciaService`
  con `@Transactional` que delega en `TransferirUseCase` (único `@Transactional`
  del sistema; ADR-006). `application` permanece Spring-free.
- **Concurrencia (BR-006, A-004):** `@Version Long` en `cuentas` (mapeado en
  ambos sentidos en el adapter); conflicto → `409 CONFLICTO_CONCURRENCIA` sin
  reintento automático.
- **Infraestructura:** `TransferenciaController` (POST → 201 +
  `TransferenciaConfirmacion`), `MovimientoController` (GET
  `/api/v1/cuentas/{id}/movimientos` → 200 + `List<MovimientoDto>`), JPA
  entities/repos/adapters de movimientos, `TransferenciaBeansConfig`,
  `SecurityConfig` (POST transferencias → CLIENTE; GET cuentas/*/movimientos →
  ADMIN|CLIENTE), `GlobalExceptionHandler` (**5 mapeos nuevos** —
  `SaldoInsuficiente`, `LimiteDiarioExcedido`, `AutoTransferencia`,
  `MonedaIncompatible`, `ObjectOptimisticLockingFailureException` → 409;
  `CuentaNoEncontrada`/`CuentaBloqueada`/`CbuInvalido` ya se mapean desde
  SPEC-002).
- **Migración** `V4__movimientos.sql` (tabla `movimientos`: FK cuenta,
  contraparte nullable, `fecha` timestamptz, índice `(cuenta_id, fecha)`; la
  tabla `cuentas` con `cbu` UNIQUE, FK cliente, `version BIGINT NOT NULL
  DEFAULT 0` y `created_at` timestamptz ya existe desde la `V3__cuentas.sql`
  de SPEC-002, sin cambios — ADR-007); propiedad
  `banco.negocio.limite-diario-transferencias` (default 200000) en ambos yml.
- **Límite diario (BR-004):** `montoTotalTransferenciasSalientesDelDia(clienteId,
  LocalDate)` en `CuentaRepository`, implementado con JPQL (COALESCE SUM,
  tipo TRANSFERENCIA_SALIENTE, subquery por cliente, rango del día en UTC).
- **Tests:** unit (VOs, agregado, validador, use cases — AC-003 evento),
  integración Testcontainers (`TransferenciaApiIntegrationTest` con AC-012 en
  dos partes: threads+latch + conflicto determinista; `MovimientosApiIntegrationTest`),
  helper `CuentaTestHelper` (A-003), ArchUnit sin cambios de reglas (AC-023).
