package pe.registroacademico.nativo.ui.registro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.Permisos
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.ConfirmDialog
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SearchField
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.escaner.EscanerScreen
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import java.text.Collator
import java.util.Locale

@Composable
fun PasarListaScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: PasarListaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val esAdmin = Permisos.esAdmin(ctx.sesion.rol) || ctx.sesion.esSuperadmin

    LaunchedEffect(ctx.sesion.colegioId, ctx.sesion.userId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargarCursos(ctx.sesion.colegioId, ctx.sesion.userId, esAdmin)
        }
    }

    LaunchedEffect(uiState.mensajeSnackbar) {
        uiState.mensajeSnackbar?.let { msg ->
            ctx.snackbarHostState.showSnackbar(msg)
            viewModel.limpiarMensajeSnackbar()
        }
    }

    if (uiState.cargando && uiState.cursosMios.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 5)
        return
    }

    if (uiState.error != null && uiState.cursosMios.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error desconocido",
            onReintentar = { viewModel.cargarCursos(ctx.sesion.colegioId, ctx.sesion.userId, esAdmin) },
            modifier = modifier
        )
        return
    }

    val cursoActual = uiState.cursoSeleccionado
    if (cursoActual == null) {
        VistaSeleccionarCurso(
            cursos = uiState.cursosMios,
            todosAlumnos = uiState.todosAlumnos,
            esAdmin = esAdmin,
            onSeleccionarCurso = { curso ->
                viewModel.abrirCurso(curso, ctx.sesion.colegioId)
            },
            modifier = modifier
        )
    } else {
        VistaClase(
            curso = cursoActual,
            uiState = uiState,
            viewModel = viewModel,
            ctx = ctx,
            onVolver = { viewModel.volverALista() },
            modifier = modifier
        )
    }
}

@Composable
private fun VistaSeleccionarCurso(
    cursos: List<Curso>,
    todosAlumnos: List<Alumno>,
    esAdmin: Boolean,
    onSeleccionarCurso: (Curso) -> Unit,
    modifier: Modifier = Modifier
) {
    val collator = remember { Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        PageHeader(
            titulo = "Pasar lista",
            subtitulo = "Elige tu curso y marca a quienes están presentes en esta clase."
        )

        if (cursos.isEmpty()) {
            EmptyState(
                titulo = "Aún no tienes cursos asignados",
                texto = if (esAdmin) {
                    "Crea cursos en Gestión → Cursos y asigna a los docentes."
                } else {
                    "Pide al administrador que te asigne tus cursos (Aula virtual → Docentes del curso)."
                },
                icono = Icons.Default.MenuBook,
                modifier = Modifier.padding(16.dp)
            )
            return
        }

        val porCarrera = remember(cursos) {
            cursos.map { it.nivel }.distinct().sortedWith { a, b -> collator.compare(a, b) }
        }

        porCarrera.forEach { carrera ->
            val cursosDeCarrera = remember(cursos, carrera) {
                cursos.filter { it.nivel == carrera }.sortedWith { a, b ->
                    val cmpGrado = collator.compare(a.grado ?: "", b.grado ?: "")
                    if (cmpGrado != 0) cmpGrado else collator.compare(a.nombre, b.nombre)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = carrera,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                cursosDeCarrera.forEach { curso ->
                    val alumnosDelCurso = remember(todosAlumnos, curso) {
                        PasarListaUtils.alumnosDelCurso(todosAlumnos, curso)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccionarCurso(curso) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val cicloTexto = if (!curso.grado.isNullOrBlank()) {
                                    CiclosUtils.cicloCorto(curso.grado, curso.nivel)
                                } else {
                                    "Todos los ciclos"
                                }
                                Text(
                                    text = cicloTexto,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = curso.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${alumnosDelCurso.size} estudiantes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { onSeleccionarCurso(curso) },
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pasar lista")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VistaClase(
    curso: Curso,
    uiState: PasarListaUiState,
    viewModel: PasarListaViewModel,
    ctx: PantallaCtx,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var mostrarConfirmarTodos by remember { mutableStateOf(false) }

    val alumnos = uiState.alumnosCurso
    val marcados = uiState.marcados
    val resumen = remember(alumnos, marcados) {
        PasarListaUtils.resumenLista(alumnos, marcados.keys)
    }

    val fechaFormateada = remember {
        DateUtils.fmtDate(DateUtils.todayStr())
    }

    val cicloSubtitulo = if (!curso.grado.isNullOrBlank()) {
        CiclosUtils.cicloCorto(curso.grado, curso.nivel)
    } else {
        "todos los ciclos"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Cabecera con botón de retorno
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onVolver,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a Mis cursos",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mis cursos")
            }
        }

        // Título del curso
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = curso.nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${curso.nivel} · $cicloSubtitulo · $fechaFormateada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de resumen de asistencia de la clase
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${resumen.presentes}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "presentes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${resumen.faltan}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (resumen.faltan > 0) Color(0xFFE65100) else Color(0xFF2E7D32)
                        )
                        Text(
                            text = "sin marcar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${resumen.total}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "en el curso",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { (resumen.pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF00796B),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "${resumen.pct}% de asistencia de la clase",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botones de acción principales
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleCamara(!uiState.camaraActiva) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.camaraActiva) Icons.Default.Close else Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.camaraActiva) "Cerrar cámara" else "Escanear carnets")
                }

                OutlinedButton(
                    onClick = {
                        val pendientes = alumnos.filter { it.id !in marcados }
                        if (pendientes.isEmpty()) {
                            viewModel.marcarTodosPendientes(ctx.sesion.colegioId, ctx.sesion.userId)
                        } else {
                            mostrarConfirmarTodos = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Marcar a todos")
                }
            }

            // Checkbox: Marcar también ingreso al instituto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleTambienIngreso(!uiState.tambienIngreso) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.tambienIngreso,
                    onCheckedChange = { viewModel.toggleTambienIngreso(it) }
                )
                Text(
                    text = "Marcar también su ingreso al instituto si aún no ingresó hoy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Cámara de escaneo activa
        AnimatedVisibility(visible = uiState.camaraActiva) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    EscanerScreen(
                        onCodigoEscaneado = { codigo ->
                            viewModel.procesarCodigo(
                                texto = codigo,
                                colegioId = ctx.sesion.colegioId,
                                qrModo = ctx.sesion.qrModo,
                                userId = ctx.sesion.userId,
                                esManual = false,
                                onFeedbackHaptico = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        },
                        onVolver = { viewModel.toggleCamara(false) }
                    )
                }
            }
        }

        // Entrada manual por código de estudiante
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.codigoManual,
                onValueChange = { viewModel.onCodigoManualChange(it) },
                placeholder = { Text("¿Sin cámara? Código del alumno") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = {
                    if (uiState.codigoManual.isNotBlank()) {
                        viewModel.procesarCodigo(
                            texto = uiState.codigoManual,
                            colegioId = ctx.sesion.colegioId,
                            qrModo = ctx.sesion.qrModo,
                            userId = ctx.sesion.userId,
                            esManual = true,
                            onFeedbackHaptico = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
            ) {
                Text("Marcar")
            }
        }

        // Resultado del último escaneo o marcado manual
        val ultimoRes = uiState.ultimoResultado
        if (ultimoRes != null) {
            val (fondoColor, textoColor) = when (ultimoRes.tipo) {
                TipoResultadoEscaneo.OK -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
                TipoResultadoEscaneo.WARN -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
                TipoResultadoEscaneo.ERROR -> Color(0xFFFFEBEE) to Color(0xFFB71C1C)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = fondoColor
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ultimoRes.alumno != null) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = textoColor.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = StringUtils.initials(ultimoRes.alumno.nombre),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textoColor
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        if (ultimoRes.alumno != null) {
                            Text(
                                text = ultimoRes.alumno.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = textoColor
                            )
                        }
                        Text(
                            text = ultimoRes.mensaje,
                            style = MaterialTheme.typography.bodySmall,
                            color = textoColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // Filtros: Todos / Sin marcar / Presentes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filtro == FiltroLista.TODOS,
                onClick = { viewModel.cambiarFiltro(FiltroLista.TODOS) },
                label = { Text("Todos (${resumen.total})") }
            )
            FilterChip(
                selected = uiState.filtro == FiltroLista.FALTAN,
                onClick = { viewModel.cambiarFiltro(FiltroLista.FALTAN) },
                label = { Text("Sin marcar (${resumen.faltan})") }
            )
            FilterChip(
                selected = uiState.filtro == FiltroLista.PRESENTES,
                onClick = { viewModel.cambiarFiltro(FiltroLista.PRESENTES) },
                label = { Text("Presentes (${resumen.presentes})") }
            )
        }

        // Búsqueda de estudiantes
        SearchField(
            query = uiState.busqueda,
            onQueryChange = { viewModel.cambiarBusqueda(it) },
            placeholder = "Buscar estudiante...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Lista de alumnos filtrada
        val qNorm = uiState.busqueda.trim().lowercase()
        val alumnosFiltrados = remember(alumnos, marcados, uiState.filtro, qNorm) {
            alumnos.filter { a ->
                val cumpleFiltro = when (uiState.filtro) {
                    FiltroLista.TODOS -> true
                    FiltroLista.FALTAN -> a.id !in marcados
                    FiltroLista.PRESENTES -> a.id in marcados
                }
                val cumpleBusqueda = qNorm.isBlank() ||
                    a.nombre.lowercase().contains(qNorm) ||
                    a.codigo.lowercase().contains(qNorm)
                cumpleFiltro && cumpleBusqueda
            }
        }

        if (alumnosFiltrados.isEmpty()) {
            EmptyState(
                titulo = if (alumnos.isNotEmpty()) "Nada que mostrar" else "Sin estudiantes",
                texto = if (alumnos.isNotEmpty()) {
                    "Cambia el filtro o la búsqueda."
                } else {
                    "No hay estudiantes activos y aprobados en este curso."
                },
                icono = Icons.Default.Group,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alumnosFiltrados.forEach { alumno ->
                    val marcado = marcados[alumno.id]
                    val esTardanza = if (marcado != null) {
                        StatsUtils.esTardanza(marcado.hora, "08:15", alumno.nivel)
                    } else {
                        false
                    }

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (marcado != null) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = StringUtils.initials(alumno.nombre),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alumno.nombre,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = alumno.codigo,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Estado y botón grande "Presente"
                            if (marcado != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (esTardanza) {
                                        EstadoBadge(
                                            texto = "Tarde · ${marcado.hora}",
                                            tipo = TipoEstadoBadge.AMBAR
                                        )
                                    } else {
                                        EstadoBadge(
                                            texto = "Presente · ${marcado.hora}",
                                            tipo = TipoEstadoBadge.VERDE
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Presente",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.marcarAlumno(
                                            alumno = alumno,
                                            colegioId = ctx.sesion.colegioId,
                                            userId = ctx.sesion.userId,
                                            origen = "manual"
                                        )
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                                ) {
                                    Text("Presente", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarConfirmarTodos) {
        val pendientesCount = alumnos.count { it.id !in marcados }
        ConfirmDialog(
            titulo = "Marcar a todos presentes",
            mensaje = "Se marcará como presentes a los $pendientesCount estudiantes que aún no están marcados. Una marca no se puede deshacer desde aquí (solo el administrador puede corregirla).",
            textoConfirmar = "Marcar a todos",
            onConfirmar = {
                viewModel.marcarTodosPendientes(ctx.sesion.colegioId, ctx.sesion.userId)
                mostrarConfirmarTodos = false
            },
            onCancelar = { mostrarConfirmarTodos = false },
            esPeligro = false
        )
    }
}
