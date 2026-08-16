# SPEC-003 — Autenticación y Autorización

## Status

Draft

---

## 1. Objective

Permitir a los usuarios autenticarse y obtener un token JWT que los identifica
con un rol (`CLIENTE` o `ADMIN`), y autorizar el acceso a los recursos según
ese rol.

---

## 2. Actors

- **Usuario no autenticado**: se registra o inicia sesión.
- **CLIENTE**: opera sus propias cuentas.
- **ADMIN**: gestiona clientes/cuentas y opera depósitos/retiros.

---

## 3. Preconditions

- Para login/registro: credenciales válidas.

---

## 4. Functional Requirements

### FR-001

Registrar un usuario con `username`, `password` y `rol`.

### FR-002

Iniciar sesión y devolver un JWT firmado (HS256) con expiración corta.

### FR-003

Proteger todos los endpoints salvo registro y login.

### FR-004

Resolver el rol desde el token y aplicarlo en la autorización de cada endpoint.

### FR-005

Vincular un usuario `CLIENTE` a un `Cliente` (para operar sus cuentas).

---

## 5. Business Rules

### BR-001

La password se almacena solo como hash **BCrypt** (nunca en claro).

### BR-002

La password debe tener al menos 8 caracteres.

### BR-003

`username` es único.

### BR-004

Un `CLIENTE` solo puede acceder a los recursos de su propio `Cliente`.

---

## 6. Main Flow

1. El usuario envía `username` y `password`.
2. El sistema verifica credenciales (BCrypt).
3. El sistema emite un JWT con `sub` (username) y `role`.
4. El usuario envía el token en `Authorization: Bearer <JWT>`.

---

## 7. Alternative Flows

### AF-001

Registro de nuevo usuario: el sistema valida unicidad de `username` y fuerza
de la password, hashea y persiste.

---

## 8. Error Cases

### ERR-001

Credenciales inválidas.

Se responde `401 Unauthorized`.

### ERR-002

Token ausente, expirado o mal firmado.

Se responde `401 Unauthorized`.

### ERR-003

Usuario autenticado sin el rol requerido.

Se responde `403 Forbidden`.

### ERR-004

Password menor a 8 caracteres en registro.

Se responde `400 Bad Request`.

---

## 9. Authorization

- Registro/login: público.
- Resto de endpoints: requieren JWT válido.
- Endpoints de `ADMIN`: requieren rol `ADMIN`.

---

## 10. Data Changes

- Entidad `Usuario` (id, username, passwordHash, rol, clienteId opcional).
- Migración Flyway correspondiente.

---

## 11. Acceptance Criteria

- [ ] Login correcto devuelve un JWT válido.
- [ ] Login con credenciales incorrectas devuelve `401`.
- [ ] Acceder a un endpoint protegido sin token devuelve `401`.
- [ ] Un token de `CLIENTE` no accede a endpoints de `ADMIN` (`403`).
- [ ] La password nunca se devuelve ni se guarda en claro.

---

## 12. Out of Scope

- Refresh tokens / rotación de tokens.
- Bloqueo por intentos fallidos.
- OAuth2 / proveedor externo (ver ADR-003).

---

## 13. Dependencies

- SPEC-001 (clientes) para la vinculación `Usuario` ↔ `Cliente`.

---

## 14. Open Questions

- ¿Refresh token en el MVP? (recomendado: no; se agrega después si hace falta).

---

## 15. Related Documents

- `docs/adr/ADR-003-jwt-spring-security.md`
- `ARCHITECTURE.md` §8
