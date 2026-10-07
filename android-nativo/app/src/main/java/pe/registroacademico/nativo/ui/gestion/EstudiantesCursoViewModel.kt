package pe.registroacademico.nativo.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.CursoAlumnosUtils
import pe.registroacademico.nativo.domain.EstudianteCursoItem
import javax.inject.Inject

data class EstudiantesCursoUiState(
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val error: String? = null,
    val curso: Curso? = null,
    val colegioId: String = "",
    val estudiantes: List<EstudianteCursoItem> = emptyList(),
    val candidatos: List<Alumno> = emptyList(),
    val manualesIds: Set<String> = emptySet(),
    val busquedaEstudiantes: String = "",
    val busquedaCandidatos: String = "",
    val candidatosSeleccionados: Set<String> = emptySet(),
    val mostrarAgregarModal: Boolean = false,
    val mensajeSnackbar: String? = null
)

@HiltViewModel
class EstudiantesCursoViewModel @Inject constructor(
    private val catalogosRepo: CatalogosRepo,
    private val alumnosRepo: AlumnosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(EstudiantesCursoUiState())
    val uiState: StateFlow<EstudiantesCursoUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargar(curso: Curso, colegioId: String) {
        _uiState.update {
            it.copy(
                cargando = true,
                error = null,
                curso = curso,
                colegioId = colegioId,
                mostrarAgregarModal = false,
                candidatosSeleccionados = emptySet()
            )
        }
        viewModelScope.launch {
            try {
                todosAlumnos = alumnosRepo.listar(colegioId)
                val cursoId = curso.id.orEmpty()
                val manuales = if (cursoId.isNotBlank()) {
                    catalogosRepo.listarAlumnosDelCurso(cursoId)
                } else emptyList()

                val manualesIds = manuales.map { it.alumnoId }.toSet()
                val estudiantes = CursoAlumnosUtils.estudiantesDelCurso(todosAlumnos, curso, manualesIds)
                val candidatos = CursoAlumnosUtils.candidatosParaAgregar(todosAlumnos, curso, manualesIds)

                _uiState.update {
                    it.copy(
                        cargando = false,
                        estudiantes = estudiantes,
                        candidatos = candidatos,
                        manualesIds = manualesIds
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar los estudiantes"
                    )
                }
            }
        }
    }

    fun cambiarBusquedaEstudiantes(query: String) {
        _uiState.update { it.copy(busquedaEstudiantes = query) }
    }

    fun cambiarBusquedaCandidatos(query: String) {
        _uiState.update { it.copy(busquedaCandidatos = query) }
    }

    fun abrirAgregarModal() {
        _uiState.update {
            it.copy(
                mostrarAgregarModal = true,
                busquedaCandidatos = "",
                candidatosSeleccionados = emptySet()
            )
        }
    }

    fun cerrarAgregarModal() {
        _uiState.update {
            it.copy(
                mostrarAgregarModal = false,
                candidatosSeleccionados = emptySet()
            )
        }
    }

    fun toggleSeleccionCandidato(alumnoId: String) {
        _uiState.update { state ->
            val set = state.candidatosSeleccionados.toMutableSet()
            if (set.contains(alumnoId)) set.remove(alumnoId) else set.add(alumnoId)
            state.copy(candidatosSeleccionados = set)
        }
    }

    fun agregarSeleccionados(userId: String, onExito: () -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        val curso = state.curso ?: return
        val cursoId = curso.id ?: return
        val seleccionados = state.candidatosSeleccionados.toList()
        if (seleccionados.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true) }
            try {
                catalogosRepo.agregarAlumnosACurso(
                    cursoId = cursoId,
                    colegioId = state.colegioId,
                    alumnoIds = seleccionados,
                    agregadoPor = userId.ifBlank { null }
                )
                val manuales = catalogosRepo.listarAlumnosDelCurso(cursoId)
                val manualesIds = manuales.map { it.alumnoId }.toSet()
                val estudiantes = CursoAlumnosUtils.estudiantesDelCurso(todosAlumnos, curso, manualesIds)
                val candidatos = CursoAlumnosUtils.candidatosParaAgregar(todosAlumnos, curso, manualesIds)

                _uiState.update {
                    it.copy(
                        guardando = false,
                        mostrarAgregarModal = false,
                        candidatosSeleccionados = emptySet(),
                        estudiantes = estudiantes,
                        candidatos = candidatos,
                        manualesIds = manualesIds,
                        mensajeSnackbar = "${seleccionados.size} estudiante(s) agregado(s)"
                    )
                }
                onExito()
            } catch (e: Throwable) {
                _uiState.update { it.copy(guardando = false) }
                onError(e.localizedMessage ?: "Error al agregar estudiantes")
            }
        }
    }

    fun quitarAlumno(alumnoId: String, onExito: () -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        val curso = state.curso ?: return
        val cursoId = curso.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true) }
            try {
                catalogosRepo.quitarAlumnoDeCurso(cursoId, alumnoId)
                val manuales = catalogosRepo.listarAlumnosDelCurso(cursoId)
                val manualesIds = manuales.map { it.alumnoId }.toSet()
                val estudiantes = CursoAlumnosUtils.estudiantesDelCurso(todosAlumnos, curso, manualesIds)
                val candidatos = CursoAlumnosUtils.candidatosParaAgregar(todosAlumnos, curso, manualesIds)

                _uiState.update {
                    it.copy(
                        guardando = false,
                        estudiantes = estudiantes,
                        candidatos = candidatos,
                        manualesIds = manualesIds,
                        mensajeSnackbar = "Estudiante quitado del curso"
                    )
                }
                onExito()
            } catch (e: Throwable) {
                _uiState.update { it.copy(guardando = false) }
                onError(e.localizedMessage ?: "Error al quitar estudiante")
            }
        }
    }

    fun limpiarMensajeSnackbar() {
        _uiState.update { it.copy(mensajeSnackbar = null) }
    }
}
