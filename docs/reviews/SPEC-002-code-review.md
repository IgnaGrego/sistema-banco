# Review Report — SPEC-002

- **Verdict:** REQUEST_CHANGES
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 1 (revisión estática completa; CI `mvn -B verify` verde en el PR #20 vía `gh pr checks 20`)

## Summary

| Area | Result |
| --- | --- |
| Readability & maintainability | OK (use cases claros, javadoc significativo, naming en español consistente, controller delgado) |
| Security | OK (SecureRandom para CBU, envelope sin stack traces, autorización server-side); nota menor sobre 404/403 documentada |
| Performance | OK (listados con una sola query derivada, índice `idx_cuentas_cliente_id`, loop de CBU acotado a 5, sin N+1) |
| Correctness & robustness | **1 Major**: `clienteId` ausente/null en apertura → `500` en lugar de `400` (no validado ni testeado) |
| Conventions (hexagonal, VOs, DTOs record, enums String, beans en config) | OK |
| Tests | OK en general; **gap significativo**: caso límite `clienteId` ausente; reflexión en `GlobalExceptionHandlerTest` razonable (ver §Findings) |
| Dead code / duplication / error handling | OK (nits: rama `default` inalcanzable de la factory, guarda de propiedad duplicada ×2) |
| Scope | OK (solo archivos de SPEC-002; sin dependencias nuevas; sin cambios ajenos) |

## Findings

### Blocker

- Ninguno.

### Major

1. **`clienteId` ausente o `null` en `POST /api/v1/cuentas` → `500 ERROR_INTERNO` en lugar de `400` (ERR-001).**
   - **Ubicación:** `application/usecase/AbrirCuentaUseCase.java:66` (`clienteRepository.findById(command.clienteId())`); causa raíz en `application/validator/AperturaValidator.java:19-40` (no valida `clienteId`) y `infrastructure/adapter/web/AbrirCuentaRequest.java:8` (`Long clienteId` nullable sin validación).
   - **Problema:** `AperturaValidator` valida `tipo` y `moneda`, pero no el único campo obligatorio de FR-001 (`clienteId`). Un request `{"tipo":"CAJA_AHORRO"}` (o con `"clienteId":null`) pasa la validación y llega a `clienteRepository.findById(null)` → `IllegalArgumentException` (`Assert.notNull` de Spring Data JPA en `SimpleJpaRepository.findById`) → cae en `GlobalExceptionHandler.handleException` → `500` con `ERROR_INTERNO`. La convención del proyecto (ERR-001, envelope, validación en `AperturaValidator`) exige `400 DATOS_INVALIDOS` con `details[{campo:"clienteId"}]`. Es un error de cliente clasificado como error de servidor, y **no hay test** que cubra el caso (ni unit ni integración).
   - **Cambio recomendado:** agregar en `AperturaValidator.validar` el chequeo `clienteId == null` → `DatosInvalidosException("clienteId", "El cliente es obligatorio")` (y opcionalmente `clienteId <= 0`); agregar unit test en `AperturaValidatorTest` y un caso de integración (`POST /api/v1/cuentas` sin `clienteId` → `400` con `details[0].campo == "clienteId"`). Re-correr `mvn -B verify`.

### Minor

2. **Orden 404-antes-que-403: un `CLIENTE` puede enumerar la existencia de cuentas ajenas.**
   - **Ubicación:** `application/usecase/ObtenerCuentaUseCase.java:28-36` y `application/usecase/ObtenerCuentaPorCbuUseCase.java:28-36` (`findById`/`findByCbu` → 404 antes del chequeo de propiedad → 403).
   - **Análisis:** el diseño es **mandatado por la spec** (AC-010/AC-011 exigen `403` para cuenta ajena y `404` para inexistente; ambos códigos distintos revelan existencia) y está **documentado** como riesgo aceptado en `docs/architecture/SPEC-002.md` §4 y §12. A diferencia de `ObtenerClienteUseCase` (403-antes-que-404, no filtra), aquí la propiedad solo se conoce tras cargar la cuenta. Para este sprint (MVP, sin exposición pública) es aceptable y los tests cubren el orden (404 → 403). **Recomendación:** dejar constancia de un story de endurecimiento futuro (respuesta uniforme 404 para ambos casos y/o rate limiting) antes de exponer la API a producción. Sin cambio de código requerido ahora.

### Nit

3. **Guarda de propiedad duplicada (4 líneas) en los dos use cases de consulta** (`ObtenerCuentaUseCase.java:32-36` y `ObtenerCuentaPorCbuUseCase.java:32-36`). Solo 2 ocurrencias; por AGENTS.md §11 es defendible dejarlo así (abstraer un helper compartido sería razonable cuando aparezca un tercer consumidor, p. ej. SPEC-004/005). Sin cambio requerido.
4. **`CuentaFactory.crear` (`domain/factory/CuentaFactory.java:34-38`):** rama `default` inalcanzable (el `switch` enum cubre ambas constantes) y ambas ramas `case` idénticas. Decisión documentada en `docs/architecture/SPEC-002.md` §13 (punto de dispatch explícito para la divergencia futura). Aceptable; considerar colapsar si tras SPEC-005 siguen idénticas.
5. **`GlobalExceptionHandlerTest` con reflexión sobre `@ResponseStatus`** (`backend/src/test/java/com/banco/infrastructure/adapter/web/GlobalExceptionHandlerTest.java:86-92`): evaluado como **razonable, no frágil**. Verifica exactamente la anotación que Spring `ExceptionHandlerExceptionResolver` usa; el acoplamiento al nombre del método + tipo de parámetro es coherente (método y anotación pertenecen juntos), y los códigos HTTP reales de los caminos alcanzables están cubiertos por `CuentaApiIntegrationTest`. Alternativa futura: un test `@WebMvcTest`/MockMvc con el advice real, pero no se justifica hoy.
6. **`MoneyInvalidoException` sin mapeo (cae en fallback 500):** decisión documentada en `docs/architecture/SPEC-002.md` §8.5; inalcanzable desde entradas de usuario en este sprint (solo la factory construye `Money` con 0). Aceptable; re-evaluar en SPEC-004/005 cuando existan operaciones de dinero con montos de entrada.

## Verification

Inspección estática completa (sin Java/Maven/Docker en el sandbox; CI verde verificado vía `gh pr checks 20` → `mvn -B verify` incluye unit + Testcontainers + ArchUnit):

1. **Dominio (9 archivos nuevos):** `Cuenta`, `TipoCuenta`, `EstadoCuenta`, `CBU`, `Money`, `Moneda`, `CuentaFactory`, `CuentaRepository`, 6 excepciones nuevas. VOs inmutables con validación en construcción (patrón `DNI`); `Money` rechaza negativos (BR-002); `CBU` valida `^[0-9]{22}$` (BR-001); `bloquear()` con guarda `verificarActiva()` (BR-003/A-001); factory con dispatch por tipo y `version 0L` documentado; puerto sin Spring ni `existsByClienteId` (justificado en §13 de arquitectura). Sin imports de Spring/JPA (regla ArchUnit 1 respetada).
2. **Aplicación (9 archivos nuevos):** `AbrirCuentaUseCase` (validación → default ARS → 422 no-ARS → 404 titular → CBU SecureRandom con regeneración acotada a 5 → factory → save), `ObtenerCuentaUseCase`, `ObtenerCuentaPorCbuUseCase` (CBU VO → 400), `ListarCuentasUseCase` (rol/claim/filtro), `AperturaValidator`, commands/queries records. Java puro; `SecureRandom` es `java.security` (permitido por ArchUnit 2). **Hallazgo Major #1 detectado aquí** (`findById` con null sin guarda).
3. **Infraestructura (10 archivos nuevos + 2 modificados):** `CuentaController` delgado (patrón `ClienteController`, `usuarioAutenticado()`), `CuentaDto`/`AbrirCuentaRequest` records sin anotaciones de validación, `CuentaJpaEntity` con `@Version` y `@Column(length)` consistentes con V3, `CuentaJpaRepository` (queries derivadas: `findByCbu`, `findAllByOrderByIdAsc`, `findByClienteIdOrderByIdAsc`, `existsByCbu`), `CuentaRepositoryAdapter` (mapeo explícito con enums como String, sin `AttributeConverter`), `CuentaBeansConfig`. `SecurityConfig`: matchers en orden crítico (§8.4: listado exacto y `/cbu/**` antes que `/{id}`; apertura solo ADMIN). `GlobalExceptionHandler`: mapeos nuevos de §8.5 (`CBU_INVALIDO` 400, `CUENTA_NO_ENCONTRADA` 404, `CUENTA_BLOQUEADA` 422, `MONEDA_NO_SOPORTADA` 422, `MonedaInvalidaException` → 400 defensivo); `MoneyInvalidoException` deliberadamente sin mapeo (documentado). Envelope sin stack traces.
4. **Migración `V3__cuentas.sql`:** `BIGSERIAL`/`VARCHAR(22)`/`VARCHAR(20)`/`NUMERIC(19,2)`/`VARCHAR(3)`/`BIGINT`/`TIMESTAMPTZ` coherentes con la entidad JPA y `ddl-auto: validate`; `UNIQUE (cbu)` con nombre `uq_cuentas_cbu` (BR-001 backstop → 409), FK inline a `clientes(id)`, índice `idx_cuentas_cliente_id` (FR-006), desviación TIMESTAMPTZ documentada (lección de V1).
5. **12 tests nuevos:**
   - Domain (5): `CBUTest` (22 OK; 21/23, no numérico, null/vacío → excepción — AC-025), `MoneyTest` (0/positivo OK; negativo/null → excepción; `cero` — AC-026), `MonedaTest` (formato; defensivo), `CuentaTest` (reconstrucción conserva todo incl. version; `bloquear()` ACTIVA→BLOQUEADA; re-`bloquear()` → `CuentaBloqueadaException` — AC-027), `CuentaFactoryTest` (ambos tipos: id null, saldo 0, ACTIVA, version 0L; tipo null → IAE — AC-024). Sin tests tautológicos; aserciones sobre valores reales.
   - Application (5): `AperturaValidatorTest` (tipo null/blank/no-parseable/minúsculas; moneda `ar`/`ars`/`AR S`; válidos; cortocircuito — AC-004 lógica; **gap: `clienteId` no validado**), `AbrirCuentaUseCaseTest` (default ARS, ARS explícito, USD → 422 sin tocar repositorio, titular inexistente → 404 sin CBU/save, regeneración con `thenReturn(true, false)` + `ArgumentCaptor` verificando 2 CBUs distintos y un único save con el segundo — AC-028, validador antes que repositorio), `ObtenerCuentaUseCaseTest` (ADMIN/CLIENTE propio/ajeno 403/claim null 403/404/orden 404→403 — AC-008..011), `ObtenerCuentaPorCbuUseCaseTest` (AC-013..017; malformado → 400 sin tocar repo), `ListarCuentasUseCaseTest` (AC-018..023: claim, sin claim 403, filtro CLIENTE 403, filtro ADMIN válido/inexistente, findAll). Mockito consistente, sin over-mocking (se mockean repositorios y validador, se verifica con captors y `never()`).
   - Infrastructure (1): `GlobalExceptionHandlerTest` — envelope + reflexión sobre `@ResponseStatus` (evaluado razonable; ver Nit #5).
   - Integración (1): `CuentaApiIntegrationTest` — AC-001..AC-023 + AC-029 (persistencia entre requests, UNIQUE y FK vía `JdbcTemplate` con `assertThrows(DataIntegrityViolationException)`, mapeo 409 del handler). **Aislamiento verificado:** DNI/email únicos por método (`400000NN` / `ac0NN-cuenta@example.com`), tokens stateless sin colisión (`admin-test` reutilizado es correcto: no hay lookup de usuario), aserciones robustas a la acumulación de datos del contenedor por clase (filtering, `size() >= 1` + búsqueda de la cuenta propia, ordenamiento sobre la lista completa). Sin condiciones de carrera entre métodos.
6. **Convenciones comparadas con SPEC-001/003:** `ClienteController`, `ClienteRepositoryAdapter`, `ClienteJpaEntity`, `ObtenerClienteUseCase`, `ClienteBeansConfig`, `ClienteApiIntegrationTest` — el código nuevo replica los patrones (controller delgado con `usuarioAutenticado()`, mapeo explícito del adapter, beans en config, `TokenConfig` anidada, envelope, DTOs record sin bean validation). Sin dependencias nuevas en `pom.xml` (verificado: ninguna adición).
7. **Dead code/duplication:** sin imports/métodos sin uso detectados; duplicación mínima (guarda de propiedad ×2, Nit #3); rama `default` de factory (Nit #4). Sin TODOs/FIXME/`System.out`/`printStackTrace`.

## Result

**REQUEST_CHANGES.** La implementación es de alta calidad general (arquitectura hexagonal respetada y verificada por ArchUnit, VOs correctos, CBU con `SecureRandom` + doble barrera de unicidad, envelope de errores sin leaks, tests significativos y bien aislados, convenciones consistentes con SPEC-001/003), pero queda **1 hallazgo Major** que impide el approve:

- **`clienteId` ausente/null en `POST /api/v1/cuentas` → `500` en lugar de `400`** (`AbrirCuentaUseCase.java:66`; `AperturaValidator` no valida el único campo obligatorio de FR-001; sin test que lo cubra). Es un error de cliente mal clasificado como error de servidor y contradice la convención documentada (ERR-001 → 400 con detalle de campo).

El fix es mínimo y está alineado con las convenciones existentes (agregar el chequeo a `AperturaValidator` + 2 tests). Los hallazgos restantes son Minor/Nit (404/403 documentado como riesgo aceptado por la spec, duplicación ×2, rama `default` de factory, reflexión del handler evaluada como razonable, `MoneyInvalidoException` sin mapeo documentado). Con el fix del Major #1 y CI verde, la re-revisión debería ser inmediata.
