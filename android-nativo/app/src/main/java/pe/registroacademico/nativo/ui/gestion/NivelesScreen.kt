package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.CampoFormulario
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.FormDialog
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class NivelesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val alumnos: List<Alumno> = emptyList()
)

@HiltViewModel
class NivelesViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val alumnosRepo: AlumnosRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NivelesUiState())
    val uiState: StateFlow<NivelesUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val niveles = if (colegioId.isNotBlank()) catalogosRepo.listarNiveles(colegioId) else emptyList()
                val grados = if (colegioId.isNotBlank()) catalogosRepo.listarGrados(colegioId) else emptyList()
                val alumnos = if (colegioId.isNotBlank()) alumnosRepo.listar(colegioId) else emptyList()

                _uiState.update {
                    it.copy(
                        cargando = false,
                        niveles = niveles,
                        grados = grados,
                        alumnos = alumnos
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar carreras"
                    )
                }
            }
        }
    }

    fun agregarNivel(nombre: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val nombreLimpio = nombre.trim().replace(Regex("\\s+"), " ").uppercase()
            if (nombreLimpio.isBlank()) {
                onError("El nombre de la carrera no puede estar vacío.")
                return@launch
            }
            if (_uiState.value.niveles.any { StringUtils.norm(it.nombre) == StringUtils.norm(nombreLimpio) }) {
                onError("Esa carrera ya existe.")
                return@launch
            }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                catalogosRepo.guardarNivel(Nivel(colegioId = colegioId, nombre = nombreLimpio))
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al agregar la carrera")
            }
        }
    }

    fun eliminarNivel(nivel: Nivel, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val id = nivel.id ?: return@launch
            val cantGrados = _uiState.value.grados.count { it.nivel == nivel.nombre }
            val cantAlumnos = _uiState.value.alumnos.count { it.nivel == nivel.nombre }
            if (cantGrados > 0 || cantAlumnos > 0) {
                onError("No se puede eliminar \"${nivel.nombre}\": tiene $cantGrados ciclo(s) y $cantAlumnos alumno(s) asociados.")
                return@launch
            }
            try {
                catalogosRepo.eliminarNivel(id)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar la carrera")
            }
        }
    }
}

@Composable
fun NivelesScreen(
    ctx: PantallaCtx,
    viewModel: NivelesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarDialogoAgregar by remember { mutableStateOf(false) }
    var nivelParaEliminar by remember { mutableStateOf<Nivel?>(null) }

    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Carreras",
            subtitulo = "Carreras o programas de estudio del instituto. Cada una tiene sus propios ciclos y salones.",
            acciones = {
                Button(
                    onClick = { mostrarDialogoAgregar = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar carrera")
                }
            }
        )

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 3)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar carreras",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            uiState.niveles.isEmpty() -> {
                EmptyState(
                    titulo = "Sin carreras",
                    texto = "Crea la primera carrera (por ejemplo MECANICA ELECTRICA) o usa «Crear ciclos» en Ciclos y salones.",
                    icono = Icons.Default.Layers,
                    accion = {
                        Button(
                            onClick = { mostrarDialogoAgregar = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Agregar carrera")
                        }
                    }
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.niveles.forEach { nivel ->
                        val cantGrados = uiState.grados.count { it.nivel == nivel.nombre }
                        val cantAlumnos = uiState.alumnos.count { it.nivel == nivel.nombre }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = nivel.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$cantGrados ciclos · $cantAlumnos alumnos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { ctx.navegar("grados") },
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ciclos")
                                    }

                                    IconButton(
                                        onClick = { nivelParaEliminar = nivel },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar ${nivel.nombre}",
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

    if (mostrarDialogoAgregar) {
        FormDialog(
            titulo = "Agregar carrera",
            campos = listOf(
                CampoFormulario(
                    nombre = "nombre",
                    etiqueta = "Nombre de la carrera",
                    placeholder = "Ej: MECANICA ELECTRICA"
                )
            ),
            onConfirmar = { valores ->
                val nom = valores["nombre"].orEmpty()
                viewModel.agregarNivel(
                    nombre = nom,
                    onExito = {
                        mostrarDialogoAgregar = false
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Carrera agregada")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { mostrarDialogoAgregar = false }
        )
    }

    nivelParaEliminar?.let { nivel ->
        ConfirmDialog(
            titulo = "Confirmar eliminación",
            mensaje = "¿Eliminar la carrera ${nivel.nombre}?",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = {
                viewModel.eliminarNivel(
                    nivel = nivel,
                    onExito = {
                        nivelParaEliminar = null
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Carrera eliminada")
                        }
                    },
                    onError = { err ->
                        nivelParaEliminar = null
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { nivelParaEliminar = null }
        )
    }
}
