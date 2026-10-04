# Changelog

## 2.5.0 — 2026-10-04

### Añadido
- **Programa de PC (.exe) para Windows** (`desktop/`, Electron): portable e instalador, con todas las librerías incluidas; abre y funciona sin internet.
- **Modo online / modo local** (solo en el .exe): el modo local usa una base de datos propia del equipo, sin cuenta ni internet; el online usa Supabase con usuario y contraseña. Selector en el inicio de sesión y en el menú lateral, siempre con confirmación. Cada modo guarda sus datos aparte y se conservan al cambiar.
- Workflow `windows.yml`: compila, prueba el .exe sin red (cambio de modo y datos conservados) y lo publica en la release `pc-latest`.
## 2.4.0 — 2026-10-04

### Añadido (preparación para uso real)
- **Modo sin internet:** las asistencias se guardan en el teléfono y se envían solas (sin duplicar); copia local de datos y apertura sin sesión.
- **Hora del servidor** y zona America/Lima: cambiar la hora del teléfono no altera los registros.
- **Roles** Administrador / Docente / Coordinador (por carrera) con RLS restrictivas (migración 004).
- **QR dinámico** firmado (HMAC, 30 s) en el carnet y verificación en el docente (`QR_MODO`).
- Portal del estudiante: recuperar contraseña, CAPTCHA opcional, contacto del apoderado, eliminar mi cuenta y datos, política de privacidad.
- **Asistencia por curso/hora**, **avisos a apoderados** (WhatsApp), **reportes mensuales**, **justificaciones** y **panel por carrera**.
- **Sistema:** diagnóstico, respaldo JSON y registro de errores.
- Entorno de pruebas (`entorno.html`), aviso para navegadores antiguos, keep-alive de Supabase.
- Documentación: PILOTO, OPERACION, PRIVACIDAD, SEGURIDAD, ENTORNOS.

## 2.3.1 — 2026-10-04

### Cambiado
- **Ciclos solo del I al VI.** «Crear ciclos» se hace con botones I–VI (sin texto libre), y el nombre se compone con formato fijo: `APSTI · IV CICLO` / `APSTI · IV CICLO · SECCIÓN A`.
- **Ciclos y salones**: una tarjeta por carrera con tabla ordenada (ciclo en insignia romana, salón y alumnos).
- Los selectores de ciclo (docente y estudiante) se ordenan I → VI, luego por salón, y no repiten la carrera cuando ya está elegida.
- Datos demo con el formato nuevo.

## 2.3.0 — 2026-10-04

### Nuevo
- **Página «Código de registro»**: generar, copiar, compartir por WhatsApp (con enlace del APK), QR del portal y código propio; acceso desde el dashboard y desde «registros por aprobar».
- **Carreras y ciclos**: «Niveles/Grados» pasan a **Carreras** y **Ciclos y salones**. Nuevo **Crear ciclos** (varios a la vez, rangos `I-VI` y salones `A, B`), con vista previa y sin duplicar. Cada ciclo es independiente y muestra su carrera (`MECANICA ELECTRICA III`) sin repetirla.
- El CSV de alumnos acepta las columnas `carrera` y `ciclo` (`nivel` y `grado` siguen valiendo).

### Cambiado
- Todo el texto visible pasa de «colegio» a **instituto**; el portal y su APK se llaman **Mi Carnet Institucional** y el APK del docente **Asistencia Institucional**. Los nombres técnicos de la base (`colegios`, `colegio_id`, `niveles`, `grados`) no cambian.
- Los datos de demostración usan carreras de ejemplo (MECANICA ELECTRICA y APSTI).

### Corregido
- El acceso directo a «registros por aprobar» no navegaba (la delegación de eventos cancelaba el clic).

## 2.2.0 — 2026-10-04

### Nuevo
- **Portal del estudiante** (`estudiante/` + APK **Mi Carnet Escolar**): cuenta, registro con código del colegio, foto (cámara o galería, recortada a 480 px) y carnet descargable con QR único.
- **Alerta de asistencia con foto** para el docente, con apellidos parcialmente censurados (`censurarNombre`).
- Aprobación de estudiantes (revisar foto y datos) y generación del **código de registro** del colegio.
- Migración `002_estudiantes.sql`: columnas, funciones `SECURITY DEFINER`, bucket privado `fotos-alumnos` y políticas.
- Dos APK desde una misma base nativa (flavors `docente` y `estudiante`).
- Modo demo forzable con `?demo=1` (datos locales compartidos entre ambas apps).

### Seguridad
- Los estudiantes no reciben fila en `perfiles`; solo leen su propio registro y usan funciones acotadas.
- La CSP permite imágenes de `*.supabase.co` (fotos firmadas).

## 2.1.0 — 2026-10-04

### Nuevo
- **PWA**: `manifest.webmanifest`, iconos y `sw.js` (arranque sin conexión; archivos propios *red primero* con revalidación, para que cada publicación llegue sola).
- **App Android** (`android/`): envoltorio WebView que abre la web publicada → actualizaciones automáticas sin reinstalar. Cámara, descargas a *Descargas*, selector de archivos y **NFC nativo**. CI (`android.yml`) que compila, firma y publica el APK en una release de enlace estable; `scripts/make_keystore.py` para la clave de firma.
- Versión de la web visible en el menú lateral.

### Cambiado
- Descargas (CSV/PNG/PDF) unificadas en `downloadFile`, compatibles con el navegador y con el APK. Los PDF de carnets usan JPEG: ~96 % más livianos.

### Corregido
- `nginx.conf`: los `add_header` dentro de `location` anulaban la CSP en `/assets/` e `index.html`; ahora cada bloque incluye `security-headers.conf`.

## 2.0.0 — 2026-10-04

Reescritura profesional del archivo único `CORREGIR.html`.

### Nuevo
- Interfaz y **dashboard** rediseñados: KPIs, tendencia, estado del día, asistencia por grado, alumnos por nivel, últimos ingresos, alertas de baja asistencia, periodo 7/14/30 días, auto‑refresco y exportación CSV.
- Detección de **tardanzas** (hora límite configurable).
- **Modo demo** sin servidor con datos de ejemplo.
- Exportación CSV en asistencia por grado y por alumno.
- Importación CSV con informe de errores por línea.
- `supabase/schema.sql` con tablas, índices y **RLS** por colegio y rol.
- Docker (nginx + CSP), docker-compose, GitHub Actions (CI + Pages), tests y documentación.

### Cambiado
- Arquitectura modular con módulos ES (sin build), router por hash, tema claro/oscuro con preferencia del sistema.
- Hora de asistencia en formato 24 h `HH:MM`.

### Corregido
Ver la tabla de defectos en [docs/PRUEBAS.md](docs/PRUEBAS.md#defectos-del-original-corregidos-auditoría).
