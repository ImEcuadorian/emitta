# Emitta — operación controlada

Preparación local, 2026-10-10. No se ha desplegado un sistema de monitoreo ni ejecutado acciones sobre el piloto.

## Seguridad y límites de la evidencia

El SRI live sigue pendiente en la suite y no se ejecuta durante build. Nunca interpretar un timeout de recepción como permiso para retransmitir. SUBMITTED, RETRY_PENDING y trabajos BLOCKED requieren conciliación por la misma clave de acceso; consultar autorización no genera otra clave ni otra recepción. Las métricas no incluyen RUC, tenant, clave de acceso, UUID fiscal, XML, contraseñas ni tokens.

`/actuator/prometheus`, `/actuator/metrics/**` e info requieren JWT con `operations:read`. Health no muestra detalles. `ops/observability/prometheus.yaml` es una plantilla HTTPS: DNS, CA y archivo externo de JWT deben configurarse antes de instalar el collector. El JWT expira; un mecanismo externo debe renovarlo sin registrar su contenido. Un `up=0` puede significar token expirado o TLS inválido, además de backend caído. No abrir Actuator a Internet ni quitar autenticación para resolver un scrape.

Importar `ops/observability/emitta-dashboard.json` en Grafana y seleccionar una fuente Prometheus. Cargar `emitta-alerts.yaml` con `rule_files`; ajustar umbrales experimentales al entorno. No hay Alertmanager desplegado ni canal de notificación configurado: reglas preparadas no equivalen a alertas entregadas.

## RabbitMQ / Outbox

1. Comprobar broker, confirms/returns, consumidores y backlog Outbox. La métrica de la aplicación mide mensajes **ready**, no total/unacked. Habilitar el plugin Prometheus en un broker de ensayo para obtener total/unacked; su puerto 15692 debe ser accesible exclusivamente por el collector privado.
2. Ante una caída, conservar PostgreSQL y colas persistentes. El publisher no marca published hasta confirm y ausencia de return. La publicación puede repetirse tras una caída entre confirm y persistencia; la admisión por documento/kind es idempotente.
3. La admisión realiza intentos locales limitados y después pone el lote en cuarentena durable DLQ, conservando ID original, categoría e intentos. ACK sólo después de confirms y routing de todo el lote. Si falla o se desconoce la confirmación de DLQ, conserva el delivery durante un cooldown configurable (30 s) antes de requeue; no descartar mensajes para contener una caída. `emitta.rabbit.admission.exhausted`, `quarantined`, `quarantine.failed` y el backlog distinguen agotamiento, cuarentena confirmada y fallo de cuarentena. Confirmaciones parciales pueden duplicar copias DLQ; correlacionar por ID original. Una caída total impide registrar exactamente todos los intentos, y se prioriza conservar el original.
4. Revisar DLQ con acceso de operador privado. Inspeccionar metadatos/categoría, sin exportar payloads fiscales a logs. VALIDATION/INTEGRITY/PERMANENT: corregir la causa y revisar el documento. EXTERNAL_UNKNOWN: conciliar, nunca replay automático.
5. Para trabajos DEAD/TRANSIENT, `Recover-ProcessingJob.ps1` exige tenant/job y confirmación RECOVER, restablece el presupuesto de ese trabajo y no modifica el estado fiscal. Ejecutarlo solamente en el entorno autorizado. La DLQ no se purga ni reprocesa automáticamente.
6. Si se agotó la admisión y falta el job, inspeccionar DLQ y el evento original publicado. La misma CLI acepta tenant/document/event, exige RECOVER y verifica los datos originales en PostgreSQL. Sólo agenda trabajo local para QUEUED/GENERATING; resultados externos desconocidos, otros tenants y eventos inconsistentes se rechazan. No reinyectar payloads ni borrar evidencia DLQ como parte de la recuperación.

Etapa 4: Outbox batch 50 y lease dos minutos, confirms correlacionados solapados de cinco segundos, marca PostgreSQL por lote confirmado. El lease se valida contra dos esperas Hikari más margen; no ampliar tiempos DB sin revisar el lease. Admisión RabbitMQ por batch de hasta 50/100 ms, commit antes de ACK y DLQ fuera de la transacción. Backpressure cuenta también publicados sin job: no interpretar Outbox vacío como broker drenado. Detalles y evidencia en [informe etapa 4](outbox-capacity-stage4.md). La entrega sigue siendo al menos una vez.

## Redis

Los endpoints API responden 503/Retry-After si Redis está caído, y 429 cuando agotan tokens. Los jobs ya persistidos no dependen de Redis. No desactivar rate limiting como solución automática. Recuperar conectividad, comprobar `emitta.redis.probe.available`, TTL y solicitudes sintéticas. La métrica antigua `emitta.redis.available` representa el último consumo; el nuevo probe comprueba PING aunque no haya tráfico.

No hacer FLUSHALL en un Redis compartido. Reiniciar Redis puede perder tokens y otorgar capacidad inicial: Redis es auxiliar, no contiene estados fiscales. Definir persistencia/HA según la política de tráfico y recordar que fail-closed durante una caída afecta admisión, no autorización ya persistida.

## PostgreSQL y workers

La aplicación necesita DML; el migrador DDL; readonly SELECT. Ninguno debe tener superuser ni poder crear roles. V12 revoca DML del historial Flyway para emitta_app. La autorización multi-tenant sigue en los casos de uso y filtros; estos roles no proporcionan RLS ni aislamiento por tenant a sesiones SQL administrativas. Limitar estrictamente el acceso readonly: el permiso SELECT actual comprende también columnas cifradas de certificates; no es una API pública de metadatos.

Durante un fallo: contener conexiones nuevas, respetar timeouts y revisar Hikari pending/active, jobs y journal tras recuperación. No editar status para desbloquear. El worker reserva un trabajo inmediatamente antes de ejecutarlo, no todo el lote; un fallo termina ese poll y el próximo respeta next_attempt_at. Una reserva vencida se recupera mediante lease y SKIP LOCKED; un trabajo activo mantiene bloqueo de fila. Una caída abrupta libera el bloqueo y el lease permite recuperación posterior.

Cada worker activo puede consumir **dos conexiones**: una mantiene el bloqueo y otra ejecuta los servicios. Con pool 10 no configurar 10 workers. Presupuesto inicial: `pool >= 2*workers + conexiones concurrentes de API + margen operativo`; comprobar contra max_connections y la suma de todas las instancias, migrador, backup y collector. Pool mínimo 2 produce contención breve al crecer desde frío; los ensayos deben distinguir calentamiento de saturación sostenida. No aumentar conexiones sin medir.

Los servicios programados usan tres hilos para separar trabajo, snapshots y broker. El poll de jobs sigue en serie por instancia; esto no introduce tres workers fiscales. Las métricas DB se muestrean, no ejecutan consultas en cada scrape. `emitta.database.probe` mide el tiempo de la muestra SQL, no la latencia de todas las consultas. `emitta.observability.sample.timestamp.seconds` permite detectar muestras viejas.

El bootstrap configura idle_in_transaction_session_timeout=60 s para app y el bloqueo del worker mantiene una transacción durante el trabajo. Debe medirse el peor tiempo de generación, firma y S3 antes de habilitar workers con ese rol: superar 60 s puede invalidar la transacción del job aunque efectos previos sean durables. La suite ordinaria usa cuentas Testcontainers distintas y no acredita por sí sola ese límite operativo. No desactivar el timeout indiscriminadamente; coordinar tiempos máximos de trabajo y política DB.

## Reinicio / RIDE

Autorización fiscal y PDF son resultados independientes. Recuperar DB/S3 y el trabajo RIDE sin invocar recepción. Mantener `EMITTA_RIDE_ENABLED=false` y jobs/worker apagados hasta validar configuración. El scanner exige evidencia de autorización persistida compatible con XML; no crea autorización. Si existe metadata PDF pero falta/corrompe el objeto, el scanner actual no lo repara: restaurar el objeto verificado desde backup y diagnosticar; no alterar silenciosamente SHA-256.

## Configuración futura de despliegue

- TLS en ingreso y comunicación interna sensible; REST pública detrás de WAF y reglas de tráfico. Con `forward-headers-strategy=NONE`, la aplicación usa la IP del peer. Detrás de un proxy todos comparten ese bucket: dimensionar protección en el borde antes de publicar.
- Para IP real, permitir sólo peers de proxies explícitos, bloquear acceso directo al origen y hacer que el proxy elimine/reemplace headers recibidos. Mantener la estrategia NONE hasta probar una configuración de confianza de Tomcat/proxy en un entorno aislado; no aceptar CF-Connecting-IP/X-Forwarded-For de clientes arbitrarios. Cloudflare exige validar sus rangos oficiales vigentes y restringir el origen.
- PostgreSQL, Redis, AMQP, consola RabbitMQ, MinIO y métricas en red privada, sin puertos públicos; paneles administrativos mediante VPN/bastión y permisos mínimos. El compose local ya usa loopback; no convertirlo directamente en compose público.
- TEST y PROD: cuentas, bases, buckets, claves AES/JWT y configuración Doppler separados. Los flags de publisher/jobs/fiscal/SRI/firmado/RIDE permanecen apagados por defecto. No habilitarlos como efecto secundario del arranque.
- Doppler inyecta secretos en procesos autorizados; jamás volcarlos a env, logs, Dockerfile, dashboard, comandos impresos o Git. Clave AES debe conservarse con respaldo de secretos independiente y acceso auditado; un backup DB sin la clave necesaria no permite recuperar certificados.
- Backups cifrados, versionado/retención de objetos, pruebas de restore e integridad SHA-256. Establecer RPO/RTO y retención legal con responsables antes del lanzamiento, no inventar valores regulatorios.

Referencias oficiales: [Prometheus configuración](https://prometheus.io/docs/prometheus/latest/configuration/configuration/), [Spring Boot métricas](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [RabbitMQ consumers](https://www.rabbitmq.com/docs/consumers). Estas plantillas no se han aplicado a servicios privados.

Fuentes de seguridad y recuperación: [Cloudflare headers y origen](https://developers.cloudflare.com/fundamentals/reference/http-headers/), [pg_dump PostgreSQL 18](https://www.postgresql.org/docs/18/app-pgdump.html), [timeouts PostgreSQL 18](https://www.postgresql.org/docs/18/runtime-config-client.html).

Etapa 5: `emitta.outbox.admission.rejected.reason` separa `backlog`, `age`, `sample_unavailable` e `infrastructure`; el agregado original se conserva. Rechazo precommit: HTTP 503, Retry-After 2, no-store y `ADMISSION_SATURATED`. Una muestra caducada nunca desactiva el brake. Ante timeout genérico, conservar clave y contenido para reconciliar el resultado. `EMITTA_ADMISSION_CONCURRENCY` permite 1–2 (default 1); dimensionar junto con las dos conexiones por worker fiscal, API, Outbox y muestreo antes de cambiarlo. Las nuevas lecturas/inserciones JDBC por lote aplican timeout de cinco segundos y deadline transaccional. Comparativa en [etapa 5](admission-capacity-stage5.md).
