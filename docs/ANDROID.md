# Apps Android (Nativa y Lite)

El proyecto ofrece dos aplicaciones para dispositivos Android que cubren diferentes necesidades y pueden **convivir instaladas al mismo tiempo** en el mismo teléfono:

1. **App Nativa (`android-nativo/`)**: la **versión principal y recomendada** para docentes y administradores. Desarrollada en Kotlin con Jetpack Compose, CameraX, Room y WorkManager.
2. **App Lite (`android/`)**: la versión secundaria basada en WebView (~300 líneas de Java). Abre la web remota, se actualiza de vez en cuando y aloja también el portal móvil del estudiante.

---

## 1. Comparativa: App Nativa vs. App Lite

| Característica | App Nativa (Principal) | App Lite (Secundaria) |
|---|---|---|
| **Tecnología** | Kotlin, Jetpack Compose, Material 3, Hilt | Java + Android WebView |
| **Código** | `android-nativo/` | `android/` |
| **ID de aplicación** | `pe.registroacademico.nativo.docente` | `app.registroacademico` (docente) / `.estudiante` |
| **Nombre visible** | **LUMMER** | **LUMMER Lite** / **LUMMER Estudiante** |
| **Público objetivo** | Docentes y directivos | Docentes (modo ligero) y Estudiantes |
| **Pantallas** | Catálogo ampliado a 36 pantallas (Aula, Pasar lista, Conexiones, etc.) | Interfaz web servida por HTTPS |
| **Cámara / Escáner QR** | CameraX + Google ML Kit a alta velocidad | `getUserMedia` HTML5 / WebRTC |
| **Respaldo offline** | Base de datos Room + cola WorkManager garantizada | Caché del Service Worker (`sw.js`) |
| **SDK mínimo / destino** | minSdk 24 (Android 7.0+) · targetSdk 37 (Android 17) | minSdk 24 · targetSdk 34 (Android 14) |
| **Prioridad** | **Alta (Release principal v4.0.0+)** | Secundaria (mantenimiento periódico) |

Ambas aplicaciones tienen `applicationId` diferentes, por lo que **pueden instalarse juntas** en cualquier dispositivo sin conflictos.

---

## 2. Enlaces de descarga estables

Los binarios se compilan automáticamente con GitHub Actions y se publican con URLs de descarga directa:

- **App Nativa (Docente / Administración):**
  - Release estable: [Releases tag `app-latest`](https://github.com/Diego694/LUMMER/releases/tag/app-latest)
  - Archivo APK: **`registro-academico.apk`**
- **App Lite (Docente y Estudiante):**
  - Release estable: [Releases tag `apk-latest`](https://github.com/Diego694/LUMMER/releases/tag/apk-latest)
  - Archivos APK:
    - **`asistencia-escolar.apk`** (Docente Lite)
    - **`carnet-estudiante.apk`** (LUMMER Estudiante - portal del estudiante)

---

## 3. Cómo compilar en tu equipo

### Opción A: Compilar App Nativa (`android-nativo/`)

**Requisitos previos:**
- **JDK:** Java 21 (Temurin o similar, configurado en `JAVA_HOME`).
- **Gradle:** 9.6.0.
- **Android SDK:** `platforms;android-37` y `build-tools;37.0.0` (configurado en `ANDROID_HOME`).

**Comandos:**
```bash
cd android-nativo

# 1. Ejecutar pruebas unitarias JVM de dominio y lógica
gradle testDocenteDebugUnitTest

# 2. Compilar APK de Release (perfil Docente)
gradle assembleDocenteRelease --no-daemon -PAPP_VERSION_CODE=1001 -PAPP_VERSION_NAME=4.0.0

# El APK resultante se genera en:
# android-nativo/app/build/outputs/apk/docente/release/app-docente-release.apk
```

### Opción B: Compilar App Lite (`android/`)

**Requisitos previos:**
- **JDK:** Java 17+.
- **Gradle:** 8.9.
- **Android SDK:** `platforms;android-34` y `build-tools;34.0.0`.

**Comandos:**
```bash
cd android

# Compilar flavors docente y estudiante
gradle assembleDocenteRelease assembleEstudianteRelease --no-daemon \
  -PAPP_URL=https://tu-usuario.github.io/tu-repo/ \
  -PAPP_VERSION_CODE=1 \
  -PAPP_VERSION_NAME=3.2.1

# Los APKs se generan en:
# android/app/build/outputs/apk/docente/release/app-docente-release.apk
# android/app/build/outputs/apk/estudiante/release/app-estudiante-release.apk
```

---

## 4. Firma y Secretos en GitHub Actions

Ambos workflows (`.github/workflows/android-nativo.yml` y `.github/workflows/android.yml`) comparten el mismo esquema de firma y los **mismos secretos de repositorio** (*Settings → Secrets and variables → Actions*):

| Secreto | Descripción |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Archivo `.jks` codificado en base64. |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del almacén de claves (keystore). |
| `ANDROID_KEY_ALIAS` | Alias de la clave de firmado. |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave privada. |

### Generar la clave de firma (una sola vez)
Ejecuta el script asistido en la raíz del repositorio:
```bash
python scripts/make_keystore.py
```
El script generará el archivo `release.jks`, imprimirá la cadena en base64 y te guiará para configurar los 4 secretos. **Haz una copia de seguridad segura de la clave**: si se pierde, las instalaciones existentes no podrán actualizarse mediante APK.

> [!NOTE]
> Si los secretos no están configurados en GitHub Actions, los workflows continuarán compilando pero firmarán con una clave debug efímera. Dicho APK servirá para pruebas, pero mostrará una advertencia y no podrá actualizar instalaciones existentes firmadas con la clave de producción.

---

## 5. Instalación en el teléfono

1. Descarga el archivo `.apk` correspondiente desde GitHub Releases.
2. Abre el archivo descargado. Android solicitará habilitar el permiso de «Instalar aplicaciones desconocidas» para el navegador o gestor de archivos.
3. Al iniciar la aplicación nativa por primera vez, concede los permisos de cámara solicitados para permitir el escaneo de carnets QR.
4. Para futuras actualizaciones, simplemente descarga el nuevo APK e instálalo sobre la app existente (requiere que ambos hayan sido firmados con la misma clave).

---

## 6. Historial de integraciones recientes (Sin publicar)

### Android nativo: Aula virtual, Pasar lista, alcance docente, Conexiones de datos, Desvincular institución, Base propia
Se integraron y cablearon en la aplicación Android nativa (`android-nativo/`) las funciones recientes del ecosistema LUMMER:
- **Aula virtual (`ui/aula/`, `AulaRepo`, `AulaViewModel`)**: catálogo de cursos asignados, materiales descargables, actividades evaluables, entregas de alumnos, calificación con escala sobre 20 y subida de archivos adjuntos mediante Supabase Storage (`install(Storage)`).
- **Pasar lista (`ui/registro/`, `PasarListaScreen`, `PasarListaViewModel`)**: asistencia por curso y ciclo con selector por carrera, lista con filtro y búsqueda, escaneo de carnets por cámara (o ingreso manual de código) con verificación de QR seguro, botón «Marcar a todos» para los alumnos pendientes y opción para marcar también el ingreso al instituto si aún no ingresó hoy.
- **Alcance docente**: filtrado automático en la navegación para que los docentes accedan únicamente a sus cursos asignados, ocultando las pantallas de gestión administrativa.
- **Conexiones de datos (`ui/sistema/`, `ConexionesRepo`, `ConexionesViewModel`)**: administración de conexiones externas (Supabase, Firebase Firestore, REST), validación de llaves públicas (con rechazo de llaves secretas o service_role), prueba de conectividad/salud y copia por lotes de datos institucionales con verificación de conteos en destino.
- **Desvincular institución y Base propia**: interfaz para suspender o reactivar instituciones (superadministrador) y configuración de base Supabase alternativa persistida en preferencias locales (`BasePropiaStore` en `domain/Conectores.kt`).

> [!NOTE]
> **Estado de verificación:** La integración compila y pasa todas las pruebas unitarias en la JVM (`AulaUtilsTest`, `ConectoresTest`, etc.); **NO** ha sido verificada aún en un dispositivo físico real ni contra una base de datos real de producción.
