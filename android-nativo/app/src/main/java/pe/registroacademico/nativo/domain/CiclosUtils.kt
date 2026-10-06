package pe.registroacademico.nativo.domain

data class ParsedCiclo(
    val ciclo: String?,
    val seccion: String
)

object CiclosUtils {

    val CICLOS = listOf("I", "II", "III", "IV", "V", "VI")

    private val REGEX_CICLO = Regex("""\b(VI|IV|V|III|II|I)\s+CICLO\b""", RegexOption.IGNORE_CASE)
    private val REGEX_SECCION = Regex("""SECCI[ÓO]N\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    fun nombreCiclo(carrera: String, ciclo: String, seccion: String = ""): String {
        return listOf(
            carrera,
            "$ciclo CICLO",
            if (seccion.isNotBlank()) "SECCIÓN $seccion" else ""
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun parsearCiclo(nombre: String?): ParsedCiclo {
        val s = nombre ?: ""
        val matchCiclo = REGEX_CICLO.find(s)
        val matchSeccion = REGEX_SECCION.find(s)

        val ciclo = matchCiclo?.groupValues?.get(1)?.uppercase()
        val seccion = matchSeccion?.groupValues?.get(1)?.uppercase() ?: ""

        return ParsedCiclo(ciclo, seccion)
    }

    fun compararCiclos(a: String, b: String): Int {
        val pa = parsearCiclo(a)
        val pb = parsearCiclo(b)

        val ia = if (pa.ciclo != null) CICLOS.indexOf(pa.ciclo) else 99
        val ib = if (pb.ciclo != null) CICLOS.indexOf(pb.ciclo) else 99

        if (ia != ib) return ia.compareTo(ib)

        val cmpSec = pa.seccion.compareTo(pb.seccion, ignoreCase = true)
        if (cmpSec != 0) return cmpSec

        return a.compareTo(b, ignoreCase = true)
    }

    fun cicloCorto(nombre: String, carrera: String): String {
        if (carrera.isBlank()) return nombre
        val prefijo = "$carrera · "
        return if (nombre.startsWith(prefijo, ignoreCase = true)) {
            nombre.substring(prefijo.length)
        } else {
            nombre
        }
    }

    fun etiquetaCiclo(carrera: String, ciclo: String): String {
        if (carrera.isBlank()) return ciclo
        if (ciclo.isBlank()) return carrera
        val normCiclo = StringUtils.norm(ciclo)
        val normCarrera = StringUtils.norm(carrera)
        return if (normCiclo.startsWith(normCarrera)) {
            ciclo
        } else {
            "$carrera · $ciclo"
        }
    }
}
