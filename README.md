<h1 align="center">Registro Académico</h1>

<p align="center"><b>Control de asistencia institucional</b></p>

<p align="center">
  Sistema de control de asistencia para institutos y escuelas, diseñado para operar en zonas con conexión limitada.<br>
  Funciona en navegadores web, dispositivos móviles y computadoras de escritorio, con o sin conexión a internet.
</p>

<p align="center">
  <a href="https://github.com/Diego694/Sistema-de-control-de-asistencia/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Diego694/Sistema-de-control-de-asistencia/ci.yml?branch=main&style=flat-square&label=CI&labelColor=1c2433"></a>
  <img alt="Versión" src="https://img.shields.io/badge/versi%C3%B3n-4.0.0-E8A33D?style=flat-square&labelColor=1c2433">
  <img alt="Licencia" src="https://img.shields.io/badge/licencia-MIT-8b95a8?style=flat-square&labelColor=1c2433">
</p>

<p align="center">
  <a href="https://diego694.github.io/Sistema-de-control-de-asistencia/"><b>Abrir la aplicación</b></a> ·
  <a href="#aplicaciones-y-descargas">Descargas</a> ·
  <a href="docs/GUIA-DE-USO.md">Guía de uso</a> ·
  <a href="docs/README.md">Documentación</a>
</p>

---

## Para qué sirve

Registro Académico resuelve la gestión diaria de asistencia y puntualidad en instituciones educativas que requieren fiabilidad operativa sin depender de una conexión permanente a internet. Automatiza el control de ingresos y salidas en accesos y aulas mediante carnets con código QR dinámico de renovación periódica, lectura de tarjetas NFC, registro por código y modo quiosco autónomo. Además, centraliza la estructura académica, consolida reportes oficiales y mantiene el aislamiento total de datos entre múltiples sedes o instituciones.

## A quién está destinado

| Perfil | Responsabilidades y alcance |
|---|---|
| Superadministrador | Administra la plataforma global: crea y suspende instituciones, renombra sedes y asigna a sus administradores. |
| Administrador institucional | Gestión integral del centro: configura periodos, horarios y seguridad QR; administra carreras, ciclos, cursos, personal y alumnos; aprueba solicitudes y descarga respaldos. |
| Coordinador | Supervisa la asistencia, justificaciones y avisos a apoderados con alcance delimitado exclusivamente a su carrera o programa asignado. |
| Docente | Registra asistencia y salidas por escáner QR, NFC o código en jornadas o cursos; opera el modo quiosco, emite avisos a apoderados y publica comunicados. |
| Estudiante | Accede a su carnet institucional interactivo con código QR dinámico firmado, consulta su historial de asistencia y su código para apoderados. |
| Apoderado | Consulta en tiempo real la asistencia, tardanzas y justificaciones de su representado mediante un código de consulta seguro, sin necesidad de crear una cuenta. |

## Todo lo que se puede hacer

### Principal

| Función | Qué permite |
|---|---|
| Dashboard | Visualizar indicadores del día, porcentaje de asistencia institucional, gráficos de tendencia histórica, ciclos con baja asistencia y estudiantes que requieren seguimiento. |
| Mi perfil | Actualizar la fotografía personal, editar el nombre de usuario y cambiar la contraseña de acceso a la cuenta. |

### Registro

| Función | Qué permite |
|---|---|
| Registro por QR | Escanear carnets con cámara o lector NFC para registrar ingresos o salidas con control de puntualidad o tardanza, pasar lista por asignatura o ingresar códigos manuales. |
| Modo quiosco | Operar una estación fija de autoservicio en pantalla completa para que los estudiantes registren su ingreso o salida de forma autónoma, con protección por PIN. |
| Solicitudes de ingreso | Revisar y aprobar o rechazar las solicitudes de nuevos estudiantes registrados mediante el código institucional, comprobando sus datos y fotografía. |
| Registro por Alumno | Buscar alumnos por nombre o código para registrar su asistencia diaria de manera individual, con filtros por estado de registro. |
| Registro Masivo por Ciclo | Registrar la asistencia de un salón o ciclo completo en una fecha determinada, bloqueando automáticamente a los alumnos previamente registrados. |

### Consultas

| Función | Qué permite |
|---|---|
| Asistencia por Ciclo | Consultar la asistencia de una carrera y ciclo en una fecha específica, visualizando métricas de presentes, ausencias y tardanzas con exportación a CSV. |
| Asistencia por Alumno | Examinar el historial cronológico de un estudiante en los últimos 30 días hábiles, calculando su porcentaje de asistencia con exportación a CSV. |
| Asistencia por Curso | Comprobar los registros de asistencia en asignaturas específicas para una fecha determinada, con opción de exportación a CSV. |
| Reporte mensual | Generar la matriz consolidada del mes por alumno y día lectivo (presente, tardanza, justificado, falta) sin computar feriados, exportable a CSV y PDF oficial con firmas. |
| Alertas de inasistencia | Identificar a los estudiantes en riesgo de superar el límite de inasistencias del periodo lectivo, con enlace directo para notificar al apoderado vía WhatsApp. |
| Avisos a apoderados | Listar las faltas y tardanzas registradas en la jornada y generar mensajes directos para envío por WhatsApp a los números de contacto. |

### Gestión

| Función | Qué permite |
|---|---|
| Carnet | Generar y previsualizar carnets institucionales con código QR para descarga individual en PNG o PDF, y descarga masiva por carrera o ciclo. |
| Código de registro | Generar, regenerar y copiar el código institucional requerido para que los estudiantes se registren en el portal o la aplicación móvil. |
| Mi instituto | Configurar el nombre oficial de la institución y seleccionar la política de validación de códigos QR (desactivado, opcional u obligatorio). |
| Alumnos | Gestionar el padrón estudiantil mediante alta manual, edición, importación masiva por archivo CSV con plantilla, enlace a carnets y entrega de códigos para apoderados. |
| Docentes | Consultar el directorio institucional de docentes y coordinadores activos, sus carreras asignadas y la disponibilidad de sus cuentas de acceso. |
| Personal y accesos | Crear cuentas de usuario para docentes y directivos, asignar roles y carreras, restablecer contraseñas y revocar credenciales de acceso. |
| Carreras | Administrar las carreras o programas de estudio del instituto, impidiendo la eliminación de aquellas con ciclos o alumnos asignados. |
| Ciclos y salones | Crear de forma asistida ciclos del I al VI y secciones correspondientes para mantener una estructura académica homogénea. |
| Cursos | Crear y asignar asignaturas por carrera y ciclo, habilitando el registro de asistencia por materia y la designación del docente a cargo. |
| Calendario y horarios | Registrar feriados y días no lectivos para no computarlos como faltas, y definir horarios de entrada, tolerancia, tardanza y salida por carrera. |
| Periodos y cambio de ciclo | Gestionar periodos académicos y ejecutar la promoción automática de ciclo de los alumnos (con pase a condición de egresado para el VI ciclo) conservando el historial. |
| Justificaciones | Registrar faltas justificadas, licencias y permisos para que no computen negativamente en reportes y alertas de inasistencia. |
| Comunicados | Publicar anuncios institucionales con fecha y contenido para su difusión en el panel y en las consultas de la comunidad educativa. |

### Sistema

| Función | Qué permite |
|---|---|
| Diagnóstico | Verificar en tiempo real el estado de conectividad, sincronización horaria con el servidor, almacenamiento local, permisos de cámara y soporte de hardware NFC. |
| Respaldo | Descargar una copia completa de la base de datos institucional en formato JSON y exportar listados de alumnos y asistencias en CSV para hojas de cálculo. |
| Respaldo offline | Exportar registros tomados sin internet a archivos firmados `.rabackup` e importarlos al recuperar la red en sus fechas originales sin duplicación. |
| Historial de cambios | Consultar la bitácora de auditoría inalterable generada por la base de datos sobre creaciones, modificaciones y eliminaciones, con resguardo de datos personales. |
| Datos del modo local | Migrar a la base en la nube de Supabase los datos registrados localmente en el programa de PC Windows, preservando los registros del equipo. |
| Errores del sistema | Monitorear excepciones técnicas ocurridas en los dispositivos clientes para diagnosticar incidencias operativas sin registrar información confidencial. |
| Instituciones | Gestionar centros educativos desde la cuenta de superadministrador: crear entidades, renombrarlas, activarlas, suspenderlas y asignar directivos. |

Cada institución educativa opera de forma independiente dentro de la base de datos. Las consultas filtran automáticamente mediante políticas de seguridad por filas (RLS en PostgreSQL) vinculadas al identificador institucional del usuario autenticado (`mi_colegio()`), impidiendo el acceso a registros pertenecientes a otras entidades.

## Cómo se usa

1. Acceder al sistema: Ingresar desde el navegador web, la aplicación móvil Android o el programa de escritorio de Windows.
2. Configurar la institución: El superadministrador crea la entidad o el administrador ingresa a *Mi instituto* para definir el nombre y seleccionar la política de validación QR (desactivado, opcional u obligatorio).
3. Definir la estructura académica: En el menú *Gestión*, crear las carreras, los ciclos y secciones, las asignaturas, el periodo lectivo activo y el calendario con feriados y horarios de ingreso.
4. Dar de alta al personal y alumnos: En *Personal y accesos*, crear las cuentas de los docentes. En *Alumnos*, registrar a los estudiantes de forma manual, importar una lista mediante CSV o compartir el código institucional para que se registren desde el portal y aprobar sus solicitudes en *Solicitudes de ingreso*.
5. Entregar carnets con código QR: Generar los carnets digitales o imprimibles desde *Carnet*, o indicar a los estudiantes que inicien sesión en su portal para visualizar su carnet interactivo.
6. Registrar asistencia diaria: En accesos o aulas, emplear *Registro por QR* con cámara o NFC, o activar el *Modo quiosco* en una tablet fija con PIN de salida. Si un alumno no cuenta con carnet, registrarlo manualmente por código o mediante *Registro por Alumno*.
7. Consultar reportes y avisos: Monitorear el *Dashboard*, revisar las *Alertas de inasistencia*, emitir avisos a apoderados por WhatsApp, asentar justificaciones y descargar la nómina mensual en PDF con firmas oficiales.
8. Respaldar y mantener el sistema: Descargar copias de seguridad periódicas en JSON o archivos `.rabackup` en jornadas sin conexión. Al finalizar el ciclo académico, ejecutar el cambio de ciclo desde *Periodos y cambio de ciclo*.

### Uso diario del docente
El docente inicia sesión en su dispositivo y accede a *Registro por QR* para escanear los carnets de los alumnos al ingreso, o activa el *Modo quiosco* en el aula para autoservicio. Si imparte una materia específica, selecciona el curso activo antes de registrar. Durante la jornada, puede asentar licencias o permisos en *Justificaciones* y revisar las inasistencias en *Avisos a apoderados* para enviar recordatorios a las familias en un solo paso.

### Uso del estudiante
El estudiante ingresa a *Mi Carnet Institucional* desde la web o la app Lite, crea su cuenta y escribe el código de su instituto. Una vez aprobada su solicitud, accede a su carnet con código QR dinámico que renueva su firma cada 30 segundos para identificarse en puerta. Además, puede consultar su historial de asistencia y obtener el código de consulta para su apoderado.

## Aplicaciones y descargas

| Aplicación | Plataforma y características | Enlace de descarga |
|---|---|---|
| Web / PWA | Navegadores modernos en PC y móvil; soporte sin conexión y modo instalable | [Abrir aplicación](https://diego694.github.io/Sistema-de-control-de-asistencia/) |
| App Android Nativa | Android 7.0 o superior (probada en Android 17); Kotlin, Compose, CameraX y Room | [Descargar APK (`app-latest`)](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/app-latest) |
| App Android Lite | Android 7.0 o superior; versión ligera basada en WebView para docentes | [Descargar APK (`apk-latest`)](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/apk-latest) |
| Programa de PC Windows | Windows 10 o superior; instalador o portable con modo online y base local | [Descargar EXE (`pc-latest`)](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/pc-latest) |
| Portal del estudiante (*Mi Carnet Institucional*) | Acceso web para estudiantes y aplicación móvil dedicada | [Portal web](https://diego694.github.io/Sistema-de-control-de-asistencia/estudiante/) · [Descargar APK](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/apk-latest) |

Para instalar archivos APK en Android por primera vez, es necesario habilitar la opción de fuentes desconocidas en los ajustes del sistema. En Windows, si el filtro SmartScreen muestra una advertencia al abrir el ejecutable debido a la ausencia de un certificado comercial, seleccione *Más información* y confirme pulsando *Ejecutar de todas formas*.

## Funcionamiento sin internet y seguridad

- Cola offline con sincronización automática: Ante cortes de conectividad, las asistencias se almacenan en el dispositivo y se transmiten automáticamente al servidor al recuperar la red, evitando registros duplicados.
- Código QR dinámico firmado: El carnet emite un código con firma HMAC-SHA256 que se regenera cada 30 segundos, impidiendo el uso de capturas de pantalla o fotografías estáticas.
- Aislamiento y control por políticas en base de datos: La autorización se valida en PostgreSQL mediante Row Level Security (RLS); cada usuario solo puede operar sobre los registros de su institución y rol.
- Historial de cambios inalterable: Los eventos de auditoría son registrados por disparadores de base de datos y no pueden ser modificados ni eliminados por los usuarios del sistema.
- Derecho de supresión de datos: Los estudiantes disponen de la opción de eliminar su cuenta y datos personales directamente desde su portal, conforme a las normativas de protección de datos vigentes.
- Protección visual de identidad: En pantallas públicas, como el escáner y el modo quiosco, los apellidos de los estudiantes se muestran parcialmente enmascarados para resguardar su privacidad ante terceros.

## Probarlo en un minuto

```bash
python scripts/serve.py 8080
```

Abra `http://127.0.0.1:8080/?demo=1` e ingrese con las credenciales `demo@instituto.pe` y contraseña `demo1234`. En modo de prueba, toda la información se almacena localmente en el navegador y no interactúa con servidores externos.

## Tecnología

| Componente | Tecnologías empleadas |
|---|---|
| Interfaz web | JavaScript modular con migración gradual a TypeScript, HTML5 y CSS nativo sin empaquetadores obligatorios |
| Base de datos y servicios | Supabase con PostgreSQL, Row Level Security (RLS), autenticación y almacenamiento seguro |
| App Android Nativa | Kotlin con Jetpack Compose, Material 3, CameraX, Room y WorkManager |
| App Android Lite | Java y Android WebView nativo |
| Aplicación de escritorio | Electron para Windows con soporte de base de datos local y sincronización |
| Integración continua | GitHub Actions para compilación automatizada, pruebas unitarias JVM y publicación de versiones |

## Documentación

| Documento | Descripción |
|---|---|
| [Guía de uso](docs/GUIA-DE-USO.md) | Flujo operativo del registro diario, importación por CSV y reglas del sistema |
| [Roles y permisos](docs/ROLES.md) | Definición de perfiles de usuario, matriz de facultades y seguridad RLS |
| [Multi-institución](docs/INSTITUCIONES.md) | Gestión de múltiples centros educativos y administración superior |
| [Portal del estudiante](docs/ESTUDIANTES.md) | Proceso de auto-registro estudiantil, validación de fotos y carnet digital |
| [Modo quiosco](docs/QUIOSCO.md) | Configuración de terminales de autoservicio en accesos y protección por PIN |
| [Respaldo offline](docs/RESPALDO-OFFLINE.md) | Procedimiento de exportación e importación con archivos `.rabackup` |
| [Apps Android](docs/ANDROID.md) | Comparativa técnica, compilación y despliegue de las versiones Nativa y Lite |
| [Programa de PC](docs/PC.md) | Guía de uso de la versión de escritorio en modos online y local |
| [Seguridad](docs/SEGURIDAD.md) | Esquema criptográfico del QR dinámico, claves de API y cabeceras HTTP |
| [Privacidad](docs/PRIVACIDAD.md) | Medidas de protección de datos personales de menores y ejercicio de derechos |
| [Despliegue](docs/DESPLIEGUE.md) | Puesta en marcha de la base de datos en Supabase y opciones con Docker |
| [Arquitectura](docs/ARQUITECTURA.md) | Diagrama de componentes, flujo de peticiones y estructura de la plataforma |
| [Pruebas](docs/PRUEBAS.md) | Resultados de verificación estática, pruebas unitarias y cobertura |
| [Operación](docs/OPERACION.md) | Mantenimiento periódico, planes de servicio y prevención de inactividad |

Para consultar el índice temático completo, visite el [índice de documentación](docs/README.md). El registro de versiones está disponible en el [historial de cambios](CHANGELOG.md).

---

HECHO CON MUCHO AMOR Y CARIÑO PARA USTEDES :)
