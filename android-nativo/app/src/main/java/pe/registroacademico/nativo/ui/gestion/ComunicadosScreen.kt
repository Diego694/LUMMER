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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Comunicado
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class ComunicadosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val comunicados: List<Comunicado> = emptyList(),
    val guardando: Boolean = false
)

@HiltViewModel
class ComunicadosViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComunicadosUiState())
    val uiState: StateFlow<ComunicadosUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val lista = catalogosRepo.listarComunicados(colegioId)
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    comunicados = lista
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar los comunicados"
                )
            }
        }
    }

    fun guardar(
        comunicado: Comunicado,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.guardarComunicado(comunicado)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo publicar el comunicado")
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
                catalogosRepo.eliminarComunicado(id)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo eliminar el comunicado")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }
}

@Composable
fun ComunicadosScreen(
    ctx: PantallaCtx,
    viewModel: ComunicadosViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    var showNuevoModal by remember { mutableStateOf(false) }
    var confirmEliminarId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Comunicados",
            subtitulo = "Avisos institucionales para alumnos y apoderados.",
            acciones = {
                Button(
                    onClick = { showNuevoModal = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nuevo comunicado")
                }
            }
        )

        when {
            state.cargando -> {
                SkeletonList(cantidad = 4)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error al cargar comunicados",
                    onReintentar = { viewModel.cargar(cid) }
                )
            }

            state.comunicados.isEmpty() -> {
                EmptyState(
                    titulo = "Sin comunicados",
                    texto = "Publica el primer comunicado para tus alumnos y apoderados.",
                    icono = Icons.Default.Campaign,
                    accion = {
                        Button(
                            onClick = { showNuevoModal = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Publicar comunicado")
                        }
                    }
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.comunicados.forEach { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = c.titulo,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${DateUtils.fmtDate(c.fecha)}${if (!c.publicadoPor.isNullOrBlank()) " · Por ${c.publicadoPor}" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { confirmEliminarId = c.id },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar comunicado",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                Text(
                                    text = c.mensaje,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Nuevo Comunicado
    if (showNuevoModal) {
        var tituloInput by remember { mutableStateOf("") }
        var mensajeInput by remember { mutableStateOf("") }
        var errorModal by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showNuevoModal = false },
            title = { Text("Nuevo comunicado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tituloInput,
                        onValueChange = { tituloInput = it },
                        label = { Text("Título del comunicado *") },
                        placeholder = { Text("Ej: Suspensión de clases por feriado") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = mensajeInput,
                        onValueChange = { mensajeInput = it },
                        label = { Text("Mensaje *") },
                        placeholder = { Text("Escribe el comunicado detallado...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
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
                        if (tituloInput.isBlank() || mensajeInput.isBlank()) {
                            errorModal = "Completa el título y el mensaje del comunicado."
                            return@Button
                        }
                        val comunicadoObj = Comunicado(
                            colegioId = cid,
                            titulo = tituloInput.trim(),
                            mensaje = mensajeInput.trim(),
                            fecha = DateUtils.todayStr(),
                            publicadoPor = ctx.sesion.perfil?.nombre ?: "Dirección"
                        )
                        viewModel.guardar(
                            comunicado = comunicadoObj,
                            colegioId = cid,
                            onSuccess = {
                                showNuevoModal = false
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Comunicado publicado") }
                            },
                            onError = { errorModal = it }
                        )
                    },
                    enabled = !state.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (state.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Publicar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNuevoModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Confirmar Eliminar
    confirmEliminarId?.let { cidEliminar ->
        ConfirmDialog(
            titulo = "Eliminar comunicado",
            mensaje = "¿Estás seguro de que deseas eliminar este comunicado?",
            textoConfirmar = "Eliminar",
            esPeligro = true,
            onConfirmar = {
                viewModel.eliminar(
                    id = cidEliminar,
                    colegioId = cid,
                    onSuccess = {
                        confirmEliminarId = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Comunicado eliminado") }
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
