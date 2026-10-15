# RA4 — Implementación, seguridad, pruebas y despliegue

## 1. Implementación efectivamente existente

La solución implementada utiliza Java 25, Spring Boot 4.1.1, PostgreSQL 18, Redis 8, RabbitMQ 4, almacenamiento S3 compatible, firma DSS/XAdES, OpenAPI, Testcontainers, k6 y Docker. La web pública se implementa con Astro, Starlight y Scalar y se entrega como sitio estático.

**Código fuente:** [Emitta backend](https://github.com/ImEcuadorian/emitta/tree/codex/ride-consolidation) y [Emitta Web](https://github.com/ImEcuadorian/emitta-web/tree/codex/ride-consolidation).  
**Despliegue staging reportado:** https://emitta.nexorf.dev y https://api-emitta.nexorf.dev. El despliegue se documenta en [emitta-publication.md](../operations/emitta-publication.md); capturas/HTTP actuales deben generarse desde el entorno del responsable.

## 2. Seguridad

| Requisito | Implementación y comprobación |
| --- | --- |
| Autenticación | Credenciales de aplicación; JWT RS256 con expiración, issuer/audience |
| Autorización | Scopes, cliente activo y pertenencia del recurso al tenant |
| Multi-tenancy | Asociaciones DB, validación de recurso/tenant; no declarar RLS sin pruebas de políticas SQL |
| Idempotencia | Idempotency-Key obligatorio y unicidad persistida |
| Certificados | PKCS#12 validado, custodia AES-GCM, claves privadas fuera de logs/Git |
| Control de tráfico | Token Bucket Redis, 429 con Retry-After y 503 ante indisponibilidad |
| Seguridad de documentos | Descarga autorizada con SHA-256; XML firmado y RIDE sin URLs públicas |
| Resiliencia | RabbitMQ con confirms, DLQ, Outbox, backpressure y jobs durables; CB/Bulkhead para SOAP |
| Entorno académico | SRI PRODUCCIÓN deshabilitado, procesamiento automático desactivado, solo TEST controlado |

Se reportaron pruebas de aislamiento, revocación de cliente, fallos, duplicados, confirma/retorno y resistencia ante caída de dependencias. **OAuth2 completo no debe afirmarse solo porque se empleen JWT y dependencias OAuth2 Resource Server**: RA4 admite JWT.

## 3. Calidad: pruebas automatizadas

El informe de consolidación local 11-10-2026 registra **358 pruebas aprobadas y una SRI live omitida** (352 de suite general + 6 de MinIO aislado). El informe posterior de publicación staging registra **359 aprobadas y una live omitida** en su suite general, más comprobaciones focalizadas (no sumarlas como únicas).

El reporte del 14-10-2026 describe nuevas suites tras V15/V16; estas modificaciones no constan completas en el snapshot remoto base examinado. **Antes de entregar, correr pruebas sobre el commit final y reportar el total exacto de esa ejecución.** El examen de GitHub no sustituye un build ejecutado.

Módulos cubiertos: invoice/idempotency, JWT/tenant/scopes, Redis, RabbitMQ, recuperación, XML/XSD, firma, S3/MinIO, RIDE y errores SOAP.

Comandos en Windows para evidencia:

~~~powershell
.\gradlew.bat clean build --console=plain
.\scripts\Test-IsolatedStorage.ps1
~~~

Docker debe estar activo; los tests son aislados. El test SriSoapAuthorizationLiveTest debe permanecer omitido salvo autorización específica. Nunca usar el piloto ni SRI como carga.

## 4. Cobertura de código

La rúbrica requiere evidencia de pruebas unitarias. **El porcentaje de cobertura no está documentado todavía**. Se añadió la preparación para reporte JaCoCo de JDK 25 en el branch académico, pero el HTML/XML y su porcentaje solo existen después de ejecutar la tarea.

~~~powershell
.\gradlew.bat test jacocoTestReport --console=plain
~~~

Artefactos esperados: build/reports/jacoco/test/html/index.html y build/reports/jacoco/test/jacocoTestReport.xml. Documentar cobertura de instrucciones y ramas observada, componentes más débiles y exclusiones, sin fabricar porcentajes.

Si se requieren agregaciones de Testcontainers/S3 ejecutadas fuera de test, documentarlas por separado; no sumar dos veces cobertura ni invocar jaCoCo si el plugin no se ha integrado en la rama local sincronizada.

## 5. Resultados de rendimiento ya medidos (línea de base)

**Ensayo B optimizado — 100 usuarios virtuales, 10 minutos, ambiente Testcontainers sintético:**

| Métrica | Resultado publicado en informe |
| --- | ---: |
| Documentos originales persistidos | 54.535 |
| Documentos originales/s | 90,47 |
| HTTP p95 | 651,48 ms |
| HTTP p99 | 998,28 ms |
| Errores HTTP | 0 % |
| Checks | 100 % |
| Admisión/replays/drenaje | Consistentes, backlog final 0; drenaje 1,14 s |
| Hikari max | 10 conexiones |

**No equivale a 90 facturas SRI autorizadas/s.** El ensayo mide HTTP + DB/Outbox/consumo durable con firma, RIDE y SRI apagados. HTTP agrupa creación, replay y GET. El throughput aquí es **documentos/s**, no peticiones/s.

**Ensayo B — 200 usuarios virtuales, 10 minutos:** 22.066 documentos, 36,43 docs/s; p95 3.787,68 ms; p99 6.487,63 ms; errores HTTP 0,238 %, idempotency/checks incompletos. La prueba **falló** sus umbrales; hubo 63 documentos persistidos sin acuse de respuesta HTTP. El conteo durable no reveló duplicados funcionales; se debe repetir con la misma clave cuando el resultado HTTP es incierto.

**Spike existente:** alcanzó 500 VUs en un escenario de aproximadamente 100 segundos; meseta predeterminada de 30 segundos. No cubre la rúbrica actual de mantener 1–2 minutos el pico.

### Gráfica de comparación p95

~~~mermaid
xychart-beta
  title "Latencia HTTP p95 observada (ms), ensayos B"
  x-axis ["100 VUs", "200 VUs"]
  y-axis "milisegundos" 0 --> 4000
  bar [651.48, 3787.68]
~~~

Estos ensayos se obtuvieron en condiciones y fechas distintas; la ejecución tiene variabilidad. No usar un p95 de 651 ms como si cumpliera el ideal de 500 ms de la rúbrica.

## 6. Ajuste necesario a los escenarios del docente

| Requisito del docente | Evidencia disponible | Acción final |
| --- | --- | --- |
| Ramp-up 2–3 min, 100–200 VUs | Actual: constant-vus | Ejecutar perfil academic-sustained nuevo |
| Meseta 5–10 min | 100 VUs 10 min validado | Mantener 5–10 min |
| Ramp-down 1–2 min | No existe en sostenido anterior | Incorporado al perfil académico; ejecutar |
| Spike 5–10× y 500 VUs | Pico de 500 hecho | Mantener 500 y documentar baseline |
| Spike sostenido 1–2 min | Viejo plateau 30 s | Ejecutar nuevo perfil academic-spike |
| RPS, promedio, p90, p95, p99 | Informes contienen docs/s, p95/p99 | Exportar http_reqs y Trend completo del nuevo ensayo |
| Error rate <1 % sostenido | 0 % en B 100; 200 falla otras métricas | Reportar por escenario sin ocultar fallas |
| Punto de ruptura | 100 pasa, 200 falla | Intervalo observado (100,200]; localizar mejor con 125/150/175 si se requiere exactitud |

La métrica p95 <500 ms se describe como ideal para endpoints síncronos estándar. Un contrato asíncrono puede tener una latencia diferente, pero **eso no elimina el requisito de explicar por qué se supera el ideal**.

Los perfiles nuevos se limitan a infraestructura Testcontainers sintética (nunca una URL arbitraria), preservan el rate limiter de pruebas y mantienen OFF los puertos fiscales.

## 7. Despliegue Docker

**Despliegue reportado en staging:** Cloudflare/Tunnel hasta la web Astro/Nginx y API Spring Boot, con Compose independiente, PostgreSQL, Redis, RabbitMQ y MinIO; secretos inyectados sin publicarse, TLS en el perímetro, backups y Flyway sobre la base staging. El entorno del piloto quedó separado.

Presentar como evidencia real:
1. Captura de la página /docs/ sobre HTTPS (sin tokens).
2. Captura de /actuator/health sin detalles sensibles.
3. docker compose ps del proyecto staging, ocultando secretos y recursos ajenos.
4. Captura de Scalar/OpenAPI del backend alineada con el commit entregado.
5. Resumen de tests Gradle y JaCoCo.
6. Pantalla del panel privado con identidades personales censuradas.
7. XML/RIDE TEST existentes usando metadatos no sensibles y hash, sin publicar el documento o certificado real.

No adjuntar salida de variables de entorno, P12, tokens, XML de contribuyentes, datos del piloto ni archivos de operación privados.

## 8. Pipeline DevOps

La automatización GitHub Actions para **validar builds y cobertura** puede añadirse a esta rama; el job debe ejecutarse y quedar verde antes de atribuir CI exitoso. Docker/Testcontainers necesita runner compatible.

Desplegar staging desde CI de forma automática no es obligatorio para acreditar **despliegue contenedorizado** si ya existe evidencia de Compose ejecutándose. Un pipeline de prueba y un despliegue real verificado pueden presentarse por separado; no inventar automatización de releases.

## 9. Punto de ruptura y discusión

La línea de base original muestra que al duplicar concurrencia de 100 a 200 VUs aumentan las colas y la latencia, mientras disminuye la admisión útil. Con los datos disponibles se identifica el **intervalo de degradación entre 100 y 200 VUs**, pero no un umbral exacto, porque sólo se midieron esos niveles con configuración comparable y los resultados muestran variabilidad.

Una conclusión académica válida es indicar: se observó estabilidad en el escenario B de 100 VUs bajo Testcontainers; en 200 se incumplieron latencia y checks; no se promete capacidad comercial. La ejecución con más pasos y rampas puede cambiar las cifras, y debe reportarse como otro experimento.

## 10. Limitaciones y alcance

No hay certificación fiscal comercial; la política del proveedor sigue pendiente y no hay automatización fiscal permanente. Los ensayos no miden SOAP real ni capacidad de firma/RIDE bajo carga. No se prueba alta disponibilidad con fallo físico. La nota no depende de ocultar estas limitaciones: depende de demostrarlas y analizarlas según la rúbrica.

### Evidencias
- [Prueba etapa 5](../operations/admission-capacity-stage5.md).
- [Testcontainers/k6](../operations/load-testing.md).
- [Guía operativa de staging](../operations/emitta-publication.md).
- [Política normativa](../provider-regulatory-policy.md).
- [Código tests k6](../../tests/load/traffic.ts).
