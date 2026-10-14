# Panel administrativo y cierre de staging

## Alcance

Panel técnico Astro/TypeScript en `/admin/`, sin autorregistro, pagos, cuentas humanas/MFA ni botón SRI. El operador global crea tenants y clientes; el cliente administrativo de cada tenant registra datos fiscales y certificados. Las aplicaciones reciben sólo sus permisos de integración. El JWT vive en memoria; CSP limita conexiones al origen API, requests sin cookies, sin redirects ni retries de POST y secretos limpiados al salir. Un timeout de POST exige conciliar el resultado antes de repetirlo.

El contrato estático versionado conserva las operaciones anteriores y añade GET paginado de tenants y clientes, exclusivamente `platform:admin`. Metadatos con `no-store`, nunca hashes o contraseñas. OpenAPI se copia a la web; Scalar sigue sin ejecutar requests. Los formularios están alineados con los DTO existentes, no inventan campos de teléfono ni alta de usuarios humanos.

## Seguridad y configuración

- `EMITTA_SECURITY_ALLOWED_WEB_ORIGINS`: vacío por defecto; staging usa sólo `https://emitta.nexorf.dev`. Sin comodines ni cookies CORS.
- `EMITTA_TRUSTED_CLOUDFLARE_PEERS`: vacío por defecto; lista de IP literales del socket del túnel previamente verificadas. Sólo ese peer permite utilizar un único `CF-Connecting-IP` válido; se ignoran X-Forwarded-For y cabeceras inválidas o múltiples. `server.forward-headers-strategy` sigue NONE. Si el contenedor cambia de IP, verificar y actualizar esta configuración; no ampliar a toda la red.
- V13 `administrative_audit_events`: auditados alta/rotación/revocación de clientes y registro de certificados en la misma transacción. Sólo identificadores/acción/fecha. Runtime sin UPDATE/DELETE; migrator mantiene privilegios de mantenimiento. No sustituye un ledger integral ni pruebas jurídicas de autorización del titular.
- Los flags fiscales/SRI permanecen false en el servicio API; almacenamiento dedicado permanece habilitado. V13 sólo es elegible para staging después de backup, nunca sobre el piloto privado.

## Confianza pública ANF

Fuentes públicas revisadas en [jerarquías oficiales ANF](https://crl.anf.es/), registradas en `ops/deployment/anf-trust-sources.json`. El fingerprint revisado de la raíz permanece fijado. `renew-anf-trust.py` comprueba raíz, firmas CA/CRL, vigencia con margen de 24 horas, revocación de la intermedia y ausencia de rollback de fecha/número. No descarga desde URLs aportadas por clientes ni cambia la raíz automáticamente. Sólo reemplaza el bundle CRL mediante archivo temporal, conservando el anterior fuera del repositorio. No accede al PKCS#12.

`schedule-anf-renewal.py` configura una única renovación diaria a las 03:17 según la zona del servidor, conserva el crontab anterior privadamente y registra resultado en `crl-renewal.log`. No programa procesamiento fiscal. Alertas externas ante fallo de cron aún requieren integración operacional; al caducar la CRL, la API rechaza el alta/firma.

## Preparación fiscal manual

El perfil `manual-test-preparation` sólo admite base `emitta_staging`, web NONE, un documento QUEUED/SIGNED TEST del tenant/RUC indicado y automatismos/SRI apagados explícitamente. Reutiliza orquestadores y almacenamiento existentes, revalida CA/CRL, RUC certificado, XSD, firma independiente y SHA-256. Informe privado nuevo 0600 en carpeta 0700. No modifica estados para forzar éxito ni transmite.

Si el operador solicita explícitamente `emitta.manual.acknowledge-provider-unresolved=true`, se permite omitir el proveedor sólo cuando su política sigue UNRESOLVED, en el hilo síncrono del documento y RUC aprobados. No afecta a un proveedor externo identificado, otros documentos, otros RUC, producción o workers. No acredita una excepción jurídica ni concede permiso para recepción SRI; la transmisión posterior sigue requiriendo aprobación del documento concreto.

La importación real permanece interactiva: contraseña oculta únicamente en el terminal del operador. No se considera comprobada la generación de staging hasta que exista certificado importado y una factura nueva llegue a SIGNED con evidencia verificable. El piloto anterior permanece intacto.

## Resultado verificado — 12 de octubre de 2026

- Publicados `emitta-web:20261012-panel1` y `emitta-api:20261012-panel1`. SHA-256 del JAR local, transferido y ejecutado: `d01f55a6b321026055945f7f5120a71feccc44e89f330c1f58c67d1b3c07a3d8`.
- V13 aplicada sólo en `emitta_staging` después de pg_dump custom y comprobación del catálogo del backup. Restore de ese backup privado aún no probado; el restore sintético con esquema/datos/privilegios sí pasó en Testcontainers. Versiones anteriores conservadas.
- Regresión completa: 371 casos, 370 aprobados, cero fallos y una SRI live omitida. `gradlew build` final aprobado en 4 s con tareas actualizadas, después de esa ejecución real. No se suman suites focalizadas solapadas.
- `Test-IsolatedStorage.ps1`: seis pruebas PostgreSQL/MinIO aprobadas, 2 min 27 s. Primer intento de invocar la tarea directamente no tenía su fixture y falló por variables ausentes; se corrigió la invocación, sin modificar sus validaciones.
- Primera regresión detectó cinco fallos del test operativo que esperaba V1–V12 y dos del slice MVC al faltar el nuevo mock administrativo. Expectativa actualizada a V1–V13, fallo sintético movido a V14 y mock añadido; segunda regresión sin fallos. Se añadió comprobación real de permisos INSERT/SELECT y rechazo de UPDATE/DELETE del ledger.
- Confianza/CRL: cuatro pruebas operativas aprobadas con material público oficial, incluyendo firma alterada, fingerprint inesperado y descarga fallida. Bundle anterior conservado; cron diario único verificado. Próximo vencimiento del bundle comprobado: 2026-10-19T23:47:00Z, sujeto a posteriores renovaciones.
- Astro: cero errores/advertencias/hints; build estático de 12 páginas, 4,05 s y 203 enlaces/anclas válidos. Copia OpenAPI idéntica al backend: 13 rutas/16 operaciones.
- HTTPS real: autenticación de operador/cliente, alta, permisos, rotación y revocación aprobadas. Se creó únicamente un tenant sintético vacío y un cliente después revocado para esta comprobación. Ledger: tres eventos, los tres con actor registrado. GET administrativo sin JWT 401; CORS del origen web permitido, origen ajeno 403 y sin cookies.
- Navegador: panel público cargado, credenciales ficticias rechazadas HTTP 401, campos limpiados y sin errores/warnings de consola. Inspección visual de escritorio realizada; el override de viewport del navegador no cambió el ancho efectivo, por lo que no se acredita revisión visual móvil en esta sesión.
- Salud UP; PostgreSQL, Redis, RabbitMQ y MinIO sin puertos host. Peer exacto del túnel coincide con configuración. Los siete flags automáticos/fiscales/SRI permanecen false.
- Auditoría de patrones de secretos y datos del piloto en producto/dist: sin hallazgos. `git diff --check` aprobado; staging Git vacío, sin commit/push/merge.

Estado fiscal staging al cierre: certificados, documentos, Outbox y jobs cero. Falta la importación interactiva del certificado real; todavía no hay un documento nuevo SIGNED listo para revisar. Autorización SRI, XML autorizado y RIDE real de esta base siguen pendientes de una transmisión posteriormente aprobada. La política normativa del proveedor sigue UNRESOLVED.

## Archivos de esta ampliación

Frontend: `src/pages/admin.astro`, `src/scripts/admin.ts`, acceso/estado/guía administrativa y copia pública OpenAPI. Backend: controllers/puerto/adaptador administrativos existentes, configuración de CORS y seguridad, `AdministrationAuditRecorder`, V13, resolución de peer confiable en rate limiting, perfil de preparación manual y scope técnico XML. Tests: seguridad/ledger, resolución y distribución de IP, generador XML, slice tenant y migraciones/backup/privilegios. Operación: scripts de publicación revisada, comprobación HTTPS y renovación/planificación de CRL; documentación y roadmap actualizados.

Antes de comercializar: cuentas humanas/MFA, auditoría integral, alertas de CRL/backup, restore operativo ensayado, permisos S3 mínimos y validación fiscal integral de staging, además de resolver el escenario normativo del proveedor. El panel actual es una herramienta técnica de staging, no una declaración de disponibilidad comercial.
