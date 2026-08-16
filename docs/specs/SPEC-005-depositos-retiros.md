# SPEC-005 — Depósitos y Retiros

## Status

Approved

---

## 1. Objective

Permitir depósitos (ingreso de dinero a una cuenta) y retiros (egreso de
dinero de una cuenta) con validación de saldo y reglas de negocio.

---

## 2. Actors

- **ADMIN**: ejecuta depósitos y retiros (caja). Puede operar sobre cualquier
  cuenta del sistema.
- **CLIENTE**: puede retirar de sus propias cuentas (según alcance del MVP).
  **No puede depositar en ninguna cuenta, ni siquiera en las propias**
  (A-001).

---

## 3. Preconditions

- Usuario autenticado con JWT válido (SPEC-003 FR-003).
- La cuenta objetivo debe existir y estar `ACTIVA`.
- El token debe tener el rol requerido según la operación (§9): depósito →
  `ADMIN`; retiro → `ADMIN` o `CLIENTE` (cuenta propia).
- El monto es mayor a 0, con hasta 2 decimales, expresado en `ARS` (moneda
  del MVP — misma semántica que SPEC-004 BR-003).

---

## 4. Functional Requirements

### FR-001

Depositar un monto en una cuenta (suma al saldo). Operación exclusiva de
`ADMIN` (§9, A-001).

### FR-002

Retirar un monto de una cuenta (resta del saldo). `ADMIN` (cualquier cuenta)
o `CLIENTE` (solo propias — §9).

### FR-003

Registrar un `Movimiento` de tipo `DEPOSITO` o `RETIRO` (se reutiliza la
entidad `Movimiento` y la tabla `movimientos` incorporadas por SPEC-004 —
V4).

### FR-004

Emitir `DepositoRealizado` o `RetiroRealizado` al completar la operación.
En este sprint el evento no tiene suscriptores (auditoría/notificación quedan
fuera de alcance); su emisión se verifica en tests (AC-002, AC-009).

---

## 5. Business Rules

### BR-001

El monto debe ser mayor a 0, con hasta 2 decimales, expresado en `ARS`
(misma semántica que SPEC-004 BR-003). Monto `<= 0`, no numérico o con más
de 2 decimales se rechaza como dato inválido.

### BR-002

El saldo no puede quedar negativo tras un retiro. Es un invariante del
agregado `Cuenta`: `debitar(monto)` lanza `SaldoInsuficienteException` si
`monto > saldo`, y `acreditar(monto)` incrementa el saldo (reuso de los
métodos de dominio de SPEC-004).

### BR-003

La cuenta debe estar `ACTIVA`. Una cuenta `BLOQUEADA` no admite depósitos ni
retiros.

### BR-004

Concurrencia: `@Version` evita saldos inconsistentes. Ante un conflicto de
versión (`OptimisticLockException`) la operación se rechaza con `409` y el
cliente puede reintentar; no hay reintento automático server-side (misma
semántica que SPEC-004 BR-006 / A-004).

---

## 6. Main Flow

1. `ADMIN` (o `CLIENTE` en retiro propio) envía `cuentaId` y `monto`.
2. Se valida la autorización (§9): depósito → solo `ADMIN`; retiro → `ADMIN`
   o `CLIENTE` con cuenta propia (`403` si no — ERR-005).
3. Se valida la cadena (Chain of Responsibility, cortando ante el primer
   error):
   1. La cuenta existe → `404` si no (ERR-003).
   2. La cuenta está `ACTIVA` → `422` si está `BLOQUEADA` (ERR-004).
   3. `monto > 0` con hasta 2 decimales → `400` si es inválido (ERR-001).
   4. Saldo suficiente → `422` si no (solo retiro — ERR-002).
4. En una única transacción se actualiza el saldo (`acreditar`/`debitar` —
   BR-002) y se persiste el `Movimiento` `DEPOSITO`/`RETIRO` (FR-003).
5. Se emite `DepositoRealizado`/`RetiroRealizado` (FR-004).
6. Se responde `201 Created` con la confirmación (A-002).

---

## 7. Alternative Flows

### AF-001

Depósito en cuenta ajena: permitido solo para `ADMIN`. Un `CLIENTE` nunca
deposita, ni siquiera en su propia cuenta (A-001, ERR-005).

---

## 8. Error Cases

Todos los errores usan el envelope JSON estándar
(`{ code, message, details? }` — `ARCHITECTURE.md` §7) con códigos HTTP
consistentes con SPEC-004.

### ERR-001 — Monto inválido

Monto `<= 0`, no numérico o con más de 2 decimales (BR-001).

Se responde `400 Bad Request` con código `DATOS_INVALIDOS` y `details`
indicando el campo `monto` (misma semántica que SPEC-004 ERR-004).

### ERR-002 — Saldo insuficiente

Saldo de la cuenta menor al monto solicitado en un retiro (BR-002).

Se responde `422 Unprocessable Entity` con código `SALDO_INSUFICIENTE`. No se
altera el saldo ni se registra movimiento.

### ERR-003 — Cuenta inexistente

El `cuentaId` no existe.

Se responde `404 Not Found` con código `CUENTA_NO_ENCONTRADA`.

### ERR-004 — Cuenta BLOQUEADA

La cuenta está en estado `BLOQUEADA` (BR-003).

Se responde `422 Unprocessable Entity` con código `CUENTA_BLOQUEADA`.

### ERR-005 — Sin permiso / cuenta ajena

`CLIENTE` intenta depositar (en cualquier cuenta, incluso la propia — A-001)
o retirar de una cuenta ajena.

Se responde `403 Forbidden` con código `ACCESO_DENEGADO` (misma semántica que
SPEC-004 ERR-006).

### ERR-006 — Conflicto de concurrencia

`@Version` detecta que la cuenta fue modificada por otra operación mientras
se ejecutaba el depósito/retiro (BR-004).

Se responde `409 Conflict` con código `CONFLICTO_CONCURRENCIA`; el cliente
puede reintentar (misma semántica que SPEC-004 ERR-005).

---

## 9. Authorization

- Depósito: **solo `ADMIN`**, sobre cualquier cuenta (propia o ajena). Un
  `CLIENTE` **nunca** deposita, ni siquiera en sus propias cuentas (A-001);
  un intento responde `403` (ERR-005).
- Retiro: `ADMIN` (cualquier cuenta) o `CLIENTE` (solo propias; una cuenta
  ajena responde `403` — ERR-005).
- Toda request requiere JWT válido (SPEC-003 FR-003); sin token responde
  `401`. La autorización se verifica server-side (nunca solo en el frontend).

---

## 10. Data Changes

- Actualización de saldo de `Cuenta` vía `acreditar`/`debitar` (BR-002,
  métodos de dominio incorporados por SPEC-004).
- `Movimiento` de tipo `DEPOSITO`/`RETIRO`: la entidad y la tabla
  `movimientos` **ya existen** (SPEC-004, migración `V4__movimientos.sql`,
  cuyo `tipo VARCHAR(22)` soporta `DEPOSITO` y `RETIRO`); SPEC-005 las
  reutiliza sin cambios de esquema.
- Eventos de dominio `DepositoRealizado` y `RetiroRealizado` (FR-004).

---

## 11. Acceptance Criteria

Depósito:

- [ ] AC-001: `ADMIN` autenticado deposita un monto válido en una cuenta
      `ACTIVA` → `201`; el saldo se incrementa exactamente por el monto y se
      registra un `Movimiento` `DEPOSITO` (FR-001, FR-003, BR-001, BR-003).
- [ ] AC-002: Unit test — el use case de depósito emite `DepositoRealizado`
      al completar la operación (FR-004).
- [ ] AC-003: Depósito con monto `<= 0`, no numérico o con más de 2 decimales
      → `400 DATOS_INVALIDOS` con `details` del campo `monto` (BR-001,
      ERR-001).
- [ ] AC-004: Depósito en una cuenta inexistente → `404 CUENTA_NO_ENCONTRADA`
      (ERR-003).
- [ ] AC-005: Depósito en una cuenta `BLOQUEADA` → `422 CUENTA_BLOQUEADA`
      (BR-003, ERR-004).
- [ ] AC-006: `CLIENTE` intenta depositar (incluso en su propia cuenta) →
      `403 ACCESO_DENEGADO` (A-001, §9, ERR-005).

Retiro:

- [ ] AC-007: `ADMIN` retira un monto válido de una cuenta `ACTIVA` con saldo
      suficiente → `201`; el saldo se decrementa exactamente por el monto y
      se registra un `Movimiento` `RETIRO` (FR-002, FR-003, BR-001..BR-003).
- [ ] AC-008: `CLIENTE` retira de su propia cuenta `ACTIVA` con saldo
      suficiente → `201`; saldo decrementado y `Movimiento` `RETIRO`
      registrado (FR-002, §9).
- [ ] AC-009: Unit test — el use case de retiro emite `RetiroRealizado` al
      completar la operación (FR-004).
- [ ] AC-010: Retiro con saldo insuficiente → `422 SALDO_INSUFICIENTE`; no se
      altera el saldo ni se registran movimientos (BR-002, ERR-002).
- [ ] AC-011: Retiro con monto `<= 0` → `400 DATOS_INVALIDOS` (BR-001,
      ERR-001).
- [ ] AC-012: Retiro en una cuenta inexistente → `404 CUENTA_NO_ENCONTRADA`
      (ERR-003).
- [ ] AC-013: Retiro en una cuenta `BLOQUEADA` → `422 CUENTA_BLOQUEADA`
      (BR-003, ERR-004).
- [ ] AC-014: `CLIENTE` retira de una cuenta ajena → `403 ACCESO_DENEGADO`
      (ERR-005, §9).

Concurrencia:

- [ ] AC-015: Concurrencia — dos retiros concurrentes sobre la misma cuenta
      cuyos montos juntos exceden el saldo: uno responde `201` y el otro
      `409 CONFLICTO_CONCURRENCIA`; el saldo final es consistente y nunca
      negativo (BR-004, ERR-006).

Autorización general:

- [ ] AC-016: Sin token responde `401` (SPEC-003 FR-003).
- [ ] AC-017: `ADMIN` puede retirar de cualquier cuenta del sistema (no solo
      propias) → `201` (FR-002, §9).

Pruebas y arquitectura:

- [ ] AC-018: Tests unitarios cubren el VO `Money` (sumar/restar), el
      agregado `Cuenta` (`debitar`/`acreditar`, invariante de saldo) y la
      validación del depósito/retiro (BR-001..BR-004, ERR-001..ERR-006).
- [ ] AC-019: Tests de integración con Testcontainers cubren los endpoints de
      depósito y retiro y los códigos `400`/`401`/`403`/`404`/`409`/`422`,
      incluida la concurrencia (AC-015) (FR-001..FR-004).
- [ ] AC-020: ArchUnit (`mvn verify`) sigue pasando con las nuevas clases:
      `domain` sin dependencias de Spring, `application` dependiendo solo de
      `domain`, y Spring/controllers solo en `infrastructure` (AGENTS.md §11).

---

## 12. Out of Scope

- Operaciones en moneda extranjera (solo `ARS` en el MVP).
- Límites de extracción por cajero.
- Suscriptores de los eventos `DepositoRealizado`/`RetiroRealizado`
  (auditoría/notificación; evolución).
- Frontend (E6).

---

## 13. Dependencies

- **SPEC-004 (transferencias)**: incorpora el agregado `Cuenta` mínimo
  (saldo, `@Version`, `debitar`/`acreditar`) y la entidad `Movimiento` con su
  migración `V4__movimientos.sql`; SPEC-005 los reutiliza.
- **SPEC-002 (cuentas)**: modelo de referencia de `Cuenta` (ver A-001 de
  SPEC-004).
- **SPEC-003 (autenticación)**: JWT con claims `role` y `clienteId`
  (prerrequisito de §9).

---

## 14. Open Questions

- Ninguna. La ambigüedad sobre quién puede depositar (¿`CLIENTE` en cuenta
  propia?) se resolvió en modo autónomo como asunción documentada A-001,
  consistente con el issue y con §9 de la draft. El código de éxito de los
  endpoints se documenta en A-002.

---

## 15. Decisiones y Asunciones

- **A-001 — Depósito exclusivo de `ADMIN` (`CLIENTE` nunca deposita):** el
  issue establece "deposito ADMIN suma saldo" y "retiro ADMIN o CLIENTE
  sobre sus cuentas", y la draft §9 establece "Depósito: solo `ADMIN`". La
  draft no explicitaba si un `CLIENTE` podría depositar en su propia cuenta;
  se interpreta que **no**: `CLIENTE` nunca deposita, ni siquiera en sus
  propias cuentas (intento → `403 ACCESO_DENEGADO`, ERR-005). Consistente
  con US-5.1/US-5.2 del backlog (depósito = caja ADMIN; retiro = ADMIN o
  CLIENTE propio).
- **A-002 — Respuesta de éxito `201 Created`:** la draft no definía el código
  de éxito. Se adopta `201 Created` con confirmación, misma convención que
  SPEC-004 FR-001 (el `POST` crea un `Movimiento`). Las rutas exactas de los
  endpoints las define el arquitecto en `docs/architecture/SPEC-005.md`.

---

## 16. Related Documents

- `ARCHITECTURE.md` §4 (agregado `Cuenta`, `Movimiento`), §5 (patrones), §6
  (transferencias/operaciones), §7 (API REST y envelope de errores), §8
  (seguridad), §9 (persistencia)
- `docs/domain/glosario.md` (Depósito, Retiro, Movimiento, Saldo)
- `docs/specs/SPEC-004-transferencias.md` (agregado `Cuenta`,
  `debitar`/`acreditar`, `Movimiento`, convenciones de errores)
- `docs/sprints/backlog.md` (E5: US-5.1, US-5.2), `docs/sprints/roadmap.md`
  (Sprint 3)
