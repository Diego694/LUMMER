package pe.registroacademico.nativo.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushUnitTests {

    @Test
    fun canalGeneral_esConstanteEsperada() {
        assertEquals("general", CANAL_GENERAL)
    }

    @Test
    fun canalGeneral_esIdentificadorValido() {
        assertTrue(CANAL_GENERAL.isNotBlank())
        assertTrue(CANAL_GENERAL.matches(Regex("^[a-z0-9_-]+$")))
    }
}
