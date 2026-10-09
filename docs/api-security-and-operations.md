# REST API and operational security

## Implemented operations

The static contract at `/openapi/emitta-openapi.yaml` documents all six business REST operations. Swagger UI is available at `/swagger-ui.html`. There are no REST endpoints yet for invoice retrieval, artifact download, SRI reception/authorization, API-client provisioning or certificate upload/rotation.

| Operation | Intended caller | Authorization | Success |
|---|---|---|---|
| POST `/api/v1/auth/token` | API client | Registered client ID and BCrypt-verified secret; active client/tenant | 200, no-store RS256 JWT |
| POST `/api/v1/invoices` | Tenant integration | `invoices:write`, active `tenant_id` from JWT | 202 new / 200 identical replay |
| POST `/api/v1/tenants` | Platform operator | `platform:admin` | 201 |
| POST `/api/v1/tenants/{tenantId}/taxpayers` | Tenant administrator | `taxpayers:write`, matching active JWT tenant | 201 |
| POST `/api/v1/taxpayers/{taxpayerId}/establishments` | Tenant administrator | `establishments:write`, parent ownership from database | 201 |
| POST `/api/v1/establishments/{establishmentId}/points-of-issue` | Tenant administrator | `points-of-issue:write`, parent ownership from database | 201 |

Administrative scopes must be assigned by trusted operators, not by API customers. Platform administration does not bypass tenant ownership on scoped endpoints. Missing/inaccessible administrative parents return 403 without revealing another tenant's resource. Authentication failures return 401; malformed requests return 400; fiscal conflicts 409; invalid fiscal values 422. Errors use Problem Details and never return SQL or certificate secrets. Health is public without details; info/metrics require `operations:read`. Other routes are denied by default.

JWT validation checks signature, issuer, audience and time validity. Tenant activity is rechecked for fiscal/administrative writes and when issuing tokens. Existing tokens expire after the configured TTL; immediate per-client token revocation is not implemented. Use HTTPS for any non-local deployment. The token endpoint requires upstream operational rate limiting before internet exposure; this repository does not implement a gateway.

Invoice tenant identity comes exclusively from JWT. Point-of-issue ownership is verified through the persisted fiscal hierarchy. Optional customer IDs must belong to the same tenant and be active; failed persistence rolls back the invoice/document/outbox transaction. Idempotency applies only to invoice creation: a tenant-scoped key with identical content replays, different content returns 409. Administrative creation is not idempotent. Request validation rejects unknown JSON properties.

## Certificates and secrets

`certificates` stores public identity metadata plus `encrypted_content` (PKCS#12) and `encrypted_password`, never plaintext. `AesGcmSecretCipher` uses AES-256-GCM, a fresh 12-byte nonce, 128-bit authentication tag and versioned envelope. Associated data binds each encrypted value to taxpayer UUID, certificate UUID and field purpose. `EMITTA_CERTIFICATE_MASTER_KEY_B64` must decode to exactly 32 bytes and is supplied by the secret store; it is not stored in PostgreSQL, source code or images. Plaintext buffers are cleared after use where supported by the implementation; this is not a guarantee of erasing all JVM copies.

`PostgreSqlSigningKeyMaterialAdapter` selects certificates in the document's tenant/taxpayer scope, requires reviewed RUC authority, matching fingerprint, active status, real certificate validity/key possession and trusted chain. V11 stores reviewed RUC, evidence hash, review timestamp and public trust anchor. These metadata alone do not establish authority; cryptographic identity and operator review are also required. The generic PKCS#12 validator additionally supports current CRL checks.

There is **no certificate registration or rotation REST endpoint**. The pilot's local importer and private operational runner are archived outside the product; existing generic crypto/validation/persistence code remains. Do not copy operational certificates into this repository. Tests generate disposable synthetic certificates in temporary directories.

A future administrative HTTPS import should require a dedicated scope, verified tenant ownership, bounded multipart PKCS#12 upload and password conveyed only in the encrypted request body. Do not accept passwords in URLs, command arguments or logs, and never echo them. Validate private-key possession, dates, certificate identity/RUC, chain/revocation and documented signing authority before encrypting with fresh nonces. Return only certificate ID and public metadata. Rotation should atomically select the new reviewed certificate, retain old certificate references for existing documents and produce an audit event without secret payloads. This is a design requirement, not an implemented endpoint.

Master-key rotation is separate from certificate rotation: envelopes currently have no key identifier. Changing the environment key alone makes existing encrypted rows unreadable. Plan controlled re-encryption and recovery before rotating it; the API has no automatic master-key rotation procedure.

## Environments and infrastructure

The default profile is `dev`; `dev`, `test` and `production` profiles keep worker, outbox publisher and SRI communication off. Enabling processing requires deliberate higher-priority operator overrides. New taxpayers are TEST-enabled and production-disabled. Provider policy defaults to UNRESOLVED and has no experimental bypass.

Use separate Doppler configurations, database credentials/databases, MinIO buckets and API clients for development, certification and production. Select the Spring profile explicitly for certification/production. The repository's `doppler.yaml` selects local development only and contains no secrets. PostgreSQL, Redis, RabbitMQ AMQP/management and MinIO API/console host ports bind to 127.0.0.1. Compose starts infrastructure only; no application worker is started by containers. Restarting containers is unnecessary for this audit; apply port changes during the next controlled restart.

Compose is a local-development topology. It is not a production deployment template: use internal networks, restricted storage credentials rather than MinIO root credentials, Redis access control, HTTPS for application/storage, and scoped monitoring access for deployed environments. Keep SRI TEST/PRODUCTION endpoints separate and require taxpayer production enablement; activating a profile alone must never transmit a document.

Communication failure during reception retains the durable attempt and uncertain SUBMITTED state. Do not delete attempts, force states or retransmit after timeout. Consult authorization by access key through the existing workflow. TEST authorization validates interoperability, not legal provider compliance.

## Validation

Run technical tests with SRI flags disabled and synthetic fixtures:

```powershell
doppler run -- powershell -NoProfile -File scripts/Test-LocalSigning.ps1
.\gradlew.bat compileJava bootJar --console=plain
python scripts/audit-public-tree.py
git diff --check
git diff --cached --check
```

The test runner uses an isolated MinIO bucket; integration database tests use Testcontainers. `Backup-LocalDatabase.ps1` requires an explicit private destination and must run inside Doppler. Backups, certificates and operational evidence stay outside the repository. `.gitignore` and `.dockerignore` prevent common accidental inclusion, but neither replaces a review of staged content.
