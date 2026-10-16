# Acceso personal y recorrido fiscal aislado

## Entrada del dueño

El panel permite correo como identificador y contraseña, sin MFA ni autorregistro. La configuración inicial y recuperación requieren un JWT `platform:admin` de un cliente activo; únicamente se modifica la cuenta vinculada al actor. Los clientes de integración conservan `clientId` y `clientSecret`.

- `POST /api/v1/operator/personal-access`: configuración privada, mínimo 12 caracteres, máximo 72 bytes UTF-8. No devuelve la contraseña.
- `POST /api/v1/operator/token`: correo y contraseña; devuelve el JWT existente con `Cache-Control: no-store`.
- V16 incorpora `operator_personal_access`. BCrypt con coste 12; nunca se guarda contraseña reversible. Cinco fallos bloquean quince minutos con contador PostgreSQL y bloqueo de fila. La protección Redis por IP se aplica como al token técnico y falla cerrada si Redis no está disponible.
- Revocar el cliente administrativo o desactivar su tenant impide ingresar y usar sus tokens. Cambiar la contraseña no revoca tokens ya emitidos: caducan conforme al TTL configurado. Para revocación inmediata se usa la revocación del cliente.
- El correo es un identificador, no una dirección verificada. No se envían enlaces de recuperación. El acceso técnico privado permite recuperación; no debe entregarse a clientes.
- Contraseña y JWT sólo en memoria del navegador; formularios se limpian. La documentación pública de integración no incorpora estas operaciones administrativas.

## Procesamiento TEST preparado, apagado por defecto

V15 amplía trabajos durables con `SRI_TEST`. Reutiliza recepción, conciliación, leases y generación RIDE existentes. Requiere `EMITTA_SRI_AUTOMATIC_TEST_ENABLED=true`, una lista explícita `EMITTA_SRI_AUTOMATIC_TEST_TENANT_IDS` y fecha `EMITTA_SRI_AUTOMATIC_TEST_NOT_BEFORE`. Las dependencias fiscales deben habilitarse expresamente. No configura tenants reales por defecto y no elude la política de proveedor.

Únicamente documentos TEST nuevos de los tenants inscritos son elegibles. Un SIGNED con intentos anteriores se detiene para revisión. Después de SUBMITTED sólo se consulta autorización por la misma clave: un timeout no reenvía recepción. NOT_FOUND usa los reintentos durables limitados; resultados definitivos se conservan para revisión. Un RIDE fallido no altera la autorización.

`ClientRestFiscalJourneyIntegrationTest` crea por REST datos fiscales, certificado sintético y factura; verifica idempotencia, firma, autorización simulada, XML/PDF y SHA-256, así como rechazo entre tenants. PostgreSQL, RabbitMQ y MinIO son reales y desechables. SOAP es exclusivamente loopback, simula recepción ambigua y autorización posterior; se exige un POST y dos consultas. Esto no reemplaza una prueba real del SRI ni habilita producción.

## Validación

Ejecutar `./gradlew.bat build` y `./scripts/Test-IsolatedStorage.ps1`. En web: `npm run check`, `npm run test:admin`, `npm run build`, `npm run check:links`. La prueba SRI live permanece omitida sin autorización explícita. No se modifica ni retransmite el comprobante autorizado existente.

## Cierre verificado — 2026-10-14

Build final satisfactorio en 8 min 54 s: 381 casos, 380 aprobados y una prueba SRI live omitida; cero errores. Almacenamiento aislado: siete aprobados. La repetición inicial encontró Docker Desktop apagado (fallos de inicialización); se inició el motor y se repitió satisfactoriamente la suite completa. Web: tipado sin errores, prueba administrativa aprobada, doce páginas y 220 enlaces verificados. Auditoría del índice aprobada; el auditor reconoce el archivo sintético de pruebas del panel.

API y web de ingreso personal publicadas y verificadas por HTTPS. V15/V16 aplicadas exclusivamente a staging tras pg_dump privado (permisos 600) y comprobación de su catálogo con pg_restore; no se afirma haber restaurado ese respaldo privado. Imágenes anteriores conservadas. Se comprobaron ocho interruptores fiscales apagados y la conservación de intentos y SHA-256 del comprobante existente. No se envió al SRI ni se generó una factura real.

El dueño debe elegir su contraseña mediante el procedimiento privado fuera del repositorio o la sección autenticada «Mi entrada personal». No se eligió ni almacenó una contraseña provisional por el agente. La entrada positiva de la cuenta real queda pendiente de esa configuración; el flujo de alta, login, bloqueo y revocación fue probado con datos sintéticos.
