package pe.registroacademico.nativo.ui.consultas.avisos

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
import pe.registroacademico.nativo.data.model.AvisoApoderado
import pe.registroacademico.nativo.data.model.Grado
import pe.registroacademico.nativo.data.model.Nivel
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

const val PLANTILLA_FALTA = "Estimado(a) apoderado(a): le informamos que {alumno} ({ciclo}) no registró su asistencia hoy {fecha} en {instituto}. Si se trata de una ausencia justificada, por favor comuníquelo a la institución. Gracias."
const val PLANTILLA_TARDANZA = "Estimado(a) apoderado(a): le informamos que {alumno} ({ciclo}) llegó con tardanza hoy {fecha} a las {hora} a {instituto}. Gracias por su apoyo."

data class FilaAviso(
    val alumno: Alumno,
    val tipo: String, // "Falta" | "Tardanza"
    val hora: String,
    val yaAvisado: Boolean
)

data class AvisosUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val nivelSeleccionado: String = "",
    val gradoSeleccionado: String = "",
    val tipoFiltro: String = "todos", // "todos" | "falta" | "tardanza"
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    val filas: List<FilaAviso> = emptyList(),
    val horaLimite: String = "08:00"
)

@HiltViewModel
class AvisosViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvisosUiState())
    val uiState: StateFlow<AvisosUiState> = _uiState.asStateFlow()

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

                cargarAvisosDelDia(colegioId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudieron cargar los datos de avisos"
                    )
                }
            }
        }
    }

    fun cambiarNivel(colegioId: String, nivel: String) {
        _uiState.update { it.copy(nivelSeleccionado = nivel, gradoSeleccionado = "") }
        cargarAvisosDelDia(colegioId)
    }

    fun cambiarGrado(colegioId: String, grado: String) {
        _uiState.update { it.copy(gradoSeleccionado = grado) }
        cargarAvisosDelDia(colegioId)
    }

    fun cambiarTipo(colegioId: String, tipo: String) {
        _uiState.update { it.copy(tipoFiltro = tipo) }
        cargarAvisosDelDia(colegioId)
    }

    fun cargarAvisosDelDia(colegioId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(cargando = true, error = null) }
                val hoy = DateUtils.todayStr()
                val s = _uiState.value

                val asis = asistenciaRepo.asistenciasPorFecha(colegioId, hoy)
                val avisos = catalogosRepo.avisosPorFecha(colegioId, hoy)
                val just = catalogosRepo.justificacionesRango(colegioId, hoy, hoy)

                val porAlumno = asis.associateBy { it.alumnoId }
                val justificados = just.map { it.alumnoId }.toSet()
                val avisadoSet = avisos.map { "${it.alumnoId}|${it.tipo}" }.toSet()

                val limite = s.horaLimite
                val resultado = mutableListOf<FilaAviso>()

                todosAlumnos.filter { a ->
                    a.estado == "ACTIVO" && a.aprobado != false &&
                        (s.nivelSeleccionado.isBlank() || a.nivel == s.nivelSeleccionado) &&
                        (s.gradoSeleccionado.isBlank() || a.grado == s.gradoSeleccionado)
                }.forEach { a ->
                    val r = porAlumno[a.id]
                    if (r == null && !justificados.contains(a.id) && s.tipoFiltro != "tardanza") {
                        val ya = avisadoSet.contains("${a.id}|Falta")
                        resultado.add(FilaAviso(alumno = a, tipo = "Falta", hora = "", yaAvisado = ya))
                    } else if (r != null && StatsUtils.esTardanza(r.hora, limite, a.nivel) && s.tipoFiltro != "falta") {
                        val ya = avisadoSet.contains("${a.id}|Tardanza")
                        val horaCorta = if (r.hora.length >= 5) r.hora.substring(0, 5) else r.hora
                        resultado.add(FilaAviso(alumno = a, tipo = "Tardanza", hora = horaCorta, yaAvisado = ya))
                    }
                }

                resultado.sortWith(
                    compareBy<FilaAviso> { it.tipo }.thenBy { it.alumno.nombre }
                )

                _uiState.update {
                    it.copy(
                        cargando = false,
                        filas = resultado
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo actualizar la lista de avisos"
                    )
                }
            }
        }
    }

    fun registrarAvisoEnviado(
        colegioId: String,
        alumnoId: String,
        tipo: String,
        canal: String,
        userId: String
    ) {
        viewModelScope.launch {
            try {
                val aviso = AvisoApoderado(
                    colegioId = colegioId,
                    alumnoId = alumnoId,
                    fecha = DateUtils.todayStr(),
                    tipo = tipo,
                    canal = canal,
                    enviadoPor = userId.ifBlank { null }
                )
                catalogosRepo.registrarAviso(aviso)
                cargarAvisosDelDia(colegioId)
            } catch (_: Exception) {
                // Silencioso o log
            }
        }
    }

    fun registrarAvisoManual(
        colegioId: String,
        codigoOAlumno: String,
        tipo: String,
        canal: String,
        userId: String,
        onResultado: (Boolean, String) -> Unit
    ) {
        val q = StringUtils.norm(codigoOAlumno.trim())
        val alumno = todosAlumnos.find {
            StringUtils.norm(it.codigo) == q || StringUtils.norm(it.nombre).contains(q)
        }

        if (alumno == null) {
            onResultado(false, "No se encontró ningún alumno con ese código o nombre.")
            return
        }

        viewModelScope.launch {
            try {
                val aviso = AvisoApoderado(
                    colegioId = colegioId,
                    alumnoId = alumno.id,
                    fecha = DateUtils.todayStr(),
                    tipo = tipo.ifBlank { "Aviso general" },
                    canal = canal.ifBlank { "WhatsApp" },
                    enviadoPor = userId.ifBlank { null }
                )
                catalogosRepo.registrarAviso(aviso)
                cargarAvisosDelDia(colegioId)
                onResultado(true, "Aviso registrado para ${alumno.nombre}.")
            } catch (e: Exception) {
                onResultado(false, e.message ?: "No se pudo registrar el aviso.")
            }
        }
    }

    fun redactarMensaje(fila: FilaAviso, nombreInstituto: String): String {
        val plantilla = if (fila.tipo == "Falta") PLANTILLA_FALTA else PLANTILLA_TARDANZA
        return StatsUtils.mensajeAviso(
            plantilla = plantilla,
            datos = mapOf(
                "alumno" to fila.alumno.nombre,
                "fecha" to DateUtils.fmtDate(DateUtils.todayStr()),
                "instituto" to nombreInstituto.ifBlank { "el instituto" },
                "hora" to fila.hora,
                "ciclo" to CiclosUtils.etiquetaCiclo(fila.alumno.nivel, fila.alumno.grado)
            )
        )
    }
}
