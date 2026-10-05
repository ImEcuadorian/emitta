# ADR-014: Procesar y transmitir comprobantes inmediatamente sin acoplar la latencia HTTP a la autorización del SRI

- Estado: Accepted
- Fecha: 2026-10-04
- Decisores: Equipo Emitta

## Contexto

Desde el 1 de enero de 2026 el SRI exige transmisión inmediata de comprobantes de venta, retención y documentos complementarios.

Emitta no puede tratar el procesamiento fiscal como un trabajo batch diferido.

Al mismo tiempo, el SRI es una dependencia externa cuya latencia y disponibilidad no están bajo control de Emitta.

Mantener una petición HTTP abierta mientras Emitta:

1. persiste el comprobante;
2. genera XML;
3. firma;
4. transmite al SRI;
5. espera autorización;
6. ejecuta reintentos;

acoplaría directamente la disponibilidad y latencia de la API a la infraestructura del SRI.

## Decisión

Emitta utilizará procesamiento asíncrono inmediato para los comprobantes fiscales.

Flujo objetivo:

```text
Cliente
  |
  | POST /api/v1/invoices
  v
Emitta API
  |
  | validar
  | persistir documento
  | persistir evento Outbox
  v
PostgreSQL
  |
  | COMMIT
  v
Outbox Publisher
  |
  v
RabbitMQ
  |
  | consumo inmediato
  v
Fiscal Worker
  |
  | generar XML
  | firmar
  | transmitir al SRI
  | actualizar resultado
  v
SRI
```

RabbitMQ se utilizará para desacoplar el procesamiento, no para retrasarlo deliberadamente.

En operación normal, los mensajes deben comenzar a procesarse inmediatamente o casi inmediatamente.

La API podrá responder inicialmente con:

```text
202 Accepted
```

y un recurso que represente el estado actual del documento.

Ejemplo:

```json
{
  "id": "document-id",
  "status": "QUEUED"
}
```

Los clientes podrán consultar el estado y, posteriormente, recibir webhooks.

## Ciclo de vida inicial

Dirección inicial:

```text
RECEIVED
   |
   v
QUEUED
   |
   v
GENERATING
   |
   v
SIGNED
   |
   v
SUBMITTED
   |
   +------> RETRY_PENDING
   |
   +------> REJECTED
   |
   v
AUTHORIZED
```

La máquina de estados definitiva se formalizará durante el diseño del dominio.

## Razones

- Cumplir con la obligación de transmisión inmediata.
- Evitar batching o retrasos deliberados.
- Evitar que la latencia del SRI determine la latencia de la API.
- Aislar fallas externas.
- Permitir reintentos controlados.
- Escalar workers independientemente del tráfico HTTP.
- Facilitar pruebas de carga y spike.

## Interpretación arquitectónica

“Transmisión inmediata” se interpreta como el inicio del procesamiento y transmisión sin demoras deliberadas.

No se interpreta como obligación de mantener síncrona la petición HTTP hasta completar la autorización del SRI.

Si una futura norma o ficha técnica exige expresamente lo contrario, este ADR deberá ser reemplazado.

## Alternativas consideradas

### Procesamiento totalmente síncrono

```text
HTTP request
   |
XML
   |
firma
   |
SRI
   |
autorización
   |
HTTP response
```

Ventajas:

- El cliente podría recibir el resultado final en una sola llamada.

Desventajas:

- La latencia depende del SRI.
- Una caída externa consume recursos de la API.
- Reintentos complejos.
- Peor comportamiento ante picos.
- Riesgo de fallas en cascada.

Rechazada como arquitectura principal.

### Procesamiento batch diferido

Ventajas:

- Implementación sencilla.

Desventajas:

- Contradice el objetivo de transmisión inmediata.

Rechazada.

### Publicar directamente en RabbitMQ después del commit

Ventajas:

- Más simple que Outbox.

Desventajas:

- Problema de dual write entre PostgreSQL y RabbitMQ.

Rechazada en favor de Transactional Outbox.

## Objetivos internos de rendimiento

Los siguientes valores son SLO internos de ingeniería, no plazos legales del SRI:

```text
API acknowledgement p95:              < 500 ms
Request -> durable outbox commit:      < 500 ms
Normal queue waiting time:             < 250 ms
Request -> first SRI attempt p95:      < 2 s
Sustained-load API error rate:         < 1%
Normal queue backlog:                  ~ 0
```

Estos valores deberán validarse con k6 y ajustarse con evidencia.

## Observabilidad requerida

El sistema deberá registrar timestamps como:

```text
received_at
queued_at
processing_started_at
signed_at
submitted_at
authorized_at
failed_at
```

Métricas derivadas:

```text
request_to_queue_duration
queue_wait_duration
processing_duration
request_to_sri_submission_duration
sri_response_duration
authorization_duration
```

Métricas operativas:

```text
queue depth
oldest queued message age
retry count
DLQ count
SRI error rate
SRI circuit-breaker state
```

## Manejo de fallas

### Fallo temporal del SRI o red

Flujo esperado:

```text
SIGNED
  |
SUBMISSION_FAILED
  |
RETRY_PENDING
  |
retry con backoff limitado
```

Los reintentos deberán ser acotados.

Resilience4j y RabbitMQ no deberán producir retry storms.

### Error fiscal permanente

Errores de validación definitivos no deben reintentarse indefinidamente.

El documento pasará a un estado explícito de rechazo/error y se conservará la respuesta del SRI.

### Caída de RabbitMQ

Transactional Outbox conservará la intención de procesamiento en PostgreSQL hasta que la publicación pueda realizarse.

## Idempotencia

El modelo asíncrono exige idempotencia.

Como mínimo:

- el cliente utilizará `Idempotency-Key`;
- PostgreSQL impondrá unicidad por tenant y clave;
- los consumidores RabbitMQ serán idempotentes;
- una entrega duplicada del broker no podrá emitir dos comprobantes.

## Consecuencias

### Positivas

- Respuesta HTTP rápida y predecible.
- Procesamiento fiscal inmediato.
- Fallas del SRI aisladas.
- Reintentos controlados.
- Workers escalables.
- La profundidad de cola se convierte en un indicador operativo claro.

### Negativas

- El cliente debe comprender estados asíncronos.
- Existe consistencia eventual.
- Se deben diseñar consultas de estado y webhooks.
- La cola debe monitorizarse.
- La idempotencia es obligatoria.

## Riesgos

### Acumulación de cola

Una cola creciente podría contradecir el objetivo de transmisión inmediata.

Mitigación:

- alertas por profundidad y antigüedad;
- escalamiento de consumidores;
- SLO de edad de mensajes;
- pruebas de capacidad.

### Retry storm

Mitigación:

- reintentos limitados;
- backoff;
- Circuit Breaker;
- DLQ;
- métricas.

### Cliente interpreta `202` como “autorizado”

Mitigación:

- contrato OpenAPI explícito;
- estados claros;
- documentación de que `202` significa aceptado para procesamiento, no autorizado por el SRI.

## Implicaciones para pruebas

Las pruebas de carga deben medir principalmente la infraestructura de Emitta y no ejecutar carga masiva contra infraestructura externa del SRI.

Para k6:

```text
k6
 |
 v
Emitta
 |
 v
Fake / Mock TaxAuthorityGateway
```

Las pruebas reales contra el ambiente de certificación del SRI serán pruebas de integración separadas.

## Evolución futura

Si las mediciones muestran que ciertos flujos pueden ofrecer una espera síncrona acotada sin comprometer disponibilidad, Emitta podría ofrecer posteriormente un modo opcional, por ejemplo:

```text
POST /invoices?waitForAuthorization=2s
```

Ese modo sería una optimización sobre el flujo asíncrono, no su reemplazo.

Cualquier cambio del modelo principal requerirá un nuevo ADR.

## Referencias

- Comunicado del SRI del 30 de diciembre de 2025 sobre transmisión inmediata obligatoria desde el 1 de enero de 2026.
- Documentación técnica vigente de facturación electrónica del SRI.
- ADR-006: RabbitMQ para procesamiento asíncrono.
- ADR-007: Transactional Outbox.
- ADR-009: Resilience4j para resiliencia frente al SRI.
