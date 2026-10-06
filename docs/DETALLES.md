# Sistema de Registro Académico — documentación técnica detallada

> Este documento conserva la descripción completa del proyecto (características, puesta en marcha, estructura y comandos). La presentación breve está en el [README](../README.md).

> Proyecto escolar que busca mejorar el control de asistencia de alumnos en sitios remotos del Perú, con mejoras continuas.

Aplicación web para el **control de asistencia institucional**: registro por carnet **QR**, **NFC** o código manual, dashboard con indicadores y alertas, carnets imprimibles, importación de alumnos por CSV y comunicados. Interfaz responsiva con tema claro/oscuro.

> Sin build ni dependencias de Node: HTML + CSS + JavaScript (módulos ES) en el cliente, **Supabase** (PostgreSQL + Auth + RLS) como backend, **Docker/nginx** para servirlo y **GitHub Actions** para CI y despliegue.

## Características

| Área | Qué incluye |
|---|---|
| **Dashboard** | KPIs del día (activos, presentes, ausentes, % asistencia vs. promedio), tendencia por día (puntuales / tardanzas / %), estado de hoy, asistencia por grado, alumnos por nivel, últimos ingresos, alumnos que *requieren atención*, comunicados. Periodo 7/14/30 días hábiles, auto‑refresco cada 60 s y exportación a CSV. |
| **Registro** | Escáner QR con cámara (frontal/trasera), lector NFC (Android + Chrome), código manual, registro por alumno y registro masivo por grado/fecha. Detecta duplicados, alumnos inactivos y tardanzas (hora límite configurable). |
| **Consultas** | Asistencia por grado y fecha (con KPIs y CSV) e historial por alumno con % de asistencia. |
| **Gestión** | CRUD de alumnos (paginado, búsqueda sin tildes, importación CSV validada), docentes, niveles, grados y comunicados. Protege contra borrados que dejarían datos huérfanos. |
| **Carnets** | Vista previa, PNG, PDF individual y PDF masivo por nivel/grado, con QR. |
| **Calidad** | Tema claro/oscuro, accesibilidad (foco, ARIA, `Esc`, trampa de foco en modales), CSP estricta, RLS por instituto y roles. |

## Inicio rápido (modo demo, sin servidor)

Requiere solo Python 3 (o cualquier servidor estático; los módulos ES no funcionan con `file://`).

```bash
python scripts/serve.py 8080
```

Abre <http://127.0.0.1:8080>. Mientras `assets/js/config.js` tenga los placeholders de Supabase, la app corre en **modo demo** con ~65 alumnos y 30 días de asistencia generados localmente (se guardan solo en tu navegador). Acceso demo: `demo@instituto.pe` / `demo1234`. El botón *Restablecer datos demo* regenera todo.

## Puesta en producción con Supabase

1. Crea un proyecto en [supabase.com](https://supabase.com).
2. En **SQL Editor** ejecuta [`supabase/setup_completo.sql`](../supabase/setup_completo.sql) (esquema + migraciones: tablas, índices, RLS, roles, portal del estudiante).
3. En **Authentication → Users** crea el usuario administrador y ejecuta el bloque *Alta de un instituto* que está al final del `schema.sql` (con el UUID de ese usuario).
4. Edita `assets/js/config.js` con tu `SUPABASE_URL` y `SUPABASE_ANON_KEY` (Settings → API). La *anon key* es pública por diseño; **nunca** pongas la `service_role`.
5. Sirve el sitio (Docker o GitHub Pages) — ver [docs/DESPLIEGUE.md](DESPLIEGUE.md).

## Portal del estudiante

Segunda app (web + APK **Lummer Estudiante**): el estudiante se registra con el código del instituto, sube su foto y obtiene su **carnet con QR único**. Al escanearlo, el docente ve una **alerta con la foto y los apellidos parcialmente censurados**. El instituto aprueba cada registro. Guía y reglas de privacidad en [docs/ESTUDIANTES.md](ESTUDIANTES.md).

## App Android (APK) y PWA

La misma web funciona en el navegador, como **PWA instalable** y como **APK**. El APK es un envoltorio nativo mínimo que abre tu web publicada, así que **cada cambio que publiques —incluida la conexión a la base de datos— llega solo, sin reinstalar el APK**. Añade lo que un WebView no trae: permiso de cámara, descargas a *Descargas*, selector de archivos y lectura NFC nativa. Detalle, compilación y firma en [docs/ANDROID.md](ANDROID.md).

## Estructura

```
index.html                  Shell de la aplicación (login + layout)
assets/css/styles.css       Sistema de diseño (tokens, claro/oscuro, responsivo)
assets/js/
  config.js                 Configuración (Supabase, hora límite, umbrales)
  main.js                   Arranque, sesión, router por hash, tema
  api.js                    Capa de datos: SupabaseBackend | DemoBackend (misma interfaz)
  state.js · ui.js          Estado en memoria · componentes (modales, formularios, toasts)
  stats.js · utils.js       Lógica pura (estadísticas, CSV, fechas) — con tests
  demo-data.js              Generador de datos demo
  pages/                    dashboard · registro · consultas · carnet · mantenimiento
sw.js · manifest.webmanifest  PWA: arranque sin conexión, actualización "red primero"
estudiante/                 Portal del estudiante (registro, foto, carnet QR)
android/                    Envoltorio nativo (WebView): 2 APK (docente y estudiante)
supabase/schema.sql         Esquema PostgreSQL + políticas RLS
supabase/migrations/        002_estudiantes.sql (portal del estudiante: columnas, funciones, fotos)
tests/                      Tests: lógica, adaptador Supabase, service worker y puente Android (tests/tests.html)
scripts/                    serve.py (servidor con CSP) · check.py · make_icons.py · make_keystore.py
Dockerfile · nginx.conf · security-headers.conf · docker-compose.yml
.github/workflows/          ci.yml · pages.yml · android.yml
docs/                       Arquitectura, despliegue, Android, guía de uso, pruebas
```

## Desarrollo y pruebas

```bash
python scripts/serve.py 8080     # servidor local con las mismas cabeceras que producción
python scripts/check.py          # imports, referencias, secretos y RLS
npm test                         # tests unitarios con Node 20 (opcional)
# o abre http://127.0.0.1:8080/tests/tests.html  → tests unitarios + adaptador Supabase simulado
```

Detalle de lo verificado y lo que no: [docs/PRUEBAS.md](PRUEBAS.md).

## Docker

```bash
docker compose up --build        # http://localhost:8080
```

## Documentación

- [Arquitectura](ARQUITECTURA.md) · [Despliegue](DESPLIEGUE.md) · [App Android](ANDROID.md) · [Portal del estudiante](ESTUDIANTES.md) · [Guía de uso](GUIA-DE-USO.md) · [Pruebas y auditoría](PRUEBAS.md) · [Programa de PC](PC.md) · [Piloto](PILOTO.md) · [Operación](OPERACION.md) · [Privacidad](PRIVACIDAD.md) · [Seguridad](SEGURIDAD.md) · [Entornos](ENTORNOS.md) · [Changelog](../CHANGELOG.md)

## Licencia

MIT — ver [LICENSE](../LICENSE).
