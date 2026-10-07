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
import pe.registroacademico.nativo.data.model.AsistenciaCurso
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.qr.MotivoRechazo
import pe.registroacademico.nativo.domain.qr.QrResultado
import pe.registroacademico.nativo.domain.qr.verificarQR
import javax.inject.Inject

data class AlumnoMarcadoInfo(
    val hora: String
)

data class EscaneoResultadoInfo(
    val alumno: Alumno?,
    val tipo: TipoResultadoEscaneo,
    val mensaje: String
)

enum class TipoResultadoEscaneo {
    OK, WARN, ERROR
}

enum class FiltroLista {
    TODOS, FALTAN, PRESENTES
}

data class PasarListaUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val cursoSeleccionado: Curso? = null,
    val cursosMios: List<Curso> = emptyList(),
    val todosAlumnos: List<Alumno> = emptyList(),
    val alumnosCurso: List<Alumno> = emptyList(),
    val marcados: Map<String, AlumnoMarcadoInfo> = emptyMap(),
    val filtro: FiltroLista = FiltroLista.TODOS,
    val busqueda: String = "",
    val tambienIngreso: Boolean = true,
    val camaraActiva: Boolean = false,
    val codigoManual: String = "",
    val ultimoResultado: EscaneoResultadoInfo? = null,
    val procesandoMarcado: Boolean = false,
    val horasIngresoHoy: Set<String> = emptySet(),
    val mensajeSnackbar: String? = null
)

@HiltViewModel
class PasarListaViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasarListaUiState())
    val uiState: StateFlow<PasarListaUiState> = _uiState.asStateFlow()

    private var ultimoCodigoEscaneado: String = ""
    private var ultimoTiempoEscaneado: Long = 0L

    fun cargarCursos(colegioId: String, userId: String, esAdmin: Boolean) {
        if (colegioId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val todosCursos = catalogosRepo.listarCursos(colegioId)
                val todosAlumnos = alumnosRepo.listar(colegioId)
                val asignados = if (esAdmin) {
                    emptyList()
                } else {
                    try {
                        catalogosRepo.cursosAsignadosADocente(userId)
                    } catch (e: Throwable) {
                        emptyList()
                    }
                }

                val mios = PasarListaUtils.cursosParaPasarLista(todosCursos, esAdmin, asignados)

                _uiState.update {
                    it.copy(
                        cargando = false,
                        cursosMios = mios,
                        todosAlumnos = todosAlumnos
                    )
                }

                // Si ya había un curso seleccionado, refrescar su lista
                val cursoActual = _uiState.value.cursoSeleccionado
                if (cursoActual != null) {
                    val refrescado = mios.find { it.id == cursoActual.id }
                    if (refrescado != null) {
                        abrirCurso(refrescado, colegioId)
                    } else {
                        volverALista()
                    }
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "No se pudieron cargar tus cursos"
                    )
                }
            }
        }
    }

    fun abrirCurso(curso: Curso, colegioId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cargando = true,
                    cursoSeleccionado = curso,
                    filtro = FiltroLista.TODOS,
                    busqueda = "",
                    camaraActiva = false,
                    ultimoResultado = null
                )
            }
            try {
                val cursoId = curso.id ?: ""
                val hoyStr = DateUtils.todayStr()
                val asistenciasCurso = if (cursoId.isNotBlank()) {
                    asistenciaRepo.asistenciasCursoPorFecha(colegioId, cursoId, hoyStr)
                } else {
                    emptyList()
                }

                val asistenciasDiarias = try {
                    asistenciaRepo.asistenciasPorFecha(colegioId, hoyStr)
                } catch (e: Throwable) {
                    emptyList()
                }

                val marcadosMap = asistenciasCurso.associate {
                    it.alumnoId to AlumnoMarcadoInfo(hora = it.hora.take(5))
                }

                val alumnosDelCurso = PasarListaUtils.alumnosDelCurso(_uiState.value.todosAlumnos, curso)

                _uiState.update {
                    it.copy(
                        cargando = false,
                        alumnosCurso = alumnosDelCurso,
                        marcados = marcadosMap,
                        horasIngresoHoy = asistenciasDiarias.map { a -> a.alumnoId }.toSet()
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "No se pudo cargar la lista del curso"
                    )
                }
            }
        }
    }

    fun volverALista() {
        _uiState.update {
            it.copy(
                cursoSeleccionado = null,
                camaraActiva = false,
                ultimoResultado = null,
                codigoManual = "",
                busqueda = "",
                filtro = FiltroLista.TODOS
            )
        }
    }

    fun cambiarFiltro(filtro: FiltroLista) {
        _uiState.update { it.copy(filtro = filtro) }
    }

    fun cambiarBusqueda(query: String) {
        _uiState.update { it.copy(busqueda = query) }
    }

    fun toggleTambienIngreso(valor: Boolean) {
        _uiState.update { it.copy(tambienIngreso = valor) }
    }

    fun toggleCamara(activa: Boolean) {
        _uiState.update { it.copy(camaraActiva = activa) }
    }

    fun onCodigoManualChange(codigo: String) {
        _uiState.update { it.copy(codigoManual = codigo) }
    }

    fun limpiarMensajeSnackbar() {
        _uiState.update { it.copy(mensajeSnackbar = null) }
    }

    fun marcarAlumno(
        alumno: Alumno,
        colegioId: String,
        userId: String,
        origen: String = "manual"
    ) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val yaMarcado = _uiState.value.marcados.containsKey(alumno.id)

        val validacion = PasarListaUtils.validarMarcar(alumno, curso, yaMarcado)
        if (validacion != null) {
            when (validacion) {
                is ResultadoMarcar.Advertencia -> {
                    _uiState.update {
                        it.copy(
                            ultimoResultado = EscaneoResultadoInfo(alumno, TipoResultadoEscaneo.WARN, validacion.texto),
                            mensajeSnackbar = validacion.texto
                        )
                    }
                }
                is ResultadoMarcar.Error -> {
                    _uiState.update {
                        it.copy(
                            ultimoResultado = EscaneoResultadoInfo(alumno, TipoResultadoEscaneo.ERROR, validacion.texto),
                            mensajeSnackbar = validacion.texto
                        )
                    }
                }
                is ResultadoMarcar.Ok -> {}
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(procesandoMarcado = true) }
            val horaActual = DateUtils.nowHHMM()
            val fechaHoy = DateUtils.todayStr()
            val horaCorta = horaActual.take(5)

            try {
                // Registrar asistencia en curso
                val rowCurso = AsistenciaCurso(
                    colegioId = colegioId,
                    alumnoId = alumno.id,
                    cursoId = curso.id ?: "",
                    fecha = fechaHoy,
                    hora = horaActual,
                    registradoPor = userId,
                    origen = origen
                )
                asistenciaRepo.registrarAsistenciaCurso(rowCurso)

                var extraTexto = ""
                var nuevosIngresos = _uiState.value.horasIngresoHoy

                // Marcar ingreso al instituto si corresponde y aún no ha ingresado hoy
                if (_uiState.value.tambienIngreso && alumno.id !in _uiState.value.horasIngresoHoy) {
                    try {
                        val rowIngreso = Asistencia(
                            colegioId = colegioId,
                            alumnoId = alumno.id,
                            fecha = fechaHoy,
                            hora = horaActual,
                            registradoPor = userId,
                            origen = origen
                        )
                        asistenciaRepo.registrarAsistencia(rowIngreso)
                        nuevosIngresos = nuevosIngresos + alumno.id
                        extraTexto = " · ingreso al instituto marcado"
                    } catch (e: Throwable) {
                        // El ingreso no bloquea la asistencia del curso
                    }
                }

                val mensajeExito = "Presente a las $horaCorta$extraTexto"
                val nuevosMarcados = _uiState.value.marcados + (alumno.id to AlumnoMarcadoInfo(horaCorta))

                _uiState.update {
                    it.copy(
                        procesandoMarcado = false,
                        marcados = nuevosMarcados,
                        horasIngresoHoy = nuevosIngresos,
                        ultimoResultado = EscaneoResultadoInfo(alumno, TipoResultadoEscaneo.OK, mensajeExito),
                        mensajeSnackbar = mensajeExito
                    )
                }
            } catch (e: Throwable) {
                val errTexto = e.localizedMessage ?: "No se pudo marcar asistencia"
                _uiState.update {
                    it.copy(
                        procesandoMarcado = false,
                        ultimoResultado = EscaneoResultadoInfo(alumno, TipoResultadoEscaneo.ERROR, errTexto),
                        mensajeSnackbar = errTexto
                    )
                }
            }
        }
    }

    fun marcarTodosPendientes(colegioId: String, userId: String) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val pendientes = _uiState.value.alumnosCurso.filter { it.id !in _uiState.value.marcados }

        if (pendientes.isEmpty()) {
            _uiState.update { it.copy(mensajeSnackbar = "Todos ya están marcados.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(procesandoMarcado = true) }
            val horaActual = DateUtils.nowHHMM()
            val fechaHoy = DateUtils.todayStr()
            val horaCorta = horaActual.take(5)

            var n = 0
            var fallos = 0
            val nuevosMarcados = _uiState.value.marcados.toMutableMap()
            var nuevosIngresos = _uiState.value.horasIngresoHoy.toMutableSet()

            for (alumno in pendientes) {
                if (alumno.estado != "ACTIVO" || alumno.aprobado == false) {
                    fallos++
                    continue
                }
                try {
                    val rowCurso = AsistenciaCurso(
                        colegioId = colegioId,
                        alumnoId = alumno.id,
                        cursoId = curso.id ?: "",
                        fecha = fechaHoy,
                        hora = horaActual,
                        registradoPor = userId,
                        origen = "manual"
                    )
                    asistenciaRepo.registrarAsistenciaCurso(rowCurso)
                    nuevosMarcados[alumno.id] = AlumnoMarcadoInfo(horaCorta)
                    n++

                    if (_uiState.value.tambienIngreso && alumno.id !in nuevosIngresos) {
                        try {
                            val rowIngreso = Asistencia(
                                colegioId = colegioId,
                                alumnoId = alumno.id,
                                fecha = fechaHoy,
                                hora = horaActual,
                                registradoPor = userId,
                                origen = "manual"
                            )
                            asistenciaRepo.registrarAsistencia(rowIngreso)
                            nuevosIngresos.add(alumno.id)
                        } catch (ignored: Throwable) {
                        }
                    }
                } catch (e: Throwable) {
                    fallos++
                }
            }

            val mensaje = "$n marcados" + if (fallos > 0) " · $fallos con error" else ""
            _uiState.update {
                it.copy(
                    procesandoMarcado = false,
                    marcados = nuevosMarcados,
                    horasIngresoHoy = nuevosIngresos,
                    mensajeSnackbar = mensaje
                )
            }
        }
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
        if (!esManual && trimmed == ultimoCodigoEscaneado && ahoraMs - ultimoTiempoEscaneado < 2000L) {
            return
        }
        ultimoCodigoEscaneado = trimmed
        ultimoTiempoEscaneado = ahoraMs

        viewModelScope.launch {
            val state = _uiState.value
            val todosAlumnos = state.todosAlumnos

            var codigoBase = trimmed
            if (!esManual) {
                val resQr = verificarQR(
                    texto = trimmed,
                    buscarPorCodigo = { cod -> todosAlumnos.find { it.codigo.equals(cod, ignoreCase = true) } },
                    ms = DateUtils.ahoraMs(),
                    obtenerSecreto = { it.qrSecreto }
                )

                when (resQr) {
                    is QrResultado.Estatico -> {
                        val alumnoEstatico = todosAlumnos.find { it.codigo.equals(trimmed, ignoreCase = true) }
                        if (qrModo.equals("obligatorio", ignoreCase = true) && alumnoEstatico != null) {
                            val mensaje = "QR estático no permitido: el estudiante debe abrir su carnet en la app."
                            _uiState.update {
                                it.copy(
                                    ultimoResultado = EscaneoResultadoInfo(alumnoEstatico, TipoResultadoEscaneo.ERROR, mensaje),
                                    mensajeSnackbar = mensaje
                                )
                            }
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
                        _uiState.update {
                            it.copy(
                                ultimoResultado = EscaneoResultadoInfo(resQr.alumno, TipoResultadoEscaneo.ERROR, mensajeRechazo),
                                mensajeSnackbar = mensajeRechazo
                            )
                        }
                        onFeedbackHaptico()
                        return@launch
                    }
                    is QrResultado.Ok -> {
                        codigoBase = resQr.alumno.codigo
                    }
                }
            }

            val alumno = todosAlumnos.find {
                it.codigo.equals(codigoBase, ignoreCase = true) || it.id == codigoBase
            }

            if (alumno == null) {
                val mensajeNoEncontrado = "No se encontró ningún estudiante con el código: $codigoBase"
                _uiState.update {
                    it.copy(
                        ultimoResultado = EscaneoResultadoInfo(null, TipoResultadoEscaneo.ERROR, mensajeNoEncontrado),
                        mensajeSnackbar = mensajeNoEncontrado
                    )
                }
                onFeedbackHaptico()
                return@launch
            }

            onFeedbackHaptico()
            marcarAlumno(alumno, colegioId, userId, if (esManual) "manual" else "qr")
            if (esManual) {
                _uiState.update { it.copy(codigoManual = "") }
            }
        }
    }
}
