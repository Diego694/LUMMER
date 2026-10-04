# Portal del estudiante

Una segunda app (web + APK) para que cada estudiante se registre con sus propios datos, suba su foto y obtenga un **carnet con código QR único**. El docente lo escanea con su app (cámara, NFC o código manual) y le salta una **alerta con la foto y el nombre con apellidos parcialmente censurados**.

```
Estudiante (APK "Mi Carnet Institucional")            Docente (APK "Asistencia Institucional")
  1 crea cuenta (correo + contraseña)             ┌─ genera el "código de registro" del instituto
  2 escribe el código del instituto ◄───────────────┘
  3 completa sus datos + autorización
  4 sube su foto  ──► bucket privado               5 revisa foto/datos y APRUEBA
  6 recibe su QR único                             7 escanea el QR → alerta con foto
```

## Direcciones

| Qué | URL |
|---|---|
| Portal del estudiante | `https://<usuario>.github.io/<repo>/estudiante/` |
| Panel del docente | `https://<usuario>.github.io/<repo>/` |
| APK estudiante / docente | Releases → `carnet-estudiante.apk` / `asistencia-escolar.apk` |

Los dos APK se instalan a la vez en un mismo teléfono (distinto identificador) y, como el resto, **se actualizan solos** al publicar la web.

## Puesta en marcha (una sola vez)

1. **Migración SQL.** En Supabase → SQL Editor ejecuta [`supabase/migrations/002_estudiantes.sql`](../supabase/migrations/002_estudiantes.sql). Es aditiva y re‑ejecutable: agrega columnas (`user_id`, `nombres`, `apellidos`, `dni`, `foto_path`, `aprobado`…), cuatro funciones, el bucket privado `fotos-alumnos` y sus políticas. No toca datos ni políticas existentes; los alumnos actuales quedan como `aprobado = true`.
2. **Registro de cuentas.** Supabase → Authentication → Sign In / Providers → Email: deja activado *Allow new users to sign up* y **desactiva *Confirm email*** (el correo integrado de Supabase solo envía unos pocos mensajes por hora y los estudiantes no podrían registrarse). La seguridad no depende del correo: sin el código del instituto y tu aprobación, una cuenta nueva no puede hacer nada.
3. **Código del instituto.** En el panel del docente abre **Código de registro** (menú *Gestión*, o el botón del dashboard) y pulsa *Generar código ahora*. Cópialo o compártelo por WhatsApp (el mensaje ya trae el enlace del APK y los pasos); también puedes mostrar el QR del portal o escribir un código propio. Si se filtra, *Regenerar* invalida el anterior.

## Reglas y privacidad

- **Aprobación obligatoria.** Un estudiante recién registrado queda *Pendiente*: su QR no registra asistencia (la alerta lo indica) y no cuenta en los porcentajes hasta que el docente lo **Revisa** (foto + datos) y lo aprueba, o lo rechaza (se elimina).
- **Apellidos censurados** en alertas, bitácora y panel del QR: `Lucía María Qu**** Fl****` (nombres completos, dos primeras letras de cada apellido). La pantalla de revisión y la gestión de alumnos muestran el nombre completo porque son vistas administrativas.
- **Foto.** Se recorta a cuadrado y se reduce a 480×480 JPEG (~40 KB). Vive en un bucket **privado**; el personal la ve con una URL firmada que caduca en 1 h. El estudiante solo puede escribir en su propia carpeta (`<su-uid>/…`), máx. 512 KB, solo imágenes.
- **Autorización.** El registro exige marcar el consentimiento (menores: autoriza el apoderado) y guarda la fecha (`consentimiento_en`). Revisa que tu instituto cumpla la normativa local de protección de datos (en Perú, Ley 29733) y publica una política de privacidad.
- **QR único.** `e` + 10 hex aleatorios (≈10¹² combinaciones), generado en el servidor; no se deduce de los datos del estudiante.
- **Sin acceso al padrón.** Los estudiantes **no** tienen fila en `perfiles` (esa fila da acceso a todo el instituto con las políticas existentes). Solo pueden leer **su** registro y usar las funciones `info_colegio`, `registrar_estudiante`, `mi_registro` y `actualizar_mi_foto`.

## Límites conocidos

- **NFC del estudiante:** un teléfono no puede “emitir” su código por NFC desde una web/WebView (requiere emulación de tarjeta nativa). Para NFC, escribe el código del alumno en una tarjeta/llavero NFC (texto NDEF); el lector del docente ya lo soporta.
- **Alumnos ya existentes** (importados por el instituto) no se vinculan automáticamente: el estudiante crearía un registro nuevo. Si aparece un duplicado, rechaza el nuevo o elimina el antiguo.
- **Fuerza bruta del código del instituto:** es de 8 caracteres de un alfabeto de 32 (≈10¹²) y requiere cuenta; Supabase limita los intentos de autenticación. Regenéralo si sospechas abuso.
- **Recuperar contraseña:** no hay flujo propio; se hace desde Supabase (Authentication → Users).

## Probar sin servidor (modo demo)

Abre `…/estudiante/?demo=1` y `…/?demo=1` en el mismo navegador: comparten datos locales. Código del instituto de prueba: `DEMO2026`. El modo demo **nunca** toca la base real; `?demo=0` lo desactiva.
