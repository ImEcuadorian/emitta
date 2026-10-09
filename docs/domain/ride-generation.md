# RIDE PDF — first implementation increment

Status: **foundation / not wired to fiscal authorization**.

## Why

The Ecuadorian SRI defines the RIDE as the printed representation of an
electronic tax document. Its displayed fiscal fields must match the XML.
A PDF is not a replacement for the electronically signed/authorized XML.

Current repository groundwork already exists:
- document_artifacts has artifact_type = RIDE_PDF (Flyway V7).
- S3-compatible artifact storage adapter and integrity metadata are implemented.
- InvoiceXmlData holds the fiscal snapshot used for XML generation.

## What this branch delivers

- Java PDFBox 3 renderer (PdfBoxInvoiceRideGenerator), isolated behind
  InvoiceRidePdfGeneratorPort.
- Input record InvoiceRideRequest for fiscal data + verified SRI
  authorization information.
- A4 layout, automatic page breaks, issuer/buyer identity, 49-digit
  access key, Code 128 barcode, invoice lines, tax summaries, payment
  methods, totals and page numbering.
- Visible SIN VALIDEZ TRIBUTARIA label for TEST (ambiente=1).
- Unit tests for a short invoice, a 90-line invoice, TEST marking and
  mismatched authorization/access keys.

## Security and lifecycle boundary

DO NOT expose a public endpoint that accepts InvoiceRideRequest from an
HTTP client and labels its output authorized. An internal orchestrator must:

1. Persist the verified SRI authorization and original signed XML.
2. Commit the document's AUTHORIZED state after checking tenant/resource
   ownership and matching the authorization/access key.
3. Render the RIDE from the persisted fiscal snapshot (ideally
   cross-checked against the signed/authorized XML), not from untrusted request
   data.
4. Store with StoreDocumentArtifactUseCase and
   DocumentArtifactType.RIDE_PDF, application/pdf, and a stable document ID.
5. Retry PDF failures independently; NEVER re-issue the invoice or change its
   SRI-authorized status just because PDF rendering or object storage failed.
6. Provide tenant-authorized retrieval/download with a short-lived URL or
   an authenticated binary response. Do not expose storage keys publicly.
7. Notify/email the recipient only when authorized XML and matching RIDE
   are available, with a reliable delivery audit trail.

Suggested future API (NOT implemented in this branch):

- GET /api/v1/documents/{id}/artifacts/ride
- GET /api/v1/documents/{id}/artifacts/authorized-xml

Response must use Content-Type: application/pdf and a safe Content-Disposition
filename for RIDE. Errors must never leak a different tenant's document
existence or storage keys.

## Remaining before regulatory production readiness

- Implement SRI SOAP reception/authorization adapter and verify signed XML.
- Connect verified AUTHORIZED transitions to a reliable PDF-generation
  task and existing artifact storage.
- Implement tenant-secured artifact retrieval.
- Extend printed details (e.g., supported optional/adicional fields, tax
  regime details and logos), validate against SRI's latest applicable
  RIDE technical layout and real certification examples.
- Embed a proper Unicode font; built-in Helvetica has limited glyph support.
  Do not silently strip characters from legal names.
- Regression-test long legal names, long descriptions, more than 90 items,
  accented characters, money calculations, barcodes, and multiple tax bands.
- Measure PDF generation time and memory under load.
- Run unit tests with JDK 25 and perform a visual PDF inspection before
  merging. CI/test execution was not performed by this patch.

## Commands

Windows:
  .\gradlew.bat test --tests "*PdfBoxInvoiceRideGeneratorTest"

Linux / macOS:
  ./gradlew test --tests "*PdfBoxInvoiceRideGeneratorTest"

## Product delivery backlog

1. Fiscal closure: PKCS12 certificate custody + signed XML -> SRI reception
   -> SRI authorization -> durable state/response.
2. RIDE integration + PDF/XML delivery + tenant-authorized download.
3. Redis distributed rate limiting; separate monthly commercial quotas.
4. RabbitMQ delayed retries/DLQ/recovery and Resilience4j protection of SRI.
5. Portal for users, companies, certificate upload and API client rotation,
   documents dashboard, sandbox and usage reports.
6. Webhooks, audit/metrics, alerting, backup, load tests and deployment.
7. Additional fiscal document types and subscription billing.

Keep a modular monolith until load and ownership requirements justify
splitting into services. The local frontend at :4321 is not assumed to
be accessible to GitHub or this branch.
