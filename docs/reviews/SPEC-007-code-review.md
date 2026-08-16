# Review Report — SPEC-007

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Readability & maintainability | OK — sección autocontenida siguiendo el patrón de `CuentasSection`; componentes privados pequeños con nombres claros; textos de UI en español; comentarios referenciando FR/BR/AC/SPEC consistentes con el repo; sin dead code ni over-abstracción |
| Security | OK — token Bearer adjuntado por `httpClient` (`autenticar` por defecto) acotado a `/api/v1`; autorización real server-side (la sección solo se monta en `/gestion` ADMIN — UX); sin `dangerouslySetInnerHTML` (React escapa); `monto` parseado con `Number()` solo tras validar con `REGEX_MONTO`; sin secretos en el diff |
| Performance | OK — sin re-render storms; `useEffect([error])` mapea una vez por error; el refresh (`recargar()`) se dispara solo tras éxito (`resultado !== null`); doble `GET /api/v1/clientes` en `/gestion` es una request trivial extra y consistente con el patrón existente (A-006, documentado en diseño §12) |
| Correctness & error handling | OK — parsing del envelope `{code, message, details?}` con whitelist `CAMPOS_CAJA` + fallback general (patrón `TransferenciaPage`); datos conservados ante errores; confirmación con CBU del snapshot local (`cuentaOperada`) estable tras refresh (AF-004); doble envío protegido (ref `enVuelo` + `enviando`); refresh sin remount preserva el panel de confirmación |
| Conventions | OK — capas respetadas (`api/`, `lib/`, `hooks/`, `pages/gestion/`); módulo por recurso (`caja.ts`); validación pura en `lib/validacion.ts`; hook de mutación espejo de `useTransferencia`; reuso de componentes (`CampoFormulario`, `Monto`, `Cargando`, `EstadoError`, `EstadoVacio`) y clases CSS planas existentes (A-004, sin redesign); tipos TS espejo de los records del backend |
| Test quality | OK — 9 `it`/`it.each` en `CajaSection.test.tsx` + unit de validación: happy path (depósito/retiro con payload exacto y refresh del saldo), pre-validaciones (monto, saldo, cuenta), errores del envelope (400 por campo, 422/404/409/403, red), doble envío y estados vacíos; asserts genuinos (sin passes vacíos); AC-011 ejercita de verdad la rama `details→por campo` (verificado en re-review de cumplimiento) |
| Dead code / duplication | OK — la única duplicación es `CuentaItem` (~10 líneas, idéntico a `CuentasSection`); documentada en arquitectura §8.8/§13 como decisión deliberada por scope control (AGENTS.md §14). Aceptable a esta escala |
| Scope | OK — solo `frontend/` + docs; cero cambios en `backend/`, migraciones, `docker/`, CSS ni dependencias npm (AC-020); `GestionPage.test.tsx` acotado sin debilitar criterios (AGENTS.md §12) |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **`CuentaItem` duplicado en `CajaSection`** (`CajaSection.tsx:103-115`) vs `CuentasSection.tsx:152-164` — implementación idéntica.
  - Problema: duplicación literal de un componente privado.
  - Justificación: documentada en arquitectura §8.8/§13 — extraer un componente compartido implicaría refactorizar `CuentasSection` y sus tests (fuera de alcance, AGENTS.md §14). Es ~10 líneas de JSX simple.
  - Recomendación: aceptar por ahora; consolidar en una limpieza futura si aparece un tercer uso. No bloquea.

- **Drift de wording de la validación vs la prosa de la arquitectura** (`validacion.ts:131` y `:182`):
  - `validarCuentaCaja` retorna `'Seleccione la cuenta'` y `validarRetiro` retorna `'El saldo no es suficiente para el retiro'`, mientras la arquitectura §5.3/§8.3 cita `'Seleccione una cuenta'` y `'El monto no puede superar el saldo de la cuenta'`.
  - Problema: la spec BR-002/BR-003 no fija el texto exacto; la implementación y sus tests son internamente consistentes. Solo drift de documentación.
  - Recomendación: alinear la prosa de la arquitectura con el código (o viceversa) a un único string en una pasada de limpieza. No bloquea.

### Nit

- **AC-018 no ejercita la guardia del ref `enVuelo` de `useCaja`** (`CajaSection.test.tsx:424-452`): el segundo `click` cae sobre el botón ya `disabled`, por lo que nunca se dispara `onSubmit`/`ejecutar` de nuevo; el assert de "una sola request" verifica el camino botón-deshabilitado, no el ref defensivo. Es consistente con el precedente `useTransferencia` (misma guardia, mismo patrón de test) y el AC-018 (botón deshabilitado + sin request duplicada) queda satisfecho. Sin acción requerida.

- **Labels `"Monto depósito"`/`"Monto retiro"` vs `"Monto"` de la arquitectura** (§8.5): decisión razonable y deliberada que resuelve la ambigüedad de labels duplicados de la spec §11; todos los tests usan los labels reales. Sin acción requerida.

- **Sin cuentas (`datos.length === 0`), la sección retorna temprano sin renderizar los formularios** (`CajaSection.tsx:84-86`), mientras la prosa de la arquitectura §5.5 dice "formularios siempre renderizados". El comportamiento real es idéntico al precedente de `CuentasDeCliente` (`CuentasSection.tsx:140-142`) y es una UX correcta (nada que operar); AC-003 queda cubierto para el caso "sin ACTIVA". Solo drift de documentación. Sin acción requerida.

## Verification

Revisión estática completa (read-only) de:

- **Spec:** `docs/specs/SPEC-007-caja-gestion.md` (FR-001..007, BR-001..005, AF-001..004, ERR-001..007, AC-001..020, A-001..A-004).
- **Arquitectura:** `docs/architecture/SPEC-007.md` (completa, incl. §5.2..5.5, §8.2..8.7, §13 alternativas, §14 decisión).
- **Revisión de cumplimiento:** `docs/reviews/SPEC-007-review.md` (PASS; hallazgo Major de AC-011 resuelto en round 1).
- **Implementación:**
  - `frontend/src/api/caja.ts` — `depositar`/`retirar` → `POST /api/v1/depositos`/`/retiros` con `{cuentaId, monto}` (A-003, §5.2).
  - `frontend/src/api/types.ts` — `DepositoRequest`, `RetiroRequest`, `DepositoConfirmacion`, `RetiroConfirmacion` espejo de los records del backend.
  - `frontend/src/lib/validacion.ts` — `validarDeposito`/`validarRetiro` con helpers privados compartidos reutilizando `REGEX_MONTO` (BR-001..003).
  - `frontend/src/hooks/useCaja.ts` — `useCaja(tipo)` con doble envío (ref `enVuelo` + `enviando`), una instancia por formulario.
  - `frontend/src/pages/gestion/CajaSection.tsx` — selector de cliente, `CajaDeCliente` (listado + `recargar`), dos `FormularioCaja`, `ConfirmacionCaja` (`role="status"`), mapeo del envelope (`details`→campo / general), snapshot CBU, `sinCuentas` disable (AF-001).
  - `frontend/src/pages/GestionPage.tsx` — `<CajaSection />` como tercera sección.
  - `frontend/src/pages/GestionPage.test.tsx` — queries acotadas (`within(heading 'Cuentas')`, `findAllByText` para "Pérez, Juan", eliminación del proxy `'Monto'` de AC-021) sin debilitar criterios.
  - Tests: `CajaSection.test.tsx` (AC-001..AC-018) y `validacion.test.ts` (AC-010).
- **Precedentes de convención:** `CuentasSection.tsx`, `useTransferencia.ts`, `httpClient.ts`, `CampoFormulario.tsx`, `src/test/helpers.tsx`, `vite.config.ts`, `package.json`.

### Nota de tooling

Este entorno no expone shell (`npm`/`git`), por lo que **no pude ejecutar** `npm test`, `npm run lint`, `npm run typecheck` ni `npm run build`, ni `git diff` (misma limitación que la revisión de cumplimiento). La verificación es estática:

- **Typecheck** — verificado por inspección: `globals: true` en `vite.config.ts` habilita `vi` global (usado sin import en `mockFetchCajaDiferido`); imports de `formatearMontoARS`/`formatearFecha` desde `lib/session.ts` existen; tipos de la unión `ConfirmacionCaja` y `ErroresPorCampo` correctos; `it.each` con firma alineada a la tupla.
- **Lint** — código consistente con el estilo ESLint del repo (sin patrones obvios de infracción).
- **Tests** — asserts genuinos verificados uno a uno (ver Findings/Summary); la revisión de cumplimiento reportó la suite completa en 136 tests pasando según el desarrollador.
- **Build** — JSX/TS estáticamente válido; sin imports circulares ni de módulos inexistentes.

Mapping de convenciones verificado:

- Seguridad: token vía `httpClient` (Bearer, base `/api/v1`); sin secrets; XSS mitigado por React; `monto` parseado tras `REGEX_MONTO`.
- Conventions: módulo por recurso en `api/`; validación pura en `lib/`; hook de mutación espejo de `useTransferencia`; componentes UI reutilizados; CSS plano sin cambios (A-004).
- Testing: happy path + validaciones + errores del envelope + doble envío + estados vacíos; payloads exactos (`toEqual({cuentaId, monto})`); AC-011 ejercita la rama `details→por campo` con mensaje server-only; refresh post-`201` leído del `CuentaDto` refrescado (BR-004, AC-006).

## Result

**APPROVE.**

La implementación de SPEC-007 es de buena calidad y consistente con los estándares del repo: capas respetadas (`api/`, `lib/`, `hooks/`, `pages/gestion/`), tipos TS espejo de los records del backend, validaciones de UX puras en `lib/validacion.ts` (BR-001..003 como mirrors), hook de mutación `useCaja(tipo)` que replica `useTransferencia` con protección de doble envío, manejo completo del envelope (`details`→por campo con whitelist, resto→general, datos conservados, reintento), refresh post-`201` sin remount para preservar la confirmación y con el saldo leído del `CuentaDto` refrescado (BR-004), y cobertura de tests amplia y significativa (payloads exactos, validaciones, errores 400/422/404/409/403/red, doble envío, estados vacíos). La única duplicación (`CuentaItem`, ~10 líneas) está justificada y documentada en la arquitectura por scope control. Los hallazgos son menores/nits (duplicación documentada, drift de wording de mensajes frente a la prosa de la arquitectura, y tres nits de comportamiento/test sin impacto en corrección, seguridad ni mantenibilidad) y no impiden el merge.

La decisión de merge (gate: APPROVE) es de este revisor; la ejecución del merge la realiza el orchestrator. Nota: las comprobaciones de CI (`lint`/`typecheck`/`test`/`build`) no pudieron ejecutarse en este entorno y deben confirmarse al ejecutar el gate (AC-019); la revisión de cumplimiento reportó la suite completa en 136 tests según el desarrollador.
