package pe.registroacademico.nativo.ui.shell

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PantallaPendienteUiState(
    val estado: String = "pendiente"
)

@HiltViewModel
class PantallaPendienteViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PantallaPendienteUiState())
    val uiState: StateFlow<PantallaPendienteUiState> = _uiState.asStateFlow()
}
