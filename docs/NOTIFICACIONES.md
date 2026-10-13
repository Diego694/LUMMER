# Notificaciones Push (Firebase Cloud Messaging)

Arquitectura y guía de configuración del sistema de notificaciones push para las aplicaciones nativas Android de LUMMER.

---

## 1. Arquitectura

El sistema de notificaciones push está diseñado sobre **Firebase Cloud Messaging (FCM)** y **Supabase** (PostgreSQL + Edge Functions):

```
+------------------+         RPC registrar_dispositivo_push         +---------------------+
|  App Android     | ---------------------------------------------> |  dispositivos_push  |
|  (Docente / Est) |                                                +---------------------+
+------------------+                                                           ^
         ^                                                                     |
         | Push FCM                                                            |
         |                                                                     |
+---------------------+      HTTP v1 API       +---------------------+         |
| Google Firebase     | <--------------------- | Supabase Edge Fn    | --------+
| Cloud Messaging     |                        | `enviar-push`       |
+---------------------+                        +---------------------+
                                                          ^
                                                          | lee cola
                                               +---------------------+
  Triggers de eventos en BD -----------------> | cola_push           |
  (avisos, alertas, etc.)                      +---------------------+
                                                          ^
                                               +---------------------+
                                               | config_push         |
                                               +---------------------+
```

### Componentes de base de datos
- **`dispositivos_push`**: Almacena los tokens FCM registrados por cada usuario e institución, asociando el perfil de la app (`docente` o `estudiante`) y la última fecha de actualización.
- **`cola_push`**: Cola transaccional de mensajes pendientes de envío generados automáticamente o por procesos del sistema.
- **`config_push`**: Parámetros de configuración del servicio push por institución y entorno.
- **Triggers**: Disparadores en PostgreSQL que detectan eventos relevantes (p. ej. nuevos avisos institucionales) e insertan los registros correspondientes en `cola_push`.

### Backend (Edge Functions)
- **Edge Function `enviar-push`**: Función serverless de Supabase que procesa las entradas pendientes de `cola_push`, genera el payload y las despacha a Google FCM usando la API HTTP v1 de Firebase.
- **Secreto `FCM_SERVICE_ACCOUNT`**: Credencial en formato JSON de la cuenta de servicio de Google Cloud / Firebase (Service Account con rol de envío FCM). Se almacena como secreto en Supabase:
  ```bash
  supabase secrets set FCM_SERVICE_ACCOUNT='{"type":"service_account",...}'
  ```

### App nativa (Android)
- **`PushRegistrar`**: Componente singleton que obtiene el token FCM del dispositivo (`FirebaseMessaging.getInstance().token`).
- **Registro de token**: Al iniciar sesión o renovar el token, la app llama a la RPC:
  ```sql
  SELECT registrar_dispositivo_push(p_token, p_app);
  ```
  donde `p_app` corresponde al flavor activo (`docente` o `estudiante`).
- **Cierre de sesión**: Invoca la RPC `quitar_dispositivo_push(p_token)` para desvincular el dispositivo y evitar recibir avisos ajenos.
- **Tolerancia a fallos**: Si la app se compila sin Firebase (sin `google-services.json`), `PushRegistrar` detecta la ausencia de Firebase y se desactiva en silencio sin emitir errores ni bloquear la app.

---

## 2. Configuración de `google-services.json` y secretos

> [!IMPORTANT]
> Nunca incluyas credenciales reales ni claves privadas en el control de versiones.

### En entorno local (desarrollo)
1. Descarga el archivo `google-services.json` desde Firebase Console para los paquetes `pe.registroacademico.nativo.docente` y `pe.registroacademico.nativo.estudiante`.
2. Coloca el archivo en:
   ```
   android-nativo/app/google-services.json
   ```
3. El archivo `build.gradle.kts` aplicará automáticamente el plugin `com.google.gms.google-services` al detectar la existencia del archivo. Si no existe, compilará sin FCM.

### En CI / GitHub Actions
1. Ve a **Settings → Secrets and variables → Actions** en tu repositorio de GitHub.
2. Agrega el secreto:
   - **`GOOGLE_SERVICES_JSON`**: Contenido íntegro del archivo `google-services.json` (en texto plano o codificado en Base64).
3. El workflow `.github/workflows/android-nativo.yml` detecta este secreto y genera automáticamente el archivo antes de compilar y probar los flavors debug y release.

---

## 3. Cómo probar

### Paso 1: Comprobar registro del token en la app
1. Compila e instala la app en un emulador con Google Play Services o dispositivo físico:
   ```bash
   cd android-nativo
   gradle assembleDocenteDebug assembleEstudianteDebug
   ```
2. Inicia sesión en la aplicación.
3. En la base de datos Supabase, consulta la tabla `dispositivos_push`:
   ```sql
   SELECT user_id, token, app, actualizado_en FROM dispositivos_push;
   ```
   Debe aparecer el registro con el token FCM del dispositivo y el flavor correspondiente.

### Paso 2: Probar envío y recepción de notificaciones
1. Inserta una notificación de prueba en `cola_push` o publica un aviso institucional para disparar el trigger.
2. Invoca la Edge Function `enviar-push` manualmente o mediante la tarea programada:
   ```bash
   supabase functions serve enviar-push --no-verify-jwt
   ```
3. Verifica la llegada de la notificación push en la bandeja del sistema Android en el canal de avisos.
