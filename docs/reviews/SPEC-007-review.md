# Review Report — SPEC-007

- **Verdict:** PASS
- **Review type:** compliance (reviewer) — re-review (round 1)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 2 (re-review of the round-1 Major finding on AC-011)

## Summary

| Area | Result |
| --- | --- |
| Functional (FR-001..FR-007) | PASS — all functional requirements implemented and correct |
| Business Rules (BR-001..BR-005) | PASS — all UX mirrors correctly enforced; backend remains the enforcement point |
| Authorization (§9, BR-005) | PASS — section only mounted in ADMIN `/gestion`; server-side enforcement preserved |
| Validation (BR-001..BR-003, ERR-001..ERR-007) | PASS — pre-validations and envelope mapping implemented |
| Persistence / data relationships | PASS — frontend-only; no backend/schema changes; saldo read from refreshed `CuentaDto` |
| Testing (FR-007, AC-001..AC-020) | PASS — suite present; AC-011 Major finding resolved in re-review (round 1 fix); all ACs now have genuine assertions |
| Architecture | PASS — adheres to the approved design; no new abstractions |
| Scope (A-001..A-004, AC-020) | PASS — frontend-only, no new deps, no backend/CSS/docker changes, no unrelated refactor |

## Findings

### Blocker

- None.

### Major

- **RESOLVED (re-review round 1) — AC-011 / ERR-001: the per-field server-error mapping path is now genuinely exercised.**
  - Location: `frontend/src/pages/gestion/CajaSection.test.tsx` (test "AC-011 — 400 DATOS_INVALIDOS con details de monto").
  - Round-1 finding: the test typed `monto = '0'`, which was blocked client-side by BR-001 pre-validation, so the mocked `400` response for `POST /api/v1/depositos` was never reached and the `details`→field mapping branch was not exercised.
  - Round-1 fix (verified): the test now types `monto = '100'` (passes BR-001 pre-validation), the mock returns `400 DATOS_INVALIDOS` with `details: [{ campo: 'monto', mensaje: 'El monto supera el límite permitido' }]` (a server-only message), and it:
    - scopes the per-field assertion to the monto field container (`dep.getByLabelText('Monto depósito').closest('.campo')` + `within(campoMonto).findByRole('alert')`) to avoid the general envelope alert, asserting the server-provided message `'El monto supera el límite permitido'`;
    - keeps the "datos conservados" assertion (`toHaveValue('100')`);
    - verifies the request was actually sent (`fetchMock` was called with `POST /api/v1/depositos`).
  - Confirmed the request now reaches the server mock, so the `useEffect` branch at `CajaSection.tsx` (lines 148-164) mapping `error.details[].campo` → per-field error is genuinely exercised. The `CampoFormulario` component renders the per-field error inside a `.campo` container with `role="alert"`, matching the test's scoping. Finding resolved; no remaining compliance issue.

### Minor / Nit

- **Wording of BR-002 retiro error:** `validarRetiro` returns `'El saldo no es suficiente para el retiro'`, whereas the architecture doc §5.3/§8.3 specifies `'El monto no puede superar el saldo de la cuenta'`. The spec (BR-002) does not fix the message text, and the implementation and its tests are internally consistent, so this is not a violation — only a documentation drift from the architecture prose. Recommend aligning the architecture doc or the code to a single string.
- **Wording of BR-003 cuenta error:** `validarCuentaCaja` returns `'Seleccione la cuenta'` vs. the architecture's `'Seleccione una cuenta'`. Same nature as above — not a violation.
- **Monto input labels:** the forms use `label="Monto depósito"` / `label="Monto retiro"` instead of the architecture's `label="Monto"`. This is a reasonable, deliberate choice that resolves the duplicate-label ambiguity described in spec §11, and all tests use the actual labels. Not a violation.
- **Verification tooling:** I could not execute `npm test`, `npm run lint`, or `npm run typecheck` in this environment (no shell access), so AC-019 and the AC-011 re-review were verified by static inspection of the test suite rather than by running the commands. The developer reported the full frontend suite at 136 passing tests.

## Verification

Inspected (read-only):

- Spec: `docs/specs/SPEC-007-caja-gestion.md` (FR-001..FR-007, BR-001..BR-005, AF-001..004, ERR-001..007, AC-001..020, A-001..A-004).
- Architecture: `docs/architecture/SPEC-007.md` (full, incl. §8, §10 test map, §13, §14).
- `AGENTS.md` (rules on business logic location, tests required, scope control, frontend-not-the-enforcement-point).
- Implementation:
  - `frontend/src/api/caja.ts` — `depositar`/`retirar` hitting `POST /api/v1/depositos` / `/retiros` with `{cuentaId, monto}` (matches architecture §5.2).
  - `frontend/src/api/types.ts` — `DepositoRequest`, `RetiroRequest`, `DepositoConfirmacion`, `RetiroConfirmacion` mirror backend records (verified against `backend/.../DepositoRequest.java`, `RetiroRequest.java`, `DepositoConfirmacion.java`, `RetiroConfirmacion.java`).
  - `frontend/src/hooks/useCaja.ts` — `useCaja(tipo)` with double-submit protection (`enVuelo` ref + `enviando`), one instance per form.
  - `frontend/src/pages/gestion/CajaSection.tsx` — client selector, `CajaDeCliente` (list + `recargar` refresh), two `FormularioCaja`, `ConfirmacionCaja` (`role="status"`), envelope error mapping (`details`→field / general), CBU snapshot, `sinCuentas` disable (AF-001).
  - `frontend/src/lib/validacion.ts` — `validarDeposito`/`validarRetiro` with shared private helpers reusing `REGEX_MONTO`.
  - `frontend/src/pages/GestionPage.tsx` — `CajaSection` mounted as third section (FR-001).
  - `frontend/src/pages/GestionPage.test.tsx` — queries scoped per §8.7 (`findAllByText` for "Pérez, Juan", `within(heading 'Cuentas')` for the "Cliente" label, removal of the `'Monto'` proxy from AC-021, criteria preserved).
  - Tests: `CajaSection.test.tsx` and `validacion.test.ts` (AC-001..AC-018, AC-010).
- `frontend/package.json` — no new dependencies (react/react-dom/react-router-dom unchanged).
- Backend file inventory — deposito/retiro endpoints already present from merged SPEC-005; no SPEC-007 backend additions observed.
- Re-review (round 1): re-read `CajaSection.test.tsx` AC-011 (lines 311-354) and confirmed the fix; re-read production `CajaSection.tsx` (lines 142-244, mapping branch + `CampoFormulario` usage) and `components/CampoFormulario.tsx` (`.campo` container + `role="alert"`) to confirm the test's scoping selector matches the production DOM and no production code changed.

Mapping verified:

- FR-001 — `CajaSection` mounted in `GestionPage` as third section. ✓
- FR-002 — client selector + `GET /api/v1/cuentas?clienteId=`; selectors offer only `ACTIVA` (BR-003); list shows all accounts incl. `BLOQUEADA`. ✓
- FR-003/FR-004 — deposit→`POST /api/v1/depositos`, withdrawal→`POST /api/v1/retiros`, payload exactly `{cuentaId, monto}`. ✓ (asserted in AC-004/AC-005 with `toEqual({cuentaId, monto})`).
- FR-005 — `role="status"` confirmation (idMovimiento, ARS monto, fechaHora, CBU); envelope feedback details→field / general; data preserved; double-submit protection (AC-018). ✓
- FR-006 — `recargar()` of `useCuentas` after `201`; saldo read from refreshed `CuentaDto`, no local computation (BR-004, AC-006). ✓
- FR-007 / AC-001..AC-020 — component + unit tests present for happy path, validations, errors, double-submit, empty-states; lint/typecheck not runnable in this environment (noted). ✓ (AC-011 coverage caveat resolved in re-review).
- BR-001 — monto > 0, ≤ 2 decimals via `REGEX_MONTO`. ✓
- BR-002 — retiro saldo-suficiente pre-validation (UX only). ✓
- BR-003 — only `ACTIVA` accounts offered; mandatory selection. ✓
- BR-005 — frontend not the enforcement point; authorization server-side; section only in ADMIN `/gestion`. ✓
- A-001 — one section, two forms. ✓ / A-003 — new `api/caja.ts`. ✓ / A-004 — current design kept, no CSS changes. ✓
- No new npm deps (AC-020), no backend/CSS/docker changes, no unrelated refactor. ✓

## Result

PASS.

The SPEC-007 implementation is functionally compliant with the approved specification and architecture. All functional requirements (FR-001..FR-007), business rules (BR-001..BR-005), authorization constraints, error-envelope handling, and acceptance criteria (AC-001..AC-020) are implemented correctly, frontend-only, with no new dependencies and no backend, CSS, or unrelated changes. Tests exist and cover the happy path, validations, error cases, empty states, and double-submit protection, and the `GestionPage.test.tsx` queries were scoped (not weakened) per architecture §8.7.

**Re-review (round 1) — Major finding resolved.** The round-1 Major finding on AC-011 is resolved: the test now types a `monto` (`'100'`) that passes BR-001 pre-validation, the mocked server returns `400 DATOS_INVALIDOS` with a server-only `details` message, the per-field message is asserted scoped to the monto field container, the "datos conservados" assertion is kept, and the test verifies the `POST /api/v1/depositos` request was actually sent — genuinely exercising the `details`→field mapping branch. No production code changes were observed in this round (the change is confined to the AC-011 test; the production `CajaSection.tsx` and `CampoFormulario.tsx` match the round-1 review). The remaining items are wording/label drift from the architecture prose, not spec violations. These do not change the compliance verdict.
