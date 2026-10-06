package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class GradosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val carreras: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val alumnos: List<Alumno> = emptyList()
)

@HiltViewModel
class GradosViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val alumnosRepo: AlumnosRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradosUiState())
    val uiState: StateFlow<GradosUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val carreras = if (colegioId.isNotBlank()) catalogosRepo.listarNiveles(colegioId) else emptyList()
                val grados = if (colegioId.isNotBlank()) catalogosRepo.listarGrados(colegioId) else emptyList()
                val alumnos = if (colegioId.isNotBlank()) alumnosRepo.listar(colegioId) else emptyList()

                _uiState.update {
                    it.copy(
                        cargando = false,
                        carreras = carreras,
                        grados = grados,
                        alumnos = alumnos
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar ciclos"
                    )
                }
            }
        }
    }

    fun crearCiclosMasivo(
        carrera: String,
        ciclos: List<String>,
        secciones: List<String>,
        onExito: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val c = carrera.trim().replace(Regex("\\s+"), " ").uppercase()
            if (c.isBlank()) {
                onError("Debes elegir o ingresar una carrera.")
                return@launch
            }
            if (ciclos.isEmpty()) {
                onError("Debes seleccionar al menos un ciclo.")
                return@launch
            }

            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val carreraExiste = _uiState.value.carreras.find { StringUtils.norm(it.nombre) == StringUtils.norm(c) }
                val nombreCarrera = carreraExiste?.nombre ?: c

                if (carreraExiste == null) {
                    catalogosRepo.guardarNivel(Nivel(colegioId = colegioId, nombre = nombreCarrera))
                }

                val gradosExistentes = _uiState.value.grados
                    .filter { StringUtils.norm(it.nivel) == StringUtils.norm(nombreCarrera) }
                    .map { StringUtils.norm(it.nombre) }
                    .toSet()

                var creados = 0
                for (ciclo in ciclos) {
                    if (secciones.isEmpty()) {
                        val nom = CiclosUtils.nombreCiclo(nombreCarrera, ciclo, "")
                        if (StringUtils.norm(nom) !in gradosExistentes) {
                            catalogosRepo.guardarGrado(Grado(colegioId = colegioId, nivel = nombreCarrera, nombre = nom))
                            creados++
                        }
                    } else {
                        for (sec in secciones) {
                            val nom = CiclosUtils.nombreCiclo(nombreCarrera, ciclo, sec)
                            if (StringUtils.norm(nom) !in gradosExistentes) {
                                catalogosRepo.guardarGrado(Grado(colegioId = colegioId, nivel = nombreCarrera, nombre = nom))
                                creados++
                            }
                        }
                    }
                }

                cargarDatos()
                onExito(creados)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al crear ciclos")
            }
        }
    }

    fun eliminarGrado(grado: Grado, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val id = grado.id ?: return@launch
            val cantAlumnos = _uiState.value.alumnos.count { it.nivel == grado.nivel && it.grado == grado.nombre }
            if (cantAlumnos > 0) {
                onError("No se puede eliminar \"${grado.nombre}\": tiene $cantAlumnos alumno(s) asociados.")
                return@launch
            }
            try {
                catalogosRepo.eliminarGrado(id)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar el ciclo")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradosScreen(
    ctx: PantallaCtx,
    viewModel: GradosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var mostrarModalCrear by remember { mutableStateOf(false) }
    var carreraPreseleccionada by remember { mutableStateOf("") }
    var gradoParaEliminar by remember { mutableStateOf<Grado?>(null) }

    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    val listaCarrerasNombres = remember(uiState.carreras, uiState.grados) {
        (uiState.carreras.map { it.nombre } + uiState.grados.map { it.nivel }).distinct().sorted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Ciclos y salones",
            subtitulo = "Cada carrera tiene sus ciclos (del I al VI) y, si hace falta, sus salones.",
            acciones = {
                Button(
                    onClick = {
                        carreraPreseleccionada = ""
                        mostrarModalCrear = true
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear ciclos")
                }
            }
        )

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 4)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar ciclos",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            listaCarrerasNombres.isEmpty() -> {
                EmptyState(
                    titulo = "Sin carreras ni ciclos",
                    texto = "Usa «Crear ciclos» para empezar: elige la carrera y marca los ciclos del I al VI.",
                    icono = Icons.Default.MenuBook,
                    accion = {
                        Button(
                            onClick = { mostrarModalCrear = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Crear ciclos")
                        }
                    }
                )
            }
            else -> {
                listaCarrerasNombres.forEach { carrera ->
                    val gradosDeCarrera = remember(uiState.grados, carrera) {
                        uiState.grados
                            .filter { it.nivel == carrera }
                            .sortedWith { a, b -> CiclosUtils.compararCiclos(a.nombre, b.nombre) }
                    }
                    val cantAlumnosCarrera = remember(uiState.alumnos, carrera) {
                        uiState.alumnos.count { it.nivel == carrera }
                    }

                    SectionCard(
                        titulo = carrera,
                        acciones = {
                            OutlinedButton(
                                onClick = {
                                    carreraPreseleccionada = carrera
                                    mostrarModalCrear = true
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Agregar")
                            }
                        }
                    ) {
                        Text(
                            text = "${gradosDeCarrera.size} ciclo(s)/salón(es) · $cantAlumnosCarrera alumno(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (gradosDeCarrera.isEmpty()) {
                            Text(
                                text = "Aún no tiene ciclos creados.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                gradosDeCarrera.forEach { grado ->
                                    val parsed = CiclosUtils.parsearCiclo(grado.nombre)
                                    val cantAlumnosGrado = uiState.alumnos.count { it.nivel == grado.nivel && it.grado == grado.nombre }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (!parsed.ciclo.isNullOrBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "${parsed.ciclo} CICLO",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = grado.nombre,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }

                                                if (parsed.seccion.isNotBlank()) {
                                                    Text(
                                                        text = "Sec. ${parsed.seccion}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "$cantAlumnosGrado alumnos",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                IconButton(
                                                    onClick = { gradoParaEliminar = grado },
                                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Eliminar ${grado.nombre}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
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
            }
        }
    }

    // Modal Crear Ciclos
    if (mostrarModalCrear) {
        val hayCarreras = uiState.carreras.isNotEmpty()
        var carreraSeleccionada by remember { mutableStateOf(carreraPreseleccionada.ifBlank { uiState.carreras.firstOrNull()?.nombre.orEmpty() }) }
        var nuevaCarreraTexto by remember { mutableStateOf("") }
        var esNuevaCarrera by remember { mutableStateOf(!hayCarreras) }
        val ciclosSeleccionados = remember { mutableStateListOf<String>() }
        val seccionesSeleccionadas = remember { mutableStateListOf<String>() }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val todosLosCiclos = CiclosUtils.CICLOS
        val seccionesOpciones = listOf("A", "B", "C", "D", "E")

        AlertDialog(
            onDismissRequest = { mostrarModalCrear = false },
            title = { Text("Crear ciclos y salones") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorMsg != null) {
                        Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (hayCarreras) {
                        val opcionesCarreras = uiState.carreras.map { OpcionDropdown(it.nombre, it.nombre) } +
                            listOf(OpcionDropdown("__nueva__", "➕ Nueva carrera…"))
                        DropdownSelector(
                            etiqueta = "Carrera",
                            opciones = opcionesCarreras,
                            seleccion = if (esNuevaCarrera) "__nueva__" else carreraSeleccionada,
                            onSeleccionar = {
                                if (it == "__nueva__") {
                                    esNuevaCarrera = true
                                } else {
                                    esNuevaCarrera = false
                                    carreraSeleccionada = it
                                }
                            }
                        )
                    }

                    if (esNuevaCarrera || !hayCarreras) {
                        OutlinedTextField(
                            value = nuevaCarreraTexto,
                            onValueChange = { nuevaCarreraTexto = it },
                            label = { Text("Nombre de la nueva carrera *") },
                            placeholder = { Text("Ej: APSTI") },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                            singleLine = true
                        )
                    }

                    Text("Ciclos *", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        todosLosCiclos.forEach { c ->
                            val seleccionado = c in ciclosSeleccionados
                            FilterChip(
                                selected = seleccionado,
                                onClick = {
                                    if (seleccionado) ciclosSeleccionados.remove(c) else ciclosSeleccionados.add(c)
                                },
                                label = { Text(c) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                ciclosSeleccionados.clear()
                                ciclosSeleccionados.addAll(todosLosCiclos)
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text("Todos (I–VI)")
                        }
                        TextButton(
                            onClick = {
                                ciclosSeleccionados.clear()
                                ciclosSeleccionados.addAll(listOf("I", "III", "V"))
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text("Impares")
                        }
                        TextButton(
                            onClick = {
                                ciclosSeleccionados.clear()
                                ciclosSeleccionados.addAll(listOf("II", "IV", "VI"))
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text("Pares")
                        }
                    }

                    Text("Salones / secciones (opcional)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        seccionesOpciones.forEach { s ->
                            val seleccionado = s in seccionesSeleccionadas
                            FilterChip(
                                selected = seleccionado,
                                onClick = {
                                    if (seleccionado) seccionesSeleccionadas.remove(s) else seccionesSeleccionadas.add(s)
                                },
                                label = { Text(s) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            )
                        }
                    }
                    Text(
                        text = "Déjalo vacío si cada ciclo tiene un solo salón.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = if (esNuevaCarrera) nuevaCarreraTexto else carreraSeleccionada
                        if (c.isBlank()) {
                            errorMsg = "Especifica el nombre de la carrera."
                            return@Button
                        }
                        if (ciclosSeleccionados.isEmpty()) {
                            errorMsg = "Selecciona al menos un ciclo."
                            return@Button
                        }

                        viewModel.crearCiclosMasivo(
                            carrera = c,
                            ciclos = ciclosSeleccionados.toList(),
                            secciones = seccionesSeleccionadas.toList(),
                            onExito = { creados ->
                                mostrarModalCrear = false
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarExito(ctx.snackbarHostState, "$creados ciclo(s) creados")
                                }
                            },
                            onError = { err -> errorMsg = err }
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { mostrarModalCrear = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ConfirmDialog eliminar grado
    gradoParaEliminar?.let { grado ->
        ConfirmDialog(
            titulo = "Confirmar eliminación",
            mensaje = "¿Eliminar el ciclo ${grado.nombre}?",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = {
                viewModel.eliminarGrado(
                    grado = grado,
                    onExito = {
                        gradoParaEliminar = null
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Ciclo eliminado")
                        }
                    },
                    onError = { err ->
                        gradoParaEliminar = null
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { gradoParaEliminar = null }
        )
    }
}
