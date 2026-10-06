package pe.registroacademico.nativo.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmDialog(
    titulo: String,
    mensaje: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    esPeligro: Boolean = false,
    textoConfirmar: String = "Confirmar",
    textoCancelar: String = "Cancelar"
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = if (esPeligro) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(textoConfirmar)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(textoCancelar)
            }
        }
    )
}
