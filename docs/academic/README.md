# Emitta — entrega universitaria de API (RA1–RA4)

**Universidad Politécnica Salesiana · Octubre de 2026**

Este directorio organiza los entregables del proyecto de APIs según los resultados de aprendizaje solicitados. Se basa en la rama codex/ride-consolidation y los informes de ensayos aportados por el responsable.

| Resultado | Entregable | Fecha de la rúbrica | Archivo |
| --- | --- | --- | --- |
| RA1 / U1 | Visión, caso de negocio y monetización | 03-10-2026 (vencida) | [RA1-negocio.md](RA1-negocio.md) |
| RA2 / U2 | Estilo arquitectónico y patrones; diagramas | 17-10-2026 | [RA2-arquitectura.md](RA2-arquitectura.md) |
| RA3 / U3 | ERD y especificación OpenAPI | 23-10-2026 | [RA3-modelo-contrato.md](RA3-modelo-contrato.md) |
| RA4 / U4 | Código, seguridad, pruebas, carga, contenedores | 29-10-2026 | [RA4-desarrollo-pruebas.md](RA4-desarrollo-pruebas.md) |

Archivos adicionales:
- [Matriz de rúbrica y evidencias](matriz-rubrica.md): distingue verificaciones documentadas de acciones aún pendientes.
- [Guion de presentación](guion-exposicion.md): demostración reproducible sin emitir otra factura al SRI.
- [Perfil de pruebas exigido](k6-plan-rubrica.md): ramp-up/meseta/ramp-down/spike y plan de medición.
- [Cierre de publicación y evidencias](cierre-publicacion.md): comandos seguros para sincronizar cambios locales y completar RA4.

## Evidencias principales del repositorio

- [Contrato OpenAPI completo](../../src/main/resources/static/openapi/emitta-openapi.yaml).
- [ERD conceptual original](../domain/erd.md); debe contrastarse con las migraciones efectivas V1–V14 de esta rama.
- [Decisiones arquitectónicas (ADR)](../architecture/adr/README.md).
- [Ensayos de carga y límites](../operations/admission-capacity-stage5.md).
- [Scripts de carga reproducibles](../../scripts/Test-OperationalLoad.ps1).
- [Despliegue staging](../operations/emitta-publication.md).
- [Prueba fiscal controlada](../operations/staging-fiscal-onboarding.md).
- [Política de proveedor sin resolver](../provider-regulatory-policy.md).
- [Sitio web](https://emitta.nexorf.dev) y [API staging](https://api-emitta.nexorf.dev).

## Honestidad académica

- Una respuesta HTTP 202 es aceptación durable; NO autorización del SRI.
- La prueba fiscal autorizada y el XML/RIDE constituyen evidencia de una ejecución TEST controlada, no homologación comercial ni automatismos habilitados.
- Los ensayos k6 miden **admisión REST con ejecución fiscal desactivada**.
- El escenario de 100 VUs validado no demuestra 200 VUs ni un SLA; el de 200 VUs falló umbrales.
- Los porcentajes de JaCoCo y las pruebas específicas de la rúbrica solo se presentan como resultados cuando se ejecuten.
- La base de staging y los datos del piloto NO deben publicarse, exportarse ni alterarse para preparar este trabajo.
- Los nuevos documentos en esta rama no significan que el trabajo local más reciente del 15 de octubre ya esté publicado.

## Checklist de entrega

- [x] RA1–RA4 redactados con alcance y límites.
- [x] Diagramas Mermaid que pueden renderizarse en GitHub.
- [x] Inventario del modelo y contratos enlazados.
- [x] Evidencias existentes de seguridad, integración y carga documentadas.
- [ ] Confirmar últimos cambios locales y sincronizarlos sin pisar archivos.
- [ ] Medir cobertura real con JaCoCo y adjuntar HTML/XML de ejecución.
- [ ] Ejecutar escenarios académicos 100–200 VUs con rampa y spike de 1–2 min **solo en Testcontainers**.
- [ ] Capturar evidencias HTTPS/web autenticada de la versión efectivamente desplegada.
- [ ] Añadir evidencia de CI ejecutado; no basta crear un workflow.
- [ ] Confirmar que RA1 pueda entregarse fuera de plazo.

La calificación depende de la rúbrica y la evaluación docente; este repositorio no garantiza una nota específica.
