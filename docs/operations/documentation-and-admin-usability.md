# Documentación de integración y administración — 2026-10-12

Cambios locales en emitta-web, sin despliegue ni modificaciones de datos fiscales.

- Documentación pública centrada en acceso, token, factura, estado y descargas. Ejemplos ficticios conservados; advertencia de procesamiento staging apagado.
- Guía administrativa retirada del contenido público y conservada en docs/operations/web-administration-reference.md. La ruta antigua redirige a /admin/.
- Scalar usa una proyección de cinco rutas existentes del contrato (la descarga parametrizada cubre XML y PDF), sin operaciones ni modelos administrativos. Contrato completo conservado. Proyección reproducible durante build, sin cambiar solicitudes/respuestas del backend.
- Panel muestra herramientas y ayuda tras iniciar sesión. Organizaciones seleccionadas por nombre; selección automática después de creación; identificadores fiscales propagados entre formularios.
- Dos perfiles predefinidos de mínimo privilegio: integración y administración fiscal. Ninguno concede platform:admin; verificación de tenant/scopes permanece en el backend.
- No autorregistro, nuevas cuentas humanas ni MFA implementados. Se conserva el acceso administrativo de aplicación actual. Preparación fiscal requiere cambiar explícitamente al acceso administrativo de la organización; no hay delegación silenciosa del operador global.
- Operaciones administrativas separadas, sin cadena automática de altas ni reintentos. Contraseña del certificado, evidencia y confirmación revisada conservan validaciones anteriores.

Validación: astro check cero errores/advertencias/hints; build estático correcto; 12 HTML y 184 enlaces/anclas correctos (incluye redirección); prueba de flujo con respuestas sintéticas y sin red verifica selección automática, perfiles, rechazo de perfil inválido, propagación de UUID y limpieza al salir; navegador confirma navegación pública sin administración y panel sólo con acceso antes de autenticar; npm audit de instalación cero vulnerabilidades. No alta real ni importación de certificado durante esta tarea.

Limitación: validación funcional del panel contra respuestas sintéticas, sin crear recursos en staging. Los cambios aún no están publicados en el servidor. Sin commit/push ni llamadas SRI.


## Revisión UX/UI publicada

Publicado sólo el sitio estático en `https://emitta.nexorf.dev/admin/`, imagen `emitta-web:20261012-ux2`. Backend conserva `emitta-api:20261012-panel1`; no migraciones, altas fiscales, importaciones, cambios de secretos ni llamadas al SRI durante esta revisión.

Pantalla de acceso con jerarquía visual clara; organizaciones presentadas como tarjetas por nombre y estado; gestionar accesos revela exclusivamente la organización seleccionada; creación en secciones plegables; perfiles legibles con mínimo privilegio; acciones cambiar clave y desactivar requieren confirmación. Resultados traducidos en filas y JSON sólo en detalle técnico. Identificadores fiscales existentes en detalle plegable, con validación explícita para evitar el fallo de foco de campos requeridos ocultos. Comprobación de sesión impide mostrar respuestas tardías de otra sesión.

Validación final: astro check 0 errores/advertencias/hints; test:admin sintético sin red correcto; build correcto; 12 HTML y 184 enlaces/anclas verificados; auditoría de 37 assets de texto sin patrones evidentes de secretos/datos del piloto; navegador HTTPS muestra diseño nuevo y documentación sin sección administrativa, sin errores/warnings de consola. Flujo autenticado real con credenciales del dueño no ejecutado por el agente. CSS adaptable implementado; no se afirma verificación visual móvil completa.

La comprobación HTTPS mediante Python urllib recibió 403 de la protección del host web, que no se modificó; navegador normal y origen Nginx verificados. La excepción Browser Integrity Check existente sigue limitada al host API.

Paquete final SHA-256: `ac20f4fb86a3fc6073124edf04986a4b73ec51e03fbae4c25a2b9aa6ff002d4f`, verificado en servidor antes de extraer. Contenedores anteriores `emitta-web-rollback-panel1` y `emitta-web-rollback-ux1` detenidos y conservados. Publicación mantiene usuario 101, rootfs de sólo lectura, sin puertos del host, sin capacidades y límites existentes. Scripts e imágenes anteriores retenidos. Sin commit/push.


## Documentación exclusiva para integradores

Revisión clientdocs publicada: se retiraron de public/ las dos copias completas del OpenAPI administrativo y se conservaron como fuentes de build en specifications/ (fuera del paquete Docker estático). Únicamente la proyección de integración se publica en la web. Se eliminó la antigua ruta pública docs/administration y referencias promocionales innecesarias al alta administrativa. El panel reserva el perfil administrativo explícitamente al operador. Los clientes reciben el perfil de integración, no permisos administrativos.

Validación: check sin errores/advertencias/hints; test:admin correcto; build correcto; 11 HTML y 183 enlaces/anclas; aserciones comprueban ausencia de guía/copias OpenAPI administrativas en dist. Imagen web emitta-web:20261012-clientdocs; rollback ux2 conservado. Backend no desplegado ni modificado por esta publicación. SHA-256 del paquete: 4537f723ac47b4f42d672ea8ee766e4cf94590f3d52b792087c4227d6f482734.

El acceso al panel continúa exigiendo credenciales administrativas. Ocultar referencias públicas no sustituye autorización del backend. El contrato técnico administrativo sigue existiendo en el backend para operación, sin cambios de seguridad ni publicación de secretos.
# Simplificación del panel publicada — 2026-10-12

Se retiraron los encabezados “Paso 1 / Paso 2”. La pantalla del dueño presenta un directorio de **Clientes** y un espacio del negocio seleccionado. **Abrir cliente** muestra sus accesos existentes. **Añadir un negocio** y **Crear identificador y clave** permanecen cerrados hasta necesitarlos; el perfil de integración es el predeterminado y las opciones fiscales internas se separan en un detalle avanzado. Estados traducidos y avisos sobre claves irrecuperables evitan crear accesos innecesarios. No se ocultaron ni eliminaron registros sintéticos existentes.

Publicación: imagen `emitta-web:20261012-admin-simple`, conservando el contenedor anterior `emitta-web-rollback-clientdocs`. Archivo estático verificado con SHA-256 antes de extraer. El backend permanente no se actualizó.

Comprobaciones: Astro 11 archivos sin errores, warnings ni hints; prueba sintética del flujo administrativo satisfactoria; build de 11 páginas; 183 enlaces/anclas locales válidos. Se verificó además la interfaz publicada con el acceso real del operador: listado, selección del negocio del piloto y sus dos accesos, sin crear, revocar ni rotar nada. Consola sin errores ni warnings. Captura en el entorno privado y en `build/operational` ignorado por Git.

Limitación pendiente: el dueño todavía no tiene un flujo único para consultar/preparar toda la configuración fiscal de otro tenant. Este cambio mejora presentación y acceso a las credenciales existentes; no inventa metadatos fiscales, no elimina la autorización por tenant ni presenta el alta completa como terminada. La documentación pública permanece orientada al integrador y la administración fuera de su navegación.

# Alta simplificada publicada — 2026-10-13

La acción **Crear cliente** solicita únicamente el nombre del negocio y encadena los endpoints existentes para guardar el tenant y generar una conexión de integración con permisos mínimos. Las credenciales se muestran una vez. Añadir un negocio no declara su preparación fiscal ni activa recepción automática.

Las conexiones fiscales internas quedan bajo **Herramientas internas de Emitta**. El identificador público de cada conexión de integración puede consultarse; su contraseña no puede recuperarse. Ante un resultado parcial o desconocido, el formulario bloquea otra creación hasta actualizar y revisar el listado. No hay retries automáticos ni una transacción ficticia entre los dos POST. Seleccionar otro negocio limpia las credenciales visibles y una respuesta tardía del listado no cruza clientes.

Validación: Astro sin errores/warnings/hints, prueba sintética ampliada con alta encadenada y fallo de red tras guardar el negocio, build de 11 páginas y 183 enlaces/anclas válidos. Comprobación autenticada de la web publicada: conexión existente, herramientas internas plegadas y formulario sencillo; sin crear ni rotar credenciales reales.

Imagen `emitta-web:20261013-client-onboarding`; versión anterior conservada en `emitta-web-rollback-admin-simple`. API permanente, documentos y certificados sin cambios. Pendiente: preparar y consultar datos fiscales de cada cliente dentro de la sesión del dueño, con permisos y auditoría explícitos. No se realizó commit ni push.
