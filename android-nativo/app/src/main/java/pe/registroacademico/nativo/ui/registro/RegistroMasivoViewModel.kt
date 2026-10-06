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
import pe.registroacademico.nativo.offline.ColaOfflineRepository
import java.util.UUID
import javax.inject.Inject

data class FilaCsvPreview(
    val linea: Int,
    val nombre: String,
    val codigo: String,
    val nivel: String,
    val grado: String,
    val apoderado: String,
    val estado: String,
    val error: String? = null,
    val aviso: String? = null
)

data class RegistroMasivoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val pestanaActiva: Int = 0, // 0 = Asistencia por Ciclo, 1 = Importar CSV/Pegado
    val nivelSeleccionado: String = "",
    val gradoSeleccionado: String = "",
    val fechaSeleccionada: String = "",
    val alumnosCiclo: List<Alumno> = emptyList(),
    val asistenciasFecha: List<Asistencia> = emptyList(),
    val marcadosIds: Set<String> = emptySet(),
    val niveles: List<Nivel> = emptyList(),
    val grados: List<Grado> = emptyList(),
    // Para importación CSV / Pegado
    val textoCsv: String = "",
    val filasPreview: List<FilaCsvPreview> = emptyList(),
    val totalValidas: Int = 0,
    val totalErrores: Int = 0,
    val procesandoImportacion: Boolean = false,
    val mensajeExito: String? = null
)

@HiltViewModel
class RegistroMasivoViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo,
    private val asistenciaRepo: AsistenciaRepo,
    private val catalogosRepo: CatalogosRepo,
    private val colaOfflineRepo: ColaOfflineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RegistroMasivoUiState(
            fechaSeleccionada = DateUtils.todayStr()
        )
    )
    val uiState: StateFlow<RegistroMasivoUiState> = _uiState.asStateFlow()

    private var todosAlumnos: List<Alumno> = emptyList()

    fun cargarDatos(colegioId: String) {
        if (colegioId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val niveles = catalogosRepo.listarNiveles(colegioId)
                val grados = catalogosRepo.listarGrados(colegioId)
                todosAlumnos = alumnosRepo.listar(colegioId)

                val nivelDefecto = niveles.firstOrNull()?.nombre ?: ""
                val gradosDeNivel = grados.filter { it.nivel == nivelDefecto }
                val gradoDefecto = gradosDeNivel.firstOrNull()?.nombre ?: ""

                val fecha = _uiState.value.fechaSeleccionada.ifBlank { DateUtils.todayStr() }
                val asistenciasFecha = try {
                    asistenciaRepo.asistenciasPorFecha(colegioId, fecha)
                } catch (e: Throwable) {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        cargando = false,
                        niveles = niveles,
                        grados = grados,
                        nivelSeleccionado = nivelDefecto,
                        gradoSeleccionado = gradoDefecto,
                        asistenciasFecha = asistenciasFecha
                    )
                }
                actualizarAlumnosCiclo()
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar datos"
                    )
                }
            }
        }
    }

    fun cambiarPestana(index: Int) {
        _uiState.update { it.copy(pestanaActiva = index) }
    }

    fun seleccionarNivel(colegioId: String, nivel: String) {
        val gradosDeNivel = _uiState.value.grados.filter { it.nivel == nivel }
        val nuevoGrado = gradosDeNivel.firstOrNull()?.nombre ?: ""
        _uiState.update {
            it.copy(
                nivelSeleccionado = nivel,
                gradoSeleccionado = nuevoGrado
            )
        }
        actualizarAlumnosCiclo()
    }

    fun seleccionarGrado(grado: String) {
        _uiState.update { it.copy(gradoSeleccionado = grado) }
        actualizarAlumnosCiclo()
    }

    fun seleccionarFecha(colegioId: String, fecha: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(fechaSeleccionada = fecha) }
            try {
                val asistencias = asistenciaRepo.asistenciasPorFecha(colegioId, fecha)
                _uiState.update { it.copy(asistenciasFecha = asistencias) }
                actualizarAlumnosCiclo()
            } catch (e: Throwable) {
                _uiState.update { it.copy(asistenciasFecha = emptyList()) }
                actualizarAlumnosCiclo()
            }
        }
    }

    private fun actualizarAlumnosCiclo() {
        val nivel = _uiState.value.nivelSeleccionado
        val grado = _uiState.value.gradoSeleccionado
        val yaAsistieron = _uiState.value.asistenciasFecha.map { it.alumnoId }.toSet()

        val alumnosFiltrados = todosAlumnos.filter { a ->
            a.estado == "ACTIVO" && a.aprobado != false &&
                (nivel.isBlank() || a.nivel == nivel) &&
                (grado.isBlank() || a.grado == grado)
        }

        // Marcar por defecto los que no están registrados aún
        val preMarcados = alumnosFiltrados.filter { !yaAsistieron.contains(it.id) }.map { it.id }.toSet()

        _uiState.update {
            it.copy(
                alumnosCiclo = alumnosFiltrados,
                marcadosIds = preMarcados
            )
        }
    }

    fun toggleMarcarAlumno(alumnoId: String) {
        _uiState.update { s ->
            val nuevos = if (s.marcadosIds.contains(alumnoId)) {
                s.marcadosIds - alumnoId
            } else {
                s.marcadosIds + alumnoId
            }
            s.copy(marcadosIds = nuevos)
        }
    }

    fun marcarTodos(marcar: Boolean) {
        val yaAsistieron = _uiState.value.asistenciasFecha.map { it.alumnoId }.toSet()
        val disponibles = _uiState.value.alumnosCiclo.filter { !yaAsistieron.contains(it.id) }.map { it.id }
        _uiState.update {
            it.copy(marcadosIds = if (marcar) disponibles.toSet() else emptySet())
        }
    }

    fun guardarAsistenciaMasiva(colegioId: String, userId: String) {
        val marcados = _uiState.value.marcadosIds
        if (marcados.isEmpty()) {
            _uiState.update { it.copy(error = "Selecciona al menos un alumno para registrar asistencia") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            val fecha = _uiState.value.fechaSeleccionada.ifBlank { DateUtils.todayStr() }
            val hora = if (fecha == DateUtils.todayStr()) DateUtils.nowHHMM() else "08:00"

            val registros = marcados.map { id ->
                Asistencia(
                    colegioId = colegioId,
                    alumnoId = id,
                    fecha = fecha,
                    hora = hora,
                    registradoPor = userId.ifBlank { null },
                    origen = "masivo"
                )
            }

            var offline = false
            var guardadosCount = 0
            try {
                guardadosCount = asistenciaRepo.registrarMasivo(registros)
            } catch (e: Throwable) {
                if (DateUtils.esErrorRed(e)) {
                    registros.forEach { colaOfflineRepo.encolarAsistencia(it) }
                    offline = true
                    guardadosCount = registros.size
                } else {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            error = e.localizedMessage ?: "Error al guardar asistencias"
                        )
                    }
                    return@launch
                }
            }

            // Recargar asistencias
            val nuevasAsistencias = try {
                asistenciaRepo.asistenciasPorFecha(colegioId, fecha)
            } catch (e: Throwable) {
                _uiState.value.asistenciasFecha + registros
            }

            _uiState.update {
                it.copy(
                    cargando = false,
                    asistenciasFecha = nuevasAsistencias,
                    marcadosIds = emptySet(),
                    mensajeExito = if (offline) {
                        "$guardadosCount asistencia(s) guardadas sin conexión (se enviarán solas al reconectar)"
                    } else {
                        "$guardadosCount asistencia(s) registradas exitosamente"
                    }
                )
            }
            actualizarAlumnosCiclo()
        }
    }

    // ================= CSV / Pegado =================

    fun onTextoCsvChange(texto: String) {
        _uiState.update { it.copy(textoCsv = texto) }
        parsearCsv(texto)
    }

    private fun parsearCsv(texto: String) {
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lineas.isEmpty()) {
            _uiState.update {
                it.copy(
                    filasPreview = emptyList(),
                    totalValidas = 0,
                    totalErrores = 0
                )
            }
            return
        }

        val primera = lineas.first().lowercase()
        val tieneCabecera = primera.contains("nombre") || primera.contains("codigo")
        val lineasDatos = if (tieneCabecera) lineas.drop(1) else lineas

        val preview = mutableListOf<FilaCsvPreview>()
        var validas = 0
        var errores = 0

        val nivelesConocidos = _uiState.value.niveles.map { it.nombre.uppercase() }.toSet()

        lineasDatos.forEachIndexed { index, linea ->
            val numLinea = index + if (tieneCabecera) 2 else 1
            // Delimitador por coma, punto y coma o tab
            val tokens = when {
                linea.contains("\t") -> linea.split("\t")
                linea.contains(";") -> linea.split(";")
                else -> linea.split(",")
            }.map { it.trim().trim('"', '\'') }

            val nombre = tokens.getOrNull(0) ?: ""
            val codigo = tokens.getOrNull(1) ?: ""
            val carrera = tokens.getOrNull(2) ?: ""
            val ciclo = tokens.getOrNull(3) ?: ""
            val apoderado = tokens.getOrNull(4) ?: ""
            val estado = (tokens.getOrNull(5) ?: "ACTIVO").ifBlank { "ACTIVO" }.uppercase()

            var error: String? = null
            var aviso: String? = null

            if (nombre.isBlank()) {
                error = "Falta el nombre completo"
            } else if (codigo.isBlank()) {
                error = "Falta el código del alumno"
            } else if (carrera.isNotBlank() && nivelesConocidos.isNotEmpty() && !nivelesConocidos.contains(carrera.uppercase())) {
                aviso = "Carrera no coincide con las registradas"
            }

            if (error != null) {
                errores++
            } else {
                validas++
            }

            preview.add(
                FilaCsvPreview(
                    linea = numLinea,
                    nombre = nombre,
                    codigo = codigo,
                    nivel = carrera,
                    grado = ciclo,
                    apoderado = apoderado,
                    estado = estado,
                    error = error,
                    aviso = aviso
                )
            )
        }

        _uiState.update {
            it.copy(
                filasPreview = preview,
                totalValidas = validas,
                totalErrores = errores
            )
        }
    }

    fun importarAlumnosCsv(colegioId: String) {
        val validas = _uiState.value.filasPreview.filter { it.error == null }
        if (validas.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(procesandoImportacion = true, error = null) }
            try {
                val modelos = validas.map { fila ->
                    Alumno(
                        id = UUID.randomUUID().toString(),
                        colegioId = colegioId,
                        codigo = fila.codigo,
                        nombre = fila.nombre,
                        nivel = fila.nivel,
                        grado = fila.grado,
                        apoderado = fila.apoderado,
                        estado = fila.estado,
                        aprobado = true
                    )
                }

                val insertados = alumnosRepo.upsertAlumnos(modelos)
                todosAlumnos = alumnosRepo.listar(colegioId)

                _uiState.update {
                    it.copy(
                        procesandoImportacion = false,
                        textoCsv = "",
                        filasPreview = emptyList(),
                        totalValidas = 0,
                        totalErrores = 0,
                        mensajeExito = "$insertados alumnos importados o actualizados exitosamente"
                    )
                }
                actualizarAlumnosCiclo()
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        procesandoImportacion = false,
                        error = e.localizedMessage ?: "Error al importar alumnos"
                    )
                }
            }
        }
    }

    fun limpiarMensajeExito() {
        _uiState.update { it.copy(mensajeExito = null) }
    }
}
