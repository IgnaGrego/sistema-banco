# Architecture — SPEC-005 (Depósitos y Retiros)

## 1. Feature

Depósitos (`POST /api/v1/depositos`, exclusivo de `ADMIN` — A-001) y retiros
(`POST /api/v1/retiros`, `ADMIN` sobre cualquier cuenta o `CLIENTE` sobre
cuentas propias) de dinero en cuentas internas, en **una única transacción de
base de datos** por operación (BR-004): se actualiza el saldo vía los métodos
de dominio `acreditar`/`debitar` del agregado `Cuenta` (BR-002), se persiste
exactamente un `Movimiento` de tipo `DEPOSITO` o `RETIRO` (FR-003) y se emite
el evento de dominio `DepositoRealizado`/`RetiroRealizado` (FR-004, sin
suscriptores en este sprint — la emisión se verifica en tests, AC-002/AC-009).
Ambos endpoints responden `201 Created` con confirmación (A-002).

SPEC-004 (transferencias) **está implementada, revisada y mergeada** (PR #21):
existen el agregado `Cuenta` con `debitar`/`acreditar`, `Money` con las
operaciones aritméticas y la factory `ars`, la entidad `Movimiento` +
`TipoMovimiento` (incluye `DEPOSITO`/`RETIRO`), los puertos
`CuentaRepository`/`MovimientoRepository`, el mecanismo `@Version` (mapeado en
ambos sentidos en `CuentaRepositoryAdapter`), la frontera transaccional en
`infrastructure/service/TransferenciaService` (ADR-006), el envelope de
errores con todos los códigos que SPEC-005 necesita y los matchers de
`SecurityConfig`. SPEC-005 **reutiliza** ese modelo sin cambios de esquema (la
migración `V4__movimientos.sql` ya soporta `DEPOSITO` y `RETIRO` — §6) y sin
cambios en `GlobalExceptionHandler` (§8.6).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-005-depositos-retiros.md` (APROBADA — fuente de verdad;
  FR-001..FR-004, BR-001..BR-004, AF-001, ERR-001..ERR-006, AC-001..AC-020,
  asunciones A-001, A-002).
- `docs/architecture/SPEC-004.md` — plantilla y diseño previo que SPEC-005
  extiende: agregado `Cuenta` con `debitar`/`acreditar`, `Money` con
  `sumar`/`restar`/`esMayorQue`/`esMayorOIgualQue`/`esCero`/`ars`, entidad
  `Movimiento` y `TipoMovimiento`, puertos `CuentaRepository`/
  `MovimientoRepository`/`TransferenciaEventPublisher`, evento
  `TransferenciaRealizada`, frontera transaccional (§8.8, ADR-006), `@Version`
  + `409 CONFLICTO_CONCURRENCIA` (§8.8), test de concurrencia en dos partes
  (§8.9), validador clase-única (§8.4), matchers (§8.5) y mapeos de errores
  (§8.6) que SPEC-005 reutiliza.
- `docs/adr/ADR-006` (decisión 2 — frontera transaccional en infraestructura —
  y decisión 3 — concurrencia `@Version` + 409 sin reintento — siguen vigentes
  y se reutilizan tal cual; el calificativo "único `@Transactional` del
  sistema" estaba acotado al alcance de SPEC-004) y `docs/adr/ADR-007`
  (reconciliación del modelo sobre SPEC-002: `Cuenta`/`Money`/`Movimiento`/
  `V4__movimientos.sql` — el modelo que SPEC-005 consume sin cambios). **No se
  crean ADRs nuevos** (§11).

---

## 3. Affected Modules

- **`backend/src/main/java/com/banco/domain`** — **sin cambios en las clases
  existentes** (se reutilizan `model/Cuenta` con `debitar`/`acreditar`,
  `model/Movimiento`, `model/TipoMovimiento`, `vo/Money`, `port/CuentaRepository`,
  `port/MovimientoRepository` y las excepciones `CuentaNoEncontradaException`,
  `CuentaBloqueadaException`, `SaldoInsuficienteException`,
  `DatosInvalidosException`, `AccesoDenegadoException`). **Nuevos de
  SPEC-005:** eventos `event/DepositoRealizado` y `event/RetiroRealizado`,
  puertos `port/DepositoEventPublisher` y `port/RetiroEventPublisher`.
- **`backend/src/main/java/com/banco/application`** — **nuevos:**
  `command/RealizarDepositoCommand`, `command/RealizarRetiroCommand`,
  `validator/DatosDepositoRetiro`, `validator/DepositoRetiroValidado`,
  `validator/DepositoRetiroValidator` (clase única con dos cadenas de
  validación — §8.2/§8.4), `usecase/RealizarDepositoUseCase`,
  `usecase/RealizarRetiroUseCase`, `usecase/DepositoConfirmacion`,
  `usecase/RetiroConfirmacion` (resultados de aplicación — §8.3).
- **`backend/src/main/java/com/banco/infrastructure`** — **modificado:**
  `security/SecurityConfig` (dos matchers nuevos — §8.5). **Nuevos:**
  `adapter/web/DepositoController`, `adapter/web/RetiroController`,
  `adapter/web/DepositoRequest`, `adapter/web/RetiroRequest`,
  `service/DepositoService` y `service/RetiroService` (fronteras
  transaccionales — §8.8), `service/DepositoEventPublisherNoop`,
  `service/RetiroEventPublisherNoop`, `config/DepositoRetiroBeansConfig`.
  **Sin cambios:** `adapter/web/GlobalExceptionHandler` (§8.6), todo
  `adapter/persistence` (`CuentaRepositoryAdapter`/`MovimientoRepositoryAdapter`
  ya cubren depósitos/retiros: `save`/`findById` y `save` — §5.4).
- **`backend/src/main/resources`** — **sin cambios**: no hay migración nueva
  (la `V4__movimientos.sql` de SPEC-004 ya soporta `DEPOSITO`/`RETIRO` — §6);
  `application.yml`/`application-test.yml` sin propiedades nuevas (SPEC-005 no
  introduce límites ni config de negocio).
- **`backend/src/test`** — **nuevos:**
  `application/DepositoRetiroValidatorTest`,
  `application/RealizarDepositoUseCaseTest`,
  `application/RealizarRetiroUseCaseTest`,
  `integration/DepositosRetirosApiIntegrationTest` (AC-001..AC-017, AC-019,
  incluida la concurrencia AC-015). **Sin cambios:** `domain/MoneyTest`,
  `domain/CuentaTest`, `domain/MovimientoTest` (AC-018: `sumar`/`restar` y
  `debitar`/`acreditar` ya están cubiertos por SPEC-004 — no se duplica
  cobertura), `architecture/LayerArchitectureTest` (reglas sin cambios —
  AC-020).
- **No afectado:** frontend, `docker/`, `pom.xml` (sin dependencias nuevas —
  §9), `JwtService`/`JwtAuthenticationFilter`/`AuthenticatedUser`/
  `JwtTokenFactory` (mecanismo de autenticación intacto), `TransferenciaService`
  y todo el flujo de SPEC-004.

---

## 4. Application Flow

```text
REST (DepositoController: POST /api/v1/depositos;
      RetiroController:   POST /api/v1/retiros)            infrastructure.adapter.web
        ↓  DepositoRequest / RetiroRequest (records planos {cuentaId, monto})
Security (JwtAuthenticationFilter → JwtService)             infrastructure.security
        ↓  resuelve AuthenticatedUser(clienteId, rol);
           matcher POST /depositos → hasRole("ADMIN");
           matcher POST /retiros   → hasAnyRole("ADMIN", "CLIENTE")
DepositoService / RetiroService.ejecutar(...)  [@Transactional — UNA transacción]  infrastructure.service
        ↓  delegan en los use cases (Java puro)
RealizarDepositoUseCase / RealizarRetiroUseCase  application.usecase
        ↓  DepositoRetiroValidator.validarDeposito / validarRetiro — cadenas de 5-6
           chequeos en el orden del main flow (spec §6 paso 2)
Domain (Cuenta.acreditar/debitar, Movimiento DEPOSITO/RETIRO, Money,
        CuentaRepository, MovimientoRepository,
        DepositoEventPublisher/RetiroEventPublisher)        domain
        ↓
Persistence (CuentaRepositoryAdapter → CuentaJpaEntity@Version,
             MovimientoRepositoryAdapter → MovimientoJpaEntity)  infrastructure.adapter.persistence
        ↓
PostgreSQL 16 (Flyway: V1+V2 de SPEC-001/003, V3 cuentas de SPEC-002,
             V4 movimientos de SPEC-004)                    db
```

**Flujo concreto del depósito (main flow de la spec, §6):**

1. `DepositoController` construye `RealizarDepositoCommand(cuentaId, monto,
   rol, clienteIdClaim)` desde el `DepositoRequest` y el `AuthenticatedUser`
   del `SecurityContext` (mismo mecanismo que `TransferenciaController`);
   delega en `DepositoService`.
2. `DepositoService` (`@Transactional`) delega en `RealizarDepositoUseCase`
   (toda la operación corre en una única transacción de BD — BR-004).
3. El use case construye `DatosDepositoRetiro` y ejecuta
   `depositoRetiroValidator.validarDeposito(...)`: cadena de 5 chequeos en
   orden (autorización `ADMIN` → cuenta existe → `ACTIVA` → monto válido —
   §8.4) que corta ante el primer error y devuelve
   `DepositoRetiroValidado(cuenta, monto)`.
4. `Instant fecha = Instant.now()`; `cuenta.acreditar(monto)` (BR-002; la
   guarda `verificarActiva()` re-verifica el estado — doble barrera).
5. En el mismo tx: `cuentaRepository.save(cuenta)` (UPDATE con `@Version` —
   BR-004) y `movimientoRepository.save(Movimiento.crear(cuentaId, DEPOSITO,
   monto, fecha, null))` (FR-003; `cuentaContraparteId` null — no hay
   contraparte).
6. Se emite `DepositoRealizado(monto, cuentaId, fecha, idMovimiento)` vía el
   puerto `DepositoEventPublisher` (FR-004; sin suscriptores — el emisor no-op
   loguea a nivel debug).
7. Se responde `201 Created` con `DepositoConfirmacion(idMovimiento, cuentaId,
   monto, fechaHora)` (FR-001, A-002).

**Flujo concreto del retiro (FR-002):**

1. `RetiroController` construye `RealizarRetiroCommand(...)` y delega en
   `RetiroService`; el `AuthenticatedUser` puede ser `ADMIN` (clienteId null) o
   `CLIENTE` (clienteId propio).
2. `RetiroService` (`@Transactional`) delega en `RealizarRetiroUseCase`.
3. `validarRetiro(...)`: cadena de 6 chequeos (cuenta existe → propiedad para
   `CLIENTE` → `ACTIVA` → monto válido → saldo suficiente — §8.4) → `403` para
   cuenta ajena (ERR-005, AC-014), `422` para saldo insuficiente (ERR-002,
   AC-010).
4. `cuenta.debitar(monto)` (BR-002: invariante saldo nunca negativo — doble
   barrera con el paso 5 del validador); `save(cuenta)`; `save(Movimiento.crear(
   cuentaId, RETIRO, monto, fecha, null))`.
5. Se emite `RetiroRealizado(monto, cuentaId, fecha, idMovimiento)` vía
   `RetiroEventPublisher`.
6. Se responde `201 Created` con `RetiroConfirmacion(idMovimiento, cuentaId,
   monto, fechaHora)` (A-002).

**Nota de transaccionalidad:** los use cases son Java puro (sin Spring); el
`@Transactional` que garantiza la atomicidad (BR-004) vive en
`DepositoService`/`RetiroService` (infraestructura) — ver §8.8 y ADR-006.

---

## 5. Components

### 5.1 Entry points / presentación — `infrastructure.adapter.web`

| Método | Ruta | Rol requerido | Status OK | Error esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/depositos` | `ADMIN` (solo; A-001) | `201 Created` + `DepositoConfirmacion` | 400, 401, 403, 404, 409, 422 |
| `POST` | `/api/v1/retiros` | `ADMIN` (cualquiera) o `CLIENTE` (propias) | `201 Created` + `RetiroConfirmacion` | 400, 401, 403, 404, 409, 422 |

- `DepositoController` (`@RestController @RequestMapping("/api/v1/depositos")`):
  solo construye el command, resuelve el `AuthenticatedUser` y delega en
  `DepositoService`; mapea el resultado a la respuesta `201`. Sin reglas de
  negocio. Sin `Location` header (no existe `GET /depositos/{id}`; misma
  decisión que `TransferenciaController` en SPEC-004).
- `RetiroController` (`@RestController @RequestMapping("/api/v1/retiros")`):
  ídem, delega en `RetiroService`.
- DTOs de entrada `DepositoRequest(Long cuentaId, BigDecimal monto)` y
  `RetiroRequest(Long cuentaId, BigDecimal monto)`: records planos sin
  anotaciones de validación (convención — SPEC-001 §5.1; dos records por
  endpooint, misma convención que `TransferirRequest` — §13). JSON malformado
  o `monto` no numérico → `HttpMessageNotReadableException` → `400` (mapeo
  existente, §8.6).
- `SecurityConfig`: dos matchers nuevos (§8.5). Resto del chain sin cambios.

### 5.2 Aplicación — `application`

- `RealizarDepositoUseCase.ejecutar(RealizarDepositoCommand)` →
  `DepositoConfirmacion`. Dependencias: `CuentaRepository`,
  `MovimientoRepository`, `DepositoRetiroValidator`, `DepositoEventPublisher`.
- `RealizarRetiroUseCase.ejecutar(RealizarRetiroCommand)` →
  `RetiroConfirmacion`. Dependencias: `CuentaRepository`,
  `MovimientoRepository`, `DepositoRetiroValidator`, `RetiroEventPublisher`.
- Validación: `DatosDepositoRetiro` (record de entrada del validador) +
  `DepositoRetiroValidator` (**clase única** con dos cadenas públicas
  `validarDeposito`/`validarRetiro` que comparten helpers privados — §8.2/§8.4;
  decisión de no usar dos clases ni un flag booleano, ver §13). Devuelve
  `DepositoRetiroValidado(Cuenta cuenta, Money monto)` (cuenta ya cargada y
  monto validado — evita recargas, mismo patrón que `TransferenciaValidada`).
- Resultados: `DepositoConfirmacion(Long idMovimiento, Long cuentaId,
  BigDecimal monto, Instant fechaHora)` y `RetiroConfirmacion(...)` (misma
  forma) — records de la capa de aplicación que son a la vez el contrato de
  salida del use case y el body de la respuesta 201 (desviación documentada en
  §8.3, precedente `TransferenciaConfirmacion`).
- Los beans se declaran en `infrastructure.config.DepositoRetiroBeansConfig`
  (la aplicación no usa anotaciones Spring).

### 5.3 Dominio — `domain`

- **Sin cambios** en `model/Cuenta` (`acreditar`/`debitar` con guarda
  `verificarActiva()` — BR-002/BR-003), `model/Movimiento` (factory `crear`
  con `cuentaContraparteId` nullable — DEPOSITO/RETIRO pasan `null`),
  `model/TipoMovimiento` (`DEPOSITO`, `RETIRO` ya existen), `vo/Money`
  (`sumar`/`restar`/`esMayorQue`/`esCero`/`ars`), `port/CuentaRepository`
  (`findById`, `save`) y `port/MovimientoRepository` (`save`).
- `event/DepositoRealizado` — `record (Money monto, Long cuentaId, Instant
  fechaHora, Long idMovimiento)` (FR-004). `idMovimiento` identifica la
  operación para conciliación (mismo rol que `idMovimientoSaliente` de
  `TransferenciaRealizada`).
- `event/RetiroRealizado` — `record (Money monto, Long cuentaId, Instant
  fechaHora, Long idMovimiento)` (FR-004).
- `port/DepositoEventPublisher` — `void publicar(DepositoRealizado evento)`
  (FR-004; abstracción para que `application` emita el evento sin depender de
  infraestructura — mismo patrón que `TransferenciaEventPublisher`).
- `port/RetiroEventPublisher` — `void publicar(RetiroRealizado evento)`.
  **Decisión: dos puertos dedicados** (no uno genérico) — §13.
- Excepciones: **todas ya existen** y se reutilizan: `CuentaNoEncontradaException`
  (ERR-003 → 404), `CuentaBloqueadaException` (ERR-004 → 422; la lanza la
  guarda `verificarActiva()`), `SaldoInsuficienteException` (ERR-002 → 422),
  `DatosInvalidosException` (ERR-001 → 400 con campo `monto`),
  `AccesoDenegadoException` (ERR-005 → 403).

### 5.4 Persistencia — `infrastructure.adapter.persistence`

Sin cambios. SPEC-005 usa únicamente operaciones ya implementadas:

- `CuentaRepositoryAdapter.findById` / `.save` (mapeo `version` en ambos
  sentidos ya presente — crítico para el lock optimista, §8.8).
- `MovimientoRepositoryAdapter.save` (persiste la entidad `MovimientoJpaEntity`
  de tipo `DEPOSITO`/`RETIRO`; `cuenta_contraparte_id` null — la columna ya es
  nullable en `V4__movimientos.sql`).

### 5.5 Seguridad — `infrastructure.security`

Sin cambios de mecanismo: el filtro JWT, `JwtService` y `AuthenticatedUser`
quedan intactos. Solo `SecurityConfig` agrega dos matchers (§8.5). La
autorización se aplica en **dos niveles**: matcher (gate grueso) + chequeo en
la capa de aplicación (defensa en profundidad y testabilidad unitaria de
ERR-005 — AC-018) — decisión documentada en §8.5/§13.

### 5.6 Configuración — `infrastructure.config` y `infrastructure.service`

- `DepositoRetiroBeansConfig` (`@Configuration`): beans `DepositoRetiroValidator`,
  `RealizarDepositoUseCase`, `RealizarRetiroUseCase` (§8.7). Sin `@Value`:
  SPEC-005 no introduce propiedades nuevas.
- `service/DepositoService` (`@Service`, método `@Transactional`): frontera
  transaccional que delega en `RealizarDepositoUseCase` (§8.8). No necesita
  declaración de bean (component scanning).
- `service/RetiroService` (`@Service`, método `@Transactional`): ídem, delega
  en `RealizarRetiroUseCase`.
- `service/DepositoEventPublisherNoop` y `service/RetiroEventPublisherNoop`
  (`@Component implements ...EventPublisher`): emisores sin suscriptores
  (loguean a nivel debug; la emisión se verifica en tests — AC-002/AC-009).

### 5.7 Async work

Ninguno: sin jobs ni colas. Los eventos `DepositoRealizado`/`RetiroRealizado`
se publican de forma **síncrona en memoria** dentro de la transacción (sin
suscriptores; sin outbox en el MVP — misma decisión que SPEC-004 §5.7).

---

## 6. Data Changes

**Ninguna.** SPEC-005 no requiere migración de esquema, ni nuevas entidades
JPA, ni propiedades de configuración:

- El saldo de `Cuenta` se actualiza vía los métodos de dominio
  `acreditar`/`debitar` (BR-002) y se persiste con `CuentaRepository.save` —
  la columna `version` (y su mapeo bidireccional) ya existe desde
  SPEC-002/SPEC-004 (BR-004).
- El `Movimiento` de tipo `DEPOSITO`/`RETIRO` persiste en la tabla
  `movimientos` creada por la migración `V4__movimientos.sql` de SPEC-004, cuya
  columna `tipo VARCHAR(22)` ya soporta `DEPOSITO` (8) y `RETIRO` (6); la
  columna `cuenta_contraparte_id` es nullable y queda `NULL` para
  depósitos/retiros (no hay contraparte). Compatible con `ddl-auto: validate`
  (sin entidades nuevas, sin cambios).
- No hay nuevas tablas, columnas, índices ni propiedades
  (`application.yml`/`application-test.yml` intactos — SPEC-005 no define
  límites ni topes de negocio; ver `docs/specs/SPEC-005` §12 Out of Scope).

---

## 7. External Integrations

- **PostgreSQL 16** (única integración): local
  `jdbc:postgresql://localhost:5433/banco` (usuario/contraseña `banco/banco`,
  ver `docker/docker-compose.yml`). Sin cambios.
- Tests de integración: Testcontainers `postgres:16` con `@ServiceConnection`
  (Postgres real — necesario para AC-015, concurrencia real). Sin cambios.
- **Sin** proveedores externos, mensajería ni frontend.

---

## 8. Detailed Design

### 8.1 File map completo

Packages bajo `backend/src/main/java/com/banco` salvo indicación.

**Archivos NUEVOS:**

**Domain (`com.banco.domain.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `event/DepositoRealizado` | `record DepositoRealizado(Money monto, Long cuentaId, Instant fechaHora, Long idMovimiento)` | Evento de dominio del depósito (FR-004). `idMovimiento` = id del `Movimiento` DEPOSITO generado (conciliación). |
| `event/RetiroRealizado` | `record RetiroRealizado(Money monto, Long cuentaId, Instant fechaHora, Long idMovimiento)` | Evento de dominio del retiro (FR-004). |
| `port/DepositoEventPublisher` | `void publicar(DepositoRealizado evento)` | Abstracción de la emisión (FR-004). Una implementación (no-op) en infra. |
| `port/RetiroEventPublisher` | `void publicar(RetiroRealizado evento)` | Abstracción de la emisión (FR-004). Una implementación (no-op) en infra. |

**Application (`com.banco.application.*`)** — Java puro, sin imports de Spring.

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `command/RealizarDepositoCommand` | `record RealizarDepositoCommand(Long cuentaId, BigDecimal monto, String rol, Long clienteIdClaim)` | Entrada del use case de depósito (FR-001). El sujeto autenticado viaja en el command (patrón de `ObtenerMovimientosQuery` — §8.3). |
| `command/RealizarRetiroCommand` | `record RealizarRetiroCommand(Long cuentaId, BigDecimal monto, String rol, Long clienteIdClaim)` | Entrada del use case de retiro (FR-002). |
| `validator/DatosDepositoRetiro` | `record DatosDepositoRetiro(Long cuentaId, BigDecimal monto, String rol, Long clienteIdClaim)` | Entrada del `DepositoRetiroValidator`. Passthrough del command (no hay normalización: campos tipados — §8.3); mantiene el validador desacoplado del command. |
| `validator/DepositoRetiroValidado` | `record DepositoRetiroValidado(Cuenta cuenta, Money monto)` | Salida del validador: cuenta ya cargada + monto validado (evita recargar en el use case). |
| `validator/DepositoRetiroValidator` | `DepositoRetiroValidado validarDeposito(DatosDepositoRetiro)` (5 chequeos) y `DepositoRetiroValidado validarRetiro(DatosDepositoRetiro)` (6 chequeos); helpers privados compartidos (carga de cuenta, verificación ACTIVA, validación del monto, verificación de propiedad) | Validación de depósito/retiro (BR-001..BR-004, §9 de la spec). Clase única, dos cadenas explícitas (decisión §8.2/§13). |
| `usecase/RealizarDepositoUseCase` | `DepositoConfirmacion ejecutar(RealizarDepositoCommand)`; dependencias: `CuentaRepository`, `MovimientoRepository`, `DepositoRetiroValidator`, `DepositoEventPublisher` | Orquesta el main flow del depósito (§4). |
| `usecase/RealizarRetiroUseCase` | `RetiroConfirmacion ejecutar(RealizarRetiroCommand)`; dependencias: `CuentaRepository`, `MovimientoRepository`, `DepositoRetiroValidator`, `RetiroEventPublisher` | Orquesta el main flow del retiro (§4). |
| `usecase/DepositoConfirmacion` | `record DepositoConfirmacion(Long idMovimiento, Long cuentaId, BigDecimal monto, Instant fechaHora)` | Resultado del use case = contrato de la respuesta 201 (FR-001, A-002). Ver §8.3. |
| `usecase/RetiroConfirmacion` | `record RetiroConfirmacion(Long idMovimiento, Long cuentaId, BigDecimal monto, Instant fechaHora)` | Resultado del use case = contrato de la respuesta 201 (FR-002, A-002). |

**Infrastructure (`com.banco.infrastructure.*`)**

| Clase | Miembros clave | Propósito |
| --- | --- | --- |
| `adapter/web/DepositoController` | `@RestController @RequestMapping("/api/v1/depositos")`; `POST` → `201` + `DepositoConfirmacion` | Coordina; sin reglas de negocio. |
| `adapter/web/RetiroController` | `@RestController @RequestMapping("/api/v1/retiros")`; `POST` → `201` + `RetiroConfirmacion` | Coordina; sin reglas de negocio. |
| `adapter/web/DepositoRequest` | `record DepositoRequest(Long cuentaId, BigDecimal monto)` | Entrada `POST /api/v1/depositos` (FR-001). |
| `adapter/web/RetiroRequest` | `record RetiroRequest(Long cuentaId, BigDecimal monto)` | Entrada `POST /api/v1/retiros` (FR-002). |
| `service/DepositoService` | `@Service`; `@Transactional public DepositoConfirmacion ejecutar(RealizarDepositoCommand)`; delega en `RealizarDepositoUseCase` | **Frontera transaccional** (BR-004) — patrón ADR-006. |
| `service/RetiroService` | `@Service`; `@Transactional public RetiroConfirmacion ejecutar(RealizarRetiroCommand)`; delega en `RealizarRetiroUseCase` | **Frontera transaccional** (BR-004) — patrón ADR-006. |
| `service/DepositoEventPublisherNoop` | `@Component implements DepositoEventPublisher`; `publicar` loguea a nivel debug | Emisor sin suscriptores (FR-004; AC-002 se cubre con mock en unit test). |
| `service/RetiroEventPublisherNoop` | `@Component implements RetiroEventPublisher`; `publicar` loguea a nivel debug | Emisor sin suscriptores (FR-004; AC-009 se cubre con mock en unit test). |
| `config/DepositoRetiroBeansConfig` | `@Configuration`; beans de §8.7 | Wiring (aplicación sin Spring). |

**Tests (`backend/src/test/...`) — NUEVOS**

| Archivo | Cubre |
| --- | --- |
| `java/com/banco/application/DepositoRetiroValidatorTest.java` | Cadenas de validación: orden, corte, ERR-001..ERR-005 lógica (AC-003..AC-006, AC-010..AC-014, AC-018). |
| `java/com/banco/application/RealizarDepositoUseCaseTest.java` | Use case de depósito: happy path, evento 1 vez (AC-001/AC-002 lógica), rollback sin evento (AC-003 lógica). |
| `java/com/banco/application/RealizarRetiroUseCaseTest.java` | Use case de retiro: happy path, evento 1 vez (AC-007..AC-009 lógica), saldo insuficiente (AC-010 lógica). |
| `java/com/banco/integration/DepositosRetirosApiIntegrationTest.java` | Endpoints de depósito y retiro + autorización + concurrencia (AC-001..AC-017, AC-019; AC-015 en dos partes — §8.9). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `infrastructure/security/SecurityConfig.java` | Dos matchers nuevos (§8.5): `POST /api/v1/depositos` → `hasRole("ADMIN")`; `POST /api/v1/retiros` → `hasAnyRole("ADMIN", "CLIENTE")`. Resto intacto. |

**Sin cambios:** `GlobalExceptionHandler` (§8.6), todo `adapter/persistence`,
migraciones, `application.yml`/`application-test.yml`, `pom.xml` (ver §9),
`domain/*` existente (`Cuenta`, `Movimiento`, `TipoMovimiento`, `Money`,
puertos), `application/*` de SPEC-004, `TransferenciaService`,
`TransferenciaEventPublisherNoop`, `TransferenciaBeansConfig`, controllers
existentes, `BaseIntegrationTest`, `JwtTokenFactory`,
`LayerArchitectureTest` (las clases nuevas deben cumplir las reglas existentes
— AC-020).

### 8.2 Domain design (resumen ejecutivo)

- **Nada nuevo en el modelo de dominio existente.** SPEC-005 es un consumidor
  del modelo de SPEC-004: `Cuenta.acreditar`/`debitar` (BR-002, ambos con la
  guarda `verificarActiva()` — BR-003), `Money.sumar`/`restar`/`esMayorQue`/
  `esCero`/`ars`, `Movimiento.crear` con `TipoMovimiento.DEPOSITO`/`RETIRO` y
  `cuentaContraparteId = null`. **No se agrega ningún método nuevo a `Cuenta`,
  `Money` ni `Movimiento`** (AGENTS.md §14 — scope mínimo).
- **Eventos como records con `idMovimiento`:** `DepositoRealizado`/
  `RetiroRealizado` replican el patrón de `TransferenciaRealizada` (record en
  `domain.event` con `Money` + identificadores + `Instant`); `idMovimiento`
  juega el rol de `idMovimientoSaliente` (identifica la operación para
  conciliación futura).
- **Dos puertos de evento dedicados** (`DepositoEventPublisher`,
  `RetiroEventPublisher`), no uno genérico: cada use case depende solo del
  puerto de su operación (inyección precisa, mocks unitarios exactos) y se
  replica el patrón un-puerto-por-evento de SPEC-004
  (`TransferenciaEventPublisher`). La alternativa genérica se descarta en §13.
- **`DepositoRetiroValidator` como clase única con dos cadenas públicas** (no
  dos clases, no un flag booleano): las cadenas comparten 3 chequeos (existe,
  ACTIVA, monto) que viven como helpers privados; las diferencias (rol en
  depósito; propiedad y saldo en retiro) quedan explícitas en cada método
  público. Es la misma justificación de clase-única que `TransferValidator`
  (SPEC-004 §8.2): evitar la duplicación sin caer en CoR multi-clase
  (AGENTS.md §11). Ver §13.
- **Excepciones:** cero nuevas. Los cinco errores de negocio de la spec
  (ERR-001..ERR-005) ya tienen su excepción mapeada (§8.6).

### 8.3 Application design (flujo de cada use case)

**Sujeto autenticado en el command (no como argumento):** SPEC-005 necesita
`rol` Y `clienteIdClaim` en la capa de aplicación (el rol para el chequeo de
defensa en profundidad del depósito — §8.5; el claim para la propiedad del
retiro). Se adopta el patrón de `ObtenerMovimientosQuery(cuentaId, rol,
clienteIdClaim)` — sujeto dentro del record — en lugar del patrón de
`TransferirUseCase(command, clienteIdAutenticado)`, que no transporta el rol.
Un método `ejecutar(command, rol, clienteIdClaim)` de tres argumentos sería
menos legible (ver §13).

**Depósito (main flow; dentro de la transacción de `DepositoService`):**

```text
RealizarDepositoUseCase.ejecutar(RealizarDepositoCommand command)
  1. DatosDepositoRetiro datos = new DatosDepositoRetiro(
        command.cuentaId(), command.monto(), command.rol(), command.clienteIdClaim())
  2. DepositoRetiroValidado v = depositoRetiroValidator.validarDeposito(datos)  // 5 chequeos (§8.4)
  3. Instant fecha = Instant.now()
  4. v.cuenta().acreditar(v.monto())     // BR-002 (guarda verificarActiva: doble barrera
                                         // con el paso 3 del validador)
  5. cuentaRepository.save(v.cuenta())   // UPDATE ... WHERE id = ? AND version = N (BR-004)
  6. Movimiento movimiento = movimientoRepository.save(Movimiento.crear(
        v.cuenta().getId(), TipoMovimiento.DEPOSITO, v.monto(), fecha, null))  // FR-003;
                                                                               // contraparte null
  7. depositoEventPublisher.publicar(new DepositoRealizado(
        v.monto(), v.cuenta().getId(), fecha, movimiento.getId()))             // FR-004
  8. return new DepositoConfirmacion(movimiento.getId(), v.cuenta().getId(),
        v.monto().monto(), fecha)                                              // FR-001, A-002
```

**Retiro (main flow; dentro de la transacción de `RetiroService`):**

```text
RealizarRetiroUseCase.ejecutar(RealizarRetiroCommand command)
  1. DatosDepositoRetiro datos = new DatosDepositoRetiro(
        command.cuentaId(), command.monto(), command.rol(), command.clienteIdClaim())
  2. DepositoRetiroValidado v = depositoRetiroValidator.validarRetiro(datos)   // 6 chequeos (§8.4)
  3. Instant fecha = Instant.now()
  4. v.cuenta().debitar(v.monto())        // BR-002 (invariante saldo nunca negativo —
                                         // doble barrera con el paso 5 del validador)
  5. cuentaRepository.save(v.cuenta())    // UPDATE ... WHERE id = ? AND version = N (BR-004)
  6. Movimiento movimiento = movimientoRepository.save(Movimiento.crear(
        v.cuenta().getId(), TipoMovimiento.RETIRO, v.monto(), fecha, null))    // FR-003
  7. retiroEventPublisher.publicar(new RetiroRealizado(
        v.monto(), v.cuenta().getId(), fecha, movimiento.getId()))             // FR-004
  8. return new RetiroConfirmacion(movimiento.getId(), v.cuenta().getId(),
        v.monto().monto(), fecha)                                              // FR-002, A-002
```

- Si cualquier paso lanza excepción, la transacción hace rollback total: no
  queda saldo modificado ni movimiento (BR-004, ERR-001..ERR-006).
- `movimiento.getId()` es el id asignado por la BD en el `save` (IDENTITY): el
  mock del unit test debe devolver un `Movimiento` con id (AC-002/AC-009;
  misma nota que SPEC-004 §8.3).
- **`DepositoConfirmacion`/`RetiroConfirmacion` en `application` (no en web):**
  misma decisión que `TransferenciaConfirmacion` (SPEC-004 §8.3) — el resultado
  del use case ES el body de la respuesta 201; un DTO web duplicado sería una
  copia sin valor. El dominio sigue sin exponer DTOs.
- **`DatosDepositoRetiro` como passthrough del command:** a diferencia de
  `DatosTransferencia` (que normaliza `cbuDestino` con trim), aquí no hay
  normalización (campos tipados). El record se mantiene por consistencia con el
  patrón (el validador no consume commands y los unit tests construyen `datos`
  directamente).

### 8.4 DepositoRetiroValidator — orden de los chequeos (spec §6, ERR-001..006)

```text
DepositoRetiroValidator.validarDeposito(DatosDepositoRetiro datos) → DepositoRetiroValidado
  1. if (!"ADMIN".equals(datos.rol()))
        → AccesoDenegadoException                    // ERR-005 → 403 (A-001: CLIENTE nunca
                                                    // deposita; defensa en profundidad — §8.5)
  2. Cuenta cuenta = cuentaRepository.findById(datos.cuentaId())
        .orElseThrow(CuentaNoEncontradaException::new)  // ERR-003 → 404
  3. if (cuenta.getEstado() != ACTIVA)
        → CuentaBloqueadaException                   // ERR-004 → 422 (BR-003)
  4. if (datos.monto() == null || datos.monto().signum() <= 0
        || datos.monto().scale() > 2)
        → DatosInvalidosException("monto", "El monto debe ser mayor a 0 y tener hasta 2 decimales")
                                                    // ERR-001 → 400 (BR-001)
     Money monto = Money.ars(datos.monto());        // factory de SPEC-004; el VO (SPEC-002)
                                                    // valida monto ≥ 0, inalcanzable tras el
                                                    // chequeo anterior
  5. return new DepositoRetiroValidado(cuenta, monto)

DepositoRetiroValidator.validarRetiro(DatosDepositoRetiro datos) → DepositoRetiroValidado
  1. Cuenta cuenta = cuentaRepository.findById(datos.cuentaId())
        .orElseThrow(CuentaNoEncontradaException::new)  // ERR-003 → 404 (404-antes-403)
  2. if ("CLIENTE".equals(datos.rol())
        && (datos.clienteIdClaim() == null
            || !datos.clienteIdClaim().equals(cuenta.getClienteId())))
        → AccesoDenegadoException                    // ERR-005 → 403 (cuenta ajena, §9; réplica
                                                    // exacta del chequeo de
                                                    // ObtenerMovimientosUseCase — SPEC-004 §8.3)
  3. if (cuenta.getEstado() != ACTIVA)
        → CuentaBloqueadaException                   // ERR-004 → 422 (BR-003)
  4. if (datos.monto() == null || datos.monto().signum() <= 0
        || datos.monto().scale() > 2)
        → DatosInvalidosException("monto", ...)      // ERR-001 → 400 (BR-001)
     Money monto = Money.ars(datos.monto());
  5. if (monto.esMayorQue(cuenta.getSaldo()))
        → SaldoInsuficienteException                 // ERR-002 → 422 (BR-002)
     // (debitar re-verifica el invariante — doble barrera)
  6. return new DepositoRetiroValidado(cuenta, monto)
```

Notas:

- El orden respeta **exactamente** el main flow de la spec (§6 paso 2):
  autorización → cuenta existe (404) → ACTIVA (422) → monto (400) → saldo
  (422, solo retiro). La cadena corta ante el primer error (convención CoR).
- **Depósito — la autorización (rol) es un chequeo de claim puro** y corre
  primero: un `CLIENTE` obtiene `403` antes de cualquier consulta a BD
  (ERR-005, A-001). A nivel HTTP el matcher `hasRole("ADMIN")` ya produce ese
  `403` (access-denied handler); el chequeo en aplicación es **defensa en
  profundidad** y habilita la cobertura unitaria de ERR-005 exigida por AC-018.
- **Retiro — la autorización (propiedad) exige la cuenta cargada**, así que la
  existencia (404) se verifica antes (orden 404-antes-403, misma racional que
  `ObtenerMovimientosUseCase` — SPEC-004 §8.3; la spec no combina ambos errores
  en un solo criterio). Para `ADMIN` el paso 2 no aplica: puede retirar de
  cualquier cuenta (AC-017). Un `CLIENTE` con `clienteIdClaim` null (token
  inconsistente) recibe `403` (misma defensa que `ObtenerMovimientosUseCase`).
- El pre-chequeo de signo/escala ocurre **antes** de construir el `Money`
  (BR-001): `MoneyInvalidoException` (SPEC-002) no está mapeada en el handler
  y caería en `500`; el chequeo explícito garantiza `400 DATOS_INVALIDOS` con
  campo `monto` (mismo razonamiento que el paso 7 de `TransferValidator`,
  SPEC-004 §8.4).
- `monto` no numérico en el JSON (p. ej. `"abc"`) → `HttpMessageNotReadableException`
  → `400 DATOS_INVALIDOS` (mapeo existente, sin `details`); `monto` `0`,
  negativo o con escala > 2 → `400 DATOS_INVALIDOS` con
  `details[0].campo == "monto"` (ERR-001).

### 8.5 SecurityConfig (matchers nuevos y orden)

```text
authorizeHttpRequests:
  0.  POST  /api/v1/auth/register          → permitAll()               // sin cambios
  1.  POST  /api/v1/auth/login             → permitAll()               // sin cambios
  2.  POST  /api/v1/clientes               → hasRole("ADMIN")          // sin cambios
  3.  PUT   /api/v1/clientes/**            → hasRole("ADMIN")          // sin cambios
  4.  GET   /api/v1/clientes               → hasRole("ADMIN")          // sin cambios
  5.  GET   /api/v1/clientes/**            → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios
  6.  POST  /api/v1/cuentas                → hasRole("ADMIN")          // sin cambios
  7.  GET   /api/v1/cuentas                → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios
  8.  GET   /api/v1/cuentas/cbu/**         → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios
  9.  GET   /api/v1/cuentas/**             → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios
 10.  POST  /api/v1/transferencias         → hasRole("CLIENTE")        // sin cambios
 11.  GET   /api/v1/cuentas/*/movimientos  → hasAnyRole("ADMIN", "CLIENTE")  // sin cambios
 12.  POST  /api/v1/depositos              → hasRole("ADMIN")          // NUEVO (FR-001, §9, A-001)
 13.  POST  /api/v1/retiros                → hasAnyRole("ADMIN", "CLIENTE")  // NUEVO (FR-002, §9)
 14.  anyRequest()                         → authenticated()           // sin cambios
```

- `POST /api/v1/depositos` → solo `ADMIN` (A-001): un `CLIENTE` recibe `403
  ACCESO_DENEGADO` del access-denied handler (ERR-005, AC-006). El rol se
  re-verifica en `validarDeposito` (paso 1, §8.4) como **defensa en
  profundidad** — ambos niveles producen el mismo código y el chequeo en
  aplicación hace que ERR-005 sea testeable en unit test (AC-018).
- `POST /api/v1/retiros` → `ADMIN` (cualquier cuenta) o `CLIENTE` (solo
  propias); la propiedad se verifica en `validarRetiro` (paso 2, §8.4) →
  `403` para cuenta ajena (ERR-005, AC-014).
- Sin conflicto con los matchers existentes (segmentos distintos de
  `/api/v1/clientes`, `/api/v1/cuentas`, `/api/v1/transferencias`). El resto
  del chain (CSRF off, stateless, entry point 401, handler 403, filtro JWT) no
  cambia.

### 8.6 GlobalExceptionHandler — sin cambios (verificación)

SPEC-005 no agrega excepciones nuevas: los seis códigos de error de la spec ya
tienen mapeo. Tabla resultante (todas las filas **existentes**, ninguna nueva):

| Excepción | HTTP | `code` | `details` | Origen del mapeo |
| --- | --- | --- | --- | --- |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | `[{campo:"monto", mensaje}]` (ERR-001; reutiliza el mapeo existente para el campo `monto` de transferencias) | SPEC-001/SPEC-004 |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | null (monto no numérico) | existente |
| `AccesoDenegadoException` | 403 | `ACCESO_DENEGADO` | null (ERR-005) | existente |
| `CuentaNoEncontradaException` | 404 | `CUENTA_NO_ENCONTRADA` | null (ERR-003) | SPEC-002 |
| `ObjectOptimisticLockingFailureException` | 409 | `CONFLICTO_CONCURRENCIA` | null (ERR-006; BR-004 — mismo mecanismo que SPEC-004 ERR-005) | SPEC-004 |
| `SaldoInsuficienteException` | 422 | `SALDO_INSUFICIENTE` | null (ERR-002) | SPEC-004 |
| `CuentaBloqueadaException` | 422 | `CUENTA_BLOQUEADA` | null (ERR-004; la lanzan la guarda `verificarActiva()` y los pasos 3 de ambas cadenas) | SPEC-002 |

- Los `401`/`403` de Spring Security los siguen escribiendo el entry point y
  el access-denied handler de `SecurityConfig` (sin cambios; el `403` del
  matcher y el `403` de `AccesoDenegadoException` comparten el mismo
  `code == ACCESO_DENEGADO`).
- **No se agrega ningún `@ExceptionHandler`.** `MoneyInvalidoException`
  (SPEC-002) sigue sin mapeo (caería en `500`), pero es inalcanzable: el paso 4
  de ambas cadenas pre-chequea signo/escala antes de construir el `Money`
  (misma decisión documentada que SPEC-004 §8.6).

### 8.7 Configuración (`DepositoRetiroBeansConfig`)

```java
@Configuration
public class DepositoRetiroBeansConfig {

    @Bean
    public DepositoRetiroValidator depositoRetiroValidator(CuentaRepository cuentaRepository) {
        return new DepositoRetiroValidator(cuentaRepository);
    }

    @Bean
    public RealizarDepositoUseCase realizarDepositoUseCase(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            DepositoRetiroValidator depositoRetiroValidator,
            DepositoEventPublisher depositoEventPublisher) {
        return new RealizarDepositoUseCase(cuentaRepository, movimientoRepository,
                depositoRetiroValidator, depositoEventPublisher);
    }

    @Bean
    public RealizarRetiroUseCase realizarRetiroUseCase(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            DepositoRetiroValidator depositoRetiroValidator,
            RetiroEventPublisher retiroEventPublisher) {
        return new RealizarRetiroUseCase(cuentaRepository, movimientoRepository,
                depositoRetiroValidator, retiroEventPublisher);
    }
}
```

- Sin `@Value` ni propiedades nuevas: SPEC-005 no define límites ni topes
  (out of scope, spec §12); `application.yml`/`application-test.yml` intactos.
- `DepositoEventPublisher`/`RetiroEventPublisher` se resuelven a los únicos
  `@Component` que los implementan (`DepositoEventPublisherNoop`/
  `RetiroEventPublisherNoop`).
- `DepositoService`/`RetiroService` no necesitan declaración (component
  scanning).
- Config **nueva separada** (`DepositoRetiroBeansConfig`), no se extiende
  `TransferenciaBeansConfig`: cada spec/feature declara su propio config
  (convención `ClienteBeansConfig`/`AuthBeansConfig`/`TransferenciaBeansConfig`).

### 8.8 Frontera transaccional (BR-004) y funcionamiento del lock optimista

**Decisión: `@Transactional` en `infrastructure/service/DepositoService` y
`infrastructure/service/RetiroService`** (dos `@Service` delgados, uno por
operación, que delegan en los use cases). Es la extensión directa del patrón de
ADR-006 (decisión 2: la frontera transaccional vive en infraestructura; el
calificativo "único `@Transactional` del sistema" estaba acotado al alcance de
SPEC-004). Alternativas descartadas en §13.

- `DepositoService.ejecutar(command)` y `RetiroService.ejecutar(command)` están
  anotados `@Transactional` (propagación REQUIRED por defecto). El use case
  corre dentro de ese tx: las lecturas (validación) y las 2 escrituras
  (`save(cuenta)`, `save(movimiento)`) comparten la misma transacción de BD;
  cualquier fallo → rollback total (BR-004, ERR-001..ERR-006).
- Los repositorios Spring Data se unen al tx externo del service (REQUIRED) —
  no abren tx propios.
- **Por qué no `@Transactional` en los use cases:** la regla ArchUnit
  "application depende solo de domain" (AC-020) prohíbe importar
  `org.springframework.transaction.annotation.Transactional` en `application`
  (misma razón que SPEC-004 §8.8).
- **Cómo funciona el lock optimista (BR-004, AC-015):** el mecanismo es el de
  SPEC-004 §8.8, sin cambios:
  1. `findById` carga la cuenta (version N) y el adapter la mapea al dominio
     **incluyendo el `version`** (mapeo bidireccional ya implementado).
  2. El use case muta el saldo; `save(cuenta)` mapea de vuelta a
     `CuentaJpaEntity` **con el mismo version N** → `merge` (entidad con id).
  3. Al flush/commit, Hibernate ejecuta `UPDATE ... WHERE id = ? AND version =
     N`. Si otro tx ya commiteó version N+1 → 0 filas → `StaleObjectStateException`
     → `ObjectOptimisticLockingFailureException` → `409 CONFLICTO_CONCURRENCIA`
     (ERR-006). Sin reintento server-side (BR-004); nunca se pierde
     consistencia de saldo (el UPDATE falla entero).
  4. **No se toca el adapter:** el mapeo `version` en ambos sentidos ya existe
     (SPEC-004). El test determinista de AC-015 (Parte 2, §8.9) lo verifica.
- Los eventos se publican dentro del tx (paso 7 de cada use case): sin
  suscriptores, no requiere outbox (evolución documentada en ADR-006).

### 8.9 Test de concurrencia (AC-015) — diseño

**Objetivo:** dos retiros concurrentes sobre la misma cuenta con montos que
juntos exceden el saldo → una `201`, la otra `409 CONFLICTO_CONCURRENCIA`;
saldo final consistente y nunca negativo (BR-004, ERR-006).

**Riesgo de determinismo:** idéntico al de SPEC-004 §8.9 — con locking
optimista puro, si la 2ª request lee DESPUÉS del commit de la 1ª verá el saldo
remanente y fallará con `422 SALDO_INSUFICIENTE`; el `409` solo ocurre si ambas
leyeron version N antes de cualquier commit. Diseño en dos partes (espejo de
SPEC-004 §8.9):

**Parte 1 — concurrencia real (HTTP, threads + latch):**

1. Crear cliente + cuenta, fondear con `S = 100000` (helper `fondear` vía
   `JdbcTemplate`, patrón de `TransferenciaApiIntegrationTest`).
2. Dos threads con `CyclicBarrier` que lanzan SIMULTÁNEAMENTE dos
   `POST /api/v1/retiros` (MockMvc es thread-safe) con montos `A = 60000` y
   `B = 60000` (A ≤ S, B ≤ S, A + B > S — AC-015).
3. Assert: exactamente una respuesta `201` y la otra `409` con
   `code == CONFLICTO_CONCURRENCIA`.
4. Assert final: vía `jdbcTemplate` (o `CuentaRepository.findById`) saldo ==
   `S - 60000` (el monto ganador), nunca negativo; exactamente 1 movimiento
   `RETIRO` en la cuenta.
5. **Mitigación de flake:** si el schedule se serializa, el resultado es
   `(201, 422)`; el test reintenta con datos frescos (límite de 3 intentos,
   patrón del AC-012 de SPEC-004) — ver §12.

**Parte 2 — conflicto determinista a nivel de persistencia (sin HTTP):**

1. Crear + fondear cuenta (version 0).
2. Cargar la `Cuenta` de dominio vía `cuentaRepository.findById` (version 0 —
   la "copia vieja").
3. Simular la operación concurrente: `jdbcTemplate.update("UPDATE cuentas SET
   version = version + 1 WHERE id = ?", id)`.
4. `cuenta.debitar(Money.ars(...))` en memoria y `cuentaRepository.save(cuenta)`
   → assert que lanza `ObjectOptimisticLockingFailureException` (→ 409 por el
   handler). Falla si el mapeo bidireccional del `version` se rompiera.

**Por qué no "dos requests secuenciales con bump manual de version":** misma
justificación que SPEC-004 §8.9 — el bump previo a la request es el estado que
la request lee (no genera conflicto); el conflicto solo se produce con
concurrencia real (Parte 1) o con control de transacción explícito (Parte 2).

---

## 9. Build & Dependencies

**Ninguna dependencia nueva.**

- `spring-boot-starter-data-jpa` (ya presente): `@Transactional`, `@Version`,
  Spring Data queries.
- Postgres + Flyway + Testcontainers + ArchUnit: ya presentes (SPEC-001/003).
- No se agrega `spring-boot-starter-validation` (los DTOs no usan bean
  validation; las reglas viven en `DepositoRetiroValidator` y en los VOs).
- `pom.xml` intacto; sin propiedades nuevas en `application.yml`/
  `application-test.yml` (§8.7).

---

## 10. Testing Strategy

### Unit (JUnit 5 + Mockito; sin Spring)

| Clase | Cobertura | AC |
| --- | --- | --- |
| `application/DepositoRetiroValidatorTest` | **`validarDeposito`** (un caso por chequeo, §8.4): rol ≠ `ADMIN` → `AccesoDenegadoException` (ERR-005 lógica, A-001); cuenta inexistente → `CuentaNoEncontradaException`; BLOQUEADA → `CuentaBloqueadaException`; monto null/0/negativo/escala 3 → `DatosInvalidosException("monto")`; happy path → `DepositoRetiroValidado` con cuenta cargada y `Money` ARS. **`validarRetiro`:** cuenta inexistente → `CuentaNoEncontradaException` (404-antes-403); `CLIENTE` cuenta ajena (claim distinto o null) → `AccesoDenegadoException`; `ADMIN` sobre cualquier cuenta → pasa (sin chequeo de propiedad); BLOQUEADA → `CuentaBloqueadaException`; monto inválido → `DatosInvalidosException("monto")`; saldo insuficiente → `SaldoInsuficienteException`; happy path. Cuenta fixture con el constructor de reconstrucción (patrón de `TransferValidatorTest`) | AC-003..AC-006, AC-010..AC-014 (lógica), AC-018 |
| `application/RealizarDepositoUseCaseTest` | happy path: valida (`validarDeposito` llamado), `acreditar` llamado, `save(cuenta)` 1 vez, `save(Movimiento DEPOSITO)` con `cuentaContraparteId` null y la misma fecha, evento publicado **una vez** con (monto, cuentaId, fechaHora, idMovimiento = id del guardado) — AC-002; confirmación con `idMovimiento` = id del movimiento guardado (el mock del `save` stubbea el id, patrón de `TransferirUseCaseTest`); validador rechaza → excepción propagada sin saves ni evento | AC-001, AC-002, AC-003 (lógica) |
| `application/RealizarRetiroUseCaseTest` | ídem para retiro: `debitar` llamado, `Movimiento RETIRO`, `RetiroRealizado` publicado una vez — AC-009; saldo insuficiente → excepción propagada sin saves ni evento — AC-010; confirmación | AC-007..AC-010 (lógica) |
| `domain/MoneyTest`, `domain/CuentaTest`, `domain/MovimientoTest` | **Sin cambios**: `sumar`/`restar` y `debitar`/`acreditar` (invariante de saldo, guarda `verificarActiva()`) ya están cubiertos por SPEC-004 | AC-018 (parte ya cubierta) |

### Integración (Spring Boot Test + Testcontainers + MockMvc)

`DepositosRetirosApiIntegrationTest extends BaseIntegrationTest` con su
`@TestConfiguration TokenConfig` anidada (patrón de
`TransferenciaApiIntegrationTest`). Helpers reutilizados como métodos privados
del test (convención actual del repo): `crearClienteAdmin(...)` (POST
`/api/v1/clientes` con token ADMIN), `abrirCuentaAdmin(...)` (POST
`/api/v1/cuentas`), `fondear(cuentaId, monto)` y `saldoDe(cuentaId)` vía
`JdbcTemplate` (el fondeo directo no toca la versión — el `@Version` sigue en
0), `cantidadMovimientosDe(cuentaId)`, `bodyDeposito`/`bodyRetiro`. Un método
por criterio:

| Cobertura | AC |
| --- | --- |
| `ADMIN` deposita monto válido en cuenta ACTIVA → `201` con `idMovimiento`/`cuentaId`/`monto`/`fechaHora`; saldo incrementado exactamente por el monto (vía `saldoDe`); exactamente 1 movimiento `DEPOSITO` (vía `cantidadMovimientosDe` + GET movimientos con `tipo == "DEPOSITO"`) | AC-001 |
| Depósito con monto `0`, `-5` o `100.123` → `400 DATOS_INVALIDOS` con `details[0].campo == "monto"`; monto no numérico (`"abc"`) → `400 DATOS_INVALIDOS` | AC-003 |
| Depósito en cuenta inexistente → `404 CUENTA_NO_ENCONTRADA` | AC-004 |
| Depósito en cuenta BLOQUEADA (creada y bloqueada vía `CuentaRepository` + `bloquear()`, patrón del AC-006 de SPEC-004) → `422 CUENTA_BLOQUEADA` | AC-005 |
| `CLIENTE` deposita (incluso en su propia cuenta) → `403 ACCESO_DENEGADO` (matcher `hasRole("ADMIN")`) | AC-006 |
| `ADMIN` retira monto válido de cuenta ACTIVA con saldo suficiente → `201`; saldo decrementado exactamente por el monto; exactamente 1 movimiento `RETIRO` | AC-007 |
| `CLIENTE` retira de su propia cuenta ACTIVA → `201`; saldo decrementado; movimiento `RETIRO` | AC-008 |
| Retiro con saldo insuficiente → `422 SALDO_INSUFICIENTE`; sin cambios de saldo ni movimientos | AC-010 |
| Retiro con monto `<= 0` → `400 DATOS_INVALIDOS` con `details[0].campo == "monto"` | AC-011 |
| Retiro en cuenta inexistente → `404 CUENTA_NO_ENCONTRADA` | AC-012 |
| Retiro en cuenta BLOQUEADA → `422 CUENTA_BLOQUEADA` | AC-013 |
| `CLIENTE` retira de cuenta ajena (claim distinto) → `403 ACCESO_DENEGADO` | AC-014 |
| Concurrencia: Parte 1 (threads + `CyclicBarrier` → `201` + `409`; saldo final `S - 60000`; 1 movimiento RETIRO; reintento acotado ante schedule serializado) + Parte 2 (conflicto determinista: bump de version + `save` → `ObjectOptimisticLockingFailureException`) | AC-015 |
| Sin token → `401 NO_AUTENTICADO` (ambos endpoints) | AC-016 |
| `ADMIN` retira de una cuenta de OTRO cliente → `201` (sin chequeo de propiedad) | AC-017 |
| Envelope JSON verificado en todos los códigos (400/401/403/404/409/422) | AC-019 |

### Arquitectura (ArchUnit) — `LayerArchitectureTest`

Sin cambios de reglas (AC-020). Las clases nuevas deben cumplir:

1. `domain` (`DepositoRealizado`, `RetiroRealizado`, `DepositoEventPublisher`,
   `RetiroEventPublisher`) sin dependencias de Spring/JPA/otras capas (records
   con solo `java.time`/`java.math`/`java.util` + tipos de dominio).
2. `application` (`RealizarDepositoCommand`, `RealizarRetiroCommand`,
   `DatosDepositoRetiro`, `DepositoRetiroValidado`, `DepositoRetiroValidator`,
   `RealizarDepositoUseCase`, `RealizarRetiroUseCase`,
   `DepositoConfirmacion`, `RetiroConfirmacion`) dependiendo solo de
   `domain`/`application`/`java`. **Ojo del developer:** el `@Transactional`
   NO puede importarse en `application` (vive en `DepositoService`/
   `RetiroService`).
3. Spring/controllers solo en `infrastructure` (`DepositoService`,
   `RetiroService`, noop publishers, controllers, config).

---

## 11. ADR

**No se crean ADRs nuevos.** Los únicos patrones que SPEC-005 introduce son
extensiones directas de decisiones ya registradas, y las decisiones menores de
diseño quedan documentadas en §13:

- **ADR-006 (decisión 2 — frontera transaccional):** SPEC-005 reutiliza el
  patrón tal cual con `DepositoService`/`RetiroService`. El calificativo
  "único `@Transactional` del sistema" de ADR-006 estaba **acotado al alcance
  de SPEC-004**; ADR-006 ya anticipaba que "SPEC-005 depósitos/retiros
  reutilizará el patrón". La decisión de fondo (la frontera vive en
  infraestructura; `application` permanece Spring-free — AC-020) no cambia y
  no amerita un ADR nuevo.
- **ADR-006 (decisión 3 — concurrencia) y ADR-007 (modelo reconciliado):**
  SPEC-005 consume el mecanismo `@Version` + `409 CONFLICTO_CONCURRENCIA` sin
  reintento y el modelo `Cuenta`/`Money`/`Movimiento`/`V4__movimientos.sql`
  exactamente como quedaron tras SPEC-004 — sin cambios ni reinterpretación.
- Las decisiones propias de SPEC-005 (validador único con dos cadenas; dos
  puertos de evento dedicados; dos services transaccionales delgados; matcher
  + chequeo en aplicación) son de **diseño de detalle** — alternativas
  evaluadas en §13 — y no constituyen decisiones arquitectónicas
  significativas que requieran ADR (AGENTS.md §16).

---

## 12. Risks

- **Flake del test de concurrencia (AC-015 Parte 1):** un schedule serializado
  produce `422 SALDO_INSUFICIENTE` en vez de `409`. Mitigación: barrera
  (`CyclicBarrier`) para que ambas lecturas ocurran antes de cualquier commit,
  reintento acotado (3 intentos con datos frescos) y la Parte 2 determinista
  que valida el mecanismo `@Version` sin depender del scheduler (mismo diseño
  que SPEC-004 §8.9).
- **Doble barrera de autorización (matcher + validador):** dos puntos producen
  `403` para el depósito de un `CLIENTE` (access-denied handler y
  `AccesoDenegadoException`). Ambos emiten el mismo código
  (`ACCESO_DENEGADO`) — sin divergencia observable; se documenta para evitar
  "simplificaciones" futuras que eliminen el chequeo en aplicación (única vía
  de cobertura unitaria de ERR-005, AC-018).
- **Orden 404-antes-403 en retiro:** la autorización por propiedad exige la
  cuenta cargada; se documenta la desviación del orden literal de la spec §6
  (autorización → existe) con la misma racional que `ObtenerMovimientosUseCase`
  (SPEC-004 §8.3). Ningún criterio de aceptación combina ambos errores.
- **`idMovimiento` conocido tras el `save` (IDENTITY):** los unit tests deben
  stubear el retorno del `save` con id (AC-002/AC-009; misma nota que SPEC-004
  §12). Sin impacto en producción.
- **`MoneyInvalidoException` sin mapeo:** inalcanzable porque el paso 4 de
  ambas cadenas pre-chequea signo/escala (BR-001 → 400) antes de construir el
  `Money`; documentado en §8.6 para no agregar mapeos especulativos.
- **Testcontainers `disabledWithoutDocker = true`:** los tests de integración
  nuevos (incluida la concurrencia AC-015) se omiten localmente sin Docker; CI
  los cubre (riesgo residual ya documentado en SPEC-001 §12).
- **Usernames/DNIs/CBUs únicos por método en tests:** el contenedor
  Testcontainers es por clase; los usernames de registro se generan por método
  (convención de SPEC-003/004) y el CBU lo asigna el endpoint de apertura de
  SPEC-002 (generador con `SecureRandom`).

---

## 13. Alternatives Considered

- **Un solo validador con flag booleano (`validar(datos, esRetiro)`):**
  descartado — el parámetro de ramificación hace el orden de cada cadena menos
  legible y acopla las dos operaciones en una firma; dos métodos públicos
  (`validarDeposito`/`validarRetiro`) con helpers privados compartidos
  mantienen cada cadena explícita (AGENTS.md §11).
- **Dos validadores separados (`DepositoValidator`, `RetiroValidator`):**
  descartado — duplicarían los 3 chequeos compartidos (existe, ACTIVA, monto)
  o exigirían un helper/base compartida; la clase única es más simple (misma
  justificación que `TransferValidator` vs CoR multi-clase en SPEC-004 §13).
- **Puerto genérico de eventos (`MovimientoEventPublisher` con dos métodos o
  genérico `<E>`):** descartado — los puertos dedicados siguen el patrón
  un-puerto-por-evento de SPEC-004 (`TransferenciaEventPublisher`), mantienen
  la dependencia de cada use case precisa (`RealizarDepositoUseCase` solo
  conoce `DepositoEventPublisher`) y los unit tests mockean el puerto exacto;
  la variante genérica agrega abstracción sin beneficio (AGENTS.md §11).
- **Un solo `DepositoRetiroService` con dos métodos `@Transactional`:**
  descartado — equivalente en comportamiento, pero dos services delgados
  replican el patrón un-service-por-operación de SPEC-004
  (`TransferenciaService`, ADR-006) y mantienen cada controller con una única
  dependencia.
- **Solo matcher de seguridad, sin chequeo en aplicación:** descartado — el
  matcher ya produce el `403` HTTP correcto (AC-006), pero AC-018 exige
  cobertura unitaria de la validación (ERR-005 incluido), lo que solo es
  posible si la regla "solo ADMIN deposita" vive en la capa de aplicación;
  además da defensa en profundidad ante consumidores no HTTP del use case.
- **Sujeto autenticado como argumentos del método
  (`ejecutar(command, rol, clienteIdClaim)`):** descartado — el patrón
  sujeto-en-record de `ObtenerMovimientosQuery(cuentaId, rol, clienteIdClaim)`
  es el precedente cuando la aplicación necesita rol y claim; un método de
  tres argumentos es menos legible y rompe la simetría con el query.
- **`DatosDepositoRetiro` como passthrough (eliminarlo y validar el command
  directamente):** descartado — se mantiene la separación command→datos del
  patrón SPEC-004 (el validador no consume commands y los unit tests
  construyen datos sin el command); el costo es un record trivial.
- **Request DTO único compartido (`DepositoRetiroRequest`):** descartado — se
  mantiene la convención un-request-por-endpoint (`TransferirRequest`); dos
  records idénticos de una línea son triviales y permiten evolucionar cada
  body de forma independiente.
- **Confirmación única (`OperacionConfirmacion`):** descartado — dos records
  simétricos a los eventos/use cases (`DepositoConfirmacion`/
  `RetiroConfirmacion`) sin abstracción adicional (AGENTS.md §11).
- **`@Transactional` en use cases / adapters / controllers:** descartado por
  las mismas razones que SPEC-004 §13 (ArchUnit AC-020, atomicidad, ocultación
  de la frontera — ADR-006 alternativas A/B/C).

---

## 14. Decision

Implementar SPEC-005 con:

- **Dominio (sin cambios en lo existente):** reuso de `Cuenta` (`acreditar`/
  `debitar` con guarda `verificarActiva()`), `Movimiento` + `TipoMovimiento`
  (`DEPOSITO`/`RETIRO`), `Money`, `CuentaRepository`/`MovimientoRepository` y
  excepciones existentes. **Nuevos:** eventos `DepositoRealizado`/
  `RetiroRealizado` (`(Money monto, Long cuentaId, Instant fechaHora, Long
  idMovimiento)`) y puertos `DepositoEventPublisher`/`RetiroEventPublisher`
  (dos puertos dedicados, no genérico).
- **Aplicación (Java puro):** `DepositoRetiroValidator` (clase única con
  `validarDeposito` — 5 chequeos — y `validarRetiro` — 6 chequeos — en el
  orden de la spec §6: autorización → existe → ACTIVA → monto → saldo; devuelve
  `DepositoRetiroValidado`), `RealizarDepositoUseCase`/`RealizarRetiroUseCase`
  (orquestan el main flow y la emisión del evento; sujeto autenticado en el
  command), `DepositoConfirmacion`/`RetiroConfirmacion` como resultados del
  use case = bodies de la respuesta 201.
- **Frontera transaccional (BR-004):** `infrastructure.service.DepositoService`
  y `RetiroService` con `@Transactional` que delegan en los use cases (patrón
  ADR-006 decisión 2 extendido a dos operaciones; `application` permanece
  Spring-free — AC-020).
- **Concurrencia (BR-004):** reuso del `@Version` de `cuentas` (mapeo
  bidireccional ya existente en `CuentaRepositoryAdapter`); conflicto → `409
  CONFLICTO_CONCURRENCIA` sin reintento automático (ADR-006 decisión 3).
- **Autorización:** matcher `POST /api/v1/depositos` → `hasRole("ADMIN")` y
  `POST /api/v1/retiros` → `hasAnyRole("ADMIN", "CLIENTE")` + chequeo en
  aplicación (defensa en profundidad y testabilidad unitaria de ERR-005):
  `validarDeposito` exige rol `ADMIN` (A-001); `validarRetiro` replica el
  chequeo de propiedad de `ObtenerMovimientosUseCase` para `CLIENTE` (404-
  antes-403).
- **Infraestructura:** `DepositoController` (POST `/api/v1/depositos` → 201 +
  `DepositoConfirmacion`), `RetiroController` (POST `/api/v1/retiros` → 201 +
  `RetiroConfirmacion`), `DepositoRequest`/`RetiroRequest`, noop publishers,
  `DepositoRetiroBeansConfig`. **Sin cambios:** `GlobalExceptionHandler`
  (todos los códigos ya mapeados — §8.6), persistencia, migraciones,
  `application.yml`/`application-test.yml`, `pom.xml`.
- **Datos:** sin migración (la `V4__movimientos.sql` de SPEC-004 ya soporta
  `DEPOSITO`/`RETIRO`; `cuenta_contraparte_id` nullable queda NULL).
- **Tests:** unit (`DepositoRetiroValidatorTest`, `RealizarDepositoUseCaseTest`
  — AC-002 evento —, `RealizarRetiroUseCaseTest` — AC-009 evento —; Money/
  Cuenta ya cubiertos por SPEC-004), integración Testcontainers
  (`DepositosRetirosApiIntegrationTest` con AC-015 en dos partes: threads +
  latch y conflicto determinista), ArchUnit sin cambios de reglas (AC-020).
- **Sin ADRs nuevos** (§11): SPEC-005 reutiliza ADR-006 (decisiones 2 y 3) y
  ADR-007 sin reinterpretación.
