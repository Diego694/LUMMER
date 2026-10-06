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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class CursosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val cursos: List<Curso> = emptyList(),
    val carreras: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList()
)

@HiltViewModel
class CursosViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CursosUiState())
    val uiState: StateFlow<CursosUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val cursos = if (colegioId.isNotBlank()) catalogosRepo.listarCursos(colegioId) else emptyList()
                val carreras = if (colegioId.isNotBlank()) catalogosRepo.listarNiveles(colegioId) else emptyList()
                val grados = if (colegioId.isNotBlank()) catalogosRepo.listarGrados(colegioId) else emptyList()

                _uiState.update {
                    it.copy(
                        cargando = false,
                        cursos = cursos,
                        carreras = carreras,
                        grados = grados
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar cursos"
                    )
                }
            }
        }
    }

    fun guardarCurso(curso: Curso, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val cursoConColegio = if (curso.colegioId.isNullOrBlank()) {
                    curso.copy(colegioId = colegioId)
                } else curso
                catalogosRepo.guardarCurso(cursoConColegio)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al guardar curso")
            }
        }
    }

    fun eliminarCurso(id: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                catalogosRepo.eliminarCurso(id)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar curso")
            }
        }
    }
}

@Composable
fun CursosScreen(
    ctx: PantallaCtx,
    viewModel: CursosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var mostrarModalCrear by remember { mutableStateOf(false) }
    var cursoParaEditar by remember { mutableStateOf<Curso?>(null) }
    var cursoParaEliminar by remember { mutableStateOf<Curso?>(null) }

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
            titulo = "Cursos",
            subtitulo = "Cursos o asignaturas por carrera (y ciclo). Permiten pasar lista por curso además de la asistencia diaria.",
            acciones = {
                Button(
                    onClick = { mostrarModalCrear = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar curso")
                }
            }
        )

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 4)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar cursos",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            uiState.cursos.isEmpty() -> {
                EmptyState(
                    titulo = "Sin cursos",
                    texto = "Agrega los cursos para poder pasar lista por asignatura.",
                    icono = Icons.Default.Book,
                    accion = {
                        Button(
                            onClick = { mostrarModalCrear = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Agregar curso")
                        }
                    }
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.cursos.forEach { curso ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = curso.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val detalleCiclo = if (!curso.grado.isNullOrBlank()) {
                                        CiclosUtils.etiquetaCiclo(curso.nivel, curso.grado)
                                    } else "${curso.nivel} · todos los ciclos"

                                    Text(
                                        text = detalleCiclo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!curso.docente.isNullOrBlank()) {
                                        Text(
                                            text = "Docente: ${curso.docente}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val activo = curso.activo != false
                                    EstadoBadge(
                                        texto = if (activo) "Activo" else "Inactivo",
                                        tipo = if (activo) TipoEstadoBadge.VERDE else TipoEstadoBadge.NEUTRAL
                                    )

                                    IconButton(
                                        onClick = { cursoParaEditar = curso },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar ${curso.nombre}")
                                    }

                                    IconButton(
                                        onClick = { cursoParaEliminar = curso },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar ${curso.nombre}",
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

    // Modal Crear/Editar Curso
    if (mostrarModalCrear || cursoParaEditar != null) {
        val existente = cursoParaEditar
        var nombre by remember { mutableStateOf(existente?.nombre.orEmpty()) }
        var nivel by remember { mutableStateOf(existente?.nivel ?: uiState.carreras.firstOrNull()?.nombre.orEmpty()) }
        var grado by remember { mutableStateOf(existente?.grado.orEmpty()) }
        var docente by remember { mutableStateOf(existente?.docente.orEmpty()) }
        var activo by remember { mutableStateOf(existente?.activo != false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val gradosDeNivel = remember(nivel, uiState.grados) {
            uiState.grados.filter { it.nivel == nivel }
        }

        AlertDialog(
            onDismissRequest = {
                mostrarModalCrear = false
                cursoParaEditar = null
            },
            title = {
                Text(if (existente != null) "Editar curso" else "Agregar curso")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorMsg != null) {
                        Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre del curso *") },
                        placeholder = { Text("Ej: Matemática aplicada") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    DropdownSelector(
                        etiqueta = "Carrera",
                        opciones = uiState.carreras.map { OpcionDropdown(it.nombre, it.nombre) },
                        seleccion = nivel,
                        onSeleccionar = {
                            nivel = it
                            grado = ""
                        }
                    )

                    DropdownSelector(
                        etiqueta = "Ciclo (opcional)",
                        opciones = listOf(OpcionDropdown("", "Todos los ciclos")) + gradosDeNivel.map {
                            OpcionDropdown(it.nombre, CiclosUtils.cicloCorto(it.nombre, it.nivel))
                        },
                        seleccion = grado,
                        onSeleccionar = { grado = it }
                    )

                    OutlinedTextField(
                        value = docente,
                        onValueChange = { docente = it },
                        label = { Text("Docente (opcional)") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { activo = true },
                            colors = if (activo) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Activo")
                        }
                        OutlinedButton(
                            onClick = { activo = false },
                            colors = if (!activo) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Inactivo")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nom = nombre.trim()
                        if (nom.isBlank()) {
                            errorMsg = "El nombre del curso es obligatorio."
                            return@Button
                        }
                        if (nivel.isBlank()) {
                            errorMsg = "Debes seleccionar una carrera."
                            return@Button
                        }

                        val cursoAGuardar = (existente ?: Curso(
                            nombre = nom,
                            nivel = nivel
                        )).copy(
                            nombre = nom,
                            nivel = nivel,
                            grado = grado.ifBlank { null },
                            docente = docente.trim().ifBlank { null },
                            activo = activo
                        )

                        viewModel.guardarCurso(
                            curso = cursoAGuardar,
                            onExito = {
                                mostrarModalCrear = false
                                cursoParaEditar = null
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Curso guardado")
                                }
                            },
                            onError = { err -> errorMsg = err }
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarModalCrear = false
                        cursoParaEditar = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ConfirmDialog eliminar curso
    cursoParaEliminar?.let { curso ->
        ConfirmDialog(
            titulo = "Eliminar curso",
            mensaje = "¿Eliminar el curso ${curso.nombre}? También se borrará su historial de asistencia.",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = {
                curso.id?.let { id ->
                    viewModel.eliminarCurso(
                        id = id,
                        onExito = {
                            cursoParaEliminar = null
                            ctx.scope.launch {
                                SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Curso eliminado")
                            }
                        },
                        onError = { err ->
                            cursoParaEliminar = null
                            ctx.scope.launch {
                                SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                            }
                        }
                    )
                }
            },
            onCancelar = { cursoParaEliminar = null }
        )
    }
}
