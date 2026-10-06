package pe.registroacademico.nativo.ui.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.AsistenciaRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.Horario
import pe.registroacademico.nativo.data.model.SalidaFila
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.qr.MotivoRechazo
import pe.registroacademico.nativo.domain.qr.QrResultado
import pe.registroacademico.nativo.domain.qr.verificarQR
import pe.registroacademico.nativo.offline.ColaOfflineRepository
import java.security.MessageDigest
import javax.inject.Inject

data class QuioscoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val quioscoActivo: Boolean = false,
    val pinHashConfigurado: String = "",
    val modoOperacion: String = "auto", // "auto", "entrada", "salida"
    val sonidoHabilitado: Boolean = true,
    val vozHabilitada: Boolean = false,
    val camaraFrontal: Boolean = false,
    val ultimoResultado: ScanLogItem? = null,
    val alumnos: List<Alumno> = emptyList(),
    val horarios: List<Horario> = emptyList(),
    val asistenciasHoy: List<Asistencia> = emptyList(),
    val totalPendientesOffline: Int = 0,
    val horaLimaActual: String = "",
    val fechaLimaActual: String = "",
    val solicitandoPinSalida: Boolean = false,
    val pinIngresado: String = "",
    val errorPin: String? = null
)

@HiltViewModel
class QuioscoViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo,
    private val colaOfflineRepo: ColaOfflineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuioscoUiState())
    val uiState: StateFlow<QuioscoUiState> = _uiState.asStateFlow()

    private var ultimoCodigoEscaneado: String = ""
    private var ultimoTiempoEscaneado: Long = 0L
    private var clockJob: Job? = null
    private var clearResultJob: Job? = null

    init {
        viewModelScope.launch {
            colaOfflineRepo.contarPendientes().collect { p ->
                _uiState.update { it.copy(totalPendientesOffline = p) }
            }
        }
        iniciarReloj()
    }

    private fun iniciarReloj() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update {
                    it.copy(
                        horaLimaActual = DateUtils.nowHHMM(),
                        fechaLimaActual = DateUtils.fmtDate(DateUtils.todayStr())
                    )
                }
                delay(1000)
            }
        }
    }

    fun cargarDatos(colegioId: String) {
        if (colegioId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val fechaHoy = DateUtils.todayStr()
                val alumnos = alumnosRepo.listar(colegioId)
                val horarios = catalogosRepo.listarHorarios(colegioId)
                val asistenciasHoy = try {
                    asistenciaRepo.asistenciasPorFecha(colegioId, fechaHoy)
                } catch (e: Throwable) {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        cargando = false,
                        alumnos = alumnos,
                        horarios = horarios,
                        asistenciasHoy = asistenciasHoy
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar datos"
                    )
                }
            }
        }
    }

    fun configurarModo(modo: String) {
        _uiState.update { it.copy(modoOperacion = modo) }
    }

    fun toggleSonido(habilitado: Boolean) {
        _uiState.update { it.copy(sonidoHabilitado = habilitado) }
    }

    fun toggleVoz(habilitado: Boolean) {
        _uiState.update { it.copy(vozHabilitada = habilitado) }
    }

    fun toggleCamaraFrontal(frontal: Boolean) {
        _uiState.update { it.copy(camaraFrontal = frontal) }
    }

    fun iniciarQuiosco(pin: String): Boolean {
        if (!pin.matches(Regex("""^\d{4,6}$"""))) {
            return false
        }
        val hash = hashPin(pin)
        _uiState.update {
            it.copy(
                quioscoActivo = true,
                pinHashConfigurado = hash,
                ultimoResultado = null,
                solicitandoPinSalida = false
            )
        }
        return true
    }

    fun solicitarSalidaQuiosco() {
        _uiState.update {
            it.copy(
                solicitandoPinSalida = true,
                pinIngresado = "",
                errorPin = null
            )
        }
    }

    fun cancelarSalidaQuiosco() {
        _uiState.update {
            it.copy(
                solicitandoPinSalida = false,
                pinIngresado = "",
                errorPin = null
            )
        }
    }

    fun onPinIngresadoChange(pin: String) {
        _uiState.update { it.copy(pinIngresado = pin, errorPin = null) }
    }

    fun verificarPinSalida(): Boolean {
        val pin = _uiState.value.pinIngresado
        val hash = hashPin(pin)
        return if (hash == _uiState.value.pinHashConfigurado) {
            _uiState.update {
                it.copy(
                    quioscoActivo = false,
                    solicitandoPinSalida = false,
                    pinIngresado = "",
                    errorPin = null
                )
            }
            true
        } else {
            _uiState.update { it.copy(errorPin = "PIN incorrecto", pinIngresado = "") }
            false
        }
    }

    fun procesarLectura(
        texto: String,
        colegioId: String,
        qrModo: String,
        userId: String,
        onHapticFeedback: () -> Unit = {}
    ) {
        val trimmed = texto.trim()
        if (trimmed.isBlank()) return

        val ahoraMs = System.currentTimeMillis()
        if (trimmed == ultimoCodigoEscaneado && ahoraMs - ultimoTiempoEscaneado < 3500L) {
            return
        }
        ultimoCodigoEscaneado = trimmed
        ultimoTiempoEscaneado = ahoraMs

        viewModelScope.launch {
            val state = _uiState.value
            val alumnos = state.alumnos
            val fechaHoy = DateUtils.todayStr()
            val horaActual = DateUtils.nowHHMM()

            // 1. Resolver QR
            val resQr = verificarQR(
                texto = trimmed,
                buscarPorCodigo = { cod -> alumnos.find { it.codigo.equals(cod, ignoreCase = true) } },
                ms = DateUtils.ahoraMs(),
                obtenerSecreto = { it.qrSecreto }
            )

            var codigoBase = trimmed
            when (resQr) {
                is QrResultado.Estatico -> {
                    val alumnoEstatico = alumnos.find { it.codigo.equals(trimmed, ignoreCase = true) }
                    if (qrModo.equals("obligatorio", ignoreCase = true) && alumnoEstatico != null) {
                        mostrarResultadoTemporal(
                            ScanLogItem(
                                alumno = alumnoEstatico,
                                estado = "qr_invalido",
                                mensaje = "QR estático no permitido: abre tu carnet en la app.",
                                hora = horaActual
                            )
                        )
                        onHapticFeedback()
                        return@launch
                    }
                }
                is QrResultado.Rechazo -> {
                    val motivoMsg = when (resQr.motivo) {
                        MotivoRechazo.VENCIDO -> "QR vencido: abre tu carnet para actualizarlo."
                        MotivoRechazo.FIRMA -> "QR no válido o duplicado."
                        else -> "Código no reconocido."
                    }
                    val alumnoMock = resQr.alumno ?: Alumno(id = "", codigo = trimmed, nombre = "Desconocido")
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumnoMock,
                            estado = "qr_invalido",
                            mensaje = motivoMsg,
                            hora = horaActual
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }
                is QrResultado.Ok -> {
                    codigoBase = resQr.alumno.codigo
                }
            }

            val alumno = alumnos.find { it.codigo.equals(codigoBase, ignoreCase = true) }
            if (alumno == null) {
                mostrarResultadoTemporal(
                    ScanLogItem(
                        alumno = Alumno(id = "", codigo = codigoBase, nombre = "No encontrado"),
                        estado = "qr_invalido",
                        mensaje = "Código no encontrado: $codigoBase",
                        hora = horaActual
                    )
                )
                onHapticFeedback()
                return@launch
            }

            if (!alumno.estado.equals("ACTIVO", ignoreCase = true)) {
                mostrarResultadoTemporal(
                    ScanLogItem(
                        alumno = alumno,
                        estado = "inactivo",
                        mensaje = "Alumno inactivo — consulta en secretaría",
                        hora = horaActual
                    )
                )
                onHapticFeedback()
                return@launch
            }

            if (alumno.aprobado == false) {
                mostrarResultadoTemporal(
                    ScanLogItem(
                        alumno = alumno,
                        estado = "pendiente",
                        mensaje = "Registro pendiente de aprobación",
                        hora = horaActual
                    )
                )
                onHapticFeedback()
                return@launch
            }

            // Horario
            val horarioEf = CalendarioUtils.horarioDe(state.horarios, alumno.nivel)
            val regHoy = state.asistenciasHoy.find { it.alumnoId == alumno.id }

            // Determinar acción: 'auto' | 'entrada' | 'salida'
            val accion = when (state.modoOperacion) {
                "salida" -> "salida"
                "entrada" -> "entrada"
                else -> StatsUtils.decidirAccion(regHoy, horaActual, horarioEf.permanencia)
            }

            if (accion == "entrada") {
                val ev = if (regHoy != null) "puntual" else CalendarioUtils.estadoIngreso(horarioEf, horaActual)
                if (ev == "temprano") {
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumno,
                            estado = "temprano",
                            mensaje = "Aún no es hora de ingreso. Abre a las ${horarioEf.desde ?: "—"}.",
                            hora = horaActual
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }
                if (ev == "cerrado") {
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumno,
                            estado = "cerrado",
                            mensaje = "El ingreso ya cerró a las ${horarioEf.hasta ?: "—"}.",
                            hora = horaActual
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }

                if (regHoy != null) {
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumno,
                            estado = "dup",
                            mensaje = "Ya registraste tu ingreso hoy a las ${regHoy.hora}.",
                            hora = regHoy.hora
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }

                val esTarde = StatsUtils.esTardanza(horaActual, horarioEf.limite, alumno.nivel)
                val nuevaAsistencia = Asistencia(
                    colegioId = colegioId,
                    alumnoId = alumno.id,
                    fecha = fechaHoy,
                    hora = horaActual,
                    registradoPor = userId.ifBlank { null },
                    origen = "quiosco"
                )

                var offline = false
                try {
                    asistenciaRepo.registrarAsistencia(nuevaAsistencia)
                } catch (e: Throwable) {
                    if (DateUtils.esErrorRed(e)) {
                        colaOfflineRepo.encolarAsistencia(nuevaAsistencia)
                        offline = true
                    } else {
                        mostrarResultadoTemporal(
                            ScanLogItem(
                                alumno = alumno,
                                estado = "error",
                                mensaje = e.localizedMessage ?: "Error al registrar",
                                hora = horaActual
                            )
                        )
                        return@launch
                    }
                }

                _uiState.update { s -> s.copy(asistenciasHoy = s.asistenciasHoy + nuevaAsistencia) }
                mostrarResultadoTemporal(
                    ScanLogItem(
                        alumno = alumno,
                        estado = if (offline) "offline" else if (esTarde) "tardanza" else "ok",
                        mensaje = if (offline) "Guardado sin conexión: se enviará solo." else if (esTarde) "Llegada con tardanza registrada." else "¡Bienvenido/a! Ingreso puntual.",
                        hora = horaActual,
                        esTardanza = esTarde
                    )
                )
                onHapticFeedback()

            } else if (accion == "salida") {
                if (regHoy == null) {
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumno,
                            estado = "sin_entrada",
                            mensaje = "No registraste tu ingreso hoy. Habla con tu docente.",
                            hora = horaActual
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }
                if (!regHoy.horaSalida.isNullOrBlank()) {
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumno,
                            estado = "dup_salida",
                            mensaje = "Ya registraste tu salida hoy.",
                            hora = regHoy.horaSalida
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }

                // Verificar permanencia mínima
                val sp = CalendarioUtils.salidaPermitida(regHoy.hora, horaActual, horarioEf.permanencia)
                if (!sp.ok) {
                    mostrarResultadoTemporal(
                        ScanLogItem(
                            alumno = alumno,
                            estado = "salida_temprana",
                            mensaje = "Aún no puedes salir. Podrás hacerlo desde las ${sp.desde}.",
                            hora = horaActual
                        )
                    )
                    onHapticFeedback()
                    return@launch
                }

                val salidaFila = SalidaFila(
                    alumnoId = alumno.id,
                    fecha = fechaHoy,
                    hora = horaActual
                )

                var offline = false
                try {
                    asistenciaRepo.registrarSalidas(listOf(salidaFila))
                } catch (e: Throwable) {
                    if (DateUtils.esErrorRed(e)) {
                        colaOfflineRepo.encolarSalida(salidaFila)
                        offline = true
                    } else {
                        mostrarResultadoTemporal(
                            ScanLogItem(
                                alumno = alumno,
                                estado = "error",
                                mensaje = e.localizedMessage ?: "Error al registrar",
                                hora = horaActual
                            )
                        )
                        return@launch
                    }
                }

                val actualizada = regHoy.copy(horaSalida = horaActual)
                _uiState.update { s ->
                    s.copy(asistenciasHoy = s.asistenciasHoy.map { if (it.id == regHoy.id || (it.alumnoId == regHoy.alumnoId && it.fecha == fechaHoy)) actualizada else it })
                }
                mostrarResultadoTemporal(
                    ScanLogItem(
                        alumno = alumno,
                        estado = if (offline) "offline" else "salida",
                        mensaje = if (offline) "Salida guardada sin conexión." else "¡Hasta luego! Salida registrada.",
                        hora = horaActual
                    )
                )
                onHapticFeedback()

            } else {
                // "ya_ingreso" o "dup_salida"
                val sp = if (regHoy != null) CalendarioUtils.salidaPermitida(regHoy.hora, horaActual, horarioEf.permanencia) else null
                val extra = if (sp != null && !sp.ok) "Podrás salir desde las ${sp.desde}." else "Hoy ya estás registrado."
                mostrarResultadoTemporal(
                    ScanLogItem(
                        alumno = alumno,
                        estado = "dup",
                        mensaje = "Ya registraste tu ingreso. $extra",
                        hora = regHoy?.hora ?: horaActual
                    )
                )
                onHapticFeedback()
            }
        }
    }

    private fun mostrarResultadoTemporal(item: ScanLogItem) {
        _uiState.update { it.copy(ultimoResultado = item) }
        clearResultJob?.cancel()
        clearResultJob = viewModelScope.launch {
            delay(4000)
            _uiState.update { it.copy(ultimoResultado = null) }
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("ra-quiosco:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCleared() {
        super.onCleared()
        clockJob?.cancel()
        clearResultJob?.cancel()
    }
}
