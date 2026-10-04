# Roles y accesos

| | **Administrador** | **Docente** | **Coordinador** |
|---|---|---|---|
| Crear / deshacer carreras y ciclos, alumnos, cursos, docentes | ✔ | — | — |
| Dar o quitar accesos (Personal y accesos) | ✔ | — | — |
| Registrar asistencia o tardanza (QR, NFC, manual, por curso) | ✔ | ✔ | ✔ |
| Ver asistencias, reportes y dashboard | ✔ | ✔ | solo su carrera |
| Justificaciones y avisos a apoderados | ✔ | ✔ | ✔ |
| Ver y compartir el código de registro | ✔ (y generarlo) | ✔ | ✔ |
| Publicar comunicados | ✔ (y eliminarlos) | ✔ | ✔ |
| Respaldo, errores, corregir o borrar asistencias | ✔ | — | — |

La interfaz oculta lo que no corresponde, pero **la seguridad real está en la base de datos** (políticas RLS): aunque alguien manipule la web, la base rechaza lo no permitido.

## Cómo dar acceso a un docente
1. En Supabase → **Authentication → Users → Add user**: crea la cuenta (correo y contraseña) y entrégasela al docente. *(Por seguridad, las cuentas con contraseña las crea una persona del instituto, no la aplicación.)*
2. En la aplicación, como administrador: **Gestión → Personal y accesos → Dar acceso**. Escribe ese correo y elige el rol (Coordinador pide además la carrera).
3. El docente inicia sesión con su correo y contraseña. Para quitarle el acceso: botón de la papelera en la misma pantalla (la cuenta no se borra).

Requisito: tener aplicada la migración `supabase/migrations/005_personal_avisos.sql`.

## Notificaciones de comunicados en el teléfono
Cuando un docente o administrador publica un **comunicado** (desde la web o el programa de PC conectado en modo *online*), las apps Android (docente y estudiante) lo muestran como **notificación**, aunque la app esté cerrada.
- Funciona sin servicios de Google: la app consulta comunicados nuevos en segundo plano (Android lo agrupa, normalmente **cada ~15 minutos**; al abrir la app se revisa al instante).
- Android 13 o superior pide permiso de notificaciones la primera vez que se inicia sesión.
- Cada instituto tiene un *token de avisos* propio; la consulta solo devuelve comunicados (nunca alumnos ni asistencias).
- Si se necesita entrega **instantánea**, el siguiente paso es Firebase Cloud Messaging (requiere un proyecto Firebase del instituto).
