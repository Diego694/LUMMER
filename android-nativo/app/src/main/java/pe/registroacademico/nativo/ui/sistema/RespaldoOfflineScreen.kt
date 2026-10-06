package pe.registroacademico.nativo.ui.sistema

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.offline.ColaOfflineEntity
import pe.registroacademico.nativo.offline.ColaOfflineRepository
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class RespaldoOfflineUiState(
    val cargando: Boolean = true,
    val items: List<ColaOfflineEntity> = emptyList(),
    val totalPendientes: Int = 0,
    val totalRechazados: Int = 0,
    val estaEnLinea: Boolean = true,
    val sincronizando: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RespaldoOfflineViewModel @Inject constructor(
    private val colaRepo: ColaOfflineRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RespaldoOfflineUiState())
    val uiState: StateFlow<RespaldoOfflineUiState> = _uiState.asStateFlow()

    init {
        actualizarEstadoRed()
        viewModelScope.launch {
            combine(
                colaRepo.obtenerTodos(),
                colaRepo.contarPendientes(),
                colaRepo.contarRechazados()
            ) { todos, pendientes, rechazados ->
                Triple(todos, pendientes, rechazados)
            }.collect { (todos, pendientes, rechazados) ->
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    items = todos,
                    totalPendientes = pendientes,
                    totalRechazados = rechazados,
                    estaEnLinea = verificarConexion()
                )
            }
        }
    }

    fun actualizarEstadoRed() {
        _uiState.value = _uiState.value.copy(estaEnLinea = verificarConexion())
    }

    private fun verificarConexion(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun sincronizarAhora() {
        _uiState.value = _uiState.value.copy(sincronizando = true)
        colaRepo.programarSincronizacion()
        colaRepo.forzarSincronizacion()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _uiState.value = _uiState.value.copy(sincronizando = false)
        }
    }

    fun descartar(id: String) {
        viewModelScope.launch {
            colaRepo.eliminarPorId(id)
        }
    }

    fun vaciarRechazados() {
        viewModelScope.launch {
            colaRepo.vaciarRechazados()
        }
    }

    fun generarJsonRespaldo(nombreInstituto: String, colegioId: String): String {
        val json = Json { prettyPrint = true }
        val itemsActuales = _uiState.value.items
        val backupObj = buildJsonObject {
            put("formato", "ra-respaldo")
            put("version", 1)
            put("creado_en", java.time.Instant.now().toString())
            put("instituto", buildJsonObject {
                put("id", colegioId)
                put("nombre", nombreInstituto)
            })
            put("origen", buildJsonObject {
                put("modo", "offline")
                put("app", "android")
                put("version", "1.0.0")
            })
            put("conteo", buildJsonObject {
                put("total", itemsActuales.size)
                put("pendientes", _uiState.value.totalPendientes)
                put("rechazados", _uiState.value.totalRechazados)
            })
            putJsonArray("registros") {
                itemsActuales.forEach { item ->
                    add(buildJsonObject {
                        put("id", item.id)
                        put("tipo", item.tipo)
                        put("clave", item.clave ?: "")
                        put("en", item.en)
                        put("intentos", item.intentos)
                        put("rechazado", item.rechazado)
                        put("ultimo_error", item.ultimoError ?: "")
                        put("payload", item.payloadJson)
                    })
                }
            }
        }
        return json.encodeToString(backupObj)
    }
}

@Composable
fun RespaldoOfflineScreen(
    ctx: PantallaCtx,
    viewModel: RespaldoOfflineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var registroParaDescartar by remember { mutableStateOf<ColaOfflineEntity?>(null) }
    var mostrarConfirmVaciarRechazados by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.actualizarEstadoRed()
    }

    fun compartirCopiaJson() {
        val jsonContenido = viewModel.generarJsonRespaldo(
            nombreInstituto = ctx.sesion.nombreInstituto,
            colegioId = ctx.sesion.colegioId
        )
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, jsonContenido)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Exportar Copia Respaldo Offline")
        context.startActivity(shareIntent)
    }

    fun copiarCopiaAlPortapapeles() {
        val jsonContenido = viewModel.generarJsonRespaldo(
            nombreInstituto = ctx.sesion.nombreInstituto,
            colegioId = ctx.sesion.colegioId
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Respaldo Offline JSON", jsonContenido)
        clipboard.setPrimaryClip(clip)
        ctx.scope.launch {
            ctx.snackbarHostState.showSnackbar("Copia de respaldo copiada al portapapeles")
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
            titulo = "Respaldo offline",
            subtitulo = "Registros guardados en este dispositivo sin internet. Se sincronizan automáticamente al recuperar la conexión.",
            acciones = {
                OutlinedButton(
                    onClick = { compartirCopiaJson() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar copia")
                }

                Button(
                    onClick = {
                        viewModel.sincronizarAhora()
                        ctx.scope.launch {
                            ctx.snackbarHostState.showSnackbar("Sincronización iniciada en segundo plano")
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sincronizar ahora")
                }
            }
        )

        // Tarjetas KPI de Estado
        val isCompact = LocalConfiguration.current.screenWidthDp < 600
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        valor = "${state.totalPendientes}",
                        etiqueta = "Pendientes",
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        valor = if (state.estaEnLinea) "En línea" else "Sin red",
                        etiqueta = "Conexión",
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.totalRechazados > 0) {
                    KpiCard(
                        valor = "${state.totalRechazados}",
                        etiqueta = "Rechazados con error",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    valor = "${state.totalPendientes}",
                    etiqueta = "Pendientes",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    valor = if (state.estaEnLinea) "En línea" else "Sin conexión",
                    etiqueta = "Estado de red",
                    modifier = Modifier.weight(1f)
                )
                if (state.totalRechazados > 0) {
                    KpiCard(
                        valor = "${state.totalRechazados}",
                        etiqueta = "Rechazados",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when {
            state.cargando -> {
                SkeletonList(cantidad = 4)
            }

            state.items.isEmpty() -> {
                EmptyState(
                    titulo = "Todo sincronizado",
                    texto = "No hay registros pendientes de envío en este dispositivo.",
                    icono = Icons.Default.CloudDone,
                    accion = {
                        OutlinedButton(
                            onClick = { copiarCopiaAlPortapapeles() },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Copiar estado en JSON")
                        }
                    }
                )
            }

            else -> {
                SectionCard(
                    titulo = "Registros en cola (${state.items.size})",
                    acciones = {
                        if (state.totalRechazados > 0) {
                            TextButton(
                                onClick = { mostrarConfirmVaciarRechazados = true },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text("Vaciar rechazados", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.items.forEach { item ->
                            val (badgeTexto, badgeTipo) = when {
                                item.rechazado -> "Rechazado" to TipoEstadoBadge.ROJO
                                item.intentos > 0 -> "Reintentos: ${item.intentos}" to TipoEstadoBadge.AMBAR
                                else -> "Pendiente" to TipoEstadoBadge.AZUL
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            EstadoBadge(texto = badgeTexto, tipo = badgeTipo)
                                            EstadoBadge(
                                                texto = item.tipo.uppercase(),
                                                tipo = TipoEstadoBadge.NEUTRAL
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        val fechaStr = DateUtils.fechaZona(item.en)
                                        val horaStr = DateUtils.horaZona(item.en)
                                        Text(
                                            text = "Registrado el ${DateUtils.fmtDate(fechaStr)} a las $horaStr",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )

                                        if (!item.clave.isNullOrBlank()) {
                                            Text(
                                                text = "Clave: ${item.clave}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (!item.ultimoError.isNullOrBlank()) {
                                            Text(
                                                text = "Error: ${item.ultimoError}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { registroParaDescartar = item },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Descartar registro",
                                            tint = MaterialTheme.colorScheme.error
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

    // Diálogo confirmar Descartar
    registroParaDescartar?.let { reg ->
        ConfirmDialog(
            titulo = "Descartar registro",
            mensaje = "¿Deseas eliminar este registro de la cola offline? No se enviará al servidor.",
            textoConfirmar = "Descartar",
            esPeligro = true,
            onConfirmar = {
                viewModel.descartar(reg.id)
                registroParaDescartar = null
                ctx.scope.launch {
                    ctx.snackbarHostState.showSnackbar("Registro descartado")
                }
            },
            onCancelar = {
                registroParaDescartar = null
            }
        )
    }

    // Diálogo confirmar Vaciar rechazados
    if (mostrarConfirmVaciarRechazados) {
        ConfirmDialog(
            titulo = "Vaciar rechazados",
            mensaje = "¿Deseas eliminar todos los registros que fueron rechazados tras múltiples intentos?",
            textoConfirmar = "Vaciar",
            esPeligro = true,
            onConfirmar = {
                viewModel.vaciarRechazados()
                mostrarConfirmVaciarRechazados = false
                ctx.scope.launch {
                    ctx.snackbarHostState.showSnackbar("Registros rechazados eliminados")
                }
            },
            onCancelar = {
                mostrarConfirmVaciarRechazados = false
            }
        )
    }
}
