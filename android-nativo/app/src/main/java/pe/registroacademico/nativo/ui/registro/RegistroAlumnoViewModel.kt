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
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.offline.ColaOfflineRepository
import java.util.UUID
import javax.inject.Inject

data class RegistroAlumnoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val busqueda: String = "",
    val filtroAsistencia: String = "todos", // "todos", "pendientes", "presentes"
    val nivelFiltro: String? = null,
    val gradoFiltro: String? = null,
    val alumnos: List<Alumno> = emptyList(),
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val asistenciasHoy: List<Asistencia> = emptyList(),
    val alumnoParaEditar: Alumno? = null,
    val alumnoParaEliminar: Alumno? = null,
    val mostrandoDialogoForm: Boolean = false,
    val mensajeExito: String? = null,
    val errorFormulario: String? = null
)

@HiltViewModel
class RegistroAlumnoViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo,
    private val colaOfflineRepo: ColaOfflineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroAlumnoUiState())
    val uiState: StateFlow<RegistroAlumnoUiState> = _uiState.asStateFlow()

    fun cargarDatos(colegioId: String) {
        if (colegioId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val fechaHoy = DateUtils.todayStr()
                val alumnos = alumnosRepo.listar(colegioId)
                val niveles = catalogosRepo.listarNiveles(colegioId)
                val grados = catalogosRepo.listarGrados(colegioId)
                val asistenciasHoy = try {
                    asistenciaRepo.asistenciasPorFecha(colegioId, fechaHoy)
                } catch (e: Throwable) {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        cargando = false,
                        alumnos = alumnos,
                        niveles = niveles,
                        grados = grados,
                        asistenciasHoy = asistenciasHoy
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar alumnos"
                    )
                }
            }
        }
    }

    fun onBusquedaChange(q: String) {
        _uiState.update { it.copy(busqueda = q) }
    }

    fun onFiltroAsistenciaChange(filtro: String) {
        _uiState.update { it.copy(filtroAsistencia = filtro) }
    }

    fun onFiltroNivelChange(nivel: String?) {
        _uiState.update { it.copy(nivelFiltro = nivel, gradoFiltro = null) }
    }

    fun onFiltroGradoChange(grado: String?) {
        _uiState.update { it.copy(gradoFiltro = grado) }
    }

    fun abrirCrearAlumno() {
        _uiState.update {
            it.copy(
                alumnoParaEditar = null,
                mostrandoDialogoForm = true,
                errorFormulario = null
            )
        }
    }

    fun abrirEditarAlumno(alumno: Alumno) {
        _uiState.update {
            it.copy(
                alumnoParaEditar = alumno,
                mostrandoDialogoForm = true,
                errorFormulario = null
            )
        }
    }

    fun cerrarDialogoForm() {
        _uiState.update {
            it.copy(
                mostrandoDialogoForm = false,
                alumnoParaEditar = null,
                errorFormulario = null
            )
        }
    }

    fun solicitarEliminarAlumno(alumno: Alumno) {
        _uiState.update { it.copy(alumnoParaEliminar = alumno) }
    }

    fun cancelarEliminarAlumno() {
        _uiState.update { it.copy(alumnoParaEliminar = null) }
    }

    fun sugerirSiguienteCodigo(): String {
        val maxNum = _uiState.value.alumnos.mapNotNull {
            val digits = it.codigo.filter { c -> c.isDigit() }
            digits.toIntOrNull()
        }.maxOrNull() ?: 1000
        return "a${maxNum + 1}"
    }

    fun guardarAlumno(
        colegioId: String,
        nombre: String,
        codigo: String,
        nivel: String,
        grado: String,
        apoderado: String,
        estado: String
    ) {
        val nom = nombre.trim()
        val cod = codigo.trim()

        if (nom.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "El nombre completo es requerido.") }
            return
        }
        if (cod.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "El código único de acceso es requerido.") }
            return
        }

        viewModelScope.launch {
            val actual = _uiState.value.alumnoParaEditar
            // Validar código duplicado
            val yaExiste = _uiState.value.alumnos.any {
                it.codigo.equals(cod, ignoreCase = true) && it.id != (actual?.id ?: "")
            }
            if (yaExiste) {
                _uiState.update { it.copy(errorFormulario = "Ese código ya está en uso por otro estudiante.") }
                return@launch
            }

            try {
                val alumnoGuardar = if (actual != null) {
                    actual.copy(
                        nombre = nom,
                        codigo = cod,
                        nivel = nivel,
                        grado = grado,
                        apoderado = apoderado.trim(),
                        estado = estado,
                        nombres = if (actual.nombre != nom) null else actual.nombres,
                        apellidos = if (actual.nombre != nom) null else actual.apellidos
                    )
                } else {
                    Alumno(
                        id = UUID.randomUUID().toString(),
                        colegioId = colegioId,
                        codigo = cod,
                        nombre = nom,
                        nivel = nivel,
                        grado = grado,
                        apoderado = apoderado.trim(),
                        estado = estado,
                        aprobado = true
                    )
                }

                alumnosRepo.guardar(alumnoGuardar)
                val nuevosAlumnos = alumnosRepo.listar(colegioId)
                _uiState.update {
                    it.copy(
                        alumnos = nuevosAlumnos,
                        mostrandoDialogoForm = false,
                        alumnoParaEditar = null,
                        errorFormulario = null,
                        mensajeExito = if (actual != null) "Alumno actualizado correctamente" else "Alumno registrado exitosamente"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(errorFormulario = e.localizedMessage ?: "Error al guardar el alumno")
                }
            }
        }
    }

    fun confirmarEliminarAlumno(colegioId: String) {
        val target = _uiState.value.alumnoParaEliminar ?: return
        viewModelScope.launch {
            try {
                alumnosRepo.eliminar(target.id)
                val nuevosAlumnos = alumnosRepo.listar(colegioId)
                _uiState.update {
                    it.copy(
                        alumnos = nuevosAlumnos,
                        alumnoParaEliminar = null,
                        mensajeExito = "Alumno eliminado correctamente"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        alumnoParaEliminar = null,
                        error = e.localizedMessage ?: "No se pudo eliminar el alumno"
                    )
                }
            }
        }
    }

    fun registrarAsistenciaRapida(alumno: Alumno, colegioId: String, userId: String) {
        viewModelScope.launch {
            val fechaHoy = DateUtils.todayStr()
            val horaActual = DateUtils.nowHHMM()

            val yaExiste = _uiState.value.asistenciasHoy.any { it.alumnoId == alumno.id }
            if (yaExiste) {
                _uiState.update { it.copy(mensajeExito = "${alumno.nombre} ya tenía asistencia registrada hoy") }
                return@launch
            }

            val nueva = Asistencia(
                colegioId = colegioId,
                alumnoId = alumno.id,
                fecha = fechaHoy,
                hora = horaActual,
                registradoPor = userId.ifBlank { null },
                origen = "alumno"
            )

            try {
                asistenciaRepo.registrarAsistencia(nueva)
            } catch (e: Throwable) {
                if (DateUtils.esErrorRed(e)) {
                    colaOfflineRepo.encolarAsistencia(nueva)
                } else {
                    _uiState.update { it.copy(error = e.localizedMessage ?: "Error al registrar asistencia") }
                    return@launch
                }
            }

            _uiState.update { s ->
                s.copy(
                    asistenciasHoy = s.asistenciasHoy + nueva,
                    mensajeExito = "Asistencia registrada para ${alumno.nombre}"
                )
            }
        }
    }

    fun limpiarMensajeExito() {
        _uiState.update { it.copy(mensajeExito = null) }
    }
}
