# Publicación y administración de staging — 2026-10-12

## Resultado y alcance

Web publicada en https://emitta.nexorf.dev y API en https://api-emitta.nexorf.dev. Guía administrativa y OpenAPI versionado actualizados. No commit, push ni merge. Base staging independiente; piloto intacto. Firma, procesamiento fiscal, Outbox, jobs, RIDE y llamadas SRI permanecen apagados. Cero documentos y certificados en staging. Esto no acredita operación fiscal integral ni disponibilidad comercial.

## Cambios

- `ApiClientAdministrationController`, puerto administrativo y adaptador PostgreSQL existente: crear credenciales de cliente, rotar secreto y revocar acceso. BCrypt para secretos de cliente; secreto entregado una sola vez con `no-store`. Sólo operador `platform:admin`; lista explícita de scopes permitidos impide escalación.
- `SecurityConfiguration` y `TenantAdministrationAccess`: aislamiento por tenant, validación de cliente/tenant activo para JWT y bloqueo de tokens tras revocación.
- `CertificateAdministrationController`, `SigningKeyMaterialConfiguration` y configuración compartida: multipart HTTPS protegido por scopes, límites de tamaño, revisión de autorización, identidad por RUC certificado, integridad, vigencia y PKIX/CRL. Reutiliza validador y almacenamiento AES-GCM existente; no acepta raíces aportadas por el cliente. GET devuelve únicamente metadatos.
- `AdministrationExceptionHandler`: errores seguros y HTTP coherente sin devolver contraseñas. `AdministrationBootstrapConfiguration`: primer operador mediante perfil sin web ni workers, transacción y bloqueo de exclusión; archivo privado de credenciales creado exclusivamente, nunca stdout. Flush JPA antes de persistencia JDBC del cliente.
- `scripts/Import-ReviewedCertificate.ps1`: solicita JWT y contraseña ocultos, usa HTTPS sin redirects y no reintenta importaciones automáticamente.
- `application.yaml`, perfil bootstrap, Compose y comprobadores de `ops/deployment`; OpenAPI estático, guías operativas y roadmap. Emitta Web: landing, estado, acceso, autenticación, quickstart y nueva guía administrativa. Scalar sin solicitudes interactivas.

El contrato público contiene 13 rutas y 14 operaciones. Los endpoints existentes para tenants, contribuyentes, establecimientos, puntos de emisión, facturas y documentos se conservan. Operaciones nuevas:

| Método | Ruta | Autorización |
|---|---|---|
| POST | `/api/v1/tenants/{tenantId}/api-clients` | platform:admin |
| POST | `/api/v1/tenants/{tenantId}/api-clients/{id}/rotate-secret` | platform:admin |
| POST | `/api/v1/tenants/{tenantId}/api-clients/{id}/revoke` | platform:admin |
| POST | `/api/v1/taxpayers/{taxpayerId}/certificates` | certificates:write y propiedad del tenant |
| GET | `/api/v1/taxpayers/{taxpayerId}/certificates` | certificates:read y propiedad del tenant |

La web es pública/documental; no se implementó un portal administrativo humano.

## Configuración y confianza

Doppler `emitta/stg`. `EMITTA_CERTIFICATE_ADMINISTRATION_ENABLED` por defecto false en producto, true explícito en staging. `EMITTA_CERTIFICATE_MASTER_KEY_B64` debe representar 32 bytes; nunca registrarla. `EMITTA_CERTIFICATE_TRUSTED_CA_FILE` y `EMITTA_CERTIFICATE_CRL_FILE` apuntan a archivos públicos montados readonly fuera de la imagen. El PKCS#12 y su contraseña se cifran mediante AES-GCM con el adaptador existente; la contraseña no se devuelve. Cambiar la clave maestra sin migrar ciphertext impediría descifrar certificados existentes.

Raíz y listas públicas obtenidas de https://crl.anf.es/; raíz contrastada con la copia previamente revisada, cadena y firmas CRL verificadas con OpenSSL. CRL actual vence el **19 de octubre de 2026 a las 13:27:03 UTC**: renovarla desde el emisor antes de esa fecha. No se generó una CA artificial, no se importó un certificado real ni se compró otro certificado. Otros emisores necesitan confianza revisada propia. La evidencia de autorización se conserva privadamente; su SHA-256 y la declaración de revisión no sustituyen por sí solos comprobación jurídica independiente.

Primer operador creado y archivo protegido entregado fuera del repositorio. Archivo servidor modo 0600, directorio 0700; copia local con ACL del usuario. Es una credencial administrativa en texto protegido: trasladarla a un gestor de secretos. No incluirla en Git ni en imágenes.

## Despliegue y migraciones

API `emitta-api:20261012-admin1`; web `emitta-web:20261012-admin1`. SHA-256 del JAR local y ejecutado: `81a36ba3faf5810cb2cbdec910565148ff908cfa8003e3ba7d0a527a05286c82`.

Servicios dedicados, red de datos interna y sin puertos host; API no root, readonly, capacidades eliminadas y recursos acotados. Secretos inyectados por Doppler. No nueva migración en esta ampliación: V1–V12 sólo sobre la base staging nueva, nunca V12 sobre la base privada. Backup previo de staging generado; restore no probado. Imagen/Compose anteriores y contenedor web anterior retenidos para rollback. MinIO disponible como servicio; bucket y almacenamiento fiscal de staging aún desactivados.

## Validación exacta

| Validación | Resultado |
|---|---|
| Build completo de regresión | 360 casos: 359 aprobados, 1 SRI live omitido, 0 fallos; 9 min 48 s |
| Regresión final seguridad/multipart | 13 aprobados; 1 min 35 s |
| PostgreSQL/MinIO aislados | 6 aprobados; 1 min 58 s |
| Cambio final bootstrap JPA/JDBC | compile/bootJar aprobado; bootstrap real en staging exitoso, 29,558 s |
| HTTPS administrativo | operador/cliente, alta, scopes, rotación, secreto antiguo inválido y JWT revocado: aprobado con User-Agent descriptivo |
| Astro check/build | 0 errores/advertencias/hints; 11 páginas, build 3,23 s; 188 enlaces/anclas válidos |
| OpenAPI | YAML, referencias locales y copia pública: 13 rutas, 14 operaciones, 74 referencias |
| Secretos y Git | patrones evidentes y recursos del JAR revisados; diff check aprobado, staging vacío |

Las suites focalizadas se solapan con la suite completa; no sumarlas como casos nuevos. El ajuste final del bootstrap se verificó mediante compilación y ejecución real, no mediante otra suite completa. Un intento previo falló por la expectativa 403 de tenant inactivo: ahora JWT inactivo devuelve 401; se corrigió el test y se ejecutó la regresión completa. No se ejecutó la prueba SRI real omitida ni llamadas fiscales. Las pruebas HTTPS sólo crearon un tenant sintético vacío y un cliente luego revocado.

## Pendientes y riesgos

1. **Cloudflare 403/1010 corregido**, tras aprobación explícita del usuario. Regla activa `dbe16c010a4e4c66af86f8dcaf128f65`, expresión `(http.host eq "api-emitta.nexorf.dev")`, omite únicamente Browser Integrity Check y conserva logging y demás controles. Python urllib por defecto y cliente con User-Agent descriptivo: autenticación válida HTTP 200; credenciales inválidas HTTP 401; documento sin JWT HTTP 401; health HTTP 200. No se modificaron otros hostnames.
2. El limiter usa peer IP y no confía en headers arbitrarios. Detrás del túnel las cuotas IP pueden agregarse; definir proxies confiables y extracción segura antes de tráfico multi-cliente amplio.
3. Renovación CRL manual; sin rotación automática de clave AES ni ledger integral de auditoría administrativa.
4. No importación de PKCS#12 real ni tenant fiscal real en staging. Sin buckets fiscales habilitados, prueba SRI integral ni resolución jurídica del proveedor. No presentar carga REST como capacidad fiscal autorizada.

Siguiente etapa: cerrar filtro de clientes HTTP y proxy confiable; después habilitar almacenamiento, alta revisada de contribuyente/certificado y prueba fiscal TEST manual con autorización explícita. No habilitar producción ni workers como efecto secundario.
