# Respaldo, restauración y futura aplicación de V12

Procedimiento preparado; **no ejecutado sobre el piloto**. Requiere ventana y autorización del responsable. No cambiar checksums de migraciones ya aplicadas ni ejecutar Flyway repair para ocultar diferencias.

1. Registrar versión de PostgreSQL, commit revisado, checksums Flyway y estado actual V1–V11. Comprobar que V12 no fue aplicada y que su checksum corresponde al código que se publicará. Revisar rol migrador, grants actuales y espacio libre. Pausar sólo workers/publisher autorizados y evitar nuevas escrituras durante el respaldo consistente. SRI y excepciones experimentales apagados.
2. Con Doppler en el entorno adecuado y credenciales fuera de argumentos/logs, realizar `pg_dump --format=custom --file=<ruta_privada>/pre-v12.dump` usando variables PG* o pgpass protegido. Respaldar además roles mediante la cuenta autorizada, secretos por su gestor y objetos del bucket con versión/metadatos. No guardar nada en Git ni en build público. pg_dump es snapshot consistente de DB; no proporciona snapshot atómico conjunto con S3: documentar punto de corte/versiones de los objetos.
3. Calcular SHA-256 del dump y manifiesto privado de objetos. `pg_restore --list` sólo verifica legibilidad del catálogo: **no es una restauración probada**. Restaurar el dump con `pg_restore --exit-on-error` en otra base/cluster aislado; no usar clean/drop contra el piloto. Verificar conteos, Flyway, jerarquías, certificados cifrados y metadata de artefactos. Contrastar objetos con SHA-256 y manifestar excepciones sin imprimir XML ni secretos.
4. Aplicar V12 primero a esa restauración con el migrador. Comprobar validate y ambas tablas/jobs/journal/índices; app tiene DML en las nuevas tablas, readonly SELECT, app no tiene DML en flyway_schema_history. V12 no modifica documentos ni certificados. Simular rollback de una migración fallida en un entorno sintético, nunca en el piloto.
5. Sólo después del restore validado y aprobación, ejecutar el proceso Flyway del backend con una configuración de migración que no inicie HTTP, workers, publisher ni SRI. El perfil local de recuperación NO sustituye un migrador genérico. El proyecto no proporciona todavía un comando dedicado de migración/backup: usar una herramienta Flyway aprobada con las mismas versiones y ubicaciones, o preparar ese comando operativo antes de la ventana. No recomendar bootRun normal, que podría heredar flags privados.
6. Consultar en lectura el historial, validate, permisos y conteos después de V12. Mantener flags apagados y conservar backup/manifiesto privados. Habilitar workers requiere prueba de pool, timeout del rol app y recuperación en ese entorno, y autorización separada.

Ante fallo transaccional DDL PostgreSQL: verificar rollback y logs saneados; la prueba aislada V13 sintética verifica rollback por excepción SQL, no acredita todas las interrupciones físicas. Si hubo pérdida de instancia o corrupción, reconstruir desde backup probado en un destino nuevo, validar objetos/secretos y realizar el cambio controlado de servicio. No restaurar automáticamente encima del piloto ni truncar tablas fiscales. El rollback de despliegue también debe considerar compatibilidad del binario anterior con esquema V12; bajar versión no significa eliminar tablas.

Conservar evidencia privada del ensayo de restore (fecha, hashes, conteos y responsables), y medir RTO. Este procedimiento no certifica que los backups privados actuales existan o sean recuperables.

`pg_dump` sin `--create` no reconstruye por sí solo las ACL del destino a nivel de base. Crear el destino con dueño correcto, revocar CONNECT/TEMP de PUBLIC y restaurar grants autorizados; validar también defaults de los roles (statement/lock/idle timeouts) en la base nueva. La nueva prueba de restore sintético lo hace explícitamente y verifica TEMP denegado a app. Los globals/roles y sus secretos son un respaldo independiente; conservarlo sólo cifrado fuera del repositorio. Fuente: [pg_dump PostgreSQL 18](https://www.postgresql.org/docs/18/app-pgdump.html).

## Comandos para una ventana futura autorizada

Requieren clientes PostgreSQL 18 y CLI Flyway **12.4.0**, versión resuelta actualmente por Gradle, previamente verificada. No se han instalado/ejecutado contra el piloto. Seleccionar primero el proyecto/config Doppler correspondiente; no usar una configuración de producción por defecto. Abrir una terminal efímera con `doppler run -- pwsh -NoProfile`, sin transcripción ni impresión de variables.

```powershell
# Valores DB_HOST/PORT/NAME y cuentas deben estar confirmados en Doppler.
foreach ($name in @('DB_HOST','DB_PORT','DB_NAME','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Falta configuración: $name" }
}
$env:PGHOST = $env:DB_HOST
$env:PGPORT = $env:DB_PORT
$env:PGDATABASE = $env:DB_NAME
$env:PGUSER = $env:DB_MIGRATION_USERNAME
$env:PGPASSWORD = $env:DB_MIGRATION_PASSWORD
$backup = Join-Path (Read-Host 'Directorio privado existente fuera del repositorio') 'emitta-pre-v12.dump'
if (Test-Path -LiteralPath $backup) { throw 'No sobrescribir un respaldo anterior' }
& pg_dump --format=custom --file $backup
if ($LASTEXITCODE -ne 0) { throw 'Falló el respaldo' }
(Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash | Set-Content -LiteralPath "$backup.sha256"
& pg_restore --list $backup | Set-Content -LiteralPath "$backup.list"
if ($LASTEXITCODE -ne 0) { throw 'Respaldo ilegible' }
# Restaurar en otro destino aprobado y comprobar objetos/roles/ACL antes de continuar.
```

Verificar que el directorio elegido esté realmente fuera de checkout, carpetas sincronizadas públicas y staging; limitar ACL de Windows al operador. El snippet sólo produce backup; el ensayo completo de restore descrito arriba es obligatorio y usa credenciales/destino independientes. No reutilizar el destino de origen en pg_restore.

Tras restore verificado y autorización específica para la migración:

```powershell
$env:FLYWAY_URL = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)"
$env:FLYWAY_USER = $env:DB_MIGRATION_USERNAME
$env:FLYWAY_PASSWORD = $env:DB_MIGRATION_PASSWORD
$env:FLYWAY_DEFAULT_SCHEMA = 'emitta'
$env:FLYWAY_SCHEMAS = 'emitta'
$env:FLYWAY_LOCATIONS = 'filesystem:' + (Resolve-Path 'src/main/resources/db/migration').Path.Replace('\','/')
$env:FLYWAY_CLEAN_DISABLED = 'true'
& flyway info
if ($LASTEXITCODE -ne 0) { throw 'Falló info' }
& flyway validate
if ($LASTEXITCODE -ne 0) { throw 'No migrar con validate fallido' }
# Revisar que sólo V12 está pendiente y confirmar el destino antes del siguiente comando.
& flyway migrate
if ($LASTEXITCODE -ne 0) { throw 'Detener y revisar rollback; no ejecutar repair automáticamente' }
& flyway validate
if ($LASTEXITCODE -ne 0) { throw 'Validación posterior fallida' }
```

Este CLI no arranca la API ni workers; el procedimiento no cambia sus flags. Cerrar la terminal elimina las variables efímeras. Fuente: [Flyway configuración por variables](https://www.red-gate.com/hub/product-learning/flyway/making-full-use-of-environment-variables-for-flyway-settings/), [default schema](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-default-schema-setting).
