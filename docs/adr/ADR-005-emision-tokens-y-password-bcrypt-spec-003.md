# ADR-005 — Emisión de tokens en producción (JwtService + puerto TokenEmisor) y hashing BCrypt vía puerto PasswordHasher (SPEC-003)

## Status

Accepted

---

## Context

ADR-004 dejó dos decisiones explícitamente diferidas a SPEC-003 (Sprint 2),
que ahora deben resolverse:

1. **Dónde vive la emisión de tokens.** ADR-004 §5 ofrece dos caminos:
   "JwtService agrega un método de emisión (o se introduce un TokenIssuer)".
   En Sprint 1 la producción solo valida y la emisión vive en
   `JwtTokenFactory` (scope de test), con el riesgo de drift de claims
   documentado en ADR-004 §Consequences.
2. **Contrato de claims.** SPEC-003 exige que `sub` pase de rol (provisional)
   a `username` (FR-002, §10 de la spec).

Además, SPEC-003 introduce BCrypt en producción (BR-001), y el código existente
impone una restricción arquitectónica: `application` depende solo de `domain`
(regla ArchUnit) — por lo tanto los use cases no pueden inyectar
`org.springframework.security.crypto.password.PasswordEncoder` ni
`JwtService` (infraestructura) directamente.

---

## Decision

1. **Emisión en `JwtService` (producción) implementando un puerto de dominio.**
   Se define `domain/port/TokenEmisor` con `String emitir(Usuario usuario)`.
   `JwtService` (infrastructure.security) lo implementa: `JwtService implements
   TokenEmisor`. El método `emitir(Usuario)` construye el JWT HS256 con
   `sub` = `usuario.getUsername()`, `role` = `usuario.getRol().name()`,
   `clienteId` (solo si `rol == CLIENTE`), `iat` y `exp` (expiración corta
   configurable `banco.security.jwt-expiration-minutes`, default 60). El
   constructor de `JwtService` pasa a recibir también el valor de expiración.
   `validar` no cambia.
2. **Hashing de passwords vía puerto de dominio.** Se define
   `domain/port/PasswordHasher` con `hash(String)` y `matches(String, String)`.
   `infrastructure/security/BcryptPasswordHasher` lo implementa envolviendo un
   bean `PasswordEncoder` (`BCryptPasswordEncoder`, provisto por
   `spring-boot-starter-security` — ADR-003). Los use cases de registro y login
   reciben el puerto, nunca la clase de Spring.
3. **Contrato de claims:** `sub` = `username` (fin del `sub` = rol
   provisional). `role` y `clienteId` (solo CLIENTE) se mantienen. El cambio no
   afecta la validación, el filtro ni la resolución de propiedad: nada del
   código de producción lee `sub` (ADR-004 §5). `JwtTokenFactory` (scope de
   test) se alinea al nuevo contrato (`tokenAdmin(String username)`,
   `tokenCliente(String username, Long clienteId)`).
4. **`SecurityConfig`:** se agregan `permitAll()` para
   `POST /api/v1/auth/register` y `POST /api/v1/auth/login`; el resto de las
   reglas RBAC y los handlers `401`/`403` no cambian.
5. **Uso de los puertos:** `AutenticarUsuarioUseCase` recibe
   `UsuarioRepository`, `PasswordHasher` y `TokenEmisor`; el login devuelve el
   JWT (FR-002: el use case orquesta la operación completa).
   `RegistrarUsuarioUseCase` recibe `UsuarioRepository`, `ClienteRepository`,
   `PasswordHasher` y `RegistroValidator` (hash BCrypt antes de persistir).

---

## Alternatives

### Alternative A — `TokenIssuer` como clase separada

Un componente dedicado a emitir (mismo `SecretKey` y contrato) mantendría a
`JwtService` en un rol único de validación. Desventaja: duplicaría la
construcción del `SecretKey` o exigiría compartirla, y separaría el contrato de
claims en dos clases. **Descartada**: `JwtService` ya es el único dueño del
secret y del contrato de validación; centralizar emisión + validación elimina
el riesgo de drift de claims que motivó la alternativa A de ADR-004. El puerto
`TokenEmisor` ya desacopla a `application` de la implementación concreta; si en
el futuro la emisión creciera (refresh tokens, claims por contexto), se puede
extraer sin cambiar los use cases.

### Alternative B — Emisión en el controller (el use case de login devuelve `Usuario`)

`AutenticarUsuarioUseCase` verificaría credenciales y devolvería el `Usuario`;
el controller llamaría `jwtService.emitir(...)`. Desventaja: FR-002 define el
login como "iniciar sesión y devolver un JWT" — la operación quedaría partida
entre capas, el contrato de claims solo se ejercitaría vía HTTP (sin unit test
del flujo completo) y el controller ganaría lógica de emisión. **Descartada.**
El puerto `TokenEmisor` mantiene al use case como orquestador único y testeable
sin HTTP.

### Alternative C — `PasswordEncoder` de Spring inyectado directo en los use cases

Ventaja: cero clases nuevas (se usa `BCryptPasswordEncoder` tal cual).
Desventaja: `application` importaría `org.springframework.security.crypto`,
violando la regla ArchUnit "application depende solo de domain" (AC-020).
**Descartada.** El puerto `PasswordHasher` con una implementación (`BcryptPasswordHasher`)
es el mecanismo hexagonal estándar, no polimorfismo especulativo.

---

## Consequences

### Positive

- Producción queda con la emisión en un solo lugar (`JwtService`), eliminando
  el riesgo de drift entre producción y test que documentó ADR-004.
- `application` permanece pura (ArchUnit sigue pasando): los use cases
  dependen de puertos de dominio (`PasswordHasher`, `TokenEmisor`,
  `UsuarioRepository`), no de Spring ni de infraestructura.
- El login es testeable de punta a punta sin HTTP (claims verificados con
  `TokenEmisor` mockeado) y el round-trip `emitir→validar` se cubre con unit
  test.
- `sub` = `username` cumple el contrato exigido por SPEC-003 (FR-002, AC-008)
  sin tocar validación, filtro ni propiedad (ADR-004 §5).

### Negative

- `JwtService` acumula dos responsabilidades (emitir + validar). Mitigación:
  decisión explícita en este ADR; si creciera, se extrae `TokenIssuer` sin
  cambiar los use cases (ver Alternative A).
- `JwtTokenFactory` (test) debe mantenerse alineado al contrato real
  (`sub` = username); los call-sites existentes de `ClienteApiIntegrationTest`
  se actualizan de forma mecánica (comportamiento sin cambios: la validación no
  lee `sub`).
- La expiración del token pasa a leerse en dos puntos de `JwtService`
  (emisión) y `JwtTokenFactory` (test) desde la misma propiedad
  `banco.security.jwt-expiration-minutes` — mismo valor de configuración,
  sin drift.

---

## Related Documents

- `docs/specs/SPEC-003-autenticacion.md` (§5, §10, FR-002, BR-001, A-004)
- `docs/architecture/SPEC-003.md` (§5.5, §6.3, §8.4, §8.5, §11)
- `docs/adr/ADR-003-jwt-spring-security.md` (JWT + BCrypt + RBAC)
- `docs/adr/ADR-004-autenticacion-minima-jwt-sprint1.md` (§5 — camino de
  extensión; decisiones 1 y 2 superadas en parte por este ADR)
- `ARCHITECTURE.md` §8 (seguridad)
