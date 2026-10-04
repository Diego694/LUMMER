# Guía de uso

## Flujo diario recomendado

1. **Ingreso de alumnos:** abre **Registro por QR** en una tablet o teléfono en la puerta, pulsa *Iniciar cámara* y cada alumno muestra su carnet. Verás su nombre, grado, hora y si llegó **puntual** o con **tardanza**.
2. **Sin carnet / sin cámara:** escribe el código en el campo manual, o usa **Registro por Alumno** (búsqueda por nombre).
3. **Salones completos:** **Registro Masivo por Grado** marca todo un grupo; los ya registrados aparecen bloqueados.
4. **Seguimiento:** el **Dashboard** muestra el % del día, la tendencia, los grados rezagados y los alumnos que *requieren atención*.

## Pantallas

| Pantalla | Para qué sirve |
|---|---|
| Dashboard | Indicadores del día y tendencia. Selector 7/14/30 días hábiles; *Exportar CSV* descarga el periodo. |
| Registro por QR | Cámara (con botón *Voltear*), NFC en Android/Chrome y código manual. Muestra el historial de la sesión. |
| Registro por Alumno | Filtro: todos / pendientes de hoy / ya registrados. |
| Registro Masivo por Grado | Elige nivel, grado y fecha (hasta hoy). En fechas pasadas la hora guardada es la hora límite. |
| Asistencia por Grado | Presentes/ausentes/tardanzas por fecha, con KPIs y CSV. |
| Asistencia por Alumno | Historial de 30 días hábiles, % de asistencia y CSV. |
| Carnet | Vista previa; PNG; PDF; *Descarga masiva* por nivel/grado. |
| Alumnos | Alta/edición/baja, búsqueda sin tildes, paginación e **Importar CSV**. Accesos directos a carnet e historial. |
| Docentes · Niveles · Grados · Comunicados | Mantenimiento de catálogos y avisos. |

## Importar alumnos por CSV

Columnas en la primera fila: `nombre, codigo, nivel, grado, apoderado, estado`. Obligatorias: **nombre** y **codigo**. Si falta `estado` se asigna ACTIVO. Si el código ya existe, el alumno **se actualiza**.
El modal muestra una vista previa, las filas omitidas con su número de línea (sin nombre/código, o código repetido en el archivo) y avisa si el nivel/grado no existe. Descarga la plantilla desde el mismo modal.

## Reglas del sistema

- Un alumno **solo puede registrar una asistencia por día**; un segundo intento se informa como duplicado.
- Los alumnos **inactivos no registran asistencia** y no cuentan en los porcentajes.
- **Tardanza** = hora de ingreso posterior a `HORA_LIMITE` (por defecto 08:00).
- No se puede eliminar un **nivel** con grados/alumnos ni un **grado** con alumnos.
- Eliminar un alumno borra también su historial de asistencia.

## Carnets y NFC

El QR del carnet contiene el **código único** del alumno. Para NFC, escribe ese mismo código como registro de texto en el tag. NFC web solo funciona en Android con Chrome y sitio HTTPS.

## Modo demo

Con los placeholders de Supabase la app corre localmente con datos de ejemplo (`demo@colegio.pe` / `demo1234`). *Restablecer datos demo* (barra lateral) regenera todo. Los datos viven solo en ese navegador.

## Atajos y accesibilidad

`Esc` cierra cualquier ventana; `Tab` queda atrapado dentro de los modales; el menú lateral es navegable por teclado; los gráficos tienen descripción alternativa y el contraste se ajusta en tema oscuro.
