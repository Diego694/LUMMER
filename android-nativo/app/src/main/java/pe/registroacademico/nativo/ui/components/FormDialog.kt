package pe.registroacademico.nativo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

data class CampoFormulario(
    val nombre: String,
    val etiqueta: String,
    val valorInicial: String = "",
    val esRequerido: Boolean = true,
    val esPassword: Boolean = false,
    val placeholder: String = ""
)

@Composable
fun FormDialog(
    titulo: String,
    campos: List<CampoFormulario>,
    onConfirmar: (Map<String, String>) -> Unit,
    onCancelar: () -> Unit,
    textoConfirmar: String = "Guardar",
    textoCancelar: String = "Cancelar"
) {
    val valores = remember {
        mutableStateMapOf<String, String>().apply {
            campos.forEach { put(it.nombre, it.valorInicial) }
        }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                campos.forEach { campo ->
                    val valor = valores[campo.nombre] ?: ""
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { valores[campo.nombre] = it },
                        label = { Text(campo.etiqueta) },
                        placeholder = {
                            if (campo.placeholder.isNotBlank()) Text(campo.placeholder)
                        },
                        singleLine = true,
                        visualTransformation = if (campo.esPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = if (campo.esPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(valores.toMap())
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
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
