# ADR-008: Treat Redis as auxiliary infrastructure, not source of truth

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Redis was introduced into the initial infrastructure, but not every Emitta requirement needs Redis.

Fiscal correctness and idempotency must not depend solely on volatile cache state.

## Decision

PostgreSQL remains authoritative.

Redis may be used only when it solves a concrete auxiliary concern, such as:

- API Gateway rate limiting
- short-lived counters
- cache
- distributed coordination where justified

Critical fiscal uniqueness/idempotency must also be enforced in PostgreSQL, for example:

```text
UNIQUE (tenant_id, idempotency_key)
```

Redis may eventually be removed from `emitta-api` and used only by `emitta-gateway` if no direct API use case remains.

## Reasons

- avoids unnecessary infrastructure coupling
- keeps fiscal correctness in the transactional database
- prevents cache loss from becoming business-data loss

## Alternatives considered

### Redis as primary idempotency store

Advantages:

- fast

Disadvantages:

- unnecessary source-of-truth risk
- PostgreSQL constraints provide stronger transactional guarantees

## Consequences

### Positive

- simpler correctness model
- Redis failures do not corrupt fiscal state
- Redis remains available for performance-oriented features

### Negative

- some operations may require both DB and cache logic

## Risks

- accidental dependency on Redis for critical state

Mitigation:

- architecture reviews and tests must distinguish authoritative vs cached data

## Future evolution

Redis may become primarily a Gateway dependency for distributed rate limiting.
