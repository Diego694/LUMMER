# Arquitectura

## Visión general

```
┌────────────── Navegador ──────────────┐        ┌──────────── Supabase ────────────┐
│ index.html                            │        │ Auth (correo + contraseña)       │
│  └ main.js (router, sesión, tema)     │  HTTPS │ PostgreSQL + Row Level Security  │
│     ├ pages/*  (vistas)               │◄──────►│  colegios · perfiles · niveles   │
│     ├ ui.js    (componentes)          │        │  grados · alumnos · docentes     │
│     ├ stats.js (lógica pura)          │        │  comunicados · asistencias       │
│     └ api.js ── SupabaseBackend       │        └──────────────────────────────────┘
│              └ DemoBackend (localStorage)
└───────────────────────────────────────┘
        servido por nginx (Docker) o GitHub Pages
```

No hay servidor propio: el cliente habla directo con Supabase y **la seguridad la imponen las políticas RLS**, no el JavaScript.

## Capas

| Capa | Archivo(s) | Responsabilidad |
|---|---|---|
| Datos | `api.js` | Dos adaptadores con la misma interfaz. `isDemoMode()` elige uno según `config.js`. Lanza errores con `code` (`duplicate`, `auth`, `profile`). Pagina lecturas de 1000 en 1000 (límite de PostgREST). |
| Estado | `state.js` | `DB`: caché en memoria de padrón, catálogos y asistencia de hoy. |
| Lógica | `stats.js`, `utils.js` | Funciones puras: resumen del día, series, agrupación por grado, baja asistencia, validación de CSV, fechas locales. **Cubiertas por tests.** |
| UI | `ui.js` | Iconos SVG, toasts, modales accesibles, `formModal` declarativo, `confirmDialog`, delegación de eventos (`data-action`). |
| Vistas | `pages/*.js` | Cada página exporta `{id, title, icon, group, render(root, params), onLeave?, onTheme?}`. `main.js` las registra en el router. |

### Decisiones

- **Sin build.** Módulos ES nativos y librerías por CDN con versión fijada: despliegue trivial y nada que compilar.
- **Router por hash** (`#/alumnos`, `#/carnet?id=…`): funciona en GitHub Pages sin reglas de reescritura y permite enlaces profundos.
- **Delegación de eventos** (`data-action`) en lugar de `onclick="…"` con ids interpolados: elimina una superficie de inyección y permite una CSP sin `unsafe-inline` para scripts.
- **Fechas locales.** `dateStr()` formatea en zona local; el original usaba `toISOString()` (UTC) en el gráfico, lo que desfasaba un día por la tarde/noche.
- **Hora en 24 h `HH:MM`.** Ordenable como texto y compatible con la columna `time`; la tardanza es una comparación de cadenas contra `HORA_LIMITE`.
- **Días de clase.** Para porcentajes por alumno se cuentan los días con al menos un registro, así feriados y días sin actividad no penalizan.

## Modelo de datos

| Tabla | Clave / restricciones |
|---|---|
| `colegios` | `id` |
| `perfiles` | `id` → `auth.users`; `colegio_id`; `rol` ∈ admin/docente/auxiliar |
| `niveles` | único `(colegio_id, nombre)` |
| `grados` | único `(colegio_id, nivel, nombre)` |
| `alumnos` | único `(colegio_id, codigo)`; `estado` ∈ ACTIVO/INACTIVO |
| `docentes` | `rol` ∈ Docente/Coordinador/Auxiliar/Administrativo |
| `comunicados` | `fecha` por defecto hoy |
| `asistencias` | único `(alumno_id, fecha)` → una asistencia por alumno y día; `ON DELETE CASCADE` desde alumnos |

`niveles.nombre` y `alumnos.nivel/grado` se relacionan por **texto** (compatible con la base original); por eso la UI impide borrar un nivel/grado con dependientes.

## Seguridad

- **RLS en las 8 tablas** (verificado por `scripts/check.py`). `mi_colegio()` y `es_admin()` son `SECURITY DEFINER` para evitar recursión sobre `perfiles`.
- Lectura: cualquier usuario del colegio. Escritura de catálogos/padrón: solo `admin`. Registrar asistencia: cualquier usuario del colegio, con `registrado_por = auth.uid()` obligatorio. Corregir/borrar asistencia: solo `admin`.
- **CSP** (en `nginx.conf` y replicada por `scripts/serve.py`): scripts solo de `'self'` y los CDN usados; `connect-src` solo a `*.supabase.co`; `frame-ancestors 'none'`; `object-src 'none'`.
- Salida HTML siempre escapada con `esc()`.
- La anon key en el cliente es normal en Supabase; `check.py` decodifica los JWT de `config.js` y falla si alguno tiene `role: service_role` (o una `sb_secret_…`).
- **Límite conocido:** GitHub Pages no permite cabeceras personalizadas, así que allí la CSP no aplica. Para la política completa usa el contenedor nginx o un proxy delante.

## Librerías (CDN, versión fijada)

Supabase JS 2 · Chart.js 4.4.1 · qrcodejs 1.0.0 · jsPDF 2.5.1 · jsQR 1.4.0 · PapaParse 5.4.1 · Fuentes: Inter, Space Grotesk, JetBrains Mono (Google Fonts).

## Extender

- **Nueva página:** crea `pages/mi-pagina.js` exportando el objeto de página y agrégala al arreglo `PAGES` de `main.js`.
- **Nuevo backend** (otra API): implementa la interfaz de `DemoBackend` en `api.js` (métodos `init, signIn, signOut, getProfile, userId, loadAll, asistencias*, registrar*, save, remove, upsertAlumnos`).
