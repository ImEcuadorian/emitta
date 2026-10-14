# Alta fiscal controlada de staging

## Quién crea un cliente

No existe autorregistro público. El operador global conserva `platform:admin` privadamente. Primero autentica en `POST /api/v1/auth/token`, luego crea un tenant en `POST /api/v1/tenants` y emite sus credenciales mediante `POST /api/v1/tenants/{tenantId}/api-clients`. El cliente pertenece a un tenant; su secreto se entrega una sola vez y sólo BCrypt queda en PostgreSQL. No entregar las credenciales del operador a clientes.

Ejemplo de scopes para la aplicación del cliente:

```json
{"name":"Integración del cliente","scopes":["invoices:write","documents:read","artifacts:read"]}
```

El cliente autentica con su propio `clientId`/`clientSecret` para obtener JWT; no elige otro tenant en el token. El alta fiscal/certificado utiliza un cliente administrativo separado del mismo tenant. Finalizado el onboarding, revocar ese cliente cuando no se necesite o conservarlo únicamente bajo custodia administrativa. Nunca darle `platform:admin`.

## Ejecución del 12 de octubre de 2026

- Bucket dedicado `emitta-staging-artifacts`, versioning habilitado, ninguna bucket policy pública. PUT/GET sintético con SHA-256 coincidente y lectura anónima rechazada HTTP 403.
- API staging con almacenamiento habilitado, endpoint interno `http://minio:9000`, region `us-east-1` y path-style. Los siete flags de firma/worker/Outbox/jobs/RIDE/recepción/autorización continúan false. MinIO sin puertos host ni ruta pública.
- Alta del contribuyente autorizado mediante endpoints existentes: tenant, cliente de onboarding, contribuyente ACTIVE, TEST true y producción false, establecimiento/punto 001 y cliente de integración de tres scopes. UUID y credenciales sólo en estado privado, fuera de Git. No se reutilizaron ni alteraron registros del piloto.
- `ops/deployment/onboard-staging-tenant.py` usa input/estado privados y checkpoint antes de cada POST. Ante resultado incierto se detiene para conciliación; no repite la creación automáticamente. Autenticación/secretos nunca impresos. `prepare-staging-storage.py` sólo comprueba objetos sintéticos dedicados.
- Cliente de integración: GET de certificados HTTP 403; consultas de artefactos inexistentes autenticadas HTTP 404. Health 200/UP, documento y métricas sin JWT 401. La descarga real de XML/PDF aún requiere sus artefactos; estos checks no acreditan generación o descarga fiscal completa.

## Certificado: paso interactivo pendiente

El PKCS#12 sigue fuera del repositorio. El operador ejecuta `Import-ReviewedCertificate.ps1` con parámetros de rutas privadas y UUID real. `-OnboardingStatePath` permite obtener el JWT directamente desde las credenciales privadas sin copiarlas al chat; la contraseña siempre se solicita con `Read-Host -AsSecureString`. Revisión explícita, HTTPS y sin redirects/retries. La identidad, PKIX y CRL se verifican nuevamente antes de AES-GCM. La evidencia histórica se conservó intacta y se creó un registro privado adicional del alcance staging autorizado; no es un instrumento jurídico firmado ni autorización fiscal.

## Preparación manual

Se añadió el perfil `manual-test-preparation` y una configuración en el módulo fiscal existente, sin otro cliente SOAP ni servicios paralelos. Sólo base `emitta_staging`, sin HTTP server y con flags automáticos/SRI explícitamente false. Reutiliza ProcessFiscalDocumentUseCase y almacenamiento; valida tenant, TEST y QUEUED/SIGNED, confianza/CRL y RUC certificado. Revalida XSD y firma independientemente con JDK y guarda un informe nuevo 0600 en directorio 0700. No imprime XML, claves ni contraseña. No altera estados manualmente, no publica Outbox y no llama al SRI.

Este comando todavía no fue ejecutado con el certificado real. La política `UNRESOLVED` mantiene el bloqueo normal. Para la operación experimental anteriormente autorizada, existe una omisión técnica explícita limitada al documento/RUC/TEST del comando síncrono mediante `emitta.manual.acknowledge-provider-unresolved=true` y `emitta.manual.expected-ruc`. Un proveedor externo identificado continúa siendo obligatorio. No se declaró una excepción jurídica, no se puso el RUC del emisor como proveedor y no se habilitó producción/worker/recepción. Controles y estado operativo en [panel y cierre de staging](admin-panel-and-staging-closure.md).

Validación focalizada: siete casos aprobados (dos guardas del comando, cuatro multipart/certificado, uno DSS), compileJava y bootJar aprobados, 34 s en la ejecución final. No suite completa nueva, ninguna factura real creada y ninguna llamada SRI. El build completo previo conserva una prueba SRI real omitida.

Siguiente paso dependiente del usuario: importación interactiva. Después cerrar el alcance técnico del proveedor y preparar una factura nueva revisable; la transmisión necesita aprobación separada. RIDE/autorización sólo se prueban después de autorización fiscal verificada. No afirmar que toda la emisión fiscal de staging esté completada.


## Actualización: certificado importado y primera preparación staging

El operador completó la importación interactiva real. La consulta HTTPS de metadatos confirmó un certificado ACTIVE, sin descargar ni descifrar el PKCS#12 para inspección. Una factura nueva TEST se creó por la API con cliente de integración y clave de idempotencia única; el intent/checkpoint se guardó privadamente antes del POST.

La ejecución manual reutilizó la imagen existente y el perfil manual-test-preparation, sin migraciones, workers, publisher, jobs, RIDE ni clientes SRI activos. Primera invocación falló antes de iniciar Java por falta de selección del fichero Compose; se conservó el log y se corrigió a compose.staging.yaml. La segunda invocación finalizó SIGNED con comprobación actual de CA/CRL, identidad certificada por RUC, XSD de ambos XML, verificador criptográfico independiente y SHA-256 contra MinIO. Informe 0600 retenido fuera del repositorio; no se modificó ningún comprobante del piloto previo.

Revisión REST posterior: SIGNED, TEST, total 20.70. Consulta PostgreSQL en transacción READ ONLY: cero intentos SRI_SUBMISSION. Dos XML almacenados. UUID, clave de acceso, certificado y hashes se conservan en informe privado y no se publican en documentación o tests.

Aún no hay XML autorizado ni PDF de esta factura nueva. Falta aprobación específica para una sola transmisión a certificación y consulta posterior; una respuesta AUTHORIZED verificada permitirá generar y descargar el RIDE mediante casos de uso existentes. Provider-policy sigue UNRESOLVED: sólo omisión técnica limitada a la preparación manual TEST, no decisión normativa. La guarda normal de envío permanece bloqueada; antes de transmitir deberá aplicarse la operación manual explícita y limitada prevista, sin modificar la política persistente ni abrir recepción automática.

Scripts ops/deployment: inspect-staging-certificate.py consulta metadatos; prepare-staging-invoice.py registra una única creación con checkpoint; run-staging-signing.sh ejecuta la preparación limitada al staging; review-staging-invoice.py consulta el estado/informe; check-staging-reception-count.sh cuenta intentos con lectura exclusiva; check-private-preparation.py clasifica logs sin imprimirlos. No contienen certificados, credenciales ni datos del emisor.
# Transmisión manual aprobada y RIDE verificado — 2026-10-12

El operador autorizó explícitamente una sola transmisión del documento TEST previamente preparado. Se ejecutó con el perfil `manual-test-transmission`, en un contenedor temporal, reutilizando los casos de uso de recepción, conciliación y RIDE. La API permanente conservó su imagen y los siete flags de procesamiento deshabilitados.

Resultado real: recepción `RECEIVED` (RECIBIDA), sin mensajes; una consulta de autorización `AUTHORIZED`, sin mensajes. PostgreSQL conserva un único intento `SRI_SUBMISSION` y los cuatro artefactos `UNSIGNED_XML`, `SIGNED_XML`, `AUTHORIZED_XML` y `RIDE_PDF`. La descarga HTTPS del PDF mediante el cliente de integración devolvió 200; sin JWT devolvió 401. Su SHA-256 coincide con MinIO y con la copia privada. El PDF se revisó visualmente: una página A4, ambiente de pruebas, autorización y total coherentes.

Identificadores, clave de acceso, hashes concretos, PDF y reporte se conservan exclusivamente en el directorio privado del operador, fuera de Git. No se cambiaron certificados ni datos del piloto anterior. Esta autorización de certificación no resuelve la clasificación normativa del proveedor.

El perfil exige el tenant/documento/RUC/clave de acceso/certificado/digest aprobados, base `emitta_staging`, documento TEST firmado y cero intentos antes de recepción. Valida XSD, PKIX/CRL e independencia criptográfica de la firma antes del POST. Las cuatro URL configuradas, incluidas las de producción del proceso temporal, deben ser exactamente las de `celcer.sri.gob.ec`. La omisión técnica de proveedor sólo vive en un scope síncrono de ese documento/RUC TEST y no modifica la política persistida. Los retries de recepción y autorización de esta operación se fijaron a uno. El reporte se crea exclusivamente y se sincroniza a disco antes de la llamada externa. Una ejecución posterior sólo puede consultar autorización de un documento ya enviado; nunca retransmitirlo.

Validación local de este cambio: `compileJava`, `bootJar` y 17 pruebas seleccionadas pasaron, cero fallos/errores/omisiones. La suite completa anterior no se volvió a ejecutar en esta operación. No se realizó commit, push ni migración.
