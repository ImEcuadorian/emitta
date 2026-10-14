# Alta fiscal por API y panel del dueño

## Contrato del cliente

`POST /api/v1/fiscal-configuration` recibe los modelos existentes de contribuyente, establecimiento y punto. El tenant procede del JWT, nunca del cuerpo. Requiere los tres permisos de creación fiscal. Devuelve `taxpayerId`, `establishmentId`, `pointOfIssueId` y ambiente fijo TEST.

La operación reutiliza los tres casos de uso y una transacción. Bloquea la fila del tenant para serializar altas concurrentes; usa las claves naturales ya protegidas por constraints. Solicitudes coherentes identifican los registros existentes. Datos suministrados diferentes, estados inactivos o habilitación incompatible provocan 409 sin mutar registros. Fallos revierten creaciones parciales. No asigna secuenciales, no crea facturas y no publica eventos fiscales.

`GET /api/v1/fiscal-configuration` consulta únicamente la cuenta del JWT y requiere `certificates:read`. Lista configuración y metadatos del certificado activo; nunca material PKCS#12 ni contraseñas. Un metadato activo no acredita confianza actual ni preparación completa para emitir.

El endpoint multipart existente `/api/v1/taxpayers/{taxpayerId}/certificates` permanece como alta/rotación, con permisos específicos y aislamiento. Reutiliza validación PKCS#12/PKIX/CRL, identidad certificada por RUC, revisión de autoridad y AES-GCM. No se desactiva ninguna validación. No se admite CA suministrada por el cliente.

## Dueño

El dueño con `platform:admin` puede seleccionar una cuenta activa y usar:

- GET/POST `/api/v1/tenants/{tenantId}/fiscal-configuration`.
- GET `/api/v1/tenants/{tenantId}/documents`, con paginación.
- GET `/api/v1/tenants/{tenantId}/documents/{documentId}/artifacts/{type}`.
- El endpoint de certificados existente para el contribuyente seleccionado, sin pedir prestadas credenciales de cliente.

El documento debe pertenecer a la cuenta seleccionada antes de consultar almacenamiento. Se reutilizan `ReadDocumentUseCase` y `LoadDocumentArtifactUseCase`, incluyendo estado AUTHORIZED y SHA-256. La autorización global del dueño no amplía las credenciales del cliente.

V14 amplía únicamente los tipos de acciones de auditoría. Guarda actor y recursos, no cuerpos fiscales ni contraseñas. La migración no cambia contribuyentes, certificados, comprobantes o estados fiscales. Aplicación staging únicamente tras verificar base y respaldarla; no aplicar sobre el piloto privado.

## Interfaz e integración

El panel muestra datos fiscales, selección de contribuyente/certificado y facturas con descargas disponibles. El UUID del punto se presenta como dato para la aplicación, sin necesitar copiar UUID para navegar o importar un certificado en el panel.

Al crear una cuenta, el dueño elige preparación por Emitta (tres permisos de integración) o gestión fiscal propia (los ocho permisos de cliente, sin `platform:admin`). No se elevan silenciosamente conexiones existentes. Las contraseñas siguen mostrando sólo al crear/rotar. No hay autorregistro público ni login humano nuevo.

La documentación pública explica configuración y certificado para integradores; excluye las rutas del dueño de la referencia pública. Las solicitudes interactivas de Scalar siguen deshabilitadas. Ejemplos exclusivamente ficticios.

## Validación

Pruebas PostgreSQL de concurrencia y rollback; HTTP real con JWT para resolución/repetición/conflicto, códigos de tres dígitos, permisos, propietario y pertenencia. Importación con criptografía real sintética para dueño y cliente, error de contraseña/identidad/trust y no exposición de secretos. Prueba del panel con respuestas sintéticas para perfil autogestionado y alta conjunta. Regresión y almacenamiento aislados; no realizar transmisiones al SRI durante esta etapa.

## Cierre verificado — 2026-10-14

- Gradle build satisfactorio: 377 pruebas registradas, 376 aprobadas, cero fallos y una prueba SRI live omitida (pendiente independiente). Seis pruebas adicionales de almacenamiento PostgreSQL/MinIO aislado aprobadas.
- Web: Astro check sin errores, warnings ni hints; flujo administrativo sintético aprobado; build estático de 12 páginas y 220 enlaces/anclas verificados.
- Staging actualizado con API `emitta-api:20261014-fiscal-admin` y web `emitta-web:20261014-fiscal-admin`. V14 aplicada únicamente a la base dedicada staging tras backup privado PostgreSQL custom e inspección de su índice con pg_restore. No se ejecutó un ensayo de restauración de ese backup.
- Health HTTPS 200. Los siete flags de firma, workers, outbox, jobs, RIDE, recepción y autorización permanecen false. Política del proveedor sin cambios.
- Comprobación HTTPS autenticada: dueño consulta configuración y metadatos existentes, lista documentos y descarga PDF original. Cliente de integración no accede a las rutas del dueño (403); descarga anónima rechazada (401).
- Navegador real: inicio de sesión, selección de negocio, visualización de datos/certificado y factura autorizada; botón Descargar PDF funcionando, digest idéntico al original conservado privadamente. No se crearon clientes, contribuyentes, certificados ni facturas en staging durante estas comprobaciones. Sólo autenticación y auditoría administrativa.
- Artefactos fiscales, evidencias y credenciales privados fuera de Git. Sin nuevos envíos SRI, commit ni push.

## Límites de esta entrega

El cliente debe recibir un acceso con gestión fiscal propia para configurar su emisor e importar certificados; los accesos antiguos no se elevan automáticamente. El dueño conserva el control de creación de cuentas: no existe autorregistro público. La autorización del certificado se revisa antes de importar; no se acredita automáticamente por nombres o por una casilla.

La habilitación comercial/PRODUCCIÓN, la política normativa del proveedor, el acceso humano con MFA y la operación fiscal automática permanecen pendientes. El alta conjunta de esta entrega es exclusivamente TEST. El panel muestra hasta cien filas de configuración fiscal por consulta; la API ofrece paginación. La recuperación del PDF existente no vuelve a emitir al SRI.
