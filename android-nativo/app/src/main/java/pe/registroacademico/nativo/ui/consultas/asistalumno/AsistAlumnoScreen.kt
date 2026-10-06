package pe.registroacademico.nativo.ui.consultas.asistalumno

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.consultas.util.ConsultasExportHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AsistAlumnoScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: AsistAlumnoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ctx.sesion.colegioId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargar(ctx.sesion.colegioId)
        }
    }

    if (state.cargando && state.alumnos.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 5)
        return
    }

    if (state.error != null && state.alumnos.isEmpty()) {
        ErrorState(
            mensaje = state.error ?: "Error al cargar alumnos",
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
            titulo = "Asistencia por Alumno",
            subtitulo = "Historial de ingresos y porcentaje de asistencia (últimos 30 días hábiles).",
            acciones = {
                if (state.alumnoSeleccionado != null && state.historial.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val a = state.alumnoSeleccionado!!
                            val csv = viewModel.generarCsv()
                            ConsultasExportHelper.compartirCsv(
                                context = context,
                                titulo = "Exportar Historial",
                                nombreArchivo = "historial_${a.codigo}.csv",
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

        // Buscador de alumno
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            SearchField(
                query = state.query,
                onQueryChange = { viewModel.buscar(it) },
                placeholder = "Buscar alumno por nombre o código…"
            )
        }

        // Selector horizontal de alumnos (hasta 20 coincidencias)
        val alumnosLista = state.alumnos.take(20)
        if (alumnosLista.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alumnosLista.forEach { a ->
                    val esSeleccionado = a.id == state.alumnoSeleccionado?.id
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (esSeleccionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable {
                                viewModel.seleccionarAlumno(ctx.sesion.colegioId, a)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (esSeleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = StringUtils.initials(a.nombre),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (esSeleccionado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = a.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (esSeleccionado) FontWeight.Bold else FontWeight.Normal,
                                color = if (esSeleccionado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val alumno = state.alumnoSeleccionado
        if (alumno == null) {
            EmptyState(
                titulo = "Selecciona un alumno",
                texto = "Verás su historial, indicadores y matriz mensual de asistencia.",
                icono = Icons.Default.History
            )
        } else {
            // Tarjeta de información del alumno
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = StringUtils.initials(alumno.nombre),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = alumno.nombre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${alumno.codigo} · ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mini KPIs del alumno
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    valor = "${state.resumen.pct}%",
                    etiqueta = "Asistencia (últimos 30 días)",
                    icono = Icons.Default.Assessment
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Presentes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.resumen.presentes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Tardanzas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.resumen.tardes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Ausencias", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.resumen.ausentes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Matriz de asistencia mensual
            val fila = state.matrizFila
            if (fila != null) {
                SectionCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    titulo = "Matriz mensual (${state.mesActual})"
                ) {
                    Text(
                        text = "La tardanza cuenta como presente según el reglamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Resumen de la matriz mensual
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BadgeMatriz(codigo = "P", cantidad = fila.p, color = Color(0xFFE8F5E9), textoColor = Color(0xFF1B5E20))
                            BadgeMatriz(codigo = "T", cantidad = fila.t, color = Color(0xFFFFF8E1), textoColor = Color(0xFFF57F17))
                            BadgeMatriz(codigo = "J", cantidad = fila.j, color = Color(0xFFE3F2FD), textoColor = Color(0xFF0D47A1))
                            BadgeMatriz(codigo = "F", cantidad = fila.f, color = Color(0xFFFFEBEE), textoColor = Color(0xFFB71C1C))
                        }
                        Text(
                            text = "${fila.pct}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Celdas por día del mes
                    if (state.diasMes.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.diasMes.forEach { d ->
                                val celda = fila.celdas[d] ?: "F"
                                val numDia = d.substringAfterLast("-")
                                val (bg, fg) = when (celda) {
                                    "P" -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
                                    "T" -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
                                    "J" -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
                                    else -> Color(0xFFFFEBEE) to Color(0xFFB71C1C)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(bg)
                                        .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = numDia, fontSize = 9.sp, color = fg.copy(alpha = 0.8f))
                                        Text(text = celda, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Historial de asistencias recientes
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                titulo = "Historial de ingresos recientes"
            ) {
                if (state.historial.isEmpty()) {
                    EmptyState(
                        titulo = "Sin registros",
                        texto = "Este alumno no tiene asistencias registradas en el periodo."
                    )
                } else {
                    state.historial.forEach { h ->
                        val esTarde = StatsUtils.esTardanza(h.hora, state.horaLimite, alumno.nivel)
                        val horaCorta = if (h.hora.length >= 5) h.hora.substring(0, 5) else h.hora
                        val horaSalida = if (!h.horaSalida.isNullOrBlank()) " · Salida: ${h.horaSalida}" else ""

                        ListaFila(
                            titulo = DateUtils.fmtDate(h.fecha),
                            subtitulo = "Ingreso: $horaCorta$horaSalida",
                            badgeTexto = if (esTarde) "Tardanza" else "Puntual",
                            badgeTipo = if (esTarde) TipoEstadoBadge.AMBAR else TipoEstadoBadge.VERDE
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeMatriz(
    codigo: String,
    cantidad: Int,
    color: Color,
    textoColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$codigo: ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textoColor
            )
            Text(
                text = "$cantidad",
                style = MaterialTheme.typography.labelSmall,
                color = textoColor
            )
        }
    }
}
