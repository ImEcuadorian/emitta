# ADR-011: Use Doppler for environment secrets and configuration

- Status: Accepted
- Date: 2026-10-04
- Decision makers: Emitta team

## Context

Emitta will manage sensitive configuration:

- database credentials
- RabbitMQ credentials
- future JWT private keys
- encryption master keys

These values must not be committed to Git or hardcoded in application configuration.

## Decision

Doppler will provide secrets and environment-specific configuration at runtime.

Development commands follow the pattern:

```text
doppler run -- docker compose up -d
doppler run -- ./gradlew bootRun
```

Environment-specific configurations will be separated, e.g.:

```text
dev
stg
prd
```

## Reasons

- avoids secrets in Git
- central configuration
- easier credential rotation
- clean separation between code and environment

## Alternatives considered

### `.env` committed or distributed manually

Rejected because secrets are easily leaked and become difficult to rotate.

### Hardcoded `application.yml`

Rejected because environment credentials would live in source control.

## Consequences

### Positive

- better secret hygiene
- consistent local/server configuration
- environment separation

### Negative

- development now depends on Doppler access
- secret-manager availability and access control become operational concerns

## Risks

- using Doppler as storage for all customer certificates would create poor secret organization

Mitigation:

Doppler will store platform-level secrets. Customer PKCS#12 certificates will require encrypted application storage, using an encryption key managed separately.

## Future evolution

Production may later move to another managed secret store. Application configuration must remain environment-variable driven to preserve portability.
