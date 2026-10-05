# Architecture Decision Records — Emitta

This directory records significant architectural decisions made during the development of Emitta.

## Rules

- ADRs are immutable after acceptance, except for minor editorial corrections.
- If a decision changes, create a new ADR.
- The new ADR must reference the previous ADR and mark it as superseded where appropriate.
- Every ADR should document context, alternatives, consequences and future reconsideration triggers.

## Status values

- `Proposed`
- `Accepted`
- `Rejected`
- `Deprecated`
- `Superseded`

## Current ADRs

| ADR | Decision | Status |
|---|---|---|
| ADR-001 | REST + OpenAPI as public API style | Accepted |
| ADR-002 | Modular monolith first | Accepted |
| ADR-003 | PostgreSQL as source of truth | Accepted |
| ADR-004 | PostgreSQL least-privilege roles | Accepted |
| ADR-005 | Shared database/shared schema multi-tenancy | Accepted |
| ADR-006 | RabbitMQ for asynchronous fiscal processing | Accepted |
| ADR-007 | Transactional Outbox | Accepted |
| ADR-008 | Redis as auxiliary infrastructure | Accepted |
| ADR-009 | Resilience4j for SRI resilience | Accepted |
| ADR-010 | JAXB + DSS/XAdES for electronic documents | Accepted |
| ADR-011 | Doppler for secret management | Accepted |
| ADR-012 | API Gateway deferred until the domain API is stable | Accepted |
