# Emitta — etapa 3: informe operativo local

Fecha: 2026-10-10. Rama `codex/ride-consolidation`. Sin commit, push, merge ni despliegue. Se preservan los cambios previos. No se ejecutaron llamadas reales al SRI ni migraciones sobre la base privada del piloto.

## Resultado y capacidad

La observabilidad y los procedimientos están preparados, pero el ensayo sostenido detectó un límite real de recuperación del Outbox. No se acredita capacidad de producción ni capacidad fiscal completa. k6 mide REST, persistencia e incorporación durable a jobs; firma, RIDE y SRI permanecen apagados en ese ensayo. Las políticas Redis se amplían exclusivamente en el fixture de carga para medir admisión, no cuotas comerciales.

Entorno: Windows, Java 25+37, Gradle 9.8, Spring Boot 4.1.1, Flyway 12.4.0; 12 CPU lógicas y 31,91 GiB RAM del host. Docker 29.8.1 dispone de 12 CPU y 15,58 GiB RAM. JVM de carga: máximo 1 GiB; k6 1.7.1: 2 CPU y 512 MiB. PostgreSQL 18, RabbitMQ 4 y Redis 8 comparten Docker sin presupuestos individuales de producción. El muestreo añade carga; estos resultados no dimensionan otro servidor.

| Ensayo | Documentos persistidos | Aceptaciones/s | HTTP p95 / p99, ms | Resultado |
|---|---:|---:|---:|---|
| 20 usuarios, 30 s | 1.406 | 45,36 | 173,88 / 334,58 | Pasa; jobs y eventos coinciden |
| 50 usuarios, 30 s | 1.835 | 56,33 | 451,66 / 786,03 | Pasa; drenaje 11,71 s |
| 100 usuarios, 30 s | 1.252 | 38,26 | 1.641,86 / 2.205,69 | Pasa; drenaje 11,78 s |
| 100 usuarios, 10 min | 38.676 | 64,12 | 1.035,47 / 1.785,89 | Falla checks y drenaje de cinco minutos |
| Spike hasta 500, escenario de 100 s | 7.834 | 77,01 | 1.991,78 / 2.332,84 | Pasa; drenaje 37,46 s |
| 200 usuarios, exploratorio de 2 min | 12.014 | 98,02 | 1.149,55 / 2.084,58 | Pasa; drenaje 108,38 s |

Los ensayos cortos y el spike no tuvieron errores HTTP. El sostenido tuvo 22 respuestas fallidas de 116.042 solicitudes (0,019 %), incluyendo 13 POST con 503. Replays/consultas fallidos impidieron acreditar el check de idempotencia al 100 %; esto no demuestra creación duplicada. Se verificó la igualdad documentos=facturas=eventos originales=38.676, todos TEST/QUEUED. El drenaje falló con 7.024 eventos pendientes al finalizar el presupuesto. La evidencia intermedia registró 28.825 jobs y 9.850 eventos pendientes, con uno en tránsito. No afirmar que ese ensayo completó todos los jobs. El harness se corrigió para conservar invariantes también cuando falla ese plazo.

Hikari llegó a sus 10 conexiones y esperas máximas de 14/36/87 en los ensayos 20/50/100; el spike alcanzó 190 esperas y 279 MiB de heap. La presión del pool y el backlog requieren dimensionamiento conjunto de admisión y consumidores antes de ampliar tráfico. No se aumentó el pool para ocultar saturación.

El microensayo de workers usa 16 jobs por nivel y SQL de 20 ms, con bloqueo real y segunda conexión. Es una medición sintética de contención, no render PDF/firma/S3/SRI: consultar `build/operational/worker-capacity.csv` de la última suite. Un worker puede ocupar dos conexiones; reservar margen para API, Outbox y probes. El rol app tiene timeout de transacción inactiva de 60 s: falta ensayar trabajos largos reales contra ese presupuesto.

| Workers | Jobs/s sintéticos | Pico conexiones | Pico esperas | Media scope de bloqueo, ms |
|---:|---:|---:|---:|---:|
| 1 | 28,99 | 2 | 0 | 27,47 |
| 2 | 50,57 | 4 | 1 | 29,05 |
| 4 | 85,14 | 7 | 3 | 31,97 |

El scope incluye checkout/transacción/trabajo simulado; no representa latencia general de consultas SQL.

## Problemas corregidos y archivos

- `recovery/DurableWorkRunner.java` y `PostgreSqlDurableWorkAdapter.java`: reclamar cada job justo antes de ejecutar, detener el poll tras fallo y medir antigüedad; contar RIDE pendiente únicamente con autorización y artefacto coherentes.
- `recovery/OperationalMetrics.java`, `RabbitQueueMetrics.java`, `DurableWorkConfiguration.java`: snapshots acotados, disponibilidad DB/Redis/broker, backlog y métricas sin consultas SQL durante scrape ni etiquetas fiscales privadas.
- `auth/adapter/config/SecurityConfiguration.java`: Prometheus requiere `operations:read`, con prueba 401/403/200.
- `outbox/adapter/config/RabbitOutboxConfiguration.java`: cooldown observable después de reintentos locales agotados, conservando el mensaje sin ACK prematuro.
- `OutboxPublisherConfiguration.java`: batch/lease configurables y rechazo de presupuestos incompatibles con confirms. Confirms siguen siendo de cinco segundos; el margen no garantiza todos los tiempos DB. Publicación sigue siendo al menos una vez, con admisión idempotente.
- `application.yaml`, `build.gradle.kts`, `JwtTestProperties.java`: pool y muestreo configurables, Prometheus, histogramas, scheduler separado y pruebas aisladas de perfiles/Redis del desarrollador.
- `V12__durable_processing_jobs.sql`: retirar DML del rol app sobre historial Flyway. No se aplicó a la base privada ni se creó V13 de producto.
- Pruebas: `OperationalMetricsTest`, `DurableWorkAdmissionTest`, `OutboxLeasePolicyTest`, `RabbitAdmissionRetryTest`, `PostgreSqlOperationalIntegrationTest`, `DurableRecoveryIntegrationTest`, `InterruptedWorkProcess`, `OperationalLoadTest`, `SecurityIntegrationTest`.
- Herramientas: `scripts/Test-IsolatedStorage.ps1`, `scripts/Test-OperationalLoad.ps1`, `tests/load/traffic.ts`; infraestructura efímera, sin URL objetivo externa ni credenciales del piloto.
- Operación: archivos de `ops/observability/`, incluido `postgres-diagnostics.sql`, esta guía, runbook, backup/V12, carga y roadmap. No se modificó la web comercial.

Se corrigieron dos fallos del harness Windows: límite de longitud de comando y classpath con acentos al lanzar el worker JVM, usando un JAR manifest temporal. Se habilitó explícitamente exportación de métricas en el test Spring. Se desactivó el fallback Redis del desarrollador en tests sin contenedor; las pruebas dedicadas sustituyen esa configuración por Redis aislado. Los resultados iniciales del harness fallido no cuentan como validación final.

## Configuraciones

| Variable | Default | Propósito |
|---|---|---|
| `EMITTA_DB_POOL_MAX` | 10 | Conexiones máximas |
| `EMITTA_DB_POOL_MIN` | 2 | Mínimo idle |
| `EMITTA_DB_CONNECTION_TIMEOUT_MS` | 30000 | Espera de conexión |
| `EMITTA_OBSERVABILITY_ENABLED` | true | Probes/snapshots operativos |
| `EMITTA_OBSERVABILITY_SAMPLE_MS` | 15000 | Muestreo |
| `EMITTA_ADMISSION_OUTAGE_COOLDOWN` | PT30S | Pausa antes de requeue tras agotamiento |
| `EMITTA_OUTBOX_BATCH_SIZE` | 5 | Publicaciones por claim |
| `EMITTA_OUTBOX_CLAIM_LEASE` | PT45S | Lease de publicación |

Las variables `EMITTA_LOAD_PROFILE`, `EMITTA_LOAD_VUS`, `EMITTA_LOAD_DURATION`, `EMITTA_LOAD_SPIKE_DIMENSIONED` pertenecen al ensayo. Los flags fiscales/recepción/jobs/firma/RIDE conservan sus defaults desactivados. Ningún cambio activa una excepción experimental o PRODUCCIÓN.

## Evidencia, procedimientos y pendientes

Validación final `gradlew.bat build`: **340 pruebas, 339 satisfactorias, 0 fallos, 0 errores y 1 SRI live omitida**, BUILD SUCCESSFUL en 5 min 33 s. Incluye compilación, integración PostgreSQL/Flyway V1–V12, Redis y RabbitMQ, concurrencia y caída/recuperación, DLQ/duplicados, Circuit Breaker/timeouts/resultado fiscal incierto, roles, rollback y migración concurrente, restore sintético, recuperación RIDE y kill del worker JVM. El kill no ejecuta firma ni recepción.

`scripts/Test-IsolatedStorage.ps1`: **6 pruebas satisfactorias**, 0 fallos y 0 omitidas, BUILD SUCCESSFUL en 1 min 39 s, con MinIO nuevo y PostgreSQL aislados. Cubre artefactos XML/XAdES/RIDE/SHA-256 sintéticos; el MinIO temporal fue eliminado. Total de ambas suites: **345 satisfactorias y 1 SRI live pendiente**. Los contenedores privados existentes permanecieron intactos.

Los cinco ensayos k6 satisfactorios y el sostenido fallido son ejecuciones separadas; no sumarlos como casos únicos de la suite. `promtool` verificó 14 reglas, configuración en modo syntax-only y tres casos de alertas. OpenAPI: 9 operaciones/72 referencias internas; dashboard: 18 paneles. YAML/JSON y sintaxis de los dos scripts PowerShell válidos. `git diff --check` y `git diff --cached --check` pasan; staging vacío. Auditoría de texto modificado sin coincidencias de identidad privada del piloto, cabeceras de claves privadas o tokens Doppler evidentes; JAR sin certificados/claves/respaldos ni helpers de tests. Esto no sustituye un scanner externo ni habilita publicación automática.

Los artefactos locales están ignorados por Git en `build/operational/`: resúmenes k6, recursos, invariantes, CSV workers y prueba backup/restore sintética. No publicar fixtures ni logs completos. El backup sintético fue restaurado realmente en otra base y se verificaron datos, esquema y ACL; no acredita un respaldo privado del piloto.

Procedimientos: [carga aislada](load-testing.md), [runbook Rabbit/Redis/PostgreSQL/RIDE y seguridad](runbook.md), [backup, restore y aplicación segura V12](backup-v12.md). Dashboard de 18 paneles y 14 reglas están preparados; Prometheus/Alertmanager no están desplegados. El collector requiere HTTPS privado, CA y renovación segura del JWT externo. Métricas ready no equivalen a total/unacked; estos últimos requieren plugin del broker.

Pendientes: resolver recuperación sostenida de Outbox/admisión y repetir soak; dimensionar conexiones/leases/timeouts sobre trabajos reales; ensayo de 200 usuarios durante diez minutos y kill de todo el servidor HTTP bajo carga; HA y pérdida de disco; respaldo/restore privado autorizado antes de V12; configuración TLS/proxies/Cloudflare y notificaciones. El scanner no repara un PDF físicamente ausente cuando su metadata ya existe. La prueba SRI live sigue omitida y pendiente; la política de proveedor sigue sin resolverse. No hubo cambios de certificados, secretos ni documentos reales.

Próxima etapa recomendada: optimizar y limitar admisión frente a capacidad de consumidores, validar presupuestos con el rol app y repetir carga sostenida en infraestructura dimensionada antes de funcionalidades SaaS o lanzamiento.
