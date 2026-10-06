package pe.registroacademico.nativo.ui.consultas.avisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StatsUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.consultas.dashboard.ColorTeal
import pe.registroacademico.nativo.ui.consultas.util.ConsultasExportHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun AvisosScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: AvisosViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ctx.sesion.colegioId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargar(ctx.sesion.colegioId)
        }
    }

    if (state.cargando && state.niveles.isEmpty()) {
        SkeletonList(modifier = modifier, cantidad = 5)
        return
    }

    if (state.error != null && state.niveles.isEmpty()) {
        ErrorState(
            mensaje = state.error ?: "Error al cargar avisos",
            onReintentar = { viewModel.cargar(ctx.sesion.colegioId) },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        PageHeader(
            titulo = "Avisos a apoderados",
            subtitulo = "Faltas y tardanzas de hoy (${DateUtils.fmtDate(DateUtils.todayStr())}). Pulsa WhatsApp: se abre con el mensaje ya redactado."
        )

        // Toolbar filtros: Carrera, Ciclo, Tipo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val opcionesNivel = listOf(OpcionDropdown("", "Todas las carreras")) +
                state.niveles.map { OpcionDropdown(it.nombre, it.nombre) }

            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Carrera",
                opciones = opcionesNivel,
                seleccion = state.nivelSeleccionado,
                onSeleccionar = { viewModel.cambiarNivel(ctx.sesion.colegioId, it) }
            )

            val gradosFiltrados = state.grados.filter {
                state.nivelSeleccionado.isBlank() || it.nivel == state.nivelSeleccionado
            }
            val opcionesGrado = listOf(OpcionDropdown("", "Todos los ciclos")) +
                gradosFiltrados.map { OpcionDropdown(it.nombre, it.nombre) }

            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Ciclo",
                opciones = opcionesGrado,
                seleccion = state.gradoSeleccionado,
                onSeleccionar = { viewModel.cambiarGrado(ctx.sesion.colegioId, it) }
            )

            val opcionesTipo = listOf(
                OpcionDropdown("todos", "Faltas y tardanzas"),
                OpcionDropdown("falta", "Solo faltas"),
                OpcionDropdown("tardanza", "Solo tardanzas")
            )
            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Tipo",
                opciones = opcionesTipo,
                seleccion = state.tipoFiltro,
                onSeleccionar = { viewModel.cambiarTipo(ctx.sesion.colegioId, it) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lista de avisos
        SectionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            titulo = "Avisos del día (${state.filas.size})"
        ) {
            if (state.cargando) {
                SkeletonList(cantidad = 4)
            } else if (state.filas.isEmpty()) {
                EmptyState(
                    titulo = "Sin avisos pendientes",
                    texto = "No hay faltas ni tardanzas para los filtros elegidos.",
                    icono = Icons.Default.Check
                )
            } else {
                state.filas.forEach { f ->
                    val a = f.alumno
                    val esFalta = f.tipo == "Falta"
                    val situacionTexto = if (esFalta) "Falta" else "Tardanza ${f.hora}"
                    val situacionTipo = if (esFalta) TipoEstadoBadge.ROJO else TipoEstadoBadge.AMBAR

                    val mensaje = viewModel.redactarMensaje(f, ctx.sesion.nombreInstituto)
                    val urlWa = if (!a.apoderadoTelefono.isNullOrBlank()) {
                        StatsUtils.enlaceWhatsApp(a.apoderadoTelefono, mensaje)
                    } else {
                        ""
                    }

                    val infoApoderado = buildString {
                        if (a.apoderado.isNotBlank()) append(a.apoderado)
                        if (!a.apoderadoTelefono.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(a.apoderadoTelefono)
                        }
                    }.ifBlank { "Sin apoderado asignado" }

                    ListaFila(
                        titulo = a.nombre,
                        subtitulo = "${CiclosUtils.etiquetaCiclo(a.nivel, a.grado)} · $infoApoderado",
                        iniciales = StringUtils.initials(a.nombre),
                        badgeTexto = situacionTexto,
                        badgeTipo = situacionTipo,
                        acciones = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (f.yaAvisado) {
                                    EstadoBadge(texto = "Avisado ✓", tipo = TipoEstadoBadge.VERDE)
                                }

                                if (urlWa.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            ConsultasExportHelper.abrirEnlace(context, urlWa)
                                            viewModel.registrarAvisoEnviado(
                                                colegioId = ctx.sesion.colegioId,
                                                alumnoId = a.id,
                                                tipo = f.tipo,
                                                canal = "WhatsApp",
                                                userId = ctx.sesion.userId
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ColorTeal),
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = "Enviar aviso por WhatsApp",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WhatsApp", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                if (!a.apoderadoEmail.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            ConsultasExportHelper.abrirCorreo(
                                                context = context,
                                                email = a.apoderadoEmail,
                                                asunto = "Aviso de asistencia",
                                                cuerpo = mensaje
                                            )
                                            viewModel.registrarAvisoEnviado(
                                                colegioId = ctx.sesion.colegioId,
                                                alumnoId = a.id,
                                                tipo = f.tipo,
                                                canal = "Correo",
                                                userId = ctx.sesion.userId
                                            )
                                        },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mail,
                                            contentDescription = "Enviar aviso por correo",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Correo", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                if (urlWa.isBlank() && a.apoderadoEmail.isNullOrBlank()) {
                                    Text(
                                        text = "Sin contacto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
