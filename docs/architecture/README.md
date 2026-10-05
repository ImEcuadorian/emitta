# Emitta Architecture

This folder contains the architecture documentation for Emitta.

## Current architectural direction

```text
                     INTERNET
                        |
                     HTTPS
                        |
              [Future Emitta Gateway]
                        |
                        v
                   Emitta API
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
     PostgreSQL       Redis        RabbitMQ
          |                            |
          |                            v
          |                        SRI Worker
          |                            |
          |                       Resilience4j
          |                            |
          +--------- Outbox -----------+
                                       |
                                       v
                                      SRI
```

## Current design principles

1. PostgreSQL is the source of truth.
2. Flyway owns schema evolution.
3. Runtime DB permissions follow least privilege.
4. Multi-tenancy is designed from the beginning.
5. External SRI processing is asynchronous where appropriate.
6. RabbitMQ handles work queues.
7. Transactional Outbox protects DB-to-broker consistency.
8. Redis is auxiliary, not authoritative.
9. Gateway is deferred, not forgotten.
10. Every infrastructure component must justify its existence.

## Documentation

- ADRs: `adr/`
- C4 diagrams: pending
- ERD: pending
- threat model: pending
- API error model: pending
