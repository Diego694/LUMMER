# Gestión académica: periodos, calendario, alertas, reportes y apoderados

## Solicitudes de ingreso  (*Registro → Solicitudes de ingreso*, administrador)
Cuando un estudiante se registra con el **código del instituto**, queda **pendiente**: su QR no registra asistencia hasta que lo apruebes. Para que no pase desapercibido, arriba aparece **«🔔 N solicitudes de ingreso»** (y una insignia en el menú); al pulsarlo abres la lista con foto, carrera·ciclo, DNI, apoderado y hora de solicitud, y puedes **Aprobar** o **Rechazar** (elimina el registro). La lista se actualiza sola cada 60 segundos.

**Aviso al estudiante:** al aprobar, el estudiante recibe la notificación «¡Tu registro fue aprobado! Ya puedes usar tu carnet QR». En la app Android llega aunque esté cerrada (la app consulta en segundo plano cada ~15 minutos y, al abrirla, al instante); en el navegador, con la página abierta, se actualiza sola en unos segundos y, si el estudiante activó el aviso, también como notificación del sistema. Funciona con un token privado por alumno (solo lo conoce él) que únicamente permite saber si su registro fue aprobado. Para entrega instantánea con la app cerrada haría falta Firebase Cloud Messaging.

## Periodos y cambio de ciclo  (*Gestión → Periodos y cambio de ciclo*, administrador)
- Define el **periodo vigente** (ej. «2026-II») y su inicio: de ahí cuentan los reportes y las alertas.
- **Cerrar periodo y pasar de ciclo**: cada alumno activo pasa al ciclo siguiente **conservando su salón**; los del **VI egresan** (quedan «EGRESADO», no cuentan como activos y su historial se conserva); los ciclos que no siguen el formato I–VI no se tocan. Muestra una **vista previa** con los números y exige confirmar que ya hiciste un **respaldo**. Se crea el nuevo periodo y queda el historial de cierres.
- Limitación: el historial de asistencia sigue a la persona; los reportes por ciclo de fechas pasadas muestran su ciclo actual.

## Calendario y horarios  (*Gestión → Calendario y horarios*, administrador)
- **Feriados y días sin clases**: botón «Cargar feriados de Perú» (referenciales: verifica traslados o días no laborables decretados) y fechas propias (*Feriado*, *Sin clases*, *Evento*). **No cuentan como falta**: se excluyen de reportes, alertas y del portal de apoderados.
- **Horarios**: ingreso, **tolerancia** y salida, del instituto y **por carrera**. Llega tarde quien ingresa después de *ingreso + tolerancia* de **su** carrera. El quiosco y todos los reportes usan ese horario.

## Alertas de inasistencia  (*Consultas → Alertas de inasistencia*)
Cuenta las faltas del periodo (sin feriados, sin el día en curso, restando las justificadas) y marca **En riesgo** desde el 70 % del límite y **Límite superado** al llegar a `LIMITE_FALTAS_PCT` (30 % por defecto, en `config.js`). Muestra cuántas faltas le quedan y un botón **Avisar** que abre WhatsApp con el mensaje al apoderado (`PLANTILLA_RIESGO`).

## Reportes oficiales  (*Consultas → Reporte mensual*)
La nómina mensual en PDF (A4 horizontal) lleva el nombre del instituto, carrera/ciclo, mes y periodo, los días sin clases del mes, la lista numerada con P/T/J/F y totales, y **líneas de firma** (docente responsable, coordinación, dirección). Los feriados no aparecen como días de clase. Si tu UGEL pide otro formato, envía un ejemplo para adaptarlo.

## Historial de cambios  (*Sistema → Historial de cambios*, administrador)
Registra **quién creó, modificó o eliminó** alumnos, carreras, ciclos, cursos, comunicados, justificaciones, personal, calendario, horarios y periodos, y las **correcciones de asistencia**. Lo escriben disparadores de la base de datos: **nadie puede editarlo ni borrarlo**. Privacidad: no guarda nombres ni DNI (solo indica que se modificó «un dato personal») y, si un alumno se elimina, **se purgan** sus datos personales del historial.

## Datos del modo local  (*Sistema → Datos del modo local*, programa de PC)
En el programa de PC, en modo **online** y como administrador: revisa y envía a la base online lo registrado en modo local (carreras, ciclos, alumnos, asistencias, comunicados, cursos). **Solo agrega**: lo que ya existe online (por código de alumno) no se toca, y los datos locales se conservan. Se puede reintentar sin duplicar.

## Portal para apoderados  (`/apoderado/`)
Sin cuenta: el apoderado escribe el **código de apoderado** (12 caracteres, único por alumno) y ve la asistencia en solo lectura: % de asistencia, faltas, tardanzas, justificadas, los últimos días con hora de ingreso y salida, y los comunicados. El código lo ve el estudiante en su carnet y el personal en **Alumnos → botón de apoderado** (con envío por WhatsApp). Protecciones: código largo, **límite de intentos fallidos por conexión**, el código no queda en la barra de direcciones y el portal solo expone a ese estudiante.
