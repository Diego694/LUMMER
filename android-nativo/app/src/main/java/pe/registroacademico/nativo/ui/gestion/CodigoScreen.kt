package pe.registroacademico.nativo.ui.gestion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.InstitucionesRepo
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.gestion.qr.QrCodeGenerator
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import java.security.SecureRandom
import javax.inject.Inject

data class CodigoUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val codigo: String? = null,
    val pendientesAprobacion: Int = 0,
    val guardando: Boolean = false
)

@HiltViewModel
class CodigoViewModel @Inject constructor(
    private val institucionesRepo: InstitucionesRepo,
    private val alumnosRepo: AlumnosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodigoUiState())
    val uiState: StateFlow<CodigoUiState> = _uiState.asStateFlow()

    private val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun cargar(colegioId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val cod = institucionesRepo.getCodigoRegistro(colegioId)
                val alumnos = alumnosRepo.listar(colegioId)
                val pend = alumnos.count { it.aprobado == false }

                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    codigo = cod,
                    pendientesAprobacion = pend
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar código de registro"
                )
            }
        }
    }

    fun generarCodigoAleatorio(): String {
        val sb = StringBuilder(8)
        for (i in 0 until 8) {
            sb.append(alfabeto[random.nextInt(alfabeto.length)])
        }
        return sb.toString()
    }

    fun guardarCodigo(
        colegioId: String,
        nuevoCodigo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(guardando = true)
            try {
                institucionesRepo.setCodigoRegistro(colegioId, nuevoCodigo.trim().uppercase())
                cargar(colegioId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar el código")
            } finally {
                _uiState.value = _uiState.value.copy(guardando = false)
            }
        }
    }
}

@Composable
fun CodigoScreen(
    ctx: PantallaCtx,
    viewModel: CodigoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()
    val context = LocalContext.current

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    var confirmRegenerar by remember { mutableStateOf(false) }
    var codigoPropioInput by remember { mutableStateOf("") }
    var errorPropio by remember { mutableStateOf<String?>(null) }

    fun copiarAlPortapapeles(texto: String, etiqueta: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(etiqueta, texto)
        clipboard.setPrimaryClip(clip)
        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("$etiqueta copiado al portapapeles") }
    }

    fun textoCompartir(cod: String): String {
        val inst = ctx.sesion.nombreInstituto.ifBlank { "nuestro instituto" }
        return """
            Regístrate en *LUMMER Estudiante* de $inst:
            1) Crea tu cuenta e ingresa el código del instituto: *$cod*
            2) Completa tus datos y sube tu foto.
            3) Tu carnet con QR se activa cuando el instituto apruebe tu registro.
        """.trimIndent()
    }

    fun compartirWhatsApp(cod: String) {
        val texto = textoCompartir(cod)
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/?text=${Uri.encode(texto)}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            copiarAlPortapapeles(texto, "Mensaje de registro")
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
            titulo = "Código de registro",
            subtitulo = "El código que tus estudiantes escriben en LUMMER Estudiante para registrarse. Tú apruebas cada registro."
        )

        when {
            state.cargando -> {
                SkeletonList(cantidad = 3)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error inesperado",
                    onReintentar = { viewModel.cargar(cid) }
                )
            }

            else -> {
                val codActual = state.codigo

                if (codActual.isNullOrBlank()) {
                    EmptyState(
                        titulo = "Aún no tienes código",
                        texto = "Genera uno para que los estudiantes puedan registrarse desde la app móvil o el portal.",
                        icono = Icons.Default.QrCode,
                        accion = {
                            Button(
                                onClick = {
                                    val nuevo = viewModel.generarCodigoAleatorio()
                                    viewModel.guardarCodigo(
                                        colegioId = cid,
                                        nuevoCodigo = nuevo,
                                        onSuccess = {
                                            ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Código generado: $nuevo") }
                                        },
                                        onError = { err ->
                                            ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                                        }
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Generar código ahora")
                            }
                        }
                    )
                } else {
                    // Tarjeta Principal del Código
                    SectionCard(titulo = "Código vigente") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = codActual,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            val isCompact = LocalConfiguration.current.screenWidthDp < 600
                            if (isCompact) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { copiarAlPortapapeles(codActual, "Código") },
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar código")
                                    }

                                    OutlinedButton(
                                        onClick = { compartirWhatsApp(codActual) },
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("WhatsApp")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { copiarAlPortapapeles(codActual, "Código") },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar código")
                                    }

                                    OutlinedButton(
                                        onClick = { compartirWhatsApp(codActual) },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("WhatsApp")
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { copiarAlPortapapeles(textoCompartir(codActual), "Mensaje de registro") },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Copiar mensaje con instrucciones")
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { confirmRegenerar = true },
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regenerar código aleatorio")
                                }
                            }

                            Text(
                                text = "Si lo regeneras o lo cambias, el código anterior deja de funcionar para nuevos registros (los estudiantes ya registrados no se ven afectados).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider()

                            Text(
                                text = "Usar un código personalizado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = codigoPropioInput,
                                    onValueChange = {
                                        codigoPropioInput = it.uppercase().take(20)
                                        errorPropio = null
                                    },
                                    label = { Text("Ej: INSTITUTO2026") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val trimmed = codigoPropioInput.trim().uppercase()
                                        val regex = Regex("""^[A-Z0-9]{6,20}$""")
                                        if (!regex.matches(trimmed)) {
                                            errorPropio = "De 6 a 20 letras o números, sin espacios."
                                            return@Button
                                        }
                                        viewModel.guardarCodigo(
                                            colegioId = cid,
                                            nuevoCodigo = trimmed,
                                            onSuccess = {
                                                codigoPropioInput = ""
                                                ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Código personalizado guardado") }
                                            },
                                            onError = { err -> errorPropio = err }
                                        )
                                    },
                                    enabled = !state.guardando && codigoPropioInput.isNotBlank(),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Guardar")
                                }
                            }

                            if (errorPropio != null) {
                                Text(
                                    text = errorPropio.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Aviso de Alumnos Pendientes
                    if (state.pendientesAprobacion > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tienes ${state.pendientesAprobacion} alumno(s) registrados pendientes de aprobación.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { ctx.navegar("alumnos") },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Revisar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmRegenerar) {
        ConfirmDialog(
            titulo = "Regenerar código",
            mensaje = "El código actual dejará de funcionar para nuevos registros. ¿Deseas generar uno nuevo?",
            textoConfirmar = "Regenerar",
            esPeligro = false,
            onConfirmar = {
                confirmRegenerar = false
                val nuevo = viewModel.generarCodigoAleatorio()
                viewModel.guardarCodigo(
                    colegioId = cid,
                    nuevoCodigo = nuevo,
                    onSuccess = {
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar("Nuevo código generado: $nuevo") }
                    },
                    onError = { err ->
                        ctx.scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { confirmRegenerar = false }
        )
    }
}
