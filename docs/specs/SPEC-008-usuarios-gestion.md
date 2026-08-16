# SPEC-008 — UI Registro de Usuarios desde Gestión (ADMIN)

## Status

Approved

---

## 1. Objective

Agregar al frontend (SPA de SPEC-006) el **registro de usuarios** que hoy solo
existe por API (`POST /api/v1/auth/register`, SPEC-003): un formulario en la
sección **"Usuarios"** de `/gestion` (ADMIN) con `username`, `password`, `rol`
(`CLIENTE`/`ADMIN`) y, cuando `rol` es `CLIENTE`, un selector del `Cliente` a
vincular (listado de `GET /api/v1/clientes`). La UI pre-valida la unicidad de
`username` y la fuerza mínima de `password` y muestra feedback claro de éxito
(`UsuarioDto` creado: `username` + `rol`) o de error usando el envelope estándar
de la API (`{ code, message, details? }`).

La SPA **no introduce reglas de negocio nuevas**: el backend (SPEC-003,
implementado y mergeado) es la fuente de verdad; el frontend solo consume su
contrato REST y pre-valida en la UI las mismas reglas que el backend ya
enforces (`RegistroValidator`, AGENTS.md §10, A-004).

---

## 2. Actors

- **ADMIN**: único actor de esta pantalla. Accede a la sección **"Usuarios"**
  en `/gestion` (ruta `ADMIN` protegida por `ProtectedRoute` — SPEC-006 FR-006)
  y crea usuarios `CLIENTE` o `ADMIN`.
- **CLIENTE**: **excluido** de esta pantalla. La sección se monta solo en
  `/gestion`; un `CLIENTE` jamás ve el formulario de registro (la creación de
  usuarios es una función de gestión — FR-001, §9).
- **Usuario no autenticado**: no interactúa con esta pantalla (no puede llegar
  a `/gestion`). El endpoint de registro es público en el backend (§9, A-006),
  pero la SPA **solo** lo expone dentro de `/gestion`.

---

## 3. Preconditions

- El backend está corriendo y expone la API en `/api/v1` (proxy de Vite en
  desarrollo — SPEC-006 FR-008, A-001).
- Sesión `ADMIN` activa (JWT con claim `role = ADMIN` — SPEC-003 FR-002): sin
  sesión o con rol `CLIENTE`, el guard de rutas impide llegar a `/gestion`
  (SPEC-006 FR-006).
- El endpoint `POST /api/v1/auth/register` está disponible (SPEC-003, ya
  mergeado; matcher `permitAll` en `SecurityConfig`).
- Para crear un usuario `CLIENTE`, existe al menos un `Cliente` registrado
  (SPEC-001/SPEC-002) para seleccionar como vínculo (FR-002, BR-004).

---

## 4. Functional Requirements

### FR-001 — Nueva sección "Usuarios" en /gestion (ADMIN)

Agregar una sección **"Usuarios"** a `GestionPage` (junto a `ClientesSection`,
`CuentasSection` y `CajaSection` — SPEC-006 FR-012/013, SPEC-007 FR-001) con el
formulario de registro de usuario (A-001). No se agregan rutas nuevas: la
sección se monta dentro de `/gestion`, protegida por `ProtectedRoute`
(`rolPermitido="ADMIN"`).

### FR-002 — Formulario de registro con rol y cliente a vincular

El formulario expone:

- **Username** (`username`) — texto (BR-001).
- **Password** (`password`) — tipo `password`, no en claro (BR-002).
- **Rol** (`rol`) — selector con las opciones `CLIENTE` y `ADMIN` (BR-003).
- **Cliente a vincular** (`clienteId`) — selector de `Cliente` **visible solo
  cuando `rol = CLIENTE`** (A-003), alimentado por `GET /api/v1/clientes` vía
  `useClientes()` (patrón de `ClientesSection`/`CajaSection`). Para `ADMIN` el
  selector se oculta y `clienteId` es `null` (A-003).

Envía `POST /api/v1/auth/register` con el payload exacto
`{username, password, rol, clienteId}` (BR-005); `clienteId` es `null` para
`ADMIN` y un `id` válido para `CLIENTE` (A-002/A-003).

### FR-003 — Pre-validación de UX (unicidad y fuerza de password)

Pre-validación client-side que espeja `RegistroValidator` (BR-001..BR-004):
- Unicidad de `username`: en la UI es una **pre-validación de formato** (no
  vacío, ≤ 50 — BR-001); la **unicidad real** la enforce el backend con `409
  CONFLICTO_UNICIDAD` (BR-005, A-004). No existe un endpoint de "chequear
  username" para pre-validar la unicidad sin enviar el formulario; se valida
  solo obligatoriedad/longitud en cliente y el `409` del backend da el feedback
  autoritativo de duplicado.
- Fuerza mínima de `password`: `>= 8` caracteres (BR-002). Se bloquea el envío
  si no se cumple (mirror UX del `400 DATOS_INVALIDOS` del backend).

### FR-004 — Feedback de éxito/error con el envelope

- **Éxito (`201`)**: mensaje claro de confirmación mostrando el `UsuarioDto`
  creado (`username` + `rol` — A-005; **nunca** la password, BR-001 del
  backend). El formulario se resetea y queda listo para otro registro.
- **Error**: se presenta el `message` del envelope estándar
  `{ code, message, details? }` (`ARCHITECTURE.md` §7): con `details` de campo →
  errores por campo en el formulario (p. ej. `username`, `password`, `rol`,
  `clienteId`); sin `details` → mensaje general en la vista (§8). Los datos
  ingresados se conservan (mismo comportamiento que `TransferenciaPage` /
  `CajaSection` — ERR-009 de SPEC-006).
- Protección de **doble envío** durante la request en vuelo (patrón
  `useCaja`/`useTransferencia` — mirror AC-030 de SPEC-006).

### FR-005 — Tests de componente (vitest)

La sección incluye tests de componente (Vitest + React Testing Library)
cubriendo happy path (registro `CLIENTE` y `ADMIN` con payloads exactos y
confirmación), validaciones (username, password, rol, cliente obligatorio para
`CLIENTE`) y errores del envelope (400/409/404/500/red), con payloads exactos
(§11). También unit tests de la función de validación pura (FR-003).

---

## 5. Business Rules

Las reglas siguientes son **pre-validaciones de UX client-side** que espejan
reglas ya enforced por el backend (`RegistroValidator` de SPEC-003 BR-001..BR-003
y ERR-004/005/006/007); no crean reglas de negocio nuevas. El backend es la
fuente de verdad y rechaza cualquier payload inválido con el envelope estándar
(BR-005).

### BR-001 — Username

`username` es obligatorio (no vacío tras recortar espacios) y de hasta 50
caracteres (mirror de SPEC-003 A-005 / `RegistroValidator`). Username vacío o
> 50 caracteres bloquea el envío con error por campo.

### BR-002 — Password

`password` es obligatoria y debe tener **al menos 8 caracteres** (mirror de
SPEC-003 BR-002 / ERR-004). Menos de 8 caracteres bloquea el envío con error por
campo. El campo se muestra como `password` (nunca en claro — AGENTS.md §17).

### BR-003 — Rol

`rol` es obligatorio y debe ser `CLIENTE` o `ADMIN` (mirror de SPEC-003
A-002 / `RegistroValidator`). El selector ofrece exactamente esas dos opciones;
sin rol seleccionado el envío se bloquea.

### BR-004 — Cliente a vincular (solo CLIENTE)

Cuando `rol = CLIENTE`, el `clienteId` es obligatorio (mirror de SPEC-003
ERR-007) y debe referenciar un `Cliente` existente (SPEC-003 ERR-006). El
selector se **muestra y es requerido** solo en ese caso; para `ADMIN` se oculta
y `clienteId` es `null` (A-003). Un `CLIENTE` sin cliente seleccionado bloquea
el envío con error por campo.

### BR-005 — El backend permanece como punto de enforcement

Las pre-validaciones son UX (BR-001..BR-004, A-004); la validación real se
verifica server-side en `RegistroValidator` + `RegistrarUsuarioUseCase`
(SPEC-003 §8.3): obligatoriedad/longitud de `username`, password ≥ 8, rol
válido, `clienteId` requerido si `CLIENTE`, unicidad de `username` (BR-003 del
backend) y existencia del `Cliente`. El frontend envía el payload **exacto**
`{username, password, rol, clienteId}` y no inventa reglas.

---

## 6. Main Flow

1. El `ADMIN` inicia sesión y es redirigido a `/gestion` (SPEC-006 FR-005).
2. En la sección **"Usuarios"**, ingresa `username` y `password`, y selecciona
   el `rol` (`CLIENTE` o `ADMIN`).
3. Si `rol = CLIENTE`, selecciona el `Cliente` a vincular del selector
   (alimentado por `GET /api/v1/clientes` — FR-002, BR-004). Si `rol = ADMIN`,
   el selector de cliente está oculto.
4. Pre-validación UX (FR-003): username no vacío y ≤ 50 (BR-001), password ≥ 8
   (BR-002), rol seleccionado (BR-003) y, si `CLIENTE`, cliente seleccionado
   (BR-004). Si falla, error por campo y no se envía.
5. Submit → `POST /api/v1/auth/register` con payload exacto
   `{username, password, rol, clienteId}` (clienteId `null` para `ADMIN`).
6. El backend valida la cadena completa (`RegistroValidator` → unicidad →
   existencia de `Cliente` si `CLIENTE` — SPEC-003 §8.3) y responde `201` con
   `UsuarioDto` (`id`, `username`, `rol`; sin password — BR-001 del backend).
7. La UI muestra el mensaje de confirmación con el usuario creado (`username` +
   `rol` — FR-004, A-005) y resetea el formulario.
8. El `ADMIN` puede continuar con otro registro.

---

## 7. Alternative Flows

### AF-001 — Rol ADMIN (sin cliente a vincular)

El `ADMIN` selecciona `rol = ADMIN`: el selector de `Cliente` **no se
muestra** y `clienteId` viaja como `null` en el payload (A-003, BR-004). El
backend ignora un `clienteId` informado para `ADMIN` (SPEC-003 §8.3), pero la
UI lo oculta por UX.

### AF-002 — Username duplicado (409)

El backend responde `409 CONFLICTO_UNICIDAD` con
`details[{campo:"username", mensaje}]` (SPEC-003 ERR-005; la unicidad no se
pre-valida client-side porque no existe un chequeo previo — FR-003, A-004): la
UI muestra el error por campo `username` con el mensaje del envelope,
conservando los datos ingresados para corregir (ERR-002).

### AF-003 — Reintento tras error de red o 500

Ante un error de red (`ERROR_RED`) o un `500 ERROR_INTERNO` defensivo, la UI
muestra el mensaje del envelope, conserva los datos ingresados y permite
reintentar (mismo comportamiento que `CajaSection`/`TransferenciaPage` —
ERR-005/006).

---

## 8. Error Cases

Todos los errores de la API usan el envelope JSON estándar
`{ code, message, details? }` con `details: [{campo, mensaje}]`
(`ARCHITECTURE.md` §7). La UI muestra `message` (y los `details` por campo
cuando corresponda) en la sección, conservando los datos ingresados.

> **Errores que NO aplican:** el endpoint de registro es **público**
> (`permitAll` — `SecurityConfig`), por lo que **no** produce `401
> NO_AUTENTICADO` ni `403 ACCESO_DENEGADO` para esta operación. El único 403/401
> posible en esta pantalla sería defensivo (p. ej. un `ADMIN` con token
> inconsistente llegando al listado de clientes del selector) y lo maneja
> `useClientes()` en el listado, no el registro. La autorización de "quién ve la
> sección" es UX (§9, A-006).

### ERR-001 — Datos inválidos (400)

`400 DATOS_INVALIDOS` con `details` del campo (`username`, `password`, `rol` o
`clienteId` — SPEC-003 ERR-004/ERR-007): el backend es la fuente de verdad; la
pre-validación BR-001..BR-004 debió bloquearlo, pero el envelope puede traer
mensajes que la UX no produce (p. ej. `password` < 8 si se envía por otra vía).

Se muestra el error por campo correspondiente; los datos ingresados no se
pierden.

### ERR-002 — Username duplicado (409)

`409 CONFLICTO_UNICIDAD` con `details[{campo:"username", mensaje}]` (SPEC-003
ERR-005).

Se muestra el error por campo `username` con el mensaje del envelope; los datos
se conservan (AF-002).

### ERR-003 — Cliente inexistente (404)

`404 CLIENTE_NO_ENCONTRADO` (SPEC-003 ERR-006; p. ej. el `Cliente` seleccionado
fue eliminado tras el listado del selector).

Se muestra el mensaje del envelope en la vista y se sugiere refrescar el
selector de clientes (el selector se repuebla al recargar).

### ERR-004 — Error interno (500, defensivo)

`500 ERROR_INTERNO` (fallback de `GlobalExceptionHandler`; sin leak de detalle
interno — SPEC-003 §8.6).

Se muestra el mensaje del envelope; los datos se conservan y se puede reintentar
(AF-003).

### ERR-005 — Error de red / backend no disponible

La request no puede completarse (backend caído, proxy sin destino, timeout) →
`ApiError` con `code === 'ERROR_RED'` y `MENSAJE_ERROR_RED` (wrapper
`httpClient` — SPEC-006 ERR-008, AC-029).

Se muestra el mensaje de conexión y la operación puede reintentarse (AF-003).

---

## 9. Authorization

- **Quién puede (UI)**: `ADMIN` autenticado (token con `role = ADMIN`). La
  sección "Usuarios" se monta solo en `/gestion` (`ProtectedRoute
  rolPermitido="ADMIN"` — SPEC-006 FR-006); el `CLIENTE` nunca ve el formulario
  (FR-001, A-006).
- **Quién no puede (UI)**: `CLIENTE` y usuarios sin sesión — no llegan a
  `/gestion`.
- **Enforcement del backend**: el endpoint `POST /api/v1/auth/register` es
  **público** (`permitAll` — `SecurityConfig`, SPEC-003 FR-001): el backend **no
  exige token ni rol ADMIN** para esta operación. La restricción a `ADMIN` es
  **solo UX** (AGENTS.md §17: la autorización se enforce server-side; este es un
  gap del backend ya documentado en SPEC-003 A-002, que no se corrige en esta
  spec — §12). El frontend adjunta o no token según A-002; el endpoint lo
  acepta o ignora por `permitAll`.
- Dado que el endpoint nunca devuelve `401`, no se dispara la limpieza global de
  sesión del `httpClient` en esta operación (A-002).

---

## 10. Data Changes

**Backend y base de datos: sin cambios.** El endpoint ya existe y está mergeado
(SPEC-003); no hay migraciones Flyway ni tablas nuevas.

**Frontend (solo):**

- `src/api/auth.ts` — **extendido**: nueva función `registro(body)` →
  `POST /api/v1/auth/register` con `autenticar: false`, junto a `login()`
  (A-002).
- `src/api/types.ts` — **extendido**: `RegistrarUsuarioRequest {username,
  password, rol, clienteId}` (espejo del record `RegistrarUsuarioRequest` del
  backend) y `UsuarioDto {id, username, rol}` (espejo del `UsuarioDto` del
  backend — nunca password). Se reutiliza el tipo `Rol = 'CLIENTE' | 'ADMIN'`
  ya existente.
- `src/lib/validacion.ts` — **extendido**: función de pre-validación
  `validarRegistroUsuario(username, password, rol, clienteId)` cubriendo
  BR-001..BR-004 (reutiliza el patrón de `ErroresPorCampo`).
- `src/hooks/` — hook de mutación del registro (p. ej. `useRegistroUsuario`)
  siguiendo el patrón de `useCaja`/`useTransferencia`
  (`{ enviando, confirmacion, error, ejecutar }` con protección de doble envío —
  FR-004).
- `src/pages/gestion/UsuariosSection.tsx` — **nuevo**: sección "Usuarios"
  (FR-001..FR-004), montada en `src/pages/GestionPage.tsx`. Reutiliza
  `CampoFormulario`, `Cargando`, `EstadoError`, `EstadoVacio` y `useClientes()`
  (selector de cliente).
- Tests: `src/pages/gestion/UsuariosSection.test.tsx` (component tests, §11) y
  unit test de `validarRegistroUsuario`; ajuste de queries no acotadas en
  `GestionPage.test.tsx` (nota en §11).
- Sin dependencias npm nuevas (AGENTS.md §13).

---

## 11. Acceptance Criteria

**Estrategia de testing:** los criterios se verifican con la suite de la SPA
con `npm test` (Vitest + React Testing Library): **component tests** para la
sección Usuarios (render, interacción, payloads, confirmación y errores) y
**unit tests** para la función de validación pura. Cada AC indica el tipo de
test que lo verifica. Además, `npm run lint` y `npm run typecheck` deben pasar
y `npm run build` debe producir el bundle.

> **Nota de impacto en tests existentes de `/gestion`:** al montar
> `UsuariosSection` en `GestionPage`, las queries no acotadas de
> `GestionPage.test.tsx` pueden dejar de funcionar: ahora hay **tres**
> selectores con label "Cliente" (CuentasSection, CajaSection y UsuariosSection
> cuando `rol = CLIENTE`), y el texto "Pérez, Juan" aparece en más lugares (lista
> de clientes + opciones del selector de la sección Usuarios). Deben **acotarse
> las queries, no debilitar los criterios** (AGENTS.md §12):
> - `getAllByLabelText('Cliente')` → `within(sección específica)` /
>   `getAllByLabelText(...)[n]` según corresponda.
> - `findAllByText('Pérez, Juan')` → acotar con `within` de la sección.
> - El campo de username debe etiquetarse "Usuario" (no "Nombre") para **no**
>   colisionar con los campos "Nombre" de `ClientesSection` usados por
>   `GestionPage.test.tsx` (que usan `getAllByLabelText('Nombre')`).
> En los tests nuevos de `UsuariosSection`, las queries se acotan con
> `within(form)` o selectores específicos (patrón `seccionDe` de
> `CajaSection.test.tsx`).

Sección e integración:

- [ ] AC-001: Component test — `/gestion` (sesión `ADMIN`) muestra la sección
      "Usuarios" con el formulario (username, password, rol); no hay rutas
      nuevas (FR-001).
- [ ] AC-002: Component test — al elegir `rol = CLIENTE` se muestra el selector
      "Cliente" poblado por `GET /api/v1/clientes`; al elegir `rol = ADMIN` el
      selector de cliente **no** se muestra (FR-002, BR-004, A-003).

Happy path:

- [ ] AC-003: Component test — registro `CLIENTE` exitoso: envía el payload
      exacto `{username, password, rol: 'CLIENTE', clienteId}` a
      `POST /api/v1/auth/register`; ante `201` muestra el mensaje de
      confirmación con el `UsuarioDto` (`username` + `rol`, sin password) y
      resetea el formulario (FR-002, FR-004, A-005).
- [ ] AC-004: Component test — registro `ADMIN` exitoso: envía el payload exacto
      `{username, password, rol: 'ADMIN', clienteId: null}`; ante `201` muestra
      la confirmación con `username` + `rol` (FR-002, FR-004, AF-001, A-003).

Validaciones (UX mirrors):

- [ ] AC-005: Component test — pre-validación de `username`: vacío o > 50
      caracteres → error por campo y envío bloqueado sin request (BR-001).
- [ ] AC-006: Component test — pre-validación de `password`: < 8 caracteres →
      error por campo y envío bloqueado sin request; con 8 caracteres la
      request SÍ se envía (boundary) (BR-002).
- [ ] AC-007: Component test — `rol = CLIENTE` sin cliente seleccionado → error
      por campo y envío bloqueado (BR-004).
- [ ] AC-008: Unit test — `validarRegistroUsuario` cubre BR-001..BR-004
      (username vacío/>50, password <8, rol vacío/inválido, CLIENTE sin
      clienteId) (BR-001..BR-004).

Errores con el envelope:

- [ ] AC-009: Component test — `400 DATOS_INVALIDOS` con `details` (p. ej.
      `password`) → error por campo y datos conservados (ERR-001).
- [ ] AC-010: Component test — `409 CONFLICTO_UNICIDAD` con
      `details[{campo:'username'}]` → error por campo `username` y datos
      conservados (ERR-002, AF-002).
- [ ] AC-011: Component test — `404 CLIENTE_NO_ENCONTRADO` → mensaje del
      envelope general (ERR-003).
- [ ] AC-012: Component test — `500 ERROR_INTERNO` → mensaje del envelope
      general y reintento posible (ERR-004, AF-003).
- [ ] AC-013: Component test — error de red → `MENSAJE_ERROR_RED` y reintento
      posible (ERR-005, AF-003).

Robustez y calidad:

- [ ] AC-014: Component test — durante el envío el botón se deshabilita y no se
      duplica la request (FR-004, mirror AC-030 de SPEC-006).
- [ ] AC-015: `npm run lint` y `npm run typecheck` pasan sin errores; `npm run
      build` produce el bundle; `npm test` ejecuta la suite completa y todos
      los tests pasan (FR-005).
- [ ] AC-016: Sin dependencias npm nuevas y sin cambios en backend,
      migraciones ni `docker/` (AGENTS.md §13, §10).

---

## 12. Out of Scope

- **Pantalla de registro público** (formulario de auto-registro para usuarios
  sin sesión): el issue solo pide el registro desde `/gestion` (ADMIN). La
  autenticación propia de `CLIENTE` sigue fuera de alcance (SPEC-003 §12 —
  "Frontend (pantallas de login/registro)": el login es SPEC-006 FR-005; el
  registro público queda como evolución).
- **Listado/edición/baja de usuarios**: solo existe `POST
  /api/v1/auth/register`; no hay `GET/PUT/DELETE /usuarios` (SPEC-003 — el
  registro responde `201` sin `Location` porque no existe el recurso de
  lectura). La sección es de **solo alta** (A-002).
- **Corregir el gap de autorización del backend**: el registro es público
  (`permitAll`) y el backend no restringe quién crea usuarios `ADMIN` (SPEC-003
  A-002). Corregir ese gap (p. ej. matcher `hasRole('ADMIN')` o validación de
  contexto) es un **cambio de backend** fuera de esta spec (frontend-only, §10);
  se documenta, no se implementa.
- **Cambios en el backend**: el endpoint de registro ya existe y está mergeado
  (SPEC-003); ningún cambio de contrato, seguridad o esquema.
- **Redesign de UI**: se mantiene el diseño actual (CSS plano existente y
  componentes existentes) hasta el redesign de UI (A-004 de SPEC-006).
- **Política de contraseñas complejas** (mayúsculas/números/símbolos): el
  backend solo exige ≥ 8 caracteres (SPEC-003 BR-002); no se añaden reglas UX
  adicionales (BR-005).
- **i18n** (la SPA es solo en español).
- **Tests e2e** (Playwright/Cypress): solo lint, typecheck, unit/component
  tests y build (FR-005).

---

## 13. Dependencies

- **SPEC-003 (autenticación)**: contrato de `POST /api/v1/auth/register` —
  payload `RegistrarUsuarioRequest(username, password, rol, clienteId)`,
  respuesta `201 UsuarioDto(id, username, rol)`, reglas BR-001..BR-003 y errores
  ERR-004..ERR-007 (FR-002..004, BR-001..005, §8).
- **SPEC-006 (SPA)**: patrones de la SPA — `httpClient`/`ApiError`/envelope
  (FR-002), hooks de datos y mutación, secciones de `/gestion`, estados de UI,
  validaciones y tests (FR-001..005, BR-005, §11).
- **SPEC-001 (clientes)**: `GET /api/v1/clientes` y `ClienteDto` para el
  selector de cliente a vincular (FR-002, BR-004; vía `useClientes`).
- **SPEC-007 (caja)**: referencia de patrón de sección de `/gestion` con
  selector de cliente + formularios + tests (FR-001, §11).
- **`ARCHITECTURE.md`** §7 (API REST y envelope de errores), §8 (seguridad y
  propiedad); **ADR-008** (decisiones del frontend).

---

## 14. Open Questions

- Ninguna pendiente: las ambigüedades del issue (módulo API, `autenticar`,
  selector de cliente por rol, pre-validación de unicidad y alcance de la
  sección) se resolvieron en modo autónomo como asunciones documentadas en §15.
  Si el Product Owner dispone otra cosa, deben ajustarse antes de la
  implementación.

---

## 15. Decisions and Assumptions

- **A-001 — Nueva sección "Usuarios" en `/gestion`, sin ruta nueva:** el issue
  pide el registro "desde Gestion (ADMIN)". Se interpreta como **una sección
  "Usuarios"** montada en `GestionPage` junto a `ClientesSection`,
  `CuentasSection` y `CajaSection`, sin rutas nuevas (protegida por
  `ProtectedRoute rolPermitido="ADMIN"`). La sección es de **solo alta** (no hay
  listado de usuarios — ver A-002).
- **A-002 — Módulo API `src/api/auth.ts` (extender) con `registro()` y
  `autenticar: false`:** el issue sugiere "nueva api/usuario.ts o api/auth.ts
  (registro)". Se elige **extender `src/api/auth.ts`** (no crear `usuario.ts`)
  porque `registro` es una operación del **recurso `auth`** (`POST
  /api/v1/auth/register`) y no existe un recurso `usuarios` con operaciones de
  lectura/listado (SPEC-003 responde `201` sin `Location`; no hay `GET
  /usuarios`). Esto sigue el patrón un-módulo-por-recurso: `auth.ts` es el
  módulo del recurso `auth`, igual que `clientes.ts`/`cuentas.ts`/`caja.ts` lo
  son para sus recursos. Crear `usuario.ts` implicaría un archivo nuevo para un
  único endpoint sin contrapartida de recurso de lectura, lo que añade
  estructura sin beneficio (AGENTS.md §11).
  **Sobre `autenticar`:** se pasa `autenticar: false`, espejo de `login()` en el
  mismo módulo. Razones: (1) el endpoint es **público** (`permitAll` —
  `SecurityConfig`): no requiere token, y enviar un header `Authorization` a un
  endpoint público es innecesario y semánticamente incorrecto (principio de
  menor privilegio); (2) consistencia dentro de `auth.ts` (ambas operaciones de
  auth son públicas); (3) aunque con `autenticar: true` (default) el token del
  `ADMIN` de `/gestion` se adjuntaría y el `permitAll` lo ignoraría, y el
  endpoint nunca devuelve `401` (así que no se dispararía la limpieza global de
  sesión), es más limpio declarar explícitamente que la llamada es pública con
  `autenticar: false`. Este flag NO cambia el comportamiento de autorización
  server-side (el endpoint es público con o sin token); solo evita adjuntar un
  header innecesario.
- **A-003 — Selector de cliente solo cuando `rol = CLIENTE`:** el selector de
  `Cliente` se muestra y es obligatorio únicamente con `rol = CLIENTE` (BR-004);
  para `ADMIN` se oculta y `clienteId` viaja como `null` en el payload. El
  backend para `ADMIN` ignora un `clienteId` informado (SPEC-003 §8.3), pero la
  UI lo oculta por UX (el issue pide "si es CLIENTE, selector del cliente a
  vincular").
- **A-004 — Unicidad de username y fuerza de password pre-validadas como UX,
  backend autoritativo:** la fuerza de `password` (≥ 8) y la obligatoriedad/
  longitud de `username` se pre-validan client-side (BR-001..BR-002, mirror del
  `400 DATOS_INVALIDOS`). La **unicidad** de `username` NO se pre-valida
  client-side (no existe un endpoint de chequeo previo sin crear el usuario); el
  `409 CONFLICTO_UNICIDAD` del backend da el feedback autoritativo y se muestra
  por campo `username` (AF-002). Cualquier detalle del envelope (400/409) se
  muestra tal cual, sobreescribiendo el mensaje UX si el backend trae uno más
  específico (BR-005).
- **A-005 — Mensaje de éxito con el `UsuarioDto`:** tras `201`, la confirmación
  muestra el usuario creado con `username` y `rol` (y opcionalmente su `id`),
  nunca la password (BR-001 del backend). El formulario se resetea para permitir
  otro registro (FR-004).
- **A-006 — La restricción a ADMIN es solo UX; el backend sigue siendo público:**
  el endpoint `POST /api/v1/auth/register` es `permitAll` y el backend no exige
  token ni rol (SPEC-003 FR-001, A-002). La sección solo se expone en `/gestion`
  (ADMIN), pero el enforcement real de "solo ADMIN crea usuarios" recae en el
  backend, que actualmente **no** lo restringe. Ese gap no se corrige en esta
  spec (frontend-only, §12); se documenta explícitamente para que el Product
  Owner decida si se cierra en una spec de backend posterior.

---

## 16. Related Documents

- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (API REST y envelope de errores),
  §8 (seguridad y propiedad)
- `docs/specs/SPEC-003-autenticacion.md` (contrato de registro: FR-001/005,
  BR-001..003, ERR-004..007, A-002/003/005)
- `docs/architecture/SPEC-003.md` (contrato HTTP exacto del registro, flujo del
  use case y matcher `permitAll` de `SecurityConfig` §8.5)
- `docs/specs/SPEC-006-frontend-react.md` (patrones de la SPA: FR-002/012/013,
  BR-008/010, §8 errores, §9 matriz por rol, §11 AC, §12 out of scope)
- `docs/specs/SPEC-007-caja-gestion.md` (patrón de sección de `/gestion` con
  selector de cliente + formularios + tests y nota de impacto en
  `GestionPage.test.tsx`)
- `docs/specs/SPEC-001-clientes.md` (`GET /api/v1/clientes`, `ClienteDto`)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend)
- `docs/sprints/backlog.md` (E3: US-3.1 registro; E6: US-6.4 gestión ADMIN)
