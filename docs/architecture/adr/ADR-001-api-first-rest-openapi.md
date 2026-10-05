# ADR-001: Use REST with OpenAPI as the public API contract

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta will be consumed by heterogeneous systems such as POS platforms, ERPs, SaaS products, e-commerce platforms and custom applications. The public API must be easy to integrate from multiple programming languages and must expose a stable, documentable contract.

The primary domain operations are transactional and resource-oriented: create a fiscal document, query its state, retrieve results and manage integration resources.

## Decision

Emitta will expose a versioned REST API over HTTPS and will use OpenAPI as its formal API contract.

Initial route style:

```text
/api/v1/...
```

Swagger UI will be generated from the OpenAPI definition during development.

## Reasons

- broad client compatibility
- strong ecosystem support
- straightforward HTTP semantics
- natural mapping to fiscal resources
- excellent support in Spring Web MVC
- contract can be inspected, tested and used for SDK generation
- directly satisfies the project's API specification requirements

## Alternatives considered

### GraphQL

Advantages:

- flexible client-side field selection
- useful for highly connected read models

Disadvantages:

- limited benefit for Emitta's command-heavy fiscal workflow
- adds schema/resolver complexity
- HTTP response semantics are less direct for some transactional operations

### gRPC

Advantages:

- efficient binary protocol
- strong contracts
- excellent service-to-service communication

Disadvantages:

- less convenient as a public API for arbitrary third-party integrators
- browser and tooling integration is less direct than REST
- does not align as naturally with the project's OpenAPI deliverable

## Consequences

### Positive

- easy third-party integration
- strong documentation
- conventional error/status handling
- good Postman and Swagger support
- API versioning is explicit

### Negative

- endpoint evolution requires discipline
- large workflows may require asynchronous resource patterns instead of a single request/response

## Risks

- controllers may drift from the documented contract if contract governance is weak

## Future evolution

gRPC may be considered for internal service-to-service communication if Emitta later becomes a distributed system.

GraphQL may be reconsidered for a future analytics/read-only API if a clear use case emerges.
