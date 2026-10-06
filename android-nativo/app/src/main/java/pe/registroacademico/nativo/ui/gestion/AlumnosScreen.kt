package pe.registroacademico.nativo.ui.gestion

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import java.util.UUID
import javax.inject.Inject

data class AlumnosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val alumnos: List<Alumno> = emptyList(),
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val query: String = "",
    val filtroNivel: String = "",
    val filtroGrado: String = "",
    val soloPendientes: Boolean = false,
    val pagina: Int = 1
)

@HiltViewModel
class AlumnosViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val catalogosRepo: CatalogosRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlumnosUiState())
    val uiState: StateFlow<AlumnosUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val alumnos = if (colegioId.isNotBlank()) alumnosRepo.listar(colegioId) else emptyList()
                val niveles = if (colegioId.isNotBlank()) catalogosRepo.listarNiveles(colegioId) else emptyList()
                val grados = if (colegioId.isNotBlank()) catalogosRepo.listarGrados(colegioId) else emptyList()

                _uiState.update {
                    it.copy(
                        cargando = false,
                        alumnos = alumnos,
                        niveles = niveles,
                        grados = grados
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar alumnos"
                    )
                }
            }
        }
    }

    fun setQuery(q: String) {
        _uiState.update { it.copy(query = q, pagina = 1) }
    }

    fun setFiltroNivel(nivel: String) {
        _uiState.update { it.copy(filtroNivel = nivel, filtroGrado = "", pagina = 1) }
    }

    fun setFiltroGrado(grado: String) {
        _uiState.update { it.copy(filtroGrado = grado, pagina = 1) }
    }

    fun setSoloPendientes(solo: Boolean) {
        _uiState.update { it.copy(soloPendientes = solo, pagina = 1) }
    }

    fun setPagina(p: Int) {
        _uiState.update { it.copy(pagina = p) }
    }

    fun guardarAlumno(alumno: Alumno, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val alumnoConColegio = if (alumno.colegioId.isNullOrBlank()) {
                    alumno.copy(colegioId = colegioId)
                } else alumno
                alumnosRepo.guardar(alumnoConColegio)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al guardar alumno")
            }
        }
    }

    fun eliminarAlumno(id: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                alumnosRepo.eliminar(id)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar alumno")
            }
        }
    }

    fun aprobarAlumno(id: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                alumnosRepo.aprobarSolicitud(id, colegioId)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al aprobar solicitud")
            }
        }
    }

    fun rechazarAlumno(id: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                alumnosRepo.rechazarSolicitud(id)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al rechazar solicitud")
            }
        }
    }

    suspend fun obtenerFotoUrl(fotoPath: String): String? {
        return alumnosRepo.fotoUrl(fotoPath)
    }
}

@Composable
fun AlumnosScreen(
    ctx: PantallaCtx,
    viewModel: AlumnosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var alumnoParaEditar by remember { mutableStateOf<Alumno?>(null) }
    var alumnoParaEliminar by remember { mutableStateOf<Alumno?>(null) }
    var alumnoParaRevisar by remember { mutableStateOf<Alumno?>(null) }
    var alumnoParaApoderado by remember { mutableStateOf<Alumno?>(null) }
    var mostrarDialogoNuevo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    val alumnosFiltrados = remember(
        uiState.alumnos,
        uiState.query,
        uiState.filtroNivel,
        uiState.filtroGrado,
        uiState.soloPendientes
    ) {
        val q = StringUtils.norm(uiState.query)
        uiState.alumnos.filter { a ->
            val textoBusqueda = StringUtils.norm("${a.nombre} ${a.codigo} ${a.apoderado} ${a.nivel} ${a.grado}")
            val cumpleQuery = q.isBlank() || textoBusqueda.contains(q)
            val cumpleNivel = uiState.filtroNivel.isBlank() || a.nivel == uiState.filtroNivel
            val cumpleGrado = uiState.filtroGrado.isBlank() || a.grado == uiState.filtroGrado
            val cumplePendiente = !uiState.soloPendientes || a.aprobado == false
            cumpleQuery && cumpleNivel && cumpleGrado && cumplePendiente
        }
    }

    val porPagina = 25
    val totalPaginas = maxOf(1, (alumnosFiltrados.size + porPagina - 1) / porPagina)
    val paginaActual = uiState.pagina.coerceIn(1, totalPaginas)
    val alumnosPaginados = remember(alumnosFiltrados, paginaActual) {
        val desde = (paginaActual - 1) * porPagina
        alumnosFiltrados.drop(desde).take(porPagina)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Alumnos",
            subtitulo = "Padrón de alumnos con su código único de acceso.",
            acciones = {
                OutlinedButton(
                    onClick = { ctx.navegar("codigo") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Código")
                }
                Button(
                    onClick = { mostrarDialogoNuevo = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar")
                }
            }
        )

        // Buscador y filtros
        SearchField(
            query = uiState.query,
            onQueryChange = { viewModel.setQuery(it) },
            placeholder = "Buscar por nombre, código, apoderado…"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val opcionesNivel = listOf(OpcionDropdown("", "Todas las carreras")) +
                uiState.niveles.map { OpcionDropdown(it.nombre, it.nombre) }

            Box(modifier = Modifier.weight(1f)) {
                DropdownSelector(
                    etiqueta = "Carrera",
                    opciones = opcionesNivel,
                    seleccion = uiState.filtroNivel,
                    onSeleccionar = { viewModel.setFiltroNivel(it) }
                )
            }

            val gradosFiltrados = if (uiState.filtroNivel.isBlank()) {
                uiState.grados
            } else {
                uiState.grados.filter { it.nivel == uiState.filtroNivel }
            }
            val opcionesGrado = listOf(OpcionDropdown("", "Todos los ciclos")) +
                gradosFiltrados.map { OpcionDropdown(it.nombre, CiclosUtils.cicloCorto(it.nombre, it.nivel)) }

            Box(modifier = Modifier.weight(1f)) {
                DropdownSelector(
                    etiqueta = "Ciclo",
                    opciones = opcionesGrado,
                    seleccion = uiState.filtroGrado,
                    onSeleccionar = { viewModel.setFiltroGrado(it) }
                )
            }
        }

        // Filtro de pendientes
        val cantPendientes = uiState.alumnos.count { it.aprobado == false }
        if (cantPendientes > 0) {
            OutlinedButton(
                onClick = { viewModel.setSoloPendientes(!uiState.soloPendientes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                colors = if (uiState.soloPendientes) {
                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else ButtonDefaults.outlinedButtonColors()
            ) {
                Text(
                    text = if (uiState.soloPendientes) "Viendo solo pendientes ($cantPendientes)" else "Ver pendientes de aprobación ($cantPendientes)"
                )
            }
        }

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 5)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar alumnos",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            alumnosFiltrados.isEmpty() -> {
                EmptyState(
                    titulo = "No se encontraron alumnos",
                    texto = "Prueba con otro criterio de búsqueda o agrega un nuevo alumno.",
                    icono = Icons.Default.Search
                )
            }
            else -> {
                // Lista de alumnos
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alumnosPaginados.forEach { alumno ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alumno.nombre,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${alumno.codigo} · ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (alumno.apoderado.isNotBlank()) {
                                            Text(
                                                text = "Apoderado: ${alumno.apoderado}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (alumno.aprobado == false) {
                                        EstadoBadge(texto = "Pendiente", tipo = TipoEstadoBadge.AMBAR)
                                    } else {
                                        EstadoBadge(
                                            texto = if (alumno.estado == "ACTIVO") "Activo" else "Inactivo",
                                            tipo = if (alumno.estado == "ACTIVO") TipoEstadoBadge.VERDE else TipoEstadoBadge.NEUTRAL
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (alumno.aprobado == false) {
                                        Button(
                                            onClick = { alumnoParaRevisar = alumno },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = "Revisar", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Revisar")
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    IconButton(
                                        onClick = { alumnoParaApoderado = alumno },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.People, contentDescription = "Código de apoderado de ${alumno.nombre}")
                                    }

                                    IconButton(
                                        onClick = { ctx.navegar("carnet") },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContactPage, contentDescription = "Carnet de ${alumno.nombre}")
                                    }

                                    IconButton(
                                        onClick = { alumnoParaEditar = alumno },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar ${alumno.nombre}")
                                    }

                                    IconButton(
                                        onClick = { alumnoParaEliminar = alumno },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar ${alumno.nombre}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Paginador
                if (totalPaginas > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setPagina(paginaActual - 1) },
                            enabled = paginaActual > 1,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Anterior")
                        }

                        Text(
                            text = "Página $paginaActual de $totalPaginas · ${alumnosFiltrados.size} alumnos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { viewModel.setPagina(paginaActual + 1) },
                            enabled = paginaActual < totalPaginas,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Siguiente")
                        }
                    }
                } else {
                    Text(
                        text = "${alumnosFiltrados.size} alumno(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }

    // Modal Crear/Editar Alumno
    if (mostrarDialogoNuevo || alumnoParaEditar != null) {
        val alumnoExistente = alumnoParaEditar
        val siguienteCodigoSugerido = remember(uiState.alumnos) {
            val maxNum = uiState.alumnos.mapNotNull { it.codigo.filter { c -> c.isDigit() }.toIntOrNull() }.maxOrNull() ?: 1000
            "a${maxNum + 1}"
        }

        var nombre by remember { mutableStateOf(alumnoExistente?.nombre.orEmpty()) }
        var codigo by remember { mutableStateOf(alumnoExistente?.codigo ?: siguienteCodigoSugerido) }
        var nivel by remember { mutableStateOf(alumnoExistente?.nivel ?: uiState.niveles.firstOrNull()?.nombre.orEmpty()) }
        var grado by remember { mutableStateOf(alumnoExistente?.grado.orEmpty()) }
        var apoderado by remember { mutableStateOf(alumnoExistente?.apoderado.orEmpty()) }
        var estado by remember { mutableStateOf(alumnoExistente?.estado ?: "ACTIVO") }
        var errorForm by remember { mutableStateOf<String?>(null) }

        val gradosDeNivel = remember(nivel, uiState.grados) {
            uiState.grados.filter { it.nivel == nivel }
        }

        AlertDialog(
            onDismissRequest = {
                mostrarDialogoNuevo = false
                alumnoParaEditar = null
            },
            title = {
                Text(if (alumnoExistente != null) "Editar alumno" else "Agregar alumno")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorForm != null) {
                        Text(
                            text = errorForm!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre completo *") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { codigo = it },
                        label = { Text("Código único de acceso *") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    DropdownSelector(
                        etiqueta = "Carrera",
                        opciones = uiState.niveles.map { OpcionDropdown(it.nombre, it.nombre) },
                        seleccion = nivel,
                        onSeleccionar = {
                            nivel = it
                            grado = ""
                        }
                    )

                    DropdownSelector(
                        etiqueta = "Ciclo / salón",
                        opciones = listOf(OpcionDropdown("", "—")) + gradosDeNivel.map {
                            OpcionDropdown(it.nombre, CiclosUtils.cicloCorto(it.nombre, it.nivel))
                        },
                        seleccion = grado,
                        onSeleccionar = { grado = it }
                    )

                    OutlinedTextField(
                        value = apoderado,
                        onValueChange = { apoderado = it },
                        label = { Text("Apoderado (opcional)") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { estado = "ACTIVO" },
                            colors = if (estado == "ACTIVO") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Activo")
                        }
                        OutlinedButton(
                            onClick = { estado = "INACTIVO" },
                            colors = if (estado == "INACTIVO") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
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
                        if (nombre.isBlank() || codigo.isBlank()) {
                            errorForm = "Nombre y código son obligatorios."
                            return@Button
                        }
                        val alumnoAGuardar = (alumnoExistente ?: Alumno(
                            id = UUID.randomUUID().toString(),
                            codigo = codigo.trim(),
                            nombre = nombre.trim()
                        )).copy(
                            nombre = nombre.trim(),
                            codigo = codigo.trim(),
                            nivel = nivel,
                            grado = grado,
                            apoderado = apoderado.trim(),
                            estado = estado
                        )

                        viewModel.guardarAlumno(
                            alumno = alumnoAGuardar,
                            onExito = {
                                mostrarDialogoNuevo = false
                                alumnoParaEditar = null
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Alumno guardado correctamente")
                                }
                            },
                            onError = { err ->
                                errorForm = err
                            }
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
                        mostrarDialogoNuevo = false
                        alumnoParaEditar = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ConfirmDialog para eliminar
    alumnoParaEliminar?.let { alumno ->
        ConfirmDialog(
            titulo = "Confirmar eliminación",
            mensaje = "¿Eliminar a ${alumno.nombre}? También se borrará su historial de asistencia. Esta acción no se puede deshacer.",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = {
                val nombre = alumno.nombre
                viewModel.eliminarAlumno(
                    id = alumno.id,
                    onExito = {
                        alumnoParaEliminar = null
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Alumno $nombre eliminado")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { alumnoParaEliminar = null }
        )
    }

    // Dialog para Código de Apoderado
    alumnoParaApoderado?.let { alumno ->
        val cod = alumno.codigoApoderado.orEmpty()
        val bonito = if (cod.length >= 8) cod.replace(Regex("(.{4})(?=.)"), "$1-") else cod
        val textoCompartir = "Código para consultar la asistencia de ${alumno.nombre}: $bonito"

        AlertDialog(
            onDismissRequest = { alumnoParaApoderado = null },
            title = { Text("Código de apoderado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = alumno.nombre, fontWeight = FontWeight.Bold)
                    if (cod.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = bonito,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Text(
                            text = "El apoderado ingresa con este código para ver la asistencia (solo lectura).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Este alumno aún no tiene código de apoderado asignado.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                if (cod.isNotBlank()) {
                    Button(
                        onClick = {
                            val uri = Uri.parse("https://wa.me/?text=${Uri.encode(textoCompartir)}")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("WhatsApp")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (cod.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(bonito))
                            ctx.scope.launch {
                                SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Código copiado")
                            }
                        }
                        alumnoParaApoderado = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (cod.isNotBlank()) "Copiar y cerrar" else "Cerrar")
                }
            }
        )
    }

    // Dialog para Revisar Solicitud de Alumno
    alumnoParaRevisar?.let { alumno ->
        AlertDialog(
            onDismissRequest = { alumnoParaRevisar = null },
            title = { Text("Revisar registro de estudiante") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nombre: ${alumno.nombre}", fontWeight = FontWeight.Bold)
                    Text("Carrera · Ciclo: ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}")
                    if (!alumno.dni.isNullOrBlank()) Text("DNI: ${alumno.dni}")
                    if (alumno.apoderado.isNotBlank()) Text("Apoderado: ${alumno.apoderado}")
                    Text("Código: ${alumno.codigo}", fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Al aprobar, su QR podrá registrar asistencia. Al rechazar, se elimina el registro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.aprobarAlumno(
                            id = alumno.id,
                            onExito = {
                                alumnoParaRevisar = null
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Estudiante aprobado: su QR ya registra asistencia")
                                }
                            },
                            onError = { err ->
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                                }
                            }
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Aprobar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.rechazarAlumno(
                            id = alumno.id,
                            onExito = {
                                alumnoParaRevisar = null
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Registro rechazado")
                                }
                            },
                            onError = { err ->
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                                }
                            }
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Rechazar")
                }
            }
        )
    }
}
