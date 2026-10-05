# Emitta Domain Model

This directory documents the initial domain model for Emitta before the model is converted into Flyway migrations, JPA entities, repositories, and application services.

The purpose of this documentation is to make the fiscal model explicit before implementation and to keep the code aligned with the architectural decisions already recorded in the ADRs.

## Scope

The first domain iteration focuses on electronic invoices and the shared fiscal concepts required to support future document types.

The initial model includes:

- Tenant
- ApiClient
- ProviderIdentity
- Taxpayer
- Establishment
- PointOfIssue
- DocumentSequence
- Certificate
- Customer
- Document
- Invoice
- InvoiceItem
- InvoiceItemTax
- DocumentAttempt
- DocumentStatusHistory
- OutboxEvent
- WebhookEndpoint

Future document types such as credit notes, debit notes, withholding documents, and dispatch guides are intentionally excluded from the MVP implementation, but the shared `Document` model must allow them to be added without redesigning the entire system.

## Core domain boundaries

### Platform

Platform-level concepts belong to Emitta itself and are not owned by a tenant.

Current platform-level concept:

```text
ProviderIdentity
```

`ProviderIdentity` represents Emitta as the electronic invoicing technology/service provider.

It must remain separate from the taxpayer that legally issues a fiscal document.

### Tenant

A `Tenant` represents an Emitta customer account or organization.

A tenant may own one or more:

- API clients
- taxpayers
- customers
- webhook endpoints

A tenant is not the same concept as a taxpayer or RUC.

Example:

```text
Tenant: NEXORF
├── ApiClient: Production ERP
├── ApiClient: Reporting
├── Taxpayer: Company A / RUC A
└── Taxpayer: Company B / RUC B
```

This separation allows one commercial Emitta account to manage multiple legal issuers if the product requires it.

### Taxpayer

A `Taxpayer` represents the legal issuer of electronic fiscal documents.

A taxpayer owns fiscal configuration such as:

- RUC
- legal/trade name
- establishments
- points of issue
- document sequences
- signing certificates
- fiscal documents

### Document

`Document` is the shared fiscal envelope for electronic documents.

It stores data common to current and future fiscal document types:

- tenant
- taxpayer
- point of issue
- document type
- environment
- sequence
- access key
- processing state
- idempotency key
- lifecycle timestamps

An invoice is modeled through composition:

```text
Document 1 --- 1 Invoice
```

instead of JPA inheritance.

This keeps the shared document lifecycle separate from invoice-specific fiscal data and allows future document tables such as:

```text
credit_notes
debit_notes
withholdings
dispatch_guides
```

to reference the same `documents` table.

## Multi-tenancy

Emitta initially uses a shared database and shared schema.

Tenant-owned records must carry or derive an unambiguous `tenant_id`.

Important rule:

> Client requests must not be trusted to select an arbitrary tenant.

The authenticated API client determines tenant context.

Future defense in depth may include PostgreSQL Row Level Security after tenant-context propagation is formally implemented.

## Idempotency

Fiscal document creation must be idempotent.

The initial rule is:

```text
UNIQUE (tenant_id, idempotency_key)
```

The client will provide an `Idempotency-Key` header when creating a fiscal document.

Repeated requests using the same key inside the same tenant must not create duplicate fiscal documents.

Idempotency in Redis may be used as an optimization in the future, but PostgreSQL remains the source of truth.

## Sequential numbering

Sequential numbers must never be generated through:

```sql
SELECT MAX(sequential) + 1
```

because that approach is unsafe under concurrency.

`DocumentSequence` will provide an atomic counter scoped by:

```text
point_of_issue
document_type
environment
```

The application must allocate the next sequential value transactionally.

## Customer snapshots

`Customer` is reusable master data.

However, an issued invoice must preserve the buyer information that existed at issuance time.

Therefore, invoice buyer fields are stored as a snapshot inside the invoice record even when an optional `customer_id` reference exists.

Changing a customer's address or name later must never alter historical fiscal documents.

## Certificates

A taxpayer may own one or more signing certificates.

The application must never persist:

- PKCS#12 content in plaintext
- certificate passwords in plaintext

The initial model stores encrypted material and metadata such as:

- fingerprint
- subject
- issuer
- validity dates
- status

The encryption master key is platform infrastructure configuration and is not stored in the database.

## Processing and auditability

Fiscal processing is asynchronous and auditable.

Three concepts have different responsibilities:

### `Document`

Represents current fiscal state.

### `DocumentStatusHistory`

Represents business-state transitions over time.

Example:

```text
RECEIVED -> QUEUED -> GENERATING -> SIGNED -> SUBMITTED -> AUTHORIZED
```

### `DocumentAttempt`

Represents technical processing attempts.

Example:

```text
Attempt 1 -> SUBMIT_TO_SRI -> TIMEOUT
Attempt 2 -> SUBMIT_TO_SRI -> SUCCESS
```

These records must not be collapsed into a single status field because they answer different operational and audit questions.

## Transactional Outbox

Creating a fiscal document and requesting asynchronous processing must occur atomically.

The transaction writes both:

```text
documents
outbox_events
```

The Outbox publisher later sends the event to RabbitMQ.

This prevents the dual-write failure where the database commit succeeds but message publication fails.

## Webhooks

Tenants may configure webhook endpoints for asynchronous events.

Examples:

```text
invoice.authorized
invoice.rejected
invoice.failed
```

Webhook secrets must be stored encrypted, not hashed, because Emitta needs the secret material to generate outbound HMAC signatures.

## Package-by-feature direction

The Java implementation should follow lightweight hexagonal / clean boundaries by feature.

Example:

```text
invoice/
├── api/
├── application/
├── domain/
└── infrastructure/
```

Do not create a global structure such as:

```text
controller/
service/
repository/
entity/
```

for the entire application.

## Domain invariants to enforce in PostgreSQL

Examples of rules that should exist at database level as well as application level:

```text
UNIQUE (tenant_id, idempotency_key)
UNIQUE (taxpayer_id, ruc) or equivalent tenant-scoped constraint
UNIQUE (taxpayer_id, establishment_code)
UNIQUE (establishment_id, point_of_issue_code)
UNIQUE (point_of_issue_id, document_type, environment)
UNIQUE (access_key)
```

Additional uniqueness constraints for fiscal series/sequential combinations will be defined in the SQL migration after the ERD is approved.

## Planned implementation sequence

1. Approve this domain documentation.
2. Create `V2__create_domain_model.sql`.
3. Add database constraints and indexes.
4. Add persistence/integration tests with Testcontainers.
5. Create JPA mappings.
6. Add domain value objects/enums.
7. Add repository boundaries only where they provide value.
8. Implement security/JWT in its own feature branch.
9. Implement invoice creation after domain and security contracts are stable.

## Related ADRs

Relevant architectural decisions include:

- ADR-002 — Modular monolith
- ADR-003 — PostgreSQL as source of truth
- ADR-004 — PostgreSQL least privilege
- ADR-005 — Shared-schema multi-tenancy
- ADR-006 — RabbitMQ asynchronous processing
- ADR-007 — Transactional Outbox
- ADR-008 — Redis as auxiliary infrastructure
- ADR-009 — Resilience4j for SRI integration
- ADR-010 — JAXB + DSS/XAdES
- ADR-013 — SRI provider registration
- ADR-014 — Immediate fiscal transmission

## Open design questions

The following items must be resolved before the corresponding implementation is considered stable:

- definitive taxpayer production/test enablement model
- definitive SRI document-state mapping
- final tax representation required by the current SRI XML specification
- certificate rotation behavior
- document retention policy
- webhook retry/retention policy
- exact API-client scope model
- RLS design and tenant-context propagation
