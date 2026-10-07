package pe.registroacademico.nativo.ui.aula

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.FilaLibroNotas
import pe.registroacademico.nativo.domain.AulaUtils
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge

@Composable
fun NotasTabContenido(
    actividades: List<CursoActividad>,
    periodoSeleccionado: Int,
    libroNotas: List<FilaLibroNotas>,
    cargando: Boolean,
    error: String?,
    onCambiarPeriodo: (Int) -> Unit,
    onExportarCsv: () -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cargando) {
        SkeletonList(cantidad = 4, modifier = modifier)
        return
    }

    if (error != null) {
        ErrorState(
            mensaje = error,
            onReintentar = onReintentar,
            modifier = modifier
        )
        return
    }

    if (actividades.isEmpty()) {
        EmptyState(
            titulo = "Aún no hay actividades",
            texto = "Publica actividades y califica las entregas para ver el libro de notas.",
            icono = Icons.Default.Checklist,
            modifier = modifier
        )
        return
    }

    if (libroNotas.isEmpty()) {
        EmptyState(
            titulo = "Sin estudiantes en este curso",
            texto = "Aparecerán los estudiantes aprobados de la carrera y el ciclo del curso.",
            icono = Icons.Default.Group,
            modifier = modifier
        )
        return
    }

    val periodos = AulaUtils.periodosDe(actividades)
    val actividadesFiltradas = if (periodoSeleccionado > 0) {
        actividades.filter { AulaUtils.periodoDe(it.periodo) == periodoSeleccionado }
    } else actividades

    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Barra de herramientas: Filtro de periodo y botón exportar CSV
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (periodos.size > 1) {
                val opcionesPeriodo = listOf(OpcionDropdown("0", "Todo el curso")) +
                    periodos.map { OpcionDropdown(it.toString(), "Periodo $it") }

                DropdownSelector(
                    etiqueta = "Ver periodo",
                    opciones = opcionesPeriodo,
                    seleccion = periodoSeleccionado.toString(),
                    onSeleccionar = { onCambiarPeriodo(it.toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onExportarCsv,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exportar CSV")
            }
        }

        if (isCompact) {
            // Vista de tarjetas en móvil
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                libroNotas.forEach { fila ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fila.alumno.nombre,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val prom = fila.promedio
                                if (prom != null) {
                                    EstadoBadge(
                                        texto = "${AulaUtils.formatearNota(prom)} / 20",
                                        tipo = if (prom >= 10.5) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                                    )
                                } else {
                                    EstadoBadge(texto = "— / 20", tipo = TipoEstadoBadge.NEUTRAL)
                                }
                            }

                            HorizontalDivider()

                            // Notas de actividades
                            actividadesFiltradas.forEach { act ->
                                val n = fila.notas[act.id.orEmpty()]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${act.titulo} (/${AulaUtils.formatearNota(act.puntajeMax)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (n != null) AulaUtils.formatearNota(n) else "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Vista en tabla con desplazamiento horizontal para pantallas anchas
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fila de encabezados
                    Row(
                        modifier = Modifier.defaultMinSize(minWidth = 600.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estudiante",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(180.dp)
                        )

                        actividadesFiltradas.forEach { act ->
                            Column(
                                modifier = Modifier.width(110.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = act.titulo,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "/ ${AulaUtils.formatearNota(act.puntajeMax)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Text(
                            text = if (periodoSeleccionado > 0) "Prom. P$periodoSeleccionado (/20)" else "Promedio (/20)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(130.dp)
                        )
                    }

                    HorizontalDivider()

                    // Filas de estudiantes
                    libroNotas.forEach { f ->
                        Row(
                            modifier = Modifier.defaultMinSize(minWidth = 600.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = f.alumno.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            actividadesFiltradas.forEach { act ->
                                val nota = f.notas[act.id.orEmpty()]
                                Text(
                                    text = if (nota != null) AulaUtils.formatearNota(nota) else "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(110.dp)
                                )
                            }

                            val prom = f.promedio
                            Row(
                                modifier = Modifier.width(130.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (prom != null) {
                                    EstadoBadge(
                                        texto = AulaUtils.formatearNota(prom),
                                        tipo = if (prom >= 10.5) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                                    )
                                } else {
                                    Text(text = "—", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
