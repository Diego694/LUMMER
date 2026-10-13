package pe.registroacademico.nativo.ui.estudiante

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DestinoEstudianteTest {

    @Test
    fun testDestinosEstudianteNoSonNullYTienenRutasCorrectas() {
        val items = DestinoEstudiante.items

        assertEquals("Deben existir exactamente 5 destinos", 5, items.size)

        items.forEachIndexed { index, destino ->
            assertNotNull("El destino en índice $index no debe ser null", destino)
            assertNotNull("La ruta del destino en índice $index no debe ser null", destino.ruta)
        }

        val rutas = items.map { it.ruta }
        val rutasEsperadas = listOf("inicio", "escanear", "cursos", "asistencia", "avisos")
        assertEquals(rutasEsperadas, rutas)
    }

    @Test
    fun testBuscarPorRutaNoLanzaNpe() {
        val rutasEsperadas = listOf("inicio", "escanear", "cursos", "asistencia", "avisos")
        for (ruta in rutasEsperadas) {
            val encontrado = DestinoEstudiante.items.find { it.ruta == ruta }
            assertNotNull("Debe encontrar el destino para la ruta $ruta", encontrado)
            assertEquals(ruta, encontrado?.ruta)
        }
    }
}
