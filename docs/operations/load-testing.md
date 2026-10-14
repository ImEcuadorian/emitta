# Ensayos reproducibles de carga

El harness `OperationalLoadTest` arranca PostgreSQL 18, RabbitMQ 4, Redis 8 y k6 1.7.1 en Testcontainers, con puertos nuevos, JWT RSA y cliente sintéticos. No admite URL externa, no usa Doppler ni base del piloto. El script TypeScript sólo permite el alias `host.testcontainers.internal` y recibe los fixtures dentro del contenedor. Firma, PDF y puertos SRI están desactivados. No se simula autorización fiscal durante el benchmark REST; los simuladores SOAP y las pruebas XML/XAdES/RIDE se ejecutan aparte.

Medimos **admisión REST durable** con creación de factura, replay de idempotencia y lectura autenticada; no emisión/autorización por segundo. No se publica una capacidad comercial. Cada iteración crea un documento y hace dos operaciones adicionales. Todos usan un tenant/punto sintético: mide también la contención de ese escenario, no múltiples tenants distribuidos.

```powershell
# Sin Doppler. Ejecutar desde el backend; requiere Docker Desktop disponible.
.\scripts\Test-OperationalLoad.ps1 -Profile normal -Vus 20 -Duration 30s
.\scripts\Test-OperationalLoad.ps1 -Profile normal -Vus 50 -Duration 30s
.\scripts\Test-OperationalLoad.ps1 -Profile normal -Vus 100 -Duration 30s

# Prolongadas: requieren supervisión y presupuesto de CPU/RAM/disco.
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 100 -Duration 10m
.\scripts\Test-OperationalLoad.ps1 -Profile sustained -Vus 200 -Duration 10m

# Sólo después de dimensionar el equipo; 20 -> 500 -> 20 VUs.
.\scripts\Test-OperationalLoad.ps1 -Profile spike -Vus 500 -Duration 30s -DimensionedSpike

# MinIO desechable: seis integraciones XML/firma/PDF/storage, sin tocar el servicio del piloto.
.\scripts\Test-IsolatedStorage.ps1
.\gradlew.bat build --console=plain
```

El rate limiter sigue activo en el benchmark, con políticas explícitamente ampliadas **sólo en el test** (capacidad 100.000, refill 10.000/s). Así medimos admisión y persistencia en vez de limitar toda la carga por la IP común. Los tests de protección conservan límites pequeños y comprueban 429, concurrencia y caída de Redis. No extrapolar las cifras a cuotas comerciales ni copiar estas políticas a producción.

Umbrales iniciales: checks e idempotencia 100%, HTTP failures <1%, p95 <2 s y p99 <5 s. 429/503 se contabilizan; también afectan la tasa HTTP failed, y no se interpretan como documentos aceptados. El replay no se reintenta ciegamente durante el ensayo. No ajustar umbrales después para ocultar fallos.

Resultados locales ignorados por Git: `build/operational/load/<perfil>-<vus>-<run>/summary.json`, k6.log, resources.csv, container-resources.jsonl, environment.json e invariants.json. El fixture con credenciales efímeras se elimina al terminar. Las métricas k6 usan nombres de operación, no URLs con UUID. Si falla un umbral k6, se conservan resumen y comprobaciones de persistencia antes de reportar fallo. Un error de arranque podría no producir resumen; no contarlo como carga ejecutada.

El test espera drenaje de Outbox/admisión hasta cinco minutos y verifica documentos=invoices=eventos originales=jobs, todo QUEUED/TEST y ninguna autorización. Un backlog que excede ese plazo falla la validación: no prueba pérdida, pero tampoco acredita recuperación dentro del presupuesto. La duración de drenaje se registra. El scanner/RIDE se prueba mediante integraciones aparte.

Recursos: JVM de ensayo máximo 1 GiB; k6 limitado a 2 CPU y 512 MiB. PostgreSQL/Rabbit/Redis emplean la capacidad compartida del motor Docker, sin límites de producción definidos. CSV muestrea heap/CPU de JVM, Hikari y backlog; Docker stats cada cinco segundos muestrea exclusivamente contenedores del ensayo. El muestreo añade algo de carga. Registrar versión del motor, CPU/RAM asignadas y presión externa antes de comparar; una ejecución en un portátil no dimensiona un servidor.

`build/operational/worker-capacity.csv` mide 16 jobs por nivel (1,2,4), bloqueo real y una operación SQL de 20 ms simulando consumo de una segunda conexión. Sirve para revelar contención/SKIP LOCKED; **no mide render PDF, firma, red S3 ni SRI**. Los máximos de espera incluyen arranque frío del pool mínimo 2.

El ensayo de migración fallida provoca una excepción SQL dentro de DDL transaccional y verifica rollback. La interrupción DB probada es pause/unpause del contenedor con socketTimeout; no equivale a un cluster HA ni pérdida física de disco. El reinicio del backend probado en la suite durable reconstruye Spring; un ensayo de kill del proceso/JVM y restore físico siguen siendo verificaciones operativas distintas.

Se incorpora también un kill real de **otro proceso JVM de worker**, manteniendo un bloqueo de job en el PostgreSQL de prueba: tras kill se recupera el lease sin ejecutar procesamiento fiscal ni alterar AUTHORIZED. Esto no equivale a reiniciar todo el servidor HTTP bajo carga. La prueba de backup hace pg_dump custom y pg_restore reales en una segunda base aislada, verifica ACL y conserva SHA-256; no valida backups privados ni recuperación de un volumen/disco perdido.

Los procesos Test reciben perfil `test`, JSON de configuración vacío y fallback Redis a un puerto cerrado. Los contextos con Redis real lo reemplazan con su contenedor; los demás desactivan probes Redis. Así no dependen de perfiles operativos ni servicios auxiliares del desarrollador. SRI live permanece desactivado por el task.

Referencia oficial: [k6 escenarios](https://grafana.com/docs/k6/latest/using-k6/scenarios/), [TypeScript](https://grafana.com/docs/k6/latest/using-k6/javascript-typescript-compatibility-mode/). Los perfiles no realizados se identifican expresamente en el informe de etapa 3.

Etapa 4 conserva umbrales y duración: se añaden conteos acumulados/timers en CSV, `pipeline-metrics.json`, `rabbit-queues.jsonl` y `drain.csv`. El drenaje final exige también ready/unacked cero en las estadísticas del broker aislado. La admisión por backlog permanece habilitada en carga; no aumentar sus límites para hacer pasar k6. Un 503 con código `ADMISSION_SATURATED` se cuenta como rechazo antes de commit, separado de resultado HTTP desconocido; sigue afectando el umbral de errores HTTP. Comparación y garantías en [capacidad etapa 4](outbox-capacity-stage4.md).

Etapa 5 añade profiling PostgreSQL únicamente en Testcontainers y comparación de concurrencia mediante `-AdmissionConcurrency 1|2`. Conservar batch 50, Hikari diez, backlog 5.000/60 s y umbrales originales. Los SQL normalizados y wait snapshots tienen coste de medición; comparar variantes con la misma instrumentación, no atribuir toda diferencia respecto a otro día a la implementación. Informe en [admisión etapa 5](admission-capacity-stage5.md).

Ante timeout u otro error posterior a un posible commit, repetir **el mismo payload y el mismo Idempotency-Key**. `ADMISSION_SATURATED` identifica rechazo de esa solicitud antes de su commit; otros errores no prueban ausencia de persistencia. Los scripts de carga mantienen su tráfico original para comparar, y registran commits sin acuse y resultados desconocidos; no constituyen un SDK con recuperación automática del cliente.
