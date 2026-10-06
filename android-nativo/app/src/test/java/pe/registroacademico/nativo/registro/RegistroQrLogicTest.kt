package pe.registroacademico.nativo.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.domain.CalendarioUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.qr.AlumnoQr
import pe.registroacademico.nativo.domain.qr.MotivoRechazo
import pe.registroacademico.nativo.domain.qr.QrResultado
import pe.registroacademico.nativo.domain.qr.generarQR
import pe.registroacademico.nativo.domain.qr.verificarQR

class RegistroQrLogicTest {

    @Test
    fun testQrDinamicoValidoGeneradoYVerificado() {
        val secreto = "clave-super-secreta-12345"
        val codigo = "a1001"
        val ms = 1700000000000L

        val qrTexto = generarQR(codigo, secreto, ms)
        assertTrue(qrTexto.startsWith("a1001."))

        val resultado = verificarQR(
            texto = qrTexto,
            buscarPorCodigo = { cod ->
                if (cod == codigo) AlumnoQr(id = "1", codigo = cod, qrSecreto = secreto) else null
            },
            ms = ms
        )

        assertTrue(resultado is QrResultado.Ok)
        assertEquals(codigo, (resultado as QrResultado.Ok).alumno.codigo)
    }

    @Test
    fun testQrDinamicoVencidoFueraDeTolerancia() {
        val secreto = "clave-super-secreta-12345"
        val codigo = "a1002"
        val msCreacion = 1700000000000L
        val msValidacion = msCreacion + 120_000L // 2 minutos después (4 ventanas)

        val qrTexto = generarQR(codigo, secreto, msCreacion)

        val resultado = verificarQR(
            texto = qrTexto,
            buscarPorCodigo = { cod ->
                if (cod == codigo) AlumnoQr(id = "2", codigo = cod, qrSecreto = secreto) else null
            },
            ms = msValidacion
        )

        assertTrue(resultado is QrResultado.Rechazo)
        assertEquals(MotivoRechazo.VENCIDO, (resultado as QrResultado.Rechazo).motivo)
    }

    @Test
    fun testQrEstatico() {
        val codigo = "a2001"
        val resultado = verificarQR(
            texto = codigo,
            buscarPorCodigo = { cod ->
                if (cod == codigo) AlumnoQr(id = "3", codigo = cod, qrSecreto = null) else null
            },
            ms = 1700000000000L
        )

        assertTrue(resultado is QrResultado.Estatico)
    }

    @Test
    fun testEvaluacionTardanzaSegunHorario() {
        val limite = "08:15"
        assertFalse(StatsUtils.esTardanza("08:00", limite))
        assertFalse(StatsUtils.esTardanza("08:15", limite))
        assertTrue(StatsUtils.esTardanza("08:16", limite))
        assertTrue(StatsUtils.esTardanza("09:00", limite))
    }

    @Test
    fun testLogicaAccionIngresoYSalida() {
        val horaIngreso = "08:00"
        val reg = Asistencia(alumnoId = "1", fecha = "2026-10-05", hora = horaIngreso)

        // Minutos después del ingreso: antes de permanencia (ej. 120 min)
        val accionTemprana = StatsUtils.decidirAccion(reg, "09:30", minPermanencia = 120)
        assertEquals("ya_ingreso", accionTemprana)

        // Pasado el tiempo de permanencia: habilitada la salida
        val accionSalida = StatsUtils.decidirAccion(reg, "10:30", minPermanencia = 120)
        assertEquals("salida", accionSalida)

        // Con salida ya registrada
        val regConSalida = reg.copy(horaSalida = "10:30")
        val accionDupSalida = StatsUtils.decidirAccion(regConSalida, "11:00", minPermanencia = 120)
        assertEquals("dup_salida", accionDupSalida)
    }
}
