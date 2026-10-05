# Changelog

## 3.1.1 — 2026-10-05

### Añadido
- **Pantalla Instituciones rediseñada**: tarjetas responsive, cabecera con identidad del acceso (fondo profundo con degradado azul-negro, orbes difuminados y formas geométricas animadas), métricas globales de instituciones, alumnos y personal, y botón para copiar el código de registro con área táctil accesible y confirmación por notificación.

### Cambiado
- Reemplazo de la tabla de instituciones por una cuadrícula responsive de tarjetas con borde superior semántico por estado (actual, activa e inactiva), acciones compactas con ajuste flexible sin desbordamiento horizontal y animaciones suaves de entrada respetando preferencias de movimiento reducido.

## 3.1.0 — 2026-10-05

### Añadido
- **Multi-institución**: un **administrador superior** crea y administra varias instituciones (Sistema → Instituciones): nombre, código de registro, renombrar, activar/desactivar, «Entrar» a cada una y asignar su administrador. También se pueden crear **desde la base de datos** con `select public.crear_instituto('Nombre', 'admin@correo')` (migración `011`).
- **El nombre de la institución se ve y se cambia dentro de la página** (Gestión → Mi instituto): aparece en el menú, el título de la pestaña, el acceso (recuerda la última institución), el carnet y los reportes.

### Cambiado
- Refinamientos visuales (ver `docs/DISENO.md`, sección «Refinamientos v3.1»): etiquetas de tendencia en los indicadores, dona con porcentaje central, matriz mensual más uniforme, selectores y confirmaciones rediseñados, quiosco y carnet más cuidados, esqueletos de carga variados y foco adaptativo.

## 3.0.3 — 2025-09-28

### Añadido
- **Auditoría**: registro automático de cambios en perfiles y colegios (migración `010`), página de auditoría en Mantenimiento con filtros y tabla cronológica.

### Corregido
- Mejoras menores de estabilidad en el quiosco y el portal del estudiante.

## 3.0.2 — 2025-09-15

### Añadido
- **Código de registro**: cada colegio tiene un código único; los nuevos usuarios lo usan para solicitar acceso al colegio correcto (migración `009`).
- Página de mantenimiento con información del código de registro.

## 3.0.1 — 2025-09-01

### Añadido
- **Portal del estudiante**: los alumnos vinculados a una cuenta pueden ver su carnet, asistencia, calificaciones y observaciones (migración `007`).
- **Quiosco de asistencia**: modo de registro rápido por selección de alumno (migración `006`).
- Reportes por sección y por alumno (migración `005`).

## 3.0.0 — 2025-08-15

### Añadido
- Versión inicial con soporte multi-colegio por RLS.
- Gestión de periodos, materias, grados, secciones, asignaciones, alumnos, asistencia, observaciones, calificaciones.
- Roles: Administrador, Docente, Orientador.
- Modo demo y modo local (sin conexión).
