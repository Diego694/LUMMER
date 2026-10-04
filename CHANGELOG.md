# Changelog

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
