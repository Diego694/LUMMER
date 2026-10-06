package pe.registroacademico.nativo.ui.consultas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import pe.registroacademico.nativo.ui.consultas.alertas.AlertasScreen
import pe.registroacademico.nativo.ui.consultas.asistalumno.AsistAlumnoScreen
import pe.registroacademico.nativo.ui.consultas.asistcurso.AsistCursoScreen
import pe.registroacademico.nativo.ui.consultas.asistgrado.AsistGradoScreen
import pe.registroacademico.nativo.ui.consultas.avisos.AvisosScreen
import pe.registroacademico.nativo.ui.consultas.dashboard.DashboardScreen
import pe.registroacademico.nativo.ui.consultas.reporte.ReporteScreen
import pe.registroacademico.nativo.ui.shell.Pantalla
import pe.registroacademico.nativo.ui.shell.PantallaCtx

val pantallasConsultas: List<Pantalla> = listOf(
    Pantalla(
        id = "dashboard",
        titulo = "Dashboard",
        icono = Icons.Default.Dashboard,
        grupo = "Principal",
        contenido = { ctx: PantallaCtx ->
            DashboardScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "asist-grado",
        titulo = "Asistencia por Ciclo",
        icono = Icons.Default.TableChart,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            AsistGradoScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "asist-alumno",
        titulo = "Asistencia por Alumno",
        icono = Icons.Default.History,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            AsistAlumnoScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "asist-curso",
        titulo = "Asistencia por Curso",
        icono = Icons.Default.ChecklistRtl,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            AsistCursoScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "reporte",
        titulo = "Reporte mensual",
        icono = Icons.Default.Assessment,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            ReporteScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "alertas",
        titulo = "Alertas de inasistencia",
        icono = Icons.Default.Warning,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            AlertasScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "avisos",
        titulo = "Avisos a apoderados",
        icono = Icons.Default.Campaign,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            AvisosScreen(ctx = ctx)
        }
    )
)
