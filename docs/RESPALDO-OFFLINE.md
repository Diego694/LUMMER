# Respaldo offline: exportar sin internet e importar después

Cuando el instituto se queda **sin internet** (varios días, por ejemplo el 4 y 5 de octubre), el administrador puede **guardar en un archivo** todo lo que se registró y, al volver la conexión, **importarlo**: cada registro queda en la **fecha en que se grabó**, no en la de hoy.

Está en **Sistema → Respaldo offline** (solo administrador) y funciona igual en la **web**, la **app Android** y el **programa de PC**.

## El archivo `.rabackup`
Un archivo de texto (JSON) con:
- **Asistencias** de cada día (hora de ingreso, hora de salida y cómo se registró: QR, NFC, quiosco…).
- **Asistencias por curso** y **justificaciones**.
- Los alumnos implicados, identificados por su **código** (no por el id interno), así vale en cualquier equipo del mismo instituto.
- Una **huella (SHA-256)**: si el archivo se daña o alguien lo modifica, la importación lo rechaza.

## 1 · Exportar (sin internet)
1. Entra con una cuenta de **administrador** (la app abre sin internet con la sesión guardada).
2. **Sistema → Respaldo offline → Exportar**. Elige **Desde / Hasta** (por defecto ayer y hoy) y pulsa **Ver qué incluye** para revisar los números.
3. **Descargar respaldo**. En el teléfono queda en *Descargas*; en el PC, en la carpeta de descargas.

De dónde toma los datos:
| Dónde | Qué exporta |
|---|---|
| App o web en **modo online** sin conexión | Lo guardado en ese equipo **y aún sin enviar** (la cola sin conexión). Si hay conexión, se puede marcar «incluir lo que ya está en el servidor». |
| Programa de PC en **modo local** | La **base local** de ese equipo en las fechas elegidas. |

> Los registros pendientes **se siguen enviando solos** al volver internet. El archivo es una copia de seguridad extra, o la forma de llevarlos a **otro equipo** (por ejemplo, de la tablet del quiosco a la PC de dirección).

## 2 · Importar (con internet)
1. **Sistema → Respaldo offline → Importar** y elige el archivo `.rabackup`.
2. Se muestra un **resumen por fecha**: asistencias nuevas, las que ya estaban, salidas que se completan, por curso, justificaciones y códigos que no existen en este instituto.
3. **Importar en sus fechas**.

Reglas de seguridad:
- **No se duplica nada**: lo que ya existe (mismo alumno y día) se respeta. Si el registro estaba sin hora de salida y el archivo la trae, solo se **completa la salida**.
- Se pueden **repetir** las importaciones sin riesgo.
- Se rechazan archivos alterados, de una versión más nueva, o con fechas futuras o anteriores a 2020.
- Los alumnos o cursos que no existan se **omiten y se listan**.
- Si el respaldo es de **otro instituto**, se avisa antes de importar.
- Lo importado queda con origen **«importado»** y registrado por el administrador que importó.

## Recomendaciones
- Exporta **al final de cada día sin internet** y guarda el archivo (correo, USB, nube del instituto).
- Importa cuando vuelva la conexión **y revisa el reporte** de esos días.
- Si usas el programa de PC en modo local, también puedes usar **Sistema → Datos del modo local** (envía todo directamente, sin archivo).
