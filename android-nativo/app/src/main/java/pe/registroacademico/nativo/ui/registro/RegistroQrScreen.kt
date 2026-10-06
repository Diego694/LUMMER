package pe.registroacademico.nativo.ui.registro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.EstadoBadge
import pe.registroacademico.nativo.ui.components.ListaFila
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.components.TipoEstadoBadge
import pe.registroacademico.nativo.ui.escaner.EscanerScreen
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@Composable
fun RegistroQrScreen(
    ctx: PantallaCtx,
    viewModel: RegistroQrViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(ctx.sesion.colegioId) {
        viewModel.cargarDatos(ctx.sesion.colegioId)
    }

    if (uiState.cargando) {
        SkeletonList(cantidad = 5)
        return
    }

    if (uiState.error != null && uiState.alumnos.isEmpty()) {
        ErrorState(
            mensaje = uiState.error ?: "Error al cargar la pantalla de registro QR",
            onReintentar = { viewModel.cargarDatos(ctx.sesion.colegioId) }
        )
        return
    }

    // Modo escáner a pantalla completa dentro de la sección
    if (uiState.camaraActiva) {
        Box(modifier = Modifier.fillMaxSize()) {
            EscanerScreen(
                onCodigoEscaneado = { codigo ->
                    viewModel.procesarCodigo(
                        texto = codigo,
                        colegioId = ctx.sesion.colegioId,
                        qrModo = ctx.sesion.qrModo,
                        userId = ctx.sesion.userId,
                        esManual = false,
                        onFeedbackHaptico = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                },
                onVolver = {
                    viewModel.toggleCamara(false)
                }
            )

            // Tarjeta flotante con último escaneo sobre la cámara
            uiState.ultimoResultado?.let { resultado ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    ResultadoScanCard(resultado = resultado)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(
            titulo = "Registro por QR",
            subtitulo = "Escanea el carnet del alumno o ingresa su código para marcar la asistencia de hoy.",
            acciones = {
                if (uiState.totalPendientesOffline > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Pendientes sin conexión",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.totalPendientesOffline} pendientes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        )

        // Selector de Modo: Ingreso vs Salida
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Estoy registrando",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = !uiState.modoSalida,
                        onClick = { viewModel.cambiarModo(false) },
                        label = { Text("Ingreso") },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                    FilterChip(
                        selected = uiState.modoSalida,
                        onClick = { viewModel.cambiarModo(true) },
                        label = { Text("Salida") },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        }

        // Sección de Cámara / Escáner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Cámara",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (uiState.modoSalida) "Cámara para registro de salidas" else "Cámara para registro de ingresos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Apunta al código QR del carnet estudiantil del alumno.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.toggleCamara(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iniciar cámara")
                }
            }
        }

        // Formulario de código manual
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "¿Sin cámara? Ingreso manual por código",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.codigoManual,
                        onValueChange = { viewModel.onCodigoManualChange(it) },
                        placeholder = { Text("Código único, ej: a1001") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    )
                    Button(
                        onClick = {
                            viewModel.procesarCodigo(
                                texto = uiState.codigoManual,
                                colegioId = ctx.sesion.colegioId,
                                qrModo = ctx.sesion.qrModo,
                                userId = ctx.sesion.userId,
                                esManual = true,
                                onFeedbackHaptico = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Registrar")
                    }
                }
            }
        }

        // Último resultado registrado
        uiState.ultimoResultado?.let { resultado ->
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ResultadoScanCard(resultado = resultado)
            }
        }

        // Historial de la sesión actual
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Registros de esta sesión",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (uiState.logSesion.isNotEmpty()) {
                Text(
                    text = "${uiState.logSesion.size} registrados",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.logSesion.isEmpty()) {
            EmptyState(
                titulo = "Sin registros todavía",
                texto = "Cada ingreso o salida escaneada aparecerá aquí en tiempo real.",
                icono = Icons.Default.QrCodeScanner
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.logSesion.forEach { item ->
                    val (badgeTxt, badgeTipo) = when (item.estado) {
                        "ok" -> "Registrado" to TipoEstadoBadge.VERDE
                        "tardanza" -> "Tardanza" to TipoEstadoBadge.AMBAR
                        "salida" -> "Salida" to TipoEstadoBadge.AZUL
                        "dup" -> "Duplicado" to TipoEstadoBadge.AMBAR
                        "dup_salida" -> "Salida repetida" to TipoEstadoBadge.AMBAR
                        "sin_entrada" -> "Sin ingreso" to TipoEstadoBadge.ROJO
                        "offline" -> "Sin enviar" to TipoEstadoBadge.AMBAR
                        "pendiente" -> "Pendiente" to TipoEstadoBadge.AMBAR
                        "inactivo" -> "Inactivo" to TipoEstadoBadge.NEUTRAL
                        else -> "Rechazado" to TipoEstadoBadge.ROJO
                    }

                    ListaFila(
                        titulo = StringUtils.censurarNombre(item.alumno.nombre),
                        subtitulo = "${CiclosUtils.etiquetaCiclo(item.alumno.nivel, item.alumno.grado)} · ${item.hora}",
                        iniciales = StringUtils.initials(item.alumno.nombre),
                        badgeTexto = badgeTxt,
                        badgeTipo = badgeTipo
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ResultadoScanCard(resultado: ScanLogItem) {
    val (fondo, borde) = when (resultado.estado) {
        "ok" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        "tardanza", "offline", "dup", "dup_salida", "pendiente" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        "salida" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = fondo),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = StringUtils.initials(resultado.alumno.nombre),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = StringUtils.censurarNombre(resultado.alumno.nombre),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${CiclosUtils.etiquetaCiclo(resultado.alumno.nivel, resultado.alumno.grado)} · ${resultado.hora}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = resultado.mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
