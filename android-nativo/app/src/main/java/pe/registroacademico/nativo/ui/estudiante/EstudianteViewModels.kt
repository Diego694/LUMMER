package pe.registroacademico.nativo.ui.estudiante

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.estudiante.ActividadEstudiante
import pe.registroacademico.nativo.data.estudiante.AlumnoPropio
import pe.registroacademico.nativo.data.estudiante.AsistenciaCursoEstudiante
import pe.registroacademico.nativo.data.estudiante.AsistenciaEstudiante
import pe.registroacademico.nativo.data.estudiante.CarnetEstudiante
import pe.registroacademico.nativo.data.estudiante.ComunicadoEstudiante
import pe.registroacademico.nativo.data.estudiante.CursoEstudiante
import pe.registroacademico.nativo.data.estudiante.EntregaEstudiante
import pe.registroacademico.nativo.data.estudiante.EstudianteRepo
import pe.registroacademico.nativo.data.estudiante.MaterialEstudiante
import pe.registroacademico.nativo.data.estudiante.NotaEstudiante
import pe.registroacademico.nativo.data.estudiante.ResultadoQrCurso
import pe.registroacademico.nativo.data.estudiante.ResultadoQrIngreso
import pe.registroacademico.nativo.data.estudiante.mensajeErrorIngreso
import pe.registroacademico.nativo.domain.estudiante.FlujoQr
import pe.registroacademico.nativo.domain.estudiante.NotaEvaluada
import pe.registroacademico.nativo.domain.estudiante.ResumenAsistenciaEstudiante
import pe.registroacademico.nativo.domain.estudiante.calcularPromedioNotas
import pe.registroacademico.nativo.domain.estudiante.calcularResumenAsistencia
import pe.registroacademico.nativo.domain.estudiante.parsearQrIngreso
import pe.registroacademico.nativo.domain.estudiante.parsearTokenQrCurso
import pe.registroacademico.nativo.domain.estudiante.seleccionarFlujoQr
import javax.inject.Inject

/**
 * Estado genérico de UI para las pantallas del estudiante.
 */
sealed interface EstUiState<out T> {
    data object Cargando : EstUiState<Nothing>
    data class Error(val mensaje: String) : EstUiState<Nothing>
    data class Datos<T>(val datos: T) : EstUiState<T>
}

// ---------------------------------------------------------------------
// 1. EstudianteInicioViewModel (alumno + carnet + estado de aprobación)
// ---------------------------------------------------------------------
data class EstudianteInicioDatos(
    val alumno: AlumnoPropio,
    val carnet: CarnetEstudiante,
    val estaAprobado: Boolean,
    val fotoUrl: String?
)

@HiltViewModel
class EstudianteInicioViewModel @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EstUiState<EstudianteInicioDatos>>(EstUiState.Cargando)
    val uiState: StateFlow<EstUiState<EstudianteInicioDatos>> = _uiState.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        viewModelScope.launch {
            _uiState.value = EstUiState.Cargando
            repo.carnet().fold(
                onSuccess = { carnet ->
                    val alumno = carnet.alumno
                    val fotoUrl = alumno.fotoPath?.let { repo.fotoUrl(it).getOrNull() }
                    _uiState.value = EstUiState.Datos(
                        EstudianteInicioDatos(
                            alumno = alumno,
                            carnet = carnet,
                            estaAprobado = alumno.estaAprobado,
                            fotoUrl = fotoUrl
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.value = EstUiState.Error(
                        error.message ?: "No se pudo cargar la información del estudiante."
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------
// 2. AvisosEstVM (comunicados institucionales / avisos)
// ---------------------------------------------------------------------
@HiltViewModel
class AvisosEstVM @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EstUiState<List<ComunicadoEstudiante>>>(EstUiState.Cargando)
    val uiState: StateFlow<EstUiState<List<ComunicadoEstudiante>>> = _uiState.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        viewModelScope.launch {
            _uiState.value = EstUiState.Cargando
            repo.avisos().fold(
                onSuccess = { avisos ->
                    _uiState.value = EstUiState.Datos(avisos)
                },
                onFailure = { error ->
                    _uiState.value = EstUiState.Error(
                        error.message ?: "No se pudieron cargar los avisos."
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------
// 3. CursosEstVM (mis cursos)
// ---------------------------------------------------------------------
@HiltViewModel
class CursosEstVM @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EstUiState<List<CursoEstudiante>>>(EstUiState.Cargando)
    val uiState: StateFlow<EstUiState<List<CursoEstudiante>>> = _uiState.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        viewModelScope.launch {
            _uiState.value = EstUiState.Cargando
            repo.misCursos().fold(
                onSuccess = { cursos ->
                    _uiState.value = EstUiState.Datos(cursos)
                },
                onFailure = { error ->
                    _uiState.value = EstUiState.Error(
                        error.message ?: "No se pudieron cargar los cursos."
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------
// 4. AulaEstVM (materiales, actividades y entregas por curso)
// ---------------------------------------------------------------------
data class AulaCursoDatos(
    val cursoId: String,
    val materiales: List<MaterialEstudiante>,
    val actividades: List<ActividadEstudiante>,
    val misEntregas: Map<String, EntregaEstudiante?>,
    val promedio: Double?
)

@HiltViewModel
class AulaEstVM @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EstUiState<AulaCursoDatos>>(EstUiState.Cargando)
    val uiState: StateFlow<EstUiState<AulaCursoDatos>> = _uiState.asStateFlow()

    private var cursoActualId: String? = null

    fun cargarCurso(cursoId: String) {
        cursoActualId = cursoId
        recargar()
    }

    fun recargar() {
        val cid = cursoActualId ?: return
        viewModelScope.launch {
            _uiState.value = EstUiState.Cargando
            val matsRes = repo.aulaMateriales(cid)
            val actsRes = repo.aulaActividades(cid)

            if (matsRes.isFailure && actsRes.isFailure) {
                _uiState.value = EstUiState.Error(
                    matsRes.exceptionOrNull()?.message ?: "No se pudo cargar el curso."
                )
                return@launch
            }

            val materiales = matsRes.getOrDefault(emptyList())
            val actividades = actsRes.getOrDefault(emptyList())

            val entregasMap = mutableMapOf<String, EntregaEstudiante?>()
            for (act in actividades) {
                val actId = act.id ?: continue
                entregasMap[actId] = repo.aulaMiEntrega(actId).getOrNull()
            }

            val evaluaciones = actividades.map { a ->
                NotaEvaluada(
                    nota = entregasMap[a.id]?.nota,
                    puntajeMax = a.puntajeMax
                )
            }
            val promedio = calcularPromedioNotas(evaluaciones)

            _uiState.value = EstUiState.Datos(
                AulaCursoDatos(
                    cursoId = cid,
                    materiales = materiales,
                    actividades = actividades,
                    misEntregas = entregasMap,
                    promedio = promedio
                )
            )
        }
    }

    fun entregar(
        actividadId: String,
        cursoId: String,
        colegioId: String,
        texto: String,
        archivoNombre: String? = null,
        archivoBytes: ByteArray? = null,
        onFin: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repo.aulaEntregar(
                actividadId = actividadId,
                cursoId = cursoId,
                colegioId = colegioId,
                texto = texto,
                archivoNombre = archivoNombre,
                archivoBytes = archivoBytes
            )
            onFin(res)
            if (res.isSuccess) {
                recargar()
            }
        }
    }
}

// ---------------------------------------------------------------------
// 5. AsistenciaEstVM (asistencia general y por curso con resumen)
// ---------------------------------------------------------------------
data class AsistenciaEstudianteDatos(
    val asistencias: List<AsistenciaEstudiante>,
    val asistenciasCurso: List<AsistenciaCursoEstudiante>,
    val resumen: ResumenAsistenciaEstudiante
)

@HiltViewModel
class AsistenciaEstVM @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EstUiState<AsistenciaEstudianteDatos>>(EstUiState.Cargando)
    val uiState: StateFlow<EstUiState<AsistenciaEstudianteDatos>> = _uiState.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        viewModelScope.launch {
            _uiState.value = EstUiState.Cargando
            val asisRes = repo.miAsistencia()
            val asisCursoRes = repo.miAsistenciaCurso()

            if (asisRes.isFailure && asisCursoRes.isFailure) {
                _uiState.value = EstUiState.Error(
                    asisRes.exceptionOrNull()?.message ?: "No se pudo cargar la asistencia."
                )
                return@launch
            }

            val asistencias = asisRes.getOrDefault(emptyList())
            val asistenciasCurso = asisCursoRes.getOrDefault(emptyList())
            val horas = asistencias.map { it.hora }
            val dias = maxOf(asistencias.size, 1)
            val resumen = calcularResumenAsistencia(horas, diasClase = dias)

            _uiState.value = EstUiState.Datos(
                AsistenciaEstudianteDatos(
                    asistencias = asistencias,
                    asistenciasCurso = asistenciasCurso,
                    resumen = resumen
                )
            )
        }
    }
}

// ---------------------------------------------------------------------
// 6. NotasEstVM (notas por actividades y promedio)
// ---------------------------------------------------------------------
data class NotasEstudianteDatos(
    val notas: List<NotaEstudiante>,
    val promedioGeneral: Double?,
    val porCurso: Map<String, List<NotaEstudiante>>
)

@HiltViewModel
class NotasEstVM @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EstUiState<NotasEstudianteDatos>>(EstUiState.Cargando)
    val uiState: StateFlow<EstUiState<NotasEstudianteDatos>> = _uiState.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        viewModelScope.launch {
            _uiState.value = EstUiState.Cargando
            repo.misNotas().fold(
                onSuccess = { notas ->
                    val evaluadas = notas.map { NotaEvaluada(it.nota, it.puntajeMax) }
                    val promedio = calcularPromedioNotas(evaluadas)
                    val agrupadas = notas.groupBy { it.cursoNombre }

                    _uiState.value = EstUiState.Datos(
                        NotasEstudianteDatos(
                            notas = notas,
                            promedioGeneral = promedio,
                            porCurso = agrupadas
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.value = EstUiState.Error(
                        error.message ?: "No se pudieron cargar las notas."
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------
// 7. EscanearEstVM (procesamiento del QR escaneado)
// ---------------------------------------------------------------------
sealed interface EscanearUiState {
    data object Idle : EscanearUiState
    data object Cargando : EscanearUiState
    data class Exito(val resultado: ResultadoQrCurso) : EscanearUiState
    data class ExitoIngreso(val resultado: ResultadoQrIngreso) : EscanearUiState
    data class Error(val mensaje: String) : EscanearUiState
}

@HiltViewModel
class EscanearEstVM @Inject constructor(
    private val repo: EstudianteRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow<EscanearUiState>(EscanearUiState.Idle)
    val uiState: StateFlow<EscanearUiState> = _uiState.asStateFlow()

    fun recargar() {
        _uiState.value = EscanearUiState.Idle
    }

    fun reiniciar() {
        _uiState.value = EscanearUiState.Idle
    }

    fun escanear(textoQr: String) {
        // Evita doble envío mientras hay una llamada en curso
        if (_uiState.value is EscanearUiState.Cargando) return

        when (seleccionarFlujoQr(textoQr)) {
            FlujoQr.INGRESO -> {
                val token = parsearQrIngreso(textoQr)
                if (token == null) {
                    _uiState.value = EscanearUiState.Error("Código QR de ingreso no válido")
                    return
                }

                viewModelScope.launch {
                    _uiState.value = EscanearUiState.Cargando
                    repo.marcarIngresoQr(textoQr.trim()).fold(
                        onSuccess = { resultado ->
                            when (resultado) {
                                is ResultadoQrIngreso.Ok,
                                is ResultadoQrIngreso.YaMarcado -> {
                                    _uiState.value = EscanearUiState.ExitoIngreso(resultado)
                                }
                                else -> {
                                    _uiState.value = EscanearUiState.Error(
                                        mensajeErrorIngreso(resultado)
                                    )
                                }
                            }
                        },
                        onFailure = { error ->
                            _uiState.value = EscanearUiState.Error(
                                error.message ?: "Error al registrar la asistencia."
                            )
                        }
                    )
                }
            }
            FlujoQr.CURSO -> {
                val token = parsearTokenQrCurso(textoQr)
                if (token == null) {
                    _uiState.value = EscanearUiState.Error("Ese código QR no es de asistencia de curso.")
                    return
                }

                viewModelScope.launch {
                    _uiState.value = EscanearUiState.Cargando
                    repo.marcarAsistenciaQr(textoQr.trim()).fold(
                        onSuccess = { resultado ->
                            if (resultado.estado == "ok" || resultado.estado == "ya_marcado") {
                                _uiState.value = EscanearUiState.Exito(resultado)
                            } else {
                                _uiState.value = EscanearUiState.Error(
                                    resultado.mensaje.ifBlank { "No se pudo registrar la asistencia." }
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.value = EscanearUiState.Error(
                                error.message ?: "Error al registrar la asistencia."
                            )
                        }
                    )
                }
            }
            FlujoQr.DESCONOCIDO -> {
                _uiState.value = EscanearUiState.Error("Ese código QR no es de asistencia.")
            }
        }
    }
}
