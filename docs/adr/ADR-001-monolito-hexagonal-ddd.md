# ADR-001 — Monolito modular con arquitectura hexagonal + DDD

## Status

Accepted

---

## Context

El proyecto es un sistema bancario de portfolio con varios módulos (clientes,
cuentas, movimientos, transferencias, depósitos/retiros, autenticación).

Había que decidir cómo organizar el backend para que:

- sea fácil de entender y desplegar (es un portfolio, no producción masiva);
- mantenga reglas de negocio testables y aisladas del framework;
- demuestre criterio de diseño sin caer en sobreingeniería.

---

## Decision

Backend como **monolito modular** organizado con **arquitectura hexagonal
(ports & adapters)** y patrones tácticos de **DDD**:

- `domain` (entidades, value objects, puertos, eventos) sin dependencia de
  Spring/JPA (enforced con ArchUnit).
- `application` (casos de uso, validación).
- `infrastructure` (adaptadores JPA/web, security, config).

---

## Alternatives

### Alternative A — Microservicios

Ventajas: escalado independiente. Desventajas: complejidad operativa,
transacciones distribuidas, más infra para un portfolio. **Descartada** por
sobrecoste sin beneficio real a esta escala.

### Alternative B — Arquitectura en capas clásica (controller → service → repository)

Más simple pero acopla negocio a Spring/JPA y dificulta testear reglas puras.
**Descartada** por menor demostración de criterio.

---

## Consequences

### Positive

- Dominio puro y testeable (reglas monetarias sin framework).
- Fronteras claras entre capas, verificables automáticamente.
- Simple de desplegar (un artefacto + una DB).

### Negative

- Mayor ceremonia inicial (puertos y adaptadores).
- Requiere disciplina para no filtrar dependencias (mitigado con ArchUnit).

---

## Related Documents

- `ARCHITECTURE.md`
- `AGENTS.md` §11
