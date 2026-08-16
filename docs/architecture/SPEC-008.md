# Architecture — SPEC-008 (UI Registro de Usuarios desde Gestión / ADMIN)

## 1. Feature

Agregar a la SPA (SPEC-006) el **registro de usuarios** que hoy solo existe por
API (`POST /api/v1/auth/register`, SPEC-003, implementado y mergeado): una
sección **"Usuarios"** en la pantalla `/gestion` del `ADMIN` (FR-001) con un
formulario de `username`, `password`, `rol` (`CLIENTE`/`ADMIN`) y, cuando
`rol = CLIENTE`, un selector del `Cliente` a vincular (alimentado por
`GET /api/v1/clientes` vía `useClientes`, FR-002). La SPA pre-valida en la UI
las mismas reglas que el backend (`RegistroValidator` — BR-001..BR-004, mirrors
UX) y muestra feedback de éxito (`UsuarioDto` creado: `username` + `rol`,
FR-004) o de error usando el envelope estándar `{ code, message, details? }`
(FR-004, ERR-001..ERR-005).

La SPA **no introduce reglas de negocio nuevas** (BR-005, A-004): el backend
(SPEC-003) permanece como fuente de verdad y punto de enforcement; el frontend
solo consume su contrato REST y pre-valida en la UI.

El diseño es **frontend-only**: cero cambios en `backend/`, base de datos,
`docker/`, dependencias npm ni CSS (spec §10/§12, A-006 del backend). Deroga
parcialmente el §12 de SPEC-006 (A-004: registro público fuera de alcance)
**solo para el registro desde `/gestion` (ADMIN)**: la sección es de solo alta
y queda montada únicamente en `/gestion`; el auto-registro público de `CLIENTE`
sigue fuera de alcance (spec §12).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-008-usuarios-gestion.md` (APROBADA — fuente de verdad;
  FR-001..FR-005, BR-001..BR-005, AF-001..AF-003, ERR-001..ERR-005,
  AC-001..AC-016, asunciones A-001..A-006).
- `docs/architecture/SPEC-007.md` (patrón de sección de `/gestion` que esta
  spec replica: selector de cliente, formulario con mutación, confirmación,
  manejo del envelope y scoping de `GestionPage.test.tsx`).
- `docs/architecture/SPEC-006.md` (patrones de la SPA que esta spec extiende:
  capa API §5.2, `src/lib/validacion.ts` §5.3, hooks §5.3/§5.4, componentes UI
  §5.4, estados y errores §8).
- `docs/architecture/SPEC-003.md` (contrato HTTP exacto de `POST
  /api/v1/auth/register` — payload `RegistrarUsuarioRequest`, `201
  UsuarioDto`, códigos 400/404/409 y matcher `permitAll` de `SecurityConfig`
  §8.5/§8.6; verificado contra el código real, §8.2).
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend que esta
  spec reutiliza sin reinterpretación).
- `ARCHITECTURE.md` §7 (API REST y envelope `{ code, message, details? }`).

---

## 3. Affected Modules

**Solo frontend.** El backend (SPEC-003 ya mergeado), las migraciones Flyway,
la base de datos, `docker/`, `package.json` (sin dependencias nuevas — AC-016)
y `src/index.css` (sin cambios de diseño — spec §12) **no se tocan**.

**Archivos NUEVOS:**

| Archivo | Propósito |
| --- | --- |
| `frontend/src/hooks/useRegistroUsuario.ts` | Hook de mutación del registro, patrón `useCaja`/`useTransferencia` (`{ enviando, confirmacion, error, ejecutar }` con protección de doble envío — FR-004, §5.4). |
| `frontend/src/pages/gestion/UsuariosSection.tsx` | Sección "Usuarios" (FR-001..FR-004): formulario de registro con selector de cliente condicional al rol (§5.5/§8.5). |
| `frontend/src/pages/gestion/UsuariosSection.test.tsx` | Component tests (Vitest + RTL): AC-001..AC-014 (§10). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `frontend/src/api/auth.ts` | Se agrega `registro(body)` → `POST /api/v1/auth/register` con `autenticar: false` (A-002, §5.2). |
| `frontend/src/api/types.ts` | Se agregan `RegistrarUsuarioRequest` y `UsuarioDto` (espejos de los records del backend — §8.2). Se reutiliza el tipo `Rol` existente. |
| `frontend/src/lib/validacion.ts` | Se agrega `validarRegistroUsuario` + helper privado compartido (BR-001..BR-004, §8.3). |
| `frontend/src/lib/validacion.test.ts` | Unit tests de `validarRegistroUsuario` (AC-008, §10.3). |
| `frontend/src/pages/GestionPage.tsx` | Se monta `<UsuariosSection />` como cuarta sección (FR-001, §5.1). |
| `frontend/src/pages/GestionPage.test.tsx` | Ajuste de queries no acotadas (duplicados de labels/textos al montar la nueva sección — §8.7). **Sin debilitar criterios** (AGENTS.md §12). |

**Verificado — sin cambios:** `App.tsx`, `httpClient.ts`, `session.ts`,
`jwt.ts`, `useClientes.ts`, `useCuentas.ts`, `useTransferencia.ts`, todos los
componentes UI (`CampoFormulario`, `Cargando`, `EstadoError`, `EstadoVacio`,
`Monto`), `index.css`, `package.json` y todo `backend/`. El endpoint de registro
es **público** (`permitAll` — `SecurityConfig`), así que `httpClient.ts` no se
toca: `registro()` declara `autenticar: false` y el `401`/`403` de este endpoint
no existe (§8.6, spec §8).

---

## 4. Application Flow

### 4.1 Flujo del registro de usuario (main flow de la spec §6)

```text
Browser — /gestion (ProtectedRoute rolPermitido="ADMIN" — FR-001, §5.6)     presentation (SPA)
    ↓  GestionPage monta ClientesSection + CuentasSection + CajaSection + UsuariosSection
UsuariosSection (useClientes → selector de cliente; useRegistroUsuario → mutación)   pages/gestion
    ↓  GET /api/v1/clientes (alimenta el selector cuando rol=CLIENTE, FR-002)
Formulario: username + password + rol (CLIENTE/ADMIN) + clienteId (solo CLIENTE, A-003)
    ↓  pre-validación UX: validarRegistroUsuario (BR-001..BR-004)
    ↓  submit → useRegistroUsuario.ejecutar({username, password, rol, clienteId})  [doble envío]
src/api/auth.ts → registro(body) (autenticar: false — A-002)                      api
    ↓  httpClient.request<UsuarioDto> — POST /api/v1/auth/register
       payload exacto {username, password, rol, clienteId}; SIN Authorization (público)  api/httpClient
    ↓  201 → UsuarioDto {id, username, rol}  |  error → ApiError(envelope {code, message, details?})
Vite dev server — proxy /api → http://localhost:8080 (sin rewrite)                dev server
    ↓  HTTP/JSON
Backend API /api/v1 (SPEC-003, mergeado): permitAll → RegistroValidator →
RegistrarUsuarioUseCase → Usuario → PostgreSQL 16
```

- Tras un `201`, `UsuariosSection` muestra el panel de confirmación
  (`role="status"`, patrón de `TransferenciaPage`/`CajaSection` — FR-004) con
  `username` + `rol` del `UsuarioDto` (nunca la password — A-005) y **resetea el
  formulario** para permitir otro registro (FR-004, paso 8 del main flow). No
  hay refresh de datos (la sección no lista usuarios — A-002).
- Los errores (`ApiError`) se mapean en el formulario: `details` con campo
  conocido (`username`/`password`/`rol`/`clienteId`) → errores por campo vía
  `CampoFormulario`; resto del envelope → mensaje general (`role="alert"`,
  clase `error-general`); los datos ingresados se conservan (FR-004,
  ERR-001..ERR-005, §8.6).
- El `401` en requests autenticadas lo maneja globalmente `httpClient`
  (limpieza de sesión + redirección a `/login` — SPEC-006 FR-007): **esta
  operación es pública** (`autenticar: false`), por lo que **no** dispara ese
  manejo (A-002; el endpoint nunca devuelve `401` — spec §8).

### 4.2 Cambio de rol y nuevo registro

1. Con `rol = ADMIN` (default sugerido) → el selector de cliente está **oculto**
   y `clienteId` viaja como `null` en el payload (AF-001, A-003, BR-004).
2. Al elegir `rol = CLIENTE` → aparece el selector "Cliente" (poblado por
   `useClientes`), obligatorio (BR-004). Al volver a `ADMIN` → se oculta y se
   limpia la selección de cliente.
3. Tras un `201`, la confirmación reemplaza al formulario y se resetean
   `username`/`password`/`clienteId` (se conserva el `rol` elegido como
   conveniencia de UX, opcional — ver §8.5). El `ADMIN` puede continuar con
   otro registro.

---

## 5. Components

### 5.1 Entry points / presentación — rutas y página

| Ruta | Acceso | Componente | FR |
| --- | --- | --- | --- |
| `/gestion` | `ADMIN` | `GestionPage` (secciones `ClientesSection`, `CuentasSection`, `CajaSection` y **`UsuariosSection`**) | FR-001 |

- **Sin rutas nuevas** (FR-001): la sección se monta dentro de `/gestion`, ya
  protegida por `ProtectedRoute rolPermitido="ADMIN"` (SPEC-006 FR-006).
  `App.tsx` y `main.tsx` no se tocan.
- **`src/pages/GestionPage.tsx`** — cambio mínimo (cuarta sección):

```tsx
import ClientesSection from './gestion/ClientesSection';
import CuentasSection from './gestion/CuentasSection';
import CajaSection from './gestion/CajaSection';
import UsuariosSection from './gestion/UsuariosSection';

export default function GestionPage() {
  return (
    <section>
      <h1>Gestión</h1>
      <ClientesSection />
      <CuentasSection />
      <CajaSection />
      <UsuariosSection />
    </section>
  );
}
```

- `UsuariosSection` es autocontenida: su propio `useClientes` (cuarto
  `GET /api/v1/clientes` al montar `/gestion` — mismo patrón sin caché de
  SPEC-006 A-006; ver §12) y su propio `useRegistroUsuario`.

### 5.2 Capa API — `src/api/auth.ts` y `src/api/types.ts`

**`src/api/auth.ts`** (MODIFICADO — A-002: se extiende el módulo existente del
recurso `auth`, no se crea `usuario.ts`):

```ts
import { request } from './httpClient';
import type { LoginRequest, LoginResponse, RegistrarUsuarioRequest, UsuarioDto } from './types';

export async function login(body: LoginRequest): Promise<string> { /* sin cambios */ }

/**
 * POST /api/v1/auth/register → 201 UsuarioDto (SPEC-003 FR-001; A-002).
 * Endpoint PÚBLICO (permitAll — SecurityConfig): se declara `autenticar: false`
 * (espejo de login() en el mismo módulo) para no adjuntar un header
 * Authorization innecesario. El endpoint nunca devuelve 401, así que no
 * dispara la limpieza global de sesión del httpClient.
 */
export async function registro(body: RegistrarUsuarioRequest): Promise<UsuarioDto> {
  return request<UsuarioDto>('/auth/register', {
    method: 'POST',
    body,
    autenticar: false,
  });
}
```

- **Nombre `registro`:** fijado por la spec §10 (A-002). Sigue la convención
  verbo-por-recurso de la capa API (`login`, `transferir`, `abrirCuenta`,
  `crearCliente`).
- **`autenticar: false`** (A-002): (1) el endpoint es **público** (`permitAll`),
  no requiere token y enviar un header `Authorization` es innecesario e
  incorrecto (menor privilegio); (2) consistencia dentro de `auth.ts` (ambas
  operaciones de auth son públicas); (3) aunque con el default `true` el
  `permitAll` ignoraría el token y el endpoint nunca devuelve `401`, declarar
  explícitamente que la llamada es pública es más limpio. Este flag **no**
  cambia la autorización server-side (el endpoint es público con o sin token);
  solo evita adjuntar el header (A-002).

**`src/api/types.ts`** (MODIFICADO — agregar al final, tras `RetiroConfirmacion`):

```ts
/** POST /api/v1/auth/register (SPEC-003 FR-001). Espejo de `RegistrarUsuarioRequest(String username, String password, String rol, Long clienteId)`. */
export interface RegistrarUsuarioRequest {
  username: string;
  password: string;
  rol: Rol;            // reutiliza el tipo existente 'CLIENTE' | 'ADMIN'
  clienteId: number | null;
}

/** 201 de POST /api/v1/auth/register. Espejo de `UsuarioDto(Long id, String username, String rol)` — NUNCA password (BR-001). */
export interface UsuarioDto {
  id: number;
  username: string;
  rol: Rol;
}
```

- **Verificado contra el código real** (`RegistrarUsuarioRequest.java`,
  `UsuarioDto.java`): nombres de campos exactos. `rol` es `String` en el backend
  y `Rol` en TS (`'CLIENTE' | 'ADMIN'`, ya existente en `types.ts` — espejo
  exacto de los valores del enum). `clienteId` es `Long` → `number | null` (la
  UI envía `null` para `ADMIN` — A-003). `id` es `Long` → `number`.
- El frontend **nunca** envía ni muestra la password (BR-001 del backend,
  A-005): `UsuarioDto` no la incluye y el payload solo la usa como input
  de tipo `password`.

### 5.3 Utilidades puras — `src/lib/validacion.ts`

Se agrega **una función pública** y helpers privados (espejo de
`RegistroValidator` del backend — SPEC-003 §8.2, clase única con chequeos
secuenciales):

```ts
/** BR-001 — username obligatorio y ≤ 50 (espejo de RegistroValidator/SPEC-003 A-005). */
function validarUsernameRegistro(username: string): string | undefined {
  if (username.trim() === '') return 'El usuario es obligatorio';
  if (username.trim().length > 50) return 'El usuario no puede superar los 50 caracteres';
  return undefined;
}

/** BR-002 — password obligatoria y ≥ 8 caracteres (espejo de SPEC-003 BR-002/ERR-004). */
function validarPasswordRegistro(password: string): string | undefined {
  if (password.length < 8) return 'La contraseña debe tener al menos 8 caracteres';
  return undefined;
}

/** BR-003 — rol obligatorio (espejo de SPEC-003 A-002/ERR-004). */
function validarRolRegistro(rol: Rol | ''): string | undefined {
  if (rol !== 'CLIENTE' && rol !== 'ADMIN') return 'Seleccione un rol';
  return undefined;
}

/** BR-004 — clienteId obligatorio solo cuando rol = CLIENTE (espejo de SPEC-003 ERR-007). */
function validarClienteRegistro(rol: Rol | '', clienteId: number | null): string | undefined {
  if (rol === 'CLIENTE' && clienteId === null) return 'Seleccione un cliente a vincular';
  return undefined;
}

/**
 * BR-001..BR-004 — registro de usuario (FR-003, A-004). Espejo de
 * `RegistroValidator`: username obligatorio/≤50, password ≥8, rol CLIENTE|ADMIN
 * y clienteId requerido si CLIENTE. La UNICIDAD de username NO se pre-valida
 * (no existe un endpoint de chequeo previo): el 409 del backend es el feedback
 * autoritativo (A-004). Devuelve `ErroresPorCampo` (vacío si válido).
 */
export function validarRegistroUsuario(
  username: string,
  password: string,
  rol: Rol | '',
  clienteId: number | null,
): ErroresPorCampo {
  const errores: ErroresPorCampo = {};
  const errUsername = validarUsernameRegistro(username);
  if (errUsername !== undefined) errores.username = errUsername;
  const errPassword = validarPasswordRegistro(password);
  if (errPassword !== undefined) errores.password = errPassword;
  const errRol = validarRolRegistro(rol);
  if (errRol !== undefined) errores.rol = errRol;
  const errCliente = validarClienteRegistro(rol, clienteId);
  if (errCliente !== undefined) errores.clienteId = errCliente;
  return errores;
}
```

- Reutiliza el tipo `Rol` y el patrón `ErroresPorCampo` existentes. Sin cambios
  en las funciones existentes.
- **Boundary de password:** 8 caracteres es **válido** (BR-002 — el backend solo
  exige ≥ 8; sin reglas UX adicionales de complejidad, spec §12).
- La unicidad NO se pre-valida client-side (FR-003, A-004): no existe un
  endpoint de "chequear username"; se valida solo obligatoriedad/longitud en
  cliente y el `409 CONFLICTO_UNICIDAD` del backend da el feedback autoritativo
  de duplicado por campo (AF-002, ERR-002).

### 5.4 Hook de mutación — `src/hooks/useRegistroUsuario.ts`

**Decisión: hook `useRegistroUsuario()`**, una instancia por sección (patrón
`useCaja`/`useTransferencia` — spec §10):

```ts
import { useCallback, useRef, useState } from 'react';
import { registro as registroApi } from '../api/auth';
import type { ApiError } from '../api/httpClient';
import type { RegistrarUsuarioRequest, UsuarioDto } from '../api/types';

/**
 * Hook del registro de usuario (SPEC-008 FR-004): envía el payload
 * `{username, password, rol, clienteId}` y expone
 * `{ enviando, confirmacion, error, ejecutar }`. Protección de doble envío
 * (AC-030 de SPEC-006, FR-004): una request en vuelo bloquea el siguiente
 * submit (ref `enVuelo` + estado `enviando` para deshabilitar el botón — AC-014).
 */
export function useRegistroUsuario() {
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState<UsuarioDto | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const enVuelo = useRef(false);

  const ejecutar = useCallback(
    async (body: RegistrarUsuarioRequest): Promise<UsuarioDto | null> => {
      if (enVuelo.current) {
        return null;
      }
      enVuelo.current = true;
      setEnviando(true);
      setError(null);
      setConfirmacion(null);
      try {
        const resultado = await registroApi(body);
        setConfirmacion(resultado);
        return resultado;
      } catch (e) {
        setError(e as ApiError);
        return null;
      } finally {
        enVuelo.current = false;
        setEnviando(false);
      }
    },
    [],
  );

  return { enviando, confirmacion, error, ejecutar };
}
```

- El hook recibe el **body completo** `RegistrarUsuarioRequest` (el formulario
  construye el payload exacto, incluido `clienteId: null` para `ADMIN` — A-003).
- Devuelve `null` en doble submit y en error (patrón `useTransferencia`): el
  formulario distingue éxito (`!== null` → confirmación + reset) de error (el
  estado `error` del hook se mapea por `useEffect`).
- **Sin parámetros** (a diferencia de `useCaja(tipo)`): la sección tiene **un**
  solo formulario de mutación, no dos — no se necesita parametrización.

### 5.5 Sección Usuarios — `src/pages/gestion/UsuariosSection.tsx`

Estructura de componentes privados dentro del archivo (patrón de
`CajaSection.tsx`):

```
UsuariosSection
├── const { enviando, confirmacion, error, ejecutar } = useRegistroUsuario()
├── useClientes() → { datos: clientes, cargando, error, recargar }  (selector, FR-002)
├── estado local: username (string), password (string), rol (Rol | '' — default 'ADMIN'),
│   clienteId (number | null), erroresCampo (ErroresPorCampo), errorGeneral (string | null)
├── useEffect([error]): mapeo del envelope → errores por campo / general (§8.6)
├── <div className="seccion">
│   ├── <h2>Usuarios</h2>
│   ├── cargandoClientes && clientes === null → <Cargando />   (solo si se necesita el selector)
│   ├── errorClientes !== null → <EstadoError mensaje={errorClientes.message} onReintentar={recargarClientes} />
│   ├── confirmacion !== null → <ConfirmacionUsuario usuario={confirmacion} onNuevo={reset} />
│   │   └── (tras 201: mensaje + botón "Registrar otro usuario" — FR-004, A-005)
│   └── <> (formulario)
│       ├── errorGeneral !== null → <div role="alert" className="error-general">{errorGeneral}</div>
│       └── <form onSubmit={onSubmit} noValidate>
│           ├── <CampoFormulario id="usuario-username" label="Usuario" error={erroresCampo.username}>
│           │   └── <input id="usuario-username" name="username" value={username} ... maxLength={50} />
│           ├── <CampoFormulario id="usuario-password" label="Contraseña" error={erroresCampo.password}>
│           │   └── <input id="usuario-password" name="password" type="password" value={password} ... />
│           ├── <CampoFormulario id="usuario-rol" label="Rol" error={erroresCampo.rol}>
│           │   └── <select id="usuario-rol" value={rol} onChange={...}>  ← "CLIENTE" | "ADMIN"
│           ├── rol === 'CLIENTE' &&   ← A-003: selector solo cuando rol=CLIENTE
│           │   <CampoFormulario id="usuario-cliente" label="Cliente" error={erroresCampo.clienteId}>
│           │       └── <select id="usuario-cliente" value={clienteId ?? ''}>
│           │           ├── <option value="">Seleccione un cliente</option>
│           │           └── (clientes ?? []).map(c => <option value={c.id}>{c.apellido}, {c.nombre} — DNI {c.dni}</option>)
│           └── <button type="submit" disabled={enviando}>
│                   {enviando ? 'Registrando...' : 'Registrar usuario'}
│               </button>
```

**Comportamiento de `onSubmit`:**

```ts
const onSubmit = async (event: FormEvent) => {
  event.preventDefault();
  const erroresValidacion = validarRegistroUsuario(username, password, rol, clienteId);
  setErroresCampo(erroresValidacion);
  if (Object.keys(erroresValidacion).length > 0) {
    return; // BR-001..BR-004: sin request
  }
  setErrorGeneral(null);
  const resultado = await ejecutar({
    username: username.trim(),
    password,
    rol: rol as Rol,              // garantizado por la pre-validación (BR-003)
    clienteId: rol === 'CLIENTE' ? clienteId : null,   // A-003: null para ADMIN
  });
  if (resultado !== null) {
    // 201: la confirmación se muestra (useRegistroUsuario.confirmacion) y se
    // resetea username/password/clienteId para otro registro (FR-004, A-005).
    setUsername('');
    setPassword('');
    setClienteId(null);
    setErroresCampo({});
  }
};
```

- **Label del username:** `"Usuario"` (no "Nombre") para **no** colisionar con
  los campos "Nombre" de `ClientesSection` que usan las queries
  `getAllByLabelText('Nombre')` de `GestionPage.test.tsx` (nota de la spec §11 —
  ver §8.7).
- **IDs únicos en la página:** `usuario-username`, `usuario-password`,
  `usuario-rol`, `usuario-cliente` (sin colisión con `cliente-nombre`,
  `cliente-dni`, `caja-cliente`, etc.).
- **Reset tras éxito:** la confirmación reemplaza al formulario (patrón
  `CajaSection`); el botón "Registrar otro usuario" (`onNuevo`) limpia la
  confirmación (`useRegistroUsuario` expone una función o se remonta la sección
  por `key`) y vuelve al formulario en blanco. Detalle de implementación
  dejado al developer con esta semántica (FR-004: "el formulario se resetea y
  queda listo para otro registro").

### 5.6 Authorization

| Nivel | Regla | Fuente |
| --- | --- | --- |
| Routing (UX) | `UsuariosSection` solo se monta en `/gestion`, protegida por `ProtectedRoute rolPermitido="ADMIN"` (SPEC-006 FR-006): el `CLIENTE` nunca ve el formulario (FR-001, A-006) | `App.tsx` (sin cambios) |
| Endpoint consumido | `POST /api/v1/auth/register` → **`permitAll()`** (público — SPEC-003 FR-001, A-002 del backend) | `SecurityConfig` (sin cambios) |
| Enforcement | El endpoint es **público**: el backend **no** exige token ni rol ADMIN (gap de SPEC-003 A-002, no corregido en esta spec — frontend-only, §12). La restricción a `ADMIN` es **solo UX** (A-006) | backend (sin cambios) |

- Como el endpoint nunca devuelve `401`, **no** se dispara la limpieza global de
  sesión del `httpClient` en esta operación (A-002). Tampoco produce `403`
  (público). Un `403`/`401` defensivo podría darse solo en el **listado de
  clientes** del selector (`GET /api/v1/clientes` → `hasRole("ADMIN")`), y lo
  maneja `useClientes()`/`EstadoError`, no el registro (spec §8).

### 5.7 Async work

**Ninguno.** Sin jobs, colas, eventos ni WebSockets. El único trabajo asíncrono
es `fetch` gobernado por `useClientes()` (datos del selector) y
`useRegistroUsuario` (mutación con doble envío). Sin refresh post-`201` (la
sección no lista usuarios — A-002).

### 5.8 Dependencias

**Ninguna dependencia npm nueva** (AC-016, AGENTS.md §13). Se reutilizan
íntegramente: `httpClient` (fetch nativo), componentes UI propios
(`CampoFormulario`, `Cargando`, `EstadoError`, `EstadoVacio`), CSS plano
existente (clases `.seccion`, `.campo`, `.error-general`, `.confirmacion` —
**sin cambios en `index.css`**) y la suite Vitest + RTL + jest-dom con el helper
`mockFetchRespuestas`.

---

## 6. Data Changes

**Backend y base de datos: ninguno** (spec §10): sin migraciones Flyway,
tablas, columnas ni propiedades de configuración. El endpoint de registro ya
existe y está mergeado (SPEC-003).

**Frontend (solo):** los archivos nuevos/modificados de §3. Sin cambios de
estado global ni de sesión: el estado del registro es local a `UsuariosSection`
(formulario + `useRegistroUsuario`), sin caché ni estado compartido (patrón
A-006 de SPEC-006). La sesión del `ADMIN` no se modifica; el registro crea un
usuario ajeno al sesión actual (no inicia sesión, no guarda token — el backend
no devuelve token en `201`, SPEC-003 §8.3).

---

## 7. External Integrations

- **Backend REST API `/api/v1`** (única integración, ya existente): un endpoint
  nuevo consumido por la SPA — `POST /api/v1/auth/register` — vía el proxy de
  desarrollo de Vite (`/api` → `http://localhost:8080`, sin rewrite; same-origin
  desde el navegador, sin CORS). **Sin** autenticación Bearer (público —
  `autenticar: false`); errores con el envelope estándar `{ code, message,
  details? }` (ARCHITECTURE.md §7).
- `GET /api/v1/clientes` ya consumido por las secciones existentes; se reutiliza
  sin cambios de contrato (vía `useClientes`).
- **Sin** proveedores externos, mensajería ni otros servicios.

---

## 8. Detailed Design

### 8.1 File map completo

**NUEVOS:**

| Archivo | Contenido |
| --- | --- |
| `frontend/src/hooks/useRegistroUsuario.ts` | `useRegistroUsuario()` → `{ enviando, confirmacion: UsuarioDto \| null, error, ejecutar(body) }` (§5.4). |
| `frontend/src/pages/gestion/UsuariosSection.tsx` | `UsuariosSection` (default export) + privados `ConfirmacionUsuario` (§5.5/§8.5). |
| `frontend/src/pages/gestion/UsuariosSection.test.tsx` | Component tests AC-001..AC-014 (§10.2). |

**MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `frontend/src/api/auth.ts` | +`registro(body): Promise<UsuarioDto>` con `autenticar: false` (§5.2). |
| `frontend/src/api/types.ts` | +`RegistrarUsuarioRequest`, `UsuarioDto` (§8.2). |
| `frontend/src/lib/validacion.ts` | +`validarRegistroUsuario` + helpers privados (§8.3). |
| `frontend/src/lib/validacion.test.ts` | +describe de `validarRegistroUsuario` (AC-008, §10.3). |
| `frontend/src/pages/GestionPage.tsx` | +`<UsuariosSection />` (cuarta sección, §5.1). |
| `frontend/src/pages/GestionPage.test.tsx` | Scoping de queries duplicadas (§8.7). |

**Sin cambios (verificado):** `App.tsx`, `httpClient.ts`, `session.ts`,
`jwt.ts`, `useClientes.ts`, `useCuentas.ts`, `useCaja.ts`, `useTransferencia.ts`,
todos los componentes UI, `index.css`, `package.json`, todo `backend/`.

### 8.2 Contrato de la API (verificado contra el código)

| Operación | Método/Ruta | Request TS (espejo) | Response 201 TS (espejo) | Errores |
| --- | --- | --- | --- | --- |
| Registro | `POST /api/v1/auth/register` | `{username: string, password: string, rol: Rol, clienteId: number \| null}` ← `RegistrarUsuarioRequest(String, String, String, Long)` | `{id, username, rol}` ← `UsuarioDto(Long, String, String)` | 400, 404, 409, 500 (+ `ERROR_RED` de red) |

**Envelope por código** (mapeo real de `GlobalExceptionHandler` + `httpClient`):

| HTTP | `code` | `details` | Origen backend | Manejo en la UI |
| --- | --- | --- | --- | --- |
| 400 | `DATOS_INVALIDOS` | `[{campo: "username"\|"password"\|"rol"\|"clienteId", mensaje}]` | `DatosInvalidosException` (`RegistroValidator` — ERR-004/ERR-007) | error por campo (§8.6) — ERR-001 |
| 404 | `CLIENTE_NO_ENCONTRADO` | null | `ClienteNoEncontradoException` (ERR-006) | mensaje general — ERR-003 |
| 409 | `CONFLICTO_UNICIDAD` | `[{campo: "username", mensaje}]` | `UsernameDuplicadoException` (ERR-005) | error por campo `username` — ERR-002 |
| 500 | `ERROR_INTERNO` | null | `Exception` fallback (defensivo) | mensaje general — ERR-004 |
| red | `ERROR_RED` | null | wrapper `httpClient` | `MENSAJE_ERROR_RED` — ERR-005 |

- **No aplican** `401 NO_AUTENTICADO` ni `403 ACCESO_DENEGADO` para el registro
  (endpoint público — `permitAll`, spec §8): no se cubren como casos de esta
  operación.
- El `201` no trae `Location` ni headers especiales (no existe `GET
  /usuarios`); la UI usa solo el body `UsuarioDto` (SPEC-003 §5.1).

### 8.3 Validación — reglas por función

| Función | Reglas (en orden) | Errores de campo |
| --- | --- | --- |
| `validarRegistroUsuario(username, password, rol, clienteId)` | BR-001: `username` vacío tras trim → error; `> 50` → error. BR-002: `password.length < 8` → error. BR-003: `rol` no es `CLIENTE`/`ADMIN` → error. BR-004: si `rol === 'CLIENTE'` y `clienteId === null` → error | `username` ('El usuario es obligatorio' / 'El usuario no puede superar los 50 caracteres'); `password` ('La contraseña debe tener al menos 8 caracteres'); `rol` ('Seleccione un rol'); `clienteId` ('Seleccione un cliente a vincular') |

- Mensajes consistentes con `validarLogin` ('El usuario es obligatorio' /
  'La contraseña es obligatoria' no se usan aquí porque la password del
  registro exige ≥ 8; el username reutiliza el mismo texto para obligatorio).
- **Unicidad NO pre-validada** (FR-003, A-004): el `409` del backend da el
  feedback autoritativo de duplicado por campo `username` (AF-002, ERR-002).
- Boundary: `password` de 8 caracteres → **válido** (BR-002). Sin reglas de
  complejidad adicionales (spec §12).

### 8.4 useRegistroUsuario — contrato

| Miembro | Tipo | Comportamiento |
| --- | --- | --- |
| `enviando` | `boolean` | `true` durante la request en vuelo → deshabilita el botón (AC-014). |
| `confirmacion` | `UsuarioDto \| null` | Seteada solo ante `201` → muestra el panel de confirmación (FR-004, A-005). |
| `error` | `ApiError \| null` | Seteado ante cualquier error (`ApiError` del envelope o `ERROR_RED`) → mapeo del formulario (§8.6). |
| `ejecutar(body)` | `Promise<UsuarioDto \| null>` | Construye el body ya recibido y llama `registro(body)`. `null` en doble submit y en error (patrón `useTransferencia`). |

### 8.5 UsuariosSection — estructura JSX y estados (resumen ejecutivo)

- **Raíz:** `<div className="seccion">` con `<h2>Usuarios</h2>` (patrón de
  `CajaSection`; sin cambio de CSS).
- **Rol default:** se sugiere `rol = 'ADMIN'` por defecto (el selector de
  cliente oculto simplifica el estado inicial); no es un requerimiento de la
  spec y queda a criterio del developer (consistente con FR-002/A-003).
- **Selector de cliente:** `CampoFormulario id="usuario-cliente" label="Cliente"`
  (mismo label que `CuentasSection`/`CajaSection` — por eso los tests de
  `GestionPage` acotan con `within`, §8.7). Opciones `"{apellido}, {nombre} —
  DNI {dni}"`. **Solo se renderiza cuando `rol === 'CLIENTE'`** (A-003).
- **Cambio de rol:** al pasar a `ADMIN` se limpia `clienteId` (→ `null`); al
  pasar a `CLIENTE` el selector se muestra (obligatorio — BR-004).
- **Confirmación tras `201`:** `ConfirmacionUsuario` (`role="status"`, clase
  `.confirmacion` — patrón `TransferenciaPage`) muestra `username` y `rol` del
  `UsuarioDto` (nunca la password — A-005) y un botón para registrar otro
  usuario (FR-004, A-005). El formulario se resetea.
- **Errores:** details con campo conocido (`username`/`password`/`rol`/
  `clienteId`) → error por campo vía `CampoFormulario`; resto → mensaje general
  (`role="alert"`, `.error-general`). Los datos ingresados se conservan
  (FR-004, ERR-001..ERR-005).

### 8.6 Manejo de errores del envelope (FR-004, ERR-001..ERR-005)

Espejo del patrón de `CajaSection` (whitelist de campos conocidos + fallback
general):

```ts
const CAMPOS_REGISTRO = new Set(['username', 'password', 'rol', 'clienteId']);

// dentro de UsuariosSection:
useEffect(() => {
  if (error === null) return;
  if (error.details !== undefined && error.details.length > 0) {
    const porCampo: ErroresPorCampo = {};
    let mensajeGeneral = error.message;
    for (const detalle of error.details) {
      if (CAMPOS_REGISTRO.has(detalle.campo)) {
        porCampo[detalle.campo] = detalle.mensaje;
      } else {
        mensajeGeneral = detalle.mensaje;
      }
    }
    setErroresCampo(porCampo);
    setErrorGeneral(mensajeGeneral);
  } else {
    setErroresCampo({});
    setErrorGeneral(error.message);
  }
}, [error]);
```

- `400 DATOS_INVALIDOS` con `details` (username/password/rol/clienteId) → error
  por campo (ERR-001); los valores de los inputs se conservan (el estado local
  no se limpia — FR-004). `400` sin details (JSON malformado) → mensaje general.
- `409 CONFLICTO_UNICIDAD` con `details[{campo:'username'}]` → error por campo
  `username` con el mensaje del envelope; datos conservados (ERR-002, AF-002).
- `404 CLIENTE_NO_ENCONTRADO` → mensaje general; se sugiere refrescar el
  selector de clientes (ERR-003; el selector se repuebla al recargar).
- `500 ERROR_INTERNO` y `ERROR_RED` → mensaje general con datos conservados y
  reintento posible (ERR-004/005, AF-003).
- Un `details` con campo **desconocido** → se trata como mensaje general
  (fallback robusto, patrón `CajaSection`).

### 8.7 Ajuste de `GestionPage.test.tsx` — scoping de queries (por test)

Al montar `UsuariosSection` en `GestionPage`, las queries no acotadas del test
existente pueden dejar de ser válidas (nota de la spec §11). Ajustes
**concretos** (se acotan las queries, no se debilitan los criterios —
AGENTS.md §12):

**Test 1 — "AC-028 — 403 ACCESO_DENEGADO en el listado":** **sin cambios.**
Ya usa `findAllByText(...)` con `length >= 1`; con `UsuariosSection` montada el
mensaje del envelope aparece en `ClientesSection` y en `UsuariosSection` (ambas
consumen `GET /api/v1/clientes`), y la aserción `>= 1` sigue pasando. Además,
`UsuariosSection` con `rol = 'ADMIN'` (default) **no** muestra el selector de
cliente, así que el mock `GET /api/v1/clientes → 403` solo dispara el
`EstadoError` de `UsuariosSection` si el rol se cambia a `CLIENTE` (no ocurre en
este test).

**Test 2 — "AC-028 — 409 CONFLICTO_UNICIDAD al crear un cliente":**

- `await screen.findByText('Pérez, Juan')` → ya está como `findAllByText` con
  `length >= 1` (verificado en el código actual): **sin cambios**. El texto
  sigue apareciendo en el listado de clientes (y, si el rol fuera `CLIENTE`, en
  el selector de `UsuariosSection`; el default `ADMIN` no agrega ocurrencias).
- `getAllByLabelText('Nombre')[0]`, `getAllByLabelText('Apellido')[0]`,
  `getAllByLabelText('DNI')[0]`, `getAllByLabelText('Email')[0]`,
  `getByRole('button', { name: 'Crear cliente' })` y
  `findByText('Conflicto de unicidad de datos')` → **sin cambios** (labels y
  textos exclusivos de `ClientesSection`; el campo de username de la nueva
  sección se etiqueta "Usuario", **no** "Nombre", para no colisionar — §5.5).

**Test 3 — "AC-028 — 422 en la apertura de cuenta":**

- `await screen.findByText('Pérez, Juan')` → ya `findAllByText` (sin cambios).
- `screen.getByLabelText('Cliente')` → ya acotado al heading "Cuentas" vía
  `within(...closest('.seccion'))` (verificado en el código actual): **sin
  cambios** — el `within` por heading "Cuentas" sigue siendo único, y aunque
  ahora hay un tercer label "Cliente" (en `UsuariosSection`, solo cuando
  `rol = CLIENTE`, que no es el caso aquí por default `ADMIN`), el acotamiento
  existente lo resuelve.
- `getByRole('button', { name: 'Abrir cuenta' })` (único; `UsuariosSection`
  usa "Registrar usuario") y
  `findByText('La moneda solicitada no es soportada')` (solo `CuentasSection`
  hace `POST /api/v1/cuentas`) → **sin cambios**.

**Test 4 — "AC-021 — la vista de gestión no expone acciones de CLIENTE":**

- `await screen.findByText('Pérez, Juan')` → ya `findAllByText` (sin cambios).
- `queryByText('Transferencia')` y `queryByLabelText('CBU destino')` →
  **sin cambios** (siguen siendo exclusivos del formulario de transferencia;
  `UsuariosSection` no agrega esos textos).

**`App.test.tsx`: sin cambios (verificado).** Los tests de `/gestion` usan
`findByText('Clientes')`: el heading `h2 "Clientes"` de `ClientesSection` sigue
siendo la única coincidencia exacta; `UsuariosSection` agrega el heading
`h2 "Usuarios"` (sin colisión) y su campo de username se etiqueta "Usuario"
(no "Clientes"). El mock `GET /api/v1/clientes → []` sirve también a la
request de `UsuariosSection` (helper `mockFetchRespuestas` responde por URL).

> **Nota de impacto (spec §11):** si el developer hace que `UsuariosSection`
> consuma `GET /api/v1/clientes` de forma incondicional (aunque el selector
> solo se muestre con `rol = CLIENTE`), habrá **cuatro** consumidores del
> listado en `/gestion` y, cuando `rol = CLIENTE`, **tres** selectores con label
> "Cliente". Las queries de `GestionPage.test.tsx` ya están acotadas (§8.7),
> pero el developer debe **no debilitar criterios** al ajustar cualquier query
> residual (AGENTS.md §12). Alternativa recomendada: consumir `useClientes()`
> de forma condicional solo cuando `rol === 'CLIENTE'` para evitar el fetch
> innecesario (ver §5.5/§12) — en ese caso el `EstadoError`/`Cargando` del
> selector solo aplica al mostrarse.

### 8.8 Convenciones

- Se reutilizan componentes y clases existentes sin modificarlos:
  `CampoFormulario` (error por campo con `role="alert"`), `Cargando`
  (`role="status"`), `EstadoError` (`role="alert"` + Reintentar), `EstadoVacio`.
- Textos de la UI en español, consistentes con las secciones existentes (spec
  §12: sin i18n).
- El tipo `Rol` se reutiliza (no se redefine): el selector de rol ofrece
  exactamente `'CLIENTE'` y `'ADMIN'` (BR-003).

---

## 9. Build & Dependencies

**Ninguna dependencia npm nueva** (AC-016, AGENTS.md §13). `package.json` y
`vite.config.ts` intactos. Sin cambios de tooling: los tests corren con
`npm test` (Vitest + RTL, `fetch` mockeado con `mockFetchRespuestas`),
`npm run lint`, `npm run typecheck` y `npm run build` deben pasar (AC-015).
Sin cambios en `backend/` ni migraciones (AC-016).

---

## 10. Testing Strategy

**Comandos (FR-005, AC-015):** `npm run lint`, `npm run typecheck`, `npm test`
y `npm run build`. Component tests en jsdom con `fetch` global mockeado
(`vi.stubGlobal` + `mockFetchRespuestas`/`mockFetchErrorRed`/`mockFetchDiferido`
de `src/test/helpers.tsx`); `localStorage` limpio entre tests
(`vitest.setup.ts`).

### 10.1 Mapeo AC → archivos de test

| AC | Tipo | Archivo(s) | Caso(s) |
| --- | --- | --- | --- |
| AC-001 | component | `src/pages/gestion/UsuariosSection.test.tsx` | render de la sección: heading "Usuarios", campos username/password/rol y botón "Registrar usuario"; sin rutas nuevas (se monta en `GestionPage`, cubierto por `GestionPage.test.tsx` con sesión ADMIN) |
| AC-002 | component | `UsuariosSection.test.tsx` | `rol = CLIENTE` → se muestra el selector "Cliente" poblado por `GET /api/v1/clientes`; `rol = ADMIN` → el selector **no** se muestra (FR-002, BR-004, A-003) |
| AC-003 | component | `UsuariosSection.test.tsx` | registro `CLIENTE` exitoso: payload exacto `{username, password, rol:'CLIENTE', clienteId}` a `POST /api/v1/auth/register`; `201` → confirmación con `username` + `rol` (sin password) y reset del formulario (FR-002/004, A-005) |
| AC-004 | component | `UsuariosSection.test.tsx` | registro `ADMIN` exitoso: payload exacto `{username, password, rol:'ADMIN', clienteId:null}`; `201` → confirmación (FR-002/004, AF-001, A-003) |
| AC-005 | component | `UsuariosSection.test.tsx` | pre-validación de `username`: vacío o `> 50` → error por campo y envío bloqueado sin request (BR-001) |
| AC-006 | component | `UsuariosSection.test.tsx` | pre-validación de `password`: `< 8` → error por campo y sin request; con 8 caracteres la request SÍ se envía (boundary, BR-002) |
| AC-007 | component | `UsuariosSection.test.tsx` | `rol = CLIENTE` sin cliente seleccionado → error por campo y envío bloqueado (BR-004) |
| AC-008 | unit | `src/lib/validacion.test.ts` | `validarRegistroUsuario` cubre BR-001..BR-004 (username vacío/>50, password <8, rol vacío/inválido, CLIENTE sin clienteId) |
| AC-009 | component | `UsuariosSection.test.tsx` | `400 DATOS_INVALIDOS` con `details` (p. ej. `password`) → error por campo y datos conservados (ERR-001) |
| AC-010 | component | `UsuariosSection.test.tsx` | `409 CONFLICTO_UNICIDAD` con `details[{campo:'username'}]` → error por campo `username` y datos conservados (ERR-002, AF-002) |
| AC-011 | component | `UsuariosSection.test.tsx` | `404 CLIENTE_NO_ENCONTRADO` → mensaje del envelope general (ERR-003) |
| AC-012 | component | `UsuariosSection.test.tsx` | `500 ERROR_INTERNO` → mensaje del envelope general y reintento posible (ERR-004, AF-003) |
| AC-013 | component | `UsuariosSection.test.tsx` | error de red → `MENSAJE_ERROR_RED` y reintento posible (ERR-005, AF-003) |
| AC-014 | component | `UsuariosSection.test.tsx` | doble envío: botón deshabilitado durante la request y una sola request (FR-004, AC-030 de SPEC-006) |
| AC-015 | comando | — | `npm run lint`, `npm run typecheck`, `npm run build` y `npm test` completos |
| AC-016 | revisión | — | sin dependencias npm nuevas; sin cambios en backend, migraciones ni `docker/` |

### 10.2 `UsuariosSection.test.tsx` — escenarios concretos

Render aislado de la sección (patrón de `CajaSection.test.tsx`, sin sesión:
`render(<UsuariosSection />)`); `useClientes` dispara la request del selector.
Helper de scoping (si el test necesita acotar el formulario):

```tsx
import { render, screen, within } from '@testing-library/react';
// ...
function seccionDe(titulo: string) {
  const heading = screen.getByRole('heading', { name: titulo });
  return within(heading.closest('.seccion') as HTMLElement);
}
```

Fixtures (reutilizar el patrón de `CajaSection.test.tsx`):

```ts
const CLIENTES: ClienteDto[] = [
  { id: 1, nombre: 'Juan', apellido: 'Pérez', dni: '30111222',
    email: 'juan@test.com', fechaAlta: '2026-01-10T10:00:00' },
];
const USUARIO_201 = { id: 5, username: 'jperez', rol: 'CLIENTE' };
```

Casos clave (detalle de la implementación de cada uno):

- **AC-001/AC-002 (render + selector por rol):** mock `GET /api/v1/clientes`
  con `CLIENTES`. Render: `getByRole('heading', { name: 'Usuarios' })`,
  `getByLabelText('Usuario')`, `getByLabelText('Contraseña')`,
  `getByLabelText('Rol')`, `getByRole('button', { name: 'Registrar usuario' })`.
  Con `rol = ADMIN` (default): `queryByLabelText('Cliente')` es `null`. Elegir
  `rol = CLIENTE` (`selectOptions(getByLabelText('Rol'), 'CLIENTE')`): el
  selector aparece y espera las opciones
  (`findByRole('option', { name: /Pérez, Juan/ })`). Volver a `ADMIN`: el
  selector se oculta (A-003).
- **AC-003 (CLIENTE exitoso):** seleccionar `rol = CLIENTE`, elegir cliente en
  el selector (`selectOptions`, valor `'1'`), escribir username/password
  (≥ 8) y submit. Verificar llamada
  `'/api/v1/auth/register'` con `method: 'POST'`, header `Authorization`
  **ausente** (autenticar: false) y payload exacto
  `JSON.parse(body) toEqual({ username: 'jperez', password: '12345678',
  rol: 'CLIENTE', clienteId: 1 })`; mock responde `201` con `USUARIO_201` →
  panel de confirmación `findByText(/Usuario creado/)` o similar con
  `username` + `rol` (p. ej. `findByText('jperez')` y `findByText('CLIENTE')`),
  y el formulario se resetea (inputs en blanco).
- **AC-004 (ADMIN exitoso):** con `rol = ADMIN` (sin elegir cliente): payload
  `{ username, password, rol: 'ADMIN', clienteId: null }`; `201` →
  confirmación con `username` + `rol`. **No** se envía `clienteId` distinto de
  `null`.
- **AC-005/006 (pre-validaciones):** `validarRegistroUsuario` bloquea el envío:
  submit con username vacío → `getByRole('alert')` (dentro del campo) con 'El
  usuario es obligatorio' y **sin** llamada a `/api/v1/auth/register`; username
  de 51 caracteres → 'El usuario no puede superar los 50 caracteres'; password
  de 7 caracteres → 'La contraseña debe tener al menos 8 caracteres' y sin
  request; password de 8 caracteres + resto válido → la request SÍ se envía
  (boundary BR-002).
- **AC-007 (CLIENTE sin cliente):** con `rol = CLIENTE` y sin elegir cliente,
  submit → alert del campo `clienteId` ('Seleccione un cliente a vincular') y
  sin request.
- **AC-009 (400):** mock `POST /api/v1/auth/register` → `400` con
  `{ code: 'DATOS_INVALIDOS', message: 'Datos inválidos',
  details: [{ campo: 'password', mensaje: 'La password debe tener al menos 8 caracteres' }] }`
  → error por campo en `password` (mensaje del envelope, no la pre-validación);
  los datos ingresados se conservan (`getByLabelText('Usuario')` mantiene su
  valor tras el submit).
- **AC-010 (409):** mock `POST` → `409` con
  `{ code: 'CONFLICTO_UNICIDAD', message: 'Ya existe un usuario con ese username',
  details: [{ campo: 'username', mensaje: 'Ya existe un usuario con ese username' }] }`
  → error por campo `username`; datos conservados; segundo submit reenvía
  (reintento — AF-002).
- **AC-011 (404):** mock `POST` → `404`
  `{ code: 'CLIENTE_NO_ENCONTRADO', message: 'El cliente no existe' }` →
  `findByText('El cliente no existe')` (mensaje general, `role="alert"`).
- **AC-012 (500):** mock `POST` → `500`
  `{ code: 'ERROR_INTERNO', message: 'Error interno del servidor' }` →
  mensaje general; datos conservados y segundo submit con mock `201` → éxito
  (reintento — AF-003).
- **AC-013 (red):** `mockFetchErrorRed()` → `findByText(/No se pudo conectar/)`;
  restaurar mock `201` y reintentar → éxito.
- **AC-014 (doble envío):** `mockFetchDiferido()` (o mock manual con promesa
  pendiente): submit → botón `disabled` con texto "Registrando..."; segundo
  submit no dispara segunda request (`fetchMock` llamado una sola vez con
  `POST /api/v1/auth/register`); al resolver → confirmación y botón habilitado.

### 10.3 `validacion.test.ts` — casos unitarios (AC-008)

Nuevo `describe` (patrón de los describes existentes):

- `validarRegistroUsuario('jperez', '12345678', 'CLIENTE', 1)` → `{}`.
- `validarRegistroUsuario('', '12345678', 'CLIENTE', 1)` → `username`.
- `validarRegistroUsuario('a'.repeat(51), '12345678', 'CLIENTE', 1)` → `username`.
- `validarRegistroUsuario('jperez', '1234567', 'CLIENTE', 1)` → `password` (7 chars).
- `validarRegistroUsuario('jperez', '12345678', '', 1)` → `rol`.
- `validarRegistroUsuario('jperez', '12345678', 'CLIENTE', null)` → `clienteId`.
- `validarRegistroUsuario('jperez', '12345678', 'ADMIN', null)` → `{}`
  (ADMIN no requiere cliente — BR-004, A-003).
- `validarRegistroUsuario('jperez', '12345678', 'ADMIN', 1)` → `{}`
  (ADMIN con cliente informado no falla en la pre-validación; el payload
  viaja con `null` — la validación solo exige cliente para CLIENTE).
- Boundary: `validarRegistroUsuario('jperez', '12345678', 'CLIENTE', 1)` →
  `{}` (8 chars es válido, BR-002).

### 10.4 `GestionPage.test.tsx` — ajustes

Los cambios concretos por test están en §8.7. Verificado: los cuatro tests
actuales ya usan `findAllByText`/`within` y **no requieren cambios** salvo
revisar que ningún label/texto nuevo colisione (el campo de username se
etiqueta "Usuario", no "Nombre"; heading "Usuarios" es único). Si algún test
residual rompe por el nuevo `GET /api/v1/clientes` (consumo del selector), se
acota con `within` sin debilitar criterios (AGENTS.md §12).

### 10.5 Cobertura por capa

Unit puro para `validacion.ts` (AC-008) + component tests para
`UsuariosSection` (render, interacción, payloads exactos, confirmación, errores
del envelope, doble envío — AC-001..AC-014) + ajuste/revisión de
`GestionPage.test.tsx`. Sin tests de integración con backend real ni e2e (fuera
de alcance — spec §12).

---

## 11. ADR

**No se crean ADRs nuevos.** SPEC-008 es una extensión **frontend-only** y de
bajo riesgo que reutiliza sin reinterpretación las decisiones ya registradas:

- **ADR-008** cubre todo el frontend (stack React/Vite, proxy sin CORS, sesión
  `localStorage`, rol client-side como UX, estado local por página, alcance).
  La sección "Usuarios" del `ADMIN` extiende el alcance de SPEC-006 (derogación
  parcial de A-004) sin cambiar ninguna de esas decisiones.
- **SPEC-006 §5.2/§5.3/§5.4** documenta los patrones que esta spec replica:
  módulos API por recurso, validación pura en `src/lib/validacion.ts`, hook de
  mutación con doble envío, manejo del envelope y scoping de tests.
- **SPEC-003 §11** ya documentó las decisiones del endpoint de registro
  (ADRD-005 + matchers `permitAll`); consumirlo desde la SPA no introduce una
  decisión arquitectónica nueva.
- **La decisión del módulo API (`auth.ts` extendido vs nuevo `usuario.ts`) está
  resuelta por el analista en A-002 de la spec** (un-módulo-por-recurso; no
  existe un recurso `usuarios` de lectura). Es una decisión de **diseño de
  detalle**, no arquitectónica.

Las decisiones propias de SPEC-008 (`registro()` en `auth.ts` con
`autenticar: false`; `validarRegistroUsuario` con helpers privados; hook
`useRegistroUsuario()`; estructura de `UsuariosSection`; scoping de
`GestionPage.test.tsx`) son de **diseño de detalle** — alternativas evaluadas
en §13 — y no constituyen decisiones arquitectónicas significativas que
requieran ADR (AGENTS.md §16).

---

## 12. Risks

- **Queries duplicadas en tests de `/gestion`:** al montar `UsuariosSection`,
  el label "Cliente" (cuando `rol = CLIENTE`), el heading "Usuarios" y el
  consumo de `GET /api/v1/clientes` se suman a la página. Mitigación prescrita
  en §8.7: las queries de `GestionPage.test.tsx` ya están acotadas
  (`findAllByText`, `within` por heading); el campo de username se etiqueta
  "Usuario" para no colisionar con "Nombre" de `ClientesSection`. Riesgo de
  romper tests si el developer "arregla" bajando el criterio (prohibido —
  AGENTS.md §12).
- **Cuarto `GET /api/v1/clientes` en `/gestion`:** `ClientesSection`,
  `CuentasSection`, `CajaSection` y `UsuariosSection` consumen el mismo listado
  con hooks independientes (sin caché — A-006 de SPEC-006). Es una request
  extra trivial por montaje, consistente con el patrón existente; se recomienda
  que `UsuariosSection` consuma `useClientes()` **solo cuando `rol === 'CLIENTE'`**
  (evita el fetch innecesario cuando el selector está oculto — ver §5.5/§8.7).
  No se introduce estado compartido (scope control — AGENTS.md §14).
- **Gap de autorización del backend (público):** el endpoint de registro es
  `permitAll` y el backend no restringe quién crea usuarios `ADMIN` (SPEC-003
  A-002). La sección solo se expone en `/gestion` (UX), pero el enforcement
  real recae en el backend, que no lo aplica. **No se corrige en esta spec**
  (frontend-only, §12); se documenta para que el Product Owner decida si se
  cierra en una spec de backend posterior (A-006).
- **Precisión/`clienteId`:** el `clienteId` del selector viaja como `number`
  (de `option value`); el backend lo recibe como `Long`. `null` para `ADMIN`
  (A-003). Sin riesgo de precisión (IDs Long dentro del rango seguro).
- **`fetch` mockeado:** cada test nuevo debe configurar su mock (y el setup
  global limpia `fetch`/`localStorage` — `vitest.setup.ts`); un mock faltante
  (`GET /api/v1/clientes` si el test cambia el rol a `CLIENTE`) rechaza con
  `TypeError` y la sección muestra `EstadoError` — verificar que los mocks
  cubran el flujo completo (clientes + registro).

---

## 13. Alternatives Considered

- **`useRegistroUsuario()` (elegido) vs estado local en la sección (patrón
  `ClientesSection`):** la spec §10 manda explícitamente "hook(s) de mutación
  siguiendo el patrón de `useCaja`/`useTransferencia` ({ enviando, confirmacion,
  error, ejecutar } con protección de doble envío — FR-004)". `ClientesSection`
  usa estado local porque tiene **una** mutación simple; pero el patrón
  establecido de `useCaja`/`useTransferencia` es más reutilizable y testable
  (ref `enVuelo` + `enviando` + `confirmacion`). **Elegido el hook.**
- **`useRegistroUsuario()` sin parámetros (elegido) vs `useRegistroUsuario(body)`
  parametrizado como `useCaja(tipo)`:** a diferencia de la caja (dos
  formularios), el registro tiene **un** formulario, así que no hay `tipo` que
  parametrizar. El hook recibe el body completo en `ejecutar(body)` (el
  formulario construye el payload exacto, incluido `clienteId: null` para
  `ADMIN`).
- **`validarRegistroUsuario(...)` con 4 argumentos (elegido) vs una función con
  un objeto:** espeja la firma de `validarCliente`/`validarDeposito` (varios
  argumentos posicionales, convención del módulo). El objeto añadiría un tipo
  sin beneficio.
- **Módulo `auth.ts` extendido (elegido, A-002) vs nuevo `usuario.ts`:**
  resuelto por el analista: `registro` es una operación del recurso `auth`
  (`POST /api/v1/auth/register`) y no existe un recurso `usuarios` con
  operaciones de lectura/listado (SPEC-003 responde `201` sin `Location`; no
  hay `GET /usuarios`). Crear `usuario.ts` para un único endpoint sin
  contrapartida de recurso añade estructura sin beneficio (AGENTS.md §11).
- **`registro()` con `autenticar: false` (elegido, A-002) vs default `true`:**
  el endpoint es público (`permitAll`); con `true` el token del `ADMIN` se
  adjuntaría y el `permitAll` lo ignoraría (sin `401` → sin limpieza global),
  pero es más limpio declarar explícitamente la llamada pública y no enviar un
  header innecesario (menor privilegio, consistencia con `login()`).
- **Selector de cliente condicional por `rol` (elegido, A-003) vs siempre
  visible:** la spec pide "si es CLIENTE, selector del cliente a vincular".
  Para `ADMIN` se oculta y `clienteId` viaja `null` (el backend ignora un
  `clienteId` informado para `ADMIN` — SPEC-003 §8.3, pero la UI lo oculta por
  UX).
- **Pre-validación de unicidad client-side (descartado, A-004):** no existe un
  endpoint de "chequear username" sin crear el usuario; la unicidad se valida
  solo obligatoriedad/longitud en cliente y el `409` del backend da el feedback
  autoritativo por campo (AF-002, ERR-002). No se inventa un mecanismo de
  chequeo previo.
- **Envelope: whitelist de campos (`username`, `password`, `rol`, `clienteId`)
  (elegido, patrón `CajaSection`) vs mapear todos los `details` a campos
  (patrón `ClientesSection`):** la whitelist con fallback a mensaje general es
  más robusta ante campos desconocidos sin perder el criterio (FR-004: details
  → por campo; sin details → general).

---

## 14. Decision

Implementar SPEC-008 como sección **"Usuarios"** frontend-only en `/gestion`
(frontend de SPEC-006; sin rutas nuevas, sin cambios de backend/CSS/deps):

- **API:** `src/api/auth.ts` (extendido, A-002) con `registro(body:
  RegistrarUsuarioRequest): Promise<UsuarioDto>` → `POST /api/v1/auth/register`
  con `autenticar: false` (endpoint público — `permitAll`); tipos
  `RegistrarUsuarioRequest`/`UsuarioDto` en `src/api/types.ts` (espejos
  verificados de los records del backend, reutilizando `Rol` — §8.2).
- **Validación UX (mirrors):** `validarRegistroUsuario(username, password, rol,
  clienteId)` en `src/lib/validacion.ts` con helpers privados compartidos
  (`validarUsernameRegistro`, `validarPasswordRegistro`, `validarRolRegistro`,
  `validarClienteRegistro`); espeja `RegistroValidator` (BR-001..BR-004).
  Unicidad NO pre-validada (A-004).
- **Mutación:** `src/hooks/useRegistroUsuario.ts` — `useRegistroUsuario()` →
  `{ enviando, confirmacion: UsuarioDto | null, error, ejecutar(body) }`,
  patrón `useTransferencia`/`useCaja` con protección de doble envío (FR-004,
  AC-014).
- **Sección:** `src/pages/gestion/UsuariosSection.tsx` (formulario username/
  password/rol con selector de cliente **solo cuando `rol = CLIENTE`** vía
  `useClientes` — A-003; confirmación tras `201` con `username` + `rol` y reset
  del formulario — FR-004/A-005; errores del envelope por campo/general con
  datos conservados — ERR-001..ERR-005). Montada en `GestionPage.tsx` como
  cuarta sección (FR-001).
- **Autorización:** la sección solo se monta en `/gestion` (`ProtectedRoute`
  ADMIN — UX); el endpoint es público (`permitAll`) y la restricción a `ADMIN`
  es **solo UX** (gap del backend documentado, no corregido — A-006, §12).
- **Tests:** `UsuariosSection.test.tsx` (AC-001..AC-014 con
  `mockFetchRespuestas`/`mockFetchDiferido`/`mockFetchErrorRed`), unit de
  `validarRegistroUsuario` en `validacion.test.ts` (AC-008) y revisión/scoping
  de `GestionPage.test.tsx` (§8.7: sin debilitar criterios). `npm run
  lint`/`typecheck`/`test`/`build` verdes (AC-015); sin dependencias nuevas ni
  cambios de backend (AC-016).
- **Sin ADRs nuevos** (§11): SPEC-008 reutiliza ADR-008 y los patrones de
  SPEC-003/SPEC-006/SPEC-007 sin reinterpretación.

---

## 15. Related Documents

- `docs/specs/SPEC-008-usuarios-gestion.md` (spec aprobada — fuente de verdad)
- `docs/specs/SPEC-003-autenticacion.md` (contrato de registro: FR-001/005,
  BR-001..003, ERR-004..007, A-002/003/005) y `docs/architecture/SPEC-003.md`
  (contrato HTTP exacto, matchers `permitAll` de `SecurityConfig` §8.5,
  código de errores §8.6)
- `docs/specs/SPEC-006-frontend-react.md` y `docs/architecture/SPEC-006.md`
  (patrones de la SPA: capa API §5.2, validación §5.3, hooks §5.3/§5.4, UI
  §5.4, testing §8 — A-004 derogada parcialmente para el registro del `ADMIN`)
- `docs/architecture/SPEC-007.md` (patrón de sección de `/gestion` con selector
  de cliente + formularios + tests + scoping de `GestionPage.test.tsx`)
- `docs/specs/SPEC-001-clientes.md` (`GET /api/v1/clientes`, `ClienteDto`)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend)
- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (envelope de errores), §8
  (seguridad y propiedad)
- `docs/sprints/backlog.md` (E3: US-3.1 registro; E6: US-6.4 gestión ADMIN)
