package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.InstitucionesRepo
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.ui.components.CampoFormulario
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.FormDialog
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class InstitutoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val nombre: String = "",
    val codigoRegistro: String = "—",
    val qrModo: String = "off",
    val guardandoQr: Boolean = false
)

@HiltViewModel
class InstitutoViewModel @Inject constructor(
    private val institucionesRepo: InstitucionesRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstitutoUiState())
    val uiState: StateFlow<InstitutoUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val sesion = sesionManager.sesion.value
                val colegioId = sesion.colegioId
                val nombre = sesion.nombreInstituto.ifBlank { "Sin nombre" }
                val qrModo = sesion.qrModo.ifBlank { "off" }

                val codigo = if (colegioId.isNotBlank()) {
                    institucionesRepo.getCodigoRegistro(colegioId) ?: "—"
                } else "—"

                _uiState.update {
                    it.copy(
                        cargando = false,
                        nombre = nombre,
                        codigoRegistro = codigo,
                        qrModo = qrModo
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar la información del instituto"
                    )
                }
            }
        }
    }

    fun cambiarNombre(nuevoNombre: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val nombreLimpio = nuevoNombre.trim()
            if (nombreLimpio.length < 3 || nombreLimpio.length > 80) {
                onError("El nombre debe tener entre 3 y 80 caracteres.")
                return@launch
            }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                if (colegioId.isNotBlank()) {
                    institucionesRepo.renombrarInstituto(colegioId, nombreLimpio)
                }
                sesionManager.refrescarInstituto(nombreLimpio)
                _uiState.update { it.copy(nombre = nombreLimpio) }
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al renombrar el instituto")
            }
        }
    }

    fun guardarQrModo(modo: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(guardandoQr = true) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                if (colegioId.isNotBlank()) {
                    institucionesRepo.guardarQrModo(colegioId, modo)
                }
                sesionManager.refrescarQrModo(modo)
                _uiState.update { it.copy(qrModo = modo, guardandoQr = false) }
                onExito()
            } catch (e: Exception) {
                _uiState.update { it.copy(guardandoQr = false) }
                onError(e.localizedMessage ?: "Error al guardar el modo QR")
            }
        }
    }
}

@Composable
fun InstitutoScreen(
    ctx: PantallaCtx,
    viewModel: InstitutoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarDialogoNombre by remember { mutableStateOf(false) }
    var modoSeleccionado by remember(uiState.qrModo) { mutableStateOf(uiState.qrModo) }

    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    LaunchedEffect(uiState.qrModo) {
        modoSeleccionado = uiState.qrModo
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Mi instituto",
            subtitulo = "Nombre y código de registro de tu institución"
        )

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 2)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error desconocido",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            else -> {
                // Tarjeta 1: Información general
                SectionCard(titulo = "Datos institucionales") {
                    Text(
                        text = uiState.nombre,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Código de registro:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = uiState.codigoRegistro,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { mostrarDialogoNombre = true },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Cambiar nombre",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cambiar nombre")
                    }

                    Text(
                        text = "Este nombre aparece en el menú, el carnet, el portal del estudiante y los reportes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tarjeta 2: Seguridad del QR
                SectionCard(titulo = "Seguridad del QR de asistencia") {
                    Text(
                        text = "Configura cómo valida la cámara los códigos QR de los carnets de los estudiantes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val opciones = listOf(
                        Triple("off", "Desactivado", "QR fijo"),
                        Triple("opcional", "Opcional", "El estudiante muestra QR que cambia, se aceptan ambos"),
                        Triple("obligatorio", "Obligatorio (recomendado)", "La cámara solo acepta el QR que cambia cada 30 s; NFC y código manual siguen valiendo")
                    )

                    opciones.forEach { (modo, titulo, desc) ->
                        val seleccionado = modoSeleccionado == modo
                        val colorBorde = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, colorBorde, RoundedCornerShape(8.dp))
                                .clickable { modoSeleccionado = modo }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = seleccionado,
                                onClick = { modoSeleccionado = modo },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = titulo,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            viewModel.guardarQrModo(
                                modo = modoSeleccionado,
                                onExito = {
                                    ctx.scope.launch {
                                        SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Seguridad del QR actualizada")
                                    }
                                },
                                onError = { errorMsg ->
                                    ctx.scope.launch {
                                        SnackbarHelper.mostrarError(ctx.snackbarHostState, errorMsg)
                                    }
                                }
                            )
                        },
                        enabled = !uiState.guardandoQr,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (uiState.guardandoQr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Guardar")
                    }
                }
            }
        }
    }

    if (mostrarDialogoNombre) {
        FormDialog(
            titulo = "Cambiar nombre de la institución",
            campos = listOf(
                CampoFormulario(
                    nombre = "nombre",
                    etiqueta = "Nombre",
                    valorInicial = uiState.nombre,
                    placeholder = "Entre 3 y 80 caracteres"
                )
            ),
            onConfirmar = { valores ->
                val nuevo = valores["nombre"].orEmpty()
                viewModel.cambiarNombre(
                    nuevoNombre = nuevo,
                    onExito = {
                        mostrarDialogoNombre = false
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Nombre actualizado")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { mostrarDialogoNombre = false }
        )
    }
}
