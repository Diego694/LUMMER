package pe.registroacademico.nativo.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AuthRepository
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val uiState: LoginUiState = LoginUiState.Idle
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(LoginFormState())
    val formState: StateFlow<LoginFormState> = _formState.asStateFlow()

    fun onEmailChanged(email: String) {
        _formState.update { it.copy(email = email, emailError = null) }
    }

    fun onPasswordChanged(password: String) {
        _formState.update { it.copy(password = password, passwordError = null) }
    }

    fun togglePasswordVisibility() {
        _formState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun login() {
        val current = _formState.value
        val emailValidation = AuthValidator.validateEmail(current.email)
        val passwordValidation = AuthValidator.validatePassword(current.password)

        val hasEmailError = emailValidation is ValidationResult.Invalid
        val hasPasswordError = passwordValidation is ValidationResult.Invalid

        if (hasEmailError || hasPasswordError) {
            _formState.update {
                it.copy(
                    emailError = (emailValidation as? ValidationResult.Invalid)?.errorMessage,
                    passwordError = (passwordValidation as? ValidationResult.Invalid)?.errorMessage
                )
            }
            return
        }

        _formState.update { it.copy(uiState = LoginUiState.Loading) }

        viewModelScope.launch {
            val result = authRepository.signInWithEmail(current.email.trim(), current.password)
            result.fold(
                onSuccess = {
                    _formState.update { it.copy(uiState = LoginUiState.Success) }
                },
                onFailure = { error ->
                    val rawMsg = error.message.orEmpty()
                    val userFriendlyMsg = when {
                        rawMsg.contains("Invalid login credentials", ignoreCase = true) ||
                        rawMsg.contains("invalid_grant", ignoreCase = true) ->
                            "Correo o contraseña incorrectos"
                        rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                        rawMsg.contains("network", ignoreCase = true) ||
                        rawMsg.contains("ConnectException", ignoreCase = true) ->
                            "Error de conexión con el servidor. Verifica tu internet."
                        rawMsg.isNotBlank() ->
                            rawMsg
                        else ->
                            "No se pudo iniciar sesión. Verifica tus credenciales."
                    }
                    _formState.update { it.copy(uiState = LoginUiState.Error(userFriendlyMsg)) }
                }
            )
        }
    }

    fun resetState() {
        _formState.update { it.copy(uiState = LoginUiState.Idle) }
    }
}
