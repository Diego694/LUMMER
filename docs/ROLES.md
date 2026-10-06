# Roles y permisos

## Roles del sistema

| Rol            | Descripción                                    |
|----------------|------------------------------------------------|
| Administrador  | Control total del colegio: periodos, grados, secciones, materias, personal, alumnos, asistencia, reportes, quiosco, carnet |
| Docente        | Registra asistencia y calificaciones en sus secciones asignadas; crea observaciones; usa el quiosco |
| Orientador     | Crea y edita observaciones de alumnos           |

### Administrador superior (superadmin)

El **superadmin** no es un rol de la tabla `perfiles`, sino una entrada en la tabla `superadmins`. Puede crear, renombrar, activar/desactivar instituciones y entrar a cualquiera como administrador. Ver [INSTITUCIONES.md](./INSTITUCIONES.md) para más detalles.

## Matriz de permisos

| Acción                   | Admin | Docente | Orientador |
|--------------------------|:-----:|:-------:|:----------:|
| Gestionar periodos       |  ✓    |         |            |
| Gestionar materias       |  ✓    |         |            |
| Gestionar grados         |  ✓    |         |            |
| Gestionar secciones      |  ✓    |         |            |
| Gestionar asignaciones   |  ✓    |         |            |
| Gestionar alumnos        |  ✓    |         |            |
| Gestionar personal       |  ✓    |         |            |
| Registrar asistencia     |  ✓    |    ✓    |            |
| Registrar calificaciones |  ✓    |    ✓    |            |
| Crear observaciones      |  ✓    |    ✓    |     ✓      |
| Aprobar solicitudes      |  ✓    |         |            |
| Activar quiosco          |  ✓    |    ✓    |            |
| Ver reportes             |  ✓    |    ✓    |     ✓      |
| Ver auditoría            |  ✓    |         |            |
| Renombrar institución    |  ✓    |         |            |
| Configurar seguridad QR  |  ✓    |         |            |
| Gestionar instituciones  |  superadmin  |   |            |

## Seguridad

- RLS en todas las tablas filtra por `mi_colegio()` (el colegio del perfil del usuario)
- `es_admin()` devuelve `true` solo si el perfil tiene rol `'Administrador'`
- `es_superadmin()` verifica si el usuario está en la tabla `superadmins`
- Las funciones sensibles verifican el rol antes de ejecutar
- El frontend replica las verificaciones con `puede(accion)` en `permisos.js`
- Solo el Administrador puede editar el nombre y la seguridad del QR de su institución (`colegios.qr_modo`)
- Las funciones `sa_*` comprueban `es_superadmin()` al inicio
- `_crear_colegio` y `crear_instituto` están revocadas a todos los roles de la API

## Cómo se asignan

1. El administrador crea una cuenta en Supabase Auth
2. El usuario solicita acceso con su código de registro
3. El administrador aprueba la solicitud y elige el rol
4. Se crea un perfil en la tabla `perfiles` con el rol asignado

Ver también: [Solicitudes de acceso](./ACCESOS.md) · [Multi-institución](./INSTITUCIONES.md)
