package pe.registroacademico.nativo.ui.estudiante

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.estudiante.EstadoQrIngreso
import pe.registroacademico.nativo.data.estudiante.QrIngresoRespuestaDto
import pe.registroacademico.nativo.data.estudiante.ResultadoQrIngreso
import pe.registroacademico.nativo.data.estudiante.formatearTextoExitoIngreso
import pe.registroacademico.nativo.data.estudiante.mapearResultadoQrIngreso
import pe.registroacademico.nativo.data.estudiante.mensajeErrorIngreso
import pe.registroacademico.nativo.domain.estudiante.FlujoQr
import pe.registroacademico.nativo.domain.estudiante.seleccionarFlujoQr

class EscanearIngresoTest {

    @Test
    fun mapearResultadoQrIngreso_okPuntual_mapeaCorrectamente() {
        val dto = QrIngresoRespuestaDto(
            estado = "ok",
            hora = "07:55:00",
            tarde = false,
            nombre = "Carlos Perez"
        )
        val resultado = mapearResultadoQrIngreso(dto)

        assertTrue(resultado is ResultadoQrIngreso.Ok)
        assertEquals(EstadoQrIngreso.OK, resultado.estado)
        assertEquals("07:55:00", resultado.hora)
        assertFalse(resultado.tarde)
        assertEquals("Carlos Perez", resultado.nombre)
        assertEquals("Ingreso registrado a las 07:55:00", formatearTextoExitoIngreso(resultado))
    }

    @Test
    fun mapearResultadoQrIngreso_okConTardanza_mapeaCorrectamente() {
        val dto = QrIngresoRespuestaDto(
            estado = "ok",
            hora = "08:15:30",
            tarde = true,
            nombre = "Maria Sanchez"
        )
        val resultado = mapearResultadoQrIngreso(dto)

        assertTrue(resultado is ResultadoQrIngreso.Ok)
        assertEquals(EstadoQrIngreso.OK, resultado.estado)
        assertEquals("08:15:30", resultado.hora)
        assertTrue(resultado.tarde)
        assertEquals("Ingreso registrado a las 08:15:30 · con tardanza", formatearTextoExitoIngreso(resultado))
    }

    @Test
    fun mapearResultadoQrIngreso_yaMarcado_mapeaCorrectamente() {
        val dto = QrIngresoRespuestaDto(
            estado = "ya_marcado",
            hora = "07:50:10",
            tarde = false,
            nombre = "Carlos Perez"
        )
        val resultado = mapearResultadoQrIngreso(dto)

        assertTrue(resultado is ResultadoQrIngreso.YaMarcado)
        assertEquals(EstadoQrIngreso.YA_MARCADO, resultado.estado)
        assertEquals("07:50:10", resultado.hora)
        assertEquals("Ya registraste tu ingreso a las 07:50:10", formatearTextoExitoIngreso(resultado))
    }

    @Test
    fun mapearResultadoQrIngreso_estadosErrorContrato_mapeaCorrectamente() {
        val vencido = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "vencido"))
        assertTrue(vencido is ResultadoQrIngreso.Vencido)
        assertEquals("El código venció, escanea el que se muestra ahora", mensajeErrorIngreso(vencido))

        val cerrado = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "cerrado"))
        assertTrue(cerrado is ResultadoQrIngreso.Cerrado)
        assertEquals("El registro de ingreso está cerrado", mensajeErrorIngreso(cerrado))

        val cerradoIngreso = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "cerrado_ingreso"))
        assertTrue(cerradoIngreso is ResultadoQrIngreso.Cerrado)
        assertEquals("El registro de ingreso está cerrado", mensajeErrorIngreso(cerradoIngreso))

        val temprano = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "temprano"))
        assertTrue(temprano is ResultadoQrIngreso.Temprano)
        assertEquals("Fuera del horario de ingreso", mensajeErrorIngreso(temprano))

        val fueraHorario = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "fuera_horario"))
        assertTrue(fueraHorario is ResultadoQrIngreso.FueraHorario)
        assertEquals("Fuera del horario de ingreso", mensajeErrorIngreso(fueraHorario))

        val noPertenece = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "no_pertenece"))
        assertTrue(noPertenece is ResultadoQrIngreso.NoPertenece)
        assertEquals("Este código QR no corresponde a tu colegio", mensajeErrorIngreso(noPertenece))

        val invalido = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "invalido"))
        assertTrue(invalido is ResultadoQrIngreso.Invalido)
        assertEquals("Código QR de ingreso no válido", mensajeErrorIngreso(invalido))

        val pendiente = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "pendiente"))
        assertTrue(pendiente is ResultadoQrIngreso.Pendiente)
        assertEquals("Tu registro de estudiante está pendiente de aprobación", mensajeErrorIngreso(pendiente))

        val inactivo = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "inactivo"))
        assertTrue(inactivo is ResultadoQrIngreso.Inactivo)
        assertEquals("Tu cuenta de estudiante se encuentra inactiva", mensajeErrorIngreso(inactivo))

        val desconocido = mapearResultadoQrIngreso(QrIngresoRespuestaDto(estado = "otro_no_reconocido"))
        assertTrue(desconocido is ResultadoQrIngreso.Desconocido)
        assertEquals("No se pudo registrar el ingreso", mensajeErrorIngreso(desconocido))
    }

    @Test
    fun seleccionarFlujoQr_discriminacionPuraPorPrefijo() {
        assertEquals(FlujoQr.INGRESO, seleccionarFlujoQr("LI.tokenDeIngreso"))
        assertEquals(FlujoQr.INGRESO, seleccionarFlujoQr("   LI.tokenDeIngreso   "))
        assertEquals(FlujoQr.CURSO, seleccionarFlujoQr("LQ.tokenDeCurso"))
        assertEquals(FlujoQr.CURSO, seleccionarFlujoQr("   LQ.tokenDeCurso   "))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr("OTRO.texto"))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr(""))
        assertEquals(FlujoQr.DESCONOCIDO, seleccionarFlujoQr(null))
    }
}
