package pe.registroacademico.nativo.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.domain.Rol

class CatalogoPantallasTest {

    @Test
    fun testNuevasEntradasDeMenu() {
        val catalogo = CatalogoPantallas.catalogo

        // Aula
        val aula = catalogo.find { it.id == "aula" }
        assertNotNull("Debe existir la entrada 'aula'", aula)
        assertEquals("Aula", aula?.titulo)
        assertEquals("Principal", aula?.grupo)
        assertFalse("Aula es para todos los roles de personal", aula?.soloAdmin ?: true)
        assertFalse("Aula no es solo superadmin", aula?.soloSuper ?: true)
        assertFalse("Aula es visible para docentes", aula?.noDocente ?: true)

        // Pasar lista
        val pasarLista = catalogo.find { it.id == "pasar-lista" }
        assertNotNull("Debe existir la entrada 'pasar-lista'", pasarLista)
        assertEquals("Pasar lista", pasarLista?.titulo)
        assertEquals("Registro", pasarLista?.grupo)
        assertFalse("Pasar lista es para docentes y administradores", pasarLista?.soloAdmin ?: true)
        assertFalse("Pasar lista no tiene flag noDocente", pasarLista?.noDocente ?: true)

        // Conexiones de datos
        val conexiones = catalogo.find { it.id == "conexiones" }
        assertNotNull("Debe existir la entrada 'conexiones'", conexiones)
        assertEquals("Conexiones de datos", conexiones?.titulo)
        assertEquals("Sistema", conexiones?.grupo)
        assertTrue("Conexiones es solo para superadmin", conexiones?.soloSuper ?: false)
    }

    @Test
    fun testPantallasNoDocenteListaExactaWeb() {
        val catalogo = CatalogoPantallas.catalogo
        val marcadasNoDocente = catalogo.filter { it.noDocente }.map { it.id }.toSet()

        val esperadasNoDocente = setOf(
            "quiosco",
            "registro-qr",
            "registro-alumno",
            "registro-masivo",
            "carnet",
            "codigo",
            "niveles",
            "grados",
            "docentes"
        )

        assertEquals(
            "La lista de pantallas noDocente debe coincidir exactamente con la web (main.js)",
            esperadasNoDocente,
            marcadasNoDocente
        )
    }

    @Test
    fun testMiCuentaAlFinalDelMenu() {
        val catalogo = CatalogoPantallas.catalogo
        val ultima = catalogo.last()

        assertEquals("perfil", ultima.id)
        assertEquals("Mi perfil", ultima.titulo)
        assertEquals("Mi cuenta", ultima.grupo)
    }

    @Test
    fun testFiltradoPorRolDocente() {
        val catalogo = CatalogoPantallas.catalogo
        val esAdmin = false
        val esSuper = false
        val esDocente = true

        val visiblesParaDocente = catalogo.filter { p ->
            (!p.soloAdmin || esAdmin) && (!p.soloSuper || esSuper) && (!p.noDocente || !esDocente)
        }

        // El docente no ve quiosco ni registro-qr
        assertFalse(visiblesParaDocente.any { it.id == "quiosco" })
        assertFalse(visiblesParaDocente.any { it.id == "registro-qr" })
        assertFalse(visiblesParaDocente.any { it.id == "registro-alumno" })
        assertFalse(visiblesParaDocente.any { it.id == "registro-masivo" })
        assertFalse(visiblesParaDocente.any { it.id == "carnet" })
        assertFalse(visiblesParaDocente.any { it.id == "codigo" })
        assertFalse(visiblesParaDocente.any { it.id == "niveles" })
        assertFalse(visiblesParaDocente.any { it.id == "grados" })
        assertFalse(visiblesParaDocente.any { it.id == "docentes" })

        // El docente SÍ ve pasar-lista, aula, dashboard y perfil
        assertTrue(visiblesParaDocente.any { it.id == "pasar-lista" })
        assertTrue(visiblesParaDocente.any { it.id == "aula" })
        assertTrue(visiblesParaDocente.any { it.id == "dashboard" })
        assertTrue(visiblesParaDocente.any { it.id == "perfil" })
    }
}
