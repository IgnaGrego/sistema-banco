# SPEC-006 — Frontend React (SPA)

## Status

Approved

---

## 1. Objective

Construir la SPA del sistema bancario con **React 18 + TypeScript + Vite**
(Epic E6 — `docs/sprints/backlog.md`), que consume la API REST existente
`/api/v1` con autenticación **Bearer JWT** (SPEC-003). La SPA cubre cuatro
flujos: login (US-6.1), listado de cuentas y saldo del `CLIENTE` (US-6.2),
formulario de transferencia del `CLIENTE` (US-6.3) y gestión de clientes y
cuentas del `ADMIN` (US-6.4). Incluye lint, typecheck, tests básicos
(Vitest + React Testing Library) y build de producción.

La SPA **no introduce reglas de negocio nuevas**: el backend implementado en
Sprints 1–3 es la fuente de verdad (AGENTS.md §10); el frontend solo consume
sus contratos REST y pre-valida en la UI las mismas reglas que el backend ya
enforces (§5).

---

## 2. Actors

- **Usuario no autenticado**: visita la SPA y es redirigido a la pantalla de
  login. No accede a ninguna vista protegida (FR-006).
- **CLIENTE**: usuario autenticado con rol `CLIENTE` (claim `role` del JWT —
  SPEC-003 FR-002). Ve sus propias cuentas y saldos (US-6.2), el historial de
  movimientos de sus cuentas y transfiere desde sus propias cuentas (US-6.3).
- **ADMIN**: usuario autenticado con rol `ADMIN`. Gestiona clientes
  (listar/crear/editar) y cuentas (listar por cliente / abrir) (US-6.4).

No existe un rol "operator": el sistema solo define `CLIENTE` y `ADMIN`
(SPEC-003, `ARCHITECTURE.md` §4).

---

## 3. Preconditions

- El backend está corriendo y expone la API en `http://localhost:8080`
  (puerto por defecto de Spring Boot; sin `server.port` explícito en
  `application.yml`).
- La API es accesible sin CORS desde la SPA en desarrollo: el proxy de Vite
  reenvía `/api` al backend (same-origin desde el navegador — A-001; el
  backend NO habilita CORS, `SecurityConfig` — ver §12).
- Existen usuarios `CLIENTE` y `ADMIN` registrados en el backend (SPEC-003
  FR-001; el registro no forma parte de esta SPA — A-004).
- El `ADMIN` gestiona clientes/cuentas con su propio token; el `CLIENTE`
  opera solo sus propias cuentas (propiedad verificada server-side —
  SPEC-003 BR-004, `ARCHITECTURE.md` §8).
- El scaffolding de `frontend/` está vacío (solo `src/{api,components,hooks,
  pages,store}/.gitkeep`): la SPA se crea desde cero en esta spec (FR-001).

---

## 4. Functional Requirements

### FR-001 — Scaffolding de la SPA

Crear la SPA bajo `frontend/` con **React 18 + TypeScript + Vite** y los
scripts npm: `lint` (ESLint), `typecheck` (`tsc --noEmit`), `test`
(Vitest + React Testing Library) y `build` (`vite build`). La estructura de
`src/` sigue la existente: `api/` (cliente HTTP), `components/` (UI
reutilizable), `hooks/` (lógica de sesión/datos), `pages/` (vistas por rol) y
`store/` (estado global liviano — A-006). No se agregan dependencias sin
justificación (AGENTS.md §13).

### FR-002 — Cliente HTTP con Bearer token

Un módulo en `src/api/` encapsula las llamadas a `/api/v1`. Toda request a la
API incluye el header `Authorization: Bearer <token>` cuando existe sesión
(FR-003). Las respuestas de error se parsean con el envelope JSON estándar
`{ code, message, details? }` (`ARCHITECTURE.md` §7) para su presentación
(§8). El token nunca se adjunta a orígenes fuera de `/api/v1` (BR-009).

### FR-003 — Sesión persistente

El token JWT se persiste en `localStorage` (clave `banco.token` — A-003) al
iniciar sesión y se elimina al cerrar sesión (FR-014). Helpers de sesión:
guardar, leer y limpiar el token. Al cargar la SPA, si existe un token
guardado, la sesión se restaura para el guard de rutas (FR-006).

### FR-004 — Decodificación del JWT (rol y clienteId)

El payload del JWT se decodifica client-side (base64url, **sin verificación
de firma**: la firma y la expiración las verifica el backend en cada request —
`JwtService.validar`). La utilidad extrae los claims `role` (`CLIENTE` |
`ADMIN`) y, si existe, `clienteId` (solo tokens `CLIENTE` — SPEC-003 FR-002,
`JwtService`). El rol así obtenido determina la redirección post-login
(FR-005) y las rutas permitidas (FR-006). Si el token no puede decodificarse,
se trata como sesión inválida (ERR-002).

### FR-005 — Login y redirección por rol

Pantalla de login (US-6.1): formulario con `username` y `password` que envía
`POST /api/v1/auth/login` (`{username, password}` — `LoginRequest`). El
backend responde `200` con `{token}` (`LoginResponse` — el rol NO viaja en la
respuesta; se lee del JWT, FR-004). Con token almacenado (FR-003), la SPA
redirige por rol: `CLIENTE` → `/cuentas`, `ADMIN` → `/gestion` (A-007). Con
credenciales inválidas, muestra el error y permanece en `/login` (ERR-001).

### FR-006 — Protección de rutas por autenticación y rol

Un guard de rutas:
- Sin sesión → redirige a `/login`.
- `CLIENTE` → solo rutas de cliente (`/cuentas`); no accede a rutas de
  `ADMIN` (`/gestion`).
- `ADMIN` → solo rutas de admin (`/gestion`); no accede a rutas de `CLIENTE`.
- Con sesión válida y visitando `/login` → redirige a la ruta de su rol
  (FR-005).

La navegación por rol es UX; la autorización real se verifica server-side en
cada endpoint (§9, BR-008).

### FR-007 — Manejo de 401 (sesión expirada/inválida)

Ante una respuesta `401` en cualquier request autenticado (token expirado,
mal firmado o ausente — SPEC-003 ERR-002), la SPA limpia la sesión (FR-003) y
redirige a `/login`. Es el mecanismo de "logout automático" por expiración del
token (JWT de expiración corta — `jwt-expiration-minutes`, default 60).

### FR-008 — Proxy de desarrollo (Vite)

`vite.config.ts` define el proxy de desarrollo: `/api` → `http://localhost:8080`
(sin reescritura de path). El navegador solo ve same-origin (la SPA en el
puerto de Vite), por lo que el backend NO requiere CORS (A-001). Ver §12
respecto a producción.

### FR-009 — CLIENTE: listado de cuentas y saldo (US-6.2)

Vista `/cuentas`: `GET /api/v1/cuentas` **sin parámetros** — el backend
resuelve el `clienteId` del claim y devuelve solo las cuentas del `CLIENTE`
autenticado (SPEC-002 FR-006, AF-001). Se renderiza cada `CuentaDto`
(`id`, `clienteId`, `cbu`, `tipo`, `saldo`, `moneda`, `estado`, `createdAt`)
con su saldo formateado en `ARS` (BR-010) y se distingue el estado
`ACTIVA`/`BLOQUEADA`. Estados vacío y de carga incluidos (FR-015, ERR-009).

### FR-010 — CLIENTE: movimientos por cuenta (US-4.2/6.2)

Desde la vista de cuentas, el `CLIENTE` puede consultar el historial de una
cuenta propia: `GET /api/v1/cuentas/{id}/movimientos` → `List<MovimientoDto>`
(`id`, `cuentaId`, `tipo`, `monto`, `moneda`, `fecha`, `cuentaContraparteId`),
ordenado por `fecha` descendente (SPEC-004 FR-005). Tipos mostrados:
`DEPOSITO`, `RETIRO`, `TRANSFERENCIA_ENTRANTE`, `TRANSFERENCIA_SALIENTE`
(`ARCHITECTURE.md` §4).

### FR-011 — CLIENTE: formulario de transferencia (US-6.3)

Vista de transferencia accesible desde `/cuentas`:
- Selector de **cuenta origen** obligatorio, poblado con las cuentas propias
  en estado `ACTIVA` (BR-002).
- Campo **CBU destino** (BR-003).
- Campo **monto** (BR-004).
- Envía `POST /api/v1/transferencias` con `{cuentaOrigenId, cbuDestino,
  monto}` (`TransferirRequest`). Ante `201` (`TransferenciaConfirmacion`:
  `idTransferencia`, `monto`, `cbuDestino`, `fechaHora`) se muestra la
  confirmación y se **refrescan** cuentas (saldo — FR-009) y movimientos
  (FR-010). Los errores de negocio (`422`) se muestran en el formulario sin
  perder los datos ingresados (ERR-006).

### FR-012 — ADMIN: gestión de clientes (US-6.4)

Vista `/gestion` (sección clientes):
- Listado: `GET /api/v1/clientes` → `List<ClienteDto>` (`id`, `nombre`,
  `apellido`, `dni`, `email`, `telefono`, `fechaAlta`).
- Alta: `POST /api/v1/clientes` con `{nombre, apellido, dni, email, telefono}`
  (`CrearClienteRequest`); `201` con el `ClienteDto` creado.
- Edición: `PUT /api/v1/clientes/{id}` con el mismo payload
  (`ActualizarClienteRequest`); `200` con la representación actualizada.

### FR-013 — ADMIN: gestión de cuentas (US-6.4)

Vista `/gestion` (sección cuentas):
- Listado por cliente: `GET /api/v1/cuentas?clienteId={id}` → `List<CuentaDto>`
  del cliente seleccionado (SPEC-002 FR-006).
- Apertura: `POST /api/v1/cuentas` con `{clienteId, tipo, moneda}`
  (`AbrirCuentaRequest`; `moneda` opcional, default `ARS` — SPEC-002 FR-008);
  `201` con el `CuentaDto` creado y refresco de la lista.

### FR-014 — Logout

Acción de cierre de sesión disponible en todas las vistas autenticadas:
limpia el token de `localStorage` (FR-003) y redirige a `/login`.

### FR-015 — Estados de UI y visibilidad por rol

Toda vista de datos maneja estados de **carga** (indicador mientras la request
está en vuelo) y **error** (mensaje con el `message` del envelope; ver §8).
Las vistas vacías (sin cuentas, sin movimientos, sin clientes) muestran un
estado vacío con mensaje (ERR-009). La UI **oculta** las acciones exclusivas
de `ADMIN` al `CLIENTE` y viceversa (BR-008): el `CLIENTE` no ve alta/edición
de clientes, apertura de cuentas ni depósitos; el `ADMIN` no ve el formulario
de transferencia. El backend permanece como punto de enforcement (§9).

---

## 5. Business Rules

Las reglas siguientes son **pre-validaciones de UX client-side** que espejan
reglas ya enforced por el backend (SPEC-001/002/004); no crean reglas de
negocio nuevas. El backend es la fuente de verdad y rechaza cualquier payload
inválido con el envelope estándar (BR-008).

### BR-001 — Login: campos obligatorios

`username` y `password` son obligatorios (no vacíos tras recortar espacios).
El formulario no se envía si faltan; se muestra error de campo.

### BR-002 — Transferencia: cuenta origen

La selección de cuenta origen es obligatoria y debe ser una cuenta **propia**
del `CLIENTE` en estado `ACTIVA` (mirror de SPEC-002 BR-003/BR-006: solo se
opera sobre cuentas propias `ACTIVA`). Las cuentas `BLOQUEADA` no se ofrecen
como origen.

### BR-003 — Transferencia: CBU destino

`cbuDestino` es obligatorio y debe cumplir el formato del VO `CBU`:
exactamente **22 dígitos numéricos** (`^[0-9]{22}$` — mirror de SPEC-002
BR-001 / SPEC-004 §10).

### BR-004 — Transferencia: monto

`monto` es obligatorio, numérico, **mayor a 0** y con **hasta 2 decimales**
(mirror de SPEC-004 BR-003).

### BR-005 — Transferencia: sin auto-transferencia

El `CBU` destino debe ser distinto del `CBU` de la cuenta origen (mirror de
SPEC-004 BR-005). La pre-validación client-side evita el envío; el backend
rechaza con `422 AUTO_TRANSFERENCIA` si igualmente ocurre (ERR-006).

### BR-006 — Cliente: validación del formulario (alta/edición)

Espejo de SPEC-001 (BR-002..BR-005):
- `nombre` y `apellido` obligatorios, no vacíos, hasta 100 caracteres.
- `dni` obligatorio, solo dígitos, de 7 a 8 caracteres.
- `email` obligatorio, formato válido, hasta 254 caracteres.
- `telefono` opcional; si se informa, `^\+?[0-9]{6,15}$`.

### BR-007 — Cuenta: validación del formulario de apertura

Espejo de SPEC-002 (FR-001, FR-008, BR-005):
- `clienteId` obligatorio (cliente seleccionado de la lista).
- `tipo` obligatorio: `CAJA_AHORRO` | `CUENTA_CORRIENTE`.
- `moneda` opcional; por defecto `ARS`. Solo se acepta `ARS` en el MVP.

### BR-008 — Sin bypass de autorización client-side

La UI oculta acciones según el rol (FR-015), pero **nunca** es el punto de
enforcement: toda request se autoriza server-side (matchers RBAC +
verificación de propiedad en la capa de aplicación — `SecurityConfig`,
`ARCHITECTURE.md` §8). Un `CLIENTE` que intente una acción de `ADMIN` recibe
`403` (ERR-003).

### BR-009 — Alcance del token

El token solo se adjunta a requests hacia la API `/api/v1` (FR-002); nunca se
envía a otros orígenes ni se incluye en logs. Se almacena únicamente en
`localStorage` (A-003) y se elimina con logout (FR-014) o ante `401`
(FR-007).

### BR-010 — Formato de montos

Saldos y montos se muestran en `ARS` con 2 decimales (formato numérico local
`es-AR`). El frontend **no calcula** saldos ni montos: solo formatea los
valores `BigDecimal` devueltos por el backend (los cálculos monetarios viven
en el dominio — `ARCHITECTURE.md` §6, AGENTS.md §11).

---

## 6. Main Flow — Operación de la SPA

1. El usuario abre la SPA. Sin sesión (FR-003), el guard de rutas redirige a
   `/login` (FR-006).
2. El usuario ingresa `username` y `password` y envía el formulario (BR-001).
3. La SPA llama `POST /api/v1/auth/login` (FR-005). El backend responde `200`
   con `{token}`.
4. La SPA almacena el token en `localStorage` (FR-003), decodifica el payload
   y obtiene `role` (`CLIENTE` | `ADMIN`) y, si aplica, `clienteId` (FR-004).
5. La SPA redirige por rol: `CLIENTE` → `/cuentas`; `ADMIN` → `/gestion`
   (FR-005).
6. **CLIENTE** (`/cuentas`): `GET /api/v1/cuentas` (sin parámetros) →
   lista de cuentas propias con saldo (FR-009). El usuario puede consultar
   los movimientos de una cuenta (`GET /api/v1/cuentas/{id}/movimientos`,
   FR-010) y transferir desde el formulario (FR-011): envía
   `POST /api/v1/transferencias`; ante `201` muestra la confirmación
   (`idTransferencia`, `monto`, `cbuDestino`, `fechaHora`) y refresca saldos y
   movimientos.
7. **ADMIN** (`/gestion`): lista clientes (`GET /api/v1/clientes`, FR-012),
   crea/edita clientes (`POST`/`PUT`), selecciona un cliente para listar sus
   cuentas (`GET /api/v1/cuentas?clienteId=`, FR-013) y abre cuentas
   (`POST /api/v1/cuentas`).
8. El usuario cierra sesión (FR-014): se limpia el token y se vuelve a
   `/login`.

---

## 7. Alternative Flows

### AF-001 — Login fallido

Credenciales incorrectas o usuario inexistente: el backend responde `401`
(SPEC-003 ERR-001, respuesta idéntica en ambos casos). La SPA muestra un
mensaje de error genérico en el formulario y permanece en `/login`
(ERR-001). No se almacena token.

### AF-002 — Token expirado o inválido durante la sesión

Una request autenticada recibe `401` (token expirado — expiración corta del
JWT — o malformado): la SPA limpia la sesión y redirige a `/login`
(FR-007, ERR-002). El usuario debe autenticarse nuevamente (no hay refresh
tokens — SPEC-003 A-001, §12).

### AF-003 — CLIENTE sin cuentas

`GET /api/v1/cuentas` devuelve una lista vacía: la vista `/cuentas` muestra el
estado vacío con un mensaje (FR-015, ERR-009). El formulario de transferencia
no tiene cuentas origen disponibles (BR-002) y permanece deshabilitado.

### AF-004 — Transferencia exitosa

Tras `201` de `POST /api/v1/transferencias`, la SPA muestra la confirmación
con `idTransferencia` (id del `Movimiento` `TRANSFERENCIA_SALIENTE` — SPEC-004
A-007) y refresca la lista de cuentas y movimientos para reflejar el nuevo
saldo (FR-011).

### AF-005 — Acción prohibida por rol (403)

El backend responde `403 ACCESO_DENEGADO` (p. ej., `CLIENTE` intentando una
acción de `ADMIN` por una vía no oculta en la UI): la SPA muestra el mensaje
de error del envelope y oculta la acción (FR-015, ERR-003). El guard de rutas
impide además navegar a rutas de otro rol (FR-006).

### AF-006 — ADMIN abre una cuenta

Tras `201` de `POST /api/v1/cuentas`, la lista de cuentas del cliente
seleccionado se refresca (FR-013) y la nueva cuenta aparece con saldo `0` y
estado `ACTIVA`.

---

## 8. Error Cases

Todos los errores de la API usan el envelope JSON estándar
`{ code, message, details? }` con `details: [{campo, mensaje}]`
(`ARCHITECTURE.md` §7). La SPA muestra `message` (y los `details` por campo
cuando corresponda) en la vista activa.

### ERR-001 — Credenciales inválidas en login (401)

`POST /api/v1/auth/login` responde `401` (SPEC-003 ERR-001).

Se muestra un mensaje genérico de error en el formulario de login (sin
revelar si falló el usuario o la password — SPEC-003 A-004); no se redirige;
no se almacena token (AF-001).

### ERR-002 — Token ausente, expirado o mal firmado (401)

Cualquier request autenticado responde `401` (SPEC-003 ERR-002), o el token
guardado no puede decodificarse (FR-004).

Se limpia la sesión y se redirige a `/login` (FR-007, AF-002).

### ERR-003 — Acceso denegado (403)

Token válido pero el rol no tiene permiso para el recurso, o `CLIENTE`
accediendo a un recurso ajeno (SPEC-003 ERR-003; `ACCESO_DENEGADO`).

Se muestra el mensaje del envelope en la vista activa y se ocultan las
acciones no permitidas (FR-015, AF-005). No se fuerza navegación salvo que el
guard de rutas determine que la ruta no corresponde al rol (FR-006).

### ERR-004 — Datos inválidos (400)

`400 DATOS_INVALIDOS` con `details` indicando el/los campo(s) (SPEC-001
ERR-002, SPEC-004 ERR-004): p. ej., `monto <= 0` o con más de 2 decimales,
`dni` con longitud distinta de 7–8, `email` malformado, `tipo` inválido.

Se muestran los errores por campo en el formulario correspondiente; los datos
ingresados no se pierden.

### ERR-005 — Recurso inexistente (404)

`404` (`CUENTA_NO_ENCONTRADA`, `CLIENTE_NO_ENCONTRADO` — SPEC-001 ERR-004,
SPEC-002 ERR-002/003, SPEC-004 ERR-002): p. ej., cuenta cuyo historial se
consulta, cliente a editar, `clienteId` inexistente en el listado de cuentas.

Se muestra un mensaje informativo en la vista activa.

### ERR-006 — Regla de negocio (422)

`422` con códigos de negocio: `SALDO_INSUFICIENTE`, `CUENTA_BLOQUEADA`,
`LIMITE_DIARIO_EXCEDIDO`, `AUTO_TRANSFERENCIA`, `MONEDA_INCOMPATIBLE`
(SPEC-004 ERR-001/003/007/008/009) y `MONEDA_NO_SOPORTADA` (SPEC-002 ERR-009).

Se muestra el mensaje en el formulario de transferencia/apertura sin perder
los datos ingresados (FR-011, AC-019).

### ERR-007 — Conflicto (409)

`409` por concurrencia (`CONFLICTO_CONCURRENCIA` — SPEC-004 ERR-005) o
unicidad (`CONFLICTO_UNICIDAD` — SPEC-002 ERR-010, SPEC-001 ERR-001): p. ej.,
dos operaciones concurrentes sobre la misma cuenta, o `dni`/`email`
duplicados al crear/editar un cliente.

Se muestra un mensaje indicando el conflicto y se invita a reintentar (la
transferencia puede reintentarse — SPEC-004 A-004).

### ERR-008 — Error de red / backend no disponible

La request no puede completarse (backend caído, proxy sin destino, timeout).

Se muestra un mensaje de error de conexión con posibilidad de reintentar; los
estados de carga no bloquean la navegación (FR-015).

### ERR-009 — Estados vacíos

Listas vacías: sin cuentas (AF-003), sin movimientos, sin clientes o sin
cuentas para un cliente.

Se muestra el estado vacío con un mensaje descriptivo y, si corresponde, la
acción primaria habilitada (FR-015).

---

## 9. Authorization

La SPA consume únicamente endpoints ya protegidos por el backend
(`SecurityConfig` — matchers RBAC). La matriz por rol:

- **Público (sin token):**
  - `POST /api/v1/auth/login` — pantalla de login (FR-005). El registro
    (`POST /api/v1/auth/register`) NO se consume en esta SPA (A-004).
- **CLIENTE (token con `role` = `CLIENTE`, `clienteId` en el claim):**
  - `GET /api/v1/cuentas` — solo sus propias cuentas (sin parámetro —
    SPEC-002 FR-006/AF-001).
  - `GET /api/v1/cuentas/{id}/movimientos` — solo cuentas propias (SPEC-004
    FR-005, A-005; propiedad verificada en el use case).
  - `POST /api/v1/transferencias` — solo `CLIENTE`, cuenta origen propia
    (SPEC-004 §9).
  - `POST /api/v1/retiros` (cuentas propias) — **opcional**, ver §12 (A-005).
- **ADMIN (token con `role` = `ADMIN`, sin `clienteId`):**
  - `GET /api/v1/clientes`, `POST /api/v1/clientes`,
    `PUT /api/v1/clientes/{id}` — gestión de clientes (SPEC-001 §9).
  - `GET /api/v1/cuentas` (+ `?clienteId=`),
    `POST /api/v1/cuentas` — gestión de cuentas (SPEC-002 §9).
  - `POST /api/v1/depositos`, `POST /api/v1/retiros` — caja (SPEC-005 §9;
    opcional en la SPA, ver §12).

El **routing post-login por rol** (FR-005/FR-006) y el **ocultamiento de
acciones por rol** (FR-015, BR-008) son UX: la autorización se verifica
server-side en cada endpoint (nunca solo en el frontend — AGENTS.md §17).

---

## 10. Data Changes

- **Backend y base de datos:** sin cambios. La SPA solo consume los
  endpoints existentes (Sprints 1–3). No se agregan migraciones Flyway ni
  tablas.
- **Frontend (nuevo):** proyecto completo bajo `frontend/`:
  - `package.json` con scripts `lint`, `typecheck`, `test`, `build` (FR-001).
  - Configuración: Vite (+ proxy `/api` → `http://localhost:8080`, FR-008),
    TypeScript (`tsconfig`), ESLint, Vitest + React Testing Library.
  - `src/api/` (cliente HTTP, FR-002), `src/hooks/` (sesión/datos),
    `src/components/` (UI), `src/pages/` (`Login`, `Cuentas`,
    `Transferencia`, `Gestion`), `src/store/` (estado liviano — A-006).
- **Sesión:** JWT en `localStorage` bajo la clave `banco.token` (FR-003,
  A-003). Dato efímero del navegador; no se persiste en el servidor.

---

## 11. Acceptance Criteria

**Estrategia de testing:** los criterios se verifican con la suite de la SPA
con `npm test` (Vitest + React Testing Library): **unit tests** para
utilidades puras (decodificación JWT, sesión, cliente HTTP) y **component
tests** para páginas y formularios (render, interacción, payloads y estados).
Cada AC indica el tipo de test que lo verifica. Además, `npm run lint` y
`npm run typecheck` deben pasar y `npm run build` debe producir el bundle.

Estructura y herramientas:

- [ ] AC-001: El scaffolding existe bajo `frontend/` con scripts npm `lint`,
      `typecheck`, `test` y `build`, y la estructura `src/{api,components,
      hooks,pages,store}` (FR-001).
- [ ] AC-002: `npm run lint` pasa sin errores (FR-001).
- [ ] AC-003: `npm run typecheck` pasa sin errores de tipos (FR-001).
- [ ] AC-004: `npm run build` produce el bundle de producción sin errores
      (FR-001).
- [ ] AC-005: `npm test` ejecuta la suite (Vitest + RTL) y todos los tests
      pasan (FR-001).

Autenticación y sesión:

- [ ] AC-006: Unit test — la sesión guarda el token recibido de
      `POST /api/v1/auth/login` en `localStorage` (`banco.token`) y lo
      elimina con logout (FR-003, FR-014).
- [ ] AC-007: Unit test — la utilidad de decodificación extrae `role` y
      `clienteId` (solo tokens `CLIENTE`) del payload JWT (base64url, sin
      verificar firma); un payload no decodificable se trata como sesión
      inválida (FR-004, ERR-002).
- [ ] AC-008: Component test — con credenciales correctas, el login envía
      `POST /api/v1/auth/login` con `{username, password}` y redirige a
      `/cuentas` si el rol decodificado es `CLIENTE` y a `/gestion` si es
      `ADMIN` (FR-005, FR-004).
- [ ] AC-009: Component test — con credenciales incorrectas (`401`), el
      formulario de login muestra el mensaje de error genérico, no almacena
      token y permanece en `/login` (FR-005, ERR-001, AF-001).
- [ ] AC-010: Unit test — el cliente HTTP adjunta
      `Authorization: Bearer <token>` a toda request a `/api/v1` cuando
      existe sesión, y NO adjunta token cuando no hay sesión ni a URLs fuera
      de `/api/v1` (FR-002, BR-009).
- [ ] AC-011: Unit test — ante una respuesta `401` en un endpoint protegido,
      la sesión se limpia y se redirige a `/login` (FR-007, ERR-002, AF-002).
- [ ] AC-012: Component test — el logout limpia el token de `localStorage` y
      redirige a `/login` (FR-014).
- [ ] AC-013: Unit test — el guard de rutas redirige a `/login` sin sesión;
      un `CLIENTE` no accede a `/gestion` y un `ADMIN` no accede a las rutas
      de `CLIENTE`; con sesión y visitando `/login` redirige a la ruta de su
      rol (FR-006).

CLIENTE (cuentas, movimientos y transferencia):

- [ ] AC-014: Component test — la vista `/cuentas` llama
      `GET /api/v1/cuentas` **sin parámetros** y renderiza las cuentas
      propias con `saldo` formateado en ARS, `cbu`, `tipo`, `moneda` y
      `estado` (FR-009, BR-010).
- [ ] AC-015: Component test — con la lista de cuentas vacía, se muestra el
      estado vacío y el formulario de transferencia sin cuentas origen
      disponibles (FR-015, ERR-009, AF-003).
- [ ] AC-016: Component test — consultar una cuenta dispara
      `GET /api/v1/cuentas/{id}/movimientos` y renderiza los `MovimientoDto`
      con su `tipo`, `monto`, `moneda` y `fecha` (FR-010).
- [ ] AC-017: Component test — el formulario de transferencia envía el
      payload exacto `{cuentaOrigenId, cbuDestino, monto}` a
      `POST /api/v1/transferencias` (FR-011).
- [ ] AC-018: Component test — ante `201`, se muestra la confirmación
      (`idTransferencia`, `monto`, `cbuDestino`, `fechaHora`) y se refrescan
      cuentas y movimientos (FR-011, AF-004).
- [ ] AC-019: Component test — ante `422` (p. ej. `SALDO_INSUFICIENTE`), el
      mensaje del envelope se muestra en el formulario y los datos ingresados
      se conservan (ERR-006).
- [ ] AC-020: Component test — las pre-validaciones del formulario (cuenta
      origen obligatoria, CBU de 22 dígitos, monto > 0 con ≤ 2 decimales,
      CBU destino ≠ CBU origen) bloquean el envío y muestran errores por
      campo (BR-002..BR-005, ERR-004).
- [ ] AC-021: Component test — la UI del `CLIENTE` no expone acciones
      exclusivas de `ADMIN` (crear/editar clientes, abrir cuentas, depósitos)
      (BR-008, FR-015).

ADMIN (gestión de clientes y cuentas):

- [ ] AC-022: Component test — la vista `/gestion` lista los clientes
      (`GET /api/v1/clientes`) con `nombre`, `apellido`, `dni` y `email`
      (FR-012).
- [ ] AC-023: Component test — crear cliente envía
      `POST /api/v1/clientes` con `{nombre, apellido, dni, email, telefono}`
      y agrega el `ClienteDto` devuelto (`201`) a la lista (FR-012).
- [ ] AC-024: Component test — editar cliente envía
      `PUT /api/v1/clientes/{id}` con el payload actualizado y muestra la
      representación devuelta (`200`) (FR-012).
- [ ] AC-025: Component test — abrir cuenta envía
      `POST /api/v1/cuentas` con `{clienteId, tipo, moneda}` (moneda
      opcional, default `ARS`) y refresca la lista de cuentas del cliente
      (FR-013, AF-006, BR-007).
- [ ] AC-026: Component test — listar cuentas por cliente usa
      `GET /api/v1/cuentas?clienteId={id}` y renderiza las `CuentaDto` del
      cliente seleccionado (FR-013).
- [ ] AC-027: Component test — ante `400 DATOS_INVALIDOS` con `details`, los
      errores se muestran por campo en los formularios de cliente/cuenta
      (ERR-004).
- [ ] AC-028: Component test — ante `403`/`404`/`409`/`422` (p. ej.
      `ACCESO_DENEGADO`, `CUENTA_NO_ENCONTRADA`, `CONFLICTO_CONCURRENCIA`,
      `LIMITE_DIARIO_EXCEDIDO`), se muestra el mensaje del envelope en la
      vista correspondiente (ERR-003, ERR-005, ERR-006, ERR-007).

Robustez:

- [ ] AC-029: Component test — ante error de red (request fallida), se
      muestra el mensaje de error de conexión y la operación puede
      reintentarse (ERR-008).
- [ ] AC-030: Component test — durante las requests asíncronas se muestra el
      estado de carga y no se duplican envíos (FR-015).
- [ ] AC-031: Unit test — la utilidad de sesión nunca adjunta el token a
      URLs fuera de `/api/v1` y no lo expone en logs (BR-009).

---

## 12. Out of Scope

- **Cambios en el backend** (Sprints 1–3 ya implementados y mergeados en
  `testing`), salvo un ajuste estrictamente necesario que deberá
  documentarse y aprobarse por separado. En particular, **habilitar CORS en
  el backend queda fuera de alcance**: en desarrollo se resuelve con el proxy
  de Vite (FR-008, A-001); producción queda fuera (§12).
- **Pantalla de registro de usuarios** (`POST /api/v1/auth/register` no se
  consume en la SPA — A-004).
- **Pantallas de depósitos/retiros** para `CLIENTE` (retiros propios: opcional,
  A-005) y para `ADMIN` (caja): las historias US-6.x no las requieren.
- **Despliegue a producción** de la SPA (build local/CI; el proxy de Vite es
  solo de desarrollo — A-001).
- **Pipeline CI/CD** del frontend (GitHub Actions, etc.).
- **Tests e2e** (Playwright/Cypress): solo lint, typecheck, unit/component
  tests (Vitest + RTL) y build (FR-001, §11).
- **i18n** (la SPA es solo en español).
- **Librerías de gestión de estado complejas** (Redux/Zustand): solo React
  state/context (A-006).
- **Refresh tokens / re-autenticación silenciosa** (SPEC-003 A-001: token de
  expiración corta; al vencer, volver a login — FR-007).
- **Almacenamiento del token fuera de `localStorage`** (cookies `httpOnly`,
  etc. — A-003).
- **Sistema de diseño / accesibilidad completa / tests visuales.**

---

## 13. Dependencies

- **SPEC-003** (autenticación): JWT con claims `role`/`clienteId`, endpoints
  `POST /api/v1/auth/login`, códigos `401`/`403` (FR-002..FR-007).
- **SPEC-001** (clientes): `ClienteDto`, `CrearClienteRequest`,
  `ActualizarClienteRequest` y endpoints de clientes (FR-012, BR-006).
- **SPEC-002** (cuentas): `CuentaDto`, `AbrirCuentaRequest`, contrato del
  listado por `clienteId` y propiedad de `CLIENTE` (FR-009, FR-013, BR-007).
- **SPEC-004** (transferencias): `TransferirRequest`,
  `TransferenciaConfirmacion`, historial `GET /api/v1/cuentas/{id}/movimientos`
  y errores `422`/`409` (FR-010, FR-011, ERR-006/007).
- **SPEC-005** (depósitos/retiros): solo referencia de contrato; no se
  consume en la SPA en este sprint (§12, A-005).
- **`ARCHITECTURE.md`** §1 (contenedor SPA), §7 (API REST y envelope de
  errores), §8 (seguridad y propiedad).
- **ADRs**: ADR-003 (JWT + Spring Security + RBAC), ADR-004 (contrato de
  claims), ADR-005 (emisión de tokens).

---

## 14. Open Questions

- Ninguna pendiente: las ambigüedades detectadas (estrategia de acceso a la
  API sin CORS, detección del rol tras el login, almacenamiento de la sesión,
  alcance de registro/depósitos/retiros en la SPA y gestión de estado) se
  resolvieron en modo autónomo como asunciones documentadas en §15. Si el
  Product Owner dispone otra cosa, deben ajustarse antes de la
  implementación.

---

## 15. Assumptions

Las siguientes decisiones no estaban definidas explícitamente en la
documentación existente; se documentan como asunciones con la interpretación
recomendada, resueltas en modo autónomo.

- **A-001 — Acceso a la API sin CORS (proxy de Vite):** el backend NO
  habilita CORS (`SecurityConfig`: "Sin CORS (no hay frontend en este
  sprint)"). En desarrollo, la SPA se sirve desde el servidor de Vite y el
  proxy reenvía `/api` → `http://localhost:8080` (FR-008): desde el navegador
  todo es same-origin y no se requiere CORS. La habilitación de CORS en el
  backend queda fuera de alcance (§12); si en el futuro la SPA y la API se
  sirven desde orígenes distintos, se requerirá un cambio de backend
  documentado y aprobado por separado.
- **A-002 — Detección del rol client-side (decodificación del JWT):** el
  `LoginResponse` del backend solo devuelve `{token}` (sin `rol`). El rol (y
  `clienteId` para `CLIENTE`) se obtiene decodificando el payload del JWT
  (base64url) **sin verificar la firma**: el backend verifica firma y
  expiración en cada request (`JwtService.validar`); el frontend solo lee
  claims para UX (routing y visibilidad — FR-004/005/006). Alternativa
  descartada: agregar `rol` al `LoginResponse` (cambio de backend, fuera de
  alcance).
- **A-003 — Sesión en `localStorage`:** el JWT se guarda en `localStorage`
  bajo la clave `banco.token` (FR-003). Se acepta el riesgo XSS propio de
  `localStorage` para el MVP; no se usan cookies `httpOnly` (requeriría
  cambios de backend). El token nunca se adjunta a orígenes fuera de
  `/api/v1` (BR-009) y se elimina con logout o ante `401` (FR-007/014).
- **A-004 — Registro de usuarios fuera de la SPA:** la SPA solo implementa
  login (US-6.1); el registro (`POST /api/v1/auth/register`) no se consume
  (US-3.1 pertenece a E3/SPEC-003 y no forma parte de E6). Se asume que los
  usuarios se crean por otro medio (p. ej., directamente contra la API o
  script).
- **A-005 — Depósitos/retiros en la SPA:** las pantallas de depósito y
  retiro no se incluyen en el MVP de la SPA: las historias US-6.1..6.4 no las
  requieren (la caja `ADMIN` y el retiro propio de `CLIENTE` quedan como
  evolución — §9 las lista como opcionales). Si el Product Owner las
  requiere, se agregan como historias posteriores sobre los contratos ya
  existentes (SPEC-005).
- **A-006 — Gestión de estado:** solo React state/context (la carpeta
  `store/` aloja el contexto de sesión/rol y el estado compartido mínimo).
  Sin Redux/Zustand: alcance acotado a 4 flujos sin estado compartido
  complejo (§12).
- **A-007 — Rutas y estructura de la SPA:** rutas `/login`, `/cuentas`
  (CLIENTE) y `/gestion` (ADMIN); estructura `src/{api,components,hooks,
  pages,store}` (ya esbozada con `.gitkeep`). Nombres ajustables por el
  desarrollador siempre que se cumplan los criterios de aceptación (§11).
- **A-008 — El frontend no es fuente de verdad:** las pre-validaciones de
  §5 (BR-001..BR-007) son UX que espejan reglas del backend; el backend
  permanece como punto de enforcement y rechaza payloads inválidos con el
  envelope estándar (BR-008, §8).

---

## 16. Related Documents

- `ARCHITECTURE.md` §1 (diagrama C4 — contenedor SPA), §4 (modelo de
  dominio: `Cuenta`, `Movimiento`), §6 (transferencias), §7 (API REST y
  envelope de errores), §8 (seguridad y propiedad)
- `docs/specs/SPEC-003-autenticacion.md` (claims `role`/`clienteId`,
  códigos 401/403)
- `docs/specs/SPEC-001-clientes.md` (DTOs y reglas del formulario de cliente)
- `docs/specs/SPEC-002-cuentas.md` (DTOs, contrato del listado, reglas de
  apertura)
- `docs/specs/SPEC-004-transferencias.md` (payload, confirmación, historial,
  errores 422/409)
- `docs/specs/SPEC-005-depositos-retiros.md` (contrato de caja; no consumido
  en este sprint)
- `docs/adr/ADR-003-jwt-spring-security.md`,
  `docs/adr/ADR-004-autenticacion-minima-jwt-sprint1.md`,
  `docs/adr/ADR-005-emision-tokens-y-password-bcrypt-spec-003.md`
- `docs/sprints/backlog.md` (E6: US-6.1..US-6.4),
  `docs/sprints/roadmap.md` (Sprint 4)
