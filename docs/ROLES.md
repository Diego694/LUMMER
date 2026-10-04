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
| Modo quiosco, alertas de inasistencia, registrar salidas | ✔ | ✔ | ✔ |
| Calendario, horarios, periodos y cambio de ciclo | ✔ | — | — |
| Historial de cambios, datos del modo local | ✔ | — | — |
| Respaldo, errores, corregir o borrar asistencias | ✔ | — | — |

La interfaz oculta lo que no corresponde, pero **la seguridad real está en la base de datos** (políticas RLS): aunque alguien manipule la web, la base rechaza lo no permitido.

> **Gestión → Docentes** es solo de consulta: lista a los docentes y coordinadores con cuenta (y cuántos son). No se agregan ahí: se crean en **Personal y accesos**.

## Crear la cuenta de un docente (desde el panel)
1. Como administrador: **Gestión → Personal y accesos → Crear usuario**.
2. Escribe su **nombre, correo y contraseña** (mínimo 8 caracteres) y elige el rol (Coordinador pide además la carrera).
3. Entrégale esos datos. Entra con ese correo y contraseña desde la **web**, el **programa de PC** o la **app Android**.
4. Después puede cambiar su contraseña y **subir su foto** en **Mi perfil** (clic en su avatar, abajo a la izquierda).

Desde la misma pantalla el administrador puede **cambiar la contraseña** de un docente (si la olvidó) o **quitarle el acceso** (se elimina su cuenta). «Dar acceso a cuenta existente» sirve para cuentas ya creadas en Supabase.

### Cómo funciona por dentro (y por qué es seguro)
Crear una cuenta con contraseña exige la clave de servicio de Supabase, que **nunca** puede estar en el navegador. Por eso la hace una *Edge Function* (`supabase/functions/gestionar-personal`) que corre en Supabase: comprueba que quien llama sea **administrador** y solo actúa sobre cuentas **de su mismo instituto**. Si no estuviera publicada, la pantalla lo avisa.

Requisitos (una sola vez): migraciones `005` y `006` aplicadas y la función `gestionar-personal` publicada (Supabase → Edge Functions; con la opción *Verify JWT* desactivada, porque la propia función valida la sesión).

## Notificaciones de comunicados en el teléfono
Cuando un docente o administrador publica un **comunicado** (desde la web o el programa de PC conectado en modo *online*), las apps Android (docente y estudiante) lo muestran como **notificación**, aunque la app esté cerrada.
- Funciona sin servicios de Google: la app consulta comunicados nuevos en segundo plano (Android lo agrupa, normalmente **cada ~15 minutos**; al abrir la app se revisa al instante).
- Android 13 o superior pide permiso de notificaciones la primera vez que se inicia sesión.
- Cada instituto tiene un *token de avisos* propio; la consulta solo devuelve comunicados (nunca alumnos ni asistencias).
- Si se necesita entrega **instantánea**, el siguiente paso es Firebase Cloud Messaging (requiere un proyecto Firebase del instituto).
