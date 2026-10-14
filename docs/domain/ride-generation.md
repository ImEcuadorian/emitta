# RIDE y consulta de documentos — implementación local

## Alcance verificado

La base del PR #1 se incorporó selectivamente a `codex/ride-consolidation`; no se fusionó el PR ni se modificó `main`. PDFBox 3.0.8 y ZXing 3.5.4 generan una factura A4 con identidad del emisor y comprador, número de comprobante, ambiente, autorización, clave de acceso, Code 128, ítems, impuestos, pagos, totales e información adicional. Noto Sans Regular/Bold se incrusta bajo licencia SIL OFL. Los caracteres sin glifo provocan error; no se borran silenciosamente.

El RIDE se obtiene exclusivamente del XML autorizado persistido, tras comprobar SHA-256 y firma XAdES mediante el verificador JDK existente. Se exige estado AUTHORIZED, fecha de autorización y clave de acceso coincidente. Se guarda como RIDE_PDF con los puertos de almacenamiento existentes. Un PDF existente se conserva y reutiliza; no hay regeneración destructiva.

El reconciliador de autorización ejecuta este paso opcional después de guardar AUTHORIZED. Un fallo PDF no revierte la autorización, no cambia el estado y no retransmite. Registra métrica `emitta.ride.generation` y un error sin contenido fiscal. Falta una cola durable para reintentar exclusivamente el PDF; por ahora el caso de uso interno puede invocarse de nuevo.

## Referencia técnica

Se revisó la [ficha técnica SRI 2.34 y su Anexo 2](https://www.sri.gob.ec/facturacion-electronica). El PDF representa el comprobante electrónico; no sustituye el XML firmado ni constituye autorización adicional. El layout conserva los valores, sin recalcularlos con datos actuales del contribuyente. Se muestran los campos adicionales y fiscales opcionales presentes en el XML. TEST se identifica como SIN VALIDEZ TRIBUTARIA.

La revisión no constituye homologación ni validación exhaustiva de todos los regímenes fiscales. Están pendientes logos, casos especiales de impuestos, mediciones de carga y contraste visual con más ejemplos oficiales.

## HTTP protegido

- GET /api/v1/documents/{documentId}: scope documents:read.
- GET /api/v1/documents/{documentId}/artifacts/authorized-xml: scope artifacts:read.
- GET /api/v1/documents/{documentId}/artifacts/ride-pdf: scope artifacts:read.

El tenant se obtiene del JWT y debe estar activo. Se verifica pertenencia antes de acceder al almacenamiento. Recursos ajenos y ausentes producen 404. Sólo se descargan artefactos de documentos AUTHORIZED. No se exponen storage keys. Las respuestas llevan no-store, nombre de archivo seguro y ETag SHA-256.

## Activación controlada

Nueva variable: EMITTA_RIDE_ENABLED=false por defecto. Para generar RIDE debe habilitarse el almacenamiento existente. La lectura de artefactos ahora pertenece a ArtifactStorageConfiguration y no exige habilitar firma ni proporcionar una clave maestra AES. No se habilitan worker, outbox, recepción o autorización por este cambio. No hay nuevas migraciones.

Pruebas locales sin SOAP real:

```powershell
doppler run -- powershell -NoProfile -File .\scripts\Test-LocalSigning.ps1
.\gradlew.bat test --tests '*PdfBoxInvoiceRideGeneratorTest' --console=plain
```

Las pruebas PDF generan muestras sintéticas en build/ride-samples, incluido un comprobante de 120 ítems y sus PNG. El E2E firma un XML sintético, simula la confirmación de autorización en Testcontainers y comprueba PostgreSQL y MinIO reales con un bucket de pruebas independiente. No afirma autorización real del SRI.
