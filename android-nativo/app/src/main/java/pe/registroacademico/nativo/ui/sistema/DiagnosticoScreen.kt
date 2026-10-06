package pe.registroacademico.nativo.ui.sistema

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.CatalogosRepo
import pe.registroacademico.nativo.data.SistemaRepo
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.Permisos
import pe.registroacademico.nativo.domain.SesionEstado
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject
import kotlin.math.abs

enum class EstadoDiag {
    OK, WARN, ERR, INFO
}

data class ItemDiag(
    val nombre: String,
    val estado: EstadoDiag,
    val detalle: String
)

data class DiagnosticoUiState(
    val cargando: Boolean = true,
    val items: List<ItemDiag> = emptyList()
)

@HiltViewModel
class DiagnosticoViewModel @Inject constructor(
    private val sistemaRepo: SistemaRepo,
    private val alumnosRepo: AlumnosRepo,
    private val catalogosRepo: CatalogosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticoUiState())
    val uiState: StateFlow<DiagnosticoUiState> = _uiState.asStateFlow()

    fun ejecutar(colegioId: String, sesion: SesionEstado) {
        viewModelScope.launch {
            _uiState.value = DiagnosticoUiState(cargando = true)
            val lista = mutableListOf<ItemDiag>()

            // 1. Servidor y reloj
            var serverMs: Long? = null
            var latenciaMs: Long = 0
            val t0 = System.currentTimeMillis()
            try {
                serverMs = sistemaRepo.horaServidor()
                latenciaMs = System.currentTimeMillis() - t0
                val estadoServidor = if (latenciaMs > 2500) EstadoDiag.WARN else EstadoDiag.OK
                lista.add(
                    ItemDiag(
                        nombre = "Servidor de datos",
                        estado = estadoServidor,
                        detalle = "Conexión activa. Responde en $latenciaMs ms."
                    )
                )

                val difMs = serverMs - System.currentTimeMillis()
                val estadoReloj = if (abs(difMs) > 120_000) EstadoDiag.WARN else EstadoDiag.OK
                val detalleReloj = if (abs(difMs) > 120_000) {
                    "Desfasado ${abs(difMs) / 60000} min respecto al servidor. No afecta la asistencia: se usa la hora del servidor."
                } else {
                    "Correcto (diferencia de ${abs(difMs) / 1000} s)."
                }
                lista.add(ItemDiag(nombre = "Reloj del dispositivo", estado = estadoReloj, detalle = detalleReloj))
            } catch (e: Exception) {
                lista.add(
                    ItemDiag(
                        nombre = "Servidor de datos",
                        estado = EstadoDiag.ERR,
                        detalle = "No responde (${e.message ?: "sin conexión"})."
                    )
                )
            }

            // 2. Hora de asistencia en Lima
            lista.add(
                ItemDiag(
                    nombre = "Hora de asistencia",
                    estado = EstadoDiag.INFO,
                    detalle = "${DateUtils.nowHHMM()} (America/Lima) · Fecha: ${DateUtils.todayStr()}"
                )
            )

            // 3. Datos del instituto
            try {
                val alumnos = alumnosRepo.listar(colegioId)
                val carreras = catalogosRepo.listarNiveles(colegioId)
                val grados = catalogosRepo.listarGrados(colegioId)
                lista.add(
                    ItemDiag(
                        nombre = "Datos del instituto",
                        estado = if (alumnos.isNotEmpty()) EstadoDiag.OK else EstadoDiag.WARN,
                        detalle = "${alumnos.size} alumnos · ${carreras.size} carreras · ${grados.size} ciclos registrados"
                    )
                )
            } catch (e: Exception) {
                lista.add(
                    ItemDiag(
                        nombre = "Datos del instituto",
                        estado = EstadoDiag.WARN,
                        detalle = "No se pudieron sincronizar los conteos: ${e.message}"
                    )
                )
            }

            // 4. Dispositivo y Plataforma
            lista.add(
                ItemDiag(
                    nombre = "Dispositivo Android",
                    estado = EstadoDiag.OK,
                    detalle = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
                )
            )

            // 5. Aplicación
            lista.add(
                ItemDiag(
                    nombre = "Aplicación",
                    estado = EstadoDiag.INFO,
                    detalle = "Registro Académico Nativo · Versión 1.0.0 · Modo Android Nativo Compose"
                )
            )

            // 6. Sesión activa
            val rolEtiqueta = Permisos.ETIQUETA_ROL[sesion.rol] ?: sesion.rol.name
            lista.add(
                ItemDiag(
                    nombre = "Sesión activa",
                    estado = EstadoDiag.INFO,
                    detalle = "${sesion.perfil?.nombre ?: "Usuario"} · Rol: $rolEtiqueta · Institución: ${sesion.nombreInstituto.ifBlank { "Sin asignar" }}"
                )
            )

            _uiState.value = DiagnosticoUiState(cargando = false, items = lista)
        }
    }
}

@Composable
fun DiagnosticoScreen(
    ctx: PantallaCtx,
    viewModel: DiagnosticoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()
    val context = LocalContext.current

    LaunchedEffect(cid) {
        viewModel.ejecutar(cid, ctx.sesion)
    }

    fun copiarInforme() {
        val lineas = mutableListOf<String>()
        lineas.add("Diagnóstico del dispositivo — ${DateUtils.todayStr()} ${DateUtils.nowHHMM()}")
        lineas.add("Institución: ${ctx.sesion.nombreInstituto}")
        lineas.add("----------------------------------------")
        state.items.forEach { itm ->
            lineas.add("[${itm.estado.name}] ${itm.nombre}: ${itm.detalle}")
        }
        val informe = lineas.joinToString("\n")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Informe de Diagnóstico", informe)
        clipboard.setPrimaryClip(clip)
        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Informe de diagnóstico copiado") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Diagnóstico del dispositivo",
            subtitulo = "Comprueba que este dispositivo está listo: conexión, hora, servidor y datos.",
            acciones = {
                OutlinedButton(
                    onClick = { copiarInforme() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar informe")
                }

                Button(
                    onClick = { viewModel.ejecutar(cid, ctx.sesion) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Volver a comprobar")
                }
            }
        )

        when {
            state.cargando -> {
                SkeletonList(cantidad = 5)
            }

            else -> {
                val tieneErr = state.items.any { it.estado == EstadoDiag.ERR }
                val tieneWarn = state.items.any { it.estado == EstadoDiag.WARN }

                val resumenTitulo = when {
                    tieneErr -> "Hay problemas por resolver"
                    tieneWarn -> "Listo, con observaciones"
                    else -> "Todo en orden"
                }

                val resumenColor = when {
                    tieneErr -> MaterialTheme.colorScheme.errorContainer
                    tieneWarn -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }

                val resumenTextoColor = when {
                    tieneErr -> MaterialTheme.colorScheme.onErrorContainer
                    tieneWarn -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = resumenColor)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val iconVector = when {
                            tieneErr -> Icons.Default.Error
                            tieneWarn -> Icons.Default.Warning
                            else -> Icons.Default.CheckCircle
                        }
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = resumenTextoColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = resumenTitulo,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = resumenTextoColor
                        )
                    }
                }

                SectionCard(titulo = "Resultados de las pruebas") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.items.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val (badgeTipo, badgeTexto) = when (item.estado) {
                                        EstadoDiag.OK -> TipoEstadoBadge.VERDE to "OK"
                                        EstadoDiag.WARN -> TipoEstadoBadge.AMBAR to "AVISO"
                                        EstadoDiag.ERR -> TipoEstadoBadge.ROJO to "ERROR"
                                        EstadoDiag.INFO -> TipoEstadoBadge.AZUL to "INFO"
                                    }

                                    EstadoBadge(texto = badgeTexto, tipo = badgeTipo)

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.nombre,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.detalle,
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
