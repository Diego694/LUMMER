package pe.registroacademico.nativo.ui.consultas.alertas

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
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.RiesgoAlumno
import pe.registroacademico.nativo.domain.RiesgoUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

const val PLANTILLA_RIESGO = "Estimado(a) apoderado(a): {alumno} ({ciclo}) acumula {faltas} inasistencias ({porcentaje} %). El límite es {limite} %: al superarlo se pierde el derecho a evaluación. Por favor comuníquese con {instituto}."

data class AlertasUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val nivelSeleccionado: String = "",
    val soloRiesgo: Boolean = true,
    val niveles: List<Nivel> = emptyList(),
    val items: List<RiesgoAlumno> = emptyList(),
    val periodoNombre: String? = null,
    val fechaDesde: String = "",
    val diasLectivosCount: Int = 0,
    val limiteFaltasPct: Int = 30
)

@HiltViewModel
class AlertasViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertasUiState())
    val uiState: StateFlow<AlertasUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargar(colegioId: String) {
        if (colegioId.isBlank()) return
        _uiState.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            try {
                val niveles = catalogosRepo.listarNiveles(colegioId)
                todosAlumnos = alumnosRepo.listar(colegioId)
                val periodos = catalogosRepo.listarPeriodos(colegioId)
                val periodoActivo = periodos.find { it.activo == true }

                val hoy = DateUtils.todayStr()
                val desde = periodoActivo?.inicio ?: DateUtils.addDays(hoy, -60)

                _uiState.update {
                    it.copy(
                        niveles = niveles,
                        periodoNombre = periodoActivo?.nombre,
                        fechaDesde = desde
                    )
                }

                calcularAlertas(colegioId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudieron cargar las alertas"
                    )
                }
            }
        }
    }

    fun cambiarNivel(colegioId: String, nivel: String) {
        _uiState.update { it.copy(nivelSeleccionado = nivel) }
        calcularAlertas(colegioId)
    }

    fun cambiarSoloRiesgo(colegioId: String, soloRiesgo: Boolean) {
        _uiState.update { it.copy(soloRiesgo = soloRiesgo) }
        calcularAlertas(colegioId)
    }

    private fun calcularAlertas(colegioId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(cargando = true, error = null) }
                val s = _uiState.value
                val hoy = DateUtils.todayStr()
                val desde = s.fechaDesde

                val alumnosFiltrados = todosAlumnos.filter { a ->
                    s.nivelSeleccionado.isBlank() || a.nivel == s.nivelSeleccionado
                }

                val calendario = catalogosRepo.listarDiasCalendario(colegioId)
                val noLectivos = CalendarioUtils.mapaNoLectivos(calendario)

                val asistencias = asistenciaRepo.asistenciasRango(colegioId, desde, hoy)
                val justificaciones = catalogosRepo.justificacionesRango(colegioId, desde, hoy)

                var r = RiesgoUtils.calcularRiesgo(
                    alumnos = alumnosFiltrados,
                    asistencias = asistencias,
                    justificaciones = justificaciones,
                    noLectivos = noLectivos,
                    desde = desde,
                    hasta = hoy,
                    limite = s.limiteFaltasPct,
                    hoy = hoy
                )

                if (s.soloRiesgo) {
                    r = r.filter { it.nivel != "ok" }
                }

                val diasLectivos = CalendarioUtils.diasLectivos(desde, hoy, noLectivos).size

                _uiState.update {
                    it.copy(
                        cargando = false,
                        items = r,
                        diasLectivosCount = diasLectivos
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo calcular el riesgo de inasistencias"
                    )
                }
            }
        }
    }

    fun mensajeWhatsApp(riesgo: RiesgoAlumno, nombreInstituto: String): String {
        val a = riesgo.alumno
        return StatsUtils.mensajeAviso(
            plantilla = PLANTILLA_RIESGO,
            datos = mapOf(
                "alumno" to a.nombre,
                "ciclo" to a.grado,
                "faltas" to riesgo.faltas.toString(),
                "porcentaje" to riesgo.pctFaltas.toString(),
                "limite" to _uiState.value.limiteFaltasPct.toString(),
                "instituto" to nombreInstituto.ifBlank { "el instituto" }
            )
        )
    }
}
