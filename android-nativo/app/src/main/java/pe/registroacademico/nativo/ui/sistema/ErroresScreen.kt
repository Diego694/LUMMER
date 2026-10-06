package pe.registroacademico.nativo.ui.sistema

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.SistemaRepo
import pe.registroacademico.nativo.data.model.LogCliente
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class ErroresUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val errores: List<LogCliente> = emptyList(),
    val borrando: Boolean = false
)

@HiltViewModel
class ErroresViewModel @Inject constructor(
    private val sistemaRepo: SistemaRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ErroresUiState())
    val uiState: StateFlow<ErroresUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val lista = sistemaRepo.erroresRecientes(colegioId, limite = 200)
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    errores = lista
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar el registro de errores"
                )
            }
        }
    }

    fun borrarTodos(
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(borrando = true)
            try {
                sistemaRepo.borrarErrores(colegioId)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudieron borrar los errores")
            } finally {
                _uiState.value = _uiState.value.copy(borrando = false)
            }
        }
    }
}

@Composable
fun ErroresScreen(
    ctx: PantallaCtx,
    viewModel: ErroresViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    var confirmBorrarTodos by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Registro de errores",
            subtitulo = "Fallos ocurridos en los teléfonos de docentes y estudiantes (sin datos personales). Úsalo para detectar problemas antes de que te los reporten.",
            acciones = {
                if (state.errores.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { confirmBorrarTodos = true },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Borrar todos")
                    }
                }
            }
        )

        when {
            state.cargando -> {
                SkeletonList(cantidad = 4)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error inesperado",
                    onReintentar = { viewModel.cargar(cid) }
                )
            }

            state.errores.isEmpty() -> {
                EmptyState(
                    titulo = "Sin errores registrados",
                    texto = "Todo funciona bien. Aquí aparecerán los fallos que ocurran en los teléfonos.",
                    icono = Icons.Default.CheckCircle
                )
            }

            else -> {
                SectionCard(titulo = "Fallos recientes (${state.errores.size})") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.errores.forEach { errItem ->
                            TarjetaErrorItem(errItem)
                        }
                    }
                }
            }
        }
    }

    if (confirmBorrarTodos) {
        ConfirmDialog(
            titulo = "Borrar registro de errores",
            mensaje = "Se eliminarán todos los errores registrados del sistema. ¿Deseas continuar?",
            textoConfirmar = "Borrar",
            esPeligro = true,
            onConfirmar = {
                confirmBorrarTodos = false
                viewModel.borrarTodos(
                    colegioId = cid,
                    onSuccess = {
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Registro de errores vaciado") }
                    },
                    onError = { err ->
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { confirmBorrarTodos = false }
        )
    }
}

@Composable
private fun TarjetaErrorItem(err: LogCliente) {
    var expandido by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EstadoBadge(
                        texto = err.app?.ifBlank { "app" } ?: "app",
                        tipo = if (err.app == "estudiante") TipoEstadoBadge.AMBAR else TipoEstadoBadge.NEUTRAL
                    )

                    Text(
                        text = err.creadoEn?.let {
                            DateUtils.fmtDate(it.take(10)) + " " + it.drop(11).take(5)
                        } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = err.mensaje ?: "(Sin mensaje de error)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!err.url.isNullOrBlank() || !err.agente.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!err.url.isNullOrBlank()) {
                        Text(
                            text = "Ruta: ${err.url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!err.agente.isNullOrBlank()) {
                        Text(
                            text = "Dispositivo: ${err.agente}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!err.detalle.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandido = !expandido }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expandido) "Ocultar detalle" else "Ver detalle técnico",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (expandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = expandido) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = err.detalle.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
