package pe.registroacademico.nativo.ui.registro

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun SolicitudesScreen(
    ctx: PantallaCtx,
    viewModel: SolicitudesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(ctx.sesion.colegioId) {
        viewModel.cargarSolicitudes(ctx.sesion.colegioId)
    }

    LaunchedEffect(uiState.mensajeExito) {
        uiState.mensajeExito?.let { msg ->
            SnackbarHelper.mostrarExito(ctx.snackbarHostState, msg)
            viewModel.limpiarMensajeExito()
        }
    }

    if (uiState.cargando && uiState.solicitudes.isEmpty()) {
        SkeletonList(cantidad = 4)
        return
    }

    if (uiState.error != null && uiState.solicitudes.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error al cargar las solicitudes de ingreso",
            onReintentar = { viewModel.cargarSolicitudes(ctx.sesion.colegioId) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            titulo = "Solicitudes de ingreso",
            subtitulo = "Estudiantes que se registraron con el código del instituto y esperan tu aprobación. Hasta entonces su QR no registra asistencia.",
            acciones = {
                OutlinedButton(
                    onClick = { viewModel.cargarSolicitudes(ctx.sesion.colegioId) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.solicitudes.size} solicitud(es) pendiente(s)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.solicitudes.isNotEmpty()) {
                EstadoBadge(
                    texto = "Por aprobar",
                    tipo = TipoEstadoBadge.AMBAR
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.solicitudes.isEmpty()) {
            EmptyState(
                titulo = "No hay solicitudes pendientes",
                texto = "Cuando un estudiante se registre con el código del instituto, aparecerá aquí para que lo apruebes.",
                icono = Icons.Default.HowToReg
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.solicitudes, key = { it.id }) { alumno ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
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

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alumno.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${alumno.codigo} · ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!alumno.dni.isNullOrBlank()) {
                                        Text(
                                            text = "DNI: ${alumno.dni}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!alumno.apoderado.isNullOrBlank()) {
                                        Text(
                                            text = "Apoderado: ${alumno.apoderado}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.abrirRevision(alumno) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ver")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { viewModel.solicitarRechazo(alumno) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Rechazar")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        viewModel.aprobarSolicitud(
                                            colegioId = ctx.sesion.colegioId,
                                            alumno = alumno
                                        )
                                    },
                                    enabled = uiState.procesandoId != alumno.id,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Aprobar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Revisión de Estudiante
    uiState.alumnoParaRevisar?.let { alumno ->
        AlertDialog(
            onDismissRequest = { viewModel.cerrarRevision() },
            title = {
                Text(
                    text = "Revisar registro de estudiante",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = StringUtils.initials(alumno.nombre),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Nombre: ${alumno.nombre}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Carrera · Ciclo: ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!alumno.dni.isNullOrBlank()) {
                        Text(
                            text = "DNI: ${alumno.dni}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!alumno.apoderado.isNullOrBlank()) {
                        Text(
                            text = "Apoderado: ${alumno.apoderado}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "Código único: ${alumno.codigo}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Al aprobar, su carnet QR podrá registrar asistencia. Al rechazar, se elimina el registro de la plataforma.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.aprobarSolicitud(
                            colegioId = ctx.sesion.colegioId,
                            alumno = alumno
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Aprobar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.solicitarRechazo(alumno) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Rechazar",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    // Modal de Confirmación de Rechazo
    uiState.alumnoParaRechazar?.let { alumno ->
        ConfirmDialog(
            titulo = "Rechazar solicitud",
            mensaje = "¿Rechazar y eliminar el registro de ${alumno.nombre}?",
            esPeligro = true,
            textoConfirmar = "Rechazar",
            onConfirmar = { viewModel.confirmarRechazar(ctx.sesion.colegioId) },
            onCancelar = { viewModel.cancelarRechazo() }
        )
    }
}
