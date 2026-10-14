# Publicación de Emitta Web y preparación de API staging

2026-10-12. El usuario cambió el alcance anterior y solicitó publicar exclusivamente Emitta Web y API, sin continuar la auditoría SSH/general. Dominios confirmados: `emitta.nexorf.dev` y `api-emitta.nexorf.dev`. No hay autorización para llamadas fiscales reales, activar producción o procesamiento automático. Sin commit/push/merge.

## Web publicada

Proyecto independiente `emitta-web`: Astro/TypeScript/Starlight/Scalar estático. Se añadió `site` con el dominio autorizado, Dockerfile con imagen Nginx sin privilegios fijada por digest, configuración de rutas estáticas y `.dockerignore` restrictivo. Check Astro: cero errores/advertencias; build y comprobación de diez páginas y 152 enlaces aprobados. Sitemap generado.

Sólo se transfirieron Dockerfile, nginx.conf y dist; no código Java, certificados, variables ni evidencias fiscales. Release conservada en `$HOME/emitta-deploy/web/releases/web-20261012`; contenedor `emitta-web`, imagen `emitta-web:20261012`. Usuario 101, filesystem readonly, tmpfs acotado, capabilities eliminadas, no-new-privileges, memoria 128 MiB, CPU 0,5 y logs 10 MiB por archivo/3 archivos. Sin puertos host publicados. Usa la red de ingreso existente y Cloudflare Tunnel hacia `http://emitta-web:8080`; no se modificó Caddy u otras rutas.

Cloudflare confirmó creación de ruta/CNAME. Origen verificado por wget dentro del contenedor. HTTPS con validación de certificado, resolviendo el hostname a su dirección obtenida de DNS público: HTTP 200 en `/`, `/docs/`, `/docs/quickstart/`, `/docs/api-reference/`, `/openapi/emitta-v1.yaml` y `/sitemap-index.xml`. El resolver local/navegador conserva inicialmente NXDOMAIN; no se ha desactivado validación TLS ni modificado DNS del equipo. Evidencia local ignorada: `build/operational/emitta-web-route.png`.

## API: registro Doppler y arranque staging

Inicialmente faltaba autenticación en el scope nuevo. El usuario registró el token en su terminal. Se verificaron únicamente metadatos: proyecto `emitta`, config/environment `stg`, variables indispensables no vacías y SRI TEST. No se imprimieron tokens ni valores secretos. La API está ahora desplegada y validada en el servidor, y su ruta Cloudflare fue publicada después de la confirmación explícita del usuario. No reutilizar el token NEXORF ni credenciales/certificados del piloto.

La [documentación Doppler](https://docs.doppler.com/docs/service-tokens) recomienda Service Tokens limitados a una configuración para servicios; no usar tokens personales con permiso amplio. Registro oculto, sin literal secreto en historial/argumentos:

```bash
mkdir -p "$HOME/emitta-deploy/api"
chmod 700 "$HOME/emitta-deploy/api"
read -r -s -p 'Service Token Doppler de Emitta staging: ' emitta_doppler_token
printf '\n'
if [ -n "$emitta_doppler_token" ]; then
  printf '%s' "$emitta_doppler_token" | doppler configure set token --scope "$HOME/emitta-deploy/api" >/dev/null
else
  printf 'Token vacío; no se registró nada.\n'
fi
unset emitta_doppler_token
```

Este registro persiste el token mediante la configuración local de Doppler; no lo convierte en una credencial temporal ni en un secreto del repositorio. No ejecutar con shell tracing (`set -x`). No pegar el token en chat. Tras registro, verificar sólo nombres/metadatos desde ese scope; no usar `doppler secrets` sin filtros ni volcar env/config Compose expandida.

Antes del arranque API: verificar proyecto/config y secretos requeridos, servicios dedicados, claves JWT segregadas, migraciones únicamente sobre base staging nueva, aislamiento/red y límites, y respuestas 401/403/rate limiting. No publicar una API placeholder ni simular conexión fiscal. Todos los flags fiscal worker/Outbox/jobs/signing/RIDE/SRI recepción/autorización deben fijarse false en el despliegue inicial. Ambiente fiscal exclusivo TEST. No importar certificados del piloto como efecto secundario de publicar.

## Resultado del despliegue staging

Archivos genéricos en `ops/deployment`: Dockerfile Java 25 no root y runtime fijado por digest, `.dockerignore` restrictivo, Compose dedicado y comprobador operativo Python. Se transfirió el JAR ya compilado/verificado y el script de roles existente con finales LF. No se trasladó la base privada ni certificados. El servidor no podía descargar MinIO DHI por autenticación de registry: se cargó la misma imagen ya disponible y utilizada localmente; no se copiaron volúmenes o datos.

Compose `emitta-staging` en `$HOME/emitta-deploy/api`: PostgreSQL 18, Redis 8, RabbitMQ 4 y MinIO con volúmenes nuevos, red de datos internal=true y cero puertos host publicados. API no root, readonly, sin capabilities, no-new-privileges, tmpfs acotado, 1 GiB/1 CPU, pool diez. Sólo API participa además en la red de ingreso existente con alias `emitta-api`. Los secretos se inyectan con Doppler; nunca usar `docker compose config` sin `--quiet` al validar.

Flyway validó y aplicó V1–V12 únicamente en `emitta_staging`, recién creada. Base privada/piloto intactos. API Java 25.0.4.1, perfil staging; flags firma/worker/Outbox/jobs/RIDE/SRI y almacenamiento desactivados, rate limiting activo y provider-policy unresolved. MinIO está preparado como servicio, pero no se ha creado/verificado un bucket ni habilitado almacenamiento fiscal. No hay certificado ni comprobante nuevo, ni llamadas SRI. La configuración de endpoints fiscales del producto no se altera; con flags false no se invocan TEST ni producción. El arranque no acredita emisión fiscal ni capacidad comercial.

Verificación `python3 check-staging.py --traffic`: health HTTP 200/UP, OpenAPI estático y `/v3/api-docs` HTTP 200; GET documento protegido y Prometheus HTTP 401 sin credenciales. 25 solicitudes auth con credenciales ficticias inválidas: diez HTTP 401 y quince HTTP 429, Retry-After presente. PostgreSQL: Flyway 12, documents/outbox/jobs/certificates cero. Cinco contenedores running y sin puertos host. No se generaron tokens reales en esta prueba. MinIO objeto/bucket, autenticación válida de cliente, roles administrativos y flujo fiscal siguen fuera de esta prueba inicial.

Ruta publicada en Cloudflare: `api-emitta.nexorf.dev` hacia `http://emitta-api:8080`, con CNAME creado. HTTPS con certificado validado: health, OpenAPI estático y /v3/api-docs HTTP 200; documento protegido y Prometheus HTTP 401. Expone auth, documentación y health; recursos fiscales y métricas requieren JWT/scopes. Evidencia local ignorada: `build/operational/emitta-api-route-published.png`. No se modificó SSH o Portainer, ni se hizo commit/push/merge.

## Administración publicada

Actualización posterior: endpoints administrativos de clientes y certificados desplegados; bootstrap de operador y prueba administrativa HTTPS aprobados. CA y CRL ANF públicas verificadas, sin importación de PKCS#12 real. Web y OpenAPI actualizados. Existe un bloqueo Cloudflare 403/1010 para Python urllib con User-Agent por defecto; regla limitada a Browser Integrity Check del hostname API preparada, todavía sin aplicar. Resultados exactos, configuraciones y riesgos en [informe administrativo](staging-administration-report-2026-10-12.md).

Corrección aprobada y aplicada: regla Cloudflare limitada al hostname API omite únicamente Browser Integrity Check. Python urllib por defecto autentica HTTP 200; credenciales inválidas y documento sin JWT HTTP 401. Evidencia local `build/operational/cloudflare-api-bic-active.png`. SRI y workers continúan apagados.
