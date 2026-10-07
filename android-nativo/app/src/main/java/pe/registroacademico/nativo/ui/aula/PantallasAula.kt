package pe.registroacademico.nativo.ui.aula

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import pe.registroacademico.nativo.ui.shell.Pantalla
import pe.registroacademico.nativo.ui.shell.PantallaCtx

val pantallasAula: List<Pantalla> = listOf(
    Pantalla(
        id = "aula",
        titulo = "Aula",
        icono = Icons.Default.MenuBook,
        grupo = "Principal",
        contenido = { ctx: PantallaCtx ->
            AulaScreen(ctx = ctx)
        }
    )
)
