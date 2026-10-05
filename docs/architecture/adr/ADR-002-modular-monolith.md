# ADR-002: Start Emitta as a modular monolith

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta has multiple domain areas: tenants, taxpayers, customers, documents, invoices, signatures, SRI integration, webhooks and messaging.

The system is being built by a small team and must reach a production-like academic milestone in a limited period.

Starting immediately with multiple microservices would add deployment, networking, observability, consistency and development overhead before scale boundaries are known.

## Decision

The first production architecture will be a modular monolith implemented in Spring Boot.

The codebase will use package-by-feature boundaries such as:

```text
identity
tenant
taxpayer
customer
document
invoice
signature
sri
webhook
outbox
shared
```

Modules should communicate through explicit application/domain interfaces rather than uncontrolled cross-package access.

## Reasons

- lower operational complexity
- simpler transactions
- easier debugging
- faster delivery for a four-person team
- domain boundaries can still be designed cleanly
- modules can later be extracted if evidence justifies it

## Alternatives considered

### Microservices from day one

Advantages:

- independent deployment
- independent scaling
- explicit runtime boundaries

Disadvantages:

- distributed transactions
- network failures
- duplicated infrastructure
- much heavier DevOps burden
- premature partitioning without production evidence

## Consequences

### Positive

- faster iteration
- one deployment unit initially
- simpler local development
- easier refactoring while the domain is still evolving

### Negative

- whole application is initially deployed together
- careless code could erode module boundaries

## Risks

- accidental tightly coupled packages

Mitigation:

- package-by-feature
- explicit module APIs
- architecture tests may be added later

## Future evolution

A module may be extracted into an independent service when one or more of the following become true:

- independent scaling is required
- deployment cadence is significantly different
- availability requirements differ
- ownership boundaries become clear
- the module creates measurable pressure on the monolith
