package pe.registroacademico.nativo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermisosTest {

    @Test
    fun testAccionesAdminContieneAulaDocentes() {
        assertTrue(
            "ACCIONES_ADMIN debe contener aula-docentes según la especificación web",
            Permisos.ACCIONES_ADMIN.contains("aula-docentes")
        )
    }

    @Test
    fun testPuedeAulaDocentesSoloAdmin() {
        // Administrador puede gestionar docentes del aula
        assertTrue(Permisos.puede("aula-docentes", Rol.ADMIN))
        assertTrue(Permisos.puede("aula-docentes", "administrador"))
        assertTrue(Permisos.puede("aula-docentes", "admin"))

        // Docente no puede gestionar docentes del aula
        assertFalse(Permisos.puede("aula-docentes", Rol.DOCENTE))
        assertFalse(Permisos.puede("aula-docentes", "docente"))

        // Coordinador no puede gestionar docentes del aula
        assertFalse(Permisos.puede("aula-docentes", Rol.COORDINADOR))
        assertFalse(Permisos.puede("aula-docentes", "coordinador"))
    }

    @Test
    fun testAccionesLibresPermitidasParaTodos() {
        // Acciones que no son solo de admin
        assertTrue(Permisos.puede("pasar-lista", Rol.DOCENTE))
        assertTrue(Permisos.puede("pasar-lista", Rol.ADMIN))
        assertTrue(Permisos.puede("pasar-lista", Rol.COORDINADOR))
    }

    @Test
    fun testRolesDesdeString() {
        assertEquals(Rol.ADMIN, Rol.desdeString("admin"))
        assertEquals(Rol.ADMIN, Rol.desdeString("ADMINISTRADOR"))
        assertEquals(Rol.COORDINADOR, Rol.desdeString("coordinador"))
        assertEquals(Rol.DOCENTE, Rol.desdeString("docente"))
        assertEquals(Rol.DOCENTE, Rol.desdeString("otro"))
        assertEquals(Rol.DOCENTE, Rol.desdeString(null))
    }
}
