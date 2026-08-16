# Architecture — SPEC-004 (Transferencias entre Cuentas)

## 1. Feature

Transferencia atómica de dinero entre cuentas internas (`POST
/api/v1/transferencias`): un `CLIENTE` debita una cuenta propia y acredita otra
cuenta (propia o de terceros) identificada por `CBU`, en una única transacción
de base de datos (FR-002), registrando exactamente dos `Movimiento` (FR-003) y
emitiendo el evento de dominio `TransferenciaRealizada` (FR-004). Además,
consulta del historial de movimientos de una cuenta (`GET
/api/v1/cuentas/{id}/movimientos`, FR-005).

Como SPEC-002 (cuentas) nunca se implementó (no existe `Cuenta`, `CBU`, `Money`
ni migración de cuentas — verificado en el código), esta spec incorpora el
agregado `Cuenta` **mínimo** requerido por las transferencias (A-001): entidad,
VOs `CBU`/`Money`, enums, puerto `CuentaRepository`, métodos de dominio
`debitar`/`acreditar` y factory de creación por tipo. **No** se implementan los
endpoints de apertura/consulta/listado de cuentas (pertenecen a SPEC-002, out of
scope).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-004-transferencias.md` (APROBADA — fuente de verdad;
  FR-001..FR-005, BR-001..BR-007, AF-001..AF-003, ERR-001..ERR-009,
  AC-001..AC-023, asunciones A-001..A-007).
- `docs/adr/ADR-001` (monolito hexagonal + DDD), `ADR-002` (PostgreSQL +
  Flyway), `ADR-003` (JWT + Spring Security + RBAC), y el nuevo `ADR-006`
  (sección 11): agregado `Cuenta` mínimo (gap de SPEC-002), frontera
  transaccional y estrategia de concurrencia.

---

## 3. Affected Modules

- **`backend/src/main/java/com/banco/domain`** — nuevos: `model/Cuenta`,
  `model/Movimiento`, enums `model/TipoCuenta`, `model/EstadoCuenta`,
  `model/TipoMovimiento`, VOs `vo/CBU`, `vo/Money`, `vo/Moneda`, puertos
  `port/CuentaRepository`, `port/MovimientoRepository`,
  `port/TransferenciaEventPublisher`, evento `event/TransferenciaRealizada`,
  excepciones `exception/SaldoInsuficienteException`,
  `exception/CuentaNoEncontradaException`, `exception/CuentaBloqueadaException`,
  `exception/LimiteDiarioExcedidoException`,
  `exception/AutoTransferenciaException`,
  `exception/MonedaIncompatibleException`, `exception/CbuInvalidoException`.
- **`backend/src/main/java/com/banco/application`** — nuevos:
  `command/TransferirCommand`, `query/ObtenerMovimientosQuery`,
  `validator/DatosTransferencia`, `validator/TransferenciaValidada`,
  `validator/TransferValidator`, `usecase/TransferirUseCase`,
  `usecase/ObtenerMovimientosUseCase`, `usecase/TransferenciaConfirmacion`
  (resultado de aplicación — ver §8.3).
- **`backend/src/main/java/com/banco/infrastructure`** — nuevos:
  `adapter/web/TransferenciaController`, `adapter/web/MovimientoController`,
  `adapter/web/TransferirRequest`, `adapter/web/MovimientoDto`,
  `adapter/persistence/CuentaJpaEntity`, `adapter/persistence/CuentaJpaRepository`,
  `adapter/persistence/CuentaRepositoryAdapter`,
  `adapter/persistence/MovimientoJpaEntity`,
  `adapter/persistence/MovimientoJpaRepository`,
  `adapter/persistence/MovimientoRepositoryAdapter`,
  `service/TransferenciaService` (frontera transaccional — §8.8),
  `service/TransferenciaEventPublisherNoop`, `config/TransferenciaBeansConfig`.
  Modificados: `security/SecurityConfig` (matchers nuevos — §8.5),
  `adapter/web/GlobalExceptionHandler` (mapeos nuevos — §8.6).
- **`backend/src/main/resources`** — nueva migración
  `db/migration/V3__cuentas_y_movimientos.sql` (§6.1); `application.yml` agrega
  `banco.negocio.limite-diario-transferencias` (§8.7).
- **`backend/src/test`** — nuevos: `support/CuentaTestHelper` (A-003),
  `domain/CBUTest`, `domain/MoneyTest`, `domain/CuentaTest`,
  `domain/MovimientoTest`, `application/TransferValidatorTest`,
  `application/TransferirUseCaseTest`,
  `application/ObtenerMovimientosUseCaseTest`,
  `integration/TransferenciaApiIntegrationTest` (AC-001..AC-015),
  `integration/MovimientosApiIntegrationTest` (AC-016..AC-020).
  Modificado: `src/test/resources/application-test.yml` (nueva propiedad §8.7).
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
PostgreSQL 16 (Flyway V1+V2+V3, cuentas/movimientos)         db
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

- `model/Cuenta` — agregado raíz (id, clienteId, CBU cbu, TipoCuenta tipo,
  Money saldo, Moneda moneda, EstadoCuenta estado, Instant createdAt, Long
  version). Métodos de dominio: `debitar(Money)` → `SaldoInsuficienteException`
  si monto > saldo (BR-001); `acreditar(Money)`; factory estática `crear(...)`
  (saldo 0, ACTIVA, version 0 — A-001, §8.2).
- `model/Movimiento` — entidad (id, cuentaId, TipoMovimiento tipo, Money monto,
  Instant fecha, Long cuentaContraparteId nullable) + factory `crear(...)`.
- `model/TipoCuenta` { `CAJA_AHORRO`, `CUENTA_CORRIENTE` }, `model/EstadoCuenta`
  { `ACTIVA`, `BLOQUEADA` }, `model/TipoMovimiento` { `DEPOSITO`, `RETIRO`,
  `TRANSFERENCIA_ENTRANTE`, `TRANSFERENCIA_SALIENTE` } — enums planos
  (almacenados como String, convención de SPEC-001/003).
- `vo/CBU` — record que valida en el constructor: exactamente 22 dígitos
  (formato real del CBU argentino; ver §8.2 y nota sobre el ejemplo de la spec
  en §12). Lanza `CbuInvalidoException`.
- `vo/Money` — record `(BigDecimal monto, Currency moneda)` inmutable;
  constructor valida monto ≥ 0 y escala ≤ 2 (BR-003 parcial); operaciones
  `sumar`/`restar` con `MathContext.DECIMAL128` (sin `double` —
  `ARCHITECTURE.md` §6); comparaciones `esMayorQue`, `esMayorOIgualQue`,
  `esCero`; factory `static Money ars(BigDecimal)`.
- `vo/Moneda` — enum { `ARS` } con `Currency currency()` (única moneda del MVP;
  BR-007).
- `port/CuentaRepository` — `save`, `findById`, `findByCbu`, `findByClienteId`
  y `montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia)` →
  `Money` (BR-004, §8.10).
- `port/MovimientoRepository` — `save`, `findByCuentaIdOrderByFechaDesc`
  (FR-005). **Puerto separado** (decisión en §8.2/§13).
- `port/TransferenciaEventPublisher` — `void publicar(TransferenciaRealizada
  evento)` (FR-004; abstracción para que `application` emita el evento sin
  depender de infraestructura — mismo patrón que `TokenEmisor`/`PasswordHasher`).
- `event/TransferenciaRealizada` — `record (Money monto, CBU cbuOrigen, CBU
  cbuDestino, Instant fechaHora, Long idMovimientoSaliente)` (FR-004).
- `exception/*` — nuevas: `SaldoInsuficienteException` (ERR-001 → 422),
  `CuentaNoEncontradaException` (ERR-002 → 404), `CuentaBloqueadaException`
  (ERR-003 → 422), `LimiteDiarioExcedidoException` (ERR-007 → 422),
  `AutoTransferenciaException` (ERR-008 → 422), `MonedaIncompatibleException`
  (ERR-009 → 422), `CbuInvalidoException` (defensiva, → 400 con campo
  `cbuDestino` — ver §8.6). Se reutilizan `DatosInvalidosException` (ERR-004
  monto, 400) y `AccesoDenegadoException` (ERR-006, 403).

### 5.4 Persistencia — `infrastructure.adapter.persistence`

- `CuentaJpaEntity` (`@Entity @Table(name = "cuentas")`) con `@Version Long
  version` (BR-006) — ver §6.1/§8.8 para los detalles de `ddl-auto: validate`.
- `CuentaJpaRepository extends JpaRepository<CuentaJpaEntity, Long>`:
  `Optional<CuentaJpaEntity> findByCbu(String)`,
  `List<CuentaJpaEntity> findByClienteId(Long)`.
- `MovimientoJpaEntity` (`@Entity @Table(name = "movimientos")`).
- `MovimientoJpaRepository extends JpaRepository<MovimientoJpaEntity, Long>`:
  `List<MovimientoJpaEntity> findByCuentaIdOrderByFechaDesc(Long)` y la
  `@Query` JPQL de agregación del límite diario (§8.10).
- `CuentaRepositoryAdapter implements CuentaRepository` (`@Component`):
  mapeo explícito (enums como String, `CBU`/`Money`/`Moneda` como String/
  BigDecimal, **version mapeada en ambos sentidos** — crítica para el lock
  optimista, §8.8). Contiene además `MovimientoJpaRepository` para implementar
  la agregación del límite diario (dato vive en `movimientos`; ver §8.10).
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

Nuevo archivo `backend/src/main/resources/db/migration/V3__cuentas_y_movimientos.sql`:

```sql
-- V3__cuentas_y_movimientos.sql
-- Agregado Cuenta mínimo (SPEC-004, A-001) + Movimiento. Compatible con
-- ddl-auto: validate (lecciones de V1/V2):
--   * Instant -> TIMESTAMP WITH TIME ZONE (lección V1/TIMESTAMPTZ: Hibernate 6
--     mapea Instant a "timestamp(6) with time zone"; un TIMESTAMP simple
--     fallaría la validación).
--   * BIGSERIAL <-> Long @Id, BIGINT <-> Long, DECIMAL(19,2) <-> BigDecimal,
--     VARCHAR(n) <-> @Column(length=n). Hibernate validate no valida
--     constraints UNIQUE/FK (los define Flyway, como en V1/V2).

CREATE TABLE cuentas (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  BIGINT           NOT NULL REFERENCES clientes(id),
    cbu         VARCHAR(22)      NOT NULL,
    tipo        VARCHAR(15)      NOT NULL,   -- CAJA_AHORRO (11) | CUENTA_CORRIENTE (15)
    saldo       DECIMAL(19,2)    NOT NULL,
    moneda      VARCHAR(3)       NOT NULL,   -- ARS
    estado      VARCHAR(9)       NOT NULL,   -- ACTIVA (6) | BLOQUEADA (9)
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT           NOT NULL DEFAULT 0   -- @Version (BR-006)
);

ALTER TABLE cuentas ADD CONSTRAINT uq_cuentas_cbu UNIQUE (cbu);

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

- `cbu VARCHAR(22)` ↔ `@Column(length = 22)`: formato CBU de 22 dígitos
  (ver §8.2 y la discrepancia con el ejemplo de la spec en §12).
- `version BIGINT NOT NULL DEFAULT 0` ↔ `@Version @Column(nullable = false)
  Long version`: Hibernate gestiona el valor (0 en insert, +1 por UPDATE); la
  columna existe en el esquema y `validate` verifica tipo/ausencia de null
  (misma estrategia de consistencia que V1/V2).
- `cliente_id` FK → `clientes(id)` y `cuenta_contraparte_id` FK →
  `cuentas(id)`: las FK las define Flyway (Hibernate no las valida). Sin
  relación JPA mapeada (`Long` plano, convención de SPEC-001/003 — sin
  `AttributeConverter`).
- Índice `(cuenta_id, fecha)`: sirve al historial (FR-005) y al rango del día
  del límite diario (BR-004, §8.10).

### 6.2 Entidades de dominio

`Cuenta` y `Movimiento` según §5.3. `Cuenta` lleva `version` como `Long` en el
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
| `model/TipoCuenta` | `enum TipoCuenta { CAJA_AHORRO, CUENTA_CORRIENTE }` | Tipo de cuenta (A-001). |
| `model/EstadoCuenta` | `enum EstadoCuenta { ACTIVA, BLOQUEADA }` | Estado (BR-002). |
| `model/TipoMovimiento` | `enum TipoMovimiento { DEPOSITO, RETIRO, TRANSFERENCIA_ENTRANTE, TRANSFERENCIA_SALIENTE }` | Tipo de operación (ARCHITECTURE.md §4). |
| `vo/Moneda` | `enum Moneda { ARS }` + `Currency currency()` (`Currency.getInstance("ARS")`) | Moneda del MVP (BR-007). |
| `vo/CBU` | `record CBU(String valor)`; constructor compacto valida `^[0-9]{22}$` → `CbuInvalidoException` | VO inmutable, único (BR de SPEC-002 §5.1; formato definido en §8.2). |
| `vo/Money` | `record Money(BigDecimal monto, Currency moneda)`; constructor compacto valida monto ≠ null, moneda ≠ null, `monto.signum() >= 0`, `monto.scale() <= 2` → `DatosInvalidosException("monto", ...)`; `static Money ars(BigDecimal)`; `Money sumar(Money)` / `Money restar(Money)` (con `MathContext.DECIMAL128`); `boolean esMayorQue(Money)`, `esMayorOIgualQue(Money)`, `esCero()` | VO monetario (ARCHITECTURE.md §4/§6; BR-003 parcial, BR-007). |
| `model/Cuenta` | constructor público `Cuenta(Long id, Long clienteId, CBU cbu, TipoCuenta tipo, Money saldo, Moneda moneda, EstadoCuenta estado, Instant createdAt, Long version)`; factory `static Cuenta crear(Long clienteId, TipoCuenta tipo, CBU cbu, Moneda moneda, Instant createdAt)` (id null, saldo 0, ACTIVA, version 0); `void debitar(Money monto)` (si `monto.esMayorQue(saldo)` → `SaldoInsuficienteException`; `saldo = saldo.restar(monto)`); `void acreditar(Money monto)` (`saldo = saldo.sumar(monto)`); getters `getId/getClienteId/getCbu/getTipo/getSaldo/getMoneda/getEstado/getCreatedAt/getVersion` | Agregado raíz (A-001). Invariante de saldo ≥ 0 (BR-001). |
| `model/Movimiento` | constructor público `Movimiento(Long id, Long cuentaId, TipoMovimiento tipo, Money monto, Instant fecha, Long cuentaContraparteId)`; factory `static Movimiento crear(Long cuentaId, TipoMovimiento tipo, Money monto, Instant fecha, Long cuentaContraparteId)` (id null); getters | Entidad que registra cada operación (FR-003). |
| `port/CuentaRepository` | `Cuenta save(Cuenta)`, `Optional<Cuenta> findById(Long)`, `Optional<Cuenta> findByCbu(CBU)`, `List<Cuenta> findByClienteId(Long)`, `Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia)` | Puerto (dominio puro). La agregación del límite diario vive aquí por mandato de la spec §10 (§8.10). |
| `port/MovimientoRepository` | `Movimiento save(Movimiento)`, `List<Movimiento> findByCuentaIdOrderByFechaDesc(Long cuentaId)` | Puerto del historial (FR-005; §8.2). |
| `port/TransferenciaEventPublisher` | `void publicar(TransferenciaRealizada evento)` | Abstracción de la emisión del evento (FR-004). Una implementación (no-op) en infra. |
| `event/TransferenciaRealizada` | `record TransferenciaRealizada(Money monto, CBU cbuOrigen, CBU cbuDestino, Instant fechaHora, Long idMovimientoSaliente)` | Evento de dominio (FR-004; A-007: id = id del saliente). |
| `exception/SaldoInsuficienteException` | `RuntimeException`; mensaje "Saldo insuficiente" | ERR-001 → 422. |
| `exception/CuentaNoEncontradaException` | `RuntimeException`; mensaje "Cuenta no encontrada" | ERR-002 → 404. |
| `exception/CuentaBloqueadaException` | `RuntimeException`; mensaje "La cuenta está bloqueada" | ERR-003 → 422. |
| `exception/LimiteDiarioExcedidoException` | `RuntimeException`; mensaje "Límite diario de transferencias excedido" | ERR-007 → 422. |
| `exception/AutoTransferenciaException` | `RuntimeException`; mensaje "No se puede transferir a la misma cuenta" | ERR-008 → 422. |
| `exception/MonedaIncompatibleException` | `RuntimeException`; mensaje "Las monedas de las cuentas son incompatibles" | ERR-009 → 422. |
| `exception/CbuInvalidoException` | `RuntimeException`; mensaje "El CBU debe contener exactamente 22 dígitos" | Defensiva (VO `CBU`); el handler la mapea a 400 con campo `cbuDestino` (misma semántica que `DniInvalidoException`). |

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
| `adapter/persistence/CuentaJpaEntity` | `@Entity @Table(name="cuentas")`; `@Id @GeneratedValue(IDENTITY) Long id`; `@Column(name="cliente_id", nullable=false) Long clienteId`; `@Column(nullable=false, length=22) String cbu`; `@Column(nullable=false, length=15) String tipo`; `@Column(nullable=false) BigDecimal saldo`; `@Column(nullable=false, length=3) String moneda`; `@Column(nullable=false, length=9) String estado`; `@Column(name="created_at", nullable=false) Instant createdAt`; `@Version @Column(nullable=false) Long version`; getters/setters | Proyección JPA (sin lógica de negocio). |
| `adapter/persistence/CuentaJpaRepository` | `interface ... extends JpaRepository<CuentaJpaEntity, Long>`; `Optional<CuentaJpaEntity> findByCbu(String)`, `List<CuentaJpaEntity> findByClienteId(Long)` | Acceso Spring Data. |
| `adapter/persistence/CuentaRepositoryAdapter` | `@Component implements CuentaRepository`; `toEntity`/`toDomain` (enums como String, `CBU`/`Money`/`Moneda` mapeados explícitamente, **version en ambos sentidos**); delega la agregación del día en `MovimientoJpaRepository` | Implementa el puerto. |
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
| `backend/src/main/resources/db/migration/V3__cuentas_y_movimientos.sql` | Migración (§6.1). |

**Tests (`backend/src/test/...`) — NUEVOS**

| Archivo | Cubre |
| --- | --- |
| `java/com/banco/support/CuentaTestHelper.java` | Helper de creación de cuentas vía `CuentaRepository` (A-003): `Cuenta.crear(...)` + `acreditar` para fondear; CBU de 22 dígitos generado secuencialmente (ver §10). |
| `java/com/banco/domain/CBUTest.java`, `domain/MoneyTest.java`, `domain/CuentaTest.java`, `domain/MovimientoTest.java` | VOs y agregado (ver §10). |
| `java/com/banco/application/TransferValidatorTest.java`, `application/TransferirUseCaseTest.java`, `application/ObtenerMovimientosUseCaseTest.java` | Validación y use cases (ver §10). |
| `java/com/banco/integration/TransferenciaApiIntegrationTest.java` | Endpoint de transferencias + autorización + concurrencia (AC-001..AC-015, AC-012). |
| `java/com/banco/integration/MovimientosApiIntegrationTest.java` | Endpoint de historial (AC-016..AC-020). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `infrastructure/security/SecurityConfig.java` | Dos matchers nuevos (§8.5). Resto intacto. |
| `infrastructure/adapter/web/GlobalExceptionHandler.java` | Ocho mapeos nuevos (§8.6). |
| `backend/src/main/resources/application.yml` | `banco.negocio.limite-diario-transferencias: 200000` (§8.7). |
| `backend/src/test/resources/application-test.yml` | Ídem (§8.7). |

**Sin cambios:** `pom.xml` (ver §9), `BaseIntegrationTest`, `JwtTokenFactory`,
`JwtAuthenticationFilter`, `AuthenticatedUser`, `JwtService`, controllers
existentes, `ClienteBeansConfig`/`AuthBeansConfig`, `LayerArchitectureTest`
(las clases nuevas deben cumplir las reglas existentes — AC-023),
`V1__schema_inicial.sql`, `V2__usuarios.sql`.

### 8.2 Domain design (resumen ejecutivo)

- **Formato de `CBU`: exactamente 22 dígitos (`^[0-9]{22}$`).** Es el estándar
  real del CBU argentino (8 dígitos de banco + 14 de cuenta) y el requisito
  fijado para este diseño. **Discrepancia con la spec:** el ejemplo de FR-001
  (`"00000031000000000001"`) tiene 20 dígitos — ver §12. Los tests y la
  documentación usan CBUs de 22 dígitos (p. ej. `"00000031" + 14 dígitos`).
- **`Money` como VO obligatorio** (no `BigDecimal` pelado): `ARCHITECTURE.md`
  §4/§6 lo exige y BR-007 (moneda compatible) necesita la `Currency`.
  Constructor estricto: monto ≥ 0 y escala ≤ 2 → `DatosInvalidosException("monto")`
  (BR-003 parcial a nivel de VO; el `> 0` lo exige el validador, ver §8.4).
  Operaciones con `MathContext.DECIMAL128` (sin `double`).
- **Factory de `Cuenta`: método estático `Cuenta.crear(...)`, no clase
  `CuentaFactory`.** Convención del repo (`Cliente.crear`, `Usuario.crear`).
  En el MVP no existe comportamiento por tipo (comisiones fuera de alcance;
  "Strategy preparado" es evolución), así que el parámetro `tipo` basta.
  Cuando SPEC-002/005 introduzcan reglas por tipo, se extrae `CuentaFactory`
  (Strategy) sin cambiar los consumidores. Decisión registrada en §13.
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
  7. Money monto;
     try { monto = Money.ars(datos.monto()); }                  // valida ≥ 0 y escala ≤ 2 (VO)
     catch (DatosInvalidosException e) → re-lanza (400)
     if (monto.esCero()) → DatosInvalidosException("monto", "El monto debe ser mayor a 0")
                                                                 // ERR-004 → 400 (BR-003)
  8. if (origen.getMoneda() != destino.getMoneda())
        → MonedaIncompatibleException                           // ERR-009 → 422 (BR-007)
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
completa resultante (nuevas filas en **negrita**):

| Excepción | HTTP | `code` | `details` |
| --- | --- | --- | --- |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | `[{campo, mensaje}]` (ERR-004 monto, cbuDestino malformado, y los usos existentes) |
| `DniInvalidoException` (defensivo) | 400 | `DATOS_INVALIDOS` | `[{campo:"dni", mensaje}]` (existente) |
| **`CbuInvalidoException` (defensivo)** | **400** | **`DATOS_INVALIDOS`** | **`[{campo:"cbuDestino", mensaje}]`** |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | null (existente) |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | null (existente; cubre `{id}` no numérico) |
| `CredencialesInvalidasException` | 401 | `NO_AUTENTICADO` | null (existente) |
| `AccesoDenegadoException` | 403 | `ACCESO_DENEGADO` | null (ERR-006) |
| `ClienteNoEncontradoException` | 404 | `CLIENTE_NO_ENCONTRADO` | null (existente) |
| **`CuentaNoEncontradaException`** | **404** | **`CUENTA_NO_ENCONTRADA`** | **null (ERR-002)** |
| `ClienteDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo, mensaje}]` (existente) |
| `UsernameDuplicadoException` | 409 | `CONFLICTO_UNICIDAD` | `[{campo:"username", mensaje}]` (existente) |
| **`ObjectOptimisticLockingFailureException`** | **409** | **`CONFLICTO_CONCURRENCIA`** | **null (ERR-005)** |
| `DataIntegrityViolationException` (backstop race) | 409 | `CONFLICTO_UNICIDAD` | null (existente) |
| **`SaldoInsuficienteException`** | **422** | **`SALDO_INSUFICIENTE`** | **null (ERR-001)** |
| **`CuentaBloqueadaException`** | **422** | **`CUENTA_BLOQUEADA`** | **null (ERR-003)** |
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
| `domain/MoneyTest` | constructor: null → excepción; negativo → `DatosInvalidosException("monto")`; escala 3 → excepción; escala 2 OK; `sumar`/`restar` correctos; `esMayorQue`/`esMayorOIgualQue`/`esCero`; operaciones sin `double` | AC-021 |
| `domain/CuentaTest` | `crear` → id null, saldo 0, ACTIVA, version 0; `debitar` OK decrementa; `debitar` > saldo → `SaldoInsuficienteException` y saldo intacto; `acreditar` incrementa; saldo nunca negativo | AC-021, BR-001 |
| `domain/MovimientoTest` | `crear` (id null) y getters | AC-021 |
| `application/TransferValidatorTest` | **Orden y corte** (un caso por chequeo, §8.4): origen inexistente → `CuentaNoEncontradaException`; origen ajeno (clienteIdClaim distinto o null) → `AccesoDenegadoException`; origen BLOQUEADA → `CuentaBloqueadaException`; CBU malformado → `DatosInvalidosException("cbuDestino")`; destino inexistente → `CuentaNoEncontradaException`; mismo CBU que origen → `AutoTransferenciaException`; destino BLOQUEADA → `CuentaBloqueadaException`; monto 0/negativo/escala 3 → `DatosInvalidosException("monto")`; moneda distinta (Money USD vs ARS) → `MonedaIncompatibleException` (AC-015); saldo insuficiente → `SaldoInsuficienteException`; total del día + monto ≥ límite → `LimiteDiarioExcedidoException`; happy path → `TransferenciaValidada` con cuentas cargadas y monto `Money`; trim de `cbuDestino` | AC-004..AC-015 (lógica), AC-021 |
| `application/TransferirUseCaseTest` | happy path: valida, `debitar`/`acreditar` llamados, 4 `save`, evento publicado **una vez** con (monto, cbuOrigen, cbuDestino, fechaHora, idMovimientoSaliente = id del saliente guardado) — AC-003; confirmación con `idTransferencia` = id del saliente (A-007); saldo insuficiente → excepción propagada sin saves; ambos movimientos con **misma fecha y monto** y contrapartes cruzadas (FR-003, AC-002 lógica) | AC-001, AC-002, AC-003, AC-004 (lógica) |
| `application/ObtenerMovimientosUseCaseTest` | CLIENTE cuenta propia → lista ordenada (mock del repo); CLIENTE ajena o sin claim → `AccesoDenegadoException`; ADMIN cualquier cuenta → lista; inexistente → `CuentaNoEncontradaException`; orden 404-antes-403 | AC-016..AC-019 (lógica) |

### Integración (Spring Boot Test + Testcontainers + MockMvc)

Ambos tests extienden `BaseIntegrationTest` con su `@TestConfiguration TokenConfig`
anidada (patrón de `AuthApiIntegrationTest`). Helpers reutilizados:
`crearClienteAdmin(...)` (POST `/api/v1/clientes` con token ADMIN), `register` +
`login` (registro/login REALES de SPEC-003), y **`CuentaTestHelper`** (A-003):

```java
// support/CuentaTestHelper — crea cuentas vía el PUERTO (no HTTP, A-003).
public class CuentaTestHelper {
    private final CuentaRepository cuentaRepository;
    private long contadorCbu = 0;

    public Cuenta crearCuenta(Long clienteId, TipoCuenta tipo, BigDecimal saldoInicial) {
        CBU cbu = new CBU("00000031" + String.format("%014d", contadorCbu++)); // 8 + 14 = 22 dígitos
        Cuenta cuenta = Cuenta.crear(clienteId, tipo, cbu, Moneda.ARS, Instant.now());
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
(resolución del gap de SPEC-002, A-001) que condiciona a SPEC-002/SPEC-005;
(2) frontera transaccional en `infrastructure.service.TransferenciaService`
(único `@Transactional` del sistema; `application` permanece Spring-free —
AC-023); (3) estrategia de concurrencia: `@Version` + `409
CONFLICTO_CONCURRENCIA` sin reintento automático (A-004, BR-006). La decisión 2
rompe el precedente "sin `@Transactional` explícito" de SPEC-001/SPEC-003 y por
eso se registra.

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
- **`CuentaFactory` como clase separada (Strategy):** descartada para el MVP —
  no hay comportamiento por tipo todavía (comisiones fuera de alcance); el
  factory estático `Cuenta.crear` sigue la convención (`Cliente.crear`,
  `Usuario.crear`). Se extrae cuando SPEC-002/005 la necesiten.
- **`Money` como `BigDecimal` + `String` moneda (sin VO):** descartado —
  `ARCHITECTURE.md` §4/§6 exige el VO (operaciones con `MathContext`, sin
  `double`) y BR-007 necesita la `Currency`.
- **Generación de `CBU` en producción (SPEC-002 FR-002):** descartada — la
  apertura de cuentas es de SPEC-002 (out of scope); `Cuenta.crear` recibe el
  CBU y los tests lo generan (A-003). El generador llega con SPEC-002.
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

- **Dominio:** agregado `Cuenta` mínimo (A-001) con VOs `CBU` (22 dígitos) y
  `Money` (`BigDecimal` + `Currency`, `MathContext`, escala ≤ 2, no negativo),
  enum `Moneda` (ARS), `TipoCuenta`, `EstadoCuenta`, `TipoMovimiento`, entidad
  `Movimiento`, puertos `CuentaRepository` (save/findById/findByCbu/
  findByClienteId/montoTotalTransferenciasSalientesDelDia) y
  `MovimientoRepository` (save/findByCuentaIdOrderByFechaDesc), puerto
  `TransferenciaEventPublisher`, evento `TransferenciaRealizada`, 7 excepciones
  nuevas + reuso de `DatosInvalidosException`/`AccesoDenegadoException`.
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
  entities/repos/adapters, `TransferenciaBeansConfig`, `SecurityConfig` (POST
  transferencias → CLIENTE; GET cuentas/*/movimientos → ADMIN|CLIENTE),
  `GlobalExceptionHandler` (8 mapeos nuevos, incluido
  `ObjectOptimisticLockingFailureException` → 409).
- **Migración** `V3__cuentas_y_movimientos.sql` (cuentas con `cbu` UNIQUE, FK
  cliente, `version BIGINT NOT NULL DEFAULT 0`, `created_at` timestamptz;
  movimientos con FK cuenta, contraparte nullable, `fecha` timestamptz, índice
  `(cuenta_id, fecha)`); propiedad `banco.negocio.limite-diario-transferencias`
  (default 200000) en ambos yml.
- **Límite diario (BR-004):** `montoTotalTransferenciasSalientesDelDia(clienteId,
  LocalDate)` en `CuentaRepository`, implementado con JPQL (COALESCE SUM,
  tipo TRANSFERENCIA_SALIENTE, subquery por cliente, rango del día en UTC).
- **Tests:** unit (VOs, agregado, validador, use cases — AC-003 evento),
  integración Testcontainers (`TransferenciaApiIntegrationTest` con AC-012 en
  dos partes: threads+latch + conflicto determinista; `MovimientosApiIntegrationTest`),
  helper `CuentaTestHelper` (A-003), ArchUnit sin cambios de reglas (AC-023).
