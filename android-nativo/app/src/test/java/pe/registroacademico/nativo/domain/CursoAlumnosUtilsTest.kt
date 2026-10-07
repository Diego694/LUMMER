package pe.registroacademico.nativo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.CursoAlumnosUtils
import pe.registroacademico.nativo.domain.OrigenMatricula

class CursoAlumnosUtilsTest {

    private fun alumno(
        id: String,
        nombre: String,
        nivel: String = "APSTI",
        grado: String = "APSTI · I CICLO",
        estado: String = "ACTIVO",
        aprobado: Boolean? = true
    ) = Alumno(
        id = id,
        codigo = "C$id",
        nombre = nombre,
        nivel = nivel,
        grado = grado,
        estado = estado,
        aprobado = aprobado
    )

    private val curso = Curso(
        id = "c1",
        nombre = "Programación Web",
        nivel = "APSTI",
        grado = "APSTI · I CICLO",
        colegioId = "inst1"
    )

    @Test
    fun estudiantesDelCurso_combinaAlumnosPorCicloYManuales() {
        val a1 = alumno("1", "Carlos Mendoza", nivel = "APSTI", grado = "APSTI · I CICLO")
        val a2 = alumno("2", "Ana Torres", nivel = "APSTI", grado = "APSTI · I CICLO")
        val a3 = alumno("3", "Beatriz Quispe", nivel = "ENFERMERIA", grado = "ENFERMERIA · II CICLO")
        val a4 = alumno("4", "David Rojas", nivel = "APSTI", grado = "APSTI · II CICLO")

        val alumnos = listOf(a1, a2, a3, a4)
        val manualesIds = setOf("3") // a3 matriculado manual

        val resultado = CursoAlumnosUtils.estudiantesDelCurso(alumnos, curso, manualesIds)

        // a1, a2 son de ciclo; a3 es manual; a4 no pertenece
        assertEquals(3, resultado.size)
        // Orden alfabético: Ana Torres, Beatriz Quispe, Carlos Mendoza
        assertEquals("Ana Torres", resultado[0].alumno.nombre)
        assertEquals(OrigenMatricula.CICLO, resultado[0].origen)

        assertEquals("Beatriz Quispe", resultado[1].alumno.nombre)
        assertEquals(OrigenMatricula.MANUAL, resultado[1].origen)

        assertEquals("Carlos Mendoza", resultado[2].alumno.nombre)
        assertEquals(OrigenMatricula.CICLO, resultado[2].origen)
    }

    @Test
    fun estudiantesDelCurso_ignoraInactivosONoAprobados() {
        val a1 = alumno("1", "Ana Inactiva", estado = "INACTIVO")
        val a2 = alumno("2", "Beto NoAprobado", aprobado = false)
        val a3 = alumno("3", "Carla ManualInactiva", nivel = "OTRA", estado = "RETIRADO")
        val a4 = alumno("4", "Diana Activa")

        val resultado = CursoAlumnosUtils.estudiantesDelCurso(
            listOf(a1, a2, a3, a4),
            curso,
            setOf("3")
        )

        assertEquals(1, resultado.size)
        assertEquals("Diana Activa", resultado[0].alumno.nombre)
    }

    @Test
    fun candidatosParaAgregar_filtraYaMatriculadosYExcluyeInactivos() {
        val a1 = alumno("1", "Ana Ciclo", nivel = "APSTI", grado = "APSTI · I CICLO") // ya en ciclo
        val a2 = alumno("2", "Beto Manual", nivel = "CONTABILIDAD", grado = "II CICLO") // ya manual
        val a3 = alumno("3", "Carlos Candidato", nivel = "CONTABILIDAD", grado = "I CICLO") // candidato válido
        val a4 = alumno("4", "Daniel Candidato", nivel = "APSTI", grado = "APSTI · II CICLO") // candidato válido de otro ciclo
        val a5 = alumno("5", "Elena Inactiva", nivel = "CONTABILIDAD", estado = "INACTIVO") // inactivo
        val a6 = alumno("6", "Fernando NoAprobado", nivel = "CONTABILIDAD", aprobado = false) // no aprobado

        val alumnos = listOf(a1, a2, a3, a4, a5, a6)
        val manualesIds = setOf("2")

        val candidatos = CursoAlumnosUtils.candidatosParaAgregar(alumnos, curso, manualesIds)

        assertEquals(2, candidatos.size)
        assertEquals("Carlos Candidato", candidatos[0].nombre)
        assertEquals("Daniel Candidato", candidatos[1].nombre)
    }

    @Test
    fun candidatosParaAgregar_retornaVacioSiTodosPertenecen() {
        val a1 = alumno("1", "Ana", nivel = "APSTI", grado = "APSTI · I CICLO")
        val a2 = alumno("2", "Beto", nivel = "APSTI", grado = "APSTI · I CICLO")

        val candidatos = CursoAlumnosUtils.candidatosParaAgregar(listOf(a1, a2), curso, emptySet())
        assertTrue(candidatos.isEmpty())
    }
}
