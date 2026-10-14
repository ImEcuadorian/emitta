# Informe de la etapa local — 2026-10-09

## Resultado

Se auditó la rama consolidada, el PR #1 y el roadmap #2. Se completó una primera etapa verificable del RIDE y las consultas, junto con el rediseño comercial. La plataforma SaaS completa sigue pendiente; no se declara preparación para producción.

Backend: rama local codex/ride-consolidation. Frontend: proyecto independiente emitta-web. No hay commit, push, merge, despliegue ni llamadas SOAP reales nuevas. No se tocaron estados, certificados o artefactos fiscales del piloto.

## Cambios

- Se incorporó selectivamente el renderer RIDE del PR #1 y se corrigieron compilación, soporte tipográfico y paginación. Las fuentes Noto Sans tienen licencia OFL incluida.
- El RIDE procede del XML autorizado almacenado, tras verificación de SHA-256 y firma. Se preservan importes y PDF existentes.
- Hook opcional tras autorización; fallos PDF se registran sin revertir autorización ni repetir la operación fiscal.
- GET de estado y GET de artefactos con JWT, scopes independientes, tenant activo, ownership antes de almacenamiento, no-store y digest ETag.
- La lectura de artefactos se desacopló de la activación de firma; no necesita la clave maestra AES.
- OpenAPI añade dos GET y conserva sin modificaciones los seis contratos y esquemas previos. Copia pública frontend idéntica.
- Landing comercial clara, diseño responsive, FAQ nativo, destinos CTA y documentación actualizada. No se inventaron portal, precios, clientes ni funcionalidades públicas.

## Pruebas ejecutadas

| Ejecución | Resultado |
| --- | --- |
| compileJava + batería Test-LocalSigning | BUILD SUCCESSFUL; 213 casos: 212 pasados, 1 omitido (consulta SRI live deshabilitada), 0 fallos |
| objectStorageIntegrationTest | BUILD SUCCESSFUL; 3 casos pasados, 0 fallos; PostgreSQL Testcontainers y MinIO real en bucket separado |
| RIDE + fallo postautorización + integridad/almacenamiento | BUILD SUCCESSFUL; 15 casos pasados, 0 fallos |
| PDF visual | Una página Unicode y cuatro páginas para 120 ítems inspeccionadas; código de barras decodificado; fuentes incrustadas |
| npm run check | 0 errores, 0 warnings, 0 hints |
| npm run build | 10 páginas estáticas generadas |
| npm run check:links | 146 enlaces/anclas locales verificados |
| npm audit | 0 vulnerabilidades |
| Contrato OpenAPI | 8 operaciones, referencias válidas, 6 contratos originales y componentes intactos |
| git diff --check y cached --check | Sin errores de whitespace |
| Auditoría de patrones sensibles | 46 archivos de texto inspeccionados, sin coincidencias de identidad privada del piloto, bloques de claves privadas o patrones de credenciales. No sustituye una auditoría integral de historial. |

Los tests no demuestran una nueva autorización real del SRI: el XML del E2E es sintético y la confirmación fiscal se simula en la base aislada. La configuración de transmisión real permanece desactivada.

El build omite sitemap porque aún no hay dominio oficial confirmado. npm audit requirió NODE_OPTIONS=--use-system-ca para utilizar la CA del sistema, sin deshabilitar TLS.

## Ejecución local

Backend, desde emitta:

```powershell
doppler run -- powershell -NoProfile -File .\scripts\Test-LocalSigning.ps1
.\gradlew.bat test --tests '*PdfBoxInvoiceRideGeneratorTest' --tests '*SriAuthorizationWorkflowConfigurationTest' --tests '*LoadDocumentArtifactServiceTest' --tests '*StoreDocumentArtifactServiceTest' --console=plain
```

Frontend:

```powershell
Set-Location 'D:\Programación\Proyectos Java\emitta-web'
$env:NODE_OPTIONS='--use-system-ca'
npm ci
npm run check
npm run build
npm run check:links
npm run dev
```

Revisión: http://localhost:4321/. La vista previa de producción ya está ejecutándose localmente. Capturas privadas en emitta-web/review (ignorado). Muestras PDF sintéticas en emitta/build/ride-samples (ignorado).

Nueva variable EMITTA_RIDE_ENABLED=false; no hay migraciones nuevas. Habilitar RIDE requiere almacenamiento; no activa worker, outbox o SRI. No se proporciona aquí un comando de activación fiscal automático.

## Pendientes y bloqueadores

- Redis rate limiting, RabbitMQ DLQ/recovery y protección Resilience4j aplicada: siguiente etapa prioritaria.
- Reintento durable sólo de generación RIDE, listado/filtros/historial y carga.
- Login humano, organizaciones, dashboard conectado, API clients, webhooks, notificaciones y cuotas.
- Alta REST segura de certificados sigue sin implementarse; AES-GCM existente se conserva.
- Canal oficial de contacto NEXORF y dominio pendientes de confirmar; /access explica la situación sin recoger datos ni simular un formulario operativo.
- Logos y casos fiscales especiales del RIDE, backups/restauración, QA de carga y requisitos comerciales/normativos antes de lanzamiento.

El roadmap detallado está en local-consolidation-roadmap.md. No se publicó el avance aún sin revisión en GitHub.

## Archivos del backend modificados o añadidos

- build.gradle.kts
- docs/architecture/local-consolidation-roadmap.md
- docs/domain/ride-generation.md
- src/main/java/io/github/imecuadorian/emitta/auth/adapter/config/SecurityConfiguration.java
- src/main/java/io/github/imecuadorian/emitta/auth/adapter/config/TenantAdministrationAccess.java
- src/main/java/io/github/imecuadorian/emitta/document/adapter/in/web/DocumentQueryController.java
- src/main/java/io/github/imecuadorian/emitta/document/application/exception/DocumentUnavailableException.java
- src/main/java/io/github/imecuadorian/emitta/document/application/port/in/ReadDocumentUseCase.java
- src/main/java/io/github/imecuadorian/emitta/document/application/service/ReadDocumentService.java
- src/main/java/io/github/imecuadorian/emitta/documentartifact/adapter/config/ArtifactStorageConfiguration.java
- src/main/java/io/github/imecuadorian/emitta/fiscalsigning/adapter/config/SigningConfiguration.java
- src/main/java/io/github/imecuadorian/emitta/ride/adapter/config/RideConfiguration.java
- src/main/java/io/github/imecuadorian/emitta/ride/adapter/out/pdf/PdfBoxInvoiceRideGenerator.java
- src/main/java/io/github/imecuadorian/emitta/ride/adapter/out/xml/AuthorizedInvoiceRideReader.java
- src/main/java/io/github/imecuadorian/emitta/ride/application/model/InvoiceRideRequest.java
- src/main/java/io/github/imecuadorian/emitta/ride/application/port/in/GenerateAndStoreInvoiceRideUseCase.java
- src/main/java/io/github/imecuadorian/emitta/ride/application/port/out/AuthorizedInvoiceRideReaderPort.java
- src/main/java/io/github/imecuadorian/emitta/ride/application/port/out/InvoiceRidePdfGeneratorPort.java
- src/main/java/io/github/imecuadorian/emitta/ride/application/service/GenerateAndStoreInvoiceRideService.java
- src/main/java/io/github/imecuadorian/emitta/sriauthorization/adapter/config/SriAuthorizationWorkflowConfiguration.java
- src/main/resources/application.yaml
- src/main/resources/fonts/NotoSans-Bold.ttf
- src/main/resources/fonts/NotoSans-Regular.ttf
- src/main/resources/fonts/OFL.txt
- src/main/resources/fonts/README.md
- src/main/resources/static/openapi/emitta-openapi.yaml
- src/test/java/io/github/imecuadorian/emitta/auth/integration/SecurityIntegrationTest.java
- src/test/java/io/github/imecuadorian/emitta/fiscalprocessing/integration/FiscalProcessingArtifactEndToEndIntegrationTest.java
- src/test/java/io/github/imecuadorian/emitta/ride/adapter/out/pdf/PdfBoxInvoiceRideGeneratorTest.java
- src/test/java/io/github/imecuadorian/emitta/sriauthorization/adapter/config/SriAuthorizationWorkflowConfigurationTest.java

## Archivos principales del frontend

- src/pages/index.astro, access.astro, project-status.astro.
- src/styles/landing.css.
- src/content/docs/docs/{index,authentication,invoices,documents}.md.
- astro.config.ts, public/openapi/emitta-v1.yaml.
- scripts/check-static.ts, package.json, README.md, .gitignore.
