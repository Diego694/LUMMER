# Guía de uso

## Flujo diario recomendado

1. **Ingreso de alumnos:** abre **Registro por QR** en una tablet o teléfono en la puerta, pulsa *Iniciar cámara* y cada alumno muestra su carnet. Verás su foto, nombre (apellidos parcialmente censurados), carrera y ciclo, hora y si llegó **puntual** o con **tardanza**.
2. **Sin carnet / sin cámara:** escribe el código en el campo manual, o usa **Registro por Alumno** (búsqueda por nombre).
3. **Salones completos:** **Registro Masivo por Ciclo** marca todo un ciclo o salón; los ya registrados aparecen bloqueados.
4. **Seguimiento:** el **Dashboard** muestra el % del día, la tendencia, los ciclos rezagados y los alumnos que *requieren atención*.

## Pantallas

| Pantalla | Para qué sirve |
|---|---|
| Dashboard | Indicadores del día y tendencia. Selector 7/14/30 días hábiles; *Exportar CSV* descarga el periodo. |
| Registro por QR | Cámara (con botón *Voltear*), NFC en Android/Chrome y código manual. Muestra el historial de la sesión. |
| Registro por Alumno | Filtro: todos / pendientes de hoy / ya registrados. |
| Registro Masivo por Ciclo | Elige carrera, ciclo y fecha (hasta hoy). En fechas pasadas la hora guardada es la hora límite. |
| Asistencia por Ciclo | Presentes/ausentes/tardanzas por fecha, con KPIs y CSV. |
| Asistencia por Alumno | Historial de 30 días hábiles, % de asistencia y CSV. |
| Carnet | Vista previa; PNG; PDF; *Descarga masiva* por carrera/ciclo. |
| Alumnos | Alta/edición/baja, búsqueda sin tildes, paginación e **Importar CSV**. Accesos directos a carnet e historial. |
| **Código de registro** | Genera, copia y comparte (WhatsApp, QR del portal) el código que los estudiantes usan en *LUMMER Estudiante*. También puedes escribir un código propio. |
| **Carreras** | Carreras o programas de estudio (por ejemplo MECANICA ELECTRICA, APSTI). |
| **Ciclos y salones** | Cada ciclo/salón es independiente. **Crear ciclos** genera varios de una vez (ver abajo). |
| Docentes · Comunicados | Mantenimiento de personal y avisos. |

## Carreras, ciclos y salones

Cada **carrera** (p. ej. `MECANICA ELECTRICA`, `APSTI`) tiene sus **ciclos del I al VI**, y cada ciclo es independiente: sus propios alumnos, asistencia, reportes y carnets. El nombre se compone solo con un formato fijo, y lleva la carrera para distinguirlo de un vistazo en cualquier pantalla: `APSTI · IV CICLO`, `MECANICA ELECTRICA · III CICLO` y, si hay salones, `APSTI · IV CICLO · SECCIÓN A`. No se escribe a mano, así todos quedan uniformes.

**Crear ciclos rápido:** *Ciclos y salones → Crear ciclos* (o el botón **Ciclos** en cada carrera).
1. Elige la carrera, o *➕ Nueva carrera…* y escríbela (se guarda en MAYÚSCULAS).
2. Marca los ciclos con los botones **I, II, III, IV, V, VI** (hay atajos: *Todos*, *I, III, V*, *II, IV, VI*).
3. *(Opcional)* marca los salones **A–E**: `A, B` crea, por ejemplo, `APSTI · I CICLO · SECCIÓN A` y `… SECCIÓN B`.
4. La vista previa muestra qué se creará; los que ya existen se omiten. Pulsa **Crear**.

## Código de registro para los estudiantes

En *Código de registro* generas el código del instituto con un clic, lo **copias** o lo **compartes por WhatsApp** (el mensaje ya incluye el enlace del APK y las instrucciones), y muestras el **QR del portal** para que lo abran con la cámara. *Regenerar* invalida el anterior. Cada estudiante que se registre aparece como *Pendiente* en *Alumnos* hasta que lo apruebes. Detalle en [ESTUDIANTES.md](ESTUDIANTES.md).

## Importar alumnos por CSV

Columnas en la primera fila: `nombre, codigo, carrera, ciclo, apoderado, estado` (también valen `nivel` y `grado`). Obligatorias: **nombre** y **codigo**. Si falta `estado` se asigna ACTIVO. Si el código ya existe, el alumno **se actualiza**.
El modal muestra una vista previa, las filas omitidas con su número de línea (sin nombre/código, o código repetido en el archivo) y avisa si la carrera/ciclo no existe. Descarga la plantilla desde el mismo modal.

## Reglas del sistema

- Un alumno **solo puede registrar una asistencia por día**; un segundo intento se informa como duplicado.
- Los alumnos **inactivos no registran asistencia** y no cuentan en los porcentajes.
- **Tardanza** = hora de ingreso posterior a `HORA_LIMITE` (por defecto 08:00).
- No se puede eliminar una **carrera** con ciclos/alumnos ni un **ciclo** con alumnos.
- Eliminar un alumno borra también su historial de asistencia.

## Carnets y NFC

El QR del carnet contiene el **código único** del alumno. Para NFC, escribe ese mismo código como registro de texto en el tag. NFC web solo funciona en Android con Chrome y sitio HTTPS.

## Modo demo

Con los placeholders de Supabase la app corre localmente con datos de ejemplo (`demo@instituto.pe` / `demo1234`). *Restablecer datos demo* (barra lateral) regenera todo. Los datos viven solo en ese navegador.

## Atajos y accesibilidad

`Esc` cierra cualquier ventana; `Tab` queda atrapado dentro de los modales; el menú lateral es navegable por teclado; los gráficos tienen descripción alternativa y el contraste se ajusta en tema oscuro.
