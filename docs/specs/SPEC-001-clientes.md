# SPEC-001 — Gestión de Clientes

## Status

Approved

---

## 1. Objective

Permitir el alta, consulta, actualización y listado de clientes del banco.
Un cliente es la persona titular de una o más cuentas (ver glosario).

---

## 2. Actors

- **ADMIN**: crea, consulta, actualiza y lista clientes.
- **CLIENTE**: consulta su propio perfil.

---

## 3. Preconditions

- El usuario debe estar autenticado con un JWT válido (infraestructura mínima
  de Sprint 1 — ver A-001).
- Para alta/edición/listado, el token debe tener rol `ADMIN`.
- Para consulta como `CLIENTE`, el token debe permitir resolver el `Cliente`
  asociado (claim `clienteId` — ver A-001).

---

## 4. Functional Requirements

### FR-001 — Alta

`POST /api/v1/clientes` — crea un cliente con: `nombre`, `apellido`, `dni`,
`email` y `telefono` (opcional). Responde `201 Created` con la representación
del cliente, incluido su `id`.

### FR-002 — Consulta

`GET /api/v1/clientes/{id}` — devuelve la representación del cliente
(`200 OK`). `ADMIN` consulta cualquier cliente; `CLIENTE` solo el propio.

### FR-003 — Edición

`PUT /api/v1/clientes/{id}` — actualiza `nombre`, `apellido`, `dni`, `email`
y `telefono` (ver A-004). Responde `200 OK` con la representación actualizada.
`id` y `fechaAlta` no son editables.

### FR-004 — Listado

`GET /api/v1/clientes` — devuelve la lista completa de clientes (`200 OK`),
ordenada por `id` ascendente, sin paginación en Sprint 1 (ver A-003).
Solo `ADMIN`.

### FR-005 — Fecha de alta

El sistema registra `fechaAlta` automáticamente al crear el cliente; no es
editable.

---

## 5. Business Rules

### BR-001

El `DNI` es único: no pueden existir dos clientes con el mismo DNI. La
unicidad se verifica en el alta y en la edición (excluyendo al propio
cliente) y se refuerza con constraint `UNIQUE` en la base de datos.

### BR-002

`DNI` debe contener solo dígitos y tener entre 7 y 8 caracteres.

### BR-003

`email` debe tener formato válido, no superar 254 caracteres y ser único
(misma exclusión de "propio cliente" que BR-001 en la edición).

### BR-004

`nombre` y `apellido` son obligatorios, no vacíos (luego de recortar espacios)
y de hasta 100 caracteres cada uno.

### BR-005

`telefono` es opcional (ver A-002). Si se informa, debe cumplir
`^\+?[0-9]{6,15}$` (dígitos, con `+` inicial opcional).

---

## 6. Main Flow

1. `ADMIN` envía `POST /api/v1/clientes` con `nombre`, `apellido`, `dni`,
   `email` y opcionalmente `telefono`.
2. El sistema valida los datos (VOs `DNI`, email, campos obligatorios,
   formatos y longitudes).
3. El sistema verifica unicidad de `dni` y `email`.
4. El sistema persiste el cliente con `fechaAlta` automática.
5. El sistema responde `201 Created` con la representación del cliente
   (incluido `id`).

---

## 7. Alternative Flows

### AF-001

`CLIENTE` consulta su perfil (`GET /api/v1/clientes/{id}` con su propio id):
el sistema devuelve solo el cliente vinculado a su token (claim `clienteId`).
Consultar cualquier otro id responde `403` (ERR-003).

---

## 8. Error Cases

Todos los errores usan el envelope JSON estándar
(`{ code, message, details? }` — ver `ARCHITECTURE.md` §7).

### ERR-001 — Conflicto de unicidad

`dni` o `email` duplicados (en alta o edición, de otro cliente).

Se responde `409 Conflict` indicando el campo duplicado.

### ERR-002 — Datos inválidos

`dni` no numérico o con longitud fuera de 7–8, `email` malformado o mayor a
254 caracteres, `nombre`/`apellido` vacíos o mayores a 100 caracteres,
`telefono` malformado.

Se responde `400 Bad Request` con el detalle del/los campo(s).

### ERR-003 — Acceso no autorizado (rol o propiedad)

`CLIENTE` intenta consultar un cliente ajeno, o cualquier usuario sin rol
`ADMIN` intenta crear, editar o listar clientes.

Se responde `403 Forbidden`.

### ERR-004 — Cliente inexistente

`GET` o `PUT` sobre `/api/v1/clientes/{id}` con un `id` que no existe.

Se responde `404 Not Found`.

### ERR-005 — Token ausente o inválido

Request sin `Authorization: Bearer <JWT>` o con token malformado/expirado.

Se responde `401 Unauthorized`.

---

## 9. Authorization

- Alta (`POST`), edición (`PUT`) y listado (`GET /api/v1/clientes`): solo
  rol `ADMIN`.
- Consulta (`GET /api/v1/clientes/{id}`): `ADMIN` (cualquier cliente) o
  `CLIENTE` (solo el propio).
- Toda request requiere JWT válido; la autorización se verifica server-side.

**Enfoque para Sprint 1 (A-001):** SPEC-003 (registro/login/gestión de
usuarios) NO está implementada en este sprint. Se implementa la
infraestructura mínima de Spring Security + JWT necesaria para proteger
`/api/v1/clientes` (validación del token HS256, resolución de rol y de
`clienteId`) y un mecanismo para emitir tokens en tests de integración. No se
exponen endpoints `/api/v1/auth`; la gestión completa de usuarios y la
vinculación `Usuario`↔`Cliente` quedan para SPEC-003 (Sprint 2).

---

## 10. Data Changes

- Entidad `Cliente` (id, nombre, apellido, dni, email, telefono, fechaAlta).
- Migración Flyway: tabla `clientes` con `UNIQUE (dni)` y `UNIQUE (email)`.

---

## 11. Acceptance Criteria

Alta (`POST /api/v1/clientes`):

- [ ] AC-001: Con token `ADMIN` y datos válidos, responde `201 Created` y
      devuelve el cliente con `id` asignado y `fechaAlta` seteada.
- [ ] AC-002: `dni` duplicado responde `409` indicando el campo (BR-001).
- [ ] AC-003: `email` duplicado responde `409` indicando el campo (BR-003).
- [ ] AC-004: `dni` no numérico o con longitud distinta de 7–8 responde
      `400` (BR-002).
- [ ] AC-005: `email` malformado responde `400` (BR-003).
- [ ] AC-006: `nombre` o `apellido` vacíos responde `400` (BR-004).
- [ ] AC-007: Alta sin `telefono` es válida (BR-005, A-002).
- [ ] AC-008: Sin token responde `401` (ERR-005).
- [ ] AC-009: Con token `CLIENTE` responde `403` (solo `ADMIN` crea).

Consulta (`GET /api/v1/clientes/{id}`):

- [ ] AC-010: `ADMIN` consulta cualquier cliente y recibe `200`.
- [ ] AC-011: `CLIENTE` consulta su propio id y recibe `200` (AF-001).
- [ ] AC-012: `CLIENTE` consulta un id ajeno y recibe `403` (ERR-003).
- [ ] AC-013: Consultar un id inexistente responde `404` (ERR-004).

Edición (`PUT /api/v1/clientes/{id}`):

- [ ] AC-014: `ADMIN` edita `nombre`/`apellido`/`dni`/`email`/`telefono` con
      datos válidos y recibe `200` con la representación actualizada (A-004).
- [ ] AC-015: Editar un id inexistente responde `404` (ERR-004).
- [ ] AC-016: Edición que produce `dni` duplicado (de otro cliente) responde
      `409` (BR-001).
- [ ] AC-017: Edición que produce `email` duplicado (de otro cliente)
      responde `409` (BR-003).
- [ ] AC-018: Edición que mantiene el propio `dni`/`email` del cliente es
      válida (la unicidad se evalúa excluyendo al propio cliente).
- [ ] AC-019: Edición con token `CLIENTE` responde `403`.

Listado (`GET /api/v1/clientes`):

- [ ] AC-020: Con token `ADMIN` responde `200` con la lista completa ordenada
      por `id` ascendente, sin paginación (A-003).
- [ ] AC-021: Con token `CLIENTE` responde `403`.
- [ ] AC-022: Sin token responde `401`.

Pruebas y arquitectura:

- [ ] AC-023: Tests unitarios cubren las validaciones (VOs y reglas de
      negocio) y los casos de error.
- [ ] AC-024: Tests de integración con Testcontainers cubren persistencia y
      endpoints REST, incluidos los códigos 400/401/403/404/409.
- [ ] AC-025: ArchUnit (`mvn verify`) verifica las reglas de dependencia de
      capas (domain sin dependencias de Spring; infrastructure como única
      capa con Spring/controllers).

---

## 12. Out of Scope

- Registro/login, gestión de usuarios y endpoints `/api/v1/auth` (SPEC-003,
  Sprint 2).
- Vinculación automática `Usuario`↔`Cliente` (SPEC-003 FR-005).
- Paginación, filtros/búsqueda y ordenamiento configurable en el listado.
- Eliminación de clientes (borrado físico o lógico).
- Estados de cliente / edición masiva.
- Frontend (E6).
- Modificación de `id` o `fechaAlta`.

---

## 13. Dependencies

- SPEC-003 (autenticación): en Sprint 1 solo se implementa la parte mínima
  necesaria para proteger los endpoints (ver A-001); el resto se completa en
  Sprint 2.
- ADR-003 (JWT + Spring Security): define el mecanismo de autenticación.

---

## 14. Open Questions

- Ninguna (las ambigüedades detectadas se resolvieron como asunciones — §15).

---

## 15. Assumptions

Las siguientes decisiones no estaban definidas explícitamente en la
documentación existente; se documentan como asunciones con la interpretación
recomendada. Si el Product Owner dispone otra cosa, deben ajustarse antes de
la implementación.

- **A-001 — Autorización en Sprint 1:** SPEC-003 no está implementada en
  Sprint 1, pero los criterios de aceptación exigen 401/403. Se implementa la
  infraestructura mínima de JWT + Spring Security (validación de token HS256,
  claims `role` y `clienteId`) para proteger `/api/v1/clientes`, más un
  mecanismo de emisión de tokens para tests de integración. Registro/login y
  gestión de usuarios quedan fuera de alcance (SPEC-003, Sprint 2). El claim
  `clienteId` (solo tokens `CLIENTE`) permite resolver el cliente propio para
  la consulta (AF-001) sin depender de la entidad `Usuario`.
- **A-002 — `telefono` opcional:** el teléfono no es obligatorio en alta ni en
  edición. Si se informa, debe cumplir `^\+?[0-9]{6,15}$`.
- **A-003 — Listado sin paginación:** en Sprint 1, `GET /api/v1/clientes`
  devuelve la lista completa, ordenada por `id` ascendente. La paginación y el
  ordenamiento configurable quedan fuera de alcance.
- **A-004 — Edición como reemplazo total (PUT):** la edición recibe todos los
  campos de negocio (`nombre`, `apellido`, `dni`, `email`, `telefono`) y se
  revalidan las mismas reglas que en el alta (formatos, longitudes y unicidad
  de `dni`/`email` excluyendo al propio cliente). `id` y `fechaAlta` no son
  editables.
- **A-005 — Longitudes máximas:** no existían límites previos; se fijan como
  valores recomendados: `nombre`/`apellido` ≤ 100 caracteres y `email` ≤ 254
  caracteres, consistentes con BR-002 (DNI de 7–8 dígitos).

---

## 16. Related Documents

- `ARCHITECTURE.md` §2, §3, §7, §8
- `docs/domain/glosario.md`
- `docs/adr/ADR-003-jwt-spring-security.md`
- `docs/specs/SPEC-003-autenticacion.md`
- `docs/sprints/backlog.md` (E1)
- `docs/sprints/roadmap.md` (Sprint 1)
