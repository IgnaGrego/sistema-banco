# SPEC-001 — Gestión de Clientes

## Status

Draft

---

## 1. Objective

Permitir el alta, consulta, actualización y listado de clientes del banco.
Un cliente es la persona titular de una o más cuentas.

---

## 2. Actors

- **ADMIN**: crea, actualiza, lista y consulta clientes.
- **CLIENTE**: consulta su propio perfil.

---

## 3. Preconditions

- El usuario debe estar autenticado (JWT válido).
- Para alta/edición, el usuario debe tener rol `ADMIN`.

---

## 4. Functional Requirements

### FR-001

Crear un cliente con: nombre, apellido, DNI, email, teléfono.

### FR-002

Consultar un cliente por id (propio si es `CLIENTE`, cualquiera si es `ADMIN`).

### FR-003

Actualizar los datos de un cliente.

### FR-004

Listar clientes (solo `ADMIN`).

### FR-005

El sistema registra `fechaAlta` automáticamente al crear.

---

## 5. Business Rules

### BR-001

El `DNI` es único: no pueden existir dos clientes con el mismo DNI.

### BR-002

`DNI` debe contener solo dígitos y tener entre 7 y 8 caracteres.

### BR-003

`email` debe tener formato válido y ser único.

### BR-004

`nombre` y `apellido` son obligatorios y no vacíos.

---

## 6. Main Flow

1. `ADMIN` envía los datos del cliente.
2. El sistema valida los datos (VO `DNI`, email, campos obligatorios).
3. El sistema verifica unicidad de DNI y email.
4. El sistema persiste el cliente y devuelve su representación con `id`.

---

## 7. Alternative Flows

### AF-001

`CLIENTE` consulta su perfil: el sistema devuelve solo el cliente vinculado a
su usuario.

---

## 8. Error Cases

### ERR-001

DNI o email duplicado.

Se responde `409 Conflict` con el detalle del campo duplicado.

### ERR-002

DNI inválido (no numérico o longitud incorrecta).

Se responde `400 Bad Request`.

### ERR-003

`CLIENTE` intenta consultar un cliente ajeno.

Se responde `403 Forbidden`.

---

## 9. Authorization

- Crear/editar/listar: solo `ADMIN`.
- Consultar: `ADMIN` (cualquiera) o `CLIENTE` (solo el propio).

---

## 10. Data Changes

- Creación de la entidad `Cliente` (id, nombre, apellido, dni, email, teléfono,
  fechaAlta).
- Migración Flyway correspondiente.

---

## 11. Acceptance Criteria

- [ ] Un `ADMIN` puede crear un cliente y obtener su `id`.
- [ ] Crear dos clientes con el mismo DNI devuelve `409`.
- [ ] Crear un cliente con DNI no numérico devuelve `400`.
- [ ] Un `CLIENTE` solo puede consultar su propio perfil; consultar otro da `403`.
- [ ] Listar clientes sin rol `ADMIN` devuelve `403`.

---

## 12. Out of Scope

- Vinculación automática cliente→usuario (cubierta en SPEC-003).
- Estados/ediciones masivas.

---

## 13. Dependencies

- SPEC-003 (autenticación) para el control de acceso.

---

## 14. Open Questions

- Ninguna.

---

## 15. Related Documents

- `ARCHITECTURE.md` §4
- `docs/domain/`
