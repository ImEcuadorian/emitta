# Provider policy

Issuer RUC and software-provider RUC are separate identities. Never infer the provider from the taxpayer, business name or signing certificate.

The trusted operator configuration selects a profile per tenant and taxpayer using `emitta.fiscal.provider-policy.assignments`. Bracketed keys preserve the `tenant-uuid:taxpayer-uuid` separator in Spring property binding. Prefer explicit assignments over a deployment-wide resolved default.

| Mode | Behavior |
|---|---|
| UNRESOLVED | Blocks XML generation and reception before state/attempt changes |
| EXTERNAL_PROVIDER | Requires identified provider RUC and reviewed evidence; signed XML must match |
| VERIFIED_NOT_APPLICABLE | Omits provider field only with a verified normative justification |

Resolved modes require justification, official SRI/Registro Oficial HTTPS reference and a private evidence file with matching SHA-256. Hash integrity is not proof of a legal conclusion. API clients cannot override profiles. Synthetic test evidence never authorizes real operations.

The XML schema permitting omission and an SRI TEST authorization do not establish a legal exemption. Resolve ambiguous cases with the SRI before normal transmission. The product has no experimental provider-policy bypass.

Configuration is injected by the deployment operator; private evidence stays outside Git and container images. The legacy `EMITTA_PROVIDER_RUC` does not resolve the policy.

Sources: [SRI electronic invoicing and current specification](https://www.sri.gob.ec/facturacion-electronica), Resolution NAC-DGERCGC26-00000027 and Annex 26 of specification 2.34. Review the current regulation when changing a policy.
