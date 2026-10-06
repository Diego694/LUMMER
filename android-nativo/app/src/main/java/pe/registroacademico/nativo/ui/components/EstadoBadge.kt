package pe.registroacademico.nativo.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class TipoEstadoBadge {
    VERDE,
    AMBAR,
    ROJO,
    AZUL,
    NEUTRAL
}

@Composable
fun EstadoBadge(
    texto: String,
    modifier: Modifier = Modifier,
    tipo: TipoEstadoBadge = TipoEstadoBadge.NEUTRAL
) {
    val (fondo, contenido) = when (tipo) {
        TipoEstadoBadge.VERDE -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
        TipoEstadoBadge.AMBAR -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
        TipoEstadoBadge.ROJO -> Color(0xFFFFEBEE) to Color(0xFFB71C1C)
        TipoEstadoBadge.AZUL -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
        TipoEstadoBadge.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = fondo
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contenido,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
