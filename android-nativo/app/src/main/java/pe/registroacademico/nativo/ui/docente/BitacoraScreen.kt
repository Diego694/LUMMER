package pe.registroacademico.nativo.ui.docente

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.docente.BitacoraItem
import pe.registroacademico.nativo.domain.docente.DocenteQrUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BitacoraScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: BitacoraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(ctx.sesion.colegioId) {
        viewModel.cargar(colegioId = ctx.sesion.colegioId)
    }

    if (uiState.cargando && uiState.items.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 7)
        return
    }

    if (uiState.error != null && uiState.items.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error al cargar la bitácora",
            onReintentar = { viewModel.cargar(colegioId = ctx.sesion.colegioId) },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PageHeader(
                    titulo = "Bitácora de registros",
                    subtitulo = "Cada registro con su hora exacta (HH:mm:ss), quién lo hizo y por qué medio.",
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.cargar(colegioId = ctx.sesion.colegioId) },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar registros de bitácora",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Filtros de fecha y búsqueda
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.fechaDesde,
                            onValueChange = { viewModel.cambiarFechaDesde(it) },
                            label = { Text("Desde (YYYY-MM-DD)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Fecha desde",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        )

                        OutlinedTextField(
                            value = uiState.fechaHasta,
                            onValueChange = { viewModel.cambiarFechaHasta(it) },
                            label = { Text("Hasta (YYYY-MM-DD)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Fecha hasta",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        )
                    }

                    SearchField(
                        query = uiState.busqueda,
                        onQueryChange = { viewModel.cambiarBusqueda(it) },
                        placeholder = "Buscar estudiante, docente o curso..."
                    )

                    // Pills / Chips por Tipo
                    val tiposDefinidos = listOf(
                        "" to "Todos (${uiState.items.size})",
                        "ingreso" to "Ingresos (${uiState.conteosPorTipo["ingreso"] ?: 0})",
                        "curso" to "Cursos (${uiState.conteosPorTipo["curso"] ?: 0})",
                        "qr_abierto" to "QR abiertos (${uiState.conteosPorTipo["qr_abierto"] ?: 0})",
                        "justificacion" to "Justificaciones (${uiState.conteosPorTipo["justificacion"] ?: 0})",
                        "aviso" to "Avisos (${uiState.conteosPorTipo["aviso"] ?: 0})"
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tiposDefinidos.forEach { (tipoKey, label) ->
                            val seleccionado = uiState.tipoSeleccionado == tipoKey
                            FilterChip(
                                selected = seleccionado,
                                onClick = { viewModel.cambiarTipo(tipoKey) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        if (uiState.itemsFiltrados.isEmpty()) {
            item {
                EmptyState(
                    titulo = "Sin registros en este rango",
                    texto = "Cambia las fechas, el tipo o el término de búsqueda para ver más.",
                    icono = Icons.Default.History,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            items(uiState.itemsFiltrados) { item ->
                FilaBitacoraCard(item = item)
            }
        }
    }
}

@Composable
private fun FilaBitacoraCard(item: BitacoraItem) {
    val fechaLegible = DocenteQrUtils.formatearFechaExacta(item.cuando)
    val horaExacta = DocenteQrUtils.formatearHoraExacta(item.cuando)
    val origenLegible = DocenteQrUtils.etiquetaOrigenLegible(item.origen)

    val (tipoTexto, badgeTipo) = when (item.tipo.lowercase()) {
        "ingreso" -> "Ingreso al instituto" to TipoEstadoBadge.VERDE
        "curso" -> "Asistencia en curso" to TipoEstadoBadge.AZUL
        "qr_abierto" -> "Docente abrió QR" to TipoEstadoBadge.AMBAR
        "justificacion" -> "Justificación" to TipoEstadoBadge.NEUTRAL
        "aviso" -> "Aviso a apoderado" to TipoEstadoBadge.NEUTRAL
        else -> item.tipo to TipoEstadoBadge.NEUTRAL
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Hora exacta del registro",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$fechaLegible · $horaExacta",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                EstadoBadge(texto = tipoTexto, tipo = badgeTipo)
            }

            if (!item.persona.isNullOrBlank()) {
                Text(
                    text = item.persona,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val lugar = listOfNotNull(item.carrera, item.ciclo).filter { it.isNotBlank() }.joinToString(" · ")
            if (lugar.isNotBlank()) {
                Text(
                    text = lugar,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.curso.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Curso: ${item.curso}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Registrado por: ${item.actor ?: "Sistema"} (${item.actorRol ?: "—"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "Vía: $origenLegible",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
