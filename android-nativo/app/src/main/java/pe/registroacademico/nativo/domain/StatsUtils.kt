package pe.registroacademico.nativo.domain

import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.Justificacion
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

data class ResumenDia(
    val activos: Int,
    val presentes: Int,
    val tardes: Int,
    val puntuales: Int,
    val ausentes: Int,
    val pct: Int
)

data class SerieDiariaPunto(
    val fecha: String,
    val presentes: Int,
    val tardes: Int,
    val pct: Int
)

data class PorGradoItem(
    val key: String,
    val nivel: String,
    val grado: String,
    val total: Int,
    val presentes: Int,
    val pct: Int
)

data class PorNivelItem(
    val nivel: String,
    val total: Int
)

data class BajaAsistenciaItem(
    val alumno: Alumno,
    val presentes: Int,
    val dias: Int,
    val pct: Int
)

data class ResumenAlumno(
    val presentes: Int,
    val tardes: Int,
    val ausentes: Int,
    val pct: Int
)

data class MatrizFila(
    val alumno: Alumno,
    val celdas: Map<String, String>,
    val p: Int,
    val t: Int,
    val j: Int,
    val f: Int,
    val pct: Int,
    val pctJust: Int
)

data class MatrizResumen(
    val alumnos: Int,
    val dias: Int,
    val pct: Int
)

data class MatrizAsistenciaResult(
    val dias: List<String>,
    val filas: List<MatrizFila>,
    val resumen: MatrizResumen
)

object LimitesConfig {
    var general: String? = null
    var porNivel: Map<String, String> = emptyMap()

    fun configurar(tabla: TablaLimites?) {
        general = tabla?.general
        porNivel = tabla?.porNivel ?: emptyMap()
    }

    fun configurar(gen: String?, niveles: Map<String, String> = emptyMap()) {
        general = gen
        porNivel = niveles
    }

    fun limpiar() {
        general = null
        porNivel = emptyMap()
    }
}

object StatsUtils {

    fun configurarLimites(general: String?, porNivel: Map<String, String> = emptyMap()) {
        LimitesConfig.configurar(general, porNivel)
    }

    fun esTardanza(hora: String?, limite: String, nivel: String? = null): Boolean {
        if (hora.isNullOrBlank()) return false
        val lim = (nivel?.let { LimitesConfig.porNivel[it] }) ?: limite
        val h = if (hora.length >= 5) hora.substring(0, 5) else hora
        return h > lim
    }

    fun decidirAccion(
        regHora: String?,
        regHoraSalida: String?,
        ahoraHHMM: String,
        minPermanencia: Int = 45
    ): String {
        if (regHora == null) return "entrada"
        if (!regHoraSalida.isNullOrBlank()) return "dup_salida"

        fun toMinutes(h: String): Int {
            val parts = h.split(":")
            val a = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val b = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return a * 60 + b
        }

        val diff = toMinutes(ahoraHHMM) - toMinutes(regHora)
        return if (diff >= minPermanencia) "salida" else "ya_ingreso"
    }

    fun decidirAccion(
        reg: Asistencia?,
        ahoraHHMM: String,
        minPermanencia: Int = 45
    ): String {
        if (reg == null) return "entrada"
        return decidirAccion(reg.hora, reg.horaSalida, ahoraHHMM, minPermanencia)
    }

    fun resumenDia(alumnos: List<Alumno>, asistencias: List<Asistencia>, limite: String): ResumenDia {
        val activos = alumnos.filter { it.estado == "ACTIVO" && it.aprobado != false }
        val ids = activos.map { it.id }.toSet()
        val delDia = asistencias.filter { ids.contains(it.alumnoId) }
        val nivelDe = activos.associate { it.id to it.nivel }
        val presentes = delDia.size
        val tardes = delDia.count { esTardanza(it.hora, limite, nivelDe[it.alumnoId]) }
        val puntuales = presentes - tardes
        val ausentes = max(0, activos.size - presentes)
        val p = StringUtils.pct(presentes, activos.size)
        return ResumenDia(
            activos = activos.size,
            presentes = presentes,
            tardes = tardes,
            puntuales = puntuales,
            ausentes = ausentes,
            pct = p
        )
    }

    fun serieDiaria(
        dias: List<String>,
        asistencias: List<Asistencia>,
        totalActivos: Int,
        limite: String,
        nivelDe: Map<String, String> = emptyMap()
    ): List<SerieDiariaPunto> {
        return dias.map { fecha ->
            val del = asistencias.filter { it.fecha == fecha }
            val tardes = del.count { esTardanza(it.hora, limite, nivelDe[it.alumnoId]) }
            SerieDiariaPunto(
                fecha = fecha,
                presentes = del.size,
                tardes = tardes,
                pct = StringUtils.pct(del.size, totalActivos)
            )
        }
    }

    private fun compareAlphanumeric(a: String, b: String): Int {
        val re = Regex("""(\d+|\D+)""")
        val aChunks = re.findAll(a).map { it.value }.toList()
        val bChunks = re.findAll(b).map { it.value }.toList()
        val len = minOf(aChunks.size, bChunks.size)
        for (i in 0 until len) {
            val ac = aChunks[i]
            val bc = bChunks[i]
            val an = ac.toLongOrNull()
            val bn = bc.toLongOrNull()
            val cmp = if (an != null && bn != null) {
                an.compareTo(bn)
            } else {
                ac.compareTo(bc, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return aChunks.size.compareTo(bChunks.size)
    }

    fun porGrado(alumnos: List<Alumno>, asistencias: List<Asistencia>): List<PorGradoItem> {
        val presentesSet = asistencias.map { it.alumnoId }.toSet()
        class Acc(val key: String, val nivel: String, val grado: String, var total: Int = 0, var presentes: Int = 0)
        val map = linkedMapOf<String, Acc>()
        alumnos.filter { it.estado == "ACTIVO" && it.aprobado != false }.forEach { a ->
            val key = CiclosUtils.etiquetaCiclo(a.nivel, a.grado)
            val acc = map.getOrPut(key) { Acc(key, a.nivel, a.grado) }
            acc.total++
            if (presentesSet.contains(a.id)) acc.presentes++
        }
        return map.values.map { acc ->
            PorGradoItem(
                key = acc.key,
                nivel = acc.nivel,
                grado = acc.grado,
                total = acc.total,
                presentes = acc.presentes,
                pct = StringUtils.pct(acc.presentes, acc.total)
            )
        }.sortedWith(Comparator { a: PorGradoItem, b: PorGradoItem ->
            compareAlphanumeric(a.key, b.key)
        })
    }

    fun porNivel(alumnos: List<Alumno>, niveles: List<String>): List<PorNivelItem> {
        return niveles.map { n ->
            val total = alumnos.count { it.nivel == n && it.estado == "ACTIVO" && it.aprobado != false }
            PorNivelItem(nivel = n, total = total)
        }
    }

    fun bajaAsistencia(
        alumnos: List<Alumno>,
        asistencias: List<Asistencia>,
        umbral: Int,
        limiteResultados: Int = 8
    ): List<BajaAsistenciaItem> {
        val diasClase = asistencias.map { it.fecha }.toSet()
        if (diasClase.isEmpty()) return emptyList()
        val conteo = mutableMapOf<String, Int>()
        asistencias.forEach { conteo[it.alumnoId] = (conteo[it.alumnoId] ?: 0) + 1 }
        return alumnos
            .filter { it.estado == "ACTIVO" && it.aprobado != false }
            .map { a ->
                val presentes = conteo[a.id] ?: 0
                val p = StringUtils.pct(presentes, diasClase.size)
                BajaAsistenciaItem(alumno = a, presentes = presentes, dias = diasClase.size, pct = p)
            }
            .filter { it.pct < umbral }
            .sortedWith(compareBy<BajaAsistenciaItem> { it.pct }.thenBy { it.alumno.nombre })
            .take(limiteResultados)
    }

    fun resumenAlumno(
        historial: List<Asistencia>,
        diasClase: Int,
        limite: String,
        nivel: String? = null
    ): ResumenAlumno {
        val presentes = historial.size
        val tardes = historial.count { esTardanza(it.hora, limite, nivel) }
        return ResumenAlumno(
            presentes = presentes,
            tardes = tardes,
            ausentes = max(0, diasClase - presentes),
            pct = StringUtils.pct(presentes, diasClase)
        )
    }

    fun matrizAsistencia(
        alumnos: List<Alumno>,
        asistencias: List<Asistencia>,
        justificaciones: List<Justificacion>,
        dias: List<String>,
        limite: String
    ): MatrizAsistenciaResult {
        val ids = alumnos.map { it.id }.toSet()
        val asis = mutableMapOf<String, String>()
        val just = mutableMapOf<String, String>()

        asistencias.filter { ids.contains(it.alumnoId) }.forEach {
            asis["${it.alumnoId}|${it.fecha}"] = it.hora
        }
        justificaciones.filter { ids.contains(it.alumnoId) }.forEach {
            just["${it.alumnoId}|${it.fecha}"] = it.tipo
        }

        val conRegistro = (asis.keys + just.keys).map { it.substringAfter("|") }.toSet()
        val diasClase = dias.filter { conRegistro.contains(it) }

        val filas = alumnos.map { alumno ->
            val celdas = mutableMapOf<String, String>()
            var p = 0
            var t = 0
            var j = 0
            var f = 0
            diasClase.forEach { d ->
                val hora = asis["${alumno.id}|$d"]
                if (hora != null) {
                    if (esTardanza(hora, limite, alumno.nivel)) {
                        celdas[d] = "T"
                        t++
                    } else {
                        celdas[d] = "P"
                    }
                    // La tardanza cuenta como presente: p++ siempre que hay hora
                    p++
                } else if (just.containsKey("${alumno.id}|$d")) {
                    celdas[d] = "J"
                    j++
                } else {
                    celdas[d] = "F"
                    f++
                }
            }
            MatrizFila(
                alumno = alumno,
                celdas = celdas,
                p = p,
                t = t,
                j = j,
                f = f,
                pct = StringUtils.pct(p, diasClase.size),
                pctJust = StringUtils.pct(p + j, diasClase.size)
            )
        }

        val total = filas.sumOf { it.p }
        val posibles = filas.size * diasClase.size
        val resumen = MatrizResumen(
            alumnos = filas.size,
            dias = diasClase.size,
            pct = StringUtils.pct(total, posibles)
        )

        return MatrizAsistenciaResult(
            dias = diasClase,
            filas = filas,
            resumen = resumen
        )
    }

    fun numeroWhatsApp(tel: String?, paisPorDefecto: String = "51"): String {
        val raw = tel.orEmpty().trim()
        val d = raw.replace(Regex("[^0-9]"), "")
        if (d.isEmpty()) return ""
        if (raw.startsWith("+")) return d
        if (d.length == 9 && d.startsWith("9")) return paisPorDefecto + d
        return d
    }

    fun enlaceWhatsApp(tel: String?, texto: String): String {
        val n = numeroWhatsApp(tel)
        if (n.isEmpty()) return ""
        val encoded = URLEncoder.encode(texto, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "https://wa.me/$n?text=$encoded"
    }

    fun mensajeAviso(plantilla: String, datos: Map<String, String>): String {
        return Regex("""\{(\w+)\}""").replace(plantilla) { mr ->
            val k = mr.groupValues[1]
            datos[k] ?: mr.value
        }
    }

    fun perteneceACurso(
        alumnoNivel: String,
        alumnoGrado: String?,
        cursoNivel: String,
        cursoGrado: String?,
        matriculadoManual: Boolean = false
    ): Boolean {
        return matriculadoManual || (alumnoNivel == cursoNivel && (cursoGrado.isNullOrBlank() || alumnoGrado == cursoGrado))
    }
}
