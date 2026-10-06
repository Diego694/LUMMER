package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Justificacion
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class JustificacionesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val justificaciones: List<Justificacion> = emptyList(),
    val alumnos: List<Alumno> = emptyList(),
    val carreras: List<Nivel> = emptyList(),
    val guardando: Boolean = false
)

@HiltViewModel
class JustificacionesViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val alumnosRepo: AlumnosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(JustificacionesUiState())
    val uiState: StateFlow<JustificacionesUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val hoy = DateUtils.todayStr()
                val desde90 = DateUtils.addDays(hoy, -90)
                val lista = catalogosRepo.justificacionesRango(colegioId, desde90, hoy)
                    .sortedByDescending { it.fecha }
                val alumnos = alumnosRepo.listar(colegioId)
                val carreras = catalogosRepo.listarNiveles(colegioId)

                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    justificaciones = lista,
                    alumnos = alumnos,
                    carreras = carreras
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar justificaciones"
                )
            }
        }
    }

    fun guardar(
        justificacion: Justificacion,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.guardarJustificacion(justificacion)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo registrar la justificación")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun eliminar(
        id: String,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.eliminarJustificacion(id)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo eliminar la justificación")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }
}

@Composable
fun JustificacionesScreen(
    ctx: PantallaCtx,
    viewModel: JustificacionesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()
    val hoy = remember { DateUtils.todayStr() }

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    var showNuevaModal by remember { mutableStateOf(false) }
    var confirmEliminarId by remember { mutableStateOf<String?>(null) }

    val mapaAlumnos = remember(state.alumnos) {
        state.alumnos.associateBy { it.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Justificaciones",
            subtitulo = "Faltas justificadas, permisos y tardanzas justificadas. Una justificada no cuenta como falta en los reportes.",
            acciones = {
                Button(
                    onClick = { showNuevaModal = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nueva justificación")
                }
            }
        )

        when {
            state.cargando -> {
                SkeletonList(cantidad = 5)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error al cargar las justificaciones",
                    onReintentar = { viewModel.cargar(cid) }
                )
            }

            state.justificaciones.isEmpty() -> {
                EmptyState(
                    titulo = "Sin justificaciones",
                    texto = "Las faltas o permisos que registres aparecerán aquí (últimos 90 días).",
                    icono = Icons.Default.EventAvailable,
                    accion = {
                        Button(
                            onClick = { showNuevaModal = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Nueva justificación")
                        }
                    }
                )
            }

            else -> {
                SectionCard(titulo = "Registradas (últimos 90 días)") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.justificaciones.forEach { j ->
                            val al = mapaAlumnos[j.alumnoId]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = DateUtils.fmtDate(j.fecha),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val badgeTipo = when (j.tipo) {
                                                "Falta justificada" -> TipoEstadoBadge.VERDE
                                                "Permiso" -> TipoEstadoBadge.AZUL
                                                else -> TipoEstadoBadge.AMBAR
                                            }
                                            EstadoBadge(texto = j.tipo, tipo = badgeTipo)
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = al?.nombre ?: "Alumno (ID: ${j.alumnoId.take(8)})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (al != null) {
                                            Text(
                                                text = "${al.nivel} · ${al.grado}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (!j.motivo.isNullOrBlank()) {
                                            Text(
                                                text = "Motivo: ${j.motivo}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { confirmEliminarId = j.id },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar justificación",
                                            tint = MaterialTheme.colorScheme.error
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

    // Modal Nueva Justificación
    if (showNuevaModal) {
        var carreraSel by remember { mutableStateOf("") }
        var alumnoSelId by remember { mutableStateOf("") }
        var fechaSel by remember { mutableStateOf(hoy) }
        var tipoSel by remember { mutableStateOf("Falta justificada") }
        var motivoInput by remember { mutableStateOf("") }
        var errorModal by remember { mutableStateOf<String?>(null) }

        val alumnosFiltrados = remember(carreraSel, state.alumnos) {
            state.alumnos.filter { a ->
                a.estado == "ACTIVO" && (carreraSel.isBlank() || a.nivel == carreraSel)
            }.sortedBy { it.nombre }
        }

        AlertDialog(
            onDismissRequest = { showNuevaModal = false },
            title = { Text("Nueva justificación") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val carrerasOpciones = listOf(OpcionDropdown("", "Todas las carreras")) +
                            state.carreras.map { OpcionDropdown(it.nombre, it.nombre) }

                    DropdownSelector(
                        etiqueta = "Carrera (filtro)",
                        opciones = carrerasOpciones,
                        seleccion = carreraSel,
                        onSeleccionar = {
                            carreraSel = it
                            alumnoSelId = ""
                        }
                    )

                    val alumnosOpciones = alumnosFiltrados.map {
                        OpcionDropdown(it.id, "${it.nombre} (${it.codigo})")
                    }

                    if (alumnosOpciones.isEmpty()) {
                        Text(
                            text = "No hay alumnos activos para la carrera seleccionada.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        DropdownSelector(
                            etiqueta = "Alumno *",
                            opciones = alumnosOpciones,
                            seleccion = alumnoSelId.ifBlank { alumnosOpciones.first().id },
                            onSeleccionar = { alumnoSelId = it }
                        )
                    }

                    OutlinedTextField(
                        value = fechaSel,
                        onValueChange = { fechaSel = it },
                        label = { Text("Fecha (YYYY-MM-DD) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    val tiposOpciones = listOf(
                        OpcionDropdown("Falta justificada", "Falta justificada"),
                        OpcionDropdown("Permiso", "Permiso"),
                        OpcionDropdown("Tardanza justificada", "Tardanza justificada")
                    )
                    DropdownSelector(
                        etiqueta = "Tipo de justificación *",
                        opciones = tiposOpciones,
                        seleccion = tipoSel,
                        onSeleccionar = { tipoSel = it }
                    )

                    OutlinedTextField(
                        value = motivoInput,
                        onValueChange = { motivoInput = it },
                        label = { Text("Motivo") },
                        placeholder = { Text("Ej: Cita médica, certificado adjunto") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    if (errorModal != null) {
                        Text(
                            text = errorModal.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val alId = alumnoSelId.ifBlank { alumnosFiltrados.firstOrNull()?.id.orEmpty() }
                        if (alId.isBlank()) {
                            errorModal = "Debes seleccionar un alumno."
                            return@Button
                        }
                        if (fechaSel.isBlank()) {
                            errorModal = "Ingresa la fecha de la justificación."
                            return@Button
                        }
                        if (fechaSel > hoy) {
                            errorModal = "La fecha no puede ser futura."
                            return@Button
                        }

                        val obj = Justificacion(
                            colegioId = cid,
                            alumnoId = alId,
                            fecha = fechaSel.trim(),
                            tipo = tipoSel,
                            motivo = motivoInput.trim().ifBlank { null },
                            registradoPor = ctx.sesion.perfil?.nombre ?: "Personal"
                        )

                        viewModel.guardar(
                            justificacion = obj,
                            colegioId = cid,
                            onSuccess = {
                                showNuevaModal = false
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Justificación registrada") }
                            },
                            onError = { errorModal = it }
                        )
                    },
                    enabled = !state.guardando && alumnosFiltrados.isNotEmpty(),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (state.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNuevaModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo confirmar eliminación
    confirmEliminarId?.let { jid ->
        ConfirmDialog(
            titulo = "Eliminar justificación",
            mensaje = "¿Estás seguro de que deseas eliminar esta justificación?",
            textoConfirmar = "Eliminar",
            esPeligro = true,
            onConfirmar = {
                viewModel.eliminar(
                    id = jid,
                    colegioId = cid,
                    onSuccess = {
                        confirmEliminarId = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Justificación eliminada") }
                    },
                    onError = { err ->
                        confirmEliminarId = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { confirmEliminarId = null }
        )
    }
}
