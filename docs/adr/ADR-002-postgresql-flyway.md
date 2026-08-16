# ADR-002 — PostgreSQL con migraciones Flyway

## Status

Accepted

---

## Context

El sistema necesita persistencia relacional para clientes, cuentas y
movimientos con integridad transaccional (las transferencias deben ser ACID).

Había que decidir base de datos y cómo gestionar el esquema.

---

## Decision

**PostgreSQL 16** como base de datos, con esquema gestionado por **Flyway**
(migraciones versionadas en `backend/src/main/resources/db/migration/`).

---

## Alternatives

### Alternative A — MySQL

Válida, pero PostgreSQL ofrece mejor soporte para transacciones, `DECIMAL`
preciso y extensiones; es la elección por defecto en banca moderna.
**Descartada** por preferencia técnica.

### Alternative B — H2 en memoria

Útil para arrancar rápido, pero no replica el motor real; los tests de
integración usan Testcontainers con PostgreSQL real. **Descartada** como
persistencia principal.

---

## Consequences

### Positive

- Transacciones ACID (requisito para transferencias consistentes).
- Esquema versionado y reproducible; los agentes agregan migraciones sin
  perder estado.
- Tests de integración contra el mismo motor que producción (Testcontainers).

### Negative

- Requiere un contenedor PostgreSQL disponible en dev/CI.

---

## Related Documents

- `ARCHITECTURE.md` §9
- `docker/docker-compose.yml`
