package pe.registroacademico.nativo.domain.docente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Curso

/**
 * Pruebas unitarias en la JVM (JUnit 4) para la lógica de DocenteQrUtils:
 * - Cuenta regresiva en ventanas de 30 segundos
 * - Agrupación de cursos por carrera y ciclo
 * - Formato de hora exacta HH:mm:ss y fecha
 * - Etiquetas legibles de medios de registro
 */
class DocenteQrUtilsTest {

    @Test
    fun testCuentaRegresivaVentana30s() {
        // En el segundo 0 de la ventana, restan 30 segundos
        assertEquals(30L, DocenteQrUtils.calcularSegundosRestantes(0L))
        assertEquals(30L, DocenteQrUtils.calcularSegundosRestantes(30_000L))
        assertEquals(30L, DocenteQrUtils.calcularSegundosRestantes(60_000L))

        // A los 10 segundos transcurridos (quedan 20 segundos)
        assertEquals(20L, DocenteQrUtils.calcularSegundosRestantes(10_000L))
        assertEquals(20L, DocenteQrUtils.calcularSegundosRestantes(40_000L))

        // A los 29 segundos transcurridos (queda 1 segundo)
        assertEquals(1L, DocenteQrUtils.calcularSegundosRestantes(29_000L))
        assertEquals(1L, DocenteQrUtils.calcularSegundosRestantes(59_000L))

        // Ventana de 30 s
        assertEquals(0L, DocenteQrUtils.calcularVentana30s(0L))
        assertEquals(0L, DocenteQrUtils.calcularVentana30s(29_999L))
        assertEquals(1L, DocenteQrUtils.calcularVentana30s(30_000L))
        assertEquals(2L, DocenteQrUtils.calcularVentana30s(60_000L))
        assertEquals(2L, DocenteQrUtils.calcularVentana30s(65_432L))
    }

    @Test
    fun testFormatoHoraExacta() {
        // 2026-10-13T13:30:45Z en UTC corresponde a 08:30:45 en America/Lima (UTC-5)
        val iso = "2026-10-13T13:30:45Z"
        assertEquals("08:30:45", DocenteQrUtils.formatearHoraExacta(iso))

        // 2026-10-13T23:59:59Z en UTC corresponde a 18:59:59 en America/Lima
        val isoTarde = "2026-10-13T23:59:59Z"
        assertEquals("18:59:59", DocenteQrUtils.formatearHoraExacta(isoTarde))

        // Casos nulos o vacíos
        assertEquals("—", DocenteQrUtils.formatearHoraExacta(null))
        assertEquals("—", DocenteQrUtils.formatearHoraExacta(""))

        // Fallback directo si ya viene formateado
        assertEquals("09:15:30", DocenteQrUtils.formatearHoraExacta("09:15:30"))
    }

    @Test
    fun testFormatoFechaExacta() {
        val iso = "2026-10-13T13:30:45Z"
        assertEquals("13/10/2026", DocenteQrUtils.formatearFechaExacta(iso))

        assertEquals("—", DocenteQrUtils.formatearFechaExacta(null))
        assertEquals("—", DocenteQrUtils.formatearFechaExacta(""))
    }

    @Test
    fun testEtiquetasOrigenLegibles() {
        assertEquals("Carnet QR", DocenteQrUtils.etiquetaOrigenLegible("qr"))
        assertEquals("NFC", DocenteQrUtils.etiquetaOrigenLegible("nfc"))
        assertEquals("Manual", DocenteQrUtils.etiquetaOrigenLegible("manual"))
        assertEquals("El propio estudiante", DocenteQrUtils.etiquetaOrigenLegible("alumno"))
        assertEquals("Masivo", DocenteQrUtils.etiquetaOrigenLegible("masivo"))
        assertEquals("QR del docente", DocenteQrUtils.etiquetaOrigenLegible("qr-docente"))
        assertEquals("Quiosco", DocenteQrUtils.etiquetaOrigenLegible("kiosco"))
        assertEquals("Quiosco", DocenteQrUtils.etiquetaOrigenLegible("quiosco"))
        assertEquals("—", DocenteQrUtils.etiquetaOrigenLegible(null))
        assertEquals("—", DocenteQrUtils.etiquetaOrigenLegible(""))
        assertEquals("Desconocido", DocenteQrUtils.etiquetaOrigenLegible("Desconocido"))
    }

    @Test
    fun testAgrupacionCursosPorCarreraYCiclo() {
        val cursos = listOf(
            Curso(id = "1", nombre = "Programación Web", nivel = "Desarrollo de Sistemas", grado = "II CICLO"),
            Curso(id = "2", nombre = "Base de Datos", nivel = "Desarrollo de Sistemas", grado = "I CICLO"),
            Curso(id = "3", nombre = "Contabilidad General", nivel = "Administración", grado = "I CICLO"),
            Curso(id = "4", nombre = "Costos y Presupuestos", nivel = "Administración", grado = "II CICLO"),
            Curso(id = "5", nombre = "Algoritmos", nivel = "Desarrollo de Sistemas", grado = "I CICLO")
        )

        val agrupados = DocenteQrUtils.agruparCursosPorCarrera(cursos)

        // Comprobar carreras agrupadas
        assertEquals(2, agrupados.size)
        assertTrue(agrupados.containsKey("Administración"))
        assertTrue(agrupados.containsKey("Desarrollo de Sistemas"))

        // Comprobar ordenación interna por grado y luego nombre
        val cursosSistemas = agrupados["Desarrollo de Sistemas"]!!
        assertEquals(3, cursosSistemas.size)
        // Grado I va antes que II; y entre Algoritmos y Base de Datos por nombre
        assertEquals("Algoritmos", cursosSistemas[0].nombre)
        assertEquals("Base de Datos", cursosSistemas[1].nombre)
        assertEquals("Programación Web", cursosSistemas[2].nombre)

        val cursosAdmin = agrupados["Administración"]!!
        assertEquals(2, cursosAdmin.size)
        assertEquals("Contabilidad General", cursosAdmin[0].nombre)
        assertEquals("Costos y Presupuestos", cursosAdmin[1].nombre)
    }
}
