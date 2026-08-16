# Review Report — SPEC-001

- **Verdict:** PASS
- **Review type:** compliance (reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 2 (revisión inicial + re-verificación tras el fix del bean `JwtTokenFactory` en CI)

## Summary

| Area | Result |
| --- | --- |
| Functional (FR-001..FR-005) | OK |
| Business rules (BR-001..BR-005) | OK |
| Authorization (rol y propiedad) | OK |
| Validation (CoR + VOs) | OK |
| Persistence (Flyway, UNIQUE, adapter) | OK |
| Testing (unit / integración / ArchUnit) | OK |
| Architecture (capas hexagonales, ArchUnit) | OK |
| Scope (sin cambios ajenos) | OK |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor / Nit

- No hay un test que verifique que `details` se *omite* en los envelopes 400/401/403/404/500; el comportamiento se deriva de `spring.jackson.default-property-inclusion: non_null` (configurado en `application.yml` y `application-test.yml`).
- Los caminos defensivos `DniInvalidoException` → 400 y el fallback 500 no tienen test directo (son inalcanzables por el flujo normal de la CoR).
- `JwtServiceTest` hardcodea el literal del secret de test (debe mantenerse sincronizado con `application-test.yml`; drift detectado fallaría ruidosamente, lo cual es deseable).

## Verification

- Checklist A–I completa: dominio (VO `DNI` 7–8 dígitos, `Cliente.crear`/`actualizar`, excepciones), aplicación (flujos de los 4 use cases, CoR en orden obligatorios → longitudes → formatos con cortocircuito, unicidad con exclusión `AndIdNot`), infraestructura (mapeo de excepciones §8.6 del diseño, matchers de seguridad §8.5, `JwtService` solo-valida, envelope `{code, message, details?}`), persistencia (`UNIQUE(dni)`/`UNIQUE(email)`, `findAllByOrderByIdAsc`), tests (52 unit + ArchUnit locales; 25 de integración ejecutados en CI con Docker).
- Local: `mvn -B test` → 81 tests (56 pass, 25 integración omitidos sin Docker, por diseño). CI: job `Backend (build + tests)` PASS con Testcontainers real.
- Documentos: `docs/specs/SPEC-001-clientes.md` (Approved), `docs/architecture/SPEC-001.md`, `docs/adr/ADR-004` presentes y commiteados en la rama.

## Result

**PASS.** Las 3 desviaciones documentadas por el developer se consideran aceptables y correctamente documentadas:

1. `fecha_alta TIMESTAMP WITH TIME ZONE` (en vez de `TIMESTAMP`) — requerido por Hibernate 6 `ddl-auto: validate` con `java.time.Instant`; sin cambio de comportamiento.
2. Regla ArchUnit 2 ajustada para permitir la auto-dependencia `com.banco.application..` — la regla literal fallaría con dependencias intra-capa legítimas; la intención (application no depende de otras capas) se preserva.
3. Mensaje de `ClienteDuplicadoException` por-campo — variación cosmética; el contrato `details[0].campo` se mantiene (ERR-001).
