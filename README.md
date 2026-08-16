# Sistema Bancario

Sistema bancario de portfolio: gestión de clientes, cuentas, movimientos,
transferencias, depósitos/retiros y autenticación con roles.

Desarrollado con un flujo **Specification-Driven Development (SDD)** impulsado
por agentes de opencode (orchestrator + 5 roles).

## Stack

- **Backend:** Java 21, Spring Boot 3.x (Maven), arquitectura hexagonal + DDD.
- **Frontend:** React 18 + TypeScript + Vite.
- **Base de datos:** PostgreSQL 16, migraciones con Flyway.
- **Seguridad:** Spring Security + JWT (roles `CLIENTE` / `ADMIN`), BCrypt.

## Estructura

```
.
├── backend/            # API Spring Boot (domain / application / infrastructure)
├── frontend/           # SPA React
├── docker/             # docker-compose (PostgreSQL local)
├── docs/
│   ├── specs/          # especificaciones del MVP (SPEC-XXX)
│   ├── architecture/   # diseño por feature
│   ├── adr/            # Architecture Decision Records
│   ├── sprints/        # backlog + roadmap
│   ├── domain/         # glosario del dominio
│   └── reviews/        # reportes de revisión
├── .opencode/agents/   # agentes SDD
├── vps/                # capa autónoma (webhook + worker) para el VPS
└── ARCHITECTURE.md     # arquitectura de referencia
```

## Documentación clave

- **Arquitectura:** [`ARCHITECTURE.md`](ARCHITECTURE.md)
- **Decisiones:** [`docs/adr/`](docs/adr/)
- **Backlog y roadmap:** [`docs/sprints/`](docs/sprints/)
- **Especificaciones del MVP:** [`docs/specs/`](docs/specs/)

## Ejecución local

```bash
# 1. Levantar PostgreSQL
cd docker
docker compose up -d postgres

# 2. Backend (cuando exista el pom.xml, lo generan los agentes en Sprint 1)
cd ../backend
mvn spring-boot:run

# 3. Frontend (Sprint 4)
cd ../frontend
npm install && npm run dev
```

## Modelo de ramas

```
feature/*   →   testing   →   main
```

## Flujo SDD

```
orchestrator → analyst → architect → developer → reviewer → code-reviewer
```

El merge se ejecuta solo cuando `reviewer = PASS` y `code-reviewer = APPROVE`.

Ver [`AGENTS.md`](AGENTS.md) para las reglas completas.
