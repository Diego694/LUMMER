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
| **Navegación** | Menú por **grupos plegables** que recuerda su estado y abre el de la página actual; migas «Grupo / Página»; en móvil, cabecera compacta. |
| **Acceso** | Pantalla de inicio con propuesta de valor (quiosco, sin internet, alertas), contraseña con botón mostrar/ocultar y mensajes claros. |
| **Estudiante / apoderado** | Cabecera de bienvenida con los 3 pasos, formularios a 16 px (sin zoom en iPhone), carnet con QR grande y estado visible. |

## Compatibilidad
Los colores usan hex (derivados de OKLCH) y `color-mix()` solo en acabados; en WebViews anteriores a Chrome 111 hay reglas de respaldo (`@supports not`), de modo que nada se rompe en teléfonos antiguos.

## Cómo seguir
- Cambia colores en el bloque de tokens (`:root` / `:root[data-theme="dark"]`) de `diseno.css`; vuelve a verificar contrastes antes de publicar.
- Componentes nuevos: usa los tokens (`--s-*`, `--fs-*`, `--radius*`) y las clases existentes (`.card`, `.btn`, `.badge`, `.empty-state`…).
