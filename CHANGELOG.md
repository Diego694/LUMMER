# Changelog

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
