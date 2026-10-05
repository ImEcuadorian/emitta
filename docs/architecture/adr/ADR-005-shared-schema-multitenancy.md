# ADR-005: Use shared-database/shared-schema multi-tenancy initially

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta is a B2B platform serving multiple organizations. Tenant data must remain logically isolated.

Possible models include:

- database per tenant
- schema per tenant
- shared database/shared schema with `tenant_id`

The initial product must remain operationally manageable for a small team.

## Decision

Emitta will initially use:

```text
one PostgreSQL database
one application schema: emitta
tenant_id on tenant-owned aggregates
```

Application authorization must always scope tenant-owned operations by tenant.

PostgreSQL Row Level Security is planned as an additional defense layer once tenant context propagation is formally implemented.

## Reasons

- simpler operations
- fewer database resources
- easier Flyway migration management
- appropriate for the expected MVP scale
- central analytics and operational visibility
- RLS remains available as defense in depth

## Alternatives considered

### Database per tenant

Advantages:

- strong physical isolation

Disadvantages:

- high operational overhead
- migrations across many databases
- difficult connection management

### Schema per tenant

Advantages:

- stronger namespace isolation

Disadvantages:

- schema proliferation
- more complex migrations
- operational overhead grows with tenant count

## Consequences

### Positive

- simple deployment
- scalable enough for early-stage SaaS
- shared infrastructure costs

### Negative

- every tenant-owned query must respect tenant boundaries
- programming mistakes can become cross-tenant risks

## Risks

- cross-tenant data exposure

Mitigations:

- tenant-aware authorization
- repository scoping
- database constraints where possible
- planned RLS
- security tests

## Future evolution

High-value or regulated tenants may eventually justify dedicated schemas or databases. Such a change requires a new ADR.
