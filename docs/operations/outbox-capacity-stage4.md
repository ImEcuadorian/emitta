# Emitta — etapa 4: Outbox y capacidad sostenida

Fecha: 2026-10-10. Rama `codex/ride-consolidation`. Cambios locales, sin commit/push/merge/despliegue. No se aplicó V12 a la base privada, no se modificó el piloto y no se llamó al SRI. Todos los documentos de carga son sintéticos, TEST/QUEUED; firma, RIDE y SRI están apagados. Aceptación HTTP no significa autorización fiscal.

## Diagnóstico y medición

Referencia de etapa 3: 38.676 documentos originales durante diez minutos con 100 VUs, 64,12 aceptaciones/s; p95 1.035,47 ms y p99 1.785,89 ms; 22 errores HTTP, fallo de checks de replay y 7.024 eventos Outbox pendientes después de cinco minutos de drenaje. Hikari máximo diez. No había captura separada de latencias de confirms/claims ni de unacked: no inventar retrospectivamente esas cifras.

Se añadió una referencia corta instrumentada sin optimización: `normal-100-23ae77f6-221a-432a-8620-b947b5f48372`, 1.878 documentos, drenaje 15,59 s. Se observaron 1.878 escrituras individuales de publicado (24,746 s acumulados), 1.878 confirms (6,648 s acumulados), 396 claims (5,388 s acumulados) y 17.621 adquisiciones de conexiones (1.977,888 s acumulados; media 112,25 ms). Los tiempos acumulados de operaciones concurrentes **no son tiempo de pared**. La evidencia apunta a escrituras/adquisiciones seriales por evento y competencia por el pool, no a un broker incapaz de confirmar.

La primera optimización corta (`normal-100-1df4c433-ff98-4df4-8426-888bab181339`) persistió 1.906 documentos y drenó en 6,95 s. Las escrituras de publicado bajaron a 68 por lote (14,810 s acumulados). No se aumentó el pool. Los confirms se solapan, por lo que sumar sus latencias tampoco mide el tiempo de publicación del lote.

Un ensayo intermedio de diez minutos con publicación optimizada y consumidor individual reveló el segundo cuello: Outbox bajo pero RabbitMQ ready creciente y sólo un consumidor con prefetch cinco. No interpretar un Outbox vacío como recuperación completa. Esto motivó admisión por lote y backpressure sobre eventos todavía sin job, incluidos los ya publicados.

Ese ensayo intermedio (`sustained-100-8da9c1e8-acd3-487b-9623-f7dc80f13ca8`) terminó con 59.755 documentos=eventos=jobs, 99,15 aceptaciones/s, HTTP p95 492,39 ms / p99 753,48 ms, cero errores y replays correctos al 100 %. Drenó en 256,98 s, dentro de cinco minutos pero con poco margen, después de acumular la cola del broker. Hikari max diez, pico 93 esperas y heap 197 MiB. Es evidencia de publicación mejorada, **no** la validación del código final con admisión por lote y brake completo.

En esa ejecución el Outbox alcanzó 86 pendientes y 1,55 s de edad, mientras RabbitMQ alcanzó 54.795 ready y cinco unacked observados. Esta diferencia localiza el cuello desplazado a admisión. En la referencia corta anterior el Outbox había alcanzado 1.717/26,58 s; su broker no fue muestreado, por lo que ready/unacked de esa referencia son desconocidos.

Los CSV contienen documentos/eventos/publicados acumulados, jobs admitidos, backlog y edad; calcular tasas con diferencias entre muestras y duración real. Las consultas separadas pueden mostrar diferencias momentáneas por transacciones concurrentes; la verificación final exige igualdad exacta. `pipeline-metrics.json` conserva count/total/max de acquisition Hikari, claims, marcas, confirms y transacciones. `rabbit-queues.jsonl` muestrea ready/unacked desde el management API del broker **efímero**, con credenciales únicamente en memoria. El management plugin actualiza sus estadísticas con retardo: los máximos son máximos observados, no instantáneos garantizados. `drain.csv` conserva recuperación después de k6.

## Cambio y garantías

1. `RabbitOutboxMessagePublisher` envía hasta 50 mensajes con correlación independiente, entrega persistente y mandatory. Sólo ACK sin returned message es éxito. Cada future tiene timeout de cinco segundos; timeout/NAK/return dejan el evento recuperable. Se detiene el envío del resto del lote tras error de transporte síncrono para evitar 50 reconexiones consecutivas. No se creó otro cliente.
2. `PublishPendingOutboxService` reúne confirmados y `PostgreSqlOutboxClaimAdapter` los marca en un único UPDATE con RETURNING y predicado de propietario. Nunca se marca antes del confirm/routing. Fallo DB o commit desconocido permite replay al menos una vez; la idempotencia del consumidor absorbe duplicados. No se usan transacciones distribuidas.
3. Claim y marca son operaciones PostgreSQL cortas; ninguna conexión DB permanece ocupada esperando confirms. Query timeout de cinco segundos en el adapter; lease default dos minutos, validado respecto al presupuesto de dos esperas Hikari y margen. Un bloqueo de transporte o DB excepcional puede exceder el presupuesto: ownership y deduplicación siguen siendo necesarios. No prometer exactly once.
4. Admisión RabbitMQ reúne hasta 50 mensajes, máximo 100 ms de reunión, un consumidor. La transacción DB comparte conexión y termina antes de publicar inválidos a DLQ. ACK del batch ocurre sólo después del commit y confirms DLQ. Fallo de infraestructura revierte el batch o provoca redelivery idempotente si falló después del commit. El recoverer de batch nunca descarta registros: retries locales limitados, cooldown y requeue. Los inválidos se clasifican individualmente; resultados fiscales inciertos siguen sin procesamiento/reenvío automático.
5. `OutboxAdmissionGuard` usa backlog PostgreSQL compartido: pendientes de publicación **o** eventos de documentos elegibles todavía sin job FISCAL. Default 5.000/60 s, muestra cada dos segundos, caducidad diez segundos y fail-closed si no hay muestra sana. Es un freno aproximado entre instancias, no un límite exacto ni cuota comercial. Ya aceptados y replays conservan la semántica habitual. Nuevos reciben 503 `ADMISSION_SATURATED`, Retry-After 2 y no-store **antes de inserción**, con rollback de su transacción. Un rechazo no elimina ni modifica documentos aceptados.

## Configuración y operación

Se conservan Hikari max=10/min=2 y flags de publicación/worker/firma/RIDE/SRI apagados por defecto. La admisión no activa workers. Variables nuevas/ajustadas:

- `EMITTA_OUTBOX_BATCH_SIZE=50`, `EMITTA_OUTBOX_CLAIM_LEASE=PT2M`.
- `EMITTA_RABBIT_CONNECTION_TIMEOUT=3s`.
- `EMITTA_ADMISSION_BATCH_SIZE=50`, `EMITTA_ADMISSION_RECEIVE_TIMEOUT_MS=100`.
- `EMITTA_OUTBOX_ADMISSION_ENABLED=true`, `EMITTA_OUTBOX_ADMISSION_MAX_PENDING=5000`, `EMITTA_OUTBOX_ADMISSION_MAX_AGE_SECONDS=60`, `EMITTA_OUTBOX_ADMISSION_SAMPLE_MS=2000`.

No hay migración nueva. V1–V12 se verifican sólo en PostgreSQL efímero. No aumentar límites de backpressure ni umbrales k6 para esconder un fallo. El backlog de jobs fiscales ya admitidos requiere dimensionamiento del motor fiscal aparte; esta prueba con jobs apagados no mide firma/SRI/RIDE.

Comandos locales, sin Doppler ni URL externa:

```powershell
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 100 -Duration 10m
# Ejecutar 200 sólo tras pasar el escenario anterior con el código final.
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 200 -Duration 10m
.\gradlew.bat build --console=plain
.\scripts\Test-IsolatedStorage.ps1
```

Artefactos: `build/operational/load/<run>/` ignorado por Git. El fixture con credenciales efímeras se borra al terminar; no publicar logs completos. Conservados todos los cambios locales anteriores.

Base técnica: [confirms y returns Spring AMQP](https://docs.spring.io/spring-amqp/reference/amqp/template.html), [batch listeners](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/batch.html), [recuperación de batches](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html). El retry de un batch se usa únicamente para efectos idempotentes y rollback/requeue completos; no para repetir emisiones fiscales.

## Validación final

El escenario final de **100 VUs / diez minutos pasó** (`sustained-100-921c0dc2-3362-4a0c-adc5-cf4f91dda570`), sin cambiar los umbrales: 33.440 documentos=facturas=eventos originales=jobs y 33.440 HTTP 202, cero resultados HTTP desconocidos/commits sin acuse, replays al 100 %, Outbox y RabbitMQ ready/unacked cero al finalizar. Drenaje **12,78 s**. Cinco minutos era el máximo, no una espera obligatoria tras haber drenado.

Hubo **572 rechazos 503 del guard antes de commit**: 0,567 % de todas las solicitudes HTTP, pero **1,68 % de los intentos iniciales de creación**. No ocultarlos detrás del porcentaje agregado de POST/replay/GET. Se registraron en `invoice_rejected_before_commit` y el contador del backend; no son documentos perdidos ni recepciones fiscales inciertas. El guard puede rechazar por backlog/edad o muestra no disponible/caducada; esta versión no etiqueta por separado esas causas, por lo que no atribuir retrospectivamente todos a superar 5.000 eventos.

| Indicador | Antes, etapa 3 | Publicación optimizada, admisión individual | Final 100, batch y guard |
|---|---:|---:|---:|
| Documentos aceptados | 38.676 | 59.755 | 33.440 |
| Aceptaciones/s | 64,12 | 99,15 | 55,42 |
| HTTP p95 / p99, ms | 1.035,47 / 1.785,89 | 492,39 / 753,48 | 1.309,77 / 2.515,53 |
| Errores/rechazos HTTP | 22 | 0 | 572, todos antes de commit |
| Replays verificados | Falló el check | 100 % | 100 % |
| Hikari max / pico esperas | 10 / 97 observado | 10 / 93 | 10 / 94 |
| Outbox pico / edad pico | Sin medición comparable consolidada | 86 / 1,55 s | 100 / 8,74 s |
| Broker pico ready / unacked | No medido | 54.795 / 5 | 2.220 / 50 |
| Eventos pendientes tras presupuesto | 7.024 | 0 | 0 |
| Drenaje | Falló cinco minutos | 256,98 s | 12,78 s |

El máximo observado de documentos todavía sin job en el ensayo final fue 2.323; los conteos separados pueden diferir momentáneamente. La recuperación mejoró, **pero el throughput aceptado bajó un 13,6 % respecto a etapa 3 y las latencias subieron**. No afirmar ausencia de regresión de rendimiento ni usar este resultado como capacidad fiscal autorizada. El brake y la infraestructura compartida forman parte de la medición; no se hizo un ensayo contrafactual para atribuir por separado cada diferencia.

Timers finales: 208.041 adquisiciones Hikari, media 211,78 ms y máximo de ventana 5,39 s, cero timeouts Hikari; 1.391 claims (media 139,91 ms); 1.290 marcas por lote (media 245,69 ms); 33.440 confirms (media 17,94 ms); 674 transacciones de admisión (media 883,25 ms). Timers incluyen sus esperas de adquisición; no equivalen a tiempo SQL puro. Los máximos Micrometer son de ventana, no necesariamente máximos de todo el ensayo. Adquisiciones por documento bajaron de 9,38 en la referencia corta a 6,22; la espera media creció. El pool continúa siendo un límite operativo.

El ensayo de **200 VUs / diez minutos falló el umbral HTTP**, con el mismo código, configuración y límites: `sustained-200-1398b861-1b7d-4318-9c5a-60ac96711676`, 40.240 documentos=eventos=jobs=HTTP 202, cero commits sin acuse/resultado HTTP desconocido, checks y replays al 100 %, drenaje 25,89 s y ready/unacked/Outbox cero al final. Throughput aceptado 66,54/s; HTTP p95 1.429,73 ms y p99 2.284,26 ms. Los **17.172 rechazos explícitos antes de commit** representan 12,45 % del HTTP total y **29,91 % de intentos iniciales de creación**. El task devolvió fallo (k6 exit 99); no cambiar sus umbrales ni desactivar el brake para declararlo satisfactorio.

En 200: Hikari max diez, pico 188 esperas, heap 355 MiB; Outbox pico 2.185/26,17 s de edad; broker pico 4.810 ready/50 unacked; máximo observado de documentos sin job 5.153. El límite por muestreo no es exacto: hubo exceso transitorio sobre 5.000, preservado y drenado. 281.774 adquisiciones, media 291,44 ms, cero timeouts Hikari; 1.298 claims, 827 marcas por lote y 40.240 confirms/marcas durables. Esa carga **no acredita 200 VUs con la política actual**, aunque todos los aceptados estén preservados y recuperados.

No se hizo commit ni se preparó staging. No se cambió la política fiscal/proveedor, certificados, claves de acceso o datos del piloto. La siguiente optimización debe reducir tiempo de conexión por operación y medir concurrencia de admisión con margen para la API; no incrementar indiscriminadamente el pool ni usar mayor backlog como sustituto de capacidad. La prueba no dimensiona autorización SRI, firma ni RIDE.

Gradle build final: SUCCESSFUL en 9 min 18 s. Suite predeterminada: 349 casos, 348 satisfactorios, cero fallos/errores y una prueba SRI live omitida que permanece pendiente. MinIO aislado: seis pruebas satisfactorias, cero fallos/errores/omisiones, BUILD SUCCESSFUL en 2 min 12 s. Total funcional y almacenamiento: 354 satisfactorias y una SRI live omitida. La recuperación incluye terminación real de la JVM publicadora después de confirms y antes de la marca durable, expiración del lease real de dos minutos, redelivery sin jobs duplicados, rollback del batch y confirm/return de rutas mixtas. No se simuló un reinicio completo del servidor HTTP durante el soak.

## Archivos intervenidos en etapa 4

Rutas Java bajo `src/main/java/io/github/imecuadorian/emitta/`:

- `outbox/`: `OutboxAdmissionGuard`, `OutboxPublisherConfiguration`, `RabbitOutboxConfiguration`, `RabbitOutboxMessagePublisher`, `PostgreSqlOutboxClaimAdapter`, `OutboxClaimPort`, `OutboxMessagePublisher` y `PublishPendingOutboxService`: brake, lotes, confirms correlacionados, ownership y mediciones.
- `fiscalprocessing/adapter/in/messaging/FiscalDocumentListener.java` y `recovery/PostgreSqlDurableWorkAdapter.java`: admisión transaccional por lote y contador de jobs tras commit.
- `document/adapter/config/DocumentConfiguration.java`, `document/application/service/CreateDocumentService.java`, `invoice/adapter/config/InvoiceConfiguration.java`, `invoice/adapter/in/web/InvoiceExceptionHandler.java` y `invoice/adapter/transaction/TransactionalCreateInvoiceUseCase.java`: integración del guard antes de persistir, replay, 503 explícito y métricas de commit.
- `src/main/resources/application.yaml` y `static/openapi/emitta-openapi.yaml`: configuración y contrato de rechazo temporal.
- Tests: `CorrelatedOutboxBatchTest`, `OutboxAdmissionGuardTest`, `CreateDocumentServiceTest`, `PublishPendingOutboxServiceTest`, `RabbitAdmissionRetryTest`, `DurableRecoveryIntegrationTest`, `InterruptedOutboxProcess`, `OperationalLoadTest` y `JwtTestProperties`; fixture de carga `tests/load/traffic.ts`.
- Observabilidad: `ops/observability/emitta-dashboard.json`, `emitta-alerts.yaml`, `emitta-alerts.test.yaml`; documentación: este informe, `runbook.md`, `load-testing.md` y `docs/architecture/local-consolidation-roadmap.md`.

Los demás archivos locales de etapas anteriores se conservan; no atribuir todos los cambios del working tree a etapa 4. Sin migraciones nuevas. V12 sólo se ejercitó en infraestructura efímera, nunca sobre la base privada.

Tasas separadas calculadas con diferencias del CSV final (ventana de muestreo, distinta de la duración k6): en 100, 604,341 s y 55,33/s para documentos persistidos, eventos generados, confirms y eventos publicados; en 200, 605,521 s y 66,46/s para cada una de esas cuatro series. Que las tasas medias coincidan sólo refleja igualdad de contadores al cierre; no implica publicación instantánea ni ausencia de backlog intermedio. La tasa de aceptaciones k6 de las tablas usa su propia duración real.


Validación estática final: git diff --check y git diff --cached --check pasan; staging vacío. OpenAPI: nueve operaciones, 72 referencias locales verificadas, sin clave 503 duplicada. Dashboard JSON válido, 24 paneles; promtool verifica 15 reglas y cuatro casos de alertas. Auditoría de patrones sensibles en textos modificados/sin seguimiento sin coincidencias de identidad del piloto, PEM privado o tokens Doppler; no equivale a un escaneo externo exhaustivo. bootJar no contiene certificados/respaldos ni launchers de pruebas. Se mantienen advertencias de APIs deprecated y normalización LF/CRLF, sin fallos de compilación.
