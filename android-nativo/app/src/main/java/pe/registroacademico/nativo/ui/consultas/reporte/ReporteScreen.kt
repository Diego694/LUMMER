package pe.registroacademico.nativo.ui.consultas.reporte

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.consultas.util.ConsultasExportHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import java.time.LocalDate

@Composable
fun ReporteScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: ReporteViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ctx.sesion.colegioId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargarCatalogos(ctx.sesion.colegioId)
        }
    }

    if (state.error != null && state.niveles.isEmpty()) {
        ErrorState(
            mensaje = state.error ?: "Error al cargar reporte",
            onReintentar = { viewModel.cargarCatalogos(ctx.sesion.colegioId) },
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
            titulo = "Reporte mensual",
            subtitulo = "Asistencia de cada alumno, día por día. P presente · T tardanza · J justificado · F falta.",
            acciones = {
                if (state.matriz != null && state.matriz!!.dias.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val csv = viewModel.generarCsv()
                            ConsultasExportHelper.compartirCsv(
                                context = context,
                                titulo = "Reporte Mensual CSV",
                                nombreArchivo = "reporte_${state.mesSeleccionado}_${state.nivelSeleccionado.replace(" ", "_")}.csv",
                                csv = csv
                            )
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("CSV")
                    }
                    Button(
                        onClick = {
                            val resumen = viewModel.generarResumenTexto()
                            ConsultasExportHelper.compartirTexto(
                                context = context,
                                titulo = "Resumen Mensual",
                                texto = resumen
                            )
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("Compartir")
                    }
                }
            }
        )

        // Toolbar filtros: Carrera, Ciclo, Mes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val opcionesNivel = state.niveles.map { OpcionDropdown(it.nombre, it.nombre) }
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

            // Mes selector (últimos 6 meses)
            val hoyLd = LocalDate.now(DateUtils.ZONE_ID)
            val mesesDisponibles = (0..5).map { offset ->
                val ld = hoyLd.minusMonths(offset.toLong())
                val mesStr = String.format("%04d-%02d", ld.year, ld.monthValue)
                val etiqueta = "${ld.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ld.year}"
                OpcionDropdown(mesStr, etiqueta)
            }
            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Mes",
                opciones = mesesDisponibles,
                seleccion = state.mesSeleccionado,
                onSeleccionar = { viewModel.cambiarMes(ctx.sesion.colegioId, it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.cargando) {
            SkeletonList(modifier = Modifier.padding(horizontal = 16.dp), cantidad = 4)
            return
        }

        val m = state.matriz
        if (m == null || state.nivelSeleccionado.isBlank()) {
            EmptyState(
                titulo = "Elige una carrera",
                texto = "El reporte se arma por carrera (y opcionalmente por ciclo) y mes.",
                icono = Icons.Default.TableChart,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            return
        }

        if (m.filas.isEmpty()) {
            EmptyState(
                titulo = "Sin datos",
                texto = "No hay alumnos activos en esa carrera/ciclo.",
                icono = Icons.Default.Group,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            return
        }

        // KPIs resumen del grupo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                valor = m.resumen.alumnos.toString(),
                etiqueta = "Alumnos",
                icono = Icons.Default.Group
            )
            KpiCard(
                valor = m.resumen.dias.toString(),
                etiqueta = "Días de clase registrados",
                icono = Icons.Default.CalendarMonth
            )
            KpiCard(
                valor = "${m.resumen.pct}%",
                etiqueta = "Asistencia del grupo",
                icono = Icons.Default.Assessment
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (m.dias.isEmpty()) {
            EmptyState(
                titulo = "Sin registros en el mes",
                texto = "Aún no hay asistencias registradas en este periodo.",
                icono = Icons.Default.CalendarMonth,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            return
        }

        // Matriz de Asistencia (Scroll horizontal)
        SectionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            titulo = "Nómina de asistencia (${m.filas.size} alumnos)"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    // Cabecera de la tabla
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alumno",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 8.dp)
                        )
                        m.dias.forEach { d ->
                            val num = d.substringAfterLast("-").toIntOrNull()?.toString() ?: d
                            Text(
                                text = num,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(32.dp)
                            )
                        }
                        Text(text = "P", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                        Text(text = "T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                        Text(text = "J", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                        Text(text = "F", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                        Text(text = "%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(42.dp))
                    }

                    HorizontalDivider()

                    // Filas de alumnos
                    m.filas.forEach { fila ->
                        Row(
                            modifier = Modifier
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = fila.alumno.nombre,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CiclosUtils.cicloCorto(fila.alumno.grado, state.nivelSeleccionado),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Celdas de cada día
                            m.dias.forEach { d ->
                                val c = fila.celdas[d] ?: "F"
                                val (bg, fg) = when (c) {
                                    "P" -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
                                    "T" -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
                                    "J" -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
                                    else -> Color(0xFFFFEBEE) to Color(0xFFB71C1C)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fg
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Métricas finales
                            Text(text = "${fila.p}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                            Text(text = "${fila.t}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                            Text(text = "${fila.j}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                            Text(text = "${fila.f}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                            Text(
                                text = "${fila.pct}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (fila.pct < 85) Color(0xFFB71C1C) else MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(42.dp)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
