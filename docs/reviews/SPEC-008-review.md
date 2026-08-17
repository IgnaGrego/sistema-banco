# Review Report — SPEC-008

- **Verdict:** PASS
- **Review type:** compliance (reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 1

## Summary

| Area | Result |
| --- | --- |
| Functional (FR-001..FR-005) | PASS — "Usuarios" section mounted in `/gestion` (no new route); form with username/password/rol + conditional client selector; UX pre-validations; envelope-based success/error feedback; component + unit tests present |
| Business Rules (BR-001..BR-005) | PASS — BR-001..BR-004 implemented as UX mirrors in `validarRegistroUsuario`; BR-005 respected: backend (`RegistroValidator`) remains the enforcement point and the payload is the exact `{username, password, rol, clienteId}` |
| Authorization (§9, A-002/A-006) | PASS — section only mounted in `/gestion` (`ProtectedRoute rolPermitido="ADMIN"`); `registro()` uses `autenticar: false` (endpoint `permitAll` verified in `SecurityConfig`); no `Authorization` header sent (asserted in AC-003) |
| Validation (BR-001..BR-004, ERR-001..ERR-005) | PASS — pre-validations mirror `RegistroValidator`; envelope `details` → per-field errors via whitelist `CAMPOS_REGISTRO`, rest → general message; data preserved on error |
| Persistence / data relationships | PASS — frontend-only; no backend/schema/docker changes; `clienteId` typed `number \| null` (null for ADMIN — A-003) mirrors backend `Long` |
| Testing (FR-005, AC-001..AC-016) | PASS — every AC-001..AC-014 has a test with genuine assertions (exact payloads, per-field error scoping, retry paths); AC-008 covered by unit tests; AC-015 verified by static analysis only (see Verification limitation); AC-016 verified |
| Architecture | PASS — adheres to the approved design (§5.2 API layer, §5.3 validacion, §5.4 hook, §5.5 section, §8.6 envelope mapping); no new abstractions; no ADRs needed (§11) |
| Scope (spec §10/§12, AC-016) | PASS — frontend-only; no new npm deps, no backend/CSS/docker changes, no unrelated refactoring |

## Findings

### Blocker

- None.

### Major

- None.

### Minor / Nit

- **AC-015 commands not executable in this environment:** I have no shell access, so `npm run lint`, `npm run typecheck`, `npm test` and `npm run build` could not be executed. AC-015 was verified by static inspection of the code, the Vitest/ESLint/tsconfig configuration, and the test suite (see Verification). The suite is internally consistent with the existing patterns that already pass in the repo (same helpers, same mock setup, same jsdom config). The developer/orchestrator must confirm the four commands are green before merge. This mirrors the verification limitation noted in the SPEC-007 review.
- **`useClientes()` consumed unconditionally:** `UsuariosSection` calls `useClientes()` at mount even when `rol = ADMIN` (selector hidden), producing a fourth `GET /api/v1/clientes` in `/gestion`. The architecture §12 explicitly frames conditional consumption as an "Alternativa recomendada" (recommendation, not a requirement), while §5.5's structure shows unconditional consumption and the spec §10 mandates the `useClientes()` pattern of the other sections. The implementation matches the approved design; not a violation.
- **Minor wording checks (no drift):** per-field message texts match the architecture §8.3 exactly ('El usuario es obligatorio', 'El usuario no puede superar los 50 caracteres', 'La contraseña debe tener al menos 8 caracteres', 'Seleccione un rol', 'Seleccione un cliente a vincular'). The confirmation text `Usuario creado: {username} ({rol})` satisfies FR-004/A-005 (username + rol, never password). No violations.

## Verification

Inspected (read-only):

- **Spec:** `docs/specs/SPEC-008-usuarios-gestion.md` — FR-001..FR-005, BR-001..BR-005, AF-001..AF-003, ERR-001..ERR-005, AC-001..AC-016, A-001..A-006, §10/§12 (scope).
- **Architecture:** `docs/architecture/SPEC-008.md` — §3 (affected modules), §5.1-§5.8 (components), §8.2 (API contract verified against code), §8.3 (validation), §8.4 (hook contract), §8.5 (section structure), §8.6 (envelope mapping), §8.7 (GestionPage.test.tsx scoping), §10 (test map), §13/§14.
- **AGENTS.md** — §8 (reviewer), §11 (business logic layers), §12 (testing), §14 (scope control), §17 (passwords never in clear / never exposed).
- **Backend contract (verified claims):**
  - `AuthController.java` — `POST /api/v1/auth/register` → `201 UsuarioDto` (no `Location`, no business rules in the controller).
  - `RegistrarUsuarioRequest.java` — `record(String username, String password, String rol, Long clienteId)` — exact mirror of the TS interface.
  - `UsuarioDto.java` — `record(Long id, String username, String rol)` — never password; exact mirror of the TS interface.
  - `RegistroValidator.java` — username required/≤50, password ≥8, rol ∈ {CLIENTE, ADMIN}, clienteId required for CLIENTE → matches the UX mirrors.
  - `GlobalExceptionHandler.java` — 400 `DATOS_INVALIDOS` (details), 409 `CONFLICTO_UNICIDAD` (details campo username), 404 `CLIENTE_NO_ENCONTRADO`, 500 `ERROR_INTERNO` (no internal detail leak) — matches the envelope table of architecture §8.2.
  - `SecurityConfig.java` — `POST /api/v1/auth/register` → `permitAll()` (public endpoint) — confirms `autenticar: false` is correct.
- **Frontend implementation:**
  - `src/api/auth.ts` — `registro(body)` → `POST /auth/register` with `autenticar: false` (A-002), alongside `login()`; no `usuario.ts` created (A-002).
  - `src/api/types.ts` — `RegistrarUsuarioRequest {username, password, rol: Rol, clienteId: number | null}` and `UsuarioDto {id, username, rol}`; `Rol = 'CLIENTE' | 'ADMIN'` reused.
  - `src/lib/validacion.ts` — `validarRegistroUsuario` + private helpers `validarUsernameRegistro`/`validarPasswordRegistro`/`validarRolRegistro`/`validarClienteRegistro` (BR-001..BR-004); uniqueness deliberately not pre-validated (A-004); boundary: 8-char password valid, 50-char username valid.
  - `src/hooks/useRegistroUsuario.ts` — `{ enviando, confirmacion, error, ejecutar, limpiarConfirmacion }` with `enVuelo` ref double-submit protection; `limpiarConfirmacion` sanctioned by architecture §5.5 ("useRegistroUsuario expone una función o se remonta la sección por key").
  - `src/pages/gestion/UsuariosSection.tsx` — section (FR-001..FR-004): form with `rol` default `'ADMIN'` (architecture §8.5 leaves this to the developer), client selector rendered only when `rol === 'CLIENTE'` (A-003) fed by `useClientes`, `clienteId` reset to null when switching back to ADMIN, exact payload `{username: username.trim(), password, rol, clienteId: rol === 'CLIENTE' ? clienteId : null}`, `ConfirmacionUsuario` (`role="status"`, username + rol, never password), reset after 201 (rol preserved — architecture §4.2 allows), envelope mapping via `CAMPOS_REGISTRO` whitelist + general fallback (§8.6), inputs labeled "Usuario"/"Contraseña"/"Rol"/"Cliente" with unique `usuario-*` ids, `maxLength={50}` on username.
  - `src/pages/GestionPage.tsx` — `<UsuariosSection />` mounted as fourth section (FR-001).
  - `src/App.tsx` — unchanged; `/gestion` still wrapped in `ProtectedRoute rolPermitido="ADMIN"`; no new routes.
  - `src/api/httpClient.ts` — unchanged; `Authorization` header only attached when `autenticar` is true → `autenticar: false` means no header; `ERROR_RED`/`MENSAJE_ERROR_RED` mapping confirmed.
- **Tests:**
  - `src/pages/gestion/UsuariosSection.test.tsx` — AC-001/AC-002 (render + selector by rol + empty list + Cargando states), AC-003 (CLIENTE payload `toEqual({username, password, rol:'CLIENTE', clienteId:1})`, no `Authorization` header, confirmation without password, reset via "Registrar otro usuario"), AC-004 (ADMIN payload `clienteId: null`), AC-005 (empty/51-char username → per-field error, no request), AC-006 (7 chars blocked / 8-char boundary sends), AC-007 (CLIENTE without client → error, no request), AC-009 (400 details password → per-field error scoped to `.campo`, general `message`, data preserved, request sent), AC-010 (409 details username → per-field, data preserved, retry → success), AC-011 (404 → general envelope message, data preserved), AC-012 (500 → general, retry → success), AC-013 (`ERROR_RED` message, retry → success), AC-014 (button disabled "Registrando...", exactly one POST via deferred mock).
  - `src/lib/validacion.test.ts` — AC-008 unit cases: valid CLIENTE, ADMIN null, ADMIN with client, username empty/spaces/>50 + 50 boundary, password <8, rol empty/invalid, CLIENTE without clientId.
  - `src/pages/GestionPage.test.tsx` — queries scoped per §8.7 (`findAllByText('Pérez, Juan')` with `length >= 1`, `within` by "Cuentas" heading for the "Cliente" label, username label "Usuario" avoiding "Nombre" collision); criteria preserved (AC-028/AC-021 assertions intact).
- **Config:** `frontend/package.json` — no new dependencies (react/react-dom/react-router-dom unchanged; devDeps all pre-existing); `vite.config.ts` (jsdom, globals, setup), `vitest.setup.ts` (cleanup of localStorage + fetch mocks), `eslint.config.js` (test files exempt from fast-refresh rule; no `no-undef` for TS), `tsconfig.json` (`vitest/globals` types) — all unchanged and consistent with the new tests.
- **Scope (git):** `.git/logs/refs/heads/feature/spec-008` shows the branch contains only docs (spec + architecture) and frontend commits (`feat: tipos y endpoint`, `feat: pre-validacion`, `feat: hook de mutacion`, `feat: seccion Usuarios`); no backend/docker/migration commits. Working tree clean at HEAD `92bf223f`.

Requirement-by-requirement mapping:

| Requirement | Status | Evidence |
| --- | --- | --- |
| FR-001 — "Usuarios" section in `/gestion`, no new route | PASS | `GestionPage.tsx:21` mounts `<UsuariosSection />`; `App.tsx` unchanged (`/gestion` under `ProtectedRoute rolPermitido="ADMIN"`); AC-001 test |
| FR-002 — form username/password/rol + client selector when CLIENTE fed by `GET /api/v1/clientes` | PASS | `UsuariosSection.tsx:118-177` (labels "Usuario"/"Contraseña"/"Rol", `type="password"`, `maxLength=50`, selector `rol === 'CLIENTE'` via `useClientes`); exact payload `{username, password, rol, clienteId}` with `null` for ADMIN (A-002/A-003); AC-002/AC-003/AC-004 tests |
| FR-003 — UX pre-validation (username format + password ≥ 8; uniqueness via 409) | PASS | `validacion.ts:238-261` mirrors `RegistroValidator`; uniqueness deliberately not pre-validated (A-004, no check endpoint exists); AC-005..AC-008 tests |
| FR-004 — success/error feedback with envelope; reset; double-submit protection | PASS | `ConfirmacionUsuario` (`role="status"`, username + rol, never password — A-005); reset after 201; envelope mapping `UsuariosSection.tsx:48-68`; `useRegistroUsuario` `enVuelo` + disabled button; AC-003/004/009..014 tests |
| FR-005 — component + unit tests | PASS | `UsuariosSection.test.tsx` (AC-001..AC-014), `validacion.test.ts` (AC-008) |
| BR-001 — username required & ≤50 | PASS | `validarUsernameRegistro`; AC-005/AC-008 |
| BR-002 — password ≥8 | PASS | `validarPasswordRegistro`; 8-char boundary valid; AC-006/AC-008 |
| BR-003 — rol required & valid (CLIENTE/ADMIN) | PASS | `validarRolRegistro`; selector offers exactly the two options; AC-008 |
| BR-004 — clienteId required when CLIENTE | PASS | `validarClienteRegistro`; selector hidden + `null` for ADMIN (A-003); AC-007/AC-008 |
| BR-005 — backend remains enforcement point | PASS | no new business rules in the SPA; exact payload; backend `RegistroValidator` verified |
| ERR-001 (400 DATOS_INVALIDOS → per-field) | PASS | AC-009 test + mapping (details password → field error, data preserved, request actually sent) |
| ERR-002 (409 CONFLICTO_UNICIDAD → per-field username) | PASS | AC-010 test + retry path |
| ERR-003 (404 CLIENTE_NO_ENCONTRADO → general) | PASS | AC-011 test |
| ERR-004 (500 ERROR_INTERNO → general) | PASS | AC-012 test + retry |
| ERR-005 (network → ERROR_RED) | PASS | AC-013 test + retry; `httpClient` verified |
| §9 Authorization | PASS | section only in ADMIN `/gestion`; endpoint `permitAll` (SecurityConfig); `autenticar: false`; no Authorization header (AC-003 asserts `init.headers` lacks it) |
| AC-001..AC-014 | PASS | each has a test with genuine assertions (payloads with `toEqual`, per-field alerts scoped via `within`, request-sent/no-request assertions, retry paths) |
| AC-015 (lint/typecheck/build/test) | PASS (static) | cannot execute commands in this environment; config + code statically consistent; see Verification limitation |
| AC-016 (no new deps / no backend / no migrations / no docker) | PASS | `package.json` unchanged; git log shows frontend+docs-only commits; backend files are the merged SPEC-003 state |
| Developer choices (limpiarConfirmacion; `clienteId: number \| null`; rol default 'ADMIN') | PASS | architecture §5.5 sanctions exposing a function from the hook; A-003 sanctions `clienteId: null` for ADMIN; architecture §8.5 leaves the 'ADMIN' default to the developer's criteria |
| AGENTS.md §11 (business logic layering) | PASS | business rules remain in `domain`/`application` (backend unchanged); the SPA only mirrors UX; `api` → `hook` → `section` layering respected; validations live in `lib/validacion.ts`, not in the component |

## Result

PASS.

The SPEC-008 implementation is functionally compliant with the approved specification and architecture. All functional requirements (FR-001..FR-005), business rules (BR-001..BR-005), authorization constraints (public endpoint consumed with `autenticar: false`, no `Authorization` header; section gated by the ADMIN route guard), error-envelope handling (400/409 per-field, 404/500/network general, data preserved), and acceptance criteria (AC-001..AC-016) are implemented correctly and frontend-only, with no new npm dependencies and no backend, migration, docker, or unrelated changes. Every AC-001..AC-014 is backed by a component or unit test whose assertions genuinely exercise the required behavior (exact payloads including `clienteId: null` for ADMIN and the chosen client id for CLIENTE, per-field envelope mapping, double-submit protection, retry paths). The developer's documented implementation choices (extra `limpiarConfirmacion` member, `clienteId` typed `number | null` always present, `rol` default `'ADMIN'`) are consistent with the spec and architecture. `GestionPage.test.tsx` queries are scoped, not weakened.

The only verification limitation is that `npm run lint`, `npm run typecheck`, `npm test` and `npm run build` could not be executed in this environment (no shell access); AC-015 was verified by static analysis of the code, the test suite and the toolchain configuration, which is internally consistent with the patterns already passing in the repo. These commands should be run by the orchestrator/code-reviewer gate before merge, per the standard pipeline.