package pe.registroacademico.nativo.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    const val ZONA_HORARIA = "America/Lima"
    val ZONE_ID: ZoneId = ZoneId.of(ZONA_HORARIA)

    private val FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private var anclaServidorMs: Long? = null
    private var anclaNanoTime: Long = 0L
    private var desfase: Long = 0L

    fun sincronizarReloj(servidorMs: Long) {
        anclaServidorMs = servidorMs
        anclaNanoTime = System.nanoTime()
        desfase = servidorMs - System.currentTimeMillis()
    }

    fun desfaseReloj(): Long = desfase

    fun ahoraMs(): Long {
        val ancla = anclaServidorMs
        return if (ancla != null) {
            val transcurridoMs = (System.nanoTime() - anclaNanoTime) / 1_000_000
            ancla + transcurridoMs
        } else {
            System.currentTimeMillis() + desfase
        }
    }

    fun ahoraZoned(epochMs: Long = ahoraMs()): ZonedDateTime {
        return Instant.ofEpochMilli(epochMs).atZone(ZONE_ID)
    }

    fun fechaZona(epochMs: Long = ahoraMs()): String {
        return ahoraZoned(epochMs).format(FORMATO_FECHA)
    }

    fun horaZona(epochMs: Long = ahoraMs()): String {
        return ahoraZoned(epochMs).format(FORMATO_HORA)
    }

    fun todayStr(): String = fechaZona(ahoraMs())

    fun nowHHMM(): String = horaZona(ahoraMs())

    fun parseDate(str: String): LocalDate {
        return LocalDate.parse(str, FORMATO_FECHA)
    }

    fun dateStr(date: LocalDate): String {
        return date.format(FORMATO_FECHA)
    }

    fun addDays(str: String, n: Int): String {
        val d = parseDate(str).plusDays(n.toLong())
        return dateStr(d)
    }

    fun isWeekend(str: String): Boolean {
        val day = parseDate(str).dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
    }

    fun lastWeekdays(n: Int, hasta: String = todayStr()): List<String> {
        val out = mutableListOf<String>()
        var cur = hasta
        while (out.size < n) {
            if (!isWeekend(cur)) {
                out.add(0, cur)
            }
            cur = addDays(cur, -1)
        }
        return out
    }

    fun diasHabilesDelMes(
        mes: String,
        hasta: String = todayStr(),
        noLectivosFechas: Set<String> = emptySet()
    ): List<String> {
        val partes = mes.split("-").map { it.toInt() }
        val anio = partes[0]
        val mesNum = partes[1]
        val primerDia = LocalDate.of(anio, mesNum, 1)
        val ultimoDiaNum = primerDia.lengthOfMonth()

        val out = mutableListOf<String>()
        for (d in 1..ultimoDiaNum) {
            val f = String.format(Locale.US, "%04d-%02d-%02d", anio, mesNum, d)
            if (f > hasta) break
            if (!isWeekend(f) && f !in noLectivosFechas) {
                out.add(f)
            }
        }
        return out
    }

    fun fmtDate(str: String): String {
        val ld = parseDate(str)
        val formatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-PE"))
        return ld.format(formatter)
    }

    fun fmtDay(str: String): String {
        val ld = parseDate(str)
        val formatter = DateTimeFormatter.ofPattern("EEE dd", Locale.forLanguageTag("es-PE"))
        return ld.format(formatter)
    }

    fun greeting(epochMs: Long = ahoraMs()): String {
        val h = ahoraZoned(epochMs).hour
        return when {
            h < 12 -> "Buenos días"
            h < 19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    fun esErrorRed(e: Throwable): Boolean {
        val msg = e.message ?: ""
        if (msg.contains("code") || msg.contains("23505") || msg.contains("42501")) return false
        return e is java.io.IOException ||
            e is java.net.SocketException ||
            e is java.net.UnknownHostException ||
            Regex("""fetch|network|abort|timeout|timed out|load failed|conexi""", RegexOption.IGNORE_CASE).containsMatchIn(msg)
    }

    fun esErrorSesion(e: Throwable): Boolean {
        val msg = e.message ?: ""
        return Regex("""jwt|expired|not authenticated|no autenticado|invalid token|401""", RegexOption.IGNORE_CASE).containsMatchIn(msg)
    }
}
