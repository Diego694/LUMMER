package pe.registroacademico.nativo.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.HorarioEfectivo
import java.security.MessageDigest

class QuioscoLogicTest {

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("ra-quiosco:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testValidacionPinQuiosco() {
        val pinValido = "4821"
        val hashGenerado = hashPin(pinValido)

        assertEquals(hashPin("4821"), hashGenerado)
        assertTrue(hashPin("0000") != hashGenerado)
    }

    @Test
    fun testSalidaPermitidaMinutosPermanencia() {
        // Ingreso a las 08:00 con 120 minutos de permanencia mínima
        val salida1 = CalendarioUtils.salidaPermitida("08:00", "09:30", permanenciaMin = 120)
        assertFalse(salida1.ok)
        assertEquals("10:00", salida1.desde)

        val salida2 = CalendarioUtils.salidaPermitida("08:00", "10:00", permanenciaMin = 120)
        assertTrue(salida2.ok)

        val salida3 = CalendarioUtils.salidaPermitida("08:00", "11:15", permanenciaMin = 120)
        assertTrue(salida3.ok)
    }

    @Test
    fun testVentanaIngresoHorario() {
        val horario = HorarioEfectivo(
            definido = true,
            ingreso = "08:00",
            tolerancia = 15,
            limite = "08:15",
            salida = "13:00",
            desde = "07:30",
            hasta = "09:30",
            permanencia = 120
        )

        assertEquals("temprano", CalendarioUtils.estadoIngreso(horario, "07:15"))
        assertEquals("puntual", CalendarioUtils.estadoIngreso(horario, "07:45"))
        assertEquals("puntual", CalendarioUtils.estadoIngreso(horario, "08:15"))
        assertEquals("tarde", CalendarioUtils.estadoIngreso(horario, "08:30"))
        assertEquals("cerrado", CalendarioUtils.estadoIngreso(horario, "09:45"))
    }
}
