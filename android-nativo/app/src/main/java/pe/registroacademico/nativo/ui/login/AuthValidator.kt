package pe.registroacademico.nativo.ui.login

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errorMessage: String) : ValidationResult
}

object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("El correo electrónico es obligatorio")
        }
        if (!EMAIL_REGEX.matches(trimmed)) {
            return ValidationResult.Invalid("Formato de correo electrónico inválido")
        }
        return ValidationResult.Valid
    }

    fun validatePassword(password: String): ValidationResult {
        if (password.isEmpty()) {
            return ValidationResult.Invalid("La contraseña es obligatoria")
        }
        if (password.length < 6) {
            return ValidationResult.Invalid("La contraseña debe tener al menos 6 caracteres")
        }
        return ValidationResult.Valid
    }

    fun validateCredentials(email: String, password: String): ValidationResult {
        val emailRes = validateEmail(email)
        if (emailRes is ValidationResult.Invalid) return emailRes
        val passRes = validatePassword(password)
        if (passRes is ValidationResult.Invalid) return passRes
        return ValidationResult.Valid
    }
}
