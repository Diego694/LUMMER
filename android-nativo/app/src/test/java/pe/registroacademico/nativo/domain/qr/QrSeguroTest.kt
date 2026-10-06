package pe.registroacademico.nativo.domain.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para QrSeguro.kt
 * Replican fielmente la suite de pruebas de tests/pwa.js (sección QR dinámico)
 */
class QrSeguroTest {

    private val alumnoQR = AlumnoQr(
        id = "1",
        codigo = "e1234567890",
        qrSecreto = "s3creto-de-prueba"
    )

    private fun buscar(codigo: String): AlumnoQr? {
        return if (codigo == alumnoQR.codigo) alumnoQR else null
    }

    // T0 = 2026-10-05T12:00:00Z en milisegundos (Date.UTC(2026, 9, 5, 12, 0, 0))
    private val t0 = 1791201600000L

    @Test
    fun testQrRecienGeneradoSeVerifica() {
        val q = generarQR(alumnoQR.codigo, alumnoQR.qrSecreto!!, t0)
        assertTrue(q.startsWith("${alumnoQR.codigo}."))
        val partes = q.split(".")
        assertEquals(3, partes.size)

        // Verificación dentro de la misma ventana (+5s)
        val r = verificarQR(q, ::buscar, t0 + 5000L)
        assertTrue("Debe ser Ok", r is QrResultado.Ok)
        val ok = r as QrResultado.Ok
        assertEquals("1", ok.alumno.id)
    }

    @Test
    fun testSecretoDistintoFallaConFirma() {
        val otro = generarQR(alumnoQR.codigo, "otro-secreto", t0)
        val r = verificarQR(otro, ::buscar, t0)
        assertTrue("Debe ser Rechazo", r is QrResultado.Rechazo)
        val rechazo = r as QrResultado.Rechazo
        assertEquals(MotivoRechazo.FIRMA, rechazo.motivo)
    }

    @Test
    fun testToleranciaVentanaYVencimiento() {
        val q = generarQR(alumnoQR.codigo, alumnoQR.qrSecreto!!, t0)

        // Con TOLERANCIA = 1, a 1 ventana (+30s) todavía es válido
        val rValido = verificarQR(q, ::buscar, t0 + VENTANA_MS)
        assertTrue("A 1 ventana aún debe ser válido con TOLERANCIA=1", rValido is QrResultado.Ok)

        // Desfase mayor a 1 ventana (e.g. 5 ventanas) debe dar 'vencido'
        val tarde = verificarQR(q, ::buscar, t0 + 5 * VENTANA_MS)
        assertTrue("Debe rechazar por vencimiento hacia el futuro", tarde is QrResultado.Rechazo)
        val rechazoTarde = tarde as QrResultado.Rechazo
        assertEquals(MotivoRechazo.VENCIDO, rechazoTarde.motivo)
        assertNotNull("Debe incluir datos del alumno", rechazoTarde.alumno)

        // Hacia el pasado (reloj adelantado más allá de tolerancia)
        val futuro = verificarQR(q, ::buscar, t0 - 5 * VENTANA_MS)
        assertTrue("Debe rechazar si es del futuro lejano", futuro is QrResultado.Rechazo)
        assertEquals(MotivoRechazo.VENCIDO, (futuro as QrResultado.Rechazo).motivo)
    }

    @Test
    fun testFirmaAlterada() {
        val q = generarQR(alumnoQR.codigo, alumnoQR.qrSecreto!!, t0)
        val partes = q.split(".")
        val c = partes[0]
        val t = partes[1]
        val s = partes[2]

        // Alterar un carácter de la firma
        val firmaAlterada = if (s.startsWith("a")) "b" + s.substring(1) else "a" + s.substring(1)
        val rFirmaAlt = verificarQR("$c.$t.$firmaAlterada", ::buscar, t0)
        assertTrue("Debe rechazar por firma alterada", rFirmaAlt is QrResultado.Rechazo)
        assertEquals(MotivoRechazo.FIRMA, (rFirmaAlt as QrResultado.Rechazo).motivo)

        // Alterar la ventana manteniendo la firma vieja
        val ventanaAlterada = java.lang.Long.toString(ventana(t0) + 1, 36)
        val rVentanaAlt = verificarQR("$c.$ventanaAlterada.$s", ::buscar, t0)
        assertTrue("Debe rechazar por firma incompatible al cambiar ventana", rVentanaAlt is QrResultado.Rechazo)
        assertEquals(MotivoRechazo.FIRMA, (rVentanaAlt as QrResultado.Rechazo).motivo)
    }

    @Test
    fun testAlumnoDesconocido() {
        val r = verificarQR("noexiste.abc.1234567890", ::buscar, t0)
        assertTrue("Debe rechazar por desconocido", r is QrResultado.Rechazo)
        assertEquals(MotivoRechazo.DESCONOCIDO, (r as QrResultado.Rechazo).motivo)
    }

    @Test
    fun testCodigosEstaticos() {
        // Código simple sin formato dinámico
        val r1 = verificarQR(alumnoQR.codigo, ::buscar, t0)
        assertEquals(QrResultado.Estatico, r1)

        // Texto aleatorio sin 3 partes
        val r2 = verificarQR("lo-que-sea", ::buscar, t0)
        assertEquals(QrResultado.Estatico, r2)
    }

    @Test
    fun testAlumnoSinSecreto() {
        val q = generarQR(alumnoQR.codigo, alumnoQR.qrSecreto!!, t0)
        val sinSecreto = alumnoQR.copy(qrSecreto = null)
        val r = verificarQR(q, { if (it == alumnoQR.codigo) sinSecreto else null }, t0)
        assertTrue("Debe rechazar por sin secreto", r is QrResultado.Rechazo)
        assertEquals(MotivoRechazo.SIN_SECRETO, (r as QrResultado.Rechazo).motivo)
    }

    @Test
    fun testVectorDePruebaDeterminista() {
        // Vector determinista con algoritmo HMAC-SHA256
        val codigo = "est-2026-001"
        val secreto = "clave-secreta-instituto"
        val timestamp = 1791201600000L // w = 59706720 = "zjq00" en base 36
        val qrGenerado = generarQR(codigo, secreto, timestamp)

        assertEquals("est-2026-001.zjq00.", qrGenerado.substring(0, 19))
        val firmaGenerada = qrGenerado.substring(19)
        assertEquals(10, firmaGenerada.length)
        assertTrue(firmaGenerada.matches(Regex("[0-9a-f]{10}")))

        // Validar que se verifica correctamente
        val alumnoTest = AlumnoQr(id = "101", codigo = codigo, qrSecreto = secreto)
        val r = verificarQR(qrGenerado, { if (it == codigo) alumnoTest else null }, timestamp)
        assertTrue("El vector debe validarse con éxito", r is QrResultado.Ok)
    }

    @Test
    fun testCalculoSegundosRestantes() {
        // Al inicio exacto de la ventana (rem = 0): 30 segundos restantes
        assertEquals(30L, segundosRestantes(t0))
        // Con 10 segundos transcurridos (rem = 10000): 20 segundos restantes
        assertEquals(20L, segundosRestantes(t0 + 10_000L))
        // A falta de 500 ms (rem = 29500): 1 segundo restante
        assertEquals(1L, segundosRestantes(t0 + 29_500L))
    }
}
