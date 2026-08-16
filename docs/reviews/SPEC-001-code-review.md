# Review Report — SPEC-001

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 2 (revisión inicial + re-revisión del fix del bean `JwtTokenFactory` y mejoras de calidad)

## Summary

| Area | Result |
| --- | --- |
| Readability & maintainability | OK |
| Security | OK |
| Performance | OK |
| Conventions (hexagonal, DTOs record, envelope) | OK |
| Test quality | OK |
| Dead code / duplication / error handling | OK (nits resueltos en re-revisión) |
| pom.xml (dependencias mínimas y justificadas) | OK |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **Resuelto en re-revisión:** `docs/architecture/SPEC-001.md` y `docs/adr/ADR-004` describían el bean `JwtTokenFactory` como `@TestConfiguration` en `BaseIntegrationTest`, patrón que falló en CI (Spring Boot solo auto-registra `@TestConfiguration` anidadas en la clase de test EJECUTADA). Los documentos fueron sincronizados con el patrón implementado (config anidada en `ClienteApiIntegrationTest`).
- **Nit (no bloqueante):** `ClienteDuplicadoException.valor`/`getValor()` (`domain/exception/ClienteDuplicadoException.java`) no se consumen (el envelope usa `campo` + mensaje). Se puede eliminar o exponer `valor` en `details` en un sprint futuro.

### Nice-to-have (no bloqueante)

- `JwtServiceTest` duplica el literal del secret de test; considerar una constante compartida o lectura desde properties para evitar drift silencioso.
- El patrón `TokenConfig` por-clase-concreta se repetirá al agregar más tests de integración; una `@TestConfiguration` top-level en `com.banco.support` importada vía `@Import` sería más DRY.
- El handler catch-all loguea ahora la causa (ERROR/WARN) sin filtrar nada al cliente; el envelope de error se mantiene idéntico.

## Verification

- Revisados ~50 archivos (30 main, 10 test, pom.xml, `application.yml`, `application-test.yml`, migración `V1__schema_inicial.sql`, `JwtTokenFactory`), más docs (SPEC-001, arquitectura, ADR-001..004) y el workflow de CI.
- Verificados: capas hexagonales respetadas (`domain`/`application` sin Spring, verificado por ArchUnit), `JwtService` solo-valida (sin emisión en producción), 401/403 con envelope, `Number → longValue` defensivo para el claim `clienteId`, secret ≥ 32 bytes, sin secretos reales commiteados.
- Local: `mvn -B test` → 81 tests (56 pass, 25 integración omitidos sin Docker). CI: `Backend (build + tests)` PASS (Testcontainers real).

## Result

**APPROVE.** El gate de merge queda autorizado: la implementación es de calidad, sin defectos bloqueantes ni mayores, con 3 desviaciones documentadas razonables (TIMESTAMPTZ para Hibernate `validate`, auto-dependencia en la regla ArchUnit 2, mensaje por-campo).
