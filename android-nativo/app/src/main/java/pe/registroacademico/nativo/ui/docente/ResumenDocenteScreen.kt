package pe.registroacademico.nativo.ui.docente

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.docente.ResumenDocenteItem
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.Permisos
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun ResumenDocenteScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: ResumenDocenteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val esAdmin = Permisos.esAdmin(ctx.sesion.rol) || ctx.sesion.esSuperadmin

    LaunchedEffect(ctx.sesion.colegioId, ctx.sesion.userId) {
        if (ctx.sesion.colegioId.isNotBlank() || ctx.sesion.estaCargada) {
            viewModel.cargar(ctx.sesion.colegioId, ctx.sesion.userId, esAdmin)
        }
    }

    if (uiState.cargando && uiState.items.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 5)
        return
    }

    if (uiState.error != null && uiState.items.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error al cargar tu resumen",
            onReintentar = { viewModel.cargar(ctx.sesion.colegioId, ctx.sesion.userId, esAdmin) },
            modifier = modifier
        )
        return
    }

    if (uiState.items.isEmpty()) {
        EmptyState(
            titulo = "Aún no tienes cursos asignados",
            texto = "El administrador asigna cursos desde Gestión → Cursos.",
            icono = Icons.Default.Checklist,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PageHeader(
                    titulo = "Mi resumen",
                    subtitulo = "Tus cursos de hoy: quiénes ya marcaron asistencia, quiénes faltan seguido y qué entregas esperan tu nota.",
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.cargar(ctx.sesion.colegioId, ctx.sesion.userId, esAdmin) },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar mi resumen docente",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Fila de KPIs principales
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    titulo = "Cursos",
                    valor = "${uiState.totalCursos}",
                    icono = Icons.Default.Checklist,
                    colorIcono = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    titulo = "Presentes hoy",
                    valor = "${uiState.totalPresentesHoy}/${uiState.totalEstudiantes}",
                    icono = Icons.Default.Check,
                    colorIcono = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    titulo = "Por calificar",
                    valor = "${uiState.totalPorCalificar}",
                    icono = Icons.Default.Schedule,
                    colorIcono = if (uiState.totalPorCalificar > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        items(uiState.items) { item ->
            CursoResumenCard(
                item = item,
                onPasarLista = { ctx.navegar("pasar-lista") },
                onAbrirQr = { ctx.navegar("qr-curso") },
                onIrAula = { ctx.navegar("aula") }
            )
        }
    }
}

@Composable
private fun KpiCard(
    titulo: String,
    valor: String,
    icono: ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icono,
                    contentDescription = titulo,
                    modifier = Modifier.size(18.dp),
                    tint = colorIcono
                )
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = valor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CursoResumenCard(
    item: ResumenDocenteItem,
    onPasarLista: () -> Unit,
    onAbrirQr: () -> Unit,
    onIrAula: () -> Unit
) {
    val curso = item.curso
    val kpi = item.resumen
    val baja = item.bajaAsistencia

    val etiquetaCiclo = if (!curso.grado.isNullOrBlank()) {
        CiclosUtils.cicloCorto(curso.grado, curso.nivel)
    } else curso.nivel

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = etiquetaCiclo,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                EstadoBadge(
                    texto = "${kpi.pct}% presentes",
                    tipo = if (kpi.pct >= 80) TipoEstadoBadge.VERDE else if (kpi.pct >= 50) TipoEstadoBadge.AMBAR else TipoEstadoBadge.ROJO
                )
            }

            Text(
                text = curso.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Barra de progreso de asistencia de hoy
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (kpi.pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (kpi.pct >= 70) Color(0xFF2E7D32) else Color(0xFFE65100),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${kpi.presentes} de ${kpi.total} presentes hoy (${kpi.pct}%) · faltan ${kpi.faltan}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Metadatos académicos
            val actTexto = buildString {
                append("${kpi.porCalificar} por calificar")
                append(" · ${kpi.abiertas} ")
                append(if (kpi.abiertas == 1) "actividad abierta" else "actividades abiertas")
                if (kpi.proxima != null) {
                    append(" · vence «${kpi.proxima.titulo}»")
                }
            }
            Text(
                text = actTexto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Sección de estudiantes con baja asistencia
            if (baja.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerta de baja asistencia",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Baja asistencia esta semana (<60%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        baja.forEach { al ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = al.nombre,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${al.presentes}/${al.de} (${al.pct}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Botones de acción rápida
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onPasarLista,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Pasar lista",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pasar lista")
                }

                OutlinedButton(
                    onClick = onAbrirQr,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR asistencia",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("QR asistencia")
                }

                OutlinedButton(
                    onClick = onIrAula,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Aula virtual",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aula")
                }
            }
        }
    }
}
