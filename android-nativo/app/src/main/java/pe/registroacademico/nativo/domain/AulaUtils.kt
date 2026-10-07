package pe.registroacademico.nativo.domain

import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoEntrega
import pe.registroacademico.nativo.data.model.FilaLibroNotas
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

object AulaUtils {

    const val BUCKET_AULA = "cursos"
    const val MAX_ARCHIVO_AULA = 10L * 1024L * 1024L // 10 MB = 10485760 bytes
    val EXT_PERMITIDAS = setOf(
        "pdf", "txt", "zip", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "jpg", "jpeg", "png", "webp"
    )
    const val PERIODOS_MAX = 8

    private val ZONE_LIMA: ZoneId = ZoneId.of("America/Lima")
    private val FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd 'de' MMM, HH:mm", Locale.forLanguageTag("es-PE"))

    /**
     * Valida el archivo antes de subirlo. Devuelve el mensaje de error o null si es válido.
     */
    fun validarArchivoAula(nombre: String, sizeBytes: Long): String? {
        val ext = nombre.substringAfterLast('.', "").lowercase().trim()
        if (ext !in EXT_PERMITIDAS) {
            val etiquetaExt = if (ext.isEmpty()) "?" else ext
            return "Tipo de archivo no permitido (.$etiquetaExt). Usa PDF, Word, PowerPoint, Excel, imágenes, TXT o ZIP."
        }
        if (sizeBytes > MAX_ARCHIVO_AULA) {
            val mb = String.format(Locale.US, "%.1f", sizeBytes / 1048576.0)
            return "El archivo pesa $mb MB; el máximo es 10 MB."
        }
        if (sizeBytes <= 0L) {
            return "El archivo está vacío."
        }
        return null
    }

    /**
     * Ruta segura dentro del bucket: <colegio>/<curso>/<id>-<nombre-limpio>.<ext>
     */
    fun rutaArchivoAula(
        colegioId: String,
        cursoId: String,
        nombre: String,
        uid: String = UUID.randomUUID().toString()
    ): String {
        val ext = nombre.substringAfterLast('.', "bin").lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
        val baseRaw = nombre.substringBeforeLast('.', nombre)
        val baseNorm = Normalizer.normalize(baseRaw, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-zA-Z0-9]+".toRegex(), "-")
            .trim('-')
            .take(50)
            .ifEmpty { "archivo" }
        return "$colegioId/$cursoId/$uid-$baseNorm.$ext"
    }

    /**
     * Ruta del archivo de una entrega: <colegio>/<curso>/entregas/<user_id>/<id>-<nombre>.<ext>
     */
    fun rutaEntrega(
        colegioId: String,
        cursoId: String,
        userId: String,
        nombre: String,
        uid: String = UUID.randomUUID().toString()
    ): String {
        val archivoParte = rutaArchivoAula(colegioId, cursoId, nombre, uid).substringAfterLast('/')
        return "$colegioId/$cursoId/entregas/$userId/$archivoParte"
    }

    /**
     * Valida la nota contra el puntaje máximo. Devuelve el mensaje de error o null.
     */
    fun validarNota(nota: Double?, max: Double = 20.0): String? {
        if (nota == null || !nota.isFinite()) return "Escribe una nota válida."
        if (nota < 0.0 || nota > max) {
            val maxStr = if (max % 1.0 == 0.0) max.toInt().toString() else max.toString()
            return "La nota debe estar entre 0 y $maxStr."
        }
        return null
    }

    /**
     * Periodo de una actividad (las anteriores a la migración 015 cuentan como periodo 1).
     */
    fun periodoDe(periodo: Int?): Int {
        return if (periodo != null && periodo in 1..PERIODOS_MAX) periodo else 1
    }

    /**
     * Periodos distintos que tiene el curso, ordenados de menor a mayor.
     */
    fun periodosDe(actividades: List<CursoActividad>): List<Int> {
        return actividades.map { periodoDe(it.periodo) }.distinct().sorted()
    }

    /**
     * Estudiantes que cursan un curso: activos y aprobados de su carrera y ciclo, o matriculados manualmente.
     */
    fun alumnosDelCurso(
        alumnos: List<Alumno>,
        curso: Curso,
        manualesIds: Set<String> = emptySet()
    ): List<Alumno> {
        return alumnos
            .filter { a ->
                a.estado == "ACTIVO" &&
                    a.aprobado != false &&
                    StatsUtils.perteneceACurso(
                        alumnoNivel = a.nivel,
                        alumnoGrado = a.grado,
                        cursoNivel = curso.nivel,
                        cursoGrado = curso.grado,
                        matriculadoManual = a.id in manualesIds
                    )
            }
            .sortedWith { x, y -> x.nombre.compareTo(y.nombre, ignoreCase = true) }
    }

    /**
     * Libro de notas de un curso. El promedio se calcula sobre 20 con las actividades YA calificadas de cada estudiante
     * (suma de notas / suma de puntajes máximos de esas actividades), así una actividad pendiente no lo hunde.
     */
    fun libroNotas(
        alumnos: List<Alumno>,
        actividades: List<CursoActividad>,
        entregas: List<CursoEntrega>
    ): List<FilaLibroNotas> {
        return alumnos.map { alumno ->
            val notas = mutableMapOf<String, Double?>()
            var suma = 0.0
            var maximo = 0.0

            for (a in actividades) {
                val actId = a.id.orEmpty()
                val e = entregas.find { it.actividadId == actId && it.alumnoId == alumno.id }
                val nota = e?.nota
                notas[actId] = nota
                if (nota != null) {
                    suma += nota
                    maximo += a.puntajeMax
                }
            }

            val promedio = if (maximo > 0.0) {
                (Math.round((suma / maximo) * 2000.0)) / 100.0
            } else {
                null
            }

            FilaLibroNotas(
                alumno = alumno,
                notas = notas,
                promedio = promedio
            )
        }
    }

    /**
     * Formateador legible de bytes a KB o MB.
     */
    fun formatearKb(bytes: Long?): String {
        if (bytes == null) return ""
        return if (bytes >= 1048576L) {
            String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        } else {
            "${maxOf(1L, (bytes / 1024.0).roundToLong())} KB"
        }
    }

    /**
     * Formatea fecha límite en zona horaria America/Lima.
     */
    fun formatearFechaLimite(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = Instant.parse(isoString)
            instant.atZone(ZONE_LIMA).format(FORMATO_FECHA_HORA)
        } catch (_: Exception) {
            isoString.take(16).replace('T', ' ')
        }
    }

    /**
     * Formateador de notas numéricas: muestra entero sin decimal o hasta 2 decimales.
     */
    fun formatearNota(nota: Double?): String {
        if (nota == null) return "—"
        return if (nota % 1.0 == 0.0) {
            nota.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", nota)
        }
    }

    /**
     * Genera el contenido CSV del libro de notas con prefijo BOM (\uFEFF) para Excel.
     */
    fun generarCsvNotas(
        actividades: List<CursoActividad>,
        filas: List<FilaLibroNotas>
    ): String {
        val cel = { t: String -> "\"${t.replace("\"", "\"\"")}\"" }
        val vacio = { n: Double? -> if (n == null) "" else formatearNota(n) }

        val encabezados = listOf("Estudiante") +
            actividades.map { "${it.titulo} (/${if (it.puntajeMax % 1.0 == 0.0) it.puntajeMax.toInt() else it.puntajeMax})" } +
            listOf("Promedio (/20)")

        val csvFilas = mutableListOf<String>()
        csvFilas.add(encabezados.map(cel).joinToString(","))

        for (f in filas) {
            val linea = listOf(cel(f.alumno.nombre)) +
                actividades.map { cel(vacio(f.notas[it.id.orEmpty()])) } +
                listOf(cel(vacio(f.promedio)))
            csvFilas.add(linea.joinToString(","))
        }

        return "\uFEFF" + csvFilas.joinToString("\r\n")
    }
}
