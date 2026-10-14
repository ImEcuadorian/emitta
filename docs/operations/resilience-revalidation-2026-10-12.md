# Revalidación de resiliencia — 2026-10-12

Rama `codex/ride-consolidation`. Se recibió de nuevo el documento de etapa 2; se revisa la implementación actual sin revertir las etapas posteriores ni reconstruir módulos. El trabajo autorizado es local. No se necesita contraseña de sudo, cambio de permisos del servidor ni despliegue para estas pruebas.

## Auditoría de los cuatro módulos

| Módulo | Implementación comprobada en código | Cobertura existente |
| --- | --- | --- |
| Redis | Lua Token Bucket, reloj Redis, consumo multibucket atómico, dimensiones IP/cliente/tenant/ruta; 429 y Retry-After; indisponibilidad devuelve 503. Usa IP del socket con forward-headers desactivados. Los workers no dependen de Redis. | Dos instancias, concurrencia, atomicidad, spoofing IP, aislamiento, caída/recuperación y cadena HTTP/JWT real. |
| RabbitMQ/Outbox | Confirmaciones correlacionadas, mandatory returns, eventos persistentes, claim/lease y marca durable. Admisión PostgreSQL por lote antes del ACK; DLQ confirmada, retry local acotado y cooldown ante caída. Jobs únicos con intentos y journal. | Broker reiniciado, confirm sin routing, duplicados, mensajes inválidos/DLQ, rollback previo al ACK, fallo entre confirm y marca, muerte del publisher y del worker. |
| Resilience4j | Guards independientes de recepción/autorización sobre los adaptadores SOAP existentes. Recepción un intento; consultas limitadas ante comunicación fallida. CB/Bulkhead, timeouts reales de conexión/cabeceras/cuerpo y cancelación de lectura. | Circuito abierto/half-open/recuperado, Bulkhead, intentos limitados, timeout de cabeceras/cuerpo y resultado incierto sin reenvío. |
| RIDE | Descubrimiento de autorizados sin PDF exige evidencia y hash de XML autorizado. Jobs RIDE durables/idempotentes, locks/leases, journal, backoff y recuperación. Fallar PDF no revoca la autorización ni envía al SRI. | Reinicio de contexto y proceso, lease vencido con lock activo, recuperación, generación PDF/SHA-256 en S3 y consultas protegidas por tenant/scope. |

Se conservaron el código fiscal, las claves de acceso, certificados, estados y artefactos del piloto. No se efectuó ninguna llamada real SRI ni se aplicaron políticas RabbitMQ o migraciones en servicios privados.

## Corrección mínima realizada

`scripts/Test-Resilience.ps1` dependía de Doppler y de credenciales de un MinIO local configurado. Ahora fuerza `build --rerun-tasks` y delega en el helper existente `Test-IsolatedStorage.ps1`: MinIO desechable, credenciales sintéticas, puerto loopback y limpieza únicamente de su contenedor etiquetado. PostgreSQL/Redis/RabbitMQ son Testcontainers. No se añadió otro broker, cliente SOAP ni infraestructura paralela.

El wrapper mantiene los flags fiscales/live apagados incluso si se heredan valores activos, restaura el entorno al salir y no continúa a almacenamiento si falla el build. Se verificaron esos caminos con dobles de comandos en un directorio ignorado; estas comprobaciones del wrapper no sustituyen las integraciones reales ni se suman a su total JUnit. Parsing PowerShell sin errores.

Archivos de la primera revalidación: este informe, `scripts/Test-Resilience.ps1`, la sección de operación local de `docs/architecture/resilience-recovery.md` y la anotación de revalidación en `docs/architecture/local-consolidation-roadmap.md`. Esa ejecución no modificó Java ni la web comercial. El cierre posterior de requisitos añade los cambios Java descritos a continuación.

## Configuración y migraciones

No hay variables de entorno ni migraciones nuevas en este trabajo. Se conservan `EMITTA_RIDE_ENABLED=false`, workers/jobs, publicación Outbox, firma, recepción y autorización desactivados por defecto. Hikari máximo diez y concurrencia de admisión uno; no se modifica el presupuesto para intentar superar la carga fallida de 200 VUs de etapa 5.

V12 ya existe y crea jobs/journal durables. Se valida con Flyway en PostgreSQL efímero; continúa pendiente su aplicación privada tras respaldo y aprobación. Las políticas de rate limiting, jobs, admisión y resiliencia permanecen configurables en `application.yaml`; inventario detallado en `docs/architecture/resilience-recovery.md`.

## Ejecución reproducible

```powershell
# Docker Desktop activo. No iniciar el backend contra la base privada.
powershell -NoProfile -File .\scripts\Test-Resilience.ps1
```

Para validar por separado:

```powershell
.\gradlew.bat build --rerun-tasks --console=plain
.\scripts\Test-IsolatedStorage.ps1
```

## Resultados

`gradlew.bat build --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, 9 min 36 s; 353 casos JUnit, **352 aprobados, cero fallos/errores y uno omitido**. Compilación Java, empaquetado y todos los casos generales se ejecutaron de nuevo. La omisión corresponde a la prueba real SRI, bloqueada explícitamente.

Entre las clases aprobadas: Redis Token Bucket (5), cadena HTTP del rate limiter (1), recuperación durable PostgreSQL/RabbitMQ (19), guardas SRI (5), lectura de cuerpo SOAP (2), seguridad (8) y creación de factura (3). Esas clases forman parte de los 352; no se suman otra vez. Flyway se ejecutó en PostgreSQL de Testcontainers.

`Test-IsolatedStorage.ps1`: **BUILD SUCCESSFUL**, 1 min 41 s; **seis casos aprobados**, cero fallos/errores/omisiones. Incluye almacenamiento XML/PDF, integridad SHA-256 y pipeline con autorización sintética y recuperación RIDE. Total general más almacenamiento: **358 aprobados y una prueba SRI live omitida**, sin duplicar ejecuciones focalizadas. Logs locales ignorados en `build/operational/resilience-revalidation-2026-10-12-build.log` y `resilience-revalidation-2026-10-12-storage.log`.

OpenAPI: nueve operaciones y 72 referencias locales válidas. Compose conserva bindings loopback de PostgreSQL/Redis/RabbitMQ/MinIO. `git diff --check` pasó; el escaneo de los archivos nuevos no detectó datos del piloto ni claves privadas evidentes, y el JAR no contiene rutas de certificados privados, respaldos o evidencias fiscales reales. Esta comprobación acotada no sustituye una auditoría especializada previa a publicar. Staging vacío.

## Pendientes y límites

- La prueba `SriSoapAuthorizationLiveTest` permanece omitida y pendiente; esta auditoría no acredita integración fiscal completa ni cumplimiento normativo del proveedor.
- Sudo se validó en la terminal del usuario, pero no está disponible sin interacción en la conexión del agente. No se solicitó, almacenó ni transfirió la contraseña; ninguna tarea de este documento necesita sudo remoto.
- La brecha de requeue indefinido con broker disponible se corrige en el cierre siguiente. Si no puede confirmarse la DLQ, se conserva el original: durante una caída total no se promete un registro exacto de todos los intentos ni se sacrifica el mensaje para imponer un límite.
- Redis Cluster usa un hash slot común para atomicidad; HA/failover real y capacidad del slot no están acreditados por los tests de caída de una instancia.
- DLQ no se reprocesa automáticamente; trabajos `BLOCKED`/resultado externo desconocido exigen conciliación por clave, nunca recepción automática.
- Si existe metadata PDF pero el objeto desaparece o se corrompe, el scanner no restaura por sí solo ese objeto: se necesita restauración verificada y diagnóstico.
- El timeout de cabeceras y cuerpo tiene presupuestos separados; no confundirlo con un único deadline global. Los circuitos y bulkheads son por instancia.
- La etapa 5 pasó 100 VUs y falló 200 VUs. No se repite carga en este trabajo ni se declara capacidad fiscal a partir de la admisión REST.

Próxima prioridad: cierre fiscal verificable del flujo existente (XAdES, recepción manual autorizada, conciliación, XML autorizado, RIDE y entrega segura), preservando el ensayo SRI real como operación explícitamente autorizada. Sin commit, push, merge ni despliegue.

## Cierre exhaustivo del adjunto

El usuario confirmó que esta continuación debe cerrar el documento de etapa 2, sin avanzar a funcionalidades nuevas del MVP fiscal. La revalidación anterior fue insuficiente como entrega: identificaba la falta de un límite durable de admisión sin corregirla.

### Brecha corregida

`RabbitOutboxConfiguration` utiliza el publisher existente para cuarentena tras agotar los retries locales. Cada copia conserva cuerpo/ID y headers, añade categoría, contador de intentos conocidos y `x-emitta-admission-exhausted=true`, y se publica persistentemente en el DLX existente. Sólo completa la recuperación del batch si todas las publicaciones tienen confirm positivo sin mandatory return. Fallo, confirm desconocido o confirm parcial retienen el original con cooldown/requeue. Duplicados de DLQ son posibles por entrega al menos una vez; no generan nuevos comprobantes ni autorizarán replay fiscal.

`DurableWorkPort`/`PostgreSqlDurableWorkAdapter` incorporan recuperación administrativa de admisión desde el evento original publicado y el documento del tenant. Reutilizan `enqueueFiscalBatch`, transacción y tabla de jobs existentes. Exigen evento/documento/payload coherentes, estado QUEUED/GENERATING y ausencia de job FISCAL. Registran recuperación en el job. No modifican estados fiscales, secuenciales, certificados ni claves de acceso. El payload de DLQ no se ejecuta ni se confía como fuente de identidad.

`RecoveryCommandConfiguration` y `Recover-ProcessingJob.ps1` ofrecen dos modos exclusivos: tenant/job o tenant/document/event. Ambos conservan confirmación RECOVER y workers/SRI apagados. No hay otro servicio, broker, endpoint público o migración. El mensaje DLQ se conserva como evidencia y no se reprocesa automáticamente. SUBMITTED/RETRY_PENDING y otros tenants nunca habilitan recepción.

### Matriz completa de requisitos

| Requisitos del adjunto | Implementación y evidencia local |
| --- | --- |
| IP, cliente, tenant, endpoint, políticas y autenticación protegida | TrafficFilter/RedisTokenBucket/RateLimitProperties; tests Redis y cadena JWT real. IP de socket, forwarded NONE. |
| Token Bucket distribuido atómico, 429, Retry-After, concurrencia e instancias | Lua Redis TIME/multibucket; dos instancias y 100 llamadas concurrentes con límite exacto; respuestas HTTP verificadas. |
| Caída/recuperación Redis y degradación explícita | 503 fail-closed y recuperación en Redis real de Testcontainers; workers/jobs conservan PostgreSQL como estado definitivo y no invocan el limiter. Cuotas comerciales no se mezclan. |
| DLX/DLQ, retries limitados, backoff/jitter, intentos persistidos y clasificación | Topología durable y política DLX mutable; jobs/journal PostgreSQL; Outbox backoff+jitter; admisión agotada en DLQ persistente con categoría/intentos. Fallo de DLQ conserva original. |
| Mensajes inválidos, duplicados, error permanente y externo desconocido | Validación acotada; job único por documento/kind; DEAD/BLOCKED; ningún replay automático de resultado fiscal incierto. Tests broker/DB y categorías. |
| Inspección y recuperación segura de DLQ | Procedimiento de operador en runbook, consultas metadata readonly y CLI limitada a un job/evento original del tenant. La CLI no purga ni copia payloads DLQ; registra recuperación. |
| Outbox sin pérdidas, confirms/returns, ACK tras efectos durables | Publicación y marca correlacionadas, locks/leases y commit previo a ACK; fallos entre confirm/marca, rollback, publisher muerto, broker reiniciado y DLQ parcial probados. |
| CB/Retry/Bulkhead/timeouts/metrics y recuperación SRI | Guards por operación, conexión/cabeceras/cuerpo reales; tests contra servidor SOAP local bloqueado, circuito abierto/half-open y exceso de concurrencia. Recepción un intento. |
| Conciliación, misma clave, coordinación de retries | QueryDocumentAuthorizationUseCase sobre clave persistida; local worker sólo llega a SIGNED, no envía al SRI; estados inciertos bloqueados. No crea clave nueva ni acumula retries de recepción. |
| Trabajo RIDE durable/idempotente, sin PDF duplicado, descubrir autorizados sin PDF | Jobs RIDE/scanner validan evidencia de autorización y hash; bytes PDF reproducibles, almacenamiento y metadata idempotentes; flags false conservan jobs sin ejecutarlos. |
| Retry temporal RIDE, journal, restart, autorización intacta, SHA-256 y consulta segura | Leases con lock, journal/budget/backoff, reinicio de contexto/proceso, MinIO con XML/PDF y SHA-256; GET ride-job con scope/tenant/no-store. Un fallo PDF no revoca autorización. |
| Métricas operativas y ausencia de secretos | Actuator/Micrometer: tráfico, HTTP errors, Redis, ready/DLQ, backlog/jobs/edad, retries, CB, duración y RIDE pendiente/fallos; contadores de cuarentena añadidos. Total/unacked mediante exporter broker aislado de etapa 3. Sin tags personales ni logging de payload/secretos. |
| Pruebas obligatorias, Flyway, servicios, seguridad, OpenAPI y rama | Suite general más MinIO aislado y pruebas nuevas de cuarentena/recuperación. V1–V12 sólo en PostgreSQL efímero; JWT/scopes/tenant, OpenAPI y scripts verificados. Rama y cambios previos conservados. |
| Restricciones y roadmap | Sin commit/push/merge/despliegue, llamadas SRI, V12 privada, cambios del piloto o web comercial. Roadmap actualizado, prueba SRI live omitida identificada. |

### Validación del cierre

Primer foco: cuatro tests de retry y un test de RabbitMQ/PostgreSQL aprobado. Comprueba tres intentos, headers persistidos en broker real, tenant/evento/estado, recuperación única y ausencia de llamadas fiscales. El test de confirm parcial conserva el batch original. Se corrigieron expectativas de tests (overload JUnit, paquete BatchMessageListener y propiedad receivedDeliveryMode) antes de aprobar el foco; no se alteraron umbrales ni garantías.

La CLI PowerShell pasó parsing y pruebas con comandos dobles: dos parameter sets, flags seguros y cancelación antes de Gradle. Un test Java adicional impide flags activos o destinos mezclados antes de cualquier operación durable. Estas comprobaciones shell no se suman a los casos JUnit.

Regresión final del código corregido: `gradlew.bat build --console=plain` satisfactorio en 8 min 3 s: 356 casos, 355 aprobados, cero fallos/errores y una prueba SRI live omitida. `Test-IsolatedStorage.ps1` satisfactorio en 1 min 39 s: seis casos aprobados con MinIO desechable. Total final: 361 aprobados y uno omitido. Los cinco casos de foco están incluidos en la suite general y no se cuentan dos veces. Flyway y recuperación se verificaron únicamente sobre contenedores efímeros.

Validaciones finales: `git diff --check` pasa; staging vacío; rama `codex/ride-consolidation`. Ambos scripts PowerShell tienen cero errores de parsing. El JAR no contiene certificados, claves ni archivos privados del piloto; las coincidencias del término evidence corresponden únicamente a clases Java del producto. Sin cambios del piloto, de la web o del servidor. No se ejecutó ninguna transmisión al SRI.

Archivos adicionales: `RabbitOutboxConfiguration.java`, `DurableWorkPort.java`, `PostgreSqlDurableWorkAdapter.java`, `RecoveryCommandConfiguration.java`, `Recover-ProcessingJob.ps1`, `RabbitAdmissionRetryTest.java`, `DurableRecoveryIntegrationTest.java`, `RecoveryCommandConfigurationTest.java` y runbook/documentación. Nuevas métricas: `emitta.rabbit.admission.quarantined` y `emitta.rabbit.admission.quarantine.failed`. No hay variables de entorno ni migraciones nuevas. Los límites de etapa 5 se conservan; este cambio no acredita 200 VUs ni capacidad fiscal.

### Revisión adicional de destinos administrativos

En la continuación se detectó que la CLI Java ignoraba un `document-id` suelto cuando se proporcionaba `job-id`. Ahora exige ambos identificadores document/event para admisión y rechaza cualquier mezcla con job, antes de llamar al puerto durable. Se amplió el test existente con destinos incompletos y mezclados, sin agregar casos redundantes. `gradlew.bat test --tests '*RecoveryCommandConfigurationTest' build --console=plain` pasa: compilación, empaquetado y el caso focalizado con cero fallos. Esta comprobación adicional no es una nueva ejecución de la suite completa ni aumenta su total de 361 aprobados. Log local ignorado: `build/operational/recovery-target-validation.log`. `git diff --check` pasa; no se ejecutó recuperación sobre datos privados.
# Revisión para publicación — 2026-10-13

La corrida completa previa al commit detectó una regresión en `ProviderSubmissionGuard`: la política no resuelta debía bloquear antes de leer artefactos, y la excepción manual introducía esa lectura anticipada. Se restauró el bloqueo temprano. Sólo el scope explícito del documento aprobado puede evaluar la omisión técnica; la comparación del RUC y ambiente TEST sigue siendo obligatoria. Las pruebas seleccionadas de configuración, guard, scope y CLI pasaron tras la corrección.

Las seis pruebas aisladas PostgreSQL/MinIO pasaron. Se detectó además que la restauración de variables inexistentes mediante `SetEnvironmentVariable(..., null, ...)` en PowerShell/.NET puede dejarlas vacías. El launcher ahora elimina esas entradas cuando no existían originalmente y conserva los valores que sí existían. La causa se reprodujo con una variable sintética y se validó la ausencia tras eliminarla; sintaxis PowerShell verificada. La corrida contaminada se interrumpió y el build final se ejecutó desde un proceso limpio.

No se ejecutaron cargas sostenidas ni pruebas SRI reales durante esta revisión. Evidencias, certificados, credenciales y auditoría privada del servidor se excluyen del repositorio público.

Resultado final de publicación (2026-10-13): Gradle build exitoso; 374 pruebas en la suite normal, 373 aprobadas y una prueba SRI real omitida, sin fallos ni errores. Seis pruebas PostgreSQL/MinIO aprobadas, sin omisiones. Web: Astro sin errores/warnings/hints, flujo administrativo sintético aprobado, 11 páginas generadas y 183 enlaces/anclas verificados. Auditoría: 593 archivos del índice backend y 33 del índice web sin patrones de secretos detectados; revisión del staging y diff check satisfactorios. No se ejecutaron nuevas transmisiones fiscales, migraciones sobre bases privadas, merges ni despliegues en esta publicación.
