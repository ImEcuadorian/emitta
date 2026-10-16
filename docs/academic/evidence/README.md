# Evidencias reproducibles

- `junit-summary.json`: conteos por suite de la ejecución integrada local, sin logs ni payloads.
- `coverage.json`: contadores de JaCoCo de la suite normal; S3 y procesos hijos se reportan por separado.
- `docs-desktop-local.png`, `docs-mobile-local.png`, `admin-login-local.png`: build local, revisión visual sin sesión ni datos privados.
- `docs-staging.png`, `scalar-staging.png`: documentación HTTPS publicada.
- `interrupted-load.json`: ejecución incompleta preservada; no se contabiliza como aprobada.
- Directorios `academic-*`: resultados sintéticos agregados, sin fixtures ni credenciales. Cada ensayo conserva umbrales, recursos y drenaje, incluyendo fallos.

Los XML/PDF reales, certificados y respaldos permanecen exclusivamente en el entorno privado. La captura de entrada no demuestra por sí sola la navegación autenticada: ésta se contrasta con tests del panel y verificaciones autorizadas de API.

- `storage-summary.json`: 7 integraciones aprobadas con MinIO desechable, PostgreSQL/RabbitMQ y SOAP simulado. La ejecución inicial falló en 3 casos por hashes de fixtures desactualizados; después de corregirlos se repitió la suite completa.
