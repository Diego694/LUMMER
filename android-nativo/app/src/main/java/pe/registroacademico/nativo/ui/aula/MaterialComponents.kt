package pe.registroacademico.nativo.ui.aula

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoMaterial
import pe.registroacademico.nativo.domain.AulaUtils
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge

@Composable
fun MaterialTabContenido(
    materiales: List<CursoMaterial>,
    gestiona: Boolean,
    onAbrirEnlace: (String) -> Unit,
    onAbrirArchivo: (String) -> Unit,
    onEditar: (CursoMaterial) -> Unit,
    onEliminar: (CursoMaterial) -> Unit,
    modifier: Modifier = Modifier
) {
    if (materiales.isEmpty()) {
        EmptyState(
            titulo = "Sin material todavía",
            texto = if (gestiona) "Sube documentos, enlaces o avisos para tus estudiantes." else "Cuando el docente publique material, aparecerá aquí.",
            icono = Icons.Default.Description,
            modifier = modifier
        )
        return
    }

    val porTema = materiales.groupBy { it.tema.ifBlank { "General" } }
    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        porTema.forEach { (tema, lista) ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tema,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                lista.forEach { m ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        val iconoTipo = when (m.tipo.lowercase()) {
                            "enlace" -> Icons.Default.Link
                            "aviso" -> Icons.Default.Info
                            else -> Icons.Default.Description
                        }

                        if (isCompact) {
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
                                    Icon(
                                        imageVector = iconoTipo,
                                        contentDescription = m.tipo,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = m.titulo,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!m.publicado) {
                                                EstadoBadge(texto = "Borrador", tipo = TipoEstadoBadge.AMBAR)
                                            }
                                        }
                                    }
                                }

                                if (m.descripcion.isNotBlank()) {
                                    Text(
                                        text = m.descripcion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!m.archivoNombre.isNullOrBlank()) {
                                    Text(
                                        text = "${m.archivoNombre} · ${AulaUtils.formatearKb(m.archivoBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!m.url.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = { onAbrirEnlace(m.url) },
                                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                            ) {
                                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Abrir enlace")
                                            }
                                        }
                                        if (!m.archivoPath.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = { onAbrirArchivo(m.archivoPath) },
                                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Abrir")
                                            }
                                        }
                                    }

                                    if (gestiona) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { onEditar(m) },
                                                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar ${m.titulo}")
                                            }
                                            IconButton(
                                                onClick = { onEliminar(m) },
                                                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Eliminar ${m.titulo}", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = iconoTipo,
                                        contentDescription = m.tipo,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = m.titulo,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (!m.publicado) {
                                                EstadoBadge(texto = "Borrador", tipo = TipoEstadoBadge.AMBAR)
                                            }
                                        }
                                        if (m.descripcion.isNotBlank()) {
                                            Text(
                                                text = m.descripcion,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!m.archivoNombre.isNullOrBlank()) {
                                            Text(
                                                text = "${m.archivoNombre} · ${AulaUtils.formatearKb(m.archivoBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!m.url.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = { onAbrirEnlace(m.url) },
                                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Abrir enlace")
                                        }
                                    }
                                    if (!m.archivoPath.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = { onAbrirArchivo(m.archivoPath) },
                                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Abrir")
                                        }
                                    }
                                    if (gestiona) {
                                        IconButton(
                                            onClick = { onEditar(m) },
                                            modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar ${m.titulo}")
                                        }
                                        IconButton(
                                            onClick = { onEliminar(m) },
                                            modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar ${m.titulo}", tint = MaterialTheme.colorScheme.error)
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
}

@Composable
fun FormularioMaterialDialog(
    material: CursoMaterial?,
    curso: Curso,
    onGuardar: (CursoMaterial, String?, ByteArray?) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    var tipo by remember { mutableStateOf(material?.tipo ?: "documento") }
    var titulo by remember { mutableStateOf(material?.titulo.orEmpty()) }
    var tema by remember { mutableStateOf(material?.tema ?: "General") }
    var descripcion by remember { mutableStateOf(material?.descripcion.orEmpty()) }
    var url by remember { mutableStateOf(material?.url.orEmpty()) }
    var publicado by remember { mutableStateOf(material?.publicado != false) }

    var archivoNombreSeleccionado by remember { mutableStateOf<String?>(null) }
    var archivoBytesSeleccionado by remember { mutableStateOf<ByteArray?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val par = obtenerDetallesUri(context, uri)
            if (par != null) {
                val (nombre, bytes) = par
                val err = AulaUtils.validarArchivoAula(nombre, bytes.size.toLong())
                if (err != null) {
                    errorMsg = err
                } else {
                    archivoNombreSeleccionado = nombre
                    archivoBytesSeleccionado = bytes
                    errorMsg = null
                }
            }
        }
    }

    val tiposOpciones = listOf(
        OpcionDropdown("documento", "Documento"),
        OpcionDropdown("enlace", "Enlace"),
        OpcionDropdown("aviso", "Aviso")
    )

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (material != null) "Editar material" else "Agregar material") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                DropdownSelector(
                    etiqueta = "Tipo",
                    opciones = tiposOpciones,
                    seleccion = tipo,
                    onSeleccionar = { tipo = it }
                )

                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it.take(160) },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = tema,
                    onValueChange = { tema = it.take(80) },
                    label = { Text("Tema o semana") },
                    placeholder = { Text("General, Semana 1, etc.") },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it.take(4000) },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                if (tipo == "enlace") {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Enlace (http:// o https://) *") },
                        placeholder = { Text("https://ejemplo.com") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )
                }

                // Archivo adjunto
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Archivo (opcional · máx. 10 MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                archivoNombreSeleccionado != null -> archivoNombreSeleccionado!!
                                !material?.archivoNombre.isNullOrBlank() -> "Actual: ${material!!.archivoNombre}"
                                else -> "Seleccionar archivo"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (archivoBytesSeleccionado != null) {
                        Text(
                            text = "Tamaño: ${AulaUtils.formatearKb(archivoBytesSeleccionado!!.size.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = publicado,
                        onCheckedChange = { publicado = it },
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Visible para los estudiantes",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tit = titulo.trim()
                    if (tit.isBlank()) {
                        errorMsg = "Escribe un título."
                        return@Button
                    }
                    val u = url.trim()
                    if (tipo == "enlace" && !u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
                        errorMsg = "El enlace debe empezar con http:// o https://"
                        return@Button
                    }

                    guardando = true
                    val matAGuardar = (material ?: CursoMaterial(
                        cursoId = curso.id.orEmpty(),
                        colegioId = curso.colegioId,
                        titulo = tit
                    )).copy(
                        tipo = tipo,
                        titulo = tit,
                        tema = tema.trim().ifBlank { "General" },
                        descripcion = descripcion.trim(),
                        url = if (tipo == "enlace") u else null,
                        publicado = publicado
                    )

                    onGuardar(matAGuardar, archivoNombreSeleccionado, archivoBytesSeleccionado)
                },
                enabled = !guardando,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

fun obtenerDetallesUri(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val cr = context.contentResolver
    var nombre = "archivo.bin"
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
