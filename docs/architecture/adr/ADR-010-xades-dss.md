# ADR-010: Use JAXB for XML generation and DSS for XAdES signing

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta's public API will primarily accept JSON, while SRI electronic documents require XML and electronic signatures.

Certificates are expected to be supplied in formats such as PKCS#12.

## Decision

Emitta will use:

```text
Java domain/request model
        |
        v
       JAXB
        |
        v
      XML
        |
        v
DSS XAdES / PKCS#12
        |
        v
 signed XML
```

DSS is the cryptographic/signature engine, but Emitta must still configure the exact profile and XML behavior required by the SRI.

## Reasons

- JAXB provides a typed XML mapping model
- DSS provides mature XAdES support
- PKCS#12 handling is supported
- avoids implementing cryptographic signature standards manually

## Alternatives considered

### Hand-built XML strings

Advantages:

- minimal dependency

Disadvantages:

- fragile
- difficult to validate
- escaping/order mistakes are likely

### Custom XAdES implementation

Advantages:

- complete control

Disadvantages:

- cryptographic implementation risk
- unnecessary complexity
- large security/testing burden

## Consequences

### Positive

- structured XML generation
- mature signature primitives
- clearer separation between document generation and signing

### Negative

- SRI compatibility still requires careful validation
- library-level XAdES support does not guarantee SRI acceptance

## Risks

- producing standards-compliant but SRI-incompatible signatures

Mitigation:

- integration tests against official test environment
- fixture-based signature validation
- explicit documentation of required signature profile

## Future evolution

The signing module should remain isolated behind an internal interface so the implementation can be replaced without affecting invoice business logic.
