package pe.registroacademico.nativo.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.data.PerfilRepository
import javax.inject.Inject

data class InicioUiState(
    val isLoading: Boolean = true,
    val userId: String = "",
    val userEmail: String = "",
    val nombre: String = "",
    val rol: String = "",
    val carrera: String? = null,
    val colegioNombre: String = "",
    val esSuperadmin: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class InicioViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val perfilRepository: PerfilRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InicioUiState())
    val uiState: StateFlow<InicioUiState> = _uiState.asStateFlow()

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val user = authRepository.currentUser
            if (user == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se encontró una sesión activa"
                    )
                }
                return@launch
            }

            val userId = user.id
            val userEmail = user.email.orEmpty()

            val perfilResult = perfilRepository.getPerfil(userId)
            val perfil = perfilResult.getOrNull()

            var colegioNombre = ""
            val cid = perfil?.colegioId
            if (cid != null) {
                val colegioResult = perfilRepository.getColegio(cid)
                colegioNombre = colegioResult.getOrNull()?.nombre ?: "Instituto #$cid"
            }

            val esSuper = perfilRepository.esSuperadmin()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    userId = userId,
                    userEmail = userEmail,
                    nombre = perfil?.nombre?.takeIf { n -> n.isNotBlank() } ?: userEmail,
                    rol = perfil?.rol?.takeIf { r -> r.isNotBlank() } ?: "Usuario",
                    carrera = perfil?.carrera?.takeIf { c -> c.isNotBlank() },
                    colegioNombre = colegioNombre,
                    esSuperadmin = esSuper
                )
            }
        }
    }

    fun cerrarSesion(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
