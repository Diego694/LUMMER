package pe.registroacademico.nativo.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.domain.SesionEstado
import pe.registroacademico.nativo.domain.SesionManager
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sesionManager: SesionManager
) : ViewModel() {

    val sesionEstado: StateFlow<SesionEstado> = sesionManager.sesion
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SesionEstado()
        )

    init {
        recargarSesion()
    }

    fun recargarSesion() {
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user != null) {
                sesionManager.cargarSesion()
            }
        }
    }

    fun cerrarSesion(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            sesionManager.limpiarSesion()
            onComplete()
        }
    }
}
