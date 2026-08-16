# ADR-008 — Frontend React SPA: stack, acceso a la API, sesión y rol client-side (SPEC-006)

## Status

Accepted

---

## Context

El sistema (backend Java 21/Spring Boot 3, hexagonal + DDD — ADR-001) está
completo para las Sprints 1–3 (clientes, cuentas, auth, transferencias,
depósitos/retiros) y expone la API REST `/api/v1` con autenticación Bearer
JWT (ADR-003/004/005). `ARCHITECTURE.md` §1 ya contempla el contenedor
**Frontend SPA** (React 18 / TypeScript / Vite), pero `frontend/` es solo
scaffolding vacío (`.gitkeep`).

SPEC-006 (E6: US-6.1..US-6.4) exige construir la SPA que consume la API
existente. La spec dejó **sin resolver** (resueltas como asunciones A-001..
A-008, ahora a ratificar) cinco decisiones arquitectónicas que no tienen
precedente registrado en el repositorio (los ADR existentes son todos de
backend):

1. **Stack y routing de la SPA.** React 18 + TypeScript + Vite (mandato de
   la spec y del C4), pero el routing con guardas por rol no está definido.
2. **Estrategia de acceso a la API sin CORS.** El backend NO habilita CORS
   (`SecurityConfig`: "Sin CORS (no hay frontend en este sprint)"). Habilitar
   CORS en Spring sería un cambio de backend (fuera de alcance de la spec).
3. **Almacenamiento de la sesión.** El token JWT debe persistirse entre
   recargas; el backend no tiene cookies `httpOnly` ni refresh tokens (solo
   `POST /api/v1/auth/login` → `{token}` con expiración corta).
4. **Detección del rol tras el login.** `LoginResponse` solo devuelve
   `{token}` (sin `rol`). El rol (y `clienteId` para `CLIENTE`) solo existe
   como claim del JWT.
5. **Gestión de estado y alcance.** El alcance son 4 flujos sin estado
   compartido complejo; sin pantallas de registro ni de depósitos/retiros en
   este sprint (las historias US-6.1..6.4 no las requieren).

---

## Decision

1. **Stack: React 18 + TypeScript + Vite bajo `frontend/`, con
   `react-router-dom` v6 para el routing.** SPA con rutas `/login` (pública),
   `/cuentas` (CLIENTE) y `/gestion` (ADMIN), protegidas por un componente
   guard (`ProtectedRoute`) que redirige por rol (A-007). Scripts `lint`,
   `typecheck`, `test` (Vitest + React Testing Library) y `build`.
2. **Acceso a la API: proxy de desarrollo de Vite (`/api` →
   `http://localhost:8080`, sin rewrite) + cliente HTTP propio sobre
   `fetch`.** Desde el navegador todo es same-origin: el backend **no
   habilita CORS** en este sprint. El cliente HTTP adjunta
   `Authorization: Bearer <token>` solo a `/api/v1`, parsea el envelope de
   errores `{ code, message, details? }` en errores tipados y, ante `401` en
   requests autenticadas, limpia la sesión (el login queda excluido de ese
   flujo: su `401` es el error esperado de credenciales).
3. **Sesión: JWT en `localStorage` bajo la clave `banco.token`.** Se
   guarda al iniciar sesión, se restaura al cargar la SPA y se elimina con
   logout o ante `401`. Se acepta el riesgo XSS propio de `localStorage`
   (A-003): el token nunca se adjunta a orígenes fuera de `/api/v1` ni se
   loguea, y la expiración corta del JWT (default 60 min) limita la ventana.
4. **Rol client-side: decodificación del payload del JWT (base64url, sin
   verificación de firma) para UX solamente.** El rol (y `clienteId` para
   `CLIENTE`) se lee del payload para el routing post-login y la visibilidad
   de acciones; **la autorización real se verifica server-side en cada
   endpoint** (firma/expiración en `JwtService.validar` + matchers RBAC +
   propiedad en la capa de aplicación — ADR-003, `ARCHITECTURE.md` §8). Un
   payload no decodificable se trata como sesión inválida.
5. **Estado: React state/context (A-006).** Único contexto compartido: la
   sesión (`AuthProvider` en `src/store/`). Sin Redux/Zustand. Sin pantallas
   de registro (A-004) ni de depósitos/retiros (A-005) en este sprint.

---

## Alternatives

### Alternative A — Habilitar CORS en el backend y llamar a la API cross-origin

`SecurityConfig` agregaría una configuración CORS (allowed origins) y la SPA
llamaría a `http://localhost:8080` directamente. **Descartada:** es un cambio
de backend (fuera de alcance — la spec exige cero cambios en Sprints 1–3),
expone la API a orígenes arbitrarios en dev y agrega configuración sin
beneficio para el desarrollo local, donde el proxy de Vite es la solución
idiomática (misma-origen, sin CORS). Si en producción la SPA y la API se
sirven de orígenes distintos, se evaluará como cambio aprobado por separado.

### Alternative B — Almacenar el token en cookies (`httpOnly`)

Más resistente a XSS. **Descartada:** requiere cambios de backend (endpoints
con cookies, CSRF) y el backend no lo soporta (login → `{token}` únicamente;
stateless — ADR-003). `localStorage` es el mecanismo disponible sin tocar el
backend; el riesgo XSS se mitiga con el alcance del token y la expiración
corta (A-003).

### Alternative C — Agregar `rol` al `LoginResponse` del backend

`AutenticarUsuarioUseCase` devolvería el rol junto al token. **Descartada:**
cambio de contrato del backend (fuera de alcance); la decodificación del JWT
ya provee el claim de forma confiable para UX, y la autorización real no
depende de él (el backend verifica en cada request).

### Alternative D — `axios` como cliente HTTP

Interceptores y tipos integrados. **Descartada:** el wrapper necesario
(Bearer + envelope + 401) es mínimo y `fetch` nativo lo cubre sin
dependencias (AGENTS.md §13).

### Alternative E — Redux/Zustand para el estado global

**Descartada:** alcance de 4 flujos sin estado compartido complejo; el
contexto de sesión + estado local por página es la solución más simple
(A-006; AGENTS.md §11).

---

## Consequences

### Positive

- **Frontend-only:** cero cambios en backend/BD; el contrato de la API y la
  autorización existentes (Sprints 1–3) se consumen tal cual.
- **Sin CORS en el backend:** el proxy de Vite mantiene same-origin en dev;
  no se debilita la postura de seguridad de la API.
- **Decisiones de UX desacopladas de la seguridad:** el rol client-side solo
  afecta routing/visibilidad; la API sigue siendo el punto de enforcement
  (BR-008, AGENTS.md §17).
- **Dependencias mínimas y justificadas:** fetch + contexto + React Router;
  sin axios, Redux/Zustand, MUI ni MSW.
- **Sesión restaurable y expirable:** recarga de la SPA conserva la sesión;
  el 401 produce el logout automático por expiración del JWT (sin refresh
  tokens — SPEC-003 A-001).

### Negative

- **XSS puede leer el token de `localStorage`** (riesgo aceptado para el MVP,
  A-003). Mitigaciones: token solo hacia `/api/v1`, sin logs, expiración
  corta; cookies `httpOnly` quedan como evolución que requeriría cambios de
  backend.
- **El rol decodificado sin verificar firma es una verdad de UX, no de
  seguridad:** un token falsificado podría engañar al guard de rutas, pero
  jamás a la API (401/403). Se documenta para evitar que el frontend se
  convierta en punto de enforcement en el futuro.
- **El proxy de Vite es solo de desarrollo:** la integración en producción
  (misma-origen o CORS) queda pendiente y requerirá una decisión aparte.
- **`localStorage` no es compartible entre pestañas/dominios** y no tiene
  expiración propia: la sesión "vive" hasta el 401 o el logout (comportamiento
  aceptado del MVP).

---

## Related Documents

- `docs/specs/SPEC-006-frontend-react.md` (asunciones A-001..A-008)
- `docs/architecture/SPEC-006.md` (diseño detallado de la SPA)
- `docs/adr/ADR-001-monolito-hexagonal-ddd.md` (backend que la SPA consume)
- `docs/adr/ADR-003-jwt-spring-security.md`, `ADR-004-autenticacion-minima-jwt-sprint1.md`,
  `ADR-005-emision-tokens-y-password-bcrypt-spec-003.md` (JWT, claims
  `role`/`clienteId`, login `{token}`)
- `ARCHITECTURE.md` §1 (contenedor SPA), §7 (API REST y envelope), §8
  (seguridad)
