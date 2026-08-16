# ADR-006 — Agregado Cuenta mínimo (gap de SPEC-002), frontera transaccional y estrategia de concurrencia (SPEC-004)

## Status

Accepted

---

## Context

SPEC-004 (transferencias) exige operar sobre cuentas con saldo, CBU, estado y
concurrencia segura, pero SPEC-002 (cuentas) figura como Draft en el roadmap y
**nunca se implementó**: el código no tiene entidad `Cuenta`, ni VOs
`CBU`/`Money`, ni migración de cuentas, ni `CuentaRepository` (verificado en el
repositorio). La asunción A-001 de la spec resuelve el alcance: esta spec
incorpora el agregado `Cuenta` mínimo requerido por las transferencias.

Tres decisiones técnicas quedan sin resolver en la spec:

1. **Dónde vive la frontera transaccional.** FR-002 exige debitar el origen,
   acreditar el destino y persistir dos movimientos en **una única transacción
   de BD**. Hasta ahora el sistema no tiene ningún `@Transactional`: SPEC-001 y
   SPEC-003 documentaron "sin `@Transactional` explícito" (operaciones de una
   sola escritura; consistencia apoyada en constraints UNIQUE). La transferencia
   es multi-escritura y requiere la frontera, pero la regla ArchUnit
   "`application` depende solo de `domain`" (AC-023) prohíbe importar Spring en
   los use cases.
2. **Estrategia de concurrencia.** BR-006 manda `@Version` (optimistic lock) en
   `Cuenta`. `ARCHITECTURE.md` §6 admite "reintenta o rechaza"; la asunción
   A-004 de la spec elige **rechazar** con `409` sin reintento automático.
3. **Forma del agregado `Cuenta` mínimo.** Modelo de saldo, CBU, moneda, factory
   de creación y puertos, con la menor superficie posible (AGENTS.md §14) pero
   compatible con SPEC-002/SPEC-005 futuras.

---

## Decision

1. **Incorporar el agregado `Cuenta` mínimo (A-001).** Entidad `Cuenta` (id,
   clienteId, `CBU` cbu, `TipoCuenta` tipo, `Money` saldo, `Moneda` moneda,
   `EstadoCuenta` estado, createdAt, `version`), VOs `CBU` (22 dígitos) y
   `Money` (`BigDecimal` + `Currency`, operaciones con `MathContext` explícito,
   sin `double`), entidad `Movimiento`, enums `TipoCuenta`/`EstadoCuenta`/
   `TipoMovimiento`/`Moneda` (ARS), puertos `CuentaRepository` (save, findById,
   findByCbu, findByClienteId, agregación del límite diario) y
   `MovimientoRepository` (save, findByCuentaIdOrderByFechaDesc), factory
   estática `Cuenta.crear(...)` (saldo 0, ACTIVA, version 0) y métodos de
   dominio `debitar`/`acreditar` (invariante de saldo ≥ 0). **Fuera de
   alcance:** endpoints de apertura/consulta/listado (SPEC-002), generador de
   CBU (SPEC-002 FR-002), comisiones por tipo (Strategy como evolución).
   Cuando SPEC-002 se implemente, debe alinearse con este modelo.

2. **Frontera transaccional en infraestructura:**
   `infrastructure/service/TransferenciaService` (`@Service`) con método
   `@Transactional` que delega en el use case puro `TransferirUseCase`. La
   transacción REQUIRED cubre las lecturas de validación y las 4 escrituras
   (origen, destino, saliente, entrante): cualquier fallo → rollback total
   (FR-002). `application` permanece libre de Spring (ArchUnit AC-023 intacta).
   Es el **único** `@Transactional` del sistema; la frontera queda explícita,
   testeable sin HTTP y en el lugar que la arquitectura hexagonal reserva a la
   infraestructura.

3. **Concurrencia: `@Version Long` + rechazo `409` sin reintento (A-004).** La
   columna `version BIGINT NOT NULL DEFAULT 0` en `cuentas`; el adapter mapea
   `version` en ambos sentidos (si se omitiera, el `merge` usaría version 0 y el
   lock jamás dispararía). Ante `OptimisticLockException`
   (`ObjectOptimisticLockingFailureException` en Spring) → `409
   CONFLICTO_CONCURRENCIA` en `GlobalExceptionHandler`; el cliente reintenta.
   No hay reintento automático server-side. Se elige rechazar por
   predecibilidad y testabilidad (misma justificación que A-004).

4. **Soporte (decisión de diseño asociada):** puerto separado
   `MovimientoRepository` para el historial (read model — CQRS ligero,
   `ARCHITECTURE.md` §5), con la agregación del límite diario en
   `CuentaRepository` por mandato de la spec §10 (el adapter delega en
   `MovimientoJpaRepository`); día calendario del límite interpretado en UTC
   (convención de almacenamiento del repo — V1).

---

## Alternatives

### Alternative A — `@Transactional` en el use case (`TransferirUseCase`)

El bean se crea en `TransferenciaBeansConfig`; Spring podría proxyarlo con
CGLIB. Desventaja: la clase del paquete `application` tendría que importar
`org.springframework.transaction.annotation.Transactional`, violando la regla
ArchUnit "`application` depende solo de `domain`" (AC-023) — el enforcement
fallaría en `mvn verify`. **Descartada.**

### Alternative B — `@Transactional` en los métodos de los adapters de repositorio

Cada `save` de `CuentaRepositoryAdapter`/`MovimientoRepositoryAdapter` abriría
su propia transacción (REQUIRED sin tx externo). Un fallo en el 3º o 4º `save`
no haría rollback de los anteriores → quedaría un débito/crédito parcial,
violando FR-002. **Descartada.**

### Alternative C — `@Transactional` en el controller (`TransferenciaController`)

Funcionaría (el proxy del controller abre el tx), pero acopla la capa web a la
gestión de transacciones, oculta la frontera y hace que el contrato
transaccional dependa del entry point HTTP (un consumidor no-HTTP del use case
perdería la atomicidad). **Descartada.**

### Alternative D — Locking pesimista (`SELECT ... FOR UPDATE` sobre la cuenta origen)

Eliminaría el `409` (las transferencias se serializarían: la 2ª espera, lee el
saldo nuevo y responde `422 SALDO_INSUFICIENTE`), contradice BR-006
(`@Version`) y degrada la latencia. **Descartada.**

### Alternative E — Reintento automático server-side ante conflicto de versión

`ARCHITECTURE.md` §6 lo admite, pero A-004 lo prohíbe explícitamente:
predecibilidad y testabilidad (el test AC-012 depende de un `409` estable).
**Descartada.**

### Alternative F — Implementar el alcance completo de SPEC-002 (CRUD de cuentas) ahora

Eliminaría el "agregado mínimo", pero excede el alcance aprobado de SPEC-004
(out of scope explícito, §12) y rompe el planeamiento de sprints (SPEC-002 es
Sprint 1 según el roadmap, nunca implementada; el backlog no la prioriza aquí).
**Descartada.** El agregado mínimo se diseña de forma que SPEC-002 lo extienda
sin reescribir el modelo (los endpoints se agregan, el agregado y los puertos
ya existen).

---

## Consequences

### Positive

- SPEC-004 es implementable sin SPEC-002: el agregado `Cuenta` mínimo
  desbloquea transferencias, movimientos y límite diario, y SPEC-002/SPEC-005
  reutilizarán el modelo (comparten `Cuenta`/`Movimiento`).
- La atomicidad (FR-002) queda garantizada por un único `@Transactional`
  explícito en infraestructura; `application` permanece pura (ArchUnit AC-023
  sigue pasando) y el resto del sistema no cambia su modelo transaccional.
- El lock optimista protege la consistencia del saldo bajo concurrencia con
  `409` predecible y sin reintento (A-004); el comportamiento es testeable de
  forma determinista (Parte 2 de AC-012) además del test de concurrencia real.
- El modelo mínimo (VOs, enums, puertos) es el "seam" que SPEC-002/005
  necesitan: cuando se implementen, no habrá que migrar el esquema ni
  reescribir el dominio.

### Negative

- El adapter debe mapear `version` en ambos sentidos; si se omite, el lock
  optimista falla silenciosamente (sin `409`). Mitigación: documentado en
  `docs/architecture/SPEC-004.md` §8.8 + test determinista de AC-012.
- `TransferenciaService` introduce la primera capa de servicio transaccional
  del sistema: una clase más con responsabilidad de frontera. Alternativas
  (A/B/C) evaluadas y descartadas; la decisión queda registrada para no
  reinterpretarla en specs futuras (SPEC-005 depósitos/retiros reutilizará el
  patrón).
- El CBU de la spec (ejemplo FR-001) tiene 20 dígitos; el formato definido es
  de 22 dígitos (estándar real). Los ejemplos de la spec se ajustan en tests y
  documentación (riesgo documentado en `docs/architecture/SPEC-004.md` §12).
- El límite diario es un control blando bajo concurrencia (dos transferencias
  simultáneas pueden superarlo en conjunto); el `@Version` solo protege el
  saldo. Aceptado como limitación del MVP (la spec no exige serializar el
  límite).

---

## Related Documents

- `docs/specs/SPEC-004-transferencias.md` (FR-002, BR-001/BR-006/BR-007,
  A-001/A-002/A-004, §10)
- `docs/architecture/SPEC-004.md` (§5, §6, §8.2, §8.8, §8.9, §8.10, §11, §13)
- `docs/specs/SPEC-002-cuentas.md` (Draft — modelo de referencia; A-001)
- `docs/specs/SPEC-005-depositos-retiros.md` (Draft — comparte `Cuenta`/`Movimiento`)
- `docs/adr/ADR-001-monolito-hexagonal-ddd.md`, `docs/adr/ADR-002-postgresql-flyway.md`
- `ARCHITECTURE.md` §4 (agregado `Cuenta`), §5 (optimistic lock, CQRS ligero),
  §6 (transferencias), §9 (persistencia)
