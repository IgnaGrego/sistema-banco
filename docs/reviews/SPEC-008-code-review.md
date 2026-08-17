# Review Report — SPEC-008

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-17
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Readability & maintainability | OK — `registro()` espejo de `login()` en `auth.ts` (módulo por recurso, A-002); `validarRegistroUsuario` + helpers privados siguen el patrón `validarDeposito`/`validarRetiro`; `useRegistroUsuario` replica `useCaja`/`useTransferencia`; `UsuariosSection` autocontenida siguiendo `CajaSection` con componentes privados pequeños (`ConfirmacionUsuario`) y nombres claros; JSDoc en español referenciando FR/BR/AC/SPEC consistente con el repo; IDs únicos (`usuario-*`); sin dead code ni over-abstracción |
| Security | OK — campo `type="password"`; el panel de confirmación muestra solo `username` + `rol` del `UsuarioDto` (nunca la password, A-005; assert AC-003: `queryByText(/12345678/)` ausente); `registro()` declara `autenticar: false` → `httpClient` no adjunta header `Authorization` a un endpoint público `permitAll` (assert AC-003: `init.headers` sin `Authorization`); sin secretos en el diff; XSS mitigado por React (sin `dangerouslySetInnerHTML`; username del envelope/`UsuarioDto` renderizado escapado) |
| Performance | OK — sin re-render storms; `useEffect([error])` mapea una vez por error; doble envío protegido (ref `enVuelo` + `enviando`); el cuarto `GET /api/v1/clientes` en `/gestion` (`useClientes` incondicional) es una request trivial y consistente con el patrón sin caché de las secciones hermanas (A-006 de SPEC-006); arquitectura §12 lo documenta (ver Minor) |
| Correctness & error handling | OK — mapeo del envelope con whitelist `CAMPOS_REGISTRO` {username, password, rol, clienteId} + fallback general (patrón `CajaSection` §8.6); datos conservados ante error (solo se resetean tras 201); pre-validación BR-001..BR-004 bloquea el envío sin request; payload exacto `{username: username.trim(), password, rol, clienteId: rol === 'CLIENTE' ? clienteId : null}`; `clienteId` limpio al volver a `ADMIN` (A-003); boundary password=8 y username=50 correctos; reintento tras 404/500/red verificado |
| Conventions | OK — capas respetadas (`api/`, `lib/`, `hooks/`, `pages/gestion/`); validación pura en `lib/validacion.ts` (sin reglas de negocio nuevas en el componente — BR-005); tipos TS espejo de los records del backend (`RegistrarUsuarioRequest`, `UsuarioDto`, reuso de `Rol`); reuso de `CampoFormulario`/`Cargando`/`EstadoError`/`EstadoVacio`; CSS plano sin cambios; backend y `package.json` intactos |
| Test quality | OK — 14 component tests + 8 unit tests (AC-008) con asserts genuinos: payloads exactos con `toEqual` (CLIENTE con `clienteId:1`, ADMIN con `clienteId:null`), ausencia de `Authorization`, no-request en error de validación (`not.toHaveBeenCalledWith('/api/v1/auth/register', ...)`), errores por campo acotados con `within(campo)` vs general (400 details vs 404/500/red), reintentos (409/500/red), doble envío con mock diferido (botón deshabilitado + una sola request), password nunca en pantalla, estados vacío/cargando del selector |
| Dead code / duplication | OK — la única duplicación es el `useEffect` de mapeo del envelope (~20 líneas, patrón de `CajaSection`/`TransferenciaPage`); es el patrón establecido del repo y consolidarlo implicaría tocar secciones existentes (scope control — AGENTS.md §14). Aceptable |
| Scope | OK — frontend-only + docs (git log: 1 commit de docs + 4 de frontend); cero cambios en `backend/`, migraciones, `docker/`, CSS ni dependencias npm (AC-016, `package.json` intacto); `GestionPage.test.tsx` acotado (`findAllByText('Pérez, Juan')` ≥ 1, `within(heading 'Cuentas')` para el label "Cliente", username etiquetado "Usuario" sin colisión con "Nombre") sin debilitar criterios (AGENTS.md §12) |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **`useClientes()` consumido incondicionalmente** (`UsuariosSection.tsx:28-33`): la sección dispara `GET /api/v1/clientes` al montar incluso con `rol = ADMIN` (selector oculto), sumando un cuarto consumidor del listado en `/gestion`.
  - Problema: una request trivial extra por montaje (patrón sin caché de SPEC-006 A-006).
  - Justificación: la arquitectura §12 la enmarca como "Alternativa recomendada" (recomendación, no requisito) y §5.5 muestra consumo incondicional; el `useClientes` compartido no admite un flag de habilitación sin tocar un hook común (fuera de alcance). El consumo incondicional además evita el flash de carga al cambiar a `CLIENTE`.
  - Recomendación: si en el futuro se optimiza, consumir `useClientes()` solo cuando `rol === 'CLIENTE'` (subcomponente montado condicionalmente o flag en el hook). No bloquea; la implementación coincide con el diseño aprobado y el precedente `CajaSection`.

### Nit

- **AC-014 no ejercita directamente la guardia del ref `enVuelo`** (`UsuariosSection.test.tsx:373-394`): el segundo `click` cae sobre el botón ya `disabled`, por lo que `onSubmit`/`ejecutar` nunca se dispara de nuevo; el assert de "una sola request" verifica el camino botón-deshabilitado, no el ref defensivo. Es el mismo precedente que AC-018 de SPEC-007 y el patrón `useTransferencia`; AC-014 (botón deshabilitado + sin request duplicada) queda satisfecho. Sin acción requerida.

- **`useEffect` de mapeo del envelope duplicado** (`UsuariosSection.tsx:48-68` vs `CajaSection.tsx:144-164` y `TransferenciaPage`): ~20 líneas del patrón whitelist + fallback general repetidas en cada sección. Es el patrón establecido del repo; extraer un helper compartido tocaría secciones existentes y sus tests (scope control). Consolidar en una limpieza futura si aparece un cuarto uso. No bloquea.

- **Con `details` de campo se muestra también el `message` general del envelope** (AC-009 lo assert: error por campo en `password` + `getByText('Datos inválidos')`): la prosa de la spec FR-004 lee "con details → por campo; sin details → general", pero el diseño detallado aprobado (§8.6, `let mensajeGeneral = error.message`) y el precedente `CajaSection` renderizan ambos. La implementación coincide con el diseño y el precedente. Solo matiz de redacción de la spec. Sin acción requerida.

## Verification

Revisión estática completa (read-only) de:

- **Spec:** `docs/specs/SPEC-008-usuarios-gestion.md` (FR-001..005, BR-001..005, ERR-001..005, AC-001..016, A-001..A-006, §10/§12 alcance).
- **Arquitectura:** `docs/architecture/SPEC-008.md` (§3 módulos afectados, §5.2 capa API, §5.3 validación, §5.4 hook, §5.5 sección, §8.2 contrato verificado contra código, §8.3 mensajes, §8.4 contrato del hook, §8.5 estados, §8.6 mapeo del envelope, §8.7 scoping de `GestionPage.test.tsx`, §12 riesgos, §13 alternativas).
- **Revisión de cumplimiento:** `docs/reviews/SPEC-008-review.md` (PASS; verifica el contrato backend `RegistrarUsuarioRequest`/`UsuarioDto`/`RegistroValidator`/`GlobalExceptionHandler`/`SecurityConfig permitAll`).
- **Implementación:**
  - `frontend/src/api/auth.ts` — `registro(body)` → `POST /auth/register` con `autenticar: false` (A-002); espejo de `login()`.
  - `frontend/src/api/types.ts` — `RegistrarUsuarioRequest {username, password, rol: Rol, clienteId: number | null}` y `UsuarioDto {id, username, rol}` (espejos exactos de los records del backend; nunca password).
  - `frontend/src/lib/validacion.ts` — `validarRegistroUsuario` + `validarUsernameRegistro`/`validarPasswordRegistro`/`validarRolRegistro`/`validarClienteRegistro` (BR-001..004); unicidad deliberadamente no pre-validada (A-004); boundary password=8 válido; `validarPasswordRegistro` sin trim (regla exacta del backend).
  - `frontend/src/hooks/useRegistroUsuario.ts` — `{ enviando, confirmacion, error, ejecutar, limpiarConfirmacion }` con guardia `enVuelo` + `enviando` (doble envío); `limpiarConfirmacion` sancionado por arquitectura §5.5.
  - `frontend/src/pages/gestion/UsuariosSection.tsx` — formulario con `rol` default `'ADMIN'` (arquitectura §8.5), selector de cliente solo con `rol = CLIENTE` (A-003), `clienteId` limpiado al volver a `ADMIN`, payload exacto con `username.trim()` y `clienteId: null` para `ADMIN`, `ConfirmacionUsuario` (`role="status"`, username + rol, nunca password), mapeo del envelope con `CAMPOS_REGISTRO` + fallback general, datos conservados en error, reset tras 201 conservando el rol (§4.2), `maxLength={50}`, labels "Usuario"/"Contraseña"/"Rol"/"Cliente" con IDs únicos.
  - `frontend/src/pages/GestionPage.tsx` — `<UsuariosSection />` como cuarta sección (FR-001).
  - `frontend/src/App.tsx` — sin cambios; `/gestion` sigue bajo `ProtectedRoute rolPermitido="ADMIN"`; sin rutas nuevas.
  - `frontend/src/api/httpClient.ts` — sin cambios; header `Authorization` solo con `autenticar` → `autenticar: false` = sin header (AC-003).
- **Tests:**
  - `frontend/src/pages/gestion/UsuariosSection.test.tsx` — 14 `it`: AC-001/002 (render + selector por rol + ocultamiento/limpieza), AC-003 (payload `toEqual({username, password, rol:'CLIENTE', clienteId:1})`, sin `Authorization`, confirmación sin password, reset), AC-004 (payload con `clienteId: null`), AC-005 (username vacío/51 → error, sin request), AC-006 (7 bloquea / 8 envía), AC-007 (CLIENTE sin cliente → error, sin request), AC-009 (400 details `password` → error por campo vía `within(campo)` + general, datos conservados, request enviada), AC-010 (409 `username` → por campo, datos conservados, reintento → éxito), AC-011 (404 → general), AC-012 (500 → general, reintento → éxito), AC-013 (`ERROR_RED`, reintento → éxito), AC-014 (botón deshabilitado "Registrando...", una sola request con mock diferido), + estados vacío y cargando del selector. Asserts genuinos en todos los casos.
  - `frontend/src/lib/validacion.test.ts` — describe `validarRegistroUsuario` (AC-008): CLIENTE válido (boundary 8), ADMIN null, ADMIN con cliente, username vacío/espacios/>50 + boundary 50, password <8, rol vacío/inválido (`GERENTE as never`), CLIENTE sin clienteId.
  - `frontend/src/pages/GestionPage.test.tsx` — queries acotadas (§8.7): `findAllByText('Pérez, Juan')` con `length >= 1`, `within(heading 'Cuentas')` para el label "Cliente", `getAllByLabelText('Nombre')[0]` intacto (el campo nuevo se etiqueta "Usuario"); criterios AC-028/AC-021 preservados.
- **Config/alcance:** `frontend/package.json` — sin dependencias nuevas (AC-016); `vite.config.ts` (`globals: true`, jsdom, setup) y `tsconfig.json` (`vitest/globals`) consistentes con los tests nuevos; `.git/logs/refs/heads/feature/spec-008` — solo commits de docs + frontend (sin backend/migraciones/docker); working tree limpio.

### Nota de tooling

Este entorno no expone shell (`npm`/`git`), por lo que **no pude ejecutar** `npm run lint`, `npm run typecheck`, `npm test` ni `npm run build` (misma limitación que las revisiones previas de SPEC-007). La verificación es estática:

- **Typecheck** — por inspección: firmas alineadas (`validarRegistroUsuario(username, password, rol, clienteId)` vs llamada en la sección con `rol: Rol | ''` y `clienteId: number | null`); `ejecutar({...})` coincide con `RegistrarUsuarioRequest`; `request<UsuarioDto>` → `registro()` → `Promise<UsuarioDto>`; `limpiarConfirmacion` expuesto y consumido; `vi` global habilitado (`globals: true`); imports de `Rol`/`UsuarioDto`/`ErroresPorCampo`/`FormEvent` existentes; `rol as Rol` cast seguro tras BR-003 (comentado).
- **Lint** — sin imports sin usar en ninguno de los archivos nuevos/modificados; `react-refresh` respeta la regla (solo export del componente; `ConfirmacionUsuario` y `CAMPOS_REGISTRO` son privados); estilo consistente con el repo.
- **Tests** — asserts verificados uno a uno (ver Summary/Findings); los helpers (`mockFetchRespuestas`, `mockFetchErrorRed`, stub diferido propio) son coherentes con el setup global que limpia `fetch`/`localStorage` entre tests (`vitest.setup.ts`).
- **Build** — JSX/TS estáticamente válido; sin imports circulares ni de módulos inexistentes.

Las cuatro comprobaciones deben confirmarse en verde al ejecutar el gate (AC-015); la revisión de cumplimiento reportó la suite completa en verde según el desarrollador.

## Result

**APPROVE.**

La implementación de SPEC-008 es de buena calidad y consistente con los estándares del repo: capas respetadas (`api/`, `lib/`, `hooks/`, `pages/gestion/`), módulo por recurso (`registro()` en `auth.ts`, A-002), tipos TS espejo de los records del backend (nunca password), pre-validaciones de UX puras en `lib/validacion.ts` (BR-001..004, unicidad delegada al 409 — A-004), hook de mutación que replica `useCaja`/`useTransferencia` con protección de doble envío, manejo completo del envelope (`details` → por campo con whitelist, resto → general, datos conservados, reintento), confirmación con `username` + `rol` sin password (A-005), y cobertura de tests amplia y significativa (14 component tests + 8 unit tests con payloads exactos, no-request en validación, scoping por campo, reintentos y doble envío). Las tres desviaciones documentadas (`limpiarConfirmacion` extra, `clienteId: number | null` siempre en el payload, `rol` default `'ADMIN'`) están sancionadas por la arquitectura (§5.5, A-003, §8.5). Sin dependencias nuevas, sin cambios de backend/migraciones/docker, y `GestionPage.test.tsx` acotado sin debilitar criterios. Los hallazgos son menores/nits (consumo incondicional de `useClientes` documentado en arquitectura §12 como recomendación, y tres nits de patrón/test sin impacto en corrección, seguridad ni mantenibilidad) y no impiden el merge.

La decisión de merge (gate: APPROVE) es de este revisor; la ejecución del merge la realiza el orchestrator. Nota: las comprobaciones de CI (`lint`/`typecheck`/`test`/`build`) no pudieron ejecutarse en este entorno y deben confirmarse al ejecutar el gate (AC-015).