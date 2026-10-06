package pe.registroacademico.nativo.consultas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Asistencia
import pe.registroacademico.nativo.data.model.Justificacion
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.RiesgoUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils

class ConsultasTest {

    @Test
    fun testMatrizAsistenciaTardanzaCuentaComoPresente() {
        val alumno = Alumno(
            id = "alu-1",
            codigo = "A001",
            nombre = "Juan Perez",
            nivel = "Computación",
            grado = "II CICLO"
        )
        // Two dates: day 1 punctual (07:50), day 2 tardanza (08:30)
        val asistencias = listOf(
            Asistencia(id = "as-1", alumnoId = "alu-1", fecha = "2026-10-01", hora = "07:50"),
            Asistencia(id = "as-2", alumnoId = "alu-1", fecha = "2026-10-02", hora = "08:30")
        )
        val dias = listOf("2026-10-01", "2026-10-02")
        val limite = "08:00"

        val resultado = StatsUtils.matrizAsistencia(
            alumnos = listOf(alumno),
            asistencias = asistencias,
            justificaciones = emptyList(),
            dias = dias,
            limite = limite
        )

        assertEquals(1, resultado.filas.size)
        val fila = resultado.filas[0]
        assertEquals("P", fila.celdas["2026-10-01"])
        assertEquals("T", fila.celdas["2026-10-02"])

        // REQUISITO: Tardanza cuenta como presente -> p = 2 (p++ siempre que hay hora)
        assertEquals(2, fila.p)
        assertEquals(1, fila.t)
        assertEquals(0, fila.f)
        assertEquals(100, fila.pct)
    }

    @Test
    fun testMatrizAsistenciaFaltasYJustificaciones() {
        val alumno = Alumno(
            id = "alu-2",
            codigo = "A002",
            nombre = "Maria Lopez",
            nivel = "Administración",
            grado = "I CICLO"
        )
        val asistencias = listOf(
            Asistencia(id = "as-3", alumnoId = "alu-2", fecha = "2026-10-01", hora = "07:55")
        )
        val justificaciones = listOf(
            Justificacion(id = "ju-1", alumnoId = "alu-2", fecha = "2026-10-02", tipo = "Salud")
        )
        val dias = listOf("2026-10-01", "2026-10-02")

        val resultado = StatsUtils.matrizAsistencia(
            alumnos = listOf(alumno),
            asistencias = asistencias,
            justificaciones = justificaciones,
            dias = dias,
            limite = "08:00"
        )

        val fila = resultado.filas[0]
        assertEquals("P", fila.celdas["2026-10-01"])
        assertEquals("J", fila.celdas["2026-10-02"])
        assertEquals(1, fila.p)
        assertEquals(1, fila.j)
        assertEquals(0, fila.f)
        assertEquals(50, fila.pct)
        assertEquals(100, fila.pctJust)
    }

    @Test
    fun testEnlaceWhatsApp() {
        val enlace = StatsUtils.enlaceWhatsApp("987654321", "Hola prueba")
        assertTrue(enlace.startsWith("https://wa.me/51987654321?text="))
        assertTrue(enlace.contains("Hola"))
    }

    @Test
    fun testMensajeAvisoPlantilla() {
        val plantilla = "Estimado apoderado: {alumno} tuvo {tipo} en {instituto}."
        val datos = mapOf(
            "alumno" to "Carlos Soto",
            "tipo" to "Falta",
            "instituto" to "IESTP Central"
        )
        val msg = StatsUtils.mensajeAviso(plantilla, datos)
        assertEquals("Estimado apoderado: Carlos Soto tuvo Falta en IESTP Central.", msg)
    }

    @Test
    fun testRiesgoInasistencia() {
        val alumno = Alumno(
            id = "alu-3",
            codigo = "A003",
            nombre = "Pedro Castillo",
            nivel = "Contabilidad",
            grado = "III CICLO"
        )
        // Del 1 al 10 de octubre de 2026: 2026-10-03, 04 y 10 son fines de semana.
        // Días lectivos (sin fin de semana) = 7 (1, 2, 5, 6, 7, 8, 9).
        // Con asistencias los días 1..6, los lectivos asistidos son 1, 2, 5, 6 (4 presentes).
        // Faltas = 7 - 4 = 3 días (7, 8, 9).
        // pctFaltas = StringUtils.pct(3, 7) = 43%. Límite = 30% -> nivel "critico".
        val asistencias = (1..6).map { d ->
            val fecha = "2026-10-%02d".format(d)
            Asistencia(alumnoId = "alu-3", fecha = fecha, hora = "08:00")
        }
        val dias = (1..10).map { "2026-10-%02d".format(it) }

        val riesgos = RiesgoUtils.calcularRiesgo(
            alumnos = listOf(alumno),
            asistencias = asistencias,
            justificaciones = emptyList(),
            noLectivos = emptyMap(),
            desde = dias.first(),
            hasta = dias.last(),
            limite = 30
        )

        assertEquals(1, riesgos.size)
        val r = riesgos[0]
        assertEquals(3, r.faltas)
        assertEquals(StringUtils.pct(3, 7), r.pctFaltas)
        assertEquals(43, r.pctFaltas)
        assertEquals("critico", r.nivel)
        assertEquals(0, RiesgoUtils.faltasRestantes(r, 30))
    }

    @Test
    fun testDateUtilsAmericaLima() {
        assertEquals("America/Lima", DateUtils.ZONA_HORARIA)
        val hoy = DateUtils.todayStr()
        assertNotNull(hoy)
        assertTrue(hoy.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }
}
