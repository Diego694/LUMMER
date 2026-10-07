package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.OrigenMatricula
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EstudiantesCursoDialog(
    curso: Curso,
    colegioId: String,
    userId: String,
    onCerrar: () -> Unit,
    onActualizado: () -> Unit = {},
    viewModel: EstudiantesCursoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var alumnoParaQuitar by remember { mutableStateOf<Alumno?>(null) }
    var mensajeErrorLocal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(curso.id) {
        viewModel.cargar(curso, colegioId)
    }

    val detalleCiclo = if (!curso.grado.isNullOrBlank()) {
        CiclosUtils.etiquetaCiclo(curso.nivel, curso.grado)
    } else "${curso.nivel} · todos los ciclos"

    val qEst = uiState.busquedaEstudiantes.trim().lowercase()
    val estudiantesFiltrados = remember(uiState.estudiantes, qEst) {
        if (qEst.isBlank()) {
            uiState.estudiantes
        } else {
            uiState.estudiantes.filter { item ->
                item.alumno.nombre.lowercase().contains(qEst) ||
                    item.alumno.codigo.lowercase().contains(qEst) ||
                    item.alumno.nivel.lowercase().contains(qEst)
            }
        }
    }

    val qCand = uiState.busquedaCandidatos.trim().lowercase()
    val candidatosFiltrados = remember(uiState.candidatos, qCand) {
        if (qCand.isBlank()) {
            uiState.candidatos
        } else {
            uiState.candidatos.filter { a ->
                a.nombre.lowercase().contains(qCand) ||
                    a.codigo.lowercase().contains(qCand) ||
                    a.nivel.lowercase().contains(qCand) ||
                    (a.grado?.lowercase()?.contains(qCand) == true)
            }
        }
    }

    val conteoCiclo = remember(uiState.estudiantes) {
        uiState.estudiantes.count { it.origen == OrigenMatricula.CICLO }
    }
    val conteoManual = remember(uiState.estudiantes) {
        uiState.estudiantes.count { it.origen == OrigenMatricula.MANUAL }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Estudiantes del curso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${curso.nombre} · $detalleCiclo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fila superior: Resumen de chips y botón "Agregar alumnos"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EstadoBadge(
                            texto = "Ciclo: $conteoCiclo",
                            tipo = TipoEstadoBadge.NEUTRAL
                        )
                        EstadoBadge(
                            texto = "Manual: $conteoManual",
                            tipo = if (conteoManual > 0) TipoEstadoBadge.AMBAR else TipoEstadoBadge.NEUTRAL
                        )
                    }

                    Button(
                        onClick = { viewModel.abrirAgregarModal() },
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Agregar alumnos",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }

                if (mensajeErrorLocal != null) {
                    Text(
                        text = mensajeErrorLocal!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Buscador de estudiantes actuales
                SearchField(
                    query = uiState.busquedaEstudiantes,
                    onQueryChange = { viewModel.cambiarBusquedaEstudiantes(it) },
                    placeholder = "Buscar estudiante...",
                    modifier = Modifier.fillMaxWidth()
                )

                // Lista de estudiantes del curso
                when {
                    uiState.cargando -> {
                        SkeletonList(cantidad = 3)
                    }

                    uiState.error != null -> {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    estudiantesFiltrados.isEmpty() -> {
                        EmptyState(
                            titulo = if (uiState.estudiantes.isNotEmpty()) "Sin coincidencias" else "Sin estudiantes",
                            texto = if (uiState.estudiantes.isNotEmpty()) {
                                "Ningún estudiante coincide con la búsqueda."
                            } else {
                                "No hay estudiantes en este curso. Usa «Agregar alumnos» para matricularlos manualmente."
                            },
                            icono = Icons.Default.Group
                        )
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            estudiantesFiltrados.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(34.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = StringUtils.initials(item.alumno.nombre),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.alumno.nombre,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${item.alumno.codigo} · ${CiclosUtils.etiquetaCiclo(item.alumno.nivel, item.alumno.grado)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        FlowRow(
                                            verticalArrangement = Arrangement.Center,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (item.origen == OrigenMatricula.MANUAL) {
                                                EstadoBadge(
                                                    texto = "Manual",
                                                    tipo = TipoEstadoBadge.AMBAR
                                                )
                                                IconButton(
                                                    onClick = { alumnoParaQuitar = item.alumno },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Quitar ${item.alumno.nombre}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                EstadoBadge(
                                                    texto = "Por su ciclo",
                                                    tipo = TipoEstadoBadge.NEUTRAL
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCerrar,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("Cerrar")
            }
        }
    )

    // Diálogo secundario: Agregar alumnos (buscador y selección múltiple)
    if (uiState.mostrarAgregarModal) {
        var errorAgregar by remember { mutableStateOf<String?>(null) }
        val seleccionadosCount = uiState.candidatosSeleccionados.size

        AlertDialog(
            onDismissRequest = { viewModel.cerrarAgregarModal() },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Agregar alumnos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Alumnos activos del instituto aún no matriculados en ${curso.nombre}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorAgregar != null) {
                        Text(
                            text = errorAgregar!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    SearchField(
                        query = uiState.busquedaCandidatos,
                        onQueryChange = { viewModel.cambiarBusquedaCandidatos(it) },
                        placeholder = "Buscar por nombre o código...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (seleccionadosCount > 0) {
                        Text(
                            text = "$seleccionadosCount seleccionado(s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (candidatosFiltrados.isEmpty()) {
                        EmptyState(
                            titulo = if (uiState.candidatos.isNotEmpty()) "Sin resultados" else "Sin candidatos",
                            texto = if (uiState.candidatos.isNotEmpty()) {
                                "No se encontraron alumnos con ese criterio."
                            } else {
                                "Todos los alumnos activos del instituto ya pertenecen a este curso."
                            },
                            icono = Icons.Default.Group
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            candidatosFiltrados.forEach { cand ->
                                val estaSeleccionado = uiState.candidatosSeleccionados.contains(cand.id)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (estaSeleccionado) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                    onClick = { viewModel.toggleSeleccionCandidato(cand.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = estaSeleccionado,
                                            onCheckedChange = { viewModel.toggleSeleccionCandidato(cand.id) }
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cand.nombre,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${cand.codigo} · ${CiclosUtils.etiquetaCiclo(cand.nivel, cand.grado)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.agregarSeleccionados(
                            userId = userId,
                            onExito = {
                                onActualizado()
                            },
                            onError = { err ->
                                errorAgregar = err
                            }
                        )
                    },
                    enabled = seleccionadosCount > 0 && !uiState.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    if (uiState.guardando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        if (seleccionadosCount > 0) "Agregar ($seleccionadosCount)" else "Agregar",
                        maxLines = 1
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cerrarAgregarModal() },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ConfirmDialog para quitar alumno matriculado manualmente
    alumnoParaQuitar?.let { alumno ->
        ConfirmDialog(
            titulo = "Quitar alumno",
            mensaje = "¿Quitar a ${alumno.nombre} de este curso? Solo se puede retirar a los estudiantes matriculados manualmente.",
            esPeligro = true,
            textoConfirmar = "Quitar",
            onConfirmar = {
                viewModel.quitarAlumno(
                    alumnoId = alumno.id,
                    onExito = {
                        alumnoParaQuitar = null
                        onActualizado()
                    },
                    onError = { err ->
                        alumnoParaQuitar = null
                        mensajeErrorLocal = err
                    }
                )
            },
            onCancelar = { alumnoParaQuitar = null }
        )
    }
}
