package pe.registroacademico.nativo.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class OpcionDropdown(
    val id: String,
    val etiqueta: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    etiqueta: String,
    opciones: List<OpcionDropdown>,
    seleccion: String?,
    onSeleccionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val opcionActual = opciones.find { it.id == seleccion }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = opcionActual?.etiqueta ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.etiqueta) },
                    onClick = {
                        onSeleccionar(opcion.id)
                        expanded = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                )
            }
        }
    }
}
