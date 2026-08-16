# Review Report — SPEC-003

- **Verdict:** PASS
- **Review type:** compliance (reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 2 (revisión estática completa + re-verificación tras el fix `697658d` del test de login y el run de CI verde)

## Summary

| Area | Result |
| --- | --- |
| Functional (FR-001..FR-005) | OK |
| Business rules (BR-001..BR-004) | OK |
| Authorization (rol y propiedad) | OK |
| Validation (RegistroValidator) | OK |
| Persistence (V2__usuarios.sql ↔ JPA, ddl-auto: validate) | OK |
| Security / JWT (contrato de claims, BCrypt) | OK |
| Testing (unit / integración Testcontainers / ArchUnit) | OK |
| Architecture (capas hexagonales, ArchUnit) | OK |
| Scope (sin cambios ajenos) | OK |
| CI gate (`mvn -B verify`) | OK — verde en HEAD |

## Findings

### Blocker

- Ninguno. El bloqueo de CI detectado en la primera pasada quedó **resuelto**: el fallo era un stub de Mockito por identidad en `AutenticarUsuarioUseCaseTest` (`Usuario` no sobrescribe `equals`/`hashCode`; el stub `when(tokenEmisor.emitir(usuarioStored()))` y el `verify` referenciaban una instancia distinta de la devuelta por `findByUsername`, por lo que `emitir` devolvía null y el happy path fallaba). Fix `697658d`: se comparte una única instancia `stored` en los tres tests con el mismo patrón (`happyPathDevuelveElJwtEmitido`, `elUsernameSeNormalizaConTrim`, `matchesRecibeLaPasswordEnClaroYElHashAlmacenado`). Solo se tocó el test; sin cambios de comportamiento ni de aserciones. Runs de CI en HEAD `697658d` **verdes**: pull_request `31932166384` (Backend job "Build and test" → success) y push `31932163610`; `mvn -B verify` → BUILD SUCCESS, Tests run: 139, Failures: 0, Errors: 0, Skipped: 0 (unit + `ClienteApiIntegrationTest` 25 + `AuthApiIntegrationTest` 16, Testcontainers Postgres real + ArchUnit).

### Major

- Ninguno.

### Minor / Nit

- AC-016 de la spec enuncia `POST`/`PUT`/listado de clientes → `403` para `CLIENTE`, pero `AuthApiIntegrationTest` solo ejercita `POST`. Los casos `PUT` y listado ya están cubiertos por `ClienteApiIntegrationTest` AC-019 (`put` con token CLIENTE → 403) y AC-021 (listado con token CLIENTE → 403), ambos con el mismo `SecurityConfig` intacto: sin gap funcional.
- `JwtServiceTest` hardcodea el literal del secret de test (debe mantenerse sincronizado con `application-test.yml`; un drift fallaría ruidosamente, lo cual es deseable). Mismo patrón ya documentado en la revisión de SPEC-001.
- La omisión de `details` en los envelopes 401/403/404/500 se deriva de `spring.jackson.default-property-inclusion: non_null` (configurado en `application.yml` y `application-test.yml`); no hay un test directo que la verifique.
- El patrón de stubs por identidad de Mockito (fix `697658d`) es frágil por diseño (depende de que `Usuario` no sobrescriba `equals`), pero está correctamente documentado en el propio test y es la solución más simple para el caso.

## Verification

Checklist completa (revisión estática de todos los archivos nuevos/modificados; sin Java/Maven/Docker en el sandbox, por lo que `mvn test`/`mvn verify` no se pudieron ejecutar localmente; el estado de CI se verificó vía GitHub API):

1. **FR-001 / AC-001..AC-004 — Registro público** — OK. `AuthController` `POST /api/v1/auth/register` → `201` + `UsuarioDto`; `RegistroValidator` → `400` (password < 8, rol inválido); `existsByUsername` → `UsernameDuplicadoException` → `409`. `SecurityConfig` matcher `permitAll()`. Tests: `AuthApiIntegrationTest` AC-001..AC-004 + unit `RegistroValidatorTest`/`RegistrarUsuarioUseCaseTest`.
2. **FR-002 / AC-008..AC-011 — Login y emisión JWT** — OK. `JwtService.emitir` HS256: `sub`=username, `role`, `clienteId` (solo CLIENTE), `exp` = now + `banco.security.jwt-expiration-minutes` (default 60). `AutenticarUsuarioUseCase`: `401` idéntico para username inexistente y password incorrecta (A-004), verificación con `PasswordHasher.matches` (nunca en claro — AC-011). Tests: `JwtServiceTest` (round-trip emitir→validar, claims, expiración, firma/expiración inválidas), `AutenticarUsuarioUseCaseTest`, AC-008..AC-010 (AC-010 compara body idéntico).
3. **FR-003 / AC-012..AC-014 — Endpoints protegidos** — OK. `SecurityConfig`: `permitAll()` solo para `POST /api/v1/auth/register` y `/login` (al inicio del chain); `anyRequest().authenticated()`; entry point `401` con envelope; filtro `JwtAuthenticationFilter` sin cambios (token inválido → contexto vacío → 401). Tests AC-012 (sin token) y AC-013 (token malformado) → `401 NO_AUTENTICADO`.
4. **FR-004 / AC-015..AC-017 — Resolución de rol y RBAC** — OK. Filtro setea autoridad `ROLE_<rol>`; matchers de clientes intactos (`POST`/`PUT`/listado → `hasRole("ADMIN")`, `GET /{id}` → `hasAnyRole("ADMIN","CLIENTE")`); access-denied handler `403`. Tests AC-016 (CLIENTE sobre POST → 403), AC-017 (ADMIN consulta/crea → 200/201).
5. **FR-005 / AC-005..AC-007, AC-017 — Vínculo CLIENTE↔Cliente** — OK. `clienteId` requerido para CLIENTE (`RegistroValidator` → 400, ERR-007); debe existir el `Cliente` (`ClienteRepository.findById` → `ClienteNoEncontradoException` → 404, ERR-006); CLIENTE opera solo su propio cliente (`ObtenerClienteUseCase` sin cambios, propiedad en application — BR-004); ADMIN ignora `clienteId` informado (se persiste null, A-003 — unit test `adminConClienteIdInformadoLoIgnoraYPersisteNull`). Tests AC-005, AC-006, AC-014, AC-015 (200 propio / 403 ajeno con login real).
6. **BR-001 / AC-007, AC-011 — BCrypt** — OK. `PasswordHasher` → `BcryptPasswordHasher` (envuelve `BCryptPasswordEncoder`); `password_hash VARCHAR(60)`; la entidad `Usuario` nunca recibe la password en claro; `UsuarioDto` no la expone (verificado `jsonPath("$.password").doesNotExist()` en AC-001). `BcryptPasswordHasherTest`: hash ≠ password, longitud 60, matches true/false, salt.
7. **BR-002 — password ≥ 8** — OK. `RegistroValidator` → `DatosInvalidosException("password", ...)` → 400 con `details[0].campo == "password"` (AC-003).
8. **BR-003 — username único (doble barrera)** — OK. Chequeo `existsByUsername` en el use case → `UsernameDuplicadoException` → 409 `CONFLICTO_UNICIDAD` con `details[0].campo == "username"` (AC-004); constraint `UNIQUE (username)` en V2 como backstop ante carreras → `DataIntegrityViolationException` → 409 genérico (mapeo existente en `GlobalExceptionHandler`).
9. **BR-004 — CLIENTE solo su propio Cliente** — OK. Propiedad en `ObtenerClienteUseCase` (capa de aplicación, sin cambios); pruebas de integración con login REAL: AC-014 (200 propio), AC-015 (403 ajeno, `ACCESO_DENEGADO`).
10. **ERR-001..ERR-007 — Mapeo de errores** — OK. `GlobalExceptionHandler` implementa la tabla §8.6 del diseño: `CredencialesInvalidasException` → 401 `NO_AUTENTICADO` (sin details, mensaje idéntico A-004); `DatosInvalidosException` → 400 `DATOS_INVALIDOS` con `[{campo, mensaje}]`; `AccesoDenegadoException` → 403 `ACCESO_DENEGADO`; `ClienteNoEncontradoException` → 404 `CLIENTE_NO_ENCONTRADO`; `UsernameDuplicadoException` → 409 `CONFLICTO_UNICIDAD` con campo; `DataIntegrityViolationException` → 409 genérico; `HttpMessageNotReadableException`/`MethodArgumentTypeMismatchException` → 400; fallback `Exception` → 500 `ERROR_INTERNO` sin leak. Los 401/403 de Spring Security los escriben el entry point y el access-denied handler (intactos).
11. **Layering / ArchUnit** — OK. `domain` sin imports de Spring/Jakarta/otras capas; `application` importa solo `com.banco.domain..`, `com.banco.application..` y `java..`; nada fuera de `infrastructure` depende de `infrastructure`; controllers solo en `infrastructure`. `LayerArchitectureTest` sin cambios (reglas no debilitadas). Verificado import por import en las 6 clases nuevas de application y las 7 de domain.
12. **Persistencia (`ddl-auto: validate`)** — OK. `UsuarioJpaEntity` ↔ `V2__usuarios.sql` coherentes: `BIGSERIAL` ↔ `Long @Id @GeneratedValue(IDENTITY)`, `VARCHAR(50)`/`VARCHAR(60)`/`VARCHAR(7)` ↔ `@Column(length=...)`, `cliente_id BIGINT` nullable con FK ↔ `Long` (nullable), sin columnas `Instant` (no reaparece el problema de V1/timestamptz). Rol como `String` (`rol.name()` / `Rol.valueOf`) sin `AttributeConverter` (convención SPEC-001). Hibernate validate no valida UNIQUE/FK (los define Flyway). El patrón idéntico de V1 ya validó en CI en SPEC-001.
13. **Contrato de claims del JWT** — OK. Producción (`JwtService.emitir`) y test (`JwtTokenFactory.tokenAdmin(username)`/`tokenCliente(username, clienteId)`) alineados: `sub`=username, `role`, `clienteId` (Long, solo CLIENTE), `exp` = now + configuración. `validar` no lee `sub` (validación/filtro/propiedad agnósticos al cambio — ADR-004 §5); ningún código de producción lee `sub`. `JwtServiceTest` cubre el round-trip y ambos lados del contrato; todos los call-sites de `ClienteApiIntegrationTest` actualizados con usernames dummy (comportamiento sin cambios).
14. **Test coverage (AGENTS.md §12, spec §11)** — OK. Unit: `UsuarioTest`, `RegistroValidatorTest`, `RegistrarUsuarioUseCaseTest`, `AutenticarUsuarioUseCaseTest`, `JwtServiceTest`, `BcryptPasswordHasherTest`. Integración Testcontainers: `AuthApiIntegrationTest` (16 tests, AC-001..AC-020: happy path, validación 400/409/404, login 200/401 idéntico, autorización 401/403, persistencia real registro→login). ArchUnit: `LayerArchitectureTest` (AC-020) sin cambios. CI verde: 139 tests, 0 failures/errors, 0 skipped (incluye 25 de `ClienteApiIntegrationTest` y 16 de `AuthApiIntegrationTest` con Postgres real + ArchUnit).
15. **Scope** — OK. Archivos nuevos/modificados exactamente según el file map §8.1 del diseño; `ClienteApiIntegrationTest` solo con call-sites actualizados mecánicamente (sin cambios de assertions); sin dependencias nuevas en `pom.xml`; `application.yml`/`application-test.yml` sin cambios (secret + expiración ya existentes); sin cambios en frontend/docker. El fix `697658d` toca únicamente `AutenticarUsuarioUseCaseTest`.

## Result

**PASS.** La implementación satisface todos los requerimientos funcionales (FR-001..FR-005), reglas de negocio (BR-001..BR-004), casos de error (ERR-001..ERR-007) y criterios de aceptación (AC-001..AC-020) de la spec aprobada, y respeta el diseño aprobado (`docs/architecture/SPEC-003.md`) y los ADR-003/004/005: contrato de claims `sub`=username con `JwtService.emitir` + `JwtTokenFactory` alineados, BCrypt vía puertos (`PasswordHasher`/`TokenEmisor`) que mantienen `application` libre de Spring, `SecurityConfig` con solo dos `permitAll()` nuevos y RBAC de clientes intacto, migración V2 coherente con la entidad JPA para `ddl-auto: validate`, mapeo completo de errores §8.6, y ArchUnit sin reglas debilitadas. El único hallazgo de la primera pasada (CI rojo en HEAD por un stub de Mockito por identidad en `AutenticarUsuarioUseCaseTest`) fue corregido en `697658d` y los runs de CI en el commit revisado están **verdes** (`mvn -B verify` → BUILD SUCCESS, 139 tests, 0 fallos), incluidos los tests de integración con Testcontainers y ArchUnit. No se encontraron violaciones de spec ni de arquitectura; los hallazgos restantes son menores/nits y no bloquean el merge.
