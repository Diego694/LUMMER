# App Android (APK)

## Cómo funciona (y por qué se actualiza sola)

El APK es un **envoltorio nativo mínimo** (`android/`, ~300 líneas de Java, sin dependencias externas) que abre **tu web publicada** en un WebView. No contiene copia de las pantallas ni de la configuración.

```
Teléfono ── APK (WebView) ──HTTPS──► tu web (GitHub Pages / Docker) ──► Supabase
                                         ▲
                       tú publicas aquí: pantallas, estilos, config.js (conexión a la base de datos)
```

| Cambio que haces | ¿Hay que recompilar/reinstalar el APK? |
|---|---|
| Nuevas pantallas, arreglos, estilos, textos | **No.** Haces `git push`; al abrir la app (o al volver tras 15 min en segundo plano) carga la versión nueva. |
| **Conectar o cambiar la base de datos** (`SUPABASE_URL` / `SUPABASE_ANON_KEY` en `assets/js/config.js`) | **No.** Es parte de la web. |
| Cambiar la **dirección (URL) de la web** | Sí, una vez: la URL va fija en el APK (`APP_URL`). Usa un dominio estable. |
| Cambiar permisos Android, el puente nativo (`MainActivity.java`), el icono o el nombre | Sí (y publicar el APK nuevo). |

Garantía de frescura: `sw.js` usa *red primero* con revalidación (`cache: no-cache`), así que online siempre llega lo último; sin conexión abre la copia guardada. La versión de la web se ve en el pie del menú lateral (`v2.1.0 · app Android`).

Esto aplica igual en el navegador: la misma web es una **PWA** (se puede “Instalar app” desde Chrome) y no depende del APK.

## Qué cubre la app nativa (lo que un WebView no hace solo)

| Función | Implementación |
|---|---|
| **Cámara / escáner QR** | Pide el permiso Android y lo concede al WebView solo para tu dominio. |
| **Descargas** (CSV, PNG, PDF de carnets) | `AndroidBridge.saveFile` guarda en **Descargas** (MediaStore, sin permisos de almacenamiento). Los PDF usan JPEG para ser ligeros. |
| **Importar CSV** | Selector de archivos nativo. |
| **NFC** | Lectura nativa de tags NDEF (texto/URI) → llama a `window.onNativeNfc(código)`; la web registra igual que con QR. (Web NFC no existe en WebView.) |
| **Sin conexión** | Si no hay red ni copia en cache, muestra una pantalla con *Reintentar*. |
| **Seguridad** | Solo HTTPS; solo tu dominio navega dentro de la app (enlaces externos abren el navegador); sin acceso a archivos locales; permisos de cámara solo para tu origen. |

Requisito: Android 10 o superior (minSdk 29).

## Obtener el APK

### Opción A — GitHub Actions (recomendada, no requiere instalar nada)

1. Sube el repositorio a GitHub y activa **Pages** (ver [DESPLIEGUE.md](DESPLIEGUE.md)).
2. **Una sola vez**, crea la clave de firma en tu equipo y guárdala en secretos:
   ```bash
   python scripts/make_keystore.py
   ```
   Te indica los 4 secretos (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) a crear en *Settings → Secrets and variables → Actions*. **Haz copia de seguridad de la carpeta que genera**: si pierdes la clave, los teléfonos con la app instalada no podrán actualizarla.
3. *(Opcional)* Si la web no está en `https://<usuario>.github.io/<repo>/`, crea la variable de repositorio `APP_URL` con tu URL HTTPS.
4. **Actions → Android APK → Run workflow** (también corre solo al cambiar `android/**`).
5. El APK queda en **Releases → “APK Android (última versión)”** con enlace de descarga estable, y como artefacto del workflow.

### Opción B — Compilar en tu PC

Requiere JDK 17+, Android SDK (plataforma 34 y build-tools) y Gradle 8.9:

```bash
cd android
gradle assembleRelease -PAPP_URL=https://tu-usuario.github.io/tu-repo/
# → android/app/build/outputs/apk/release/app-release.apk
```

## Instalar en el teléfono

1. Descarga el `.apk` desde la release (o pásalo por cable/WhatsApp/Drive).
2. Ábrelo; Android pedirá permitir “instalar apps desconocidas” para el navegador o gestor de archivos que lo abrió (Ajustes → Aplicaciones → acceso especial).
3. La primera vez que uses el escáner, acepta el permiso de cámara.

Para actualizar el **APK** en el futuro, instala el nuevo encima: funciona solo si está firmado con la **misma clave** (por eso el paso 2).

## Solución de problemas

| Síntoma | Causa probable |
|---|---|
| “No se pudo abrir la aplicación” | Sin internet la primera vez, o `APP_URL` incorrecta (compila de nuevo con la URL correcta). |
| “La app no se instaló / conflicto de paquete” | El APK nuevo está firmado con otra clave. Desinstala el anterior (se pierde solo lo local) o usa siempre la clave del proyecto. |
| No se descarga un PDF/CSV | La descarga va a la carpeta *Descargas*; aparece un aviso “Guardado en Descargas”. |
| El escáner no abre la cámara | Permiso de cámara denegado: Ajustes → Apps → Asistencia Escolar → Permisos. |
| NFC “desactivado” | Activa NFC en los ajustes del teléfono y vuelve a la pantalla. |
| Veo una versión vieja | Cierra la app desde recientes y ábrela; confirma la versión en el pie del menú. |
