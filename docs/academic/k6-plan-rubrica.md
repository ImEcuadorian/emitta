# Plan k6 específico para la rúbrica UPS

## Diferencia frente a los ensayos anteriores

El escenario sostenido de etapas previas usa constant-vus y no ejecuta ramp-up/ramp-down. El spike anterior mantuvo 500 VUs durante 30 segundos. Ambos sirven como línea de base, pero no satisfacen el **formato temporal completo** solicitado por la rúbrica de 2026.

## Perfil A: académico sostenido

- Inicio: 0 VUs.
- Ramp-up: 2 minutos hasta 100 VUs, en ensayo inicial.
- Meseta: 5 minutos a 100 VUs; ampliable a 10 minutos.
- Ramp-down: 1 minuto de 100 a 0.
- Después: drenaje de Outbox/jobs hasta cinco minutos sin carga adicional, comprobando invariantes.
- Repetir de modo exploratorio a 125/150/175/200 VUs según capacidad observada y resultados.
- Límites: recursos aislados, datos sintéticos, Redis test y firma/RIDE/SRI OFF.

Comando ejecutable en Windows, desde la carpeta del backend (Docker activo; Testcontainers sintéticos):

~~~powershell
.\scripts\Test-OperationalLoad.ps1 -Profile academic-sustained -Vus 100 -Duration 5m -AdmissionConcurrency 1
~~~

Para explorar el punto de ruptura, ejecutar **por separado**, no en paralelo, 125, 150, 175 y 200 VUs con la misma infraestructura y configuración, después de confirmar que 100 pasó. El perfil original sustained no cambia.

## Perfil B: pico extremo

- Inicio estable: 50 VUs durante 30 segundos (referencia).
- Subida brusca: 1 segundo hasta 500 VUs (10 × carga normal).
- Meseta extrema: 1 minuto (o 2 minutos en ensayo aprobado).
- Descenso: 1 segundo a 0.
- Drenaje de mensajes y recuperación hasta cinco minutos.
- Los recursos asignados a k6/infraestructura deben revisarse primero; NO ejecutar sobre API staging externa ni piloto.

~~~powershell
# Requiere revisión de capacidad previa; no quitar -DimensionedSpike.
.\scripts\Test-OperationalLoad.ps1 -Profile academic-spike -Vus 500 -Duration 1m -DimensionedSpike -AdmissionConcurrency 1
~~~

La duración es el plateau del pico. Los resultados se almacenan localmente en build/operational/load; no se publican fixtures ni tokens.

## Indicadores de entrega

| Indicador | k6 o evidencia | Nota |
| --- | --- | --- |
| Requests/s | http_reqs.values.rate | No confundir con invoices_accepted |
| Duración media | http_req_duration.values.avg | Incluye POST/GET/replay; desglosar endpoint si se puede |
| p90 | http_req_duration.values["p(90)"] | Registrar unidades en ms |
| p95 | http_req_duration.values["p(95)"] | Comparar con ideal académico de 500 ms |
| p99 | http_req_duration.values["p(99)"] | Reconocer cola de latencia |
| Error Rate | http_req_failed.values.rate | Registrar 429/503 y causa |
| Throughput documentos | invoices_accepted / duración | No igualar a autorizaciones SRI |
| CPU/memoria | resources.csv + container-resources.jsonl | Señalar muestreo y presupuesto |
| BD/Hikari | active, pending, acquisitions | Evitar atribuciones no verificadas |
| Broker | ready/unacked + jobs y Outbox | Drenaje de flujo durable |
| Breakpoint | Repeticiones intermedias | Definir como mínimo intervalo observado |

## Criterios de aceptación de la rúbrica

- Reportar ramp-up 2–3 min, meseta 5–10 min, ramp-down 1–2 min.
- Reportar spike a 5–10 × baseline y duración 1–2 min.
- Reportar RPS, promedio, p90, p95, p99, errores y recursos.
- Distinguir **umbral del test** (p95<2000, p99<5000, HTTP error<1% y checks=100%) del **objetivo ideal de la rúbrica** (p95<500 ms).
- No elevar límites por encima de los originales para forzar éxito.
- Reportar código HTTP 503 que precede al commit separado de errores de resultado desconocido.
- Documentar fallos sin eliminar logs ni datos agregados.

## Seguridad

El harness Java crea un tenant, credenciales y comprobantes TEST sintéticos en Testcontainers; no acepta URL externa y no utiliza Doppler ni datos del piloto. Asegurar que el script académico conserve ese bloqueo. Cualquier ejecución de 500 VUs necesita supervisar CPU y RAM.

## Reproducibilidad y trazabilidad

Guardar cada run con: fecha/hora, commit SHA, modo, VUs, perfiles y duración, hardware y límites Docker, versión k6, summary.json, resources.csv, invariants.json, filas de Rabbit, CSV de drenaje y resultado del umbral.

No publicar fixture.json, tokens ni logs completos; solo gráficas y tablas agregadas sin identidades.

**Estado:** plan y perfiles propuestos; las cifras académicas todavía no están medidas hasta ejecutar esos ensayos.
