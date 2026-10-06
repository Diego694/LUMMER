package pe.registroacademico.nativo.ui.registro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.ui.escaner.EscanerScreen
import pe.registroacademico.nativo.ui.shell.Pantalla
import pe.registroacademico.nativo.ui.shell.PantallaCtx

val pantallasRegistro: List<Pantalla> = listOf(
    Pantalla(
        id = "registro-qr",
        titulo = "Registro por QR",
        icono = Icons.Default.QrCodeScanner,
        grupo = "Registro",
        contenido = { ctx: PantallaCtx ->
            EscanerScreen(
                onCodigoEscaneado = { codigo ->
                    ctx.scope.launch {
                        ctx.snackbarHostState.showSnackbar("Código escaneado: $codigo")
                    }
                },
                onVolver = {
                    ctx.navegar("dashboard")
                }
            )
        }
    )
)
