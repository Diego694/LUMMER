package pe.registroacademico.nativo.ui.sistema

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SyncAlt
import pe.registroacademico.nativo.ui.shell.Pantalla

val pantallasSistema: List<Pantalla> = listOf(
    Pantalla(
        id = "diagnostico",
        titulo = "Diagnóstico",
        icono = Icons.Default.Info,
        grupo = "Sistema",
        contenido = { ctx -> DiagnosticoScreen(ctx) }
    ),
    Pantalla(
        id = "respaldo",
        titulo = "Respaldo",
        icono = Icons.Default.CloudDownload,
        grupo = "Sistema",
        soloAdmin = true,
        contenido = { ctx -> RespaldoScreen(ctx) }
    ),
    Pantalla(
        id = "respaldo-offline",
        titulo = "Respaldo offline",
        icono = Icons.Default.CloudUpload,
        grupo = "Sistema",
        soloAdmin = true,
        contenido = { ctx -> RespaldoOfflineScreen(ctx) }
    ),
    Pantalla(
        id = "historial",
        titulo = "Historial de cambios",
        icono = Icons.Default.ManageHistory,
        grupo = "Sistema",
        soloAdmin = true,
        contenido = { ctx -> HistorialScreen(ctx) }
    ),
    Pantalla(
        id = "migrar",
        titulo = "Datos del modo local",
        icono = Icons.Default.SyncAlt,
        grupo = "Sistema",
        soloAdmin = true,
        contenido = { ctx -> MigrarScreen(ctx) }
    ),
    Pantalla(
        id = "errores",
        titulo = "Errores",
        icono = Icons.Default.ReportProblem,
        grupo = "Sistema",
        soloAdmin = true,
        contenido = { ctx -> ErroresScreen(ctx) }
    ),
    Pantalla(
        id = "instituciones",
        titulo = "Instituciones",
        icono = Icons.Default.Domain,
        grupo = "Sistema",
        soloSuper = true,
        contenido = { ctx -> InstitucionesScreen(ctx) }
    )
)
