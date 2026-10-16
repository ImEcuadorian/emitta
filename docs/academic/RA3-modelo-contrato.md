# RA3 — Modelo de datos orientado a API y contrato formal

## 1. Contrato formal y versionado

El artefacto que se entrega es el [OpenAPI 3.0.3 completo](../../src/main/resources/static/openapi/emitta-openapi.yaml) del backend en la rama de consolidación. La inspección del archivo versionado constató **20 rutas y 25 operaciones HTTP**. Su servidor público de referencia es https://api-emitta.nexorf.dev y está limitado a STAGING / SRI TEST.

La web ofrece una [referencia de integración simplificada](https://github.com/ImEcuadorian/emitta-web/blob/main/public/openapi/emitta-integration-v1.yaml) (7 rutas y 9 operaciones en la revisión consultada). No se debe confundir con el contrato completo de administración.

**Control de vigencia:** inventario actualizado tras integrar los cambios locales y académicos el 15-10-2026.

## 2. Recursos REST y operaciones del OpenAPI completo

| Recurso | Operaciones |
| --- | --- |
| Entrada personal del operador | POST /api/v1/operator/token |
| Configurar entrada personal | POST /api/v1/operator/personal-access |
| Métricas del operador | GET /api/v1/operator/overview |
| Autenticación de aplicación | POST /api/v1/auth/token |
| Facturas | POST /api/v1/invoices |
| Tenants | GET y POST /api/v1/tenants |
| Contribuyentes | POST /api/v1/tenants/{tenantId}/taxpayers |
| Establecimientos | POST /api/v1/taxpayers/{taxpayerId}/establishments |
| Puntos de emisión | POST /api/v1/establishments/{establishmentId}/points-of-issue |
| Documento | GET /api/v1/documents/{documentId} |
| Artefactos del documento | GET /api/v1/documents/{documentId}/artifacts/{type} |
| Trabajo RIDE | GET /api/v1/documents/{documentId}/ride-job |
| Clientes API | GET y POST /api/v1/tenants/{tenantId}/api-clients |
| Rotar secreto | POST /api/v1/tenants/{tenantId}/api-clients/{id}/rotate-secret |
| Revocar cliente | POST /api/v1/tenants/{tenantId}/api-clients/{id}/revoke |
| Certificados | GET y POST /api/v1/taxpayers/{taxpayerId}/certificates |
| Configuración fiscal propia | GET y POST /api/v1/fiscal-configuration |
| Configuración fiscal administrada | GET y POST /api/v1/tenants/{tenantId}/fiscal-configuration |
| Documentos del tenant | GET /api/v1/tenants/{tenantId}/documents |
| Artefactos del tenant administrado | GET /api/v1/tenants/{tenantId}/documents/{documentId}/artifacts/{type} |

Son **operaciones HTTP**, no equivalentes a funcionalidades activas sin configuración. Algunas rutas administrativas están reservadas a usuarios/credenciales de operador y no aparecen en la referencia pública para integradores.

## 3. Modelo lógico — entidades y dependencias

~~~mermaid
erDiagram
  TENANTS ||--o{ API_CLIENTS : contiene
  API_CLIENTS ||--o| OPERATOR_PERSONAL_ACCESS : autentica
  TENANTS ||--o{ TAXPAYERS : registra
  TENANTS ||--o{ DOCUMENTS : posee
  TENANTS ||--o{ OUTBOX_EVENTS : produce
  TAXPAYERS ||--o{ ESTABLISHMENTS : tiene
  TAXPAYERS ||--o{ CERTIFICATES : custodia
  ESTABLISHMENTS ||--o{ POINTS_OF_ISSUE : contiene
  POINTS_OF_ISSUE ||--o{ DOCUMENT_SEQUENCES : numera
  DOCUMENTS ||--o| INVOICES : especializa
  INVOICES ||--|{ INVOICE_ITEMS : compone
  INVOICE_ITEMS ||--o{ INVOICE_ITEM_TAXES : tributa
  DOCUMENTS ||--o{ DOCUMENT_ARTIFACTS : almacena
  DOCUMENTS ||--o| SRI_AUTHORIZATIONS : acredita
  DOCUMENTS ||--o{ PROCESSING_JOBS : procesa
  PROCESSING_JOBS ||--o{ PROCESSING_JOB_ATTEMPTS : registra
  TENANTS ||--o{ ADMINISTRATIVE_AUDIT_EVENTS : audita
~~~

Este diagrama se centra en tablas creadas por las migraciones examinadas. Para cada campo y cardinalidad definitiva prevalece **el esquema Flyway real**, no un diseño conceptual previo. Las relaciones de negocio no garantizan claves FK donde no existen restricciones físicas; contrastar en el DDL.

## 4. Diccionario de datos resumido

| Tabla real / familia | Clave y campos relevantes | Propósito y regla |
| --- | --- | --- |
| operator_personal_access | api_client_id PK/FK; email UNIQUE; password_hash; failures; locked_until | Entrada del dueño, hash de contraseña y bloqueo temporal |
| tenants | id UUID PK; name; status | Organización y límite de cuenta |
| api_clients | id; tenant_id FK; client_id UNIQUE; client_secret_hash | Credenciales de aplicaciones, nunca secreto recuperable |
| taxpayers | id; tenant_id; ruc; legal_name; test_enabled; production_enabled | Emisor, RUC único dentro de tenant |
| establishments | id; taxpayer_id; code CHAR(3) | Código único por contribuyente |
| points_of_issue | id; establishment_id; code CHAR(3) | Código único por establecimiento |
| document_sequences | id; point_of_issue_id; document_type; environment; current_value | Secuencial único por punto/tipo/ambiente |
| certificates | id; taxpayer_id; fingerprint; encrypted_content; encrypted_password | Material cifrado, unicidad de huella por emisor |
| customers | id; tenant_id; identification; email/address | Catálogo auxiliar; distinto de tenant y comprador embebido |
| documents | id; tenant_id; point_of_issue_id; status; idempotency_key | Raíz fiscal y trazabilidad de creación |
| invoices | document_id FK/PK; buyer_name; buyer_identification; total | Snapshot fiscal de factura |
| invoice_items / invoice_item_taxes | invoice_id; line_number; amount; taxable_base | Detalle y desglose tributario |
| invoice_payments | referencia a factura; forma y total | Pagos declarados |
| outbox_events | id; aggregate_id; event_type; payload; published_at | Evento durable para publicar en RabbitMQ |
| document_artifacts | document_id; artifact_type; storage_key; sha256 | Metadatos y hash XML/PDF; objeto vive en S3 |
| sri_authorizations / sri_authorization_messages | document_id PK; authorization_number; authorized_at | Resultado fiscal verificado y mensajes |
| processing_jobs | id; document_id; kind; state; attempts; lease | Trabajo durable fiscal/RIDE, UNIQUE(document_id, kind) |
| processing_job_attempts | job_id + attempt_number PK; result | Diario de intentos y recuperaciones |
| administrative_audit_events | id; tenant_id; actor_client_id; action; resource_id | Auditoría de acciones administrativas |

**Precisión:** la tabla webhook_endpoints existe en la migración de dominio, pero el envío de webhooks comerciales aún no está implementado. No confundir presencia en el esquema con una característica entregada. V15 añade el tipo de trabajo SRI_TEST, desactivado por defecto; V16 incorpora operator_personal_access, vinculado a un cliente privilegiado existente.

## 5. Restricciones de integridad representativas

- Unicidad del identificador de cliente API; el secreto se almacena con hash.
- RUC de taxpayer único por tenant, no global.
- Establecimientos y puntos de emisión con códigos de tres dígitos y unicidad dentro de su padre.
- Secuencia única por (point_of_issue_id, document_type, environment) y asignación atómica.
- Idempotencia por tenant + clave; replay con el **mismo body y clave** devuelve el documento existente.
- Una única clase de artefacto por documento: UNIQUE(document_id, artifact_type).
- SHA-256 de artefactos en formato hex de 64 caracteres.
- El registro sri_authorizations enlaza el resultado a un documento autorizado y una clave de 49 dígitos.
- Un único job por par (document_id, kind), con lease/journal de intentos.

La combinación de constraints PostgreSQL y validaciones del dominio reduce inconsistencias; JWT solo no garantiza aislamiento si faltan comprobaciones de pertenencia.

## 6. Contratos y respuestas HTTP fundamentales

### POST /api/v1/auth/token

Entrada: clientId y clientSecret.
Salida: accessToken, tokenType Bearer y expiresIn.
Seguridad: credenciales validadas y JWT RS256 temporal. La emisión de un JWT **no implica** que exista un servidor de autorizaciones OAuth2 completo.

### POST /api/v1/invoices

Headers: Authorization: Bearer (scope invoices:write), Idempotency-Key no vacío, Content-Type application/json.
Datos principales: pointOfIssueId, environment TEST, issuedAt, buyer, items, taxes, payments y expectedTotal.

**HTTP 202:** creación aceptada/durable en Emitta, NO autorización fiscal.
**HTTP 200:** replay exacto de clave y contenido; devuelve el documento existente.
**HTTP 429/503:** limitación o indisponibilidad según motivo. Nunca crear otra clave de idempotencia para resolver una respuesta ambigua.

### GET /api/v1/documents/{documentId}

Consulta con token scope documents:read; el documento debe pertenecer a ese tenant. El estado puede seguir QUEUED o progresar cuando la operación fiscal esté habilitada.

### GET /api/v1/documents/{documentId}/artifacts/{type}

Acceso scope artifacts:read; devuelve XML autorizado o PDF RIDE únicamente si existen y están disponibles, conservando integridad SHA-256. No se ofrece almacenamiento público.

### Configuración fiscal y certificados

Los endpoints administrativos requieren scopes adecuados, aislamiento de la cuenta y validaciones de identidad/PKCS#12. Los datos secretos nunca deben aparecer en GET.

## 7. Ejemplo de factura sintética

~~~json
{
  "pointOfIssueId": "00000000-0000-4000-8000-000000000003",
  "environment": "TEST",
  "issuedAt": "2026-10-15T15:00:00Z",
  "buyer": {
    "identificationType": "07",
    "identification": "9999999999999",
    "name": "CONSUMIDOR FINAL"
  },
  "items": [{
    "sku": "P001",
    "description": "Producto de demostracion",
    "quantity": 2,
    "unitPrice": 10,
    "discount": 2,
    "taxes": [{"taxCode": "2", "percentageCode": "4", "rate": 15}]
  }],
  "payments": [{"paymentMethod": "01", "total": 20.70}],
  "expectedTotal": 20.70
}
~~~

La identificación ficticia especial del ejemplo se usa únicamente en ambiente TEST y con reglas del SRI verificadas. El UUID de ejemplo no corresponde a un punto de emisión real y debe sustituirse. Subtotal tras descuento 18,00; 15 % = 2,70; total 20,70.

## 8. Control del contrato

- OpenAPI 3.0.3: fuente backend indicada arriba.
- Frontend Astro/Starlight/Scalar: catálogo de integración; API administrativa separada.
- Versionado por prefijo /api/v1 y etiquetas del contrato.
- Migraciones Flyway V1–V16 de la versión integrada.
- En la entrega, adjuntar el YAML completo descargado de la rama efectivamente presentada y capturas de la documentación Scalar; NO copiar una versión antigua.
- El archivo [ERD original](../domain/erd.md) describe también ideas de producto futuras y debe acompañarse con este modelo implementado.

## Conclusión RA3

El contrato relaciona recursos HTTP con entidades y restricciones; las respuestas distinguen persistencia y autorización fiscal. El diseño es verificable por las migraciones, OpenAPI y pruebas de aislamiento/idempotencia, sujeto a sincronizar los cambios locales recientes antes de la entrega.
