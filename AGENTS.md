# AGENTS.md — SDD (Specification-Driven Development)

## 1. Purpose

This repository is developed using a Specification-Driven Development (SDD)
workflow driven by a set of AI agents (an orchestrator and five specialized
roles).

The AI must treat the repository documentation as the source of truth.

The AI must not rely on previous conversations as authoritative project
knowledge.

If a decision is not documented in the repository, it must be considered
unknown unless it can be safely inferred from existing code and documentation.

---

## 2. Source of Truth

The priority order is:

1. Approved specifications
2. Architecture documentation
3. ADRs
4. Domain documentation
5. Product documentation
6. Existing implementation
7. AI assumptions

The AI must never use an assumption to override an explicit project decision.

---

## 3. SDD Workflow

All non-trivial functionality follows:

Product Requirement
→ Specification
→ Architecture
→ Implementation
→ Tests
→ Review (compliance + code quality)
→ Merge

The workflow is executed by the following roles:

| Role | Mode | Responsibility | Writes code? |
| --- | --- | --- | --- |
| orchestrator | primary | Drives the pipeline, manages feedback loops, executes the merge | Coordinates only |
| analyst | subagent | Business requirement → testable specification | No |
| architect | subagent | Specification → pragmatic technical design + ADRs | No |
| developer | subagent | Implements the approved design + tests | Yes |
| reviewer | subagent | Verifies implementation vs specification + architecture | No (read-only) |
| code-reviewer | subagent | Reviews code quality and decides merge approval | No (read-only) |

The responsibilities of each role are documented in:

- `.opencode/agents/orchestrator.md`
- `.opencode/agents/analyst.md`
- `.opencode/agents/architect.md`
- `.opencode/agents/developer.md`
- `.opencode/agents/reviewer.md`
- `.opencode/agents/code-reviewer.md`

---

## 4. Orchestration

The `orchestrator` agent owns the end-to-end pipeline:

1. Receive a requirement.
2. Delegate to `analyst` → specification.
3. Delegate to `architect` → architecture document and ADRs.
4. Delegate to `developer` → implementation + tests.
5. Delegate to `reviewer` → compliance verdict (PASS / FAIL).
6. Delegate to `code-reviewer` → quality verdict (APPROVE / REQUEST_CHANGES).
7. Merge only when both verdicts pass; otherwise route findings back to the
   responsible role and repeat.

Roles feed back into each other:

- `reviewer` / `code-reviewer` findings → back to `developer` to fix.
- `architect` finding the specification insufficient → back to `analyst`.
- `analyst` discovering ambiguity → documents an open question / assumption.

The merge is gated: `code-reviewer` decides approval, the `orchestrator`
performs the merge only on approval.

---

## 5. Analyst

The Analyst is responsible for understanding business requirements.

The Analyst must:

- analyze requirements;
- identify actors;
- identify preconditions;
- identify business rules;
- identify alternative flows;
- identify error cases;
- identify authorization requirements;
- identify missing information;
- define acceptance criteria.

The Analyst must NOT:

- implement application code;
- modify database schemas;
- introduce technical architecture;
- invent business rules.

When requirements are ambiguous, the Analyst documents the ambiguity as an
Open Question together with a recommended interpretation and marks the
assumption explicitly. In interactive sessions the Analyst may ask the user;
in autonomous mode it proceeds with the documented assumption.

---

## 6. Architect

The Architect translates approved requirements into a technical design.

The Architect must:

- inspect the existing architecture;
- identify affected modules;
- identify required data changes;
- identify application services/actions;
- identify authorization requirements;
- identify integration requirements;
- document significant architectural decisions.

The Architect must NOT implement the feature unless explicitly asked to do so
as a separate development task.

The Architect must avoid unnecessary abstraction.

---

## 7. Developer

The Developer implements an approved specification.

Before writing code, the Developer must read:

- AGENTS.md
- the project architecture documentation
- relevant product and domain documentation
- the feature specification
- the relevant architecture document
- relevant ADRs

The Developer must:

- implement the requested functionality;
- follow the existing architecture;
- add or update tests;
- run relevant tests;
- avoid unrelated changes.

The Developer must NOT:

- invent business rules;
- silently change requirements;
- perform unrelated refactoring;
- introduce dependencies without justification;
- change architectural conventions without documentation.

---

## 8. Reviewer (Compliance)

The Reviewer validates the implementation against the specification and
architecture. It is read-only and must not modify code.

The Reviewer returns `PASS` or `FAIL`, listing any violated requirement,
its location, expected vs actual behavior, and a recommended correction.

The Reviewer must not reject an implementation merely because another
architectural approach could also work, and must not introduce new
requirements during review.

---

## 9. Code Reviewer (Quality)

The Code Reviewer evaluates code quality and owns the merge approval decision.
It is read-only and must not modify code.

The Code Reviewer checks, at minimum:

- readability and maintainability;
- security;
- performance;
- adherence to project conventions;
- test coverage and quality;
- dead code, duplication, and error handling.

The Code Reviewer returns `APPROVE` or `REQUEST_CHANGES`. `REQUEST_CHANGES`
lists concrete, prioritized findings. `APPROVE` is the gate that authorizes
the `orchestrator` to merge.

---

## 10. Specification First

A non-trivial feature must have an approved specification under:

`docs/specs/`

Do not implement a feature based only on a natural-language request if the
required business behavior is not sufficiently defined.

If the request is ambiguous, STOP and report:

1. What is ambiguous.
2. Why it matters.
3. Possible interpretations.
4. Recommended interpretation.

---

## 11. Business Logic

Business rules must live in the `domain` and `application` layers, never in
controllers, DTO mappers, or migrations.

- `domain` — entities, value objects (e.g. `Money`, `CBU`, `DNI`), business
  invariants, domain services, ports (interfaces). It must NOT depend on
  Spring or any infrastructure framework (enforced by ArchUnit).
- `application` — use cases (commands/queries), validation pipeline
  (Chain of Responsibility), and transactional coordination.
- `infrastructure` — adapters (JPA persistence, REST controllers, security),
  and configuration. It implements the ports defined by the domain.

Controllers coordinate behavior; they do not contain business rules.
Important business operations use explicit use cases.

Do not create abstractions merely to satisfy a theoretical architecture.
Prefer the simplest correct solution.

---

## 12. Testing

Every new business feature must include tests.

Tests should cover:

- happy path;
- validation;
- business rules;
- authorization;
- relevant failure cases.

Do not delete or weaken existing tests merely to make new code pass.

Before considering a feature complete:

- run the relevant tests;
- run the complete test suite when practical.

---

## 13. Dependencies

Do not install a package without justification.

Before adding a dependency, document:

- problem being solved;
- why existing functionality is insufficient;
- package purpose;
- impact on architecture.

---

## 14. Scope Control

Only modify what is necessary for the requested task.

Do not perform unrelated refactoring.

If a discovered issue is unrelated, document it instead of silently fixing it.

---

## 15. Git Branching

The repository uses a three-tier branching model:

```
feature/*   →   testing   →   main
```

- **main** — production-stable. Protected; only merges from `testing` via
  pull request, never direct commits.
- **testing** — integration branch. Receives feature branches.
- **feature/*** — one branch per spec/task, created from `testing`, merged to
  `testing` via pull request.

The `orchestrator` creates the feature branch, opens the pull request, and
merges only after the `code-reviewer` approves.

---

## 16. Architectural Decisions

Important architectural decisions must be documented as ADRs under:

`docs/adr/`

---

## 17. Security

Never expose passwords, secrets, API keys, tokens, or private credentials.

Do not commit `.env` files containing secrets.

Authorization must be enforced server-side; never rely only on frontend
restrictions.

Passwords must be stored only as strong hashes (BCrypt), never in plain text
or reversible encryption.

---

## 18. Completion

A feature is complete only when:

- specification is satisfied;
- implementation exists;
- tests exist;
- tests pass;
- authorization is correct;
- documentation is updated where required;
- reviewer returned PASS;
- code-reviewer returned APPROVE;
- the pull request is merged.

---

## 19. When in Doubt

Do not guess about business behavior. Ask (or document the assumption).

Do not introduce architectural complexity without justification. Prefer the
simplest correct solution.

---

## 20. Project Context

- **Stack / languages:** Java 21, Spring Boot 3.x (Maven), React 18 + TypeScript
  + Vite, PostgreSQL 16.
- **Architecture:** modular monolith, hexagonal (ports & adapters) + DDD
  tactical patterns. See `ARCHITECTURE.md`.
- **Package root:** `com.banco` (backend).
- **Business modules:** clientes, cuentas, movimientos, transferencias,
  depósitos/retiros, autenticación y autorización (roles `CLIENTE`, `ADMIN`).
- **Branching:** `feature/*` → `testing` → `main`.
- **Test command (backend):** `mvn test` (unit + integration with
  Testcontainers) and `mvn verify` (includes ArchUnit).
- **Lint / typecheck (frontend):** `npm run lint`, `npm run typecheck`,
  `npm test`.
- **Database:** PostgreSQL; migrations with Flyway under
  `backend/src/main/resources/db/migration/`.
- **Key patterns:** Value Objects (`Money`, `CBU`, `DNI`), Aggregate root
  (`Cuenta`), Repository ports, Factory (creación de cuentas), Strategy
  (comisiones por tipo de cuenta), Chain of Responsibility (validación),
  Domain Events, optimistic locking (`@Version`) for transfers.
- **Architecture summary:** see `ARCHITECTURE.md`.
