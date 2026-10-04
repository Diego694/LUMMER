# Despliegue

## 1. Base de datos (Supabase) — obligatorio en producción

1. Crea el proyecto en Supabase.
2. **SQL Editor → New query**: pega y ejecuta `supabase/schema.sql`. Es idempotente.
3. **Authentication → Users → Add user**: crea al administrador (correo + contraseña).
4. Copia su UUID y ejecuta el bloque *ALTA DE UN COLEGIO* (comentado al final de `schema.sql`) reemplazando `UUID-DEL-USUARIO`.
5. **Settings → API**: copia *Project URL* y *anon public key* a `assets/js/config.js`.
6. **Authentication → URL Configuration**: agrega la URL pública del sitio como *Site URL*.
7. Recomendado: en **Authentication → Providers → Email** desactiva el registro libre (*Enable sign ups*) — los usuarios los crea el administrador.

Más usuarios (docentes/auxiliares): crea el usuario en Auth y agrega su fila en `perfiles` con `rol = 'docente'` o `'auxiliar'` (solo pueden consultar y registrar asistencia).

## 2. Opción A — Docker (recomendada: aplica la CSP y las cabeceras)

```bash
docker compose up --build -d      # http://localhost:8080
```

La imagen es `nginx:alpine` con solo `index.html` y `assets/`. Para HTTPS ponla detrás de tu proxy (Caddy, Traefik, nginx del host) o de un balanceador. **La cámara (QR) y el NFC exigen HTTPS** salvo en `localhost`.

## 3. Opción B — GitHub Pages

1. Sube el repositorio a GitHub (rama `main`).
2. **Settings → Pages → Build and deployment → Source: GitHub Actions**.
3. Cada `push` a `main` ejecuta `.github/workflows/pages.yml` y publica en `https://<usuario>.github.io/<repo>/`.

Limitación: Pages no permite cabeceras HTTP personalizadas, por lo que la CSP de `nginx.conf` no se aplica allí.

## 4. CI

`.github/workflows/ci.yml` en cada push/PR: `scripts/check.py` → `npm test` → `docker build` → arranca el contenedor y comprueba HTTP 200, la cabecera CSP y que `/tests/` no se exponga.

## 5. Configuración (`assets/js/config.js`)

| Clave | Descripción | Por defecto |
|---|---|---|
| `SUPABASE_URL` / `SUPABASE_ANON_KEY` | Credenciales públicas del proyecto. Con los placeholders → modo demo | placeholders |
| `HORA_LIMITE` | Ingresos posteriores (HH:MM) cuentan como tardanza | `08:00` |
| `UMBRAL_ASISTENCIA` | % mínimo; por debajo, el alumno aparece en *Requieren atención* | `85` |
| `ALUMNOS_POR_PAGINA` | Paginación del listado de alumnos | `20` |

## 6. Migrar desde la versión de un solo archivo

El esquema conserva los nombres de tablas y columnas del `CORREGIR.html` original (`alumnos`, `niveles`, `grados`, `asistencias`, `comunicados`, `docentes`, `perfiles`, `colegios`). Si ya tienes datos en Supabase: respalda, revisa que las tablas tengan `id uuid`, la restricción única `(alumno_id, fecha)` y la columna `hora` en formato `HH:MM`, y aplica las políticas RLS del `schema.sql`. La aplicación no requiere más cambios.

## Solución de problemas

| Síntoma | Causa probable |
|---|---|
| Pantalla de login con “No se encontró un perfil…” | El usuario existe en Auth pero falta su fila en `perfiles`. |
| Listas vacías tras iniciar sesión | Falta fila en `perfiles` o RLS sin aplicar (`schema.sql`). |
| “Ese código ya está en uso” | `(colegio_id, codigo)` duplicado. |
| La cámara no inicia | Sitio sin HTTPS, permiso denegado, o la cámara está en uso por otra app. |
| Página en blanco al abrir `index.html` con doble clic | Los módulos ES requieren HTTP: usa `python scripts/serve.py`. |
