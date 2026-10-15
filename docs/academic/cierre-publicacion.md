# Cierre de GitHub y evidencia final para la UPS

## Dónde está cada trabajo

- Backend y RA1–RA4: rama academic/ups-ra1-ra4-20261015 de ImEcuadorian/emitta.
- Frontend actualizado: misma rama del repositorio ImEcuadorian/emitta-web.
- Ambos PR apuntan a codex/ride-consolidation, **no** a main.
- main del backend continúa antiguo en el momento de esta preparación. La presentación debe enlazar el commit/rama efectivamente entregado.

## Bloqueo real: cambios locales del 15 de octubre

El repositorio remoto backend revisado terminaba en el commit del 14-10-2026. Los informes locales del 15-10 mencionan un panel personal y ajustes adicionales. Los documentos académicos creados aquí no capturan el contenido del disco D:\ de Windows.

**No empujar por fuerza ni descartar el working tree local.** Codex ejecutado en el equipo Windows debe:

1. Verificar git status/branch/remote para backend y web; comprobar archivos untracked e ignorados.
2. Revisar cambios frente al commit remoto y realizar auditoría de secretos antes de staging.
3. Excluir P12/PFX/PEM privados, credenciales, base de datos, XML fiscal real, backups, tokens, datos del piloto, reportes privados y archivos Doppler.
4. Crear commits de cambios locales legítimos y push de codex/ride-consolidation a origin; no forzar.
5. Integrar los dos PR académicos con su última versión; resolver conflictos con revisión, preservando la versión local más reciente del producto y los entregables RA1–RA4.
6. Comprobar CI backend/CI web y recrear las evidencias sobre el commit final; después proponer PR verificado a main.
7. Entregar al docente el enlace a un **commit o tag inmutable**, no solo una rama que cambiará.

## Comandos de inspección sin efectos destructivos

~~~powershell
Set-Location 'D:\Programación\Proyectos Java\emitta'
git branch --show-current
git status --short --untracked-files=all
git diff --check
git log -1 --oneline
git remote -v

Set-Location 'D:\Programación\Proyectos Java\emitta-web'
git branch --show-current
git status --short --untracked-files=all
git diff --check
git log -1 --oneline
git remote -v
~~~

No usar git reset --hard, push --force o git add -A sin revisar lo que se incluye.

## Evidencias exactas para cierre RA4

En el backend con rama académica integrada:

~~~powershell
.\gradlew.bat clean build jacocoTestReport --console=plain
.\scripts\Test-IsolatedStorage.ps1
.\scripts\Test-OperationalLoad.ps1 -Profile academic-sustained -Vus 100 -Duration 5m -AdmissionConcurrency 1
~~~

Después, con capacidad Docker revisada:

~~~powershell
.\scripts\Test-OperationalLoad.ps1 -Profile academic-spike -Vus 500 -Duration 1m -DimensionedSpike -AdmissionConcurrency 1
~~~

Si 100 pasa y se necesita breakpoint detallado: repetir academic-sustained a 125, 150, 175 y 200 por separado, con métricas comparables. Si falla, documentar correctamente el fallo.

En el frontend con rama académica integrada:

~~~powershell
npm ci
npm run check
npm run build
npm run check:links
npm run test:admin
~~~

Archivos a conservar fuera de Git: reportes HTML de JaCoCo, summary.json k6 agregado, gráficas y capturas sin datos privados, resultados de Docker/HTTPS y comprobaciones de XML/RIDE TEST sin exportar contenido sensible. Se pueden subir **tablas desidentificadas** y capturas revisadas al directorio docs/academic/evidence una vez autorizadas.

## Capturas mínimas recomendadas

1. Inicio Emitta Web y docs/Scalar por HTTPS, fecha visible.
2. Administración del dueño, sin secretos/datos personales.
3. Respuesta 401 de API sin token y 200 autenticada con token oculto.
4. Estado AUTHORIZED de un documento TEST existente y comprobación de XML/RIDE cifrada/sin contenido.
5. Diagrama RA2 renderizado.
6. Gradle/JaCoCo con porcentaje real.
7. Dos gráficas k6 con RPS, avg, p90, p95, p99 y error rate.
8. Contenedores Docker staging, sin datos del piloto.
9. Commit SHA y CI verde.

## Entrega

- RA2: 17-10-2026.
- RA3: 23-10-2026.
- RA4: 29-10-2026.
- RA1 tenía fecha 03-10-2026: verificar aceptación de entrega tardía.

Una nota del 100% depende de evaluación docente. No se debe declarar cumplimiento completo mientras falten ejecución de pruebas académicas, cobertura, capturas y sincronización de cambios locales.
