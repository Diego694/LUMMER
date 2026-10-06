package pe.registroacademico.nativo.domain

import pe.registroacademico.nativo.data.model.DiaCalendario
import pe.registroacademico.nativo.data.model.Horario
import java.time.LocalDate
import java.util.Locale

data class TablaLimites(
    val general: String,
    val porNivel: Map<String, String>
)

data class HorarioEfectivo(
    val definido: Boolean,
    val ingreso: String?,
    val tolerancia: Int,
    val limite: String,
    val salida: String?,
    val desde: String?,
    val hasta: String?,
    val permanencia: Int
)

data class SalidaPermitidaResult(
    val ok: Boolean,
    val desde: String
)

object CalendarioUtils {

    fun aMin(hhmm: String): Int {
        val partes = hhmm.split(":").map { it.toInt() }
        return partes[0] * 60 + partes[1]
    }

    fun aHHMM(min: Int): String {
        val h = min / 60
        val m = min % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun mapaNoLectivos(calendario: List<DiaCalendario> = emptyList()): Map<String, DiaCalendario> {
        return calendario
            .filter { it.tipo == "Feriado" || it.tipo == "Sin clases" }
            .associateBy { it.fecha }
    }

    fun esDiaLectivo(fecha: String, noLectivos: Map<String, DiaCalendario>): Boolean {
        return !DateUtils.isWeekend(fecha) && fecha !in noLectivos
    }

    fun diasLectivos(
        desde: String,
        hasta: String,
        noLectivos: Map<String, DiaCalendario> = emptyMap()
    ): List<String> {
        val out = mutableListOf<String>()
        var f = desde
        while (f <= hasta) {
            if (esDiaLectivo(f, noLectivos)) {
                out.add(f)
            }
            f = DateUtils.addDays(f, 1)
        }
        return out
    }

    fun feriadosPeru(anio: Int): List<DiaCalendario> {
        // Algoritmo de Meeus/Jones/Butcher para calcular Pascua
        val a = anio % 19
        val b = anio / 100
        val c = anio % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mes = (h + l - 7 * m + 114) / 31
        val dia = ((h + l - 7 * m + 114) % 31) + 1

        val pascuaStr = String.format(Locale.US, "%04d-%02d-%02d", anio, mes, dia)

        val fijos = listOf(
            "01-01" to "Año Nuevo",
            "05-01" to "Día del Trabajo",
            "06-07" to "Batalla de Arica y Día de la Bandera",
            "06-29" to "San Pedro y San Pablo",
            "07-23" to "Día de la Fuerza Aérea del Perú",
            "07-28" to "Fiestas Patrias",
            "07-29" to "Fiestas Patrias",
            "08-06" to "Batalla de Junín",
            "08-30" to "Santa Rosa de Lima",
            "10-08" to "Combate de Angamos",
            "11-01" to "Todos los Santos",
            "12-08" to "Inmaculada Concepción",
            "12-09" to "Batalla de Ayacucho",
            "12-25" to "Navidad"
        ).map { (md, nombre) ->
            DiaCalendario(fecha = "$anio-$md", tipo = "Feriado", nombre = nombre)
        }

        val moviles = listOf(
            DiaCalendario(
                fecha = DateUtils.addDays(pascuaStr, -3),
                tipo = "Feriado",
                nombre = "Jueves Santo"
            ),
            DiaCalendario(
                fecha = DateUtils.addDays(pascuaStr, -2),
                tipo = "Feriado",
                nombre = "Viernes Santo"
            )
        )

        return (fijos + moviles).sortedBy { it.fecha }
    }

    fun limiteDeHorario(
        horarios: List<Horario> = emptyList(),
        nivel: String?,
        porDefecto: String = "08:00"
    ): String {
        val h = horarios.find { it.nivel != null && it.nivel == nivel }
            ?: horarios.find { it.nivel == null }
        return if (h != null) {
            val tol = h.toleranciaMin ?: 0
            aHHMM(aMin(h.horaIngreso) + tol)
        } else {
            porDefecto
        }
    }

    fun tablaLimites(
        horarios: List<Horario> = emptyList(),
        porDefecto: String = "08:00"
    ): TablaLimites {
        val porNivel = mutableMapOf<String, String>()
        horarios.filter { !it.nivel.isNullOrBlank() }.forEach { h ->
            val n = h.nivel!!
            porNivel[n] = limiteDeHorario(horarios, n, porDefecto)
        }
        val general = limiteDeHorario(horarios, null, porDefecto)
        return TablaLimites(general = general, porNivel = porNivel)
    }

    fun horarioDe(
        horarios: List<Horario> = emptyList(),
        nivel: String?,
        porDefectoLimite: String = "08:00",
        porDefectoPermanencia: Int = 120
    ): HorarioEfectivo {
        val h = horarios.find { it.nivel != null && it.nivel == nivel }
            ?: horarios.find { it.nivel == null }

        if (h == null) {
            return HorarioEfectivo(
                definido = false,
                ingreso = null,
                tolerancia = 0,
                limite = porDefectoLimite,
                salida = null,
                desde = null,
                hasta = null,
                permanencia = porDefectoPermanencia
            )
        }

        val tol = h.toleranciaMin ?: 0
        val lim = aHHMM(aMin(h.horaIngreso) + tol)
        return HorarioEfectivo(
            definido = true,
            ingreso = h.horaIngreso,
            tolerancia = tol,
            limite = lim,
            salida = h.horaSalida,
            desde = h.ingresoDesde,
            hasta = h.ingresoHasta,
            permanencia = h.permanenciaMin ?: porDefectoPermanencia
        )
    }

    fun estadoIngreso(h: HorarioEfectivo, hhmm: String): String {
        if (!h.desde.isNullOrBlank() && hhmm < h.desde) return "temprano"
        if (!h.hasta.isNullOrBlank() && hhmm > h.hasta) return "cerrado"
        return if (hhmm > h.limite) "tarde" else "puntual"
    }

    fun salidaPermitida(
        horaIngreso: String,
        ahoraHHMM: String,
        permanenciaMin: Int = 120
    ): SalidaPermitidaResult {
        val minSalida = minOf(aMin(horaIngreso) + permanenciaMin, 24 * 60 - 1)
        val desde = aHHMM(minSalida)
        return SalidaPermitidaResult(
            ok = ahoraHHMM >= desde,
            desde = desde
        )
    }
}
