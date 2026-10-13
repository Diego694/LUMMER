package pe.registroacademico.nativo.ui.estudiante

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.estudiante.ActividadEstudiante
import pe.registroacademico.nativo.data.estudiante.EntregaEstudiante
import pe.registroacademico.nativo.data.estudiante.MaterialEstudiante
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge

@Composable
fun AulaEstScreen(
    cursoId: String,
    onVolver: () -> Unit,
    viewModel: AulaEstVM = hiltViewModel()
) {
    BackHandler(onBack = onVolver)

    LaunchedEffect(cursoId) {
        viewModel.cargarCurso(cursoId)
    }

    val state by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var actividadAEntregar by remember { mutableStateOf<ActividadEstudiante?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Cabecera con botón volver
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onVolver,
                modifier = Modifier
                    .size(48.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a la lista de cursos"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Aula Virtual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { viewModel.recargar() },
                modifier = Modifier
                    .size(48.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Recargar contenido del curso"
                )
            }
        }

        when (val ui = state) {
            is EstUiState.Cargando -> {
                SkeletonList(cantidad = 4)
            }
            is EstUiState.Error -> {
                ErrorState(
                    mensaje = ui.mensaje,
                    onReintentar = { viewModel.recargar() }
                )
            }
            is EstUiState.Datos -> {
                val datos = ui.datos

                // Promedio del curso si no es null
                if (datos.promedio != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Promedio del curso",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${EstAulaUtils.formatearNota(datos.promedio)} / 20",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Según actividades ya calificadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            EstadoBadge(
                                texto = if (datos.promedio >= 10.5) "Aprobado" else "En riesgo",
                                tipo = if (datos.promedio >= 10.5) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                            )
                        }
                    }
                }

                // Selector de pestañas: Material / Actividades / Notas
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Material") },
                        icon = { Icon(Icons.Default.Description, contentDescription = null) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Actividades") },
                        icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Notas") },
                        icon = { Icon(Icons.Default.Grade, contentDescription = null) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }

                when (selectedTab) {
                    0 -> MaterialTab(materiales = datos.materiales)
                    1 -> ActividadesTab(
                        actividades = datos.actividades,
                        misEntregas = datos.misEntregas,
                        onAbrirEntrega = { actividad -> actividadAEntregar = actividad }
                    )
                    2 -> NotasCursoTab(
                        actividades = datos.actividades,
                        misEntregas = datos.misEntregas,
                        promedio = datos.promedio
                    )
                }

                // Diálogo para entregar actividad
                actividadAEntregar?.let { act ->
                    EntregarActividadDialog(
                        actividad = act,
                        cursoId = datos.cursoId,
                        entregaPrevia = datos.misEntregas[act.id],
                        onDismiss = { actividadAEntregar = null },
                        onEnviar = { texto, archNombre, archBytes ->
                            viewModel.entregar(
                                actividadId = act.id ?: "",
                                cursoId = datos.cursoId,
                                colegioId = act.colegioId ?: "",
                                texto = texto,
                                archivoNombre = archNombre,
                                archivoBytes = archBytes,
                                onFin = { res ->
                                    if (res.isSuccess) {
                                        actividadAEntregar = null
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialTab(
    materiales: List<MaterialEstudiante>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (materiales.isEmpty()) {
        EmptyState(
            titulo = "Sin material publicado",
            texto = "Tu docente aún no ha publicado documentos ni enlaces para este curso.",
            icono = Icons.Default.Description,
            modifier = modifier
        )
        return
    }

    val porTema = materiales.groupBy { it.tema.ifBlank { "General" } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        porTema.forEach { (tema, lista) ->
            item(key = "tema-$tema") {
                Text(
                    text = tema,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(lista, key = { it.id ?: it.titulo }) { m ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val (icono, tint) = when (m.tipo.lowercase()) {
                                "enlace" -> Icons.Default.Link to MaterialTheme.colorScheme.primary
                                "aviso" -> Icons.Default.Campaign to MaterialTheme.colorScheme.tertiary
                                else -> Icons.Default.Description to MaterialTheme.colorScheme.secondary
                            }
                            Icon(
                                imageVector = icono,
                                contentDescription = m.tipo,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.titulo,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (m.tipo.lowercase() == "aviso") {
                                    EstadoBadge(texto = "Aviso", tipo = TipoEstadoBadge.AMBAR)
                                }
                            }
                        }

                        if (m.descripcion.isNotBlank()) {
                            Text(
                                text = m.descripcion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!m.archivoNombre.isNullOrBlank()) {
                            Text(
                                text = "${m.archivoNombre} · ${EstAulaUtils.formatearTamanoArchivo(m.archivoBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        val urlDestino = m.url ?: if (m.archivoPath?.startsWith("http") == true) m.archivoPath else null
                        if (!urlDestino.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlDestino))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        // Navegador no disponible
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (m.tipo.lowercase() == "enlace") "Abrir enlace" else "Abrir archivo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActividadesTab(
    actividades: List<ActividadEstudiante>,
    misEntregas: Map<String, EntregaEstudiante?>,
    onAbrirEntrega: (ActividadEstudiante) -> Unit,
    modifier: Modifier = Modifier
) {
    if (actividades.isEmpty()) {
        EmptyState(
            titulo = "Sin actividades",
            texto = "Tu docente aún no ha publicado tareas ni evaluaciones en este curso.",
            icono = Icons.AutoMirrored.Filled.Assignment,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(actividades, key = { it.id ?: it.titulo }) { act ->
            val entrega = act.id?.let { misEntregas[it] }
            val estado = EstAulaUtils.determinarEstadoEntrega(act, entrega)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = act.titulo,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hasta ${EstAulaUtils.formatearFechaIso(act.fechaLimite)} · ${act.puntajeMax.toInt()} pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        EstadoBadge(texto = estado.etiqueta, tipo = estado.tipoBadge)
                    }

                    if (act.instrucciones.isNotBlank()) {
                        Text(
                            text = act.instrucciones,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!act.archivoNombre.isNullOrBlank()) {
                        Text(
                            text = "Adjunto del docente: ${act.archivoNombre} (${EstAulaUtils.formatearTamanoArchivo(act.archivoBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Detalle de la entrega si existe
                    if (entrega != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Tu entrega enviada",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (entrega.tardia) {
                                        EstadoBadge(texto = "Tardía", tipo = TipoEstadoBadge.AMBAR)
                                    }
                                }

                                if (entrega.texto.isNotBlank()) {
                                    Text(
                                        text = entrega.texto,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!entrega.archivoNombre.isNullOrBlank()) {
                                    Text(
                                        text = "Archivo: ${entrega.archivoNombre}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (entrega.comentario.isNotBlank()) {
                                    Text(
                                        text = "Comentario docente: ${entrega.comentario}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    // Botón para entregar si no está calificada
                    if (!estado.calificada) {
                        Button(
                            onClick = { onAbrirEntrega(act) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (entrega != null) "Cambiar mi entrega" else "Entregar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotasCursoTab(
    actividades: List<ActividadEstudiante>,
    misEntregas: Map<String, EntregaEstudiante?>,
    promedio: Double?,
    modifier: Modifier = Modifier
) {
    if (actividades.isEmpty()) {
        EmptyState(
            titulo = "Sin notas aún",
            texto = "Aún no hay actividades asignadas para calcular notas en este curso.",
            icono = Icons.Default.Grade,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(actividades, key = { it.id ?: it.titulo }) { act ->
            val entrega = act.id?.let { misEntregas[it] }
            val nota = entrega?.nota

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
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
                            text = act.titulo,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Periodo ${act.periodo} · Máx. ${act.puntajeMax.toInt()} pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (!entrega?.comentario.isNullOrBlank()) {
                            Text(
                                text = "Comentario: ${entrega!!.comentario}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (nota != null) {
                        EstadoBadge(
                            texto = "${EstAulaUtils.formatearNota(nota)} / ${act.puntajeMax.toInt()}",
                            tipo = if (nota >= (act.puntajeMax * 0.525)) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                        )
                    } else if (entrega != null) {
                        EstadoBadge(texto = "Pendiente de calificar", tipo = TipoEstadoBadge.AZUL)
                    } else {
                        EstadoBadge(texto = "Sin entregar", tipo = TipoEstadoBadge.NEUTRAL)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntregarActividadDialog(
    actividad: ActividadEstudiante,
    cursoId: String,
    entregaPrevia: EntregaEstudiante?,
    onDismiss: () -> Unit,
    onEnviar: (String, String?, ByteArray?) -> Unit
) {
    val context = LocalContext.current
    var texto by rememberSaveable { mutableStateOf(entregaPrevia?.texto.orEmpty()) }
    var archivoNombre by remember { mutableStateOf<String?>(entregaPrevia?.archivoNombre) }
    var archivoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val detalles = leerDetallesUri(context, uri)
            if (detalles != null) {
                val (nombre, bytes) = detalles
                val validacion = EstAulaUtils.validarArchivo(nombre, bytes.size.toLong())
                if (validacion != null) {
                    errorMsg = validacion
                } else {
                    archivoNombre = nombre
                    archivoBytes = bytes
                    errorMsg = null
                }
            } else {
                errorMsg = "No se pudo leer el archivo seleccionado."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!enviando) onDismiss() },
        title = {
            Text(
                text = "Entregar · ${actividad.titulo}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(8000) },
                    label = { Text("Tu respuesta o comentario") },
                    placeholder = { Text("Escribe aquí tu entrega o notas...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Archivo adjunto (opcional · máx. 10 MB)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = archivoNombre ?: "Seleccionar archivo",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (archivoBytes != null) {
                        Text(
                            text = "Tamaño: ${EstAulaUtils.formatearTamanoArchivo(archivoBytes!!.size.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val txt = texto.trim()
                    if (txt.isBlank() && archivoNombre == null && archivoBytes == null) {
                        errorMsg = "Debes escribir una respuesta o adjuntar un archivo."
                        return@Button
                    }
                    enviando = true
                    onEnviar(txt, archivoNombre, archivoBytes)
                },
                enabled = !enviando,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                if (enviando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Enviar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !enviando,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Lee el nombre de visualización y el contenido en bytes de un Uri.
 */
private fun leerDetallesUri(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val cr = context.contentResolver
    var nombre = "entrega.bin"
    try {
        cr.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val n = cursor.getString(nameIndex)
                if (!n.isNullOrBlank()) nombre = n
            }
        }
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return nombre to bytes
    } catch (_: Exception) {
        return null
    }
}
