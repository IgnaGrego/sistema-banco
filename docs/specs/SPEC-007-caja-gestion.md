# SPEC-007 — UI Caja: Depósitos y Retiros en Gestión (ADMIN)

## Status

Approved

---

## 1. Objective

Agregar al frontend (SPA de SPEC-006) la operación de **caja** que hoy solo
existe por API (issue #26): formularios de **depósito** y **retiro** en la
pantalla `/gestion` del `ADMIN`, consumiendo `POST /api/v1/depositos` y
`POST /api/v1/retiros` (SPEC-005). El `ADMIN` elige el cliente y la cuenta
destino (reutilizando `GET /api/v1/cuentas?clienteId=`), ingresa un monto con
validación (> 0, hasta 2 decimales; saldo suficiente en retiro) y recibe
feedback claro de éxito/error con el envelope estándar de la API
(`{ code, message, details? }`). Tras operar, el saldo mostrado se actualiza
(refresh del listado de cuentas).

La SPA **no introduce reglas de negocio nuevas**: el backend (SPEC-005,
implementado y mergeado) es la fuente de verdad; el frontend solo consume sus
contratos REST y pre-valida en la UI las mismas reglas que el backend ya
enforces (AGENTS.md §10, A-008 de SPEC-006).

Esta spec **deroga parcialmente** el §12 de SPEC-006 ("Pantallas de
depósitos/retiros... fuera de alcance", asunción A-005): el issue #26 trae la
**caja del ADMIN** a la SPA. Los retiros propios del `CLIENTE` siguen fuera de
alcance (no los pide el issue).

---

## 2. Actors

- **ADMIN**: único actor de esta pantalla. Ejecuta depósitos y retiros
  (caja) sobre cualquier cuenta del sistema (SPEC-005 §9): depósito →
  exclusivo de `ADMIN`; retiro → `ADMIN` puede operar cualquier cuenta.
- **CLIENTE**: **excluido** de esta pantalla. La sección Caja se monta solo en
  `/gestion` (ruta `ADMIN` protegida por `ProtectedRoute` — SPEC-006 FR-006).
  El retiro propio de `CLIENTE` (permitido por el backend) queda fuera de
  alcance de esta spec (§12).

---

## 3. Preconditions

- El backend está corriendo y expone la API en `/api/v1` (proxy de Vite en
  desarrollo — SPEC-006 FR-008, A-001).
- Sesión `ADMIN` activa (JWT con claim `role = ADMIN`, sin `clienteId` —
  SPEC-003 FR-002): sin sesión o con rol `CLIENTE`, el guard de rutas impide
  llegar a `/gestion` (SPEC-006 FR-006).
- Existen clientes registrados y cuentas creadas (SPEC-001/SPEC-002); la
  operación exige al menos una cuenta `ACTIVA` como destino (§5 BR-003).
- Los endpoints `POST /api/v1/depositos` y `POST /api/v1/retiros` están
  disponibles y autorizados para `ADMIN` (SPEC-005 §9, matchers de
  `SecurityConfig`).

---

## 4. Functional Requirements

### FR-001 — Nueva sección "Caja" en /gestion (ADMIN)

Agregar una sección **"Caja"** a `GestionPage` (junto a `ClientesSection` y
`CuentasSection` — SPEC-006 FR-012/FR-013), con los formularios de **depósito**
y **retiro** (A-001). No se agregan rutas nuevas: la sección se monta dentro
de `/gestion`, protegida por `ProtectedRoute` (`rolPermitido="ADMIN"`).

### FR-002 — Selector de cuenta destino (listado por cliente)

La sección reutiliza el patrón de `CuentasSection`: selector de **cliente**
(obligatorio) y listado de sus cuentas vía `GET /api/v1/cuentas?clienteId={id}`
(SPEC-002 FR-006, ya consumido por SPEC-006 FR-013). El listado muestra cada
`CuentaDto` con su saldo formateado (BR-004) y su estado. Los selectores de
cuenta de los formularios se alimentan de ese listado ofreciendo **solo
cuentas `ACTIVA`** (BR-003).

### FR-003 — Formulario de depósito

Formulario con selector de **cuenta destino** (solo `ACTIVA`, BR-003) y campo
**monto** (BR-001). Envía `POST /api/v1/depositos` con payload exacto
`{cuentaId, monto}` (SPEC-005 FR-001, A-002). Ante `201` muestra la
confirmación y refresca el listado de cuentas (FR-006).

### FR-004 — Formulario de retiro

Formulario con selector de **cuenta destino** (solo `ACTIVA`, BR-003) y campo
**monto** (BR-001), con pre-validación client-side de **saldo suficiente**
(BR-002). Envía `POST /api/v1/retiros` con payload exacto `{cuentaId, monto}`
(SPEC-005 FR-002). Ante `201` muestra la confirmación y refresca el listado
(FR-006).

### FR-005 — Feedback de éxito/error con el envelope

- **Éxito (`201`)**: panel de confirmación (patrón de `TransferenciaPage`,
  `role="status"`) mostrando `idMovimiento`, `monto` formateado en ARS
  (BR-004) y `fechaHora` de la `DepositoConfirmacion`/`RetiroConfirmacion`,
  más la cuenta operada (p. ej. su CBU, disponible en el selector local).
- **Error**: se presenta el `message` del envelope estándar
  `{ code, message, details? }` (`ARCHITECTURE.md` §7): con `details` de
  campo → errores por campo en el formulario; sin `details` → mensaje general
  en la vista (§8). Los datos ingresados se conservan (mismo comportamiento
  que `TransferenciaPage` — ERR-006/AC-019 de SPEC-006).
- Protección de **doble envío** durante la request en vuelo (patrón
  `useTransferencia` — AC-030 de SPEC-006).

### FR-006 — Saldo actualizado tras operar

Tras un `201`, la sección refresca el listado de cuentas del cliente
seleccionado (`GET /api/v1/cuentas?clienteId={id}`), de modo que el saldo
mostrado refleja la operación (BR-004: el frontend **no calcula** el nuevo
saldo; lo lee del `CuentaDto` refrescado).

### FR-007 — Tests de componente (vitest)

La sección incluye tests de componente (Vitest + React Testing Library)
cubriendo happy path (depósito y retiro exitosos con refresh de saldo),
validaciones (monto, saldo en retiro, cuenta obligatoria) y errores del
envelope (400/403/404/409/422/red), con payloads exactos (§11).

---

## 5. Business Rules

Las reglas siguientes son **pre-validaciones de UX client-side** que espejan
reglas ya enforced por el backend (SPEC-005 BR-001..BR-004); no crean reglas
de negocio nuevas. El backend es la fuente de verdad y rechaza cualquier
payload inválido con el envelope estándar (BR-005).

### BR-001 — Monto de la caja

`monto` es obligatorio, numérico, **mayor a 0** y con **hasta 2 decimales**
(mirror de SPEC-005 BR-001 / SPEC-006 BR-004): regex `^\d+(\.\d{1,2})?$`
(REGEX_MONTO existente en `src/lib/validacion.ts`) y `Number(monto) > 0`.
Monto vacío, no numérico, `<= 0` o con más de 2 decimales bloquea el envío con
error por campo.

### BR-002 — Retiro: pre-validación de saldo suficiente

En el retiro, se pre-valida client-side que `monto <= saldo` de la cuenta
seleccionada; si no, el envío se bloquea con error por campo. Es un **espejo
de UX** del `422 SALDO_INSUFICIENTE` del backend (SPEC-005 BR-002/ERR-002):
ante saldo desactualizado (p. ej. otra operación concurrente), el backend
sigue rechazando con `422` y la UI muestra el mensaje del envelope (A-002).

### BR-003 — Cuenta destino

La selección de cuenta es obligatoria y debe ser una cuenta **`ACTIVA`** del
cliente seleccionado (mirror de SPEC-005 BR-003: solo se opera sobre cuentas
`ACTIVA`). Las cuentas `BLOQUEADA` **no se ofrecen** en el selector. Si el
cliente no tiene cuentas `ACTIVA`, los formularios quedan deshabilitados
(AF-001).

### BR-004 — El frontend no calcula montos

Saldos y montos se muestran en ARS con 2 decimales (formato `es-AR` —
`formatearMontoARS`, mirror de SPEC-006 BR-010). El frontend **no calcula**
saldos ni montos: el saldo post-operación proviene del `CuentaDto` refrescado
(FR-006); los montos de la confirmación provienen de la respuesta `201`.

### BR-005 — El backend permanece como punto de enforcement

Las pre-validaciones son UX (BR-001..BR-003, A-002); la autorización real se
verifica server-side en cada endpoint (matchers RBAC — `SecurityConfig`:
`POST /api/v1/depositos` → `hasRole("ADMIN")`; `POST /api/v1/retiros` →
`hasAnyRole("ADMIN", "CLIENTE")` — SPEC-005 §8.5). Un payload o token inválido
se rechaza con el envelope (mirror de SPEC-006 BR-008).

---

## 6. Main Flow

1. El `ADMIN` inicia sesión y es redirigido a `/gestion` (SPEC-006 FR-005).
2. En la sección **Caja**, selecciona un **cliente** → `GET
   /api/v1/cuentas?clienteId={id}` → listado de sus cuentas con saldo
   (FR-002).
3. En el formulario de depósito o retiro, selecciona la **cuenta destino**
   (solo `ACTIVA` — BR-003) e ingresa el **monto**.
4. Pre-validación UX: monto > 0 con ≤ 2 decimales (BR-001); en retiro,
   monto ≤ saldo de la cuenta (BR-002). Si falla, error por campo y no se
   envía.
5. Submit → `POST /api/v1/depositos` (o `/api/v1/retiros`) con payload
   exacto `{cuentaId, monto}`.
6. El backend valida la cadena completa (autorización → cuenta existe →
   `ACTIVA` → monto → saldo en retiro — SPEC-005 §6) y responde `201` con
   `DepositoConfirmacion`/`RetiroConfirmacion` (`idMovimiento`, `cuentaId`,
   `monto`, `fechaHora`).
7. La UI muestra el panel de confirmación (FR-005) y **refresca el listado de
   cuentas**: el saldo mostrado queda actualizado (FR-006, BR-004).
8. El `ADMIN` puede continuar con otra operación (mismo cliente u otro).

---

## 7. Alternative Flows

### AF-001 — Cliente sin cuentas (o sin cuentas ACTIVA)

El listado de cuentas del cliente seleccionado está vacío o no hay cuentas
`ACTIVA`: se muestra el estado vacío (patrón `EstadoVacio` — SPEC-006
ERR-009) y los formularios de la caja quedan deshabilitados con mensaje
(mirror de SPEC-006 AF-003).

### AF-002 — Retiro con monto mayor al saldo (pre-validación client-side)

El monto ingresado supera el saldo de la cuenta seleccionada: el envío se
bloquea con error por campo (BR-002). Si aun así el backend responde `422
SALDO_INSUFICIENTE` (saldo desactualizado por una operación concurrente), se
muestra el mensaje del envelope y se invita a refrescar el listado (ERR-002).

### AF-003 — Reintento tras 409 o error de red

Ante `409 CONFLICTO_CONCURRENCIA` o error de red, la UI muestra el mensaje del
envelope, conserva los datos ingresados y permite reintentar (mismo
comportamiento que `TransferenciaPage` — AC-029 de SPEC-006; el backend no
reintenta automáticamente — SPEC-005 BR-004).

### AF-004 — Cuenta seleccionada que deja de estar ACTIVA

La cuenta seleccionada pasa a `BLOQUEADA` (p. ej. por otra operación) tras el
listado: el backend responde `422 CUENTA_BLOQUEADA` (SPEC-005 ERR-004); la UI
muestra el mensaje del envelope y, al refrescar, la cuenta deja de ofrecerse
en el selector (BR-003).

---

## 8. Error Cases

Todos los errores de la API usan el envelope JSON estándar
`{ code, message, details? }` con `details: [{campo, mensaje}]`
(`ARCHITECTURE.md` §7). La UI muestra `message` (y los `details` por campo
cuando corresponda) en la sección activa, conservando los datos ingresados.

### ERR-001 — Datos inválidos (400)

`400 DATOS_INVALIDOS` con `details` del campo `monto` (SPEC-005 ERR-001: monto
`<= 0`, no numérico o con más de 2 decimales; la pre-validación BR-001 debió
bloquearlo — el backend es la fuente de verdad).

Se muestra el error por campo `monto`; los datos ingresados no se pierden.

### ERR-002 — Saldo insuficiente (422)

`422 SALDO_INSUFICIENTE` (SPEC-005 ERR-002; solo retiro — p. ej. saldo
desactualizado).

Se muestra el mensaje del envelope en el formulario; los datos se conservan y
se puede refrescar el saldo (AF-002).

### ERR-003 — Cuenta BLOQUEADA (422)

`422 CUENTA_BLOQUEADA` (SPEC-005 ERR-004; la cuenta dejó de estar `ACTIVA`).

Se muestra el mensaje del envelope; al refrescar, la cuenta no se ofrece en el
selector (AF-004).

### ERR-004 — Cuenta inexistente (404)

`404 CUENTA_NO_ENCONTRADA` (SPEC-005 ERR-003; p. ej. cuenta eliminada o lista
desactualizada).

Se muestra el mensaje del envelope en la vista y se sugiere refrescar el
listado.

### ERR-005 — Conflicto de concurrencia (409)

`409 CONFLICTO_CONCURRENCIA` (SPEC-005 ERR-006; `@Version` — otra operación
modificó la cuenta mientras se ejecutaba la caja).

Se muestra el mensaje del envelope y se puede reintentar tras refrescar
(AF-003; sin reintento automático — SPEC-005 BR-004).

### ERR-006 — Acceso denegado (403)

`403 ACCESO_DENEGADO` (SPEC-005 ERR-005; caso defensivo: p. ej. token `ADMIN`
inconsistente o `CLIENTE` intentando la operación por una vía no oculta en la
UI).

Se muestra el mensaje del envelope. La sección no se renderiza para `CLIENTE`
(FR-001, §9).

### ERR-007 — Error de red / backend no disponible

La request no puede completarse (backend caído, proxy sin destino, timeout) →
`ApiError` con `code === 'ERROR_RED'` y `MENSAJE_ERROR_RED` (wrapper
`httpClient` — SPEC-006 ERR-008, AC-029).

Se muestra el mensaje de conexión y la operación puede reintentarse.

> Nota: el `401` en una request autenticada lo maneja globalmente el
> `httpClient` (limpieza de sesión + redirección a `/login` — SPEC-006
> FR-007/ERR-002): no requiere manejo específico en esta sección.

---

## 9. Authorization

- **Quién puede**: `ADMIN` autenticado (token con `role = ADMIN`). Ejecuta
  depósitos (exclusivo de `ADMIN` — SPEC-005 §9) y retiros sobre **cualquier**
  cuenta del sistema (SPEC-005 FR-002, AC-017).
- **Quién no puede**: `CLIENTE` y usuarios sin sesión. La sección Caja se
  monta solo en `/gestion` (`ProtectedRoute rolPermitido="ADMIN"` — SPEC-006
  FR-006); el `CLIENTE` nunca ve los formularios (FR-001, BR-005).
- **Enforcement**: la autorización se verifica server-side en cada endpoint
  (matchers RBAC — `SecurityConfig` de SPEC-005 §8.5; defensa en profundidad
  en `DepositoRetiroValidator`). El ocultamiento por rol es UX (BR-005,
  AGENTS.md §17).
- El backend permite `CLIENTE` retirando de cuentas propias, pero **la SPA no
  expone esa pantalla en esta spec** (§12): la sección solo consume los
  endpoints con token `ADMIN`.

---

## 10. Data Changes

**Backend y base de datos: sin cambios.** Los endpoints ya existen y están
mergeados (SPEC-005); no hay migraciones Flyway ni tablas nuevas.

**Frontend (solo):**

- `src/api/caja.ts` — **nuevo** módulo API (A-003): `depositar(body)` →
  `POST /api/v1/depositos` y `retirar(body)` → `POST /api/v1/retiros`,
  siguiendo el patrón de `request`/`httpClient` (FR-002 de SPEC-006).
- `src/api/types.ts` — **extendido**: `DepositoRequest {cuentaId, monto}` y
  `RetiroRequest {cuentaId, monto}` (espejo de los records del backend),
  `DepositoConfirmacion {idMovimiento, cuentaId, monto, fechaHora}` y
  `RetiroConfirmacion {...}` (espejo de las confirmaciones `201` — los
  `BigDecimal` viajan como número JSON; el `Instant` como string ISO-8601).
- `src/lib/validacion.ts` — **extendido**: función de pre-validación de la
  caja (p. ej. `validarCaja`, nombre orientativo) cubriendo BR-001 y BR-002
  (reutiliza `REGEX_MONTO` y la semántica de `validarTransferencia`).
- `src/hooks/` — hook(s) de mutación de la caja siguiendo el patrón de
  `useTransferencia` (`{ enviando, confirmacion, error, ... }` con protección
  de doble envío — FR-005).
- `src/pages/gestion/CajaSection.tsx` — **nuevo**: sección Caja (FR-001..FR-006),
  montada en `src/pages/GestionPage.tsx`.
- Tests: `src/pages/gestion/CajaSection.test.tsx` (component tests, §11) y
  unit test de la validación; ajuste de queries no acotadas en
  `GestionPage.test.tsx` (nota en §11).
- Sin dependencias npm nuevas (AGENTS.md §13).

---

## 11. Acceptance Criteria

**Estrategia de testing:** los criterios se verifican con la suite de la SPA
con `npm test` (Vitest + React Testing Library): **component tests** para la
sección Caja (render, interacción, payloads, confirmación y errores) y
**unit tests** para la función de validación pura. Cada AC indica el tipo de
test que lo verifica. Además, `npm run lint` y `npm run typecheck` deben pasar
y `npm run build` debe producir el bundle.

> **Nota de impacto en tests existentes de `/gestion`:** al montar
> `CajaSection` en `GestionPage`, las queries no acotadas de
> `GestionPage.test.tsx` dejarán de funcionar: ahora hay dos selectores con
> label "Cliente" (CuentasSection y CajaSection), dos campos "Monto", y el
> texto "Pérez, Juan" aparece en más de un lugar. Deben **acotarse las
> queries, no debilitar los criterios** (AGENTS.md §12):
> - `getByLabelText('Cliente')` → `getAllByLabelText('Cliente')[0]` /
>   `within(sección)`.
> - `findByText('Pérez, Juan')` → `findAllByText` / `within`.
> - `queryByLabelText('Monto')` (AC-021 de SPEC-006: `/gestion` no expone el
>   formulario de transferencia) → la ausencia de la transferencia se verifica
>   con queries específicas de esa sección ('Transferencia', 'CBU destino');
>   el label 'Monto' ya no es exclusivo de la transferencia.
> En los tests nuevos de `CajaSection`, las queries entre ambos formularios se
> acotan con `within(form)` o `getAllByLabelText`.

Sección e integración:

- [ ] AC-001: Component test — `/gestion` (sesión `ADMIN`) muestra la sección
      "Caja" con los formularios de depósito y retiro; no hay rutas nuevas
      (FR-001).
- [ ] AC-002: Component test — al seleccionar un cliente, la sección llama
      `GET /api/v1/cuentas?clienteId={id}` y los selectores de cuenta ofrecen
      **solo cuentas `ACTIVA`** (las `BLOQUEADA` no aparecen como opción)
      (FR-002, BR-003).
- [ ] AC-003: Component test — cliente sin cuentas o sin cuentas `ACTIVA`:
      estado vacío y formularios deshabilitados (FR-002, BR-003, AF-001).

Happy path:

- [ ] AC-004: Component test — depósito exitoso: envía el payload exacto
      `{cuentaId, monto}` a `POST /api/v1/depositos`; ante `201` muestra el
      panel de confirmación (`idMovimiento`, `monto` formateado en ARS,
      `fechaHora`) y refresca el listado de cuentas con el saldo actualizado
      (FR-003, FR-005, FR-006, BR-004).
- [ ] AC-005: Component test — retiro exitoso: envía el payload exacto
      `{cuentaId, monto}` a `POST /api/v1/retiros`; ante `201` muestra la
      confirmación y refresca el listado con el saldo actualizado (FR-004,
      FR-005, FR-006, BR-004).
- [ ] AC-006: Component test — el saldo post-operación proviene del
      `CuentaDto` refrescado (la UI no calcula el nuevo saldo) (FR-006,
      BR-004).

Validaciones (UX mirrors):

- [ ] AC-007: Component test — pre-validación del monto en ambos formularios:
      vacío, no numérico, `0`/negativo o con más de 2 decimales → error por
      campo y envío bloqueado sin request (BR-001, ERR-001).
- [ ] AC-008: Component test — retiro: monto mayor al saldo de la cuenta
      seleccionada → error por campo y envío bloqueado (BR-002, A-002).
- [ ] AC-009: Component test — sin cuenta seleccionada → envío bloqueado con
      error por campo (BR-003).
- [ ] AC-010: Unit test — la función de validación de la caja cubre los casos
      de BR-001 y BR-002 (monto vacío/`0`/negativo/3 decimales; retiro sin
      saldo suficiente) (BR-001, BR-002).

Errores con el envelope:

- [ ] AC-011: Component test — `400 DATOS_INVALIDOS` con `details` del campo
      `monto` → error por campo en `monto` y datos conservados (ERR-001).
- [ ] AC-012: Component test — `422 SALDO_INSUFICIENTE` → mensaje del
      envelope general y datos conservados (ERR-002, AF-002).
- [ ] AC-013: Component test — `422 CUENTA_BLOQUEADA` → mensaje del envelope
      (ERR-003, AF-004).
- [ ] AC-014: Component test — `404 CUENTA_NO_ENCONTRADA` → mensaje del
      envelope (ERR-004).
- [ ] AC-015: Component test — `409 CONFLICTO_CONCURRENCIA` → mensaje del
      envelope y reintento posible (ERR-005, AF-003).
- [ ] AC-016: Component test — `403 ACCESO_DENEGADO` → mensaje del envelope
      (ERR-006, defensivo).
- [ ] AC-017: Component test — error de red → `MENSAJE_ERROR_RED` y reintento
      posible (ERR-007, AF-003).

Robustez y calidad:

- [ ] AC-018: Component test — durante el envío el botón se deshabilita y no
      se duplica la request (FR-005, mirror AC-030 de SPEC-006).
- [ ] AC-019: `npm run lint` y `npm run typecheck` pasan sin errores; `npm run
      build` produce el bundle; `npm test` ejecuta la suite completa y todos
      los tests pasan (FR-007).
- [ ] AC-020: Sin dependencias npm nuevas y sin cambios en backend,
      migraciones ni `docker/` (AGENTS.md §13, §10).

---

## 12. Out of Scope

- **Retiros propios del `CLIENTE`** (pantalla de caja para `CLIENTE`): el
  backend lo permite (SPEC-005 §9), pero el issue #26 solo pide la caja del
  `ADMIN` en `/gestion`. Queda como evolución (A-005 de SPEC-006 sigue
  vigente para el `CLIENTE`).
- **Cambios en el backend**: los endpoints de depósito/retiro ya existen y
  están mergeados (SPEC-005); ningún cambio de contrato, seguridad o esquema.
- **Redesign de UI**: se mantiene el diseño actual (CSS plano existente y
  componentes existentes) hasta el redesign de UI (A-004, pedido explícito
  del issue).
- **Historial de movimientos en la sección Caja** (no requerido por el issue;
  ya existe en `/cuentas` — SPEC-006 FR-010).
- **i18n** (la SPA es solo en español).
- **Tests e2e** (Playwright/Cypress): solo lint, typecheck, unit/component
  tests y build (FR-007).
- **Límites de extracción, operaciones en moneda extranjera y suscriptores de
  eventos** (ya out of scope de SPEC-005 §12).

---

## 13. Dependencies

- **SPEC-005 (depósitos/retiros)**: contrato de los endpoints — payloads
  `{cuentaId, monto}`, confirmaciones `201`, reglas BR-001..BR-004 y errores
  ERR-001..ERR-006 (FR-003/004, BR-001..003, §8).
- **SPEC-006 (SPA)**: patrones de la SPA — `httpClient`/`ApiError`/envelope
  (FR-002), hooks de datos y mutación, secciones de `/gestion`, estados de
  UI, validaciones y tests (FR-001..007, BR-004/005, §11).
- **SPEC-002 (cuentas)**: `CuentaDto` y contrato del listado por `clienteId`
  (`GET /api/v1/cuentas?clienteId=`) (FR-002).
- **SPEC-003 (autenticación)**: JWT con claims `role`/`clienteId` (prerrequisito
  de §9; vía SPEC-006).
- **`ARCHITECTURE.md`** §7 (API REST y envelope de errores), §8 (seguridad y
  propiedad); **ADR-008** (decisiones del frontend).

---

## 14. Open Questions

- Ninguna pendiente: las ambigüedades del issue (estructura de la sección,
  módulo API, pre-validación de saldo y alcance del diseño) se resolvieron en
  modo autónomo como asunciones documentadas en §15. Si el Product Owner
  dispone otra cosa, deben ajustarse antes de la implementación.

---

## 15. Decisions and Assumptions

- **A-001 — Sección única "Caja" con dos formularios (depósito y retiro):** el
  issue pide "una nueva sección... formulario de depósito y retiro". Se
  interpreta como **una sección "Caja"** en `/gestion` con **dos formularios**
  (depósito y retiro), cada uno con su propio selector de cuenta (solo
  `ACTIVA`) y campo `monto`, compartiendo el selector de cliente con la
  sección (cada sección es autocontenida, patrón de `CuentasSection`). Se
  descarta un formulario único con toggle de tipo de operación (acopla ambos
  flujos y complica los tests) y dos secciones separadas (el issue habla de
  "una sección"). El layout exacto (apilados o lado a lado) queda a criterio
  del desarrollador siempre que se cumplan los criterios de aceptación (§11).
- **A-002 — Pre-validación de saldo suficiente en retiro: solo UX.** El issue
  pide validación de "saldo suficiente en retiro". Se implementa client-side
  como espejo del `422 SALDO_INSUFICIENTE` del backend (BR-002): bloquea el
  envío cuando el monto supera el saldo conocido de la cuenta seleccionada.
  El backend permanece como fuente de verdad: ante saldo desactualizado
  responde `422` y la UI muestra el mensaje del envelope (ERR-002).
- **A-003 — Nuevo módulo `src/api/caja.ts`:** el issue sugiere "api/cuentas.ts
  o nueva api/caja.ts". Se elige un **módulo nuevo** para no mezclar
  responsabilidades, consistente con el patrón un-módulo-por-recurso existente
  (`clientes.ts`, `cuentas.ts`, `transferencias.ts`); `cuentas.ts` sigue
  cubriendo el listado/apertura/movimientos.
- **A-004 — Mantener el diseño actual:** el issue pide "mantener el diseño
  actual hasta el redesign de UI". Se reutilizan los componentes y el CSS
  plano existentes (`CampoFormulario`, `Monto`, `Cargando`, `EstadoError`,
  `EstadoVacio`, clases `.seccion`/`.campo`/`.error-general`/`.confirmacion`);
  no se introduce sistema de diseño.

---

## 16. Related Documents

- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (API REST y envelope de errores),
  §8 (seguridad y propiedad)
- `docs/specs/SPEC-005-depositos-retiros.md` (contrato de caja: FR, BR,
  errores, autorización)
- `docs/architecture/SPEC-005.md` (contrato HTTP exacto de los endpoints y
  matchers de `SecurityConfig`)
- `docs/specs/SPEC-006-frontend-react.md` (patrones de la SPA: FR-002/013/015,
  BR-004/008/010, §8 errores, §9 matriz por rol, §11 AC, §12 out of scope y
  A-005 — parcialmente derogada por esta spec para la caja del `ADMIN`)
- `docs/specs/SPEC-002-cuentas.md` (`CuentaDto`, listado por `clienteId`)
- `docs/specs/SPEC-003-autenticacion.md` (claims `role`/`clienteId`)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend)
- `docs/sprints/backlog.md` (E5: US-5.1, US-5.2; E6: US-6.4)