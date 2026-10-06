package pe.registroacademico.nativo.ui.consultas.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.KpiCard
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.consultas.util.ConsultasExportHelper
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import kotlin.math.abs

@Composable
fun DashboardScreen(
    ctx: PantallaCtx,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(ctx.sesion.colegioId) {
        if (ctx.sesion.colegioId.isNotBlank()) {
            viewModel.cargar(ctx.sesion.colegioId)
        }
    }

    if (state.cargando) {
        SkeletonList(modifier = modifier, cantidad = 5)
        return
    }

    if (state.error != null) {
        ErrorState(
            mensaje = state.error ?: "Error desconocido",
            onReintentar = { viewModel.cargar(ctx.sesion.colegioId) },
            modifier = modifier
        )
        return
    }

    val nombreUsuario = ctx.sesion.nombreUsuario.ifBlank {
        ctx.sesion.perfil?.nombre ?: ""
    }.split(" ").firstOrNull() ?: ""

    val saludo = DateUtils.greeting()
    val tituloHeader = if (nombreUsuario.isNotBlank()) "$saludo, $nombreUsuario" else saludo
    val subtituloHeader = "${DateUtils.fmtDate(DateUtils.todayStr())} · Actualizado ${state.actualizado}"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        PageHeader(
            titulo = tituloHeader,
            subtitulo = subtituloHeader,
            acciones = {
                OutlinedButton(
                    onClick = {
                        val csv = viewModel.generarCsv()
                        ConsultasExportHelper.compartirCsv(
                            context = context,
                            titulo = "Exportar Asistencia CSV",
                            nombreArchivo = "asistencia_${state.diasCache.firstOrNull() ?: DateUtils.todayStr()}_a_${DateUtils.todayStr()}.csv",
                            csv = csv
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("Exportar CSV")
                }
            }
        )

        // Botones de acción rápida: Registrar asistencia / Código de registro
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { ctx.navegar("registro-qr") },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text("Registrar")
            }
            OutlinedButton(
                onClick = { ctx.navegar("codigo") },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text("Código")
            }
        }

        // Filtros: Carrera y Periodo ocupando todo el ancho
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.niveles.size > 1) {
                val opcionesCarrera = listOf(OpcionDropdown("", "Todas las carreras")) +
                    state.niveles.map { OpcionDropdown(it, it) }
                pe.registroacademico.nativo.ui.components.DropdownSelector(
                    etiqueta = "Carrera",
                    opciones = opcionesCarrera,
                    seleccion = state.carreraFiltro,
                    onSeleccionar = { viewModel.cambiarCarrera(it) }
                )
            }

            val opcionesRango = listOf(
                OpcionDropdown("7", "Últimos 7 días hábiles"),
                OpcionDropdown("14", "Últimos 14 días hábiles"),
                OpcionDropdown("30", "Últimos 30 días hábiles")
            )
            pe.registroacademico.nativo.ui.components.DropdownSelector(
                etiqueta = "Periodo",
                opciones = opcionesRango,
                seleccion = state.rangoDias.toString(),
                onSeleccionar = {
                    it.toIntOrNull()?.let { r -> viewModel.cambiarRango(ctx.sesion.colegioId, r) }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tarjetas de KPIs del día
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                valor = state.resumen.activos.toString(),
                etiqueta = "Alumnos activos",
                delta = "${state.inactivos} inactivo(s) · ${state.totalCiclos} ciclos",
                icono = Icons.Default.Group
            )
            KpiCard(
                valor = state.resumen.presentes.toString(),
                etiqueta = "Presentes hoy",
                delta = "${state.resumen.puntuales} puntuales · ${state.resumen.tardes} tardanzas",
                icono = Icons.Default.Checklist
            )
            val valorAusentes = if (state.esFinDeSemana && state.resumen.presentes == 0) "—" else state.resumen.ausentes.toString()
            val hintAusentes = if (state.esFinDeSemana && state.resumen.presentes == 0) {
                "Fin de semana: sin clases"
            } else if (state.resumen.ausentes > 0) {
                "Sin registro de ingreso"
            } else {
                "¡Asistencia completa!"
            }
            KpiCard(
                valor = valorAusentes,
                etiqueta = "Ausentes hoy",
                delta = hintAusentes,
                icono = Icons.Default.Person
            )
            val deltaTexto = if (state.delta != null) "${abs(state.delta!!)} pts · ${state.deltaHint}" else state.deltaHint
            KpiCard(
                valor = "${state.resumen.pct}%",
                etiqueta = "% de asistencia hoy",
                delta = deltaTexto,
                icono = Icons.Default.Assessment
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gráficos dibujados con Canvas de Compose
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(titulo = "Tendencia de asistencia") {
                GraficoTendencia(serie = state.serie)
            }

            SectionCard(titulo = "Estado de hoy") {
                GraficoEstadoHoy(
                    puntuales = state.resumen.puntuales,
                    tardes = state.resumen.tardes,
                    ausentes = state.resumen.ausentes,
                    pct = state.resumen.pct
                )
            }

            SectionCard(titulo = "Asistencia de hoy por ciclo") {
                GraficoPorGrado(grados = state.porGrado, metaPct = 85)
            }

            SectionCard(titulo = "Alumnos por carrera") {
                GraficoAlumnosPorCarrera(niveles = state.porNivel)
            }

            // Sección: Últimos ingresos
            SectionCard(
                titulo = "Últimos ingresos",
                acciones = {
                    TextButton(onClick = { ctx.navegar("asist-grado") }) {
                        Text("Ver asistencia por ciclo →")
                    }
                }
            ) {
                if (state.recientes.isEmpty()) {
                    EmptyState(
                        titulo = "Aún no hay ingresos hoy",
                        texto = "Los registros aparecerán aquí en cuanto se escanee el primer carnet."
                    )
                } else {
                    state.recientes.forEach { item ->
                        ListaFila(
                            titulo = item.alumno?.nombre ?: "—",
                            subtitulo = "${item.alumno?.grado ?: "—"} · ${item.hora}",
                            iniciales = StringUtils.initials(item.alumno?.nombre),
                            badgeTexto = if (item.esTardanza) "Tardanza" else "Puntual",
                            badgeTipo = if (item.esTardanza) TipoEstadoBadge.AMBAR else TipoEstadoBadge.VERDE
                        )
                    }
                }
            }

            // Sección: Requieren atención (< 85%)
            SectionCard(titulo = "Requieren atención (< 85% en ${state.rangoDias} días)") {
                if (state.bajaAsistencia.isEmpty()) {
                    EmptyState(
                        titulo = "Todo en orden",
                        texto = "Ningún alumno está por debajo de la meta en este periodo."
                    )
                } else {
                    state.bajaAsistencia.forEach { item ->
                        ListaFila(
                            titulo = item.alumno.nombre,
                            subtitulo = "${item.alumno.grado} · ${item.presentes}/${item.dias} días",
                            iniciales = StringUtils.initials(item.alumno.nombre),
                            badgeTexto = "${item.pct}%",
                            badgeTipo = if (item.pct < 60) TipoEstadoBadge.ROJO else TipoEstadoBadge.AMBAR
                        )
                    }
                }
            }

            // Sección: Últimos comunicados
            SectionCard(
                titulo = "Últimos comunicados",
                acciones = {
                    TextButton(onClick = { ctx.navegar("comunicados") }) {
                        Text("Gestionar →")
                    }
                }
            ) {
                if (state.comunicados.isEmpty()) {
                    EmptyState(
                        titulo = "Sin comunicados",
                        texto = "Publica el primero desde la sección Comunicados."
                    )
                } else {
                    state.comunicados.take(3).forEach { c ->
                        ListaFila(
                            titulo = c.titulo,
                            subtitulo = "${DateUtils.fmtDate(c.fecha)} · ${c.mensaje}",
                            icono = Icons.Default.Notifications
                        )
                    }
                }
            }
        }
    }
}
