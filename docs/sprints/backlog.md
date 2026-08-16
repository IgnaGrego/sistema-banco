# Product Backlog — Sistema Bancario

## Épicas

| ID | Épica | Descripción | Spec |
| --- | --- | --- | --- |
| E1 | Clientes | CRUD de clientes del banco | SPEC-001 |
| E2 | Cuentas | Apertura y consulta de cuentas | SPEC-002 |
| E3 | Autenticación | Login JWT + roles (RBAC) | SPEC-003 |
| E4 | Transferencias | Transferencias atómicas entre cuentas | SPEC-004 |
| E5 | Caja | Depósitos y retiros | SPEC-005 |
| E6 | Frontend | SPA React que consume la API | — |

## Historias de usuario (MVP)

### E1 — Clientes
- **US-1.1** Como `ADMIN`, quiero crear un cliente para darlo de alta en el
  sistema. *(AC: SPEC-001 §11)*
- **US-1.2** Como `ADMIN`, quiero listar/consultar clientes para gestionarlos.
- **US-1.3** Como `CLIENTE`, quiero ver mi propio perfil.

### E2 — Cuentas
- **US-2.1** Como `ADMIN`, quiero abrir una cuenta para un cliente.
- **US-2.2** Como `CLIENTE`, quiero listar mis cuentas y ver sus saldos.
- **US-2.3** Como sistema, quiero generar `CBU` únicos automáticamente.

### E3 — Autenticación
- **US-3.1** Como usuario, quiero registrarme y obtener credenciales.
- **US-3.2** Como usuario, quiero iniciar sesión y recibir un JWT.
- **US-3.3** Como sistema, quiero proteger endpoints por rol (`CLIENTE`/`ADMIN`).

### E4 — Transferencias
- **US-4.1** Como `CLIENTE`, quiero transferir dinero entre cuentas.
- **US-4.2** Como `CLIENTE`, quiero ver el historial de movimientos de una cuenta.

### E5 — Caja
- **US-5.1** Como `ADMIN`, quiero depositar en una cuenta.
- **US-5.2** Como `ADMIN`/`CLIENTE`, quiero retirar de una cuenta (según rol).

### E6 — Frontend
- **US-6.1** Como usuario, quiero una pantalla de login.
- **US-6.2** Como `CLIENTE`, quiero ver mis cuentas y saldo.
- **US-6.3** Como `CLIENTE`, quiero un formulario de transferencia.
- **US-6.4** Como `ADMIN`, quiero gestionar clientes y cuentas.

## Priorización

| Prioridad | Épica | Criterio |
| --- | --- | --- |
| P0 | E1, E2 | Base de datos de negocio (sin cuentas no hay operaciones) |
| P1 | E3 | Seguridad y autorización (prerrequisito de los flujos operativos) |
| P2 | E4, E5 | Operaciones transaccionales (núcleo del valor bancario) |
| P3 | E6 | Frontend (valor de demo/portfolio) |
