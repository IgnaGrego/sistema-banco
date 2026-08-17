# SPEC-009 — Rediseño de UI del Frontend (sistema de diseño y look bancario moderno)

## Status

Approved

---

## 1. Objective

Rediseñar la SPA (SPEC-006) para que se vea como una **app bancaria moderna**
(issue #28): tarjetas de saldo, navegación limpia, paleta profesional (azul
institucional + neutros), tipografía, espaciado, sombras y redondeos
consistentes a través de **variables CSS** (sistema de diseño), header con
marca/usuario/logout, login centrado en tarjeta, cuentas como tarjetas con
**saldo destacado**, movimientos legibles, formulario de transferencia claro,
secciones de gestión organizadas y estados de carga/vacío/error atractivos.
Incluye **responsive básico mobile-first** y **accesibilidad** (contraste AA,
labels, focus visible).

El issue exige además **definir primero una breve guía de diseño en `docs/`
antes de implementar** (FR-001): el documento `docs/design/ui-guide.md` es un
entregable de esta spec.

La spec es **frontend-only**: no cambia contratos de la API ni comportamiento
de negocio, y **todos los tests existentes (158) deben seguir pasando**
(BR-001, BR-003, A-008). No introduce reglas de negocio nuevas: solo
presentación sobre los componentes, páginas y flujos ya existentes.

---

## 2. Actors

- **CLIENTE**: usa la UI rediseñada en `/cuentas` — header con marca, su
  username y rol, logout; tarjetas de cuenta con saldo destacado, movimientos
  legibles y el formulario de transferencia claro (FR-003/005/006). El login
  rediseñado (FR-004) es su puerta de entrada.
- **ADMIN**: usa la UI rediseñada en `/gestion` — header con marca, su username
  y rol, logout; las cuatro secciones organizadas con estilo consistente
  (clientes, cuentas, caja, usuarios — FR-003/007).
- **Usuario no autenticado**: ve la pantalla de login rediseñada (tarjeta
  centrada con marca — FR-004). No accede a vistas protegidas (SPEC-006
  FR-006, sin cambios).

---

## 3. Preconditions

- La SPA de SPEC-006 está implementada y mergeada, con sus 158 tests pasando
  (`npm test`), y los scripts `lint`, `typecheck` y `build` operativos
  (SPEC-006 FR-001, AC-001..AC-005).
- Existen las secciones de `/gestion` de SPEC-007 (`CajaSection`) y SPEC-008
  (`UsuariosSection`), con sus tests pasando.
- **La guía de diseño `docs/design/ui-guide.md` se define y aprueba ANTES de
  implementar el resto del rediseño** (requisito explícito del issue #28,
  FR-001): ninguna tarea de rediseño de componentes/páginas comienza sin la
  guía aprobada.
- El backend está corriendo y expone la API en `/api/v1` (proxy de Vite —
  SPEC-006 FR-008, A-001); el rediseño NO requiere el backend para su
  verificación automatizada (tests con mocks de `fetch` — helpers de
  SPEC-006), pero sí para la revisión visual manual en `npm run dev`.
- Sin cambios en backend, BD, migraciones ni `docker/` (BR-001, §10).

---

## 4. Functional Requirements

### FR-001 — Guía de diseño en `docs/` (definida primero)

Crear la guía breve **`docs/design/ui-guide.md`** (nueva carpeta
`docs/design/`; A-001) que define, con valores concretos:

- **Paleta**: azul institucional (primario + oscuro) y neutros, con los
  valores exactos de cada color y su uso (fondo, superficie, borde, texto,
  texto suave, primario, primario oscuro).
- **Colores semánticos de estado**: éxito, error, advertencia e info (fondo +
  borde + texto) para alertas y confirmaciones.
- **Tipografía**: familia (stack existente de `system-ui`) y escala de
  tamaños para títulos, subtítulos, cuerpo, texto pequeño, montos destacados.
- **Escala de espaciado** (p. ej. base 4 px) y **sombras** y **redondeos**
  con valores exactos.
- **Breakpoints** mobile-first (A-006) y **objetivos de accesibilidad**
  (contraste AA — A-007, focus visible).

La guía es la **única fuente de verdad de los tokens** (A-001): los valores
deben coincidir con las variables CSS de FR-002. Se entrega antes de la
implementación de FR-003..FR-010 (verificación: AC-001).

### FR-002 — Sistema de diseño con CSS custom properties

Definir en **`src/index.css`** (única hoja existente, CSS plano — sin
framework, sin dependencias: ADR-008, AGENTS.md §13) un bloque `:root` con
variables CSS que materializan la guía (FR-001):

- `--color-*` (paleta y roles), `--estado-*` (semánticos: éxito/error/
  advertencia/info), `--font-*` (familia/escala), `--espacio-*` (escala),
  `--sombra-*`, `--radio-*` (A-004).

Todas las reglas de estilo de la hoja usan estas variables; **no se escriben
valores de color (ni de espaciado/radio/sombra) hardcodeados fuera de `:root`**
(BR-002). Se permite reorganizar `index.css` en bloques comentados (layout,
componentes, páginas, estados, responsive); no se exige un archivo de tokens
separado (A-003).

### FR-003 — Layout: header con marca, usuario actual y logout

Rediseñar `Layout` con un **header** (se elige header sobre sidebar — A-002)
que contiene, en este orden y en todas las vistas autenticadas
(`/cuentas`, `/gestion`):

1. **Marca "Banco"** (texto estilizado con tokens — A-009).
2. **Navegación por rol preservada**: link "Mis cuentas" (`CLIENTE`) y
   "Gestión" (`ADMIN`) — mismos textos y destinos que hoy (sin rutas nuevas,
   BR-004; los tests existentes por `getByRole('link', ...)` siguen pasando).
3. **Usuario actual**: `username` + `rol` del usuario autenticado. El
   `username` se lee del claim `sub` del JWT (SPEC-003 §6.3) — extensión
   frontend-only de la decodificación (A-005); el `rol` ya está en el contexto
   de sesión.
4. **Botón "Cerrar sesión"** con el comportamiento actual intacto (limpia
   token y redirige a `/login` — SPEC-006 FR-014, AC-012).

El contenedor principal `.layout-main` se mantiene **centrado con ancho
máximo** (actual `60rem`, ajustable dentro de los tokens) con padding
consistente. Estructura semántica `<header>`/`<nav>`/`<main>` y navegación
accesible (links reales, focus visible — BR-007).

### FR-004 — Login: tarjeta centrada con marca

Rediseñar `/login` (`LoginPage`): la sección se presenta como **tarjeta
centrada** (uso de tokens de superficie/sombra/radio/espaciado) con la marca
"Banco" (A-009) y el título "Iniciar sesión". El formulario conserva **todos
los elementos, pre-validaciones y comportamiento actuales** (campos
`username`/`password` con `CampoFormulario`, BR-001 de SPEC-006, manejo de
`401` con mensaje genérico y de `ERROR_RED`, redirección por rol — SPEC-006
FR-005, AF-001, ERR-001): solo cambia la presentación (BR-008).

### FR-005 — Cuentas: tarjetas con saldo destacado y movimientos legibles

Rediseñar `/cuentas` (`CuentasPage`):

- Cada cuenta se presenta como **tarjeta** (superficie, sombra, radio,
  espaciado) con el **saldo destacado** (jerarquía visual: el monto
  formateado por `Monto` es el elemento visual dominante de la tarjeta). Se
  conservan los datos actuales (tipo con `ETIQUETA_TIPO`, `CBU: ...`, saldo
  ARS, moneda, estado `ACTIVA`/`BLOQUEADA`) y el botón "Ver movimientos" /
  "Ocultar movimientos" con el comportamiento expandir/colapsar intacto.
- El **historial de movimientos** se presenta como lista legible
  (alineación, jerarquía, separación). Se permite diferenciación visual por
  tipo (p. ej. acentos de color vía clases), pero **sin alterar los textos
  visibles que los tests asertan** (`DEPOSITO`, `TRANSFERENCIA_SALIENTE`,
  etc. — BR-003).
- Se conservan los estados de carga/error/vacío y los mensajes actuales
  (BR-003, FR-008).

### FR-006 — Transferencia: formulario claro

Rediseñar la sección de transferencia dentro de `/cuentas`
(`TransferenciaPage`): presentación clara del formulario (selector "Cuenta
origen", "CBU destino", "Monto", botón "Transferir") y del panel de
confirmación. **Sin cambios de comportamiento**: mismas pre-validaciones
(BR-002..BR-005 de SPEC-006), mismo payload `{cuentaOrigenId, cbuDestino,
monto}`, mismo manejo de `201`/`400`/`422`/errores de red y mismos textos
(BR-003, BR-008).

### FR-007 — Gestión: secciones organizadas y consistentes

Rediseñar `/gestion` (`GestionPage` y sus cuatro secciones — `ClientesSection`,
`CuentasSection`, `CajaSection`, `UsuariosSection`): todas las secciones
comparten un **estilo de sección consistente** (título, tarjeta/contenedor,
espaciado, formularios y listas con los mismos tokens). Se conserva el
comportamiento completo de SPEC-006 FR-012/013, SPEC-007 y SPEC-008
(payloads, validaciones, confirmaciones, errores, estados) y sus textos
asertados (BR-003).

### FR-008 — Estados de carga, vacío y error atractivos

Rediseñar visualmente los componentes de estado `Cargando`, `EstadoVacio` y
`EstadoError` (tarjetas/indicadores con tokens). **Se conservan**:

- `Cargando`: `role="status"` y el texto "Cargando..." (BR-006; AC-030 de
  SPEC-006 aserta ambos).
- `EstadoVacio`: el mensaje descriptivo recibido por prop (sin cambios en la
  interfaz de props — A-008).
- `EstadoError`: `role="alert"`, el mensaje del envelope y el botón
  "Reintentar" (BR-006; AC-029 de SPEC-006 aserta el botón).

La iconografía (si se agrega) usa solo caracteres/estilos CSS, sin
dependencias ni assets nuevos (A-010).

### FR-009 — Responsive básico, mobile-first

Diseño **mobile-first**: layout de una columna en móvil que aprovecha el
ancho en pantallas mayores mediante media queries `min-width` (breakpoints de
la guía — A-006); unidades relativas (`rem`); las tarjetas, el header y los
formularios se adaptan sin romper el layout en pantallas de **≥ 360 px de
ancho** (BR-009). Sin librerías JS de layout.

### FR-010 — Accesibilidad

- **Contraste**: los pares texto/fondo de la paleta y de los estados cumplen
  **WCAG AA** (4.5:1 texto normal; 3:1 texto grande y componentes UI — A-007);
  se verifica al definir la paleta en la guía (FR-001).
- **Labels**: se mantiene la asociación `label ↔ input` vía
  `CampoFormulario` (los tests por `getByLabelText` siguen pasando).
- **Focus visible**: `:focus-visible` visible en todos los elementos
  interactivos (inputs, selects, botones, links — BR-007).
- **ARIA**: se conservan `role="status"` y `role="alert"` de estados y
  alertas (BR-006).

### FR-011 — Tests: comportamiento preservado + tests nuevos

- **Tests nuevos** de componente (Vitest + RTL) donde el rediseño agrega
  comportamiento visible: header con marca, username (del claim `sub`) y rol
  (AC-004), y estados rediseñados (AC-010).
- **Tests existentes**: los 158 deben seguir pasando **sin debilitarse**
  (AGENTS.md §12, A-008): el rediseño es presentacional y aditivo (BR-003).

### FR-012 — Restricciones de contrato y dependencias

- Sin cambios de contratos de API, payloads, manejo del envelope ni
  autorización (BR-001, BR-008).
- Sin rutas nuevas (BR-004).
- Sin dependencias npm nuevas ni frameworks CSS (BR-005, ADR-008,
  AGENTS.md §13).

---

## 5. Business Rules

Las reglas siguientes no son reglas de negocio del dominio: son **reglas de
consistencia del rediseño** que garantizan que el cambio es puramente de
presentación y no rompe contratos ni tests.

### BR-001 — Sin cambios de contrato de API ni de backend

Ningún endpoint, payload, respuesta, código de error o comportamiento de
autorización cambia. El backend no se toca (tampoco migraciones ni `docker/`).
El rediseño solo modifica la capa de presentación del frontend (FR-012, §10).

### BR-002 — Tokens CSS en todo el estilo; sin colores hardcodeados en componentes

Todos los valores de color (y de tipografía, espaciado, sombra y radio) de
`index.css` provienen de las variables `:root` de FR-002. Ningún componente
`.tsx` define estilos con valores de color literales (hex/rgb/hsl/nombres) ni
usa tokens inexistentes (BR-002 se verifica con grep — AC-003). La única
excepción permitida son los valores de color dentro de la **definición** de
las variables en `:root`.

### BR-003 — Preservación de los textos que los tests asertan

El rediseño no altera el **texto visible** que los tests actuales asertan, ni
agrega texto duplicado que colisione con aserciones de cantidad. Casos
críticos verificados en la suite actual:

- Tipos de movimiento en crudo: `DEPOSITO`, `TRANSFERENCIA_SALIENTE`
  (CuentasPage AC-016).
- Moneda `ARS` con **cantidad exacta** por cuenta (2 cuentas → 2 "ARS";
  CuentasPage AC-014): la tarjeta rediseñada muestra la moneda **una sola
  vez** por cuenta.
- Estados en crudo: `ACTIVA`, `BLOQUEADA` (CuentasPage AC-014).
- `Cargando...` con `role="status"` (AC-030) y botón `Reintentar` con
  `role="alert"` (AC-029).
- Labels y textos de botones/links: "Mis cuentas", "Gestión", "Cerrar
  sesión", "Ver movimientos", "Transferir", "Cuenta origen", "CBU destino",
  "Monto", mensajes de estados vacíos y de confirmación.

Si el rediseño necesita texto adicional (p. ej. un subtítulo), debe ser
**aditivo** y no duplicar textos asertados en el mismo árbol renderizado.

### BR-004 — Sin rutas nuevas

La estructura de rutas (`/login`, `/cuentas`, `/gestion`, `*`) y el guard
`ProtectedRoute` por rol no cambian (SPEC-006 FR-006); solo se rediseña la
presentación de cada vista.

### BR-005 — Sin dependencias npm nuevas ni frameworks CSS

No se agregan librerías (UI, CSS, iconos, layout) — AGENTS.md §13, ADR-008.
El sistema de diseño es CSS plano con variables (FR-002).

### BR-006 — Roles ARIA y textos de estados preservados

`Cargando` mantiene `role="status"` y "Cargando..."; `EstadoError` mantiene
`role="alert"`, el mensaje y el botón "Reintentar"; los mensajes de éxito/
confirmación mantienen `role="status"`; las alertas de formulario mantienen
`role="alert"` (FR-008, FR-010).

### BR-007 — Focus visible en todos los elementos interactivos

Todo elemento interactivo (input, select, botón, link) muestra un indicador
de foco visible con `:focus-visible` (anillo/outline con color de contraste
suficiente), definido en `index.css` (FR-010).

### BR-008 — Comportamiento de formularios, validaciones y payloads intacto

Las pre-validaciones UX (mirrors del backend — SPEC-006 BR-001..BR-010,
SPEC-007 BR-001..BR-004, SPEC-008 BR-001..BR-005) y el envío de payloads
exactos no cambian; el backend permanece como punto de enforcement
(AGENTS.md §10). El rediseño solo afecta la presentación.

### BR-009 — Layout usable en pantallas ≥ 360 px (mobile-first)

El layout se construye mobile-first (una columna) y no se rompe
(desbordes, solapamientos) en anchos de **360 px en adelante** (FR-009).

---

## 6. Main Flow

Los flujos de la SPA **no cambian** (SPEC-006 §6, SPEC-007, SPEC-008); el
rediseño solo cambia la presentación. Flujo principal con la nueva UI:

1. El usuario abre la SPA sin sesión y ve la **tarjeta de login centrada**
   con la marca "Banco" (FR-004).
2. Ingresa `username`/`password` y envía (mismas pre-validaciones y manejo de
   `401`/`ERROR_RED` — BR-008); con `200 {token}` se guarda la sesión y se
   redirige por rol (`CLIENTE` → `/cuentas`, `ADMIN` → `/gestion` — SPEC-006
   FR-005).
3. En las vistas autenticadas, el **header rediseñado** (FR-003) muestra
   marca, links de navegación por rol, `username` + `rol` y "Cerrar sesión".
4. **CLIENTE** en `/cuentas`: ve sus **tarjetas de cuenta con saldo
   destacado** (FR-005), expande/colapsa el historial (mismos datos y
   llamadas) y usa el formulario de transferencia claro (FR-006); ante `201`
   se muestra la confirmación y se refrescan cuentas y movimientos (SPEC-006
   AF-004).
5. **ADMIN** en `/gestion`: ve las **cuatro secciones organizadas** con
   estilo consistente (FR-007) y opera clientes, cuentas, caja y usuarios con
   el mismo comportamiento (SPEC-006 FR-012/013, SPEC-007, SPEC-008).
6. El usuario cierra sesión desde el header: se limpia el token y se vuelve a
   `/login` (SPEC-006 FR-014, AC-012 — comportamiento intacto).

---

## 7. Alternative Flows

Todos los flujos alternativos existentes **se mantienen sin cambios**; el
rediseño solo les aplica la nueva presentación:

- **AF-001 (SPEC-006) — Login fallido**: `401` → mensaje genérico en la
  tarjeta de login, sin token, permanece en `/login` (presentación
  rediseñada).
- **AF-002 (SPEC-006) — Token expirado/inválido**: `401` en request
  autenticada → limpieza de sesión y redirección a `/login` (FR-007 de
  SPEC-006, sin cambios).
- **AF-003 (SPEC-006) — CLIENTE sin cuentas**: estado vacío rediseñado
  (FR-008) con el mismo mensaje; formulario de transferencia sin cuentas
  origen (BR-003).
- **AF-004 (SPEC-006) — Transferencia exitosa**: confirmación rediseñada con
  los mismos datos y refresh de cuentas/movimientos.
- **AF-005 (SPEC-006) — Acción prohibida por rol (403)**: mensaje del
  envelope en la vista; ocultamiento por rol intacto (BR-008).
- **AF-006 (SPEC-006) — ADMIN abre cuenta**: lista refrescada, misma lógica.
- **AF-001..AF-003 (SPEC-008) — Registro de usuario**: rol `ADMIN` sin
  selector de cliente, `409` por username duplicado, reintento tras error de
  red — comportamiento intacto (BR-008).

---

## 8. Error Cases

Los errores de la API usan el envelope estándar `{ code, message, details? }`
(`ARCHITECTURE.md` §7) y **su manejo no cambia** (SPEC-006 §8, SPEC-007 §8,
SPEC-008 §8): la presentación del error se rediseña visualmente (FR-008,
FR-010) pero conserva mensajes, `details` por campo, roles ARIA y botones:

- **ERR-001 (SPEC-006) — Credenciales inválidas (`401` en login)**: mensaje
  genérico en la tarjeta de login; sin token (BR-003).
- **ERR-002 (SPEC-006) — Token ausente/expirado/mal firmado**: limpieza de
  sesión y redirección a `/login`.
- **ERR-003 (SPEC-006) — Acceso denegado (`403`)**: mensaje del envelope en
  la vista activa.
- **ERR-004 (SPEC-006) — Datos inválidos (`400` con `details`)**: errores por
  campo vía `CampoFormulario`, datos conservados (BR-008).
- **ERR-005 (SPEC-006) — Recurso inexistente (`404`)**: mensaje informativo.
- **ERR-006 (SPEC-006) — Regla de negocio (`422`)**: mensaje en el
  formulario sin perder datos.
- **ERR-007 (SPEC-006) — Conflicto (`409`)**: mensaje de conflicto con
  invitación a reintentar.
- **ERR-008 (SPEC-006) — Error de red/backend no disponible**: mensaje de
  conexión con reintento (`EstadoError` rediseñado, mismo texto asertado —
  BR-003).
- **ERR-009 (SPEC-006) — Estados vacíos**: `EstadoVacio` rediseñado con los
  mismos mensajes.
- **ERR-001..ERR-005 (SPEC-007/SPEC-008)**: mismo tratamiento (BR-008).

---

## 9. Authorization

**Sin cambios.** El rediseño no toca:

- El guard de rutas `ProtectedRoute` por rol (SPEC-006 FR-006): sin sesión →
  `/login`; `CLIENTE` solo `/cuentas`; `ADMIN` solo `/gestion`.
- La redirección post-login por rol (SPEC-006 FR-005).
- El ocultamiento de acciones por rol (SPEC-006 FR-015, BR-008).
- La autorización server-side en cada endpoint (AGENTS.md §17: el frontend
  nunca es el punto de enforcement).

`CLIENTE` y `ADMIN` ven la misma UI rediseñada en sus respectivas vistas; la
única información de sesión adicional que se muestra es el `username` del
header (FR-003), leída del claim `sub` del JWT (A-005), que no expone datos
sensibles y no afecta autorización.

---

## 10. Data Changes

**Backend y base de datos: sin cambios.** No hay migraciones Flyway, ni
tablas, ni endpoints, ni contratos modificados (BR-001).

**Frontend (solo):**

- `docs/design/ui-guide.md` — **nuevo** (FR-001): la guía de diseño,
  definida antes de la implementación.
- `src/index.css` — **reescrito**: bloque `:root` con los tokens (FR-002) y
  todas las reglas de las vistas rediseñadas (FR-003..FR-010). Única hoja de
  estilos, sin dependencias (A-003).
- `src/lib/jwt.ts` y `src/api/types.ts` — **extendidos**: decodificación del
  claim `sub` (username) del JWT; `JwtClaims` gana `username?: string`
  (A-005; SPEC-003 §6.3 — `sub = username`). Sin cambio de contrato de API.
- `src/store/auth-context.tsx` — **extendido**: el estado de sesión expone
  `username` (A-005), junto a `token`, `rol`, `clienteId`.
- `src/components/Layout.tsx` — **rediseñado**: marca "Banco", `username` +
  `rol` del usuario actual, navegación por rol y logout intactos (FR-003).
- `src/components/Cargando.tsx`, `EstadoVacio.tsx`, `EstadoError.tsx`,
  `CampoFormulario.tsx`, `Monto.tsx` — ajustes de **presentación** (clases/
  estructura mínima aditiva); props y roles ARIA intactos (FR-008, BR-006).
- `src/pages/LoginPage.tsx`, `CuentasPage.tsx`, `TransferenciaPage.tsx`,
  `GestionPage.tsx` y `src/pages/gestion/*Section.tsx` — ajustes de
  **presentación** (clases CSS y wrappers de estilo); lógica, payloads,
  validaciones y textos intactos (FR-004..FR-007, BR-003, BR-008).
- Tests — `src/components/Layout.test.tsx` **extendido** (marca/usuario/rol —
  AC-004) y tests nuevos de estados si aplica (AC-010); los 158 tests
  existentes **no se modifican salvo ajustes aditivos justificados** (A-008).

**Sin cambios:** `package.json` / `package-lock.json` (BR-005), `App.tsx`
(rutas — BR-004), `src/api/*` (contratos), backend, migraciones, `docker/`.

---

## 11. Acceptance Criteria

**Estrategia de testing:** la suite de la SPA con `npm test` (Vitest + React
Testing Library) verifica los criterios de comportamiento; `npm run lint`,
`npm run typecheck` y `npm run build` verifican calidad; los criterios de
tokens/estilos se verifican por inspección de código (grep/revisión) y
revisión visual en `npm run dev`. Cada AC indica su método de verificación.

Guía de diseño y sistema de diseño:

- [ ] AC-001: Existe `docs/design/ui-guide.md` con el contenido mínimo de
      FR-001 (paleta con valores, colores semánticos de estado, tipografía,
      espaciado, sombras, redondeos, breakpoints y objetivos de accesibilidad
      AA) y se definió antes de la implementación del resto del rediseño
      (FR-001) — verificación: existencia + revisión del documento.
- [ ] AC-002: `src/index.css` define en `:root` variables `--color-*`,
      `--estado-*`, `--font-*`, `--espacio-*`, `--sombra-*` y `--radio-*`
      cuyos valores coinciden con la guía (FR-002) — verificación: grep de
      variables + cotejo con `docs/design/ui-guide.md`.
- [ ] AC-003: Ningún archivo `.tsx` contiene valores de color literales
      (hex/rgb/hsl/nombres de color) en estilos; todo color proviene de
      variables CSS (BR-002) — verificación: `grep -rE '#[0-9a-fA-F]{3,6}|rgb\(|hsl\(' src --include='*.tsx'` sin resultados (salvo comentarios).
- [ ] AC-004: Component test — el header (Layout) muestra la marca "Banco",
      el `username` (claim `sub`) y el `rol` del usuario autenticado, el
      botón "Cerrar sesión" y los links de navegación por rol ("Mis cuentas"
      para `CLIENTE`; "Gestión" para `ADMIN`) (FR-003, A-005).
- [ ] AC-005: Component test — el logout desde el header rediseñado limpia el
      token de `localStorage` y redirige a `/login` (test existente AC-012 de
      SPEC-006 sigue pasando sin cambios) (FR-003).

Login:

- [ ] AC-006: Component test — `/login` renderiza la tarjeta centrada con la
      marca y el formulario; con credenciales correctas envía
      `POST /api/v1/auth/login` con `{username, password}` y redirige por
      rol; con `401` muestra el mensaje genérico sin token (tests existentes
      AC-008/009 de SPEC-006 pasan sin cambios) (FR-004, BR-008).

Cuentas y transferencia:

- [ ] AC-007: Component test — `/cuentas` renderiza las tarjetas de cuenta con
      saldo formateado ARS destacado, `cbu`, tipo, moneda (una vez por
      cuenta) y estado; expandir una cuenta dispara
      `GET /api/v1/cuentas/{id}/movimientos` y renderiza el historial
      (tests existentes AC-014/016 de SPEC-006 pasan sin cambios) (FR-005,
      BR-003).
- [ ] AC-008: Component test — el formulario de transferencia envía el
      payload exacto `{cuentaOrigenId, cbuDestino, monto}` y ante `201`
      muestra la confirmación y refresca cuentas/movimientos; ante `422`
      conserva los datos (tests existentes AC-017..020 de SPEC-006 pasan sin
      cambios) (FR-006, BR-008).

Gestión:

- [ ] AC-009: Component test — `/gestion` renderiza las cuatro secciones
      (Clientes, Cuentas, Caja, Usuarios) con el mismo estilo de sección y
      con su comportamiento intacto (tests existentes de SPEC-006/007/008
      pasan sin cambios) (FR-007).

Estados y accesibilidad:

- [ ] AC-010: Component test — `Cargando` conserva `role="status"` y
      "Cargando..."; `EstadoError` conserva `role="alert"`, el mensaje y el
      botón "Reintentar"; los mensajes de confirmación conservan
      `role="status"` (tests existentes AC-029/030 de SPEC-006 pasan sin
      cambios) (FR-008, BR-006).
- [ ] AC-011: `index.css` contiene media queries mobile-first (`min-width`)
      y el layout usa unidades relativas (`rem`); revisión manual en
      `npm run dev` confirma que las vistas no se rompen en 360 px y en
      desktop (FR-009, BR-009) — verificación: grep de `@media` + revisión
      visual.
- [ ] AC-012: `index.css` define estilos `:focus-visible` para inputs,
      selects, botones y links; los labels de `CampoFormulario` se mantienen
      (los tests por `getByLabelText` pasan); la paleta cumple contraste AA
      según la guía (FR-010, BR-007) — verificación: grep + cotejo con la
      guía + revisión.

Calidad y restricciones:

- [ ] AC-013: `npm run lint` y `npm run typecheck` pasan sin errores; `npm
      run build` produce el bundle (FR-011).
- [ ] AC-014: `npm test` ejecuta la suite completa y todos los tests pasan
      (los 158 existentes sin debilitarse + los nuevos de AC-004/AC-010)
      (FR-011, BR-003, A-008).
- [ ] AC-015: Sin dependencias npm nuevas: `package.json` y
      `package-lock.json` no incorporan dependencias (BR-005, AGENTS.md §13)
      — verificación: diff de `package.json`/`package-lock.json`.
- [ ] AC-016: Sin cambios en backend, migraciones ni `docker/` (BR-001) —
      verificación: `git diff` sin archivos fuera de `frontend/` y
      `docs/design/`.
- [ ] AC-017: Sin rutas nuevas: `App.tsx` y `ProtectedRoute` sin cambios de
      estructura (BR-004); el test existente AC-013 de SPEC-006 (guard de
      rutas por rol) pasa sin cambios.
- [ ] AC-018: Contratos intactos: los tests de payloads y manejo del envelope
      existentes pasan sin cambios (AC-008/017/023/025 de SPEC-006; AC-003/
      004 de SPEC-008; SPEC-007) (BR-001, BR-008).

---

## 12. Out of Scope

- **Cambios en el backend, API, migraciones o `docker/`**: frontend-only
  (BR-001, §10).
- **Cambios de contratos**: payloads, respuestas, códigos de error y
  autorización intactos (BR-001, BR-008).
- **Rutas nuevas**: estructura de rutas y guard por rol sin cambios (BR-004).
- **Dependencias npm nuevas, frameworks CSS o librerías de UI**: CSS plano con
  variables (BR-005, ADR-008, AGENTS.md §13). Sin librerías de iconos ni
  assets de imagen externos (A-009, A-010).
- **Cambios de comportamiento, validaciones o payloads**: pre-validaciones UX
  y manejo del envelope intactos (BR-008).
- **Cambios en los textos que los tests asertan** (tipos de movimiento en
  crudo, moneda, estados, mensajes, labels): el rediseño es presentacional
  (BR-003).
- **Rediseño de las props/API de los componentes existentes** (props de
  `Cargando`, `EstadoVacio`, `EstadoError`, `CampoFormulario`, `Monto`,
  `Layout`, `ProtectedRoute`): se mantienen para no romper tests (A-008).
- **i18n**: la SPA es solo en español (SPEC-006 §12).
- **Dark mode**: no requerido; solo si resulta trivial con los tokens, queda a
  criterio del implementador y documentado en la guía.
- **Accesibilidad en profundidad** (auditoría completa de lectores de
  pantalla, navegación por teclado exhaustiva, pruebas de herramientas de
  accesibilidad): solo los objetivos básicos de FR-010 (contraste AA, labels,
  focus visible, ARIA de estados).
- **Tests e2e** (Playwright/Cypress) y **tests visuales de snapshot por
  navegador**: solo lint, typecheck, unit/component tests y build (FR-011).
- **Cambios en el roadmap/backlog**: el rediseño no agrega historias de
  producto nuevas.

---

## 13. Dependencies

- **SPEC-006 (SPA baseline)**: componentes y páginas a rediseñar
  (`Layout`, `LoginPage`, `CuentasPage`, `TransferenciaPage`, `GestionPage`,
  `Cargando`, `EstadoVacio`, `EstadoError`, `CampoFormulario`, `Monto`),
  patrones de la SPA, estados de UI, tests existentes y AC de referencia
  (FR-003..FR-012, BR-003, §11).
- **SPEC-007 (caja)** y **SPEC-008 (usuarios)**: secciones de `/gestion` a
  rediseñar visualmente con comportamiento intacto (FR-007, BR-008).
- **SPEC-003 §6.3 (contrato de claims del JWT)**: `sub = username`, `role`,
  `clienteId` — fuente del `username` del header (FR-003, A-005).
- **ADR-008 (frontend)**: stack, sin librerías de UI, CSS plano, sesión y rol
  client-side (FR-002, FR-012, BR-005).
- **`ARCHITECTURE.md`** §1 (contenedor SPA), §7 (API REST y envelope de
  errores), §8 (seguridad y propiedad).
- **AGENTS.md** §13 (política de dependencias) y §12 (testing).
- **Issue #28** (requisito del rediseño; exige la guía de diseño primero).

---

## 14. Open Questions

- Ninguna pendiente: las ambigüedades del issue (header vs sidebar, ubicación
  y nombre de la guía de diseño, convención de tokens, breakpoints
  mobile-first, objetivo de contraste, origen del username del header y
  preservación de los tests) se resolvieron en modo autónomo como asunciones
  documentadas en §15. Si el Product Owner dispone otra cosa, deben ajustarse
  antes de la implementación.

---

## 15. Decisions and Assumptions

- **A-001 — Guía de diseño en `docs/design/ui-guide.md`, definida primero:**
  el issue exige "Definir primero una breve guia de diseno en docs/ antes de
  implementar". Se crea la carpeta `docs/design/` y la guía
  `docs/design/ui-guide.md` (breve, ~1-2 páginas) con el contenido mínimo de
  FR-001. Es la única fuente de verdad de los tokens; la implementación de
  FR-003..FR-010 no comienza sin la guía aprobada. Se descartan
  `docs/ui-guide.md` o `frontend/` como ubicación porque el issue pide
  explícitamente `docs/`.
- **A-002 — Header en lugar de sidebar:** el issue permite "sidebar o header
  de navegacion". Se elige **header**: la SPA tiene navegación de primer
  nivel mínima (1-2 links por rol), el contenido (tarjetas de cuenta) es el
  foco de la app bancaria, y un sidebar fijo agrega complejidad de layout
  responsive (móvil) sin beneficio (AGENTS.md §11: preferir la solución más
  simple). El header vive en `Layout` (todas las vistas autenticadas) con
  marca, navegación por rol, usuario actual y logout (FR-003).
- **A-003 — CSS plano con tokens, sin framework:** se mantiene la política de
  ADR-008/AGENTS.md §13 (sin librería de UI, sin framework CSS, sin
  dependencias npm). El sistema de diseño se materializa en variables CSS
  dentro del único `src/index.css` (bloque `:root` + secciones comentadas).
  No se exige un archivo de tokens separado (p. ej. `tokens.css`): la
  decisión de organizarlo en un solo archivo o en bloques del mismo `index.css`
  queda al implementador, siempre que se cumplan AC-002/AC-003.
- **A-004 — Convención de nombres y valores iniciales de los tokens:** nombres
  con prefijo semántico en español kebab-case (`--color-*`, `--estado-*`,
  `--font-*`, `--espacio-*`, `--sombra-*`, `--radio-*`). Valores iniciales
  recomendados (a confirmar en la guía, cumpliendo contraste AA — A-007):
  azul institucional `#1f6feb` (primario) y `#102a43` (oscuro, ya usado en el
  header actual), neutros ya presentes en el CSS actual
  (`#f5f7fa` fondo, `#ffffff` superficie, `#d9e2ec` borde, `#1f2933` texto,
  `#486581` texto suave), semánticos derivados de los existentes (éxito
  `#e3fcec`/`#9ae6b4`, error `#fdecea`/`#b30000`), escala de espaciado base
  4 px, radios 4/8/12/16 px, sombras suaves. Los valores finales son del
  implementador dentro de la guía; los **nombres** de las variables son los
  que AC-002 verifica.
- **A-005 — Username del header vía claim `sub` del JWT:** el JWT contiene
  `sub = username` (SPEC-003 §6.3, `JwtService`), pero el frontend hoy solo
  decodifica `role`/`clienteId` (SPEC-006 FR-004). Para mostrar el usuario
  actual se extiende la decodificación (frontend-only): `decodificarJwt`
  devuelve también `username` (de `sub`) y `JwtClaims`/`AuthProvider` lo
  exponen (FR-003, §10). No cambia contratos ni autorización; el claim `sub`
  ya existe y el backend no depende de esta lectura.
- **A-006 — Breakpoints mobile-first:** `sm 640px`, `md 768px`, `lg 1024px`,
  documentados en la guía; media queries `min-width` (mobile-first). Nota
  técnica: las variables CSS no pueden usarse en las condiciones de `@media`;
  los breakpoints se documentan en la guía y se usan como valores literales
  en las media queries (FR-009).
- **A-007 — Contraste objetivo WCAG AA:** 4.5:1 para texto normal y 3:1 para
  texto grande (≥ 18 px / ≥ 14 px bold) y componentes UI; se fija como
  requisito de la paleta de la guía (FR-001, FR-010).
- **A-008 — Preservación de los tests existentes:** RTL consulta por
  rol/texto/label (class-agnostic): renombrar o agregar clases CSS no rompe
  tests. Los cambios de markup deben ser **aditivos** (marca, username,
  wrappers de presentación) sin duplicar textos asertados (BR-003: p. ej.
  exactamente 2 "ARS" en `/cuentas`, tipos de movimiento en crudo, estados,
  "Cargando...", labels de botones). No se debilitan criterios existentes
  (AGENTS.md §12). Si algún test requiriera ajuste por colisión de textos,
  debe ser un ajuste de query (acotar con `within`), nunca debilitar el
  criterio (patrón de SPEC-008 §11).
- **A-009 — Marca textual "Banco":** sin assets de imagen ni logo externo (sin
  dependencias nuevas); la marca es texto estilizado con los tokens del
  sistema de diseño (FR-003, FR-004).
- **A-010 — Iconografía de estados opcional y sin dependencias:** los estados
  (carga/error/vacío) pueden usar caracteres unicode o estilos CSS puros;
  sin librerías de iconos ni assets (FR-008, BR-005).

---

## 16. Related Documents

- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (API REST y envelope de errores),
  §8 (seguridad y propiedad)
- `docs/specs/SPEC-006-frontend-react.md` (patrones de la SPA a rediseñar:
  FR-009..FR-015, §5 BR, §8 errores, §9 matriz por rol, §11 AC, A-008)
- `docs/specs/SPEC-007-caja-gestion.md` (sección Caja: FR, BR, AC)
- `docs/specs/SPEC-008-usuarios-gestion.md` (sección Usuarios: FR, BR, AC)
- `docs/specs/SPEC-003-autenticacion.md` §6.3 (contrato de claims del JWT:
  `sub = username`, `role`, `clienteId`)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend: stack,
  sin librerías de UI, CSS plano)
- `docs/sprints/roadmap.md` (Sprint 4 — Frontend React) y
  `docs/sprints/backlog.md` (E6)
- Issue #28 (IgnaGrego/sistema-banco) — requisito del rediseño