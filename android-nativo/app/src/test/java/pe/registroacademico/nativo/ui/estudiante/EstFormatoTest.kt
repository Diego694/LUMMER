package pe.registroacademico.nativo.ui.estudiante

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.estudiante.AlumnoPropio
import pe.registroacademico.nativo.domain.qr.generarQR

import java.util.TimeZone

/**
 * Pruebas unitarias para la lógica pura del módulo de estudiante (EstFormato.kt).
 */
class EstFormatoTest {

    @Test
    fun testFormatearFechaAvisoStandard() {
        val f1 = EstFormato.formatearFechaAviso("2026-10-13")
        assertEquals("13 de octubre de 2026", f1)

        val f2 = EstFormato.formatearFechaAviso("2026-05-01")
        assertEquals("1 de mayo de 2026", f2)

        val f3 = EstFormato.formatearFechaAviso("2026-12-25T15:30:00Z")
        assertEquals("25 de diciembre de 2026", f3)

        val f4 = EstFormato.formatearFechaAviso("2026-12-25T03:30:00Z")
        assertEquals("24 de diciembre de 2026", f4)

        val f5 = EstFormato.formatearFechaAviso("2026-12-25 03:30:00")
        assertEquals("24 de diciembre de 2026", f5)

        val fVacia = EstFormato.formatearFechaAviso("")
        assertEquals("", fVacia)

        val fNull = EstFormato.formatearFechaAviso(null)
        assertEquals("", fNull)

        val fInvalida = EstFormato.formatearFechaAviso("fecha-no-estandar")
        assertEquals("fecha-no-estandar", fInvalida)
    }

    @Test
    fun testFormatearFechaAvisoIndependienteDeZonaDelSistema() {
        val zonaOriginal = TimeZone.getDefault()
        try {
            val zonas = listOf("UTC", "America/Lima", "Asia/Tokyo", "America/New_York", "Europe/London", "GMT+12")
            for (idZona in zonas) {
                TimeZone.setDefault(TimeZone.getTimeZone(idZona))

                // Solo fecha conserva el día calendario en America/Lima
                assertEquals("13 de octubre de 2026", EstFormato.formatearFechaAviso("2026-10-13"))
                assertEquals("1 de mayo de 2026", EstFormato.formatearFechaAviso("2026-05-01"))

                // Instante UTC temprano (03:30 UTC = 22:30 día anterior en Lima UTC-5)
                assertEquals("24 de diciembre de 2026", EstFormato.formatearFechaAviso("2026-12-25T03:30:00Z"))
                assertEquals("24 de diciembre de 2026", EstFormato.formatearFechaAviso("2026-12-25 03:30:00"))

                // Instante UTC de la tarde se mantiene en el mismo día en Lima
                assertEquals("25 de diciembre de 2026", EstFormato.formatearFechaAviso("2026-12-25T15:30:00Z"))

                // Casos borde
                assertEquals("", EstFormato.formatearFechaAviso(""))
                assertEquals("", EstFormato.formatearFechaAviso(null))
                assertEquals("fecha-no-estandar", EstFormato.formatearFechaAviso("fecha-no-estandar"))
            }
        } finally {
            TimeZone.setDefault(zonaOriginal)
        }
    }

    @Test
    fun testObtenerIniciales() {
        assertEquals("JP", EstFormato.obtenerIniciales("Juan Perez"))
        assertEquals("MA", EstFormato.obtenerIniciales("Maria Acosta"))
        assertEquals("C", EstFormato.obtenerIniciales("Carlos"))
        assertEquals("?", EstFormato.obtenerIniciales(""))
        assertEquals("?", EstFormato.obtenerIniciales(null))
    }

    @Test
    fun testContenidoQrCarnetEstaticoModoOff() {
        val codigo = "est-101"
        val secreto = "clave-secreta"
        val resultado = EstFormato.contenidoQrCarnet(
            codigo = codigo,
            qrSecreto = secreto,
            qrModo = "off"
        )
        // En modo "off", el carnet debe ser puramente estático con el código del estudiante
        assertEquals(codigo, resultado)
    }

    @Test
    fun testContenidoQrCarnetEstaticoSinSecreto() {
        val codigo = "est-202"
        val resultado = EstFormato.contenidoQrCarnet(
            codigo = codigo,
            qrSecreto = null,
            qrModo = "obligatorio"
        )
        // Si no tiene secreto asignado, debe ser estático
        assertEquals(codigo, resultado)
    }

    @Test
    fun testContenidoQrCarnetDinamicoObligatorio() {
        val codigo = "est-303"
        val secreto = "secreto-seguro-2026"
        val timestamp = 1791201600000L

        val resultado = EstFormato.contenidoQrCarnet(
            codigo = codigo,
            qrSecreto = secreto,
            qrModo = "obligatorio",
            ms = timestamp
        )

        // Debe coincidir con generarQR
        val esperado = generarQR(codigo, secreto, timestamp)
        assertEquals(esperado, resultado)

        // Debe tener formato <codigo>.<ventana>.<firma10hex>
        val partes = resultado.split(".")
        assertEquals(3, partes.size)
        assertEquals(codigo, partes[0])
        assertEquals(10, partes[2].length)
    }

    @Test
    fun testContenidoQrCarnetConModeloAlumno() {
        val alumno = AlumnoPropio(
            id = "alm-1",
            codigo = "a2026",
            nombre = "Ana Torres",
            qrSecreto = "secreto-ana"
        )
        val timestamp = 1791201600000L

        val qrDinamico = EstFormato.contenidoQrCarnet(
            alumno = alumno,
            qrModo = "opcional",
            ms = timestamp
        )
        val esperado = generarQR(alumno.codigo, alumno.qrSecreto!!, timestamp)
        assertEquals(esperado, qrDinamico)
    }
}
