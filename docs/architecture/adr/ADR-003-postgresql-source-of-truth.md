# ADR-003: Use PostgreSQL as Emitta's transactional source of truth

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Fiscal documents require strong transactional guarantees, uniqueness constraints, relationships, auditable state transitions and reliable persistence.

Emitta also needs multi-tenancy, indexing, structured relational data and occasional semi-structured payloads.

## Decision

PostgreSQL 18 will be the authoritative transactional database for Emitta.

Hibernate/JPA will be used for application persistence, while Flyway will own schema evolution.

Hibernate is configured to validate rather than generate the schema.

## Reasons

PostgreSQL provides:

- ACID transactions
- foreign keys
- unique constraints
- CHECK constraints
- JSONB
- indexing
- partial indexes
- Row Level Security
- strong concurrency support
- mature operational tooling

These features align directly with fiscal data integrity requirements.

## Alternatives considered

### MongoDB

Advantages:

- flexible document representation
- simple storage for evolving JSON payloads

Disadvantages:

- Emitta's core model is highly relational
- relational constraints are valuable
- multi-entity fiscal transactions benefit from SQL semantics

### MySQL

Advantages:

- mature relational database
- large ecosystem

Disadvantages:

- PostgreSQL features such as RLS and advanced indexing better match planned Emitta capabilities

## Consequences

### Positive

- strong consistency
- robust data constraints
- future RLS support
- transactional foundation for the Outbox pattern

### Negative

- schema changes must be managed carefully
- scaling writes horizontally is more complex than some distributed stores

## Risks

- relying only on application validation would weaken integrity

Mitigation:

- enforce critical invariants in PostgreSQL as well as Java

## Future evolution

Read replicas, partitioning or specialized analytical stores may be introduced when measurable workload requires them. PostgreSQL remains the primary source of truth unless a future ADR supersedes this decision.
