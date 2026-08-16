# SPEC-004 — Transferencias entre Cuentas

## Status

Draft

---

## 1. Objective

Permitir a un cliente transferir dinero entre cuentas (propias o de terceros)
de forma atómica, debitando el origen y acreditando el destino en una única
transacción.

---

## 2. Actors

- **CLIENTE**: inicia transferencias desde sus cuentas.

---

## 3. Preconditions

- El usuario debe estar autenticado como `CLIENTE`.
- La cuenta origen debe pertenecer al cliente autenticado.

---

## 4. Functional Requirements

### FR-001

Transferir un monto de una cuenta origen a una cuenta destino (por `CBU`).

### FR-002

Debitar origen y acreditar destino en la **misma transacción** (ACID).

### FR-003

Registrar dos `Movimiento`: `TRANSFERENCIA_SALIENTE` y
`TRANSFERENCIA_ENTRANTE`.

### FR-004

Emitir el evento de dominio `TransferenciaRealizada`.

### FR-005

Consultar el historial de movimientos de una cuenta.

---

## 5. Business Rules

### BR-001

El saldo de la cuenta origen debe ser suficiente (nunca negativo).

### BR-002

Ambas cuentas deben estar `ACTIVA`.

### BR-003

El monto debe ser mayor a 0.

### BR-004

Existe un límite diario de transferencias por cliente (por defecto
`ARS 200.000`; configurable).

### BR-005

No se puede transferir de una cuenta a sí misma.

### BR-006

Concurrencia: `@Version` en `Cuenta` evita saldos inconsistentes
(optimistic lock); ante conflicto se rechaza o reintenta.

---

## 6. Main Flow

1. `CLIENTE` envía `origen`, `destino` (CBU) y `monto`.
2. Se valida (Chain of Responsibility): origen activa → destino válido y
   distinto → monto > 0 → saldo suficiente → límite diario.
3. En una transacción se debita origen y acredita destino.
4. Se persisten dos movimientos y se emite `TransferenciaRealizada`.
5. Se devuelve confirmación con el id de la transferencia.

---

## 7. Alternative Flows

### AF-001

Destino inexistente: se aborta la operación sin débito (se responde `404`).

### AF-002

Límite diario superado: se rechaza la transferencia (se responde `422`).

---

## 8. Error Cases

### ERR-001

Saldo insuficiente.

Se responde `422 Unprocessable Entity` (`SALDO_INSUFICIENTE`).

### ERR-002

Cuenta origen/destino inexistente.

Se responde `404 Not Found`.

### ERR-003

Cuenta `BLOQUEADA`.

Se responde `422 Unprocessable Entity`.

### ERR-004

Monto <= 0 o inválido.

Se responde `400 Bad Request`.

### ERR-005

Conflicto de concurrencia (versión).

Se responde `409 Conflict`; el cliente puede reintentar.

### ERR-006

`CLIENTE` intenta operar una cuenta ajena.

Se responde `403 Forbidden`.

---

## 9. Authorization

- Solo `CLIENTE`, y únicamente sobre sus propias cuentas de origen.

---

## 10. Data Changes

- Entidad `Movimiento` (id, cuentaId, tipo, monto, fecha, cuentaContraparte).
- `@Version` en `Cuenta`.
- Migración Flyway correspondiente.

---

## 11. Acceptance Criteria

- [ ] Una transferencia válida debita origen y acredita destino.
- [ ] El saldo nunca queda negativo tras una transferencia.
- [ ] Transferir con saldo insuficiente devuelve `422` y no altera saldos.
- [ ] Transferir a sí mismo se rechaza.
- [ ] Superar el límite diario se rechaza.
- [ ] Se registran exactamente dos movimientos por transferencia.

---

## 12. Out of Scope

- Transferencias interbancarias externas.
- Comisiones (Strategy preparado; se activa en evolución).

---

## 13. Dependencies

- SPEC-002 (cuentas), SPEC-003 (autenticación).

---

## 14. Open Questions

- ¿Límite diario configurable por cliente? (recomendado: global configurable).

---

## 15. Related Documents

- `ARCHITECTURE.md` §5, §6
