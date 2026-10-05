# ADR-009: Use Resilience4j for SRI integration resilience

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

The SRI is an external dependency outside Emitta's operational control.

Slow responses, network faults or temporary outages must not cause cascading failure across Emitta.

## Decision

The SRI integration layer will use Resilience4j policies where appropriate.

Expected policies:

- timeout
- retry with bounded attempts
- circuit breaker
- potentially bulkhead where justified

Retries must distinguish transient failures from permanent fiscal validation errors.

## Reasons

- protects application resources
- prevents repeated calls against an unhealthy dependency
- allows controlled recovery
- integrates with Spring

## Alternatives considered

### Custom retry/circuit-breaker implementation

Advantages:

- complete control

Disadvantages:

- unnecessary custom infrastructure
- higher implementation and testing burden

### Retry everything through RabbitMQ only

Advantages:

- simple conceptual retry queue

Disadvantages:

- still needs dependency health protection
- not every failure should become a queue retry

## Consequences

### Positive

- better failure isolation
- measurable dependency behavior
- improved recovery

### Negative

- policy tuning requires real observations
- poorly configured retries can amplify incidents

## Risks

- retry storms

Mitigations:

- exponential/backoff strategies
- bounded retry counts
- circuit breaking
- metrics

## Future evolution

Policies must be tuned using production-like test evidence, not arbitrary defaults.
