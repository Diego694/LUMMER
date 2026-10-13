package pe.registroacademico.nativo.ui.estudiante

import pe.registroacademico.nativo.data.estudiante.ActividadEstudiante
import pe.registroacademico.nativo.data.estudiante.CursoEstudiante
import pe.registroacademico.nativo.data.estudiante.EntregaEstudiante
import pe.registroacademico.nativo.data.estudiante.NotaEstudiante
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Modelo de agrupación para la lista de cursos del estudiante.
 */
data class GrupoCursosEstudiante(
    val etiqueta: String,
    val nivel: String,
    val grado: String?,
    val cursos: List<CursoEstudiante>
)

/**
 * Categorías de estado para una actividad del estudiante.
 */
enum class EstadoEntregaTipo {
    CALIFICADA,
    ENTREGADA,
    VENCIDA,
    PENDIENTE
}

/**
 * Información visual y de texto del estado de entrega de una actividad.
 */
data class InfoEstadoEntrega(
    val tipo: EstadoEntregaTipo,
    val etiqueta: String,
    val tipoBadge: TipoEstadoBadge,
    val esTardia: Boolean = false,
    val calificada: Boolean = false,
    val notaTexto: String? = null
)

/**
 * Utilidades puras para la lógica de cursos, aula, asistencia y notas del estudiante.
 * Libre de dependencias directas de Android para ejecución confiable en JVM unit tests.
 */
object EstAulaUtils {

    const val MAX_ARCHIVO_BYTES = 10L * 1024L * 1024L // 10 MB

    val EXTENSIONES_PERMITIDAS = setOf(
        "pdf", "txt", "zip", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "jpg", "jpeg", "png", "webp"
    )

    private val ZONE_LIMA = ZoneId.of("America/Lima")
    private val FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd 'de' MMM, HH:mm", Locale.forLanguageTag("es-PE"))

    /**
     * Agrupa cursos por carrera (nivel) y ciclo/salón (grado) similar a agruparCursos de la web.
     * Cursos con ciclo van primero ordenados por ciclo (I a VI); sin ciclo van al final.
     * Cursos dentro de cada grupo se ordenan alfabéticamente por nombre.
     */
    fun agruparCursos(cursos: List<CursoEstudiante>): List<GrupoCursosEstudiante> {
        if (cursos.isEmpty()) return emptyList()

        // Agrupar por nivel y grado
        val mapa = LinkedHashMap<Pair<String, String?>, MutableList<CursoEstudiante>>()
        for (c in cursos) {
            val clave = Pair(c.nivel.trim(), c.grado?.trim()?.ifBlank { null })
            mapa.getOrPut(clave) { mutableListOf() }.add(c)
        }

        return mapa.entries
            .sortedWith { e1, e2 ->
                val (n1, g1) = e1.key
                val (n2, g2) = e2.key

                val compNivel = n1.compareTo(n2, ignoreCase = true)
                if (compNivel != 0) return@sortedWith compNivel

                // Sin grado va al final de su nivel
                if (g1 == null && g2 != null) return@sortedWith 1
                if (g1 != null && g2 == null) return@sortedWith -1
                if (g1 != null && g2 != null) {
                    val compCiclo = CiclosUtils.compararCiclos(g1, g2)
                    if (compCiclo != 0) return@sortedWith compCiclo
                }
                0
            }
            .map { (clave, lista) ->
                val (nivel, grado) = clave
                val etiqueta = if (grado.isNullOrBlank()) {
                    "$nivel · Todos los ciclos"
                } else {
                    CiclosUtils.etiquetaCiclo(nivel, grado)
                }
                GrupoCursosEstudiante(
                    etiqueta = etiqueta,
                    nivel = nivel,
                    grado = grado,
                    cursos = lista.sortedWith { a, b -> a.nombre.compareTo(b.nombre, ignoreCase = true) }
                )
            }
    }

    /**
     * Determina el estado de una entrega para una actividad dada.
     */
    fun determinarEstadoEntrega(
        actividad: ActividadEstudiante,
        entrega: EntregaEstudiante?,
        ahoraEpochMs: Long = System.currentTimeMillis()
    ): InfoEstadoEntrega {
        if (entrega?.nota != null) {
            val notaStr = formatearNota(entrega.nota)
            val maxStr = formatearNota(actividad.puntajeMax)
            val badgeTipo = if (entrega.nota >= (actividad.puntajeMax * 0.525)) {
                TipoEstadoBadge.VERDE
            } else {
                TipoEstadoBadge.ROJO
            }
            return InfoEstadoEntrega(
                tipo = EstadoEntregaTipo.CALIFICADA,
                etiqueta = "Nota: $notaStr / $maxStr",
                tipoBadge = badgeTipo,
                esTardia = entrega.tardia,
                calificada = true,
                notaTexto = "$notaStr / $maxStr"
            )
        }

        if (entrega != null) {
            val texto = if (entrega.tardia) "Entregada (Tardía)" else "Entregada"
            val badge = if (entrega.tardia) TipoEstadoBadge.AMBAR else TipoEstadoBadge.AZUL
            return InfoEstadoEntrega(
                tipo = EstadoEntregaTipo.ENTREGADA,
                etiqueta = texto,
                tipoBadge = badge,
                esTardia = entrega.tardia,
                calificada = false,
                notaTexto = null
            )
        }

        // Sin entrega: verificar si está vencida
        val vencida = esFechaVencida(actividad.fechaLimite, ahoraEpochMs)
        return if (vencida) {
            InfoEstadoEntrega(
                tipo = EstadoEntregaTipo.VENCIDA,
                etiqueta = "Vencida sin entregar",
                tipoBadge = TipoEstadoBadge.ROJO,
                esTardia = false,
                calificada = false,
                notaTexto = null
            )
        } else {
            InfoEstadoEntrega(
                tipo = EstadoEntregaTipo.PENDIENTE,
                etiqueta = "Por entregar",
                tipoBadge = TipoEstadoBadge.AMBAR,
                esTardia = false,
                calificada = false,
                notaTexto = null
            )
        }
    }

    /**
     * Comprueba si una fecha ISO está antes del instante actual.
     */
    fun esFechaVencida(fechaLimiteIso: String?, ahoraEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (fechaLimiteIso.isNullOrBlank()) return false
        return try {
            Instant.parse(fechaLimiteIso).toEpochMilli() < ahoraEpochMs
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Formateador de notas numéricas: sin decimales innecesarios, hasta 2 decimales.
     */
    fun formatearNota(nota: Double?): String {
        if (nota == null) return "—"
        return if (nota % 1.0 == 0.0) {
            nota.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", nota).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Formatea un porcentaje numérico como "85%".
     */
    fun formatearPorcentaje(porcentaje: Double?): String {
        if (porcentaje == null) return "0%"
        return "${porcentaje.roundToInt()}%"
    }

    fun formatearPorcentaje(porcentaje: Int?): String {
        return "${porcentaje ?: 0}%"
    }

    /**
     * Calcula el promedio sobre 20 de una lista de notas del estudiante considerando solo las ya calificadas.
     */
    fun calcularPromedioCurso(notas: List<NotaEstudiante>): Double? {
        val evaluadas = notas.filter { it.nota != null }
        if (evaluadas.isEmpty()) return null

        var suma = 0.0
        var maximo = 0.0
        for (n in evaluadas) {
            val nota = n.nota ?: continue
            suma += nota
            maximo += n.puntajeMax
        }

        return if (maximo > 0.0) {
            (Math.round((suma / maximo) * 2000.0)) / 100.0
        } else {
            null
        }
    }

    /**
     * Valida el archivo a entregar según extensión y tamaño (máx. 10 MB).
     */
    fun validarArchivo(nombre: String, tamanoBytes: Long): String? {
        val ext = nombre.substringAfterLast('.', "").lowercase().trim()
        if (ext !in EXTENSIONES_PERMITIDAS) {
            val etiquetaExt = if (ext.isEmpty()) "?" else ext
            return "Tipo de archivo no permitido (.$etiquetaExt). Usa PDF, Word, PowerPoint, Excel, imágenes, TXT o ZIP."
        }
        if (tamanoBytes > MAX_ARCHIVO_BYTES) {
            val mb = String.format(Locale.US, "%.1f", tamanoBytes / (1024.0 * 1024.0))
            return "El archivo pesa $mb MB; el máximo es 10 MB."
        }
        if (tamanoBytes <= 0L) {
            return "El archivo está vacío."
        }
        return null
    }

    /**
     * Formatea tamaño en bytes a KB o MB legible.
     */
    fun formatearTamanoArchivo(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return ""
        return if (bytes >= 1024 * 1024) {
            String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        } else {
            "${maxOf(1L, (bytes / 1024.0).roundToLong())} KB"
        }
    }

    /**
     * Formatea fecha ISO para mostrar fecha y hora en zona horaria America/Lima.
     */
    fun formatearFechaIso(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "Sin fecha límite"
        return try {
            val instant = Instant.parse(isoString)
            instant.atZone(ZONE_LIMA).format(FORMATO_FECHA_HORA)
        } catch (_: Exception) {
            isoString.take(16).replace('T', ' ')
        }
    }
}
