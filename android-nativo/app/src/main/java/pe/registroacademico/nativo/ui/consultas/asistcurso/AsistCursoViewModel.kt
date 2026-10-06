package pe.registroacademico.nativo.ui.consultas.asistcurso

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
import pe.registroacademico.nativo.data.model.AsistenciaCurso
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

data class FilaAsistCurso(
    val alumno: Alumno,
    val reg: AsistenciaCurso?,
    val estadoEtiqueta: String,
    val hora: String
)

data class AsistCursoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val cursos: List<Curso> = emptyList(),
    val cursoSeleccionadoId: String = "",
    val fechaSeleccionada: String = DateUtils.todayStr(),
    val filas: List<FilaAsistCurso> = emptyList(),
    val presentes: Int = 0,
    val totalCurso: Int = 0,
    val horaLimite: String = "08:00"
)

@HiltViewModel
class AsistCursoViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(AsistCursoUiState())
    val uiState: StateFlow<AsistCursoUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargar(colegioId: String) {
        if (colegioId.isBlank()) return
        _uiState.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            try {
                val cursos = catalogosRepo.listarCursos(colegioId).filter { it.activo != false }
                todosAlumnos = alumnosRepo.listar(colegioId)
                val horarios = catalogosRepo.listarHorarios(colegioId)
                val tablaLimites = CalendarioUtils.tablaLimites(horarios)
                StatsUtils.configurarLimites(tablaLimites.general, tablaLimites.porNivel)

                val cursoInicialId = _uiState.value.cursoSeleccionadoId.ifBlank {
                    cursos.firstOrNull()?.id ?: ""
                }

                _uiState.update {
                    it.copy(
                        cursos = cursos,
                        cursoSeleccionadoId = cursoInicialId,
                        horaLimite = tablaLimites.general
                    )
                }

                if (cursoInicialId.isNotBlank()) {
                    cargarAsistenciaCurso(colegioId, cursoInicialId, _uiState.value.fechaSeleccionada)
                } else {
                    _uiState.update { it.copy(cargando = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudieron cargar los cursos"
                    )
                }
            }
        }
    }

    fun cambiarCurso(colegioId: String, cursoId: String) {
        _uiState.update { it.copy(cursoSeleccionadoId = cursoId) }
        cargarAsistenciaCurso(colegioId, cursoId, _uiState.value.fechaSeleccionada)
    }

    fun cambiarFecha(colegioId: String, fecha: String) {
        _uiState.update { it.copy(fechaSeleccionada = fecha) }
        val cursoId = _uiState.value.cursoSeleccionadoId
        if (cursoId.isNotBlank()) {
            cargarAsistenciaCurso(colegioId, cursoId, fecha)
        }
    }

    private fun cargarAsistenciaCurso(colegioId: String, cursoId: String, fecha: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(cargando = true, error = null) }

                val curso = _uiState.value.cursos.find { it.id == cursoId }
                if (curso == null) {
                    _uiState.update { it.copy(cargando = false, filas = emptyList()) }
                    return@launch
                }

                val alumnosDelCurso = todosAlumnos.filter { a ->
                    a.estado == "ACTIVO" && a.aprobado != false &&
                        StatsUtils.perteneceACurso(a.nivel, a.grado, curso.nivel, curso.grado)
                }.sortedWith(
                    compareBy<Alumno> { it.grado }.thenBy { it.nombre }
                )

                val asistencias = asistenciaRepo.asistenciasCursoPorFecha(colegioId, cursoId, fecha)
                val asistenciaMap = asistencias.associateBy { it.alumnoId }
                val limite = _uiState.value.horaLimite

                val filas = alumnosDelCurso.map { a ->
                    val reg = asistenciaMap[a.id]
                    val estado = when {
                        reg == null -> "Ausente"
                        StatsUtils.esTardanza(reg.hora, limite, a.nivel) -> "Tardanza"
                        else -> "Presente"
                    }
                    val horaCorta = if (reg != null) {
                        if (reg.hora.length >= 5) reg.hora.substring(0, 5) else reg.hora
                    } else {
                        "—"
                    }
                    FilaAsistCurso(
                        alumno = a,
                        reg = reg,
                        estadoEtiqueta = estado,
                        hora = horaCorta
                    )
                }

                val pres = filas.count { it.reg != null }

                _uiState.update {
                    it.copy(
                        cargando = false,
                        filas = filas,
                        presentes = pres,
                        totalCurso = filas.size
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo cargar la asistencia del curso"
                    )
                }
            }
        }
    }

    fun generarCsv(): String {
        val s = _uiState.value
        val curso = s.cursos.find { it.id == s.cursoSeleccionadoId }
        val nombreCurso = curso?.nombre ?: "curso"

        val sb = StringBuilder()
        sb.append("Fecha,Curso,Código,Alumno,Carrera,Ciclo,Estado,Hora\n")
        s.filas.forEach { f ->
            val horaLimpia = if (f.hora == "—") "" else f.hora
            sb.append("${s.fechaSeleccionada},\"${nombreCurso}\",\"${f.alumno.codigo}\",\"${f.alumno.nombre}\",\"${f.alumno.nivel}\",\"${f.alumno.grado}\",${f.estadoEtiqueta},${horaLimpia}\n")
        }
        return sb.toString()
    }
}
