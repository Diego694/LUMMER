package pe.registroacademico.nativo.ui.consultas.reporte

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
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.MatrizAsistenciaResult
import pe.registroacademico.nativo.domain.MatrizResumen
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

data class ReporteUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val nivelSeleccionado: String = "",
    val gradoSeleccionado: String = "",
    val mesSeleccionado: String = DateUtils.todayStr().substring(0, 7),
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val matriz: MatrizAsistenciaResult? = null,
    val titulo: String = "",
    val horaLimite: String = "08:00"
)

@HiltViewModel
class ReporteViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReporteUiState())
    val uiState: StateFlow<ReporteUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargarCatalogos(colegioId: String) {
        if (colegioId.isBlank()) return
        viewModelScope.launch {
            try {
                val niveles = catalogosRepo.listarNiveles(colegioId)
                val grados = catalogosRepo.listarGrados(colegioId)
                todosAlumnos = alumnosRepo.listar(colegioId)
                val horarios = catalogosRepo.listarHorarios(colegioId)
                val tablaLimites = CalendarioUtils.tablaLimites(horarios)
                StatsUtils.configurarLimites(tablaLimites.general, tablaLimites.porNivel)

                val nivelInicial = _uiState.value.nivelSeleccionado.ifBlank {
                    niveles.firstOrNull()?.nombre ?: ""
                }

                _uiState.update {
                    it.copy(
                        niveles = niveles,
                        grados = grados,
                        nivelSeleccionado = nivelInicial,
                        horaLimite = tablaLimites.general
                    )
                }

                if (nivelInicial.isNotBlank()) {
                    generarReporte(colegioId)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Error al cargar catálogos")
                }
            }
        }
    }

    fun cambiarNivel(colegioId: String, nivel: String) {
        _uiState.update { it.copy(nivelSeleccionado = nivel, gradoSeleccionado = "") }
        generarReporte(colegioId)
    }

    fun cambiarGrado(colegioId: String, grado: String) {
        _uiState.update { it.copy(gradoSeleccionado = grado) }
        generarReporte(colegioId)
    }

    fun cambiarMes(colegioId: String, mes: String) {
        _uiState.update { it.copy(mesSeleccionado = mes) }
        generarReporte(colegioId)
    }

    fun generarReporte(colegioId: String) {
        val s = _uiState.value
        if (s.nivelSeleccionado.isBlank()) {
            _uiState.update { it.copy(matriz = null) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            try {
                val alumnos = todosAlumnos.filter { a ->
                    a.estado == "ACTIVO" && a.aprobado != false &&
                        a.nivel == s.nivelSeleccionado &&
                        (s.gradoSeleccionado.isBlank() || a.grado == s.gradoSeleccionado)
                }.sortedWith(
                    compareBy<Alumno> { it.grado }.thenBy { it.nombre }
                )

                val calendario = catalogosRepo.listarDiasCalendario(colegioId)
                val noLectivos = CalendarioUtils.mapaNoLectivos(calendario)
                val dias = DateUtils.diasHabilesDelMes(
                    mes = s.mesSeleccionado,
                    hasta = DateUtils.todayStr(),
                    noLectivosFechas = noLectivos.keys
                )

                if (alumnos.isEmpty() || dias.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            matriz = MatrizAsistenciaResult(
                                dias = emptyList(),
                                filas = emptyList(),
                                resumen = MatrizResumen(alumnos = alumnos.size, dias = dias.size, pct = 0)
                            )
                        )
                    }
                    return@launch
                }

                val desde = dias.first()
                val hasta = dias.last()

                val asistencias = asistenciaRepo.asistenciasRango(colegioId, desde, hasta)
                val justificaciones = catalogosRepo.justificacionesRango(colegioId, desde, hasta)

                val matriz = StatsUtils.matrizAsistencia(
                    alumnos = alumnos,
                    asistencias = asistencias,
                    justificaciones = justificaciones,
                    dias = dias,
                    limite = s.horaLimite
                )

                val titulo = "${s.nivelSeleccionado}${if (s.gradoSeleccionado.isNotBlank()) " · " + s.gradoSeleccionado else ""} — ${s.mesSeleccionado}"

                _uiState.update {
                    it.copy(
                        cargando = false,
                        matriz = matriz,
                        titulo = titulo
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo generar el reporte mensual"
                    )
                }
            }
        }
    }

    fun generarCsv(): String {
        val m = _uiState.value.matriz ?: return ""
        val sb = StringBuilder()

        // Header
        sb.append("Código,Alumno,Carrera,Ciclo")
        m.dias.forEach { d ->
            sb.append(",").append(d)
        }
        sb.append(",Presentes,Tardanzas,Justificadas,Faltas,% Asistencia\n")

        // Rows
        m.filas.forEach { r ->
            sb.append("\"${r.alumno.codigo}\",\"${r.alumno.nombre}\",\"${r.alumno.nivel}\",\"${r.alumno.grado}\"")
            m.dias.forEach { d ->
                sb.append(",").append(r.celdas[d] ?: "F")
            }
            sb.append(",${r.p},${r.t},${r.j},${r.f},${r.pct}\n")
        }

        return sb.toString()
    }

    fun generarResumenTexto(): String {
        val s = _uiState.value
        val m = s.matriz ?: return ""
        return buildString {
            appendLine("REPORTE MENSUAL DE ASISTENCIA")
            appendLine("Institución: Lummer")
            appendLine("Filtro: ${s.titulo}")
            appendLine("Alumnos evaluados: ${m.resumen.alumnos}")
            appendLine("Días de clase registrados: ${m.resumen.dias}")
            appendLine("Asistencia promedio del grupo: ${m.resumen.pct}%")
            appendLine("----------------------------------------")
            m.filas.take(15).forEach { f ->
                appendLine("• ${f.alumno.nombre} (${f.alumno.grado}): ${f.pct}% (P: ${f.p}, T: ${f.t}, J: ${f.j}, F: ${f.f})")
            }
            if (m.filas.size > 15) {
                appendLine("... y ${m.filas.size - 15} alumnos más.")
            }
        }
    }
}
