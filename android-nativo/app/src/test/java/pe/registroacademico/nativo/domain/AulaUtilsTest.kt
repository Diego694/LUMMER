package pe.registroacademico.nativo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoEntrega

class AulaUtilsTest {

    @Test
    fun validarArchivoAula_archivosValidos_devuelveNull() {
        val extensionesValidas = listOf(
            "guia.pdf", "notas.xls", "cuadro.xlsx", "doc.doc", "informe.docx",
            "presentacion.ppt", "diapo.pptx", "codigo.txt", "archivos.zip",
            "foto.jpg", "imagen.jpeg", "captura.png", "web.webp"
        )
        for (nombre in extensionesValidas) {
            val resultado = AulaUtils.validarArchivoAula(nombre, 1024 * 500)
            assertNull("El archivo $nombre debería ser válido pero dio: $resultado", resultado)
        }
    }

    @Test
    fun validarArchivoAula_tipoNoPermitido_devuelveError() {
        val extensionesInvalidas = listOf("script.exe", "test.sh", "app.apk", "datos.json", "pagina.html")
        for (nombre in extensionesInvalidas) {
            val error = AulaUtils.validarArchivoAula(nombre, 1024)
            assertNotNull("El archivo $nombre debería rechazarse", error)
            assertTrue(error!!.contains("Tipo de archivo no permitido"))
        }
    }

    @Test
    fun validarArchivoAula_archivoVacio_devuelveError() {
        val error = AulaUtils.validarArchivoAula("documento.pdf", 0L)
        assertNotNull(error)
        assertEquals("El archivo está vacío.", error)
    }

    @Test
    fun validarArchivoAula_archivoExcedeLimite_devuelveError() {
        val limiteMasUno = (10L * 1024L * 1024L) + 1L
        val error = AulaUtils.validarArchivoAula("manual_pesado.pdf", limiteMasUno)
        assertNotNull(error)
        assertTrue(error!!.contains("el máximo es 10 MB"))
    }

    @Test
    fun rutaArchivoAula_limpiaNombreYFormateaCorrectamente() {
        val colegioId = "col-123"
        val cursoId = "cur-456"
        val fixedUid = "uuid-test"

        val ruta = AulaUtils.rutaArchivoAula(
            colegioId = colegioId,
            cursoId = cursoId,
            nombre = "Sílabo de Matemáticas (2026).pdf",
            uid = fixedUid
        )

        assertEquals("col-123/cur-456/uuid-test-Silabo-de-Matematicas-2026.pdf", ruta)
    }

    @Test
    fun rutaEntrega_formatoPrivadoEstudiante() {
        val colegioId = "col-123"
        val cursoId = "cur-456"
        val userId = "user-789"
        val fixedUid = "uuid-test"

        val ruta = AulaUtils.rutaEntrega(
            colegioId = colegioId,
            cursoId = cursoId,
            userId = userId,
            nombre = "Tarea 1 - Final.docx",
            uid = fixedUid
        )

        assertEquals("col-123/cur-456/entregas/user-789/uuid-test-Tarea-1-Final.docx", ruta)
    }

    @Test
    fun validarNota_valoresValidos_devuelveNull() {
        assertNull(AulaUtils.validarNota(0.0, 20.0))
        assertNull(AulaUtils.validarNota(10.5, 20.0))
        assertNull(AulaUtils.validarNota(20.0, 20.0))
        assertNull(AulaUtils.validarNota(50.0, 100.0))
    }

    @Test
    fun validarNota_valoresInvalidos_devuelveError() {
        assertNotNull(AulaUtils.validarNota(-1.0, 20.0))
        assertNotNull(AulaUtils.validarNota(21.0, 20.0))
        assertNotNull(AulaUtils.validarNota(null, 20.0))
        assertNotNull(AulaUtils.validarNota(Double.NaN, 20.0))
    }

    @Test
    fun periodoDe_y_periodosDe_comportamientoCorrecto() {
        assertEquals(1, AulaUtils.periodoDe(null))
        assertEquals(1, AulaUtils.periodoDe(0))
        assertEquals(3, AulaUtils.periodoDe(3))
        assertEquals(1, AulaUtils.periodoDe(9)) // mayor que PERIODOS_MAX

        val actividades = listOf(
            CursoActividad(id = "1", cursoId = "c1", titulo = "Act 1", periodo = 2),
            CursoActividad(id = "2", cursoId = "c1", titulo = "Act 2", periodo = 1),
            CursoActividad(id = "3", cursoId = "c1", titulo = "Act 3", periodo = 2),
            CursoActividad(id = "4", cursoId = "c1", titulo = "Act 4", periodo = 4)
        )
        val periodos = AulaUtils.periodosDe(actividades)
        assertEquals(listOf(1, 2, 4), periodos)
    }

    @Test
    fun alumnosDelCurso_filtraPorCarreraCicloYActivos() {
        val curso = Curso(id = "c1", nombre = "Redes I", nivel = "Computación", grado = "I CICLO")

        val alumno1 = Alumno(id = "a1", codigo = "001", nombre = "Ana", nivel = "Computación", grado = "I CICLO", estado = "ACTIVO", aprobado = true)
        val alumno2 = Alumno(id = "a2", codigo = "002", nombre = "Beto", nivel = "Computación", grado = "II CICLO", estado = "ACTIVO", aprobado = true)
        val alumno3 = Alumno(id = "a3", codigo = "003", nombre = "Carlos", nivel = "Contabilidad", grado = "I CICLO", estado = "ACTIVO", aprobado = true)
        val alumno4 = Alumno(id = "a4", codigo = "004", nombre = "Diana", nivel = "Computación", grado = "I CICLO", estado = "INACTIVO", aprobado = true)
        val alumno5 = Alumno(id = "a5", codigo = "005", nombre = "Elena", nivel = "Computación", grado = "I CICLO", estado = "ACTIVO", aprobado = false)

        val resultado = AulaUtils.alumnosDelCurso(listOf(alumno1, alumno2, alumno3, alumno4, alumno5), curso)
        assertEquals(listOf(alumno1), resultado)
    }

    @Test
    fun libroNotas_calculaPromedioSobre20SoloConCalificadas() {
        val alumno = Alumno(id = "a1", codigo = "001", nombre = "Juan Pérez", nivel = "Computación", grado = "I")
        val act1 = CursoActividad(id = "act1", cursoId = "c1", titulo = "Práctica 1", puntajeMax = 20.0)
        val act2 = CursoActividad(id = "act2", cursoId = "c1", titulo = "Proyecto", puntajeMax = 20.0)
        val act3 = CursoActividad(id = "act3", cursoId = "c1", titulo = "Examen Final", puntajeMax = 20.0)

        // Estudiante solo entregó y tiene calificada la práctica 1 con 18 y el proyecto con 14; act3 está pendiente
        val entregas = listOf(
            CursoEntrega(id = "e1", actividadId = "act1", alumnoId = "a1", nota = 18.0),
            CursoEntrega(id = "e2", actividadId = "act2", alumnoId = "a1", nota = 14.0)
        )

        val libro = AulaUtils.libroNotas(listOf(alumno), listOf(act1, act2, act3), entregas)
        assertEquals(1, libro.size)

        val fila = libro.first()
        assertEquals(18.0, fila.notas["act1"])
        assertEquals(14.0, fila.notas["act2"])
        assertNull(fila.notas["act3"])

        // Suma notas = 18 + 14 = 32. Suma puntajes máximos calificados = 20 + 20 = 40.
        // Promedio sobre 20 = (32 / 40) * 20 = 16.0
        assertEquals(16.0, fila.promedio)
    }

    @Test
    fun libroNotas_sinNotasCalificadas_promedioEsNull() {
        val alumno = Alumno(id = "a1", codigo = "001", nombre = "Juan", nivel = "Computación", grado = "I")
        val act1 = CursoActividad(id = "act1", cursoId = "c1", titulo = "Actividad 1", puntajeMax = 20.0)

        val libro = AulaUtils.libroNotas(listOf(alumno), listOf(act1), emptyList())
        assertNull(libro.first().promedio)
    }

    @Test
    fun generarCsvNotas_contieneBOM_y_columnasCorrectas() {
        val alumno = Alumno(id = "a1", codigo = "001", nombre = "María Ramos", nivel = "Computación", grado = "I")
        val act = CursoActividad(id = "act1", cursoId = "c1", titulo = "Tarea 1", puntajeMax = 20.0)
        val entregas = listOf(CursoEntrega(id = "e1", actividadId = "act1", alumnoId = "a1", nota = 17.5))

        val libro = AulaUtils.libroNotas(listOf(alumno), listOf(act), entregas)
        val csv = AulaUtils.generarCsvNotas(listOf(act), libro)

        assertTrue("El CSV debe comenzar con el BOM UTF-8", csv.startsWith("\uFEFF"))
        assertTrue("Debe incluir la columna Estudiante", csv.contains("\"Estudiante\""))
        assertTrue("Debe incluir el título de la actividad", csv.contains("\"Tarea 1 (/20)\""))
        assertTrue("Debe incluir la columna de Promedio", csv.contains("\"Promedio (/20)\""))
        assertTrue("Debe contener los datos del alumno", csv.contains("\"María Ramos\""))
        assertTrue("Debe contener la nota", csv.contains("\"17.50\"") || csv.contains("\"17.5\""))
    }
}
