# ADR-012: Defer the API Gateway until the domain API is stable

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

The target Emitta architecture benefits from an API Gateway for cross-cutting edge concerns.

However, introducing the Gateway before the domain API works would add another source of routing, authentication, CORS and networking failures during the earliest development phase.

## Decision

The Gateway is part of the target architecture but will be implemented after the core `emitta-api` reaches a stable functional milestone.

It will be a separate application, expected to use Spring Cloud Gateway.

Target responsibilities:

- external routing
- JWT / API key validation
- rate limiting
- CORS
- correlation IDs
- edge metrics
- request policies

The domain API will still retain relevant internal authorization instead of blindly trusting that every request passed through the Gateway.

## Reasons

- reduces premature complexity
- keeps early debugging simple
- maintains clear separation between edge and domain concerns
- still satisfies the project's architectural pattern requirement

## Alternatives considered

### Gateway from day one

Advantages:

- target topology exists immediately

Disadvantages:

- additional debugging layer
- duplicates early security/configuration work
- no practical benefit before the API exists

### No Gateway

Advantages:

- simplest deployment

Disadvantages:

- cross-cutting policies remain duplicated
- weaker future multi-service routing story

## Consequences

### Positive

- faster core API development
- clean future gateway boundary

### Negative

- some temporary direct API access is necessary during development
- certain edge concerns will later move or be duplicated deliberately

## Risks

- API could become coupled to edge-specific behavior before the Gateway arrives

Mitigation:

- keep edge responsibilities explicit in architecture documentation

## Future evolution

Create `emitta-gateway` when the following are stable:

- security model
- first fiscal endpoint
- OpenAPI contract
- core domain workflow
- local container environment
