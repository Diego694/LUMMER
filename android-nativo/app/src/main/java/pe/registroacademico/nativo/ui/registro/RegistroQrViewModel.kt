package pe.registroacademico.nativo.ui.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import javax.inject.Inject

data class ScanLogItem(
    val alumno: Alumno,
    val estado: String, // "ok", "salida", "tardanza", "dup", "dup_salida", "sin_entrada", "offline", "inactivo", "pendiente", "qr_invalido"
    val mensaje: String,
    val hora: String,
    val esTardanza: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class RegistroQrUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val modoSalida: Boolean = false,
    val camaraActiva: Boolean = false,
    val codigoManual: String = "",
    val ultimoResultado: ScanLogItem? = null,
    val logSesion: List<ScanLogItem> = emptyList(),
    val totalPendientesOffline: Int = 0,
    val alumnos: List<Alumno> = emptyList(),
    val horarios: List<Horario> = emptyList(),
    val asistenciasHoy: List<Asistencia> = emptyList()
)

@HiltViewModel
class RegistroQrViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo,
    private val colaOfflineRepo: ColaOfflineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroQrUiState())
    val uiState: StateFlow<RegistroQrUiState> = _uiState.asStateFlow()

    private var ultimoCodigoEscaneado: String = ""
    private var ultimoTiempoEscaneado: Long = 0L

    init {
        viewModelScope.launch {
            colaOfflineRepo.contarPendientes().collect { pendientes ->
                _uiState.update { it.copy(totalPendientesOffline = pendientes) }
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

    fun cambiarModo(modoSalida: Boolean) {
        _uiState.update { it.copy(modoSalida = modoSalida) }
    }

    fun toggleCamara(activa: Boolean) {
        _uiState.update { it.copy(camaraActiva = activa) }
    }

    fun onCodigoManualChange(codigo: String) {
        _uiState.update { it.copy(codigoManual = codigo) }
    }

    fun procesarCodigo(
        texto: String,
        colegioId: String,
        qrModo: String,
        userId: String,
        esManual: Boolean,
        onFeedbackHaptico: () -> Unit = {}
    ) {
        val trimmed = texto.trim()
        if (trimmed.isBlank()) return

        val ahoraMs = System.currentTimeMillis()
        // Anti-duplicado: ignorar mismo código en menos de 3 segundos
        if (!esManual && trimmed == ultimoCodigoEscaneado && ahoraMs - ultimoTiempoEscaneado < 3000L) {
            return
        }
        ultimoCodigoEscaneado = trimmed
        ultimoTiempoEscaneado = ahoraMs

        viewModelScope.launch {
            val state = _uiState.value
            val alumnos = state.alumnos
            val modoSalida = state.modoSalida
            val fechaHoy = DateUtils.todayStr()
            val horaActual = DateUtils.nowHHMM()

            // 1. Resolver el código y el alumno
            var codigoBase = trimmed
            if (!esManual) {
                val resQr = verificarQR(
                    texto = trimmed,
                    buscarPorCodigo = { cod -> alumnos.find { it.codigo.equals(cod, ignoreCase = true) } },
                    ms = DateUtils.ahoraMs(),
                    obtenerSecreto = { it.qrSecreto }
                )

                when (resQr) {
                    is QrResultado.Estatico -> {
                        val alumnoEstatico = alumnos.find { it.codigo.equals(trimmed, ignoreCase = true) }
                        if (qrModo.equals("obligatorio", ignoreCase = true) && alumnoEstatico != null) {
                            registrarResultado(
                                item = ScanLogItem(
                                    alumno = alumnoEstatico,
                                    estado = "qr_invalido",
                                    mensaje = "QR estático no permitido: el estudiante debe abrir su carnet en la app (se puede usar código manual).",
                                    hora = horaActual
                                )
                            )
                            onFeedbackHaptico()
                            return@launch
                        }
                    }
                    is QrResultado.Rechazo -> {
                        val mensajeRechazo = when (resQr.motivo) {
                            MotivoRechazo.VENCIDO -> "El QR está vencido. Pide al estudiante que abra su carnet para actualizarlo."
                            MotivoRechazo.FIRMA -> "La firma del QR no coincide: puede ser una copia. Verifica la identidad."
                            MotivoRechazo.SIN_SECRETO -> "Este alumno aún no tiene QR seguro configurado."
                            else -> "QR no reconocido."
                        }
                        val alumnoMock = resQr.alumno ?: Alumno(
                            id = "",
                            codigo = trimmed,
                            nombre = "Código desconocido"
                        )
                        registrarResultado(
                            item = ScanLogItem(
                                alumno = alumnoMock,
                                estado = "qr_invalido",
                                mensaje = mensajeRechazo,
                                hora = horaActual
                            )
                        )
                        onFeedbackHaptico()
                        return@launch
                    }
                    is QrResultado.Ok -> {
                        codigoBase = resQr.alumno.codigo
                    }
                }
            }

            val alumno = alumnos.find { it.codigo.equals(codigoBase, ignoreCase = true) }
            if (alumno == null) {
                registrarResultado(
                    item = ScanLogItem(
                        alumno = Alumno(id = "", codigo = codigoBase, nombre = "No encontrado"),
                        estado = "qr_invalido",
                        mensaje = "Código no encontrado: $codigoBase",
                        hora = horaActual
                    )
                )
                onFeedbackHaptico()
                return@launch
            }

            // Validar estado del alumno
            if (!alumno.estado.equals("ACTIVO", ignoreCase = true)) {
                registrarResultado(
                    item = ScanLogItem(
                        alumno = alumno,
                        estado = "inactivo",
                        mensaje = "Alumno inactivo — no se registra asistencia",
                        hora = horaActual
                    )
                )
                onFeedbackHaptico()
                return@launch
            }

            if (alumno.aprobado == false) {
                registrarResultado(
                    item = ScanLogItem(
                        alumno = alumno,
                        estado = "pendiente",
                        mensaje = "Registro pendiente de aprobación por el instituto",
                        hora = horaActual
                    )
                )
                onFeedbackHaptico()
                return@launch
            }

            // Horario y tardanza
            val horarioEf = CalendarioUtils.horarioDe(state.horarios, alumno.nivel)
            val esTarde = StatsUtils.esTardanza(horaActual, horarioEf.limite, alumno.nivel)

            val yaAsistio = state.asistenciasHoy.find { it.alumnoId == alumno.id }

            if (modoSalida) {
                // Registrar Salida
                if (yaAsistio == null) {
                    registrarResultado(
                        item = ScanLogItem(
                            alumno = alumno,
                            estado = "sin_entrada",
                            mensaje = "Sin ingreso hoy: no se puede registrar salida",
                            hora = horaActual
                        )
                    )
                    onFeedbackHaptico()
                    return@launch
                }
                if (!yaAsistio.horaSalida.isNullOrBlank()) {
                    registrarResultado(
                        item = ScanLogItem(
                            alumno = alumno,
                            estado = "dup_salida",
                            mensaje = "Ya tenía salida registrada (${yaAsistio.horaSalida})",
                            hora = horaActual
                        )
                    )
                    onFeedbackHaptico()
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
                        registrarResultado(
                            item = ScanLogItem(
                                alumno = alumno,
                                estado = "error",
                                mensaje = "Error: ${e.localizedMessage}",
                                hora = horaActual
                            )
                        )
                        return@launch
                    }
                }

                val actualizada = yaAsistio.copy(horaSalida = horaActual)
                _uiState.update { s ->
                    s.copy(asistenciasHoy = s.asistenciasHoy.map { if (it.id == yaAsistio.id || (it.alumnoId == yaAsistio.alumnoId && it.fecha == fechaHoy)) actualizada else it })
                }

                registrarResultado(
                    item = ScanLogItem(
                        alumno = alumno,
                        estado = if (offline) "offline" else "salida",
                        mensaje = if (offline) "Salida guardada sin conexión: se enviará solo" else "Salida registrada exitosamente",
                        hora = horaActual
                    )
                )
                onFeedbackHaptico()

            } else {
                // Registrar Entrada
                if (yaAsistio != null) {
                    registrarResultado(
                        item = ScanLogItem(
                            alumno = alumno,
                            estado = "dup",
                            mensaje = "Ya estaba registrado hoy a las ${yaAsistio.hora}",
                            hora = yaAsistio.hora
                        )
                    )
                    onFeedbackHaptico()
                    return@launch
                }

                val nuevaAsistencia = Asistencia(
                    colegioId = colegioId,
                    alumnoId = alumno.id,
                    fecha = fechaHoy,
                    hora = horaActual,
                    registradoPor = userId.ifBlank { null },
                    origen = if (esManual) "manual" else "qr"
                )

                var offline = false
                try {
                    asistenciaRepo.registrarAsistencia(nuevaAsistencia)
                } catch (e: Throwable) {
                    if (DateUtils.esErrorRed(e)) {
                        colaOfflineRepo.encolarAsistencia(nuevaAsistencia)
                        offline = true
                    } else {
                        registrarResultado(
                            item = ScanLogItem(
                                alumno = alumno,
                                estado = "error",
                                mensaje = "Error: ${e.localizedMessage}",
                                hora = horaActual
                            )
                        )
                        return@launch
                    }
                }

                _uiState.update { s ->
                    s.copy(asistenciasHoy = s.asistenciasHoy + nuevaAsistencia)
                }

                val estadoFinal = if (offline) "offline" else if (esTarde) "tardanza" else "ok"
                val mensajeFinal = if (offline) {
                    "Guardado sin conexión: se enviará solo"
                } else if (esTarde) {
                    "Llegada con tardanza registrada"
                } else {
                    "Asistencia puntual registrada"
                }

                registrarResultado(
                    item = ScanLogItem(
                        alumno = alumno,
                        estado = estadoFinal,
                        mensaje = mensajeFinal,
                        hora = horaActual,
                        esTardanza = esTarde
                    )
                )
                onFeedbackHaptico()
            }
        }
    }

    private fun registrarResultado(item: ScanLogItem) {
        _uiState.update { s ->
            s.copy(
                ultimoResultado = item,
                logSesion = listOf(item) + s.logSesion.take(14),
                codigoManual = ""
            )
        }
    }
}
