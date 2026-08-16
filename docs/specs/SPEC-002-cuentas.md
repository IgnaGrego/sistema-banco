# SPEC-002 — Gestión de Cuentas

## Status

Draft

---

## 1. Objective

Permitir la apertura y consulta de cuentas bancarias asociadas a un cliente.
Cada cuenta tiene saldo, moneda y tipo.

---

## 2. Actors

- **ADMIN**: abre cuentas, las lista y consulta.
- **CLIENTE**: consulta y lista sus propias cuentas.

---

## 3. Preconditions

- El cliente titular debe existir.
- El usuario debe estar autenticado.

---

## 4. Functional Requirements

### FR-001

Abrir una cuenta para un cliente con: tipo (`CAJA_AHORRO` | `CUENTA_CORRIENTE`)
y moneda (por defecto `ARS`).

### FR-002

El sistema asigna un `CBU` único automáticamente.

### FR-003

El saldo inicial es `0` y nunca puede ser negativo.

### FR-004

Consultar una cuenta por `id` o `cbu`.

### FR-005

Listar cuentas de un cliente.

---

## 5. Business Rules

### BR-001

`CBU` es único y validado por su VO.

### BR-002

El saldo es siempre `>= 0` (invariante del agregado `Cuenta`).

### BR-003

Solo se puede operar sobre cuentas en estado `ACTIVA`.

### BR-004

La creación de la cuenta usa una Factory según el tipo.

---

## 6. Main Flow

1. `ADMIN` solicita apertura indicando cliente y tipo.
2. El sistema valida que el cliente exista.
3. La Factory crea la `Cuenta` con `CBU` único y saldo 0.
4. El sistema persiste y devuelve la cuenta con su `cbu`.

---

## 7. Alternative Flows

### AF-001

`CLIENTE` lista sus cuentas: el sistema devuelve solo las vinculadas a su
cliente.

---

## 8. Error Cases

### ERR-001

Cliente inexistente al abrir la cuenta.

Se responde `404 Not Found`.

### ERR-002

Intento de operar una cuenta `BLOQUEADA`.

Se responde `422 Unprocessable Entity`.

### ERR-003

`CLIENTE` intenta consultar una cuenta ajena.

Se responde `403 Forbidden`.

---

## 9. Authorization

- Abrir cuenta: solo `ADMIN`.
- Consultar/listar: `ADMIN` (cualquiera) o `CLIENTE` (solo propias).

---

## 10. Data Changes

- Entidad `Cuenta` (id, clienteId, cbu, tipo, saldo, moneda, estado,
  createdAt, `@Version`).
- Migración Flyway correspondiente.

---

## 11. Acceptance Criteria

- [ ] `ADMIN` abre una cuenta y recibe `cbu` y saldo 0.
- [ ] El `CBU` generado es único.
- [ ] Intentar abrir cuenta para cliente inexistente da `404`.
- [ ] Un `CLIENTE` solo lista sus propias cuentas.
- [ ] Operar una cuenta `BLOQUEADA` devuelve `422`.

---

## 12. Out of Scope

- Depósitos/retiros y transferencias (SPEC-004 y SPEC-005).
- Comisiones por tipo de cuenta (evolución; Strategy preparado en dominio).

---

## 13. Dependencies

- SPEC-001 (clientes).
- SPEC-003 (autenticación).

---

## 14. Open Questions

- Ninguna.

---

## 15. Related Documents

- `ARCHITECTURE.md` §4, §5
- `docs/domain/`
