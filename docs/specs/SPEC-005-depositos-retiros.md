# SPEC-005 — Depósitos y Retiros

## Status

Draft

---

## 1. Objective

Permitir depósitos (ingreso de dinero a una cuenta) y retiros (egreso de
dinero de una cuenta) con validación de saldo y reglas de negocio.

---

## 2. Actors

- **ADMIN**: ejecuta depósitos y retiros (caja).
- **CLIENTE**: puede retirar de sus propias cuentas (según alcance del MVP).

---

## 3. Preconditions

- Usuario autenticado.
- La cuenta objetivo debe existir y estar `ACTIVA`.

---

## 4. Functional Requirements

### FR-001

Depositar un monto en una cuenta (suma al saldo).

### FR-002

Retirar un monto de una cuenta (resta del saldo).

### FR-003

Registrar un `Movimiento` de tipo `DEPOSITO` o `RETIRO`.

### FR-004

Emitir `DepositoRealizado` o `RetiroRealizado`.

---

## 5. Business Rules

### BR-001

El monto debe ser mayor a 0.

### BR-002

El saldo no puede quedar negativo tras un retiro.

### BR-003

La cuenta debe estar `ACTIVA`.

### BR-004

Concurrencia: `@Version` evita saldos inconsistentes.

---

## 6. Main Flow

1. `ADMIN` (o `CLIENTE` en retiro propio) envía cuenta y monto.
2. Se valida (Chain of Responsibility): cuenta activa → monto > 0 →
   saldo suficiente (solo retiro).
3. En una transacción se actualiza el saldo.
4. Se persiste un movimiento y se emite el evento.

---

## 7. Alternative Flows

### AF-001

Depósito en cuenta ajena: permitido solo para `ADMIN`.

---

## 8. Error Cases

### ERR-001

Monto <= 0 o inválido → `400 Bad Request`.

### ERR-002

Saldo insuficiente en retiro → `422 Unprocessable Entity`.

### ERR-003

Cuenta inexistente → `404 Not Found`.

### ERR-004

Cuenta `BLOQUEADA` → `422 Unprocessable Entity`.

### ERR-005

`CLIENTE` intenta retirar de una cuenta ajena → `403 Forbidden`.

---

## 9. Authorization

- Depósito: solo `ADMIN`.
- Retiro: `ADMIN` (cualquier cuenta) o `CLIENTE` (solo propias).

---

## 10. Data Changes

- Actualización de saldo de `Cuenta`.
- Entidad `Movimiento`.
- Migración Flyway correspondiente.

---

## 11. Acceptance Criteria

- [ ] Un depósito válido suma al saldo y registra `Movimiento` `DEPOSITO`.
- [ ] Un retiro válido resta del saldo y registra `Movimiento` `RETIRO`.
- [ ] Retirar más del saldo disponible devuelve `422` y no altera el saldo.
- [ ] Depósito con monto <= 0 devuelve `400`.
- [ ] Un `CLIENTE` no puede retirar de una cuenta ajena (`403`).

---

## 12. Out of Scope

- Operaciones en moneda extranjera (solo `ARS` en el MVP).
- Límites de extracción por cajero.

---

## 13. Dependencies

- SPEC-002 (cuentas), SPEC-003 (autenticación).

---

## 14. Open Questions

- Ninguna.

---

## 15. Related Documents

- `ARCHITECTURE.md` §5, §6
