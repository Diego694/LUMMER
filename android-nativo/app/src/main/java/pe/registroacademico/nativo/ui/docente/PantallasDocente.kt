package pe.registroacademico.nativo.ui.docente

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import pe.registroacademico.nativo.ui.shell.Pantalla
import pe.registroacademico.nativo.ui.shell.PantallaCtx

val pantallasDocente: List<Pantalla> = listOf(
    Pantalla(
        id = "qr-curso",
        titulo = "QR por curso",
        icono = Icons.Default.QrCode,
        grupo = "Registro",
        contenido = { ctx: PantallaCtx ->
            QrCursoScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "bitacora",
        titulo = "Bitácora",
        icono = Icons.Default.History,
        grupo = "Consultas",
        contenido = { ctx: PantallaCtx ->
            BitacoraScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "mi-resumen",
        titulo = "Mi resumen",
        icono = Icons.Default.Assessment,
        grupo = "Principal",
        contenido = { ctx: PantallaCtx ->
            ResumenDocenteScreen(ctx = ctx)
        }
    )
)
