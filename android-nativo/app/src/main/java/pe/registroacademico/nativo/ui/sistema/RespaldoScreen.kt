package pe.registroacademico.nativo.ui.sistema

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.registroacademico.nativo.data.SistemaRepo
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class RespaldoUiState(
    val exportando: Boolean = false,
    val ultimoRespaldo: String? = null,
    val totalTablas: Int = 0,
    val totalRegistros: Int = 0
)

@HiltViewModel
class RespaldoViewModel @Inject constructor(
    private val sistemaRepo: SistemaRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(RespaldoUiState())
    val uiState: StateFlow<RespaldoUiState> = _uiState.asStateFlow()

    fun exportar(
        colegioId: String,
        nombreColegio: String,
        onSuccess: (String, Int, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exportando = true)
            try {
                val datos = sistemaRepo.exportarTodo(colegioId)
                val totalReg = datos.values.sumOf { it.size }
                val totalTab = datos.size

                val metaJson = buildJsonObject {
                    put("instituto", nombreColegio)
                    put("colegio_id", colegioId)
                    put("generado", DateUtils.todayStr() + "T" + DateUtils.nowHHMM() + ":00")
                    put("version_app", "1.0.0-nativo")
                }

                val jsonBuilder = StringBuilder()
                jsonBuilder.append("{\n  \"meta\": ").append(metaJson.toString()).append(",\n")
                jsonBuilder.append("  \"tablas\": {\n")
                val entries = datos.entries.toList()
                entries.forEachIndexed { i, (k, list) ->
                    jsonBuilder.append("    \"").append(k).append("\": [\n")
                    list.forEachIndexed { j, item ->
                        jsonBuilder.append("      ").append(item.toString())
                        if (j < list.size - 1) jsonBuilder.append(",")
                        jsonBuilder.append("\n")
                    }
                    jsonBuilder.append("    ]")
                    if (i < entries.size - 1) jsonBuilder.append(",")
                    jsonBuilder.append("\n")
                }
                jsonBuilder.append("  }\n}")

                val jsonFinal = jsonBuilder.toString()
                val ahora = DateUtils.todayStr() + " " + DateUtils.nowHHMM()
                _uiState.value = _uiState.value.copy(
                    exportando = false,
                    ultimoRespaldo = ahora,
                    totalTablas = totalTab,
                    totalRegistros = totalReg
                )
                onSuccess(jsonFinal, totalTab, totalReg)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportando = false)
                onError(e.message ?: "No se pudo generar el respaldo")
            }
        }
    }
}

@Composable
fun RespaldoScreen(
    ctx: PantallaCtx,
    viewModel: RespaldoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()
    val context = LocalContext.current

    fun compartirJson(jsonContenido: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, jsonContenido)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Exportar Respaldo JSON")
        context.startActivity(shareIntent)
    }

    fun copiarJson(jsonContenido: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Respaldo JSON", jsonContenido)
        clipboard.setPrimaryClip(clip)
        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Respaldo copiado al portapapeles") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Respaldo de datos",
            subtitulo = "Descarga una copia completa de la información de tu instituto. Guárdala en un lugar seguro y hazlo con frecuencia."
        )

        SectionCard(titulo = "Copia completa institucional") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Incluye alumnos, carreras, ciclos, docentes, comunicados, cursos, asistencias, justificaciones y auditoría en un formato JSON completo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.ultimoRespaldo != null) {
                    Text(
                        text = "Último respaldo generado: ${state.ultimoRespaldo} (${state.totalRegistros} registros en ${state.totalTablas} tablas).",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportar(
                                colegioId = cid,
                                nombreColegio = ctx.sesion.nombreInstituto,
                                onSuccess = { json, tablas, reg ->
                                    ctx.scope.launch {
                                        ctx.snackbarHostState.showSnackbar("Respaldo generado con éxito ($reg registros)")
                                    }
                                    compartirJson(json)
                                },
                                onError = { err ->
                                    ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                                }
                            )
                        },
                        enabled = !state.exportando,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (state.exportando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generar y compartir JSON")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.exportar(
                                colegioId = cid,
                                nombreColegio = ctx.sesion.nombreInstituto,
                                onSuccess = { json, _, _ ->
                                    copiarJson(json)
                                },
                                onError = { err ->
                                    ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                                }
                            )
                        },
                        enabled = !state.exportando,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar JSON")
                    }
                }
            }
        }

        SectionCard(titulo = "Buenas prácticas") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "• Frecuencia: al menos una vez al mes y siempre antes de cambios importantes (cierre de periodo, importaciones masivas o cambio de ciclo).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Seguridad: el archivo contiene datos institucionales y de alumnos. No lo envíes por chats públicos; guárdalo cifrado o en un almacenamiento institucional de acceso restringido.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Restauración: el JSON conserva todos los identificadores únicos, tablas y relaciones para su posterior restauración si fuera necesario.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
