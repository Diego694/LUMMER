package pe.registroacademico.nativo.ui.aula

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.registroacademico.nativo.data.model.Curso
import pe.registroacademico.nativo.data.model.PersonalItem
import pe.registroacademico.nativo.ui.components.EmptyState

@Composable
fun DocentesDialog(
    curso: Curso,
    personalDocentes: List<PersonalItem>,
    docentesIdsAsignados: List<String>,
    guardandoId: String?,
    onToggleDocente: (String, Boolean) -> Unit,
    onCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Text(
                text = "Docentes de ${curso.nombre}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (personalDocentes.isEmpty()) {
                    EmptyState(
                        titulo = "Sin docentes",
                        texto = "Crea al personal docente en Sistema → Personal y accesos.",
                        icono = Icons.Default.Group
                    )
                } else {
                    personalDocentes.forEach { p ->
                        val asignado = docentesIdsAsignados.contains(p.id)
                        val estaGuardando = guardandoId == p.id

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = p.nombre ?: p.email ?: "Docente",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = p.rol ?: "Docente",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (asignado) {
                                    OutlinedButton(
                                        onClick = { onToggleDocente(p.id, false) },
                                        enabled = !estaGuardando,
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        if (estaGuardando) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Quitar")
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onToggleDocente(p.id, true) },
                                        enabled = !estaGuardando,
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        if (estaGuardando) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Asignar")
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
