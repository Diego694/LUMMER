package pe.registroacademico.nativo.ui.aula

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.esAdmin
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.SnackbarHelper
import pe.registroacademico.nativo.ui.gestion.EstudiantesCursoDialog
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AulaScreen(
    ctx: PantallaCtx,
    viewModel: AulaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val esAdmin = ctx.sesion.esAdmin
    val colegioId = ctx.sesion.colegioId

    LaunchedEffect(colegioId) {
        if (colegioId.isNotBlank()) {
            viewModel.cargarCursos(colegioId)
        }
    }

    fun abrirUrlEnNavegador(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            ctx.scope.launch {
                SnackbarHelper.mostrarError(ctx.snackbarHostState, "No se encontró una aplicación para abrir el enlace")
            }
        }
    }

    fun abrirArchivoRemoto(path: String) {
        viewModel.obtenerUrlArchivo(
            path = path,
            onExito = { url -> abrirUrlEnNavegador(url) },
            onError = { err ->
                ctx.scope.launch {
                    SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                }
            }
        )
    }

    fun compartirCsvNotas() {
        val csv = viewModel.exportarNotasCsv()
        if (csv.isNullOrBlank()) {
            ctx.scope.launch {
                SnackbarHelper.mostrarError(ctx.snackbarHostState, "No hay notas para exportar.")
            }
            return
        }
        val curso = uiState.cursoSeleccionado
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Notas - ${curso?.nombre ?: "Curso"}")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Exportar notas CSV"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Aula",
            subtitulo = "Material y actividades de cada curso. Lo que publicas aquí lo ven solo los estudiantes de ese curso."
        )

        when {
            uiState.cargandoCursos -> {
                SkeletonList(cantidad = 3)
            }

            uiState.error != null && uiState.cursos.isEmpty() -> {
                ErrorState(
                    mensaje = uiState.error ?: "Error al cargar cursos",
                    onReintentar = { viewModel.cargarCursos(colegioId) }
                )
            }

            uiState.cursos.isEmpty() -> {
                EmptyState(
                    titulo = "Aún no hay cursos",
                    texto = if (esAdmin) {
                        "Crea el primer curso en Gestión → Cursos y asígnale un docente."
                    } else {
                        "El administrador debe crear los cursos y asignarte a ellos."
                    },
                    icono = Icons.Default.School
                )
            }

            else -> {
                // Selector de curso si hay más de 1 curso
                val opcionesCursos = uiState.cursos.map { c ->
                    val ciclo = if (!c.grado.isNullOrBlank()) {
                        CiclosUtils.etiquetaCiclo(c.nivel, c.grado)
                    } else c.nivel
                    OpcionDropdown(c.id.orEmpty(), "${c.nombre} · $ciclo")
                }

                DropdownSelector(
                    etiqueta = "Curso",
                    opciones = opcionesCursos,
                    seleccion = uiState.cursoSeleccionado?.id.orEmpty(),
                    onSeleccionar = { id ->
                        uiState.cursos.find { it.id == id }?.let { viewModel.seleccionarCurso(it) }
                    }
                )

                // Encabezado del curso activo
                val curso = uiState.cursoSeleccionado
                if (curso != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val cicloDetalle = if (!curso.grado.isNullOrBlank()) {
                            CiclosUtils.etiquetaCiclo(curso.nivel, curso.grado)
                        } else "${curso.nivel} · todos los ciclos"

                        val nombresDocentesAsignados = uiState.docentesIds.mapNotNull {
                            uiState.nombresPersonal[it]
                        }
                        val docentesTexto = when {
                            nombresDocentesAsignados.isNotEmpty() -> " · " + nombresDocentesAsignados.joinToString(", ")
                            !curso.docente.isNullOrBlank() -> " · ${curso.docente}"
                            else -> ""
                        }

                        val isCompact = LocalConfiguration.current.screenWidthDp < 600

                        if (isCompact) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = curso.nombre,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$cicloDetalle$docentesTexto",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                 FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (uiState.gestionaCurso) {
                                        OutlinedButton(
                                            onClick = { viewModel.abrirModalEstudiantes() },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Estudiantes")
                                        }
                                    }
                                    if (esAdmin) {
                                        OutlinedButton(
                                            onClick = { viewModel.abrirModalDocentes() },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Docentes")
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = curso.nombre,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$cicloDetalle$docentesTexto",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (uiState.gestionaCurso) {
                                        OutlinedButton(
                                            onClick = { viewModel.abrirModalEstudiantes() },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Estudiantes")
                                        }
                                    }
                                    if (esAdmin) {
                                        OutlinedButton(
                                            onClick = { viewModel.abrirModalDocentes() },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Docentes")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Barra segmentada de pestañas (Material, Actividades, Notas)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = uiState.seccionActiva == AulaSeccion.MATERIAL,
                                onClick = { viewModel.cambiarSeccion(AulaSeccion.MATERIAL) },
                                label = {
                                    Text(
                                        text = "Material (${uiState.materiales.size})",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors()
                            )

                            FilterChip(
                                selected = uiState.seccionActiva == AulaSeccion.ACTIVIDADES,
                                onClick = { viewModel.cambiarSeccion(AulaSeccion.ACTIVIDADES) },
                                label = {
                                    Text(
                                        text = "Actividades (${uiState.actividades.size})",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors()
                            )

                            if (uiState.gestionaCurso) {
                                FilterChip(
                                    selected = uiState.seccionActiva == AulaSeccion.NOTAS,
                                    onClick = { viewModel.cambiarSeccion(AulaSeccion.NOTAS) },
                                    label = {
                                        Text(
                                            text = "Notas",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors()
                                )
                            }
                        }

                        // Botón de acción contextual según pestaña activa
                        if (uiState.gestionaCurso) {
                            when (uiState.seccionActiva) {
                                AulaSeccion.MATERIAL -> {
                                    Button(
                                        onClick = { viewModel.abrirModalMaterial() },
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Agregar material",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                AulaSeccion.ACTIVIDADES -> {
                                    Button(
                                        onClick = { viewModel.abrirModalActividad() },
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Nueva actividad",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                AulaSeccion.NOTAS -> {
                                    // El botón de Exportar CSV está en NotasTabContenido
                                }
                            }
                        }
                    }

                    // Cuerpo de la sección activa
                    when {
                        uiState.cargandoContenido -> {
                            SkeletonList(cantidad = 3)
                        }

                        uiState.seccionActiva == AulaSeccion.MATERIAL -> {
                            MaterialTabContenido(
                                materiales = uiState.materiales,
                                gestiona = uiState.gestionaCurso,
                                onAbrirEnlace = { abrirUrlEnNavegador(it) },
                                onAbrirArchivo = { abrirArchivoRemoto(it) },
                                onEditar = { viewModel.abrirModalMaterial(it) },
                                onEliminar = { viewModel.confirmarEliminarMaterial(it) }
                            )
                        }

                        uiState.seccionActiva == AulaSeccion.ACTIVIDADES -> {
                            ActividadesTabContenido(
                                actividades = uiState.actividades,
                                gestiona = uiState.gestionaCurso,
                                onAbrirArchivo = { abrirArchivoRemoto(it) },
                                onVerEntregas = { viewModel.verEntregas(it) },
                                onEditar = { viewModel.abrirModalActividad(it) },
                                onEliminar = { viewModel.confirmarEliminarActividad(it) }
                            )
                        }

                        uiState.seccionActiva == AulaSeccion.NOTAS -> {
                            NotasTabContenido(
                                actividades = uiState.actividades,
                                periodoSeleccionado = uiState.periodoFiltro,
                                libroNotas = uiState.libroNotas,
                                cargando = uiState.cargandoNotas,
                                error = uiState.errorNotas,
                                onCambiarPeriodo = { viewModel.cambiarPeriodoFiltro(it) },
                                onExportarCsv = { compartirCsvNotas() },
                                onReintentar = {
                                    uiState.cursoSeleccionado?.let {
                                        viewModel.seleccionarCurso(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Crear / Editar Material
    if (uiState.mostrarModalMaterial && uiState.cursoSeleccionado != null) {
        FormularioMaterialDialog(
            material = uiState.materialParaEditar,
            curso = uiState.cursoSeleccionado!!,
            onGuardar = { mat, nombre, bytes ->
                viewModel.guardarMaterial(
                    material = mat,
                    archivoNombre = nombre,
                    archivoBytes = bytes,
                    onExito = {
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Material guardado")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { viewModel.cerrarModalMaterial() }
        )
    }

    // Confirmación Eliminar Material
    uiState.materialParaEliminar?.let { mat ->
        ConfirmDialog(
            titulo = "Eliminar material",
            mensaje = "¿Eliminar este material «${mat.titulo}»? Los estudiantes dejarán de verlo y no se puede deshacer.",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = {
                viewModel.eliminarMaterial(
                    material = mat,
                    onExito = {
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Material eliminado")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { viewModel.confirmarEliminarMaterial(null) }
        )
    }

    // Modal Crear / Editar Actividad
    if (uiState.mostrarModalActividad && uiState.cursoSeleccionado != null) {
        FormularioActividadDialog(
            actividad = uiState.actividadParaEditar,
            curso = uiState.cursoSeleccionado!!,
            periodoPorDefecto = uiState.periodoFiltro,
            onGuardar = { act, nombre, bytes ->
                viewModel.guardarActividad(
                    actividad = act,
                    archivoNombre = nombre,
                    archivoBytes = bytes,
                    onExito = {
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Actividad guardada")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { viewModel.cerrarModalActividad() }
        )
    }

    // Confirmación Eliminar Actividad
    uiState.actividadParaEliminar?.let { act ->
        ConfirmDialog(
            titulo = "Eliminar actividad",
            mensaje = "¿Eliminar esta actividad «${act.titulo}»? Los estudiantes dejarán de verla y no se puede deshacer.",
            esPeligro = true,
            textoConfirmar = "Eliminar",
            onConfirmar = {
                viewModel.eliminarActividad(
                    actividad = act,
                    onExito = {
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, "Actividad eliminada")
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCancelar = { viewModel.confirmarEliminarActividad(null) }
        )
    }

    // Modal Ver Entregas
    uiState.actividadEntregas?.let { act ->
        DialogoEntregas(
            actividad = act,
            entregas = uiState.entregasActividad,
            cargando = uiState.cargandoEntregas,
            guardandoId = uiState.guardandoCalificacionId,
            onAbrirArchivo = { abrirArchivoRemoto(it) },
            onCalificar = { entregaId, nota, comentario ->
                viewModel.calificarEntrega(
                    entregaId = entregaId,
                    nota = nota,
                    comentario = comentario,
                    onExito = { msg ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(ctx.snackbarHostState, msg)
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCerrar = { viewModel.cerrarModalEntregas() }
        )
    }

    // Modal Docentes del Curso (Admin)
    if (uiState.mostrarModalDocentes && uiState.cursoSeleccionado != null) {
        DocentesDialog(
            curso = uiState.cursoSeleccionado!!,
            personalDocentes = uiState.personalDocente,
            docentesIdsAsignados = uiState.docentesIds,
            guardandoId = uiState.guardandoDocenteId,
            onToggleDocente = { userId, asignar ->
                viewModel.toggleDocente(
                    userId = userId,
                    asignar = asignar,
                    onExito = {
                        ctx.scope.launch {
                            SnackbarHelper.mostrarExito(
                                ctx.snackbarHostState,
                                if (asignar) "Docente asignado" else "Docente quitado"
                            )
                        }
                    },
                    onError = { err ->
                        ctx.scope.launch {
                            SnackbarHelper.mostrarError(ctx.snackbarHostState, err)
                        }
                    }
                )
            },
            onCerrar = { viewModel.cerrarModalDocentes() }
        )
    }

    // Modal Estudiantes del Curso
    if (uiState.mostrarModalEstudiantes && uiState.cursoSeleccionado != null) {
        val curso = uiState.cursoSeleccionado!!
        EstudiantesCursoDialog(
            curso = curso,
            colegioId = ctx.sesion.colegioId,
            userId = ctx.sesion.userId,
            onCerrar = { viewModel.cerrarModalEstudiantes() },
            onActualizado = {
                viewModel.seleccionarCurso(curso)
            }
        )
    }
}
