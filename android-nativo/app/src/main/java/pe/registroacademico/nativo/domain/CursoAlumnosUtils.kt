package pe.registroacademico.nativo.domain

import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import java.text.Collator
import java.util.Locale

enum class OrigenMatricula {
    CICLO,
    MANUAL
}

data class EstudianteCursoItem(
    val alumno: Alumno,
    val origen: OrigenMatricula
)

object CursoAlumnosUtils {

    /**
     * Lista de estudiantes que pertenecen al curso (tanto por ciclo como agregados manualmente).
     * Solo estudiantes con estado ACTIVO y aprobado != false.
     * Ordenados alfabéticamente por nombre.
     */
    fun estudiantesDelCurso(
        alumnos: List<Alumno>,
        curso: Curso,
        manualesIds: Set<String>
    ): List<EstudianteCursoItem> {
        val collator = Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }
        return alumnos
            .filter { a ->
                a.estado == "ACTIVO" && a.aprobado != false &&
                    StatsUtils.perteneceACurso(
                        alumnoNivel = a.nivel,
                        alumnoGrado = a.grado,
                        cursoNivel = curso.nivel,
                        cursoGrado = curso.grado,
                        matriculadoManual = a.id in manualesIds
                    )
            }
            .map { a ->
                val esManual = a.id in manualesIds
                EstudianteCursoItem(
                    alumno = a,
                    origen = if (esManual) OrigenMatricula.MANUAL else OrigenMatricula.CICLO
                )
            }
            .sortedWith { x, y -> collator.compare(x.alumno.nombre, y.alumno.nombre) }
    }

    /**
     * Candidatos disponibles para agregar manualmente a un curso:
     * Estudiantes activos y aprobados del instituto que aún NO pertenecen al curso (ni por ciclo ni manual).
     * Ordenados alfabéticamente por nombre.
     */
    fun candidatosParaAgregar(
        alumnos: List<Alumno>,
        curso: Curso,
        manualesIds: Set<String>
    ): List<Alumno> {
        val collator = Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }
        return alumnos
            .filter { a ->
                a.estado == "ACTIVO" && a.aprobado != false &&
                    !StatsUtils.perteneceACurso(
                        alumnoNivel = a.nivel,
                        alumnoGrado = a.grado,
                        cursoNivel = curso.nivel,
                        cursoGrado = curso.grado,
                        matriculadoManual = a.id in manualesIds
                    )
            }
            .sortedWith { x, y -> collator.compare(x.nombre, y.nombre) }
    }
}
