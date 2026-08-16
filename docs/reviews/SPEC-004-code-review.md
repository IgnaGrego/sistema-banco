# Review Report — SPEC-004

- **Verdict:** APPROVE
- **Review type:** quality (code-reviewer)
- **Date:** 2026-08-16
- **Reviewer:** SDD code-reviewer
- **Implementation attempts:** 1

## Summary

| Área | Resultado |
| --- | --- |
| Readability & maintainability | OK — clases pequeñas, nombres claros, javadoc en español con referencias a spec/architecture/ADR (convención del repo) |
| Security | OK — transferencia solo `CLIENTE` (matcher), propiedad del origen en el paso 2 del validador (server-side), propiedad del historial en el use case, JPQL con parámetros (sin inyección), `@Version` para evitar doble gasto, saldo nunca negativo (invariante en `Cuenta` + `Money`) |
| Performance | OK — sin N+1 (2 lecturas + 1 agregación + 4 escrituras en un único tx), índice `(cuenta_id, fecha)` cubre historial y rango del día, agregación con `COALESCE(SUM)` |
| Conventions (hexagonal, Money/BigDecimal, records, envelope, español) | OK — `domain` sin Spring/JPA, `application` Spring-free (ArchUnit), único `@Transactional` en `infrastructure.service`, `Money` con `MathContext.DECIMAL128` sin `double`, envelope `{code, message, details?}` consistente |
| Test quality | OK — unit tests significativos (un caso por chequeo del validador, uso de ArgumentCaptor, contrapartes cruzadas), 17 tests de integración Testcontainers (incluido AC-012 en dos partes) |
| Dead code / duplication / error handling | OK — sin duplicados en `GlobalExceptionHandler` (5 mapeos nuevos, tabla §8.6 completa); hallazgos menores: matcher redundante en `SecurityConfig`, `Money.esCero()` sin consumidor de producción, `Moneda("ARS")` hardcodeada en el read del adapter (correcto en MVP, documentado) |
| Scope | OK — archivos nuevos/modificados según el file map §8.1 y ADR-007; `pom.xml` sin dependencias nuevas; sin `V3__cuentas_y_movimientos.sql` residual |
| CI gate (`mvn clean verify`) | OK según reporte del developer (291 tests, 0 fallos, 86 skipped — integración omitida localmente sin Docker, corre en CI); ArchUnit 4 reglas |

## Findings

### Blocker

- Ninguno.

### Major

- Ninguno.

### Minor

- **Matcher redundante/inalcanzable en `SecurityConfig` (línea 70).**
  - Ubicación: `infrastructure/security/SecurityConfig.java` — `GET /api/v1/cuentas/*/movimientos → hasAnyRole("ADMIN", "CLIENTE")`.
  - Problema: en Spring Security el primer matcher que coincide gana; el matcher existente de SPEC-002 `GET /api/v1/cuentas/**` (línea 65, `hasAnyRole("ADMIN", "CLIENTE")`) coincide con `/api/v1/cuentas/{id}/movimientos` ANTES que el nuevo, con autorización idéntica. El matcher de SPEC-004 nunca se evalúa: es código muerto (inofensivo — la autorización efectiva es exactamente la requerida por §8.5/A-005, y documenta la intención).
  - Recomendación: eliminarlo, o moverlo ANTES del matcher `cuentas/**` si se quiere que documente la ruta explícitamente (sin cambio de comportamiento — misma regla). Coincide con el nit del reviewer de cumplimiento.

- **`Money.esCero()` sin consumidor en producción.**
  - Ubicación: `domain/vo/Money.java` (línea 75) — solo lo usan los tests (`MoneyTest`).
  - Problema: el método es parte de la API del VO planificada en architecture §8.1 (operaciones de SPEC-004) y probablemente la usará SPEC-005 (depósitos/retiros), pero hoy no tiene consumidor en código de producción.
  - Recomendación: mantener como API planificada (aceptable) o eliminarlo si SPEC-005 no lo requiere. No bloquea.

- **`Moneda("ARS")` hardcodeada en la reconstrucción de movimientos.**
  - Ubicación: `infrastructure/adapter/persistence/MovimientoRepositoryAdapter.toDomain` (línea 51) — la tabla `movimientos` no tiene columna de moneda y el adapter reconstruye `Money` siempre en ARS.
  - Problema: correcto en el MVP (SPEC-002 rechaza aperturas de moneda ≠ ARS con `MonedaNoSoportadaException` → 422, verificado en `AbrirCuentaUseCase`), y está documentado en el javadoc; pero es un gap latente del modelo de datos si mañana existiera multi-moneda (el read model perdería la moneda del movimiento).
  - Recomendación: documentado y aceptable para el MVP; si se introduce otra moneda, derivar la moneda del movimiento de la cuenta (columna `moneda` de `cuentas`) o agregar la columna en la migración correspondiente.

### Nit

- **Helper `usuarioAutenticado()` duplicado** en `TransferenciaController` y `MovimientoController` (idéntico al de `ClienteController`/`CuentaController`). Sigue la convención existente del repo; una extracción a un helper compartido reduciría la duplicación (4 copias hoy). Opcional.
- **AC-016 (orden por fecha descendente) no se asevera en integración con múltiples movimientos:** el orden lo garantiza la query derivada de Spring Data (`findByCuentaIdOrderByFechaDesc`) y se verifica el endpoint con movimientos de transferencia en AC-001; la aserción explícita del orden con varios movimientos queda solo a nivel de contrato del repo. Nit de cobertura (ya señalado por el reviewer de cumplimiento).
- **`var` en `TransferirUseCase` (líneas 52-54):** el repo usa tipos explícitos en el resto del código; `var` aquí es legible por el contexto, pero rompe levemente la consistencia de estilo. Opcional.

## Verification

Revisión estática completa de todos los archivos nuevos/modificados de SPEC-004. En este sandbox no hay herramienta de shell: no se pudo ejecutar `git diff` ni `mvn test`; la evidencia de build se basa en el reporte del developer (`mvn clean verify` verde: 291 tests, 0 fallos, 86 skipped — integración Testcontainers omitida localmente sin Docker; corre en CI) y en la revisión de cumplimiento (PASS, solo nits). Verificado:

1. **`domain/model/Cuenta.java`** — `debitar`/`acreditar` invocan primero la guarda `verificarActiva()` (BR-002/ERR-003); `debitar` lanza `SaldoInsuficienteException` si `monto > saldo` (BR-001, invariante saldo ≥ 0, doble barrera con el paso 9 del validador); `saldo` dejó de ser `final` (ADR-007). Constructor de reconstrucción, `bloquear()`, `version` y getters intactos (SPEC-002).
2. **`domain/vo/Money.java`** — record `(BigDecimal monto, Moneda moneda)` de SPEC-002 extendido con `sumar`/`restar` (`MathContext.DECIMAL128`, sin `double`), comparaciones por `compareTo` (tolerantes a escala), `esCero` y factory `ars`. El constructor conserva el invariante monto ≥ 0 (el `restar` de un monto mayor lanza `MoneyInvalidoException` — última barrera). Sin fugas de escala (DECIMAL(19,2) ↔ BigDecimal con `compareTo` en todas las comparaciones).
3. **`domain/vo/Moneda.java`** — record de SPEC-002 SIN modificaciones (valida `^[A-Z]{3}$`); confirmado por lectura directa. BR-007 se evalúa por igualdad de `Money.moneda()` (código ISO — ADR-007).
4. **`TransferValidator`** — los 10 chequeos en el orden EXACTO del main flow (spec §6 paso 2): 1 origen existe (404), 2 propiedad (403, `clienteIdClaim` null o distinto), 3 origen ACTIVA (422), 4 CBU formato (400 con campo `cbuDestino` — el `CbuInvalidoException` del VO se envuelve en `DatosInvalidosException`) + destino existe (404, AF-001), 5 destino ≠ origen (422 AUTO_TRANSFERENCIA), 6 destino ACTIVA (422), 7 monto null/signum ≤ 0/scale > 2 → 400 `DATOS_INVALIDOS` con campo `monto` (pre-chequeo ANTES de construir `Money` — evita el `MoneyInvalidoException` sin mapeo), 8 monedas compatibles (422), 9 saldo (422), 10 límite diario (`>=`, día en UTC, por cliente sobre todas sus cuentas — 422). Corta ante el primer error; devuelve `TransferenciaValidada` con las cuentas cargadas. Trim null-safe de `cbuDestino` en `DatosTransferencia`.
5. **`TransferirUseCase`** — flujo atómico: valida, una única `Instant fecha` compartida (FR-003), `debitar`/`acreditar`, 4 `save` (2 cuentas + 2 movimientos con contrapartes cruzadas), evento publicado UNA vez con `idMovimientoSaliente` (FR-004/AC-003), `idTransferencia` = id del saliente (A-007). Si el validador rechaza, nada se persiste ni se publica (verificado en unit test). `ObtenerMovimientosUseCase` — 404-antes-403, propiedad solo para rol CLIENTE, ADMIN pasa directo.
6. **Frontera transaccional** — único `@Transactional` en `infrastructure/service/TransferenciaService` (ADR-006); `application` sin imports de Spring (ArchUnit). `TransferenciaBeansConfig` inyecta el límite como `Money` desde `@Value("${banco.negocio.limite-diario-transferencias:200000}")` — la capa de aplicación no lee propiedades.
7. **Controllers/DTOs** — `TransferenciaController` (POST → 201 + `TransferenciaConfirmacion`, sin reglas de negocio, sin `Location` — decisión documentada), `MovimientoController` (GET → 200 + `List<MovimientoDto>`), `TransferirRequest`/`MovimientoDto` records planos. Sin reglas de negocio en la capa web.
8. **`GlobalExceptionHandler`** — tabla §8.6 completa sin duplicados: 5 mapeos nuevos (SaldoInsuficiente 422, LimiteDiarioExcedido 422, AutoTransferencia 422, MonedaIncompatible 422, ObjectOptimisticLockingFailure 409); `CuentaNoEncontrada`/`CuentaBloqueada`/`CbuInvalido` ya mapeados desde SPEC-002 (sin cambios); envelope consistente con omisión de null. `MoneyInvalidoException` sin mapeo — documentado como inalcanzable en el flujo (pre-chequeo del paso 7). `GlobalExceptionHandlerTest` verifica cada mapeo + `@ResponseStatus` por reflexión (incluido 409).
9. **Persistencia** — `MovimientoJpaEntity` ↔ `V4__movimientos.sql` alineados para `ddl-auto: validate` (BIGSERIAL↔Long IDENTITY, VARCHAR(22)↔tipo, DECIMAL(19,2)↔BigDecimal, TIMESTAMPTZ↔Instant, contraparte nullable); `MovimientoJpaRepository` con JPQL parametrizado (sin inyección) y `findByCuentaIdOrderByFechaDesc` (índice `idx_movimientos_cuenta_fecha`); `CuentaRepositoryAdapter` inyecta `MovimientoJpaRepository` para la agregación del día (rango `[inicio, fin)` UTC, subquery por `cliente_id` — índice `idx_cuentas_cliente_id` existente) y mapea `version` en AMBOS sentidos (crítico para el lock — verificado en AC-012 parte 2). Sin `AttributeConverter` (convención).
10. **Seguridad** — `POST /api/v1/transferencias` → `hasRole("CLIENTE")` (ADMIN → 403, AC-014); historial accesible a ADMIN|CLIENTE con propiedad en use case (AC-017). Sin secretos nuevos (la clave JWT ya existía); sin inyección SQL; lock optimista previene doble gasto; saldo nunca negativo (invariante de dominio + `Money`).
11. **Migración** — `V4__movimientos.sql` solo crea `movimientos` + índice, sin tocar `cuentas` (V3 de SPEC-002). Verificado: NO existe `V3__cuentas_y_movimientos.sql` residual (solo V1, V2, V3__cuentas, V4 en `db/migration`).
12. **Tests** — Unit: `MoneyTest` (operaciones nuevas, DECIMAL128, sin double), `CuentaTest` (debitar/acreditar + guarda + saldo intacto ante insuficiencia), `MovimientoTest`, `TransferValidatorTest` (un caso por chequeo + happy path + trim), `TransferirUseCaseTest` (2 movimientos misma fecha/monto, contrapartes cruzadas, evento una vez, nada persistido si falla), `ObtenerMovimientosUseCaseTest` (403/404/ADMIN, orden 404-antes-403), `GlobalExceptionHandlerTest`. Integración: `TransferenciaApiIntegrationTest` — 17 tests que cubren AC-001..AC-020 (consolidados) + AF-003 + envelope en todos los códigos; AC-012 en dos partes (concurrencia real con `CyclicBarrier` + reintento acotado ante flake, y conflicto determinista vía bump de `version` + `save`). ArchUnit: 4 reglas sin cambios (AC-023).
13. **Scope** — modificados solo los previstos por ADR-007 (`Money`, `Cuenta`, `CuentaRepository`, `CuentaRepositoryAdapter`, `SecurityConfig`, `GlobalExceptionHandler`, ambos yml); nuevos solo los del file map §8.1; `pom.xml` sin dependencias nuevas; `Moneda`/`CBU`/`CuentaFactory`/`CuentaJpaEntity`/`CuentaJpaRepository` intactos.

## Result

**APPROVE.** La implementación es de alta calidad: capas hexagonales respetadas y verificadas por ArchUnit (`domain` sin Spring/JPA, `application` Spring-free, único `@Transactional` en infraestructura — ADR-006), seguridad correcta en el dominio definido por la spec (autorización server-side de propiedad, optimistic lock con `version` mapeada en ambos sentidos, JPQL parametrizado, invariante de saldo nunca negativo), dinero manejado exclusivamente con `Money`/`BigDecimal` (`MathContext.DECIMAL128`, sin `double`), validador con los 10 chequeos en el orden exacto y mapeo de errores completo según §8.6 sin duplicados, y cobertura de tests amplia (unit + 17 tests de integración Testcontainers + ArchUnit). Los hallazgos son menores/nits documentados (matcher redundante en `SecurityConfig` — autorización efectiva idéntica; `esCero()` sin consumidor de producción — API planificada; ARS hardcodeada en el read del adapter — correcta en el MVP y documentada) y no afectan corrección, seguridad ni mantenibilidad del dominio funcional. La decisión final de merge es de este revisor (gate: APPROVE) y la ejecución del merge la realiza el orchestrator.

Nota: en este entorno de revisión no hay herramienta de shell, por lo que no se pudo ejecutar `mvn test` localmente ni realizar el `git commit` del reporte; la evidencia de build se basa en la revisión estática completa y en el reporte del developer (`mvn clean verify` verde en su entorno local — 291 tests, 0 fallos, 86 skipped).
