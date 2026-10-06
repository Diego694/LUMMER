package pe.registroacademico.nativo.ui.sistema

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.InstitucionesRepo
import pe.registroacademico.nativo.data.model.InstitucionItem
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class InstitucionesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val instituciones: List<InstitucionItem> = emptyList(),
    val guardando: Boolean = false
)

@HiltViewModel
class InstitucionesViewModel @Inject constructor(
    private val institucionesRepo: InstitucionesRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstitucionesUiState())
    val uiState: StateFlow<InstitucionesUiState> = _uiState.asStateFlow()

    fun cargar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val lista = institucionesRepo.saListar()
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    instituciones = lista
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al listar instituciones"
                )
            }
        }
    }

    fun crear(
        nombre: String,
        codigo: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                institucionesRepo.saCrear(nombre.trim(), codigo?.trim()?.ifBlank { null })
                cargar()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo crear la institución")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun renombrar(
        id: String,
        nuevoNombre: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                institucionesRepo.saRenombrar(id, nuevoNombre.trim())
                cargar()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo renombrar")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun toggleActivo(
        id: String,
        activo: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                institucionesRepo.saActivar(id, activo)
                cargar()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo actualizar el estado")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun asignarAdmin(
        id: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                institucionesRepo.saAsignarAdmin(id, email.trim())
                cargar()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo asignar el administrador")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun entrar(
        id: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                institucionesRepo.saEntrar(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo cambiar de institución")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstitucionesScreen(
    ctx: PantallaCtx,
    viewModel: InstitucionesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.cargar()
    }

    var showCrearModal by remember { mutableStateOf(false) }
    var itemRenombrar by remember { mutableStateOf<InstitucionItem?>(null) }
    var itemAdmin by remember { mutableStateOf<InstitucionItem?>(null) }
    var itemActivar by remember { mutableStateOf<InstitucionItem?>(null) }
    var itemEntrar by remember { mutableStateOf<InstitucionItem?>(null) }

    fun copiarAlPortapapeles(texto: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Código de registro", texto)
        clipboard.setPrimaryClip(clip)
        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Código copiado") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Instituciones",
            subtitulo = "Crear y administrar instituciones del sistema (Superadmin).",
            acciones = {
                Button(
                    onClick = { showCrearModal = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear institución")
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
                    onReintentar = { viewModel.cargar() }
                )
            }

            else -> {
                val items = state.instituciones
                val totalAlumnos = items.sumOf { it.alumnos ?: 0L }
                val totalPersonal = items.sumOf { it.personal ?: 0L }

                // Hero KPIs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        etiqueta = "Institutos",
                        valor = items.size.toString(),
                        icono = Icons.Default.Domain,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        etiqueta = "Alumnos",
                        valor = totalAlumnos.toString(),
                        icono = Icons.Default.School,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        etiqueta = "Personal",
                        valor = totalPersonal.toString(),
                        icono = Icons.Default.Group,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (items.isEmpty()) {
                    EmptyState(
                        titulo = "No hay instituciones registradas",
                        texto = "Crea la primera institución con el botón «Crear institución».",
                        icono = Icons.Default.Domain,
                        accion = {
                            Button(onClick = { showCrearModal = true }) {
                                Text("Crear institución")
                            }
                        }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.forEach { inst ->
                            val esActual = inst.actual == true
                            val estaActiva = inst.activo != false

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (esActual) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (esActual) {
                                                    EstadoBadge(
                                                        texto = "Actual",
                                                        tipo = TipoEstadoBadge.AZUL
                                                    )
                                                }
                                                EstadoBadge(
                                                    texto = if (estaActiva) "Activa" else "Inactiva",
                                                    tipo = if (estaActiva) TipoEstadoBadge.VERDE else TipoEstadoBadge.AMBAR
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = inst.nombre,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (!inst.codigoRegistro.isNullOrBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = inst.codigoRegistro,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = { copiarAlPortapapeles(inst.codigoRegistro) },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copiar código",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = "${inst.alumnos ?: 0} alumnos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${inst.personal ?: 0} personal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!inst.creadoEn.isNullOrBlank()) {
                                            Text(
                                                text = "Creada: ${DateUtils.fmtDate(inst.creadoEn.take(10))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Botones de acción
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!esActual) {
                                            Button(
                                                onClick = { itemEntrar = inst },
                                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                            ) {
                                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Entrar")
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { itemRenombrar = inst },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Renombrar")
                                        }

                                        OutlinedButton(
                                            onClick = { itemAdmin = inst },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Admin")
                                        }

                                        TextButton(
                                            onClick = { itemActivar = inst },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                            colors = if (estaActiva) {
                                                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            } else {
                                                ButtonDefaults.textButtonColors()
                                            }
                                        ) {
                                            Text(if (estaActiva) "Desactivar" else "Activar")
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

    // Modal Crear Institución
    if (showCrearModal) {
        var nombreNuevo by remember { mutableStateOf("") }
        var codigoNuevo by remember { mutableStateOf("") }
        var errorCrear by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCrearModal = false },
            title = { Text("Crear institución") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nombreNuevo,
                        onValueChange = { nombreNuevo = it },
                        label = { Text("Nombre de la institución *") },
                        placeholder = { Text("Entre 3 y 80 caracteres") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = codigoNuevo,
                        onValueChange = { codigoNuevo = it.uppercase().take(20) },
                        label = { Text("Código de registro (opcional)") },
                        placeholder = { Text("Vacío para automático") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (errorCrear != null) {
                        Text(
                            text = errorCrear.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreNuevo.trim().length < 3) {
                            errorCrear = "El nombre debe tener al menos 3 caracteres."
                            return@Button
                        }
                        viewModel.crear(
                            nombre = nombreNuevo,
                            codigo = codigoNuevo.ifBlank { null },
                            onSuccess = {
                                showCrearModal = false
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Institución creada con éxito") }
                            },
                            onError = { err -> errorCrear = err }
                        )
                    },
                    enabled = !state.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (state.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Crear")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCrearModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Renombrar Institución
    itemRenombrar?.let { inst ->
        var nombreEdit by remember { mutableStateOf(inst.nombre) }
        var errorRenombrar by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { itemRenombrar = null },
            title = { Text("Renombrar institución") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nombreEdit,
                        onValueChange = { nombreEdit = it },
                        label = { Text("Nuevo nombre *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (errorRenombrar != null) {
                        Text(
                            text = errorRenombrar.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreEdit.trim().length < 3) {
                            errorRenombrar = "El nombre debe tener al menos 3 caracteres."
                            return@Button
                        }
                        viewModel.renombrar(
                            id = inst.id,
                            nuevoNombre = nombreEdit,
                            onSuccess = {
                                itemRenombrar = null
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Institución renombrada") }
                            },
                            onError = { err -> errorRenombrar = err }
                        )
                    },
                    enabled = !state.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemRenombrar = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Asignar Admin
    itemAdmin?.let { inst ->
        var emailAdmin by remember { mutableStateOf("") }
        var errorAdmin by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { itemAdmin = null },
            title = { Text("Asignar administrador") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Institución: ${inst.nombre}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = emailAdmin,
                        onValueChange = { emailAdmin = it },
                        label = { Text("Correo electrónico del administrador *") },
                        placeholder = { Text("admin@instituto.pe") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (errorAdmin != null) {
                        Text(
                            text = errorAdmin.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!emailAdmin.contains("@")) {
                            errorAdmin = "Ingresa un correo electrónico válido."
                            return@Button
                        }
                        viewModel.asignarAdmin(
                            id = inst.id,
                            email = emailAdmin,
                            onSuccess = {
                                itemAdmin = null
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Administrador asignado") }
                            },
                            onError = { err -> errorAdmin = err }
                        )
                    },
                    enabled = !state.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Asignar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemAdmin = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Confirmar Activar / Desactivar
    itemActivar?.let { inst ->
        val nuevoEstado = !(inst.activo ?: true)
        val accion = if (nuevoEstado) "Activar" else "Desactivar"

        ConfirmDialog(
            titulo = "$accion institución",
            mensaje = "¿Deseas $accion «${inst.nombre}»?",
            textoConfirmar = accion,
            esPeligro = !nuevoEstado,
            onConfirmar = {
                viewModel.toggleActivo(
                    id = inst.id,
                    activo = nuevoEstado,
                    onSuccess = {
                        itemActivar = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Institución $accion".lowercase()) }
                    },
                    onError = { err ->
                        itemActivar = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { itemActivar = null }
        )
    }

    // Modal Confirmar Entrar
    itemEntrar?.let { inst ->
        ConfirmDialog(
            titulo = "Entrar a esta institución",
            mensaje = "Pasarás a administrar «${inst.nombre}». Tu sesión se actualizará con los datos de la nueva institución.",
            textoConfirmar = "Entrar",
            esPeligro = false,
            onConfirmar = {
                viewModel.entrar(
                    id = inst.id,
                    onSuccess = {
                        itemEntrar = null
                        ctx.scope.launch {
                            ctx.snackbarHostState.showSnackbar("Cambiando a «${inst.nombre}»...")
                            ctx.navegar("dashboard")
                        }
                    },
                    onError = { err ->
                        itemEntrar = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { itemEntrar = null }
        )
    }
}
