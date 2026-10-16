# Resultados de carga académica — 16 de octubre de 2026

Ensayos aislados; firma, RIDE y SRI desactivados. Hikari máximo 10, JVM de prueba 1 GiB; k6 2 CPU/512 MiB. Docker Desktop comparte el equipo de 12 procesadores lógicos y 32 GiB. Las cifras HTTP agrupan creación, replay y consulta.

## academic-spike-500-818c8d3e-1bc9-469f-bfe4-72bbb5bd79c0

| Indicador | Resultado |
| --- | ---: |
| Solicitudes HTTP/s | 155.53 |
| Documentos aceptados/s | 51.84 |
| Documentos persistidos | 5083 |
| Eventos persistidos | 5083 |
| Jobs admitidos | 5083 |
| Latencia media (ms) | 2117.96 |
| p90 (ms) | 3439.98 |
| p95 (ms) | 3700.97 |
| p99 (ms) | 4405.17 |
| Errores HTTP (%) | 0 |
| Checks (%) | 100 |
| Idempotencia (%) | 100 |
| Hikari activo máximo | 10 |
| Hikari pendientes máximo | 192 |
| Heap máximo (MiB) | 285.61 |
| CPU proceso máxima muestreada (%) | 57.07 |
| Outbox máximo muestreado | 253 |
| Antigüedad Outbox máxima (s) | 3.36 |
| Outbox final | 0 |
| Drenaje (s) | 5.19 |
| Persistidos sin acuse HTTP | 0 |
| Código k6 | 99 |
| Llamadas SRI | 0 |

| Operación | Media (ms) | Máximo de ventana Micrometer (ms) |
| --- | ---: | ---: |
| hikaricp.connections.acquire | 268.82 | 2267.51 |
| emitta.invoice.transaction.duration | 336.30 | 2232.34 |
| emitta.outbox.claim.duration | 87.72 | 1121.25 |
| emitta.outbox.confirm.duration | 16.31 | 77.99 |
| emitta.outbox.mark.duration | 170.94 | 1395.97 |

[Agregados y umbrales](evidence/academic-spike-500-818c8d3e-1bc9-469f-bfe4-72bbb5bd79c0/summary.json) · [Invariantes](evidence/academic-spike-500-818c8d3e-1bc9-469f-bfe4-72bbb5bd79c0/invariants.json) · [Muestras de recursos](evidence/academic-spike-500-818c8d3e-1bc9-469f-bfe4-72bbb5bd79c0/resources.csv)

## academic-sustained-100-a2dc930c-d452-4f81-9517-a02dd236f257

| Indicador | Resultado |
| --- | ---: |
| Solicitudes HTTP/s | 164.07 |
| Documentos aceptados/s | 54.69 |
| Documentos persistidos | 26382 |
| Eventos persistidos | 26382 |
| Jobs admitidos | 26382 |
| Latencia media (ms) | 425.59 |
| p90 (ms) | 684.50 |
| p95 (ms) | 785.50 |
| p99 (ms) | 1023.15 |
| Errores HTTP (%) | 0 |
| Checks (%) | 100 |
| Idempotencia (%) | 100 |
| Hikari activo máximo | 10 |
| Hikari pendientes máximo | 84 |
| Heap máximo (MiB) | 178.67 |
| CPU proceso máxima muestreada (%) | 34.04 |
| Outbox máximo muestreado | 72 |
| Antigüedad Outbox máxima (s) | 3.96 |
| Outbox final | 0 |
| Drenaje (s) | 4.17 |
| Persistidos sin acuse HTTP | 0 |
| Código k6 | 0 |
| Llamadas SRI | 0 |

| Operación | Media (ms) | Máximo de ventana Micrometer (ms) |
| --- | ---: | ---: |
| hikaricp.connections.acquire | 102.30 | 931.24 |
| emitta.invoice.transaction.duration | 152.31 | 986.82 |
| emitta.outbox.claim.duration | 55.25 | 319.43 |
| emitta.outbox.confirm.duration | 14.99 | 66.36 |
| emitta.outbox.mark.duration | 99.95 | 472.47 |

[Agregados y umbrales](evidence/academic-sustained-100-a2dc930c-d452-4f81-9517-a02dd236f257/summary.json) · [Invariantes](evidence/academic-sustained-100-a2dc930c-d452-4f81-9517-a02dd236f257/invariants.json) · [Muestras de recursos](evidence/academic-sustained-100-a2dc930c-d452-4f81-9517-a02dd236f257/resources.csv)

## Interpretación

El máximo de ventana de Micrometer no equivale al máximo de toda la ejecución. CPU se expresa como la fracción reportada por el proceso respecto de los procesadores disponibles; no representa consumo total del host. El drenaje exige eventos publicados, broker vacío y admisión durable de jobs; no exige ejecutar jobs fiscales, que permanecen apagados. En el formato de summary de k6, los booleanos de thresholds indican fallo: false significa que ese umbral no falló.

El sostenido cumple los umbrales técnicos sin pérdida observada, pero supera el ideal académico p95 <500 ms. Las esperas de adquisición y los pendientes Hikari muestran contención aun con backlog Outbox pequeño: no se justifica atribuir toda la latencia al broker. Las comparaciones con la etapa 5 son descriptivas: difieren perfil temporal e instrumentación JaCoCo. No se acredita capacidad comercial ni throughput fiscal.

El spike falló exclusivamente el umbral p95<2000 ms (3.700,97 ms; código k6 99). Sus invariantes de durabilidad e idempotencia pasaron. La cola de adquisición alcanzó 192, mientras el throughput útil no aumentó frente al sostenido; el sistema preservó datos pero acumuló espera. No se alteraron umbrales ni se repitió para seleccionar un resultado favorable.
