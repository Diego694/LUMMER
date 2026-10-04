# Pruebas y auditoría

Resultados de la verificación realizada sobre esta versión (4 de octubre de 2026), sirviendo la app con `scripts/serve.py` (misma CSP que nginx) y manejándola en un navegador real.

## Cómo reproducir

| Qué | Comando |
|---|---|
| Servidor con cabeceras de producción | `python scripts/serve.py 8080` |
| Verificaciones estáticas | `python scripts/check.py` |
| Tests unitarios + adaptador Supabase simulado | abrir `http://127.0.0.1:8080/tests/tests.html` |
| Tests unitarios en Node 20 (lo que corre CI) | `npm test` |

## Resultados

### Versión 2.2.0 (portal del estudiante) — añadido
- **43/43 tests** en `tests/tests.html` (22 unitarios, 8 adaptador, 13 PWA/puente/portal): se suman la censura de apellidos y el adaptador del portal (parámetros de las funciones SQL, ruta `<uid>/foto-*.jpg`, borrado de fotos anteriores, URL firmada cacheada, mensajes de error, código duplicado).
- **Recorrido completo en modo demo** (estudiante → docente): validaciones del registro (contraseñas distintas, código inválido, falta de consentimiento), QR único generado, foto subida y comprimida, QR **sin aprobar → alerta "pendiente" y no registra**, revisión con foto y aprobación, y escaneo posterior → **alerta con foto y `Lucía María Qu**** Fl****`**, asistencia registrada y descarga del carnet por el puente Android simulado.
- Ambas apps cargan bajo la **CSP de producción sin violaciones** (ajustada para permitir imágenes de `*.supabase.co`).
- Las **dos APK compilan** (`app.registroacademico` y `app.registroacademico.estudiante`), firmadas y con la URL correcta embebida.

**No verificado en 2.2.0:** la migración SQL contra la base real (ver estado en el resumen de la entrega), la cámara/selfie en un teléfono, la subida real al bucket de Storage y las políticas de `storage.objects`, y el APK del estudiante en un dispositivo.

### Versión 2.1.0 (PWA + APK) — añadido
- **35/35 tests** en `tests/tests.html`: a los 27 anteriores se suman 6 del **service worker** (se ejecuta su código real contra un entorno simulado: red primero y revalidación `no-cache`, copia offline, fallback de navegación a `index.html`, cache-primero para CDN, y que **no** intercepta Supabase, otros orígenes ni peticiones no-GET) y 2 del **puente Android** simulado (el archivo llega a `saveFile` en base64 y sin puente usa `<a download>`).
- En la app real con `AndroidBridge` simulado: descarga de CSV, PNG y PDF individual (29 KB) y masivo (65 carnets, 1,8 MB) → `saveFile`; flujo **NFC nativo** (`nfcState` → botón → `onNativeNfc` → asistencia registrada).
- `check.py` ahora valida manifiesto, iconos y que **todos** los módulos JS estén en el precache del SW (22 archivos).
- Corregido al revisar nginx: los `add_header` de cada `location` anulaban la CSP en `/assets/` e `index.html`.

**No verificado en 2.1.0:** (1) el **APK no se ha compilado** — no hay Android SDK/Gradle aquí; el código nativo no pasó por un compilador y no se ha ejecutado en un teléfono; (2) el **service worker no pudo registrarse en vivo** porque el navegador integrado de este entorno rechaza incluso un SW trivial, por eso se probó su lógica simulada; (3) cámara y NFC físicos.

### Automatizados — 27/27 (v2.0.0)
- **19 unitarios** (`tests/unit.js`): escape HTML, fechas locales sin desfase UTC, días hábiles, porcentajes, CSV (comillas/saltos/BOM), tardanza, resumen del día, series, agrupación por grado con orden natural, baja asistencia, validación y deduplicación del CSV de importación.
- **8 del adaptador de producción** (`tests/adapter.js`) contra un cliente Supabase simulado: paginación de 1000 filas, filtro por `colegio_id` en toda lectura, traducción del error `23505` a `duplicate`, upsert masivo con `ignoreDuplicates`, insert vs update, tablas no permitidas, perfil inexistente, lotes de 200 en importación.
- **`check.py`**: 46 imports JS válidos, referencias de `index.html` existentes, 8/8 tablas con RLS, y detección de `service_role` (probado con una clave falsa: falla; con una anon key: pasa).

### Recorrido funcional en navegador (modo demo)
| Flujo | Resultado |
|---|---|
| Las 12 páginas renderizan sin error ni excepción | ✔ |
| Login: contraseña errónea → mensaje; correcta → dashboard; logout; persistencia de tema | ✔ |
| Registro por código: éxito, duplicado, código inexistente, alumno **inactivo rechazado** | ✔ |
| Registro por alumno y **masivo por grado** (26 registros) | ✔ |
| CRUD alumno: validación de requeridos, **código duplicado rechazado**, alta, búsqueda, edición, baja con confirmación | ✔ |
| Niveles/grados: borrado **bloqueado** si hay dependientes; alta; **grado duplicado rechazado** | ✔ |
| Importación CSV: vista previa, líneas omitidas (vacías y duplicadas), aviso de nivel/grado inexistente, importación | ✔ |
| Carnet: vista previa con QR, canvas, PNG y PDF (765 KB) generados; descarga masiva con conteo | ✔ |
| Historial por alumno y asistencia por grado con KPIs | ✔ |
| Dashboard: KPIs, 4 gráficos, tablas, exportación CSV | ✔ |
| Accesibilidad: `Esc` cierra, `Tab`/`Shift+Tab` atrapados en el modal, foco devuelto al origen | ✔ |
| Responsivo (375 px): 0 px de desborde horizontal en las 12 páginas; menú lateral abre y se cierra al navegar | ✔ |
| **CSP estricta** activa: 0 violaciones en todo el recorrido (gráficos, PDF, QR incluidos) | ✔ |

## Defectos del original corregidos (auditoría)

| # | Problema en `CORREGIR.html` | Corrección |
|---|---|---|
| 1 | El gráfico de 7 días usaba `toISOString()` (UTC) mientras el resto usaba fecha local → desfase de un día por la tarde/noche | `dateStr()` local en todo el código |
| 2 | Los errores de Supabase se ignoraban en altas/bajas (`await sb…delete()` sin revisar) y `registrarAsistencia` mostraba éxito aunque fallara | Todas las operaciones propagan el error y se informa al usuario |
| 3 | Alumnos **inactivos** podían registrar asistencia | Se rechazan y se explica |
| 4 | Se podían borrar niveles/grados con alumnos, dejando datos huérfanos | Borrado bloqueado con mensaje de cuántos dependientes hay |
| 5 | La hora se guardaba con `toLocaleTimeString('es-PE')` (p. ej. “08:05 a. m.”) → no ordenable ni comparable | `HH:MM` 24 h; tardanza configurable |
| 6 | `onclick="deleteNivel('${esc(n)}')"`: ids/nombres interpolados en atributos (se rompe con comillas y es superficie de inyección) | Delegación de eventos con `data-action` |
| 7 | PostgREST limita a 1000 filas: padrones grandes y rangos de asistencia se truncaban en silencio | Lectura paginada |
| 8 | Carnet descargable con la marca fija “CAMPUS VIRTUAL” | Usa el nombre del colegio |
| 9 | Gráficos en `<canvas>` manual con colores fijos (ilegibles en tema oscuro, sin tooltips ni accesibilidad) | Chart.js con colores por tema, tooltips y `aria-label` |
| 10 | Importar CSV sin informar filas descartadas ni duplicados internos | Informe por número de línea |
| 11 | Modales sin foco, sin `Esc`, sin etiquetas ARIA | Modal accesible con trampa de foco |
| 12 | Archivo único de 1270 líneas sin separación de capas, sin esquema SQL, sin tests ni forma de probar sin credenciales | Estructura modular, `schema.sql` con RLS, tests, modo demo |
| 13 | (Hallazgos propios, corregidos) Carrera entre carga async y cambio de página; selector que cambiaba en silencio un grado inexistente; caché de “hoy” obsoleta tras medianoche | Tokens de versión, opción conservada, `asegurarHoy()` |

## Lo que NO se pudo verificar en este entorno

Para no dar falsas garantías:

- **Supabase real:** no hay credenciales. El adaptador se probó contra un cliente simulado, y `schema.sql` **no se ejecutó** contra un PostgreSQL. Revisa el SQL en tu proyecto (es idempotente) y prueba un login real antes de usarlo en producción.
- **Docker / nginx:** este equipo no tiene Docker. `nginx.conf` y `Dockerfile` no se construyeron aquí; la CSP sí se validó a través de `serve.py`, que lee las mismas cabeceras de `nginx.conf`. El job de CI construye y prueba la imagen en su primera ejecución.
- **GitHub Actions:** los workflows no se ejecutaron (no se ha subido el repositorio). `npm test` tampoco se ejecutó localmente porque no hay Node instalado; los mismos tests sí pasan en el navegador.
- **Hardware:** la lectura real de QR con cámara y de NFC no se probó (el entorno no tiene cámara ni NFC). Se probó la ruta completa de registro por código, que es la misma que usan QR y NFC tras decodificar.
