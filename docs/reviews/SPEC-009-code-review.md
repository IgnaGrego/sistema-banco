# Review Report — SPEC-009

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-17
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Readability & maintainability | OK — `Layout` mínimo y claro (marca, nav por rol, chip de usuario, logout); CSS reorganizado en bloques comentados (1. Tokens, 2. Base, 3. Layout, 4. Componentes, 5. Estados, 6. Páginas, 7. Responsive) con tokens semánticos kebab-case en español; comentarios en español referenciando FR/BR/AC/SPEC consistentes con el repo; sin dead code ni TODO/FIXME; cada clase CSS nueva tiene regla definida y viceversa (verificación cruzada TSX↔CSS sin huérfanas) |
| Security | OK — lectura del claim `sub` del JWT solo para UX (A-005, ADR-008): `decodificarJwt` no verifica firma (el backend sigue siendo el punto de enforcement server-side — AGENTS.md §17, BR-001); `username` opcional y renderizado solo si el token trae `sub` string (fallback R2); sin `dangerouslySetInnerHTML`, sin `console.*`, sin secretos; autorización por rol intacta (`ProtectedRoute`, rutas sin cambios) |
| Performance | OK — rediseño presentacional sin trabajo asíncrono nuevo (sin hooks ni llamadas a la API modificados); CSS custom properties resueltas por el navegador sin coste relevante; sin layout thrash (media queries declarativas); sin N+1 (no aplica: presentación pura) |
| Conventions | OK — tokens `:root` de `src/index.css` coinciden **exactamente** con `docs/design/ui-guide.md` (AC-002); sin valores de color literales fuera de `:root` (BR-002, AC-003 verificado con grep); media queries mobile-first `min-width` 640/768/1024 (A-006, AC-011) con unidades `rem`; `:focus-visible` global en inputs/selects/botones/links (BR-007, AC-012); props de componentes y roles ARIA intactos (A-008, BR-006); textos asertados por los tests intactos (BR-003) |
| Test quality | OK — `Layout.test.tsx` extendido aditivamente (3 casos AC-004 nuevos + fallback R2; los 3 casos existentes AC-012/AC-021 intactos); `Estados.test.tsx` nuevo (4 casos AC-010 con asserts genuinos de roles ARIA y textos); `jwt.test.ts` **aditivo** (verificado: las 2 aserciones `toEqual` de `crearToken` ganan `username: 'usuario-test'` — ajuste prescripto por arquitectura §5.8/§8.1 — y se agregan 2 casos A-005; las 10 aserciones de payload inválido/null existentes se conservan sin debilitarse); aserciones críticas preservadas: `getAllByText('ARS')` → `toHaveLength(2)`, `DEPOSITO`/`TRANSFERENCIA_SALIENTE`/`BLOQUEADA` en crudo, `findByRole('status')`/`role="alert"` |
| Dead code / duplication | OK — sin estilos huérfanos (cada clase en TSX tiene regla CSS y toda regla nueva tiene consumo); el `CuentaItem` duplicado entre `CuentasSection`/`CajaSection` es precedente documentado de SPEC-007 (scope control — AGENTS.md §14); sin componentes sin usar |
| Scope | OK — frontend-only + `docs/design/ui-guide.md` (entregable FR-001) + reviews; `package.json`/`package-lock.json` intactos (BR-005, AC-015); `App.tsx`/`ProtectedRoute` intactos (BR-004, AC-017); `src/api/*`, `src/hooks/*`, `src/lib/session.ts`, `src/lib/validacion.ts` intactos; cero cambios en `backend/`, migraciones, `docker/` (BR-001, AC-016); working tree = HEAD de `feature/spec-009-ui-redesign` (7 commits sobre `origin/testing`) |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **Nombre de la clase de la marca `marca` vs `layout-marca` propuesto** (`Layout.tsx:25` + `index.css:129`):
  - La arquitectura §5.2 proponía `className="layout-marca"`; la implementación usa `marca`. La especificación (FR-003, A-009) solo exige texto estilizado con tokens, no heading ni link, una sola vez por árbol: la clase es un detalle de implementación y el nombre elegido es claro y sin colisiones.
  - Impacto: ninguno en tests (RTL es class-agnostic) ni en contratos.
  - Recomendación: opcional — alinear el nombre en una limpieza futura. No bloquea.

- **Conteo de tests 160 estático vs 167 esperado** (spec §11 AC-014 cita "158 existentes + nuevos"):
  - Conteo estático por inspección de los 17 archivos de test: 160 casos (`it` + 5 casos de `it.each` de `CajaSection.test.tsx`); la instrucción de gate espera 17 archivos / 167 tests.
  - Posible explicación: el "158" de la prosa de la spec era una estimación del analista; la base real en `origin/testing` puede diferir. No hay indicios de tests faltantes: los 3 casos nuevos de Layout, los 4 de Estados y los 2 aditivos de jwt están presentes y son los esperados por AC-004/AC-010/A-005.
  - Recomendación: el orchestrator debe confirmar el conteo exacto al ejecutar `npm test` en el gate. No bloquea.

### Nit

- **Ajuste aditivo de `jwt.test.ts` es el único cambio a un test existente** (prescripto por la spec §10 y la arquitectura §5.8/§8.1, A-008): las dos aserciones `toEqual` sobre tokens de `crearToken` ahora incluyen `username: 'usuario-test'` (más estricto, no debilita) y `decodificarJwt` devuelve la clave `username` solo cuando `sub` es string (las aserciones con `tokenConPayload` sin `sub` pasan igual porque `toEqual` ignora `undefined`). Sin acción requerida.

- **`getByText('CLIENTE')`/`getByText('ADMIN')` en los casos AC-004 de Layout** dependen de que el rol sea texto único en el árbol renderizado del test; si un futuro test renderizara otro texto con el mismo contenido podría colisionar, pero hoy no hay tal texto en los árboles de `Layout.test.tsx`. Sin acción requerida.

## Verification

Revisión estática completa (read-only) de la rama `feature/spec-009-ui-redesign` contra `origin/testing` (7 commits: docs de spec/arquitectura/guía, tokens CSS, username del `sub`, header, rediseño presentacional). El diff no pudo emitirse con `git diff` por falta de shell; se reconstruyó el mapa de archivos desde el log de commits (`.git/logs/refs/heads/feature/spec-009-ui-redesign`), la tabla §3 de la arquitectura y el estado del working tree (= HEAD).

- **Spec:** `docs/specs/SPEC-009-ui-redesign.md` (FR-001..FR-012, BR-001..BR-009, AC-001..AC-018, A-001..A-010).
- **Arquitectura:** `docs/architecture/SPEC-009.md` (§3 módulos afectados, §5.1 tokens y contraste AA, §5.2 Layout, §5.4 cuentas, §5.7 estados, §5.8 sesión/`sub`, §5.9 responsive/focus, §8 tests, §9 riesgos R1..R10).
- **Guía de diseño:** `docs/design/ui-guide.md` (FR-001, AC-001) — paleta, estados, tipografía, espaciado, sombras, radios, breakpoints, AA. **Cotejada 1:1 con el `:root` de `index.css` (AC-002): todos los valores coinciden.**
- **Implementación:**
  - `src/index.css` — reescrito: `:root` con los 40+ tokens de la guía; 7 bloques comentados; `@media (min-width: 640/768/1024px)` literales (A-006); `:focus-visible` global; spinner solo CSS (`::before` + `@keyframes girar`, A-010); `.cuenta-saldo .monto` localiza el saldo destacado solo en `/cuentas` (§5.4); estados con tokens (`--estado-exito-*`, `--estado-error-*`); `.seccion`/`.seccion-sub`/`.campo` conservados (R7).
  - `src/lib/jwt.ts` — `username` de `sub` solo si es string, en ambos caminos CLIENTE/ADMIN; validación de forma de `role`/`clienteId` intacta (no se debilita ERR-002).
  - `src/api/types.ts` — `JwtClaims.username?: string` (aditivo).
  - `src/store/auth-context.tsx` — `EstadoSesion.username?`; `login()` y restauración setean `username`; firma `Promise<Rol>` intacta; el consumidor de `auth-context.test.tsx` no lee `username` (compatibilidad verificada).
  - `src/components/Layout.tsx` — header `<header>/<nav>/<main>` con marca (span, no heading/link — A-009/R9), nav por rol con textos/destinos intactos, chip `username`+`rol` con fallback R2, logout intacto (`logout()` + `navigate('/login', {replace:true})`); sin `role="status"/"alert"` (BR-006/R6).
  - `src/components/Cargando.tsx`, `EstadoVacio.tsx`, `EstadoError.tsx`, `CampoFormulario.tsx`, `Monto.tsx` — solo clases/tokens; props, roles ARIA y textos intactos (BR-006, A-008).
  - `src/pages/LoginPage.tsx` — tarjeta `.login` + marca; pre-validaciones, `401` genérico, `ERROR_RED`, redirección por rol y botón `Ingresar`/`Ingresando...` intactos (FR-004, BR-008).
  - `src/pages/CuentasPage.tsx` — tarjetas `.cuenta` con `.cuenta-saldo` destacado; moneda una sola vez por cuenta (BR-003: conteo ARS=2 intacto), estados en crudo, tipos de movimiento en crudo con acento decorativo CSS por tipo (A-010, sin colorear el texto del tipo); expansión y refresco intactos.
  - `src/pages/TransferenciaPage.tsx` — tarjeta `.transferencia`; labels, payload `{cuentaOrigenId, cbuDestino, monto}`, manejo `201/400/422/red` y confirmación `role="status"` intactos (FR-006).
  - `src/pages/GestionPage.tsx` + `src/pages/gestion/*Section.tsx` — wrapper `.seccion` (clase consultada por `GestionPage.test` — R7), títulos h2/h3 conservados, `<section className="seccion-sub">` en Caja (consultado por `closest('section')` — R7), `.campo` conservado, comportamiento/payloads/confirmaciones/errores intactos (FR-007, BR-008).
  - **Sin cambios:** `App.tsx`, `ProtectedRoute.tsx`, `main.tsx`, `package.json` (mismas 3 dependencias runtime + devDeps estándar; sin dependencias nuevas — AC-015), `src/api/*`, `src/hooks/*`, `src/lib/session.ts`, `src/lib/validacion.ts`.
- **Tests:**
  - `src/components/Layout.test.tsx` — 6 casos (3 existentes AC-012/AC-021 + 3 nuevos AC-004 con marca/username/rol/links/logout y fallback token sin `sub`).
  - `src/components/Estados.test.tsx` — NUEVO, 4 casos AC-010: `role="status"`+"Cargando...", `role="alert"`+mensaje+`Reintentar` (click → `onReintentar`), sin botón sin `onReintentar`, `EstadoVacio` por prop.
  - `src/lib/jwt.test.ts` — 12 casos: 2 ajustes aditivos (`username: 'usuario-test'`) + 2 nuevos A-005 + 10 existentes intactos.
  - Resto de la suite (App, ProtectedRoute, LoginPage, CuentasPage, TransferenciaPage, GestionPage, ClientesSection, CuentasSection, CajaSection, UsuariosSection, auth-context, session, validacion, httpClient) revisado: 15 archivos, 17 archivos de test en total; aserciones críticas verificadas (conteo ARS=2, tipos en crudo, roles ARIA, payloads exactos, scoping por campo).
- **Grep / verificación por inspección:**
  - **AC-003 / BR-002 — PASS:** `#[0-9a-fA-F]{3,8}|rgb(|hsl(|rgba(` en `*.tsx` y `*.ts`: **0 resultados**; en `index.css`: 24 coincidencias, **todas dentro de `:root`** (hex líneas 17-39; `rgba` de sombras líneas 62-64 — única excepción permitida).
  - **AC-011 — PASS:** 3 media queries `min-width` (640/768/1024) y unidades `rem`.
  - **AC-012 — PASS:** `:focus-visible` en `a, button, input, select, textarea` con `--color-primario`; labels vía `CampoFormulario` (`htmlFor`↔`id`) preservados.
  - **Seguridad:** sin `dangerouslySetInnerHTML`, sin `console.*`, sin TODO/FIXME/HACK, sin secretos.
  - **Clases CSS:** cruce TSX↔CSS sin huérfanas.

### Nota de tooling

Este entorno no expone shell (`npm`/`git`), por lo que **no pude ejecutar** `npm test`, `npm run lint`, `npm run typecheck` ni `npm run build` (misma limitación que las revisiones previas de SPEC-007/SPEC-008). La verificación es estática:

- **Typecheck** — por inspección: `JwtClaims.username?` consumido en `auth-context` (`claims.username`) y en `Layout` (`username`); `EstadoSesion`/`AuthContextValue` consistentes; `username !== undefined && ...` reduce el tipo correctamente; `vi` global habilitado (`globals: true` en `vite.config.ts`); `base64url` helpers locales en `Layout.test.tsx`/`jwt.test.ts` sin colisiones; JSX válido en todos los componentes.
- **Lint** — sin imports sin usar en los archivos nuevos/modificados (verificado en cada archivo leído); `react-refresh` respeta la regla (solo exports de componentes; helpers privados `MovimientosCuenta`/`MovimientoItem`/`Confirmacion*`/`tokenSinSub`); estilo consistente con el repo (comillas simples, `;`, comentarios en español).
- **Tests** — asserts verificados uno a uno (ver Summary/Findings); `Estados.test.tsx` y los casos AC-004 usan los helpers existentes (`renderizarConSesion`, `crearToken`) y el setup global que limpia `fetch`/`localStorage` (`vitest.setup.ts`).
- **Build** — JSX/TS estáticamente válido; sin imports circulares ni de módulos inexistentes; `index.css` importado en `main.tsx` (sin cambios).

Las cuatro comprobaciones deben confirmarse en verde al ejecutar el gate (AC-013/AC-014, incluyendo el conteo exacto de tests).

## Result

**APPROVE.**

La implementación de SPEC-009 es de buena calidad y consistente con el diseño aprobado: sistema de diseño materializado en variables `:root` que coinciden **exactamente** con `docs/design/ui-guide.md` (AC-002), sin valores de color literales fuera de `:root` (BR-002/AC-003 verificado con grep: 0 en `.tsx`/`.ts`, 24 en CSS todos dentro de `:root`), header con marca/username/rol/logout (FR-003) con la lectura del claim `sub` como extensión UX-only de la decodificación del JWT (A-005, sin impacto en autorización ni contratos), páginas y secciones rediseñadas de forma puramente presentacional y aditiva con textos, payloads, validaciones, roles ARIA y estructuras consultadas por los tests intactos (BR-001/003/006/008), responsive mobile-first (FR-009) y `:focus-visible` global (FR-010/BR-007). Los tests nuevos (Layout AC-004, Estados AC-010, jwt A-005) son significativos y el único cambio a un test existente (`jwt.test.ts`) es el ajuste aditivo justificado por la arquitectura §5.8/§8.1 (no debilita ningún criterio). Sin dependencias npm nuevas, sin rutas nuevas, sin cambios de backend/migraciones/docker. Los hallazgos son menores/nits documentados (nombre de la clase `marca` vs `layout-marca` propuesto, conteo de tests a confirmar en el gate) y no afectan corrección, seguridad ni mantenibilidad.

La decisión de merge (gate: APPROVE) es de este revisor; la ejecución del merge la realiza el orchestrator. Nota: las comprobaciones de CI (`lint`/`typecheck`/`test`/`build`) no pudieron ejecutarse en este entorno y deben confirmarse al ejecutar el gate (AC-013/AC-014).