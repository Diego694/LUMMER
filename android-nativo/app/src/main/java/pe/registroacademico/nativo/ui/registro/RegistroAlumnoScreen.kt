package pe.registroacademico.nativo.ui.registro

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistroAlumnoScreen(
    ctx: PantallaCtx,
    viewModel: RegistroAlumnoViewModel = hiltViewModel()
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

    if (uiState.cargando && uiState.alumnos.isEmpty()) {
        SkeletonList(cantidad = 5)
        return
    }

    if (uiState.error != null && uiState.alumnos.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error al cargar la lista de alumnos",
            onReintentar = { viewModel.cargarDatos(ctx.sesion.colegioId) }
        )
        return
    }

    val yaAsistieronSet = remember(uiState.asistenciasHoy) {
        uiState.asistenciasHoy.map { it.alumnoId }.toSet()
    }
    val horaPorAlumnoMap = remember(uiState.asistenciasHoy) {
        uiState.asistenciasHoy.associate { it.alumnoId to it.hora }
    }

    val listaFiltrada = remember(
        uiState.alumnos,
        uiState.busqueda,
        uiState.filtroAsistencia,
        uiState.nivelFiltro,
        uiState.gradoFiltro,
        yaAsistieronSet
    ) {
        val q = StringUtils.norm(uiState.busqueda)
        uiState.alumnos.filter { a ->
            val cumpleBusqueda = q.isBlank() || StringUtils.norm("${a.nombre} ${a.codigo} ${a.apoderado}").contains(q)
            val cumpleNivel = uiState.nivelFiltro.isNullOrBlank() || a.nivel == uiState.nivelFiltro
            val cumpleGrado = uiState.gradoFiltro.isNullOrBlank() || a.grado == uiState.gradoFiltro
            val cumpleAsistencia = when (uiState.filtroAsistencia) {
                "pendientes" -> !yaAsistieronSet.contains(a.id)
                "presentes" -> yaAsistieronSet.contains(a.id)
                else -> true
            }
            cumpleBusqueda && cumpleNivel && cumpleGrado && cumpleAsistencia
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            titulo = "Registro por Alumno",
            subtitulo = "Busca un alumno y marca su asistencia de hoy o administra sus datos.",
            acciones = {
                Button(
                    onClick = { viewModel.abrirCrearAlumno() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar alumno")
                }
            }
        )

        // Barra de búsqueda
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SearchField(
                query = uiState.busqueda,
                onQueryChange = { viewModel.onBusquedaChange(it) },
                placeholder = "Buscar por nombre, código o apoderado…"
            )
        }

        // Filtros de asistencia
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.filtroAsistencia == "todos",
                onClick = { viewModel.onFiltroAsistenciaChange("todos") },
                label = { Text("Todos") },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            FilterChip(
                selected = uiState.filtroAsistencia == "pendientes",
                onClick = { viewModel.onFiltroAsistenciaChange("pendientes") },
                label = { Text("Pendientes de hoy") },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            FilterChip(
                selected = uiState.filtroAsistencia == "presentes",
                onClick = { viewModel.onFiltroAsistenciaChange("presentes") },
                label = { Text("Ya registrados") },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
        }

        // Contador de resultados
        Text(
            text = "${listaFiltrada.size} alumno(s) encontrado(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (listaFiltrada.isEmpty()) {
            EmptyState(
                titulo = "Sin resultados",
                texto = "No se encontraron alumnos con los criterios o filtros seleccionados.",
                icono = Icons.Default.PersonSearch
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(listaFiltrada, key = { it.id }) { alumno ->
                    val yaAsistio = yaAsistieronSet.contains(alumno.id)
                    val hora = horaPorAlumnoMap[alumno.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        ListaFila(
                            titulo = alumno.nombre,
                            subtitulo = "${alumno.codigo} · ${CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado)}${if (!alumno.apoderado.isNullOrBlank()) " · Apod: ${alumno.apoderado}" else ""}",
                            iniciales = StringUtils.initials(alumno.nombre),
                            badgeTexto = if (yaAsistio) "Asistió ${hora ?: ""}" else "Pendiente",
                            badgeTipo = if (yaAsistio) TipoEstadoBadge.VERDE else TipoEstadoBadge.NEUTRAL,
                            acciones = {
                                Button(
                                    onClick = {
                                        viewModel.registrarAsistenciaRapida(
                                            alumno = alumno,
                                            colegioId = ctx.sesion.colegioId,
                                            userId = ctx.sesion.userId
                                        )
                                    },
                                    enabled = !yaAsistio && alumno.estado == "ACTIVO" && alumno.aprobado != false,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Registrar")
                                }

                                IconButton(
                                    onClick = { viewModel.abrirEditarAlumno(alumno) },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar ${alumno.nombre}"
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.solicitarEliminarAlumno(alumno) },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar ${alumno.nombre}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de Formulario: Alta / Edición
    if (uiState.mostrandoDialogoForm) {
        val editando = uiState.alumnoParaEditar
        var nombreInput by remember { mutableStateOf(editando?.nombre ?: "") }
        var codigoInput by remember { mutableStateOf(editando?.codigo ?: viewModel.sugerirSiguienteCodigo()) }
        var nivelInput by remember { mutableStateOf(editando?.nivel ?: uiState.niveles.firstOrNull()?.nombre ?: "") }
        var gradoInput by remember { mutableStateOf(editando?.grado ?: "") }
        var apoderadoInput by remember { mutableStateOf(editando?.apoderado ?: "") }
        var estadoInput by remember { mutableStateOf(editando?.estado ?: "ACTIVO") }

        val gradosDisponibles = remember(nivelInput, uiState.grados) {
            uiState.grados.filter { it.nivel == nivelInput }
        }

        AlertDialog(
            onDismissRequest = { viewModel.cerrarDialogoForm() },
            title = {
                Text(
                    text = if (editando != null) "Editar alumno" else "Agregar alumno",
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
                    OutlinedTextField(
                        value = nombreInput,
                        onValueChange = { nombreInput = it },
                        label = { Text("Nombre completo *") },
                        placeholder = { Text("Ej: Juan Perez Rios") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    )

                    OutlinedTextField(
                        value = codigoInput,
                        onValueChange = { codigoInput = it },
                        label = { Text("Código único de acceso *") },
                        placeholder = { Text("Ej: a1001") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    )

                    DropdownSelector(
                        etiqueta = "Carrera / Nivel",
                        opciones = uiState.niveles.map { OpcionDropdown(it.nombre, it.nombre) },
                        seleccion = nivelInput,
                        onSeleccionar = {
                            nivelInput = it
                            gradoInput = uiState.grados.firstOrNull { g -> g.nivel == it }?.nombre ?: ""
                        }
                    )

                    DropdownSelector(
                        etiqueta = "Ciclo / Salón",
                        opciones = gradosDisponibles.map { OpcionDropdown(it.nombre, it.nombre) },
                        seleccion = gradoInput,
                        onSeleccionar = { gradoInput = it }
                    )

                    OutlinedTextField(
                        value = apoderadoInput,
                        onValueChange = { apoderadoInput = it },
                        label = { Text("Apoderado (opcional)") },
                        placeholder = { Text("Ej: Maria Rios") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    )

                    DropdownSelector(
                        etiqueta = "Estado",
                        opciones = listOf(
                            OpcionDropdown("ACTIVO", "Activo"),
                            OpcionDropdown("INACTIVO", "Inactivo")
                        ),
                        seleccion = estadoInput,
                        onSeleccionar = { estadoInput = it }
                    )

                    uiState.errorFormulario?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.guardarAlumno(
                            colegioId = ctx.sesion.colegioId,
                            nombre = nombreInput,
                            codigo = codigoInput,
                            nivel = nivelInput,
                            grado = gradoInput,
                            apoderado = apoderadoInput,
                            estado = estadoInput
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (editando != null) "Guardar cambios" else "Crear alumno")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cerrarDialogoForm() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Confirmación para Eliminar
    uiState.alumnoParaEliminar?.let { alumnoEliminar ->
        ConfirmDialog(
            titulo = "Eliminar alumno",
            mensaje = "¿Eliminar a ${alumnoEliminar.nombre}? También se borrará su historial de asistencia. Esta acción no se puede deshacer.",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = { viewModel.confirmarEliminarAlumno(ctx.sesion.colegioId) },
            onCancelar = { viewModel.cancelarEliminarAlumno() }
        )
    }
}
