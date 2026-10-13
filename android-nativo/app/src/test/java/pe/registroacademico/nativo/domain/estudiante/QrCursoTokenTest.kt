package pe.registroacademico.nativo.domain.estudiante

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCursoTokenTest {

    @Test
    fun parsearTokenQrCurso_tokenValido_devuelveObjeto() {
        val tokenValido = "LQ.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"
        val resultado = parsearTokenQrCurso(tokenValido)

        assertNotNull(resultado)
        assertEquals("e9b49b348f9547cb82103f736a445499", resultado?.sesionHex)
        assertEquals(59374021L, resultado?.ventana)
        assertEquals("a1b2c3d4e5f6", resultado?.firma)
    }

    @Test
    fun parsearTokenQrCurso_conMayusculas_normalizaAMinusculas() {
        val tokenMayus = "LQ.E9B49B348F9547CB82103F736A445499.12345.A1B2C3D4E5F6"
        val resultado = parsearTokenQrCurso(tokenMayus)

        assertNotNull(resultado)
        assertEquals("e9b49b348f9547cb82103f736a445499", resultado?.sesionHex)
        assertEquals(12345L, resultado?.ventana)
        assertEquals("a1b2c3d4e5f6", resultado?.firma)
    }

    @Test
    fun parsearTokenQrCurso_tokenNuloOVacio_devuelveNull() {
        assertNull(parsearTokenQrCurso(null))
        assertNull(parsearTokenQrCurso(""))
        assertNull(parsearTokenQrCurso("   "))
    }

    @Test
    fun parsearTokenQrCurso_prefijoInvalido_devuelveNull() {
        val token = "XX.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"
        assertNull(parsearTokenQrCurso(token))
    }

    @Test
    fun parsearTokenQrCurso_cantidadPartesInvalida_devuelveNull() {
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.59374021"))
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6.extra"))
    }

    @Test
    fun parsearTokenQrCurso_sesionInvalida_devuelveNull() {
        // Menos de 32 hex
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb.59374021.a1b2c3d4e5f6"))
        // Caracteres no hexadecimales
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a44549Z.59374021.a1b2c3d4e5f6"))
    }

    @Test
    fun parsearTokenQrCurso_ventanaInvalida_devuelveNull() {
        // No numérico
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.abc.a1b2c3d4e5f6"))
        // Demasiados dígitos (> 12 dígitos)
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.1234567890123.a1b2c3d4e5f6"))
    }

    @Test
    fun parsearTokenQrCurso_firmaInvalida_devuelveNull() {
        // Menos de 12 caracteres
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3"))
        // Más de 12 caracteres
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6a1"))
        // Caracteres no hexadecimales
        assertNull(parsearTokenQrCurso("LQ.e9b49b348f9547cb82103f736a445499.59374021.g1b2c3d4e5f6"))
    }

    @Test
    fun esTardanzaIngreso_calculaCorrectamente() {
        // Puntual antes de la hora límite (08:00)
        assertFalse(esTardanzaIngreso("07:50", "08:00"))
        assertFalse(esTardanzaIngreso("07:59", "08:00"))
        // En la hora exacta
        assertFalse(esTardanzaIngreso("08:00", "08:00"))
        // Pasada la hora
        assertTrue(esTardanzaIngreso("08:01", "08:00"))
        assertTrue(esTardanzaIngreso("08:15:30", "08:00"))
        // Nulo o en blanco
        assertFalse(esTardanzaIngreso(null))
        assertFalse(esTardanzaIngreso(""))
    }

    @Test
    fun calcularResumenAsistencia_listaHoras_calculaTotalesYPorcentaje() {
        val horas = listOf("07:45", "08:00", "08:10", "08:20")
        val diasClase = 5 // 4 asistencias registradas, 1 falta, 2 tardanzas (>08:00)

        val resumen = calcularResumenAsistencia(horas, diasClase = diasClase, horaLimite = "08:00")

        assertEquals(4, resumen.presentes)
        assertEquals(2, resumen.tardanzas)
        assertEquals(1, resumen.faltas)
        assertEquals(80, resumen.porcentaje) // 4 / 5 * 100 = 80%
    }

    @Test
    fun calcularResumenAsistencia_sobrecargaValoresNumericos_calculaCorrectamente() {
        val resumen = calcularResumenAsistencia(presentes = 18, tardanzas = 3, diasClase = 20)

        assertEquals(18, resumen.presentes)
        assertEquals(3, resumen.tardanzas)
        assertEquals(2, resumen.faltas) // 20 - 18 = 2
        assertEquals(90, resumen.porcentaje) // 18 / 20 * 100 = 90%
    }

    @Test
    fun calcularResumenAsistencia_ceroDiasClase_porcentajeCero() {
        val resumen = calcularResumenAsistencia(emptyList(), diasClase = 0)
        assertEquals(0, resumen.porcentaje)
        assertEquals(0, resumen.faltas)
    }

    @Test
    fun calcularPromedioNotas_actividadesVigesimales_calculaPromedioCorrecto() {
        val evals = listOf(
            NotaEvaluada(nota = 16.0, puntajeMax = 20.0),
            NotaEvaluada(nota = 18.0, puntajeMax = 20.0),
            NotaEvaluada(nota = null, puntajeMax = 20.0) // Sin entregar/calificar: no perjudica el promedio
        )

        val promedio = calcularPromedioNotas(evals)

        // (16 + 18) / (20 + 20) * 20 = (34 / 40) * 20 = 17.0
        assertNotNull(promedio)
        assertEquals(17.0, promedio!!, 0.01)
    }

    @Test
    fun calcularPromedioNotas_sinCalificaciones_devuelveNull() {
        val evals = listOf(
            NotaEvaluada(nota = null, puntajeMax = 20.0),
            NotaEvaluada(nota = null, puntajeMax = 10.0)
        )
        assertNull(calcularPromedioNotas(evals))
        assertNull(calcularPromedioNotas(emptyList()))
    }

    @Test
    fun calcularPromedioSimple_conPares_calculaPromedio() {
        val pares = listOf(
            15.0 to 20.0,
            20.0 to 20.0
        )
        val promedio = calcularPromedioSimple(pares)

        // (35 / 40) * 20 = 17.5
        assertNotNull(promedio)
        assertEquals(17.5, promedio!!, 0.01)
    }
}
