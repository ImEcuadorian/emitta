# ADR-006: Use RabbitMQ for asynchronous fiscal processing

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

SRI communication can be slow, unavailable or temporarily inconsistent. Emitta must not keep client HTTP connections blocked while external fiscal processing completes.

The initial asynchronous requirement is primarily work execution: a fiscal document needs to be processed by a worker reliably.

## Decision

RabbitMQ will be used as the message broker for asynchronous processing.

Expected responsibilities include:

- work queues
- acknowledgements
- retries
- dead-letter queues
- worker routing
- independent worker scaling

An invoice submission should evolve toward:

```text
HTTP request
   |
PostgreSQL
   |
Outbox
   |
RabbitMQ
   |
SRI worker
```

The public API may return `202 Accepted` for asynchronous workflows.

## Reasons

RabbitMQ maps naturally to task/job processing.

It supports:

- ACK / NACK
- DLQ
- routing
- queue semantics
- straightforward Spring AMQP integration

## Alternatives considered

### Apache Kafka

Advantages:

- durable event log
- replay
- excellent high-throughput event streaming
- multiple independent consumer groups

Disadvantages for the current MVP:

- heavier operational model
- primary requirement is work queues rather than historical event streams
- additional complexity is not currently justified

## Consequences

### Positive

- SRI latency is decoupled from client requests
- workers can scale independently
- failures can be retried
- dead-letter handling is explicit

### Negative

- introduces asynchronous state
- message handling must be idempotent
- RabbitMQ must be operated and monitored

## Risks

- duplicate delivery
- poison messages
- queue accumulation

Mitigations:

- idempotent consumers
- DLQ
- retry limits
- queue-depth monitoring
- Transactional Outbox

## Future evolution

Kafka should be evaluated when Emitta has a real need for:

- replay
- event retention
- domain event streaming
- multiple independent consumers
- analytics pipelines
- billing/audit streams at scale

RabbitMQ and Kafka may coexist. Kafka does not automatically replace RabbitMQ.
