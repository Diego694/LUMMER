package pe.registroacademico.nativo.ui.estudiante

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.data.estudiante.CursoEstudiante
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.SkeletonList

@Composable
fun CursosEstScreen(
    viewModel: CursosEstVM = hiltViewModel()
) {
    var cursoSeleccionadoId by rememberSaveable { mutableStateOf<String?>(null) }

    if (cursoSeleccionadoId != null) {
        AulaEstScreen(
            cursoId = cursoSeleccionadoId!!,
            onVolver = { cursoSeleccionadoId = null }
        )
    } else {
        ListaCursosContenido(
            viewModel = viewModel,
            onSeleccionarCurso = { id -> cursoSeleccionadoId = id }
        )
    }
}

@Composable
private fun ListaCursosContenido(
    viewModel: CursosEstVM,
    onSeleccionarCurso: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
                    text = "Mis Cursos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Selecciona un curso para ver su aula virtual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.recargar() },
                modifier = Modifier
                    .size(48.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Recargar mis cursos"
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
                val cursos = ui.datos
                if (cursos.isEmpty()) {
                    EmptyState(
                        titulo = "Aún no tienes cursos",
                        texto = "Tu instituto todavía no ha registrado cursos activos para tu carrera y ciclo.",
                        icono = Icons.Default.MenuBook,
                        accion = {
                            Button(
                                onClick = { viewModel.recargar() },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Actualizar")
                            }
                        }
                    )
                } else {
                    val grupos = remember(cursos) { EstAulaUtils.agruparCursos(cursos) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        grupos.forEach { grupo ->
                            item(key = "grupo-${grupo.etiqueta}") {
                                Text(
                                    text = grupo.etiqueta,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            items(grupo.cursos, key = { it.id ?: it.nombre }) { curso ->
                                TarjetaCursoEstudiante(
                                    curso = curso,
                                    onClick = { curso.id?.let { onSeleccionarCurso(it) } }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCursoEstudiante(
    curso: CursoEstudiante,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Entrar al curso ${curso.nombre}",
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = curso.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = curso.docente?.ifBlank { "Docente por asignar" } ?: "Docente por asignar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir curso",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
