# ADR-003 — Autenticación JWT con Spring Security (RBAC)

## Status

Accepted

---

## Context

El sistema distingue dos roles (`CLIENTE` y `ADMIN`) con permisos distintos y
debe autenticar usuarios para operar cuentas y transferencias.

Había que decidir el mecanismo de autenticación/autorización.

---

## Decision

**Spring Security** con **JWT** firmado (HS256) y **RBAC** por roles:

- Login emite un JWT de expiración corta.
- Cada request presenta `Authorization: Bearer <JWT>`.
- Los roles se verifican server-side en cada endpoint.
- Passwords almacenadas con **BCrypt**.

---

## Alternatives

### Alternative A — Sesiones de servidor (cookies + HttpSession)

Simple pero acopla el cliente y complica la SPA/API. **Descartada** por el
modelo stateless que pide una SPA + API REST.

### Alternative B — OAuth2 / OpenID Connect con proveedor externo

Muy completo, pero añade infraestructura y complejidad innecesaria para un
portfolio. **Descartada** (posible evolución futura).

---

## Consequences

### Positive

- Stateless: escala horizontal sin estado compartido.
- Integración natural con SPA (Bearer token).
- RBAC explícito y testeable por endpoint.

### Negative

- Gestión propia de tokens (expiración, firma).
- La revocación de tokens requiere cuidado (expiración corta lo mitiga).

---

## Related Documents

- `ARCHITECTURE.md` §8
- `docs/specs/SPEC-003-autenticacion.md`
