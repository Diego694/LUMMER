package pe.registroacademico.nativo.ui.estudiante

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.estudiante.AsistenciaCursoEstudiante
import pe.registroacademico.nativo.data.estudiante.AsistenciaEstudiante
import pe.registroacademico.nativo.data.estudiante.CursoEstudiante
import pe.registroacademico.nativo.data.estudiante.NotaEstudiante
import pe.registroacademico.nativo.domain.estudiante.ResumenAsistenciaEstudiante
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge

@Composable
fun AsistenciaEstScreen(
    asistenciaVm: AsistenciaEstVM = hiltViewModel(),
    notasVm: NotasEstVM = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (selectedTab == 0) "Mi Asistencia" else "Mis Calificaciones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedTab == 0) "Registro institucional y por curso" else "Resumen académico general y por curso",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    if (selectedTab == 0) asistenciaVm.recargar() else notasVm.recargar()
                },
                modifier = Modifier
                    .size(48.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Recargar datos"
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Asistencia") },
                icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Notas") },
                icon = { Icon(Icons.Default.Grade, contentDescription = null) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
        }

        when (selectedTab) {
            0 -> AsistenciaSeccion(viewModel = asistenciaVm)
            1 -> NotasSeccion(viewModel = notasVm)
        }
    }
}

@Composable
private fun AsistenciaSeccion(
    viewModel: AsistenciaEstVM,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    when (val ui = state) {
        is EstUiState.Cargando -> {
            SkeletonList(cantidad = 5)
        }
        is EstUiState.Error -> {
            ErrorState(
                mensaje = ui.mensaje,
                onReintentar = { viewModel.recargar() }
            )
        }
        is EstUiState.Datos -> {
            val datos = ui.datos
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // KPIs de asistencia
                item(key = "kpis") {
                    KpisAsistencia(resumen = datos.resumen)
                }

                // Asistencia institucional por fecha
                item(key = "titulo-asistencias") {
                    Text(
                        text = "Registro por fecha",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (datos.asistencias.isEmpty()) {
                    item(key = "vacio-asistencias") {
                        EmptyState(
                            titulo = "Sin asistencias registradas",
                            texto = "Aún no se registran ingresos institucionales.",
                            icono = Icons.Default.FactCheck
                        )
                    }
                } else {
                    items(datos.asistencias, key = { it.id ?: "${it.fecha}-${it.hora}" }) { a ->
                        TarjetaAsistencia(asistencia = a)
                    }
                }

                // Asistencia por curso (marcado QR)
                if (datos.asistenciasCurso.isNotEmpty()) {
                    item(key = "titulo-asistencia-curso") {
                        Text(
                            text = "Asistencia en cursos (marcado con QR)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(datos.asistenciasCurso, key = { it.id ?: "${it.cursoId}-${it.fecha}-${it.hora}" }) { ac ->
                        TarjetaAsistenciaCurso(asistenciaCurso = ac, curso = datos.cursos[ac.cursoId])
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun KpisAsistencia(resumen: ResumenAsistenciaEstudiante) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                titulo = "Presentes",
                valor = resumen.presentes.toString(),
                icono = Icons.Default.CheckCircle,
                colorIcono = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                titulo = "Tardanzas",
                valor = resumen.tardanzas.toString(),
                icono = Icons.Default.Schedule,
                colorIcono = Color(0xFFF57F17),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                titulo = "Faltas",
                valor = resumen.faltas.toString(),
                icono = Icons.Default.Close,
                colorIcono = Color(0xFFC62828),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                titulo = "Porcentaje",
                valor = "${resumen.porcentaje}%",
                icono = Icons.Default.FactCheck,
                colorIcono = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    titulo: String,
    valor: String,
    icono: ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 72.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = colorIcono,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = valor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TarjetaAsistencia(asistencia: AsistenciaEstudiante) {
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
            Column {
                Text(
                    text = asistencia.fecha,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ingreso: ${asistencia.hora}${if (!asistencia.horaSalida.isNullOrBlank()) " · Salida: ${asistencia.horaSalida}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            EstadoBadge(
                texto = "Registrado",
                tipo = TipoEstadoBadge.VERDE
            )
        }
    }
}

@Composable
private fun TarjetaAsistenciaCurso(
    asistenciaCurso: AsistenciaCursoEstudiante,
    curso: CursoEstudiante?
) {
    var expandido by rememberSaveable(asistenciaCurso.id, asistenciaCurso.fecha, asistenciaCurso.hora) {
        mutableStateOf(false)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = curso?.nombre ?: "Curso",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${asistenciaCurso.fecha} · ${asistenciaCurso.hora}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                EstadoBadge(
                    texto = "QR Curso",
                    tipo = TipoEstadoBadge.AZUL
                )
            }

            TextButton(onClick = { expandido = !expandido }) {
                Text(if (expandido) "Ocultar información" else "Más información")
            }

            if (expandido) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetalleAsistencia("Curso", curso?.nombre ?: "No disponible")
                    if (!curso?.docente.isNullOrBlank()) DetalleAsistencia("Docente", curso?.docente.orEmpty())
                    if (curso != null) {
                        DetalleAsistencia(
                            "Carrera / ciclo",
                            listOfNotNull(curso.nivel, curso.grado?.takeIf { it.isNotBlank() }).joinToString(" · ")
                        )
                    }
                    DetalleAsistencia("Fecha", asistenciaCurso.fecha)
                    DetalleAsistencia("Hora de registro", asistenciaCurso.hora)
                    DetalleAsistencia(
                        "Método",
                        if (asistenciaCurso.origen == "qr-docente") "QR del docente" else (asistenciaCurso.origen ?: "Registro")
                    )
                }
            }
        }
    }
}

@Composable
private fun DetalleAsistencia(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = valor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NotasSeccion(
    viewModel: NotasEstVM,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

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

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Promedio general
                item(key = "promedio-general") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                            Column {
                                Text(
                                    text = "Promedio General",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${EstAulaUtils.formatearNota(datos.promedioGeneral)} / 20",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${datos.notas.size} actividades evaluadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            if (datos.promedioGeneral != null) {
                                EstadoBadge(
                                    texto = if (datos.promedioGeneral >= 10.5) "Aprobatorio" else "En riesgo",
                                    tipo = if (datos.promedioGeneral >= 10.5) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                                )
                            }
                        }
                    }
                }

                if (datos.porCurso.isEmpty()) {
                    item(key = "vacio-notas") {
                        EmptyState(
                            titulo = "Sin calificaciones registradas",
                            texto = "Tus docentes aún no han publicado notas de tus actividades.",
                            icono = Icons.Default.Grade
                        )
                    }
                } else {
                    datos.porCurso.forEach { (cursoNombre, notasCurso) ->
                        item(key = "curso-header-$cursoNombre") {
                            val promCurso = EstAulaUtils.calcularPromedioCurso(notasCurso)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cursoNombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (promCurso != null) {
                                    EstadoBadge(
                                        texto = "Promedio: ${EstAulaUtils.formatearNota(promCurso)}",
                                        tipo = if (promCurso >= 10.5) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                                    )
                                }
                            }
                        }

                        items(notasCurso, key = { "${it.cursoId}-${it.actividadId}" }) { n ->
                            TarjetaNotaEstudiante(nota = n)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TarjetaNotaEstudiante(nota: NotaEstudiante) {
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
                    text = nota.actividadTitulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Periodo ${nota.periodo} · Escala: 0 a ${nota.puntajeMax.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (nota.comentario.isNotBlank()) {
                    Text(
                        text = "Comentario: ${nota.comentario}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (nota.nota != null) {
                EstadoBadge(
                    texto = "${EstAulaUtils.formatearNota(nota.nota)} / ${nota.puntajeMax.toInt()}",
                    tipo = if (nota.nota >= (nota.puntajeMax * 0.525)) TipoEstadoBadge.VERDE else TipoEstadoBadge.ROJO
                )
            } else {
                EstadoBadge(texto = "Pendiente", tipo = TipoEstadoBadge.NEUTRAL)
            }
        }
    }
}
