package pe.registroacademico.nativo.ui.aula

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.CursoActividad
import pe.registroacademico.nativo.data.model.CursoEntrega
import pe.registroacademico.nativo.domain.AulaUtils
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import java.time.Instant

@Composable
fun ActividadesTabContenido(
    actividades: List<CursoActividad>,
    gestiona: Boolean,
    onAbrirArchivo: (String) -> Unit,
    onVerEntregas: (CursoActividad) -> Unit,
    onEditar: (CursoActividad) -> Unit,
    onEliminar: (CursoActividad) -> Unit,
    modifier: Modifier = Modifier
) {
    if (actividades.isEmpty()) {
        EmptyState(
            titulo = "Sin actividades todavía",
            texto = if (gestiona) "Crea una actividad con instrucciones y fecha límite." else "Cuando el docente publique actividades, aparecerán aquí.",
            icono = Icons.Default.Checklist,
            modifier = modifier
        )
        return
    }

    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val ahoraIso = remember { Instant.now().toString() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actividades.forEach { a ->
            val vencida = a.fechaLimite != null && a.fechaLimite < ahoraIso

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
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
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = a.titulo,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    EstadoBadge(
                                        texto = "Periodo ${AulaUtils.periodoDe(a.periodo)}",
                                        tipo = TipoEstadoBadge.NEUTRAL
                                    )
                                    if (!a.publicado) {
                                        EstadoBadge(texto = "Borrador", tipo = TipoEstadoBadge.AMBAR)
                                    }
                                }
                            }
                        }

                        if (a.instrucciones.isNotBlank()) {
                            Text(
                                text = a.instrucciones,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fechaTexto = if (a.fechaLimite != null) {
                                "Entrega hasta ${AulaUtils.formatearFechaLimite(a.fechaLimite)}"
                            } else "Sin fecha límite"

                            Text(
                                text = "$fechaTexto · ${AulaUtils.formatearNota(a.puntajeMax)} pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (vencida) {
                                EstadoBadge(texto = "Vencida", tipo = TipoEstadoBadge.ROJO)
                            }
                        }

                        if (!a.archivoNombre.isNullOrBlank()) {
                            Text(
                                text = "${a.archivoNombre} · ${AulaUtils.formatearKb(a.archivoBytes)}",
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
                                if (!a.archivoPath.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { onAbrirArchivo(a.archivoPath) },
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Abrir")
                                    }
                                }
                                if (gestiona) {
                                    OutlinedButton(
                                        onClick = { onVerEntregas(a) },
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Entregas")
                                    }
                                }
                            }

                            if (gestiona) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onEditar(a) },
                                        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar ${a.titulo}")
                                    }
                                    IconButton(
                                        onClick = { onEliminar(a) },
                                        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar ${a.titulo}", tint = MaterialTheme.colorScheme.error)
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
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = a.titulo,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    EstadoBadge(
                                        texto = "Periodo ${AulaUtils.periodoDe(a.periodo)}",
                                        tipo = TipoEstadoBadge.NEUTRAL
                                    )
                                    if (!a.publicado) {
                                        EstadoBadge(texto = "Borrador", tipo = TipoEstadoBadge.AMBAR)
                                    }
                                }
                                if (a.instrucciones.isNotBlank()) {
                                    Text(
                                        text = a.instrucciones,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val fechaTexto = if (a.fechaLimite != null) {
                                    "Entrega hasta ${AulaUtils.formatearFechaLimite(a.fechaLimite)}"
                                } else "Sin fecha límite"

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$fechaTexto · ${AulaUtils.formatearNota(a.puntajeMax)} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    if (vencida) {
                                        EstadoBadge(texto = "Vencida", tipo = TipoEstadoBadge.ROJO)
                                    }
                                }
                                if (!a.archivoNombre.isNullOrBlank()) {
                                    Text(
                                        text = "${a.archivoNombre} · ${AulaUtils.formatearKb(a.archivoBytes)}",
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
                            if (!a.archivoPath.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { onAbrirArchivo(a.archivoPath) },
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abrir")
                                }
                            }
                            if (gestiona) {
                                OutlinedButton(
                                    onClick = { onVerEntregas(a) },
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                ) {
                                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Entregas")
                                }
                                IconButton(
                                    onClick = { onEditar(a) },
                                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar ${a.titulo}")
                                }
                                IconButton(
                                    onClick = { onEliminar(a) },
                                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar ${a.titulo}", tint = MaterialTheme.colorScheme.error)
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
fun FormularioActividadDialog(
    actividad: CursoActividad?,
    curso: Curso,
    periodoPorDefecto: Int,
    onGuardar: (CursoActividad, String?, ByteArray?) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    var titulo by remember { mutableStateOf(actividad?.titulo.orEmpty()) }
    var instrucciones by remember { mutableStateOf(actividad?.instrucciones.orEmpty()) }
    var fechaLimite by remember { mutableStateOf(actividad?.fechaLimite.orEmpty().take(16)) }
    var puntajeMax by remember { mutableStateOf(AulaUtils.formatearNota(actividad?.puntajeMax ?: 20.0)) }
    var periodo by remember {
        mutableStateOf(AulaUtils.periodoDe(actividad?.periodo ?: (if (periodoPorDefecto > 0) periodoPorDefecto else 1)).toString())
    }
    var publicado by remember { mutableStateOf(actividad?.publicado != false) }

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

    val periodosOpciones = (1..AulaUtils.PERIODOS_MAX).map {
        OpcionDropdown(it.toString(), "Periodo $it")
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (actividad != null) "Editar actividad" else "Nueva actividad") },
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

                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it.take(160) },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = instrucciones,
                    onValueChange = { instrucciones = it.take(8000) },
                    label = { Text("Instrucciones") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                OutlinedTextField(
                    value = fechaLimite,
                    onValueChange = { fechaLimite = it },
                    label = { Text("Fecha límite (YYYY-MM-DDTHH:mm)") },
                    placeholder = { Text("Ej: 2026-10-30T23:59") },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = puntajeMax,
                        onValueChange = { puntajeMax = it },
                        label = { Text("Puntaje máx. *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                        singleLine = true
                    )

                    DropdownSelector(
                        etiqueta = "Periodo",
                        opciones = periodosOpciones,
                        seleccion = periodo,
                        onSeleccionar = { periodo = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Archivo adjunto
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Archivo o rúbrica (opcional · máx. 10 MB)",
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
                                !actividad?.archivoNombre.isNullOrBlank() -> "Actual: ${actividad!!.archivoNombre}"
                                else -> "Adjuntar archivo"
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
                    val per = periodo.toIntOrNull() ?: 1
                    if (per !in 1..AulaUtils.PERIODOS_MAX) {
                        errorMsg = "El periodo debe estar entre 1 y ${AulaUtils.PERIODOS_MAX}."
                        return@Button
                    }
                    val puntaje = puntajeMax.toDoubleOrNull() ?: 20.0
                    if (puntaje <= 0.0 || puntaje > 100.0) {
                        errorMsg = "El puntaje debe estar entre 1 y 100."
                        return@Button
                    }

                    val limiteIso = if (fechaLimite.isNotBlank()) {
                        try {
                            if (fechaLimite.length == 16) "${fechaLimite}:00Z" else fechaLimite
                        } catch (_: Exception) {
                            null
                        }
                    } else null

                    guardando = true
                    val actAGuardar = (actividad ?: CursoActividad(
                        cursoId = curso.id.orEmpty(),
                        colegioId = curso.colegioId,
                        titulo = tit
                    )).copy(
                        titulo = tit,
                        instrucciones = instrucciones.trim(),
                        fechaLimite = limiteIso,
                        puntajeMax = puntaje,
                        periodo = per,
                        publicado = publicado
                    )

                    onGuardar(actAGuardar, archivoNombreSeleccionado, archivoBytesSeleccionado)
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

@Composable
fun DialogoEntregas(
    actividad: CursoActividad,
    entregas: List<CursoEntrega>,
    cargando: Boolean,
    guardandoId: String?,
    onAbrirArchivo: (String) -> Unit,
    onCalificar: (String, Double?, String) -> Unit,
    onCerrar: () -> Unit
) {
    // Conserva los borradores de notas y comentarios por cada fila aunque se guarde otra fila
    val borradoresNotas = remember { mutableStateMapOf<String, String>() }
    val borradoresComentarios = remember { mutableStateMapOf<String, String>() }
    val erroresFila = remember { mutableStateMapOf<String, String>() }

    val calificadas = entregas.count { it.nota != null }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Column {
                Text(
                    text = "Entregas · ${actividad.titulo}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${entregas.size} entrega(s) · $calificadas calificada(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    cargando -> {
                        SkeletonList(cantidad = 3)
                    }
                    entregas.isEmpty() -> {
                        EmptyState(
                            titulo = "Aún no hay entregas",
                            texto = "Cuando los estudiantes entreguen, aparecerán aquí para calificarlas.",
                            icono = Icons.Default.Checklist
                        )
                    }
                    else -> {
                        entregas.forEach { e ->
                            val max = actividad.puntajeMax
                            val notaGuardada = e.nota
                            val nombreAlumno = e.alumnos?.nombre ?: "Estudiante"

                            // Inicializar borrador si aún no se editó
                            val notaInput = borradoresNotas.getOrPut(e.id) {
                                if (notaGuardada != null) AulaUtils.formatearNota(notaGuardada) else ""
                            }
                            val comentarioInput = borradoresComentarios.getOrPut(e.id) {
                                e.comentario
                            }
                            val errorFila = erroresFila[e.id]
                            val estaGuardandoEstaFila = guardandoId == e.id

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
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = nombreAlumno,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (e.tardia) {
                                                    EstadoBadge(texto = "Tardía", tipo = TipoEstadoBadge.AMBAR)
                                                }
                                                if (notaGuardada == null) {
                                                    EstadoBadge(texto = "Sin calificar", tipo = TipoEstadoBadge.NEUTRAL)
                                                } else {
                                                    EstadoBadge(
                                                        texto = "${AulaUtils.formatearNota(notaGuardada)} / ${AulaUtils.formatearNota(max)}",
                                                        tipo = if (notaGuardada >= (max / 2.0)) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                                                    )
                                                }
                                            }

                                            if (!e.enviadoEn.isNullOrBlank()) {
                                                Text(
                                                    text = "Enviada: ${AulaUtils.formatearFechaLimite(e.enviadoEn)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        if (!e.archivoPath.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = { onAbrirArchivo(e.archivoPath) },
                                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Abrir")
                                            }
                                        }
                                    }

                                    if (e.texto.isNotBlank()) {
                                        Text(
                                            text = e.texto,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (!e.archivoNombre.isNullOrBlank()) {
                                        Text(
                                            text = "${e.archivoNombre} · ${AulaUtils.formatearKb(e.archivoBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    HorizontalDivider()

                                    // Formulario de calificación por fila
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = notaInput,
                                            onValueChange = {
                                                borradoresNotas[e.id] = it
                                                erroresFila.remove(e.id)
                                            },
                                            label = { Text("Nota (0–${AulaUtils.formatearNota(max)})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = comentarioInput,
                                            onValueChange = { borradoresComentarios[e.id] = it.take(2000) },
                                            label = { Text("Comentario") },
                                            modifier = Modifier.weight(2f).defaultMinSize(minHeight = 44.dp),
                                            singleLine = true
                                        )
                                    }

                                    if (errorFila != null) {
                                        Text(
                                            text = errorFila,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                val nStr = notaInput.trim()
                                                val notaVal = if (nStr.isBlank()) null else nStr.toDoubleOrNull()
                                                if (nStr.isNotBlank() && notaVal == null) {
                                                    erroresFila[e.id] = "Escribe una nota válida."
                                                    return@Button
                                                }
                                                val err = if (notaVal != null) AulaUtils.validarNota(notaVal, max) else null
                                                if (err != null) {
                                                    erroresFila[e.id] = err
                                                    return@Button
                                                }

                                                onCalificar(e.id, notaVal, comentarioInput.trim())
                                            },
                                            enabled = !estaGuardandoEstaFila,
                                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                        ) {
                                            if (estaGuardandoEstaFila) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text(if (notaInput.isBlank()) "Quitar nota" else "Guardar nota")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCerrar,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cerrar")
            }
        }
    )
}
