# SPEC-003 — Autenticación y Autorización

## Status

Approved

---

## 1. Objective

Permitir a los usuarios autenticarse y obtener un token JWT que los identifica
con un rol (`CLIENTE` o `ADMIN`), y autorizar el acceso a los recursos según
ese rol.

---

## 2. Actors

- **Usuario no autenticado**: se registra o inicia sesión.
- **CLIENTE**: opera los recursos de su propio `Cliente` vinculado (BR-004).
- **ADMIN**: gestiona clientes/cuentas y opera depósitos/retiros.

---

## 3. Preconditions

- **Registro (`POST /api/v1/auth/register`):** `username` no existente
  (BR-003), `password` con al menos 8 caracteres (BR-002) y, si `rol` es
  `CLIENTE`, un `Cliente` existente para vincular (FR-005, SPEC-001).
- **Login (`POST /api/v1/auth/login`):** el usuario existe y la `password`
  coincide con el hash BCrypt almacenado (BR-001).
- **Endpoints protegidos (FR-003):** JWT válido (firma HS256, no expirado) en
  el header `Authorization: Bearer <JWT>`; el token permite resolver el `rol`
  y, para `CLIENTE`, el `clienteId` vinculado.

---

## 4. Functional Requirements

### FR-001

Registrar un usuario con `username`, `password` y `rol`
(`CLIENTE` | `ADMIN`). El registro es público (no requiere token).

### FR-002

Iniciar sesión (`username` + `password`) y devolver un JWT firmado (HS256)
con expiración corta. El JWT identifica al usuario por su `username` (`sub`) e
incluye el `rol` y, para `CLIENTE`, el `clienteId` vinculado (contrato de
claims: ADR-004 §5).

### FR-003

Proteger todos los endpoints salvo registro y login: toda request a un
endpoint protegido debe presentar un JWT válido; la autorización se verifica
server-side.

### FR-004

Resolver el rol desde el token y aplicarlo en la autorización de cada endpoint
(ver §9).

### FR-005

Vincular un usuario `CLIENTE` a un `Cliente` existente para operar sus
recursos. El vínculo se expresa como `clienteId` asociado al usuario: para el
registro de un usuario `CLIENTE` se informa el `clienteId` del `Cliente`
vinculado (ver A-003). Un usuario `CLIENTE` opera únicamente los recursos del
`Cliente` vinculado (BR-004).

---

## 5. Business Rules

### BR-001

La password se almacena solo como hash **BCrypt** (nunca en claro) y nunca se
devuelve en las respuestas.

### BR-002

La password debe tener al menos 8 caracteres.

### BR-003

`username` es único.

### BR-004

Un `CLIENTE` solo puede acceder a los recursos de su propio `Cliente`.

---

## 6. Main Flow — Login

1. El usuario envía `POST /api/v1/auth/login` con `username` y `password`.
2. El sistema verifica las credenciales: usuario existente y `password`
   coincidente con el hash BCrypt (BR-001). Si falla → ERR-001 (`401`).
3. El sistema emite un JWT HS256 de expiración corta con `sub` = `username`,
   `role` y, si es `CLIENTE`, `clienteId` (FR-002).
4. El sistema responde `200 OK` con el token.
5. En cada request posterior, el usuario envía `Authorization: Bearer <JWT>`.
6. El sistema valida el token (firma y expiración — ERR-002) y resuelve
   `rol`/`clienteId` para autorizar el endpoint (FR-003, FR-004; §9).

---

## 7. Alternative Flows

### AF-001 — Registro

1. El usuario envía `POST /api/v1/auth/register` con `username`, `password` y
   `rol` (y `clienteId` si `rol` = `CLIENTE`).
2. El sistema valida: `password` ≥ 8 caracteres (BR-002 — ERR-004), `username`
   no existente (BR-003 — ERR-005) y, para `CLIENTE`, que el `clienteId`
   referencie un `Cliente` existente (FR-005 — ERR-006).
3. El sistema hashea la `password` con BCrypt (BR-001) y persiste el `Usuario`.
4. El sistema responde `201 Created` (sin devolver la password — BR-001).

### AF-002 — CLIENTE sin vínculo

Un usuario `CLIENTE` sin `clienteId` vinculado (o cuyo `clienteId` no
coincide con el recurso consultado) no puede acceder a recursos de clientes:
la verificación de propiedad responde `403` (BR-004, ERR-003). Ver A-003.

---

## 8. Error Cases

Todos los errores usan el envelope JSON estándar
(`{ code, message, details? }` — `ARCHITECTURE.md` §7).

### ERR-001 — Credenciales inválidas

`username` inexistente o `password` incorrecta en login.

Se responde `401 Unauthorized`. La respuesta es idéntica en ambos casos (no se
revela si el problema es el usuario o la password — ver A-004).

### ERR-002 — Token ausente, expirado o mal firmado

Request a un endpoint protegido sin `Authorization: Bearer <JWT>` o con token
ausente, expirado o mal firmado.

Se responde `401 Unauthorized`.

### ERR-003 — Usuario autenticado sin el rol requerido

Token válido pero el rol no tiene permiso para el endpoint, o `CLIENTE`
intentando acceder a un recurso de otro `Cliente` (BR-004).

Se responde `403 Forbidden`.

### ERR-004 — Password menor a 8 caracteres en registro

Se responde `400 Bad Request` (BR-002).

### ERR-005 — Username duplicado en registro

Se responde `409 Conflict` indicando el campo duplicado (BR-003; misma
semántica que SPEC-001 ERR-001).

### ERR-006 — Registro CLIENTE con `clienteId` inexistente

El `Cliente` referenciado no existe (SPEC-001).

Se responde `404 Not Found` (misma semántica que SPEC-001 ERR-004).

### ERR-007 — Registro CLIENTE sin `clienteId`

`rol` = `CLIENTE` sin `clienteId` en el payload de registro (FR-005, A-003).

Se responde `400 Bad Request`.

---

## 9. Authorization

- `POST /api/v1/auth/register`: público (sin token).
- `POST /api/v1/auth/login`: público (sin token).
- Endpoints de clientes existentes (SPEC-001 §9):
  - `POST /api/v1/clientes`, `PUT /api/v1/clientes/{id}` y
    `GET /api/v1/clientes`: solo rol `ADMIN`.
  - `GET /api/v1/clientes/{id}`: `ADMIN` (cualquier cliente) o `CLIENTE`
    (solo el vinculado — BR-004).
- Toda request a un endpoint protegido requiere JWT válido (FR-003); la
  autorización se verifica server-side (nunca solo en el frontend).

---

## 10. Data Changes

- Entidad `Usuario` (id, username, passwordHash, rol, clienteId opcional).
- Migración Flyway: tabla `usuarios` con `username` `UNIQUE NOT NULL`
  (BR-003), `password_hash` `NOT NULL` (hash BCrypt — BR-001), `rol`
  `NOT NULL` (`CLIENTE` | `ADMIN`) y `cliente_id` nullable con `FOREIGN KEY`
  → `clientes(id)` (FR-005; `null` para `ADMIN`).
- La password en claro nunca se persiste ni se expone (BR-001).
- Contrato de claims del JWT: `sub` pasa de `rol` (provisional, Sprint 1) a
  `username` (ADR-004 §5); `role` y `clienteId` (solo `CLIENTE`) se mantienen.

---

## 11. Acceptance Criteria

Registro (`POST /api/v1/auth/register`):

- [ ] AC-001: Con `username`, `password` (≥ 8) y `rol` `CLIENTE` con
      `clienteId` de un `Cliente` existente, responde `201 Created`
      (FR-001, FR-005, AF-001).
- [ ] AC-002: Con `rol` `ADMIN` (sin `clienteId`), responde `201 Created`
      (FR-001).
- [ ] AC-003: Con `password` de menos de 8 caracteres, responde `400`
      (BR-002, ERR-004).
- [ ] AC-004: Con `username` ya existente, responde `409` indicando el campo
      (BR-003, ERR-005).
- [ ] AC-005: Con `rol` `CLIENTE` sin `clienteId`, responde `400`
      (FR-005, ERR-007).
- [ ] AC-006: Con `rol` `CLIENTE` y `clienteId` inexistente, responde `404`
      (FR-005, ERR-006).
- [ ] AC-007: Unit test — el usuario persistido guarda la password como hash
      BCrypt; el hash verifica contra la password original y no es igual a la
      password en claro; la respuesta del registro no contiene la password
      (BR-001).

Login (`POST /api/v1/auth/login`):

- [ ] AC-008: Con credenciales correctas, responde `200` con un JWT válido
      (firma HS256, expiración corta) cuyo `sub` es el `username` y que
      contiene `role` (+ `clienteId` si es `CLIENTE`) (FR-002, FR-004).
- [ ] AC-009: Con `password` incorrecta, responde `401` (ERR-001).
- [ ] AC-010: Con `username` inexistente, responde `401` con la misma
      respuesta que AC-009 (ERR-001, A-004).
- [ ] AC-011: Unit test — la verificación de credenciales usa BCrypt
      (`matches`); nunca compara la password en claro (BR-001).

Autorización en endpoints de clientes existentes (SPEC-001):

- [ ] AC-012: `GET /api/v1/clientes/{id}` sin token responde `401` (FR-003,
      ERR-002).
- [ ] AC-013: `GET /api/v1/clientes/{id}` con token malformado o expirado
      responde `401` (ERR-002).
- [ ] AC-014: Token `CLIENTE` consulta el `Cliente` vinculado y responde `200`
      (FR-004, BR-004; SPEC-001 AF-001).
- [ ] AC-015: Token `CLIENTE` consulta un `Cliente` ajeno y responde `403`
      (BR-004, ERR-003).
- [ ] AC-016: Token `CLIENTE` sobre `POST`/`PUT`/listado de clientes (solo
      `ADMIN`) responde `403` (ERR-003).
- [ ] AC-017: Token `ADMIN` consulta cualquier `Cliente` (`200`) y crea
      clientes (`201`) (FR-004; SPEC-001 §9).

Pruebas y arquitectura:

- [ ] AC-018: Tests unitarios cubren las reglas de registro/login
      (BR-001..BR-003, ERR-001, ERR-004).
- [ ] AC-019: Tests de integración con Testcontainers cubren los endpoints
      `/api/v1/auth` y la autorización `401`/`403` en los endpoints de
      clientes existentes.
- [ ] AC-020: ArchUnit (`mvn verify`) sigue pasando con las nuevas clases:
      `domain` sin dependencias de Spring, `application` dependiendo solo de
      `domain`, y Spring/controllers solo en `infrastructure`.

---

## 12. Out of Scope

- Refresh tokens / rotación de tokens (ver A-001).
- Bloqueo por intentos fallidos.
- OAuth2 / proveedor externo (ver ADR-003).
- Recuperación o cambio de password.
- Frontend (pantallas de login/registro — E6).
- Matrices de autorización específicas de cuentas/movimientos/transferencias/
  depósitos: quedan definidas por sus specs (SPEC-002, SPEC-004, SPEC-005);
  esta spec solo exige la protección genérica por token (FR-003) sobre los
  endpoints existentes.

---

## 13. Dependencies

- SPEC-001 (clientes): para la vinculación `Usuario` ↔ `Cliente` (FR-005,
  ERR-006) y para las reglas de autorización de los endpoints de clientes
  (§9).
- ADR-003 (JWT + Spring Security + BCrypt + RBAC).
- ADR-004 §5 (camino de extensión: emisión en `JwtService`, entidad `Usuario`,
  endpoints `/api/v1/auth`, `sub` = username).

---

## 14. Open Questions

- Ninguna (la ambigüedad del refresh token se resolvió como asunción A-001 en
  modo autónomo; las demás interpretaciones recomendadas quedan documentadas
  como asunciones en §15).

---

## 15. Assumptions

Las siguientes decisiones no estaban definidas explícitamente en la
documentación existente; se documentan como asunciones con la interpretación
recomendada. Si el Product Owner dispone otra cosa, deben ajustarse antes de
la implementación.

- **A-001 — Sin refresh tokens en el MVP:** el login emite un único JWT de
  acceso con expiración corta; no hay refresh tokens ni rotación de tokens en
  este sprint (resuelto en modo autónomo según la recomendación de la draft).
  Al vencer el token, el cliente vuelve a autenticarse. Ver §12.
- **A-002 — Rol en el registro:** el payload de registro incluye el `rol`
  (`CLIENTE` | `ADMIN`) y el registro es público (FR-003). No se restringe
  quién puede crear usuarios `ADMIN`; si el Product Owner quiere restringirlo
  (p. ej., solo `ADMIN` crea `ADMIN`), se ajusta antes de implementar.
- **A-003 — Vínculo `Usuario` ↔ `Cliente`:** el vínculo se expresa como
  `clienteId` en el usuario. Para el registro de un usuario `CLIENTE` se
  informa en el payload y debe referenciar un `Cliente` existente (SPEC-001).
  Un `CLIENTE` sin vínculo no puede acceder a recursos de clientes (`403` —
  BR-004, AF-002).
- **A-004 — Login sin enumeración de usuarios:** ante `username` inexistente o
  `password` incorrecta, la respuesta `401` es idéntica (mismo código y
  mensaje) para no revelar qué dato falló (ERR-001).
- **A-005 — `username` obligatorio:** `username` es obligatorio (no vacío tras
  recortar espacios) y de hasta 50 caracteres; no se definen otras restricciones
  de formato. Recomendado para garantizar la integridad del dato; ajustar si el
  Product Owner define otra regla.

---

## 16. Related Documents

- `ARCHITECTURE.md` §4 (relación `Usuario (1) — (0..1) Cliente`), §7, §8
- `docs/adr/ADR-003-jwt-spring-security.md`
- `docs/adr/ADR-004-autenticacion-minima-jwt-sprint1.md` (§5 — camino de
  extensión para SPEC-003)
- `docs/domain/glosario.md`
- `docs/specs/SPEC-001-clientes.md` (§9, A-001)
- `docs/sprints/backlog.md` (E3), `docs/sprints/roadmap.md` (Sprint 2)
