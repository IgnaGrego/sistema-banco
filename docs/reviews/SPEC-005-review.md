# Review Report — SPEC-005

- **Verdict:** PASS
- **Review type:** compliance (reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Functional (FR-001..FR-004) | OK |
| Business rules (BR-001..BR-004) | OK |
| Alternative flows (AF-001) | OK |
| Error cases (ERR-001..ERR-006) | OK |
| Authorization (§9: depósito solo ADMIN — A-001; retiro ADMIN o CLIENTE propio; 401 sin token) | OK |
| Validation (DepositoRetiroValidator: cadenas `validarDeposito`/`validarRetiro` en el orden exacto de §8.4, cortan ante el primer error) | OK |
| Concurrencia (BR-004: `@Version` reutilizado con mapeo bidireccional ya existente; `ObjectOptimisticLockingFailureException` → 409 CONFLICTO_CONCURRENCIA, sin reintento — ADR-006 decisión 3) | OK |
| Persistencia (sin migración nueva; `V4__movimientos.sql` ya soporta DEPOSITO/RETIRO; adapters sin cambios) | OK |
| Eventos de dominio (FR-004: `DepositoRealizado`/`RetiroRealizado` con `idMovimiento`; publicados una vez, verificado en unit tests AC-002/AC-009) | OK |
| Frontera transaccional (BR-004: `@Transactional` en `DepositoService`/`RetiroService` — infraestructura; `application` Spring-free — ADR-006 decisión 2) | OK |
| Testing (unit + integración Testcontainers + ArchUnit sin cambios de reglas) | OK |
| Architecture (capas hexagonales, §8.1 file map, §8.5 matchers, §8.7 beans, §8.8 frontera, §8.9 concurrencia en dos partes) | OK |
| Scope (sin dependencias nuevas, `pom.xml` intacto, sin migración, `GlobalExceptionHandler` sin cambios, `SecurityConfig` solo con 2 matchers nuevos) | OK |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor / Nit

- **AC-015 Parte 1 no asevera el `code` del envelope en la respuesta 409** (architecture §8.9/§10 preveía "409 con `code == CONFLICTO_CONCURRENCIA`"). `AC015_dosRetirosConcurrentesSobreLaMismaCuentaUna201YLaOtra409` asevera solo el status HTTP `409`; el mapeo del envelope `409 CONFLICTO_CONCURRENCIA` está cubierto por `GlobalExceptionHandlerTest.optimisticLockSeMapeaA409ConflictoConcurrencia` (test existente de SPEC-004, sin cambios). El test replica exactamente el patrón de `TransferenciaApiIntegrationTest.AC012` (que tampoco asevera el code). No afecta requerimientos.
- **Log del handler 409 dice "en transferencia":** `GlobalExceptionHandler.handleOptimisticLockingFailure` loguea "Conflicto de concurrencia (optimistic lock) en transferencia", ahora compartido por depósitos/retiros. Cosmético (wording del log); el handler está sin cambios por diseño (§8.6) y el envelope es correcto.
- **Javadoc/comentarios en archivos marcados "sin cambios" (§3/§8.1):** javadocs de `CuentaRepositoryAdapter` y `TipoMovimiento` mencionan "SPEC-004/005". No se pudo confirmar con `git diff` (sin shell en este sandbox) si son modificaciones del developer de SPEC-005 o texto preexistente de SPEC-004; en cualquier caso son solo comentarios, sin impacto de comportamiento. Sin acción requerida.
- **Ejecución de tests no disponible en el sandbox:** no hay shell/Maven/Docker, por lo que `mvn test`/`mvn verify` no se pudieron correr; los tests de integración Testcontainers se omiten localmente sin Docker por diseño (`disabledWithoutDocker = true`, riesgo documentado en SPEC-005 §12) y corren en CI. La verificación es estática completa (ver abajo).

## Verification

Revisión estática completa de `feature/spec-005` (HEAD = `7229b09f4b057e3cc97fff0ddc9ea6f4a8f9e722`; base `testing` = `d5992de93b49a1e5b65800b39b13085337125f39`; sin herramienta de shell/git en este sandbox — no se pudo ejecutar `mvn test`/`mvn verify` ni `git diff`; la evidencia se basa en la inspección de los archivos completos).

### Tabla de cumplimiento por criterio de aceptación (AC-001..AC-020)

| AC | Test que lo cubre | ¿OK? |
| --- | --- | --- |
| AC-001 | `DepositosRetirosApiIntegrationTest.AC001_adminDepositaMontoValidoResponde201ConConfirmacionYSaldoIncrementado`: 201 con `idMovimiento`/`cuentaId`/`monto`/`fechaHora`; saldo +15000 exacto (vía `saldoDe`); exactamente 1 movimiento `DEPOSITO` (vía `cantidadMovimientosDe` + GET movimientos con `tipo == "DEPOSITO"`, `moneda == "ARS"`) | OK |
| AC-002 | `RealizarDepositoUseCaseTest.happyPathAcreditaPersisteMovimientoDepositoYPublicaElEvento`: `verify(depositoEventPublisher, times(1))`; evento con `monto`, `cuentaId=10`, `fechaHora == fecha del Movimiento`, `idMovimiento == 501` (id del `save` stubbeado) | OK |
| AC-003 | `DepositoRetiroValidatorTest` (monto null/0/negativo/escala 3 → `DatosInvalidosException("monto")`) + `AC003_depositoConMontoInvalidoResponde400ConCampoMonto` (`0`, `-5`, `100.123` → 400 `DATOS_INVALIDOS` con `details[0].campo == "monto"`; `"abc"` → 400 `DATOS_INVALIDOS` vía `HttpMessageNotReadableException`; sin cambios de saldo/movimientos). Validador: `monto == null \|\| signum() <= 0 \|\| scale() > 2` antes de construir `Money` (§8.4, evita `MoneyInvalidoException` sin mapeo). Mapeo 400 ya existente en `GlobalExceptionHandler` (sin cambios, verificado) | OK |
| AC-004 | `AC004_depositoEnCuentaInexistenteResponde404`: 404 `CUENTA_NO_ENCONTRADA` (chequeo 2 del validador → `CuentaNoEncontradaException` → mapeo existente) | OK |
| AC-005 | `AC005_depositoEnCuentaBloqueadaResponde422`: cuenta creada y bloqueada vía agregado + `save`; 422 `CUENTA_BLOQUEADA` (chequeo 3 del validador + guarda `verificarActiva()`; mapeo de `CuentaBloqueadaException` → 422 existente desde SPEC-002) | OK |
| AC-006 | `AC006_clienteDepositaInclusoEnSuPropiaCuentaResponde403`: 403 `ACCESO_DENEGADO` (matcher `hasRole("ADMIN")` línea 78 de `SecurityConfig` + chequeo 1 del validador `!"ADMIN".equals(rol)` — defensa en profundidad §8.5); sin cambios de saldo/movimientos | OK |
| AC-007 | `AC007_adminRetiraMontoValidoResponde201ConConfirmacionYSaldoDecrementado`: 201 con confirmación; saldo 100000−40000 = 60000 exacto; exactamente 1 movimiento `RETIRO` | OK |
| AC-008 | `AC008_clienteRetiraDeSuPropiaCuentaResponde201`: 201; saldo 50000−20000 = 30000; 1 movimiento `RETIRO` | OK |
| AC-009 | `RealizarRetiroUseCaseTest.happyPathDebitaPersisteMovimientoRetiroYPublicaElEvento`: `verify(retiroEventPublisher, times(1))`; evento con `monto`, `cuentaId`, `fechaHora == fecha del Movimiento`, `idMovimiento == 601` (id del `save` stubbeado) | OK |
| AC-010 | `AC010_retiroConSaldoInsuficienteResponde422SinCambios` (422 `SALDO_INSUFICIENTE`, saldo intacto, 0 movimientos) + `RealizarRetiroUseCaseTest.saldoInsuficienteSePropagaSinPersistirNiPublicar` (sin saves ni evento). Mapeo `SaldoInsuficienteException` → 422 existente desde SPEC-004 (handler sin cambios) | OK |
| AC-011 | `AC011_retiroConMontoInvalidoResponde400ConCampoMonto` (`0`, `-5` → 400 `DATOS_INVALIDOS` con `details[0].campo == "monto"`; saldo/movimientos intactos) | OK |
| AC-012 | `AC012_retiroEnCuentaInexistenteResponde404`: 404 `CUENTA_NO_ENCONTRADA` (orden 404-antes-403 del validador) | OK |
| AC-013 | `AC013_retiroEnCuentaBloqueadaResponde422`: 422 `CUENTA_BLOQUEADA` | OK |
| AC-014 | `AC014_clienteRetiraDeCuentaAjenaResponde403`: 403 `ACCESO_DENEGADO` (chequeo 2 del validador: `"CLIENTE".equals(rol) && (clienteIdClaim == null \|\| !clienteIdClaim.equals(cuenta.getClienteId()))` — réplica del chequeo de `ObtenerMovimientosUseCase`); saldo/movimientos intactos | OK |
| AC-015 | Parte 1: `AC015_dosRetirosConcurrentesSobreLaMismaCuentaUna201YLaOtra409` (2 threads + `CyclicBarrier`, montos 60000+60000 sobre saldo 100000 → una 201 y una 409; saldo final 40000 nunca negativo; exactamente 1 movimiento RETIRO; reintento acotado a 3 intentos ante schedule serializado). Parte 2: `AC015_conflictoDeterministaDeVersionAlPersistir` (bump de `version` vía `JdbcTemplate` + `save` → `ObjectOptimisticLockingFailureException` — verifica el mapeo bidireccional del `@Version` en `CuentaRepositoryAdapter`, sin tocar el adapter) | OK |
| AC-016 | `AC016_sinTokenResponde401EnAmbosEndpoints`: 401 `NO_AUTENTICADO` en `POST /api/v1/depositos` y `POST /api/v1/retiros` (entry point existente, sin cambios) | OK |
| AC-017 | `AC017_adminRetiraDeUnaCuentaDeOtroClienteResponde201`: 201; saldo 50000−10000 = 40000; 1 movimiento (ADMIN sin chequeo de propiedad) | OK |
| AC-018 | `domain/MoneyTest` (`sumarDevuelveLaSumaConLaMismaMoneda`, `restarDevuelveLaDiferencia`, `restarUnMontoMayorLanzaMoneyInvalido`), `domain/CuentaTest` (`debitarDecrementaElSaldo`, `debitarSaldoInsuficienteLanzaSaldoInsuficienteYNoAlteraElSaldo`, `debitarMontoIgualAlSaldoDejaSaldoCero`, `debitarSobreCuentaBloqueadaLanzaCuentaBloqueada`, `acreditarIncrementaElSaldo`, `acreditarSobreCuentaBloqueadaLanzaCuentaBloqueada` — sin cambios, sin regresión) + `DepositoRetiroValidatorTest` (ambas cadenas, un caso por chequeo) | OK |
| AC-019 | `DepositosRetirosApiIntegrationTest` (Testcontainers + MockMvc, patrón exacto de `TransferenciaApiIntegrationTest`): endpoints + códigos 400/401/403/404/409/422 + concurrencia AC-015 en dos partes; envelope `{code, message, details?}` verificado en 400/401/403/404/422 (para 409 el code se verifica en `GlobalExceptionHandlerTest` — ver Minor) | OK |
| AC-020 | `LayerArchitectureTest` sin cambios de reglas (4 reglas); grep estático: sin imports de `org.springframework`/`jakarta` en `com.banco.application..` (clases nuevas: commands, `DatosDepositoRetiro`, `DepositoRetiroValidado`, `DepositoRetiroValidator`, use cases, confirmaciones) ni en `com.banco.domain..` (nuevos: `DepositoRealizado`, `RetiroRealizado`, puertos); `@Transactional` solo en `infrastructure/service` (`DepositoService`, `RetiroService`); Spring/controllers solo en `infrastructure` | OK |

### Verificación de arquitectura (docs/architecture/SPEC-005.md)

1. **§8.1 File map** — OK. Archivos nuevos exactamente los previstos: domain (`event/DepositoRealizado`, `event/RetiroRealizado`, `port/DepositoEventPublisher`, `port/RetiroEventPublisher`), application (`command/RealizarDepositoCommand`, `command/RealizarRetiroCommand`, `validator/DatosDepositoRetiro`, `validator/DepositoRetiroValidado`, `validator/DepositoRetiroValidator`, `usecase/RealizarDepositoUseCase`, `usecase/RealizarRetiroUseCase`, `usecase/DepositoConfirmacion`, `usecase/RetiroConfirmacion`), infrastructure (`adapter/web/DepositoController`, `adapter/web/RetiroController`, `adapter/web/DepositoRequest`, `adapter/web/RetiroRequest`, `service/DepositoService`, `service/RetiroService`, `service/DepositoEventPublisherNoop`, `service/RetiroEventPublisherNoop`, `config/DepositoRetiroBeansConfig`); modificado solo `SecurityConfig`. Sin archivos residuales (no hay `DepositoRetiroRequest` compartido, ni puerto genérico de eventos, ni `DepositoRetiroService` único).
2. **§8.3 Flujos de los use cases** — OK. `RealizarDepositoUseCase.ejecutar`: `DatosDepositoRetiro` desde el command → `validarDeposito` → `Instant.now()` → `acreditar` → `save(cuenta)` → `save(Movimiento.crear(cuentaId, DEPOSITO, monto, fecha, null))` → `publicar(new DepositoRealizado(monto, cuentaId, fecha, movimiento.getId()))` → `DepositoConfirmacion(movimiento.getId(), ...)`. `RealizarRetiroUseCase`: idem con `debitar`/`RETIRO`/`RetiroRealizado`/`RetiroConfirmacion`. Sujeto autenticado en el command (patrón `ObtenerMovimientosQuery`). `DepositoConfirmacion`/`RetiroConfirmacion` en `application` (contrato de la respuesta 201, desviación documentada §8.3).
3. **§8.4 Orden del validador** — OK. `validarDeposito`: (1) rol `ADMIN` → 403, (2) existe → 404, (3) ACTIVA → 422, (4) monto → 400 (pre-chequeo de signo/escala antes del `Money`), (5) return. `validarRetiro`: (1) existe → 404 (404-antes-403), (2) propiedad CLIENTE → 403, (3) ACTIVA → 422, (4) monto → 400, (5) saldo → 422, (6) return. Cortan ante el primer error. Helpers privados compartidos.
4. **§8.5 Matchers** — OK. `SecurityConfig` líneas 78-79: `POST /api/v1/depositos` → `hasRole("ADMIN")`; `POST /api/v1/retiros` → `hasAnyRole("ADMIN", "CLIENTE")`. Solo esos dos matchers nuevos; el resto del chain intacto (CSRF off, stateless, entry point 401 `NO_AUTENTICADO`, access-denied 403 `ACCESO_DENEGADO`, filtro JWT). Sin conflictos con matchers existentes (segmentos distintos).
5. **§8.6 GlobalExceptionHandler sin cambios** — OK. Todos los códigos de SPEC-005 ya mapeados (verificado en el archivo actual): `DatosInvalidosException` → 400 `DATOS_INVALIDOS` con `details[{campo, mensaje}]`; `HttpMessageNotReadableException` → 400 `DATOS_INVALIDOS`; `AccesoDenegadoException` → 403 `ACCESO_DENEGADO`; `CuentaNoEncontradaException` → 404 `CUENTA_NO_ENCONTRADA`; `ObjectOptimisticLockingFailureException` → 409 `CONFLICTO_CONCURRENCIA`; `SaldoInsuficienteException` → 422 `SALDO_INSUFICIENTE`; `CuentaBloqueadaException` → 422 `CUENTA_BLOQUEADA`. Javadoc del handler sin mención de SPEC-005 (consistente con "sin cambios").
6. **§8.7 Beans** — OK. `DepositoRetiroBeansConfig` declara exactamente los 3 beans previstos; sin `@Value` ni propiedades nuevas (`application.yml`/`application-test.yml` intactos, sin `banco.*` nuevo — solo el `limite-diario-transferencias` de SPEC-004).
7. **§8.8 Frontera transaccional** — OK. `DepositoService`/`RetiroService` (`@Service`, método `@Transactional`, propagación REQUIRED) delegan en los use cases (Java puro, sin anotaciones Spring). Sin `@Transactional` en `application` (regla ArchUnit). Lock optimista: `@Version` con mapeo bidireccional ya presente en `CuentaRepositoryAdapter.toEntity`/`toDomain` (sin cambios); Parte 2 de AC-015 lo verifica.
8. **§8.9 Concurrencia en dos partes** — OK (detalle en AC-015).
9. **Sin migración** — OK. `db/migration/` solo V1, V2, V3__cuentas, V4__movimientos; `V4` con `tipo VARCHAR(22)` soporta `DEPOSITO` (8) y `RETIRO` (6); `cuenta_contraparte_id` nullable queda NULL; `MovimientoJpaEntity` (`length = 22`, columna nullable) compatible con `ddl-auto: validate`.
10. **Sin dependencias nuevas** — OK. `pom.xml` intacto (mismas dependencias que SPEC-004: web, data-jpa, security, flyway, postgresql, jjwt, test/Testcontainers/ArchUnit).
11. **Reglas de negocio solo en domain/application** — OK. `DepositoController`/`RetiroController` solo construyen el command, resuelven `AuthenticatedUser` y delegan; sin lógica de negocio ni `Location` header (decisión §5.1). Validación en `DepositoRetiroValidator` (application); invariantes en `Cuenta`/`Money` (domain).
12. **Envelope JSON** — OK. `ErrorResponse(String code, String message, List<DetalleError> details)` con omisión de null (`non_null`); verificado en los tests de integración para los códigos (400/401/403/404/422) y en `GlobalExceptionHandlerTest` para todos los mapeos (incluido 409).
13. **Integración (patrón)** — OK. `DepositosRetirosApiIntegrationTest` replica exactamente el patrón de `TransferenciaApiIntegrationTest`: `@Testcontainers(disabledWithoutDocker = true)` + `BaseIntegrationTest` + `@TestConfiguration TokenConfig` con el bean `JwtTokenFactory` (secret/expiración desde `application-test.yml`); clientes/cuentas vía API (SPEC-002/003 ya implementadas); fondeo con `JdbcTemplate` (no toca `version`); usernames/DNIs únicos por método (riesgo de SPEC-005 §12); helper `cantidadMovimientosDe` + verificación del historial vía `GET /api/v1/cuentas/{id}/movimientos` (endpoint de SPEC-004, sin cambios).

### Tests / comandos

- No ejecutables en este sandbox (sin shell/Maven/Docker): `JAVA_HOME=/opt/jdk21 mvn -q test` y `mvn -q verify` no se pudieron correr. Los tests de integración Testcontainers se omiten localmente sin Docker por diseño (`disabledWithoutDocker = true` — riesgo documentado en SPEC-005 §12, precedente SPEC-001/004) y corren en CI.
- Compilación verificada estáticamente: todas las referencias entre las clases nuevas y las existentes (puertos, adapters, excepciones, `AuthenticatedUser.rol()/clienteId()`, `Money.ars`, `Movimiento.crear`, `TipoMovimiento.DEPOSITO/RETIRO`, `MovimientoDto.tipo`) resuelven correctamente; sin imports de Spring en `application`/`domain`.

## Result

**PASS.** La implementación satisface los requerimientos funcionales (FR-001..FR-004), las reglas de negocio (BR-001..BR-004), el flujo alternativo (AF-001) y los casos de error (ERR-001..ERR-006) de la spec aprobada, y cumple los 20 criterios de aceptación (AC-001..AC-020) con tests unitarios e de integración que los cubren. Respeta la arquitectura aprobada: reuso del modelo de SPEC-004/002 sin cambios de esquema ni migración, `DepositoRetiroValidator` con las dos cadenas en el orden exacto de §8.4, matchers de `SecurityConfig` conforme a §8.5 (depósitos solo ADMIN; retiros ADMIN|CLIENTE), frontera transaccional en `DepositoService`/`RetiroService` (`application` Spring-free — AC-020), lock optimista `@Version` con `409 CONFLICTO_CONCURRENCIA` sin reintento, eventos de dominio publicados una vez (AC-002/AC-009), `GlobalExceptionHandler` sin cambios (todos los códigos ya mapeados) y `pom.xml`/config intactos. La autorización (§9: depósito exclusivo de ADMIN — A-001; retiro ADMIN o CLIENTE propio con orden 404-antes-403) y el scope (sin cambios ajenos) son correctos. Los hallazgos son menores/documentados (nit de cobertura del envelope 409 en la integración, wording de un log, javadocs) y no afectan requerimientos aprobados.

Nota: en este entorno de revisión no hay herramienta de shell disponible, por lo que no se pudo ejecutar `mvn test`/`mvn verify` localmente ni realizar el `git commit` del reporte; la evidencia de build se basa en la revisión estática completa del branch `feature/spec-005` (HEAD `7229b09f` sobre base `testing` `d5992de`).
