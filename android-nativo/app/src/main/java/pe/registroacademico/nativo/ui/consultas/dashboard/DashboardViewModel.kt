package pe.registroacademico.nativo.ui.consultas.dashboard

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
import pe.registroacademico.nativo.data.model.Comunicado
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.BajaAsistenciaItem
import pe.registroacademico.nativo.domain.PorGradoItem
import pe.registroacademico.nativo.domain.PorNivelItem
import pe.registroacademico.nativo.domain.ResumenDia
import pe.registroacademico.nativo.domain.SerieDiariaPunto
import pe.registroacademico.nativo.domain.StatsUtils
import javax.inject.Inject
import kotlin.math.abs

data class IngresoReciente(
    val alumno: Alumno?,
    val hora: String,
    val esTardanza: Boolean
)

data class DashboardUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val rangoDias: Int = 7,
    val carreraFiltro: String = "",
    val niveles: List<String> = emptyList(),
    val totalCiclos: Int = 0,
    val inactivos: Int = 0,
    val resumen: ResumenDia = ResumenDia(0, 0, 0, 0, 0, 0),
    val serie: List<SerieDiariaPunto> = emptyList(),
    val porGrado: List<PorGradoItem> = emptyList(),
    val porNivel: List<PorNivelItem> = emptyList(),
    val bajaAsistencia: List<BajaAsistenciaItem> = emptyList(),
    val recientes: List<IngresoReciente> = emptyList(),
    val comunicados: List<Comunicado> = emptyList(),
    val delta: Int? = null,
    val deltaHint: String = "Sin histórico",
    val esFinDeSemana: Boolean = false,
    val horaLimite: String = "08:00",
    val actualizado: String = "",
    val filasCache: List<Asistencia> = emptyList(),
    val diasCache: List<String> = emptyList(),
    val alumnosCache: List<Alumno> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String, silencioso: Boolean = false) {
        if (colegioId.isBlank()) return
        if (!silencioso) {
            _uiState.update { it.copy(cargando = true, error = null) }
        }

        viewModelScope.launch {
            try {
                val hoy = DateUtils.todayStr()
                val dias = DateUtils.lastWeekdays(_uiState.value.rangoDias, hoy)
                val desde = dias.firstOrNull() ?: hoy

                // Fetch data in parallel
                val alumnos = alumnosRepo.listar(colegioId)
                val asistenciasRango = asistenciaRepo.asistenciasRango(colegioId, desde, hoy)
                val niveles = catalogosRepo.listarNiveles(colegioId).map { it.nombre }
                val grados = catalogosRepo.listarGrados(colegioId)
                val comunicados = catalogosRepo.listarComunicados(colegioId)
                val horarios = catalogosRepo.listarHorarios(colegioId)

                val tablaLimites = CalendarioUtils.tablaLimites(horarios)
                StatsUtils.configurarLimites(tablaLimites.general, tablaLimites.porNivel)
                val limiteGeneral = tablaLimites.general

                procesarDatos(
                    alumnos = alumnos,
                    asistencias = asistenciasRango,
                    niveles = niveles,
                    totalCiclos = grados.size,
                    comunicados = comunicados,
                    dias = dias,
                    hoy = hoy,
                    limite = limiteGeneral,
                    carrera = _uiState.value.carreraFiltro
                )
            } catch (e: Exception) {
                if (!silencioso) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            error = e.message ?: "No se pudo cargar el dashboard"
                        )
                    }
                }
            }
        }
    }

    fun cambiarRango(colegioId: String, nuevoRango: Int) {
        if (_uiState.value.rangoDias == nuevoRango) return
        _uiState.update { it.copy(rangoDias = nuevoRango) }
        cargar(colegioId)
    }

    fun cambiarCarrera(nuevaCarrera: String) {
        _uiState.update { it.copy(carreraFiltro = nuevaCarrera) }
        // Re-process with cached data
        val s = _uiState.value
        if (s.alumnosCache.isNotEmpty()) {
            procesarDatos(
                alumnos = s.alumnosCache,
                asistencias = s.filasCache,
                niveles = s.niveles,
                totalCiclos = s.totalCiclos,
                comunicados = s.comunicados,
                dias = s.diasCache,
                hoy = DateUtils.todayStr(),
                limite = s.horaLimite,
                carrera = nuevaCarrera
            )
        }
    }

    private fun procesarDatos(
        alumnos: List<Alumno>,
        asistencias: List<Asistencia>,
        niveles: List<String>,
        totalCiclos: Int,
        comunicados: List<Comunicado>,
        dias: List<String>,
        hoy: String,
        limite: String,
        carrera: String
    ) {
        val alumnosVista = if (carrera.isNotBlank()) {
            alumnos.filter { it.nivel == carrera }
        } else {
            alumnos
        }
        val idsVista = alumnosVista.map { it.id }.toSet()
        val asistenciasVista = asistencias.filter { idsVista.contains(it.alumnoId) }
        val asistenciasHoy = asistenciasVista.filter { it.fecha == hoy }

        val r = StatsUtils.resumenDia(alumnosVista, asistenciasHoy, limite)
        val activos = alumnosVista.filter { it.estado == "ACTIVO" && it.aprobado != false }
        val activosIds = activos.map { it.id }.toSet()

        val nivelMap = activos.associate { it.id to it.nivel }
        val serie = StatsUtils.serieDiaria(
            dias = dias,
            asistencias = asistenciasVista.filter { activosIds.contains(it.alumnoId) },
            totalActivos = r.activos,
            limite = limite,
            nivelDe = nivelMap
        )

        val previos = serie.filter { it.fecha != hoy && it.presentes > 0 }
        val promedio = if (previos.isNotEmpty()) {
            (previos.sumOf { it.pct } / previos.size)
        } else {
            0
        }
        val delta = if (previos.isNotEmpty()) r.pct - promedio else null
        val deltaHint = if (delta == null) "Sin histórico" else "vs. promedio ($promedio%)"

        val atencion = StatsUtils.bajaAsistencia(
            alumnos = alumnosVista,
            asistencias = asistenciasVista,
            umbral = 85,
            limiteResultados = 8
        )

        val alumnoMap = alumnos.associateBy { it.id }
        val recientes = asistenciasHoy
            .sortedByDescending { it.hora }
            .take(8)
            .map { asis ->
                val al = alumnoMap[asis.alumnoId]
                IngresoReciente(
                    alumno = al,
                    hora = if (asis.hora.length >= 5) asis.hora.substring(0, 5) else asis.hora,
                    esTardanza = StatsUtils.esTardanza(asis.hora, limite, al?.nivel)
                )
            }

        val porGrado = StatsUtils.porGrado(alumnosVista, asistenciasHoy)
        val porNivel = StatsUtils.porNivel(
            alumnos = alumnosVista,
            niveles = if (carrera.isNotBlank()) listOf(carrera) else niveles
        )

        val inactivos = alumnosVista.size - r.activos
        val esFinde = DateUtils.isWeekend(hoy)
        val horaActual = DateUtils.nowHHMM()

        _uiState.update {
            it.copy(
                cargando = false,
                error = null,
                niveles = niveles,
                totalCiclos = totalCiclos,
                inactivos = inactivos,
                resumen = r,
                serie = serie,
                porGrado = porGrado,
                porNivel = porNivel,
                bajaAsistencia = atencion,
                recientes = recientes,
                comunicados = comunicados,
                delta = delta,
                deltaHint = deltaHint,
                esFinDeSemana = esFinde,
                horaLimite = limite,
                actualizado = horaActual,
                filasCache = asistencias,
                diasCache = dias,
                alumnosCache = alumnos
            )
        }
    }

    fun generarCsv(): String {
        val s = _uiState.value
        val alumnoMap = s.alumnosCache.associateBy { it.id }
        val limite = s.horaLimite

        val filas = s.filasCache.sortedWith(
            compareBy<Asistencia> { it.fecha }.thenBy { it.hora }
        )

        val sb = StringBuilder()
        sb.append("Fecha,Hora,Código,Alumno,Carrera,Ciclo,Estado\n")
        filas.forEach { f ->
            val a = alumnoMap[f.alumnoId]
            val horaCorta = if (f.hora.length >= 5) f.hora.substring(0, 5) else f.hora
            val estado = if (StatsUtils.esTardanza(f.hora, limite, a?.nivel)) "Tardanza" else "Puntual"
            sb.append("${f.fecha},${horaCorta},\"${a?.codigo ?: ""}\",\"${a?.nombre ?: ""}\",\"${a?.nivel ?: ""}\",\"${a?.grado ?: ""}\",${estado}\n")
        }
        return sb.toString()
    }
}
