# Emitta — etapa 5: admisión y preparación fiscal

Fecha de informe: 2026-10-11. Rama `codex/ride-consolidation`. Ensayos sintéticos aislados; SRI, firma, RIDE y ejecución fiscal desactivados. Sin commit, push, despliegue, cambios del piloto ni V12 privado.

## Diseño del experimento

A: código de etapa 4, sin optimizar. La primera repetición `sustained-100-a7062547-5e02-4e07-bd05-ae50ba58a1b4` pasó: 57.483 aceptados, 95,46/s, HTTP p95 501,79 ms y p99 780,06 ms, cero rechazos, drenaje 17,52 s. Es significativamente distinto del ensayo anterior de etapa 4: la variabilidad impide atribuir mejoras sólo por comparación con su cifra de 55,42/s.

Se añade profiling uniforme al fixture, no al backend privado: PostgreSQL con pg_stat_statements y track_io_timing, SQL normalizado y planes EXPLAIN ANALYZE/BUFFERS, muestras de estados y esperas de conexiones. Hikari mide adquisición/uso; pg_stat_statements mide ejecución en servidor sin checkout ni viaje JDBC; los timers de transacción incluyen checkout, aplicación y commit. Los planes se toman sobre datos reales del ensayo sintético. El profiling tiene coste y su serie se compara separadamente de la primera repetición sin profiling.

B: eliminación del merge/read/update inicial redundante; lectura de metadatos de admisión por lote, inserción de jobs por conjunto, parsing previo a checkout y motivos de rechazo observables. Concurrencia de admisión uno, pool diez, backlog máximo 5.000, edad 60 s y umbrales HTTP originales.

C: misma variante B, con dos consumidores de admisión en infraestructura aislada; máximo/prefetch acotados, sin aumentar Hikari. Es una comparación, no un nuevo valor definitivo acreditado para el motor fiscal completo.

## Evidencia inicial y cambios

- En snapshots reales del ensayo A hubo seis y después nueve conexiones idle in transaction, frente a una o dos ejecutando SQL. El trabajo entre viajes JDBC mantiene conexiones ocupadas. También se observaron WalSync/WALWrite: no atribuir toda la latencia a CPU o consultas.
- Con 8.800 documentos, el UPDATE inicial de documents y las lecturas ORM fueron las operaciones de mayor tiempo SQL acumulado; el consumidor seguía realizando lectura completa e INSERT por cada mensaje. Un batch de mensajes no era un batch de SQL.
- La consulta del guard escanea históricos. Se comparó una candidata UNION ALL/anti-join: 46,67 frente a 37,39 ms en una muestra, con equivalencia de IDs en la misma sentencia. Posteriormente, tres mediciones alternadas con mayor histórico dieron 524,16/339,48/353,91 ms para la original y 182,95/172,92/124,12 ms para la candidata. Se incorporó la candidata a B: ramas disjuntas para no duplicar eventos, índice existente para pendientes y anti-join antes de consultar metadatos. No se añadió ningún índice ni migración.
- El estado QUEUED se construye antes del INSERT inicial, usando la transición del dominio existente. Factura, documento y evento siguen bajo un commit único; la carrera de idempotencia mantiene su consulta y validación del contenido. No se modifican documentos existentes.
- La admisión obtiene sólo id/tenant/status para hasta 50 documentos en una consulta y crea jobs FISCAL con un INSERT SELECT por conjunto. El SQL vuelve a comprobar tenant y estado, usa ON CONFLICT y verifica existentes ante conflictos; entradas inválidas revierten todo el batch. Orden estable de UUID para reducir riesgos de deadlock entre consumidores.
- Parsing de mensajes acotados antes de la transacción; DLQ fuera del commit, con confirms conservados. Resultados externos desconocidos siguen sin reenvío.
- Counter agregado de rechazo conservado y serie por motivo de cardinalidad acotada: backlog, age, sample_unavailable, infrastructure. Muestra atómica e inmutable evita leer motivo y timestamp de snapshots diferentes. Límites y caducidad no se relajan.
- Timers adicionales de statement de admisión (sin checkout, incluye transferencia/mapeo JDBC), frontera beforeCommit/afterCommit (incluye callbacks/commit, no es un fsync puro) y muestreo del guard. SQL puro se obtiene del servidor.

## Resultados y validación

Los tres ensayos A/B/C instrumentados usaron 100 VUs durante diez minutos, Hikari máximo diez, los mismos umbrales HTTP y cinco minutos máximos de recuperación. A falló; B y C pasaron. Ningún umbral se relajó.

| Medida | A original instrumentado | B SQL/lotes, un consumidor | C SQL/lotes, dos consumidores |
| --- | ---: | ---: | ---: |
| Documentos/eventos/jobs originales | 24.060 | 54.535 | 37.012 |
| HTTP 202 de creación | 23.986 | 54.535 | 37.012 |
| Documentos persistidos/s | 39,77 | 90,47 | 61,40 |
| HTTP p95 / p99, ms | 1.508,81 / 3.397,34 | 651,48 / 998,28 | 1.062,59 / 1.600,71 |
| Rechazos iniciales precommit | 545 | 0 | 0 |
| Resultados iniciales desconocidos | 230 | 0 | 0 |
| Persistidos sin acuse HTTP | 74 | 0 | 0 |
| Errores HTTP | 1,211 % | 0 % | 0 % |
| Checks | 99,577 % | 100 % | 100 % |
| Hikari active / pending máximos | 10 / 167 | 10 / 87 | 10 / 91 |
| Adquisición Hikari media, ms | 304,38 | 112,56 | 187,24 |
| Timeouts Hikari | 6 | 0 | 0 |
| Outbox máximo / edad máxima, s | 84 / 18,28 | 81 / 2,01 | 100 / 3,36 |
| RabbitMQ ready / unacked máximos | 348 / 50 | 20 / 50 | 0 / 67 |
| Claim Outbox medio, ms | 195,70 | 81,49 | 139,29 |
| Marcado durable medio, ms | 373,92 | 125,19 | 216,61 |
| Publisher confirm medio, ms | 19,96 | 14,02 | 19,75 |
| Drenaje, s | 3,56 | 1,14 | 1,13 |
| Outbox/ready/unacked al finalizar | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |

Los máximos se muestrean y pueden omitir picos entre muestras. La latencia HTTP abarca POST nuevo, replay y GET; no es exclusivamente la de creación. La tasa se calcula sobre documentos persistidos y el tiempo real del escenario, no sobre facturas autorizadas. A conserva los 74 documentos cuyo acuse se perdió; su fallo HTTP no acredita pérdida en PostgreSQL. El fixture no reemplaza un SDK de conciliación de respuestas desconocidas.

B eliminó el UPDATE inicial y redujo la admisión a 1.910 lecturas de metadatos y 1.910 INSERT por lote para 54.535 documentos. A efectuó 24.093 INSERT individuales de jobs y 24.060 UPDATE iniciales. Las sentencias de aplicación normalizadas por documento pasaron de 23,22 a 19,21; incluyen replay, GET y muestreo, por lo que no equivalen a consultas exclusivas de un POST. Las transacciones de admisión medias pasaron de 1.211,54 ms a 160,29 ms. Hikari confirma que la espera por conexiones, junto con los viajes JDBC dentro de transacciones, era una contribución importante; no se atribuye todo el coste a una sola consulta.

En B el statement de admisión promedió 10,93 ms y la frontera de commit de admisión 10,77 ms; el commit de factura, incluyendo replays, 7,21 ms. Los confirms promediaron 14,02 ms. En C el commit de admisión promedió 18,42 ms y la adquisición Hikari 187,24 ms. Dos consumidores no acreditaron mejora sostenida: se conserva **uno** por defecto. La comparación no está contrabalanceada; el estado del host y el coste del profiling siguen siendo limitaciones causales.

La consulta del guard en carga promedió 161,30 ms en A y 161,02 ms en B, pero B acumuló 2,27 veces más documentos. La reducción de planes alternados corresponde a la misma base/snapshot sintética; no se presenta el promedio de carga como una reducción del 60 %. El guard continúa recorriendo histórico publicado para detectar eventos sin jobs: a mayor volumen puede requerir otra estrategia demostrada por planes, fuera de esta optimización mínima.

Se inspeccionó la serialización del publisher: el evento ya contiene JSON y se convierte a UTF-8 antes de iniciar la medida de confirms. No se obtuvo un timer aislado de CPU de serialización; no se inventa su duración ni se cambia el formato sin evidencia. El scheduler y su frecuencia de dos segundos se conservaron.

### 200 VUs, variante B con un consumidor

Run `sustained-200-ba6664d7-5f2f-4cb1-9fcc-5abd190644fa`: **falló**, sin modificar umbrales. 22.066 documentos/eventos/jobs persistidos; 22.003 HTTP 202; 36,43 documentos persistidos/s (36,33 acuses/s). HTTP p95 3.787,68 ms y p99 6.487,63 ms incumplen 2.000/5.000 ms. Checks 99,776 % e idempotency_intact 99,823 % también fallan; el replay tuvo 39 comprobaciones HTTP fallidas, por lo que no se declara su aprobación integral. El conteo durable y las restricciones de unicidad no detectaron duplicados funcionales.

Hubo 72 resultados iniciales desconocidos y 63 documentos persistidos sin acuse. Se registraron nueve HTTP 503 genéricos, **cero rechazos ADMISSION_SATURATED verificables**: no presentar esos nueve como rechazos precommit garantizados. Errores HTTP totales 0,238 % (157/66.082), por debajo del 1 %, pero no compensan los otros umbrales incumplidos.

Hikari active máximo diez, pending 190; adquisición media 773,63 ms, sin timeout del pool. Outbox máximo 2.186 y edad 53,11 s; RabbitMQ ready 150/unacked 50. Los jobs y eventos alcanzaron los 22.066 originales y el drenaje completo tardó 4,21 s, con Outbox/ready/unacked cero. No hubo llamadas al SRI ni autorizaciones. Heap máximo muestreado 243.722.168 bytes.

Admisión: 515 transacciones, media completa 935,70 ms, statement 20,72 ms y frontera commit 30,66 ms. Claim Outbox medio 426,07 ms y marcado durable 785,43 ms. Confirms medios 26,44 ms; commit de factura medio 21,31 ms. SQL normalizado de aplicación: 422.085 llamadas (19,13/documento), 81,46 s de ejecución servidor acumulada. Los contadores FISCAL admitidos, Outbox generated/confirmed/published y facturas committed coinciden en 22.066; esto permite distinguir persistencia, publicación confirmada y admisión durable del mero volumen HTTP. La media de guard SQL fue 47,56 ms, frente a 1.004,30 ms del timer completo de muestra, que incluye espera por conexión. El contraste con Hikari y la transacción completa apunta a contención fuera del SQL puro; no justifica ampliar el pool indiscriminadamente.

Se conserva B/default uno, por su resultado de 100 VUs y menor número de viajes JDBC. **200 VUs no queda validado**, y no se repite con límites relajados ni se añade otra optimización sin evidencia. La variación entre ensayos limita extrapolaciones de capacidad; antes de publicar un SLA se necesitan repeticiones controladas y presupuesto del flujo fiscal completo.

## Próxima etapa

Prioridad al cierre fiscal verificado: XAdES y validación criptográfica independiente; recepción y consulta de autorización; conservación íntegra del XML autorizado; RIDE; entrega/descarga tenant segura desde Emitta Web; y prueba integral de certificación sólo con autorización explícita. La prueba SRI live omitida permanece pendiente. La carga REST no acredita emisión fiscal.


A con profiling (sustained-100-48735c5b-aaba-40e8-97ed-0cb541769308) falló los umbrales originales: 24.060 documentos/jobs/eventos originales, 23.986 HTTP 202, 74 documentos persistidos sin acuse HTTP, 545 rechazos precommit y 230 resultados de creación desconocidos. HTTP failed 1,211 %, checks 99,577 %, p95 1.508,81 ms y p99 3.397,34 ms; drenaje 3,56 s. No declarar cero resultados desconocidos ni idempotencia integral aprobada. El resultado refuerza la regla de recuperar con la misma clave y contenido ante timeout; nunca crear un sustituto con otra clave. El contrato OpenAPI lo explicita. Las observaciones de esperas incluyen WalSync/WALWrite y no aíslan causalmente el coste del profiling del estado del host.


Profiling A: 151.031 adquisiciones Hikari, 45.971,33 s acumulados de espera (media 304,38 ms), seis timeouts; 5.861,86 s acumulados de uso. 558.739 sentencias normalizadas que mencionan emitta (incluyen POST, replay, GET y muestreo), 23,22 por documento persistido, 150,01 s de ejecución SQL acumulada en servidor. Guard: 225 llamadas, 36,29 s, media 161,30 ms. Los acumulados concurrentes no son tiempo de pared ni porcentajes sumables. pg_stat_statements no captura aquí todos los commits del protocolo, y no se habilitó track_wal_io_timing: no inferir latencia de fsync cero a partir de esos contadores. Los nuevos timers beforeCommit/afterCommit miden la frontera de flush/commit y callbacks en JVM, no el disco aislado.

## Reproducción y configuración

```powershell
# Siempre servicios efímeros, sin Doppler, URL externa ni credenciales privadas.
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 100 -Duration 10m -AdmissionConcurrency 1
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 100 -Duration 10m -AdmissionConcurrency 2
# 200 únicamente tras validar 100 con la configuración elegida.
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 200 -Duration 10m -AdmissionConcurrency 1
.\gradlew.bat build --console=plain
.\scripts\Test-IsolatedStorage.ps1
```

Nueva variable configurable: `EMITTA_ADMISSION_CONCURRENCY`, default **1**, rango 1–2. La bandera de publicación/worker/firma/RIDE/SRI sigue false por defecto. En los experimentos se controla con `-AdmissionConcurrency`; `experiment.json` identifica el valor. Dos consumidores pueden ocupar dos conexiones simultáneas de admisión; no reservar diez para consumers ni extrapolar el ensayo con jobs apagados al motor fiscal, cuyos workers usan dos conexiones cada uno. Pool de referencia diez, batch/prefetch 50 por consumidor, espera de reunión 100 ms; sin cambios de límites del guard ni cuotas comerciales.

Nuevos artefactos de diagnóstico, ignorados por Git, bajo `build/operational/load/<run>/`: `postgres-statements.json`, `postgres-waits.jsonl`, `guard-plan.json` y `experiment.json`. `pipeline-metrics.json` incluye causas del brake, duración de su muestra, statement/commit/transacción de admisión y commit/transacción de factura. Las consultas normalizadas provienen únicamente de la base sintética efímera; no publicar logs completos.

En A hubo 557 muestras de estados, sin espera de tipo Lock observada y una transacción máxima muestreada de 4,89 s. Un muestreo de aproximadamente un segundo puede omitir bloqueos breves y no determina su duración exacta. La prueba de bloqueo forzado verifica un timeout de statement de cinco segundos, rollback y recuperación; no representa la latencia de carga normal.


## Archivos de esta etapa

Se conservan todos los cambios de etapas anteriores. Esta etapa modifica únicamente las siguientes responsabilidades:

- Dominio/admisión: `CreateDocumentService`, `DocumentRepository` y `DocumentPersistenceAdapter`: INSERT inicial QUEUED y proyección JDBC por conjunto.
- Recuperación/consumo: `DurableWorkPort`, `PostgreSqlDurableWorkAdapter` y `FiscalDocumentListener`: admisión durable por conjunto, tenant/estado/idempotencia, rollback, timeout y métricas.
- Configuración/observabilidad: `OutboxAdmissionGuard`, `RabbitOutboxConfiguration`, `TransactionalCreateInvoiceUseCase`, `application.yaml` y `ops/observability/emitta-dashboard.json`: consulta, causas, timers y concurrencia default uno.
- Contrato: `src/main/resources/static/openapi/emitta-openapi.yaml`: conciliación con la misma clave ante respuesta desconocida, sin ampliar endpoints.
- Validación: `CreateDocumentServiceTest`, `OutboxAdmissionGuardTest`, `DurableRecoveryIntegrationTest`, `InvoiceBackpressureResponseTest` y `OperationalLoadTest`; `scripts/Test-OperationalLoad.ps1` permite comparación aislada y profiling.
- Documentación: este informe, `load-testing.md`, `runbook.md` y `local-consolidation-roadmap.md`.

No hay dependencias Java ni migraciones Flyway nuevas en esta etapa. V12 pertenece al trabajo previo y se utiliza únicamente en bases efímeras de prueba; no se aplicó a la base privada. No hay cambios de la web comercial ni del perfil fiscal del piloto.

## Riesgos y alcance

El guard muestreado es un freno de seguridad, no una cuota distribuida exacta; puede admitir entre muestras y rechaza si la muestra caduca. La reconciliación de un HTTP desconocido debe conservar clave y contenido aunque el documento ya exista. La entrega al menos una vez requiere tolerar eventos duplicados; no se convierte en exactamente una vez. La concurrencia de workers fiscales, firma, SOAP y PDF no fue ejercitada por k6 y necesita su propio presupuesto antes de activarse. No se acreditó un límite comercial de capacidad.

Las métricas y consultas se obtuvieron de Testcontainers con datos ficticios. La revisión de archivos de esta etapa no detectó claves privadas, RUC ni certificado del piloto. No se imprimieron tokens, contraseñas ni XML reales. OpenAPI: nueve operaciones y 72 referencias locales válidas; dashboard: 25 paneles, JSON válido; script de carga: cero errores de sintaxis PowerShell. El staging permanece vacío.




## Regresión final

`gradlew.bat build --console=plain`: **BUILD SUCCESSFUL**, 9 min 30 s, 353 casos JUnit: **352 aprobados, cero fallos/errores y uno omitido**. La omisión es `SriSoapAuthorizationLiveTest.shouldParseRealSriInvalidAccessKeyResponse`; no se ejecutó contra el SRI. CompileJava y empaquetado incluidos en build.

La suite incluye PostgreSQL/Redis/RabbitMQ, desconexión y recuperación, confirms/mandatory returns, DLQ, reinicios con procesos terminados abruptamente, idempotencia, backpressure, aislamiento multi-tenant y rollback/recuperación tras timeout real de bloqueo SQL. El test nuevo de respuesta verifica 503/ADMISSION_SATURATED, Retry-After 2 y no-store. La admisión por conjunto verifica conflictos de tenant/estado y contador sólo tras commit; la consulta del guard se compara contra la anterior en datos mixtos.

MinIO aislado (`Test-IsolatedStorage.ps1`): BUILD SUCCESSFUL, 2 min 9 s, seis casos aprobados, cero fallos/errores/omisiones. Total de regresión general y almacenamiento: **358 aprobados y una prueba SRI live omitida**. La carga operacional se contabiliza por separado: A instrumentado y 200 VUs fallaron; B/C de 100 VUs pasaron. No se suman ensayos fallidos ni repeticiones focalizadas a las pruebas satisfactorias de la suite general.

`git diff --check`: exit 0. JAR inspeccionado: 1.052 entradas, cero rutas de certificados privados, respaldos o evidencias del piloto. Rama comprobada y staging vacío; sin commit/push/merge/despliegue.


## Identificación de evidencias locales

| Variante | Directorio bajo build/operational/load |
| --- | --- |
| A sin profiling | sustained-100-a7062547-5e02-4e07-bd05-ae50ba58a1b4 |
| A instrumentado | sustained-100-48735c5b-aaba-40e8-97ed-0cb541769308 |
| B, un consumidor | sustained-100-366edecb-124f-4113-9202-9486517d8847 |
| C, dos consumidores | sustained-100-b32de6ba-8111-45ed-969e-be483bc327b8 |
| B, 200 VUs | sustained-200-ba6664d7-5f2f-4cb1-9fcc-5abd190644fa |

Logs ignorados: `build/operational/stage5-final-build.log` y `stage5-final-storage.log`. El informe resume datos sintéticos; los artefactos completos permanecen locales y no se prepararon para publicación.
