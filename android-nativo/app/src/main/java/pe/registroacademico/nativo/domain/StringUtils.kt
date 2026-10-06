package pe.registroacademico.nativo.domain

import java.text.Normalizer
import kotlin.math.roundToInt

object StringUtils {

    fun esc(s: String?): String {
        if (s == null) return ""
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun norm(s: String?): String {
        if (s == null) return ""
        val normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
    }

    fun initials(name: String?): String {
        val trimmed = name?.trim() ?: ""
        if (trimmed.isEmpty()) return "?"
        val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }.take(2)
        if (parts.isEmpty()) return "?"
        return parts.map { it.first() }.joinToString("").uppercase()
    }

    fun censurarNombre(
        nombreCompleto: String? = null,
        nombresParam: String? = null,
        apellidosParam: String? = null
    ): String {
        var nombres = nombresParam?.trim() ?: ""
        var apellidos = apellidosParam?.trim() ?: ""

        if (nombres.isEmpty() || apellidos.isEmpty()) {
            val tokens = (nombreCompleto ?: "").trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            when {
                tokens.size >= 3 -> {
                    nombres = tokens.dropLast(2).joinToString(" ")
                    apellidos = tokens.takeLast(2).joinToString(" ")
                }
                tokens.size == 2 -> {
                    nombres = tokens[0]
                    apellidos = tokens[1]
                }
                tokens.size == 1 -> {
                    nombres = tokens[0]
                    apellidos = ""
                }
                else -> {
                    nombres = ""
                    apellidos = ""
                }
            }
        }

        fun enmascarar(w: String): String {
            return if (w.length <= 2) {
                w.take(1) + "*"
            } else {
                w.substring(0, 2) + "*".repeat(minOf(w.length - 2, 6))
            }
        }

        val apEnmascarado = if (apellidos.isNotEmpty()) {
            apellidos.split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .joinToString(" ") { enmascarar(it) }
        } else {
            ""
        }

        return listOf(nombres, apEnmascarado)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    fun pct(num: Int, den: Int): Int {
        return if (den > 0) {
            ((num.toDouble() / den) * 100).roundToInt()
        } else {
            0
        }
    }
}
