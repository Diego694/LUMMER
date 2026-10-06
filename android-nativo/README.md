# Registro Académico — App Android Nativa (Kotlin + Compose)

Proyecto nativo Android desarrollado en **Kotlin**, **Jetpack Compose** y **Material 3**, bajo arquitectura **MVVM**, inyección de dependencias con **Hilt** y backend en **Supabase** (Postgrest + Auth + Storage + RPC).

Este módulo reside de forma independiente en `android-nativo/` y reproduce la totalidad de las entidades, lógica de negocio y navegación de la plataforma web.

> [!IMPORTANT]
> **Estado del proyecto:** **Completa y lista para release (v4.0.0)**.
> - Probada satisfactoriamente en hardware real en todas sus pantallas y flujos.
> - Se distribuye como la **Release Principal** en GitHub (`app-latest` / `registro-academico.apk`) para el perfil **Docente** (`pe.registroacademico.nativo.docente`).
> - El perfil **Estudiante** aún no está portado a Compose; los estudiantes continúan accediendo vía el portal web y la app Lite («Mi Carnet Institucional»).
> - La app anterior WebView (`android/`) se mantiene como versión **Lite** y convive instalada sin interferencias.

---

## 1. Requisitos del entorno de compilación

- **JDK:** Java 21 (configurado en `JAVA_HOME`).
- **Gradle:** 9.6.0 (sin wrapper en el repositorio; el orquestador usa su distribución local).
- **Android SDK:** `platforms;android-37` y `build-tools;37.0.0` (configurado en `ANDROID_HOME`).
- **Nivel de SDK:** `compileSdk 37`, `targetSdk 37`, `minSdk 24`.
- **Toolchain:** Android Gradle Plugin 9.4.1 (Kotlin 2.4.20 integrado), KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00, Supabase BOM 3.8.0, Room 2.8.5, WorkManager 2.12.0, DataStore 1.2.1.

---

## 2. Cómo compilar y probar

Desde el directorio raíz de la app nativa (`android-nativo/`):

### Ejecutar pruebas unitarias JVM
```bash
gradle testDocenteDebugUnitTest
```

### Compilar APK del perfil Docente para Producción (Release)
```bash
gradle assembleDocenteRelease -PAPP_VERSION_CODE=1001 -PAPP_VERSION_NAME=4.0.0
# Genera: app/build/outputs/apk/docente/release/app-docente-release.apk
```

### Compilar APK del perfil Docente (Debug)
```bash
gradle assembleDocenteDebug
```

---

## 3. Estructura del proyecto

```
android-nativo/app/src/main/java/pe/registroacademico/nativo/
├── App.kt                              # Application Hilt (@HiltAndroidApp)
├── MainActivity.kt                     # Activity principal con soporte edge-to-edge y tema dinámico
├── data/
│   ├── ApiException.kt                 # Mapeo de errores de red, sesión y RLS a mensajes en español
│   ├── AuthRepository.kt               # Autenticación, sesión y recuperación
│   ├── PerfilRepository.kt             # Perfiles, instituciones y RPCs de detalle
│   ├── AlumnosRepo.kt                  # CRUD de alumnos, solicitudes, fotos y subidas masivas
│   ├── AsistenciaRepo.kt               # Registro diario, por curso, salida y reportes por rango
│   ├── CatalogosRepo.kt                # Carreras, ciclos, cursos, horarios, periodos, comunicados, justificaciones
│   ├── PersonalRepo.kt                 # Directorio de personal, asignación de roles y actualización de perfiles
│   ├── InstitucionesRepo.kt            # RPCs multi-institución (sa_*) y configuración de instituto / QR
│   ├── SistemaRepo.kt                  # Hora servidor, auditoría, errores y exportación completa
│   ├── SupabaseModule.kt               # Proveedor singleton de SupabaseClient
│   ├── RepositoryModule.kt             # Enlace de dependencias Hilt para repositorios
│   └── model/                          # Clases @Serializable en snake_case
│       ├── Alumno.kt
│       ├── Asistencia.kt & AsistenciaCurso.kt
│       ├── Justificacion.kt
│       ├── Nivel.kt, Grado.kt, Curso.kt
│       ├── Horario.kt & DiaCalendario.kt
│       ├── Periodo.kt, Comunicado.kt, Docente.kt
│       ├── AvisoApoderado.kt, LogCliente.kt, Auditoria.kt
│       ├── Colegio.kt & Perfil.kt
│       └── RpcModels.kt
├── domain/
│   ├── Permisos.kt                     # Roles (admin, coordinador, docente), acciones y puede()
│   ├── Sesion.kt & SesionManagerImpl.kt# StateFlow con perfil, rol, institución y qr_modo
│   ├── DateUtils.kt                    # Fechas en America/Lima (UTC-5), sincronización de reloj y días hábiles
│   ├── StringUtils.kt                  # norm(), initials(), censurarNombre(), pct() y sanitización
│   ├── CiclosUtils.kt                  # Ciclos canónicos I–VI, formateo, comparación y etiquetas
│   ├── CalendarioUtils.kt              # Feriados de Perú (Semana Santa Butcher), días lectivos y horarios
│   ├── StatsUtils.kt                   # esTardanza(), resumenDia(), porGrado(), matrizAsistencia() y quiosco
│   ├── RiesgoUtils.kt                  # Alerta temprana de inasistencia y cálculo de margen
│   └── DomainModule.kt                 # Inyección de dependencias de dominio
├── ui/
│   ├── AppNav.kt                       # Navegación raíz (Login ↔ Shell) con restauración de sesión
│   ├── login/                          # Pantalla de autenticación y validación
│   ├── shell/
│   │   ├── Pantalla.kt                 # Modelos Pantalla y PantallaCtx
│   │   ├── CatalogoPantallas.kt        # Catálogo canónico de las 33 pantallas (main.js)
│   │   ├── Pantallas.kt                # Registro unificado que mergea las pantallas de los 3 agentes
│   │   ├── PantallaPendiente.kt        # Pantalla y ViewModel de ejemplo para secciones pendientes
│   │   ├── ShellScreen.kt              # Scaffold con Navigation Drawer plegable, migas y chip de red
│   │   ├── ShellViewModel.kt           # ViewModel de sesión para el Shell
│   │   └── NetworkMonitor.kt           # Monitor reactivo de conectividad a internet
│   ├── registro/
│   │   └── PantallasRegistro.kt        # Registro de pantallas del dominio Registro (ej. registro-qr)
│   ├── consultas/
│   │   └── PantallasConsultas.kt       # Registro de pantallas del dominio Consultas
│   ├── gestion/
│   │   └── PantallasGestion.kt         # Registro de pantallas del dominio Gestión (ej. perfil)
│   ├── components/                     # Componentes accesibles Material 3 (touch targets >= 48dp)
│   │   ├── PageHeader.kt
│   │   ├── KpiCard.kt
│   │   ├── EmptyState.kt
│   │   ├── ErrorState.kt
│   │   ├── SkeletonList.kt
│   │   ├── ConfirmDialog.kt
│   │   ├── FormDialog.kt
│   │   ├── EstadoBadge.kt
│   │   ├── SearchField.kt
│   │   ├── DropdownSelector.kt
│   │   ├── SectionCard.kt
│   │   ├── ListaFila.kt
│   │   └── SnackbarHelper.kt
│   └── theme/                          # Tokens de diseño y colores
└── test/
    └── java/pe/registroacademico/nativo/
        └── domain/
            └── DomainUnitTests.kt      # Casos de prueba unitarios JVM portados de tests/unit.js
```

---

## 4. Cómo añadir una pantalla en 3 pasos

Gracias a la arquitectura desacoplada de la Ola A, cada agente o desarrollador puede implementar pantallas funcionales completas **sin tocar archivos comunes ni alterar el Shell**:

### Paso 1: Crear el Composable (y su ViewModel Hilt)
Crea tu pantalla bajo tu paquete (`ui/registro/`, `ui/consultas/` o `ui/gestion/`). Tu Composable debe recibir `PantallaCtx`:

```kotlin
@Composable
fun MiNuevaScreen(
    ctx: PantallaCtx,
    viewModel: MiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            titulo = "Mi Nueva Pantalla",
            subtitulo = "Descripción de la funcionalidad"
        )
        // Usa los componentes compartidos (KpiCard, SearchField, ListaFila, etc.)
    }
}
```

### Paso 2: Registrarla en la lista de tu dominio
Abre únicamente el archivo correspondiente a tu rol:
- **Agente Registro:** edita `ui/registro/PantallasRegistro.kt`.
- **Agente Consultas:** edita `ui/consultas/PantallasConsultas.kt`.
- **Agente Gestión:** edita `ui/gestion/PantallasGestion.kt`.

Agrega tu pantalla con el mismo `id` que figura en `CatalogoPantallas.kt`:

```kotlin
Pantalla(
    id = "mi-pantalla-id",
    titulo = "Título de Pantalla",
    icono = Icons.Default.Assessment,
    grupo = "Consultas",
    contenido = { ctx -> MiNuevaScreen(ctx) }
)
```

### Paso 3: ¡Listo!
El Shell (`Pantallas.todas`) detecta automáticamente el `id` registrado y sustituye `PantallaPendiente` por tu pantalla real. El menú lateral colapsable, las migas de pan en el TopAppBar, el control de permisos (`soloAdmin` / `soloSuper`) y el manejo de insets funcionarán de inmediato sin necesidad de tocar `ShellScreen.kt`, `CatalogoPantallas.kt` ni `AppNav.kt`.
