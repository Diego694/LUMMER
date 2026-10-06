package pe.registroacademico.nativo.ui.consultas.alertas

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.RiesgoUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.consultas.dashboard.ColorAmber
import pe.registroacademico.nativo.ui.consultas.dashboard.ColorRed
import pe.registroacademico.nativo.ui.consultas.dashboard.ColorTeal
import pe.registroacademico.nativo.ui.consultas.util.ConsultasExportHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun AlertasScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: AlertasViewModel = hiltViewModel()
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
            mensaje = state.error ?: "Error al cargar alertas",
            onReintentar = { viewModel.cargar(ctx.sesion.colegioId) },
            modifier = modifier
        )
        return
    }

    val fechaDesdeFmt = if (state.fechaDesde.isNotBlank()) DateUtils.fmtDate(state.fechaDesde) else "—"
    val subtitulo = "Quién se acerca al límite de ${state.limiteFaltasPct} % de faltas. Cuenta desde $fechaDesdeFmt${if (!state.periodoNombre.isNullOrBlank()) " (periodo ${state.periodoNombre})" else " (últimos 60 días)"}, sin feriados ni días sin clases."

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        PageHeader(
            titulo = "Alertas de inasistencia",
            subtitulo = subtitulo
        )

        // Toolbar filtros: Carrera y Mostrar
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

            val opcionesMostrar = listOf(
                OpcionDropdown("riesgo", "Solo en riesgo"),
                OpcionDropdown("todos", "Todos")
            )
            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Mostrar",
                opciones = opcionesMostrar,
                seleccion = if (state.soloRiesgo) "riesgo" else "todos",
                onSeleccionar = {
                    viewModel.cambiarSoloRiesgo(ctx.sesion.colegioId, it == "riesgo")
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lista de alumnos en riesgo
        SectionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            titulo = "Alumnos evaluados (${state.items.size})"
        ) {
            if (state.cargando) {
                SkeletonList(cantidad = 4)
            } else if (state.items.isEmpty()) {
                val tituloEmpty = if (state.diasLectivosCount > 0) "Nadie en riesgo" else "Aún no hay días de clase"
                val textoEmpty = if (state.diasLectivosCount > 0) {
                    "Ningún alumno está cerca del límite de faltas."
                } else {
                    "Cuando empiecen las clases, aquí verás las alertas."
                }
                EmptyState(
                    titulo = tituloEmpty,
                    texto = textoEmpty,
                    icono = Icons.Default.Check
                )
            } else {
                state.items.take(200).forEach { r ->
                    val a = r.alumno
                    val (badgeTexto, badgeTipo) = when (r.nivel) {
                        "critico" -> "Límite superado" to TipoEstadoBadge.ROJO
                        "alerta" -> "En riesgo" to TipoEstadoBadge.AMBAR
                        else -> "Normal" to TipoEstadoBadge.VERDE
                    }

                    val margen = if (r.nivel == "critico") {
                        "—"
                    } else {
                        val rest = RiesgoUtils.faltasRestantes(r, state.limiteFaltasPct)
                        "$rest falta(s)"
                    }

                    val tel = a.apoderadoTelefono
                    val msg = viewModel.mensajeWhatsApp(r, ctx.sesion.nombreInstituto)
                    val enlaceWa = if (!tel.isNullOrBlank()) StatsUtils.enlaceWhatsApp(tel, msg) else ""

                    ListaFila(
                        titulo = a.nombre,
                        subtitulo = "${CiclosUtils.cicloCorto(a.grado, a.nivel)} · ${r.faltas} de ${r.dias} faltas (${r.pctFaltas}%) · Margen: $margen",
                        iniciales = StringUtils.initials(a.nombre),
                        badgeTexto = badgeTexto,
                        badgeTipo = badgeTipo,
                        acciones = {
                            if (enlaceWa.isNotBlank()) {
                                Button(
                                    onClick = {
                                        ConsultasExportHelper.abrirEnlace(context, enlaceWa)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorTeal),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = "Avisar apoderado por WhatsApp",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Avisar", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Text(
                                    text = "Sin teléfono",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
