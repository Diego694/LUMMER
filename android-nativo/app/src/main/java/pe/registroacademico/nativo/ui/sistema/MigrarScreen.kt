package pe.registroacademico.nativo.ui.sistema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun MigrarScreen(ctx: PantallaCtx) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Datos del modo local",
            subtitulo = "Envía a la base online lo que registraste sin internet. No borra nada: tus datos locales siguen en el equipo."
        )

        SectionCard(titulo = "Migración de base de datos local") {
            EmptyState(
                titulo = "Disponible en el programa de PC",
                texto = "Abre el programa de PC en modo online para pasar los datos que registraste en modo local.",
                icono = Icons.Default.SyncAlt
            )
        }

        SectionCard(titulo = "Información del proceso") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "• Esta función está diseñada para la versión de escritorio cuando se trabaja con SQLite/almacenamiento local desconectado.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• En la aplicación Android, la sincronización se realiza directamente a través de la cola de envíos y Supabase.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Nunca sobreescribe los registros online existentes: solo incorpora las asistencias y estudiantes nuevos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
