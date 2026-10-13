package pe.registroacademico.nativo.ui.estudiante

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.estudiante.ActividadEstudiante
import pe.registroacademico.nativo.data.estudiante.CursoEstudiante
import pe.registroacademico.nativo.data.estudiante.EntregaEstudiante
import pe.registroacademico.nativo.data.estudiante.NotaEstudiante
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge

class EstAulaUtilsTest {

    @Test
    fun agruparCursos_agrupaPorNivelGradoYOrdenaAlfabeticamente() {
        val cursos = listOf(
            CursoEstudiante(id = "c1", nivel = "Computación", grado = "II CICLO", nombre = "Base de Datos"),
            CursoEstudiante(id = "c2", nivel = "Computación", grado = "I CICLO", nombre = "Algoritmos"),
            CursoEstudiante(id = "c3", nivel = "Computación", grado = "I CICLO", nombre = "Arquitectura de TI"),
            CursoEstudiante(id = "c4", nivel = "Contabilidad", grado = "I CICLO", nombre = "Costos"),
            CursoEstudiante(id = "c5", nivel = "Computación", grado = null, nombre = "Tutoría Libre")
        )

        val grupos = EstAulaUtils.agruparCursos(cursos)

        // Grupos esperados:
        // 1. Computación · I CICLO (Algoritmos, Arquitectura de TI)
        // 2. Computación · II CICLO (Base de Datos)
        // 3. Computación · Todos los ciclos (Tutoría Libre)
        // 4. Contabilidad · I CICLO (Costos)
        assertEquals(4, grupos.size)

        assertEquals("Computación · I CICLO", grupos[0].etiqueta)
        assertEquals(listOf("Algoritmos", "Arquitectura de TI"), grupos[0].cursos.map { it.nombre })

        assertEquals("Computación · II CICLO", grupos[1].etiqueta)
        assertEquals(listOf("Base de Datos"), grupos[1].cursos.map { it.nombre })

        assertEquals("Computación · Todos los ciclos", grupos[2].etiqueta)
        assertEquals(listOf("Tutoría Libre"), grupos[2].cursos.map { it.nombre })

        assertEquals("Contabilidad · I CICLO", grupos[3].etiqueta)
        assertEquals(listOf("Costos"), grupos[3].cursos.map { it.nombre })
    }

    @Test
    fun agruparCursos_listaVacia_retornaVacio() {
        val grupos = EstAulaUtils.agruparCursos(emptyList())
        assertTrue(grupos.isEmpty())
    }

    @Test
    fun determinarEstadoEntrega_calificadaAprobada_badgeVerde() {
        val act = ActividadEstudiante(id = "act-1", cursoId = "c1", titulo = "Tarea 1", puntajeMax = 20.0)
        val ent = EntregaEstudiante(id = "ent-1", actividadId = "act-1", alumnoId = "a1", nota = 17.0)

        val estado = EstAulaUtils.determinarEstadoEntrega(act, ent)

        assertEquals(EstadoEntregaTipo.CALIFICADA, estado.tipo)
        assertEquals(TipoEstadoBadge.VERDE, estado.tipoBadge)
        assertEquals("Nota: 17 / 20", estado.etiqueta)
        assertTrue(estado.calificada)
    }

    @Test
    fun determinarEstadoEntrega_calificadaDesaprobada_badgeRojo() {
        val act = ActividadEstudiante(id = "act-1", cursoId = "c1", titulo = "Examen", puntajeMax = 20.0)
        val ent = EntregaEstudiante(id = "ent-1", actividadId = "act-1", alumnoId = "a1", nota = 8.5)

        val estado = EstAulaUtils.determinarEstadoEntrega(act, ent)

        assertEquals(EstadoEntregaTipo.CALIFICADA, estado.tipo)
        assertEquals(TipoEstadoBadge.ROJO, estado.tipoBadge)
        assertEquals("Nota: 8.5 / 20", estado.etiqueta)
        assertTrue(estado.calificada)
    }

    @Test
    fun determinarEstadoEntrega_entregadaATiempo_badgeAzul() {
        val act = ActividadEstudiante(id = "act-1", cursoId = "c1", titulo = "Proyecto", puntajeMax = 20.0)
        val ent = EntregaEstudiante(id = "ent-1", actividadId = "act-1", alumnoId = "a1", nota = null, tardia = false)

        val estado = EstAulaUtils.determinarEstadoEntrega(act, ent)

        assertEquals(EstadoEntregaTipo.ENTREGADA, estado.tipo)
        assertEquals(TipoEstadoBadge.AZUL, estado.tipoBadge)
        assertEquals("Entregada", estado.etiqueta)
        assertFalse(estado.esTardia)
        assertFalse(estado.calificada)
    }

    @Test
    fun determinarEstadoEntrega_entregadaTardia_badgeAmbar() {
        val act = ActividadEstudiante(id = "act-1", cursoId = "c1", titulo = "Proyecto", puntajeMax = 20.0)
        val ent = EntregaEstudiante(id = "ent-1", actividadId = "act-1", alumnoId = "a1", nota = null, tardia = true)

        val estado = EstAulaUtils.determinarEstadoEntrega(act, ent)

        assertEquals(EstadoEntregaTipo.ENTREGADA, estado.tipo)
        assertEquals(TipoEstadoBadge.AMBAR, estado.tipoBadge)
        assertEquals("Entregada (Tardía)", estado.etiqueta)
        assertTrue(estado.esTardia)
    }

    @Test
    fun determinarEstadoEntrega_sinEntrega_noVencida_pendiente() {
        val ahora = 1776000000000L // punto de referencia
        val futuroIso = "2026-05-01T23:59:59Z"
        val act = ActividadEstudiante(id = "act-1", cursoId = "c1", titulo = "Informe", fechaLimite = futuroIso)

        val estado = EstAulaUtils.determinarEstadoEntrega(act, null, ahoraEpochMs = ahora)

        assertEquals(EstadoEntregaTipo.PENDIENTE, estado.tipo)
        assertEquals(TipoEstadoBadge.AMBAR, estado.tipoBadge)
        assertEquals("Por entregar", estado.etiqueta)
    }

    @Test
    fun determinarEstadoEntrega_sinEntrega_vencida_rojo() {
        val ahora = 1776000000000L
        val pasadoIso = "2026-01-01T23:59:59Z"
        val act = ActividadEstudiante(id = "act-1", cursoId = "c1", titulo = "Informe", fechaLimite = pasadoIso)

        val estado = EstAulaUtils.determinarEstadoEntrega(act, null, ahoraEpochMs = ahora)

        assertEquals(EstadoEntregaTipo.VENCIDA, estado.tipo)
        assertEquals(TipoEstadoBadge.ROJO, estado.tipoBadge)
        assertEquals("Vencida sin entregar", estado.etiqueta)
    }

    @Test
    fun formatearNota_manejoDeEnterosYDecimales() {
        assertEquals("—", EstAulaUtils.formatearNota(null))
        assertEquals("20", EstAulaUtils.formatearNota(20.0))
        assertEquals("15", EstAulaUtils.formatearNota(15.0))
        assertEquals("14.5", EstAulaUtils.formatearNota(14.5))
        assertEquals("18.75", EstAulaUtils.formatearNota(18.75))
    }

    @Test
    fun formatearPorcentaje_formateaConSimbolo() {
        assertEquals("0%", EstAulaUtils.formatearPorcentaje(null as Double?))
        assertEquals("85%", EstAulaUtils.formatearPorcentaje(85.3))
        assertEquals("90%", EstAulaUtils.formatearPorcentaje(89.6))
        assertEquals("100%", EstAulaUtils.formatearPorcentaje(100))
        assertEquals("0%", EstAulaUtils.formatearPorcentaje(null as Int?))
    }

    @Test
    fun calcularPromedioCurso_calculaPromedioPonderadoSobre20SoloCalificadas() {
        val notas = listOf(
            NotaEstudiante(actividadId = "1", actividadTitulo = "A1", cursoId = "c1", cursoNombre = "C", nota = 18.0, puntajeMax = 20.0),
            NotaEstudiante(actividadId = "2", actividadTitulo = "A2", cursoId = "c1", cursoNombre = "C", nota = 12.0, puntajeMax = 20.0),
            NotaEstudiante(actividadId = "3", actividadTitulo = "A3", cursoId = "c1", cursoNombre = "C", nota = null, puntajeMax = 20.0) // pendiente
        )

        // Suma notas = 30. Suma max = 40. Promedio = (30/40) * 20 = 15.0
        val promedio = EstAulaUtils.calcularPromedioCurso(notas)
        assertEquals(15.0, promedio)
    }

    @Test
    fun calcularPromedioCurso_sinCalificadas_retornaNull() {
        val notas = listOf(
            NotaEstudiante(actividadId = "1", actividadTitulo = "A1", cursoId = "c1", cursoNombre = "C", nota = null, puntajeMax = 20.0)
        )
        assertNull(EstAulaUtils.calcularPromedioCurso(notas))
    }

    @Test
    fun validarArchivo_casosValidosEInvalidos() {
        assertNull(EstAulaUtils.validarArchivo("tarea.pdf", 1024L))
        assertNull(EstAulaUtils.validarArchivo("doc.docx", 2048L))
        assertNull(EstAulaUtils.validarArchivo("foto.png", 5000L))

        assertNotNull(EstAulaUtils.validarArchivo("virus.exe", 1024L))
        assertNotNull(EstAulaUtils.validarArchivo("script.sh", 1024L))
        assertNotNull(EstAulaUtils.validarArchivo("vacio.pdf", 0L))

        val excedeLimite = EstAulaUtils.MAX_ARCHIVO_BYTES + 1L
        val errPesado = EstAulaUtils.validarArchivo("manual_pesado.pdf", excedeLimite)
        assertNotNull(errPesado)
        assertTrue(errPesado!!.contains("el máximo es 10 MB"))
    }
}
