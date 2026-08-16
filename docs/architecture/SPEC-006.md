# Architecture — SPEC-006 (Frontend React SPA)

## 1. Feature

SPA del sistema bancario con **React 18 + TypeScript + Vite** bajo
`frontend/` (Epic E6 — US-6.1..US-6.4): login con redirección por rol
(FR-005), listado de cuentas y saldo del `CLIENTE` con historial de
movimientos (FR-009/FR-010), formulario de transferencia del `CLIENTE`
(FR-011) y gestión de clientes/cuentas del `ADMIN` (FR-012/FR-013). La SPA
**consume la API REST existente** `/api/v1` (Sprints 1–3, mergeada en
`testing`) con autenticación `Authorization: Bearer <JWT>` y **no introduce
reglas de negocio nuevas** (A-008): las pre-validaciones de UI (BR-001..BR-007)
espejan reglas ya enforced por el backend, que permanece como fuente de verdad
y punto de enforcement (BR-008, AGENTS.md §10/§11).

El diseño es **frontend-only**: cero cambios en `backend/`, base de datos o
`docker/`. Incluye scaffolding completo (FR-001), cliente HTTP con Bearer
token y parsing del envelope de errores (FR-002), sesión en `localStorage`
(FR-003), decodificación client-side del JWT (FR-004), guard de rutas por rol
(FR-006), manejo de 401 (FR-007), proxy de desarrollo de Vite (FR-008),
estados de UI (FR-015), logout (FR-014) y la suite de tests
(lint/typecheck/unit/component/build — AC-001..AC-031).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-006-frontend-react.md` (APROBADA — fuente de verdad;
  FR-001..FR-015, BR-001..BR-010, AF-001..AF-006, ERR-001..ERR-009,
  AC-001..AC-031, asunciones A-001..A-008).
- `docs/adr/ADR-008-frontend-react-spa.md` (NUEVO — decisiones
  arquitectónicas del frontend: stack, acceso a la API, sesión, rol
  client-side, estado y alcance; ver §11 de este documento).
- `ARCHITECTURE.md` §1 (contenedor SPA del diagrama C4), §7 (API REST y
  envelope `{ code, message, details? }`), §8 (seguridad y propiedad).

---

## 3. Affected Modules

**Solo frontend.** El backend (Sprints 1–3), las migraciones Flyway, la base
de datos y `docker/` **no se tocan** (spec §12: los cambios de backend
requieren documentación y aprobación por separado). Tampoco se modifica
`docs/specs/` ni `docs/architecture/` existentes.

- **`frontend/` (nuevo proyecto, hoy scaffolding vacío con
  `src/{api,components,hooks,pages,store}/.gitkeep`):**
  - Archivos de configuración: `package.json` (scripts `lint`, `typecheck`,
    `test`, `build` — FR-001), `vite.config.ts` (plugin react + proxy `/api`
    → `http://localhost:8080` + config de Vitest — FR-008), `tsconfig.json` y
    `tsconfig.node.json`, `eslint.config.js`, `vitest.setup.ts`,
    `index.html`, `src/vite-env.d.ts`.
  - `src/api/` — cliente HTTP y funciones tipadas por recurso (FR-002).
  - `src/lib/` — **extensión documentada** (ver nota) — utilidades puras:
    decodificación JWT (FR-004), helpers de sesión y formato ARS (FR-003,
    BR-010) y validaciones de formulario (BR-001..BR-007).
  - `src/store/` — contexto de sesión/rol (`AuthProvider` + `useAuth`,
    A-006).
  - `src/hooks/` — hooks de datos (cuentas, movimientos, clientes,
    transferencia).
  - `src/components/` — UI reutilizable (layout, guard, estados de
    carga/error/vacío, campos de formulario, montos).
  - `src/pages/` — vistas por rol: `LoginPage`, `CuentasPage`,
    `TransferenciaPage` (sección dentro de `/cuentas`), `GestionPage` con
    secciones de clientes y cuentas (A-007).
  - Tests co-locados `src/**/*.test.ts(x)` (Vitest + React Testing Library).
  - Los `.gitkeep` de `src/{api,components,hooks,pages,store}` se eliminan al
    agregar archivos reales.

> **Nota sobre `src/lib/` (desviación menor de A-007, sin impacto en AC):**
> la spec (A-007) fija la estructura `src/{api,components,hooks,pages,store}`
> y AC-001 exige que esas 5 carpetas existan. Las utilidades puras
> (decodificación JWT, sesión, validación, formato de montos) no pertenecen a
> ninguna de las cinco: no son HTTP (`api/`), ni UI (`components/`), ni estado
> React (`hooks/`/`store/`), ni vistas (`pages/`). Se agrega un sexto
> directorio `src/lib/` para utilidades puras, conservando las 5 carpetas
> canónicas con sus responsabilidades (AC-001 sigue verificándose; A-007
> permite ajustar nombres). Alternativa descartada: dispersar las utilidades
> en `api/` y `store/` — mezclaría responsabilidades y dificultaría los unit
> tests puros (AC-006/007/031).

---

## 4. Application Flow

### 4.1 Flujo de presentación (cómo llega la SPA al dominio)

```text
Browser (SPA React servida por el dev server de Vite, puerto 5173 por defecto)   presentation (SPA)
    ↓  rutas /login, /cuentas, /gestion + guard por rol (FR-006)
Vite dev server — proxy: /api → http://localhost:8080 (sin rewrite; FR-008)      dev server (same-origin)
    ↓  HTTP/JSON + Authorization: Bearer <JWT> (FR-002)
Backend API /api/v1 (Spring Boot 8080 — controllers REST)                        infrastructure.adapter.web
    ↓  JwtAuthenticationFilter → JwtService (verifica firma y expiración)         infrastructure.security
Application (use cases, validadores, verificación de propiedad)                   application
    ↓
Domain (Cuenta, Movimiento, Money, CBU, puertos)                                  domain
    ↓
PostgreSQL 16                                                                     persistence
```

- Desde el navegador **todo es same-origin** (la SPA se sirve del dev server
  de Vite; el proxy reenvía `/api`): el backend **no requiere CORS** (A-001;
  `SecurityConfig` no lo habilita — verificado). El token JWT viaja solo en
  requests hacia `/api/v1` (BR-009).
- El backend permanece como punto de enforcement en cada endpoint (matchers
  RBAC + verificación de propiedad en la capa de aplicación —
  `ARCHITECTURE.md` §8): el rol y el routing client-side son solo UX
  (BR-008).

### 4.2 Flujo de login y sesión (main flow de la spec §6)

```text
1. Usuario abre la SPA. Sin sesión (FR-003), el guard redirige a /login (FR-006).
2. Ingresa username/password → pre-validación BR-001 (campos obligatorios).
3. POST /api/v1/auth/login {username, password}  →  200 {token}      (FR-005)
        (401 → mensaje genérico en el formulario; permanece en /login — ERR-001/AF-001)
4. guardarToken(token) en localStorage (clave banco.token)            (FR-003)
5. decodificarJwt(token) → { role, clienteId? } (base64url, sin verificación de firma)  (FR-004/A-002)
        (payload no decodificable → sesión inválida, limpiar token — ERR-002)
6. Redirección por rol: CLIENTE → /cuentas; ADMIN → /gestion          (FR-005/A-007)
7. CLIENTE (/cuentas): GET /api/v1/cuentas (sin parámetros; el backend resuelve
   el clienteId del claim) → lista de cuentas propias con saldo (FR-009).
   - Historial: GET /api/v1/cuentas/{id}/movimientos (FR-010).
   - Transferencia: POST /api/v1/transferencias {cuentaOrigenId, cbuDestino,
     monto} → 201 TransferenciaConfirmacion → confirmación + refresh de
     cuentas y movimientos (FR-011/AF-004; 422 → error en el formulario sin
     perder datos — ERR-006/AC-019).
8. ADMIN (/gestion): GET /api/v1/clientes (lista); POST /api/v1/clientes
   (alta, 201); PUT /api/v1/clientes/{id} (edición, 200) (FR-012);
   GET /api/v1/cuentas?clienteId={id} (lista por cliente); POST /api/v1/cuentas
   {clienteId, tipo, moneda?} (apertura, 201, refresh) (FR-013/AF-006).
9. Logout (FR-014): limpiarToken() + redirección a /login.
   Ante 401 en cualquier request autenticada: limpiarToken() + redirección a
   /login (FR-007/AF-002 — "logout automático" por expiración del JWT).
```

---

## 5. Components

### 5.1 Entry points / presentación — rutas y páginas

| Ruta | Acceso | Componente | FR/BR/ERR |
| --- | --- | --- | --- |
| `/login` | público (sin sesión) | `LoginPage` | FR-005, BR-001, ERR-001 |
| `/cuentas` | `CLIENTE` | `CuentasPage` (+ sección `TransferenciaPage`) | FR-009/010/011, BR-002..005, ERR-003..009, AF-003/004 |
| `/gestion` | `ADMIN` | `GestionPage` (secciones `ClientesSection` y `CuentasSection`) | FR-012/013, BR-006/007, ERR-003..009 |
| `*` (default) | — | `<Navigate>` al home del rol (o `/login` sin sesión) | FR-006 |

- **`src/App.tsx`** — declara el router (`BrowserRouter` + `Routes`): `/login`
  sin guard; `/cuentas` y `/gestion` envueltas en `ProtectedRoute`
  (FR-006). Con sesión y visitando `/login` → `Navigate` a la ruta del rol
  (AC-013).
- **`src/main.tsx`** — `ReactDOM.createRoot(...)` montando `App` envuelta en
  `AuthProvider` (sesión restaurada desde `localStorage` al cargar — FR-003).
- **`src/pages/LoginPage.tsx`** — formulario `username`/`password` con
  pre-validación BR-001 (obligatorios tras trim), estado de envío (bloquea
  doble submit — AC-030), error genérico ante 401 (ERR-001, AF-001, sin
  almacenar token) y redirección por rol tras éxito (FR-005, AC-008).
- **`src/pages/CuentasPage.tsx`** — vista `/cuentas` del `CLIENTE`: llama
  `GET /api/v1/cuentas` **sin parámetros** (FR-009; el backend resuelve el
  `clienteId` del claim — SPEC-002 FR-006/AF-001); renderiza cada `CuentaDto`
  (cbu, tipo, saldo formateado ARS, moneda, estado `ACTIVA`/`BLOQUEADA`);
  estados carga/error/ vacío (FR-015, ERR-008/009, AF-003); expansión por
  cuenta → movimientos (FR-010, AC-016); monta la sección de transferencia
  con las cuentas `ACTIVA` como orígenes (BR-002; deshabilitada sin cuentas —
  AF-003); tras una transferencia exitosa refresca cuentas y movimientos
  (FR-011, AF-004, AC-018).
- **`src/pages/TransferenciaPage.tsx`** — **no es una ruta** (A-007 define
  solo 3 rutas): es una sección montada dentro de `/cuentas`. Formulario:
  cuenta origen (solo propias `ACTIVA` — BR-002), `cbuDestino` (BR-003),
  `monto` (BR-004) y anti-auto-transferencia (BR-005); pre-validaciones que
  bloquean el envío con errores por campo (AC-020); submit → `POST
  /api/v1/transferencias` con payload exacto `{cuentaOrigenId, cbuDestino,
  monto}` (AC-017); `201` → panel de confirmación (`idTransferencia`, `monto`,
  `cbuDestino`, `fechaHora`) + callback `onTransferenciaExitosa` (AC-018);
  `422` → mensaje del envelope sin perder datos (ERR-006, AC-019); `400` con
  `details` → errores por campo (ERR-004).
- **`src/pages/GestionPage.tsx`** — vista `/gestion` del `ADMIN` con dos
  secciones:
  - **`src/pages/gestion/ClientesSection.tsx`** — lista (`GET
    /api/v1/clientes`, FR-012/AC-022), alta (`POST /api/v1/clientes` con
    `{nombre, apellido, dni, email, telefono}` → 201 `ClienteDto` agregado a
    la lista — AC-023) y edición (`PUT /api/v1/clientes/{id}` → 200, forma
    pre-cargada — AC-024); validación BR-006; `400` por campo (AC-027);
    `409 CONFLICTO_UNICIDAD` (ERR-007).
  - **`src/pages/gestion/CuentasSection.tsx`** — selector de cliente → `GET
    /api/v1/cuentas?clienteId={id}` (FR-013/AC-026) y apertura (`POST
    /api/v1/cuentas` con `{clienteId, tipo, moneda?}` default `ARS` → 201,
    refresh de la lista — AC-025, AF-006); validación BR-007; estados
    vacíos (ERR-009).

### 5.2 Capa API — `src/api/` (FR-002)

**`src/api/httpClient.ts`** — wrapper de `fetch` (sin axios; ver §10):

- Base relativa `/api/v1` (same-origin vía proxy de Vite en dev — FR-008).
- `request<T>(path, { method, body, autenticar = true })`: adjunta
  `Authorization: Bearer <token>` solo cuando `autenticar === true` y existe
  sesión (leído con `leerToken()` de `src/lib/session.ts`); `Content-Type:
  application/json` cuando hay body; nunca adjunta el token a URLs fuera de
  `/api/v1` (BR-009) y nunca lo loguea (AC-031).
- Respuesta no-OK: parsea el envelope `{ code, message, details? }`
  (ARCHITECTURE.md §7) y lanza `ApiError(status, code, message, details)`
  tipado.
- **Manejo de 401 (distingue login de sesión expirada):**
  - `autenticar: true` (toda request con token) + `401` → invoca el callback
    `onNoAutorizado` (registrado por `AuthProvider`): limpia la sesión y el
    guard redirige a `/login` (FR-007, AF-002, AC-011).
  - `autenticar: false` (solo login) + `401` → `ApiError` normal que
    `LoginPage` muestra como mensaje genérico sin limpiar sesión ni redirigir
    (ERR-001, AC-009). **Sin esta distinción, un login fallido limpiaría la
    sesión y rompería AF-001.**
- Error de red (fetch rechazado/`TypeError`) → `ApiError` con
  `code === 'ERROR_RED'` para que las vistas muestren el mensaje de conexión
  con reintento (ERR-008, AC-029). Respuesta 5xx → se muestra el envelope
  recibido o un mensaje de error interno (ERR fallback).
- Tipos compartidos en **`src/api/types.ts`** — espejo de los DTOs del
  backend (verificado contra `infrastructure/adapter/web`):

| Tipo TS | Espejo backend | Campos |
| --- | --- | --- |
| `LoginRequest` | `LoginRequest` | `{ username, password }` |
| `LoginResponse` | `LoginResponse` | `{ token }` |
| `ClienteDto` | `ClienteDto` | `{ id, nombre, apellido, dni, email, telefono, fechaAlta }` |
| `CrearClienteRequest` / `ActualizarClienteRequest` | `CrearClienteRequest` / `ActualizarClienteRequest` | `{ nombre, apellido, dni, email, telefono }` |
| `CuentaDto` | `CuentaDto` | `{ id, clienteId, cbu, tipo: 'CAJA_AHORRO'\|'CUENTA_CORRIENTE', saldo: number, moneda, estado: 'ACTIVA'\|'BLOQUEADA', createdAt }` |
| `AbrirCuentaRequest` | `AbrirCuentaRequest` | `{ clienteId, tipo, moneda? }` |
| `MovimientoDto` | `MovimientoDto` | `{ id, cuentaId, tipo, monto, moneda, fecha, cuentaContraparteId }` |
| `TransferirRequest` | `TransferirRequest` | `{ cuentaOrigenId, cbuDestino, monto: number }` |
| `TransferenciaConfirmacion` | `TransferenciaConfirmacion` (también body del 201) | `{ idTransferencia, monto, cbuDestino, fechaHora }` |
| `DetalleError` | `DetalleError` | `{ campo, mensaje }` |
| `ErrorEnvelope` | `ErrorResponse` | `{ code, message, details?: DetalleError[] }` |
| `JwtClaims` | claims de `JwtService` | `{ role: 'CLIENTE'\|'ADMIN', clienteId?: number }` |

> Nota de tipos: `Instant` del backend se serializa como string ISO-8601
> (Jackson) → `string` en TS, formateada con `Date`/`Intl` para display. El
> `BigDecimal` se serializa como número JSON → `number` en TS (solo display
> formateado con `Intl`; el frontend **no calcula** montos — BR-010).

**Módulos por recurso (funciones tipadas, una por endpoint consumido):**

- **`src/api/auth.ts`** — `login(body: LoginRequest): Promise<string>` → `POST
  /api/v1/auth/login` (devuelve el `token` de `LoginResponse`). Es el único
  call-site con `autenticar: false`.
- **`src/api/clientes.ts`** — `listarClientes()`, `crearCliente(body)`,
  `actualizarCliente(id, body)` → endpoints de SPEC-001 (FR-012). No se
  consume `GET /api/v1/clientes/{id}` (la SPA no lo requiere).
- **`src/api/cuentas.ts`** — `listarCuentas(clienteId?: number)` (sin
  parámetro para `CLIENTE`; con `?clienteId=` para `ADMIN` — FR-009/FR-013),
  `abrirCuenta(body)` (FR-013) y `obtenerMovimientos(cuentaId)` → `GET
  /api/v1/cuentas/{id}/movimientos` (FR-010; vive aquí por pertenecer al
  dominio cuentas). No se consumen `GET /api/v1/cuentas/{id}` ni
  `/cbu/{cbu}`.
- **`src/api/transferencias.ts`** — `transferir(body: TransferirRequest):
  Promise<TransferenciaConfirmacion>` → `POST /api/v1/transferencias` (FR-011).

### 5.3 Utilidades puras — `src/lib/` y estado — `src/store/` + `src/hooks/`

- **`src/lib/jwt.ts`** — `decodificarJwt(token: string): JwtClaims | null`:
  decodifica el payload del JWT (segmento base64url central, sin verificación
  de firma — A-002), valida la forma (`role ∈ {CLIENTE, ADMIN}`; `clienteId`
  solo para `CLIENTE`) y devuelve `null` ante cualquier payload malformado o
  claims inválidos → sesión inválida (FR-004, ERR-002, AC-007).
- **`src/lib/session.ts`** — helpers de sesión sobre `localStorage` con clave
  `banco.token` (A-003): `guardarToken(token)`, `leerToken(): string | null`,
  `limpiarToken()` (FR-003/FR-014) y `formatearMontoARS(monto: number):
  string` — `Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS',
  minimumFractionDigits: 2, maximumFractionDigits: 2 })` (BR-010).
- **`src/lib/validacion.ts`** — funciones puras de pre-validación (UX, mirror
  de reglas del backend — A-008): `validarLogin` (BR-001), `validarTransferencia`
  (BR-002..BR-005, con el regex `^[0-9]{22}$` del CBU y el chequeo de monto
  `> 0` con ≤ 2 decimales), `validarCliente` (BR-006), `validarAperturaCuenta`
  (BR-007). Devuelven `ErroresPorCampo` (`Record<campo, mensaje>`). Centraliza
  los formatos compartidos (CBU/monto) y habilita unit tests puros (AC-020
  se cubre a nivel componente con estas funciones).
- **`src/store/auth-context.tsx`** — `AuthProvider` + hook `useAuth()` (A-006:
  el contexto de sesión/rol vive en `store/`). Estado: `{ token, rol,
  clienteId }` restaurado al montar desde `leerToken()` + `decodificarJwt()`
  (FR-003); API: `login(username, password)` (llama a `api/auth.login`,
  `guardarToken`, decodifica y setea el estado; devuelve el `rol` para la
  redirección — FR-005), `logout()` (`limpiarToken` + estado vacío — FR-014),
  `sesionExpirada()` (ídem, invocada por el callback `onNoAutorizado` del
  `httpClient` — FR-007) y `cargando` (restauración inicial).
- **`src/hooks/useCuentas.ts`**, **`src/hooks/useMovimientos.ts`**,
  **`src/hooks/useClientes.ts`**, **`src/hooks/useTransferencia.ts`** — hooks
  de datos que envuelven la capa API con estado `{ datos, cargando, error }`,
  `recargar()` y manejo del doble submit (AC-029/AC-030). Sin caché ni estado
  compartido complejo (A-006): cada página usa su hook.

### 5.4 Componentes UI reutilizables — `src/components/`

| Componente | Propósito | FR/ERR |
| --- | --- | --- |
| `Cargando.tsx` | indicador de carga durante requests en vuelo | FR-015, AC-030 |
| `EstadoError.tsx` | mensaje del envelope (o error de red) con botón `Reintentar` | ERR-003/005/006/007/008, AC-028/029 |
| `EstadoVacio.tsx` | listas vacías con mensaje descriptivo | ERR-009, AF-003 |
| `CampoFormulario.tsx` | label + input + mensaje de error por campo (usa `DetalleError.campo`) | ERR-004/006, AC-019/020/027 |
| `Monto.tsx` | display de montos con `formatearMontoARS` | BR-010, AC-014 |
| `Layout.tsx` | shell autenticado: header, navegación por rol (CLIENTE → "Mis cuentas"; ADMIN → "Gestión") y botón de **logout** (FR-014, AC-012) | FR-014/015, AC-021 |
| `ProtectedRoute.tsx` | guard de rutas (FR-006, AC-013) | ver §5.5 |

### 5.5 Authorization

**Matriz del guard de rutas (UX; el backend es el punto de enforcement — BR-008):**

| Estado de sesión | `/login` | `/cuentas` | `/gestion` |
| --- | --- | --- | --- |
| Sin sesión | formulario de login | → `/login` | → `/login` |
| `CLIENTE` (claim `role=CLIENTE`, `clienteId`) | → `/cuentas` | vista de cuentas | → `/cuentas` |
| `ADMIN` (claim `role=ADMIN`, sin `clienteId`) | → `/gestion` | → `/gestion` | vista de gestión |

- `ProtectedRoute` implementa la matriz con `Navigate` (React Router): sin
  `rol` → `/login`; rol con ruta ajena → home del rol (un `CLIENTE` que
  navegue a `/gestion` vuelve a `/cuentas` y viceversa); con sesión y
  visitando `/login` → ruta del rol (FR-006, AC-013).
- **Por qué el backend sigue siendo el punto de enforcement:** el rol
  decodificado client-side es confiable solo como UX (A-002): un token
  falsificado o expirado puede engañar al guard, pero **nunca** a la API —
  `JwtService.validar` verifica firma/expiración y los matchers RBAC + la
  verificación de propiedad en la capa de aplicación rechazan toda request no
  autorizada (401/403 — ERR-002/003). El frontend además oculta acciones por
  rol (FR-015, AC-021) y muestra el `403` del envelope si ocurre por una vía
  no oculta (AF-005).
- **Endpoints consumidos por la SPA (todos ya protegidos por
  `SecurityConfig` — verificado):**

| Endpoint | Rol | Uso en la SPA |
| --- | --- | --- |
| `POST /api/v1/auth/login` | público | `LoginPage` (único call-site `autenticar: false`) |
| `GET /api/v1/cuentas` (sin params) | `CLIENTE` (propias) | `CuentasPage` (FR-009) |
| `GET /api/v1/cuentas/{id}/movimientos` | `CLIENTE` (propias) | `CuentasPage` (FR-010) |
| `POST /api/v1/transferencias` | `CLIENTE` | `TransferenciaPage` (FR-011) |
| `GET /api/v1/clientes` | `ADMIN` | `ClientesSection` (FR-012) |
| `POST /api/v1/clientes` | `ADMIN` | `ClientesSection` (FR-012) |
| `PUT /api/v1/clientes/{id}` | `ADMIN` | `ClientesSection` (FR-012) |
| `GET /api/v1/cuentas?clienteId=` | `ADMIN` | `CuentasSection` (FR-013) |
| `POST /api/v1/cuentas` | `ADMIN` | `CuentasSection` (FR-013) |

- **No consumidos en este sprint:** `POST /api/v1/auth/register` (A-004),
  `POST /api/v1/depositos` y `POST /api/v1/retiros` (A-005, opcionales).

### 5.6 Async work

**Ninguno.** Sin jobs, colas, eventos ni WebSockets: el único trabajo asíncrono
es `fetch` (requests HTTP con `Promise`), gobernado por los hooks de datos
con estados `cargando`/`error`/`datos` (FR-015). No hay worker, service
worker, ni suscripciones. El "logout automático" por 401 (FR-007) es reactivo
(síncrono) al resultado de una request, no un timer.

### 5.7 Dependencias (justificadas; AGENTS.md §13)

**Runtime (`dependencies`):**

| Paquete | Justificación |
| --- | --- |
| `react` ^18 | UI — mandato de la spec (FR-001) y `ARCHITECTURE.md` §1. |
| `react-dom` ^18 | renderizado DOM de React 18. |
| `react-router-dom` ^6 | routing SPA + guard por rol (FR-006). Se fija la versión exacta (v6; ver riesgo §9) — API `BrowserRouter`/`Routes`/`Route`/`Navigate`. |

**Dev (`devDependencies`):**

| Paquete | Justificación |
| --- | --- |
| `typescript` ^5 | typecheck (FR-001, AC-003). |
| `vite` ^5 | dev server con proxy `/api` (FR-008), build de producción (AC-004) y runner de Vitest. Requiere Node 18+/20+. |
| `@vitejs/plugin-react` | transform JSX + fast refresh (plugin estándar del template Vite react-ts). |
| `@types/react`, `@types/react-dom` | tipos TS de React (sin ellos `tsc --noEmit` falla). |
| `eslint` ^9 | lint (FR-001, AC-002) en flat config. |
| `typescript-eslint` | parser + reglas TS para ESLint flat config. |
| `eslint-plugin-react-hooks` | reglas de los hooks de React 18 (Rules of Hooks). |
| `eslint-plugin-react-refresh` | reglas de fast refresh de Vite (evita exportar no-componentes). |
| `globals` | definiciones de globales (browser/node) para `languageOptions` de ESLint. |
| `vitest` ^2 | test runner (FR-001, AC-005). |
| `jsdom` | entorno DOM de los component tests (RTL). |
| `@testing-library/react` | render + queries de componentes (AC-008..AC-030). |
| `@testing-library/dom` | peer dependency de RTL (matchers de queries). |
| `@testing-library/user-event` | interacciones realistas (tipeo, submit) en formularios. |
| `@testing-library/jest-dom` | matchers DOM (`toBeInTheDocument`, etc.) vía `vitest.setup.ts`. |

**Explícitamente NO incluidos:** `axios` (ver §10), `redux`/`zustand`
(A-006, §10), librería de UI/MUI (§10), `msw` (los tests mockean `fetch`
global — sin dependencia extra), `react-hook-form`/`yup` (validación con
funciones puras de `src/lib/validacion.ts`, sin abstracción adicional), `i18n`
(solo español — spec §12).

### 5.8 Configuración y tooling (FR-001)

- **`package.json`** — scripts: `dev` (`vite`), `build` (`tsc -b && vite
  build` o `vite build` con typecheck previo por separado), `lint` (`eslint
  .`), `typecheck` (`tsc --noEmit`), `test` (`vitest run`), `preview`
  (`vite preview`). Versiones **pinneadas** para evitar drift de majors.
- **`vite.config.ts`** — plugin `react()`; `server.proxy: { '/api': {
  target: 'http://localhost:8080', changeOrigin: true } }` **sin rewrite**
  (FR-008, A-001); bloque `test` de Vitest (environment `jsdom`,
  `setupFiles: ['./vitest.setup.ts']`, `globals: true`).
- **`tsconfig.json`** — `strict: true`, `jsx: 'react-jsx'`,
  `moduleResolution: 'bundler'`, `target: ES2022`, `noUnusedLocals`/
  `noUnusedParameters` (alineado con lint), `types: ['vite/client',
  'vitest/globals']`; **`tsconfig.node.json`** para `vite.config.ts`.
- **`eslint.config.js`** — flat config: `typescript-eslint` (recommended),
  `react-hooks` (recommended), `react-refresh` (solo `src/`), `globals` de
  browser.
- **`vitest.setup.ts`** — importa `@testing-library/jest-dom`; limpieza de
  `localStorage` y de mocks de `fetch` entre tests (helper `mockFetch`
  opcional en `src/test/` o inline).
- **`index.html`** — entry HTML de Vite (mount `#root`, lang `es`).
- **`src/vite-env.d.ts`** — `/// <reference types="vite/client" />`.

---

## 6. Data Changes

**Backend y base de datos: ninguno.** No hay migraciones Flyway, tablas,
columnas ni propiedades de configuración nuevas (spec §10).

**Frontend (nuevo):** proyecto completo bajo `frontend/` (ver §3): archivos de
configuración, `src/{api,lib,store,hooks,components,pages}` y tests. Los
`.gitkeep` existentes se eliminan al agregar archivos reales.

**Sesión (dato efímero del navegador):** JWT en `localStorage` bajo la clave
`banco.token` (A-003, FR-003). No se persiste en el servidor; se elimina con
logout (FR-014) o ante 401 (FR-007). El token nunca se adjunta a orígenes
fuera de `/api/v1` ni se loguea (BR-009, AC-031).

---

## 7. External Integrations

- **Backend REST API `/api/v1`** (única integración): consumida por la SPA
  vía el **proxy de desarrollo de Vite** (`/api` → `http://localhost:8080`,
  sin rewrite — FR-008, A-001): desde el navegador todo es same-origin y el
  backend no requiere CORS (`SecurityConfig` no lo habilita — verificado).
  Autenticación Bearer JWT en cada request (FR-002); errores con el envelope
  estándar `{ code, message, details? }` (ARCHITECTURE.md §7).
- **Producción (fuera de alcance, spec §12):** el bundle de `vite build` es
  estático; su integración con la API dependerá de servir ambos desde el mismo
  origen o de habilitar CORS en el backend (cambio que requeriría aprobación
  por separado — A-001).
- **Sin** proveedores externos, mensajería ni otros servicios.

---

## 8. Testing Strategy

**Comandos (FR-001):** `npm run lint` (AC-002), `npm run typecheck`
(AC-003), `npm test` (AC-005; Vitest + React Testing Library) y `npm run
build` (AC-004). Los component tests corren en jsdom y **mockean `fetch`
global** (`vi.stubGlobal` + fixture de envelopes) — sin MSW, sin backend
real. El `localStorage` se limpia entre tests (`vitest.setup.ts`).

**Mapeo AC → archivos de test:**

| AC | Tipo | Archivo(s) | Caso(s) |
| --- | --- | --- | --- |
| AC-001 | estructural | revisión + estructura de `frontend/` | scaffolding y scripts npm; las 5 carpetas de `src/` |
| AC-002 | comando | — | `npm run lint` sin errores |
| AC-003 | comando | — | `npm run typecheck` sin errores |
| AC-004 | comando | — | `npm run build` produce el bundle |
| AC-005 | comando | — | `npm test` corre la suite y pasa |
| AC-006 | unit | `src/lib/session.test.ts`, `src/store/auth-context.test.tsx` | `guardarToken`/`leerToken`/`limpiarToken` con `banco.token`; logout limpia |
| AC-007 | unit | `src/lib/jwt.test.ts` | extrae `role`/`clienteId` (solo CLIENTE) de un payload real; payload malformado → `null` |
| AC-008 | component | `src/pages/LoginPage.test.tsx` | login exitoso: `POST /api/v1/auth/login` con `{username, password}`, redirige a `/cuentas` (CLIENTE) y a `/gestion` (ADMIN) |
| AC-009 | component | `src/pages/LoginPage.test.tsx` | `401` → mensaje genérico, no almacena token, permanece en `/login` |
| AC-010 | unit | `src/api/httpClient.test.ts` | adjunta `Authorization: Bearer` con sesión; sin token sin sesión; nunca a URLs fuera de `/api/v1` |
| AC-011 | unit | `src/api/httpClient.test.ts`, `src/App.test.tsx` | `401` en request autenticada → `limpiarToken` + redirección a `/login` |
| AC-012 | component | `src/components/Layout.test.tsx` (o `App.test.tsx`) | logout limpia `localStorage` y redirige a `/login` |
| AC-013 | component | `src/App.test.tsx` / `ProtectedRoute.test.tsx` | sin sesión → `/login`; CLIENTE no accede a `/gestion`; ADMIN no accede a `/cuentas`; `/login` con sesión → ruta del rol |
| AC-014 | component | `src/pages/CuentasPage.test.tsx` | `GET /api/v1/cuentas` sin parámetros; render con saldo ARS, cbu, tipo, moneda y estado |
| AC-015 | component | `src/pages/CuentasPage.test.tsx` | lista vacía → estado vacío; formulario de transferencia sin orígenes (deshabilitado) |
| AC-016 | component | `src/pages/CuentasPage.test.tsx` | expansión de cuenta → `GET /api/v1/cuentas/{id}/movimientos`; render de tipo, monto, moneda y fecha |
| AC-017 | component | `src/pages/TransferenciaPage.test.tsx` | submit envía payload exacto `{cuentaOrigenId, cbuDestino, monto}` a `POST /api/v1/transferencias` |
| AC-018 | component | `src/pages/TransferenciaPage.test.tsx` | `201` → confirmación (`idTransferencia`, `monto`, `cbuDestino`, `fechaHora`) y refresh de cuentas/movimientos |
| AC-019 | component | `src/pages/TransferenciaPage.test.tsx` | `422` (p. ej. `SALDO_INSUFICIENTE`) → mensaje del envelope en el formulario; datos conservados |
| AC-020 | component | `src/pages/TransferenciaPage.test.tsx` (+ `src/lib/validacion.test.ts`) | pre-validaciones BR-002..BR-005 bloquean el envío con errores por campo |
| AC-021 | component | `src/pages/CuentasPage.test.tsx`, `src/App.test.tsx` | la UI del CLIENTE no expone alta/edición de clientes, apertura de cuentas ni depósitos |
| AC-022 | component | `src/pages/gestion/ClientesSection.test.tsx` | `GET /api/v1/clientes`; render de nombre, apellido, dni y email |
| AC-023 | component | `src/pages/gestion/ClientesSection.test.tsx` | `POST /api/v1/clientes` con payload exacto; `201` agrega el `ClienteDto` a la lista |
| AC-024 | component | `src/pages/gestion/ClientesSection.test.tsx` | `PUT /api/v1/clientes/{id}` con payload actualizado; muestra la representación `200` |
| AC-025 | component | `src/pages/gestion/CuentasSection.test.tsx` | `POST /api/v1/cuentas` con `{clienteId, tipo, moneda}` (default `ARS`); refresh de la lista |
| AC-026 | component | `src/pages/gestion/CuentasSection.test.tsx` | `GET /api/v1/cuentas?clienteId={id}`; render de `CuentaDto` del cliente |
| AC-027 | component | `ClientesSection/CuentasSection.test.tsx` | `400 DATOS_INVALIDOS` con `details` → errores por campo |
| AC-028 | component | `src/pages/GestionPage.test.tsx` | `403`/`404`/`409`/`422` → mensaje del envelope en la vista |
| AC-029 | component | tests de páginas (Cuentas/Gestion/Transferencia) | error de red (`ERROR_RED`) → mensaje de conexión y operación reintentable |
| AC-030 | component | tests de páginas (Login/Cuentas/Gestion/Transferencia) | estado de carga durante requests; sin doble envío |
| AC-031 | unit | `src/api/httpClient.test.ts` (+ `src/lib/session.test.ts`) | token nunca adjunto a URLs fuera de `/api/v1`; no expuesto en logs |

> Nota de mapeo: AC-031 menciona "la utilidad de sesión"; el *scoping* del
> token se implementa y verifica en `httpClient.ts` (que lee el token de
> `src/lib/session.ts`), cubierto por los tests de `httpClient` — sin cambio
> de comportamiento ni de criterio (BR-009, FR-002).

**Cobertura por capa:** unit tests puros para `lib/` (jwt, session,
validacion) y `api/` (httpClient); component tests (RTL) para páginas,
guard, layout y formularios — render, interacción, payloads exactos y estados
de carga/error/vacío. No hay tests de integración con backend real ni e2e
(fuera de alcance — spec §12).

---

## 9. Risks

- **XSS y `localStorage` (A-003 aceptado):** un XSS puede leer el token de
  `localStorage`. Mitigaciones: el token nunca se adjunta fuera de `/api/v1`
  (BR-009), no se loguea (AC-031), y la expiración corta del JWT (default 60
  min) limita la ventana. Cookies `httpOnly` requerirían cambios de backend
  (fuera de alcance).
- **Decodificación del JWT sin verificación de firma (A-002):** un token
  falsificado solo engaña al routing/visibilidad client-side; la API verifica
  firma/expiración y autoriza por rol y propiedad en cada request (BR-008).
  Riesgo de UX, no de datos — se documenta para evitar "simplificaciones"
  futuras que confíen en el rol client-side.
- **Proxy de Vite solo en desarrollo (FR-008):** producción queda fuera de
  alcance (spec §12). Si en el futuro la SPA y la API se sirven de orígenes
  distintos, se requerirá CORS en el backend (cambio aprobado por separado —
  A-001).
- **Versiones de React Router:** pinneado a v6 (API `Routes`/`Navigate`);
  v7 mantiene la misma API en modo library pero introduce flags futuras.
  Fijar la versión exacta en `package.json` evita drift.
- **Node/npm:** Vite 5 requiere Node 18+/20+; si el entorno tiene otra
  versión, `npm install`/`npm run dev` fallan. Documentar el requisito en el
  README de `frontend/` (o en el PR). Disponibilidad del registry npm
  (entorno offline) bloquea el install.
- **Precisión numérica de montos:** el `BigDecimal` viaja como número JSON;
  JS lo representa como `float64`. Aceptable porque el frontend **solo
  formatea** (BR-010) y no calcula; el backend es la fuente de verdad de los
  valores.
- **`moneda` opcional en `AbrirCuentaRequest`:** con TS estricto el campo es
  `moneda?: string`; el JSON debe omitir la clave cuando no se informa
  (`JSON.stringify` descarta `undefined`) para no enviar `moneda: null` (el
  backend espera string o ausencia).
- **Tests con `fetch` mockeado:** cada test debe resetear el mock y el
  `localStorage` (setup compartido en `vitest.setup.ts`); un mock mal
  limpiado produce tests acoplados (flakes). Sin MSW a propósito (§10).
- **Backend no disponible durante el desarrollo:** el proxy de Vite falla a
  `ECONNREFUSED` → la UI muestra el estado de error de conexión con reintento
  (ERR-008); el backend debe estar corriendo en `:8080` (docker-compose).
- **Puertos:** Vite usa 5173 por defecto y el backend 8080; conflictos de
  puerto rompen el proxy (ajustar `server.port` o `proxy.target`).

---

## 10. Alternatives Considered

- **`axios` vs `fetch`:** `axios` agrega interceptores y tipos, pero el
  wrapper necesario es mínimo (Bearer + envelope + 401): `fetch` nativo
  cubre el 100% del caso con cero dependencias (AGENTS.md §13). **Descartado
  axios.**
- **Redux/Zustand vs React Context + hooks (A-006):** el alcance son 4 flujos
  sin estado compartido complejo; el contexto de sesión (`AuthProvider`) y el
  estado local por página son suficientes. Redux/Zustand agregan
  infraestructura sin beneficio (spec §12). **Descartados.**
- **CORS en el backend vs proxy de Vite (A-001):** habilitar CORS en Spring
  implica un cambio de backend (fuera de alcance — spec §12) y expone la API
  a orígenes arbitrarios; el proxy mantiene same-origin en dev y no toca el
  backend. **Elegido el proxy.**
- **`LoginResponse` con `rol` vs decodificar el JWT client-side (A-002):**
  agregar el rol a la respuesta del login es un cambio de contrato del
  backend (y de `AutenticarUsuarioUseCase`); decodificar el payload (base64url,
  sin firma) es suficiente para UX porque el backend verifica en cada request.
  **Elegida la decodificación.**
- **MUI / librería de componentes vs CSS plano:** el spec declara el sistema
  de diseño fuera de alcance (§12); una librería pesada (MUI) sin diseño
  definido es dependencia no justificada. Componentes UI propios mínimos con
  CSS plano (`src/index.css`). **Elegido CSS plano.**
- **MSW vs mock de `fetch` en tests:** MSW agrega una dependencia y un
  servidor de service workers innecesario para una suite de ~30 criterios;
  `vi.stubGlobal('fetch', ...)` con fixtures de envelopes es suficiente.
  **Elegido el mock directo.**
- **`src/lib/` vs dispersar utilidades:** ver §3 (nota). **Elegido `lib/`**
  para utilidades puras, conservando las 5 carpetas canónicas (A-007).
- **React Router vs routing manual:** el router idiomatic de React evita
  reimplementar historial/guardas y es el estándar de la SPA. **Elegido
  React Router.**

---

## 11. Decision

Implementar SPEC-006 como SPA **React 18 + TypeScript + Vite** bajo
`frontend/` (ADR-008), frontend-only, con:

- **Acceso a la API:** cliente HTTP propio sobre `fetch` (`src/api/httpClient.ts`)
  con base `/api/v1`, `Authorization: Bearer` desde `src/lib/session.ts`,
  parsing del envelope `{ code, message, details? }` en `ApiError` tipado y
  callback `onNoAutorizado` para el 401 en requests autenticadas (FR-002,
  FR-007); **login excluido del flujo de 401** (ERR-001 vs ERR-002). Proxy de
  desarrollo de Vite `/api` → `http://localhost:8080` sin rewrite (FR-008,
  A-001); sin CORS backend.
- **Sesión:** JWT en `localStorage` (`banco.token`) con helpers en
  `src/lib/session.ts` (FR-003/FR-014); rol y `clienteId` decodificados
  client-side (base64url, sin verificación de firma) en `src/lib/jwt.ts` para
  UX (FR-004, A-002); `AuthProvider`/`useAuth` en `src/store/auth-context.tsx`
  como único estado compartido (A-006).
- **Routing y autorización:** React Router v6 con `ProtectedRoute` por rol
  (FR-006, AC-013) y rutas `/login`, `/cuentas` (CLIENTE), `/gestion`
  (ADMIN) — A-007; `TransferenciaPage` como sección de `/cuentas`. El
  backend permanece como punto de enforcement (BR-008).
- **Vistas:** `LoginPage`, `CuentasPage` (+ movimientos y transferencia),
  `GestionPage` (clientes: listar/crear/editar; cuentas: listar por cliente/
  abrir) — FR-005..FR-013. Estados de carga/error/vacío y ocultamiento por
  rol (FR-015). Sin pantallas de registro (A-004) ni depósitos/retiros
  (A-005).
- **Validación UX:** funciones puras en `src/lib/validacion.ts` que espejan
  BR-001..BR-007; el backend rechaza payloads inválidos con el envelope
  (A-008).
- **Herramientas:** lint (ESLint flat), typecheck (`tsc --noEmit`), tests
  (Vitest + RTL + jest-dom, `fetch` mockeado) y build (`vite build`) —
  FR-001, AC-001..AC-031 (§8).
- **Dependencias:** mínimas y justificadas (§5.7); sin axios, Redux/Zustand,
  MUI ni MSW.
- **ADR-008** documenta las decisiones arquitectónicas del frontend (stack,
  proxy, sesión, rol client-side, estado, alcance).

---

## 12. Related Documents

- `docs/specs/SPEC-006-frontend-react.md` (spec aprobada — fuente de verdad)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend — NUEVO)
- `docs/adr/ADR-003-jwt-spring-security.md`, `ADR-004-autenticacion-minima-jwt-sprint1.md`,
  `ADR-005-emision-tokens-y-password-bcrypt-spec-003.md` (contrato de claims y
  autenticación que la SPA consume)
- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (envelope de errores), §8
  (seguridad y propiedad)
- `docs/architecture/SPEC-001.md`..`SPEC-005.md` (contratos REST consumidos)
- `docs/sprints/backlog.md` (E6: US-6.1..US-6.4), `docs/sprints/roadmap.md`
  (Sprint 4)
