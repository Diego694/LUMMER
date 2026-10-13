package pe.registroacademico.nativo.data.docente

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias en la JVM (JUnit 4) para la deserialización de modelos JSON
 * devueltos por los RPC qr_ingreso_abrir y qr_ingreso_token según el contrato.
 */
class DocenteQrIngresoModelosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializarSesionQrIngresoRespuestaCompleta() {
        val jsonStr = """
            {
                "sesion": "550e8400-e29b-41d4-a716-446655440000",
                "intervalo": 30,
                "expira_en": "2026-10-13T23:59:59Z"
            }
        """.trimIndent()

        val resp = json.decodeFromString<SesionQrIngresoRespuesta>(jsonStr)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", resp.sesion)
        assertEquals(30, resp.intervalo)
        assertEquals("2026-10-13T23:59:59Z", resp.expiraEn)
    }

    @Test
    fun testDeserializarSesionQrIngresoRespuestaMinima() {
        val jsonStr = """
            {
                "sesion": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        """.trimIndent()

        val resp = json.decodeFromString<SesionQrIngresoRespuesta>(jsonStr)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", resp.sesion)
        assertNull(resp.intervalo)
        assertNull(resp.expiraEn)
    }

    @Test
    fun testDeserializarTokenQrIngresoRespuestaActiva() {
        val jsonStr = """
            {
                "token": "LI.550e8400e29b41d4a716446655440000.123456.abcdef123456",
                "segundos": 28,
                "expira_en": "2026-10-13T23:59:59Z"
            }
        """.trimIndent()

        val resp = json.decodeFromString<TokenQrIngresoRespuesta>(jsonStr)
        assertEquals("LI.550e8400e29b41d4a716446655440000.123456.abcdef123456", resp.token)
        assertEquals(28L, resp.segundos)
        assertEquals("2026-10-13T23:59:59Z", resp.expiraEn)
        assertFalse(resp.cerrada)
    }

    @Test
    fun testDeserializarTokenQrIngresoRespuestaCerrada() {
        val jsonStr = """
            {
                "cerrada": true
            }
        """.trimIndent()

        val resp = json.decodeFromString<TokenQrIngresoRespuesta>(jsonStr)
        assertNull(resp.token)
        assertNull(resp.segundos)
        assertTrue(resp.cerrada)
    }
}
