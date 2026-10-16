# Cierre para presentación universitaria — 2026-10-15

## Alcance demostrable
- API REST autenticada y aislamiento por tenant; alta privada de clientes y credenciales.
- Configuración de emisor y puntos e importación de certificados mediante API.
- Creación idempotente, consulta y descargas protegidas.
- Evidencia previa real de autorización SRI TEST y XML/PDF conservados. No equivale a certificación comercial.
- Panel del dueño separado de la integración del cliente; observabilidad básica por instancia.
- Documentación y OpenAPI públicos, con solicitudes interactivas deshabilitadas.

## Cierre mínimo de demostración
1. Ensayar ingreso, selección de cliente, configuración y navegación desde una sesión limpia. La revisión visual autenticada completa y móvil sigue pendiente.
2. Preparar el guion con un documento TEST existente autorizado y sus descargas autenticadas. No requiere emitir otro documento.
3. Si la evaluación exige una factura nueva hasta PDF, coordinar y autorizar una prueba fiscal específica. El procesamiento automático continúa apagado; un POST de factura puede quedarse QUEUED.
4. Revisar los cambios locales y aprobar por separado commit/push cuando corresponda.

## Fuera del cierre universitario / antes de uso comercial
- Resolver clasificación y obligaciones del proveedor; no reutilizar la excepción experimental como cumplimiento.
- Validar operación continua, recuperación real de backups, alertas y renovaciones de confianza/CRL.
- Configurar continuidad de secuenciales para migraciones (sin endpoint de secuencial inicial actualmente).
- Revisar capacidades administrativas de documentos aún presentes en backend por compatibilidad, aunque eliminadas del panel.
- Métricas por cliente, búsqueda y edición administrativa son mejoras pendientes, no requisitos para mostrar lo existente.
- Sin autoservicio público, webhooks ni envío de documentos por correo.

## Evidencia de esta ronda
La ronda previa pasó 14 pruebas de SecurityIntegrationTest y bootJar. No se ejecutó de nuevo la suite completa por cambios documentales. La prueba SRI real omitida de la regresión sigue pendiente y no se presenta como aprobada. Esta ronda no transmite documentos.


## Cierre operativo revisado — 2026-10-15

Decisión del dueño: permanecer siempre en staging/SRI TEST. No habilitar PRODUCCIÓN.
Se publicó la guía de códigos y pagos. Se obtuvo un token nuevo de integración y se consultó el documento TEST existente: AUTHORIZED. Descargas privadas de XML y PDF verificadas contra los SHA-256 conservados, sin emisiones nuevas.
La inspección del servidor encontró una sola factura (TEST/AUTHORIZED), un trabajo RIDE pendiente y dos cuentas activas (operador y piloto). Ocho flags de procesamiento siguen apagados y provider-policy permanece unresolved.
Antes de habilitar integración fiscal automática por cuenta, falta autorizar y delimitar una excepción técnica experimental por cuenta o aportar una política de proveedor verificable. La excepción manual actual no autoriza procesamiento global ni constituye cumplimiento normativo.
