package pe.registroacademico.nativo.ui.docente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.docente.DocenteQrRepo
import pe.registroacademico.nativo.data.docente.ResumenDocenteItem
import javax.inject.Inject

data class ResumenDocenteUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val items: List<ResumenDocenteItem> = emptyList(),
    val totalCursos: Int = 0,
    val totalPresentesHoy: Int = 0,
    val totalEstudiantes: Int = 0,
    val totalPorCalificar: Int = 0
)

@HiltViewModel
class ResumenDocenteViewModel @Inject constructor(
    private val docenteQrRepo: DocenteQrRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResumenDocenteUiState())
    val uiState: StateFlow<ResumenDocenteUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String = "", userId: String = "", esAdmin: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            val res = docenteQrRepo.resumenDocente(colegioId, userId, esAdmin)
            res.fold(
                onSuccess = { lista ->
                    val totPres = lista.sumOf { it.resumen.presentes }
                    val totEst = lista.sumOf { it.resumen.total }
                    val totCal = lista.sumOf { it.resumen.porCalificar }

                    _uiState.update {
                        it.copy(
                            cargando = false,
                            items = lista,
                            totalCursos = lista.size,
                            totalPresentesHoy = totPres,
                            totalEstudiantes = totEst,
                            totalPorCalificar = totCal
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            error = err.localizedMessage ?: "Error al cargar el resumen del docente"
                        )
                    }
                }
            )
        }
    }
}
