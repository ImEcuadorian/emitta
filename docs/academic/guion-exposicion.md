# Guion de exposición — Emitta API (10–12 minutos)

**Propósito:** demostrar los resultados RA1–RA4 sin emitir documentos nuevos ni habilitar procesos automáticos. Preparar capturas reales antes de clase y un plan sin Internet.

## Min 0:00–1:30 · Presentación y negocio (RA1)

**Discurso:** "Emitta es una API de facturación electrónica para Ecuador orientada a quienes desarrollan POS, ERP y aplicaciones SaaS. La idea no es reemplazar el sistema de ventas, sino evitar que cada integrador reconstruya por separado XML, firma digital, estados del SRI y almacenamiento. Es un proyecto académico en staging y ambiente de certificación TEST".

**Mostrar:** portada https://emitta.nexorf.dev, problema del cliente, tabla de cadena de valor y modelo hipotético de monetización.

**Pregunta probable:** "¿Qué tiene de innovador si hay APIs de facturación?"
**Respuesta:** "No afirmo haber inventado una categoría. Evalúo una alternativa API-first con idempotencia, tenants, contrato claro y trazabilidad; la diferencia comercial aún requiere validación".

## Min 1:30–4:00 · Arquitectura (RA2)

**Discurso:** "Escogí REST/HTTPS con OpenAPI por compatibilidad con sistemas diversos y facilidad de integración. Backend Java/Spring modular, PostgreSQL fuente de verdad, RabbitMQ para trabajo, Redis para rate limiting, S3 para artefactos y Resilience4j frente a SRI. La API no añade un gateway Java independiente porque ya hay Cloudflare/Tunnel y no existía necesidad medida".

**Mostrar:** diagrama Mermaid C4 contenedores de [RA2](RA2-arquitectura.md); explicar Outbox/consumidor idempotente.

**Pregunta probable:** "¿Por qué no microservicios?"
**Respuesta:** "Añaden transacciones distribuidas y más operaciones; elegí monolito modular mientras no exista presión medida para independizar módulos".

## Min 4:00–6:00 · Modelo y OpenAPI (RA3)

**Discurso:** "La organización es un tenant; el contribuyente es el RUC emisor; un API client es la aplicación autorizada; el buyer es quien recibe la factura. PostgreSQL garantiza restricciones como unicidad de puntos, secuenciales y claves de idempotencia. El contrato OpenAPI completo contiene operaciones de integración y administración separadas".

**Mostrar:** [ERD de RA3](RA3-modelo-contrato.md), YAML/Scalar, GET/POST y schemas. Evitar abrir operaciones administrativas con secretos.

**Pregunta probable:** "¿Qué pasa si envío dos veces la factura?"
**Respuesta:** "Mismo Idempotency-Key y mismo contenido recupera el documento; una petición HTTP con timeout no implica que el commit no exista. Nunca creo una segunda clave para resolver una respuesta ambigua".

## Min 6:00–8:30 · API funcionando (RA4)

**Demo sin transmisión fiscal nueva:**
1. Abrir el sitio HTTPS y la documentación pública, y explicar cómo se obtienen credenciales.
2. Mostrar /actuator/health con respuesta sana sin detalles internos, si responde públicamente.
3. Entrar a la administración usando **las credenciales solo en el teclado, fuera de capturas/grabación**; seleccionar cuenta de prueba existente.
4. Mostrar configuración de emisor y punto; no revelar certificado ni contraseña.
5. Desde una sesión de la aplicación cliente por API (separada del panel del dueño), consultar el comprobante TEST ya autorizado; mostrar sólo estado AUTHORIZED y metadatos no privados.
6. Descargar XML autorizado y RIDE por los endpoints de artefactos, con el token del cliente; verificar hashes **sin publicar los archivos fiscales privados**.
7. Explicar HTTP 202 vs AUTHORIZED; procesamiento automático apagado.

**Plan de contingencia:** guardar capturas autorizadas de web/Scalar, diagrama y terminal sin secretos. Nunca fabricar AUTHORIZED ni emitir documentos con credenciales de un tercero.

## Min 8:30–10:30 · Calidad y rendimiento (RA4)

**Discurso:** "Ejecuté pruebas Java y Testcontainers y analicé dos intensidades k6. Con 100 VUs/10 min en entorno sintético obtuve 54.535 documentos durables, 90,47 documentos/s y 0 % error HTTP, p95 de 651 ms. En 200 VUs se incumplieron latencia y algunas verificaciones; ese límite es parte de mi análisis. El SRI y firma están apagados en esta carga, por lo que no presento estas cifras como autorizaciones por segundo".

**Mostrar:** tabla de [resultados académicos](resultados-carga.md), JaCoCo y comparación histórica B 100/200. Señalar el fallo de latencia del spike 500 y la conservación de todos los documentos.

**Pregunta probable:** "¿Por qué no llegaste al ideal p95 de 500 ms?"
**Respuesta:** "Porque el perfil académico tuvo p95 de 786 ms con 100 VUs. Comparo contra el ideal honestamente; es una API que confirma durabilidad, y se pueden revisar rutas lentas y pool, sin falsear resultados".

## Min 10:30–12:00 · Cierre

**Discurso:** "El proyecto satisface los objetivos de diseñar, documentar, proteger, desplegar y medir una API. Se demostró un ciclo TEST controlado hasta XML autorizado y RIDE. No afirmo certificación comercial ni proceso automático de producción: la política normativa y operación continua siguen pendientes".

## Material a llevar

- README académico y RA1–RA4.
- Dos diagramas Mermaid exportados a imagen o impresos.
- OpenAPI completo en YAML y vista Scalar.
- Evidencia de Gradle, JaCoCo, k6 y Docker.
- Capturas de HTTPS y estado autorizado sin datos privados.
- Informe claro del ensayo fallido 200 VUs.
- USB/archivo offline con documentación, **sin secretos, P12, XML real ni backups**.

## Preguntas frecuentes

**¿Cuál es la diferencia entre API y web?** API es el contrato de integración máquina-máquina; la web presenta la documentación y permite tareas de administración reservadas al operador.

**¿JWT equivale a OAuth2?** No necesariamente; JWT firmado cumple el mecanismo exigido por la rúbrica. No se presenta Emitta como Authorization Server OAuth2 completo.

**¿Qué diferencia hay entre XML y RIDE?** El XML firmado/autorizado es el artefacto fiscal; el PDF representa sus datos de manera legible y no sustituye el XML.

**¿Qué ocurre si RabbitMQ o Redis cae?** Outbox preserva eventos y el flujo durable admite reintentos idempotentes; Redis caído puede devolver 503 para nuevas solicitudes. Las simulaciones no acreditan HA comercial.

**¿Cuál es la prueba SRI real?** Se reporta un comprobante TEST autorizado de una ejecución controlada, con XML/RIDE verificados, pero no se realizan envíos automáticos actualmente.

**¿Qué harías después?** Operación fiscal validada, recuperación, mejora de observabilidad, ensayos repetibles y revisión jurídica. No forman parte del requisito mínimo RA1–RA4.
