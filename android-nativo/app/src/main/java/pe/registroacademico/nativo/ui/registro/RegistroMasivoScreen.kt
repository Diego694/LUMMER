package pe.registroacademico.nativo.ui.registro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun RegistroMasivoScreen(
    ctx: PantallaCtx,
    viewModel: RegistroMasivoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(ctx.sesion.colegioId) {
        viewModel.cargarDatos(ctx.sesion.colegioId)
    }

    LaunchedEffect(uiState.mensajeExito) {
        uiState.mensajeExito?.let { msg ->
            SnackbarHelper.mostrarExito(ctx.snackbarHostState, msg)
            viewModel.limpiarMensajeExito()
        }
    }

    if (uiState.cargando && uiState.niveles.isEmpty()) {
        SkeletonList(cantidad = 5)
        return
    }

    if (uiState.error != null && uiState.alumnosCiclo.isEmpty() && uiState.niveles.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error al cargar la pantalla de registro masivo",
            onReintentar = { viewModel.cargarDatos(ctx.sesion.colegioId) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            titulo = "Registro Masivo por Ciclo",
            subtitulo = "Marca la asistencia de un ciclo o salón completo en un solo paso, o importa alumnos por CSV."
        )

        SecondaryTabRow(selectedTabIndex = uiState.pestanaActiva) {
            Tab(
                selected = uiState.pestanaActiva == 0,
                onClick = { viewModel.cambiarPestana(0) },
                text = { Text("Asistencia por Ciclo") },
                icon = { Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            Tab(
                selected = uiState.pestanaActiva == 1,
                onClick = { viewModel.cambiarPestana(1) },
                text = { Text("Importar CSV / Pegado") },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
        }

        if (uiState.pestanaActiva == 0) {
            // PESTAÑA 0: Asistencia Masiva por Ciclo
            PestanaAsistenciaCiclo(
                ctx = ctx,
                uiState = uiState,
                viewModel = viewModel
            )
        } else {
            // PESTAÑA 1: Importar CSV / Pegado
            PestanaImportarCsv(
                ctx = ctx,
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PestanaAsistenciaCiclo(
    ctx: PantallaCtx,
    uiState: RegistroMasivoUiState,
    viewModel: RegistroMasivoViewModel
) {
    val yaAsistieronSet = remember(uiState.asistenciasFecha) {
        uiState.asistenciasFecha.map { it.alumnoId }.toSet()
    }
    val horaPorAlumnoMap = remember(uiState.asistenciasFecha) {
        uiState.asistenciasFecha.associate { it.alumnoId to it.hora }
    }

    val gradosDeNivel = remember(uiState.nivelSeleccionado, uiState.grados) {
        uiState.grados.filter { it.nivel == uiState.nivelSeleccionado }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Selectores de Filtro
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownSelector(
                    etiqueta = "Carrera / Nivel",
                    opciones = uiState.niveles.map { OpcionDropdown(it.nombre, it.nombre) },
                    seleccion = uiState.nivelSeleccionado,
                    onSeleccionar = { viewModel.seleccionarNivel(ctx.sesion.colegioId, it) }
                )

                DropdownSelector(
                    etiqueta = "Ciclo / Salón",
                    opciones = gradosDeNivel.map { OpcionDropdown(it.nombre, it.nombre) },
                    seleccion = uiState.gradoSeleccionado,
                    onSeleccionar = { viewModel.seleccionarGrado(it) }
                )

                OutlinedTextField(
                    value = uiState.fechaSeleccionada,
                    onValueChange = { viewModel.seleccionarFecha(ctx.sesion.colegioId, it) },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Cabecera de la lista con botones marcar/desmarcar
        val screenWidth = LocalConfiguration.current.screenWidthDp
        if (screenWidth >= 600) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.alumnosCiclo.size} alumnos en este grupo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.marcarTodos(true) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Marcar todos")
                    }
                    OutlinedButton(
                        onClick = { viewModel.marcarTodos(false) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Desmarcar todos")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${uiState.alumnosCiclo.size} alumnos en este grupo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.marcarTodos(true) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Marcar todos")
                    }
                    OutlinedButton(
                        onClick = { viewModel.marcarTodos(false) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Desmarcar todos")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.alumnosCiclo.isEmpty()) {
            EmptyState(
                titulo = "Sin alumnos en este ciclo",
                texto = "Elige una carrera y ciclo con alumnos activos asignados."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(uiState.alumnosCiclo, key = { it.id }) { alumno ->
                    val yaRegistrado = yaAsistieronSet.contains(alumno.id)
                    val hora = horaPorAlumnoMap[alumno.id]
                    val marcado = uiState.marcadosIds.contains(alumno.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!yaRegistrado) Modifier.clickable {
                                        viewModel.toggleMarcarAlumno(alumno.id)
                                    } else Modifier
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = yaRegistrado || marcado,
                                    onCheckedChange = { if (!yaRegistrado) viewModel.toggleMarcarAlumno(alumno.id) },
                                    enabled = !yaRegistrado
                                )
                                Column {
                                    Text(
                                        text = alumno.nombre,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${alumno.codigo} · ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (yaRegistrado) {
                                EstadoBadge(
                                    texto = "Ya registrado ${hora?.take(5) ?: ""}",
                                    tipo = TipoEstadoBadge.VERDE
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.guardarAsistenciaMasiva(
                        colegioId = ctx.sesion.colegioId,
                        userId = ctx.sesion.userId
                    )
                },
                enabled = uiState.marcadosIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar asistencia (${uiState.marcadosIds.size} seleccionados)")
            }
        }
    }
}

@Composable
private fun PestanaImportarCsv(
    ctx: PantallaCtx,
    uiState: RegistroMasivoUiState,
    viewModel: RegistroMasivoViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Importar alumnos por texto CSV o pegado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Formato de columnas: nombre, codigo, carrera, ciclo, apoderado, estado\n" +
                        "Solo 'nombre' y 'codigo' son obligatorios. Las filas con códigos existentes serán actualizadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.textoCsv,
                    onValueChange = { viewModel.onTextoCsvChange(it) },
                    placeholder = {
                        Text(
                            "nombre,codigo,carrera,ciclo,apoderado,estado\n" +
                                "Juan Perez Rios,a2001,MECANICA ELECTRICA,MECANICA ELECTRICA I,Maria Rios,ACTIVO\n" +
                                "Ana Torres Vega,a2002,APSTI,APSTI III,,ACTIVO"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.onTextoCsvChange(
                            "nombre,codigo,carrera,ciclo,apoderado,estado\n" +
                                "Juan Perez Rios,a2001,MECANICA ELECTRICA,MECANICA ELECTRICA I,Maria Rios,ACTIVO\n" +
                                "Ana Torres Vega,a2002,APSTI,APSTI III,,ACTIVO"
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cargar ejemplo")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta resumen del análisis
        if (uiState.filasPreview.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.totalErrores > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vista previa: ${uiState.totalValidas} alumno(s) listos para importar.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.totalErrores > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (uiState.totalErrores > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.totalErrores} fila(s) contienen errores y serán omitidas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lista con vista previa por fila y errores
            Text(
                text = "Detalle de filas a importar:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.filasPreview.forEach { fila ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (fila.error != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Línea ${fila.linea}: ${fila.nombre.ifBlank { "(Sin nombre)" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Código: ${fila.codigo.ifBlank { "—" }} · ${fila.nivel} ${fila.grado}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (fila.error != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "✕ ${fila.error}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                if (fila.aviso != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "⚠ ${fila.aviso}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            EstadoBadge(
                                texto = if (fila.error != null) "Error" else if (fila.aviso != null) "Aviso" else "Válido",
                                tipo = if (fila.error != null) TipoEstadoBadge.ROJO else if (fila.aviso != null) TipoEstadoBadge.AMBAR else TipoEstadoBadge.VERDE
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.importarAlumnosCsv(ctx.sesion.colegioId) },
                enabled = uiState.totalValidas > 0 && !uiState.procesandoImportacion,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(if (uiState.procesandoImportacion) "Importando…" else "Importar ${uiState.totalValidas} alumnos")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
