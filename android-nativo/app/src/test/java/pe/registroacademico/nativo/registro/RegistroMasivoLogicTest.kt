package pe.registroacademico.nativo.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RegistroMasivoLogicTest {

    private fun parsearLineasCsv(texto: String): List<Map<String, String>> {
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lineas.isEmpty()) return emptyList()

        val primera = lineas.first().lowercase()
        val tieneCabecera = primera.contains("nombre") || primera.contains("codigo")
        val lineasDatos = if (tieneCabecera) lineas.drop(1) else lineas

        return lineasDatos.map { linea ->
            val tokens = when {
                linea.contains("\t") -> linea.split("\t")
                linea.contains(";") -> linea.split(";")
                else -> linea.split(",")
            }.map { it.trim().trim('"', '\'') }

            mapOf(
                "nombre" to (tokens.getOrNull(0) ?: ""),
                "codigo" to (tokens.getOrNull(1) ?: ""),
                "carrera" to (tokens.getOrNull(2) ?: ""),
                "ciclo" to (tokens.getOrNull(3) ?: ""),
                "apoderado" to (tokens.getOrNull(4) ?: ""),
                "estado" to (tokens.getOrNull(5) ?: "ACTIVO").ifBlank { "ACTIVO" }
            )
        }
    }

    @Test
    fun testParseoCvsConCabecera() {
        val csv = """
            nombre,codigo,carrera,ciclo,apoderado,estado
            Juan Perez Rios,a2001,MECANICA ELECTRICA,MECANICA ELECTRICA I,Maria Rios,ACTIVO
            Ana Torres Vega,a2002,APSTI,APSTI III,,ACTIVO
        """.trimIndent()

        val filas = parsearLineasCsv(csv)
        assertEquals(2, filas.size)

        assertEquals("Juan Perez Rios", filas[0]["nombre"])
        assertEquals("a2001", filas[0]["codigo"])
        assertEquals("MECANICA ELECTRICA", filas[0]["carrera"])
        assertEquals("MECANICA ELECTRICA I", filas[0]["ciclo"])
        assertEquals("Maria Rios", filas[0]["apoderado"])

        assertEquals("Ana Torres Vega", filas[1]["nombre"])
        assertEquals("a2002", filas[1]["codigo"])
        assertEquals("APSTI", filas[1]["carrera"])
    }

    @Test
    fun testDeteccionErroresPorFila() {
        val csv = """
            ,a2001,MECANICA ELECTRICA,MECANICA ELECTRICA I,,ACTIVO
            Carlos Gomez,,APSTI,APSTI I,,ACTIVO
        """.trimIndent()

        val filas = parsearLineasCsv(csv)
        assertEquals(2, filas.size)

        val errFila1 = if (filas[0]["nombre"].isNullOrBlank()) "Falta el nombre" else null
        val errFila2 = if (filas[1]["codigo"].isNullOrBlank()) "Falta el código" else null

        assertNotNull(errFila1)
        assertNotNull(errFila2)
    }
}
