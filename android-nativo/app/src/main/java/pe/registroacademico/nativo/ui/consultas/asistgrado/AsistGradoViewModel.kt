package pe.registroacademico.nativo.ui.consultas.asistgrado

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
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

data class FilaAsistGrado(
    val alumno: Alumno,
    val reg: Asistencia?,
    val estadoEtiqueta: String,
    val estadoTipo: String, // "Pendiente" | "Inactivo" | "Ausente" | "Tardanza" | "Presente"
    val hora: String
)

data class AsistGradoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val nivelSeleccionado: String = "",
    val gradoSeleccionado: String = "",
    val fechaSeleccionada: String = DateUtils.todayStr(),
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val filas: List<FilaAsistGrado> = emptyList(),
    val presentes: Int = 0,
    val tardes: Int = 0,
    val ausentes: Int = 0,
    val activosCount: Int = 0,
    val pct: Int = 0,
    val horaLimite: String = "08:00"
)

@HiltViewModel
class AsistGradoViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(AsistGradoUiState())
    val uiState: StateFlow<AsistGradoUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargar(colegioId: String) {
        if (colegioId.isBlank()) return
        _uiState.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            try {
                val niveles = catalogosRepo.listarNiveles(colegioId)
                val grados = catalogosRepo.listarGrados(colegioId)
                todosAlumnos = alumnosRepo.listar(colegioId)

                val horarios = catalogosRepo.listarHorarios(colegioId)
                val tablaLimites = CalendarioUtils.tablaLimites(horarios)
                StatsUtils.configurarLimites(tablaLimites.general, tablaLimites.porNivel)

                _uiState.update {
                    it.copy(
                        niveles = niveles,
                        grados = grados,
                        horaLimite = tablaLimites.general
                    )
                }

                cargarAsistenciaDelDia(colegioId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "Error al cargar asistencia por ciclo"
                    )
                }
            }
        }
    }

    fun cambiarNivel(colegioId: String, nivel: String) {
        _uiState.update { it.copy(nivelSeleccionado = nivel, gradoSeleccionado = "") }
        cargarAsistenciaDelDia(colegioId)
    }

    fun cambiarGrado(colegioId: String, grado: String) {
        _uiState.update { it.copy(gradoSeleccionado = grado) }
        cargarAsistenciaDelDia(colegioId)
    }

    fun cambiarFecha(colegioId: String, fecha: String) {
        _uiState.update { it.copy(fechaSeleccionada = fecha) }
        cargarAsistenciaDelDia(colegioId)
    }

    private fun cargarAsistenciaDelDia(colegioId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(cargando = true, error = null) }
                val s = _uiState.value
                val fecha = s.fechaSeleccionada

                val alumnosFiltrados = todosAlumnos.filter { a ->
                    (s.nivelSeleccionado.isBlank() || a.nivel == s.nivelSeleccionado) &&
                    (s.gradoSeleccionado.isBlank() || a.grado == s.gradoSeleccionado)
                }

                if (alumnosFiltrados.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            filas = emptyList(),
                            presentes = 0,
                            tardes = 0,
                            ausentes = 0,
                            activosCount = 0,
                            pct = 0
                        )
                    }
                    return@launch
                }

                val asistencias = asistenciaRepo.asistenciasPorFecha(colegioId, fecha)
                val asistenciaMap = asistencias.associateBy { it.alumnoId }
                val limite = s.horaLimite

                val filas = alumnosFiltrados.map { a ->
                    val reg = asistenciaMap[a.id]
                    val estadoTipo: String
                    val estadoEtiqueta: String

                    when {
                        a.aprobado == false -> {
                            estadoTipo = "Pendiente"
                            estadoEtiqueta = "Pendiente"
                        }
                        a.estado != "ACTIVO" -> {
                            estadoTipo = "Inactivo"
                            estadoEtiqueta = "Inactivo"
                        }
                        reg == null -> {
                            estadoTipo = "Ausente"
                            estadoEtiqueta = "Ausente"
                        }
                        StatsUtils.esTardanza(reg.hora, limite, a.nivel) -> {
                            estadoTipo = "Tardanza"
                            estadoEtiqueta = "Tardanza"
                        }
                        else -> {
                            estadoTipo = "Presente"
                            estadoEtiqueta = "Presente"
                        }
                    }

                    val horaTexto = if (reg != null) {
                        if (reg.hora.length >= 5) reg.hora.substring(0, 5) else reg.hora
                    } else {
                        "—"
                    }

                    FilaAsistGrado(
                        alumno = a,
                        reg = reg,
                        estadoEtiqueta = estadoEtiqueta,
                        estadoTipo = estadoTipo,
                        hora = horaTexto
                    )
                }

                val activos = filas.filter { it.alumno.estado == "ACTIVO" && it.alumno.aprobado != false }
                val presentes = activos.count { it.reg != null }
                val tardes = activos.count { it.estadoTipo == "Tardanza" }
                val ausentes = activos.size - presentes
                val pct = StringUtils.pct(presentes, activos.size)

                _uiState.update {
                    it.copy(
                        cargando = false,
                        filas = filas,
                        presentes = presentes,
                        tardes = tardes,
                        ausentes = ausentes,
                        activosCount = activos.size,
                        pct = pct
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo cargar la asistencia"
                    )
                }
            }
        }
    }

    fun generarCsv(): String {
        val s = _uiState.value
        val sb = StringBuilder()
        sb.append("Fecha,Código,Alumno,Carrera,Ciclo,Estado,Hora\n")
        s.filas.forEach { f ->
            val horaLimpia = if (f.hora == "—") "" else f.hora
            sb.append("${s.fechaSeleccionada},\"${f.alumno.codigo}\",\"${f.alumno.nombre}\",\"${f.alumno.nivel}\",\"${f.alumno.grado}\",${f.estadoEtiqueta},${horaLimpia}\n")
        }
        return sb.toString()
    }
}
