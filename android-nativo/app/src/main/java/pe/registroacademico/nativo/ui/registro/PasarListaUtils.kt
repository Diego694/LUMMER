package pe.registroacademico.nativo.ui.registro

import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.StatsUtils
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

data class ResumenLista(
    val total: Int,
    val presentes: Int,
    val faltan: Int,
    val pct: Int
)

sealed class ResultadoMarcar {
    data class Ok(val texto: String, val hora: String) : ResultadoMarcar()
    data class Advertencia(val texto: String) : ResultadoMarcar()
    data class Error(val texto: String) : ResultadoMarcar()
}

object PasarListaUtils {

    fun cursosParaPasarLista(
        cursos: List<Curso>,
        esAdmin: Boolean,
        asignados: List<String>
    ): List<Curso> {
        val activos = cursos.filter { it.activo != false }
        return if (esAdmin) {
            activos
        } else {
            val setAsignados = asignados.toSet()
            activos.filter { it.id != null && it.id in setAsignados }
        }
    }

    fun alumnosDelCurso(alumnos: List<Alumno>, curso: Curso): List<Alumno> {
        val collator = Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }
        return alumnos.filter { a ->
            a.estado == "ACTIVO" &&
                a.aprobado != false &&
                StatsUtils.perteneceACurso(a.nivel, a.grado, curso.nivel, curso.grado)
        }.sortedWith { a, b -> collator.compare(a.nombre, b.nombre) }
    }

    fun resumenLista(alumnos: List<Alumno>, marcados: Set<String>): ResumenLista {
        val total = alumnos.size
        val presentes = alumnos.count { it.id in marcados }
        val faltan = maxOf(0, total - presentes)
        val pct = if (total > 0) ((presentes.toFloat() / total.toFloat()) * 100f).roundToInt() else 0
        return ResumenLista(
            total = total,
            presentes = presentes,
            faltan = faltan,
            pct = pct
        )
    }

    fun validarMarcar(
        alumno: Alumno,
        curso: Curso,
        yaMarcado: Boolean
    ): ResultadoMarcar? {
        if (alumno.estado != "ACTIVO") {
            return ResultadoMarcar.Error("Alumno inactivo")
        }
        if (alumno.aprobado == false) {
            return ResultadoMarcar.Error("Su registro aún no fue aprobado")
        }
        if (!StatsUtils.perteneceACurso(alumno.nivel, alumno.grado, curso.nivel, curso.grado)) {
            return ResultadoMarcar.Error("No pertenece a este curso o ciclo")
        }
        if (yaMarcado) {
            return ResultadoMarcar.Advertencia("Ya estaba marcado en este curso")
        }
        return null // Válido para registrar
    }
}
