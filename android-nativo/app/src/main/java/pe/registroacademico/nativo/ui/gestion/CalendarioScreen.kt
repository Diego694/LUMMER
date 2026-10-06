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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import pe.registroacademico.nativo.data.model.DiaCalendario
import pe.registroacademico.nativo.data.model.Horario
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CalendarioUtils
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

data class CalendarioUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val horarios: List<Horario> = emptyList(),
    val diasCalendario: List<DiaCalendario> = emptyList(),
    val carreras: List<Nivel> = emptyList(),
    val guardando: Boolean = false
)

@HiltViewModel
class CalendarioViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarioUiState())
    val uiState: StateFlow<CalendarioUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val horarios = catalogosRepo.listarHorarios(colegioId)
                val dias = catalogosRepo.listarDiasCalendario(colegioId)
                val niveles = catalogosRepo.listarNiveles(colegioId)

                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    horarios = horarios,
                    diasCalendario = dias.sortedBy { it.fecha },
                    carreras = niveles
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar calendario y horarios"
                )
            }
        }
    }

    fun guardarHorario(
        horario: Horario,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.guardarHorario(horario)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar el horario")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun eliminarHorario(
        id: String,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.eliminarHorario(id)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo eliminar el horario")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun guardarDia(
        dia: DiaCalendario,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.guardarDiaCalendario(dia)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar la fecha")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun eliminarDia(
        id: String,
        colegioId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                catalogosRepo.eliminarDiaCalendario(id)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo eliminar la fecha")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun cargarFeriadosPeru(
        colegioId: String,
        anio: Int,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                val existentes = _uiState.value.diasCalendario.map { it.fecha }.toSet()
                val feriados = CalendarioUtils.feriadosPeru(anio).filter { it.fecha !in existentes }

                for (f in feriados) {
                    catalogosRepo.guardarDiaCalendario(
                        DiaCalendario(
                            colegioId = colegioId,
                            fecha = f.fecha,
                            tipo = "Feriado",
                            nombre = f.nombre
                        )
                    )
                }
                cargar(colegioId)
                onSuccess(feriados.size)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudieron cargar los feriados")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }
}

@Composable
fun CalendarioScreen(
    ctx: PantallaCtx,
    viewModel: CalendarioViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()
    val hoy = remember { DateUtils.todayStr() }

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    // Modal state for Horario
    var horarioFormTargetNivel by remember { mutableStateOf<String?>(null) }
    var showHorarioModal by remember { mutableStateOf(false) }
    var presetTardeModal by remember { mutableStateOf(false) }

    // Modal state for Dia Calendario
    var showDiaModal by remember { mutableStateOf(false) }

    // Confirm dialogs
    var confirmEliminarHorarioId by remember { mutableStateOf<String?>(null) }
    var confirmEliminarDia by remember { mutableStateOf<DiaCalendario?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Calendario y horarios",
            subtitulo = "Los feriados no cuentan como falta; los horarios definen quién llega tarde (por carrera)."
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

            else -> {
                // SECCIÓN HORARIOS
                SectionCard(
                    titulo = "Horarios de ingreso y salida",
                    acciones = {
                        OutlinedButton(
                            onClick = {
                                horarioFormTargetNivel = null
                                presetTardeModal = true
                                showHorarioModal = true
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Usar horario tarde/noche (14:00–20:00)")
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Es puntual quien ingresa hasta inicio de clases + tolerancia; después es tardanza hasta el cierre del ingreso. La salida solo se habilita pasados los minutos mínimos desde el ingreso (120 = 2 horas). Una carrera sin horario propio usa el general.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Horario General
                        val generalH = state.horarios.find { it.nivel.isNullOrBlank() }
                        val efectivoGeneral = CalendarioUtils.horarioDe(state.horarios, null)
                        FilaHorario(
                            etiqueta = "General (todo el instituto)",
                            horario = generalH,
                            limite = efectivoGeneral.limite,
                            hasta = efectivoGeneral.hasta,
                            desde = efectivoGeneral.desde,
                            permanencia = efectivoGeneral.permanencia,
                            salida = efectivoGeneral.salida,
                            esGeneral = true,
                            onEditar = {
                                horarioFormTargetNivel = null
                                presetTardeModal = false
                                showHorarioModal = true
                            },
                            onEliminar = null
                        )

                        // Horarios por Carrera
                        state.carreras.forEach { carrera ->
                            val carreraH = state.horarios.find { it.nivel == carrera.nombre }
                            val efectivoCarrera = CalendarioUtils.horarioDe(state.horarios, carrera.nombre)
                            FilaHorario(
                                etiqueta = carrera.nombre,
                                horario = carreraH,
                                limite = efectivoCarrera.limite,
                                hasta = efectivoCarrera.hasta,
                                desde = efectivoCarrera.desde,
                                permanencia = efectivoCarrera.permanencia,
                                salida = efectivoCarrera.salida,
                                esGeneral = false,
                                onEditar = {
                                    horarioFormTargetNivel = carrera.nombre
                                    presetTardeModal = false
                                    showHorarioModal = true
                                },
                                onEliminar = carreraH?.id?.let { hid ->
                                    { confirmEliminarHorarioId = hid }
                                }
                            )
                        }
                    }
                }

                // SECCIÓN CALENDARIO
                val proximos = state.diasCalendario.filter { it.fecha >= hoy }
                val hintProximo = proximos.firstOrNull()?.let {
                    "Próximo: ${DateUtils.fmtDate(it.fecha)} — ${it.nombre}"
                }

                SectionCard(
                    titulo = "Feriados y días sin clases",
                    acciones = {
                        OutlinedButton(
                            onClick = {
                                val anioActual = hoy.take(4).toIntOrNull() ?: 2026
                                viewModel.cargarFeriadosPeru(
                                    colegioId = cid,
                                    anio = anioActual,
                                    onSuccess = { count ->
                                        ctx.scope.launch {
                                            ctx.snackbarHostState.showSnackbar(
                                                if (count > 0) "$count feriados cargados para $anioActual"
                                                else "Los feriados de $anioActual ya estaban cargados"
                                            )
                                        }
                                    },
                                    onError = { err ->
                                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                                    }
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Cargar feriados Perú")
                        }

                        Button(
                            onClick = { showDiaModal = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Agregar fecha")
                        }
                    }
                ) {
                    if (hintProximo != null) {
                        Text(
                            text = hintProximo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (state.diasCalendario.isEmpty()) {
                        EmptyState(
                            titulo = "Sin fechas cargadas",
                            texto = "Carga los feriados nacionales con un clic o agrega los días sin clases de tu instituto.",
                            icono = Icons.Default.CalendarMonth
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.diasCalendario.forEach { dia ->
                                val esPasado = dia.fecha < hoy
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (esPasado) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
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
                                                    text = DateUtils.fmtDate(dia.fecha),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val badgeTipo = when (dia.tipo) {
                                                    "Feriado" -> TipoEstadoBadge.ROJO
                                                    "Sin clases" -> TipoEstadoBadge.AMBAR
                                                    else -> TipoEstadoBadge.AZUL
                                                }
                                                EstadoBadge(texto = dia.tipo, tipo = badgeTipo)
                                            }
                                            Text(
                                                text = dia.nombre,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        IconButton(
                                            onClick = { confirmEliminarDia = dia },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Quitar ${dia.nombre}",
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

    // Modal Formulario Horario
    if (showHorarioModal) {
        val targetNivel = horarioFormTargetNivel
        val existingH = state.horarios.find { (it.nivel ?: "") == (targetNivel ?: "") }

        var hIngreso by remember {
            mutableStateOf(if (presetTardeModal) "14:00" else (existingH?.horaIngreso ?: "08:00"))
        }
        var tolMin by remember {
            mutableStateOf(if (presetTardeModal) "10" else (existingH?.toleranciaMin?.toString() ?: "0"))
        }
        var hDesde by remember {
            mutableStateOf(if (presetTardeModal) "13:00" else (existingH?.ingresoDesde ?: ""))
        }
        var hHasta by remember {
            mutableStateOf(if (presetTardeModal) "19:00" else (existingH?.ingresoHasta ?: ""))
        }
        var hSalida by remember {
            mutableStateOf(if (presetTardeModal) "20:00" else (existingH?.horaSalida ?: ""))
        }
        var permMin by remember {
            mutableStateOf(if (presetTardeModal) "120" else (existingH?.permanenciaMin?.toString() ?: "120"))
        }
        var errorH by remember { mutableStateOf<String?>(null) }

        val tituloModal = if (targetNivel.isNullOrBlank()) "Horario general del instituto"
        else "Horario de $targetNivel"

        AlertDialog(
            onDismissRequest = { showHorarioModal = false },
            title = { Text(tituloModal) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hIngreso,
                        onValueChange = { hIngreso = it },
                        label = { Text("Inicio de clases (HH:MM 24h) *") },
                        placeholder = { Text("08:00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tolMin,
                        onValueChange = { tolMin = it },
                        label = { Text("Tolerancia (min) puntual") },
                        placeholder = { Text("10") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hDesde,
                        onValueChange = { hDesde = it },
                        label = { Text("Marcar ingreso desde (opcional)") },
                        placeholder = { Text("07:30") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hHasta,
                        onValueChange = { hHasta = it },
                        label = { Text("Cierre del ingreso (opcional)") },
                        placeholder = { Text("12:00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hSalida,
                        onValueChange = { hSalida = it },
                        label = { Text("Fin de clases — salida (opcional)") },
                        placeholder = { Text("13:30") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = permMin,
                        onValueChange = { permMin = it },
                        label = { Text("Minutos mínimos para marcar SALIDA") },
                        placeholder = { Text("120") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (errorH != null) {
                        Text(
                            text = errorH.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hhmmRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
                        val ing = hIngreso.trim()
                        if (!hhmmRegex.matches(ing)) {
                            errorH = "La hora de ingreso debe ser HH:MM (ej: 08:00)."
                            return@Button
                        }
                        val des = hDesde.trim().ifBlank { null }
                        val has = hHasta.trim().ifBlank { null }
                        val sal = hSalida.trim().ifBlank { null }

                        if (des != null && !hhmmRegex.matches(des)) {
                            errorH = "La apertura del ingreso debe ser HH:MM."
                            return@Button
                        }
                        if (has != null && !hhmmRegex.matches(has)) {
                            errorH = "El cierre del ingreso debe ser HH:MM."
                            return@Button
                        }
                        if (sal != null && !hhmmRegex.matches(sal)) {
                            errorH = "La hora de salida debe ser HH:MM."
                            return@Button
                        }

                        val tol = tolMin.toIntOrNull() ?: 0
                        val perm = permMin.toIntOrNull() ?: 120

                        val horarioObj = Horario(
                            id = existingH?.id,
                            colegioId = cid,
                            nivel = targetNivel,
                            horaIngreso = ing,
                            toleranciaMin = tol,
                            ingresoDesde = des,
                            ingresoHasta = has,
                            horaSalida = sal,
                            permanenciaMin = perm
                        )

                        viewModel.guardarHorario(
                            horario = horarioObj,
                            colegioId = cid,
                            onSuccess = {
                                showHorarioModal = false
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Horario guardado") }
                            },
                            onError = { errorH = it }
                        )
                    },
                    enabled = !state.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (state.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Guardar horario")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showHorarioModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Formulario Agregar Fecha
    if (showDiaModal) {
        var fechaInput by remember { mutableStateOf(DateUtils.todayStr()) }
        var tipoInput by remember { mutableStateOf("Feriado") }
        var nombreInput by remember { mutableStateOf("") }
        var errorDia by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDiaModal = false },
            title = { Text("Agregar fecha") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = fechaInput,
                        onValueChange = { fechaInput = it },
                        label = { Text("Fecha (YYYY-MM-DD) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Tipo Selector simple
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Feriado", "Sin clases", "Evento").forEach { t ->
                            val sel = tipoInput == t
                            if (sel) {
                                Button(
                                    onClick = { tipoInput = t },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Text(t, style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { tipoInput = t },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Text(t, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = nombreInput,
                        onValueChange = { nombreInput = it },
                        label = { Text("Nombre / Motivo *") },
                        placeholder = { Text("Ej: Aniversario del instituto") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorDia != null) {
                        Text(
                            text = errorDia.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fechaInput.isBlank() || nombreInput.isBlank()) {
                            errorDia = "Completa la fecha y el nombre."
                            return@Button
                        }
                        val diaObj = DiaCalendario(
                            colegioId = cid,
                            fecha = fechaInput.trim(),
                            tipo = tipoInput,
                            nombre = nombreInput.trim()
                        )
                        viewModel.guardarDia(
                            dia = diaObj,
                            colegioId = cid,
                            onSuccess = {
                                showDiaModal = false
                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Fecha agregada") }
                            },
                            onError = { errorDia = it }
                        )
                    },
                    enabled = !state.guardando,
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
                    onClick = { showDiaModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogos de confirmación
    confirmEliminarHorarioId?.let { hid ->
        ConfirmDialog(
            titulo = "Quitar horario",
            mensaje = "Esta carrera volverá a usar el horario general.",
            textoConfirmar = "Quitar",
            esPeligro = true,
            onConfirmar = {
                viewModel.eliminarHorario(
                    id = hid,
                    colegioId = cid,
                    onSuccess = {
                        confirmEliminarHorarioId = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Horario restablecido al general") }
                    },
                    onError = { err ->
                        confirmEliminarHorarioId = null
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { confirmEliminarHorarioId = null }
        )
    }

    confirmEliminarDia?.let { dia ->
        ConfirmDialog(
            titulo = "Quitar fecha",
            mensaje = "¿Deseas eliminar «${dia.nombre}» (${DateUtils.fmtDate(dia.fecha)})?",
            textoConfirmar = "Eliminar",
            esPeligro = true,
            onConfirmar = {
                dia.id?.let { did ->
                    viewModel.eliminarDia(
                        id = did,
                        colegioId = cid,
                        onSuccess = {
                            confirmEliminarDia = null
                            ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Fecha eliminada") }
                        },
                        onError = { err ->
                            confirmEliminarDia = null
                            ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                        }
                    )
                }
            },
            onCancelar = { confirmEliminarDia = null }
        )
    }
}

@Composable
private fun FilaHorario(
    etiqueta: String,
    horario: Horario?,
    limite: String,
    hasta: String?,
    desde: String?,
    permanencia: Int,
    salida: String?,
    esGeneral: Boolean,
    onEditar: () -> Unit,
    onEliminar: (() -> Unit)?
) {
    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        if (isCompact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = etiqueta,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (horario != null) {
                        Text(
                            text = "Inicio: ${horario.horaIngreso} · Puntual hasta $limite · Tardanza ${if (hasta != null) "hasta $hasta" else "sin tope"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${if (desde != null) "Ingreso desde $desde · " else ""}Salida desde ${permanencia / 60}h después del ingreso${if (salida != null) " · fin $salida" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = if (esGeneral) "Sin definir: se usa 08:00 por defecto" else "Usa el horario general",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEditar,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(if (horario != null) "Cambiar" else "Definir")
                    }

                    if (onEliminar != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onEliminar,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Quitar horario de $etiqueta",
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
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = etiqueta,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (horario != null) {
                        Text(
                            text = "Inicio: ${horario.horaIngreso} · Puntual hasta $limite · Tardanza ${if (hasta != null) "hasta $hasta" else "sin tope"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${if (desde != null) "Ingreso desde $desde · " else ""}Salida desde ${permanencia / 60}h después del ingreso${if (salida != null) " · fin $salida" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = if (esGeneral) "Sin definir: se usa 08:00 por defecto" else "Usa el horario general",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEditar,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(if (horario != null) "Cambiar" else "Definir")
                    }

                    if (onEliminar != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onEliminar,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Quitar horario de $etiqueta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
