package pe.registroacademico.nativo.ui.registro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QrCodeScanner
import pe.registroacademico.nativo.ui.shell.Pantalla
import pe.registroacademico.nativo.ui.shell.PantallaCtx

val pantallasRegistro: List<Pantalla> = listOf(
    Pantalla(
        id = "pasar-lista",
        titulo = "Pasar lista",
        icono = Icons.Default.Checklist,
        grupo = "Registro",
        contenido = { ctx: PantallaCtx ->
            PasarListaScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "registro-qr",
        titulo = "Registro por QR",
        icono = Icons.Default.QrCodeScanner,
        grupo = "Registro",
        noDocente = true,
        contenido = { ctx: PantallaCtx ->
            RegistroQrScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "quiosco",
        titulo = "Modo quiosco",
        icono = Icons.Default.Badge,
        grupo = "Registro",
        noDocente = true,
        contenido = { ctx: PantallaCtx ->
            QuioscoScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "solicitudes",
        titulo = "Solicitudes de ingreso",
        icono = Icons.Default.HowToReg,
        grupo = "Registro",
        soloAdmin = true,
        contenido = { ctx: PantallaCtx ->
            SolicitudesScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "registro-alumno",
        titulo = "Registro por Alumno",
        icono = Icons.Default.PersonSearch,
        grupo = "Registro",
        noDocente = true,
        contenido = { ctx: PantallaCtx ->
            RegistroAlumnoScreen(ctx = ctx)
        }
    ),
    Pantalla(
        id = "registro-masivo",
        titulo = "Registro Masivo por Ciclo",
        icono = Icons.Default.Checklist,
        grupo = "Registro",
        noDocente = true,
        contenido = { ctx: PantallaCtx ->
            RegistroMasivoScreen(ctx = ctx)
        }
    )
)
