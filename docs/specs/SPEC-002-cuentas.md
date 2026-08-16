# SPEC-002 — Gestión de Cuentas

## Status

Approved

---

## 1. Objective

Permitir la apertura y consulta de cuentas bancarias asociadas a un cliente.
Cada cuenta tiene `CBU` único auto-generado, tipo (`CAJA_AHORRO` |
`CUENTA_CORRIENTE`), saldo inicial `0` nunca negativo, moneda y estado
(`ACTIVA` | `BLOQUEADA`). La creación usa una Factory según el tipo
(ARCHITECTURE.md §5). Se implementan consulta por `id`/`cbu` y listado por
cliente, con autorización por rol y propiedad.

---

## 2. Actors

- **ADMIN**: abre cuentas, las lista y consulta (cualquiera).
- **CLIENTE**: consulta y lista únicamente sus propias cuentas.

---

## 3. Preconditions

- El usuario debe estar autenticado con un JWT válido (SPEC-003 FR-003).
- Para apertura y listado con filtro, el token debe tener rol `ADMIN`.
- Para consulta como `CLIENTE`, el token debe permitir resolver el `Cliente`
  asociado (claim `clienteId` — SPEC-003 FR-002/FR-005; `AuthenticatedUser
  (clienteId, rol)`, `clienteId` es `null` para tokens `ADMIN`).
- El cliente titular de la cuenta debe existir (SPEC-001).

---

## 4. Functional Requirements

### FR-001 — Apertura

`POST /api/v1/cuentas` — crea una cuenta para un cliente existente con:
`clienteId`, `tipo` (`CAJA_AHORRO` | `CUENTA_CORRIENTE`) y `moneda` opcional
(por defecto `ARS` — ver A-005). Responde `201 Created` con la representación
de la cuenta: `id`, `clienteId`, `cbu`, `tipo`, `saldo` (`0`), `moneda`,
`estado` (`ACTIVA`) y `createdAt`. Solo `ADMIN`.

### FR-002 — CBU único auto-generado

El sistema genera el `CBU` automáticamente (sin intervención del usuario):
22 dígitos numéricos (banco 8 + sucursal 4 + cuenta 10, banco ficticio — ver
A-002). El `CBU` es único entre todas las cuentas.

### FR-003 — Saldo inicial y no negativo

El saldo inicial de toda cuenta nueva es `0` y el saldo nunca puede ser
negativo (invariante del agregado `Cuenta` — BR-002).

### FR-004 — Consulta por id

`GET /api/v1/cuentas/{id}` — devuelve la representación de la cuenta
(`200 OK`). `ADMIN` consulta cualquier cuenta; `CLIENTE` solo las propias.

### FR-005 — Consulta por cbu

`GET /api/v1/cuentas/cbu/{cbu}` — devuelve la representación de la cuenta
(`200 OK`). `ADMIN` consulta cualquier cuenta; `CLIENTE` solo las propias.

### FR-006 — Listado por cliente

`GET /api/v1/cuentas` — devuelve la lista de cuentas (`200 OK`) ordenada por
`id` ascendente, sin paginación en Sprint 1 (ver A-004):
- `CLIENTE`: solo sus propias cuentas (el `clienteId` se resuelve del claim;
  no se acepta parámetro — ver A-004).
- `ADMIN`: todas las cuentas, o las de un cliente específico con el parámetro
  `clienteId` (ver A-004).

### FR-007 — Estado de la cuenta

Toda cuenta se crea en estado `ACTIVA`. El modelo de dominio soporta la
transición a `BLOQUEADA` (método `bloquear()` del agregado) para sustentar
SPEC-004/SPEC-005; no se exponen endpoints REST de bloqueo/desbloqueo en este
sprint (ver A-003). Operar una cuenta `BLOQUEADA` se rechaza (BR-003).

### FR-008 — Moneda

El payload de apertura puede omitir `moneda`; el valor por defecto es `ARS`.
En este sprint solo se acepta `ARS` (ver A-005).

---

## 5. Business Rules

### BR-001

El `CBU` es único y validado por su VO: exactamente 22 dígitos numéricos
(ver A-002). La unicidad se verifica en la capa de aplicación al generar el
`CBU` (regenerando ante colisión) y se refuerza con constraint `UNIQUE (cbu)`
en la base de datos (garantía final).

### BR-002

El saldo es siempre `>= 0` (invariante del agregado `Cuenta` y del VO
`Money`). El saldo inicial es `0`.

### BR-003

Solo se puede operar sobre cuentas en estado `ACTIVA`: la guarda del agregado
lanza `CuentaBloqueadaException` ante cualquier operación sobre una cuenta
`BLOQUEADA` (ver A-001). El `GlobalExceptionHandler` la mapea a
`422 Unprocessable Entity` (ERR-008).

### BR-004

La creación de la cuenta usa una Factory según el tipo
(`CajaAhorro` / `CuentaCorriente` — ARCHITECTURE.md §5). El agregado `Cuenta`
es el único punto de mutación del saldo (ARCHITECTURE.md §4).

### BR-005

Solo se acepta la moneda `ARS` en este sprint (ver A-005). Una moneda
distinta se rechaza con `422` (ERR-009); un formato de moneda inválido se
rechaza con `400` (ERR-001).

### BR-006

Un `CLIENTE` solo puede acceder a las cuentas de su propio `Cliente`
vinculado (SPEC-003 BR-004); la verificación de propiedad se realiza en la
capa de aplicación (ARCHITECTURE.md §8).

---

## 6. Main Flow — Apertura

1. `ADMIN` envía `POST /api/v1/cuentas` con `clienteId`, `tipo` y
   opcionalmente `moneda`.
2. El sistema valida los datos: `tipo` válido y `moneda` soportada
   (BR-005; ERR-001/ERR-009).
3. El sistema verifica que el `Cliente` titular exista (SPEC-001); si no →
   `404` (ERR-002).
4. La Factory crea la `Cuenta` según el tipo (BR-004) con `CBU` único
   generado (FR-002, A-002), saldo `0` (FR-003), moneda `ARS` y estado
   `ACTIVA` (FR-007).
5. El sistema persiste la cuenta (constraint `UNIQUE (cbu)` como garantía
   final; ante colisión → `409`, ERR-010).
6. El sistema responde `201 Created` con la representación de la cuenta,
   incluido su `cbu`.

---

## 7. Alternative Flows

### AF-001 — CLIENTE lista sus cuentas

`CLIENTE` envía `GET /api/v1/cuentas` sin parámetros: el sistema resuelve el
`clienteId` desde el claim del token y devuelve solo las cuentas de ese
cliente (FR-006, BR-006). Si el token `CLIENTE` no tiene `clienteId`
vinculado, responde `403` (ERR-007).

### AF-002 — CLIENTE consulta una cuenta ajena

`CLIENTE` consulta por `id` o `cbu` una cuenta cuyo `clienteId` no coincide
con el claim de su token: responde `403 Forbidden` (BR-006, ERR-007).

### AF-003 — Colisión de CBU en la generación

La capa de aplicación verifica la unicidad del `CBU` generado antes de
persistir y regenera ante colisión (A-002). Si una carrera contra el
constraint `UNIQUE (cbu)` persiste, la violación de integridad se responde
como `409 Conflict` (ERR-010, misma semántica que SPEC-001 ERR-001).

### AF-004 — ADMIN lista con `clienteId` inexistente

`ADMIN` envía `GET /api/v1/cuentas?clienteId={id}` con un id que no existe:
responde `404 Not Found` (ERR-004).

---

## 8. Error Cases

Todos los errores usan el envelope JSON estándar
(`{ code, message, details? }` — `ARCHITECTURE.md` §7).

### ERR-001 — Datos inválidos en apertura

`tipo` distinto de `CAJA_AHORRO`/`CUENTA_CORRIENTE`, `moneda` con formato
inválido, JSON malformado o tipos de dato incorrectos en `POST
/api/v1/cuentas`. También aplica a parámetros de ruta malformados
(p. ej., `id` no numérico en `GET /api/v1/cuentas/{id}`).

Se responde `400 Bad Request` con el detalle del/los campo(s).

### ERR-002 — Cliente titular inexistente

`clienteId` en `POST /api/v1/cuentas` referencia un `Cliente` que no existe
(SPEC-001).

Se responde `404 Not Found` (`CLIENTE_NO_ENCONTRADO`).

### ERR-003 — Cuenta inexistente

`GET /api/v1/cuentas/{id}` o `GET /api/v1/cuentas/cbu/{cbu}` con una cuenta
que no existe.

Se responde `404 Not Found` (`CUENTA_NO_ENCONTRADA`).

### ERR-004 — Cliente inexistente en el listado

`GET /api/v1/cuentas?clienteId={id}` (ADMIN) con un `clienteId` que no
existe.

Se responde `404 Not Found`.

### ERR-005 — CBU malformado en la consulta

`GET /api/v1/cuentas/cbu/{cbu}` con un `cbu` que no cumple el formato del VO
(22 dígitos numéricos — BR-001).

Se responde `400 Bad Request` (`CBU_INVALIDO`).

### ERR-006 — Token ausente o inválido

Request a cualquier endpoint de cuentas sin `Authorization: Bearer <JWT>` o
con token malformado/expirado (SPEC-003 ERR-002).

Se responde `401 Unauthorized`.

### ERR-007 — Acceso no autorizado (rol o propiedad)

`CLIENTE` intenta abrir una cuenta (solo `ADMIN`), consultar una cuenta ajena
(AF-002), usar el parámetro `clienteId` en el listado (A-004), o un token
`CLIENTE` sin `clienteId` vinculado intenta consultar/listar (AF-001).
También cualquier usuario sin rol `ADMIN` en los endpoints exclusivos de
`ADMIN`.

Se responde `403 Forbidden` (`ACCESO_DENEGADO`).

### ERR-008 — Operar una cuenta BLOQUEADA

Cualquier operación (método de negocio del agregado) sobre una cuenta en
estado `BLOQUEADA` lanza `CuentaBloqueadaException` (BR-003, A-001).

Se responde `422 Unprocessable Entity` (`CUENTA_BLOQUEADA`). Se verifica en
este sprint con unit test de dominio + test de mapeo del
`GlobalExceptionHandler` (AC-027); los endpoints que mueven dinero llegan en
SPEC-004/SPEC-005.

### ERR-009 — Moneda no soportada

`moneda` presente y distinta de `ARS` en `POST /api/v1/cuentas` (BR-005,
A-005).

Se responde `422 Unprocessable Entity` (`MONEDA_NO_SOPORTADA`).

### ERR-010 — Conflicto de unicidad de CBU

Carrera de concurrencia: dos generaciones de `CBU` idénticos persisten casi
simultáneamente y el constraint `UNIQUE (cbu)` de la base de datos rechaza el
segundo insert (A-002).

Se responde `409 Conflict` (`CONFLICTO_UNICIDAD`; misma semántica que
SPEC-001 ERR-001 y el backstop de `DataIntegrityViolationException`).

---

## 9. Authorization

- Apertura (`POST /api/v1/cuentas`): solo rol `ADMIN`.
- Consulta por `id` (`GET /api/v1/cuentas/{id}`): `ADMIN` (cualquier cuenta)
  o `CLIENTE` (solo las del `clienteId` de su token — BR-006).
- Consulta por `cbu` (`GET /api/v1/cuentas/cbu/{cbu}`): `ADMIN` (cualquier
  cuenta) o `CLIENTE` (solo propias — BR-006).
- Listado (`GET /api/v1/cuentas`): `ADMIN` (todas o filtrado por `clienteId`)
  o `CLIENTE` (solo propias, sin parámetro — ver A-004).
- Toda request requiere JWT válido; la autorización se verifica server-side
  (ARCHITECTURE.md §8). Un token `CLIENTE` sin `clienteId` vinculado no puede
  acceder a cuentas (`403` — AF-001; misma semántica que SPEC-003 AF-002).

---

## 10. Data Changes

- **Value Objects nuevos** (`domain.vo`): `CBU` (22 dígitos numéricos —
  BR-001), `Money` (`BigDecimal` + moneda; inmutable, saldo `>= 0` — BR-002)
  y `Moneda` (solo `ARS` en este sprint — BR-005, A-005).
- **Agregado nuevo** (`domain.model`): `Cuenta` (id, clienteId, cbu, tipo,
  saldo, moneda, estado, createdAt, `@Version`). Incluye la transición
  `bloquear()` (ACTIVA → BLOQUEADA) y la guarda de BR-003 (A-001).
- **Excepciones de dominio nuevas** (`domain.exception`):
  `CuentaNoEncontradaException` (404), `CuentaBloqueadaException` (422) y
  `CbuInvalidoException` (400).
- **Puerto nuevo** (`domain.port`): `CuentaRepository`, con adaptador JPA en
  `infrastructure.adapter.persistence`.
- **Migración Flyway** (`V3__cuentas.sql`): tabla `cuentas` con:
  - `id` `BIGSERIAL` `PRIMARY KEY`,
  - `cliente_id` `BIGINT NOT NULL` con `FOREIGN KEY` → `clientes(id)`,
  - `cbu` `VARCHAR(22) NOT NULL` con `UNIQUE (cbu)` (BR-001),
  - `tipo` `VARCHAR NOT NULL` (`CAJA_AHORRO` | `CUENTA_CORRIENTE`),
  - `saldo` `NUMERIC(19,2) NOT NULL DEFAULT 0` (FR-003),
  - `moneda` `VARCHAR(3) NOT NULL DEFAULT 'ARS'` (A-005),
  - `estado` `VARCHAR NOT NULL DEFAULT 'ACTIVA'` (FR-007),
  - `version` `BIGINT NOT NULL DEFAULT 0` (optimistic locking,
    ARCHITECTURE.md §5),
  - `created_at` `TIMESTAMP NOT NULL`.

---

## 11. Acceptance Criteria

Apertura (`POST /api/v1/cuentas`):

- [ ] AC-001: Con token `ADMIN`, `clienteId` de un `Cliente` existente,
      `tipo` `CAJA_AHORRO` y sin `moneda`, responde `201 Created` y devuelve
      la cuenta con `cbu` de 22 dígitos, `saldo` `0`, `estado` `ACTIVA` y
      `moneda` `ARS` (FR-001, FR-002, FR-003, FR-007, FR-008).
- [ ] AC-002: Apertura con `tipo` `CUENTA_CORRIENTE` y `moneda` `ARS`
      explícita responde `201 Created` (FR-001, A-005).
- [ ] AC-003: Apertura con `moneda` distinta de `ARS` (p. ej. `USD`)
      responde `422` (BR-005, ERR-009, A-005).
- [ ] AC-004: Apertura con `tipo` inválido responde `400` (ERR-001).
- [ ] AC-005: Apertura con `clienteId` inexistente responde `404` (ERR-002).
- [ ] AC-006: Apertura sin token responde `401` (ERR-006).
- [ ] AC-007: Apertura con token `CLIENTE` responde `403` (solo `ADMIN`
      abre — ERR-007).

Consulta por id (`GET /api/v1/cuentas/{id}`):

- [ ] AC-008: `ADMIN` consulta cualquier cuenta y recibe `200` con todos los
      datos (FR-004).
- [ ] AC-009: `CLIENTE` consulta una cuenta propia y recibe `200` (FR-004,
      BR-006).
- [ ] AC-010: `CLIENTE` consulta una cuenta ajena y recibe `403` (AF-002,
      ERR-007).
- [ ] AC-011: Consultar un `id` inexistente responde `404` (ERR-003).
- [ ] AC-012: Consultar con `id` no numérico responde `400` (ERR-001).

Consulta por cbu (`GET /api/v1/cuentas/cbu/{cbu}`):

- [ ] AC-013: `ADMIN` consulta por `cbu` existente y recibe `200` (FR-005).
- [ ] AC-014: `CLIENTE` consulta el `cbu` de una cuenta propia y recibe `200`
      (FR-005, BR-006).
- [ ] AC-015: `CLIENTE` consulta el `cbu` de una cuenta ajena y recibe `403`
      (AF-002, ERR-007).
- [ ] AC-016: Consultar un `cbu` inexistente responde `404` (ERR-003).
- [ ] AC-017: Consultar con `cbu` malformado (no 22 dígitos) responde `400`
      (BR-001, ERR-005).

Listado (`GET /api/v1/cuentas`):

- [ ] AC-018: Con token `CLIENTE` responde `200` con solo las cuentas del
      `clienteId` de su claim (FR-006, AF-001).
- [ ] AC-019: `CLIENTE` que envía el parámetro `clienteId` responde `403`
      (A-004, ERR-007).
- [ ] AC-020: Con token `ADMIN` y `clienteId` válido responde `200` con solo
      las cuentas de ese cliente (FR-006).
- [ ] AC-021: Con token `ADMIN` y `clienteId` inexistente responde `404`
      (ERR-004, AF-004).
- [ ] AC-022: Con token `ADMIN` y sin parámetro responde `200` con todas las
      cuentas ordenadas por `id` ascendente (FR-006, A-004).
- [ ] AC-023: Token `CLIENTE` sin `clienteId` vinculado en el claim responde
      `403` (AF-001, ERR-007).

Dominio, persistencia y arquitectura:

- [ ] AC-024: Unit test — la Factory crea `CAJA_AHORRO` y
      `CUENTA_CORRIENTE` con saldo `0` y estado `ACTIVA` (BR-004, FR-003,
      FR-007).
- [ ] AC-025: Unit test — el VO `CBU` valida exactamente 22 dígitos; un
      `CBU` inválido lanza `CbuInvalidoException` (BR-001).
- [ ] AC-026: Unit test — el VO `Money` rechaza montos negativos en su
      construcción (BR-002).
- [ ] AC-027: Unit test de dominio — `bloquear()` transiciona
      `ACTIVA` → `BLOQUEADA`, y cualquier operación sobre una cuenta
      `BLOQUEADA` (incluido volver a `bloquear()`) lanza
      `CuentaBloqueadaException`; test de mapeo: el `GlobalExceptionHandler`
      la traduce a `422` (BR-003, ERR-008, A-001).
- [ ] AC-028: Unit test — la generación de `CBU` en la capa de aplicación
      verifica la unicidad antes de persistir y regenera ante colisión
      (BR-001, A-002).
- [ ] AC-029: Tests de integración con Testcontainers cubren la persistencia
      y los endpoints REST, incluidos los códigos `400/401/403/404/409/422`
      (ERR-001..ERR-010), y verifican que la tabla `cuentas` respeta
      `UNIQUE (cbu)` y la FK a `clientes` (BR-001).
- [ ] AC-030: ArchUnit (`mvn verify`) verifica las reglas de dependencia de
      capas con las clases nuevas: `domain` sin dependencias de Spring,
      `application` dependiendo solo de `domain`, y Spring/controllers solo
      en `infrastructure`.

---

## 12. Out of Scope

- Depósitos, retiros y transferencias (SPEC-004 y SPEC-005), incluidos
  endpoints que mueven dinero y la entidad `Movimiento`.
- Endpoints REST de bloqueo/desbloqueo de cuentas: el modelo de dominio
  soporta `bloquear()` (A-003), pero no se expone ningún endpoint en este
  sprint.
- Moneda distinta de `ARS` (BR-005, A-005).
- Comisiones por tipo de cuenta (evolución; Strategy preparado en dominio).
- Paginación, filtros adicionales y ordenamiento configurable en el listado
  (solo filtro `clienteId` para `ADMIN` — A-004).
- Cierre, eliminación (física o lógica) o transferencia de titularidad de
  cuentas.
- Frontend (E6).

---

## 13. Dependencies

- SPEC-001 (clientes): el titular debe existir (ERR-002); reutiliza
  `ClienteRepository`, `ClienteNoEncontradoException` y la semántica de
  `409` (ERR-010).
- SPEC-003 (autenticación): JWT con claims `role` y `clienteId` (solo
  `CLIENTE`); `AuthenticatedUser(clienteId, rol)` con `clienteId` `null`
  para `ADMIN`.
- ADR-003 (JWT + Spring Security + RBAC).
- `ARCHITECTURE.md` §4 (agregado `Cuenta`, VOs `Money`/`CBU`), §5 (Factory,
  `@Version`), §7 (API/errores), §8 (seguridad).

---

## 14. Open Questions

- Ninguna (las ambigüedades detectadas se resolvieron como asunciones — §15).

---

## 15. Assumptions

Las siguientes decisiones no estaban definidas explícitamente en la
documentación existente; se documentan como asunciones con la interpretación
recomendada. Si el Product Owner dispone otra cosa, deben ajustarse antes de
la implementación.

- **A-001 — Verificación de "operar una cuenta BLOQUEADA → 422" sin
  endpoint de dinero:** SPEC-002 no expone endpoints que muevan dinero
  (llegan en SPEC-004/SPEC-005). Para que la regla BR-003 sea verificable en
  este sprint, el agregado `Cuenta` expone una transición `bloquear()`
  (`ACTIVA` → `BLOQUEADA`) y una guarda interna que lanza
  `CuentaBloqueadaException` ante cualquier operación sobre una cuenta
  `BLOQUEADA` (incluido volver a `bloquear()`); el `GlobalExceptionHandler`
  incorpora el mapeo a `422` (hoy no existe ese mapeo). El AC se verifica con
  unit test de dominio + test de mapeo (AC-027); los endpoints reales de
  dinero validarán la regla de extremo a extremo en SPEC-004/SPEC-005.
- **A-002 — Formato y unicidad del CBU:** el `CBU` es un número de 22 dígitos
  del banco ficticio (8 banco + 4 sucursal + 10 cuenta). Se genera en la capa
  de aplicación; antes de persistir se verifica que no exista en el
  repositorio y se regenera ante colisión (AC-028). El constraint
  `UNIQUE (cbu)` de la base de datos es la garantía final; una carrera que
  golpee el constraint se responde `409` con la misma semántica que
  SPEC-001 (ERR-010).
- **A-003 — Transiciones de estado:** la apertura siempre crea la cuenta en
  estado `ACTIVA`. El modelo de dominio incluye la transición a `BLOQUEADA`
  (`bloquear()`) para sustentar las reglas de SPEC-004/SPEC-005, pero NO se
  exponen endpoints REST de bloqueo/desbloqueo en este sprint (ver §12).
- **A-004 — Contrato del listado:** `GET /api/v1/cuentas` — el `CLIENTE`
  lista sus propias cuentas usando el `clienteId` del claim y no envía
  parámetros; si envía `clienteId`, se responde `403` (ERR-007). El `ADMIN`
  puede filtrar con el parámetro `clienteId` (opcional); si lo omite, se
  devuelven todas las cuentas ordenadas por `id` ascendente (misma semántica
  que SPEC-001 FR-004). Un `clienteId` inexistente responde `404` (ERR-004).
- **A-005 — Moneda:** la request puede omitir `moneda`; el valor por defecto
  es `ARS`. Solo se acepta `ARS` en este sprint; una moneda distinta (p. ej.
  `USD`) se rechaza con `422 MONEDA_NO_SOPORTADA` (regla de negocio: el banco
  opera solo `ARS` en el MVP, consistente con SPEC-005 §12); un formato de
  moneda inválido se rechaza con `400` (ERR-001).

---

## 16. Related Documents

- `ARCHITECTURE.md` §4 (agregado `Cuenta`, VOs `Money`/`CBU`/`Moneda`), §5
  (Factory, `@Version`), §7 (API REST, envelope de errores), §8 (seguridad)
- `docs/domain/glosario.md` (Cuenta, CBU, saldo, tipo y estado de cuenta)
- `docs/adr/ADR-003-jwt-spring-security.md`
- `docs/specs/SPEC-001-clientes.md` (§9, A-001; semántica de errores)
- `docs/specs/SPEC-003-autenticacion.md` (claims `role`/`clienteId`)
- `docs/specs/SPEC-004-transferencias.md`, `docs/specs/SPEC-005-depositos-retiros.md`
  (consumen el estado `ACTIVA`/`BLOQUEADA` y la invariante de saldo)
- `docs/sprints/backlog.md` (E2), `docs/sprints/roadmap.md` (Sprint 1)
