package pe.registroacademico.nativo.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class DefinicionPantalla(
    val id: String,
    val titulo: String,
    val icono: ImageVector,
    val grupo: String,
    val soloAdmin: Boolean = false,
    val soloSuper: Boolean = false,
    val noDocente: Boolean = false
)

object CatalogoPantallas {
    val catalogo: List<DefinicionPantalla> = listOf(
        // Principal
        DefinicionPantalla("dashboard", "Dashboard", Icons.Default.Dashboard, "Principal"),
        DefinicionPantalla("aula", "Aula", Icons.Default.MenuBook, "Principal"),

        // Registro
        DefinicionPantalla("pasar-lista", "Pasar lista", Icons.Default.Checklist, "Registro"),
        DefinicionPantalla("registro-qr", "Registro por QR", Icons.Default.QrCodeScanner, "Registro", noDocente = true),
        DefinicionPantalla("quiosco", "Modo quiosco", Icons.Default.Badge, "Registro", noDocente = true),
        DefinicionPantalla("solicitudes", "Solicitudes de ingreso", Icons.Default.HowToReg, "Registro", soloAdmin = true),
        DefinicionPantalla("registro-alumno", "Registro por Alumno", Icons.Default.PersonSearch, "Registro", noDocente = true),
        DefinicionPantalla("registro-masivo", "Registro Masivo por Ciclo", Icons.Default.Checklist, "Registro", noDocente = true),

        // Consultas
        DefinicionPantalla("asist-grado", "Asistencia por Ciclo", Icons.Default.TableChart, "Consultas"),
        DefinicionPantalla("asist-alumno", "Asistencia por Alumno", Icons.Default.History, "Consultas"),
        DefinicionPantalla("asist-curso", "Asistencia por Curso", Icons.Default.ChecklistRtl, "Consultas"),
        DefinicionPantalla("reporte", "Reporte mensual", Icons.Default.Assessment, "Consultas"),
        DefinicionPantalla("alertas", "Alertas de inasistencia", Icons.Default.Warning, "Consultas"),
        DefinicionPantalla("avisos", "Avisos a apoderados", Icons.Default.Campaign, "Consultas"),

        // Gestión
        DefinicionPantalla("carnet", "Carnet", Icons.Default.ContactPage, "Gestión", noDocente = true),
        DefinicionPantalla("codigo", "Código de registro", Icons.Default.QrCode, "Gestión", noDocente = true),
        DefinicionPantalla("instituto", "Mi instituto", Icons.Default.Business, "Gestión", soloAdmin = true),
        DefinicionPantalla("alumnos", "Alumnos", Icons.Default.School, "Gestión"),
        DefinicionPantalla("docentes", "Docentes", Icons.Default.Work, "Gestión", noDocente = true),
        DefinicionPantalla("personal", "Personal y accesos", Icons.Default.Group, "Gestión", soloAdmin = true),
        DefinicionPantalla("niveles", "Carreras", Icons.Default.Layers, "Gestión", noDocente = true),
        DefinicionPantalla("grados", "Ciclos y salones", Icons.Default.MenuBook, "Gestión", noDocente = true),
        DefinicionPantalla("cursos", "Cursos", Icons.Default.Book, "Gestión", soloAdmin = true),
        DefinicionPantalla("calendario", "Calendario y horarios", Icons.Default.CalendarMonth, "Gestión", soloAdmin = true),
        DefinicionPantalla("periodos", "Periodos y cambio de ciclo", Icons.Default.DateRange, "Gestión", soloAdmin = true),
        DefinicionPantalla("justificaciones", "Justificaciones", Icons.Default.EventAvailable, "Gestión"),
        DefinicionPantalla("comunicados", "Comunicados", Icons.Default.Notifications, "Gestión"),

        // Sistema
        DefinicionPantalla("diagnostico", "Diagnóstico", Icons.Default.Info, "Sistema"),
        DefinicionPantalla("respaldo", "Respaldo", Icons.Default.CloudDownload, "Sistema", soloAdmin = true),
        DefinicionPantalla("respaldo-offline", "Respaldo offline", Icons.Default.CloudUpload, "Sistema", soloAdmin = true),
        DefinicionPantalla("historial", "Historial de cambios", Icons.Default.ManageHistory, "Sistema", soloAdmin = true),
        DefinicionPantalla("migrar", "Datos del modo local", Icons.Default.SyncAlt, "Sistema", soloAdmin = true),
        DefinicionPantalla("errores", "Errores", Icons.Default.ReportProblem, "Sistema", soloAdmin = true),
        DefinicionPantalla("instituciones", "Instituciones", Icons.Default.Domain, "Sistema", soloSuper = true),
        DefinicionPantalla("conexiones", "Conexiones de datos", Icons.Default.Layers, "Sistema", soloSuper = true),

        // Mi cuenta (al final del menú)
        DefinicionPantalla("perfil", "Mi perfil", Icons.Default.AccountCircle, "Mi cuenta")
    )
}
