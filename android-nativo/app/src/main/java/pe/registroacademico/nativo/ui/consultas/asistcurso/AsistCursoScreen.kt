package pe.registroacademico.nativo.ui.consultas.asistcurso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.consultas.util.ConsultasExportHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun AsistCursoScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: AsistCursoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ctx.sesion.colegioId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargar(ctx.sesion.colegioId)
        }
    }

    if (state.cargando && state.cursos.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 4)
        return
    }

    if (state.error != null && state.cursos.isEmpty()) {
        ErrorState(
            mensaje = state.error ?: "Error al cargar cursos",
            onReintentar = { viewModel.cargar(ctx.sesion.colegioId) },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        PageHeader(
            titulo = "Asistencia por Curso",
            subtitulo = "Quién asistió a cada curso en la fecha elegida. Para pasar lista, usa «Registro por QR» y elige el curso.",
            acciones = {
                if (state.filas.isNotEmpty()) {
                    val curso = state.cursos.find { it.id == state.cursoSeleccionadoId }
                    OutlinedButton(
                        onClick = {
                            val csv = viewModel.generarCsv()
                            ConsultasExportHelper.compartirCsv(
                                context = context,
                                titulo = "Exportar Asistencia Curso",
                                nombreArchivo = "asistencia_${(curso?.nombre ?: "curso").replace(" ", "_")}_${state.fechaSeleccionada}.csv",
                                csv = csv
                            )
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("CSV")
                    }
                }
            }
        )

        if (state.cursos.isEmpty()) {
            EmptyState(
                titulo = "Sin cursos",
                texto = "El administrador debe crear cursos en Gestión → Cursos.",
                icono = Icons.Default.Book,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            return
        }

        // Toolbar filtros: Curso y Fecha
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val opcionesCurso = state.cursos.map { c ->
                val ciclo = if (!c.grado.isNullOrBlank()) CiclosUtils.cicloCorto(c.grado, c.nivel) else c.nivel
                val label = "${c.nombre} · $ciclo"
                OpcionDropdown(c.id ?: "", label)
            }

            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Curso",
                opciones = opcionesCurso,
                seleccion = state.cursoSeleccionadoId,
                onSeleccionar = { viewModel.cambiarCurso(ctx.sesion.colegioId, it) }
            )

            val fechasDisponibles = DateUtils.lastWeekdays(14, DateUtils.todayStr()).reversed().map { f ->
                val label = if (f == DateUtils.todayStr()) "Hoy (${DateUtils.fmtDate(f)})" else DateUtils.fmtDate(f)
                OpcionDropdown(f, label)
            }
            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Fecha",
                opciones = fechasDisponibles,
                seleccion = state.fechaSeleccionada,
                onSeleccionar = { viewModel.cambiarFecha(ctx.sesion.colegioId, it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // KPI resumen
        if (state.totalCurso > 0) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                KpiCard(
                    valor = "${state.presentes} de ${state.totalCurso}",
                    etiqueta = "Alumnos presentes",
                    delta = "${StringUtils.pct(state.presentes, state.totalCurso)}% de asistencia",
                    icono = Icons.Default.ChecklistRtl
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Lista de alumnos del curso
        SectionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            titulo = "Asistencia (${state.filas.size} alumnos)"
        ) {
            if (state.cargando) {
                SkeletonList(cantidad = 4)
            } else if (state.filas.isEmpty()) {
                EmptyState(
                    titulo = "Sin alumnos",
                    texto = "No hay alumnos activos que pertenezcan a este curso."
                )
            } else {
                state.filas.forEach { f ->
                    val badgeTipo = when (f.estadoEtiqueta) {
                        "Presente" -> TipoEstadoBadge.VERDE
                        "Tardanza" -> TipoEstadoBadge.AMBAR
                        else -> TipoEstadoBadge.ROJO
                    }

                    ListaFila(
                        titulo = f.alumno.nombre,
                        subtitulo = "${CiclosUtils.cicloCorto(f.alumno.grado, f.alumno.nivel)} · Hora: ${f.hora}",
                        iniciales = StringUtils.initials(f.alumno.nombre),
                        badgeTexto = f.estadoEtiqueta,
                        badgeTipo = badgeTipo
                    )
                }
            }
        }
    }
}
