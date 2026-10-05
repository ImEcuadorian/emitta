# ADR-013: Register and identify Emitta as an electronic invoicing service provider

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta is designed as a B2B platform that provides electronic invoicing infrastructure to third-party systems such as POS platforms, ERPs, SaaS products, e-commerce platforms, and enterprise applications.

In 2026, the Ecuadorian Internal Revenue Service (SRI) established formal registration requirements for providers of electronic invoicing systems or services. The regulation requires these providers to register, in their RUC, an establishment and an economic activity dedicated exclusively to this activity. In addition, when an issuer uses an electronic invoicing system provided by a third party, the electronic document must include the provider's RUC in the additional information section, according to the current SRI technical specification.

This introduces two legally and technically distinct identities inside the Emitta domain:

1. The taxpayer that issues the fiscal document.
2. The technology provider that supplies the electronic invoicing service.

These identities must never be confused.

## Decision

Emitta will model the invoicing provider identity separately from taxpayer identities owned by tenants.

Conceptual model:

```text
Platform
└── ProviderIdentity
    ├── providerRuc
    ├── legalName
    ├── establishment
    ├── economicActivity
    └── regulatoryStatus
```

Issuer data will remain within the tenant context:

```text
Tenant
└── Taxpayer
    ├── ruc
    ├── legalName
    ├── establishments
    ├── pointsOfIssue
    ├── certificates
    └── documents
```

The provider RUC will be controlled by Emitta platform configuration and must not be freely supplied or overridden by API clients.

When required by the current SRI technical specification, Emitta will include the registered provider RUC in the generated electronic document.

## Reasons

- Prevent confusion between the issuer RUC and the technology provider RUC.
- Support compliance with the formal SRI provider-registration requirement.
- Prevent clients from spoofing or replacing the provider identity.
- Centralize platform-level regulatory information.
- Keep platform data separate from multi-tenant customer data.
- Accurately represent Emitta's B2B business model.

## Alternatives considered

### Store the provider RUC inside every tenant

Advantages:

- Direct access from tenant workflows.

Disadvantages:

- Duplicates platform-level information.
- Can create inconsistent values across tenants.
- Allows accidental or unauthorized modification.
- Incorrectly models platform data as customer-owned data.

Rejected.

### Allow each API request to submit `providerRuc`

Advantages:

- Flexible.

Disadvantages:

- Creates a spoofing risk.
- Reduces auditability.
- Is unnecessary because the provider identity is already known by Emitta.

Rejected.

### Hardcode the provider RUC in Java source code

Advantages:

- Trivial implementation.

Disadvantages:

- Mixes regulatory configuration with source code.
- Makes environment changes harder.
- Is not appropriate for production configuration.

Rejected.

## Consequences

### Positive

- Clear separation between issuer and provider identities.
- Consistent provider identity across all tenants.
- Better auditability.
- Lower risk of manipulation by external clients.
- Easier regulatory updates.

### Negative

- Emitta must manage its own provider regulatory configuration.
- Production readiness includes a regulatory registration process outside the software itself.
- Configuration must be validated per environment.

## Security considerations

The provider RUC is not necessarily secret, but it is trusted platform configuration.

Only authorized platform administration processes may change the provider identity.

The API must never treat a client-supplied provider identity as authoritative.

## Domain implications

The domain model must distinguish:

```text
ProviderIdentity
Taxpayer
Tenant
```

`ProviderIdentity` belongs to the Emitta platform.

`Taxpayer` belongs to a tenant and represents the legal issuer of fiscal documents.

## Initial configuration

Production configuration may include:

```text
EMITTA_PROVIDER_RUC
EMITTA_PROVIDER_LEGAL_NAME
EMITTA_PROVIDER_ESTABLISHMENT
```

Platform configuration will be managed through Doppler.

## Risks

### Regulatory changes

The SRI may modify:

- registration requirements;
- mandatory fields;
- additional-information structure;
- compliance deadlines.

Mitigation:

- isolate `ProviderIdentity`;
- maintain XML regression tests;
- review the current SRI technical specification before each significant fiscal release.

### Incorrect provider information

Mitigation:

- controlled configuration;
- startup validation;
- integration tests;
- production deployment checklist.

## Future evolution

A new ADR must be created if Emitta later supports:

- multiple registered providers;
- white-label providers;
- resellers;
- provider selection per tenant.

The initial architecture assumes one Emitta provider identity per deployment/environment.

## References

- SRI Resolution NAC-DGERCGC26-00000027.
- SRI announcement dated July 28, 2026 regarding registration of electronic invoicing system/service providers.
- Current SRI electronic invoicing technical specification.
