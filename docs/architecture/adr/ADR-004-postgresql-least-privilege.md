# ADR-004: Separate PostgreSQL privileges by runtime responsibility

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Using a database owner or superuser account from the Spring Boot runtime would give the application excessive privileges.

A compromised application account must not automatically provide permission to create roles, create databases, alter schemas or drop arbitrary structures.

## Decision

Emitta will use separate PostgreSQL accounts:

```text
emitta_admin
emitta_migrator
emitta_app
emitta_readonly
```

Responsibilities:

### `emitta_admin`

- database/container initialization
- administrative operations
- not used by the Spring Boot runtime

### `emitta_migrator`

- used by Flyway
- owns the `emitta` schema
- creates and modifies database structures

### `emitta_app`

- used by Spring Boot at runtime
- DML permissions only where required
- no superuser
- no database creation
- no role creation
- no replication
- no bypass RLS

### `emitta_readonly`

- operational support/reporting
- SELECT-only access where granted

## Reasons

- least privilege
- limits blast radius of application compromise
- prevents Hibernate/runtime from silently owning DDL
- clearer operational responsibility
- supports security auditability

## Alternatives considered

### One `emitta` database user for everything

Advantages:

- simple setup

Disadvantages:

- excessive runtime privileges
- migration and application responsibilities are mixed
- greater blast radius

## Consequences

### Positive

- stronger database security
- clearer migration ownership
- easier future auditing

### Negative

- additional secrets
- bootstrap setup is more complex

## Risks

- Flyway-created future objects may not automatically grant runtime privileges

Mitigation:

- configure `ALTER DEFAULT PRIVILEGES` for `emitta_migrator`

## Future evolution

Production may integrate managed database identities or stronger credential rotation. Role separation remains the expected model.
