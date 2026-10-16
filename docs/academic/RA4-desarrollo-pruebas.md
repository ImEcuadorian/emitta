# RA4 — Implementación, seguridad, pruebas y despliegue

## 1. Implementación efectivamente existente

La solución implementada utiliza Java 25, Spring Boot 4.1.1, PostgreSQL 18, Redis 8, RabbitMQ 4, almacenamiento S3 compatible, firma DSS/XAdES, OpenAPI, Testcontainers, k6 y Docker. La web pública se implementa con Astro, Starlight y Scalar y se entrega como sitio estático.

**Código fuente:** [Emitta backend](https://github.com/ImEcuadorian/emitta/tree/main) y [Emitta Web](https://github.com/ImEcuadorian/emitta-web/tree/main).
**Despliegue staging reportado:** https://emitta.nexorf.dev y https://api-emitta.nexorf.dev. El despliegue se documenta en [emitta-publication.md](../operations/emitta-publication.md); las capturas HTTPS de documentación y Scalar están en [evidence/](evidence/README.md).

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

La suite integrada se ejecutó con `build jacocoTestReport`: **381 aprobadas, 0 fallos y 1 prueba SRI live omitida** (382 casos). La compilación y el build finalizaron correctamente. Las 7 integraciones S3 pasaron en ejecución separada con MinIO desechable (incluye recorrido REST hasta XML/PDF); los perfiles k6 se reportan aparte. [Evidencia S3](evidence/storage-summary.json).

Módulos cubiertos: invoice/idempotency, JWT/tenant/scopes, Redis, RabbitMQ, recuperación, XML/XSD, firma, S3/MinIO, RIDE y errores SOAP.

Comandos en Windows para evidencia:

~~~powershell
.\gradlew.bat clean build --console=plain
.\scripts\Test-IsolatedStorage.ps1
~~~

Docker debe estar activo; los tests son aislados. El test SriSoapAuthorizationLiveTest debe permanecer omitido salvo autorización específica. Nunca usar el piloto ni SRI como carga.

## 4. Cobertura de código

JaCoCo 0.8.15 registró **80,91 % de instrucciones, 59,70 % de ramas y 82,70 % de líneas** en la suite normal integrada. Véase [evidence/coverage.json](evidence/coverage.json). Los procesos hijos y la tarea S3 independiente no se agregan a estos porcentajes.

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

## 6. Escenarios académicos ejecutados

El 16 de octubre se ejecutaron los perfiles de la rúbrica en infraestructura sintética. El sostenido utiliza 2 minutos de subida, 5 de meseta a 100 VUs y 1 de bajada; el spike utiliza referencia de 50 VUs y pico de 500 durante un minuto. Los resultados, incluidos fallos, se conservan en [resultados-carga.md](resultados-carga.md), con RPS, media, percentiles, errores, CPU, memoria, Hikari y drenaje.

El sostenido aceptó 26.382 documentos (54,69/s), con p95 de 785,50 ms, p99 de 1.023,15 ms, 0 % de errores HTTP y 100 % de idempotencia. Drenó en 4,17 s. Cumple los umbrales técnicos; **no cumple el ideal académico p95 <500 ms**. La adquisición de conexiones promedió 102,30 ms y alcanzó 84 solicitantes pendientes: existe contención bajo concurrencia. No se aumentó el pool ni se relajaron umbrales para aprobar.

La tarea está instrumentada con JaCoCo y comparte recursos del portátil con Docker Desktop. Por ello no se atribuyen causalmente las diferencias con los ensayos históricos sólo al código. Los jobs fiscales permanecen pendientes por diseño: el drenaje verifica publicación y admisión durable, no ejecución fiscal.

El spike de 500 VUs **falló el umbral p95<2000 ms**: registró p95 3.700,97 ms y p99 4.405,17 ms; k6 terminó con código 99. Conservó 5.083 documentos/eventos/jobs, cero errores HTTP, 100 % de checks e idempotencia y drenaje en 5,19 s. Hikari llegó a 192 solicitantes pendientes. No se observaron pérdidas, pero no se acredita soporte de 500 usuarios con la latencia requerida.

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

GitHub Actions ejecuta Java 25, build, Testcontainers y JaCoCo con SRI live desactivado. La integración del backend pasó en [run 38844921951](https://github.com/ImEcuadorian/emitta/actions/runs/38844921951); la web en main pasó en [run 38894598765](https://github.com/ImEcuadorian/emitta-web/actions/runs/38894598765). Los artefactos de cobertura de CI tienen retención limitada; los contadores agregados quedan versionados en evidence/.

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
