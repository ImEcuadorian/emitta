# Emitta — API de facturación electrónica para Ecuador

> **Proyecto académico en STAGING / SRI TEST.** La API es un servicio de integración para sistemas POS, ERP, SaaS y e-commerce. No es un POS, un ERP, un servicio comercial certificado ni un ambiente de emisión de PRODUCCIÓN.

**Sitio web:** https://emitta.nexorf.dev
**API de pruebas:** https://api-emitta.nexorf.dev
**Documentación:** https://emitta.nexorf.dev/docs/
**Repositorio de la web:** https://github.com/ImEcuadorian/emitta-web

## Entregables de la Universidad Politécnica Salesiana

La entrega final RA1–RA4 tiene un índice, informes completos, diagramas, contrato, pruebas y guion de exposición:

### [Ver los entregables académicos (RA1–RA4)](docs/academic/README.md)

| Resultado | Evidencia |
| --- | --- |
| RA1 — Negocio | [Visión, cadena de valor y monetización propuesta](docs/academic/RA1-negocio.md) |
| RA2 — Arquitectura | [Diagramas C4/flujo y patrones explicados](docs/academic/RA2-arquitectura.md) |
| RA3 — Diseño API | [ERD, diccionario y OpenAPI](docs/academic/RA3-modelo-contrato.md) |
| RA4 — Desarrollo | [Seguridad, pruebas, carga y despliegue](docs/academic/RA4-desarrollo-pruebas.md) |
| Presentación | [Guion de demostración](docs/academic/guion-exposicion.md) |
| Rúbrica | [Matriz de requisitos y evidencias](docs/academic/matriz-rubrica.md) |

## ¿Qué hace Emitta?

1. Autentica aplicaciones mediante credenciales de cliente y JWT RS256.
2. Mantiene separadas las organizaciones (tenants), emisores y credenciales.
3. Permite preparar un contribuyente, establecimiento, punto de emisión y certificados P12 bajo autorización.
4. Recibe solicitudes de factura con Idempotency-Key y devuelve un identificador y estado.
5. Conserva estados y artefactos fiscalmente autorizados para consulta y descarga privada XML/RIDE.
6. Cuenta con generación XML/XSD, firma XAdES, adaptadores de SRI TEST, RabbitMQ/Outbox, Redis y recuperación; la **ejecución fiscal automática está deshabilitada por defecto**.

**Precisión fiscal:** HTTP 202 no significa autorización del SRI. Existe evidencia reportada de una **prueba TEST manual autorizada** con XML/RIDE verificados; esa prueba no valida operación comercial continua ni autoriza retransmisiones automáticas. PRODUCCIÓN permanece fuera del alcance.

## Arquitectura

~~~mermaid
flowchart TB
  POS["POS / ERP / SaaS"] -->|"JWT y JSON / HTTPS"| EDGE["Cloudflare Tunnel"]
  EDGE --> API["API REST — Java 25 / Spring Boot 4"]
  EDGE --> WEB["Web y documentación — Astro"]
  API --> PG[("PostgreSQL — fuente de verdad")]
  API --> REDIS[("Redis — rate limiting")]
  API --> MQ["RabbitMQ — Outbox / DLQ"]
  API --> MINIO[("S3/MinIO — XML y RIDE")]
  API -. "Envíos manuales TEST autorizados" .-> SRI["SRI TEST"]
~~~

La API es un **monolito modular**, no un conjunto de microservicios. El diseño y las decisiones se justifican en [RA2](docs/academic/RA2-arquitectura.md) y en los [ADR](docs/architecture/adr/README.md).

## Stack

Java 25 · Spring Boot 4.1.1 · Gradle Kotlin DSL · PostgreSQL 18/Flyway · Redis 8 · RabbitMQ 4 · JWT/Spring Security · DSS/XAdES · PDFBox · MinIO/S3 · Resilience4j · Testcontainers · k6 · Docker/Cloudflare Tunnel.

## Contrato OpenAPI

- [OpenAPI 3.0.3 completo (administrativo y de integración)](src/main/resources/static/openapi/emitta-openapi.yaml).
- [Referencia simplificada para integradores](https://github.com/ImEcuadorian/emitta-web/blob/main/public/openapi/emitta-integration-v1.yaml).
- En la rama de consolidación verificada: **20 rutas / 25 operaciones**, con OAuth2 Resource Server para validación JWT pero sin afirmar un Authorization Server OAuth2 completo.
- Todas las operaciones fiscales reales requieren credenciales, scopes y pertenencia al tenant.

## Ejecutar pruebas

El proyecto requiere Java 25, Docker funcional y herramientas de ejecución Gradle/PowerShell si se utiliza Windows.

~~~powershell
.\gradlew.bat clean build --console=plain
.\gradlew.bat test jacocoTestReport --console=plain
.\scripts\Test-IsolatedStorage.ps1
~~~

La suite normal **no ejecuta** la prueba real del SRI. No se envían facturas a producción.

Para la rúbrica académica de carga, **solo contra Testcontainers sintéticos**, después de revisar CPU/RAM:

~~~powershell
.\scripts\Test-OperationalLoad.ps1 -Profile academic-sustained -Vus 100 -Duration 5m -AdmissionConcurrency 1
.\scripts\Test-OperationalLoad.ps1 -Profile academic-spike -Vus 500 -Duration 1m -DimensionedSpike -AdmissionConcurrency 1
~~~

JaCoCo genera build/reports/jacoco/test; los ensayos k6 agregan resultados locales bajo build/operational/load. Los resultados nuevos solo se declaran después de ejecutar las tareas. El ensayo de 200 VUs anterior **falló** criterios de latencia/replays; no se acredita una capacidad comercial.

## Staging y seguridad

- El despliegue doméstico se documenta en [operaciones](docs/operations/emitta-publication.md); no requiere puertos fiscales públicos.
- Los certificados/contraseñas se gestionan con cifrado y nunca deben agregarse a Git.
- Los procesos automáticos fiscales están apagados por defecto; la política jurídica del proveedor sigue [sin resolver](docs/provider-regulatory-policy.md).
- No ejecutar k6 ni escáneres intrusivos contra el sitio público, la API staging, el SRI o la base privada del piloto.
- [Matriz de evidencias y límites de la entrega](docs/academic/matriz-rubrica.md).

## Consolidación universitaria

Los cambios locales y los entregables académicos se integran preservando el historial. La evidencia exacta de validación y publicación se encuentra en [el informe final](docs/academic/informe-final.md).

**Proyecto:** NEXORF · Emitta.
