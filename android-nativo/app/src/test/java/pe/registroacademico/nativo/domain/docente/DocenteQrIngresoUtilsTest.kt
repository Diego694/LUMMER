package pe.registroacademico.nativo.domain.docente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias puras en la JVM (JUnit 4) para las utilidades de QR rotativo de ingreso:
 * - Validación y normalización de intervalos (15 s / 30 s, rango 10..60 s)
 * - Cálculo de segundos restantes en la ventana dinámica
 * - Cálculo de ms hasta el siguiente refresco con margen de anticipación
 * - Cálculo de índice de ventana
 * - Verificación de prefijo de token de ingreso ('LI.')
 */
class DocenteQrIngresoUtilsTest {

    @Test
    fun testValidacionIntervalo() {
        // Rango válido según contrato de base de datos (10 a 60 segundos)
        assertTrue(DocenteQrUtils.esIntervaloValido(10))
        assertTrue(DocenteQrUtils.esIntervaloValido(15))
        assertTrue(DocenteQrUtils.esIntervaloValido(30))
        assertTrue(DocenteQrUtils.esIntervaloValido(45))
        assertTrue(DocenteQrUtils.esIntervaloValido(60))

        // Fuera de rango
        assertFalse(DocenteQrUtils.esIntervaloValido(9))
        assertFalse(DocenteQrUtils.esIntervaloValido(0))
        assertFalse(DocenteQrUtils.esIntervaloValido(-15))
        assertFalse(DocenteQrUtils.esIntervaloValido(61))
        assertFalse(DocenteQrUtils.esIntervaloValido(120))
    }

    @Test
    fun testNormalizacionIntervaloIngreso() {
        // Solo 15 y 30 s son opciones admitidas en quiosco
        assertEquals(15, DocenteQrUtils.normalizarIntervaloIngreso(15))
        assertEquals(30, DocenteQrUtils.normalizarIntervaloIngreso(30))

        // Valores nulos o no soportados caen al valor por defecto (30)
        assertEquals(30, DocenteQrUtils.normalizarIntervaloIngreso(null))
        assertEquals(30, DocenteQrUtils.normalizarIntervaloIngreso(20))
        assertEquals(30, DocenteQrUtils.normalizarIntervaloIngreso(45))
        assertEquals(30, DocenteQrUtils.normalizarIntervaloIngreso(0))
        assertEquals(30, DocenteQrUtils.normalizarIntervaloIngreso(-1))
    }

    @Test
    fun testCalcularSegundosRestantesIntervalo15s() {
        val intervalo = 15

        // Inicio exacto de la ventana: restan 15 s
        assertEquals(15L, DocenteQrUtils.calcularSegundosRestantesIntervalo(0L, intervalo))
        assertEquals(15L, DocenteQrUtils.calcularSegundosRestantesIntervalo(15_000L, intervalo))
        assertEquals(15L, DocenteQrUtils.calcularSegundosRestantesIntervalo(30_000L, intervalo))

        // A los 5 segundos: restan 10 s
        assertEquals(10L, DocenteQrUtils.calcularSegundosRestantesIntervalo(5_000L, intervalo))
        assertEquals(10L, DocenteQrUtils.calcularSegundosRestantesIntervalo(20_000L, intervalo))

        // A los 14 segundos: resta 1 s
        assertEquals(1L, DocenteQrUtils.calcularSegundosRestantesIntervalo(14_000L, intervalo))
        assertEquals(1L, DocenteQrUtils.calcularSegundosRestantesIntervalo(29_000L, intervalo))
    }

    @Test
    fun testCalcularSegundosRestantesIntervalo30s() {
        val intervalo = 30

        // Inicio exacto de la ventana: restan 30 s
        assertEquals(30L, DocenteQrUtils.calcularSegundosRestantesIntervalo(0L, intervalo))
        assertEquals(30L, DocenteQrUtils.calcularSegundosRestantesIntervalo(30_000L, intervalo))

        // A los 10 segundos: restan 20 s
        assertEquals(20L, DocenteQrUtils.calcularSegundosRestantesIntervalo(10_000L, intervalo))
        assertEquals(20L, DocenteQrUtils.calcularSegundosRestantesIntervalo(40_000L, intervalo))

        // A los 29 segundos: resta 1 s
        assertEquals(1L, DocenteQrUtils.calcularSegundosRestantesIntervalo(29_000L, intervalo))
        assertEquals(1L, DocenteQrUtils.calcularSegundosRestantesIntervalo(59_000L, intervalo))
    }

    @Test
    fun testCalcularMsHastaSiguienteRefrescoVentana30s() {
        val intervalo = 30
        val margen = 1000L // refrescar 1 s antes de expirar

        // En epochMs = 0 ms: faltan 30 s = 30,000 ms. Con margen de 1,000 ms, espera 29,000 ms
        assertEquals(29_000L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(0L, intervalo, margen))

        // En epochMs = 10,000 ms: faltan 20 s = 20,000 ms. Con margen, espera 19,000 ms
        assertEquals(19_000L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(10_000L, intervalo, margen))

        // En epochMs = 28,000 ms: faltan 2 s = 2,000 ms. Con margen, espera 1,000 ms
        assertEquals(1_000L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(28_000L, intervalo, margen))

        // En epochMs = 29,000 ms: falta exactamente 1 s (<= margen). Refresco inmediato (0 ms)
        assertEquals(0L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(29_000L, intervalo, margen))

        // En epochMs = 29,500 ms: faltan 500 ms (<= margen). Refresco inmediato (0 ms)
        assertEquals(0L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(29_500L, intervalo, margen))
    }

    @Test
    fun testCalcularMsHastaSiguienteRefrescoVentana15s() {
        val intervalo = 15
        val margen = 1000L

        // En epochMs = 0 ms: faltan 15 s = 15,000 ms. Espera 14,000 ms
        assertEquals(14_000L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(0L, intervalo, margen))

        // En epochMs = 13,000 ms: faltan 2,000 ms. Espera 1,000 ms
        assertEquals(1_000L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(13_000L, intervalo, margen))

        // En epochMs = 14,000 ms: resta 1 s. Refresco inmediato (0 ms)
        assertEquals(0L, DocenteQrUtils.calcularMsHastaSiguienteRefresco(14_000L, intervalo, margen))
    }

    @Test
    fun testCalcularVentanaIngreso() {
        // Ventana de 15 s (floor(seg / 15))
        assertEquals(0L, DocenteQrUtils.calcularVentanaIngreso(0L, 15))
        assertEquals(0L, DocenteQrUtils.calcularVentanaIngreso(14_999L, 15))
        assertEquals(1L, DocenteQrUtils.calcularVentanaIngreso(15_000L, 15))
        assertEquals(1L, DocenteQrUtils.calcularVentanaIngreso(29_999L, 15))
        assertEquals(2L, DocenteQrUtils.calcularVentanaIngreso(30_000L, 15))

        // Ventana de 30 s (floor(seg / 30))
        assertEquals(0L, DocenteQrUtils.calcularVentanaIngreso(0L, 30))
        assertEquals(0L, DocenteQrUtils.calcularVentanaIngreso(29_999L, 30))
        assertEquals(1L, DocenteQrUtils.calcularVentanaIngreso(30_000L, 30))
        assertEquals(1L, DocenteQrUtils.calcularVentanaIngreso(59_999L, 30))
        assertEquals(2L, DocenteQrUtils.calcularVentanaIngreso(60_000L, 30))
    }

    @Test
    fun testEsTokenQrIngreso() {
        // Prefijo LI. exclusivo para QR de ingreso institucional
        assertTrue(DocenteQrUtils.esTokenQrIngreso("LI.550e8400e29b41d4a716446655440000.12345.abcdef123456"))
        assertTrue(DocenteQrUtils.esTokenQrIngreso("LI.test"))

        // Otros códigos no deben ser reconocidos como QR de ingreso
        assertFalse(DocenteQrUtils.esTokenQrIngreso("LQ.550e8400e29b41d4a716446655440000.12345.abcdef123456")) // QR de curso
        assertFalse(DocenteQrUtils.esTokenQrIngreso("CARNET-ESTUDIANTE-2026"))
        assertFalse(DocenteQrUtils.esTokenQrIngreso(""))
        assertFalse(DocenteQrUtils.esTokenQrIngreso(null))
        assertFalse(DocenteQrUtils.esTokenQrIngreso("li.minusculas"))
    }
}
