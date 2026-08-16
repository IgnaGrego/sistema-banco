# Roadmap de Sprints — Sistema Bancario

## Sprint 1 — Base de negocio (clientes y cuentas)

- **Alcance:** SPEC-001, SPEC-002.
- **Objetivo:** montar el backend hexagonal con el dominio base (entidades,
  VOs, puertos, repositorios JPA, migraciones Flyway) y el CRUD de clientes y
  cuentas con su Factory.
- **Criterio de Done:** tests unitarios + integración (Testcontainers) + ArchUnit
  pasando; endpoints de clientes/cuentas operativos.

## Sprint 2 — Autenticación y autorización

- **Alcance:** SPEC-003.
- **Objetivo:** login JWT, BCrypt, RBAC por roles y protección de endpoints.
- **Criterio de Done:** auth integrada en los endpoints del Sprint 1; tests de
  autorización (401/403) pasando.

## Sprint 3 — Operaciones transaccionales

- **Alcance:** SPEC-004, SPEC-005.
- **Objetivo:** transferencias atómicas (optimistic lock), depósitos y retiros,
  movimientos, límite diario y eventos de dominio.
- **Criterio de Done:** flujo de transferencia cubierto por tests de
  integración (incluida concurrencia) y reglas de negocio verificadas.

## Sprint 4 — Frontend React

- **Alcance:** E6.
- **Objetivo:** SPA con login, listado de cuentas, transferencia y gestión
  admin.
- **Criterio de Done:** flujo completo de UI contra la API con JWT.

## Definición de Done (DoD) transversal

Una historia está "done" cuando:

1. La spec está aprobada (`docs/specs/`).
2. El diseño/arquitectura está documentado (`docs/architecture/`, ADRs si aplica).
3. Implementación + tests en la rama `feature/*`.
4. `mvn test` y `mvn verify` pasan.
5. El `reviewer` devolvió `PASS`.
6. El `code-reviewer` devolvió `APPROVE`.
7. La PR fue mergeada a `testing`.
