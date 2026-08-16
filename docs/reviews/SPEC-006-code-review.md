# Review Report — SPEC-006

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Readability & maintainability | OK — componentes y utilidades pequeñas con nombres claros, separación limpia por capas (`api/`, `lib/`, `store/`, `hooks/`, `components/`, `pages/`), textos de UI en español, hooks de datos con estado `{datos, cargando, error, recargar}` uniforme; la duplicación del manejo de errores de formulario entre secciones es mínima y aceptable a esta escala |
| Security | OK — token adjuntado solo a la base relativa `/api/v1` (BR-009); nunca logueado (sin `console.*` en `src/` — AC-031); `autenticar: false` solo en login, por lo que un 401 de login (ERR-001) nunca limpia la sesión (ERR-002) — distinción correcta en `httpClient.ts`; el decode del JWT es UX-only (routing/visibilidad), nunca se usa como enforcement (A-002/BR-008); 401 en requests autenticadas → `onNoAutorizado` → limpiar sesión + redirección (FR-007); sin `dangerouslySetInnerHTML`; riesgo XSS de `localStorage` aceptado y documentado (A-003/ADR-008); sin secretos commiteados |
| Performance | OK — sin N+1: movimientos cargados lazy por cuenta expandida; sin refetch storms; guards de doble submit (ref + flag `enviando`, botones deshabilitados) |
| Correctness & error handling | OK — parsing del envelope `{code, message, details?}` con fallback 5xx; `ERROR_RED` para errores de red con reintento (ERR-008); `400` con `details` por campo (ERR-004); `422` conserva los datos ingresados (ERR-006); estados de carga/error/vacío en toda vista (FR-015); formulario de transferencia con payload exacto `{cuentaOrigenId, cbuDestino, monto}` |
| Conventions (TS estricto, ESLint, tipos espejo, español) | OK — `tsconfig` con `strict: true` + `noUnusedLocals/Parameters`; ESLint flat config correcto; tipos TS espejo 1:1 de los DTOs del backend (verificado contra `infrastructure/adapter/web`); montos solo formateados con `Intl es-AR` (BR-010, sin cálculo client-side); `formatearMontoARS` normaliza NBSP para tests/detalle |
| Test quality | OK — 111 tests en 14 archivos, aserciones de payload exacto, fixtures de envelopes realistas, mocks de `fetch` con `vi.stubGlobal`, POST→GET stateful para el refresh (AC-025), deferred-fetch para el estado de carga (AC-030), limpieza de `localStorage`/mocks entre tests (`vitest.setup.ts`) |
| Dead code / duplication | OK — sin código sin usar, sin TODO/FIXME, sin scaffolding residual (`.gitkeep` × 5 eliminados); duplicación de manejo de errores de formulario aceptable y localizada |
| Scope | OK — solo `frontend/` + docs de spec/arquitectura/ADR/reviews; cero cambios en `backend/`; dependencias exactamente las justificadas en diseño §5.7 (sin axios/Redux/MUI/MSW) |
| CI gate (`npm run typecheck` / `lint` / `test` / `build`) | OK — verificados: `typecheck` PASS, `lint` PASS, `test` 111/111 PASS, `build` PASS (bundle en `dist/`) |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **Rangos `^` en `package.json`** (diseño §5.7/§5.8 pedía versiones pinneadas):
  - Ubicación: `frontend/package.json`.
  - Problema: las dependencias usan caret ranges (p. ej. `"react": "^18.3.1"`). El caret previene drift de majors y `package-lock.json` fija el árbol exacto; no hay impacto de comportamiento. Alineación con el texto del diseño, no con un requerimiento de la spec.
  - Recomendación: opcional — pin exacto o aceptar `^` + `package-lock.json` (documentado en el PR). No bloquea.

- **Ausencia de README en `frontend/`** (riesgo §9 del diseño: documentar requisito Node 18+/20+):
  - Ubicación: `frontend/` (sin README).
  - Problema: el requisito de Node para Vite 5 no está documentado en el repositorio del frontend.
  - Recomendación: documentar en el PR (o agregar un README breve en una limpieza futura). No bloquea.

### Nit

- **Mensaje genérico del login ante 5xx:** `LoginPage.tsx:36-43` muestra "Usuario o contraseña incorrectos" para cualquier `ApiError` no-`ERROR_RED` (incluidos 5xx) en lugar del `message` del envelope. La spec exige mensaje genérico solo para 401 (ERR-001, A-004); para 5xx es una decisión de UX defendible y consistente. Sin acción requerida.
- **`formatearFecha` en hora local:** `src/lib/session.ts` formatea los `Instant` ISO-8601 del backend en la zona local del navegador (solo display). Sin impacto en requerimientos.
- **Warnings de future-flags de React Router v6** en stderr de los tests: informativos (preparación de v7); el router está pinneado a v6 por decisión del diseño (§9). Sin acción requerida.

## Verification

Revisión estática completa de todos los archivos de `frontend/` (configs, `src/`, 14 archivos de test + `src/test/helpers.tsx`) y de los contratos del backend consumidos (`infrastructure/adapter/web` + `JwtService`). Se verificó además la revisión de cumplimiento (`docs/reviews/SPEC-006-review.md`, PASS) y sus hallazgos menores (confirmados no bloqueantes).

1. **Seguridad** — `httpClient.ts`: el token se adjunta solo con base relativa `/api/v1` y solo con sesión; `autenticar: false` únicamente en `auth.login()`; `401` en requests autenticadas → callback `onNoAutorizado` registrado por `AuthProvider` (limpia sesión + redirección); `401` de login → `ApiError` normal (AF-001 intacto). `jwt.ts`: decode base64url sin verificación de firma, validación de forma (`role ∈ {CLIENTE, ADMIN}`, `clienteId` solo CLIENTE), `null` ante payload malformado. Sin `dangerouslySetInnerHTML`, sin `console.*` en `src/`. Sin secretos en el diff (`.gitignore` cubre `node_modules/`, `dist/`, `*.env`).
2. **Correctitud** — envelope parseado con `details?` opcional; red → `ERROR_RED` con reintento (ERR-008); formularios conservan datos ante 422/400 (ERR-004/006); estados de carga/error/vacío en todas las vistas (FR-015); doble submit bloqueado (ref + flag, botones deshabilitados).
3. **Tipos/DTOs** — espejo 1:1 verificado: `LoginRequest{username,password}`, `LoginResponse{token}`, `ClienteDto` (`telefono?`), `CuentaDto{id,clienteId,cbu,tipo,saldo,moneda,estado,createdAt}`, `MovimientoDto` (`cuentaContraparteId?`), `TransferirRequest{cuentaOrigenId,cbuDestino,monto}`, `AbrirCuentaRequest{clienteId,tipo,moneda?}`, `CrearClienteRequest`/`ActualizarClienteRequest`, `TransferenciaConfirmacion{idTransferencia,monto,cbuDestino,fechaHora}`, `ErrorEnvelope{code,message,details?}` + `DetalleError{campo,mensaje}`.
4. **Testing** — 111 `it()` en 14 archivos (coincide con el conteo de la revisión de cumplimiento): `jwt` 10, `session` 8, `validacion` 23, `httpClient` 12, `auth-context` 5, `App` 7, `ProtectedRoute` 5, `Layout` 3, `LoginPage` 5, `CuentasPage` 7, `TransferenciaPage` 8, `GestionPage` 4, `ClientesSection` 7, `CuentasSection` 7. Mocks con fixtures de envelopes realistas; `vi.stubGlobal('fetch')`; limpieza entre tests en `vitest.setup.ts`.
5. **Configuración** — `vite.config.ts` con proxy `/api` → `http://localhost:8080` sin rewrite + bloque Vitest (jsdom, setupFiles, globals); `tsconfig` estricto con `moduleResolution: bundler` y `tsconfig.node.json`; ESLint flat config correcto (typescript-eslint + react-hooks + react-refresh solo en `src/`); `package.json` con scripts `dev/build/lint/typecheck/test/preview`.
6. **Scope** — `git diff testing..HEAD --stat` (ejecutado por el orchestrator): solo archivos de `docs/` (spec, arquitectura, ADR-008, sprints, review) y `frontend/`; cero referencias a SPEC-006 en `backend/`.

### Tests / comandos (ejecutados por el orchestrator en `frontend/`)

- `npm run typecheck` — **PASS** (`tsc --noEmit`, sin errores).
- `npm run lint` — **PASS** (`eslint .`, sin errores ni warnings).
- `npm test` — **PASS** — 14 archivos / **111 tests** pasando (solo warnings informativos de future-flags de React Router v6).
- `npm run build` — **PASS** — `tsc -b && vite build`; 60 módulos; `dist/` (JS 184.17 kB / gzip 59.30 kB, CSS 2.21 kB).

## Result

**APPROVE.** La implementación es de buena calidad y consistente con el estándar del repo: TypeScript estricto con ESLint limpio, capas bien separadas (`api/`, `lib/`, `store/`, `hooks/`, `components/`, `pages/`), tipos espejo 1:1 de los DTOs del backend, cliente HTTP con Bearer token acotado a `/api/v1` (BR-009) y distinción correcta entre 401 de login y 401 de sesión (AF-001 vs AF-002/FR-007), decode del JWT solo para UX (A-002) con el backend como punto de enforcement (BR-008), manejo completo del envelope de errores (400 por campo, 403/404/409/422, red con reintento), estados de carga/error/vacío y guards de doble submit en todos los flujos, y cobertura de tests amplia y significativa (111 tests: payloads exactos, fixtures realistas, casos de 401/403/409/422, estados vacíos y de carga, refresh post-transferencia/apertura). Dependencias mínimas y justificadas (sin axios/Redux/MUI/MSW — diseño §5.7), cero cambios de backend y sin secretos en el diff. Los hallazgos son menores/nits documentados (rango `^` vs texto del diseño, ausencia de README, mensaje genérico del login ante 5xx, formato local de fechas, warnings de future-flags) y no afectan corrección, seguridad ni mantenibilidad. La decisión final de merge es de este revisor (gate: APPROVE) y la ejecución del merge la realiza el orchestrator.
