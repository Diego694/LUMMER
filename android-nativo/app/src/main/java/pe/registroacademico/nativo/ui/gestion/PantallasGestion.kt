package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.registroacademico.nativo.domain.Permisos
import pe.registroacademico.nativo.ui.shell.Pantalla
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import pe.registroacademico.nativo.ui.sistema.pantallasSistema

@Composable
fun PerfilScreenContent(ctx: PantallaCtx) {
    val sesion = ctx.sesion
    val perfil = sesion.perfil

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar de usuario",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = perfil?.nombre?.ifBlank { null } ?: "Usuario",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!perfil?.email.isNullOrBlank()) {
                            Text(
                                text = perfil?.email.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = Permisos.ETIQUETA_ROL[sesion.rol] ?: sesion.rol.name,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )

                    if (sesion.esSuperadmin) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Superadmin") }
                        )
                    }
                }

                perfil?.carrera?.let { carrera ->
                    if (carrera.isNotBlank()) {
                        Text(
                            text = "Carrera: $carrera",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (sesion.nombreInstituto.isNotBlank()) {
                    Text(
                        text = "Institución: ${sesion.nombreInstituto}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { ctx.navegar("registro-qr") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear QR")
            }

            OutlinedButton(
                onClick = {
                    ctx.navegar("login")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }
}

val pantallasGestion: List<Pantalla> = listOf(
    Pantalla(
        id = "perfil",
        titulo = "Mi perfil",
        icono = Icons.Default.AccountCircle,
        grupo = "Principal",
        contenido = { ctx -> PerfilScreenContent(ctx) }
    ),
    Pantalla(
        id = "carnet",
        titulo = "Carnet",
        icono = Icons.Default.ContactPage,
        grupo = "Gestión",
        contenido = { ctx -> CarnetScreen(ctx) }
    ),
    Pantalla(
        id = "codigo",
        titulo = "Código de registro",
        icono = Icons.Default.QrCode,
        grupo = "Gestión",
        contenido = { ctx -> CodigoScreen(ctx) }
    ),
    Pantalla(
        id = "instituto",
        titulo = "Mi instituto",
        icono = Icons.Default.Business,
        grupo = "Gestión",
        soloAdmin = true,
        contenido = { ctx -> InstitutoScreen(ctx) }
    ),
    Pantalla(
        id = "alumnos",
        titulo = "Alumnos",
        icono = Icons.Default.School,
        grupo = "Gestión",
        contenido = { ctx -> AlumnosScreen(ctx) }
    ),
    Pantalla(
        id = "docentes",
        titulo = "Docentes",
        icono = Icons.Default.Work,
        grupo = "Gestión",
        contenido = { ctx -> DocentesScreen(ctx) }
    ),
    Pantalla(
        id = "personal",
        titulo = "Personal y accesos",
        icono = Icons.Default.Group,
        grupo = "Gestión",
        soloAdmin = true,
        contenido = { ctx -> PersonalScreen(ctx) }
    ),
    Pantalla(
        id = "niveles",
        titulo = "Carreras",
        icono = Icons.Default.Layers,
        grupo = "Gestión",
        contenido = { ctx -> NivelesScreen(ctx) }
    ),
    Pantalla(
        id = "grados",
        titulo = "Ciclos y salones",
        icono = Icons.Default.MenuBook,
        grupo = "Gestión",
        contenido = { ctx -> GradosScreen(ctx) }
    ),
    Pantalla(
        id = "cursos",
        titulo = "Cursos",
        icono = Icons.Default.Book,
        grupo = "Gestión",
        soloAdmin = true,
        contenido = { ctx -> CursosScreen(ctx) }
    ),
    Pantalla(
        id = "calendario",
        titulo = "Calendario y horarios",
        icono = Icons.Default.CalendarMonth,
        grupo = "Gestión",
        soloAdmin = true,
        contenido = { ctx -> CalendarioScreen(ctx) }
    ),
    Pantalla(
        id = "periodos",
        titulo = "Periodos y cambio de ciclo",
        icono = Icons.Default.DateRange,
        grupo = "Gestión",
        soloAdmin = true,
        contenido = { ctx -> PeriodosScreen(ctx) }
    ),
    Pantalla(
        id = "justificaciones",
        titulo = "Justificaciones",
        icono = Icons.Default.EventAvailable,
        grupo = "Gestión",
        contenido = { ctx -> JustificacionesScreen(ctx) }
    ),
    Pantalla(
        id = "comunicados",
        titulo = "Comunicados",
        icono = Icons.Default.Notifications,
        grupo = "Gestión",
        contenido = { ctx -> ComunicadosScreen(ctx) }
    )
) + pantallasSistema
