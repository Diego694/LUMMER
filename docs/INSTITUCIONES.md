# Multi-institución

## Modelo

Una **institución** equivale a un registro en la tabla `colegios`. Cada miembro del personal (tabla `perfiles`) pertenece a una sola institución. Los datos están completamente aislados por RLS: cada consulta filtra automáticamente por `mi_colegio()`, que devuelve el `colegio_id` del perfil del usuario autenticado.

Los alumnos también pertenecen a una sola institución. Un estudiante se registra usando el **código de registro** de su institución.

## Administrador superior (superadmin)

El superadmin es un rol especial que puede crear, renombrar, activar/desactivar instituciones y entrar a cualquiera de ellas como administrador. No es un rol de la tabla `perfiles`, sino una entrada en la tabla `superadmins`.

### Cómo convertirse en superadmin

Desde el **SQL Editor** de Supabase (o con `psql`), ejecuta:

```sql
INSERT INTO public.superadmins (user_id)
SELECT id FROM auth.users
WHERE lower(email) = lower('tu-correo@ejemplo.com');
```

Sustituye `tu-correo@ejemplo.com` por el correo real de tu cuenta.

## Crear instituciones

### Desde la página (superadmin)

1. Inicia sesión con una cuenta que sea superadmin.
2. Ve a **Sistema → Instituciones**.
3. Haz clic en **Crear institución**.
4. Escribe el nombre (3–80 caracteres) y, opcionalmente, un código de registro (6–20 caracteres, A-Z y 0-9). Si no das código, se generará uno automático de 8 caracteres.

### Desde la base de datos (SQL Editor / service_role)

```sql
-- Solo nombre (código autogenerado)
SELECT public.crear_instituto('Mi Nueva Escuela');

-- Con administrador
SELECT public.crear_instituto('Mi Nueva Escuela', 'admin@correo.com');

-- Con administrador y código personalizado
SELECT public.crear_instituto('Mi Nueva Escuela', 'admin@correo.com', 'ESCUELA01');
```

> [!NOTE]
> La cuenta del administrador debe existir previamente en **Supabase → Authentication → Users**.

## Asignar un administrador

Desde la página de Instituciones, haz clic en **Dar administrador** en la fila de la institución deseada e ingresa el correo de una cuenta existente en Supabase Auth. También puedes usar el flujo habitual: la persona solicita acceso con el código de registro y el administrador actual aprueba la solicitud desde **Sistema → Solicitudes**.

## Cambiar el nombre de la institución

Hay dos formas:

1. **Administrador normal**: ve a **Gestión → Mi instituto** y haz clic en **Cambiar nombre**.
2. **Superadmin**: desde **Sistema → Instituciones**, haz clic en **Renombrar** en cualquier institución.

El nombre aparece en el menú lateral, el título de la pestaña, el carnet, el portal del estudiante y los reportes. También se recuerda entre sesiones para mostrarlo en la pantalla de acceso.

## Seguridad del QR por institución

Cada institución define de forma independiente su modo de validación de asistencia en **Gestión → Mi instituto** (solo Administrador):
- **Desactivado (`off`)**: QR fijo (el código del alumno).
- **Opcional (`opcional`)**: el estudiante muestra QR dinámico que renueva cada 30 s; se aceptan tanto el dinámico como el fijo impreso.
- **Obligatorio (`obligatorio`, por defecto)**: la cámara solo valida el QR dinámico firmado, impidiendo el uso de capturas de pantalla (NFC y código manual siguen como alternativa).

## Limitaciones

- El superadmin «entra» a una institución a la vez (su perfil se mueve al colegio destino).
- Cada cuenta de personal pertenece a una sola institución.
- Los estudiantes se registran con el código de registro de **su** institución y no pueden cambiar de institución.
- La función `crear_instituto` solo puede ejecutarse desde el SQL Editor o con la clave `service_role`, no desde el frontend autenticado.
