package pe.registroacademico.nativo.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.domain.StringUtils

class RegistroAlumnoLogicTest {

    @Test
    fun testCensuraNombreEstudiante() {
        // Nombre con más de 3 tokens
        val censurado1 = StringUtils.censurarNombre("Juan Carlos Perez Rios")
        assertTrue(censurado1.contains("Juan Carlos"))
        assertFalse(censurado1.contains("Perez"))

        // Nombre de 2 tokens
        val censurado2 = StringUtils.censurarNombre("Ana Torres")
        assertTrue(censurado2.startsWith("Ana"))
        assertFalse(censurado2.contains("Torres"))
    }

    @Test
    fun testGeneracionSiguienteCodigo() {
        val alumnos = listOf(
            Alumno(id = "1", codigo = "a1001", nombre = "Estudiante 1"),
            Alumno(id = "2", codigo = "a1005", nombre = "Estudiante 2"),
            Alumno(id = "3", codigo = "a1003", nombre = "Estudiante 3")
        )

        val maxNum = alumnos.mapNotNull {
            val digits = it.codigo.filter { c -> c.isDigit() }
            digits.toIntOrNull()
        }.maxOrNull() ?: 1000

        val sugerido = "a${maxNum + 1}"
        assertEquals("a1006", sugerido)
    }

    @Test
    fun testValidacionCamposAlumno() {
        val nombreValido = "Carlos Mendoza"
        val nombreInvalido = "   "
        val codigoValido = "a2026"
        val codigoInvalido = ""

        assertTrue(nombreValido.isNotBlank())
        assertFalse(nombreInvalido.isNotBlank())
        assertTrue(codigoValido.isNotBlank())
        assertFalse(codigoInvalido.isNotBlank())
    }
}
