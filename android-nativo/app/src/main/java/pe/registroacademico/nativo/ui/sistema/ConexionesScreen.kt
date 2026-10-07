package pe.registroacademico.nativo.ui.sistema

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.model.ConexionDatos
import pe.registroacademico.nativo.domain.BasePropiaStore
import pe.registroacademico.nativo.domain.Conectores
import pe.registroacademico.nativo.domain.ConectoresHttp
import pe.registroacademico.nativo.domain.InformeTabla
import pe.registroacademico.nativo.domain.TABLAS_CONEXION
import pe.registroacademico.nativo.domain.TipoConexion
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConexionesScreen(
    ctx: PantallaCtx,
    viewModel: ConexionesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.cargar()
    }

    var showAgregarModal by remember { mutableStateOf(false) }
    var showBasePropiaModal by remember { mutableStateOf(false) }
    var itemDesasociar by remember { mutableStateOf<ConexionDatos?>(null) }
    var itemCopiar by remember { mutableStateOf<ConexionDatos?>(null) }

    fun compartirJson(jsonContenido: String, totalRegistros: Int) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, jsonContenido)
            type = "application/json"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Exportar paquete JSON ($totalRegistros filas)")
        context.startActivity(shareIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Conexiones de datos",
            subtitulo = "Asocia otras bases de datos con llaves públicas y copia allí todos los datos de LUMMER.",
            acciones = {
                OutlinedButton(
                    onClick = {
                        viewModel.descargarPaqueteJson(
                            onSuccess = { json, total ->
                                scope.launch {
                                    ctx.snackbarHostState.showSnackbar("Paquete generado ($total filas)")
                                }
                                compartirJson(json, total)
                            },
                            onError = { err ->
                                scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                            }
                        )
                    },
                    enabled = !state.operacionEnProgreso && !state.copiando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Descargar paquete")
                }

                Button(
                    onClick = { showAgregarModal = true },
                    enabled = !state.operacionEnProgreso && !state.copiando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar conexión")
                }
            }
        )

        // Nota de aviso de seguridad
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Solo se guardan llaves públicas; las secretas (service_role, claves privadas) se rechazan. Esta versión copia los datos; mover también las cuentas de acceso a otra base es una fase aparte (docs/CONEXIONES.md).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Fila «LUMMER · base principal»
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LUMMER · base principal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Supabase ${if (state.esBasePropia) "propio de este equipo" else "original del proyecto"} (en uso)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    EstadoBadge(
                        texto = "En uso",
                        tipo = TipoEstadoBadge.VERDE
                    )
                }

                Text(
                    text = state.baseActivaUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Aquí se conecta LUMMER con sus cuentas de acceso. Puedes asociar otro proyecto Supabase con su URL y su llave publishable, y volver a la original cuando quieras; el cambio vale para este navegador o equipo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { showBasePropiaModal = true },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Asociar otra base de Supabase")
                }
            }
        }

        // Estado de carga o lista
        when {
            state.cargando -> {
                SkeletonList(cantidad = 3)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error.orEmpty(),
                    onReintentar = { viewModel.cargar() }
                )
            }

            state.conexiones.isEmpty() -> {
                EmptyState(
                    titulo = "Sin conexiones",
                    texto = "Agrega una base de datos externa para copiar allí los datos de LUMMER.",
                    icono = Icons.Default.Layers,
                    accion = {
                        Button(onClick = { showAgregarModal = true }) {
                            Text("Agregar conexión")
                        }
                    }
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.conexiones.forEach { cx ->
                        val tipoConfig = TipoConexion.desdeValor(cx.tipo)
                        val (estadoTxt, estadoTipo) = when (cx.estado) {
                            "verificada" -> "Verificada" to TipoEstadoBadge.VERDE
                            "error" -> "Con error" to TipoEstadoBadge.ROJO
                            "copiada" -> "Datos copiados" to TipoEstadoBadge.VERDE
                            else -> "Sin probar" to TipoEstadoBadge.NEUTRAL
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
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cx.nombre,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = tipoConfig.etiqueta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (cx.destino) {
                                            EstadoBadge(
                                                texto = "Destino de la copia",
                                                tipo = TipoEstadoBadge.AMBAR
                                            )
                                        }
                                        EstadoBadge(
                                            texto = estadoTxt,
                                            tipo = estadoTipo
                                        )
                                    }
                                }

                                Text(
                                    text = if (cx.detalle.isNotBlank()) "${cx.url} · ${cx.detalle}" else cx.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val fechaPrueba = cx.ultimaPrueba?.take(10)?.let { "Probada $it" } ?: "Aún no se probó"
                                val fechaCopia = cx.ultimaCopia?.take(10)?.let { " · Última copia $it" } ?: ""
                                Text(
                                    text = fechaPrueba + fechaCopia,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Botones de acción
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.probar(cx) { ok, detalle ->
                                                scope.launch { ctx.snackbarHostState.showSnackbar(detalle) }
                                            }
                                        },
                                        enabled = !state.operacionEnProgreso && !state.copiando,
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Probar")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.toggleDestino(cx.id) { err ->
                                                scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                                            }
                                        },
                                        enabled = !state.operacionEnProgreso && !state.copiando,
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Text(if (cx.destino) "Quitar como destino" else "Marcar como destino")
                                    }

                                    Button(
                                        onClick = { itemCopiar = cx },
                                        enabled = cx.destino && !state.operacionEnProgreso && !state.copiando,
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copiar todos los datos")
                                    }

                                    TextButton(
                                        onClick = { itemDesasociar = cx },
                                        enabled = !state.operacionEnProgreso && !state.copiando,
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Desasociar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Panel de Progreso e Informe de la Copia
        if (state.copiando || state.progresoCopia != null || state.informeCopia != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Informe de la copia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (state.copiando) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (!state.progresoCopia.isNullOrBlank()) {
                        Text(
                            text = state.progresoCopia.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (state.copiaTerminadaConFallo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    if (state.avisosPaquete.isNotEmpty()) {
                        Text(
                            text = state.avisosPaquete.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.informeCopia?.let { informe ->
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tabla", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text("Enviadas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Resultado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                            }

                            informe.forEach { item ->
                                val resultadoTexto = when {
                                    item.error != null -> "error: ${item.error}"
                                    item.conteoDestino == null -> "enviada (sin verificar)"
                                    item.conteoDestino >= item.esperadas -> "verificada"
                                    else -> "faltan filas (${item.conteoDestino}/${item.esperadas})"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.tabla, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                                    Text(item.enviadas.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                    Text(
                                        text = resultadoTexto,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(2f)
                                    )
                                }
                            }
                        }

                        val primerFallo = informe.find { it.error != null }
                        if (primerFallo != null) {
                            Text(
                                text = "La copia se detuvo en «${primerFallo.tabla}». Lo ya copiado queda en el destino; corrige y repite.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Copia terminada. Tablas: ${informe.size} de ${TABLAS_CONEXION.size}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Agregar Conexión
    if (showAgregarModal) {
        var tipoSeleccionado by remember { mutableStateOf(TipoConexion.SUPABASE) }
        var dropdownExpanded by remember { mutableStateOf(false) }
        var nombreNuevo by remember { mutableStateOf("") }
        var urlNuevo by remember { mutableStateOf("") }
        var llaveNueva by remember { mutableStateOf("") }
        var errorAgregar by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAgregarModal = false },
            title = { Text("Agregar conexión") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = tipoSeleccionado.etiqueta,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de base de datos *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            TipoConexion.entries.forEach { tipo ->
                                DropdownMenuItem(
                                    text = { Text(tipo.etiqueta) },
                                    onClick = {
                                        tipoSeleccionado = tipo
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = nombreNuevo,
                        onValueChange = { nombreNuevo = it },
                        label = { Text("Nombre *") },
                        placeholder = { Text("Instituto — respaldo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = urlNuevo,
                        onValueChange = { urlNuevo = it },
                        label = { Text(tipoSeleccionado.urlEtiqueta + " *") },
                        placeholder = { Text(tipoSeleccionado.urlEjemplo) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = llaveNueva,
                        onValueChange = { llaveNueva = it },
                        label = { Text(tipoSeleccionado.llaveEtiqueta + " *") },
                        placeholder = { Text("Nunca service_role ni claves secretas") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    if (errorAgregar != null) {
                        Text(
                            text = errorAgregar.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.agregar(
                            nombre = nombreNuevo,
                            tipo = tipoSeleccionado.valor,
                            url = urlNuevo,
                            llave = llaveNueva,
                            onSuccess = {
                                showAgregarModal = false
                                scope.launch { ctx.snackbarHostState.showSnackbar("Conexión guardada. Pruébala antes de copiar datos.") }
                            },
                            onError = { err -> errorAgregar = err }
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Guardar conexión")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAgregarModal = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal Desasociar
    itemDesasociar?.let { cx ->
        ConfirmDialog(
            titulo = "Desasociar conexión",
            mensaje = "¿Quitar «${cx.nombre}» de LUMMER? Se borra la llave guardada; los datos que ya copiaste allí no se tocan.",
            textoConfirmar = "Desasociar",
            esPeligro = true,
            onConfirmar = {
                viewModel.desasociar(
                    id = cx.id,
                    onSuccess = {
                        itemDesasociar = null
                        scope.launch { ctx.snackbarHostState.showSnackbar("Conexión desasociada.") }
                    },
                    onError = { err ->
                        itemDesasociar = null
                        scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { itemDesasociar = null }
        )
    }

    // Modal Copiar Todos los Datos
    itemCopiar?.let { cx ->
        val nombreColegio = ctx.sesion.nombreInstituto ?: "la institución activa"
        ConfirmDialog(
            titulo = "Copiar todos los datos",
            mensaje = "Se copiarán los datos de $nombreColegio a «${cx.nombre}». Es una copia (upsert): no borra nada del destino ni de LUMMER y puede repetirse. Los archivos (fotos y materiales) no se copian.",
            textoConfirmar = "Copiar",
            esPeligro = false,
            onConfirmar = {
                itemCopiar = null
                viewModel.copiarTodosLosDatos(
                    conexion = cx,
                    onError = { err ->
                        scope.launch { ctx.snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onCancelar = { itemCopiar = null }
        )
    }

    // Modal Base Propia (Asociar otra base de Supabase)
    if (showBasePropiaModal) {
        BasePropiaDialog(
            onDismiss = {
                showBasePropiaModal = false
                viewModel.cargar()
            }
        )
    }
}

/**
 * Diálogo reutilizable para asociar base de datos propia de Supabase (o volver a la original).
 * Usado tanto en ConexionesScreen como en LoginScreen.
 */
@Composable
fun BasePropiaDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val overrideActual = remember { BasePropiaStore.getOverride(context) }
    val esPropiaInicial = overrideActual != null
    var urlInput by remember { mutableStateOf(overrideActual?.first ?: "") }
    var keyInput by remember { mutableStateOf(overrideActual?.second ?: "") }
    var probando by remember { mutableStateOf(false) }
    var errorMensaje by remember { mutableStateOf<String?>(null) }
    var exitoMensaje by remember { mutableStateOf<String?>(null) }
    var mostrarBotonReiniciar by remember { mutableStateOf(false) }

    fun reiniciarApp() {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Base de datos") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Estado actual de la base activa
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (esPropiaInicial) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (esPropiaInicial) {
                            "Base en uso: propia → ${overrideActual?.first}"
                        } else {
                            "Base en uso: la base original del proyecto."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (esPropiaInicial) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Elige a qué base de datos se conecta la aplicación en este equipo. Sirve para instalar tu propia base de datos sin tocar el código.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it; errorMensaje = null; exitoMensaje = null },
                    label = { Text("URL del proyecto *") },
                    placeholder = { Text("https://xxxx.supabase.co") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; errorMensaje = null; exitoMensaje = null },
                    label = { Text("Publishable (anon) key *") },
                    placeholder = { Text("sb_publishable_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Pega solo la publishable / anon key. Nunca la «service_role» ni una clave secreta: se rechazan. La base nueva necesita el esquema de LUMMER y una cuenta de superadministrador; las cuentas de la base anterior no se trasladan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (probando) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Probando y validando conexión…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (errorMensaje != null) {
                    Text(
                        text = errorMensaje.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (exitoMensaje != null) {
                    Text(
                        text = exitoMensaje.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            if (mostrarBotonReiniciar) {
                Button(
                    onClick = { reiniciarApp() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Reiniciar aplicación")
                }
            } else {
                Button(
                    onClick = {
                        val url = urlInput.trim().replace(Regex("""/+$"""), "")
                        val key = keyInput.trim()

                        if (!Regex("""^https://[a-z0-9.-]+(:\d+)?$""", RegexOption.IGNORE_CASE).matches(url)) {
                            errorMensaje = "La URL debe ser https:// y sin ruta, por ejemplo https://xxxx.supabase.co"
                            return@Button
                        }

                        val clasif = Conectores.clasificarLlave(key)
                        if (!clasif.ok || key.length < 20) {
                            errorMensaje = clasif.motivo ?: "Esa clave no es válida o es secreta. Usa la publishable/anon key."
                            return@Button
                        }

                        probando = true
                        errorMensaje = null
                        exitoMensaje = null

                        scope.launch {
                            val r = ConectoresHttp.probarConexion("supabase", url, key)
                            probando = false
                            if (r.ok) {
                                BasePropiaStore.guardarOverride(context, url, key)
                                exitoMensaje = "Conectado. La aplicación debe reiniciarse para aplicar el cambio."
                                mostrarBotonReiniciar = true
                            } else {
                                errorMensaje = r.detalle
                            }
                        }
                    },
                    enabled = !probando,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Probar y conectar")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (esPropiaInicial && !mostrarBotonReiniciar) {
                    OutlinedButton(
                        onClick = {
                            BasePropiaStore.borrarOverride(context)
                            exitoMensaje = "Listo: se usa la base original. La aplicación debe reiniciarse."
                            mostrarBotonReiniciar = true
                        },
                        enabled = !probando,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Volver a la base original")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cerrar")
                }
            }
        }
    )
}
