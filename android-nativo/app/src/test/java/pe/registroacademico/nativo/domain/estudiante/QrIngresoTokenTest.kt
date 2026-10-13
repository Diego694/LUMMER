package pe.registroacademico.nativo.domain.estudiante

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrIngresoTokenTest {

    @Test
    fun parsearQrIngreso_tokenValido_devuelveObjeto() {
        val tokenValido = "LI.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"
        val resultado = parsearQrIngreso(tokenValido)

        assertNotNull(resultado)
        assertEquals("e9b49b348f9547cb82103f736a445499", resultado?.sesionHex)
        assertEquals(59374021L, resultado?.ventana)
        assertEquals("a1b2c3d4e5f6", resultado?.firma)
    }

    @Test
    fun parsearQrIngreso_conMayusculas_normalizaAMinusculas() {
        val tokenMayus = "LI.E9B49B348F9547CB82103F736A445499.12345.A1B2C3D4E5F6"
        val resultado = parsearQrIngreso(tokenMayus)

        assertNotNull(resultado)
        assertEquals("e9b49b348f9547cb82103f736a445499", resultado?.sesionHex)
        assertEquals(12345L, resultado?.ventana)
        assertEquals("a1b2c3d4e5f6", resultado?.firma)
    }

    @Test
    fun parsearQrIngreso_toleranteAEspacios_devuelveObjeto() {
        val conEspacios = "   LI.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6   \n"
        val resultado = parsearQrIngreso(conEspacios)

        assertNotNull(resultado)
        assertEquals("e9b49b348f9547cb82103f736a445499", resultado?.sesionHex)
        assertEquals(59374021L, resultado?.ventana)
        assertEquals("a1b2c3d4e5f6", resultado?.firma)
    }

    @Test
    fun parsearQrIngreso_prefijoIncorrectoLQ_devuelveNull() {
        val tokenCurso = "LQ.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"
        assertNull(parsearQrIngreso(tokenCurso))
    }

    @Test
    fun parsearQrIngreso_prefijoDesconocido_devuelveNull() {
        val tokenOtro = "XX.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"
        assertNull(parsearQrIngreso(tokenOtro))
    }

    @Test
    fun parsearQrIngreso_partesFaltantes_devuelveNull() {
        // Solo 3 partes
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.59374021"))
        // Solo 2 partes
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499"))
        // Solo 1 parte
        assertNull(parsearQrIngreso("LI"))
        // Partes sobrantes (> 4 partes)
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6.extra"))
    }

    @Test
    fun parsearQrIngreso_hexInvalido_devuelveNull() {
        // Sesión de menos de 32 hex
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb.59374021.a1b2c3d4e5f6"))
        // Sesión de más de 32 hex
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499aa.59374021.a1b2c3d4e5f6"))
        // Sesión con caracteres no hex ('z')
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a44549z.59374021.a1b2c3d4e5f6"))
        // Firma de menos de 12 hex
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3"))
        // Firma de más de 12 hex
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f677"))
        // Firma con caracteres no hex ('g')
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.59374021.g1b2c3d4e5f6"))
    }

    @Test
    fun parsearQrIngreso_ventanaNoNumerica_devuelveNull() {
        // Alfabético
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.abc.a1b2c3d4e5f6"))
        // Decimal con punto no admitido en ventana
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.12.34.a1b2c3d4e5f6"))
        // Signo negativo
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.-123.a1b2c3d4e5f6"))
        // Más de 12 dígitos
        assertNull(parsearQrIngreso("LI.e9b49b348f9547cb82103f736a445499.1234567890123.a1b2c3d4e5f6"))
    }

    @Test
    fun parsearQrIngreso_nuloOVacio_devuelveNull() {
        assertNull(parsearQrIngreso(null))
        assertNull(parsearQrIngreso(""))
        assertNull(parsearQrIngreso("   "))
    }

    @Test
    fun seleccionarFlujoQr_clasificaCorrectamente() {
        assertEquals(FlujoQr.INGRESO, seleccionarFlujoQr("LI.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"))
        assertEquals(FlujoQr.INGRESO, seleccionarFlujoQr("  LI.algo  "))
        assertEquals(FlujoQr.CURSO, seleccionarFlujoQr("LQ.e9b49b348f9547cb82103f736a445499.59374021.a1b2c3d4e5f6"))
        assertEquals(FlujoQr.CURSO, seleccionarFlujoQr("  LQ.algo  "))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr("XX.algo"))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr("https://colegio.pe"))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr(null))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr(""))
    }
}
