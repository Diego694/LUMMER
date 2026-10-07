# Changelog

## 4.0.0 — 2026-10-05

### Añadido
- **Superadmin · Conexiones de datos (migración 017)**: registra bases externas (Supabase/Postgres, Firebase/Firestore o una puerta de enlace REST para MySQL/MariaDB/MongoDB) con URL y llave pública (se rechazan llaves secretas), pruébalas, copia todos los datos de la institución con verificación de conteos o descarga un paquete JSON. Es una primera fase: cuentas, políticas de seguridad y archivos siguen en Supabase. Ver `docs/CONEXIONES.md`.
- **Panel: «Pasar lista» por curso y ciclo**: cada docente ve solo los cursos que el administrador le asignó (el administrador y el superadmin ven todos), elige curso y marca a cada alumno con un botón grande «Presente», escaneando su carnet (cámara o código) o con «Marcar a todos presentes». Muestra presentes, sin marcar y barra de avance, con filtro y búsqueda, y puede marcar a la vez el ingreso al instituto si el alumno aún no ingresó hoy. No requiere migración.
- **Migración 016 · alcance del docente en la base de datos**: políticas de lectura restrictivas para que un docente solo lea alumnos, cursos, carreras, ciclos, asistencias, justificaciones y avisos de los cursos que se le asignaron (el directorio de docentes, nada). Administrador, coordinador y estudiantes no cambian.
- **Docentes con alcance por curso**: el docente solo ve a los alumnos, carreras, ciclos y cursos que el administrador le asignó (sin asignaciones no ve ninguno) y ya no tiene Modo quiosco, Registro por QR / por alumno / masivo, Carnet, Código de registro, Carreras, Ciclos ni Docentes (estos últimos quedan para coordinador, administrador y superadministrador); marca asistencia desde «Pasar lista» eligiendo curso y ciclo. «Mi perfil» pasa al final del menú como «Mi cuenta» para todos los roles.
- **Rediseño del dashboard**: lo operativo va primero (últimos ingresos y alumnos que requieren atención antes que los gráficos por ciclo y carrera); la cabecera queda con «Pasar lista» y «Registrar asistencia» y Exportar CSV / Código de registro pasan a la tarjeta de ingresos; el % de hoy muestra un medidor contra la meta; entrada escalonada solo al abrir la página y respeta «reducir movimiento».
- **Rediseño del acceso y las páginas públicas**: el login queda sobrio (superficie quieta en lugar de formas animadas, entrada suave del formulario, foco visible y separador «o» antes del Aula virtual); privacidad y entorno heredan el esquema claro/oscuro, el color de barra y el foco visible, y respetan «reducir movimiento».
- **Aula: «Mis pendientes» del estudiante**: agenda con las actividades de todos sus cursos agrupadas en vencidas sin entregar, por entregar (la más próxima primero) y entregadas con su nota. No requiere migración.
- **Portal separado LUMMER Aula**: portal web independiente (`aula/`) exclusivo para personal (administrador y docentes) enfocado únicamente en el aula virtual (cursos, material, actividades, entregas y notas), con manifest PWA propio, soporte de navegación offline y botón «Aula virtual» en el login de `index.html`.
- **App nativa Android (Kotlin + Jetpack Compose)**: versión oficial principal para docentes y administradores con las 33 pantallas de la web portadas en su totalidad (registro QR con CameraX y Google ML Kit, quiosco con PIN y bloqueo, asistencia masiva y por curso, consultas, gestión, reportes, instituciones y configuración general).
- **Respaldo y sincronización offline en móvil**: base de datos local Room y sincronización garantizada en segundo plano con WorkManager.
- **Compilación y firma para Android 17 (API 37)**: `compileSdk 37`, `targetSdk 37`, `minSdk 24`, soporte para signing configs con keystore de producción en CI y distribución como release principal `app-latest` (`registro-academico.apk`) y por tags de versión en GitHub Actions.
- **Workflow de CI para Android nativo**: ejecución automatizada de pruebas unitarias JVM (`gradle testDocenteDebugUnitTest`) con JDK 21 Temurin y Gradle 9.6.0.
- **TypeScript gradual**: tipado estático progresivo en el frontend web con validación estricta de tipos `npm run typecheck` integrada en el pipeline de CI.

### Cambiado
- **App móvil anterior pasa a versión «Lite»**: el APK previo basado en WebView (`android/`, «Asistencia Institucional Lite») pasa a mantenimiento secundario y convive instalado en el dispositivo junto a la nueva app nativa gracias a identificadores de aplicación independientes. El portal del estudiante («Mi Carnet Institucional») se mantiene en la app Lite.

### Corregido
- **Cálculo de asistencia en matriz tipada**: corrección en el cálculo de la matriz tipada para que el estado de tardanza se contabilice adecuadamente como presente en las asistencias.

## 3.2.1 — 2026-10-05

### Corregido
- **Colisión de clase CSS `.stack` en panel de registros de sesión**: corrección del conflicto entre la clase de diseño (`display: grid`) y la regla de trazas de error renombrada a `.err-stack`, restaurando el diseño visual de «Registros de esta sesión» en Registro por QR y los formularios afectados.

## 3.2.0 — 2026-10-05

### Añadido
- **QR dinámico seguro por institución**: configuración en *Mi instituto* (`colegios.qr_modo`) con tres modos: `off`, `opcional` y `obligatorio` (por defecto activo como `obligatorio`). Migración `012_qr_modo.sql` con constraint y función `mi_registro()` actualizada.
- **Protección contra capturas en Android**: activación de `FLAG_SECURE` en el sabor de estudiante de la app Android nativa para bloquear capturas de pantalla y grabaciones en video.
- **Regeneración dinámica en portal de estudiante**: actualización instantánea del código QR al abrir la app o volver al foco (`visibilitychange`/`focus`), limpieza del canvas en segundo plano y ocultación del código de texto en modo obligatorio.
- **Aviso en carnets impresos**: advertencia visual para el personal indicando que con QR obligatorio los carnets impresos con QR estático no se validan por cámara y requieren NFC o código de respaldo.

### Cambiado
- **Ventana de tolerancia reducida**: reducción de tolerancia de HMAC-SHA256 de 2 a 1 ventana en `qr-seguro.js` (~60 s de validez máxima).
- **Descarga de carnet estudiantil adaptada**: en modos dinámicos (`opcional` y `obligatorio`), la descarga de imagen del carnet omite el QR estático y muestra el aviso de que el QR solo se visualiza en vivo dentro de la aplicación.

## 3.1.2 — 2026-10-05

### Corregido
- **Desborde horizontal en Calendario en móvil**: cabeceras de tarjeta (`.card-head`) configuradas con ajuste envolvente (`flex-wrap`) para que títulos largos y botones de configuración amplia no empujen el ancho de la página ni generen desplazamiento horizontal.
- **Cabeceras de tarjeta y de página que envuelven en móvil**: los títulos de tarjetas y páginas ocupan el ancho completo (100%) y los controles, selects e inputs se posicionan debajo ocupando todo el ancho disponible, con texto de botones en varias líneas si es necesario.
- **Filtros a ancho completo y 44px en móvil**: barras de filtros y herramientas (`.toolbar`) adaptadas en móvil a diseño vertical de ancho completo (100%) con altura táctil mínima de 44px en todas las pantallas de consultas y mantenimiento.
- **Tablas con ancho mínimo y sombra de scroll**: tablas con ancho mínimo razonable (560px) para desplazamiento horizontal fluido en pantallas táctiles sin estrujar columnas, nombres de alumnos en una sola línea y sombra/degradado lateral indicativo de contenido desplazable en tema claro y oscuro.
- **Objetivos táctiles de 44px**: botones compactos (`.btn-sm`), botones de solo icono (`.icon-only`) y controles de paginación con tamaño táctil mínimo de 44px en pantallas de 640px o menos.
- **Etiqueta «Institutos» sin cortes**: eliminación del corte de palabras a mitad de sílaba («INSTITUCIO / NES») en el hero de Instituciones mediante la etiqueta «Institutos», espaciado de letras normalizado y ajuste dinámico en resoluciones reducidas.
- **Aria-label en campos de contraseña del perfil**: accesibilidad mejorada en campos de cambio de contraseña en Mi Perfil y PIN de salida en Modo Quiosco mediante atributos `aria-label` descriptivos.
- **Modales con botones apilados en pantallas estrechas**: botones de pie de modal (`.modal-foot`) apilados al 100% de ancho con altura mínima de 44px en pantallas de hasta 480px.

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
