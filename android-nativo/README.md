# Registro Académico — App Android Nativa (Kotlin + Compose)

Proyecto nativo Android desarrollado en **Kotlin**, **Jetpack Compose** y **Material 3**, bajo arquitectura **MVVM**, inyección de dependencias con **Hilt** y backend en **Supabase** (Postgrest + Auth).

Este módulo reside de forma independiente en `android-nativo/` y reemplaza progresivamente la envoltura WebView previa.

---

## 1. Requisitos del entorno de compilación

- **JDK:** Java 21 (configurado en `JAVA_HOME`).
- **Gradle:** 9.6.0 (sin wrapper en el repositorio; el orquestador usa su distribución local).
- **Android SDK:** `platforms;android-37.0` y `build-tools;37.0.0` (configurado en `ANDROID_HOME`).
- **Nivel de SDK:** `compileSdk 37`, `targetSdk 37`, `minSdk 24`.
- **Toolchain:** Android Gradle Plugin 9.4.1 (Kotlin 2.4.20 integrado), KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00, Supabase BOM 3.8.0, Room 2.8.5, WorkManager 2.12.0, DataStore 1.2.1.

---

## 2. Cómo compilar

Desde el directorio raíz de la app nativa (`android-nativo/`), ejecuta:

### Compilar APK del perfil Docente (Debug)
```bash
gradle assembleDocenteDebug
```
El APK resultante se genera en:
`app/build/outputs/apk/docente/debug/app-docente-debug.apk`

### Compilar APK del perfil Estudiante (Debug)
```bash
gradle assembleEstudianteDebug
```
El APK resultante se genera en:
`app/build/outputs/apk/estudiante/debug/app-estudiante-debug.apk`

### Compilar todos los sabores Debug
```bash
gradle assembleDebug
```

### Ejecutar pruebas unitarias JVM
```bash
gradle test
```

---

## 3. Estructura del proyecto

```
android-nativo/
├── settings.gradle.kts          # Inclusión de submódulos y repositorios
├── build.gradle.kts             # Configuración raíz de plugins Kotlin/Hilt/Android
├── gradle.properties            # JVM args (-Xmx2g) y AndroidX activado
├── gradle/
│   └── libs.versions.toml       # Catálogo centralizado de versiones y dependencias
├── README.md                    # Documentación del módulo nativo
└── app/
    ├── build.gradle.kts         # Configuración del módulo de la app (flavors, dependencias)
    ├── proguard-rules.pro       # Reglas ProGuard para serialización y supabase-kt
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/pe/registroacademico/nativo/
        │   │   ├── App.kt                      # Aplicación Hilt (@HiltAndroidApp)
        │   │   ├── MainActivity.kt             # Punto de entrada Compose (@AndroidEntryPoint)
        │   │   ├── data/
        │   │   │   ├── SupabaseModule.kt       # Proveedor Singleton de SupabaseClient
        │   │   │   ├── AuthRepository.kt       # Gestión de autenticación y sesiones
        │   │   │   ├── PerfilRepository.kt     # Consulta de perfiles, colegios y RPC es_superadmin
        │   │   │   ├── RepositoryModule.kt     # Enlace de dependencias Hilt
        │   │   │   └── model/
        │   │   │       ├── Perfil.kt           # Modelo serializable de perfiles
        │   │   │       └── Colegio.kt          # Modelo serializable de colegios
        │   │   └── ui/
        │   │       ├── AppNav.kt               # Router con restauración de sesión
        │   │       ├── login/
        │   │       │   ├── AuthValidator.kt    # Validación pura de credenciales
        │   │       │   ├── LoginViewModel.kt   # Estados cargando/error/éxito
        │   │       │   └── LoginScreen.kt      # Pantalla accesible Material 3
        │   │       ├── inicio/
        │   │       │   ├── InicioViewModel.kt  # Carga de datos de perfil e institución
        │   │       │   └── InicioScreen.kt     # Panel post-login con cierre de sesión
        │   │       └── theme/
        │   │           ├── Color.kt            # Tokens derivados de diseno.css
        │   │           ├── Type.kt             # Tipografía del sistema
        │   │           └── Theme.kt            # Paleta Material 3 claro/oscuro
        │   └── res/
        │       ├── drawable/                   # Iconos vectoriales adaptativos
        │       ├── mipmap-anydpi-v26/          # Iconos adaptativos launcher
        │       └── values/                     # Colores, estilos y strings en español
        └── test/
            └── java/pe/registroacademico/nativo/ui/login/
                └── AuthValidatorTest.kt        # Pruebas unitarias de validación en JVM
```

---

## 4. Siguientes hitos (Hoja de ruta)

- **KT-2:** Persistencia local y cola de sincronización offline (Room / SQLite) para registrar asistencias sin conexión.
- **KT-3:** Módulo de cámara nativa con escáner de códigos QR dinámicos y lectura NFC para quiosco institucional.
- **KT-4:** Carnet digital del estudiante con generación de token temporal (TOTP) y consulta de asistencias.
