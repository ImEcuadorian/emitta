---
title: Administración
description: Alta de clientes API y certificados revisados por tenant.
---
La web y la documentación están publicadas en **https://emitta.nexorf.dev**. La API staging está en **https://api-emitta.nexorf.dev**. Su exposición pública no activa firma, trabajadores fiscales ni llamadas al SRI. No es un servicio comercial de emisión habilitado.

## Entrega de acceso a una aplicación

Un operador con `platform:admin` puede crear un tenant mediante `POST /api/v1/tenants` y emitir credenciales para él mediante `POST /api/v1/tenants/{tenantId}/api-clients`.

```json
{
  "name": "Integración de ejemplo",
  "scopes": ["invoices:write", "documents:read", "artifacts:read"]
}
```

La respuesta incluye `id`, `tenantId`, `clientId` y `clientSecret`. El secreto aleatorio se muestra una sola vez y únicamente se guarda su hash BCrypt. Guarda la respuesta en un gestor de secretos; entrégala al responsable del cliente mediante un canal privado. Nunca incorpores estas credenciales en esta web, correos públicos, repositorios o frontend de clientes.

El cliente solicita su JWT en `POST /api/v1/auth/token`. El operador puede rotar su secreto con `POST /api/v1/tenants/{tenantId}/api-clients/{id}/rotate-secret` o revocarlo con `POST /api/v1/tenants/{tenantId}/api-clients/{id}/revoke`. Estos endpoints exigen `platform:admin`; un cliente no puede autoasignarse ese permiso. La rotación conserva los JWT ya emitidos hasta su expiración; revocar el cliente bloquea su validación posterior.

El [panel administrativo](/admin/) permite operar estas API con credenciales previamente entregadas. No hay registro comercial autoservicio ni cuentas humanas con MFA: el acceso actual utiliza un cliente API administrativo. El primer operador se habilita exclusivamente desde el servidor con el procedimiento de bootstrap privado; no existe un endpoint público para crearlo.

Sólo el operador global puede listar tenants y clientes, crear cuentas, emitir un secreto nuevo, rotarlo o revocarlo. Cada cliente recibe únicamente los permisos necesarios para su tenant. El JWT permanece en memoria y se elimina al cerrar sesión o abandonar la página; no se guarda en almacenamiento del navegador. El panel no incluye transmisión al SRI.

## Contexto fiscal del tenant

Las operaciones existentes permiten crear contribuyentes, establecimientos y puntos de emisión con sus scopes respectivos. Introduce únicamente datos tributarios verificados. Crear estos recursos no autoriza por sí mismo la emisión fiscal ni resuelve la clasificación normativa del proveedor.

`GET /api/v1/taxpayers/{taxpayerId}/certificates` permite consultar exclusivamente UUID, huella, estado y caducidad con `certificates:read`. No devuelve archivos ni contraseñas.

## Cargar o rotar un certificado PKCS#12

`POST /api/v1/taxpayers/{taxpayerId}/certificates` admite multipart por HTTPS. Requiere `certificates:write`, pertenencia al tenant, contribuyente activo habilitado para TEST y activación explícita de la administración de certificados en el servidor.

| Campo | Contenido |
| --- | --- |
| `certificate` | Archivo `.p12`, máximo 1 MiB |
| `password` | Contraseña; sólo de entrada |
| `alias` | Opcional si hay una sola clave |
| `authorizationEvidence` | Evidencia previamente revisada, máximo 256 KiB |
| `authorizationReviewed` | `true`, declaración de revisión administrativa |

La API comprueba clave privada, RSA, vigencia, uso de firma, cadena de confianza, CRL vigente e identidad certificada por RUC. La CA y la CRL proceden de configuración del operador del servidor; el cliente no puede subir su propia CA para declararse confiable. Los formatos de identidad o delegaciones que el validador no reconoce se rechazan y requieren revisión específica; no se aceptan por coincidencia de nombres.

El archivo y la contraseña se cifran con AES-GCM utilizando la clave maestra externa de Doppler y datos asociados al certificado y contribuyente. Sólo se devuelve UUID, huella SHA-256 y fecha de caducidad. No existe descarga del `.p12` o su contraseña. La evidencia se conserva sólo como SHA-256: el operador debe conservar el documento revisado en un archivo privado de auditoría. Una declaración o hash no constituye por sí solo prueba jurídica.

Un nuevo registro válido selecciona el certificado activo del contribuyente para documentos futuros; no reescribe comprobantes existentes ni sus referencias históricas. No reactiva certificados revocados o deshabilitados.

`503` indica que falta confianza o CRL del servidor; `422`, que no se pudo establecer validez, confianza o identidad. No desactives estos controles para conseguir un alta. La carga no activa la firma ni transmite al SRI.

## Estado operativo

Los endpoints administrativos están publicados en staging con permisos por operación y tenant. El operador configuró confianza pública ANF para este despliegue; otras entidades emisoras requieren configuración y revisión específicas. La importación real requiere CA confiable y CRL vigente configuradas por el operador. Estos archivos públicos los emite la autoridad certificadora; no se generan sustitutos ni se necesita comprar otra firma para obtener los archivos públicos disponibles. La CRL debe renovarse antes de su vencimiento. Consulta la [referencia del contrato](/docs/api-reference/) y el [estado del proyecto](/project-status/). Las solicitudes interactivas de Scalar siguen desactivadas.
