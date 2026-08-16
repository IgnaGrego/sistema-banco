# Architecture — SPEC-007 (UI Caja: Depósitos y Retiros en Gestión)

## 1. Feature

Agregar a la SPA (SPEC-006) la operación de **caja** del `ADMIN` en la pantalla
`/gestion`: una sección **"Caja"** (FR-001) con dos formularios — **depósito**
(FR-003) y **retiro** (FR-004) — que consumen los endpoints ya existentes
`POST /api/v1/depositos` y `POST /api/v1/retiros` (SPEC-005, implementado y
mergeado). El `ADMIN` selecciona el **cliente** (selector compartido de la
sección, FR-002) y la **cuenta destino** (solo `ACTIVA` — BR-003), ingresa un
**monto** con pre-validación UX (BR-001; saldo suficiente en retiro — BR-002) y
recibe feedback de éxito/error con el envelope estándar `{ code, message,
details? }` (FR-005). Tras un `201`, el listado de cuentas del cliente se
**refresca** para que el saldo mostrado refleje la operación (FR-006): el
frontend **no calcula** saldos ni montos, los lee del `CuentaDto` refrescado
(BR-004).

La SPA **no introduce reglas de negocio nuevas**: las pre-validaciones de UI
(BR-001..BR-003) son espejos de reglas ya enforced por el backend (SPEC-005
BR-001..BR-004), que permanece como fuente de verdad y punto de enforcement
(BR-005, AGENTS.md §10/§11 — A-008 de SPEC-006).

El diseño es **frontend-only**: cero cambios en `backend/`, base de datos,
`docker/`, dependencias npm ni CSS (spec §10/§12, A-004). Deroga parcialmente
el §12 de SPEC-006 (A-005) solo para la **caja del `ADMIN`**: los retiros
propios del `CLIENTE` siguen fuera de alcance (spec §12).

---

## 2. Specification

Referencia:

- `docs/specs/SPEC-007-caja-gestion.md` (APROBADA — fuente de verdad;
  FR-001..FR-007, BR-001..BR-005, AF-001..AF-004, ERR-001..ERR-007,
  AC-001..AC-020, asunciones A-001..A-004).
- `docs/architecture/SPEC-006.md` (patrones de la SPA que esta spec extiende:
  capa API §5.2, `src/lib/validacion.ts` §5.3, hooks §5.3/§5.4, componentes
  UI §5.4, estados y errores §8, `TransferenciaPage` como precedente de
  formulario con confirmación y doble envío).
- `docs/architecture/SPEC-005.md` (contrato HTTP exacto de los endpoints:
  payloads `{cuentaId, monto}`, confirmaciones `201` y códigos de error
  400/401/403/404/409/422 — verificado contra el código real, §8.2).
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend que esta
  spec reutiliza sin reinterpretación).
- `ARCHITECTURE.md` §7 (API REST y envelope `{ code, message, details? }`).

---

## 3. Affected Modules

**Solo frontend.** El backend (SPEC-005 ya mergeado), las migraciones Flyway,
la base de datos, `docker/`, `package.json` (sin dependencias nuevas — AC-020)
y `src/index.css` (sin cambios de diseño — A-004) **no se tocan**.

**Archivos NUEVOS:**

| Archivo | Propósito |
| --- | --- |
| `frontend/src/api/caja.ts` | Módulo API de la caja: `depositar` y `retirar` (A-003, §5.2). |
| `frontend/src/hooks/useCaja.ts` | Hook de mutación por operación (`tipo: 'deposito' \| 'retiro'`), patrón `useTransferencia` con protección de doble envío (FR-005, §5.4). |
| `frontend/src/pages/gestion/CajaSection.tsx` | Sección "Caja" (FR-001..FR-006): selector de cliente, listado de cuentas y los dos formularios (A-001, §5.5/§8.5). |
| `frontend/src/pages/gestion/CajaSection.test.tsx` | Component tests (Vitest + RTL): AC-001..AC-018 (§10). |

**Archivos MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `frontend/src/api/types.ts` | Se agregan `DepositoRequest`, `RetiroRequest`, `DepositoConfirmacion`, `RetiroConfirmacion` (espejo de los records del backend — §8.2). |
| `frontend/src/lib/validacion.ts` | Se agregan `validarDeposito` y `validarRetiro` + helpers privados compartidos (BR-001..BR-003, §8.3). |
| `frontend/src/lib/validacion.test.ts` | Unit tests de las nuevas validaciones (AC-010, §10.3). |
| `frontend/src/pages/GestionPage.tsx` | Se monta `<CajaSection />` como tercera sección (FR-001, §5.1). |
| `frontend/src/pages/GestionPage.test.tsx` | Ajuste de queries no acotadas (duplicados de labels/textos al montar la nueva sección — §8.7). **Sin debilitar criterios** (AGENTS.md §12). |

**Verificado — sin cambios:** `App.test.tsx` (el heading "Clientes" sigue
siendo único; ver §8.7), `CuentasSection.test.tsx`/`ClientesSection.test.tsx`
(renderizan sus secciones aisladas, sin impacto), `httpClient.ts`,
`session.ts`, `jwt.ts`, `useCuentas.ts`, `useClientes.ts`, todos los
componentes UI (`CampoFormulario`, `Monto`, `Cargando`, `EstadoError`,
`EstadoVacio`), `index.css` y todo `backend/`.

---

## 4. Application Flow

### 4.1 Flujo de la operación de caja (main flow de la spec §6)

```text
Browser — /gestion (ProtectedRoute rolPermitido="ADMIN" — FR-001, §5.6)     presentation (SPA)
    ↓  GestionPage monta ClientesSection + CuentasSection + CajaSection
CajaSection (selector de cliente → useClientes; CajaDeCliente → useCuentas)  pages/gestion
    ↓  GET /api/v1/clientes; GET /api/v1/cuentas?clienteId={id} (FR-002)
FormularioCaja ×2 (depósito/retiro): cuenta destino (solo ACTIVA) + monto      pages/gestion
    ↓  pre-validación UX: validarDeposito / validarRetiro (BR-001..BR-003)
    ↓  submit → useCaja.ejecutar(cuentaId, monto)  [doble envío protegido]
src/api/caja.ts → depositar / retirar                                         api
    ↓  httpClient.request<T> — POST /api/v1/depositos | /api/v1/retiros
       payload exacto {cuentaId, monto}; Authorization: Bearer <JWT>          api/httpClient
    ↓  201 → DepositoConfirmacion / RetiroConfirmacion (idMovimiento, cuentaId,
       monto, fechaHora)  |  error → ApiError(envelope {code, message, details?})
Vite dev server — proxy /api → http://localhost:8080 (sin rewrite)            dev server
    ↓  HTTP/JSON
Backend API /api/v1 (SPEC-005, mergeado): SecurityConfig (matchers RBAC) →
RealizarDepositoUseCase / RealizarRetiroUseCase → Cuenta/Movimiento → PostgreSQL 16
```

- Tras un `201`, `FormularioCaja` muestra el panel de confirmación
  (`role="status"`, patrón de `TransferenciaPage` — FR-005) e invoca
  `onOperacionExitosa` = `recargar` de `useCuentas`: el listado de cuentas se
  re-fetchea (`GET /api/v1/cuentas?clienteId=`) y el saldo mostrado proviene
  del `CuentaDto` refrescado (FR-006, BR-004). Sin remount (ver §13: el
  remount por `key` de `CuentasSection` borraría el panel de confirmación).
- Los errores (`ApiError`) se mapean en el formulario activo: `details` con
  campo conocido (`cuentaId`/`monto`) → errores por campo vía
  `CampoFormulario`; resto del envelope → mensaje general (`role="alert"`,
  clase `error-general`); los datos ingresados se conservan (FR-005, ERR-001..
  ERR-007, §8.6).
- El `401` en requests autenticadas lo maneja globalmente `httpClient`
  (limpieza de sesión + redirección a `/login` — SPEC-006 FR-007): la sección
  no lo maneja específicamente (nota de la spec §8).

### 4.2 Cambio de cliente y nueva operación

1. Sin cliente seleccionado → `EstadoVacio` ("Seleccione un cliente para
   operar la caja.") y no se renderiza `CajaDeCliente`.
2. Al seleccionar cliente → `CajaDeCliente` (con `key={clienteSeleccionado}`)
   dispara `GET /api/v1/cuentas?clienteId=` y renderiza el listado + los dos
   formularios (AF-001 si no hay cuentas o no hay `ACTIVA`).
3. Tras operar, la confirmación reemplaza al formulario de esa operación; el
   otro formulario permanece intacto (estado independiente por hook — §5.4).
4. Cambiar de cliente remonta `CajaDeCliente` (cambio de `key`): formularios
   en blanco y confirmaciones limpias (paso 8 del main flow — "puede continuar
   con otra operación").

---

## 5. Components

### 5.1 Entry points / presentación — rutas y página

| Ruta | Acceso | Componente | FR |
| --- | --- | --- | --- |
| `/gestion` | `ADMIN` | `GestionPage` (secciones `ClientesSection`, `CuentasSection` y **`CajaSection`**) | FR-001 |

- **Sin rutas nuevas** (FR-001): la sección se monta dentro de `/gestion`,
  ya protegida por `ProtectedRoute rolPermitido="ADMIN"` (SPEC-006 FR-006).
  `App.tsx` y `main.tsx` no se tocan.
- **`src/pages/GestionPage.tsx`** — cambio mínimo:

```tsx
import ClientesSection from './gestion/ClientesSection';
import CuentasSection from './gestion/CuentasSection';
import CajaSection from './gestion/CajaSection';

export default function GestionPage() {
  return (
    <section>
      <h1>Gestión</h1>
      <ClientesSection />
      <CuentasSection />
      <CajaSection />
    </section>
  );
}
```

- `CajaSection` es autocontenida (patrón de `CuentasSection`): su propio
  `useClientes` (segundo `GET /api/v1/clientes` al montar `/gestion` — mismo
  patrón sin caché de SPEC-006 A-006; ver §12) y su selector de cliente.

### 5.2 Capa API — `src/api/caja.ts` y `src/api/types.ts`

**`src/api/caja.ts`** (NUEVO — A-003: módulo por recurso, patrón de
`transferencias.ts`):

```ts
import { request } from './httpClient';
import type { DepositoConfirmacion, DepositoRequest, RetiroConfirmacion, RetiroRequest } from './types';

/** POST /api/v1/depositos → 201 DepositoConfirmacion (SPEC-005 FR-001; exclusivo ADMIN). */
export async function depositar(body: DepositoRequest): Promise<DepositoConfirmacion> {
  return request<DepositoConfirmacion>('/depositos', { method: 'POST', body });
}

/** POST /api/v1/retiros → 201 RetiroConfirmacion (SPEC-005 FR-002). */
export async function retirar(body: RetiroRequest): Promise<RetiroConfirmacion> {
  return request<RetiroConfirmacion>('/retiros', { method: 'POST', body });
}
```

- **Nombres `depositar`/`retirar`:** fijados por la spec §10 (A-003). Siguen
  la convención verbo-por-recurso de la capa API (`transferir`, `abrirCuenta`,
  `crearCliente`, `listarCuentas`). Se descartó `realizarDeposito`/
  `realizarRetiro` (espejo de los use cases del backend, pero ajeno a la
  convención de nombres de `src/api/` — §13).
- `autenticar` por defecto (`true`): el token Bearer viaja en ambas requests;
  el `401` lo maneja globalmente `httpClient` (nota de la spec §8).

**`src/api/types.ts`** (MODIFICADO — agregar al final, tras
`TransferenciaConfirmacion`):

```ts
/** POST /api/v1/depositos (SPEC-005 FR-001). Espejo de `DepositoRequest(Long cuentaId, BigDecimal monto)`. */
export interface DepositoRequest {
  cuentaId: number;
  monto: number;
}

/** POST /api/v1/retiros (SPEC-005 FR-002). Espejo de `RetiroRequest(Long cuentaId, BigDecimal monto)`. */
export interface RetiroRequest {
  cuentaId: number;
  monto: number;
}

/** 201 de POST /api/v1/depositos. Espejo de `DepositoConfirmacion(Long idMovimiento, Long cuentaId, BigDecimal monto, Instant fechaHora)`. */
export interface DepositoConfirmacion {
  idMovimiento: number;
  cuentaId: number;
  monto: number;
  fechaHora: string;
}

/** 201 de POST /api/v1/retiros. Espejo de `RetiroConfirmacion(...)` (misma forma). */
export interface RetiroConfirmacion {
  idMovimiento: number;
  cuentaId: number;
  monto: number;
  fechaHora: string;
}
```

- **Verificado contra el código real** (`backend/.../DepositoRequest.java`,
  `RetiroRequest.java`, `application/usecase/DepositoConfirmacion.java`,
  `RetiroConfirmacion.java`): nombres de campos exactos. `BigDecimal` → `number`
  (JSON) y `Instant` → `string` ISO-8601 (nota de tipos de SPEC-006 §5.2). El
  frontend solo formatea (`formatearMontoARS`/`formatearFecha`), nunca calcula
  (BR-004).

### 5.3 Utilidades puras — `src/lib/validacion.ts`

Se agregan **dos funciones públicas** y dos helpers privados compartidos
(espejo de la decisión del backend: `DepositoRetiroValidator` como clase única
con dos cadenas explícitas, sin flag booleano — SPEC-005 §8.2/§13; ver §13):

```ts
// helpers privados (no exportados) junto a REGEX_MONTO existente
function validarCuentaCaja(cuentaId: number | null): string | undefined {
  if (cuentaId === null) return 'Seleccione una cuenta';
  return undefined;
}

function validarMontoCaja(monto: string): string | undefined {
  const montoRecortado = monto.trim();
  if (montoRecortado === '') return 'El monto es obligatorio';
  if (!REGEX_MONTO.test(montoRecortado)) return 'El monto debe ser un número con hasta 2 decimales';
  if (Number(montoRecortado) <= 0) return 'El monto debe ser mayor a 0';
  return undefined;
}

/** BR-001 y BR-003 — depósito: cuenta obligatoria y monto > 0 con hasta 2 decimales. */
export function validarDeposito(cuentaId: number | null, monto: string): ErroresPorCampo {
  const errores: ErroresPorCampo = {};
  const errCuenta = validarCuentaCaja(cuentaId);
  if (errCuenta !== undefined) errores.cuentaId = errCuenta;
  const errMonto = validarMontoCaja(monto);
  if (errMonto !== undefined) errores.monto = errMonto;
  return errores;
}

/**
 * BR-001..BR-003 — retiro: ídem depósito + saldo suficiente (espejo UX del
 * 422 SALDO_INSUFICIENTE — A-002). `saldoCuenta` es el saldo del CuentaDto
 * de la cuenta seleccionada (undefined si no hay selección → se omite el
 * chequeo; el backend permanece como fuente de verdad).
 */
export function validarRetiro(
  cuentaId: number | null,
  monto: string,
  saldoCuenta: number | undefined,
): ErroresPorCampo {
  const errores = validarDeposito(cuentaId, monto);
  if (
    Object.keys(errores).length === 0 &&
    saldoCuenta !== undefined &&
    Number(monto.trim()) > saldoCuenta
  ) {
    errores.monto = 'El monto no puede superar el saldo de la cuenta';
  }
  return errores;
}
```

- Reutiliza el `REGEX_MONTO` existente (`/^\d+(\.\d{1,2})?$/`) y los mensajes
  exactos de `validarTransferencia` (BR-001 = espejo de SPEC-005 BR-001 /
  SPEC-006 BR-004). Sin cambios en las funciones existentes.
- Borde del retiro: `monto === saldo` es **válido** (BR-002: `monto <= saldo`);
  `monto` `0`/negativo falla primero por BR-001.

### 5.4 Hook de mutación — `src/hooks/useCaja.ts`

**Decisión: hook `useCaja(tipo)`, una instancia por formulario** (spec §10:
"hook(s) de mutación de la caja siguiendo el patrón de `useTransferencia`";
justificación y alternativa local-state en §13):

```ts
import { useCallback, useRef, useState } from 'react';
import { depositar as depositarApi, retirar as retirarApi } from '../api/caja';
import type { ApiError } from '../api/httpClient';
import type { DepositoConfirmacion, RetiroConfirmacion } from '../api/types';

/** Operación de caja de la sección (A-001: dos formularios, una sección). */
export type TipoOperacionCaja = 'deposito' | 'retiro';

/** Unión de las confirmaciones 201 (misma forma {idMovimiento, cuentaId, monto, fechaHora}). */
export type ConfirmacionCaja = DepositoConfirmacion | RetiroConfirmacion;

/**
 * Hook de la caja (FR-005): envía el payload de una operación y expone
 * `{ enviando, confirmacion, error, ejecutar }`. Protección de doble envío
 * (AC-030 de SPEC-006): una request en vuelo bloquea el siguiente submit
 * (ref `enVuelo` + estado `enviando` para deshabilitar el botón).
 */
export function useCaja(tipo: TipoOperacionCaja) {
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState<ConfirmacionCaja | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const enVuelo = useRef(false);

  const ejecutar = useCallback(
    async (cuentaId: number, monto: number): Promise<ConfirmacionCaja | null> => {
      if (enVuelo.current) {
        return null;
      }
      enVuelo.current = true;
      setEnviando(true);
      setError(null);
      try {
        const resultado =
          tipo === 'deposito'
            ? await depositarApi({ cuentaId, monto })
            : await retirarApi({ cuentaId, monto });
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
    [tipo],
  );

  return { enviando, confirmacion, error, ejecutar };
}
```

- El hook construye el body `{cuentaId, monto}` (mismo shape en ambas
  operaciones): el formulario no maneja los DTOs.
- Devuelve `null` en doble submit y en error (patrón `useTransferencia`): el
  formulario distingue éxito (`!== null` → refresh + confirmación) de error
  (el estado `error` del hook se mapea por `useEffect`).

### 5.5 Sección Caja — `src/pages/gestion/CajaSection.tsx`

Estructura de componentes privados dentro del archivo (patrón de
`CuentasSection.tsx`, que mantiene `CuentasDeCliente`/`CuentaItem` privados):

```
CajaSection
├── useClientes() → { datos, cargando, error, recargar }
├── estado: clienteSeleccionado (number | null)
├── <div className="seccion">
│   ├── <h2>Caja</h2>
│   ├── cargandoClientes && clientes === null → <Cargando />
│   ├── errorClientes !== null → <EstadoError mensaje={errorClientes.message} onReintentar={recargarClientes} />
│   ├── <CampoFormulario id="caja-cliente" label="Cliente" error={erroresCliente}>   ← selector de cliente
│   │   └── <select id="caja-cliente" value={clienteSeleccionado ?? ''}>  ← opciones "Apellido, Nombre — DNI x"
│   │       (onChange: setClienteSeleccionado(...); limpiar errores)
│   └── clienteSeleccionado === null
│       ? <EstadoVacio mensaje="Seleccione un cliente para operar la caja." />
│       : <CajaDeCliente key={clienteSeleccionado} clienteId={clienteSeleccionado} />
└──
```

```
CajaDeCliente({ clienteId })
├── useCuentas(clienteId) → { datos, cargando, error, recargar }   (FR-002, FR-006)
├── cuentas = datos ?? []; cuentasActivas = cuentas.filter(c => c.estado === 'ACTIVA')  (BR-003)
├── sinCuentas = cuentasActivas.length === 0
├── cargando && datos === null → <Cargando />
├── error !== null → <EstadoError mensaje={error.message} onReintentar={recargar} />
├── cuentas.length === 0 → <EstadoVacio mensaje="El cliente no tiene cuentas." />       (AF-001)
│   └── (los formularios se renderizan igual, deshabilitados — ver abajo)
├── cuentas.length > 0 →
│   ├── <ul className="lista-cuentas">  ← todas las cuentas con saldo y estado (FR-002)
│   │   └── <CuentaItem cuenta={c} />   (espejo del privado de CuentasSection)
│   └── cuentasActivas.length === 0 → <EstadoVacio mensaje="El cliente no tiene cuentas activas." />  (AF-001)
├── <FormularioCaja tipo="deposito" cuentasActivas={cuentasActivas} onOperacionExitosa={recargar} />
└── <FormularioCaja tipo="retiro"  cuentasActivas={cuentasActivas} onOperacionExitosa={recargar} />
```

```
FormularioCaja({ tipo, cuentasActivas, onOperacionExitosa })
├── const { enviando, confirmacion, error, ejecutar } = useCaja(tipo)
├── estado local del formulario: cuentaId (string), monto (string),
│   erroresCampo (ErroresPorCampo), errorGeneral (string | null),
│   cuentaOperada (CuentaDto | null — snapshot del CBU al enviar, FR-005)
├── useEffect([error]): mapeo del envelope → errores por campo / general (§8.6)
├── <section>   ← raíz del formulario; los tests la localizan con
│   │             heading.closest('section') (dentro de CajaSection.test.tsx)
│   ├── <h3>{tipo === 'deposito' ? 'Depósito' : 'Retiro'}</h3>
│   ├── confirmacion !== null && cuentaOperada !== null
│   │   └── <ConfirmacionCaja tipo={tipo} confirmacion={confirmacion} cbu={cuentaOperada.cbu} />
│   └── <>  (formulario)
│       ├── errorGeneral !== null → <div role="alert" className="error-general">{errorGeneral}</div>
│       └── <form onSubmit={onSubmit} noValidate>
│           ├── <CampoFormulario id={`caja-cuenta-${tipo}`} label="Cuenta destino" error={erroresCampo.cuentaId}>
│           │   └── <select id={`caja-cuenta-${tipo}`} value={cuentaId} disabled={sinCuentas}>
│           │       ├── <option value="">{sinCuentas ? 'No hay cuentas disponibles' : 'Seleccione una cuenta'}</option>
│           │       └── cuentasActivas.map(c => <option key={c.id} value={c.id}>
│           │               {c.tipo} — CBU {c.cbu} — {formatearMontoARS(c.saldo)}</option>)
│           ├── <CampoFormulario id={`caja-monto-${tipo}`} label="Monto" error={erroresCampo.monto}>
│           │   └── <input id={`caja-monto-${tipo}`} name="monto" value={monto}
│           │             onChange={...} inputMode="decimal" />
│           └── <button type="submit" disabled={enviando || sinCuentas}>
│                   {enviando ? 'Procesando...' : tipo === 'deposito' ? 'Depositar' : 'Retirar'}
│               </button>
```

```
ConfirmacionCaja({ tipo, confirmacion, cbu })
└── <div role="status" className="confirmacion">        (patrón TransferenciaPage — FR-005)
    ├── <h3>{tipo === 'deposito' ? 'Depósito realizado' : 'Retiro realizado'}</h3>
    ├── <p>ID de movimiento: {confirmacion.idMovimiento}</p>
    ├── <p>Monto: {formatearMontoARS(confirmacion.monto)}</p>      (BR-004)
    ├── <p>Cuenta (CBU): {cbu}</p>                                  (FR-005 — del snapshot local)
    └── <p>Fecha y hora: {formatearFecha(confirmacion.fechaHora)}</p>
```

**Comportamiento de `onSubmit`** (en `FormularioCaja`):

```ts
const onSubmit = async (event: FormEvent) => {
  event.preventDefault();
  const idCuenta = cuentaId !== '' ? Number(cuentaId) : null;
  const saldoCuenta = cuentasActivas.find((c) => String(c.id) === cuentaId)?.saldo;
  const erroresValidacion =
    tipo === 'deposito' ? validarDeposito(idCuenta, monto) : validarRetiro(idCuenta, monto, saldoCuenta);
  setErroresCampo(erroresValidacion);
  if (Object.keys(erroresValidacion).length > 0 || idCuenta === null) {
    return; // BR-001..BR-003: sin request
  }
  const cuenta = cuentasActivas.find((c) => c.id === idCuenta);
  setCuentaOperada(cuenta ?? null);
  setErrorGeneral(null);
  const resultado = await ejecutar(idCuenta, Number(monto.trim())); // payload {cuentaId, monto} exacto
  if (resultado !== null) {
    onOperacionExitosa(); // FR-006: recargar() de useCuentas — refresh del listado
  }
};
```

### 5.6 Authorization

| Nivel | Regla | Fuente |
| --- | --- | --- |
| Routing (UX) | `CajaSection` solo se monta en `/gestion`, protegida por `ProtectedRoute rolPermitido="ADMIN"` (SPEC-006 FR-006): el `CLIENTE` nunca ve los formularios (FR-001, BR-005, ERR-006) | `App.tsx` (sin cambios) |
| Endpoint consumido | `POST /api/v1/depositos` → `hasRole("ADMIN")` (depósito exclusivo de `ADMIN` — SPEC-005 §9) | `SecurityConfig` (sin cambios) |
| Endpoint consumido | `POST /api/v1/retiros` → `hasAnyRole("ADMIN", "CLIENTE")`; la SPA lo llama solo con token `ADMIN` (el retiro propio del `CLIENTE` queda fuera de alcance — spec §12) | `SecurityConfig` (sin cambios) |
| Enforcement | El backend verifica autorización y reglas en cada request (matchers RBAC + `DepositoRetiroValidator` — SPEC-005 §8.4/§8.5); las pre-validaciones de la UI son solo UX (BR-005, AGENTS.md §17) | backend (sin cambios) |

- `401` en request autenticada → manejo global de `httpClient`
  (sesión limpiada + redirect a `/login` — SPEC-006 FR-007): la sección no lo
  trata específicamente (nota de la spec §8). El `403 ACCESO_DENEGADO`
  defensivo (ERR-006) llega como envelope y se muestra como mensaje general.

### 5.7 Async work

**Ninguno.** Sin jobs, colas, eventos ni WebSockets. El único trabajo
asíncrono es `fetch` gobernado por `useClientes`/`useCuentas` (datos) y
`useCaja` (mutación con doble envío). El refresh post-`201` es una request
adicional de `useCuentas.recargar()` (FR-006).

### 5.8 Dependencias

**Ninguna dependencia npm nueva** (AC-020, AGENTS.md §13). Se reutilizan
íntegramente: `httpClient` (fetch nativo — sin axios), componentes UI propios
(`CampoFormulario`, `Monto`, `Cargando`, `EstadoError`, `EstadoVacio`), CSS
plano existente (clases `.seccion`, `.campo`, `.error-general`,
`.confirmacion`, `.lista-cuentas`, `.cuenta`, `.cuenta-estado-*`, `.monto` —
**sin cambios en `index.css`**, A-004) y la suite Vitest + RTL + jest-dom con
el helper `mockFetchRespuestas` (sin MSW).

---

## 6. Data Changes

**Backend y base de datos: ninguno** (spec §10): sin migraciones Flyway,
tablas, columnas ni propiedades de configuración. Los endpoints ya existen y
están mergeados (SPEC-005).

**Frontend (solo):** los archivos nuevos/modificados de §3. Sin cambios de
estado global ni de sesión: el estado de la caja es local a `CajaSection`
(selector de cliente) y a cada `FormularioCaja` (formulario + `useCaja`), sin
caché ni estado compartido (patrón A-006 de SPEC-006).

---

## 7. External Integrations

- **Backend REST API `/api/v1`** (única integración, ya existente): dos
  endpoints nuevos consumidos por la SPA — `POST /api/v1/depositos` y
  `POST /api/v1/retiros` — vía el proxy de desarrollo de Vite (`/api` →
  `http://localhost:8080`, sin rewrite; same-origin desde el navegador, sin
  CORS). Autenticación Bearer JWT (vía `httpClient`); errores con el envelope
  estándar `{ code, message, details? }` (ARCHITECTURE.md §7).
- `GET /api/v1/clientes` y `GET /api/v1/cuentas?clienteId=` ya consumidos por
  las secciones existentes; se reutilizan sin cambios de contrato.
- **Sin** proveedores externos, mensajería ni otros servicios.

---

## 8. Detailed Design

### 8.1 File map completo

**NUEVOS:**

| Archivo | Contenido |
| --- | --- |
| `frontend/src/api/caja.ts` | `depositar(body: DepositoRequest): Promise<DepositoConfirmacion>`; `retirar(body: RetiroRequest): Promise<RetiroConfirmacion>` (§5.2). |
| `frontend/src/hooks/useCaja.ts` | `TipoOperacionCaja`, `ConfirmacionCaja`, `useCaja(tipo)` → `{ enviando, confirmacion, error, ejecutar(cuentaId, monto) }` (§5.4). |
| `frontend/src/pages/gestion/CajaSection.tsx` | `CajaSection` (default export) + privados `CajaDeCliente`, `FormularioCaja`, `ConfirmacionCaja`, `CuentaItem` (§5.5/§8.5). |
| `frontend/src/pages/gestion/CajaSection.test.tsx` | Component tests AC-001..AC-018 (§10.2). |

**MODIFICADOS:**

| Archivo | Cambio |
| --- | --- |
| `frontend/src/api/types.ts` | +`DepositoRequest`, `RetiroRequest`, `DepositoConfirmacion`, `RetiroConfirmacion` (§8.2). |
| `frontend/src/lib/validacion.ts` | +`validarDeposito`, `validarRetiro`, helpers privados `validarCuentaCaja`/`validarMontoCaja` (§8.3). |
| `frontend/src/lib/validacion.test.ts` | +describe de `validarDeposito`/`validarRetiro` (AC-010, §10.3). |
| `frontend/src/pages/GestionPage.tsx` | +`<CajaSection />` (tercera sección, §5.1). |
| `frontend/src/pages/GestionPage.test.tsx` | Scoping de queries duplicadas (§8.7). |

**Sin cambios (verificado):** `App.tsx`, `httpClient.ts`, `session.ts`,
`jwt.ts`, `useCuentas.ts`, `useClientes.ts`, `useTransferencia.ts`, todos los
componentes UI, `index.css`, `package.json`, todo `backend/`.

### 8.2 Contrato de la API (verificado contra el código)

| Operación | Método/Ruta | Request TS (espejo) | Response 201 TS (espejo) | Errores |
| --- | --- | --- | --- | --- |
| Depósito | `POST /api/v1/depositos` | `{cuentaId: number, monto: number}` ← `DepositoRequest(Long, BigDecimal)` | `{idMovimiento, cuentaId, monto, fechaHora}` ← `DepositoConfirmacion(Long, Long, BigDecimal, Instant)` | 400, 401, 403, 404, 409, 422 |
| Retiro | `POST /api/v1/retiros` | `{cuentaId: number, monto: number}` ← `RetiroRequest(Long, BigDecimal)` | `{idMovimiento, cuentaId, monto, fechaHora}` ← `RetiroConfirmacion(...)` | 400, 401, 403, 404, 409, 422 |

- Códigos de error y envelope (SPEC-005 §8.6): `400 DATOS_INVALIDOS` (con
  `details[0].campo === 'monto'` o sin details si el monto no es numérico),
  `403 ACCESO_DENEGADO`, `404 CUENTA_NO_ENCONTRADA`, `409
  CONFLICTO_CONCURRENCIA`, `422 SALDO_INSUFICIENTE` (solo retiro) y `422
  CUENTA_BLOQUEADA`. Error de red → `ApiError` con `code === 'ERROR_RED'` y
  `MENSAJE_ERROR_RED` (wrapper `httpClient` — ERR-007).
- El `201` no trae `Location` ni headers especiales: la UI usa solo el body.

### 8.3 Validación — reglas por función

| Función | Reglas (en orden) | Errores de campo |
| --- | --- | --- |
| `validarDeposito(cuentaId, monto)` | BR-003: `cuentaId === null` → error; BR-001: `monto` vacío → error; no cumple `REGEX_MONTO` → error; `Number(monto) <= 0` → error | `cuentaId` ('Seleccione una cuenta'); `monto` ('El monto es obligatorio' / 'El monto debe ser un número con hasta 2 decimales' / 'El monto debe ser mayor a 0') |
| `validarRetiro(cuentaId, monto, saldoCuenta)` | ídem depósito + BR-002: si el monto validado `> saldoCuenta` → error `monto` ('El monto no puede superar el saldo de la cuenta') | ídem + `monto` (saldo) |

- Mensajes idénticos a los de `validarTransferencia` para las reglas
  compartidas (BR-001): consistencia de textos de la UI y de los tests.
- `saldoCuenta === undefined` (sin selección válida) → se omite BR-002: la
  pre-validación solo compara contra el saldo conocido del `CuentaDto`
  seleccionado (A-002; el backend sigue siendo la fuente de verdad).

### 8.4 useCaja — contrato

| Miembro | Tipo | Comportamiento |
| --- | --- | --- |
| `enviando` | `boolean` | `true` durante la request en vuelo → deshabilita el botón (AC-018). |
| `confirmacion` | `ConfirmacionCaja \| null` | Seteada solo ante `201` → muestra el panel (FR-005). |
| `error` | `ApiError \| null` | Seteado ante cualquier error (`ApiError` del envelope o `ERROR_RED`) → mapeo del formulario (§8.6). |
| `ejecutar(cuentaId, monto)` | `Promise<ConfirmacionCaja \| null>` | Construye `{cuentaId, monto}` y llama `depositar`/`retirar` según `tipo`. `null` en doble submit y en error (patrón `useTransferencia`). |

- El estado del hook es **independiente por instancia**: una operación de
  depósito no afecta el formulario de retiro (dos instancias de `useCaja`).

### 8.5 CajaSection — estructura JSX y estados (resumen ejecutivo)

- **Raíz:** `<div className="seccion">` con `<h2>Caja</h2>` (patrón de
  `CuentasSection`; A-004).
- **Selector de cliente:** `CampoFormulario id="caja-cliente" label="Cliente"`
  (mismo label que `CuentasSection` — por eso los tests de `GestionPage`
  acotan con `within`, §8.7). Opciones `"{apellido}, {nombre} — DNI {dni}"`.
  Al cambiar: `setClienteSeleccionado`, `setErroresCampo({})`,
  `setErrorGeneral(null)`.
- **Sin cliente:** `EstadoVacio` "Seleccione un cliente para operar la caja."
- **Con cliente:** `CajaDeCliente key={clienteSeleccionado}` (remount al
  cambiar de cliente → formularios limpios, §4.2).
- **`CajaDeCliente`:** `useCuentas(clienteId)`; listado completo con
  `CuentaItem` (CBU, tipo, `Monto` con saldo, moneda, estado — FR-002);
  estados: `Cargando` (solo si `cargando && datos === null`), `EstadoError`
  con `Reintentar` (`recargar`), `EstadoVacio` ("El cliente no tiene
  cuentas." / "El cliente no tiene cuentas activas." — AF-001). Formularios
  siempre renderizados; `sinCuentas` (sin `ACTIVA`) → selects y botones
  `disabled`, placeholder "No hay cuentas disponibles" (espejo de
  `TransferenciaPage`).
- **`FormularioCaja`:** raíz `<section>` (sin clase nueva; los tests la
  acotan con `heading.closest('section')`); h3 `'Depósito'`/`'Retiro'`;
  confirmación reemplaza al formulario tras `201` (patrón
  `TransferenciaPage`); botones `'Depositar'`/`'Retirar'` (label "Procesando..."
  durante el envío).
- **IDs únicos en la página:** `caja-cliente`, `caja-cuenta-deposito`,
  `caja-cuenta-retiro`, `caja-monto-deposito`, `caja-monto-retiro` (sin
  colisión con `cliente-selector`/`tipo-cuenta`/`moneda-cuenta` de
  `CuentasSection`).

### 8.6 Manejo de errores del envelope (FR-005, ERR-001..ERR-007)

Espejo del patrón de `TransferenciaPage` (whitelist de campos conocidos +
fallback general):

```ts
const CAMPOS_CAJA = new Set(['cuentaId', 'monto']);

// dentro de FormularioCaja:
useEffect(() => {
  if (error === null) return;
  if (error.details !== undefined && error.details.length > 0) {
    const porCampo: ErroresPorCampo = {};
    let mensajeGeneral = error.message;
    for (const detalle of error.details) {
      if (CAMPOS_CAJA.has(detalle.campo)) {
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

- `400 DATOS_INVALIDOS` con `details[0].campo === 'monto'` → error por campo
  en `monto` (ERR-001); el valor del input se conserva (el estado local no se
  limpia — FR-005). `400` sin details (monto no numérico) → mensaje general.
- `422 SALDO_INSUFICIENTE`, `422 CUENTA_BLOQUEADA`, `404
  CUENTA_NO_ENCONTRADA`, `409 CONFLICTO_CONCURRENCIA`, `403 ACCESO_DENEGADO` y
  `ERROR_RED` → mensaje general (`role="alert"`, clase `error-general`) con
  los datos conservados y posibilidad de reintentar (ERR-002..ERR-007,
  AF-002/AF-003/AF-004; el backend no reintenta — SPEC-005 BR-004).
- Al refrescar tras `CUENTA_BLOQUEADA`/`404`, la cuenta deja de ofrecerse en
  el selector (AF-004: el selector se alimenta del listado refrescado, solo
  `ACTIVA`).

### 8.7 Ajuste de `GestionPage.test.tsx` — scoping de queries (por test)

Al montar `CajaSection` en `GestionPage`, las queries no acotadas del test
existente dejan de ser válidas (nota de la spec §11). Ajustes **concretos**
(se acotan las queries, no se debilitan los criterios — AGENTS.md §12):

**Test 1 — "AC-028 — 403 ACCESO_DENEGADO en el listado":** **sin cambios.**
Ya usa `findAllByText(...)` con `length >= 1`; con `CajaSection` montada el
mensaje del envelope aparece en `ClientesSection` y en `CajaSection` (ambas
consumen `GET /api/v1/clientes`), y la aserción `>= 1` sigue pasando.

**Test 2 — "AC-028 — 409 CONFLICTO_UNICIDAD al crear un cliente":**

- `await screen.findByText('Pérez, Juan')` → reemplazar por:

```ts
const nombres = await screen.findAllByText('Pérez, Juan');
expect(nombres.length).toBeGreaterThanOrEqual(1);
```

  (el texto ahora puede aparecer en más de un lugar — listado de clientes y
  opciones de los selectores "Cliente" de `CuentasSection`/`CajaSection`;
  `findAllByText` espera la carga sin fallar por múltiples coincidencias).
- `getAllByLabelText('Nombre')[0]`, `getAllByLabelText('Apellido')[0]`,
  `getAllByLabelText('DNI')[0]`, `getAllByLabelText('Email')[0]`,
  `getByRole('button', { name: 'Crear cliente' })` y
  `findByText('Conflicto de unicidad de datos')` → **sin cambios** (labels y
  textos exclusivos de `ClientesSection`; los `[0]` ya presentes siguen
  funcionando).

**Test 3 — "AC-028 — 422 en la apertura de cuenta":**

- `await screen.findByText('Pérez, Juan')` → `findAllByText` (como test 2).
- `screen.getByLabelText('Cliente')` → **acotar al selector de la sección
  Cuentas** (ahora hay dos labels "Cliente": `CuentasSection` y
  `CajaSection`). Importar `within` de `@testing-library/react`:

```ts
import { screen, within } from '@testing-library/react';
// ...
const seccionCuentas = within(
  screen.getByRole('heading', { name: 'Cuentas' }).closest('.seccion')!,
);
await usuario.selectOptions(seccionCuentas.getByLabelText('Cliente'), '1');
```

  (el heading `h2 "Cuentas"` es único — `h1 "Gestión"`, `h2 "Clientes"`,
  `h2 "Caja"` no colisionan; `closest('.seccion')` acota al árbol de la
  sección. Alternativa aceptable de la spec §11: `getAllByLabelText('Cliente')[0]`
  — primer selector en orden de render — pero el `within` por heading es
  independiente del orden y expresa la intención).
- `getByRole('button', { name: 'Abrir cuenta' })` (único; `CajaSection` usa
  "Depositar"/"Retirar") y `findByText('La moneda solicitada no es soportada')`
  (solo `CuentasSection` hace `POST /api/v1/cuentas`) → **sin cambios**.

**Test 4 — "AC-021 — la vista de gestión no expone acciones de CLIENTE":**

- `await screen.findByText('Pérez, Juan')` → `findAllByText` (como test 2).
- `queryByText('Transferencia')` y `queryByLabelText('CBU destino')` →
  **sin cambios** (siguen siendo exclusivos del formulario de transferencia).
- `queryByLabelText('Monto')` → **eliminar** (nota de la spec §11): el label
  "Monto" ya no es exclusivo de la transferencia — `CajaSection` lo usa en
  sus dos formularios. El criterio AC-021 (ausencia del formulario de
  transferencia en `/gestion`) queda verificado por `'Transferencia'` y
  `'CBU destino'`, que son los elementos distintivos de esa sección. No se
  debilita el criterio: se reemplaza un proxy inválido por las queries
  específicas que la spec prescribe. (Opcional, no requerido: reafirmar que
  los dos campos "Monto" pertenecen a la caja con
  `expect(screen.getAllByLabelText('Monto')).toHaveLength(2)`.)

**`App.test.tsx`: sin cambios (verificado).** Los tests de `/gestion`
("AC-013 — un ADMIN en /cuentas vuelve a /gestion" y "…visitando /login")
usan `findByText('Clientes')`: el heading `h2 "Clientes"` de `ClientesSection`
sigue siendo la única coincidencia exacta (las opciones de los selectores de
cliente tienen texto "Pérez, Juan — DNI …"; `CajaSection` no agrega texto
exacto "Clientes"). El mock `GET /api/v1/clientes → []` sirve también a la
request de `CajaSection` (helper `mockFetchRespuestas` responde por URL).

### 8.8 Convenciones

- Se reutilizan componentes y clases existentes sin modificarlos (A-004):
  `CampoFormulario` (error por campo con `role="alert"`), `Monto`,
  `Cargando` (`role="status"`), `EstadoError` (`role="alert"` + Reintentar),
  `EstadoVacio`, `formatearMontoARS`, `formatearFecha`.
- El listado de cuentas duplica el `CuentaItem` privado de `CuentasSection`
  (~10 líneas): extraer un componente compartido implicaría refactorizar
  `CuentasSection` (fuera de alcance — AGENTS.md §14; ver §13).
- Textos de la UI en español, consistentes con las secciones existentes
  (spec §12: sin i18n).

---

## 9. Build & Dependencies

**Ninguna dependencia npm nueva** (AC-020, AGENTS.md §13). `package.json` y
`vite.config.ts` intactos. Sin cambios de tooling: los tests corren con
`npm test` (Vitest + RTL, `fetch` mockeado con `mockFetchRespuestas`),
`npm run lint`, `npm run typecheck` y `npm run build` deben pasar (AC-019).

---

## 10. Testing Strategy

**Comandos (FR-007, AC-019):** `npm run lint`, `npm run typecheck`, `npm test`
y `npm run build`. Component tests en jsdom con `fetch` global mockeado
(`vi.stubGlobal` + `mockFetchRespuestas`/`mockFetchErrorRed`/`mockFetchDiferido`
de `src/test/helpers.tsx`); `localStorage` limpio entre tests
(`vitest.setup.ts`).

### 10.1 Mapeo AC → archivos de test

| AC | Tipo | Archivo(s) | Caso(s) |
| --- | --- | --- | --- |
| AC-001 | component | `src/pages/gestion/CajaSection.test.tsx` | render de la sección: headings "Caja", "Depósito", "Retiro" y botones "Depositar"/"Retirar"; sin rutas nuevas (la sección se monta en `GestionPage`, cubierto por `GestionPage.test.tsx` con sesión ADMIN) |
| AC-002 | component | `CajaSection.test.tsx` | seleccionar cliente → `GET /api/v1/cuentas?clienteId={id}`; los selectores "Cuenta destino" ofrecen **solo `ACTIVA`** (la `BLOQUEADA` no es opción); la `BLOQUEADA` sí aparece en el listado |
| AC-003 | component | `CajaSection.test.tsx` | cliente sin cuentas → `EstadoVacio` + formularios deshabilitados; cliente solo con `BLOQUEADA` → `EstadoVacio` "no tiene cuentas activas" + deshabilitados (AF-001) |
| AC-004 | component | `CajaSection.test.tsx` | depósito exitoso: payload exacto `{cuentaId, monto}` a `POST /api/v1/depositos`; `201` → panel de confirmación (`idMovimiento`, monto ARS, `fechaHora`) y refresh del listado con saldo actualizado (FR-003/005/006) |
| AC-005 | component | `CajaSection.test.tsx` | retiro exitoso: payload exacto a `POST /api/v1/retiros`; `201` → confirmación y refresh (FR-004/005/006) |
| AC-006 | component | `CajaSection.test.tsx` | el saldo post-operación proviene del `CuentaDto` refrescado (el mock "persiste" el nuevo saldo; la UI lo muestra sin calcular — BR-004) |
| AC-007 | component | `CajaSection.test.tsx` | pre-validación del monto en **ambos** formularios: vacío, no numérico, `0`/negativo, 3 decimales → error por campo y **sin request** (BR-001) |
| AC-008 | component | `CajaSection.test.tsx` | retiro: monto > saldo de la cuenta seleccionada → error por campo y sin request (BR-002) |
| AC-009 | component | `CajaSection.test.tsx` | sin cuenta seleccionada → error por campo y sin request (BR-003) |
| AC-010 | unit | `src/lib/validacion.test.ts` | `validarDeposito`/`validarRetiro`: monto vacío/`0`/negativo/3 decimales/no numérico; retiro sin saldo suficiente (BR-001/BR-002) |
| AC-011 | component | `CajaSection.test.tsx` | `400 DATOS_INVALIDOS` con `details` de `monto` → error por campo en `monto`; datos conservados (ERR-001) |
| AC-012 | component | `CajaSection.test.tsx` | `422 SALDO_INSUFICIENTE` → mensaje del envelope general; datos conservados (ERR-002, AF-002) |
| AC-013 | component | `CajaSection.test.tsx` | `422 CUENTA_BLOQUEADA` → mensaje del envelope (ERR-003, AF-004) |
| AC-014 | component | `CajaSection.test.tsx` | `404 CUENTA_NO_ENCONTRADA` → mensaje del envelope (ERR-004) |
| AC-015 | component | `CajaSection.test.tsx` | `409 CONFLICTO_CONCURRENCIA` → mensaje del envelope y reintento posible (ERR-005, AF-003) |
| AC-016 | component | `CajaSection.test.tsx` | `403 ACCESO_DENEGADO` → mensaje del envelope (ERR-006, defensivo) |
| AC-017 | component | `CajaSection.test.tsx` | error de red → `MENSAJE_ERROR_RED` y reintento posible (ERR-007, AF-003) |
| AC-018 | component | `CajaSection.test.tsx` | doble envío: botón deshabilitado durante la request y una sola request (FR-005, espejo AC-030 de SPEC-006) |
| AC-019 | comando | — | `npm run lint`, `npm run typecheck`, `npm run build` y `npm test` completos |
| AC-020 | revisión | — | sin dependencias npm nuevas; sin cambios en backend, migraciones ni `docker/` |

### 10.2 `CajaSection.test.tsx` — escenarios concretos

Render aislado de la sección (patrón de `CuentasSection.test.tsx`, sin sesión:
`render(<CajaSection />)`); `useClientes`/`useCuentas` disparan las requests
mockeadas. Helper de scoping entre los dos formularios (los labels "Cuenta
destino" y "Monto" están duplicados dentro de la sección — nota de la spec
§11):

```tsx
import { render, screen, within } from '@testing-library/react';
// ...
function seccionDe(titulo: string) {
  const heading = screen.getByRole('heading', { name: titulo });
  return within(heading.closest('section')!);
}
```

Fixtures (reutilizar el patrón de `CuentasSection.test.tsx`):

```ts
const CLIENTES: ClienteDto[] = [ /* id 1: 'Pérez, Juan'; id 2: 'Gómez, Ana' */ ];
const CUENTAS: CuentaDto[] = [
  { id: 1, clienteId: 1, cbu: '0000003100000000000001', tipo: 'CAJA_AHORRO',
    saldo: 1000, moneda: 'ARS', estado: 'ACTIVA', createdAt: '...' },
  { id: 2, clienteId: 1, cbu: '0000003100000000000002', tipo: 'CUENTA_CORRIENTE',
    saldo: 500, moneda: 'ARS', estado: 'BLOQUEADA', createdAt: '...' },
];
async function seleccionarCliente(usuario: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('option', { name: /Pérez, Juan/ }); // clientes cargados
  await usuario.selectOptions(screen.getByLabelText('Cliente'), '1'); // label único: sección aislada
  await screen.findByText('CBU: 0000003100000000000001'); // cuentas cargadas
}
```

Casos clave (detalle de la implementación de cada uno):

- **AC-002:** mock `GET /api/v1/cuentas` con `CUENTAS` (1 ACTIVA + 1
  BLOQUEADA). Tras seleccionar cliente: verificar la llamada
  `'/api/v1/cuentas?clienteId=1'` (patrón del AC-026 de `CuentasSection`);
  en **cada** formulario (`seccionDe('Depósito')` y `seccionDe('Retiro')`):
  `getByRole('option', { name: /CBU 0000003100000000000001/ })` presente y
  `queryByRole('option', { name: /CBU 0000003100000000000002/ })` **null**
  (BR-003); el listado sí muestra la `BLOQUEADA` (`findByText('BLOQUEADA')`).
- **AC-004 (depósito + refresh + AC-006):** mock **con estado** (patrón del
  AC-025 de `CuentasSection`): el `POST /api/v1/depositos` "persiste"
  `saldo: 1100` en `cuentasBackend` y responde
  `{ idMovimiento: 77, cuentaId: 1, monto: 100, fechaHora: '2026-08-16T14:30:00' }`;
  el `GET` posterior (refresh) devuelve el saldo actualizado. En
  `seccionDe('Depósito')`: `selectOptions(getByLabelText('Cuenta destino'), '1')`,
  `type(getByLabelText('Monto'), '100')`, `click(getByRole('button', { name: 'Depositar' }))`.
  Assert: payload exacto (`JSON.parse(body) toEqual({ cuentaId: 1, monto: 100 })` —
  verificar que `Number('100')` viaja como número); panel de confirmación
  (`findByText('Depósito realizado')`, `findByText('ID de movimiento: 77')`,
  `findByText(formatearMontoARS(100))`, `findByText(formatearFecha('2026-08-16T14:30:00'))`,
  `findByText('Cuenta (CBU): 0000003100000000000001')`); **refresh**: el
  listado muestra `findByText(formatearMontoARS(1100))` — el saldo proviene
  del `CuentaDto` refrescado, no de un cálculo local (AC-006, BR-004). Nota:
  importar `formatearMontoARS`/`formatearFecha` en el test para los strings
  esperados (evita drift de locale; el `\u00A0` ya se normaliza en
  `formatearMontoARS`).
- **AC-005 (retiro):** ídem con `POST /api/v1/retiros` (payload `{cuentaId: 1,
  monto: 100}`, saldo 1000 → 900, confirmación `{ idMovimiento: 88, ... }`),
  botón "Retirar", panel "Retiro realizado".
- **AC-007 (ambos formularios):** para cada formulario, casos:
  `''`, `'abc'`, `'0'`, `'-5'`, `'10.555'` → `getByRole('alert')` (dentro de
  la sección) con el mensaje esperado y el mock **no** recibe `POST`
  (`fetchMock` sin llamadas a `/api/v1/depositos`/`/api/v1/retiros`).
- **AC-008:** en `seccionDe('Retiro')` con cuenta 1 (saldo 1000): monto
  `'1500'` → alert "El monto no puede superar el saldo de la cuenta" y sin
  `POST /api/v1/retiros`. Borde positivo cubierto por AC-010 (unit).
- **AC-009:** en un formulario, sin seleccionar cuenta: submit → alert con
  "Seleccione una cuenta" y sin request. Nota: el placeholder del selector
  tiene el mismo texto → usar `getByRole('alert')` + `toHaveTextContent`
  (patrón del test BR-007 de `CuentasSection.test.tsx`), no `getByText`.
- **AC-011:** `POST /api/v1/depositos` → `400` con
  `{ code: 'DATOS_INVALIDOS', message: 'Datos inválidos', details: [{ campo: 'monto', mensaje: 'El monto debe ser mayor a 0' }] }`
  → alert de `monto` dentro del formulario; el input conserva el valor
  (`expect(getByLabelText('Monto')).toHaveValue('0')` tras el submit).
- **AC-012..AC-016:** `422 SALDO_INSUFICIENTE`, `422 CUENTA_BLOQUEADA`,
  `404 CUENTA_NO_ENCONTRADA`, `409 CONFLICTO_CONCURRENCIA`, `403
  ACCESO_DENEGADO` → `findByText(message del envelope)` (mensaje general,
  `role="alert"`); en AC-012/AC-015 verificar además que el monto sigue en el
  input y que un segundo submit reenvía (reintento — AF-002/AF-003).
- **AC-017:** `mockFetchErrorRed()` → `findByText(MENSAJE_ERROR_RED)` y
  segundo submit con mock restaurado → éxito (reintento).
- **AC-018:** `mockFetchDiferido()` (o un mock manual de `fetch` con promesa
  pendiente): submit → botón `disabled` y con texto "Procesando..."; segundo
  submit no dispara segunda request (`fetchMock` llamado una sola vez con
  `POST /api/v1/depositos`); al resolver → confirmación y botón habilitado.
- **Independencia de formularios (complementa AC-001/AC-004):** tras un
  depósito exitoso, `seccionDe('Retiro')` sigue mostrando su formulario (no
  la confirmación) — estado por instancia de `useCaja`.

### 10.3 `validacion.test.ts` — casos unitarios (AC-010)

Nuevo `describe` para las dos funciones (patrón del `describe` de
`validarTransferencia`):

- `validarDeposito`: `(1, '100')` → `{}`; `(1, '100.5')` → `{}`; `(null, '100')`
  → `cuentaId`; `(1, '')` → `monto`; `(1, 'abc')` → `monto`; `(1, '0')` →
  `monto`; `(1, '-5')` → `monto`; `(1, '10.555')` → `monto`.
- `validarRetiro`: `(1, '100', 1000)` → `{}`; `(1, '1000', 1000)` → `{}`
  (borde: `monto === saldo` es válido — BR-002); `(1, '1500', 1000)` → `monto`
  ("no puede superar el saldo"); `(1, '100', undefined)` → `{}` (sin saldo
  conocido se omite BR-002); los mismos casos de monto de `validarDeposito`
  siguen aplicando.

### 10.4 `GestionPage.test.tsx` — ajustes

Los cambios concretos por test están en §8.7 (importar `within`; `findAllByText`
para "Pérez, Juan"; scoping del label "Cliente" al heading "Cuentas"; eliminar
la aserción de `queryByLabelText('Monto')` del test AC-021). Los cuatro tests
deben seguir pasando con las mismas aserciones de criterio.

### 10.5 Cobertura por capa

Unit puro para `validacion.ts` (AC-010) + component tests para `CajaSection`
(render, interacción, payloads exactos, confirmación, errores del envelope,
doble envío — AC-001..AC-018) + ajuste de `GestionPage.test.tsx`. Sin tests de
integración con backend real ni e2e (fuera de alcance — spec §12).

---

## 11. ADR

**No se crean ADRs nuevos.** SPEC-007 es una extensión **frontend-only** y de
bajo riesgo que reutiliza sin reinterpretación las decisiones ya registradas:

- **ADR-008** cubre todo el frontend (stack React/Vite, proxy sin CORS,
  sesión `localStorage`, rol client-side como UX, estado local por página,
  alcance). La caja del `ADMIN` extiende el alcance de SPEC-006 (derogación
  parcial de A-005) sin cambiar ninguna de esas decisiones.
- **SPEC-006 §5.2/§5.3/§5.4** documenta los patrones que esta spec replica:
  módulos API por recurso, validación pura en `src/lib/validacion.ts`, hook de
  mutación con doble envío, manejo del envelope y scoping de tests.
- **SPEC-005 §11** ya documentó que los endpoints de depósito/retiro no
  requerían ADR (extensiones de ADR-006/007); consumirlos desde la SPA no
  introduce una decisión arquitectónica nueva.

Las decisiones propias de SPEC-007 (módulo `caja.ts` con `depositar`/`retirar`;
`validarDeposito`/`validarRetiro` con helpers compartidos; hook `useCaja(tipo)`
por formulario; estructura de `CajaSection`; scoping de `GestionPage.test.tsx`)
son de **diseño de detalle** — alternativas evaluadas en §13 — y no
constituyen decisiones arquitectónicas significativas que requieran ADR
(AGENTS.md §16).

---

## 12. Risks

- **Queries duplicadas en tests de `/gestion`:** al montar `CajaSection`,
  "Cliente" (label), "Monto" y "Pérez, Juan" (texto) dejan de ser únicos en
  `GestionPage.test.tsx`. Mitigación prescrita en §8.7: `within` por heading +
  `findAllByText` + reemplazo del proxy "Monto" por las queries distintivas de
  la transferencia. Riesgo de romper tests si el developer "arregla" bajando
  el criterio (prohibido — AGENTS.md §12).
- **Doble `GET /api/v1/clientes` en `/gestion`:** `ClientesSection` y
  `CajaSection` consumen el mismo listado con hooks independientes (sin caché
  — A-006 de SPEC-006). Es una request extra trivial por montaje, consistente
  con el patrón existente; no se introduce estado compartido (scope control —
  AGENTS.md §14).
- **Saldo desactualizado (BR-002 vs ERR-002):** la pre-validación de saldo usa
  el `CuentaDto` conocido; una operación concurrente puede hacerla obsoleta →
  el backend responde `422 SALDO_INSUFICIENTE` y la UI muestra el mensaje del
  envelope e invita a reintentar tras refrescar (AF-002, ERR-002). Sin
  reintento automático (SPEC-005 BR-004).
- **`409 CONFLICTO_CONCURRENCIA`:** mismo tratamiento de mensaje + reintento
  manual (AF-003). El frontend no reintenta solo.
- **`404`/`CUENTA_BLOQUEADA` tras el listado:** la cuenta desaparece del
  selector al refrescar (AF-004); si la operación ya se envió, el backend la
  rechaza con el envelope y la UI lo muestra (ERR-003/ERR-004).
- **Precisión numérica del `monto`:** `Number(monto.trim())` sobre un string
  ya validado por `REGEX_MONTO` (≤ 2 decimales) → sin problemas de float en el
  payload (el backend es la fuente de verdad de los valores — BR-004/BR-005).
- **Confirmación y refresh:** el refresh post-`201` usa `recargar()` de
  `useCuentas` (sin remount) para no perder el panel de confirmación; si un
  developer reusara el truco de `key`/`claveLista` de `CuentasSection`, el
  panel se borraría (ver §13).
- **`fetch` mockeado:** cada test nuevo debe configurar su mock (y el setup
  global limpia `fetch`/`localStorage` — `vitest.setup.ts`); un mock faltante
  (`GET /api/v1/cuentas` tras seleccionar cliente) rechaza con `TypeError` y
  la sección muestra `EstadoError` — verificar que los mocks cubran el flujo
  completo (clientes + cuentas + operación).

---

## 13. Alternatives Considered

- **`useCaja(tipo)` (elegido) vs estado local en la sección (patrón
  `CuentasSection`):** la spec §10 manda explícitamente "hook(s) de mutación
  de la caja siguiendo el patrón de `useTransferencia` ({ enviando,
  confirmacion, error, ... } con protección de doble envío — FR-005)". Además
  hay **dos** formularios independientes: el estado local implicaría duplicar
  por formulario `{enviando, confirmacion, error, enVuelo}` + el try/catch del
  submit (o un estado compuesto más complejo). `CuentasSection` tiene **un**
  formulario de mutación, por eso su estado local es suficiente. Un hook
  parametrizado por `tipo` reutiliza el patrón de `useTransferencia`
  (ref `enVuelo` + `enviando`) sin abstracción adicional. **Elegido
  `useCaja(tipo)`** — una instancia por formulario (AC-018; AC-001 verifica
  que ambos formularios conviven).
- **Dos hooks `useDeposito`/`useRetiro`:** descartado — duplicaría el hook con
  la única diferencia del endpoint; `useCaja(tipo)` es una sola firma.
- **`validarCaja(tipo, ...)` con flag vs `validarDeposito`/`validarRetiro`
  (elegido):** replica la decisión del backend (SPEC-005 §8.2/§13:
  `DepositoRetiroValidator` con dos cadenas públicas y helpers privados, en
  lugar de un flag booleano): cada firma queda explícita, el helper compartido
  `validarMontoCaja` evita la duplicación de BR-001 y los unit tests (AC-010)
  son directos. La variante con flag espeja el mismo problema que el backend
  descartó (orden de chequeos menos legible).
- **Módulo `caja.ts` vs agregar a `cuentas.ts` (A-003):** la spec elige módulo
  nuevo para no mezclar responsabilidades (un-módulo-por-recurso: `clientes`,
  `cuentas`, `transferencias`). **Elegido `caja.ts`.**
- **`depositar`/`retirar` (elegido, spec §10) vs `realizarDeposito`/
  `realizarRetiro`:** la spec fija los nombres de las funciones del módulo
  (`depositar(body)` / `retirar(body)`), consistentes con la convención verbo
  de `src/api/` (`transferir`, `abrirCuenta`); los nombres de los use cases del
  backend no son convención del frontend.
- **Refresh con `recargar()` (elegido) vs remount por `key`/`claveLista`
  (patrón de `CuentasSection`):** en `CuentasSection` el remount refresca
  porque el submit vive en el padre y el hook en el hijo. En `CajaSection` los
  formularios viven **dentro** de `CajaDeCliente` junto a `useCuentas`:
  `recargar()` está disponible directamente y, al no remontar, **preserva el
  panel de confirmación** tras el `201` (FR-005). El remount por `key` solo se
  usa al **cambiar de cliente** (`key={clienteSeleccionado}`), donde sí se
  quieren formularios limpios.
- **Extraer un `CuentaItem` compartido vs duplicarlo en `CajaSection`:
  elegida la duplicación** (~10 líneas): extraer implicaría refactorizar
  `CuentasSection.tsx` y sus tests (fuera de alcance — AGENTS.md §14). Se
  documenta para una posible consolidación futura.
- **Panel de confirmación con CBU del snapshot local (`cuentaOperada`)
  (elegido) vs lookup en el listado refrescado:** tras el refresh la cuenta
  puede dejar de estar `ACTIVA` (AF-004) y desaparecer del listado filtrado; el
  snapshot capturado al submit garantiza el CBU estable del panel (FR-005:
  "la cuenta operada, p. ej. su CBU, disponible en el selector local").
- **Envelope: whitelist de campos (`cuentaId`, `monto`) (elegido, patrón
  `TransferenciaPage`) vs mapear todos los `details` a campos (patrón
  `CuentasSection`):** el backend solo reporta `campo: 'monto'` (ERR-001), pero
  la whitelist con fallback a mensaje general es más robusta ante campos
  desconocidos sin perder el criterio (FR-005: details → por campo; sin
  details → general).

---

## 14. Decision

Implementar SPEC-007 como sección **"Caja"** frontend-only en `/gestion`
(frontend de SPEC-006; sin rutas nuevas, sin cambios de backend/CSS/deps):

- **API:** `src/api/caja.ts` nuevo con `depositar(body: DepositoRequest)` →
  `POST /api/v1/depositos` y `retirar(body: RetiroRequest)` →
  `POST /api/v1/retiros` (A-003); tipos `DepositoRequest`/`RetiroRequest`/
  `DepositoConfirmacion`/`RetiroConfirmacion` en `src/api/types.ts` (espejos
  verificados de los records del backend — §8.2).
- **Validación UX (mirrors):** `validarDeposito(cuentaId, monto)` y
  `validarRetiro(cuentaId, monto, saldoCuenta)` en `src/lib/validacion.ts` con
  helpers privados compartidos (`validarCuentaCaja`, `validarMontoCaja`);
  reutilizan `REGEX_MONTO` y los mensajes de BR-001 (BR-001..BR-003, A-002).
- **Mutación:** `src/hooks/useCaja.ts` — `useCaja(tipo: TipoOperacionCaja)` →
  `{ enviando, confirmacion, error, ejecutar(cuentaId, monto) }`, patrón
  `useTransferencia` con protección de doble envío (FR-005, AC-018); una
  instancia por formulario.
- **Sección:** `src/pages/gestion/CajaSection.tsx` (selector de cliente con
  `useClientes`; `CajaDeCliente` con `useCuentas(clienteId)` — listado con
  saldo/estado + refresh `recargar()` tras `201`; dos `FormularioCaja`
  (depósito/retiro) con cuenta destino solo `ACTIVA` (BR-003), deshabilitados
  sin cuentas (AF-001), confirmación `role="status"` con CBU del snapshot,
  errores del envelope por campo/general con datos conservados (FR-005,
  ERR-001..007)). Montada en `GestionPage.tsx` como tercera sección (FR-001).
- **Autorización:** la sección solo se monta en `/gestion` (`ProtectedRoute`
  ADMIN — UX); el backend permanece como punto de enforcement (BR-005).
- **Tests:** `CajaSection.test.tsx` (AC-001..AC-018 con `mockFetchRespuestas`/
  `mockFetchDiferido`/`mockFetchErrorRed` y helper `seccionDe` para acotar
  formularios), unit de `validarDeposito`/`validarRetiro` en
  `validacion.test.ts` (AC-010) y scoping de `GestionPage.test.tsx` (§8.7: sin
  debilitar criterios). `npm run lint`/`typecheck`/`test`/`build` verdes
  (AC-019); sin dependencias nuevas ni cambios de backend (AC-020).
- **Sin ADRs nuevos** (§11): SPEC-007 reutiliza ADR-008 y los patrones de
  SPEC-005/SPEC-006 sin reinterpretación.

---

## 15. Related Documents

- `docs/specs/SPEC-007-caja-gestion.md` (spec aprobada — fuente de verdad)
- `docs/specs/SPEC-005-depositos-retiros.md` (contrato de caja: FR, BR,
  errores, autorización) y `docs/architecture/SPEC-005.md` (contrato HTTP
  exacto, matchers de `SecurityConfig`, código de errores)
- `docs/specs/SPEC-006-frontend-react.md` y `docs/architecture/SPEC-006.md`
  (patrones de la SPA: capa API §5.2, validación §5.3, hooks §5.3/§5.4, UI
  §5.4, testing §8 — A-005 derogada parcialmente para la caja del `ADMIN`)
- `docs/adr/ADR-008-frontend-react-spa.md` (decisiones del frontend)
- `docs/specs/SPEC-002-cuentas.md` (`CuentaDto`, listado por `clienteId`),
  `docs/specs/SPEC-003-autenticacion.md` (claims `role`/`clienteId`)
- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (envelope de errores), §8
  (seguridad y propiedad)
- `docs/sprints/backlog.md` (E5: US-5.1, US-5.2; issue #26 — caja del `ADMIN`)