package pe.registroacademico.nativo.ui.docente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.docente.BitacoraFiltros
import pe.registroacademico.nativo.data.docente.BitacoraItem
import pe.registroacademico.nativo.data.docente.DocenteQrRepo
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StringUtils
import javax.inject.Inject

data class BitacoraUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val items: List<BitacoraItem> = emptyList(),
    val itemsFiltrados: List<BitacoraItem> = emptyList(),
    val fechaDesde: String = DateUtils.todayStr(),
    val fechaHasta: String = DateUtils.todayStr(),
    val tipoSeleccionado: String = "", // "" = Todos, "ingreso", "curso", "qr_abierto", "justificacion", "aviso"
    val busqueda: String = "",
    val conteosPorTipo: Map<String, Int> = emptyMap()
)

@HiltViewModel
class BitacoraViewModel @Inject constructor(
    private val docenteQrRepo: DocenteQrRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(BitacoraUiState())
    val uiState: StateFlow<BitacoraUiState> = _uiState.asStateFlow()

    private var colegioIdActual: String = ""

    fun cargar(
        desde: String? = null,
        hasta: String? = null,
        tipo: String? = null,
        colegioId: String? = null
    ) {
        if (!colegioId.isNullOrBlank()) {
            colegioIdActual = colegioId
        }
        val fDesde = desde ?: _uiState.value.fechaDesde
        val fHasta = hasta ?: _uiState.value.fechaHasta
        val fTipo = tipo ?: _uiState.value.tipoSeleccionado

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cargando = true,
                    error = null,
                    fechaDesde = fDesde,
                    fechaHasta = fHasta,
                    tipoSeleccionado = fTipo
                )
            }

            val filtros = BitacoraFiltros(
                desde = fDesde,
                hasta = fHasta,
                tipo = fTipo.ifBlank { null },
                limite = 500,
                colegio = colegioIdActual.ifBlank { null }
            )

            val res = docenteQrRepo.bitacora(filtros)
            res.fold(
                onSuccess = { lista ->
                    val conteos = lista.groupingBy { it.tipo }.eachCount()
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            items = lista,
                            conteosPorTipo = conteos,
                            itemsFiltrados = aplicarFiltroBusqueda(lista, it.busqueda)
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            error = err.localizedMessage ?: "Error al cargar la bitácora de registros"
                        )
                    }
                }
            )
        }
    }

    fun cambiarFechaDesde(nuevaFecha: String) {
        val hastaActual = _uiState.value.fechaHasta
        val hastaFinal = if (nuevaFecha > hastaActual) nuevaFecha else hastaActual
        cargar(desde = nuevaFecha, hasta = hastaFinal)
    }

    fun cambiarFechaHasta(nuevaFecha: String) {
        val desdeActual = _uiState.value.fechaDesde
        val desdeFinal = if (nuevaFecha < desdeActual) nuevaFecha else desdeActual
        cargar(desde = desdeFinal, hasta = nuevaFecha)
    }

    fun cambiarTipo(nuevoTipo: String) {
        cargar(tipo = nuevoTipo)
    }

    fun cambiarBusqueda(texto: String) {
        _uiState.update {
            it.copy(
                busqueda = texto,
                itemsFiltrados = aplicarFiltroBusqueda(it.items, texto)
            )
        }
    }

    private fun aplicarFiltroBusqueda(items: List<BitacoraItem>, query: String): List<BitacoraItem> {
        val q = StringUtils.norm(query)
        if (q.isBlank()) return items
        return items.filter { item ->
            val contenido = StringUtils.norm(
                listOfNotNull(item.persona, item.curso, item.actor, item.carrera, item.ciclo)
                    .joinToString(" ")
            )
            contenido.contains(q)
        }
    }
}
