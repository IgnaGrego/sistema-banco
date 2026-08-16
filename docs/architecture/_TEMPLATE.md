# Architecture — SPEC-XXX

## 1. Feature

Name of the feature.

---

## 2. Specification

Reference:

`docs/specs/SPEC-XXX.md`

---

## 3. Affected Modules

- Module A
- Module B

---

## 4. Application Flow

```text
Presentation
    ↓
Application
    ↓
Domain
    ↓
Persistence / External Service
```

Explain the actual flow (adapt the layers to the project's architecture).

---

## 5. Components

Adapt the subsections to the target stack (controllers/routes, actions or use
cases, services, data access, authorization, async work).

### Entry points / presentation

List routes, controllers, views, endpoints.

### Application / use cases

List actions or services.

### Domain / models

List models or domain entities.

### Authorization

List authorization rules.

### Async work

List events, jobs, or queues if required.

---

## 6. Data Changes

Describe required migrations/schema changes and relationships.

---

## 7. External Integrations

Describe integrations.

---

## 8. Testing Strategy

Describe:

- feature tests;
- unit tests;
- authorization tests;
- integration tests.

---

## 9. Risks

List relevant technical risks.

---

## 10. Alternatives Considered

Describe significant alternatives.

---

## 11. Decision

Explain the selected approach.
