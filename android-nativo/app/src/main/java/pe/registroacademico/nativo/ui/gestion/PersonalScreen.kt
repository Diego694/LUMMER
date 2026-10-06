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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import pe.registroacademico.nativo.data.PersonalRepo
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.data.model.PersonalItem
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class PersonalUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val lista: List<PersonalItem> = emptyList(),
    val carreras: List<Nivel> = emptyList()
)

@HiltViewModel
class PersonalViewModel @Inject constructor(
    private val personalRepo: PersonalRepo,
    private val catalogosRepo: CatalogosRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalUiState())
    val uiState: StateFlow<PersonalUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val personal = personalRepo.personalListar()
                val carreras = if (colegioId.isNotBlank()) catalogosRepo.listarNiveles(colegioId) else emptyList()
                _uiState.update {
                    it.copy(
                        cargando = false,
                        lista = personal,
                        carreras = carreras
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar el personal"
                    )
                }
            }
        }
    }

    fun asignarAcceso(
        email: String,
        rol: String,
        carrera: String?,
        nombre: String?,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                personalRepo.personalAsignar(email, rol, carrera, nombre)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al guardar el acceso del personal")
            }
        }
    }

    fun quitarAcceso(id: String, onExito: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                personalRepo.personalQuitar(id)
                cargarDatos()
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al quitar acceso")
            }
        }
    }
}

@Composable
fun PersonalScreen(
    ctx: PantallaCtx,
    viewModel: PersonalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var mostrarDialogoCrear by remember { mutableStateOf(false) }
    var itemParaEditar by remember { mutableStateOf<PersonalItem?>(null) }
    var itemParaQuitar by remember { mutableStateOf<PersonalItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    val rolesOpciones = listOf(
        OpcionDropdown("Docente", "Docente — asistencia, código y comunicados"),
        OpcionDropdown("Coordinador", "Coordinador — solo su carrera"),
        OpcionDropdown("Administrador", "Administrador — acceso total")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Personal y accesos",
            subtitulo = "Crea las cuentas de tus docentes y define qué puede hacer cada uno.",
            acciones = {
                OutlinedButton(
                    onClick = { mostrarDialogoCrear = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dar acceso")
                }
                Button(
                    onClick = { mostrarDialogoCrear = true },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear usuario")
                }
            }
        )

        SectionCard(titulo = "Permisos por rol") {
            Text(
                text = "• Administrador: acceso total (carreras, ciclos, alumnos, cursos, respaldo).\n" +
                    "• Docente: ver asistencias, registrar asistencia o tardanza, justificar, avisar a apoderados, publicar comunicados y compartir el código de registro.\n" +
                    "• Coordinador: mismas funciones de docente pero filtradas solo a los alumnos de su carrera asignada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 4)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar personal",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            uiState.lista.isEmpty() -> {
                EmptyState(
                    titulo = "Sin personal",
                    texto = "Crea la primera cuenta con «Crear usuario» o asigna acceso a una cuenta existente.",
                    icono = Icons.Default.Group
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isCompact = LocalConfiguration.current.screenWidthDp < 600
                    uiState.lista.forEach { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val rol = p.rol.orEmpty()
                            val badgeTipo = when {
                                rol.contains("admin", ignoreCase = true) -> TipoEstadoBadge.AZUL
                                rol.contains("coord", ignoreCase = true) -> TipoEstadoBadge.AMBAR
                                else -> TipoEstadoBadge.VERDE
                            }

                            if (isCompact) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = p.nombre ?: p.email ?: "Sin nombre",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!p.email.isNullOrBlank()) {
                                            Text(
                                                text = p.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!p.carrera.isNullOrBlank()) {
                                            Text(
                                                text = "Carrera: ${p.carrera}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        EstadoBadge(texto = rol.ifBlank { "Docente" }, tipo = badgeTipo)

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            IconButton(
                                                onClick = { itemParaEditar = p },
                                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar acceso de ${p.email}")
                                            }

                                            IconButton(
                                                onClick = { itemParaQuitar = p },
                                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Quitar acceso a ${p.email}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p.nombre ?: p.email ?: "Sin nombre",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!p.email.isNullOrBlank()) {
                                            Text(
                                                text = p.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!p.carrera.isNullOrBlank()) {
                                            Text(
                                                text = "Carrera: ${p.carrera}",
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
                                        EstadoBadge(texto = rol.ifBlank { "Docente" }, tipo = badgeTipo)

                                        IconButton(
                                            onClick = { itemParaEditar = p },
                                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar acceso de ${p.email}")
                                        }

                                        IconButton(
                                            onClick = { itemParaQuitar = p },
                                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Quitar acceso a ${p.email}",
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
    }

    // Modal Crear o Asignar
    if (mostrarDialogoCrear || itemParaEditar != null) {
        val existente = itemParaEditar
        var email by remember { mutableStateOf(existente?.email.orEmpty()) }
        var nombre by remember { mutableStateOf(existente?.nombre.orEmpty()) }
        var rol by remember { mutableStateOf(existente?.rol ?: "Docente") }
        var carrera by remember { mutableStateOf(existente?.carrera.orEmpty()) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val opcionesCarrera = listOf(OpcionDropdown("", "—")) +
            uiState.carreras.map { OpcionDropdown(it.nombre, it.nombre) }

        AlertDialog(
            onDismissRequest = {
                mostrarDialogoCrear = false
                itemParaEditar = null
            },
            title = {
                Text(if (existente != null) "Editar rol y acceso" else "Crear o asignar usuario")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico *") },
                        enabled = existente == null,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre completo (opcional)") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    DropdownSelector(
                        etiqueta = "Rol",
                        opciones = rolesOpciones,
                        seleccion = rol,
                        onSeleccionar = { rol = it }
                    )

                    if (rol.contains("coord", ignoreCase = true)) {
                        DropdownSelector(
                            etiqueta = "Carrera (requerida para coordinador)",
                            opciones = opcionesCarrera,
                            seleccion = carrera,
                            onSeleccionar = { carrera = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val e = email.trim().lowercase()
                        if (e.isBlank() || !e.contains("@")) {
                            errorMsg = "Ingresa un correo electrónico válido."
                            return@Button
                        }
                        if (rol.contains("coord", ignoreCase = true) && carrera.isBlank()) {
                            errorMsg = "El coordinador necesita tener una carrera asignada."
                            return@Button
                        }

                        viewModel.asignarAcceso(
                            email = e,
                            rol = rol,
                            carrera = if (rol.contains("coord", ignoreCase = true)) carrera else null,
                            nombre = nombre.trim().ifBlank { null },
                            onExito = {
                                mostrarDialogoCrear = false
                                itemParaEditar = null
                                ctx.scope.launch {
                                    SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Acceso guardado correctamente")
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
                        mostrarDialogoCrear = false
                        itemParaEditar = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ConfirmDialog Quitar Acceso
    itemParaQuitar?.let { item ->
        ConfirmDialog(
            titulo = "Quitar acceso",
            mensaje = "¿Quitar el acceso a ${item.email ?: item.nombre}? Deja de poder entrar al sistema.",
            esPeligro = true,
            textoConfirmar = "Quitar acceso",
            onConfirmar = {
                viewModel.quitarAcceso(
                    id = item.id,
                    onExito = {
                        itemParaQuitar = null
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Acceso quitado")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { itemParaQuitar = null }
        )
    }
}
