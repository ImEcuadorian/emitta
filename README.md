# Emitta

> API-first infrastructure for electronic invoicing in Ecuador.

**Emitta** is a B2B API platform designed to simplify the integration of electronic tax documents with Ecuador's SRI. Instead of requiring each POS, ERP, SaaS, e-commerce platform or custom system to implement XML generation, electronic signature, authorization flows, retries and SRI-specific integration logic from scratch, Emitta exposes a simpler REST API and encapsulates that complexity behind a secure, observable and resilient backend.

> Product idea: **“Emite. Integra. Escala.”**

---

## 1. Project status

Emitta has an implemented REST API and a verified XML/signing/SRI certification workflow. See [API security and operations](docs/api-security-and-operations.md) for the current endpoints, scopes and deployment limitations. Fiscal workers and SRI communication are disabled by default.

Implemented / configured so far:

- Java 25
- Spring Boot 4.1.1
- Gradle Kotlin DSL
- Spring Web MVC
- Bean Validation
- Spring Security
- OAuth2 Resource Server support for JWT
- Spring Data JPA / Hibernate
- PostgreSQL 18
- Flyway
- RabbitMQ 4
- Redis 8
- Spring Boot Actuator
- OpenAPI / Swagger via springdoc
- Resilience4j
- JAXB for XML
- DSS for XAdES / PKCS#12 electronic signatures
- Testcontainers
- Doppler for secrets and environment configuration
- PostgreSQL role separation:
  - `emitta_admin`
  - `emitta_migrator`
  - `emitta_app`
  - `emitta_readonly`
- Dedicated PostgreSQL schema: `emitta`
- Local Docker-based infrastructure
- ADR repository started

Implemented business flow: JWT issuance, tenant/fiscal administration, tenant-scoped invoice creation with idempotency, XML 2.1.0 validation, DSS XAdES-BES signing, encrypted certificate storage and durable SRI reception/authorization workflows.

Not yet implemented: certificate upload/rotation REST endpoints, invoice retrieval/artifact REST endpoints and customer-facing SRI submission/query endpoints. Production deployment and regulatory provider review remain operator responsibilities.

---

## 2. Product vision

### Problem

Integrating electronic invoicing with the SRI introduces significant complexity for software vendors and development teams:

- fiscal data validation
- SRI-specific XML structures
- XSD validation
- electronic signature
- PKCS#12 certificate handling
- communication with SRI services
- authorization status handling
- retries and transient failure management
- document persistence
- idempotency
- auditability
- eventual generation or storage of printable representations
- security of tax and certificate data

A business application should not need to reimplement this entire fiscal infrastructure.

### Proposed solution

Emitta exposes a developer-friendly REST API.

Conceptually:

```text
POS / ERP / SaaS / E-commerce
             |
             | JSON / HTTPS
             v
        Emitta API
             |
     +-------+--------+
     |                |
PostgreSQL         RabbitMQ
                      |
                      v
                  SRI Worker
                      |
               Resilience4j
                      |
                      v
                     SRI
```

A future external entry point will add:

```text
Internet
   |
   v
Emitta Gateway
   |
   v
Emitta API
```

---

## 3. Target users

Emitta is intended primarily for software integrators rather than end users who manually issue invoices.

Typical customers:

- POS vendors
- ERP vendors
- SaaS platforms
- e-commerce platforms
- marketplaces
- custom enterprise applications
- independent software developers

---

## 4. Business model hypothesis

Initial business model:

- B2B SaaS
- subscription tiers
- usage-based billing by processed tax document
- free or low-cost sandbox for development
- paid production usage

The commercial unit should be the **processed fiscal document**, not merely the number of HTTP requests.

Potential plans may later differentiate by:

- monthly document volume
- number of taxpayers / tenants
- webhook usage
- retention period
- support SLA
- production throughput
- custom integrations

---

## 5. Academic scope

Emitta is also designed to satisfy the final API project requirements across four learning outcomes.

### RA1 — Business justification

- API value chain
- target customers
- product vision
- API monetization model
- business case

### RA2 — Architecture and patterns

Selected / planned patterns:

- REST
- API Gateway
- Adapter
- Circuit Breaker
- Retry
- Queue-based asynchronous processing
- Transactional Outbox
- Idempotency
- Multi-tenancy
- Webhooks

### RA3 — Data model and API contract

- relational domain model
- contract-first API design
- OpenAPI 3 specification
- consistent error contract
- versioned endpoints

### RA4 — Development, security and deployment

- Spring Security
- JWT
- unit tests
- integration tests
- Testcontainers
- Docker
- load testing with k6
- spike testing
- deployment to server infrastructure
- observability through Actuator / metrics

---

## 6. Technology stack

| Area | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.1 |
| Build | Gradle Kotlin DSL |
| API | REST / Spring Web MVC |
| Validation | Jakarta Bean Validation |
| Security | Spring Security |
| Token validation | OAuth2 Resource Server / JWT |
| Database | PostgreSQL 18 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Messaging | RabbitMQ 4 |
| Cache / auxiliary state | Redis 8 |
| Resilience | Resilience4j |
| API contract | OpenAPI / Swagger |
| XML | JAXB |
| Electronic signature | DSS / XAdES / PKCS#12 |
| Observability | Spring Boot Actuator |
| Testing | JUnit / Spring Test / Testcontainers |
| Secrets | Doppler |
| Containers | Docker / Docker Compose |
| Load testing | k6 |
| Future edge layer | Spring Cloud Gateway |

---

## 7. Why RabbitMQ?

RabbitMQ is used because the primary asynchronous problem in the MVP is **work processing**.

Example:

```text
Invoice accepted
      |
      v
PostgreSQL transaction
      |
      v
Outbox event
      |
      v
RabbitMQ
      |
      v
SRI processing worker
```

RabbitMQ is a good fit for:

- work queues
- acknowledgement / negative acknowledgement
- retries
- dead-letter queues
- routing
- independent worker scaling

Kafka is intentionally not part of the MVP.

Kafka becomes interesting if Emitta later requires:

- long-lived event streams
- replay
- multiple independent consumers
- analytics pipelines
- billing event streams
- audit event streams
- large-scale event-driven integration

RabbitMQ and Kafka may coexist in a future architecture; Kafka does not automatically replace RabbitMQ.

See:

`docs/architecture/adr/ADR-006-rabbitmq-async-processing.md`

---

## 8. Why Redis?

Redis is **not** the source of truth.

PostgreSQL remains authoritative.

Potential Redis responsibilities:

- gateway rate limiting
- short-lived counters
- cache
- distributed coordination where justified

Critical invoice idempotency must also be enforced by PostgreSQL constraints, for example:

```text
UNIQUE (tenant_id, idempotency_key)
```

Redis may ultimately live mainly in `emitta-gateway` if the API does not need it directly.

See:

`docs/architecture/adr/ADR-008-redis-auxiliary.md`

---

## 9. Database security model

Emitta uses separate PostgreSQL identities.

```text
emitta_admin
    |
    +-- database administration only

emitta_migrator
    |
    +-- Flyway
    +-- CREATE / ALTER / INDEX / migrations

emitta_app
    |
    +-- runtime Spring Boot account
    +-- SELECT / INSERT / UPDATE / DELETE
    +-- no schema ownership
    +-- no CREATE ROLE / CREATE DB / superuser

emitta_readonly
    |
    +-- operational support / reporting
    +-- SELECT only
```

The runtime application must never connect using the administrator account.

### Database organization

```text
emitta_db
└── schema: emitta
```

Expected future core entities:

```text
Tenant
 ├── API Client / User
 ├── Taxpayer
 │    └── Certificate
 ├── Customer
 ├── Document
 │    └── Invoice
 │         └── InvoiceItem
 ├── WebhookEndpoint
 └── OutboxEvent
```

The project adopts a shared-database/shared-schema multi-tenant model initially.

Each tenant-owned aggregate will carry a `tenant_id`.

PostgreSQL Row Level Security is planned as an additional defense layer after tenant propagation is formally designed.

---

## 10. Flyway ownership

Hibernate is configured with:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Therefore Hibernate does not own schema evolution.

Flyway owns database migrations.

Runtime:

```text
Flyway -> emitta_migrator -> DDL
Spring -> emitta_app      -> DML
```

This separation follows least privilege.

---

## 11. Transactional Outbox

Directly writing to PostgreSQL and then publishing to RabbitMQ introduces a dual-write problem.

Unsafe flow:

```text
1. INSERT invoice
2. COMMIT
3. Publish RabbitMQ  <-- failure here
```

Emitta will instead use a Transactional Outbox.

```text
        SAME DATABASE TRANSACTION
             |
      +------+------+
      |             |
   invoice      outbox_event
      |             |
      +------+------+
             |
           COMMIT
             |
             v
       Outbox publisher
             |
             v
          RabbitMQ
```

This allows message publication to be retried without losing the original business transaction.

---

## 12. API Gateway strategy

The API Gateway is part of the target architecture but intentionally deferred.

The first milestone is a stable domain API.

Future topology:

```text
Internet
   |
  HTTPS
   |
   v
Emitta Gateway
   |
   +-- JWT / API key validation
   +-- rate limiting
   +-- CORS
   +-- correlation IDs
   +-- request policies
   +-- routing
   |
   v
Emitta API
```

The Gateway will be a separate application, not a dependency embedded into the domain API.

See:

`docs/architecture/adr/ADR-012-api-gateway-deferred.md`

---

## 13. Local infrastructure

Current development ports:

| Service | Host port | Container port |
|---|---:|---:|
| Emitta API | `18080` | application process |
| PostgreSQL | `55432` | `5432` |
| Redis | `56379` | `6379` |
| RabbitMQ AMQP | `55672` | `5672` |
| RabbitMQ Management | `15682` | `15672` |

These ports are intentionally different from common defaults on the host to avoid collisions with other local projects.

Inside the Docker network, standard service ports remain unchanged.

---

## 14. Secrets with Doppler

Secrets must not be committed to Git.

Current development variables include:

```text
APP_ENV
SERVER_PORT

DB_HOST
DB_PORT
DB_NAME

DB_ADMIN_USERNAME
DB_ADMIN_PASSWORD

DB_MIGRATION_USERNAME
DB_MIGRATION_PASSWORD

DB_APP_USERNAME
DB_APP_PASSWORD

DB_READONLY_USERNAME
DB_READONLY_PASSWORD

POSTGRES_HOST_PORT

REDIS_HOST
REDIS_PORT
REDIS_HOST_PORT

RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_HOST_PORT
RABBITMQ_MANAGEMENT_HOST_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
```

Future secrets:

```text
JWT_PRIVATE_KEY_B64
JWT_PUBLIC_KEY_B64
ENCRYPTION_MASTER_KEY
```

Certificates belonging to Emitta customers must not be stored as individual Doppler secrets. They will require application-level encrypted storage.

---

## 15. Running locally

### Requirements

- JDK 25
- Docker Desktop / Docker Engine
- Doppler CLI
- Gradle Wrapper included in the repository

### Start infrastructure

```powershell
doppler run -- docker compose up -d
```

Check:

```powershell
doppler run -- docker compose ps
```

### Run Spring Boot

Windows:

```powershell
doppler run -- .\gradlew.bat bootRun
```

Linux / macOS:

```bash
doppler run -- ./gradlew bootRun
```

---

## 16. Useful development endpoints

Once security configuration permits them:

```text
Swagger UI:
http://localhost:18080/swagger-ui/index.html

OpenAPI JSON:
http://localhost:18080/v3/api-docs

Actuator health:
http://localhost:18080/actuator/health
```

---

## 17. Target document lifecycle

The final state machine is still pending formal approval, but the working direction is:

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
   +-------> RETRY_PENDING
   |
   +-------> REJECTED
   |
   v
AUTHORIZED
```

A formal document-state ADR or domain specification will be added before implementation.

---

## 18. Security direction

Planned security layers:

- TLS at the edge
- Gateway authentication policies
- JWT / API credentials
- tenant authorization
- PostgreSQL least privilege
- future PostgreSQL RLS
- encrypted certificate storage
- secrets managed through Doppler
- no raw private keys in Git
- no full JWTs or certificate passwords in logs
- idempotency constraints
- webhook authenticity controls
- correlation / trace IDs

---

## 19. Load and spike testing

Required performance scenarios:

### Sustained load

- ramp-up: 2–3 minutes
- peak: 100–200 VUs
- plateau: 5–10 minutes
- ramp-down: 1–2 minutes

### Spike

- approximately 500 VUs
- peak duration: 1–2 minutes
- immediate recovery phase

KPIs:

- throughput / RPS
- average response time
- p90
- p95
- p99
- error rate
- breakpoint
- CPU
- memory
- DB behavior
- RabbitMQ queue depth

SRI must be mocked or isolated for platform load tests so the project measures Emitta rather than stress-testing an external government service.

---

## 20. Repository structure — current direction

```text
emitta/
├── build.gradle.kts
├── settings.gradle.kts
├── compose.yml
├── README.md
│
├── docker/
│   └── postgres/
│       └── init/
│           └── 01-emitta-roles.sh
│
├── docs/
│   └── architecture/
│       └── adr/
│
└── src/
    ├── main/
    │   ├── java/
    │   │   └── io/github/imecuadorian/emitta/
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │
    └── test/
```

Target package-by-feature direction:

```text
io.github.imecuadorian.emitta
├── identity
├── tenant
├── taxpayer
├── customer
├── document
├── invoice
├── signature
├── sri
├── webhook
├── outbox
└── shared
```

---

## 21. Architecture Decision Records

The current ADRs are located in:

```text
docs/architecture/adr/
```

Current decisions:

1. REST + OpenAPI
2. Modular monolith
3. PostgreSQL as source of truth
4. PostgreSQL least privilege
5. Shared-schema multi-tenancy
6. RabbitMQ asynchronous processing
7. Transactional Outbox
8. Redis as auxiliary infrastructure
9. Resilience4j for SRI integration
10. DSS / XAdES for signatures
11. Doppler for secrets
12. Deferred API Gateway

---

## 22. Immediate next steps

1. Validate PostgreSQL role separation.
2. Finalize initial ERD.
3. Define document lifecycle.
4. Define API error contract.
5. Configure Spring Security.
6. Configure JWT keys / issuance strategy.
7. Enable Swagger for allowed routes.
8. Define first contract-first invoice endpoint.
9. Implement first Flyway domain migrations.
10. Implement Outbox.
11. Configure RabbitMQ topology.
12. Implement XML generation.
13. Implement XAdES signing.
14. Implement SRI Adapter.
15. Add Resilience4j policies.
16. Add API Gateway.
17. Execute k6 load / spike tests.
18. Deploy.

---

## 23. Architecture principle

> Every technology in Emitta must solve a concrete problem. Technology is not added only because it is popular.

The architecture should evolve based on measurable requirements rather than hypothetical scale.
