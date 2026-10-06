package pe.registroacademico.nativo.ui.consultas.asistgrado

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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
fun AsistGradoScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: AsistGradoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ctx.sesion.colegioId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargar(ctx.sesion.colegioId)
        }
    }

    if (state.cargando && state.niveles.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 5)
        return
    }

    if (state.error != null && state.niveles.isEmpty()) {
        ErrorState(
            mensaje = state.error ?: "Error al cargar asistencia",
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
            titulo = "Asistencia por Ciclo",
            subtitulo = "Estado de ingreso de cada alumno de la carrera y ciclo elegidos, en la fecha elegida.",
            acciones = {
                OutlinedButton(
                    onClick = {
                        val csv = viewModel.generarCsv()
                        ConsultasExportHelper.compartirCsv(
                            context = context,
                            titulo = "Exportar Asistencia CSV",
                            nombreArchivo = "asistencia_${state.fechaSeleccionada}.csv",
                            csv = csv
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("Exportar CSV")
                }
            }
        )

        // Filtros ocupando todo el ancho
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val opcionesNivel = listOf(OpcionDropdown("", "Todas las carreras")) +
                state.niveles.map { OpcionDropdown(it.nombre, it.nombre) }

            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Carrera",
                opciones = opcionesNivel,
                seleccion = state.nivelSeleccionado,
                onSeleccionar = { viewModel.cambiarNivel(ctx.sesion.colegioId, it) }
            )

            val gradosFiltrados = state.grados.filter {
                state.nivelSeleccionado.isBlank() || it.nivel == state.nivelSeleccionado
            }
            val opcionesGrado = listOf(OpcionDropdown("", "Todos los ciclos")) +
                gradosFiltrados.map { OpcionDropdown(it.nombre, it.nombre) }

            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Ciclo",
                opciones = opcionesGrado,
                seleccion = state.gradoSeleccionado,
                onSeleccionar = { viewModel.cambiarGrado(ctx.sesion.colegioId, it) }
            )

            // Selector de fecha: hoy + días hábiles recientes
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

        // KPIs
        if (state.filas.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    valor = state.presentes.toString(),
                    etiqueta = "Presentes",
                    delta = "${state.tardes} con tardanza",
                    icono = Icons.Default.Checklist
                )
                KpiCard(
                    valor = state.ausentes.toString(),
                    etiqueta = "Ausentes",
                    icono = Icons.Default.Person
                )
                KpiCard(
                    valor = "${state.pct}%",
                    etiqueta = "% asistencia",
                    delta = "${state.activosCount} alumnos activos",
                    icono = Icons.Default.Assessment
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tabla de alumnos
        SectionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            titulo = "Alumnos (${state.filas.size})"
        ) {
            if (state.cargando) {
                SkeletonList(cantidad = 4)
            } else if (state.filas.isEmpty()) {
                EmptyState(
                    titulo = "Sin alumnos",
                    texto = "Selecciona una carrera y ciclo con alumnos registrados."
                )
            } else {
                state.filas.forEach { f ->
                    val badgeTipo = when (f.estadoTipo) {
                        "Presente" -> TipoEstadoBadge.VERDE
                        "Tardanza" -> TipoEstadoBadge.AMBAR
                        "Ausente" -> TipoEstadoBadge.ROJO
                        "Pendiente" -> TipoEstadoBadge.AMBAR
                        else -> TipoEstadoBadge.NEUTRAL
                    }

                    ListaFila(
                        titulo = f.alumno.nombre,
                        subtitulo = "${f.alumno.codigo} · ${f.alumno.grado} · Hora: ${f.hora}",
                        iniciales = StringUtils.initials(f.alumno.nombre),
                        badgeTexto = f.estadoEtiqueta,
                        badgeTipo = badgeTipo
                    )
                }
            }
        }
    }
}
