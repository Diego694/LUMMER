package pe.registroacademico.nativo.domain.estudiante

import kotlin.math.round

/**
 * Token parseado de asistencia por curso ("LQ.<sesion hex32>.<ventana>.<firma 12 hex>").
 * Mismo formato que escanear.js y supabase/migrations/019_qr_asistencia_curso.sql.
 */
data class QrCursoToken(
    val sesionHex: String,
    val ventana: Long,
    val firma: String
)

/**
 * Parsea el texto escaneado de un QR de asistencia a curso.
 * Devuelve null si no cumple exactamente la estructura LQ.<hex32>.<1-12 dígitos>.<12 hex>.
 */
fun parsearTokenQrCurso(texto: String?): QrCursoToken? {
    if (texto.isNullOrBlank()) return null
    val partes = texto.trim().split(".")
    if (partes.size != 4) return null
    if (partes[0] != "LQ") return null

    val sesion = partes[1]
    if (!Regex("^[0-9a-fA-F]{32}$").matches(sesion)) return null

    val ventanaStr = partes[2]
    if (!Regex("^[0-9]{1,12}$").matches(ventanaStr)) return null
    val ventana = ventanaStr.toLongOrNull() ?: return null

    val firma = partes[3]
    if (!Regex("^[0-9a-fA-F]{12}$").matches(firma)) return null

    return QrCursoToken(
        sesionHex = sesion.lowercase(),
        ventana = ventana,
        firma = firma.lowercase()
    )
}

/**
 * Comprueba si una hora (HH:MM o HH:MM:SS) se considera tardanza con respecto al límite.
 */
fun esTardanzaIngreso(hora: String?, limite: String = "08:00"): Boolean {
    if (hora.isNullOrBlank()) return false
    val h = if (hora.length >= 5) hora.substring(0, 5) else hora
    val lim = if (limite.length >= 5) limite.substring(0, 5) else limite
    return h > lim
}

/**
 * Resumen de asistencia para el estudiante.
 */
data class ResumenAsistenciaEstudiante(
    val presentes: Int,
    val tardanzas: Int,
    val faltas: Int,
    val porcentaje: Int
)

/**
 * Calcula el resumen de asistencia a partir de las horas registradas y el total de días de clase.
 */
fun calcularResumenAsistencia(
    horasAsistencia: List<String?>,
    diasClase: Int,
    horaLimite: String = "08:00"
): ResumenAsistenciaEstudiante {
    val presentes = horasAsistencia.size
    val tardanzas = horasAsistencia.count { esTardanzaIngreso(it, horaLimite) }
    val faltas = maxOf(0, diasClase - presentes)
    val pct = if (diasClase > 0) round((presentes.toDouble() / diasClase) * 100.0).toInt() else 0
    return ResumenAsistenciaEstudiante(
        presentes = presentes,
        tardanzas = tardanzas,
        faltas = faltas,
        porcentaje = pct
    )
}

/**
 * Sobrecarga de cálculo de resumen con conteos ya sumarizados.
 */
fun calcularResumenAsistencia(
    presentes: Int,
    tardanzas: Int,
    diasClase: Int
): ResumenAsistenciaEstudiante {
    val faltas = maxOf(0, diasClase - presentes)
    val pct = if (diasClase > 0) round((presentes.toDouble() / diasClase) * 100.0).toInt() else 0
    return ResumenAsistenciaEstudiante(
        presentes = presentes,
        tardanzas = tardanzas,
        faltas = faltas,
        porcentaje = pct
    )
}

/**
 * Modelo para evaluar actividades calificadas.
 */
data class NotaEvaluada(
    val nota: Double?,
    val puntajeMax: Double
)

/**
 * Calcula el promedio sobre escala vigesimal (0 a 20) considerando solo las actividades ya calificadas.
 * Equivalente exacto a Math.round((suma / maximo) * 2000) / 100 en api-aula.js / aula.js.
 */
fun calcularPromedioNotas(evaluaciones: List<NotaEvaluada>): Double? {
    val calificadas = evaluaciones.filter { it.nota != null }
    if (calificadas.isEmpty()) return null

    val sumaNotas = calificadas.sumOf { it.nota ?: 0.0 }
    val sumaMaximos = calificadas.sumOf { it.puntajeMax }
    if (sumaMaximos <= 0.0) return null

    return round((sumaNotas / sumaMaximos) * 2000.0) / 100.0
}

/**
 * Sobrecarga que recibe pares (nota?, puntajeMax).
 */
fun calcularPromedioSimple(notasConMaximo: List<Pair<Double?, Double>>): Double? {
    return calcularPromedioNotas(notasConMaximo.map { NotaEvaluada(it.first, it.second) })
}
