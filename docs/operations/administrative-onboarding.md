# Alta administrativa REST y bootstrap de staging

Las operaciones administrativas no activan firma, workers, recepción o autorización SRI. Ambiente exclusivo TEST. No hay alta comercial autoservicio ni portal de administración humano.

## Primer operador

El perfil `administration-bootstrap` es un comando explícito sin servidor HTTP. Reutiliza CreateTenantUseCase, el adaptador de clientes y BCrypt. Un advisory lock impide dos altas simultáneas y se rechaza el bootstrap si existe cualquier scope platform:admin. Crea tenant y cliente dentro de una transacción; guarda el secreto sólo en un archivo nuevo 0600 dentro de un directorio 0700. Si falla, revierte y elimina ese archivo. No acepta sobrescribir archivos existentes ni funciona sobre un filesystem sin permisos POSIX. No ejecutar sobre la base privada del piloto.

Desde la carpeta dedicada de staging, con Doppler registrado:

```bash
cd "$HOME/emitta-deploy/api"
umask 077
mkdir -p "$HOME/emitta-deploy/operator"
chmod 700 "$HOME/emitta-deploy/operator"
doppler run -- docker compose -f compose.staging.yaml run --rm --no-deps \
  --user "$(id -u):$(id -g)" \
  -e SPRING_PROFILES_ACTIVE=administration-bootstrap \
  -v "$HOME/emitta-deploy/operator:/bootstrap:rw" \
  api --emitta.bootstrap.operator-name='NEXORF staging operator' \
  --emitta.bootstrap.credentials-file=/bootstrap/operator.credentials
```

El archivo debe conservarse en un gestor de secretos y no pegarse en chat, logs o Git. Este cliente sólo tiene platform:admin: crea tenants/clientes, pero no obtiene automáticamente acceso a sus documentos o certificados. No se entrega este cliente global a clientes comerciales. Se asignan scopes por tenant y mínimo privilegio.

## Endpoints

- POST /api/v1/tenants/{tenantId}/api-clients: nombre y scopes permitidos; 201, secreto aleatorio de 256 bits devuelto una vez, no-store, sólo hash BCrypt persistido.
- POST /api/v1/tenants/{tenantId}/api-clients/{id}/rotate-secret: invalida la contraseña anterior, mantiene JWT emitidos hasta expiración; 200, nuevo secreto una vez.
- POST /api/v1/tenants/{tenantId}/api-clients/{id}/revoke: 204; autenticar y validar JWT del cliente deja de ser posible. Consultar estado activo requiere PostgreSQL; indisponibilidad no da acceso permisivo.
- POST /api/v1/taxpayers/{taxpayerId}/certificates: multipart certificate/password/alias opcional/authorizationEvidence/authorizationReviewed. Scope certificates:write, tenant propio, contribuyente activo TEST. Máximo .p12 1 MiB y evidencia 256 KiB, total multipart 2 MiB. Respuesta sólo UUID/huella/caducidad, no-store.

El cliente nunca puede pedir platform:admin u operations:read en el endpoint de creación. Cliente UUID + tenant se comprueban conjuntamente al rotar/revocar. No hay descarga del PKCS#12, contraseña ni clave maestra.

## Confianza y cifrado

Se reutilizan Pkcs12CertificateValidator, PostgreSqlSigningKeyMaterialAdapter y AesGcmSecretCipher. Antes de guardar: RSA y clave privada correspondiente, vigencia, uso de firma, PKIX contra CA configurada en servidor, CRL vigente y no revocación, identidad certificada por RUC y revisión administrativa. No se acepta CA del solicitante, nombre coincidente, sólo FK ni hash como prueba jurídica. Perfil actual admite identidad directa certificada; delegaciones/formatos distintos necesitan soporte y revisión específica, no excepciones permisivas.

La evidencia revisada se conserva sólo como SHA-256 en V11; su documento y trazabilidad de revisión deben conservarse privadamente por el operador. V13 añade auditoría transaccional de alta, rotación y revocación de clientes y registro de certificados: UUID de actor/recurso/tenant, acción y fecha, sin payloads ni secretos. El rol runtime puede insertar/consultar, pero no actualizar o borrar ese ledger. No es todavía una auditoría integral de accesos, intentos fallidos o cambios tributarios. El endpoint permite rotar mediante un nuevo registro válido y selección atómica; documentos previos conservan su certificado. No reactiva certificados deshabilitados/revocados. Rotar la clave AES es otro procedimiento y no está automatizado.

Variables: EMITTA_CERTIFICATE_ADMINISTRATION_ENABLED (default false en producto), EMITTA_CERTIFICATE_MASTER_KEY_B64 (32 bytes, Doppler), EMITTA_CERTIFICATE_TRUSTED_CA_FILE, EMITTA_CERTIFICATE_CRL_FILE. Activar administración no activa emitta.signing.enabled. Se reutilizan api_clients, api_client_scopes y certificates/V11; la única tabla nueva de esta ampliación es el ledger V13.

Staging monta sólo archivos públicos de confianza en /certificate-trust readonly, fuera de imagen/repositorio. Se verificó ANF High Assurance Ecuador Root CA contra descarga HTTPS oficial; SHA-256 0f361d8b258123ea9bb84dd3f2c821c0285479626e1185e12f1a04b85546e459. CRL y ARL oficiales descargadas sin credenciales/pago; firmas comprobadas con OpenSSL. Fuentes: https://crl.anf.es/ (jerarquía y enlaces públicos). La CRL del 12 de octubre vence el 19 de octubre de 2026; renovar antes del vencimiento. El controlador recarga archivos en cada importación y rechaza datos vencidos; no hay renovador automático ni descargas dictadas por el cliente, evitando SSRF.

No reutilizar el certificado personal del piloto como credencial de staging. Para cada alta real se necesita el .p12 autorizado y revisión del contribuyente del tenant correspondiente.

## Entrega al cliente

Crear tenant, cliente y scopes mínimos; guardar el secreto retornado en gestor privado y compartirlo con el responsable autorizado. Preparar contribuyente, establecimiento y punto de emisión con los casos de uso existentes. La contraseña del .p12 sólo se introduce por HTTPS desde una herramienta segura: no ponerla en argumentos de shell, URL o ejemplos de Swagger. El transporte requiere HTTPS; memoria multipart y buffers se limpian donde es posible, sin prometer borrado de todas las copias inmutables de la JVM. Scalar permanece sin botón de envío.
## PowerShell: importación interactiva

Tras crear un cliente administrativo del tenant y obtener su JWT, ejecutar el script genérico. Solicita JWT y contraseña ocultos, rechaza HTTP/redirecciones, no imprime el certificado y no reintenta automáticamente:

```powershell
.\scripts\Import-ReviewedCertificate.ps1 `
  -ApiBase https://api-emitta.nexorf.dev `
  -TaxpayerId '<UUID real del contribuyente en staging>' `
  -CertificatePath '<ruta privada local del .p12 autorizado>' `
  -AuthorizationEvidencePath '<ruta privada de la evidencia revisada>'
```

No sustituir estos marcadores por datos sintéticos para un certificado real. La contraseña no se incluye en argumentos ni historial. GET /api/v1/taxpayers/{taxpayerId}/certificates requiere certificates:read y devuelve sólo metadata.

Si las credenciales de onboarding se guardaron en un archivo privado de estado, `-OnboardingStatePath '<ruta privada del estado JSON>'` obtiene el JWT sin pedirlo ni imprimirlo y comprueba que corresponde al UUID del contribuyente. Mantener este archivo fuera del repositorio con permisos restringidos. La contraseña del certificado sigue siendo interactiva. Ver [alta fiscal de staging y alcance pendiente](staging-fiscal-onboarding.md).
