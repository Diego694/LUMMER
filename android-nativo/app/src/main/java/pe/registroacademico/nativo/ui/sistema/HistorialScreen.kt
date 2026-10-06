package pe.registroacademico.nativo.ui.sistema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.registroacademico.nativo.data.SistemaRepo
import pe.registroacademico.nativo.data.model.Auditoria
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
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class HistorialUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val filas: List<Auditoria> = emptyList(),
    val sinColegio: Boolean = false
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
        if (colegioId.isBlank()) {
            _uiState.value = HistorialUiState(
                cargando = false,
                error = null,
                filas = emptyList(),
                sinColegio = true
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null, sinColegio = false)
            try {
                val lista = sistemaRepo.auditoriaLista(
                    colegioId = colegioId,
                    limite = 300,
                    tabla = tabla?.ifBlank { null },
                    accion = accion?.ifBlank { null }
                )
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    filas = lista,
                    sinColegio = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar historial de auditoría",
                    sinColegio = false
                )
            }
        }
    }
}

val TABLAS_MAP = mapOf(
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

fun formatearCuando(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        val instant = java.time.Instant.parse(iso)
        val zdt = instant.atZone(ZoneId.of("America/Lima"))
        val dia = String.format(Locale.US, "%02d", zdt.dayOfMonth)
        val mes = when (zdt.monthValue) {
            1 -> "ene"
            2 -> "feb"
            3 -> "mar"
            4 -> "abr"
            5 -> "may"
            6 -> "jun"
            7 -> "jul"
            8 -> "ago"
            9 -> "set"
            10 -> "oct"
            11 -> "nov"
            12 -> "dic"
            else -> ""
        }
        val hora = String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
        "$dia $mes, $hora"
    } catch (_: Exception) {
        try {
            val zdt = java.time.ZonedDateTime.parse(iso).withZoneSameInstant(ZoneId.of("America/Lima"))
            val dia = String.format(Locale.US, "%02d", zdt.dayOfMonth)
            val mes = when (zdt.monthValue) {
                1 -> "ene"
                2 -> "feb"
                3 -> "mar"
                4 -> "abr"
                5 -> "may"
                6 -> "jun"
                7 -> "jul"
                8 -> "ago"
                9 -> "set"
                10 -> "oct"
                11 -> "nov"
                12 -> "dic"
                else -> ""
            }
            val hora = String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
            "$dia $mes, $hora"
        } catch (_: Exception) {
            iso.take(16)
        }
    }
}

fun formatearResumen(accion: String, detalle: JsonElement?): String {
    if (detalle == null || detalle is JsonNull) return "—"
    if (detalle !is JsonObject) {
        val s = detalle.toString().removeSurrounding("\"")
        return s.ifBlank { "—" }
    }
    val purgado = detalle["purgado"]
    if (purgado != null && purgado !is JsonNull) {
        return "Datos personales eliminados"
    }

    if (accion.equals("UPDATE", ignoreCase = true)) {
        val partes = detalle.entries.map { (k, v) ->
            if (v is JsonObject && "a" in v) {
                val de = fmtValor(v["de"])
                val a = fmtValor(v["a"])
                "$k: $de → $a"
            } else {
                "$k: ${fmtValor(v)}"
            }
        }
        val res = partes.joinToString(" · ")
        return res.ifBlank { "—" }
    }

    val nombre = detalle["nombre"] ?: detalle["titulo"] ?: detalle["codigo"] ?: detalle["fecha"]
    val nombreStr = valorStringOpcional(nombre)
    val nivelStr = valorStringOpcional(detalle["nivel"])
    val gradoStr = valorStringOpcional(detalle["grado"])

    val campos = listOfNotNull(nombreStr, nivelStr, gradoStr).filter { it.isNotBlank() }
    val res = campos.joinToString(" · ")
    return res.ifBlank { "—" }
}

private fun fmtValor(element: JsonElement?): String {
    if (element == null || element is JsonNull) return "vacío"
    if (element is JsonPrimitive) {
        val content = element.content
        return if (content.isEmpty()) "vacío" else content
    }
    val raw = element.toString().removeSurrounding("\"")
    return if (raw.isEmpty()) "vacío" else raw
}

private fun valorStringOpcional(element: JsonElement?): String? {
    if (element == null || element is JsonNull) return null
    if (element is JsonPrimitive) {
        return element.content.takeIf { it.isNotBlank() }
    }
    return element.toString().removeSurrounding("\"").takeIf { it.isNotBlank() }
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
        viewModel.cargar(cid, filtroTabla, filtroAccion)
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
            subtitulo = "Quién hizo qué y cuándo. No se puede editar ni borrar. Los ingresos normales de asistencia no se listan (solo sus correcciones)."
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
                OpcionDropdown("INSERT", "Creó"),
                OpcionDropdown("UPDATE", "Modificó"),
                OpcionDropdown("DELETE", "Eliminó")
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

            state.sinColegio -> {
                EmptyState(
                    titulo = "Sin institución seleccionada",
                    texto = "Inicia sesión con un instituto o selecciona una institución para ver el historial.",
                    icono = Icons.Default.Info
                )
            }

            state.error != null -> {
                val esErrorMigracion = state.error?.contains("007") == true ||
                    state.error?.contains("does not exist", ignoreCase = true) == true ||
                    state.error?.contains("schema cache", ignoreCase = true) == true ||
                    state.error?.contains("relation", ignoreCase = true) == true

                if (esErrorMigracion) {
                    EmptyState(
                        titulo = "No se pudo cargar el historial",
                        texto = "Falta aplicar la migración 007 (supabase/migrations/007_operacion_avanzada.sql).",
                        icono = Icons.Default.Warning,
                        accion = {
                            Button(
                                onClick = { viewModel.cargar(cid, filtroTabla, filtroAccion) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Reintentar")
                            }
                        }
                    )
                } else {
                    ErrorState(
                        mensaje = state.error ?: "Error al cargar historial",
                        onReintentar = { viewModel.cargar(cid, filtroTabla, filtroAccion) }
                    )
                }
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
                                            text = formatearCuando(aud.creadoEn),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = "Usuario: ${aud.usuario ?: "—"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val resumenDetalle = formatearResumen(aud.accion, aud.detalle)
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
