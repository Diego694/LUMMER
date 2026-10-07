package pe.registroacademico.nativo.ui.aula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AulaRepo
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoEntrega
import pe.registroacademico.nativo.data.model.CursoMaterial
import pe.registroacademico.nativo.domain.AulaUtils
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.domain.esAdmin
import javax.inject.Inject

@HiltViewModel
class AulaViewModel @Inject constructor(
    private val aulaRepo: AulaRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AulaUiState())
    val uiState: StateFlow<AulaUiState> = _uiState.asStateFlow()

    fun cargarCursos(colegioId: String, cursoIdInicial: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(cargandoCursos = true, error = null) }
            try {
                val cursos = if (colegioId.isNotBlank()) aulaRepo.listarCursos(colegioId) else emptyList()
                val cursoActual = cursos.find { it.id == cursoIdInicial }
                    ?: cursos.find { it.id == _uiState.value.cursoSeleccionado?.id }
                    ?: cursos.firstOrNull()

                // Cargar nombres de personal si es admin para mostrar docentes
                val sesion = sesionManager.sesion.value
                val personal = if (sesion.esAdmin && _uiState.value.nombresPersonal.isEmpty()) {
                    aulaRepo.listarPersonal()
                } else emptyList()

                val mapaPersonal = if (personal.isNotEmpty()) {
                    personal.associate { it.id to (it.nombre ?: it.email ?: "Docente") }
                } else _uiState.value.nombresPersonal

                val personalDocente = if (personal.isNotEmpty()) {
                    personal.filter {
                        val rol = it.rol.orEmpty()
                        rol.contains("docente", ignoreCase = true) || rol.contains("coordinador", ignoreCase = true)
                    }
                } else _uiState.value.personalDocente

                _uiState.update {
                    it.copy(
                        cargandoCursos = false,
                        cursos = cursos,
                        cursoSeleccionado = cursoActual,
                        nombresPersonal = mapaPersonal,
                        personalDocente = personalDocente
                    )
                }

                if (cursoActual != null && !cursoActual.id.isNullOrBlank()) {
                    cargarDetallesCurso(cursoActual)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargandoCursos = false,
                        error = e.localizedMessage ?: "Error al cargar cursos del aula"
                    )
                }
            }
        }
    }

    fun seleccionarCurso(curso: Curso) {
        _uiState.update {
            it.copy(
                cursoSeleccionado = curso,
                periodoFiltro = 0,
                entregasLibro = emptyList(),
                libroNotas = emptyList(),
                errorNotas = null
            )
        }
        cargarDetallesCurso(curso)
    }

    private fun cargarDetallesCurso(curso: Curso) {
        val cursoId = curso.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(cargandoContenido = true, error = null) }
            try {
                val materialesDeferred = async { aulaRepo.listarMateriales(cursoId) }
                val actividadesDeferred = async { aulaRepo.listarActividades(cursoId) }
                val docentesDeferred = async { aulaRepo.listarDocentesDelCurso(cursoId) }

                val materiales = materialesDeferred.await()
                val actividades = actividadesDeferred.await()
                val docentes = docentesDeferred.await()
                val docentesIds = docentes.map { it.userId }

                val sesion = sesionManager.sesion.value
                val yo = sesion.userId
                val gestiona = sesion.esAdmin || docentesIds.contains(yo)

                var seccion = _uiState.value.seccionActiva
                if (seccion == AulaSeccion.NOTAS && !gestiona) {
                    seccion = AulaSeccion.MATERIAL
                }

                _uiState.update {
                    it.copy(
                        cargandoContenido = false,
                        materiales = materiales,
                        actividades = actividades,
                        docentesIds = docentesIds,
                        gestionaCurso = gestiona,
                        seccionActiva = seccion
                    )
                }

                if (seccion == AulaSeccion.NOTAS) {
                    cargarNotas(curso, actividades)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargandoContenido = false,
                        error = e.localizedMessage ?: "Error al cargar contenido del curso"
                    )
                }
            }
        }
    }

    fun cambiarSeccion(seccion: AulaSeccion) {
        _uiState.update { it.copy(seccionActiva = seccion) }
        val curso = _uiState.value.cursoSeleccionado
        if (seccion == AulaSeccion.NOTAS && curso != null && _uiState.value.entregasLibro.isEmpty()) {
            cargarNotas(curso, _uiState.value.actividades)
        }
    }

    fun cambiarPeriodoFiltro(periodo: Int) {
        _uiState.update { it.copy(periodoFiltro = periodo) }
        recalcularLibro()
    }

    private fun cargarNotas(curso: Curso, actividades: List<CursoActividad>) {
        val cursoId = curso.id ?: return
        val colegioId = curso.colegioId ?: sesionManager.sesion.value.colegioId
        viewModelScope.launch {
            _uiState.update { it.copy(cargandoNotas = true, errorNotas = null) }
            try {
                val entregasDeferred = async { aulaRepo.listarEntregasCurso(cursoId) }
                val alumnosDeferred = async {
                    if (colegioId.isNotBlank()) aulaRepo.listarAlumnos(colegioId) else emptyList()
                }

                val entregas = entregasDeferred.await()
                val alumnos = alumnosDeferred.await()

                _uiState.update {
                    it.copy(
                        cargandoNotas = false,
                        entregasLibro = entregas,
                        alumnosCurso = alumnos
                    )
                }
                recalcularLibro()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargandoNotas = false,
                        errorNotas = e.localizedMessage ?: "Error al cargar notas"
                    )
                }
            }
        }
    }

    private fun recalcularLibro() {
        val state = _uiState.value
        val curso = state.cursoSeleccionado ?: return
        val periodo = state.periodoFiltro
        val actividades = if (periodo > 0) {
            state.actividades.filter { AulaUtils.periodoDe(it.periodo) == periodo }
        } else {
            state.actividades
        }
        val alumnos = AulaUtils.alumnosDelCurso(state.alumnosCurso, curso)
        val libro = AulaUtils.libroNotas(alumnos, actividades, state.entregasLibro)
        _uiState.update { it.copy(libroNotas = libro) }
    }

    // --- Materiales ---
    fun abrirModalMaterial(material: CursoMaterial? = null) {
        _uiState.update { it.copy(mostrarModalMaterial = true, materialParaEditar = material) }
    }

    fun cerrarModalMaterial() {
        _uiState.update { it.copy(mostrarModalMaterial = false, materialParaEditar = null) }
    }

    fun confirmarEliminarMaterial(material: CursoMaterial?) {
        _uiState.update { it.copy(materialParaEliminar = material) }
    }

    fun guardarMaterial(
        material: CursoMaterial,
        archivoNombre: String?,
        archivoBytes: ByteArray?,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val cursoId = curso.id ?: return
        val colegioId = curso.colegioId ?: sesionManager.sesion.value.colegioId

        viewModelScope.launch {
            try {
                var mat = material
                if (archivoNombre != null && archivoBytes != null) {
                    val subido = aulaRepo.subirArchivoAula(colegioId, cursoId, archivoNombre, archivoBytes)
                    mat = mat.copy(
                        archivoPath = subido.archivoPath,
                        archivoNombre = subido.archivoNombre,
                        archivoBytes = subido.archivoBytes
                    )
                }
                aulaRepo.guardarMaterial(mat)
                cerrarModalMaterial()
                cargarDetallesCurso(curso)
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al guardar material")
            }
        }
    }

    fun eliminarMaterial(
        material: CursoMaterial,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val id = material.id ?: return
        viewModelScope.launch {
            try {
                aulaRepo.eliminarMaterial(id, material.archivoPath)
                _uiState.update { it.copy(materialParaEliminar = null) }
                cargarDetallesCurso(curso)
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar material")
            }
        }
    }

    // --- Actividades ---
    fun abrirModalActividad(actividad: CursoActividad? = null) {
        _uiState.update { it.copy(mostrarModalActividad = true, actividadParaEditar = actividad) }
    }

    fun cerrarModalActividad() {
        _uiState.update { it.copy(mostrarModalActividad = false, actividadParaEditar = null) }
    }

    fun confirmarEliminarActividad(actividad: CursoActividad?) {
        _uiState.update { it.copy(actividadParaEliminar = actividad) }
    }

    fun guardarActividad(
        actividad: CursoActividad,
        archivoNombre: String?,
        archivoBytes: ByteArray?,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val cursoId = curso.id ?: return
        val colegioId = curso.colegioId ?: sesionManager.sesion.value.colegioId

        viewModelScope.launch {
            try {
                var act = actividad
                if (archivoNombre != null && archivoBytes != null) {
                    val subido = aulaRepo.subirArchivoAula(colegioId, cursoId, archivoNombre, archivoBytes)
                    act = act.copy(
                        archivoPath = subido.archivoPath,
                        archivoNombre = subido.archivoNombre,
                        archivoBytes = subido.archivoBytes
                    )
                }
                aulaRepo.guardarActividad(act)
                cerrarModalActividad()
                cargarDetallesCurso(curso)
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al guardar actividad")
            }
        }
    }

    fun eliminarActividad(
        actividad: CursoActividad,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        val curso = _uiState.value.cursoSeleccionado ?: return
        val id = actividad.id ?: return
        viewModelScope.launch {
            try {
                aulaRepo.eliminarActividad(id, actividad.archivoPath)
                _uiState.update { it.copy(actividadParaEliminar = null) }
                cargarDetallesCurso(curso)
                onExito()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar actividad")
            }
        }
    }

    // --- Entregas ---
    fun verEntregas(actividad: CursoActividad) {
        val actId = actividad.id ?: return
        _uiState.update {
            it.copy(
                actividadEntregas = actividad,
                cargandoEntregas = true,
                entregasActividad = emptyList()
            )
        }
        viewModelScope.launch {
            try {
                val entregas = aulaRepo.listarEntregas(actId)
                _uiState.update {
                    it.copy(
                        cargandoEntregas = false,
                        entregasActividad = entregas
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargandoEntregas = false,
                        errorNotas = e.localizedMessage
                    )
                }
            }
        }
    }

    fun cerrarModalEntregas() {
        _uiState.update {
            it.copy(actividadEntregas = null, entregasActividad = emptyList())
        }
    }

    fun calificarEntrega(
        entregaId: String,
        nota: Double?,
        comentario: String,
        onExito: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(guardandoCalificacionId = entregaId) }
            try {
                aulaRepo.calificarEntrega(entregaId, nota, comentario)

                // Actualizar solo esta fila de entregas para conservar borradores de las otras
                val entregasAct = _uiState.value.entregasActividad.map { e ->
                    if (e.id == entregaId) {
                        e.copy(nota = nota, comentario = comentario)
                    } else e
                }

                val entregasLibro = _uiState.value.entregasLibro.map { e ->
                    if (e.id == entregaId) {
                        e.copy(nota = nota)
                    } else e
                }

                _uiState.update {
                    it.copy(
                        guardandoCalificacionId = null,
                        entregasActividad = entregasAct,
                        entregasLibro = entregasLibro
                    )
                }
                recalcularLibro()
                onExito(if (nota == null) "Calificación quitada" else "Nota guardada")
            } catch (e: Exception) {
                _uiState.update { it.copy(guardandoCalificacionId = null) }
                onError("No se pudo guardar: ${e.localizedMessage}")
            }
        }
    }

    // --- Docentes del curso ---
    fun abrirModalDocentes() {
        _uiState.update { it.copy(mostrarModalDocentes = true) }
    }

    fun cerrarModalDocentes() {
        _uiState.update { it.copy(mostrarModalDocentes = false) }
    }

    fun toggleDocente(
        userId: String,
        asignar: Boolean,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cursoId = _uiState.value.cursoSeleccionado?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(guardandoDocenteId = userId) }
            try {
                if (asignar) {
                    aulaRepo.asignarDocente(cursoId, userId)
                    _uiState.update {
                        it.copy(
                            guardandoDocenteId = null,
                            docentesIds = (it.docentesIds + userId).distinct()
                        )
                    }
                } else {
                    aulaRepo.quitarDocente(cursoId, userId)
                    _uiState.update {
                        it.copy(
                            guardandoDocenteId = null,
                            docentesIds = it.docentesIds.filter { d -> d != userId }
                        )
                    }
                }
                onExito()
            } catch (e: Exception) {
                _uiState.update { it.copy(guardandoDocenteId = null) }
                onError(e.localizedMessage ?: "Error al actualizar asignación docente")
            }
        }
    }

    // --- Archivos ---
    fun obtenerUrlArchivo(path: String, onExito: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val url = aulaRepo.urlArchivoAula(path)
                onExito(url)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "No se pudo abrir el archivo")
            }
        }
    }

    // --- Exportar CSV ---
    fun exportarNotasCsv(): String? {
        val state = _uiState.value
        if (state.libroNotas.isEmpty()) return null
        val actividades = if (state.periodoFiltro > 0) {
            state.actividades.filter { AulaUtils.periodoDe(it.periodo) == state.periodoFiltro }
        } else state.actividades

        return AulaUtils.generarCsvNotas(actividades, state.libroNotas)
    }
}
