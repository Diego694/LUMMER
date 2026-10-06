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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.data.model.Periodo
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class PlanMover(
    val alumno: Alumno,
    val de: String,
    val a: String,
    val carrera: String,
    val ciclo: String
)

data class GradoNuevo(
    val nivel: String,
    val nombre: String
)

data class PlanPromocion(
    val mover: List<PlanMover>,
    val egresan: List<Alumno>,
    val sinCiclo: List<Alumno>,
    val gradosNuevos: List<GradoNuevo>
)

data class PeriodosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val periodoActivo: Periodo? = null,
    val periodosPasados: List<Periodo> = emptyList(),
    val carreras: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val alumnos: List<Alumno> = emptyList(),
    val guardando: Boolean = false
)

@HiltViewModel
class PeriodosViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val alumnosRepo: AlumnosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeriodosUiState())
    val uiState: StateFlow<PeriodosUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val periodos = catalogosRepo.listarPeriodos(colegioId)
                val activo = periodos.find { it.activo == true }
                val pasados = periodos.filter { it.activo != true }.sortedByDescending { it.inicio }
                val niveles = catalogosRepo.listarNiveles(colegioId)
                val grados = catalogosRepo.listarGrados(colegioId)
                val alumnos = alumnosRepo.listar(colegioId)

                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    periodoActivo = activo,
                    periodosPasados = pasados,
                    carreras = niveles,
                    grados = grados,
                    alumnos = alumnos
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar periodos"
                )
            }
        }
    }

    fun calcularPlan(carreraFiltro: String?): PlanPromocion {
        val alumnos = _uiState.value.alumnos
        val grados = _uiState.value.grados
        val existentes = grados.map { "${it.nivel}|${it.nombre}" }.toSet()

        val mover = mutableListOf<PlanMover>()
        val egresan = mutableListOf<Alumno>()
        val sinCiclo = mutableListOf<Alumno>()
        val gradosNuevos = mutableListOf<GradoNuevo>()
        val nuevosSet = mutableSetOf<String>()

        val activos = alumnos.filter { a ->
            a.estado == "ACTIVO" && (carreraFiltro.isNullOrBlank() || a.nivel == carreraFiltro)
        }

        for (a in activos) {
            val p = CiclosUtils.parsearCiclo(a.grado)
            if (p.ciclo == null) {
                sinCiclo.add(a)
                continue
            }
            val idx = CiclosUtils.CICLOS.indexOf(p.ciclo)
            if (idx < 0) {
                sinCiclo.add(a)
                continue
            }
            if (idx == CiclosUtils.CICLOS.size - 1) {
                egresan.add(a)
                continue
            }

            val proxCiclo = CiclosUtils.CICLOS[idx + 1]
            val nuevoNombre = CiclosUtils.nombreCiclo(a.nivel, proxCiclo, p.seccion)
            mover.add(
                PlanMover(
                    alumno = a,
                    de = a.grado,
                    a = nuevoNombre,
                    carrera = a.nivel,
                    ciclo = proxCiclo
                )
            )

            val key = "${a.nivel}|$nuevoNombre"
            if (key !in existentes && key !in nuevosSet) {
                nuevosSet.add(key)
                gradosNuevos.add(GradoNuevo(nivel = a.nivel, nombre = nuevoNombre))
            }
        }

        return PlanPromocion(
            mover = mover,
            egresan = egresan,
            sinCiclo = sinCiclo,
            gradosNuevos = gradosNuevos
        )
    }

    fun crearPeriodoInicial(
        colegioId: String,
        nombre: String,
        inicio: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                val nuevo = Periodo(
                    colegioId = colegioId,
                    nombre = nombre.trim(),
                    inicio = inicio.trim(),
                    activo = true
                )
                catalogosRepo.guardarPeriodo(nuevo)
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo crear el periodo")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }

    fun aplicarCambioDeCiclo(
        colegioId: String,
        nuevoNombre: String,
        nuevoInicio: String,
        carreraFiltro: String?,
        plan: PlanPromocion,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                // 1. Crear grados nuevos
                for (g in plan.gradosNuevos) {
                    catalogosRepo.guardarGrado(
                        Grado(colegioId = colegioId, nivel = g.nivel, nombre = g.nombre)
                    )
                }

                // 2. Modificar alumnos
                val alumnosActualizados = mutableListOf<Alumno>()
                for (m in plan.mover) {
                    alumnosActualizados.add(m.alumno.copy(grado = m.a))
                }
                for (e in plan.egresan) {
                    alumnosActualizados.add(e.copy(estado = "EGRESADO"))
                }

                if (alumnosActualizados.isNotEmpty()) {
                    alumnosRepo.upsertAlumnos(alumnosActualizados)
                }

                // 3. Cerrar periodo activo
                val act = _uiState.value.periodoActivo
                if (act != null) {
                    val resumenJson = buildJsonObject {
                        put("movidos", plan.mover.size)
                        put("egresados", plan.egresan.size)
                        put("carrera", if (carreraFiltro.isNullOrBlank()) "todas" else carreraFiltro)
                    }
                    val cerrado = act.copy(
                        activo = false,
                        fin = DateUtils.todayStr(),
                        cerradoEn = DateUtils.todayStr() + "T12:00:00.000Z",
                        resumen = resumenJson
                    )
                    catalogosRepo.guardarPeriodo(cerrado)
                }

                // 4. Abrir nuevo periodo
                val nuevoPeriodo = Periodo(
                    colegioId = colegioId,
                    nombre = nuevoNombre.trim(),
                    inicio = nuevoInicio.trim(),
                    activo = true
                )
                catalogosRepo.guardarPeriodo(nuevoPeriodo)

                cargar(colegioId)
                onSuccess("Listo: ${plan.mover.size} pasaron de ciclo y ${plan.egresan.size} egresaron.")
            } catch (e: Exception) {
                onError("No se pudo completar el cambio de ciclo: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }
}

@Composable
fun PeriodosScreen(
    ctx: PantallaCtx,
    viewModel: PeriodosViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    var showNuevoModal by remember { mutableStateOf(false) }
    var showCierreModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Periodos y cambio de ciclo",
            subtitulo = "Cierra el periodo y pasa a los alumnos al ciclo siguiente, sin perder el historial."
        )

        when {
            state.cargando -> {
                SkeletonList(cantidad = 3)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error inesperado",
                    onReintentar = { viewModel.cargar(cid) }
                )
            }

            else -> {
                // Periodo actual
                SectionCard(titulo = "Periodo vigente") {
                    val act = state.periodoActivo
                    val isCompact = LocalConfiguration.current.screenWidthDp < 600
                    if (act != null) {
                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = act.nombre,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Desde ${act.inicio}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showCierreModal = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cerrar periodo y pasar de ciclo")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = act.nombre,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Desde ${act.inicio}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showCierreModal = true },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cerrar periodo y pasar de ciclo")
                                }
                            }
                        }
                    } else {
                        EmptyState(
                            titulo = "Sin periodo vigente",
                            texto = "Define el periodo actual (por ejemplo «2026-II»): los reportes y las alertas de inasistencia cuentan desde su inicio.",
                            icono = Icons.Default.CalendarToday,
                            accion = {
                                Button(
                                    onClick = { showNuevoModal = true },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Definir periodo actual")
                                }
                            }
                        )
                    }
                }

                // Periodos anteriores
                SectionCard(titulo = "Periodos anteriores") {
                    if (state.periodosPasados.isEmpty()) {
                        EmptyState(
                            titulo = "Aún no hay periodos cerrados",
                            texto = "Aquí quedará el historial de cada cierre.",
                            icono = Icons.Default.History
                        )
                    } else {
                        val isCompact = LocalConfiguration.current.screenWidthDp < 600
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.periodosPasados.forEach { p ->
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
                                                    text = p.nombre,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Inicio: ${p.inicio} · Cierre: ${p.fin ?: p.cerradoEn?.take(10) ?: "—"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val movidos = p.resumen?.get("movidos")?.jsonPrimitive?.content ?: "0"
                                            val egresados = p.resumen?.get("egresados")?.jsonPrimitive?.content ?: "0"

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                pe.registroacademico.nativo.ui.components.EstadoBadge(
                                                    texto = "Pasaron: $movidos",
                                                    tipo = pe.registroacademico.nativo.ui.components.TipoEstadoBadge.VERDE
                                                )
                                                pe.registroacademico.nativo.ui.components.EstadoBadge(
                                                    texto = "Egresaron: $egresados",
                                                    tipo = pe.registroacademico.nativo.ui.components.TipoEstadoBadge.AZUL
                                                )
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
                                                    text = p.nombre,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Inicio: ${p.inicio} · Cierre: ${p.fin ?: p.cerradoEn?.take(10) ?: "—"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val movidos = p.resumen?.get("movidos")?.jsonPrimitive?.content ?: "0"
                                            val egresados = p.resumen?.get("egresados")?.jsonPrimitive?.content ?: "0"

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                pe.registroacademico.nativo.ui.components.EstadoBadge(
                                                    texto = "Pasaron: $movidos",
                                                    tipo = pe.registroacademico.nativo.ui.components.TipoEstadoBadge.VERDE
                                                )
                                                pe.registroacademico.nativo.ui.components.EstadoBadge(
                                                    texto = "Egresaron: $egresados",
                                                    tipo = pe.registroacademico.nativo.ui.components.TipoEstadoBadge.AZUL
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Explicación didáctica
                SectionCard(titulo = "Cómo funciona el cambio de ciclo") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• Cada alumno activo pasa del ciclo I al II, del II al III… manteniendo su salón (A, B…). Si el ciclo nuevo no existe, se crea automáticamente.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Los alumnos del VI ciclo egresan: quedan como «EGRESADO» (no cuentan como activos), con su historial de asistencia intacto.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Alumnos con un ciclo que no sigue el formato estándar (I–VI) no se tocan y se informan en el resumen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Se cierra el periodo vigente y se abre el nuevo inmediatamente. Recomendamos descargar un respaldo antes.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Modal Crear Periodo
    if (showNuevoModal) {
        var nombreNuevo by remember { mutableStateOf("") }
        var inicioNuevo by remember { mutableStateOf(DateUtils.todayStr()) }
        var errorModal by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showNuevoModal = false },
            title = { Text("Definir periodo actual") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nombreNuevo,
                        onValueChange = { nombreNuevo = it },
                        label = { Text("Nombre (ej: 2026-II)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inicioNuevo,
                        onValueChange = { inicioNuevo = it },
                        label = { Text("Fecha de inicio (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
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
                        if (nombreNuevo.isBlank() || inicioNuevo.isBlank()) {
                            errorModal = "Completa todos los campos obligatorios."
                            return@Button
                        }
                        viewModel.crearPeriodoInicial(
                            colegioId = cid,
                            nombre = nombreNuevo,
                            inicio = inicioNuevo,
                            onSuccess = {
                                showNuevoModal = false
                                ctx.scope.launch {
                                    ctx.snackbarHostState.showSnackbar("Periodo definido con éxito")
                                }
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
                        Text("Guardar")
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

    // Modal Cierre y Promoción
    if (showCierreModal) {
        var carreraSel by remember { mutableStateOf("") }
        var nombreSiguiente by remember { mutableStateOf("") }
        var inicioSiguiente by remember { mutableStateOf(DateUtils.todayStr()) }
        var respaldoConfirmado by remember { mutableStateOf(false) }
        var errorCierre by remember { mutableStateOf<String?>(null) }

        val plan = remember(carreraSel, state.alumnos, state.grados) {
            viewModel.calcularPlan(carreraSel.ifBlank { null })
        }

        AlertDialog(
            onDismissRequest = { showCierreModal = false },
            title = { Text("Cerrar periodo y pasar de ciclo") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = carreraSel,
                        onValueChange = { carreraSel = it },
                        label = { Text("Carrera (vacío = todas)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Opcional: solo una carrera") }
                    )

                    OutlinedTextField(
                        value = nombreSiguiente,
                        onValueChange = { nombreSiguiente = it },
                        label = { Text("Nombre del nuevo periodo *") },
                        placeholder = { Text("Ej: 2027-I") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inicioSiguiente,
                        onValueChange = { inicioSiguiente = it },
                        label = { Text("Inicio del nuevo periodo *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider()

                    Text(
                        text = "Resumen de la promoción:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiCard(
                            valor = plan.mover.size.toString(),
                            etiqueta = "Pasan de ciclo",
                            icono = Icons.Default.School,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            valor = plan.egresan.size.toString(),
                            etiqueta = "Egresan (VI)",
                            icono = Icons.Default.CheckCircle,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (plan.sinCiclo.isNotEmpty()) {
                        Text(
                            text = "Sin cambio (ciclo libre/no reconocido): ${plan.sinCiclo.size} alumnos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (plan.gradosNuevos.isNotEmpty()) {
                        Text(
                            text = "Se crearán automáticamente ${plan.gradosNuevos.size} ciclo(s) nuevo(s): " +
                                    plan.gradosNuevos.take(4).joinToString(", ") { it.nombre } +
                                    if (plan.gradosNuevos.size > 4) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = respaldoConfirmado,
                            onCheckedChange = { respaldoConfirmado = it },
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ya descargué un respaldo de los datos antes de continuar.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (errorCierre != null) {
                        Text(
                            text = errorCierre.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreSiguiente.isBlank() || inicioSiguiente.isBlank()) {
                            errorCierre = "Escribe el nombre y la fecha de inicio del nuevo periodo."
                            return@Button
                        }
                        if (!respaldoConfirmado) {
                            errorCierre = "Confirma que descargaste un respaldo antes de continuar."
                            return@Button
                        }
                        if (plan.mover.isEmpty() && plan.egresan.isEmpty()) {
                            errorCierre = "No hay alumnos activos para pasar de ciclo en la selección."
                            return@Button
                        }

                        viewModel.aplicarCambioDeCiclo(
                            colegioId = cid,
                            nuevoNombre = nombreSiguiente,
                            nuevoInicio = inicioSiguiente,
                            carreraFiltro = carreraSel.ifBlank { null },
                            plan = plan,
                            onSuccess = { msg ->
                                showCierreModal = false
                                ctx.scope.launch {
                                    ctx.snackbarHostState.showSnackbar(msg)
                                }
                            },
                            onError = { errorCierre = it }
                        )
                    },
                    enabled = !state.guardando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (state.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Aplicar cambio de ciclo")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCierreModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
