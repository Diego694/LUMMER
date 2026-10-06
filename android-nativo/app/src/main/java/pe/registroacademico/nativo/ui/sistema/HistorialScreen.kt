package pe.registroacademico.nativo.ui.sistema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.registroacademico.nativo.data.SistemaRepo
import pe.registroacademico.nativo.data.model.Auditoria
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class HistorialUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val filas: List<Auditoria> = emptyList()
)

@HiltViewModel
class HistorialViewModel @Inject constructor(
    private val sistemaRepo: SistemaRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialUiState())
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    fun cargar(
        colegioId: String,
        tabla: String? = null,
        accion: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val lista = sistemaRepo.auditoriaLista(
                    colegioId = colegioId,
                    limite = 200,
                    tabla = tabla?.ifBlank { null },
                    accion = accion?.ifBlank { null }
                )
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    filas = lista
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar historial de auditoría"
                )
            }
        }
    }
}

private val TABLAS_MAP = mapOf(
    "alumnos" to "Alumnos",
    "niveles" to "Carreras",
    "grados" to "Ciclos",
    "docentes" to "Docentes",
    "comunicados" to "Comunicados",
    "cursos" to "Cursos",
    "justificaciones" to "Justificaciones",
    "perfiles" to "Personal y accesos",
    "calendario" to "Calendario",
    "horarios" to "Horarios",
    "periodos" to "Periodos",
    "asistencias" to "Asistencias (correcciones)",
    "colegios" to "Instituto"
)

private fun formatoResumen(detalle: JsonObject?): String {
    if (detalle == null) return "—"
    return detalle.entries.joinToString(" · ") { (k, v) ->
        "$k: $v"
    }.take(160)
}

@Composable
fun HistorialScreen(
    ctx: PantallaCtx,
    viewModel: HistorialViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()

    var filtroTabla by remember { mutableStateOf("") }
    var filtroAccion by remember { mutableStateOf("") }

    LaunchedEffect(cid, filtroTabla, filtroAccion) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid, filtroTabla, filtroAccion)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Historial de cambios",
            subtitulo = "Quién hizo qué y cuándo. No se puede editar ni borrar. Las modificaciones quedan registradas automáticamente."
        )

        // Filtros
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            val isCompact = LocalConfiguration.current.screenWidthDp < 600
            val opcionesTablas = listOf(OpcionDropdown("", "Todas las secciones")) +
                    TABLAS_MAP.map { (k, v) -> OpcionDropdown(k, v) }
            val opcionesAccion = listOf(
                OpcionDropdown("", "Todas las acciones"),
                OpcionDropdown("INSERT", "Creó (INSERT)"),
                OpcionDropdown("UPDATE", "Modificó (UPDATE)"),
                OpcionDropdown("DELETE", "Eliminó (DELETE)")
            )

            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DropdownSelector(
                        etiqueta = "Sección",
                        opciones = opcionesTablas,
                        seleccion = filtroTabla,
                        onSeleccionar = { filtroTabla = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownSelector(
                        etiqueta = "Acción",
                        opciones = opcionesAccion,
                        seleccion = filtroAccion,
                        onSeleccionar = { filtroAccion = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DropdownSelector(
                        etiqueta = "Sección",
                        opciones = opcionesTablas,
                        seleccion = filtroTabla,
                        onSeleccionar = { filtroTabla = it },
                        modifier = Modifier.weight(1f)
                    )

                    DropdownSelector(
                        etiqueta = "Acción",
                        opciones = opcionesAccion,
                        seleccion = filtroAccion,
                        onSeleccionar = { filtroAccion = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when {
            state.cargando -> {
                SkeletonList(cantidad = 5)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error inesperado",
                    onReintentar = { viewModel.cargar(cid, filtroTabla, filtroAccion) }
                )
            }

            state.filas.isEmpty() -> {
                EmptyState(
                    titulo = "Sin cambios registrados",
                    texto = "Cuando alguien cree, modifique o elimine datos, aparecerá aquí.",
                    icono = Icons.Default.ManageHistory
                )
            }

            else -> {
                SectionCard(titulo = "Registro de actividad (${state.filas.size})") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.filas.forEach { aud ->
                            val accionNorm = aud.accion.uppercase()
                            val (badgeTipo, badgeTexto) = when (accionNorm) {
                                "INSERT" -> TipoEstadoBadge.VERDE to "Creó"
                                "UPDATE" -> TipoEstadoBadge.AMBAR to "Modificó"
                                "DELETE" -> TipoEstadoBadge.ROJO to "Eliminó"
                                else -> TipoEstadoBadge.NEUTRAL to accionNorm
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            EstadoBadge(texto = badgeTexto, tipo = badgeTipo)
                                            Text(
                                                text = TABLAS_MAP[aud.tabla] ?: aud.tabla,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = aud.creadoEn?.let {
                                                DateUtils.fmtDate(it.take(10)) + " " + it.drop(11).take(5)
                                            } ?: "—",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = "Usuario: ${aud.usuario ?: "Sistema"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val resumenDetalle = formatoResumen(aud.detalle)
                                    if (resumenDetalle.isNotBlank() && resumenDetalle != "—") {
                                        Text(
                                            text = resumenDetalle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}
