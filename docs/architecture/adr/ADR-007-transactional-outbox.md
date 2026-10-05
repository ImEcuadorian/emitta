# ADR-007: Use the Transactional Outbox pattern

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta needs to persist business state in PostgreSQL and publish asynchronous work to RabbitMQ.

A naive dual write can fail:

```text
1. Commit invoice in PostgreSQL
2. Publish message to RabbitMQ
3. RabbitMQ publish fails
```

The invoice would exist without being processed.

Publishing first creates the inverse consistency problem if the database transaction later rolls back.

## Decision

Domain state and an `outbox_events` record will be committed in the same PostgreSQL transaction.

A separate publisher will read pending outbox records and publish them to RabbitMQ.

After successful publication, the outbox record will be marked as published.

## Reasons

- removes the database/message-broker dual-write gap
- uses PostgreSQL transaction guarantees
- supports retry
- broker outages do not lose business intent

## Alternatives considered

### Direct database + RabbitMQ dual write

Advantages:

- simple implementation

Disadvantages:

- inconsistent state is possible

### Distributed transaction / 2PC

Advantages:

- atomic cross-resource semantics

Disadvantages:

- operational complexity
- poor fit for the selected stack
- unnecessary for the project scope

## Consequences

### Positive

- durable event intent
- recoverable publication
- clear operational visibility

### Negative

- requires publisher process
- outbox table requires cleanup/retention strategy
- publication is eventually consistent

## Risks

- duplicate publication if acknowledgement/update fails

Mitigation:

- consumers must be idempotent

## Future evolution

CDC-based outbox publication may be considered if polling becomes a bottleneck.
