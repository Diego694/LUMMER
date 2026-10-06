package pe.registroacademico.nativo.ui.consultas.asistalumno

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
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.MatrizFila
import pe.registroacademico.nativo.domain.ResumenAlumno
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

data class AsistAlumnoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val alumnos: List<Alumno> = emptyList(),
    val alumnoSeleccionado: Alumno? = null,
    val historial: List<Asistencia> = emptyList(),
    val resumen: ResumenAlumno = ResumenAlumno(0, 0, 0, 0),
    val matrizFila: MatrizFila? = null,
    val diasMes: List<String> = emptyList(),
    val mesActual: String = DateUtils.todayStr().substring(0, 7),
    val horaLimite: String = "08:00"
)

@HiltViewModel
class AsistAlumnoViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(AsistAlumnoUiState())
    val uiState: StateFlow<AsistAlumnoUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargar(colegioId: String, alumnoIdInicial: String? = null) {
        if (colegioId.isBlank()) return
        _uiState.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            try {
                todosAlumnos = alumnosRepo.listar(colegioId)
                val horarios = catalogosRepo.listarHorarios(colegioId)
                val tablaLimites = CalendarioUtils.tablaLimites(horarios)
                StatsUtils.configurarLimites(tablaLimites.general, tablaLimites.porNivel)

                _uiState.update {
                    it.copy(
                        cargando = false,
                        alumnos = todosAlumnos,
                        horaLimite = tablaLimites.general
                    )
                }

                val seleccionado = if (!alumnoIdInicial.isNullOrBlank()) {
                    todosAlumnos.find { it.id == alumnoIdInicial }
                } else {
                    _uiState.value.alumnoSeleccionado ?: todosAlumnos.firstOrNull()
                }

                if (seleccionado != null) {
                    seleccionarAlumno(colegioId, seleccionado)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo cargar la lista de alumnos"
                    )
                }
            }
        }
    }

    fun buscar(query: String) {
        val qNorm = StringUtils.norm(query)
        val filtrados = if (qNorm.isBlank()) {
            todosAlumnos
        } else {
            todosAlumnos.filter { a ->
                StringUtils.norm("${a.nombre} ${a.codigo} ${a.grado}").contains(qNorm)
            }
        }
        _uiState.update { it.copy(query = query, alumnos = filtrados) }
    }

    fun seleccionarAlumno(colegioId: String, alumno: Alumno) {
        _uiState.update { it.copy(alumnoSeleccionado = alumno, cargando = true) }

        viewModelScope.launch {
            try {
                val hoy = DateUtils.todayStr()
                val dias30 = DateUtils.lastWeekdays(30, hoy)
                val desde30 = dias30.firstOrNull() ?: hoy

                val histCompleto = asistenciaRepo.asistenciasAlumno(alumno.id, 200)
                val hist30 = histCompleto.filter { it.fecha >= desde30 }

                val asistenciasRango = asistenciaRepo.asistenciasRango(colegioId, desde30, hoy)
                val diasClase = asistenciasRango.map { it.fecha }.toSet().size

                val r = StatsUtils.resumenAlumno(
                    historial = hist30,
                    diasClase = diasClase,
                    limite = _uiState.value.horaLimite,
                    nivel = alumno.nivel
                )

                // Matriz mensual del alumno (tardanza cuenta como presente)
                val mes = _uiState.value.mesActual
                val diasHabilesMes = DateUtils.diasHabilesDelMes(mes, hoy)
                val primerDiaMes = diasHabilesMes.firstOrNull() ?: "$mes-01"
                val ultimoDiaMes = diasHabilesMes.lastOrNull() ?: hoy

                val asisMes = asistenciaRepo.asistenciasRango(colegioId, primerDiaMes, ultimoDiaMes)
                val justMes = catalogosRepo.justificacionesRango(colegioId, primerDiaMes, ultimoDiaMes)

                val matrizResultado = StatsUtils.matrizAsistencia(
                    alumnos = listOf(alumno),
                    asistencias = asisMes,
                    justificaciones = justMes,
                    dias = diasHabilesMes,
                    limite = _uiState.value.horaLimite
                )
                val filaMatriz = matrizResultado.filas.firstOrNull()

                _uiState.update {
                    it.copy(
                        cargando = false,
                        alumnoSeleccionado = alumno,
                        historial = hist30,
                        resumen = r,
                        matrizFila = filaMatriz,
                        diasMes = matrizResultado.dias
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo cargar el historial del alumno"
                    )
                }
            }
        }
    }

    fun generarCsv(): String {
        val s = _uiState.value
        val a = s.alumnoSeleccionado ?: return ""
        val limite = s.horaLimite

        val sb = StringBuilder()
        sb.append("Fecha,Hora,Estado,Salida\n")
        s.historial.forEach { h ->
            val horaCorta = if (h.hora.length >= 5) h.hora.substring(0, 5) else h.hora
            val estado = if (StatsUtils.esTardanza(h.hora, limite, a.nivel)) "Tardanza" else "Puntual"
            val salida = h.horaSalida ?: ""
            sb.append("${h.fecha},${horaCorta},${estado},\"${salida}\"\n")
        }
        return sb.toString()
    }
}
