# ADR-007 — Reconciliación de SPEC-004 sobre SPEC-002 (VOs y agregado Cuenta implementados)

## Status

Aceptado

---

## Context

La arquitectura de SPEC-004 (`docs/architecture/SPEC-004.md`) y su ADR-006 se
redactaron bajo la premisa de que SPEC-002 (cuentas) **nunca se implementó**
(§1 de `docs/architecture/SPEC-004.md`): prescribían crear desde cero un
agregado `Cuenta` **mínimo** (resolución del gap de SPEC-002, asunción A-001),
un VO `Money(BigDecimal, Currency)` y un `enum Moneda { ARS }`.

Esa premisa es **falsa**. SPEC-002 fue implementada, revisada y mergeada
(PR #20, rama `testing`) con formas aprobadas y distintas:

- `record Money(BigDecimal monto, Moneda moneda)` — **no**
  `(BigDecimal, Currency)`.
- `record Moneda(String codigo)` — decisión deliberada "record con String, no
  enum" (SPEC-002 §13): distingue formato inválido (400) de moneda no soportada
  (422).
- Agregado `Cuenta` con `@Version` (lock optimista), `bloquear()`, guarda
  privada `verificarActiva()`, campo `saldo` **final** y constructor público
  `Cuenta(Long id, Long clienteId, CBU cbu, TipoCuenta tipo, Money saldo,
  Moneda moneda, EstadoCuenta estado, Instant createdAt, Long version)`.
- Migración `V3__cuentas.sql` ya crea `cuentas` con
  `version BIGINT NOT NULL DEFAULT 0` y `uq_cuentas_cbu`.

Reconstruir o reescribir esos VOs/agregado con las formas originales de la
arquitectura de SPEC-004 rompería código aprobado y testeado de SPEC-002,
contradiría su arquitectura aprobada y constituiría refactorización no
relacionada (AGENTS.md §14).

---

## Decision

SPEC-004 se implementa **sobre** los VOs y el agregado aprobados de SPEC-002:

- **`Money`** gana las operaciones aritméticas/de consulta que necesitan las
  transferencias: `sumar`, `restar`, `esMayorQue`, `esMayorOIgualQue`,
  `esCero` y la factory `ars(BigDecimal)` (construye `Moneda("ARS")`). Todas
  las operaciones usan `MathContext`/`compareTo` sobre `BigDecimal`; no se usa
  `double`.
- **`Moneda`** conserva su forma de record; BR-007 (misma moneda) se evalúa por
  igualdad de `Money.moneda()` (código ISO 4217 alpha-3). En el MVP solo existe
  ARS.
- **`Cuenta`** (agregado de SPEC-002) gana `debitar(Money)` (lanza
  `SaldoInsuficienteException` si `monto > saldo` — invariante `saldo >= 0`,
  BR-001) y `acreditar(Money)`; ambos invocan primero la guarda existente
  `verificarActiva()` (BR-002/ERR-003). El campo `saldo` deja de ser `final`.
- **Persistencia/migraciones:** la `V3__cuentas.sql` de SPEC-002 ya crea
  `cuentas` con `version`; SPEC-004 agrega `V4__movimientos.sql` (tabla
  `movimientos` + índice) y la entidad JPA `MovimientoJpaEntity`. El `@Version`
  de `Cuenta` ya está presente.
- **Límite diario:** el método de agregación
  `Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia)`
  se agrega al puerto `CuentaRepository` de SPEC-002 y lo implementa
  `CuentaRepositoryAdapter` delegando en
  `MovimientoJpaRepository.sumarTransferenciasSalientesDelDia(...)` (JPQL de
  suma sobre movimientos `TRANSFERENCIA_SALIENTE` de las cuentas del cliente en
  el rango del día en UTC).
- El constructor de `Cuenta` y el mapeo JPA (incluido `version` en ambos
  sentidos) se mantienen como en SPEC-002.

---

## Consequences

### Positive

- Consistencia con el diseño aprobado e implementado de SPEC-002: SPEC-004
  extiende `Cuenta`, `Money`, `Moneda` y `CuentaRepository` en lugar de
  duplicarlos o reescribirlos.
- No se reescribe ni se rompe código mergeado (PR #20); no hay refactorización
  no relacionada (AGENTS.md §14).
- La semántica de BR-007 no cambia: se sigue exigiendo la misma moneda entre
  origen y destino (evaluada por el código ISO 4217 alpha-3 del `Moneda`).
- La tabla `cuentas` (con `version` y `uq_cuentas_cbu`) proviene de
  `V3__cuentas.sql` (SPEC-002); `V4__movimientos.sql` solo agrega `movimientos`
  y su índice: sin duplicación de migraciones.

### Negative

- Desviación menor del wording literal `Currency` de la arquitectura original
  de SPEC-004, resuelta por este ADR y por la enmienda en curso de la spec
  (la analista alinea la forma de los VOs con SPEC-002).
- `Cuenta.saldo` deja de ser `final` (mutación acotada a los métodos de
  dominio `debitar`/`acreditar`, ambos con la guarda `verificarActiva()`).
- El borrador previo `V3__cuentas_y_movimientos.sql` (WIP pre-reconciliación)
  queda obsoleto y debe eliminarse: duplicaría `cuentas` y colisionaría con la
  `V3__cuentas.sql` de SPEC-002.

---

## Related Documents

- `docs/architecture/SPEC-004.md` (§1, §2, §3, §5, §6, §8, §11, §13, §14)
- `docs/architecture/SPEC-002.md` (§8.1 VOs y agregado `Cuenta`, §13 decisión
  del record `Moneda`)
- `docs/adr/ADR-006-agregado-cuenta-minimo-frontera-transaccional-concurrencia-spec-004.md`
  (decisión 1 — agregado mínimo desde cero — reemplazada por este ADR;
  decisiones 2 — frontera transaccional — y 3 — concurrencia — siguen vigentes)
- `docs/specs/SPEC-004-transferencias.md` (enmienda en curso: VOs alineados con
  SPEC-002)
- `backend/src/main/resources/db/migration/V3__cuentas.sql` (SPEC-002, sin
  cambios)
- `AGENTS.md` §14 (scope control)
