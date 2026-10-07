package pe.registroacademico.nativo.ui.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso

class PasarListaTest {

    private val curso1 = Curso(
        id = "c1",
        nombre = "Matemática I",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · I CICLO",
        activo = true
    )

    private val curso2 = Curso(
        id = "c2",
        nombre = "Programación Web",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · III CICLO",
        activo = true
    )

    private val cursoInactivo = Curso(
        id = "c3",
        nombre = "Curso Inactivo",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · I CICLO",
        activo = false
    )

    private val alumno1 = Alumno(
        id = "a1",
        codigo = "c001",
        nombre = "Ana Morales",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · I CICLO",
        estado = "ACTIVO",
        aprobado = true
    )

    private val alumno2 = Alumno(
        id = "a2",
        codigo = "c002",
        nombre = "Carlos Betancourt",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · I CICLO",
        estado = "ACTIVO",
        aprobado = true
    )

    private val alumnoOtroCiclo = Alumno(
        id = "a3",
        codigo = "c003",
        nombre = "David Quispe",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · III CICLO",
        estado = "ACTIVO",
        aprobado = true
    )

    private val alumnoInactivo = Alumno(
        id = "a4",
        codigo = "c004",
        nombre = "Elena Silva",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · I CICLO",
        estado = "INACTIVO",
        aprobado = true
    )

    private val alumnoNoAprobado = Alumno(
        id = "a5",
        codigo = "c005",
        nombre = "Franco Ramos",
        nivel = "COMPUTACIÓN",
        grado = "COMPUTACIÓN · I CICLO",
        estado = "ACTIVO",
        aprobado = false
    )

    @Test
    fun testCursosParaPasarListaAdminVeTodosLosActivos() {
        val todos = listOf(curso1, curso2, cursoInactivo)
        val resultado = PasarListaUtils.cursosParaPasarLista(
            cursos = todos,
            esAdmin = true,
            asignados = listOf("c1")
        )

        assertEquals("Admin debe ver todos los activos", 2, resultado.size)
        assertTrue(resultado.contains(curso1))
        assertTrue(resultado.contains(curso2))
    }

    @Test
    fun testCursosParaPasarListaDocenteSoloVeAsignados() {
        val todos = listOf(curso1, curso2, cursoInactivo)
        val resultado = PasarListaUtils.cursosParaPasarLista(
            cursos = todos,
            esAdmin = false,
            asignados = listOf("c1")
        )

        assertEquals("Docente debe ver únicamente sus cursos asignados activos", 1, resultado.size)
        assertEquals(curso1, resultado.first())
    }

    @Test
    fun testCursosParaPasarListaDocenteSinAsignados() {
        val todos = listOf(curso1, curso2)
        val resultado = PasarListaUtils.cursosParaPasarLista(
            cursos = todos,
            esAdmin = false,
            asignados = emptyList()
        )

        assertTrue("Docente sin asignaciones no debe ver ningún curso", resultado.isEmpty())
    }

    @Test
    fun testAlumnosDelCursoFiltraPorCicloYEstado() {
        val alumnos = listOf(alumno1, alumno2, alumnoOtroCiclo, alumnoInactivo, alumnoNoAprobado)
        val filtrados = PasarListaUtils.alumnosDelCurso(alumnos, curso1)

        assertEquals("Solo deben pertenecer activos, aprobados y de ese ciclo", 2, filtrados.size)
        assertEquals("Ana Morales", filtrados[0].nombre)
        assertEquals("Carlos Betancourt", filtrados[1].nombre)
    }

    @Test
    fun testResumenListaCalculaTotalesYPorcentajes() {
        val lista = listOf(alumno1, alumno2)
        val marcados = setOf("a1")

        val resumen = PasarListaUtils.resumenLista(lista, marcados)
        assertEquals(2, resumen.total)
        assertEquals(1, resumen.presentes)
        assertEquals(1, resumen.faltan)
        assertEquals(50, resumen.pct)
    }

    @Test
    fun testResumenListaVacia() {
        val resumen = PasarListaUtils.resumenLista(emptyList(), emptySet())
        assertEquals(0, resumen.total)
        assertEquals(0, resumen.presentes)
        assertEquals(0, resumen.faltan)
        assertEquals(0, resumen.pct)
    }

    @Test
    fun testValidarMarcarReglas() {
        // Alumno válido no marcado -> null (éxito)
        val resValido = PasarListaUtils.validarMarcar(alumno1, curso1, yaMarcado = false)
        assertNull("Debe ser válido para marcar", resValido)

        // Alumno ya marcado -> Advertencia
        val resDup = PasarListaUtils.validarMarcar(alumno1, curso1, yaMarcado = true)
        assertTrue(resDup is ResultadoMarcar.Advertencia)
        assertEquals("Ya estaba marcado en este curso", (resDup as ResultadoMarcar.Advertencia).texto)

        // Alumno no pertenece al ciclo -> Error
        val resNoPertenece = PasarListaUtils.validarMarcar(alumnoOtroCiclo, curso1, yaMarcado = false)
        assertTrue(resNoPertenece is ResultadoMarcar.Error)
        assertEquals("No pertenece a este curso o ciclo", (resNoPertenece as ResultadoMarcar.Error).texto)

        // Alumno inactivo -> Error
        val resInactivo = PasarListaUtils.validarMarcar(alumnoInactivo, curso1, yaMarcado = false)
        assertTrue(resInactivo is ResultadoMarcar.Error)
        assertEquals("Alumno inactivo", (resInactivo as ResultadoMarcar.Error).texto)

        // Alumno no aprobado -> Error
        val resNoAprobado = PasarListaUtils.validarMarcar(alumnoNoAprobado, curso1, yaMarcado = false)
        assertTrue(resNoAprobado is ResultadoMarcar.Error)
        assertEquals("Su registro aún no fue aprobado", (resNoAprobado as ResultadoMarcar.Error).texto)
    }
}
