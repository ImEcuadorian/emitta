# Matriz de cumplimiento y carpeta de evidencias

**Criterio:** ✅ preparado/documentado; ⚠ requiere ejecución, captura o ajuste; ❌ no demostrar como existente. No equivale a calificación docente.

| Unidad / RA | Requisito textual de la rúbrica | Estado | Evidencia / archivo | Acción necesaria |
| --- | --- | --- | --- | --- |
| U1 / RA1 | Caso de negocio | ✅ | [RA1-negocio](RA1-negocio.md) | Revisar fecha de entrega (03-10) |
| U1 / RA1 | Cadena de valor de APIs | ✅ | RA1 §5 | Presentar tabla |
| U1 / RA1 | Modelo de monetización | ✅ propuesto | RA1 §7 | Aclarar números hipotéticos |
| U2 / RA2 | Selección REST/GraphQL/gRPC | ✅ | [RA2](RA2-arquitectura.md) §2 | Entregar antes 17-10 |
| U2 / RA2 | Diagrama de arquitectura | ✅ documento | RA2 §3–6 (Mermaid) | Capturar render final |
| U2 / RA2 | Patrones y justificación | ✅ | RA2 §7–9, ADR-001–014 | Explicar Gateway diferido |
| U3 / RA3 | Modelo de datos | ✅ documento | [RA3](RA3-modelo-contrato.md) §3–5 | Verificar migraciones locales V15/V16 |
| U3 / RA3 | OpenAPI formal | ✅ en Git | [YAML backend](../../src/main/resources/static/openapi/emitta-openapi.yaml) | Comprobar snapshot exacto |
| U3 / RA3 | Esquemas/ejemplos/HTTP | ✅ | RA3 §6–7, OpenAPI | Validar enlaces y Scalar |
| U4 / RA4 | Seguridad JWT/OAuth2 | ✅ JWT | [RA4](RA4-desarrollo-pruebas.md) §2 | Evidencia 401/403/200 |
| U4 / RA4 | Pruebas unitarias/integración | ✅ según reportes | RA4 §3 | Ejecutar en commit final |
| U4 / RA4 | Porcentaje de cobertura | ⚠ | JaCoCo en Gradle | Ejecutar y guardar HTML/XML |
| U4 / RA4 | Stress/load 100–200 VUs | ✅ anteriores / ⚠ formato | RA4 §5–6 | Ejecutar rampa 2–3m, meseta 5–10m y bajada 1–2m |
| U4 / RA4 | Spike 500 VUs 1–2 min | ⚠ | Viejo spike 30 s | Ejecutar nuevo academic-spike |
| U4 / RA4 | RPS, promedio, p90/p95/p99 | ⚠ | k6 reportes antiguos p95/p99 | Extraer métricas nuevas |
| U4 / RA4 | Error rate <1 % bajo carga | ✅ solo 100 B | Reporte etapa 5 | No ocultar escenarios fallidos |
| U4 / RA4 | Breakpoint exacto | ⚠ | Entre 100 y 200 VUs | Experimentos 125/150/175 o definir intervalo |
| U4 / RA4 | Contenedores Docker | ✅ según reporte staging | ops/deployment y reportes | Captura de estado real |
| U4 / RA4 | API HTTPS en staging | ✅ según reporte | https://api-emitta.nexorf.dev | Captura desde entorno del estudiante |
| U4 / RA4 | Pipeline CI/CD | ⚠ | Workflow académico propuesto | Ejecutar y conservar run verde |
| Demostración | SRI TEST autorizado + PDF | ✅ evidencia privada reportada | documentos de prueba / SHA en servidor | Mostrar sin divulgar secretos |
| Producción comercial | Operación fiscal automática | ❌ | Política no resuelta | **Fuera del alcance universitario** |

## Entrega recomendada

- 17 octubre: RA1 (si el docente acepta entrega tardía) + RA2.
- 23 octubre: RA3 + YAML completo y ERD.
- 29 octubre: RA4 + screenshots + JaCoCo + 2 perfiles k6 rubricados + código publicado.
- Guion de exposición: [guion-exposicion.md](guion-exposicion.md).

## Regla sobre GitHub

La rama académica parte de codex/ride-consolidation publicada hasta el 14-10-2026. El 15-10 existen informes de cambios locales adicionales. **No atribuir a esta rama cambios locales todavía no enviados.** Sincronizar después en rama de integración protegida, revisar diff, conservar material sensible fuera del repositorio y generar evidencias del commit definitivo. No cambiar main sin revisión.

## Archivos que nunca deben subirse

Claves privadas/certificados PKCS#12, contraseñas, tokens JWT, Doppler config con valores, respaldos de DB/MinIO, XML fiscal de contribuyentes reales, archivos privados de CRL/operador, reportes que imprimen secretos y datos del piloto. Sí pueden versionarse hashes y reportes agregados desidentificados si la autorización lo permite.
