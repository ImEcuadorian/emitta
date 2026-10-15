# RA2 — Arquitectura de software y justificación de patrones

## 1. Problema arquitectónico y decisiones

Emitta debe recibir solicitudes REST desde múltiples clientes POS/ERP/SaaS, aislar la información fiscal de cada organización, evitar duplicados, proteger certificados y conservar el resultado aun ante interrupciones de red o servicios. El desarrollo académico necesita demostrar seguridad, diseño comprensible, calidad y despliegue sin introducir innecesariamente microservicios.

**Decisión documentada:** API REST versionada, monolito modular Spring Boot, PostgreSQL como fuente de verdad, comunicación asíncrona con RabbitMQ, Transactional Outbox, Redis para control de tráfico, Resilience4j para integraciones SOAP y S3-compatible para artefactos.

La presencia de elementos para futuros documentos fiscales o webhooks en el modelo no significa que todas sus operaciones estén habilitadas.

## 2. Selección del estilo de API

| Alternativa | Ventajas | Limitaciones en este caso | Decisión |
| --- | --- | --- | --- |
| REST/JSON + OpenAPI | HTTPS estándar; herramientas ampliamente disponibles; POST/GET y códigos HTTP claros | Requiere versionado e idempotencia explícita | **Elegida** |
| GraphQL | Selección flexible de campos; útil para lecturas complejas | Costo adicional de resolvers y gestión de operaciones fiscales; menor beneficio en un flujo command-heavy | No requerida |
| gRPC | Comunicación binaria tipada y eficiente | Añade complejidad de acceso para integradores heterogéneos y no sustituye el requisito OpenAPI | No requerida |

La API expone recursos bajo /api/v1. El frontend documental utiliza una referencia de integración reducida, mientras el contrato administrativo completo permanece versionado.

## 3. Diagrama de contexto (C4 nivel 1, simplificado)

~~~mermaid
flowchart LR
  I["Desarrollador / Sistema POS, ERP, SaaS"] -->|"JSON sobre HTTPS, JWT"| E["Emitta API"]
  O["Operador de Emitta"] -->|"Panel de administración"| W["Emitta Web"]
  W -->|"HTTPS autenticado"| E
  E -->|"SOAP fiscal solo bajo ejecución controlada TEST"| S["SRI — ambiente de certificación"]
  E -->|"XML y RIDE disponibles"| I
  classDef external fill:#f4f6fb,stroke:#607089,color:#152438
  class I,O,S external
~~~

**Límites de confianza:** Emitta no conoce por anticipado el estado final del SRI cuando responde HTTP 202. El documento se consulta posteriormente. La prueba fiscal TEST documentada fue manual/autorizada, no operación automática permanente.

## 4. Diagrama de contenedores desplegados (C4 nivel 2, simplificado)

~~~mermaid
flowchart TB
  U["Navegador / Integrador"] -->|"HTTPS"| CF["Cloudflare DNS + Tunnel"]
  CF --> WEB["Emitta Web (Astro estático / Nginx)"]
  CF --> API["Emitta API (Spring Boot 4, Java 25)"]
  WEB -->|"HTTPS con credenciales autorizadas"| API
  API --> PG[("PostgreSQL 18 — esquema emitta")]
  API --> REDIS[("Redis 8 — Token Bucket")]
  API --> MQ["RabbitMQ 4 — Exchange / DLQ"]
  MQ -. "Admisión durable; ejecución fiscal desactivada por defecto" .-> API
  API --> S3[("MinIO S3 — XML / PDF SHA-256")]
  API -. "SOAP manual TEST autorizado, nunca reenvío automático ambiguo" .-> SRI["SRI TEST"]
  classDef data fill:#f2f6fd,stroke:#64748b,color:#101828
  class PG,REDIS,S3 data
~~~

El despliegue real documentado usa **Cloudflare Tunnel hacia contenedores**, sin requerir que Caddy sea un salto de Emitta. Caddy puede estar ejecutándose para otros servicios Nexorf. PostgreSQL, RabbitMQ, Redis y MinIO no se exponen públicamente. El túnel, WAF y JWT cumplen objetivos distintos. La red, políticas y HTTPS deben respaldarse con capturas del entorno real para evaluación.

## 5. Diagrama de componentes internos (C4 nivel 3, vista funcional)

~~~mermaid
flowchart LR
  HTTP["Controllers REST + Validation + JWT/Scopes"] --> APP["Casos de uso: Invoice / Document / Tenant / Fiscal Setup"]
  APP --> DOMAIN["Dominio: snapshots fiscales, estados e idempotencia"]
  APP --> REPOS["Adapters JPA/JDBC / PostgreSQL"]
  APP --> OUTBOX["Outbox dentro de transacción"]
  OUTBOX --> MQ["Rabbit Publisher + Consumer"]
  MQ --> JOBS["Admisión de jobs durables"]
  JOBS --> XML["XML / XSD"]
  XML --> SIGN["Firma DSS XAdES / P12"]
  SIGN --> SOAP["Puertos recepción y autorización + Resilience4j"]
  SOAP --> ART["XML autorizado y RIDE + SHA-256"]
  ART --> OBJECT["S3-compatible"]
  HTTP --> RATE["Redis Token Bucket"]
~~~

La secuencia fiscal aparece como **capacidad implementada con ejecución automática deshabilitada por defecto**; no representa un pipeline que opere permanentemente en staging.

## 6. Secuencia: aceptación frente a autorización

~~~mermaid
sequenceDiagram
  participant C as POS/ERP
  participant A as Emitta API
  participant P as PostgreSQL
  participant O as Outbox/RabbitMQ
  participant F as Motor fiscal TEST
  participant S as SRI TEST
  C->>A: POST /api/v1/invoices + JWT + Idempotency-Key
  A->>P: Validar tenant, persistir factura y evento en transacción
  P-->>A: Commit
  A-->>C: 202 Accepted + documentId (no autorización)
  Note over O,F: Publicador y workers están apagados por defecto
  opt Prueba fiscal explícitamente autorizada
    O->>F: Trabajo único durable
    F->>S: XML XSD + firma XAdES (recepción única)
    S-->>F: RECIBIDA o error / incertidumbre
    F->>S: Consultar autorización misma clave
    S-->>F: AUTORIZADO / NO AUTORIZADO / pendiente
    F->>P: Estado y evidencias verificadas
  end
  C->>A: GET /api/v1/documents/{documentId}
  A-->>C: Estado persistido
  C->>A: GET artefacto si AUTHORIZED y disponible
  A-->>C: XML o PDF tenant-protegido
~~~

Si recepción devuelve timeout o resultado externo desconocido, **no debe retransmitirse automáticamente**. Se consulta autorización usando la misma clave de acceso.

## 7. Patrones de diseño y motivación

| Patrón | Implementación / papel | Problema que resuelve | Límite reconocido |
| --- | --- | --- | --- |
| API-first REST/OpenAPI | Contratos versionados, controllers y DTO | Integración entre tecnologías | Mantener código y contrato sincronizados |
| Monolito modular | Paquetes tenant, invoice, document, auth, fiscalprocessing, etc. | Mantener dominio separado con despliegue sencillo | No escalado independiente de cada módulo |
| Ports & Adapters | Casos de uso + interfaces + adaptadores JPA/SOAP/S3 | Separar dominio e infraestructura | Requiere gobernar dependencias |
| Tenant isolation | JWT tenant_id, scopes y comprobación por recurso | Evitar acceso transversal entre empresas | No afirmar RLS en PostgreSQL sin verificar políticas |
| Idempotency Key | Persistencia y restricciones de unicidad | Evitar facturas duplicadas por reintento | Un timeout HTTP no demuestra rollback |
| Transactional Outbox | Evento creado junto al documento | Evitar pérdida entre commit DB y broker | Entrega al menos una vez; consumidor idempotente |
| Worker durable | processing_jobs y journal | Recuperar trabajo tras reinicio | Requiere leases, control de estados y presupuesto DB |
| DLQ | RabbitMQ y revisión por operador | Aislar fallos permanentes | No hacer replay fiscal automático |
| Token Bucket Redis | Políticas atómicas por IP/cliente/tenant/ruta | Limitar abuso compartido entre instancias | Redis caído puede devolver 503 |
| Circuit Breaker / Bulkhead | Protecciones separadas del cliente SOAP | Contener servicio externo lento/caído | No cambia semántica de recepción fiscal |
| Object Storage + SHA-256 | XML/RIDE en S3, metadatos PostgreSQL | Integridad y separación de blobs | Hash no prueba validez jurídica |
| Reverse proxy / Tunnel | Cloudflare Tunnel hacia origen privado | HTTPS externo sin abrir puertos fiscales | No es un API Gateway con transformación funcional |
| Paginación | Listados operativos/administrativos | Límites de respuesta y navegación | Hay recursos aún sin búsqueda avanzada |

**No implementados como servicios productivos:** API Gateway dedicado, BFF independiente, Kubernetes, autoescalado, webhooks o suscripciones. No atribuirles el resultado de rendimiento.

## 8. Seguridad y modelo de amenazas abreviado

- **Robo de credenciales:** secretos de API cifrados en tránsito, hash BCrypt para autenticación; el secreto sólo se muestra en la creación/rotación.
- **Acceso cruzado entre tenants:** validar pertenencia del recurso y scopes en la API, no confiar en tenantId del JSON.
- **Manipulación de certificado:** multipart limitado, validación PKCS#12/PKIX/CRL, custodia cifrada AES-GCM y claves externas a Git.
- **Reenvíos duplicados:** idempotencia PostgreSQL, Unique Keys, conciliación por clave de acceso y recepción sin retry automático.
- **Saturación de API:** Redis y backpressure antes del commit; HTTP 429/503 con semántica conocida.
- **Exposición de administración y métricas:** permisos administrativos diferenciados; Actuator restringido; no exponer DB/RabbitMQ/MinIO.
- **Acceso público a información fiscal:** XML/RIDE descargados con autenticación y autorización; integridad comprobada.
- **Riesgo operativo:** TEST y PRODUCCIÓN separados; automático apagado por defecto. Fallos de red y pérdida física no equivalen a pruebas de HA.

## 9. Decisiones tecnológicas que no se adoptaron

**Microservicios desde el inicio:** elevarían redes, consistencia, despliegue y depuración sin un límite de escala medido que lo justifique. Se eligió un único backend modular.  
**Gateway independiente:** Cloudflare/Tunnel realizan ingreso y protección perimetral; el gateway con funciones de negocio no es requisito del MVP.  
**BFF separado:** el frontend Astro actual consume servicios protegidos; no se añadió una capa intermedia únicamente por moda. Para sesiones humanas o secretos de navegador deberá revisarse un BFF de seguridad si se requiere.  
**Kafka:** RabbitMQ resuelve el flujo de trabajo y confirmaciones; no se necesita replay de flujos analíticos a gran escala para RA2.

## 10. Evaluación crítica de la arquitectura

El diseño resiste caídas simuladas y ofrece evidencia de integridad, pero el flujo fiscal completo no estuvo activo durante el ensayo k6. La prueba sostenida de 200 VUs falló. La política de proveedor fiscal y la operación continua de un sistema comercial siguen sin resolverse. Estos puntos deben mencionarse en la exposición.

### Fuentes y evidencia

- [ADR-001 REST](../architecture/adr/ADR-001-api-first-rest-openapi.md), [ADR-002 monolito](../architecture/adr/ADR-002-modular-monolith.md), [ADR-006 RabbitMQ](../architecture/adr/ADR-006-rabbitmq-async-processing.md), [ADR-007 Outbox](../architecture/adr/ADR-007-transactional-outbox.md).
- [Arquitectura del proyecto](../architecture/README.md).
- [OpenAPI versionado](../../src/main/resources/static/openapi/emitta-openapi.yaml).
- [Entrega staging](../operations/emitta-publication.md).
- [Pruebas de admisión](../operations/admission-capacity-stage5.md).

### Conclusión RA2

La arquitectura seleccionada equilibra requisitos de integración, seguridad, consistencia y costo operativo. Los patrones se justifican por riesgos observables; no por la cantidad de tecnologías instaladas.
