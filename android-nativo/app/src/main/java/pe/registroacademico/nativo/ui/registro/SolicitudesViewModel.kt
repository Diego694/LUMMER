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
import pe.registroacademico.nativo.data.model.Alumno
import javax.inject.Inject

data class SolicitudesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val solicitudes: List<Alumno> = emptyList(),
    val alumnoParaRevisar: Alumno? = null,
    val alumnoParaRechazar: Alumno? = null,
    val procesandoId: String? = null,
    val mensajeExito: String? = null
)

@HiltViewModel
class SolicitudesViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(SolicitudesUiState())
    val uiState: StateFlow<SolicitudesUiState> = _uiState.asStateFlow()

    fun cargarSolicitudes(colegioId: String) {
        if (colegioId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val lista = alumnosRepo.listarSolicitudes(colegioId)
                _uiState.update {
                    it.copy(
                        cargando = false,
                        solicitudes = lista
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar solicitudes"
                    )
                }
            }
        }
    }

    fun abrirRevision(alumno: Alumno) {
        _uiState.update { it.copy(alumnoParaRevisar = alumno) }
    }

    fun cerrarRevision() {
        _uiState.update { it.copy(alumnoParaRevisar = null) }
    }

    fun solicitarRechazo(alumno: Alumno) {
        _uiState.update { it.copy(alumnoParaRechazar = alumno) }
    }

    fun cancelarRechazo() {
        _uiState.update { it.copy(alumnoParaRechazar = null) }
    }

    fun aprobarSolicitud(colegioId: String, alumno: Alumno) {
        viewModelScope.launch {
            _uiState.update { it.copy(procesandoId = alumno.id, error = null) }
            try {
                alumnosRepo.aprobarSolicitud(alumno.id, colegioId)
                val nuevaLista = alumnosRepo.listarSolicitudes(colegioId)
                _uiState.update {
                    it.copy(
                        solicitudes = nuevaLista,
                        procesandoId = null,
                        alumnoParaRevisar = null,
                        mensajeExito = "${alumno.nombre} aprobado: su QR ya registra asistencia"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        procesandoId = null,
                        error = e.localizedMessage ?: "No se pudo aprobar la solicitud"
                    )
                }
            }
        }
    }

    fun confirmarRechazar(colegioId: String) {
        val target = _uiState.value.alumnoParaRechazar ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(procesandoId = target.id, error = null) }
            try {
                alumnosRepo.rechazarSolicitud(target.id)
                val nuevaLista = alumnosRepo.listarSolicitudes(colegioId)
                _uiState.update {
                    it.copy(
                        solicitudes = nuevaLista,
                        procesandoId = null,
                        alumnoParaRechazar = null,
                        alumnoParaRevisar = null,
                        mensajeExito = "Solicitud de ${target.nombre} rechazada"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        procesandoId = null,
                        alumnoParaRechazar = null,
                        error = e.localizedMessage ?: "No se pudo rechazar la solicitud"
                    )
                }
            }
        }
    }

    fun limpiarMensajeExito() {
        _uiState.update { it.copy(mensajeExito = null) }
    }
}
