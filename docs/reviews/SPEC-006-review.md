# Review Report — SPEC-006

- **Verdict:** PASS
- **Review type:** compliance (reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Functional (FR-001..FR-015) | OK |
| Business rules (BR-001..BR-010) | OK |
| Alternative flows (AF-001..AF-006) | OK |
| Error cases (ERR-001..ERR-009) | OK |
| Authorization (§9 + matriz de guard §5.5 del diseño: login público; CLIENTE cuentas/movimientos/transferencias; ADMIN clientes/cuentas; backend como enforcement — BR-008) | OK |
| Acceptance criteria (AC-001..AC-031) | OK — 111 tests en 14 archivos, todos cubiertos (ver tabla) |
| Architecture (file map §3/§5 del diseño, tipos espejo de DTOs, dependencias §5.7, proxy Vite, sesión localStorage, decode JWT client-side) | OK |
| ADR-008 (stack, proxy sin CORS, sesión, rol client-side UX-only, estado Context) | OK |
| Scope (solo `frontend/` + docs de spec/arquitectura/ADR/review; cero cambios en `backend/`; sin registro/depósitos/retiros UI) | OK |
| Testing commands (`npm run typecheck`, `npm run lint`, `npm test`, `npm run build`) | OK — verificados, 14 archivos / 111 tests pasando |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor / Nit

- **Versionado con caret ranges en `package.json`:** el diseño (§5.7/§5.8) pide "versiones pinneadas"; la implementación usa rangos `^` (p. ej. `"react": "^18.3.1"`). El caret previene drift de major y no afecta requerimientos; se recomienda confirmar `package-lock.json` para reproducibilidad.
- **Mensaje del login ante 5xx:** `LoginPage.tsx:36-43` muestra "Usuario o contraseña incorrectos" para cualquier `ApiError` no-`ERROR_RED` (incluidos 5xx), no el `message` del envelope. La spec solo exige mensaje genérico para `401` (ERR-001, A-004); para 5xx es una decisión de UX defendible. No afecta AC.
- **Sin README en `frontend/`:** el riesgo §9 del diseño sugiere documentar el requisito de Node 18+/20+ "en el README o en el PR". No bloquea; se recomienda documentar en el PR.
- **`formatearFecha` en hora local:** `src/lib/session.ts` formatea los `Instant` ISO-8601 del backend en la zona local del navegador (solo display, sin impacto en tests ni en reglas).
- **Warnings de React Router v6 (future flags v7):** aparecen en stderr de los tests (informativos); el router está pinneado a v6 por decisión del diseño (§9). Sin acción requerida.

## Verification

### Tabla de cumplimiento por criterio de aceptación (AC-001..AC-031)

| AC | Test que lo cubre | ¿OK? |
| --- | --- | --- |
| AC-001 | Estructural — scaffolding `frontend/` con scripts `lint`/`typecheck`/`test`/`build` y carpetas `src/{api,components,hooks,pages,store}` (+ `lib/` documentada en diseño §3) | OK |
| AC-002 | Comando — `npm run lint` sin errores | OK |
| AC-003 | Comando — `npm run typecheck` sin errores | OK |
| AC-004 | Comando — `npm run build` produce el bundle (`dist/`: index.html + assets) | OK |
| AC-005 | Comando — `npm test`: 14 archivos / 111 tests, todos pasando | OK |
| AC-006 | `src/lib/session.test.ts` (8) + `src/store/auth-context.test.tsx` (5): `guardarToken`/`leerToken`/`limpiarToken` con `banco.token`; logout limpia | OK |
| AC-007 | `src/lib/jwt.test.ts` (10): extrae `role`/`clienteId` (solo CLIENTE) de payload real; malformado → `null` | OK |
| AC-008 | `src/pages/LoginPage.test.tsx` (5): login OK envía `{username, password}` y redirige a `/cuentas` (CLIENTE) y `/gestion` (ADMIN) | OK |
| AC-009 | `LoginPage.test.tsx`: `401` → mensaje genérico, no almacena token, permanece en `/login` | OK |
| AC-010 | `src/api/httpClient.test.ts` (12): adjunta `Authorization: Bearer` con sesión; sin token sin sesión; nunca fuera de `/api/v1` | OK |
| AC-011 | `httpClient.test.ts` + `src/App.test.tsx` (7): `401` en request autenticada → `limpiarToken` + redirección a `/login` | OK |
| AC-012 | `src/components/Layout.test.tsx` (3): logout limpia `localStorage` y redirige a `/login` | OK |
| AC-013 | `src/App.test.tsx` + `src/components/ProtectedRoute.test.tsx` (5): sin sesión → `/login`; CLIENTE no accede a `/gestion`; ADMIN no accede a rutas de CLIENTE; `/login` con sesión → ruta del rol | OK |
| AC-014 | `src/pages/CuentasPage.test.tsx` (7): `GET /api/v1/cuentas` sin parámetros; render con saldo ARS, cbu, tipo, moneda y estado | OK |
| AC-015 | `CuentasPage.test.tsx`: lista vacía → estado vacío; formulario de transferencia sin orígenes (deshabilitado) | OK |
| AC-016 | `CuentasPage.test.tsx`: expansión → `GET /api/v1/cuentas/{id}/movimientos`; render de tipo, monto, moneda y fecha | OK |
| AC-017 | `src/pages/TransferenciaPage.test.tsx` (8): submit envía payload exacto `{cuentaOrigenId, cbuDestino, monto}` a `POST /api/v1/transferencias` | OK |
| AC-018 | `TransferenciaPage.test.tsx`: `201` → confirmación (`idTransferencia`, `monto`, `cbuDestino`, `fechaHora`) y refresh de cuentas/movimientos | OK |
| AC-019 | `TransferenciaPage.test.tsx`: `422` (SALDO_INSUFICIENTE) → mensaje del envelope en el formulario; datos conservados | OK |
| AC-020 | `TransferenciaPage.test.tsx` + `src/lib/validacion.test.ts` (23): pre-validaciones BR-002..BR-005 bloquean el envío con errores por campo | OK |
| AC-021 | `CuentasPage.test.tsx`, `App.test.tsx`, `Layout.test.tsx`, `GestionPage.test.tsx` (4): la UI del CLIENTE no expone acciones de ADMIN | OK |
| AC-022 | `src/pages/gestion/ClientesSection.test.tsx` (7): `GET /api/v1/clientes`; render de nombre, apellido, dni y email | OK |
| AC-023 | `ClientesSection.test.tsx`: `POST /api/v1/clientes` con payload exacto; `201` agrega el `ClienteDto` a la lista | OK |
| AC-024 | `ClientesSection.test.tsx`: `PUT /api/v1/clientes/{id}` con payload actualizado; muestra la representación `200` | OK |
| AC-025 | `src/pages/gestion/CuentasSection.test.tsx` (7): `POST /api/v1/cuentas` con `{clienteId, tipo, moneda}` (default `ARS`); refresh de la lista | OK |
| AC-026 | `CuentasSection.test.tsx`: `GET /api/v1/cuentas?clienteId={id}`; render de `CuentaDto` del cliente | OK |
| AC-027 | `ClientesSection.test.tsx` + `CuentasSection.test.tsx`: `400 DATOS_INVALIDOS` con `details` → errores por campo | OK |
| AC-028 | `GestionPage.test.tsx` (4) + `TransferenciaPage.test.tsx` + `CuentasSection.test.tsx` + `ClientesSection.test.tsx`: `403`/`404`/`409`/`422` → mensaje del envelope en la vista | OK |
| AC-029 | `CuentasPage.test.tsx`, `TransferenciaPage.test.tsx`, `ClientesSection.test.tsx`: error de red (`ERROR_RED`) → mensaje de conexión y operación reintentable | OK |
| AC-030 | `LoginPage.test.tsx`, `TransferenciaPage.test.tsx`, `CuentasPage.test.tsx`, `CuentasSection.test.tsx`: estado de carga durante requests; sin doble envío | OK |
| AC-031 | `httpClient.test.ts` + `src/lib/session.test.ts`: token nunca adjunto a URLs fuera de `/api/v1`; no expuesto en logs | OK |

### Verificación de arquitectura (docs/architecture/SPEC-006.md)

1. **§3/§5.1 File map** — OK. `frontend/` con scaffolding completo (package.json, vite.config.ts con proxy `/api` → `http://localhost:8080` sin rewrite, tsconfig estricto + tsconfig.node, eslint flat, vitest.setup, index.html); `src/{api,lib,store,hooks,components,pages}`; la extensión `lib/` está documentada en §3 (nota) y no rompe AC-001. `.gitkeep` × 5 eliminados.
2. **§5.2 Tipos espejo** — OK. `src/api/types.ts` espeja 1:1 los DTOs del backend (verificado contra `infrastructure/adapter/web`): `LoginRequest`, `LoginResponse{token}` (sin `rol` — A-002), `ClienteDto`, `CuentaDto`, `MovimientoDto`, `TransferirRequest`, `AbrirCuentaRequest{moneda?}`, `TransferenciaConfirmacion`, `DetalleError`, `ErrorEnvelope`, `JwtClaims{role, clienteId?}`.
3. **§5.2 httpClient (FR-002/FR-007)** — OK. `request<T>` con base `/api/v1`; Bearer solo con sesión y solo hacia `/api/v1` (BR-009); parsing del envelope `{code, message, details?}` en `ApiError`; distingue `401` en login (`autenticar:false` → error de formulario, AF-001) del `401` en requests autenticadas (callback `onNoAutorizado` → limpiarToken + redirección, FR-007/AF-002); red → `ERROR_RED` (ERR-008).
4. **§5.3 Utilidades puras** — OK. `jwt.ts` (base64url, sin verificación de firma, valida forma, `null` ante malformado — FR-004/A-002), `session.ts` (`banco.token`, `formatearMontoARS` con Intl es-AR — BR-010), `validacion.ts` (BR-001..BR-007 puras).
5. **§5.4/§5.5 Guard y componentes** — OK. `ProtectedRoute` implementa la matriz §5.5 (sin sesión → `/login`; CLIENTE → `/cuentas`; ADMIN → `/gestion`; `/login` con sesión → home del rol; ruta ajena → home del rol — FR-006/AC-013). `Layout` con nav por rol + logout (FR-014). Estados Cargando/Error(Reintentar)/Vacío y `CampoFormulario`/`Monto` (FR-015).
6. **§5.7 Dependencias** — OK. Solo las justificadas: react 18, react-dom, react-router-dom v6; dev: typescript, vite 5, @vitejs/plugin-react, @types/react(-dom), eslint flat + typescript-eslint + plugin-react-hooks/react-refresh + globals, vitest, jsdom, @testing-library/{react,dom,user-event,jest-dom}. Sin axios, redux/zustand, MUI ni MSW (verificado en package.json).
7. **§6 Sin cambios de datos backend** — OK. Sin migraciones ni cambios de configuración del backend; sesión efímera en `localStorage`.
8. **§7 Integración** — OK. Única integración: API `/api/v1` vía proxy de Vite (same-origin en dev, sin CORS backend — A-001 confirmado en `SecurityConfig`).
9. **§8 Testing** — OK. 14 archivos de test, 111 casos; mapeo AC-001..AC-031 conforme a la tabla del diseño; `fetch` mockeado con `vi.stubGlobal`; `localStorage` limpio entre tests (`vitest.setup.ts`).
10. **Scope / no tocar backend** — OK. `git diff testing..HEAD` muestra solo archivos de `docs/` (spec, arquitectura, ADR-008, sprints, reviews) y `frontend/`; cero referencias a SPEC-006 en `backend/`; sin pantallas de registro (A-004), depósitos o retiros (A-005) en la UI.

### Tests / comandos

- `npm run typecheck` — **PASS** (`tsc --noEmit`, sin errores).
- `npm run lint` — **PASS** (`eslint .`, sin errores ni warnings).
- `npm test` — **PASS** — 14 archivos / **111 tests** pasando (duración ~13s; solo warnings informativos de future-flags de React Router v6).
- `npm run build` — **PASS** — `tsc -b && vite build`; 60 módulos; `dist/` con `index.html`, CSS 2.21 kB (gzip 0.81 kB) y JS 184.17 kB (gzip 59.30 kB).

## Result

**PASS.** La implementación satisface los requerimientos funcionales (FR-001..FR-015), las reglas de negocio (BR-001..BR-010, todas pre-validaciones UX que espejan reglas del backend — el backend permanece como punto de enforcement, BR-008), los flujos alternativos (AF-001..AF-006) y los casos de error (ERR-001..ERR-009) de la spec aprobada, y cumple los 31 criterios de aceptación (AC-001..AC-031) con 111 tests unitarios y de componente que los cubren (más lint/typecheck/build verificados). Respeta la arquitectura aprobada: file map de `docs/architecture/SPEC-006.md` §3/§5 (incluida la extensión documentada `src/lib/`), tipos TS espejo 1:1 de los DTOs del backend, cliente HTTP con Bearer y envelope de errores, sesión en `localStorage` (`banco.token`), decode client-side del JWT solo para UX (A-002), guard de rutas por rol (matriz §5.5), proxy de desarrollo de Vite `/api` → `http://localhost:8080` sin cambios de CORS en el backend (A-001), y las decisiones de ADR-008. La autorización (§9: login público; CLIENTE solo cuentas/movimientos/transferencias propias; ADMIN clientes y cuentas) y el alcance (cero cambios en `backend/`, sin UI de registro/depósitos/retiros) son correctos. Los hallazgos son menores/nits documentados (versionado caret, mensaje del login ante 5xx, ausencia de README del frontend, formato local de fechas, warnings de future-flags) y no afectan requerimientos aprobados.
