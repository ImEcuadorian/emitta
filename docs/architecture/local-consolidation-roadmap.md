# Consolidación local — 2026-10-09

Base publicada: feature/certificate-signing, 0f0a7c7. Trabajo actual: codex/ride-consolidation, sin commit, push, merge o despliegue.

Se revisaron PR #1 (fundación RIDE) e issue #2 en lectura. El roadmap público contiene tareas ya implementadas en la rama consolidada; este documento distingue evidencia actual de planes. No se modificó GitHub durante esta etapa.

| Área | Estado comprobado | Pendiente |
| --- | --- | --- |
| Facturas e idempotencia | Casos de uso y pruebas existentes | Casos de carga/concurrencia adicionales |
| XML 2.1.0 y XAdES-BES | Implementados y probados | Otros tipos fiscales |
| Recepción y autorización | Adaptadores existentes protegidos con CB/Bulkhead, recepción sin retry y consultas acotadas | SRI live omitido; conciliación automática y operaciones públicas pendientes |
| Almacenamiento | XML, SHA-256 y adaptador S3 existentes | Recuperación y backups verificados |
| RIDE | Renderer y SHA-256 reproducibles, scanner y trabajos PostgreSQL recuperables | Aplicar V12 tras respaldo privado; logos y casos fiscales especiales |
| Consultas/descargas | Tres GET con scopes y aislamiento, incluido estado del trabajo RIDE | Listado, filtros, historial y enlaces temporales |
| Seguridad | JWT, scopes, tenant activo, AES-GCM y PKCS12 existentes | Revocación inmediata por API client y alta REST de certificados |
| Redis | Token Bucket Lua compartido; límites IP/cliente/tenant/ruta y fail-closed HTTP | Carga, HA y política explícita para proxies confiables |
| RabbitMQ | DLQ, confirms/returns, admisión durable, retries locales acotados y journal | Política DLX privada y ensayo operativo HA |
| Resilience4j | CB/Bulkhead/Retry aplicados a puertos SOAP existentes; lectura de cuerpo cancelable en Java 25 | Ajuste operativo de umbrales y alertas |
| Observabilidad | Métricas HTTP/Redis/colas/jobs/CB/RIDE; pruebas aisladas de fallo y recuperación | Alertas y monitoreo HA; total/unacked desde el broker |
| Web comercial | Landing local clara, responsive y documentación real | Canal comercial confirmado y revisión del propietario |
| Portal SaaS | No implementado | Login humano, organizaciones, dashboard, API clients, cuotas |
| Webhooks y correo | No implementados | Entrega autenticada, reintentos y auditoría |
| Lanzamiento | No preparado | QA/carga, CI/CD, backups, políticas y requisitos normativos |

Las cuatro áreas de resiliencia se implementan en etapa 2 con pruebas aisladas. El circuito fiscal conserva la prohibición de retransmitir por timeout.


## Etapa 2 — motor resistente a fallos

Implementación local incremental: rate limiting Redis distribuido; DLQ y admisión durable; confirms/returns Outbox; protección independiente SRI sin retry de recepción; trabajos recuperables RIDE con V12, leases y SHA-256 reproducible. Consulta REST del trabajo RIDE y OpenAPI sincronizados.

La tabla refleja el estado local de etapa 2. La evidencia de implementación, configuraciones y resultados finales se registra en [resilience-recovery.md](resilience-recovery.md). No se activaron workers ni se aplicó V12 a la base privada. SRI live continúa pendiente, sin afirmar integración fiscal completa.

Siguiente revisión: aplicar V12 con respaldo en entorno aislado, política RabbitMQ y recuperación operativa, dimensionar pool/leases, definir alertas y evaluar carga mediante simuladores. Portal SaaS, cuotas comerciales y webhooks quedan fuera de esta etapa.


## Etapa 3 — preparación operativa local

Se añaden muestreo operativo, Prometheus protegido por operations:read, dashboard y 14 reglas configurables; cooldown observable de admisión RabbitMQ; reclamación de jobs justo antes de ejecutar; dimensionamiento explícito de lease/batch Outbox y variables Hikari sin ampliar el pool. V12 protege el historial Flyway frente a DML de app.

Pruebas nuevas cubren roles reales, rollback, migradores concurrentes, caída DB, backup/restore sintético y terminación abrupta de un worker JVM. k6 usa infraestructura efímera, API real y cuotas de tráfico ampliadas sólo en el ensayo; conserva firma/SRI apagados y mide admisión durable. Informes y límites en [docs/operations](../operations/load-testing.md).

Pendientes operativos: aprobación y restore privado antes de V12, integración de notificaciones, renovación del JWT del collector, configuración de proxies confiables, límites de trabajo compatibles con timeout app y ensayo HA. La prueba SRI live permanece pendiente. Ningún resultado de admisión REST acredita capacidad fiscal completa.

La carga corta de 20/50/100, el ensayo exploratorio de 200 y el spike de 500 completaron admisión y drenaje. El sostenido de 100 durante diez minutos falló checks y recuperación de Outbox dentro de cinco minutos: resolver ese cuello de botella y repetir soak antes de lanzamiento. Resultados, límites y procedimientos en [informe operativo](../operations/operational-report.md).

## Etapa 4 — Outbox y admisión sostenida

Medición separada de confirms, claims, marcas, adquisición Hikari, transacción de factura y jobs confirmados tras commit; tasas derivadas de snapshots y ready/unacked del broker efímero. Publicación correlacionada y marca por lote, admisión RabbitMQ por transacción acotada y backpressure sobre pendientes de publicación/admisión. Pool permanece en diez; estados fiscales, certificados y política de proveedor no se alteran.

Evidencia comparativa y límites en [capacidad etapa 4](../operations/outbox-capacity-stage4.md). El código final pasó 100 VUs durante diez minutos y drenó en 12,78 s, dentro del presupuesto de cinco minutos. Después se ensayaron 200 VUs: los aceptados drenaron en 25,89 s, pero falló el umbral HTTP por rechazos de capacidad; 200 VUs no están acreditados. En 100 también disminuyó el throughput aceptado y aumentó la latencia respecto a etapa 3. Próxima optimización: reducir esperas y uso de conexiones por operación, medir concurrencia de admisión con margen para la API y distinguir causas del brake. SRI live, reinicio del servidor HTTP durante carga y dimensionamiento de firma/RIDE/SRI siguen pendientes.

## Etapa 5 — admisión eficiente y cierre fiscal

Comparativa A/B/C en [admisión etapa 5](../operations/admission-capacity-stage5.md). Se conserva Hikari diez y el brake; se eliminan el merge/update inicial redundante y viajes SQL individuales de admisión. El guard distingue cuatro causas y mantiene caducidad/fail-closed. La concurrencia configurable conserva default uno; los ensayos con dos consumidores son aislados. B pasó 100 VUs/10 minutos: 54.535 documentos, 90,47/s, p95 651,48 ms, p99 998,28 ms y drenaje 1,14 s. Dos consumidores pasaron pero rindieron menos; se conserva uno. B con 200 VUs falló latencia/checks/replays, aunque conservó 22.066 documentos/eventos/jobs y drenó en 4,21 s. No queda acreditada capacidad de 200 VUs ni capacidad fiscal. Regresión final: build satisfactorio, 352 casos aprobados y uno SRI live omitido; seis casos MinIO aislados aprobados (358 aprobados en total). Detalle y limitaciones en el informe de etapa 5.

La prioridad siguiente es el MVP fiscal completo, sin introducir módulos comerciales:

1. Verificar XAdES, referencias SRI y validación criptográfica independiente con evidencia reproducible.
2. Recepción manual controlada en certificación, únicamente con autorización explícita y sin retry ambiguo.
3. Consulta y conciliación de autorización por la misma clave de acceso.
4. Conservación íntegra del XML autorizado, respuesta y SHA-256 con aislamiento por tenant.
5. Generación y recuperación durable del RIDE, sin alterar autorización si falla el PDF.
6. Entrega y descarga segura de XML/PDF desde Emitta Web; definir cómo se autentica el cliente antes de integrar.
7. Prueba integral de certificación autorizada y auditada. La prueba SRI live omitida sigue pendiente.

Los módulos existentes se reutilizan y se verifican; no reconstruirlos ni afirmar que todos estos pasos estén pendientes de implementación. La carga REST con firma/SRI/RIDE apagados no acredita capacidad de emisión fiscal ni resuelve la política normativa de proveedor.

## Revalidación de etapa 2 — 2026-10-12

El documento recibido de nuevo se contrasta con la implementación actual; se conservan las etapas posteriores. Redis, DLQ/Outbox, protección SOAP y recuperación RIDE ya existen y se vuelven a validar con servicios efímeros. `Test-Resilience.ps1` utiliza ahora el helper MinIO desechable existente, sin Doppler ni sudo remoto, y conserva flags fiscales apagados y restauración del entorno. Resultados, cobertura y limitaciones en [revalidación del motor](../operations/resilience-revalidation-2026-10-12.md). La prioridad sigue siendo el cierre fiscal y la prueba SRI pendiente con autorización explícita; sin despliegue ni migración privada.

Primera revalidación: build 9 min 36 s, 352 pruebas aprobadas y una SRI live omitida; MinIO aislado 1 min 41 s, seis aprobadas. Total 358 aprobadas y una omitida. En esa primera comprobación no había cambios Java nuevos.

El usuario confirmó cerrar todos los requisitos del adjunto de etapa 2. Se corrigió la admisión que podía reencolar indefinidamente tras agotar retries: cuarentena persistente mediante el publisher existente, ACK sólo con confirms y enrutamiento completos, y conservación del original ante resultado parcial/desconocido. La CLI administrativa recupera únicamente un evento original coherente del tenant y un documento elegible sin job; no ejecuta payloads DLQ ni recepción SRI. No hay migraciones nuevas ni aplicación privada de V12. Si el almacenamiento de cuarentena también falla, se conserva el original: no se garantiza un contador histórico exacto durante la indisponibilidad simultánea.

Cierre final: build 8 min 3 s, 355 casos aprobados y una SRI live omitida; MinIO aislado 1 min 39 s, seis aprobados. Total 361 aprobados y uno omitido. Matriz completa y archivos en el informe enlazado. Se conservan los límites de etapa 5; 200 VUs siguen sin estar acreditados. La siguiente etapa requiere validación fiscal integral explícitamente autorizada, sin despliegue ni envío automático.

Revisión adicional: la CLI rechaza destinos document/event incompletos y cualquier mezcla con job antes de persistir. Test existente ampliado y build focalizado aprobados; no se cuenta como una nueva suite completa. No hubo recuperación privada ni envío fiscal.

## Auditoría del servidor — 2026-10-12

La auditoría de solo lectura del servidor se conserva en el entorno privado del operador. Las plantillas públicas no acreditan aplicación de controles, backups ni monitoreo en un servidor concreto.

Cloudflare autenticado e inspeccionado: túnel saludable con una réplica y ocho rutas, ninguna de Emitta/Portainer en el túnel observado. Inconsistencia entre hostname SSH publicado y destino protegido por Access/alias local; función MFA de Access no activada según UI. Grafana tiene política Allow limitada a identidad específica. UFW activo, DOCKER-USER IPv4/IPv6 vacías y acceso LAN Portainer confirmado; alcance Internet no verificado. No se cambiaron rutas, políticas ni firewall.



## Publicación y administración — 2026-10-12

El usuario autorizó publicar Emitta Web y API staging. Ambas rutas están publicadas; fiscal/SRI apagados. Alta REST de clientes, rotación, revocación y certificados revisados implementadas y probadas. Bootstrap operativo ejecutado y credenciales entregadas fuera del repositorio. CA y CRL públicas de ANF verificadas y montadas en staging. Ver [informe de entrega](../operations/staging-administration-report-2026-10-12.md). Pendientes: compatibilidad del filtro Cloudflare con algunos clientes HTTP, renovación de CRL, cuotas por IP detrás del túnel, almacenamiento fiscal de staging y prueba fiscal integral autorizada. Sin nueva migración ni cambios al piloto; no afirmar disponibilidad comercial completa.


Bloqueo 403/1010 del cliente urllib resuelto mediante excepción Browser Integrity Check sólo para el hostname API, aprobada por el usuario y verificada por HTTPS. Se conservan JWT y demás controles; pendientes de proxy, CRL, almacenamiento y validación fiscal sin cambios.

## Panel y cierre operativo de staging

Ampliación en curso: panel técnico `/admin/` con credenciales de aplicación, alta/rotación/revocación de clientes, formularios fiscales y carga revisada de certificados; sin autorregistro, MFA humano ni transmisión SRI. GET paginado administrativo, CORS de origen exacto, extracción de IP sólo desde peer del túnel verificado y auditoría transaccional V13. CRL ANF renovada desde fuentes oficiales y revisión diaria instalada sin workers fiscales. Bucket privado de staging y alta fiscal autorizada ya preparados; la contraseña del PKCS#12 sigue siendo interactiva. Pruebas finales/despliegue y factura SIGNED real todavía deben acreditarse. Detalle: [panel y controles](../operations/admin-panel-and-staging-closure.md). El piloto y la base privada no son destinos de estas migraciones.

Cierre operativo de esta ampliación: panel/API publicados y verificados por HTTPS; V13 sólo en staging con backup previo. Regresión: 370 aprobados/una SRI live omitida; almacenamiento aislado: seis aprobados; renovación pública de CRL: cuatro aprobados. CORS y peer del túnel exactos, ledger real con actor, automatismos fiscales/SRI apagados. Certificado real aún no importado: generación SIGNED y transmisión/autorización/RIDE de staging continúan pendientes de entrada segura de contraseña y posteriores aprobaciones de documento. No confundir este cierre administrativo con validación fiscal integral ni disponibilidad comercial.


### 2026-10-12 — Simplificación de documentación y administración (local)

Documentación pública separada de operación administrativa; referencia de integración derivada del contrato; alta por perfiles y selección/identificadores automáticos. Build, enlaces, tipado y flujo sintético validados. Pendiente revisión visual y publicación de esta revisión. Véase `docs/operations/documentation-and-admin-usability.md`.


### Revisión UX/UI de administración publicada

Sitio estático ux2 publicado y verificado en HTTPS; documentación simplificada publicada. API/estado fiscal intactos. Alta y perfiles probados con datos sintéticos; autenticación real del dueño y revisión móvil pendientes de su uso/revisión. Acceso humano con correo/MFA sigue pendiente, sin autorregistro.


### Preparación fiscal staging verificada

Certificado real importado por el operador; nueva factura TEST creada mediante REST y completada a SIGNED con XSD, verificación independiente y dos SHA-256 de MinIO. Cero intentos de recepción. Pendiente aprobación de envío manual, conciliación real y RIDE/descarga end-to-end. UNRESOLVED no se declaró resuelto; guarda normal intacta. Documentación pública de integración separada del contrato administrativo completo en la web.
# Actualización — 2026-10-12: prueba fiscal staging completa

- Verificado con una única recepción real aprobada: XML firmado → RECIBIDA → autorización TEST → XML autorizado → RIDE almacenado → descarga HTTPS protegida con integridad SHA-256.
- Procesos automáticos y PRODUCCIÓN siguen deshabilitados; política normativa del proveedor pendiente.
- Panel del dueño simplificado y publicado; pendiente unificar preparación/consulta fiscal y entrega de accesos en una sola sesión administrativa, con autorización y auditoría por tenant.
- Evidencias fiscales y certificados fuera de Git. Sin commit, push ni nuevas migraciones.

# Actualización — 2026-10-13: alta administrativa sencilla

- Publicado: crear negocio y credenciales de integración desde una única acción; conexiones internas separadas, identificación de aplicación consultable, resultados parciales sin reintentos automáticos.
- Próximo trabajo: ficha fiscal del cliente y preparación de contribuyente, establecimiento, punto y certificado desde la sesión del dueño. Requiere autorización por tenant y auditoría; no resolverlo entregando al cliente permisos administrativos.
- No se generaron facturas ni se realizaron llamadas al SRI en esta etapa de interfaz.

## 2026-10-14 — Alta fiscal por cliente y ficha del dueño

- Implementados y publicados en staging: configuración fiscal conjunta mediante API, resolución segura de registros existentes y entrega de pointOfIssueId; certificado por API con permisos propios; panel del dueño con datos fiscales, certificado y listado/descarga de documentos sin cambiar de sesión.
- Aislamiento y auditoría comprobados; V14 sólo amplía acciones del ledger, aplicada con backup previo a staging. No se modificó el piloto privado.
- Build satisfactorio: 376 pruebas aprobadas y una SRI live omitida; seis pruebas aisladas de almacenamiento adicionales. Web: doce páginas y 220 enlaces; tipado y flujo administrativo aprobados.
- Descarga real del PDF autorizado existente comprobada desde el navegador y por SHA-256. No hubo nuevos comprobantes ni llamadas al SRI.
- Pendientes: política del proveedor y preparación comercial/PRODUCCIÓN, login humano/MFA y habilitación operativa fiscal explícita. Automatismos apagados. Detalle en [alta fiscal y panel](../operations/fiscal-self-service-and-owner-panel.md).
