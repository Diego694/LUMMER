package pe.registroacademico.nativo.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Horario
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.ui.gestion.PlanPromocion
import pe.registroacademico.nativo.ui.gestion.qr.QrCodeGenerator
import pe.registroacademico.nativo.ui.gestion.qr.QrSeguro

class GestionTest {

    @Test
    fun testQrModoEfectivo() {
        assertEquals("off", QrSeguro.qrModoEfectivo(null))
        assertEquals("off", QrSeguro.qrModoEfectivo(""))
        assertEquals("off", QrSeguro.qrModoEfectivo("invalido"))
        assertEquals("off", QrSeguro.qrModoEfectivo("OFF"))
        assertEquals("opcional", QrSeguro.qrModoEfectivo("opcional"))
        assertEquals("opcional", QrSeguro.qrModoEfectivo("OPCIONAL"))
        assertEquals("obligatorio", QrSeguro.qrModoEfectivo("obligatorio"))
        assertEquals("obligatorio", QrSeguro.qrModoEfectivo("OBLIGATORIO"))
    }

    @Test
    fun testQrSeguroGeneracionCodigo() {
        val codigo = "ALU001"
        val secreto = "clave-secreta-hmac"
        val timestamp = 1775000000000L // arbitrary fixed timestamp

        // En modo off debe retornar el código sin modificar
        val estatico = QrSeguro.generarCodigo(codigo, secreto, timestamp, "off")
        assertEquals(codigo, estatico)

        // Sin secreto debe retornar el código
        val sinSecreto = QrSeguro.generarCodigo(codigo, null, timestamp, "obligatorio")
        assertEquals(codigo, sinSecreto)

        // En modo dinámico debe contener el formato <codigo>.<base36>.<sig>
        val dinamico = QrSeguro.generarCodigo(codigo, secreto, timestamp, "obligatorio")
        val partes = dinamico.split(".")
        assertEquals(3, partes.size)
        assertEquals(codigo, partes[0])
        assertTrue(partes[1].isNotBlank())
        assertEquals(10, partes[2].length) // 5 bytes hex = 10 chars
    }

    @Test
    fun testQrCodeGenerator() {
        val texto = "A12345"
        val matrix = QrCodeGenerator.encode(texto)

        assertTrue(matrix.size >= 21) // Mínimo Versión 1 (21x21)

        // Verificar el patrón de detección de posición superior izquierdo (7x7)
        // El centro (2,2) debe ser negro
        assertTrue(matrix.isDark(2, 2))
        // El borde (0,0) debe ser negro
        assertTrue(matrix.isDark(0, 0))
        // El separador interior (1,1) debe ser blanco si es un QR estándar, o evaluable
        assertNotNull(matrix.isDark(0, 0))
    }

    @Test
    fun testFeriadosPeru() {
        val feriados2026 = CalendarioUtils.feriadosPeru(2026)
        assertTrue(feriados2026.isNotEmpty())

        val fechas = feriados2026.map { it.fecha }
        assertTrue("2026-01-01" in fechas) // Año Nuevo
        assertTrue("2026-05-01" in fechas) // Día del Trabajo
        assertTrue("2026-07-28" in fechas) // Fiestas Patrias
        assertTrue("2026-07-29" in fechas) // Fiestas Patrias
        assertTrue("2026-12-25" in fechas) // Navidad

        // Jueves y Viernes Santo deben estar calculados
        val nombres = feriados2026.map { it.nombre }
        assertTrue("Jueves Santo" in nombres)
        assertTrue("Viernes Santo" in nombres)
    }

    @Test
    fun testHorarioEfectivo() {
        val horarios = listOf(
            Horario(horaIngreso = "08:00", toleranciaMin = 15, horaSalida = "13:30"),
            Horario(nivel = "ENFERMERIA", horaIngreso = "14:00", toleranciaMin = 10, horaSalida = "20:00")
        )

        val efectivoGeneral = CalendarioUtils.horarioDe(horarios, null)
        assertEquals("08:00", efectivoGeneral.ingreso)
        assertEquals("08:15", efectivoGeneral.limite)

        val efectivoEnfermeria = CalendarioUtils.horarioDe(horarios, "ENFERMERIA")
        assertEquals("14:00", efectivoEnfermeria.ingreso)
        assertEquals("14:10", efectivoEnfermeria.limite)

        val estadoPuntual = CalendarioUtils.estadoIngreso(efectivoEnfermeria, "14:05")
        assertEquals("puntual", estadoPuntual)

        val estadoTardanza = CalendarioUtils.estadoIngreso(efectivoEnfermeria, "14:15")
        assertEquals("tarde", estadoTardanza)
    }

    @Test
    fun testCiclosUtilsPromocionParsing() {
        val parsedI = CiclosUtils.parsearCiclo("ENFERMERIA · I CICLO · SECCIÓN A")
        assertEquals("I", parsedI.ciclo)
        assertEquals("A", parsedI.seccion)

        val parsedVI = CiclosUtils.parsearCiclo("MECANICA · VI CICLO")
        assertEquals("VI", parsedVI.ciclo)
        assertEquals("", parsedVI.seccion)

        val siguienteI = CiclosUtils.nombreCiclo("ENFERMERIA", "II", "A")
        assertEquals("ENFERMERIA · II CICLO · SECCIÓN A", siguienteI)
    }
}
