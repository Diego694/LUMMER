package pe.registroacademico.nativo.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

object SnackbarHelper {

    suspend fun mostrar(
        snackbarHostState: SnackbarHostState,
        mensaje: String,
        accion: String? = null,
        duracion: SnackbarDuration = SnackbarDuration.Short
    ): SnackbarResult {
        return snackbarHostState.showSnackbar(
            message = mensaje,
            actionLabel = accion,
            duration = duracion
        )
    }

    suspend fun mostrarExito(
        snackbarHostState: SnackbarHostState,
        mensaje: String
    ) {
        snackbarHostState.showSnackbar(
            message = "✓ $mensaje",
            duration = SnackbarDuration.Short
        )
    }

    suspend fun mostrarError(
        snackbarHostState: SnackbarHostState,
        mensaje: String
    ) {
        snackbarHostState.showSnackbar(
            message = "✕ $mensaje",
            duration = SnackbarDuration.Long
        )
    }
}
