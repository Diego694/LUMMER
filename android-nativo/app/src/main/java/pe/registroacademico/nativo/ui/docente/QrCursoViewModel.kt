package pe.registroacademico.nativo.ui.docente

import android.graphics.Bitmap
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
import pe.registroacademico.nativo.data.docente.DocenteQrRepo
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.docente.DocenteQrUtils
import javax.inject.Inject

data class QrCursoUiState(
    val cargandoCursos: Boolean = true,
    val cursos: List<Curso> = emptyList(),
    val cursosAgrupados: Map<String, List<Curso>> = emptyMap(),
    val cursoSeleccionado: Curso? = null,
    val sesionId: String? = null,
    val tokenActual: String? = null,
    val segundosRestantes: Long = 30L,
    val expiraEn: String? = null,
    val sesionCerrada: Boolean = false,
    val cargandoSesion: Boolean = false,
    val cantidadMarcados: Int = 0,
    val error: String? = null,
    val qrBitmap: Bitmap? = null
)

@HiltViewModel
class QrCursoViewModel @Inject constructor(
    private val docenteQrRepo: DocenteQrRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrCursoUiState())
    val uiState: StateFlow<QrCursoUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null
    private var colegioIdActual: String = ""

    fun cargarCursos(colegioId: String, userId: String, esAdmin: Boolean, cursoIdInicial: String? = null) {
        colegioIdActual = colegioId
        viewModelScope.launch {
            _uiState.update { it.copy(cargandoCursos = true, error = null) }
            val res = docenteQrRepo.listarCursosDocente(colegioId, userId, esAdmin)
            res.fold(
                onSuccess = { lista ->
                    val agrupados = DocenteQrUtils.agruparCursosPorCarrera(lista)
                    val seleccionado = if (!cursoIdInicial.isNullOrBlank()) {
                        lista.find { it.id == cursoIdInicial }
                    } else {
                        _uiState.value.cursoSeleccionado ?: lista.firstOrNull()
                    }
                    _uiState.update {
                        it.copy(
                            cargandoCursos = false,
                            cursos = lista,
                            cursosAgrupados = agrupados,
                            cursoSeleccionado = seleccionado
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            cargandoCursos = false,
                            error = err.localizedMessage ?: "Error al cargar cursos asignados"
                        )
                    }
                }
            )
        }
    }

    fun seleccionarCurso(curso: Curso) {
        if (_uiState.value.sesionId != null) {
            cerrarSesion()
        }
        _uiState.update {
            it.copy(
                cursoSeleccionado = curso,
                error = null,
                cantidadMarcados = 0
            )
        }
    }

    fun abrirSesion(minutos: Int = 20) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val cursoId = curso.id ?: return

        loopJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cargandoSesion = true,
                    error = null,
                    sesionCerrada = false,
                    cantidadMarcados = 0
                )
            }

            val resAbrir = docenteQrRepo.abrirSesion(cursoId, minutos)
            resAbrir.fold(
                onSuccess = { sesion ->
                    _uiState.update {
                        it.copy(
                            cargandoSesion = false,
                            sesionId = sesion.id,
                            expiraEn = sesion.expiraEn,
                            segundosRestantes = 30L
                        )
                    }
                    refrescarMarcados(cursoId)
                    iniciarBucleRotacion(sesion.id, cursoId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            cargandoSesion = false,
                            error = "No se pudo abrir el QR: " + (err.localizedMessage ?: "Error desconocido")
                        )
                    }
                }
            )
        }
    }

    fun cerrarSesion() {
        val sesionId = _uiState.value.sesionId
        loopJob?.cancel()
        loopJob = null

        _uiState.update {
            it.copy(
                sesionId = null,
                tokenActual = null,
                qrBitmap = null,
                sesionCerrada = true,
                segundosRestantes = 0L
            )
        }

        if (!sesionId.isNullOrBlank()) {
            viewModelScope.launch {
                docenteQrRepo.cerrarSesion(sesionId)
            }
        }
    }

    private fun iniciarBucleRotacion(sesionId: String, cursoId: String) {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            var vueltas = 0
            while (isActive) {
                val estado = _uiState.value
                val faltan = estado.segundosRestantes

                if (faltan <= 2 || estado.tokenActual == null) {
                    val resToken = docenteQrRepo.tokenActual(sesionId)
                    resToken.fold(
                        onSuccess = { tokenResp ->
                            if (tokenResp.cerrada) {
                                _uiState.update {
                                    it.copy(
                                        sesionCerrada = true,
                                        sesionId = null,
                                        tokenActual = null,
                                        qrBitmap = null
                                    )
                                }
                                return@launch
                            }
                            val nuevoToken = tokenResp.token.orEmpty()
                            val nuevosSegundos = tokenResp.segundos ?: 30L
                            val bitmap = DocenteQrUtils.generarQrBitmap(nuevoToken)
                            _uiState.update {
                                it.copy(
                                    tokenActual = nuevoToken,
                                    segundosRestantes = nuevosSegundos,
                                    qrBitmap = bitmap,
                                    expiraEn = tokenResp.expiraEn ?: it.expiraEn
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    error = "Reintentando conexión: " + (err.localizedMessage ?: "error"),
                                    segundosRestantes = 0L
                                )
                            }
                        }
                    )
                } else {
                    _uiState.update { it.copy(segundosRestantes = it.segundosRestantes - 1L) }
                }

                if (vueltas % 4 == 0) {
                    refrescarMarcados(cursoId)
                }
                vueltas++

                delay(1000L)
            }
        }
    }

    private fun refrescarMarcados(cursoId: String) {
        viewModelScope.launch {
            docenteQrRepo.asistenciasHoyCurso(colegioIdActual, cursoId, DateUtils.todayStr()).onSuccess { lista ->
                _uiState.update { it.copy(cantidadMarcados = lista.size) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val sesionId = _uiState.value.sesionId
        loopJob?.cancel()
        if (!sesionId.isNullOrBlank()) {
            viewModelScope.launch {
                docenteQrRepo.cerrarSesion(sesionId)
            }
        }
    }
}
