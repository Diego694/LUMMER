# Sistema de diseño

Rediseño visual aplicado a todo el sistema (acceso del personal, panel interno, portal del estudiante, portal de apoderados y quiosco). Vive en `assets/css/diseno.css`, que **se carga después de `styles.css`** y lo refina sin romper sus clases. Se guía por la skill `diseno-ui-referencias`.

## Principios aplicados
| Área | Qué se hizo |
|---|---|
| **Color** | Paleta derivada en **OKLCH** (matiz 265 para azul y neutros, 75 ámbar, 172 verde, 27 rojo) con claridad uniforme entre claro y oscuro. Todos los pares texto/fondo se **verificaron con WCAG**: texto ≥ 4,5:1 (principal ≥ 7:1), bordes de campos ≥ 3:1, foco ≥ 3:1. Se comprobó también en pantalla (7 páginas × 2 temas): ningún texto bajo el umbral. |
| **Tipografía** | Escala fija (12–32 px), Inter para la interfaz, Space Grotesk para títulos, JetBrains Mono para códigos; números tabulares en tablas y relojes; párrafos a ≤ 68 caracteres; `text-wrap: balance/pretty`. |
| **Espacio y forma** | Escala de 4 px (`--s-1 … --s-12`), radios 10/16/22, sombras en 3 niveles. |
| **Accesibilidad** | Foco visible siempre (3 px, ≥ 3:1), «Saltar al contenido», `aria-current="page"`, grupos del menú con `aria-expanded`, pestañas con `aria-selected`, errores con icono (no solo color), objetivos táctiles de **44 px** en pantallas táctiles, `prefers-reduced-motion`, `prefers-contrast` y `forced-colors`. |
| **Estados** | Vacío (con qué hacer), carga (esqueleto que pulsa con `opacity`, sin mover el layout) y error (causa + acción) en cada pantalla de datos. |
| **Movimiento** | Solo `transform` y `opacity`, 120–200 ms, curva *ease-out*; nada que se repita decenas de veces al día se anima de más. |
| **Navegación** | Menú por **grupos plegables, comprimidos por defecto**: se despliegan a voluntad, recuerda lo que abriste y abre solo el grupo de la página actual; migas «Grupo / Página»; en móvil, cabecera compacta. |
| **Acceso** | Pantalla de inicio con propuesta de valor (quiosco, sin internet, alertas), contraseña con botón mostrar/ocultar y mensajes claros. |
| **Estudiante / apoderado** | Cabecera de bienvenida con los 3 pasos, formularios a 16 px (sin zoom en iPhone), carnet con QR grande y estado visible. |

## Compatibilidad
Los colores usan hex (derivados de OKLCH) y `color-mix()` solo en acabados; en WebViews anteriores a Chrome 111 hay reglas de respaldo (`@supports not`), de modo que nada se rompe en teléfonos antiguos.

## Cómo seguir
- Cambia colores en el bloque de tokens (`:root` / `:root[data-theme="dark"]`) de `diseno.css`; vuelve a verificar contrastes antes de publicar.
- Componentes nuevos: usa los tokens (`--s-*`, `--fs-*`, `--radius*`) y las clases existentes (`.card`, `.btn`, `.badge`, `.empty-state`…).

## Refinamientos v3.1

| Mejora | Dónde | Criterio |
|---|---|---|
| **1. KPIs y tendencias** | `assets/js/ui.js`, `assets/js/pages/dashboard.js`, `assets/css/diseno.css` | Filo superior de 2px del color del tono. Píldoras `.kpi-delta` con colores semánticos (verde/rojo) e iconos direccionales ▲/▼ para no depender solo del color. |
| **2. Gráficos de Dashboard** | `assets/js/pages/dashboard.js` | Dona con porcentaje y texto «Presentes» al centro mediante plugin inline de Chart.js; línea guía punteada de meta al 80% en gráfico de ciclos; lectura dinámica de variables CSS para modo claro y oscuro. |
| **3. Matriz mensual** | `assets/js/pages/reportes.js`, `assets/css/diseno.css`, `assets/css/styles.css` | Fichas uniformes para todos los estados (incluyendo P con fondo suave); columna fija `.sticky` con sombra lateral dinámica al desplazarse horizontalmente. |
| **4. Cabeceras y paginador** | `assets/js/pages/mantenimiento.js`, `assets/css/diseno.css` | Cabeceras fijas `th` con elevación y borde de separación; paginador `.pager` agrupado como botonera compacta segmentada con áreas táctiles ≥ 44px en pantallas táctiles. |
| **5. Selects refinados** | `assets/css/diseno.css` | Flecha chevron SVG propia vía data URI con `appearance: none;`, alineada a 42px de altura (44px en `pointer: coarse`), con regla de respaldo en navegadores antiguos. |
| **6. Diálogo de confirmación** | `assets/js/ui.js`, `assets/css/diseno.css` | Icono circular de severidad (alerta roja para peligro / info azul para general) previo al mensaje; pie `.modal-foot` delimitado con borde superior; trampeo de foco y Escape intactos. |
| **7. Menú lateral** | `assets/css/diseno.css`, `assets/css/styles.css` | Iconos de `.nav-item` dentro de contenedor cuadrado redondeado de 28px mediante CSS; títulos de grupo `.nav-group-title` sutiles con `var(--side-soft)`, tipografía de 12px (`--fs-xs`) y `opacity: 1` verificados con contraste ≥ 4.5:1 en temas claro y oscuro. |
| **8. Chip de conectividad** | `assets/css/diseno.css` | `#net-chip` y `.net-chip` estilizados como píldora con micro-borde, punto semántico verificado en contraste (≥ 4.5:1) y parpadeo restringido a `transform`/`opacity` desactivado en `prefers-reduced-motion`. |
| **9. Quiosco moderno** | `assets/js/pages/quiosco.js`, `assets/css/diseno.css` | Tarjeta oscura con anillo perimetral luminoso del tono del resultado (verde, ámbar, rojo, azul) y títulos ≥ 28px legibles a 2 metros; contadores `#q-contadores` presentados como mini-tarjetas de datos tabulares. |
| **10. Carnet institucional** | `assets/css/diseno.css`, `assets/css/styles.css`, `assets/css/estudiante.css` | Fondo con patrón geométrico sutil (repeating-linear-gradient al 4–6%); cinta superior con el nombre real del instituto; contenedor del QR blanco liso `#fff` sin patrones encima; ritmo segmental en barra de vigencia. |
| **11. Esqueletos de carga** | `assets/css/diseno.css` | Variación rítmica de anchura (`:nth-child` al 100%, 80%, 45%, 90%) en `.skeleton`, conservando la animación de pulsación por opacidad (`sk-pulse`). |
| **12. Foco adaptativo** | `assets/css/diseno.css` | `:focus-visible` que adapta su curvatura (`border-radius: inherit`) en píldoras, insignias, chips y botones redondeados manteniendo contraste ≥ 3:1 con variables `--focus` y `--amber`. |

