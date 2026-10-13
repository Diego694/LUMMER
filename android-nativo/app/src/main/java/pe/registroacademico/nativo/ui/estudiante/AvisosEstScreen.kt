package pe.registroacademico.nativo.ui.estudiante

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.estudiante.ComunicadoEstudiante
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList

/**
 * Pantalla de avisos y comunicados institucionales para el estudiante.
 * Muestra el listado de comunicados con fecha formateada en es-PE, títulos y mensajes,
 * y permite refrescar la lista.
 */
@Composable
fun AvisosEstScreen(
    viewModel: AvisosEstVM = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val estado = uiState) {
        is EstUiState.Cargando -> {
            Column(modifier = Modifier.fillMaxSize()) {
                PageHeader(
                    titulo = "Avisos y Comunicados",
                    subtitulo = "Información oficial de tu instituto"
                )
                SkeletonList(cantidad = 4)
            }
        }
        is EstUiState.Error -> {
            ErrorState(
                mensaje = estado.mensaje,
                onReintentar = { viewModel.recargar() }
            )
        }
        is EstUiState.Datos -> {
            val avisos = estado.datos
            if (avisos.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    PageHeader(
                        titulo = "Avisos y Comunicados",
                        subtitulo = "Información oficial de tu instituto",
                        acciones = {
                            IconButton(
                                onClick = { viewModel.recargar() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualizar avisos",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    EmptyState(
                        titulo = "Sin comunicados",
                        texto = "No hay avisos o comunicados publicados en este momento.",
                        icono = Icons.Default.Campaign,
                        accion = {
                            Button(
                                onClick = { viewModel.recargar() },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Actualizar")
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        PageHeader(
                            titulo = "Avisos y Comunicados",
                            subtitulo = "Información oficial de tu instituto",
                            acciones = {
                                IconButton(
                                    onClick = { viewModel.recargar() },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Actualizar avisos",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }

                    items(avisos, key = { it.id ?: "${it.titulo}-${it.fecha}" }) { aviso ->
                        ItemComunicadoCard(aviso = aviso)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemComunicadoCard(
    aviso: ComunicadoEstudiante
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Comunicado",
                    modifier = Modifier
                        .size(28.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = aviso.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = EstFormato.formatearFechaAviso(aviso.fecha),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text = aviso.mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            if (!aviso.publicadoPor.isNullOrBlank()) {
                Text(
                    text = "Publicado por: ${aviso.publicadoPor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
