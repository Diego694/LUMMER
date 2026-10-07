# Conexiones de datos (superadmin)

Pantalla: **Sistema → Conexiones de datos** (solo superadministrador). Permite registrar bases de datos externas y copiar allí **todos los datos** de la institución activa. Lo implementan `assets/js/conectores.js` (lógica) y `assets/js/pages/conexiones.js` (pantalla); la tabla es `conexiones_datos` (migración 017).

## Qué hace hoy y qué no

| Hace | No hace (todavía) |
|---|---|
| Guardar URL + llave **pública** de otras bases (se rechazan `service_role`, `sb_secret_` y claves privadas). | Cambiar la base **en uso** de LUMMER: las cuentas de acceso, las políticas de seguridad (RLS), las fotos y los archivos siguen en Supabase. |
| Probar la conexión (alcanzable, llave aceptada). | Copiar fotos y materiales del aula (archivos): solo filas de tablas. |
| Copiar las 15 tablas de datos (upsert, repetible, por lotes) y verificar conteos. | Conectar MySQL/MariaDB/MongoDB **directo** desde el navegador (no es posible): hace falta la puerta de enlace REST de abajo. |
| Descargar un paquete JSON portable con todos los datos. | |

Desvincular Supabase por completo exige además mover **autenticación** (cuentas y contraseñas), **autorización** (hoy RLS de Postgres) y **almacenamiento de archivos**. Es una fase aparte, y cada motor necesita su propio adaptador de cuentas y de permisos.

## Tipos de destino

- **Supabase / Postgres (PostgREST):** URL del proyecto + llave publishable/anon. El destino debe tener el esquema (`supabase/schema.sql` y migraciones) y permitir la escritura a esa llave (RLS). Si no, la copia se detiene con el aviso correspondiente.
- **Firebase / Firestore:** ID del proyecto + API key web. Cada fila se guarda como documento en la colección de su tabla. Las reglas de Firestore deben permitir escribir. *No verificado contra un proyecto real.*
- **API REST (puerta de enlace):** para MySQL, MariaDB, MongoDB u otra. Debes alojar un servicio que cumpla el contrato siguiente; LUMMER envía `Authorization: Bearer <token>`.

### Contrato de la puerta de enlace REST

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /salud` | — | `2xx` si está viva y el token es válido; `401/403` si no. |
| `POST /importar/{tabla}` | `{"filas": [ {…}, … ]}` (hasta 400 filas, **upsert** por `id`; `curso_docentes` por `curso_id`+`user_id`) | `2xx` si se guardó todo. |
| `GET /conteo/{tabla}` | — | `{"total": N}` (opcional; permite verificar). |

Tablas, en este orden: `colegios, niveles, grados, docentes, alumnos, comunicados, cursos, curso_docentes, asistencias, justificaciones, avisos_apoderados, asistencias_curso, curso_materiales, curso_actividades, curso_entregas`.

## Seguridad

- Nunca pegues llaves secretas: la web las rechaza y la base también (restricción `llave_no_secreta`).
- La tabla `conexiones_datos` solo la lee y modifica el superadmin (RLS con `es_superadmin()`). Aun así, las llaves públicas que guardas dan acceso de escritura al destino según sus reglas: configúralas con el mínimo necesario.
- Cada copia es *upsert*: no borra nada, ni en LUMMER ni en el destino.

## Base propia y alojamiento

- **Asociar otro Supabase sin tocar código:** `conexion.html` (enlace «Base de datos del servidor» en el login y botón en Conexiones de datos) guarda en este navegador/equipo la URL y la llave publishable de otro proyecto. Todo LUMMER (web, aula, estudiante, apoderado) usa esa base; «Volver a la base original» lo deshace. La base nueva necesita el esquema (`supabase/schema.sql` + migraciones) y su propia cuenta de superadministrador: las cuentas no se trasladan.
- **La base original sigue escrita en `assets/js/config.js`** como valor por defecto; la llave publishable es pública por diseño (la seguridad la dan las políticas RLS).
- **Alojamiento privado:** GitHub Pages publica el sitio de forma pública aunque el repositorio sea privado (solo GitHub Enterprise Cloud permite restringir el acceso). Con repositorio privado y sitio protegido hacen falta otras opciones (Cloudflare Pages + Access, Netlify con contraseña, o un servidor propio con el `Dockerfile`/`nginx.conf` del repositorio). El acceso real lo controlan siempre las cuentas y las políticas de la base.
