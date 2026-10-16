# RA1 — Justificación de negocio y visión del producto

## 1. Resumen ejecutivo

**Nombre:** Emitta (iniciativa tecnológica de NEXORF).
**Naturaleza:** Plataforma B2B de API REST para integrar facturación electrónica ecuatoriana con software existente.
**Etapa actual:** Desarrollo y staging académico; sin comercialización ni habilitación fiscal automática de producción.

**Propuesta de valor:** permitir que un proveedor de software POS, ERP, e-commerce o SaaS incorpore emisión electrónica sin implementar individualmente toda la lógica de validación del comprobante, firma digital, comunicación con el SRI, trazabilidad, reintentos seguros y almacenamiento de artefactos. La integración se realiza mediante JSON/HTTPS con autenticación y consulta posterior de estado.

Emitta no pretende sustituir el POS ni ERP del integrador, sino complementarlo como componente fiscal. La aceptación HTTP de la API y la autorización tributaria son hechos distintos.

## 2. Necesidad identificada

Quienes mantienen aplicaciones de ventas enfrentan obligaciones técnicas y de mantenimiento: convertir operaciones de negocio en XML conforme al esquema fiscal, custodiar certificados PKCS#12, firmar electrónicamente, comunicarse con servicios del SRI, reconocer fallas ambiguas, almacenar XML autorizado y proporcionar RIDE. Cada aplicación que replica esta lógica aumenta el esfuerzo de integración y soporte.

El usuario objetivo no es necesariamente quien compra un producto en caja: es **el equipo que construye o mantiene el sistema que factura**.

## 3. Objetivo general y objetivos específicos

**General:** diseñar, implementar y evaluar una API REST segura y documentada para integrar sistemas empresariales con la emisión de facturas electrónicas en el ambiente de pruebas del SRI.

**Específicos:**
1. Ofrecer operaciones versionadas de autenticación, configuración fiscal, emisión, estado y descarga.
2. Aislar los datos entre organizaciones (tenants), validar permisos y proteger certificados.
3. Procesar documentos con integridad, idempotencia y trazabilidad.
4. Evaluar capacidad, latencia, recuperación y tolerancia a fallos con datos sintéticos.
5. Publicar una demostración controlada y una documentación para desarrolladores.

## 4. Segmentación de clientes

| Segmento | Problema habitual | Valor ofrecido por la API |
| --- | --- | --- |
| Desarrolladores de POS | Integración fiscal para cada instalación | Una interfaz de emisión y consulta reutilizable |
| Proveedores de ERP | Mantener formatos y estados fiscales | Separación de lógica comercial y fiscal |
| Plataformas SaaS | Gestionar clientes organizacionales | Credenciales independientes y aislamiento multi-tenant |
| E-commerce | Asociar pedidos con comprobantes | Idempotencia por operación e historial de estados |
| Integradores de software | Reducir implementación específica | OpenAPI, ejemplos y contrato versionado |

No se atribuyen clientes reales, cuotas de mercado ni ingresos actuales a Emitta.

## 5. Cadena de valor de la API

| Actor | Aporta | Obtiene |
| --- | --- | --- |
| Cliente integrador (POS/ERP/SaaS) | Datos de factura y autenticación | Interfaz HTTP, identificador del documento y resultados |
| Emitta (proveedor tecnológico propuesto) | Validación, motor fiscal, seguridad, monitoreo y conservación | Potencial ingreso por servicio/consumo |
| Contribuyente emisor | Datos fiscales y certificado autorizados | Comprobante firmado y resultado fiscal correspondiente |
| SRI | Servicios fiscales de recepción y autorización | Documento sometido a controles y respuesta fiscal |
| Comprador final | Datos de identificación cuando corresponda | Comprobante y representación legible entregados por el emisor/integrador |

**Flujo de valor:** Integrador → petición REST → validación y persistencia → firma/servicio SRI TEST (bajo operación autorizada) → estado y artefactos XML/RIDE → integrador/receptor.

Las etapas fiscales automáticas permanecen apagadas en staging, por lo que este flujo completo es la visión del servicio, no una promesa de operación continua.

## 6. Diferenciación y competencia

Existen soluciones de facturación electrónica e integración en el mercado. Emitta **no es una categoría inédita**. La hipótesis de posicionamiento es especializar la experiencia en desarrolladores ecuatorianos: contrato REST claro, separación de credenciales por organización, trazabilidad e idempotencia y guía de integración local.

Antes de afirmar superioridad competitiva se requiere una comparación verificable de proveedores específicos con dimensiones equivalentes: documentación, precio vigente, tiempos de integración, tipos de comprobantes, disponibilidad, soporte y obligaciones legales. No se declara que Emitta sea más barato, rápido o certificado.

## 7. Modelo de negocio propuesto (no comercializado)

Modelo B2B SaaS **hipotético** con suscripción y/o facturación por volumen de documentos correctamente procesados, no por cantidad bruta de solicitudes HTTP. Los reintentos idempotentes no deberían producir cargos duplicados.

| Oferta hipotética | Clientes | Mecanismo de ingreso |
| --- | --- | --- |
| Sandbox de evaluación | Desarrolladores | Acceso controlado, límites razonables, sin comprobantes de producción |
| Integración inicial | POS o pequeña SaaS | Suscripción mensual y volumen incluido |
| Integración de mayor escala | ERP/SaaS multiempresa | Tarifa por uso y condiciones de soporte |
| Proyecto empresarial | Integradores especializados | Contrato y soporte acordados |

**Escenario numérico ilustrativo; no es precio anunciado, forecast validado ni ingresos existentes:**

- Supuesto: 20 integradores en un plan de USD 29/mes con 300 documentos mensuales por integrador.
- Ingresos brutos hipotéticos: 20 × 29 = **USD 580/mes**.
- Costos operativos fijos supuestos: **USD 250/mes**.
- Costo variable supuesto: USD 0,01 por documento; 20 × 300 × 0,01 = **USD 60/mes**.
- Contribución simplificada hipotética: 580 − 250 − 60 = **USD 270/mes** antes de impuestos, salarios, adquisiciones y costos legales.
- Punto de equilibrio simplificado para ese mix: 250 ÷ (29 − 300 × 0,01) = 9,62, es decir, **10 integradores**, si todos los supuestos se cumplieran.

Esta simulación no incorpora inversión inicial, retención de clientes, atención a incidentes, mantenimiento regulatorio, infraestructura de alta disponibilidad, tasas fiscales, comisiones ni requisitos del proveedor. En consecuencia **no acredita viabilidad financiera real**.

## 8. Indicadores del producto propuestos

- Tiempo hasta primera solicitud API autenticada.
- Porcentaje de integraciones que completan una factura de prueba.
- Tasa de operaciones idempotentes sin duplicados funcionales.
- Porcentaje de documentos con estado/artefactos disponibles.
- Tiempo hasta conciliación de un resultado fiscal incierto.
- Facturas fiscalmente autorizadas por período (diferentes de HTTP 202).
- Costos variables por documento y soporte por cliente.

No hay datos comerciales suficientes para medir actualmente estos indicadores.

## 9. Alcance del MVP académico

**Verificable:** autenticación JWT de aplicaciones, tenants y scopes, configuración fiscal, importación de certificados controlada, API para creación idempotente, estados, descargas de XML/RIDE autorizados, pruebas automatizadas, pruebas de carga de admisión, interfaz documental, despliegue staging.

**Evidencia operativa reportada:** una autorización TEST controlada y descarga protegida de XML/RIDE. No equivale a automatización fiscal global ni aprobación regulatoria.

**Fuera del MVP:** facturación de producción, registro público de clientes, cobros recurrentes, planes activos, webhooks, email transaccional, todos los tipos de comprobantes y certificación comercial.

## 10. Riesgos del modelo

Dependencia de la normativa y disponibilidad de servicios SRI, seguridad y vigencia de certificados, continuidad de Internet/servidor, gestión de información tributaria y resolución de la política de proveedor. La evidencia de un comprobante TEST autorizado no demuestra cumplimiento de las obligaciones de un proveedor comercial.

## 11. Conclusión RA1

Emitta crea valor potencial al convertir una integración fiscal técnicamente especializada en una API de uso repetible. El negocio es una **hipótesis académica** que necesitaría validación comercial y legal. Su contribución demostrable en esta asignatura es el diseño, la implementación y la evaluación técnica de esa API.

### Fuentes y evidencias

- [Sitio de Emitta](https://emitta.nexorf.dev).
- [ADR-001: REST y OpenAPI](../architecture/adr/ADR-001-api-first-rest-openapi.md).
- [Contrato de API](../../src/main/resources/static/openapi/emitta-openapi.yaml).
- [Política normativa todavía sin resolver](../provider-regulatory-policy.md).
- [Servicio de Rentas Internas: facturación electrónica](https://www.sri.gob.ec/facturacion-electronica).

Los valores financieros son supuestos didácticos del autor y requieren investigación de mercado si se quisiera vender el servicio.
