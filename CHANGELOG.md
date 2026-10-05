# Changelog

## 3.0.3 — 2026-10-05

### Cambiado
- **Menú lateral comprimido por defecto**: todos los grupos arrancan cerrados y se despliegan a voluntad (cada uno por separado). Solo se abre solo el grupo de la página en la que estás. El estado que elijas se recuerda.

## 3.0.2 — 2026-10-05

### Cambiado
- Se quitó el recuadro provisional **«RA»** del acceso, el menú lateral, el portal del estudiante, el de apoderados y el quiosco: la marca es solo texto hasta que el instituto tenga su logo.

## 3.0.1 — 2026-10-05

### Cambiado
- **Acceso**: fondo más profundo (degradado hacia negro) y **formas en movimiento** (orbes de luz, anillos, cuadrados, triángulos, cruces y puntos que flotan y giran despacio). Capa decorativa `aria-hidden`; solo `transform`/`opacity`; se detiene con «reducir movimiento»; en móvil se simplifica.

## 3.0.0 — 2026-10-05

### Rediseño de la interfaz (ver `docs/DISENO.md`)
- Nuevo **sistema de diseño**: paleta OKLCH verificada con WCAG (claro y oscuro), escalas de espacio y tipografía, sombras y radios coherentes.
- **Acceso del personal** rediseñado, **menú por grupos plegables**, migas de pan, cabecera compacta en móvil, aviso de solicitudes más claro.
- **Portal del estudiante y de apoderados**: cabecera de bienvenida, formularios sin zoom en iPhone, carnet renovado.
- **Accesibilidad**: foco visible, «Saltar al contenido», `aria-current`, objetivos táctiles de 44 px, reducción de movimiento y alto contraste.
- Estados de carga que no mueven el diseño; respaldo para WebViews antiguos.

## 2.11.0 — 2026-10-05

### Añadido
- **Respaldo offline** (Sistema → Respaldo offline, administrador; web, app Android y programa de PC): exporta lo registrado sin internet a un archivo **`.rabackup`** (asistencias con hora de ingreso y salida, por curso y justificaciones, con huella SHA-256) y lo **importa después en las fechas originales**. Vista previa por fecha, sin duplicar, completa salidas faltantes, omite códigos desconocidos y rechaza archivos alterados o futuros. Documentado en `docs/RESPALDO-OFFLINE.md`.

## 2.10.0 — 2026-10-05

### Añadido
- **Horario con ventana de ingreso** (Calendario y horarios): inicio de clases + tolerancia (puntual), **tardanza hasta el cierre del ingreso**, hora desde la que se abre el ingreso y fin de clases; general o por carrera. Botón **«Usar horario tarde/noche (14:00–20:00)»**. El quiosco no registra antes de la apertura ni después del cierre y avisa por qué.
- **Salida solo después de una permanencia mínima** (por defecto **2 horas** desde el ingreso, configurable): quien sale temprano también espera, para evitar fugas. Si intenta antes, el quiosco le dice desde qué hora podrá salir. (Migración `010`.)

## 2.9.0 — 2026-10-05

### Añadido
- **Aviso de aprobación al estudiante**: cuando el administrador aprueba su registro, el estudiante recibe «¡Tu registro fue aprobado!». En la **app Android** llega como notificación aunque la app esté cerrada (consulta en segundo plano, ~15 min); con la **página abierta** se actualiza sola en segundos y, si dio permiso, también como notificación del navegador. Usa un token privado por alumno (migración `009`); sin datos personales.

## 2.8.3 — 2026-10-05

### Añadido
- **Solicitudes de ingreso**: aviso visible arriba («🔔 N solicitudes de ingreso», con insignia en el menú) cuando estudiantes se registran con el código del instituto y esperan aprobación, y una página **Registro → Solicitudes de ingreso** para aprobar o rechazar con foto, carrera, ciclo, DNI y apoderado. El administrador ve las nuevas sin recargar (se revisa cada 60 s).

## 2.8.2 — 2026-10-05

### Añadido
- **Quiosco: botón «Pantalla completa / Modo normal»** en la barra superior. Alterna sin salir del quiosco (el PIN sigue siendo necesario para salir). Se oculta en navegadores sin pantalla completa (iPhone/Safari).

## 2.8.1 — 2026-10-05

### Cambiado
- **Docentes ahora es solo lectura**: ya no se registran a mano. Muestra los docentes y coordinadores con cuenta (con foto, rol y carrera) y su conteo; al crear un usuario en «Personal y accesos» aparece solo. Los docentes cargados antes sin cuenta se siguen viendo, marcados «Sin cuenta». Migración `008` (directorio sin correos).

## 2.8.0 — 2026-10-05

### Añadido
- **Modo quiosco**: tablet fija en la puerta; el estudiante muestra su carnet (QR/NFC/lector USB) y se registra solo. Pantalla completa con PIN, foto y color, sonido y voz, ingreso y salida automáticos, funciona sin internet y se reanuda solo.
- **Hora de salida** (escáner y quiosco), con cola sin conexión; se ve en historial, reportes y portal de apoderados.
- **Periodos y cambio de ciclo** (vista previa, egreso del VI ciclo, historial de cierres).
- **Calendario** (feriados de Perú y días sin clases) y **horarios por carrera** con tolerancia; los feriados no cuentan como falta.
- **Alertas de inasistencia** con margen de faltas y aviso por WhatsApp.
- **Nómina mensual oficial** en PDF con firmas y días sin clases.
- **Historial de cambios** inalterable (disparadores en la base), sin datos personales.
- **Portal para apoderados** (`/apoderado/`) con código por alumno y límite de intentos.
- **Datos del modo local → online** desde el programa de PC, sin pisar nada.
- Migración `007` (aditiva) y pruebas SQL nuevas (calendario, salidas, historial, apoderados); pruebas unitarias del calendario, riesgo, promoción, fusión y quiosco.

## 2.7.0 — 2026-10-04

### Añadido
- **Crear usuarios del personal desde el panel** (correo + contraseña + rol), cambiar su contraseña y eliminarlos. Lo hace la Edge Function `gestionar-personal`, que valida que quien llama sea administrador y solo toca cuentas de su instituto.
- **Mi perfil**: cada persona del personal sube su **foto** (se muestra en el menú y en la lista de personal), edita su nombre y cambia su contraseña. Bucket privado `fotos-personal` (migración 006).
- Funciona igual en la web, el programa de PC (modo online) y la app Android.

## 2.6.0 — 2026-10-04

### Añadido
- **Personal y accesos** (administrador): asigna Administrador, Docente o Coordinador a cuentas existentes y quita accesos, sin SQL (migración 005).
- **Docentes y coordinadores pueden publicar comunicados** y ver/compartir el código de registro; eliminar o regenerar sigue siendo del administrador.
- **Notificaciones de comunicados en Android** (docente y estudiante), sin Firebase: consulta en segundo plano con un token de avisos por instituto.
- Documento `docs/ROLES.md`.

## 2.5.0 — 2026-10-04

### Añadido
- **Programa de PC (.exe) para Windows** (`desktop/`, Electron): portable e instalador, con todas las librerías incluidas; abre y funciona sin internet.
- **Modo online / modo local** (solo en el .exe): el modo local usa una base de datos propia del equipo, sin cuenta ni internet; el online usa Supabase con usuario y contraseña. Selector en el inicio de sesión y en el menú lateral, siempre con confirmación. Cada modo guarda sus datos aparte y se conservan al cambiar.
- **Android:** minSdk 24 (Android 7+) con guardado de archivos alternativo para Android 7–9, e icono para versiones sin iconos adaptativos.
- **`app-config.json`:** la URL de la web de las apps Android se puede cambiar sin reinstalar.
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
