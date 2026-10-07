package pe.registroacademico.nativo.ui.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import pe.registroacademico.nativo.domain.SesionEstado

data class PantallaCtx(
    val snackbarHostState: SnackbarHostState,
    val navegar: (String) -> Unit,
    val sesion: SesionEstado,
    val scope: CoroutineScope
)

data class Pantalla(
    val id: String,
    val titulo: String,
    val icono: ImageVector,
    val grupo: String,
    val soloAdmin: Boolean = false,
    val soloSuper: Boolean = false,
    val noDocente: Boolean = false,
    val contenido: @Composable (PantallaCtx) -> Unit = {}
)
