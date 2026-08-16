# SPEC-004 — Transferencias entre Cuentas

## Status

Approved

---

## 1. Objective

Permitir a un cliente transferir dinero entre cuentas (propias o de terceros)
de forma atómica, debitando el origen y acreditando el destino en una única
transacción de base de datos (ACID). Cada transferencia registra dos
`Movimiento` y emite el evento de dominio `TransferenciaRealizada`.

---

## 2. Actors

- **CLIENTE**: inicia transferencias desde sus propias cuentas (US-4.1) y
  consulta el historial de movimientos de sus cuentas (US-4.2).
- **ADMIN**: consulta el historial de movimientos de cualquier cuenta
  (solo lectura; no inicia transferencias — ver §9 y A-005).

---

## 3. Preconditions

- El usuario debe estar autenticado con un JWT válido (SPEC-003 FR-003).
- Para transferir, el token debe tener rol `CLIENTE` e incluir el claim
  `clienteId` que identifica al titular de la cuenta origen (SPEC-003 FR-002,
  BR-004).
- Existe la cuenta origen (por `cuentaOrigenId`) y pertenece al cliente
  autenticado.
- Existe la cuenta destino (por `CBU`).
- Ambas cuentas están en estado `ACTIVA`.
- El monto es mayor a 0 y está expresado en `ARS` (moneda del MVP).
- Existe la configuración del límite diario (valor por defecto `200000` ARS —
  A-002).

---

## 4. Functional Requirements

### FR-001 — Transferir

`POST /api/v1/transferencias` — transfiere un monto desde una cuenta origen
propia a una cuenta destino identificada por `CBU` (puede ser propia —otra
cuenta del mismo cliente— o de un tercero).

Request:

```json
{
  "cuentaOrigenId": 10,
  "cbuDestino": "0000003100000000001234",
  "monto": 15000.00
}
```

Responde `201 Created` con la confirmación:

```json
{
  "idTransferencia": 501,
  "monto": 15000.00,
  "cbuDestino": "0000003100000000001234",
  "fechaHora": "2026-08-16T10:30:00Z"
}
```

`idTransferencia` es el id del `Movimiento` `TRANSFERENCIA_SALIENTE` generado
(no existe una entidad `Transferencia` separada — ver A-007).

### FR-002 — Atomicidad

Debitar el origen y acreditar el destino en la **misma transacción** de base
de datos (ACID, ADR-002). Si falla cualquier paso, se hace rollback total: no
queda débito, crédito ni movimientos parciales.

### FR-003 — Registro de movimientos

Registrar exactamente dos `Movimiento` por transferencia:

- `TRANSFERENCIA_SALIENTE` en la cuenta origen (contraparte: cuenta destino).
- `TRANSFERENCIA_ENTRANTE` en la cuenta destino (contraparte: cuenta origen).

Ambos con el mismo `monto` y `fecha`.

### FR-004 — Evento de dominio

Emitir el evento de dominio `TransferenciaRealizada` al completar la
operación, con los datos de la transferencia (monto, `cbuOrigen`,
`cbuDestino`, `fechaHora`, `idMovimientoSaliente`). En este sprint el evento
no tiene suscriptores (auditoría/notificación quedan fuera de alcance); su
emisión se verifica en tests (AC-003).

### FR-005 — Historial de movimientos

`GET /api/v1/cuentas/{id}/movimientos` — devuelve el historial de movimientos
de una cuenta (`200 OK`), ordenado por `fecha` descendente (más recientes
primero) y sin paginación en el MVP (A-006). Incluye los movimientos
generados por transferencias (FR-003).

---

## 5. Business Rules

### BR-001 — Saldo suficiente (dominio)

El saldo de la cuenta origen debe ser suficiente: el saldo nunca queda
negativo. Es un invariante del agregado `Cuenta`: el método de dominio
`debitar(monto)` lanza `SaldoInsuficienteException` si `monto > saldo`, y
`acreditar(monto)` incrementa el saldo.

### BR-002 — Cuentas ACTIVA

Ambas cuentas (origen y destino) deben estar en estado `ACTIVA`. Una cuenta
`BLOQUEADA` no participa en transferencias.

### BR-003 — Monto válido

El monto debe ser mayor a 0, con hasta 2 decimales, expresado en `ARS`
(moneda del MVP). Monto `<= 0`, no numérico o con más de 2 decimales se
rechaza como dato inválido.

### BR-004 — Límite diario

Existe un límite diario de transferencias por cliente (por defecto
`ARS 200.000`). Se computa como la suma de los montos de los movimientos
`TRANSFERENCIA_SALIENTE` del día calendario actual sobre **todas** las cuentas
del cliente. El valor del límite es global y configurable (A-002); los límites
por cliente individual quedan fuera del MVP.

### BR-005 — Sin auto-transferencia

No se puede transferir de una cuenta a sí misma: el `CBU` destino debe ser
distinto del `CBU` de la cuenta origen. Transferir entre **dos cuentas
distintas del mismo cliente** sí está permitido (objetivo: "cuentas propias").

### BR-006 — Concurrencia (optimistic lock)

`@Version` en `Cuenta` evita saldos inconsistentes bajo concurrencia. Ante un
conflicto de versión (`OptimisticLockException`) la operación se rechaza con
`409` y el cliente puede reintentar; **no** hay reintento automático
server-side (A-004). Nunca se pierde consistencia de saldo.

### BR-007 — Moneda compatible

Las cuentas origen y destino deben tener la misma moneda (`Money.moneda()`
igual — mismo código ISO 4217 alpha-3 del VO `Moneda`). En el MVP solo existe
`ARS`, por lo que la regla se verifica siempre y se cubre con unit test
(AC-015).

### Ubicación de las reglas en capas (AGENTS.md §11)

- **domain** (agregado `Cuenta`, VOs): BR-001 (invariante de saldo en
  `debitar`/`acreditar`), BR-006 (parcial: `@Version` en la entidad),
  BR-007 (parcial: `Money`/`Moneda`). Los VOs `CBU`, `Money` y `Moneda`
  validan en su constructor.
- **application** (use case + Chain of Responsibility): BR-002, BR-003,
  BR-004, BR-005, BR-007 (comparación de monedas) y la verificación de
  propiedad/permisos de §9. El use case orquesta la transacción (FR-002) y la
  emisión del evento (FR-004).
- **infrastructure**: solo adaptadores (JPA, REST, security, configuración del
  límite). Sin reglas de negocio en controllers ni en migraciones.

---

## 6. Main Flow

1. `CLIENTE` envía `POST /api/v1/transferencias` con `cuentaOrigenId`,
   `cbuDestino` y `monto`.
2. El sistema resuelve el `clienteId` del token (SPEC-003) y valida la cadena
   (Chain of Responsibility, en orden y cortando ante el primer error):
   1. La cuenta origen existe → `404` si no (ERR-002).
   2. La cuenta origen pertenece al cliente autenticado → `403` si es ajena
      (ERR-006).
   3. La cuenta origen está `ACTIVA` → `422` si está `BLOQUEADA` (ERR-003).
   4. La cuenta destino (por `CBU`) existe → `404` si no (ERR-002).
   5. El destino es distinto del origen → `422` si es la misma cuenta
      (ERR-008).
   6. La cuenta destino está `ACTIVA` → `422` si está `BLOQUEADA` (ERR-003).
   7. `monto > 0` (hasta 2 decimales) → `400` si es inválido (ERR-004).
   8. Monedas de origen y destino compatibles → `422` si no (ERR-009).
   9. Saldo suficiente en origen → `422` si no (ERR-001).
   10. Límite diario del cliente no superado → `422` si se supera (ERR-007).
3. En una única transacción de base de datos se debita el origen (FR-002),
   se acredita el destino (FR-002) y se persisten los dos `Movimiento`
   (FR-003).
4. Se emite `TransferenciaRealizada` (FR-004).
5. Se responde `201 Created` con la confirmación (FR-001, A-007).

---

## 7. Alternative Flows

### AF-001 — Destino inexistente

El `CBU` destino no corresponde a ninguna cuenta: se aborta la operación sin
débito ni crédito (se responde `404` — ERR-002).

### AF-002 — Límite diario superado

La suma de los salientes del día del cliente alcanza o supera el límite: se
rechaza la transferencia sin débito (se responde `422` — ERR-007).

### AF-003 — Transferencia entre cuentas propias

El `CBU` destino pertenece a otra cuenta del mismo cliente: permitida
(BR-005). Fluye igual que el main flow; los dos movimientos se registran en
las dos cuentas del cliente.

---

## 8. Error Cases

Todos los errores usan el envelope JSON estándar
(`{ code, message, details? }` — `ARCHITECTURE.md` §7) con códigos HTTP
correctos (400 validación, 401/403 auth, 404 no encontrado, 409
conflicto/concurrencia, 422 regla de negocio).

### ERR-001 — Saldo insuficiente

Saldo de la cuenta origen menor al monto solicitado.

Se responde `422 Unprocessable Entity` con código `SALDO_INSUFICIENTE`. No se
altera ningún saldo ni se registran movimientos.

### ERR-002 — Cuenta origen/destino inexistente

`cuentaOrigenId` no existe, o el `CBU` destino no corresponde a ninguna
cuenta (AF-001).

Se responde `404 Not Found` con código `CUENTA_NO_ENCONTRADA`.

### ERR-003 — Cuenta BLOQUEADA

La cuenta origen o la cuenta destino está en estado `BLOQUEADA` (BR-002).

Se responde `422 Unprocessable Entity` con código `CUENTA_BLOQUEADA`.

### ERR-004 — Monto inválido

`monto <= 0`, no numérico o con más de 2 decimales (BR-003).

Se responde `400 Bad Request` con código `DATOS_INVALIDOS` (misma semántica
que SPEC-001 ERR-002) y `details` indicando el campo `monto`.

### ERR-005 — Conflicto de concurrencia

`@Version` detecta que la cuenta origen fue modificada por otra operación
mientras se ejecutaba la transferencia (BR-006).

Se responde `409 Conflict` con código `CONFLICTO_CONCURRENCIA`; el cliente
puede reintentar la operación (A-004).

### ERR-006 — Cuenta ajena / rol sin permiso

`CLIENTE` intenta transferir desde una cuenta que no le pertenece, o consulta
el historial de una cuenta ajena; o un usuario sin el rol requerido intenta la
operación (por ejemplo, `ADMIN` iniciando una transferencia).

Se responde `403 Forbidden` con código `ACCESO_DENEGADO` (misma semántica que
SPEC-003 ERR-003).

### ERR-007 — Límite diario superado

La suma de los salientes del día del cliente alcanza o supera el límite
configurado (BR-004, AF-002).

Se responde `422 Unprocessable Entity` con código `LIMITE_DIARIO_EXCEDIDO`. No
se altera ningún saldo.

### ERR-008 — Auto-transferencia

El `CBU` destino es el `CBU` de la propia cuenta origen (BR-005).

Se responde `422 Unprocessable Entity` con código `AUTO_TRANSFERENCIA`.

### ERR-009 — Moneda incompatible

La moneda de la cuenta origen difiere de la moneda de la cuenta destino
(BR-007).

Se responde `422 Unprocessable Entity` con código `MONEDA_INCOMPATIBLE`.

---

## 9. Authorization

- `POST /api/v1/transferencias`: solo rol `CLIENTE` (el `ADMIN` no inicia
  transferencias). La cuenta origen debe pertenecer al cliente autenticado
  (claim `clienteId` del JWT — SPEC-003 FR-002); una cuenta ajena responde
  `403` (ERR-006). El destino puede ser cualquier cuenta del sistema (propia
  u de otro cliente).
- `GET /api/v1/cuentas/{id}/movimientos`: `ADMIN` (cualquier cuenta) o
  `CLIENTE` (solo sus propias cuentas; una cuenta ajena responde `403` —
  ERR-006). Ver A-005.
- Toda request requiere JWT válido (SPEC-003 FR-003); la autorización se
  verifica server-side (nunca solo en el frontend).

---

## 10. Data Changes

- **Entidad `Cuenta`** (agregado raíz, ya implementado por SPEC-002 — ver
  A-008): `id`, `clienteId`, `cbu` (VO `CBU`), `tipo`
  (`CAJA_AHORRO` | `CUENTA_CORRIENTE`), `saldo` (VO `Money`), `moneda`
  (VO `Moneda` — `ARS`), `estado` (`ACTIVA` | `BLOQUEADA`), `createdAt`,
  `@Version`; la creación la centraliza `CuentaFactory` de SPEC-002 (saldo
  inicial `0`, estado `ACTIVA`, `CBU` asignado). SPEC-004 le agrega los
  métodos de dominio `debitar(monto)` y `acreditar(monto)` (invariante de
  saldo `>= 0` — BR-001) sobre el agregado existente.
- **Value Objects** (dominio, inmutables — implementados por SPEC-002): `CBU`
  (validado, único — BR de SPEC-002 §5.1) y `Money` (record `(BigDecimal
  monto, Moneda moneda)`, inmutable, saldo `>= 0` — invariante BR-001; en
  este sprint se le agregan las operaciones
  `sumar`/`restar`/`esMayorQue`/`esMayorOIgualQue`/`esCero` y la factory
  `ars(BigDecimal)`); `Moneda` record `(String codigo)` con formato ISO 4217
  alpha-3 (mantiene la decisión de SPEC-002 §13: "Record con String, no
  enum").
- **CBU — formato**: exactamente 22 dígitos (`^[0-9]{22}$`), validado en el VO
  en su constructor.
- **Entidad `Movimiento`** (parte del agregado `Cuenta`): `id`, `cuentaId`,
  `tipo` (`DEPOSITO` | `RETIRO` | `TRANSFERENCIA_ENTRANTE` |
  `TRANSFERENCIA_SALIENTE`), `monto`, `fecha`, `cuentaContraparteId`
  (cuenta opuesta de la operación; nullable para operaciones sin contraparte).
- **Puerto `CuentaRepository`** (dominio): el puerto de SPEC-002 ya define
  `save`, `findById`, `findByCbu` y `findByClienteId`; SPEC-004 le agrega la
  consulta de agregación del límite diario (suma de `TRANSFERENCIA_SALIENTE`
  del día por cliente — BR-004). Lo implementa un adaptador JPA en
  `infrastructure`.
- **`@Version`** en la entidad JPA de `Cuenta` (columna `version`) para
  optimistic locking (BR-006).
- **Migración Flyway** `V4__movimientos.sql` (NOTA: la tabla `cuentas` ya la
  crea SPEC-002 en `V3__cuentas.sql`, incluyendo la columna `version` para
  `@Version` — BR-006): solo agrega la tabla `movimientos` (`cuenta_id`
  `FOREIGN KEY` → `cuentas(id)`, `tipo`, `monto` `DECIMAL`, `fecha`,
  `cuenta_contraparte_id` nullable) e índice `idx_movimientos_cuenta_fecha`
  sobre `(cuenta_id, fecha)` para historial y límite diario.
- **Configuración**: clave `banco.negocio.limite-diario-transferencias`
  (default `200000`, en `ARS`) en `application.yml` (A-002), consistente con
  el naming `banco.security.*` existente.
- **Evento de dominio** `TransferenciaRealizada` (FR-004).
- **Excepciones de dominio** para los errores de §8
  (`SaldoInsuficienteException`, `CuentaNoEncontradaException`,
  `CuentaBloqueadaException`, `LimiteDiarioExcedidoException`,
  `AutoTransferenciaException`, `MonedaIncompatibleException`, reuso de
  `DatosInvalidosException` para el monto), mapeadas al envelope en
  `GlobalExceptionHandler`.

---

## 11. Acceptance Criteria

Transferencia (`POST /api/v1/transferencias`):

- [ ] AC-001: `CLIENTE` autenticado transfiere desde una cuenta propia
      `ACTIVA` con saldo suficiente hacia un `CBU` válido → `201`; el saldo de
      origen se debita y el de destino se acredita exactamente por el monto
      (FR-001, FR-002, BR-001).
- [ ] AC-002: Se registran exactamente dos movimientos por transferencia:
      `TRANSFERENCIA_SALIENTE` en origen y `TRANSFERENCIA_ENTRANTE` en
      destino, con el mismo monto y fecha, y contraparte apuntando a la cuenta
      opuesta (FR-003).
- [ ] AC-003: Unit test — el use case emite `TransferenciaRealizada` con
      monto, `cbuOrigen`, `cbuDestino` y `fechaHora` al completar la operación
      (FR-004).
- [ ] AC-004: Transferencia con saldo insuficiente → `422
      SALDO_INSUFICIENTE`; no se modifica ningún saldo ni se registran
      movimientos (BR-001, ERR-001).
- [ ] AC-005: `CBU` destino inexistente → `404 CUENTA_NO_ENCONTRADA`; sin
      débito (AF-001, ERR-002).
- [ ] AC-006: Cuenta origen `BLOQUEADA` → `422 CUENTA_BLOQUEADA`
      (BR-002, ERR-003).
- [ ] AC-007: Cuenta destino `BLOQUEADA` → `422 CUENTA_BLOQUEADA`
      (BR-002, ERR-003).
- [ ] AC-008: `monto <= 0` o con más de 2 decimales → `400 DATOS_INVALIDOS`
      con `details` del campo `monto` (BR-003, ERR-004).
- [ ] AC-009: Destino = `CBU` de la propia cuenta origen → `422
      AUTO_TRANSFERENCIA` (BR-005, ERR-008).
- [ ] AC-010: Suma de salientes del día del cliente `>=` límite configurado →
      `422 LIMITE_DIARIO_EXCEDIDO`; sin débito (BR-004, AF-002, ERR-007).
- [ ] AC-011: El límite diario se computa por cliente (todas sus cuentas),
      solo con `TRANSFERENCIA_SALIENTE` del día calendario, y el valor por
      defecto es `200000` ARS configurable vía
      `banco.negocio.limite-diario-transferencias` (BR-004, A-002).
- [ ] AC-012: Concurrencia — dos transferencias concurrentes sobre la misma
      cuenta origen cuyos montos juntos exceden el saldo: una responde `201` y
      la otra `409 CONFLICTO_CONCURRENCIA`; el saldo final es consistente y
      nunca negativo (BR-006, ERR-005, A-004).
- [ ] AC-013: `CLIENTE` transfiere desde un `cuentaOrigenId` de otro cliente →
      `403 ACCESO_DENEGADO` (ERR-006, §9).
- [ ] AC-014: Sin token responde `401`; token `ADMIN` responde `403` (solo
      `CLIENTE` transfiere) (SPEC-003 FR-003, §9).
- [ ] AC-015: Unit test — transferencia entre cuentas de moneda distinta →
      `422 MONEDA_INCOMPATIBLE` (BR-007, ERR-009).

Historial (`GET /api/v1/cuentas/{id}/movimientos`):

- [ ] AC-016: `CLIENTE` consulta el historial de una cuenta propia → `200` con
      los movimientos ordenados por fecha descendente, incluidos los de
      transferencias (FR-005, A-006).
- [ ] AC-017: `CLIENTE` consulta el historial de una cuenta ajena → `403
      ACCESO_DENEGADO` (ERR-006, §9, A-005).
- [ ] AC-018: `ADMIN` consulta el historial de cualquier cuenta → `200`
      (FR-005, §9, A-005).
- [ ] AC-019: Historial de una cuenta inexistente → `404 CUENTA_NO_ENCONTRADA`
      (ERR-002).
- [ ] AC-020: Sin token responde `401` (SPEC-003 FR-003).

Pruebas y arquitectura:

- [ ] AC-021: Tests unitarios cubren los VOs (`CBU`, `Money`), el agregado
      `Cuenta` (`debitar`/`acreditar`, invariante de saldo) y la cadena de
      validación de la transferencia (orden y errores)
      (BR-001..BR-007, ERR-001..ERR-009).
- [ ] AC-022: Tests de integración con Testcontainers cubren los endpoints
      `POST /api/v1/transferencias` y `GET /api/v1/cuentas/{id}/movimientos`
      y los códigos `400`/`401`/`403`/`404`/`409`/`422`, incluidos la
      concurrencia (AC-012) y el límite diario (AC-010, AC-011)
      (FR-001..FR-005).
- [ ] AC-023: ArchUnit (`mvn verify`) sigue pasando con las nuevas clases:
      `domain` sin dependencias de Spring (nuevas entidades/VOs), `application`
      dependiendo solo de `domain`, y Spring/controllers solo en
      `infrastructure` (AGENTS.md §11).

---

## 12. Out of Scope

- Apertura, consulta y listado de cuentas vía API (SPEC-002; esta spec solo
  incorpora el agregado `Cuenta` mínimo — A-001).
- Depósitos y retiros (SPEC-005).
- Transferencias interbancarias externas (solo cuentas internas por `CBU`).
- Comisiones por tipo de cuenta (Strategy preparado; evolución).
- Límite diario configurable por cliente individual (A-002: valor global).
- Reintento automático server-side ante conflicto de concurrencia (A-004).
- Paginación y filtros en el historial de movimientos (A-006).
- Suscriptores del evento `TransferenciaRealizada` (auditoría/notificación;
  evolución).
- Monedas distintas de `ARS` (MVP).
- Frontend (E6).

---

## 13. Dependencies

- **SPEC-003 (autenticación)**: JWT con claims `role` y `clienteId`
  (implementada) — prerrequisito de §9 y del paso 2 del main flow.
- **SPEC-001 (clientes)**: semántica del claim `clienteId` (vinculación
  `Usuario` ↔ `Cliente`, SPEC-003 FR-005).
- **SPEC-002 (cuentas)**: NO implementada (ver A-001). Esta spec incorpora el
  agregado `Cuenta` mínimo requerido por las transferencias; cuando SPEC-002
  se implemente, debe alinearse con el modelo definido aquí (§10).
- **SPEC-005 (depósitos/retiros)**: mismo sprint; comparte `Cuenta` y
  `Movimiento` y se beneficia de lo incorporado por A-001.
- **ADR-001** (monolito hexagonal + DDD), **ADR-002** (PostgreSQL + Flyway),
  **ADR-003** (JWT + Spring Security + BCrypt + RBAC).

---

## 14. Open Questions

- Ninguna. Las ambigüedades detectadas en la draft (límite diario
  configurable por cliente) y en el contexto del repositorio (gap de
  SPEC-002) se resolvieron en modo autónomo como asunciones documentadas en
  §15.

---

## 15. Decisiones y Asunciones

Las siguientes decisiones no estaban definidas explícitamente en la
documentación existente; se documentan como asunciones con la interpretación
recomendada, resueltas en modo autónomo. Si el Product Owner dispone otra
cosa, deben ajustarse antes de la implementación.

- **A-001 — Incorporación del agregado `Cuenta` mínimo (resolución del gap de
  SPEC-002):** SPEC-002 (cuentas) figura como Draft en el roadmap (Sprint 1)
  pero nunca se implementó: el código no tiene entidad `Cuenta`, ni VOs
  `CBU`/`Money`, ni migración de cuentas, ni `CuentaRepository`. SPEC-004 no
  puede existir sin el agregado `Cuenta` (saldo, `CBU`, estado, `@Version`).
  Por lo tanto, esta spec incorpora el agregado `Cuenta` **mínimo** requerido
  por las transferencias: entidad (`id`, `clienteId`, `cbu`, `tipo`, `saldo`,
  `moneda`, `estado`, `createdAt`, `@Version`), VOs `CBU` y `Money`, puerto
  `CuentaRepository`, métodos de dominio `debitar`/`acreditar` y factory de
  creación por tipo (§10). **Scope control:** NO se incluye el alcance completo
  de SPEC-002 (endpoints de apertura/consulta/listado de cuentas); cuando
  SPEC-002 se implemente, debe alinearse con este modelo.
- **A-002 — Límite diario global configurable (resolución de la pregunta
  abierta de la draft):** el límite diario es un valor **global** configurable
  (no por cliente): clave `banco.negocio.limite-diario-transferencias`,
  default `200000` (ARS), consistente con el naming `banco.security.*` de
  `application.yml`. Se aplica **por cliente** por día calendario, sumando los
  `TRANSFERENCIA_SALIENTE` de todas sus cuentas (BR-004). Límites por cliente
  individual quedan fuera del MVP.
- **A-003 — Creación de cuentas en tests (helper de repositorio):** como no
  existen endpoints de cuentas (SPEC-002 no implementada), los tests de
  integración crean las cuentas directamente vía el puerto `CuentaRepository`
  (helper de test que usa la API de dominio: creación por factory con saldo
  `0` y `acreditar` para fondear saldo), NO vía HTTP. Justificación: mantiene
  el alcance de SPEC-004 acotado al flujo de transferencia (no se implementan
  endpoints de apertura/consulta que pertenecen a SPEC-002) y los tests
  ejercitan el feature bajo prueba en lugar del CRUD de cuentas. La apertura
  de cuentas en producción queda para SPEC-002.
- **A-004 — Sin reintento automático ante concurrencia:** ante un conflicto de
  `@Version` la operación se rechaza con `409 CONFLICTO_CONCURRENCIA` y el
  cliente puede reintentar; no hay reintento automático server-side
  (`ARCHITECTURE.md` §6 admite "reintenta o rechaza"; se elige rechazar por
  predecibilidad y testabilidad).
- **A-005 — Autorización del historial (FR-005):** `GET
  /api/v1/cuentas/{id}/movimientos` sigue el patrón de consulta de SPEC-002
  §9: `ADMIN` consulta cualquier cuenta y `CLIENTE` solo las propias (`403` si
  ajena). Las transferencias (`POST /api/v1/transferencias`) quedan
  restringidas a `CLIENTE` sobre sus propias cuentas de origen; `ADMIN` no
  inicia transferencias.
- **A-006 — Historial sin paginación y orden fijo:** el historial se devuelve
  completo, ordenado por `fecha` descendente (más recientes primero), sin
  paginación ni filtros en el MVP (consistente con SPEC-001 A-003).
- **A-007 — Identificador de la transferencia:** no existe una entidad
  `Transferencia` (el agregado es `Cuenta` con `Movimiento` como parte —
  `ARCHITECTURE.md` §4). El `idTransferencia` de la respuesta de FR-001 es el
  id del `Movimiento` `TRANSFERENCIA_SALIENTE` generado, que identifica la
  operación para conciliación.
- **A-008 — Reconciliación con SPEC-002 (SPEC-002 implementada):** esta spec
  se redactó antes de la implementación de SPEC-002; los VOs `Money`/`Moneda`
  y el agregado `Cuenta` conservan las formas APROBADAS e implementadas de
  SPEC-002 (record `Moneda(String codigo)`, record
  `Money(BigDecimal monto, Moneda moneda)`), y SPEC-004 los extiende
  (aritmética de `Money`, `debitar`/`acreditar` en `Cuenta`). BR-007 se
  evalúa por código ISO de la moneda.

---

## 16. Related Documents

- `ARCHITECTURE.md` §4 (agregado `Cuenta`, `Movimiento`, VOs), §5 (patrones:
  Aggregate Root, Factory, Strategy, CoR, optimistic lock, CQRS ligero), §6
  (transferencias), §7 (API REST y envelope de errores), §8 (seguridad), §9
  (persistencia)
- `docs/domain/glosario.md` (lenguaje ubicuo: `Cuenta`, `Movimiento`, `Saldo`,
  `CBU`, `Money`, `Transferencia`, `Límite diario`)
- `docs/adr/ADR-001-monolito-hexagonal-ddd.md`
- `docs/adr/ADR-002-postgresql-flyway.md`
- `docs/adr/ADR-003-jwt-spring-security.md`
- `docs/specs/SPEC-001-clientes.md` (claim `clienteId`, A-003)
- `docs/specs/SPEC-002-cuentas.md` (Draft — modelo de referencia; ver A-001)
- `docs/specs/SPEC-003-autenticacion.md` (FR-002/FR-003, BR-004)
- `docs/specs/SPEC-005-depositos-retiros.md` (Draft — comparte `Cuenta`/
  `Movimiento`)
- `docs/sprints/backlog.md` (E4: US-4.1, US-4.2), `docs/sprints/roadmap.md`
  (Sprint 3)
