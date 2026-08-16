# Review Report — SPEC-003

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 2 (incluye el fix `697658d` del stub de Mockito por identidad en `AutenticarUsuarioUseCaseTest`, ya revisado)

## Summary

| Area | Result |
| --- | --- |
| Readability & maintainability | OK — nombres claros, métodos pequeños, javadoc útil con referencias a spec/ADR |
| Security | OK — BCrypt vía puerto (nunca en claro, nunca en respuestas), JWT HS256 con secret ≥ 32 bytes, contrato de claims alineado, 401 idéntico (A-004); 1 hallazgo minor de edge case (límite de 72 bytes de BCrypt) |
| Performance | OK — sin N+1, BCrypt cost default, claims pequeños |
| Conventions (hexagonal, records, envelope, Spanish) | OK — ArchUnit verde en CI, domain/application sin Spring, wiring vía `AuthBeansConfig` |
| Test quality | OK — unit tests significativos, integración cubre AC-001..AC-020 (happy path, validación, autorización, errores) |
| Dead code / duplication / error handling | OK — mapeo de `GlobalExceptionHandler` completo; duplicación `emitir`/`JwtTokenFactory` documentada y alineada (ADR-005) |
| Scope (pom/yml intactos, cambios mecánicos) | OK |
| CI gate (`mvn -B verify`) | OK — verde en HEAD: BUILD SUCCESS, 139 tests (0 failures/errors/skipped) |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **Límite de 72 bytes de BCrypt sin validar (edge case → 500 en vez de 400/401).**
  - Ubicación: `RegistroValidator.validarPassword` (solo valida mínimo de 8), `RegistrarUsuarioUseCase.ejecutar` (hash, línea 61), `AutenticarUsuarioUseCase.ejecutar` (matches, línea 52), `BcryptPasswordHasher`.
  - Problema: `BCryptPasswordEncoder` (Spring Security 6) lanza `IllegalArgumentException` cuando la password supera los 72 bytes. El registro la acepta (pasa la validación de ≥ 8 caracteres) y el hashing revienta → cae al catch-all `handleException` → `500 ERROR_INTERNO` en vez de `400`. En login, un username existente + password > 72 bytes → `500`, mientras que username inexistente → `401`: un canal de enumeración de usuarios por código de estado que contradice el espíritu de A-004 (aunque el body siga siendo idéntico en el rango normal). La spec solo define mínimo (BR-002), no máximo, por lo que la entrada es "válida" según el contrato actual.
  - Recomendación: validar longitud máxima (≤ 72 bytes) en `RegistroValidator` → `400` en registro, y/o capturar `IllegalArgumentException` en el login (p. ej., en `BcryptPasswordHasher.matches` o en el use case) para conservar el `401` idéntico. Corrección pequeña y localizada; no bloquea el merge (caso fuera del dominio habitual, CI verde, todos los AC de la spec cumplidos).

### Nit

- `docs/architecture/SPEC-003.md` §5.6 lista `PasswordHasher` entre los beans declarados en `AuthBeansConfig`, pero la implementación lo provee como `@Component` (`BcryptPasswordHasher`) vía component scan; `AuthBeansConfig` solo declara `PasswordEncoder`. La desviación está documentada en el javadoc de `AuthBeansConfig` y en el file map §8.1 (correcto). Verificado: sin beans duplicados ni ambigüedad (una única implementación de `PasswordHasher` y de `TokenEmisor`). Sincronizar §5.6 del documento.
- `JwtServiceTest` hardcodea el literal del secret de test (duplicado de `application-test.yml`); un drift fallaría ruidosamente (deseable). Mismo patrón ya señalado en la revisión de SPEC-001.
- Duplicación `JwtService.emitir` ↔ `JwtTokenFactory`: riesgo de drift documentado en ADR-005 §Consequences; ambos leen la misma propiedad configurable y el round-trip `emitir→validar` de `JwtServiceTest` mantiene el contrato honesto. Aceptable por la decisión explícita de ADR-005.

### Nice-to-have (no bloqueante)

- Cobertura de integración para token **expirado** (AC-013 solo ejercita token malformado "Bearer abc"). El 401 por expiración está cubierto a nivel unitario (`JwtServiceTest.tokenExpiradoEsRechazado` → `JwtException`) y el filtro mapea cualquier fallo de validación al mismo path (contexto vacío → entry point 401), por lo que no hay gap funcional; un caso de integración con token expirado sería más directo.
- Enumeración por timing en login: username existente ejecuta `matches` BCrypt (~100 ms) y username inexistente no. Mitigación estándar: comparación BCrypt dummy cuando el usuario no existe. Fuera del contrato de la spec (A-004 exige body idéntico, no timing).

### Product consideration (no es defecto)

- El registro público permite crear usuarios `ADMIN` (A-002): asunción documentada de la spec; si el Product Owner quiere restringir quién crea `ADMIN`, se ajusta en un sprint futuro. Comportamiento coherente con lo aprobado.

## Verification

Revisión estática rigurosa de todos los archivos nuevos/modificados (sin Java/Maven/Docker en el sandbox; el estado de CI se verifica por evidencia: `mvn -B verify` verde en HEAD `697658d`, 139 tests, 0 fallos, incluyendo `ClienteApiIntegrationTest` 25, `AuthApiIntegrationTest` 16 con Testcontainers Postgres real y ArchUnit):

1. **Hexagonal / ArchUnit** — `domain` (Usuario, Rol, puertos, excepciones) sin imports de Spring/Jakarta/otras capas; `application` (commands, `DatosRegistro`, `RegistroValidator`, use cases) importa solo `com.banco.domain..`/`com.banco.application..`/`java..`; Spring/controllers solo en `infrastructure`. `LayerArchitectureTest` sin cambios de reglas.
2. **Seguridad — passwords** — `PasswordHasher` → `BcryptPasswordHasher` (envuelve `BCryptPasswordEncoder`); la entidad nunca recibe la password en claro; `UsuarioDto` no expone hash (verificado `$.password`/`$.passwordHash` `doesNotExist()` en AC-001); `password_hash VARCHAR(60)` coherente con la salida de BCrypt. `BcryptPasswordHasherTest` con BCrypt real (hash ≠ password, longitud 60, matches true/false, salt).
3. **Seguridad — JWT** — `JwtService.emitir` HS256: `sub`=username, `role`, `clienteId` (Long, solo CLIENTE), `exp` = now + `banco.security.jwt-expiration-minutes` (default 60); secret ≥ 32 bytes en `application.yml` (46) y `application-test.yml` (47); `validar` intacto y nunca lee `sub` (verificado por grep: ningún código de producción usa `getSubject`/`"sub"`). `JwtServiceTest` cubre round-trip, claims, expiración, firma inválida y token expirado. `JwtTokenFactory` alineado al contrato nuevo (test).
4. **No enumeración en login** — `AutenticarUsuarioUseCase`: mismo `CredencialesInvalidasException` (mismo tipo y mensaje) para username inexistente, password incorrecta y entradas null/blank; AC-010 compara bodies idénticos en integración. Guard defensivo antes de tocar el repositorio.
5. **Validación de registro** — `RegistroValidator` (clase única, corte ante el primer error, desviación CoR documentada en §8.2): username obligatorio/≤ 50 (A-005), password ≥ 8 (BR-002), rol ∈ {CLIENTE, ADMIN}, CLIENTE sin clienteId → 400 (ERR-007); `DatosRegistro` recorta username (null-safe) y nunca la password. Unicidad por doble barrera (chequeo de aplicación → 409 con campo; constraint UNIQUE de V2 → backstop 409 genérico).
6. **Vínculo CLIENTE↔Cliente** — `RegistrarUsuarioUseCase`: `ClienteRepository.findById` → `ClienteNoEncontradoException` → 404 (ERR-006); ADMIN ignora `clienteId` informado (se persiste null, A-003) — cubierto por unit test `adminConClienteIdInformadoLoIgnoraYPersisteNull` y comentado en el código.
7. **Mapeo de errores** — `GlobalExceptionHandler` implementa la tabla §8.6: `CredencialesInvalidasException` → 401 `NO_AUTENTICADO` (sin details), `UsernameDuplicadoException` → 409 `CONFLICTO_UNICIDAD` con `details[0].campo = "username"`, `DatosInvalidosException` → 400 con campo, `DataIntegrityViolationException` → 409 genérico, catch-all → 500 sin leak de stack (la causa solo se loguea). Los 401/403 de Spring Security los escriben el entry point y el access-denied handler (intactos).
8. **Wiring** — `AuthBeansConfig`: beans `PasswordEncoder`, `RegistroValidator`, ambos use cases; `PasswordHasher` y `TokenEmisor` resueltos por component scan (única implementación cada uno — sin ambigüedad ni duplicados). `SecurityConfig`: solo dos `permitAll()` nuevos al inicio; RBAC de clientes y handlers intactos.
9. **Persistencia** — `V2__usuarios.sql` ↔ `UsuarioJpaEntity` coherentes para `ddl-auto: validate` (BIGSERIAL↔Long, VARCHAR(n)↔length(n), cliente_id nullable con FK); sin `AttributeConverter` (convención SPEC-001).
10. **Tests** — Unit: `UsuarioTest`, `RegistroValidatorTest`, `RegistrarUsuarioUseCaseTest`, `AutenticarUsuarioUseCaseTest`, `JwtServiceTest`, `BcryptPasswordHasherTest` — significativos, no tautológicos. El patrón de stubs por identidad (fix `697658d`) está documentado en el propio test con comentario explicativo. Integración: `AuthApiIntegrationTest` (AC-001..AC-020) con registro+login reales, usernames únicos por método (riesgo §12 del diseño contemplado), envelope verificado en 400/401/403/404/409.
11. **Scope** — Archivos nuevos/modificados según el file map §8.1; `ClienteApiIntegrationTest` con solo call-sites actualizados mecánicamente (usernames dummy, sin cambios de assertions — verificado método por método); `pom.xml` sin dependencias nuevas (jjwt 0.12.6 y BCrypt ya presentes); `application.yml`/`application-test.yml` sin cambios; sin tocar frontend/docker. El fix `697658d` toca únicamente `AutenticarUsuarioUseCaseTest`.

## Result

**APPROVE.** La implementación es de calidad: capas hexagonales respetadas y verificadas por ArchUnit, seguridad sólida en el dominio definido por la spec (BCrypt nunca en claro, JWT con contrato de claims alineado entre producción y test, 401 idéntico sin enumeración por body), validación y mapeo de errores completos, tests unitarios e de integración significativos y verdes en CI (139 tests, 0 fallos). Los hallazgos son un minor de edge case (límite de 72 bytes de BCrypt → 500 en registro/login con passwords > 72 bytes; recomendado corregir en un follow-up con validación de longitud máxima y/o captura defensiva en `matches`) y nits documentados (mismatch §5.6 del diseño vs. wiring real, secret literal en test, duplicación aceptada por ADR-005). Ninguno afecta los requerimientos aprobados ni la corrección del dominio funcional de la spec; el gate de merge queda autorizado.
