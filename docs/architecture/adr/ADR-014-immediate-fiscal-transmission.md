# ADR-014: Process and transmit fiscal documents immediately without coupling HTTP latency to SRI authorization

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Starting January 1, 2026, the SRI requires immediate transmission of electronic sales documents, withholding documents, and complementary documents.

Emitta therefore cannot treat fiscal processing as delayed batch work.

At the same time, the SRI is an external dependency whose latency and availability are outside Emitta's operational control.

Keeping an HTTP request open while Emitta:

1. persists the document;
2. generates XML;
3. signs the document;
4. transmits it to the SRI;
5. waits for authorization;
6. executes retries;

would directly couple API availability and client latency to SRI infrastructure.

## Decision

Emitta will use immediate asynchronous processing for fiscal documents.

Target flow:

```text
Client
  |
  | POST /api/v1/invoices
  v
Emitta API
  |
  | validate
  | persist document
  | persist Outbox event
  v
PostgreSQL
  |
  | COMMIT
  v
Outbox Publisher
  |
  v
RabbitMQ
  |
  | immediate consumption
  v
Fiscal Worker
  |
  | generate XML
  | sign
  | transmit to SRI
  | update result
  v
SRI
```

RabbitMQ will be used to decouple processing, not to intentionally delay fiscal transmission.

Under normal operating conditions, messages must begin processing immediately or near-immediately.

The API may initially respond with:

```text
202 Accepted
```

and a resource representing the current document status.

Example:

```json
{
  "id": "document-id",
  "status": "QUEUED"
}
```

Clients may query the document status and, later, receive webhooks when the state changes.

## Initial lifecycle

Current direction:

```text
RECEIVED
   |
   v
QUEUED
   |
   v
GENERATING
   |
   v
SIGNED
   |
   v
SUBMITTED
   |
   +------> RETRY_PENDING
   |
   +------> REJECTED
   |
   v
AUTHORIZED
```

The final state machine will be formally defined during domain modeling.

## Reasons

- Comply with the immediate-transmission requirement.
- Avoid batching or deliberate delays.
- Prevent SRI latency from defining API latency.
- Isolate external failures.
- Enable controlled retries.
- Scale workers independently from HTTP traffic.
- Support load and spike testing requirements.

## Architectural interpretation

“Immediate transmission” is interpreted as beginning processing and transmission without deliberate delay.

It is not interpreted as a requirement to keep the original HTTP request synchronous until SRI authorization is complete.

If future SRI regulation or technical specifications explicitly require different behavior, this ADR must be superseded.

## Alternatives considered

### Fully synchronous processing

```text
HTTP request
   |
XML
   |
signature
   |
SRI
   |
authorization
   |
HTTP response
```

Advantages:

- The client could receive the final result in a single request.

Disadvantages:

- Latency depends directly on the SRI.
- External outages consume API resources.
- Retry behavior becomes more complex.
- Poorer behavior under traffic spikes.
- Higher risk of cascading failures.

Rejected as the primary architecture.

### Delayed batch processing

Advantages:

- Simple implementation.

Disadvantages:

- Conflicts with the immediate-transmission objective.

Rejected.

### Publish directly to RabbitMQ after the database commit

Advantages:

- Simpler than an Outbox.

Disadvantages:

- Creates a PostgreSQL/RabbitMQ dual-write consistency problem.

Rejected in favor of the Transactional Outbox pattern.

## Internal performance objectives

The following values are internal engineering SLOs, not legal SRI deadlines:

```text
API acknowledgement p95:              < 500 ms
Request -> durable outbox commit:      < 500 ms
Normal queue waiting time:             < 250 ms
Request -> first SRI attempt p95:      < 2 s
Sustained-load API error rate:         < 1%
Normal queue backlog:                  ~ 0
```

These targets must be validated with k6 and adjusted using evidence.

## Required observability

The system should capture timestamps such as:

```text
received_at
queued_at
processing_started_at
signed_at
submitted_at
authorized_at
failed_at
```

Derived metrics:

```text
request_to_queue_duration
queue_wait_duration
processing_duration
request_to_sri_submission_duration
sri_response_duration
authorization_duration
```

Operational metrics:

```text
queue depth
oldest queued message age
retry count
DLQ count
SRI error rate
SRI circuit-breaker state
```

## Failure handling

### Temporary SRI or network failure

Expected flow:

```text
SIGNED
  |
SUBMISSION_FAILED
  |
RETRY_PENDING
  |
bounded retry with backoff
```

Retries must be bounded.

Resilience4j and RabbitMQ must not create retry storms.

### Permanent fiscal validation error

Definitive validation errors must not be retried indefinitely.

The document will move to an explicit rejected/error state and the SRI response will be preserved for diagnosis.

### RabbitMQ outage

The Transactional Outbox will preserve processing intent in PostgreSQL until publication can succeed.

## Idempotency

The asynchronous model requires idempotency.

At minimum:

- the client will use an `Idempotency-Key`;
- PostgreSQL will enforce uniqueness by tenant and idempotency key;
- RabbitMQ consumers will be idempotent;
- duplicate broker delivery must not result in duplicate fiscal issuance.

## Consequences

### Positive

- Fast and predictable HTTP acknowledgement.
- Immediate fiscal processing.
- SRI failures are isolated.
- Controlled retries.
- Independently scalable workers.
- Queue depth becomes a clear operational signal.

### Negative

- Clients must understand asynchronous states.
- Eventual consistency is introduced.
- Status-query and webhook behavior must be designed.
- Queue health must be monitored.
- Idempotency becomes mandatory.

## Risks

### Queue backlog

A growing queue could conflict with the immediate-transmission objective.

Mitigation:

- alerts on queue depth and message age;
- consumer scaling;
- queue-age SLO;
- capacity testing.

### Retry storm

Mitigation:

- bounded retries;
- backoff;
- Circuit Breaker;
- DLQ;
- metrics.

### Client interprets `202` as “authorized”

Mitigation:

- explicit OpenAPI contract;
- clear state names;
- documentation that `202` means accepted for processing, not authorized by the SRI.

## Testing implications

Load tests must primarily measure Emitta infrastructure and must not generate massive load against external SRI infrastructure.

For k6:

```text
k6
 |
 v
Emitta
 |
 v
Fake / Mock TaxAuthorityGateway
```

Real tests against the SRI certification/test environment will be separate integration tests.

## Future evolution

If measurements show that specific flows can safely provide a bounded synchronous wait without compromising availability, Emitta may later offer an optional mode, for example:

```text
POST /invoices?waitForAuthorization=2s
```

That mode would be an optimization on top of the asynchronous source-of-truth workflow, not a replacement for it.

Any change to the primary processing model requires a new ADR.

## References

- SRI announcement dated December 30, 2025 regarding mandatory immediate transmission starting January 1, 2026.
- Current SRI electronic invoicing technical documentation.
- ADR-006: RabbitMQ for asynchronous fiscal processing.
- ADR-007: Transactional Outbox.
- ADR-009: Resilience4j for SRI integration resilience.
