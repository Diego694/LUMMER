package pe.registroacademico.nativo.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun emailVacio_retornaInvalido() {
        val result = AuthValidator.validateEmail("")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("El correo electrónico es obligatorio", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun emailConEspacios_retornaInvalido() {
        val result = AuthValidator.validateEmail("   ")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("El correo electrónico es obligatorio", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun emailSinArroba_retornaInvalido() {
        val result = AuthValidator.validateEmail("usuarioinstituto.pe")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("Formato de correo electrónico inválido", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun emailSinDominio_retornaInvalido() {
        val result = AuthValidator.validateEmail("usuario@")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("Formato de correo electrónico inválido", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun emailValido_retornaValido() {
        val validEmails = listOf(
            "docente@instituto.pe",
            "admin@colegio.edu.pe",
            "estudiante.2026@dominio.com",
            "demo@instituto.pe"
        )
        for (email in validEmails) {
            val result = AuthValidator.validateEmail(email)
            assertTrue("Debería ser válido: $email", result is ValidationResult.Valid)
        }
    }

    @Test
    fun passwordVacia_retornaInvalido() {
        val result = AuthValidator.validatePassword("")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("La contraseña es obligatoria", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun passwordMenorASeisCaracteres_retornaInvalido() {
        val result = AuthValidator.validatePassword("12345")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("La contraseña debe tener al menos 6 caracteres", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun passwordValida_retornaValido() {
        val validPasswords = listOf(
            "123456",
            "demo1234",
            "claveSegura#2026"
        )
        for (password in validPasswords) {
            val result = AuthValidator.validatePassword(password)
            assertTrue("Debería ser válida: $password", result is ValidationResult.Valid)
        }
    }

    @Test
    fun credencialesCompletasValidas_retornaValido() {
        val result = AuthValidator.validateCredentials("docente@instituto.pe", "demo1234")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun credencialesConCorreoInvalido_retornaInvalido() {
        val result = AuthValidator.validateCredentials("correo-invalido", "demo1234")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("Formato de correo electrónico inválido", (result as ValidationResult.Invalid).errorMessage)
    }

    @Test
    fun credencialesConPasswordInvalida_retornaInvalido() {
        val result = AuthValidator.validateCredentials("docente@instituto.pe", "123")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("La contraseña debe tener al menos 6 caracteres", (result as ValidationResult.Invalid).errorMessage)
    }
}
