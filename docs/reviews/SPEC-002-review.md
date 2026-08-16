# Review Report — SPEC-002

- **Verdict:** PASS
- **Review type:** compliance (reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 1 (revisión estática completa + CI `mvn -B verify` verde en HEAD)

## Summary

| Area | Result |
| --- | --- |
| Functional (FR-001..FR-008) | OK |
| Business rules (BR-001..BR-006) | OK |
| Authorization (rol y propiedad) | OK |
| Validation (AperturaValidator + VOs) | OK |
| CBU (formato 22 dígitos, unicidad, regeneración) | OK |
| Persistence (V3__cuentas.sql ↔ JPA, ddl-auto: validate) | OK |
| Security / SecurityConfig (matchers §8.4) | OK |
| Error mapping (GlobalExceptionHandler §8.5) | OK |
| Testing (unit / integración Testcontainers / ArchUnit) | OK |
| Architecture (capas hexagonales, ArchUnit) | OK |
| Scope (sin cambios ajenos) | OK |
| CI gate (`mvn -B verify`) | OK — verde en HEAD (PR #20) |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor / Nit

- **`created_at TIMESTAMP WITH TIME ZONE` vs `TIMESTAMP` de la spec §10:** desviación documentada (spec §10, architecture §6.1 y comentario del SQL) para que `ddl-auto: validate` valide contra `java.time.Instant` (Hibernate 6 mapea a timestamptz; lección de V1). Consistente con el patrón de `fecha_alta` de V1. No se considera violación.
- **Orden 404-antes-que-403 en consulta por id/cbu:** el `id`/`cbu` no es el `clienteId`, la propiedad solo se conoce tras cargar la cuenta (architecture §4/§13). Un `CLIENTE` con claim ajeno que consulta un id inexistente recibe `404`, no `403`. Comportamiento documentado y cubierto por `ObtenerCuentaUseCaseTest` (orden 404→403). Diferente a `ObtenerClienteUseCase` por la naturaleza del id; aceptado.
- **Inicialización de `version` en `0L` en `CuentaFactory` (no null):** decisión de implementación documentada (determinismo del INSERT, coherente con `DEFAULT 0` de la migración); consistente en factory/dominio/adapter/tests. No afecta el comportamiento del optimistic lock.
- **`MoneyInvalidoException` sin mapeo:** invariante interno del VO inalcanzable desde entradas de usuario en este sprint (solo se construye `Money` con 0); cae en el fallback 500. Documentado en architecture §8.5.

## Verification

Checklist completa (revisión estática de todos los archivos nuevos/modificados; sin Java/Maven/Docker en el sandbox, por lo que `mvn test`/`mvn verify` no se pudieron ejecutar localmente; el estado de CI se verificó vía GitHub API — ver más abajo).

1. **FR-001 / AC-001..AC-007 — Apertura** — OK. `POST /api/v1/cuentas` → `201` + `Location` (solo ADMIN vía matcher §8.4). `AbrirCuentaUseCase`: `AperturaValidator` → `400` (tipo inválido / formato de moneda); default `ARS` (FR-008); `MonedaNoSoportadaException` para no-ARS → `422` (AC-003); `ClienteRepository.findById` → `ClienteNoEncontradoException` → `404` (AC-005); sin token → `401` (AC-006); token CLIENTE → `403` (AC-007). Tests: `CuentaApiIntegrationTest` AC-001..AC-007 + `AbrirCuentaUseCaseTest`/`AperturaValidatorTest`.
2. **FR-002 / BR-001 / AC-028 — CBU único auto-generado** — OK. VO `CBU` valida exactamente 22 dígitos (`CBUTest` AC-025). Generación en aplicación (SecureRandom, `BANCO="00000001"` + 14 dígitos), `existsByCbu` con regeneración (tope 5) — `AbrirCuentaUseCaseTest` con `thenReturn(true, false)` + `ArgumentCaptor` (AC-028); `UNIQUE (cbu)` en V3 como backstop → 409 (AC-029 vía JdbcTemplate, `DataIntegrityViolationException`).
3. **FR-003 / BR-002 / AC-026 — Saldo inicial 0, nunca negativo** — OK. `Money` VO rechaza montos negativos (`MoneyTest`); factory construye `Money.cero(moneda)`; `saldo NUMERIC(19,2) DEFAULT 0`.
4. **FR-004 / FR-005 / AC-008..AC-017 — Consulta por id y por cbu** — OK. `ObtenerCuentaUseCase` (404 → propiedad 403) y `ObtenerCuentaPorCbuUseCase` (CBU malformado → 400 `CBU_INVALIDO`; 404; propiedad 403). Admin consulta cualquiera (AC-008, AC-013); CLIENTE solo propias (AC-009, AC-014); ajena → 403 (AC-010, AC-015); inexistente → 404 (AC-011, AC-016); cbu malformado → 400 (AC-017); id no numérico → 400 (AC-012). Tests unit + integración.
5. **FR-006 / AF-001 / A-004 / AC-018..AC-023 — Listado por cliente** — OK. `ListarCuentasUseCase`: CLIENTE con claim → `findByClienteId(claim)` ordenado por id asc (AC-018); CLIENTE con claim null → 403 (AC-023); CLIENTE con parámetro `clienteId` → 403 (AC-019); ADMIN con filtro → verifica `ClienteRepository.findById` (inexistente → 404, AC-021) y filtra (AC-020); ADMIN sin filtro → `findAll` ordenado (AC-022).
6. **FR-007 / BR-003 / A-001 / A-003 / AC-027 — Estado y guarda** — OK. `bloquear()` transiciona `ACTIVA → BLOQUEADA`; `verificarActiva()` lanza `CuentaBloqueadaException` ante cualquier operación sobre `BLOQUEADA` (incluido re-`bloquear()`) — `CuentaTest`; mapeo a `422 CUENTA_BLOQUEADA` en `GlobalExceptionHandler` verificado por `GlobalExceptionHandlerTest` (reflexión sobre `@ResponseStatus`) y unit de dominio. Sin endpoint REST de bloqueo (out of scope).
7. **FR-008 / BR-005 / A-005 / AC-003 — Moneda** — OK. `Moneda` record `^[A-Z]{3}$` (`MonedaInvalidaException` defensivo → 400, mapeado); soporte ARS-only en el use case (`MonedaNoSoportadaException` → 422). AC-003 en integración.
8. **BR-004 / AC-024 — Factory por tipo** — OK. `CuentaFactory.crear(clienteId, tipo, cbu, moneda, createdAt)` con dispatch por `switch`; ambas ramas: saldo 0, estado ACTIVA; `tipo` null → `IllegalArgumentException` (defensivo). `CuentaFactoryTest` para `CAJA_AHORRO` y `CUENTA_CORRIENTE`.
9. **BR-006 — CLIENTE solo propias** — OK. Propiedad en capa de aplicación (use cases), nunca en matchers/controllers; verificación por claim `clienteId` (SPEC-003).
10. **ERR-001..ERR-010 — Mapeo de errores** — OK. Tabla §8.5 implementada en `GlobalExceptionHandler`: `CbuInvalidoException` → 400 `CBU_INVALIDO` + `details[{campo:"cbu"}]`; `MonedaInvalidaException` → 400 `DATOS_INVALIDOS` + campo moneda; `CuentaNoEncontradaException` → 404 `CUENTA_NO_ENCONTRADA`; `CuentaBloqueadaException` → 422 `CUENTA_BLOQUEADA`; `MonedaNoSoportadaException` → 422 `MONEDA_NO_SOPORTADA`; `ERR-010` reutiliza el mapeo existente de `DataIntegrityViolationException` → 409. Envelope `{code, message, details?}` con `DetalleError{campo, mensaje}`. 401/403 de Spring Security intactos (entry point / access-denied).
11. **SecurityConfig (§8.4)** — OK. Matchers insertados entre los de clientes y `anyRequest`, en el orden crítico: `POST /api/v1/cuentas` → ADMIN; `GET /api/v1/cuentas` (exacto) → ADMIN|CLIENTE; `GET /api/v1/cuentas/cbu/**` → ADMIN|CLIENTE; `GET /api/v1/cuentas/**` → ADMIN|CLIENTE (propiedad en el use case). Matchers de clientes/auth intactos.
12. **Persistencia (`ddl-auto: validate`)** — OK. `CuentaJpaEntity` ↔ `V3__cuentas.sql` coherentes: `BIGSERIAL` ↔ `Long @Id IDENTITY`; `VARCHAR(22)` ↔ `@Column(length=22)`; `VARCHAR(20)` ↔ `@Column(length=20)` (tipo/estado); `NUMERIC(19,2)` ↔ `@Column(precision=19, scale=2)`; `VARCHAR(3)` ↔ `@Column(length=3)`; `version BIGINT` ↔ `@Version Long`; `created_at TIMESTAMPTZ` ↔ `Instant`. Enums como `String` (`.name()`/`valueOf`) sin `AttributeConverter` (convención SPEC-001). Constraint `UNIQUE (cbu)` (`uq_cuentas_cbu`) y FK `cliente_id → clientes(id)` definidos por Flyway. Índice `idx_cuentas_cliente_id` para FR-006.
13. **Layering / ArchUnit (AC-030)** — OK. `domain` sin imports de Spring/Jakarta/otras capas; `application` importa solo `com.banco.application..`, `com.banco.domain..` y `java..` (SecureRandom es `java.security`); Spring/controllers solo en `infrastructure`. `LayerArchitectureTest` sin cambios (4 reglas existentes, no debilitadas). Verificado import por import en las 14 clases de domain y 9 de application.
14. **Test coverage (AGENTS.md §12, spec §11)** — OK. Unit: `CBUTest`, `MoneyTest`, `MonedaTest`, `CuentaTest`, `CuentaFactoryTest`, `AperturaValidatorTest`, `AbrirCuentaUseCaseTest`, `ObtenerCuentaUseCaseTest`, `ObtenerCuentaPorCbuUseCaseTest`, `ListarCuentasUseCaseTest`, `GlobalExceptionHandlerTest`. Integración Testcontainers: `CuentaApiIntegrationTest` (AC-001..AC-023 + persistencia entre requests + constraints UNIQUE/FK vía JdbcTemplate + mapeo 409 del handler). ArchUnit: `LayerArchitectureTest` (AC-030). CI verde: `mvn -B verify` → BUILD SUCCESS (jobs de push y pull_request de PR #20), unit + integración Testcontainers (Postgres real) + ArchUnit.
15. **Scope** — OK. `git diff origin/testing...feature/spec-002 --stat`: 47 archivos, todos de SPEC-002 (2 docs, 24 main, 12 test, 1 migración, 2 modificados SecurityConfig/GlobalExceptionHandler); sin cambios en clientes/auth, `pom.xml`, `application.yml`, frontend o docker; sin dependencias nuevas; sin ADR nuevo (justificado en architecture §11). Trabajo previo de SPEC-004 preservado en `feature/spec-004` (fuera de esta rama).

## Result

**PASS.** La implementación de SPEC-002 cumple la especificación aprobada (`docs/specs/SPEC-002-cuentas.md`) y el diseño (`docs/architecture/SPEC-002.md`) en todos los puntos verificados: 30/30 criterios de aceptación cubiertos por tests reales que asertan los valores correctos (códigos HTTP, códigos del envelope, `details` por campo, ordenamientos y violaciones de constraint). Las desviaciones documentadas (TIMESTAMPTZ, 404-antes-que-403, version 0L) son consistentes con el diseño y están documentadas. CI (`mvn -B verify`) verde en HEAD.
