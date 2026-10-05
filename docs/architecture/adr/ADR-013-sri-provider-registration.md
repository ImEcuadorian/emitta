# ADR-013: Registrar e identificar a Emitta como proveedor de facturación electrónica

- Estado: Accepted
- Fecha: 2026-10-04
- Decisores: Equipo Emitta

## Contexto

Emitta está concebido como una plataforma B2B que presta infraestructura de facturación electrónica a terceros, como POS, ERP, SaaS, e-commerce y aplicaciones empresariales.

En 2026 el SRI estableció requisitos formales para los proveedores de sistemas informáticos o servicios de facturación electrónica. La regulación exige que estos proveedores incorporen en su RUC un establecimiento y una actividad económica exclusivos para esta actividad. Además, cuando un emisor utilice un sistema provisto por un tercero, el comprobante deberá incluir el RUC del proveedor en la información adicional conforme a la ficha técnica vigente.

Esto introduce dos identidades distintas dentro del dominio de Emitta:

1. El contribuyente que emite el comprobante.
2. El proveedor tecnológico que presta el servicio de facturación.

Estas identidades no deben confundirse.

## Decisión

Emitta modelará la identidad del proveedor de facturación de forma separada de los contribuyentes pertenecientes a cada tenant.

Modelo conceptual:

```text
Platform
└── ProviderIdentity
    ├── providerRuc
    ├── legalName
    ├── establishment
    ├── economicActivity
    └── regulatoryStatus
```

Los datos del emisor permanecerán en el contexto del tenant:

```text
Tenant
└── Taxpayer
    ├── ruc
    ├── legalName
    ├── establishments
    ├── pointsOfIssue
    ├── certificates
    └── documents
```

El RUC del proveedor será configuración controlada por la plataforma y no podrá ser enviado o reemplazado libremente por los clientes de la API.

Cuando la ficha técnica vigente del SRI lo exija, Emitta incorporará el RUC registrado del proveedor en el comprobante electrónico.

## Razones

- Evitar confundir el RUC del emisor con el RUC del proveedor tecnológico.
- Cumplir con el registro formal exigido por el SRI.
- Evitar que un cliente suplante o reemplace la identidad del proveedor.
- Centralizar información regulatoria de la plataforma.
- Mantener separados los datos de plataforma y los datos multi-tenant.
- Reflejar correctamente el modelo de negocio B2B de Emitta.

## Alternativas consideradas

### Guardar el RUC del proveedor dentro de cada tenant

Ventajas:

- Acceso directo desde los flujos del tenant.

Desventajas:

- Duplica información de plataforma.
- Puede generar valores inconsistentes entre tenants.
- Permite modificaciones indebidas.
- Modela incorrectamente un dato de plataforma como si perteneciera al cliente.

Rechazada.

### Permitir que cada solicitud envíe `providerRuc`

Ventajas:

- Flexible.

Desventajas:

- Riesgo de suplantación.
- Reduce auditabilidad.
- Es innecesario porque la identidad del proveedor ya es conocida por Emitta.

Rechazada.

### Hardcodear el RUC en el código Java

Ventajas:

- Implementación trivial.

Desventajas:

- Mezcla configuración regulatoria con código fuente.
- Dificulta cambios entre ambientes.
- No es apropiado para producción.

Rechazada.

## Consecuencias

### Positivas

- Separación clara entre emisor y proveedor.
- Consistencia de la identidad del proveedor.
- Mayor auditabilidad.
- Menor riesgo de manipulación desde clientes externos.
- Facilita cambios regulatorios.

### Negativas

- Emitta debe gestionar su propia configuración regulatoria.
- La preparación para producción incluye un proceso externo de registro ante el SRI.
- Debe existir validación de configuración por ambiente.

## Consideraciones de seguridad

El RUC del proveedor no necesariamente es secreto, pero sí es configuración confiable de plataforma.

Solo procesos administrativos autorizados podrán modificar la identidad del proveedor.

La API no deberá aceptar una identidad de proveedor suministrada por el cliente como fuente de verdad.

## Implicaciones de dominio

El modelo deberá distinguir:

```text
ProviderIdentity
Taxpayer
Tenant
```

`ProviderIdentity` pertenece a la plataforma Emitta.

`Taxpayer` pertenece a un tenant y representa al emisor legal del comprobante.

## Configuración inicial

La configuración de producción podrá incluir:

```text
EMITTA_PROVIDER_RUC
EMITTA_PROVIDER_LEGAL_NAME
EMITTA_PROVIDER_ESTABLISHMENT
```

La configuración de plataforma será administrada mediante Doppler.

## Riesgos

### Cambios regulatorios

El SRI puede modificar:

- requisitos de registro;
- campos obligatorios;
- estructura de información adicional;
- plazos de cumplimiento.

Mitigación:

- aislar `ProviderIdentity`;
- mantener pruebas de regresión sobre XML;
- revisar la ficha técnica vigente antes de cada release fiscal relevante.

### Información del proveedor incorrecta

Mitigación:

- configuración controlada;
- validación al arranque;
- pruebas de integración;
- checklist de despliegue.

## Evolución futura

Se deberá crear un nuevo ADR si Emitta llega a soportar:

- múltiples proveedores registrados;
- white-label;
- resellers;
- selección de proveedor por tenant.

La arquitectura inicial asume una sola identidad de proveedor Emitta por despliegue/ambiente.

## Referencias

- Resolución SRI NAC-DGERCGC26-00000027.
- Comunicado del SRI del 28 de julio de 2026 sobre registro de proveedores de sistemas o servicios de facturación electrónica.
- Ficha técnica vigente de comprobantes electrónicos del SRI.
