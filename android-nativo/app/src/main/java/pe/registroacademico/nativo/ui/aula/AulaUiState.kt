package pe.registroacademico.nativo.ui.aula

import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoEntrega
import pe.registroacademico.nativo.data.model.CursoMaterial
import pe.registroacademico.nativo.data.model.FilaLibroNotas
import pe.registroacademico.nativo.data.model.PersonalItem

enum class AulaSeccion(val titulo: String) {
    MATERIAL("Material"),
    ACTIVIDADES("Actividades"),
    NOTAS("Notas")
}

data class AulaUiState(
    val cargandoCursos: Boolean = true,
    val cargandoContenido: Boolean = false,
    val error: String? = null,
    val cursos: List<Curso> = emptyList(),
    val cursoSeleccionado: Curso? = null,
    val seccionActiva: AulaSeccion = AulaSeccion.MATERIAL,

    // Materiales y actividades
    val materiales: List<CursoMaterial> = emptyList(),
    val actividades: List<CursoActividad> = emptyList(),
    val docentesIds: List<String> = emptyList(),
    val gestionaCurso: Boolean = false,
    val nombresPersonal: Map<String, String> = emptyMap(),
    val personalDocente: List<PersonalItem> = emptyList(),

    // Libro de notas
    val periodoFiltro: Int = 0, // 0 = todo el curso
    val entregasLibro: List<CursoEntrega> = emptyList(),
    val alumnosCurso: List<Alumno> = emptyList(),
    val libroNotas: List<FilaLibroNotas> = emptyList(),
    val cargandoNotas: Boolean = false,
    val errorNotas: String? = null,

    // Modal de entregas
    val actividadEntregas: CursoActividad? = null,
    val entregasActividad: List<CursoEntrega> = emptyList(),
    val cargandoEntregas: Boolean = false,
    val guardandoCalificacionId: String? = null,

    // Modales y diálogos
    val mostrarModalMaterial: Boolean = false,
    val materialParaEditar: CursoMaterial? = null,
    val materialParaEliminar: CursoMaterial? = null,

    val mostrarModalActividad: Boolean = false,
    val actividadParaEditar: CursoActividad? = null,
    val actividadParaEliminar: CursoActividad? = null,

    val mostrarModalDocentes: Boolean = false,
    val guardandoDocenteId: String? = null
)
