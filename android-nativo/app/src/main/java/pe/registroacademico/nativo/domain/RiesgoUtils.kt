package pe.registroacademico.nativo.domain

import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.DiaCalendario
import pe.registroacademico.nativo.data.model.Justificacion
import kotlin.math.max
import kotlin.math.roundToInt

data class RiesgoAlumno(
    val alumno: Alumno,
    val dias: Int,
    val faltas: Int,
    val justificadas: Int,
    val pctFaltas: Int,
    val nivel: String // "critico" | "alerta" | "ok"
)

object RiesgoUtils {

    fun calcularRiesgo(
        alumnos: List<Alumno>,
        asistencias: List<Asistencia>,
        justificaciones: List<Justificacion> = emptyList(),
        noLectivos: Map<String, DiaCalendario> = emptyMap(),
        desde: String,
        hasta: String,
        limite: Int = 30,
        aviso: Int? = null,
        hoy: String? = null
    ): List<RiesgoAlumno> {
        val umbralAviso = aviso ?: (limite * 0.7).roundToInt()
        var dias = CalendarioUtils.diasLectivos(desde, hasta, noLectivos)
        val asis = asistencias.map { "${it.alumnoId}|${it.fecha}" }.toSet()
        val just = justificaciones.map { "${it.alumnoId}|${it.fecha}" }.toSet()

        if (hoy != null && dias.isNotEmpty() && dias.last() == hoy) {
            dias = dias.dropLast(1)
        }
        val total = dias.size
        if (total == 0) return emptyList()

        return alumnos
            .filter { it.estado == "ACTIVO" && it.aprobado != false }
            .map { alumno ->
                var faltas = 0
                var justificadas = 0
                for (d in dias) {
                    val k = "${alumno.id}|$d"
                    if (asis.contains(k)) continue
                    if (just.contains(k)) {
                        justificadas++
                    } else {
                        faltas++
                    }
                }
                val pctFaltas = StringUtils.pct(faltas, total)
                val nivel = when {
                    pctFaltas >= limite -> "critico"
                    pctFaltas >= umbralAviso -> "alerta"
                    else -> "ok"
                }
                RiesgoAlumno(
                    alumno = alumno,
                    dias = total,
                    faltas = faltas,
                    justificadas = justificadas,
                    pctFaltas = pctFaltas,
                    nivel = nivel
                )
            }
            .sortedWith(
                compareByDescending<RiesgoAlumno> { it.pctFaltas }
                    .thenBy { it.alumno.nombre }
            )
    }

    fun faltasRestantes(r: RiesgoAlumno, limite: Int = 30): Int {
        val maxFaltas = ((limite.toDouble() / 100.0) * r.dias).toInt()
        return max(0, maxFaltas - r.faltas)
    }
}
