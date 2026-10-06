**Registro Académico** — aplicación nativa Android oficial para docentes y administradores (Kotlin + Jetpack Compose).

### Qué es
Aplicación nativa completa que incluye las 33 pantallas del sistema: registro de asistencia con cámara (QR dinámico con CameraX y Google ML Kit), modo quiosco, asistencia masiva y por curso, consultas, justificaciones, auditoría, alertas tempranas, gestión multi-institución y configuración general. Incluye respaldo offline con base de datos local (Room) y cola de sincronización garantizada en segundo plano (WorkManager).

### Requisitos y compatibilidad
- **Sistema operativo**: requiere Android 7.0 o superior (minSdk 24).
- **Probada y optimizada**: en Android 17 (compileSdk 37, targetSdk 37).
- **Convivencia**: convive instalada al mismo tiempo que la versión Lite anterior (tienen applicationId diferentes: `pe.registroacademico.nativo.docente` vs `app.registroacademico`).

### Cómo instalar el APK
1. Descarga el archivo **registro-academico.apk** adjunto.
2. Ábrelo en tu dispositivo móvil. Si el sistema te lo solicita, autoriza la instalación de aplicaciones de fuentes desconocidas para el navegador o gestor de archivos.
3. Al abrir la app por primera vez, concede los permisos de cámara solicitados para el escaneo de carnets QR.
