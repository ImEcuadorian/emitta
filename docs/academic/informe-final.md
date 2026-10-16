# Diseño, implementación y evaluación de una API REST para la integración de facturación electrónica en Ecuador: Caso Emitta

## 1. Resumen
Emitta es un proyecto universitario que integra aplicaciones de negocio con la facturación electrónica ecuatoriana. Implementa una API REST multi-tenant, custodia cifrada de certificados, generación XML, firma XAdES-BES y persistencia de estados y artefactos. La demostración disponible corresponde exclusivamente a SRI TEST. La aceptación HTTP y la autorización fiscal son resultados diferentes.

## 2. Introducción y problema
Un sistema de ventas necesita transformar datos comerciales en comprobantes, gestionar numeración, firmar XML y conservar respuestas externas. Las interrupciones pueden dejar resultados inciertos: repetir una solicitud sin idempotencia puede duplicar operaciones. Emitta concentra estas responsabilidades en una API con trazabilidad durable.

## 3. Objetivos
Diseñar un contrato integrable, aislar organizaciones, impedir duplicación funcional, proteger material de firma y evaluar el comportamiento ante fallos y carga. El cierre académico conserva el alcance existente; no incorpora funcionalidades comerciales adicionales.

## 4. Justificación y modelo de negocio
El público hipotético comprende desarrolladores de POS, ERP y aplicaciones de comercio. La propuesta reduce trabajo repetido de integración fiscal. [RA1](RA1-negocio.md) desarrolla la cadena de valor y una monetización hipotética: no existen precios contratados, certificaciones ni promesas de capacidad comercial.

## 5. Arquitectura y patrones
Se utiliza un monolito modular con Ports & Adapters. REST/JSON y OpenAPI facilitan clientes heterogéneos; GraphQL y gRPC no aportan ventajas suficientes para este alcance. PostgreSQL conserva el estado definitivo; Redis controla tráfico; Outbox y RabbitMQ separan aceptación y procesamiento durable. Los confirms, leases, DLQ e idempotencia permiten recuperación con entrega al menos una vez. Resilience4j limita llamadas externas y los resultados de recepción ambiguos no se retransmiten automáticamente. Véanse los [diagramas y decisiones RA2](RA2-arquitectura.md).

## 6. Modelo de datos
Las migraciones V1–V16 definen organizaciones, clientes API, contribuyentes, establecimientos, puntos, secuencias, documentos, facturas, certificados, artefactos, autorización, Outbox y trabajos. V15 añade trabajos SRI_TEST sin activar su ejecución; V16 almacena la entrada personal del operador mediante hash. [RA3](RA3-modelo-contrato.md) contiene ERD y diccionario. Los constraints físicos y comprobaciones de pertenencia complementan JWT; no se afirma aislamiento mediante RLS.

## 7. Especificación OpenAPI
El [contrato completo](../../src/main/resources/static/openapi/emitta-openapi.yaml) contiene 20 rutas y 25 operaciones. La referencia pública de integración presenta 7 rutas y 9 operaciones, sin administración del proveedor. El cliente obtiene su token, configura o consulta su contexto fiscal, importa su certificado autorizado, crea una factura conservando Idempotency-Key y guarda el id devuelto. Con ese id consulta el documento y descarga artefactos disponibles. No necesita inventar UUID.

## 8. Implementación
Java 25, Spring Boot 4.1.1 y Gradle Kotlin implementan la API. DSS 6.5 genera XAdES-BES con referencia #comprobante; el resultado se valida antes de SIGNED. El XML es el documento firmado; el PDF RIDE es su representación y no se presenta como PDF firmado digitalmente. Astro, TypeScript, Starlight y Scalar generan una web estática. El panel del dueño separa clientes, credenciales, contexto fiscal y operación; no expone controles de carga P12 ni descargas fiscales de clientes.

## 9. Seguridad
JWT RS256, scopes, validación de tenant y revocación protegen las operaciones. La entrada personal usa contraseña hasheada y bloqueo temporal. AES-GCM protege PKCS#12 y contraseña; la clave maestra vive fuera de Git. Redis aplica límites compartidos y una degradación explícita. La API rechaza creación PRODUCTION y los adaptadores rechazan ese ambiente antes de red. Los tests SOAP utilizan simuladores locales. La política de proveedor permanece sin resolver y no se sustituye por un bypass global.

## 10. Pruebas funcionales y unitarias
La ejecución local de `build jacocoTestReport` terminó satisfactoriamente: 382 casos, 381 aprobados, 0 fallos y 1 prueba SRI live omitida. La suite cubre idempotencia, tenant/scopes, firma/XML, recuperación, Redis, RabbitMQ y SOAP simulado. Las 7 integraciones S3 pasaron con MinIO desechable el 16 de octubre, incluyendo el recorrido REST completo hasta XML/PDF con SOAP local simulado. Véase [storage-summary.json](evidence/storage-summary.json). La primera ejecución detectó tres fallos por dos fixtures que conservaban el SHA anterior del archivo sintético de evidencia tras normalizar sus finales de línea; se actualizaron al SHA verificado, sin modificar validaciones ni política del producto. La prueba SRI live permanece omitida deliberadamente: durante esta consolidación no se autoriza transmisión nueva.

## 11. Cobertura
JaCoCo 0.8.15 genera HTML y XML. La suite normal cubrió 80,91 % de instrucciones (25.225 de 31.175), 59,70 % de ramas (1.373 de 2.300) y 82,70 % de líneas (5.986 de 7.238). No incluye las integraciones S3 ejecutadas aparte ni representa cobertura completa de procesos hijos. La cobertura indica ejecución de código, no prueba de corrección fiscal completa. La suite normal registra 0 % de líneas en configuración/adaptador de almacenamiento, cuya tarea S3 se ejecuta aparte; también registra 0 % en el adaptador transaccional de recepción y 9,62 % en su configuración. Estas zonas limitan la cobertura y no se ocultan con exclusiones adicionales.

## 12. Pruebas de carga y spike
Los perfiles academic-sustained y academic-spike ejecutan únicamente contra Testcontainers. El primero utiliza 2 minutos de subida, 5 de meseta y 1 de bajada; el segundo pasa de 50 a 500 VUs y mantiene el pico 1 minuto. Cada ejecución verifica persistencia, idempotencia, eventos y drenaje. Firma, RIDE y SRI están apagados. Los resultados y umbrales incumplidos se conservan en [la tabla completa de mediciones](resultados-carga.md). El sostenido aceptó 26.382 documentos, 54,69 documentos/s y 164,07 solicitudes/s; p95 785,50 ms, p99 1.023,15 ms, cero errores HTTP y drenaje en 4,17 s. No se observaron pérdidas ni duplicados funcionales; no se alcanzó el ideal p95 <500 ms.

El spike de 500 VUs **falló el umbral p95<2000 ms**: registró p95 3.700,97 ms y p99 4.405,17 ms; k6 terminó con código 99. Conservó 5.083 documentos/eventos/jobs, cero errores HTTP, 100 % de checks e idempotencia y drenaje en 5,19 s. Hikari llegó a 192 solicitantes pendientes. No se observaron pérdidas, pero no se acredita soporte de 500 usuarios con la latencia requerida.

## 13. Análisis y punto de ruptura
La línea de base histórica de 100 VUs registró 54.535 documentos y p95 de 651,48 ms; el ensayo de 200 VUs incumplió criterios y no acredita esa capacidad. Los nuevos perfiles son experimentos distintos: el harness se ejecuta con JaCoCo 0.8.15 aplicado a la tarea Test y comparte un portátil Windows con Docker Desktop. La instrumentación y los recursos compartidos impiden atribuir diferencias de rendimiento únicamente al código. El objetivo ideal académico p95 inferior a 500 ms no debe confundirse con el umbral técnico del harness. No se extrapola throughput REST a autorizaciones SRI por segundo.

## 14. Despliegue Docker y staging
La publicación utiliza imágenes identificables y respaldo privado previo, preservando volúmenes. Cloudflare Tunnel proporciona acceso HTTPS sin abrir nuevos puertos. Sólo se actualizan Emitta API y Web; los servicios Nexorf se conservan. Los procesos fiscales automáticos permanecen desactivados.

## 15. Demostración TEST
Existe una factura TEST autorizada con XML y PDF. La demostración consulta y descarga esos artefactos mediante su cuenta autorizada; se comparan SHA-256 sin divulgar datos tributarios, certificado ni credenciales. No se genera otra factura para preparar la presentación.

## 16. Limitaciones
No hay operación en SRI PRODUCCIÓN, homologación comercial, política de proveedor resuelta ni procesamiento automático habilitado. Los ensayos de carga no miden capacidad de firma o SOAP real. No hay SLA, alta disponibilidad física demostrada, webhooks ni suscripciones. El alta de aplicaciones requiere intervención privada del dueño; no existe autorregistro público. La prueba SRI live sigue pendiente fuera de esta consolidación.

## 17. Conclusiones
El aporte verificable es una integración REST con aceptación durable, separación por tenant y recuperación explícita. La prueba TEST existente demuestra una ejecución concreta hasta artefactos; las pruebas sintéticas estudian fallos y carga sin comprometer datos reales. Las conclusiones de capacidad deben limitarse a los recursos y escenarios medidos.

## 18. Referencias y anexos
- [Índice de entrega RA1–RA4](README.md).
- [Plan reproducible k6](k6-plan-rubrica.md).
- [Migraciones](../../src/main/resources/db/migration/).
- [OpenAPI completo](../../src/main/resources/static/openapi/emitta-openapi.yaml).
- [ADR](../architecture/adr/README.md).
- [Política del proveedor y referencias oficiales](../provider-regulatory-policy.md).
- [Guion de exposición](guion-exposicion.md).

Anexos locales: [cobertura agregada](evidence/coverage.json), [guía en escritorio](evidence/docs-desktop-local.png), [guía móvil](evidence/docs-mobile-local.png) y [entrada del panel](evidence/admin-login-local.png). Las capturas corresponden al build local integrado, sin sesión ni datos privados.
