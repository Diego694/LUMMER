package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.PersonalRepo
import pe.registroacademico.nativo.domain.SesionManager
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class DocenteFila(
    val id: String,
    val nombre: String,
    val rol: String,
    val carrera: String,
    val tieneCuenta: Boolean
)

data class DocentesUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val docentes: List<DocenteFila> = emptyList(),
    val query: String = ""
)

@HiltViewModel
class DocentesViewModel @Inject constructor(
    private val personalRepo: PersonalRepo,
    private val catalogosRepo: CatalogosRepo,
    private val sesionManager: SesionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocentesUiState())
    val uiState: StateFlow<DocentesUiState> = _uiState.asStateFlow()

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val colegioId = sesionManager.sesion.value.colegioId
                val directorio = personalRepo.personalDirectorio() ?: emptyList()
                val docentesCatalogo = if (colegioId.isNotBlank()) catalogosRepo.listarDocentes(colegioId) else emptyList()

                val listaDirectorio = directorio.map {
                    DocenteFila(
                        id = it.id,
                        nombre = it.nombre ?: "Sin nombre",
                        rol = it.rol ?: "Docente",
                        carrera = it.carrera ?: "",
                        tieneCuenta = true
                    )
                }

                // Docentes registrados en catalogo que puedan no estar en el directorio de cuentas
                val idsConCuenta = listaDirectorio.map { it.id }.toSet()
                val listaSinCuenta = docentesCatalogo
                    .filter { (it.id ?: "") !in idsConCuenta && it.estado == "ACTIVO" }
                    .map {
                        DocenteFila(
                            id = it.id ?: "",
                            nombre = it.nombre,
                            rol = it.rol ?: "Docente",
                            carrera = it.profesion ?: "",
                            tieneCuenta = false
                        )
                    }

                val todos = (listaDirectorio + listaSinCuenta).sortedBy { it.nombre }

                _uiState.update {
                    it.copy(
                        cargando = false,
                        docentes = todos
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        error = e.localizedMessage ?: "Error al cargar los docentes"
                    )
                }
            }
        }
    }

    fun setQuery(q: String) {
        _uiState.update { it.copy(query = q) }
    }
}

@Composable
fun DocentesScreen(
    ctx: PantallaCtx,
    viewModel: DocentesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    val docentesFiltrados = remember(uiState.docentes, uiState.query) {
        val q = StringUtils.norm(uiState.query)
        uiState.docentes.filter { d ->
            if (q.isBlank()) true
            else {
                val texto = StringUtils.norm("${d.nombre} ${d.rol} ${d.carrera}")
                texto.contains(q)
            }
        }
    }

    val totalDocentes = remember(uiState.docentes) {
        uiState.docentes.count { it.rol.contains("docente", ignoreCase = true) }
    }
    val totalCoordinadores = remember(uiState.docentes) {
        uiState.docentes.count { it.rol.contains("coordinador", ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Docentes",
            subtitulo = "Docentes y coordinadores con acceso al sistema. Solo lectura: las cuentas se crean en «Personal y accesos»."
        )

        SearchField(
            query = uiState.query,
            onQueryChange = { viewModel.setQuery(it) },
            placeholder = "Buscar por nombre, rol o carrera…"
        )

        when {
            uiState.cargando -> {
                SkeletonList(cantidad = 4)
            }
            uiState.error != null -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar docentes",
                    onReintentar = { viewModel.cargarDatos() }
                )
            }
            uiState.docentes.isEmpty() -> {
                EmptyState(
                    titulo = "Aún no hay docentes",
                    texto = "Crea el primero en «Personal y accesos → Crear usuario» y aparecerá aquí.",
                    icono = Icons.Default.Work
                )
            }
            docentesFiltrados.isEmpty() -> {
                EmptyState(
                    titulo = "Sin coincidencias",
                    texto = "No se encontraron docentes con el término buscado.",
                    icono = Icons.Default.Search
                )
            }
            else -> {
                Text(
                    text = "$totalDocentes docentes · $totalCoordinadores coordinadores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    docentesFiltrados.forEach { docente ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = docente.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (docente.carrera.isNotBlank()) {
                                        Text(
                                            text = "Carrera: ${docente.carrera}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val esCoord = docente.rol.contains("coordinador", ignoreCase = true)
                                    EstadoBadge(
                                        texto = docente.rol,
                                        tipo = if (esCoord) TipoEstadoBadge.AMBAR else TipoEstadoBadge.VERDE
                                    )

                                    EstadoBadge(
                                        texto = if (docente.tieneCuenta) "Con acceso" else "Sin cuenta",
                                        tipo = if (docente.tieneCuenta) TipoEstadoBadge.AZUL else TipoEstadoBadge.NEUTRAL
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
