# ADR-004 — Autenticación mínima JWT en Sprint 1 (solo validación) y propiedad por claim `clienteId`

## Status

Accepted

---

## Context

SPEC-001 (Sprint 1) exige proteger `/api/v1/clientes` y responder `401`/`403`
(ERR-003, ERR-005; AC-008..AC-022). Sin embargo, SPEC-003 (registro/login,
entidad `Usuario`, endpoints `/api/v1/auth`) está planificada para el Sprint 2.

La asunción A-001 de SPEC-001 resuelve el alcance: infraestructura mínima de
JWT + Spring Security (validación HS256, claims `role` y `clienteId`) y un
mecanismo para emitir tokens en tests de integración, sin `Usuario` ni
endpoints de autenticación.

Quedan dos decisiones técnicas no cubiertas por la spec ni por ADR-003:

1. **Dónde vive la emisión de tokens**: si se agrega un método de emisión a la
   producción, el código de producción contiene funcionalidad que solo usan los
   tests (SPEC-003 recién en Sprint 2 requerirá emisión de verdad).
2. **Cómo resuelve el sistema la propiedad** (AF-001: `CLIENTE` consulta solo su
   perfil) sin la entidad `Usuario` ni la vinculación `Usuario`↔`Cliente`.

---

## Decision

1. **`JwtService` (producción) solo valida.** Expone `validar(String token)` →
   `AuthenticatedUser(rol, clienteId)` (claims `role`, `clienteId` opcional;
   `sub` provisional = rol). No emite tokens.
2. **La emisión vive en scope de test**: `JwtTokenFactory` en
   `src/test/java/com/banco/support/`, que firma tokens HS256 con el mismo
   secret de `application-test.yml` y el mismo contrato de claims que
   `JwtService` espera. Se provee como bean vía `@TestConfiguration` en
   `BaseIntegrationTest`. Producción queda libre de código de test.
3. **Propiedad resuelta por claim `clienteId` en la capa de aplicación**:
   `ObtenerClienteUseCase.ejecutar(ObtenerClienteQuery(id, rol, clienteIdClaim))`
   lanza `AccesoDenegadoException` (→ `403`) cuando `rol == "CLIENTE"` y el
   `clienteIdClaim` no coincide con el `id` consultado. El claim permite
   resolver el cliente propio sin depender de la entidad `Usuario`.
4. **Contrato de claims (Sprint 1):** `sub` = rol (provisional), `role` =
   `ADMIN`|`CLIENTE`, `clienteId` (solo tokens `CLIENTE`), `iat`, `exp`
   (expiración corta, configurable `banco.security.jwt-expiration-minutes`,
   default 60).
5. **Camino de extensión para SPEC-003 (Sprint 2):** `JwtService` agrega un
   método de emisión (o se introduce un `TokenIssuer`), llega la entidad
   `Usuario` (con `passwordHash` BCrypt y `clienteId` opcional), se exponen los
   endpoints `/api/v1/auth` y el `sub` pasa a ser el username. La validación,
   el filtro y la verificación de propiedad no cambian de lugar.

---

## Alternatives

### Alternative A — Emisión de tokens en producción (método `generar` en `JwtService`)

Ventaja: un solo lugar con el contrato de claims; SPEC-003 lo reutilizaría tal
cual. Desventaja: código de producción que solo ejercitan los tests durante
todo el Sprint 1 (funcionalidad muerta), y riesgo de que se use para firmar
tokens en entornos no productivos sin propósito real. **Descartada** para
Sprint 1; el factory de test duplica ~10 líneas de jjwt a cambio de una
producción limpia. (Si el drift de claims se volviera un problema, esta
alternativa se puede retomar en SPEC-003.)

### Alternative B — Verificación de propiedad en el filtro o el controller

Ventaja: la capa web concentra la autorización. Desventaja: ARCHITECTURE.md §8
establece que la verificación de propiedad se hace "en la capa de aplicación";
hacerla en el filtro/controller la volvería no testeable sin HTTP y duplicaría
lógica por endpoint. **Descartada.**

### Alternative C — Implementar ya la entidad `Usuario` y el login (adelantar SPEC-003)

Ventaja: elimina la infraestructura "provisional". Desventaja: excede el
alcance aprobado de SPEC-001 (out of scope explícito, §12) y rompe el
planeamiento de sprints (E3 es posterior a E1 en el backlog). **Descartada.**

---

## Consequences

### Positive

- Producción sin código de emisión de tokens (solo validación) y sin código
  de test (factory en `src/test`).
- Los criterios 401/403 de SPEC-001 se cumplen sin la entidad `Usuario`.
- La verificación de propiedad queda en aplicación: testeable con unit tests y
  reutilizable por SPEC-002 (cuentas: "CLIENTE opera solo sus cuentas").
- SPEC-003 extiende el diseño sin rediseñar el filtro ni la validación.

### Negative

- El contrato de claims se define en dos lugares (producción: `JwtService`;
  test: `JwtTokenFactory`) — riesgo de drift, mitigado documentando el contrato
  en `docs/architecture/SPEC-001.md` §8.4 y verificándolo en revisión.
- Tokens `ADMIN` sin `clienteId`: cualquier lógica futura de propiedad para
  ADMIN debe contemplar el null (comportamiento documentado).

---

## Related Documents

- `docs/specs/SPEC-001-clientes.md` §9 (A-001), §11 (AC-008..AC-022)
- `docs/specs/SPEC-003-autenticacion.md` §3, §5 (claims `sub`/`role`, entidad `Usuario`)
- `docs/adr/ADR-003-jwt-spring-security.md`
- `ARCHITECTURE.md` §8
- `docs/architecture/SPEC-001.md` §8.4, §8.5, §13
