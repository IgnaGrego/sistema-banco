# Architecture — SPEC-009 (Rediseño de UI del Frontend)

## 1. Feature

Rediseño **frontend-only** de la SPA de SPEC-006 hacia un **look bancario
moderno** (issue #28): sistema de diseño con variables CSS (`:root` en el
único `src/index.css`), header con marca "Banco" + usuario actual + logout,
login en tarjeta centrada, cuentas como tarjetas con **saldo destacado**,
movimientos legibles, formulario de transferencia claro, secciones de
`/gestion` organizadas con estilo consistente y estados de
carga/error/vacío atractivos. Incluye **responsive mobile-first** (breakpoints
sm 640 / md 768 / lg 1024, media queries `min-width`) y **accesibilidad
básica** (contraste AA, labels preservados, `:focus-visible`, ARIA de estados
intactos — FR-010).

El issue exige **definir primero la guía de diseño
`docs/design/ui-guide.md`** (FR-001) antes de implementar el resto: es un
entregable de esta spec y la **única fuente de verdad de los tokens** (A-001),
que esta arquitectura materializa en el bloque `:root` de `src/index.css`
(FR-002).

**Naturaleza del cambio:** puramente presentacional y **aditivo**. No cambia
contratos de la API, rutas, props de componentes, comportamiento de negocio ni
autorización (BR-001, BR-004, BR-008). Los **158 tests existentes** deben
seguir pasando sin debilitarse (BR-003, A-008); la compatibilidad de cada
cambio de markup/estado se verificó contra la suite actual en §5.8/§8/§9.

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-009-ui-redesign.md` (APROBADA — fuente de verdad;
  FR-001..FR-012, BR-001..BR-009, AF-001..AF-006, ERR-001..ERR-009,
  AC-001..AC-018, asunciones A-001..A-010).
- `docs/specs/SPEC-006-frontend-react.md` (SPA baseline a rediseñar:
  FR-009..FR-015, §5 BR, §8 errores, §9 matriz por rol, §11 AC, A-008).
- `docs/specs/SPEC-007-caja-gestion.md` y `docs/specs/SPEC-008-usuarios-gestion.md`
  (secciones de `/gestion` a reestilizar con comportamiento intacto — FR-007).
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend que este
  rediseño **refina**: stack, sin librerías de UI, CSS plano, rol client-side).
- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (API REST y envelope
  `{ code, message, details? }`), §8 (seguridad y propiedad).

---

## 3. Affected Modules

**Solo frontend (`frontend/`) + un documento nuevo en `docs/design/`.**
El backend, las migraciones Flyway, la base de datos, `docker/`, `App.tsx`
(rutas — BR-004) y `src/api/*` (contratos — BR-001) **no se tocan**
(spec §10, BR-001, AC-016).

| Archivo | Cambio |
| --- | --- |
| `docs/design/ui-guide.md` | **NUEVO** (FR-001): guía con paleta, colores semánticos de estado, tipografía, espaciado, sombras, redondeos, breakpoints y objetivos AA. Se define **antes** de FR-003..FR-010 (AC-001). Única fuente de verdad de los tokens. |
| `src/index.css` | **Reescrito** (FR-002): bloque `:root` con los tokens (§5.1) y todas las reglas rediseñadas en bloques comentados (layout, componentes, páginas, estados, responsive). Sin valores de color/espaciado/radio/sombra literales fuera de `:root` (BR-002, AC-003). |
| `src/lib/jwt.ts` + `src/api/types.ts` | **Extendidos** (A-005): `decodificarJwt` expone también `username` (claim `sub`); `JwtClaims` gana `username?: string`. Cambio mínimo y aditivo (§5.8 — compatibilidad verificada contra `jwt.test.ts`/`auth-context.test.tsx`). |
| `src/store/auth-context.tsx` | **Extendido** (A-005): `EstadoSesion` gana `username?: string`; `login()` y la restauración lo setean desde `claims.username`. Sin cambios en la API del contexto. |
| `src/components/Layout.tsx` | **Rediseñado** (FR-003): header con marca "Banco", navegación por rol preservada, `username` + `rol` y botón "Cerrar sesión" con comportamiento intacto (AC-012). |
| `src/components/Cargando.tsx`, `EstadoVacio.tsx`, `EstadoError.tsx`, `CampoFormulario.tsx`, `Monto.tsx` | Ajustes de **presentación** (FR-008, FR-010): clases/tokens; props, roles ARIA y textos intactos (BR-006, A-008). `CampoFormulario` conserva el wrapper `.campo` y la asociación `label↔input`. |
| `src/pages/LoginPage.tsx` | Presentación (FR-004): tarjeta centrada con marca; formulario, pre-validaciones, manejo de `401`/`ERROR_RED` y redirección por rol intactos (BR-008). |
| `src/pages/CuentasPage.tsx` | Presentación (FR-005): tarjetas de cuenta con saldo destacado; datos, expansión de movimientos y textos exactos intactos (BR-003). |
| `src/pages/TransferenciaPage.tsx` | Presentación (FR-006): formulario y panel de confirmación claros; payload, validaciones y textos intactos (BR-003, BR-008). |
| `src/pages/GestionPage.tsx` + `src/pages/gestion/*Section.tsx` | Presentación (FR-007): secciones consistentes; wrapper `.seccion` y títulos conservados; comportamiento de SPEC-006/007/008 intacto. |
| Tests | `src/components/Layout.test.tsx` **extendido** (AC-004: marca, username, rol, links por rol); `src/components/Estados.test.tsx` **nuevo** (AC-010); `src/lib/jwt.test.ts` **ajuste aditivo justificado** (§8 — A-008). |

**Sin cambios:** `package.json`/`package-lock.json` (BR-005, AC-015),
`App.tsx` y `ProtectedRoute` (BR-004, AC-017), `src/api/*`, `src/hooks/*`,
`src/lib/session.ts` y `src/lib/validacion.ts`, backend, migraciones,
`docker/`.

---

## 4. Application Flow

El rediseño **no altera ningún flujo de datos**: los flujos de SPEC-006 §6,
SPEC-007 y SPEC-008 se mantienen; solo cambia la capa de presentación. El
diagrama de capas de SPEC-006 §4.1 sigue siendo válido: la SPA es el único
contenedor tocado y su integración con la API (`/api/v1` vía proxy de Vite,
Bearer JWT, envelope de errores) es idéntica.

### 4.1 Flujo de presentación del rediseño

```text
docs/design/ui-guide.md (FR-001 — definida PRIMERO)
        ↓  valores exactos (paleta, estados, tipografía, espaciado, sombras, radios)
src/index.css :root (FR-002 — tokens --color-*, --estado-*, --font-*, --espacio-*,
                      --sombra-*, --radio-*)  →  única fuente de los valores de estilo
        ↓  variables consumidas por las reglas CSS (clases por componente)
Componentes .tsx (Layout, Login, Cuentas, Transferencia, Gestión, estados)
        ↓  mismos datos, flujos, payloads y textos (BR-001/BR-003/BR-008)
API /api/v1 (sin cambios — proxy de Vite, Bearer JWT, envelope de errores)
```

- **Flujo de tokens → clases:** los componentes no conocen valores de estilo:
  agregan/conservan **clases CSS** (`layout-header`, `cuenta`, `estado-error`,
  etc.) y las reglas de `index.css` resuelven esas clases con las variables
  del `:root`. El único lugar con valores literales (hex/rgba) es la
  **definición** de las variables en `:root` (BR-002, AC-003).
- **Flujo de la sesión hacia el header (FR-003):** `AuthProvider` restaura la
  sesión desde `localStorage` (`banco.token` → `decodificarJwt` →
  `{ role, clienteId?, username? }`) y expone `username` junto a
  `token`/`rol`/`clienteId`. `Layout` lee `{ rol, username, logout }` de
  `useAuth()` y renderiza marca, nav por rol, chip de usuario
  (`username` + `rol`) y "Cerrar sesión" (logout intacto: `limpiarToken` +
  `Navigate('/login')` — AC-012).
- **Flujos de datos de las páginas intactos:** `CuentasPage` sigue usando
  `useCuentas`/`useMovimientos`, `TransferenciaPage` usa `useTransferencia`,
  y `/gestion` usa `useClientes`/`useCuentas`/`useCaja`/`useRegistroUsuario`
  con sus estados `{ datos, cargando, error, recargar }` y mocks de `fetch`
  en tests — **ningún hook ni llamada a la API se modifica** (BR-001, BR-008).
- **Sin nuevo trabajo asíncrono:** no hay jobs, eventos ni WebSockets nuevos
  (SPEC-006 §5.6 se mantiene).

---

## 5. Components

### 5.1 Design tokens (FR-002) — variables CSS en `:root`

Tabla de variables que el desarrollador debe definir en `src/index.css`
(bloque `:root`), con valores concretos derivados de la paleta actual del CSS
y de A-004. **Los valores finales se confirman en `docs/design/ui-guide.md`
(FR-001, AC-002): la guía y el `:root` deben coincidir exactamente.** Los
**nombres** de las variables son los que verifica AC-002.

| Variable | Valor | Uso |
| --- | --- | --- |
| `--color-primario` | `#1f6feb` | acciones principales, links, botón submit, anillo de foco |
| `--color-primario-oscuro` | `#102a43` | fondo del header, títulos y montos destacados |
| `--color-fondo` | `#f5f7fa` | fondo de página (`body`) |
| `--color-superficie` | `#ffffff` | tarjetas, secciones, login, confirmaciones |
| `--color-borde` | `#d9e2ec` | bordes de superficies (tarjetas, secciones) |
| `--color-borde-input` | `#bcccdc` | bordes de inputs/selects |
| `--color-texto` | `#1f2933` | texto principal |
| `--color-texto-suave` | `#486581` | texto secundario, estados vacíos |
| `--color-deshabilitado` | `#9fb3c8` | fondo de controles deshabilitados |
| `--estado-exito-fondo` | `#e3fcec` | fondo de confirmaciones |
| `--estado-exito-borde` | `#9ae6b4` | borde de confirmaciones |
| `--estado-exito-texto` | `#1a7f37` | texto de confirmaciones |
| `--estado-error-fondo` | `#fdecea` | fondo de errores y alertas |
| `--estado-error-borde` | `#e8a0a0` | borde de errores |
| `--estado-error-texto` | `#8b0000` | texto de errores |
| `--estado-advertencia-fondo` | `#fff8e6` | fondo de advertencias (si se usan) |
| `--estado-advertencia-borde` | `#f0d78c` | borde de advertencias |
| `--estado-advertencia-texto` | `#7a4d00` | texto de advertencias |
| `--estado-info-fondo` | `#e8f0fe` | fondo de mensajes informativos |
| `--estado-info-borde` | `#b3c9f0` | borde informativo |
| `--estado-info-texto` | `#1f4e8c` | texto informativo |
| `--font-familia` | `system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif` | familia tipográfica (stack actual — A-004) |
| `--font-xs` | `0.75rem` (12 px) | texto pequeño, fechas, DNI |
| `--font-sm` | `0.875rem` (14 px) | errores de campo, textos secundarios |
| `--font-base` | `1rem` (16 px) | cuerpo |
| `--font-lg` | `1.125rem` (18 px) | subtítulos/secciones |
| `--font-xl` | `1.25rem` (20 px) | títulos de sección y tarjeta |
| `--font-2xl` | `1.5rem` (24 px) | títulos de página y **montos destacados** |
| `--font-3xl` | `1.875rem` (30 px) | marca/título de login (opcional) |
| `--espacio-1` | `0.25rem` (4 px) | gaps mínimos |
| `--espacio-2` | `0.5rem` (8 px) | padding internos pequeños |
| `--espacio-3` | `0.75rem` (12 px) | padding de tarjetas compactas |
| `--espacio-4` | `1rem` (16 px) | padding estándar |
| `--espacio-5` | `1.5rem` (24 px) | padding de secciones y login |
| `--espacio-6` | `2rem` (32 px) | separación entre bloques |
| `--espacio-8` | `3rem` (48 px) | márgenes de página |
| `--sombra-sm` | `0 1px 2px rgba(16, 42, 67, 0.08)` | sombra sutil de tarjetas |
| `--sombra-md` | `0 4px 12px rgba(16, 42, 67, 0.12)` | sombra de login y secciones |
| `--sombra-lg` | `0 8px 24px rgba(16, 42, 67, 0.16)` | sombra de estados/elevación |
| `--radio-sm` | `4px` | inputs, botones |
| `--radio-md` | `8px` | tarjetas de cuenta, login, secciones |
| `--radio-lg` | `12px` | tarjetas destacadas |
| `--radio-xl` | `16px` | elementos hero (estados) |

> **Nota BR-002:** ninguna regla de `index.css` usa valores literales de
> color/espaciado/radio/sombra fuera de `:root`; los `rgba(...)` de las
> sombras viven dentro de la definición de `--sombra-*` en `:root` (única
> excepción permitida por la spec). Verificación: AC-003 (grep de
> hex/rgb/hsl en `.tsx`) + revisión de `index.css`.

#### Contraste AA (A-007, FR-010) — nota de verificación de la paleta

Pares texto/fondo críticos y su ratio (≈, luminancia relativa sRGB; los
valores finales se cotejan en la guía — AC-012):

| Par | Ratio | AA (4.5:1 texto normal) |
| --- | --- | --- |
| `#1f2933` sobre `#ffffff` | ≈ 14.4:1 | ✓ |
| `#486581` sobre `#ffffff` | ≈ 6.0:1 | ✓ (texto suave) |
| `#486581` sobre `#f5f7fa` | ≈ 5.6:1 | ✓ (estados vacíos) |
| `#1f6feb` sobre `#ffffff` (botón/link/foco) | ≈ 4.6:1 | ✓ (también ≥ 3:1 para UI — FR-010) |
| `#ffffff` sobre `#102a43` (header) | ≈ 14.6:1 | ✓ |
| `#8b0000` sobre `#fdecea` (errores) | ≈ 7.5:1 | ✓ |
| `#1a7f37` sobre `#e3fcec` (éxito) | ≈ 4.7:1 | ✓ |
| `#7a4d00` sobre `#fff8e6` (advertencia) | ≈ 6.9:1 | ✓ |
| `#1f4e8c` sobre `#e8f0fe` (info) | ≈ 7.3:1 | ✓ |
| `#9fb3c8` (deshabilitado) | — | exento: WCAG 1.4.3 excluye componentes inactivos |

El texto grande (≥ 18 px normal o ≥ 14 px bold — p. ej. `--font-2xl` de
montos) solo requiere 3:1, muy superado por los pares anteriores. **Regla de
implementación:** si el implementador ajusta algún valor en la guía, debe
re-verificar el par con la fórmula WCAG antes de fijarlo (FR-001, A-007).

### 5.2 Layout — header con marca, usuario y logout (FR-003)

Estructura markup propuesta (aditiva sobre la actual; se conservan textos y
destinos de links y el botón de logout):

```tsx
<header className="layout-header">
  <span className="layout-marca">Banco</span>
  <nav className="layout-nav" aria-label="Navegación principal">
    {rol === 'CLIENTE' && <Link to="/cuentas">Mis cuentas</Link>}
    {rol === 'ADMIN' && <Link to="/gestion">Gestión</Link>}
  </nav>
  <div className="layout-usuario">
    {username !== undefined && <span className="layout-usuario-nombre">{username}</span>}
    <span className="layout-usuario-rol">{rol}</span>
  </div>
  <button type="button" className="boton-logout" onClick={cerrarSesion}>Cerrar sesión</button>
</header>
<main className="layout-main">{children}</main>
```

Decisiones de diseño:

- **Marca textual** (A-009): `<span className="layout-marca">Banco</span>` —
  **no es un heading** (preserva la jerarquía `h1` única por página: "Mis
  cuentas" / "Gestión") y **no es un link** (no agrega destinos). Estilo con
  `--color-superficie`, `--font-xl`/`--font-2xl`, peso 700, sobre
  `--color-primario-oscuro`. **Texto exacto "Banco" una sola vez por árbol
  renderizado** (BR-003): ningún test actual consulta "Banco" (verificado por
  grep en §8), pero es texto aditivo y no debe duplicarse.
- **Navegación por rol preservada**: mismos textos ("Mis cuentas" / "Gestión")
  y destinos (`/cuentas` / `/gestion`); los tests por
  `getByRole('link', { name: ... })` (Layout.test AC-021, App.test AC-021,
  CuentasPage.test AC-021) siguen pasando (BR-003, BR-004).
- **Chip de usuario**: `username` (de `sub` — A-005, §5.8) + `rol`. Fallback:
  si `username` es `undefined` (token sin `sub`), se **omite el nombre** y se
  muestra solo el `rol`; nunca se rompe el render (Riesgo R2, §9).
- **Logout intacto**: mismo texto "Cerrar sesión", misma lógica
  (`logout()` + `navigate('/login', { replace: true })` — AC-012 existente).
- **`.layout-main`**: se mantiene centrado con ancho máximo (actual `60rem`),
  padding con `--espacio-4`/`--espacio-5` — FR-003.
- **Responsive (FR-009)**: mobile-first con `flex-wrap` en `.layout-header`
  (marca en la primera fila; nav/usuario/logout envueltos con `gap`) y en
  `md` (768 px) una sola fila `space-between`. Sin hamburguesa ni librerías JS
  de layout (BR-005, A-002): la SPA tiene 1-2 links de navegación, el wrap es
  la solución más simple (AGENTS.md §11) y cumple BR-009 (≥ 360 px sin
  desbordes).
- **Semántica y accesibilidad**: `<header>`/`<nav>`/`<main>` ya presentes;
  `aria-label` de navegación opcional (aditivo); `:focus-visible` global
  (BR-007, §5.9). **No** se agregan `role="status"`/`role="alert"` en el
  header (BR-006: AC-030 de CuentasPage usa `findByRole('status')` y el único
  `status` en ese render debe seguir siendo `Cargando`).

### 5.3 Login — tarjeta centrada con marca (FR-004)

```tsx
<section className="login">
  <p className="login-marca">Banco</p>
  <h1>Iniciar sesión</h1>
  {errorGeneral !== null && <div role="alert" className="error-general">{errorGeneral}</div>}
  <form onSubmit={onSubmit} noValidate>
    {/* CampoFormulario "Usuario" / "Contraseña" + botón "Ingresar" — intactos */}
  </form>
</section>
```

- `.login` (tarjeta centrada): `max-width: 24rem; margin: 4rem auto;`
  (centrado horizontal existente) con `--color-superficie`, `--sombra-md`,
  `--radio-lg`, padding `--espacio-5`, borde `--color-borde`.
- `.login-marca`: texto "Banco" estilizado (`--color-primario-oscuro`,
  `--font-2xl`/`--font-3xl`, peso 700), centrado. **No es heading**: `h1`
  "Iniciar sesión" se conserva único (LoginPage.test/App.test consultan
  `findByText('Iniciar sesión')` — BR-003).
- **Comportamiento intacto** (BR-008): labels "Usuario"/"Contraseña"
  (`getByLabelText` — AC-008/009), pre-validación BR-001 de SPEC-006,
  `401` → "Usuario o contraseña incorrectos" sin token, `ERROR_RED` → mensaje
  de conexión, botón `Ingresar`/`Ingresando...` y redirección por rol. Solo
  cambia la presentación.

### 5.4 Cuentas — tarjetas con saldo destacado y movimientos legibles (FR-005)

Estructura por cuenta (aditiva sobre la actual; los textos asertados se
conservan **exactos** — BR-003, AC-014/AC-016):

```tsx
<li key={cuenta.id} className="cuenta">
  <div className="cuenta-resumen">
    <span className="cuenta-tipo">{ETIQUETA_TIPO[cuenta.tipo]}</span>   {/* "Caja de ahorro" / "Cuenta corriente" */}
    <span className="cuenta-cbu">CBU: {cuenta.cbu}</span>
    <span className="cuenta-saldo">
      <Monto monto={cuenta.saldo} />                                     {/* saldo destacado */}
    </span>
    <span className="cuenta-moneda">{cuenta.moneda}</span>               {/* exactamente 1 "ARS" por cuenta */}
    <span className={`cuenta-estado cuenta-estado-${cuenta.estado.toLowerCase()}`}>
      {cuenta.estado}                                                    {/* "ACTIVA" / "BLOQUEADA" exactos */}
    </span>
    <button type="button" onClick={() => alternarExpansion(cuenta.id)}>
      {cuentaExpandida === cuenta.id ? 'Ocultar movimientos' : 'Ver movimientos'}
    </button>
  </div>
  {cuentaExpandida === cuenta.id && <MovimientosCuenta ... />}
</li>
```

- **Tarjeta**: `.cuenta` con `--color-superficie`, `--sombra-sm`, `--radio-md`,
  borde `--color-borde`, padding `--espacio-4`.
- **Saldo destacado**: wrapper **aditivo** `.cuenta-saldo` que agranda el
  monto (`--font-2xl`, peso 700, `--color-primario-oscuro`) **solo** en
  `/cuentas` (regla `.cuenta-saldo .monto`). **No** se agranda `.monto`
  globalmente: el mismo componente se usa en la lista de movimientos y en las
  secciones de gestión, donde conserva tamaño de cuerpo (jerarquía: el saldo
  es el elemento visual dominante de la tarjeta, no de las listas secundarias).
- **Moneda exactamente una vez por cuenta** (`getAllByText('ARS')` → 2 con 2
  cuentas — AC-014): el span `.cuenta-moneda` con texto exacto `ARS` se
  conserva; **no** agregar otros elementos con texto exacto "ARS" en la vista
  (un badge "ARS" duplicado rompería el conteo — BR-003).
- **Estados en crudo**: `ACTIVA`/`BLOQUEADA` como texto exacto standalone
  (AC-014). Diferenciación visual con la clase existente
  `.cuenta-estado-bloqueada` redefinida con tokens: `BLOQUEADA` en
  `--estado-error-texto` (AA sobre superficie ✓).
- **Movimientos legibles**: `.movimiento` con alineación grid/flex y
  separación consistente (`gap`), fecha con `--color-texto-suave` y `--font-sm`.
  Los **tipos se mantienen en crudo** (`DEPOSITO`, `TRANSFERENCIA_SALIENTE`,
  ... — AC-016) y se permite diferenciación visual **opcional** por tipo vía
  clases aditivas (p. ej. `movimiento-tipo-deposito` con un indicador CSS
  `::before`), **sin colorear el texto del tipo** (queda en `--color-texto`,
  AA garantizada; el color solo como acento decorativo).
- **Estados carga/error/vacío y expansión**: comportamiento intacto
  (AC-016, AC-029, AC-030) con los componentes rediseñados de §5.7.

### 5.5 Transferencia — formulario claro (FR-006)

- `section.transferencia` adquiere el **tratamiento de tarjeta consistente**
  (mismo lenguaje visual que `.seccion`: `--color-superficie`, `--radio-md`,
  `--sombra-sm`, padding) con `h2` "Transferencia" — título conservado
  (`GestionPage.test` aserta que "Transferencia" **no** existe en `/gestion`;
  en `/cuentas` el título es único — no duplicar texto).
- Formulario: `CampoFormulario` con labels **"Cuenta origen"**, **"CBU
  destino"**, **"Monto"** (queries `getByLabelText` — AC-017..AC-020) y botón
  **"Transferir"**/`Enviando...` — textos y payload
  `{cuentaOrigenId, cbuDestino, monto}` intactos (BR-003, BR-008).
- **Confirmación** `.confirmacion`: `role="status"` conservado (BR-006) y
  textos exactos ("Transferencia realizada", "ID de transferencia: ...",
  "Monto: ...", "CBU destino: ...", "Fecha y hora: ..." — AC-018) con
  presentación de éxito (`--estado-exito-*`, AA ✓).
- Pre-validaciones, mapeo `details`→por campo, `400`/`422`/`ERROR_RED` y
  conservación de datos: **sin cambios** (BR-008, AC-019/020).

### 5.6 Gestión — secciones organizadas y consistentes (FR-007)

- Las cuatro secciones comparten **el mismo lenguaje de tarjeta**: `.seccion`
  (clase **obligatoria**: `GestionPage.test` usa
  `heading.closest('.seccion')` para acotar el selector de cliente) con
  `--color-superficie`, `--sombra-md`, `--radio-md`, `--espacio-5` de padding
  y `h2` de sección con `--font-xl` + `--color-primario-oscuro`.
- **Títulos conservados**: `h2` "Clientes", "Cuentas", "Caja", "Usuarios"
  (queries por heading — App.test/GestionPage.test/CajaSection.test/
  UsuariosSection.test) y `h3` de formularios ("Nuevo cliente", "Abrir
  cuenta", "Depósito", "Retiro").
- **CajaSection**: los sub-formularios "Depósito"/"Retiro" siguen envueltos en
  `<section>` (CajaSection.test usa `heading.closest('section')`) — solo se
  reestilizan.
- Formularios y listas comparten `CampoFormulario` (wrapper `.campo`
  conservado: tests usan `.closest('.campo')` — UsuariosSection.test/
  CajaSection.test), `Monto`, `Cargando`, `EstadoError`, `EstadoVacio` — todos
  con los tokens de §5.1.
- **Comportamiento intacto** (BR-008): payloads, validaciones, confirmaciones
  (`role="status"`), errores del envelope y estados de SPEC-006 FR-012/013,
  SPEC-007 y SPEC-008.

### 5.7 Estados de carga, vacío y error (FR-008)

| Componente | Conserva (BR-006, A-008) | Rediseño (tokens) |
| --- | --- | --- |
| `Cargando` | `role="status"` + texto "Cargando..." | `.estado-cargando` como indicador tipo tarjeta sutil: spinner opcional **solo CSS** (`::after` con borde animado — A-010) o texto estilizado con `--color-texto-suave`; si se agrega un spinner, "Cargando..." debe seguir presente como subcadena (`toHaveTextContent` — AC-030) |
| `EstadoError` | `role="alert"`, mensaje del envelope, botón "Reintentar" | `.estado-error` con `--estado-error-*`, `--radio-md`, `--sombra-sm`; botón "Reintentar" con estilo primario (texto exacto — AC-029) |
| `EstadoVacio` | misma interfaz de props (`{ mensaje }`), mensaje recibido | `.estado-vacio` con `--color-texto-suave`; ícono opcional CSS/unicode (A-010) |
| `CampoFormulario` | wrapper `.campo`, `label htmlFor`↔input, `.error-campo` con `role="alert"` | `.error-campo` con `--estado-error-texto` y `--font-sm` |
| `Monto` | `formatearMontoARS` (display) | `.monto` con `--font-base`/`--font-lg` (el énfasis de saldo vive en `.cuenta-saldo`, §5.4) |
| `ConfirmacionTransferencia`/`ConfirmacionCaja`/`ConfirmacionUsuario` | `role="status"` y textos | fondo `--estado-exito-fondo`, borde `--estado-exito-borde`, texto `--estado-exito-texto` (AA ✓) |

### 5.8 Sesión: `username` desde el claim `sub` (A-005) — cambio mínimo verificado

**Verificación de compatibilidad con la suite actual (hecha en esta
arquitectura, no asumida):**

- **`src/test/helpers.tsx` → `crearToken`** ya emite `sub: 'usuario-test'` en
  **todo** token de tests → cualquier token creado por `crearToken` tiene
  `username` disponible.
- **`jwt.test.ts`**: dos aserciones con `toEqual` sobre tokens de `crearToken`
  (`{ role: 'CLIENTE', clienteId: 42 }` y `{ role: 'ADMIN' }`) **dejan de
  pasar** si `decodificarJwt` devuelve `username` → **ajuste aditivo
  justificado** (spec §10, A-008): el objeto esperado gana
  `username: 'usuario-test'`. Las aserciones con `tokenConPayload({ role:
  'ADMIN' })` (sin `sub`) **siguen pasando sin cambios** porque Vitest/Jest
  `toEqual` ignora propiedades `undefined` → el `username` debe ser
  **opcional** (`username?: string`) y devolverse solo cuando `sub` es string.
- **`auth-context.test.tsx`**: el consumidor de prueba nunca lee `username`;
  agregar `username` al estado no rompe ninguna aserción. ✓
- **`Layout.test.tsx`**: las aserciones existentes usan `getByRole('link',
  ...)` y `getByRole('button', { name: 'Cerrar sesión' })` → el header con
  marca + chip de usuario es aditivo y no colisiona. ✓

**Cambio prescripto (mínimo, aditivo):**

1. `src/api/types.ts` → `JwtClaims`:
   ```ts
   export interface JwtClaims {
     role: Rol;
     clienteId?: number;
     username?: string; // claim `sub` del JWT (SPEC-003 §6.3: sub = username) — A-005
   }
   ```
2. `src/lib/jwt.ts` → `decodificarJwt`: tras validar `role`/`clienteId`, leer
   `const username = payload.sub as unknown;` e incluir la clave `username` en
   el objeto devuelto **solo si** `typeof username === 'string'` (en ambos
   caminos, CLIENTE y ADMIN). Payload sin `sub` → sin clave `username`
   (equivale a `undefined`; `toEqual` no se ve afectado).
3. `src/store/auth-context.tsx` → `EstadoSesion` gana `username?: string`;
   `login()` y la restauración setean `username: claims.username` en el
   `setEstado`. `AuthContextValue extends EstadoSesion` → `useAuth()` expone
   `username` sin cambios de API. El `login()` debe conservar la devolución
   `Promise<Rol>` (el rediseño no cambia la firma).

**El claim `sub` ya existe en todos los tokens emitidos por el backend**
(`JwtService` emite `sub = username` — SPEC-003 §6.3): no hay cambio de
contrato ni riesgo para tokens reales; solo tokens de tests construidos a
mano pueden carecer de `sub` (cubierto por el fallback).

### 5.9 Responsive y focus visible (FR-009, FR-010, BR-007)

- **Media queries mobile-first** en `index.css`:
  `@media (min-width: 640px)` (sm), `@media (min-width: 768px)` (md),
  `@media (min-width: 1024px)` (lg). Nota: las variables CSS **no** pueden
  usarse en condiciones `@media` (A-006): los breakpoints van como literales
  y se documentan en la guía (AC-011).
- **Unidades relativas**: todo tamaño en `rem` (los tokens ya son `rem`);
  el layout no se rompe en **≥ 360 px** (BR-009, AC-011) — revisión manual en
  `npm run dev`.
- **`:focus-visible`** global:
  `:focus-visible { outline: 2px solid var(--color-primario); outline-offset: 2px; }`
  aplicado a inputs, selects, botones y links (BR-007, AC-012); `#1f6feb`
  sobre superficies claras ≈ 4.6:1 ≥ 3:1 requerido para componentes UI (A-007).

---

## 6. Data Changes

**Backend y base de datos: ninguno.** No hay migraciones Flyway, tablas,
columnas ni propiedades nuevas (BR-001, AC-016).

**Frontend:**

- **Nuevo:** `docs/design/ui-guide.md` (FR-001).
- **Modificados (presentación/estado):** `src/index.css` (tokens + reglas),
  `src/components/*` (clases/wrappers aditivos), `src/pages/*` y
  `src/pages/gestion/*` (clases/wrappers aditivos).
- **Extendidos (estado de sesión):** `src/api/types.ts` (`JwtClaims.username?`),
  `src/lib/jwt.ts` (devuelve `username` de `sub`), `src/store/auth-context.tsx`
  (expone `username`).
- **Sin cambios:** `package.json`/`package-lock.json` (BR-005, AC-015),
  `App.tsx`/`ProtectedRoute` (BR-004, AC-017), `src/api/*`, `src/hooks/*`,
  `src/lib/session.ts`, `src/lib/validacion.ts`, backend, migraciones,
  `docker/`.

**Dato de sesión (efímero, navegador):** el JWT en `localStorage`
(`banco.token`) no cambia de forma ni de clave; solo se lee un claim adicional
(`sub`) que ya existía (A-005).

---

## 7. External Integrations

**Ninguna nueva y ninguna modificada.** La única integración sigue siendo la
API REST `/api/v1` (proxy de Vite, Bearer JWT, envelope de errores — SPEC-006
§7, ADR-008): sin cambios de endpoints, payloads, códigos de error ni CORS
(BR-001). Sin proveedores externos, sin assets de imagen ni librerías de
iconos (A-009, A-010, BR-005).

---

## 8. Testing Strategy

**Comandos (FR-011, AC-013/014):** `npm test` (Vitest + RTL, `fetch` mockeado,
jsdom), `npm run lint`, `npm run typecheck`, `npm run build`. La suite
completa actual (158 tests) debe seguir pasando **sin debilitarse** (BR-003,
A-008, AGENTS.md §12).

**Principio clave:** RTL consulta por rol/texto/label (class-agnostic) —
renombrar o agregar clases CSS **no** rompe tests; solo los cambios de markup
que alteren textos exactos, roles ARIA o estructuras consultadas podrían
romperlos. Los cambios de esta spec son **aditivos** y se verificaron contra
la suite (ver tabla de riesgos en §9).

### 8.1 Tests nuevos

| Test | Cubre |
| --- | --- |
| `src/components/Layout.test.tsx` (extendido) | AC-004: header renderiza la marca "Banco", el `username` (`usuario-test`, del `sub` del token de `crearToken`) y el `rol`; links por rol ("Mis cuentas" para `CLIENTE`; "Gestión" para `ADMIN`); botón "Cerrar sesión". Usar `renderizarConSesion` (helpers existentes) — sin tocar las 3 aserciones actuales (AC-012/AC-021). |
| `src/components/Estados.test.tsx` (nuevo, pequeño) | AC-010: `Cargando` conserva `role="status"` + "Cargando..."; `EstadoError` conserva `role="alert"`, el mensaje y el botón "Reintentar"; `EstadoVacio` renderiza el mensaje recibido por prop. Regresión barata del rediseño visual de §5.7. |
| `src/lib/jwt.test.ts` (ajuste aditivo justificado) | Las 2 aserciones `toEqual` sobre tokens de `crearToken` ganan `username: 'usuario-test'` (A-008: ajuste aditivo, no debilitamiento); caso nuevo: token sin `sub` → sin clave `username`. |

### 8.2 Archivos de test existentes que deben seguir pasando sin cambios

`src/components/Layout.test.tsx` (3 casos actuales), `ProtectedRoute.test.tsx`,
`App.test.tsx`, `src/pages/LoginPage.test.tsx`, `CuentasPage.test.tsx`,
`TransferenciaPage.test.tsx`, `GestionPage.test.tsx`,
`src/pages/gestion/ClientesSection.test.tsx`, `CuentasSection.test.tsx`,
`CajaSection.test.tsx`, `UsuariosSection.test.tsx`,
`src/store/auth-context.test.tsx`, `src/lib/jwt.test.ts` (resto),
`src/lib/session.test.ts`, `src/lib/validacion.test.ts`,
`src/api/httpClient.test.ts`.

### 8.3 Verificación de compatibilidad puntual (hecha sobre la suite actual)

- **Sin colisión con "Banco"**: grep sobre `src/**/*.test.tsx` → ningún test
  consulta el texto "Banco" ni headings sin nombre; los headings consultados
  son por nombre ("Iniciar sesión", "Mis cuentas", "Clientes", "Cuentas",
  "Caja", "Usuarios", "Depósito", "Retiro"). La marca no debe ser un heading
  ni duplicarse en el mismo árbol.
- **Conteos exactos**: `CuentasPage.test` `getAllByText('ARS')` → 2 (moneda
  una vez por cuenta, sin badges adicionales); `ACTIVA`/`BLOQUEADA`,
  `DEPOSITO`/`TRANSFERENCIA_SALIENTE` como texto exacto standalone.
- **Roles ARIA únicos en el render crítico**: AC-030 de `CuentasPage.test`
  usa `findByRole('status')` → el header no debe agregar `role="status"`.
- **Estructuras consultadas**: `.seccion` (GestionPage.test), `.campo`
  (CajaSection/UsuariosSection.test), `<section>` (CajaSection.test) —
  conservadas.

### 8.4 Verificación por comando/inspección (AC)

- AC-001/AC-002: existencia de `docs/design/ui-guide.md` + grep de variables
  `--color-*`, `--estado-*`, `--font-*`, `--espacio-*`, `--sombra-*`,
  `--radio-*` en `:root` y cotejo con la guía.
- AC-003: `grep -rE '#[0-9a-fA-F]{3,6}|rgb\(|hsl\(' src --include='*.tsx'`
  sin resultados (salvo comentarios).
- AC-011/AC-012: grep de `@media (min-width: ...)` y de `:focus-visible` en
  `index.css`; revisión visual en `npm run dev` (360 px y desktop).
- AC-013..AC-018: lint, typecheck, build, suite completa, diff sin cambios en
  backend/`docker/`, sin dependencias nuevas, sin rutas nuevas.

---

## 9. Risks

- **R1 — Colisión de texto con la marca "Banco" (bajo hoy, vigilar):** ningún
  test actual consulta "Banco" (verificado con grep en §8.3), pero es texto
  nuevo visible en todas las vistas autenticadas y en el login. Mitigación:
  la marca **no** es heading ni link, se renderiza **una sola vez** por árbol
  (BR-003), y los tests nuevos de AC-004 la asertan explícitamente para fijar
  el contrato.
- **R2 — `username` ausente (token sin `sub`):** solo ocurre con tokens de
  tests construidos a mano (`tokenConPayload`) o payloads atípicos; el backend
  siempre emite `sub` (SPEC-003 §6.3). Mitigación: `username?: string`
  opcional y fallback en `Layout` (se omite el nombre, se muestra solo el
  rol); `decodificarJwt` nunca devuelve `null` por falta de `sub` (la
  validación de `role`/`clienteId` no cambia — no se debilita ERR-002).
- **R3 — `jwt.test.ts` con `toEqual`:** dos aserciones requieren el ajuste
  aditivo `username: 'usuario-test'` (§8.1). Es el único cambio de test
  existente, justificado por la spec (§10, A-008): **aditivo, no debilita**
  ningún criterio. Si el implementador eligiera devolver `username` siempre
  (incluido `undefined`), las aserciones sin `sub` seguirían pasando
  (`toEqual` ignora `undefined`); la opción prescripta (clave solo cuando hay
  string) es la más limpia.
- **R4 — Refactor de `index.css` con valores hardcodeados residuales:** riesgo
  de dejar un hex fuera de `:root` (BR-002). Mitigación: verificación AC-003
  (grep en `.tsx`) **+** revisión/grep de `index.css`; los `rgba` de sombras
  solo dentro de la definición de `--sombra-*`.
- **R5 — Textos exactos asertados por la suite:** "ARS" (conteo 2), estados
  `ACTIVA`/`BLOQUEADA`, tipos de movimiento en crudo, "Cargando...",
  "Reintentar", labels y textos de botones/links. El rediseño es aditivo y no
  debe renombrar ni duplicar estos textos en el mismo árbol (BR-003).
- **R6 — Roles ARIA:** `role="status"` debe seguir siendo único en el render
  inicial de `CuentasPage` (AC-030); `role="alert"` de errores y de
  `CampoFormulario` se conserva (BR-006). El header no agrega roles de estado.
- **R7 — Restricciones estructurales de tests:** `.seccion`, `.campo` y
  `<section>` (CajaSection) son consultadas por `closest(...)` — no se
  renombran ni se cambia su anidamiento.
- **R8 — `@media` sin variables CSS:** los breakpoints van como literales
  (A-006); duplicar el breakpoint en la guía y en las media queries puede
  desincronizarse — mitigación: revisión de AC-011 y comentario en `index.css`
  apuntando a la guía.
- **R9 — Cambio de jerarquía de headings:** la marca no es heading; el `h1`
  único por página ("Iniciar sesión", "Mis cuentas", "Gestión") se conserva —
  no hay queries de heading sin nombre en la suite (verificado), pero la
  jerarquía es un objetivo de accesibilidad (FR-010).
- **R10 — Regresión visual no detectada por tests:** los tests no verifican
  estilos (class-agnostic). Mitigación: revisión visual en `npm run dev`
  (AC-011) y la guía como fuente de verdad de tokens (AC-002).

---

## 10. Alternatives Considered

- **Sidebar vs header (A-002):** el issue permitía "sidebar o header". Se
  elige **header**: la SPA tiene navegación de primer nivel mínima (1-2 links
  por rol), el contenido (tarjetas de cuenta) es el foco de la app bancaria, y
  un sidebar fijo agrega complejidad de layout responsive en móvil sin
  beneficio (AGENTS.md §11: preferir la solución más simple). El header vive
  en `Layout` y se adapta en móvil con `flex-wrap` (sin hamburguesa ni
  librerías JS).
- **Framework CSS / librería de UI vs CSS plano con tokens (A-003, BR-005):**
  Tailwind/MUI agregarían una dependencia y un sistema de estilos ajeno a la
  política de ADR-008 y AGENTS.md §13 (sin dependencias sin justificación).
  Se mantiene CSS plano con variables en el único `src/index.css`.
- **Archivo de tokens separado (`tokens.css`) vs bloque `:root` en
  `index.css` (A-003):** la spec no exige un archivo separado; un solo
  `index.css` con el bloque `:root` + secciones comentadas evita una hoja
  extra y un punto de importación adicional, cumpliendo AC-002/AC-003.
  **Elegido `:root` en `index.css`.**
- **Mostrar el usuario actual vs no mostrarlo:** el issue exige header con
  marca/usuario/logout (FR-003); la única forma de obtener el `username` sin
  cambiar contratos es leer el claim `sub` del JWT ya emitido (A-005,
  SPEC-003 §6.3). Alternativas descartadas: pedir un endpoint `/me` (cambio de
  backend — BR-001) o agregar `rol`/`username` al `LoginResponse` (cambio de
  contrato — mismo motivo). La decodificación client-side del claim `sub` es
  una extensión directa del mecanismo ya registrado en ADR-008 (decisión 4:
  claims del JWT para UX).
- **Marca como link al home del rol vs texto estático:** un link agregaría un
  destino accesible "extra" sin requerimiento; texto estático (span) evita
  colisiones y ambigüedad de navegación. **Elegido texto estático** (A-009).
- **Agrandar `.monto` globalmente vs wrapper `.cuenta-saldo`:** agrandar el
  componente global afectaría movimientos y secciones de gestión; el wrapper
  aditivo localiza el énfasis en el saldo de `/cuentas`. **Elegido wrapper.**
- **Spinner con librería de iconos vs CSS puro (A-010):** sin dependencias;
  spinner/íconos con CSS o caracteres unicode.

---

## 11. Decision

Implementar SPEC-009 como rediseño **frontend-only, presentacional y aditivo**
de la SPA de SPEC-006, siguiendo la guía `docs/design/ui-guide.md` (definida
primero — FR-001) y materializándola en variables CSS del `:root` de
`src/index.css` (FR-002), con:

- **Tokens** `--color-*`, `--estado-*`, `--font-*`, `--espacio-*`,
  `--sombra-*`, `--radio-*` según §5.1 (paleta derivada del CSS actual:
  `#1f6feb`/`#102a43` primarios, neutros `#f5f7fa`/`#ffffff`/`#d9e2ec`/
  `#1f2933`/`#486581`, estados de éxito/error/advertencia/info, escala de
  espaciado base 4 px, sombras y radios 4/8/12/16 px) con verificación de
  contraste AA (A-007) y sin valores literales fuera de `:root` (BR-002).
- **Header en `Layout`** (FR-003): marca "Banco" (texto, no heading/link),
  navegación por rol preservada, chip `username` + `rol` (username del claim
  `sub` del JWT — A-005, §5.8, con fallback si está ausente) y logout intacto.
  **Login** en tarjeta centrada (FR-004); **Cuentas** como tarjetas con saldo
  destacado vía `.cuenta-saldo` y movimientos legibles (FR-005); **Transferencia**
  con formulario claro (FR-006); **Gestión** con secciones consistentes
  (FR-007); **estados** rediseñados conservando roles ARIA y textos (FR-008).
- **Responsive mobile-first** (min-width 640/768/1024 — FR-009) y
  **`:focus-visible`** global (FR-010, BR-007).
- **Sin cambios** de API, rutas, props, payloads, autorización ni
  dependencias (BR-001, BR-004, BR-005, BR-008); **158 tests existentes
  pasando sin debilitarse**, con los tests nuevos de AC-004/AC-010 y el único
  ajuste aditivo justificado en `jwt.test.ts` (§8, A-008).
- **No se crea ningún ADR** (ver nota siguiente).

### Decisión sobre ADR

**No se crea un ADR nuevo para SPEC-009.** Justificación:

1. Las decisiones de este rediseño — **header sobre sidebar** (A-002),
   **sistema de diseño en variables CSS** (A-003/A-004) y **username del
   header vía claim `sub` del JWT** (A-005) — son **refinamientos
   presentacionales** de decisiones arquitectónicas ya registradas en
   **ADR-008**: decisión 1 (SPA con CSS plano, sin librerías de UI), decisión
   4 (lectura de claims del JWT client-side para UX, con el backend como punto
   de enforcement) y decisión 5 (estado con contexto de sesión).
2. Ninguna de ellas cambia el enfoque arquitectónico, el contrato de la API,
   el modelo de autorización ni agrega infraestructura: no son decisiones
   arquitectónicas significativas (AGENTS.md §16) sino decisiones de diseño de
   presentación, resueltas además como asunciones documentadas en la spec
   (A-001..A-010, §15).
3. Mostrar el `username` client-side **no altera la postura de seguridad**: el
   claim `sub` ya viaja en todo token emitido, no expone datos sensibles y la
   autorización real sigue verificándose server-side en cada endpoint
   (spec §9, ADR-008 consecuencias); no constituye un cambio en "cómo se
   establece la identidad", solo en "cómo se presenta".
4. Un ADR nuevo duplicaría registros existentes sin aportar información
   arquitectónica nueva (AGENTS.md §16: documentar decisiones *importantes*).

**Gatillos para un ADR futuro** (si ocurren, sí ameritan ADR): cambiar cómo se
obtiene la identidad del usuario (p. ej. endpoint `/me`, cookies `httpOnly`),
cambiar el almacenamiento de la sesión, adoptar un framework CSS/librería de
UI, o introducir dark mode con estrategia de tokens multicapa.

---

## 12. Related Documents

- `docs/specs/SPEC-009-ui-redesign.md` (spec aprobada — fuente de verdad;
  FR-001..FR-012, BR-001..BR-009, AC-001..AC-018, A-001..A-010)
- `docs/specs/SPEC-006-frontend-react.md` (SPA baseline: FR-009..FR-015,
  BR-001..BR-010, AC-001..AC-031, A-001..A-008)
- `docs/specs/SPEC-007-caja-gestion.md`, `docs/specs/SPEC-008-usuarios-gestion.md`
  (secciones de `/gestion`)
- `docs/specs/SPEC-003-autenticacion.md` §6.3 (contrato de claims del JWT:
  `sub = username`, `role`, `clienteId`)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend que este
  rediseño refina — ver §11)
- `docs/architecture/SPEC-006.md` (diseño de la SPA baseline), `SPEC-007.md`,
  `SPEC-008.md`
- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (API REST y envelope), §8
  (seguridad y propiedad)
- `docs/design/ui-guide.md` (guía de diseño — entregable de FR-001, nueva)
- Issue #28 (IgnaGrego/sistema-banco) — requisito del rediseño