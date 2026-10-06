# Changelog

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
