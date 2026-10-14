# Etapa 2 — resiliencia y recuperación

Trabajo local en `codex/ride-consolidation`. Se conservan los cambios anteriores. No se realizaron commit, push, merge, despliegue ni llamadas a servicios del SRI. La autorización fiscal real y sus evidencias no se modificaron.

Este documento conserva la evidencia histórica de etapa 2. La [revalidación del 2026-10-12](../operations/resilience-revalidation-2026-10-12.md) contrasta esos requisitos con la rama actual, incluyendo las mejoras de etapas posteriores, y registra los resultados nuevos sin sustituir los anteriores.

## Problemas encontrados

- Redis estaba configurado, pero no controlaba el tráfico.
- RabbitMQ carecía de DLQ y el consumidor dependía de completar el procesamiento antes de confirmar. Un publisher confirm positivo no comprobaba mensajes devueltos por falta de routing.
- Resilience4j estaba declarado, sin proteger los adaptadores SOAP reales.
- El hook de RIDE intentaba generar el PDF una sola vez: un fallo conservaba la autorización, pero no dejaba un trabajo recuperable.
- El identificador aleatorio del PDF impedía reproducir exactamente sus bytes después de un fallo entre subir a S3 y registrar metadatos.

## Comportamiento implementado

### Redis

Token Bucket mediante Lua y reloj de Redis. Una ejecución evalúa y consume todos los buckets de una fase de forma atómica. Se comparten entre instancias, con claves SHA-256 y un mismo hash slot para compatibilidad con Redis Cluster. Se aplican buckets globales y por ruta normalizada: IP antes del JWT; cliente y tenant después; autenticación además comprueba el identificador de cliente antes de BCrypt.

Políticas de `emitta.security.rate-limit.policies`: dimensiones `ip`, `client`, `tenant`; una clave como `tenant-invoices` sobrescribe el límite de esa operación. Rutas normalizadas: `authentication`, `invoices`, `documents`, `artifacts`, `ride-status`, `tenants`, `taxpayers`, `establishments`, `points-of-issue`. Capacidad y recarga son configurables. No son cuotas comerciales.

`429` incluye `Retry-After`. Redis indisponible produce `503` con `Retry-After`, sin permitir libremente solicitudes nuevas. Los workers durables siguen funcionando: Redis no almacena estados fiscales. No hay fallback local que multiplique los límites por número de instancias.

Se utiliza únicamente `getRemoteAddr`; `server.forward-headers-strategy=NONE`. Ningún proxy ni cabecera forwarded se considera confiable. Detrás de un proxy, su IP comparte bucket; configurar identidades de cliente/tenant y diseñar una lista explícita de proxies confiables antes de habilitar IP real. El hash slot común puede ser un punto de capacidad a medir bajo carga.

### RabbitMQ y Outbox

Topología conservada: exchange `emitta.events`, queue `emitta.fiscal.documents`, routing `document.received.v1`. Nuevos exchange `emitta.deadletters`, queue `emitta.processing.dlq` y bindings para mensajes inválidos y `processing.failed.v1`.

No se cambian argumentos inmutables de la cola existente. `scripts/Configure-RabbitDlqPolicy.ps1` configura una política mutable DLX. Su aplicación se probó únicamente en RabbitMQ de Testcontainers; no se aplicó al broker privado.

Publicación persistente, mandatory, publisher confirms correlacionados y comprobación de returned messages. Un evento se marca publicado únicamente tras confirmación y routing. Si el broker está caído permanece en PostgreSQL, con backoff exponencial configurable y jitter. El Outbox conserva eventos sin limitar su retención: sus reintentos de transporte no son reintentos fiscales.

El listener valida el evento, tenant y estado, y registra el trabajo durable antes del AUTO ACK. Eventos inválidos se publican en DLQ con confirmación antes de ACK. Si la admisión falla, hay tres intentos locales por defecto con espera; al agotarlos se publica el lote en DLQ con delivery persistente, categoría, ID original, `x-emitta-admission-exhausted=true` y `x-emitta-admission-attempts`. Sólo se permite ACK tras confirmar y comprobar routing de todas las publicaciones. Si una confirmación falla, falta routing o el resultado es desconocido, se conserva/reencola el lote original tras cooldown; pueden aparecer duplicados de cuarentena, identificados por ID original. Los intentos registrados en el mensaje reflejan el ciclo confirmado y los contadores heredados, no intentos anteriores cuyos registros no pudieron persistirse durante una caída total. Los intentos de jobs admitidos permanecen en PostgreSQL con su journal.

No hay consumidor automático de DLQ. Los eventos duplicados admiten un solo trabajo por documento/tipo; estados ya firmados/autorizados no vuelven a ejecutar el flujo. `SUBMITTED`/`RETRY_PENDING` se clasifican como resultado externo desconocido.

### Protección SRI

Se envuelven los puertos existentes; se reutilizan los clientes SOAP actuales. Circuit Breaker y Bulkhead independientes para recepción/autorización. Recepción tiene exactamente un intento. Autorización permite por defecto dos intentos totales solo ante comunicación fallida; respuestas fiscales definitivas y fallos de validación no se reintentan.

Los timeouts son de conexión de `HttpClient`, espera de cabeceras de `HttpRequest` y lectura efectiva del cuerpo mediante un `BodySubscriber` con deadline que cancela su suscripción. En Java 25 el timeout de solicitud no cubre por sí solo toda la lectura; el [cambio oficial en Java 26](https://inside.java/2026/03/04/jdk-26-http-client/) documenta esa limitación. Se reutiliza `request-timeout` como límite para cada etapa de cabeceras/cuerpo, por lo que el máximo de una llamada puede aproximarse al doble de ese valor; los retries de autorización suman sus propios límites. No se usa TimeLimiter para fingir que cancela I/O bloqueante. HTTP 429/5xx se clasifica como comunicación. La recepción conserva su resultado potencialmente incierto; no genera reenvíos ni nuevas claves.

El caso de uso existente `QueryDocumentAuthorizationUseCase` concilia usando la clave de acceso persistida de documentos `SUBMITTED`, `RETRY_PENDING` o `AUTHORIZED`. No se añadió conciliación automática ni endpoint público de envío. Los workers locales y la recuperación de RIDE nunca llaman a recepción. Los circuitos son por instancia; no sustituyen un límite global de conexiones hacia el SRI. Se conserva la estrategia conservadora existente: el caso de uso registra SUBMITTED antes del puerto; incluso un rechazo previo a I/O por circuito/Bulkhead requiere revisión/conciliación y no habilita retransmisión automática. Un estado explícito de rechazo antes de transmisión puede evaluarse posteriormente sin debilitar la protección fiscal.

### RIDE y trabajos durables

V12 crea `processing_jobs` y `processing_job_attempts`, sin modificar documentos, certificados ni evidencias existentes. FISCAL y RIDE tienen trabajos únicos por documento, journal de intentos, leases con token, backoff+jitter, límite de intentos y recuperación explícita.

Se exige autorización persistida V10, ambiente/fecha/número coherentes y SHA-256 coincidente del `AUTHORIZED_XML` para crear el trabajo RIDE. El hook solo encola. Un scanner detecta autorizados sin PDF y repara un fallo de encolado. La generación existente vuelve a comprobar XML, firma y hash. El PDF tiene bytes reproducibles y conserva la integridad SHA-256.

El worker bloquea la fila del trabajo con una conexión dedicada; otros workers usan `SKIP LOCKED`. Así un trabajo en ejecución no se reclama aunque expire su lease. Sus efectos mantienen las transacciones originales del flujo fiscal. Después de reiniciar, leases expirados permiten recuperar trabajo pendiente. Esto requiere conexiones disponibles para el bloqueo y para los servicios; dimensionar pool/batch por instancia.

Estados de trabajo: `PENDING`, `RUNNING`, `COMPLETED`, `DEAD`, `BLOCKED`. Categorías: `TRANSIENT`, `VALIDATION`, `INTEGRITY`, `PERMANENT`, `EXTERNAL_UNKNOWN`. Solo errores temporales reciben retries. Al agotarlos se registra un aviso Outbox para DLQ. `BLOCKED` nunca se recupera automáticamente. Un fallo del PDF deja la autorización intacta.

Nuevo GET `/api/v1/documents/{documentId}/ride-job`: JWT, `artifacts:read`, tenant activo y propiedad del documento; no-store; devuelve estado, intentos, próxima fecha y categoría. No devuelve XML, claves de objetos ni secretos. OpenAPI contiene nueve operaciones; la copia estática de la web y su documentación técnica se sincronizan sin modificar la landing.

## Operación local

Verificación aislada con Docker Desktop activo, sin Doppler ni secretos privados:

```powershell
powershell -NoProfile -File .\scripts\Test-Resilience.ps1
```

El script bloquea la prueba SRI live y los flags de procesamiento, fuerza una nueva ejecución del build y usa Testcontainers para PostgreSQL/Redis/RabbitMQ. Reutiliza `Test-IsolatedStorage.ps1` para crear MinIO desechable con credenciales y bucket sintéticos, publica sólo en loopback y elimina únicamente su propio contenedor al terminar. No limpia ni toca buckets del piloto. Los valores de entorno se restauran al salir. El build no requiere iniciar el backend contra la base privada ni sudo en el servidor.

V12 está preparada y se valida con Flyway en Testcontainers. No fue aplicada a la base privada. Antes de iniciar la aplicación allí: respaldo verificado, revisión de permisos del usuario migrador y aplicación controlada con los workers deshabilitados. El init PostgreSQL existente concede privilegios por defecto a tablas creadas por el rol migrador; verificarlo si la base se creó por otro procedimiento.

Configurar opcionalmente la política DLX local:

```powershell
doppler run -- powershell -NoProfile -File .\scripts\Configure-RabbitDlqPolicy.ps1
```

Inspección de DLQ: usar el panel RabbitMQ restringido a loopback con una cuenta de operador, comenzar por cantidades y headers/categoría. No copiar payloads ni XML a logs o repositorio. Los avisos `processing.failed.v1` permiten localizar el trabajo en PostgreSQL. No reinyectar en lote mensajes ni usar una redelivery como orden de transmisión fiscal.

Consultar trabajos con una conexión readonly administrada, parámetros tenant/job y sin imprimir documentos:

```sql
SELECT id, tenant_id, document_id, kind, state, attempts, recoveries,
       next_attempt_at, failure_category
FROM emitta.processing_jobs
WHERE tenant_id = :tenant_id AND id = :job_id;
SELECT attempt_number, started_at, finished_at, result
FROM emitta.processing_job_attempts
WHERE job_id = :job_id ORDER BY attempt_number;
```

Después de corregir una causa temporal, un operador autorizado puede reprogramar un solo trabajo `DEAD/TRANSIENT`:

```powershell
doppler run -- powershell -NoProfile -File .\scripts\Recover-ProcessingJob.ps1 -TenantId '<UUID_TENANT>' -JobId '<UUID_JOB>'
```

El comando pide `RECOVER`, deshabilita workers/SRI y no cambia estados fiscales. Rechaza tenant ajeno, validación, integridad y resultados externos desconocidos. Registra el nuevo presupuesto y contador de recuperaciones. La ejecución posterior del worker se habilita separadamente. Esta herramienta requiere acceso operativo a Doppler/base de datos; no es un endpoint para clientes.

Para una admisión agotada que aún no creó job: inspeccionar primero la categoría y el ID original en DLQ, restaurar DB/broker y verificar el evento original con una conexión readonly. Un operador puede usar el mismo comando con otro conjunto de parámetros:

```powershell
doppler run -- powershell -NoProfile -File .\scripts\Recover-ProcessingJob.ps1 -TenantId '<UUID_TENANT>' -DocumentId '<UUID_DOCUMENT>' -EventId '<UUID_ORIGINAL_OUTBOX>'
```

No copia ni reinyecta el payload DLQ: verifica tenant, documento QUEUED/GENERATING, evento original publicado `document.received.v1` y coherencia del payload persistido, y encola un único job FISCAL si falta. Rechaza SUBMITTED/RETRY_PENDING, IDs ajenos o inventados y jobs ya existentes. Registra una recuperación en el job, conserva el mensaje DLQ como evidencia y no altera la factura ni genera otra clave de acceso. La CLI sólo admite un conjunto de parámetros, mantiene workers/SRI apagados y exige `RECOVER`. Nunca ejecutarla sobre el piloto sin autorización ni antes de aplicar V12 de forma controlada.

Para procesar RIDE durables hacen falta `EMITTA_PROCESSING_JOBS_ENABLED=true`, `EMITTA_RIDE_ENABLED=true` y almacenamiento configurado. Con `EMITTA_RIDE_ENABLED=false` no se escanea ni procesa RIDE, y los trabajos persistidos se conservan. FISCAL requiere además worker, outbox, almacenamiento y firma correctamente configurados. No activar esos flags en el entorno privado sin revisar primero sus trabajos pendientes. Ninguna de estas activaciones autoriza envíos al SRI.

## Variables añadidas

| Variables | Default |
| --- | --- |
| `EMITTA_RATE_LIMIT_ENABLED` | `true` |
| `EMITTA_REDIS_TIMEOUT`, `EMITTA_REDIS_CONNECT_TIMEOUT` | `PT1S` |
| `EMITTA_PROCESSING_JOBS_ENABLED` | `false` |
| `EMITTA_PROCESSING_MAX_ATTEMPTS` | `5` |
| `EMITTA_PROCESSING_LEASE` | `PT2M` |
| `EMITTA_PROCESSING_INITIAL_BACKOFF`, `EMITTA_PROCESSING_MAXIMUM_BACKOFF` | `PT2S`, `PT2M` |
| `EMITTA_PROCESSING_BATCH_SIZE`, `EMITTA_PROCESSING_POLL_MS` | `5`, `2000` |
| `EMITTA_ADMISSION_RETRIES` | `2` (más intento inicial) |
| `EMITTA_ADMISSION_INITIAL_DELAY`, `EMITTA_ADMISSION_MAX_DELAY` | `PT1S`, `PT5S` |
| `EMITTA_OUTBOX_INITIAL_BACKOFF`, `EMITTA_OUTBOX_MAXIMUM_BACKOFF` | `PT1S`, `PT1M` |
| `EMITTA_SRI_RECEPTION_CONCURRENCY`, `EMITTA_SRI_AUTHORIZATION_CONCURRENCY` | `8`, `8` |
| `EMITTA_SRI_RECEPTION_OPEN_WAIT`, `EMITTA_SRI_AUTHORIZATION_OPEN_WAIT` | `PT15S`, `PT15S` |
| `EMITTA_SRI_AUTHORIZATION_ATTEMPTS`, `EMITTA_SRI_AUTHORIZATION_RETRY_DELAY` | `2`, `PT0.2S` |

Se conservan timeouts SRI existentes `EMITTA_SRI_{RECEPTION,AUTHORIZATION}_{CONNECT,REQUEST}_TIMEOUT` (`PT3S`/`PT10S`) y flags SRI apagados. Las políticas por endpoint, ventana, mínimo de llamadas y umbral son configurables mediante propiedades Spring. Recepción no acepta más de un intento.

## Métricas

Actuator conserva `/actuator/health` público sin detalles; `/actuator/metrics/**` exige `operations:read`. Métricas HTTP de Spring: `http.server.requests`, incluidas tasas de error por status. `emitta.http.traffic` cuenta decisiones de buckets/fases, no solicitudes únicas.

- `emitta.redis.available`: último resultado (-1 sin comprobación, 0 fallo, 1 disponible).
- `emitta.rabbit.ready`: mensajes listos fiscal/DLQ; `emitta.rabbit.consumers`; -1 si no se puede medir. AMQP no informa unacked/total: para eso usar métricas oficiales del broker, sin exponer el panel públicamente.
- `emitta.outbox.pending`, `emitta.processing.jobs` por estado, `emitta.ride.pending`.
- `emitta.processing.queue.wait`, `emitta.processing.duration`, `emitta.processing.attempts`, `emitta.processing.retries`.
- `emitta.sri.circuit.state`, `emitta.sri.failures`, `emitta.sri.retries`, `emitta.sri.duration` por operación; estados corresponden al enum de Resilience4j.
- `emitta.ride.enqueue`, `emitta.ride.generation`.

Tags normalizados y limitados; no RUC, UUID, IP, tokens, XML, certificados ni contraseñas como etiquetas. Los fallos Outbox persisten clase de excepción, no mensajes arbitrarios del transporte.

## Archivos de esta etapa

Nuevos paquetes `ratelimit`, `recovery`, `shared/resilience`; Lua `redis/token-bucket.lua`; migración V12; scripts genéricos de validación/política/recuperación; pruebas Redis, HTTP, resiliencia, admisión y recuperación durable. Cambios acotados en SecurityConfiguration, AuthController, DocumentQueryController, FiscalDocumentListener, configuración/publicador Outbox, configuración y adaptadores SOAP, hook de autorización, renderer PDF y tests E2E existentes. `application.yaml` y OpenAPI actualizados. No se reconstruyeron servicios fiscales ni se creó otro cliente SOAP/broker.

## Validación y pendientes

- `gradlew build --console=plain`: **BUILD SUCCESSFUL**, 5m46s; 328 casos, **327 satisfactorios**, 0 fallos, 0 errores, **1 omitido** (`SriSoapAuthorizationLiveTest`, `RUN_SRI_LIVE_TEST=false`). Incluye compilación, JAR/bootJar, Flyway y regresiones existentes.
- `objectStorageIntegrationTest` con Doppler y bucket sintético nuevo en MinIO local: **6 satisfactorios**, 0 fallos/errores/omitidos, cuatro clases. Incluye XML/XSD/DSS, firma criptográfica independiente, autorización sintética persistida, descubrimiento durable RIDE, PDF y SHA-256. No hubo llamadas SRI.
- **Total sin duplicar ejecuciones focalizadas: 333 pruebas satisfactorias y 1 omitida.**
- 20 pruebas focalizadas SOAP/resiliencia: satisfactorias. Cabeceras bloqueadas, cuerpo bloqueado después de cabeceras, cancelación efectiva de suscripción, Circuit Breaker abierto/half-open/cerrado, Bulkhead, recepción sin retries y consultas limitadas. SOAP Client Fault no se considera retry de comunicación; SOAP Server Fault conserva diagnóstico.
- Redis: dos instancias, 100 llamadas concurrentes/20 threads con exactamente 10 aceptadas; consumo multibucket atómico; cache Lua; IP spoofing; cliente/tenant; indisponibilidad por pausa del contenedor y recuperación; 429/503/Retry-After con JWT real y DB aislada. La pausa simula pérdida de respuesta del servicio; no es una prueba de failover Redis HA.
- PostgreSQL/RabbitMQ: journal y límite de intentos, dos workers concurrentes, lease vencido mientras existe lock activo, cierre/reinicio completo del contexto Spring, mensajes duplicados/inválidos, confirm sin ruta, crash entre confirm y marca publicada, cierre del consumidor antes de ACK y reinicio real del contenedor RabbitMQ. Script PowerShell DLX ejecutado contra broker Testcontainers y mensaje rechazado comprobado en DLQ. Recuperación y consulta REST rechazan tenant ajeno y scopes insuficientes; resultados externos inciertos permanecen bloqueados.
- Flyway V1–V12 migró correctamente en PostgreSQL 18 de Testcontainers. No se inició el backend contra la base privada ni se aplicó allí V12.
- OpenAPI: nueve operaciones, 72 referencias internas resueltas y copia web idéntica. Las dos operaciones de documentos anteriores y las seis POST se conservan; la operación nueva es consulta de trabajo RIDE.
- Documentación web: `npm run check` (0 errores/advertencias/hints), `npm run build` (10 páginas) y `npm run check:links` (152 enlaces/anchors) satisfactorios. Sigue la advertencia preexistente de sitemap por dominio oficial sin configurar; no se modificó la landing.
- Scripts PowerShell: parsing satisfactorio; validación local ejecutada con Doppler; política DLX probada en broker aislado; recuperación de trabajos validada mediante puerto/DB y guardas del comando, sin ejecutar la CLI sobre datos privados.
- `git diff --check`: satisfactorio. Escaneo por patrones de 75 archivos modificados/no seguidos sin coincidencias de datos del piloto, bloques de clave privada ni tokens evidentes. Los únicos binarios son las dos fuentes NotoSans de la etapa anterior, con su licencia. Esto no sustituye un scanner especializado de secretos antes de publicar.
- Rama conservada `codex/ride-consolidation`; staging vacío. Sin commit, push, merge, despliegue ni transmisión real. Los cambios de la etapa anterior permanecen.

Las advertencias Hikari sobre conexiones cerradas aparecen durante desmontaje de contextos/contenedores de prueba; no produjeron fallos. Los informes exactos quedan en `build/reports/tests/test/index.html` y `build/reports/tests/objectStorageIntegrationTest/index.html`.


La prueba `SriSoapAuthorizationLiveTest` sigue identificada y omitida; esta etapa no demuestra integración fiscal completa ni cumplimiento normativo del proveedor. Pendientes operativos: respaldo/aplicación privada de V12, política DLX privada, revisión del pool y capacidad bajo carga, alertas/dashboard de operaciones, ensayo de failover HA y respaldo/restauración. Las pruebas de caída son contra instancias aisladas, no una certificación de alta disponibilidad en producción.

Próxima etapa: revisión de cambios y ensayo de recuperación en un entorno TEST aislado, luego observabilidad/alertas y carga controlada contra simuladores. Mantener la prueba SRI real separada y explícitamente autorizada.

Fuentes técnicas: [atomicidad Lua Redis](https://redis.io/docs/latest/develop/programmability/eval-intro/), [publisher confirms RabbitMQ](https://www.rabbitmq.com/docs/confirms), [reliability RabbitMQ](https://www.rabbitmq.com/docs/reliability), [Resilience4j](https://resilience4j.readme.io/docs/getting-started).

## Inventario de implementación nuevo

- `src/main/java/io/github/imecuadorian/emitta/ratelimit/RateLimitConfiguration.java`
- `src/main/java/io/github/imecuadorian/emitta/ratelimit/RateLimitProperties.java`
- `src/main/java/io/github/imecuadorian/emitta/ratelimit/RedisTokenBucket.java`
- `src/main/java/io/github/imecuadorian/emitta/ratelimit/TrafficFilter.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/DurableWorkConfiguration.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/DurableWorkPort.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/DurableWorkRunner.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/PostgreSqlDurableWorkAdapter.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/ProcessingPolicy.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/RabbitQueueMetrics.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/RecoveryCommandConfiguration.java`
- `src/main/java/io/github/imecuadorian/emitta/recovery/WorkFailureClassifier.java`
- `src/main/java/io/github/imecuadorian/emitta/shared/resilience/SriCallGuard.java`
- `src/main/java/io/github/imecuadorian/emitta/shared/resilience/SriResilienceConfiguration.java`
- `src/main/java/io/github/imecuadorian/emitta/shared/resilience/SriResilienceProperties.java`
- `src/main/java/io/github/imecuadorian/emitta/shared/resilience/SriSoapBodyHandlers.java`

Recursos y herramientas: `src/main/resources/redis/token-bucket.lua`, `src/main/resources/db/migration/V12__durable_processing_jobs.sql`, `scripts/Configure-RabbitDlqPolicy.ps1`, `scripts/Recover-ProcessingJob.ps1`, `scripts/Test-Resilience.ps1`.

Adaptaciones existentes: `auth/adapter/config/SecurityConfiguration.java`, `auth/adapter/in/web/AuthController.java`, `document/adapter/in/web/DocumentQueryController.java`, `fiscalprocessing/adapter/in/messaging/FiscalDocumentListener.java`, `outbox/adapter/config/{OutboxPublisherConfiguration,RabbitOutboxConfiguration}.java`, `outbox/adapter/out/messaging/RabbitOutboxMessagePublisher.java`, `outbox/application/service/PublishPendingOutboxService.java`, `sriauthorization/adapter/config/{SriAuthorizationConfiguration,SriAuthorizationWorkflowConfiguration}.java`, `sriauthorization/adapter/out/soap/SriSoapAuthorizationAdapter.java`, `srireception/adapter/config/SriReceptionConfiguration.java`, `srireception/adapter/out/soap/SriSoapReceptionAdapter.java`, `ride/adapter/out/pdf/PdfBoxInvoiceRideGenerator.java`, `application.yaml`, OpenAPI y pruebas relacionadas. Las rutas Java abreviadas parten de `src/main/java/io/github/imecuadorian/emitta/`.

La web solo cambió su copia pública OpenAPI y las guías técnicas `authentication.md`/`documents.md` para explicar los nuevos errores y estados.
