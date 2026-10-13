package pe.registroacademico.nativo.ui.registro

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.DropdownSelector
import pe.registroacademico.nativo.ui.components.OpcionDropdown
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.escaner.EscanerScreen
import pe.registroacademico.nativo.ui.shell.PantallaCtx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuioscoScreen(
    ctx: PantallaCtx,
    viewModel: QuioscoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // Mantener la pantalla encendida mientras el modo QR de quiosco esté activo
    DisposableEffect(uiState.quioscoActivo && uiState.mostrarQr) {
        val window = (context as? Activity)?.window
        if (uiState.quioscoActivo && uiState.mostrarQr) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(ctx.sesion.colegioId) {
        viewModel.cargarDatos(ctx.sesion.colegioId)
    }

    if (uiState.cargando && uiState.alumnos.isEmpty()) {
        SkeletonList(cantidad = 4)
        return
    }

    if (uiState.quioscoActivo) {
        // Pantalla completa de Quiosco Activo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TopBar de Quiosco
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = ctx.sesion.nombreInstituto.ifBlank { "LUMMER" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = when (uiState.modoOperacion) {
                                "salida" -> "MODO: SOLO SALIDA"
                                "entrada" -> "MODO: SOLO INGRESO"
                                else -> "MODO: INGRESO Y SALIDA"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Reloj Lima
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.horaLimaActual,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = uiState.fechaLimaActual,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    // Botón para salir (con PIN)
                    IconButton(
                        onClick = { viewModel.solicitarSalidaQuiosco() },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Salir del modo quiosco",
                            tint = Color.White
                        )
                    }
                }

                // Conmutador visible dentro del quiosco: "Cámara frontal" / "Mostrar QR"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !uiState.mostrarQr,
                        onClick = {
                            if (uiState.mostrarQr) {
                                viewModel.activarVistaCamara()
                            }
                        },
                        label = {
                            Text(
                                text = "Cámara frontal",
                                fontWeight = if (!uiState.mostrarQr) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8),
                            iconColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    )

                    FilterChip(
                        selected = uiState.mostrarQr,
                        onClick = {
                            if (!uiState.mostrarQr) {
                                viewModel.activarVistaQr(ctx.sesion.colegioId)
                            }
                        },
                        label = {
                            Text(
                                text = "Mostrar QR",
                                fontWeight = if (uiState.mostrarQr) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8),
                            iconColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    )
                }

                if (uiState.mostrarQr) {
                    // Vista QR de Ingreso: detiene cámara, muestra QR grande, cuenta atrás, selector 15/30 s
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF020617))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Selector de intervalo en la propia pantalla
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Rotación:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF94A3B8)
                                )
                                FilterChip(
                                    selected = uiState.intervaloQrSegundos == 15,
                                    onClick = { viewModel.cambiarIntervaloQr(15, ctx.sesion.colegioId) },
                                    label = { Text("15 s") },
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                                FilterChip(
                                    selected = uiState.intervaloQrSegundos == 30,
                                    onClick = { viewModel.cambiarIntervaloQr(30, ctx.sesion.colegioId) },
                                    label = { Text("30 s") },
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }

                            // QR Grande
                            val qrBm = uiState.qrBitmap
                            if (uiState.cargandoQr && qrBm == null) {
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color.White)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Iniciando sesión QR...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else if (qrBm != null) {
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBm.asImageBitmap(),
                                        contentDescription = "Código QR rotativo de ingreso",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Sin código QR activo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Cuenta atrás de rotación
                            if (uiState.sesionQrId != null) {
                                val intervaloMax = uiState.intervaloQrSegundos.toFloat().coerceAtLeast(1f)
                                val progreso = (uiState.segundosRestantesQr.toFloat() / intervaloMax).coerceIn(0f, 1f)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "El código cambia en ${uiState.segundosRestantesQr} s",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { progreso },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = Color(0xFF38BDF8),
                                        trackColor = Color(0xFF334155)
                                    )
                                }
                            }

                            // Texto obligatorio del contrato
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                Text(
                                    text = "Los estudiantes escanean este código con la app LUMMER → Escanear",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF1F5F9),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            // Mensaje de error de red y reintento
                            if (uiState.errorQr != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = uiState.errorQr ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { viewModel.reintentarQr(ctx.sesion.colegioId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            contentPadding = ButtonDefaults.TextButtonContentPadding
                                        ) {
                                            Text("Reintentar", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Vista central: Escáner y Feedback (Cámara frontal)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        EscanerScreen(
                            onCodigoEscaneado = { codigo ->
                                viewModel.procesarLectura(
                                    texto = codigo,
                                    colegioId = ctx.sesion.colegioId,
                                    qrModo = ctx.sesion.qrModo,
                                    userId = ctx.sesion.userId,
                                    onHapticFeedback = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                            },
                            onVolver = { viewModel.solicitarSalidaQuiosco() }
                        )

                        // Gran tarjeta de resultado o espera
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                        ) {
                            val resultado = uiState.ultimoResultado
                            if (resultado != null) {
                                val (cardColor, textColor) = when (resultado.estado) {
                                    "ok" -> Color(0xFF15803D) to Color.White
                                    "salida" -> Color(0xFF1D4ED8) to Color.White
                                    "tardanza", "dup", "dup_salida", "salida_temprana", "temprano", "offline" -> Color(0xFFD97706) to Color.White
                                    else -> Color(0xFFB91C1C) to Color.White
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = StringUtils.initials(resultado.alumno.nombre),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(20.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = StringUtils.censurarNombre(resultado.alumno.nombre),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${CiclosUtils.etiquetaCiclo(resultado.alumno.nivel, resultado.alumno.grado)} · ${resultado.hora}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = resultado.mensaje,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Acerca tu carnet",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Muestra el código QR a la cámara para marcar tu asistencia",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFCBD5E1),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer de Contadores
                val activos = uiState.alumnos.filter { it.estado == "ACTIVO" && it.aprobado != false }.size
                val ingresados = uiState.asistenciasHoy.size
                val salidas = uiState.asistenciasHoy.count { !it.horaSalida.isNullOrBlank() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ingresados de $activos ingresaron",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = "$salidas salidas",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    if (uiState.totalPendientesOffline > 0) {
                        Text(
                            text = "${uiState.totalPendientesOffline} sin enviar",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }
                }
            }

            // Modal de PIN para salir
            if (uiState.solicitandoPinSalida) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelarSalidaQuiosco() },
                    title = { Text("Salir del modo quiosco") },
                    text = {
                        Column {
                            Text("Ingresa el PIN de seguridad configurado para salir.")
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.pinIngresado,
                                onValueChange = { viewModel.onPinIngresadoChange(it) },
                                label = { Text("PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                            )
                            uiState.errorPin?.let { err ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.verificarPinSalida() },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Salir")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelarSalidaQuiosco() },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
        return
    }

    // Configuración Inicial de Quiosco (Modo normal)
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(
            titulo = "Modo quiosco",
            subtitulo = "Deja una tablet o teléfono fijo en la puerta: cada estudiante muestra su carnet y se registra solo, sin que el docente tenga que escanear."
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Configurar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                DropdownSelector(
                    etiqueta = "Qué registra",
                    opciones = listOf(
                        OpcionDropdown("auto", "Ingreso y salida (automático)"),
                        OpcionDropdown("entrada", "Solo ingreso"),
                        OpcionDropdown("salida", "Solo salida")
                    ),
                    seleccion = uiState.modoOperacion,
                    onSeleccionar = { viewModel.configurarModo(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                DropdownSelector(
                    etiqueta = "Intervalo del QR rotativo",
                    opciones = listOf(
                        OpcionDropdown("30", "30 segundos (recomendado)"),
                        OpcionDropdown("15", "15 segundos (rotación rápida)")
                    ),
                    seleccion = uiState.intervaloQrSegundos.toString(),
                    onSeleccionar = { viewModel.configurarIntervaloQr(it.toIntOrNull() ?: 30) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        pinError = null
                    },
                    label = { Text("PIN para salir (4 a 6 números) *") },
                    placeholder = { Text("Ej: 4821") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                )

                if (pinError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pinError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.sonidoHabilitado,
                        onCheckedChange = { viewModel.toggleSonido(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sonido de confirmación",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.vozHabilitada,
                        onCheckedChange = { viewModel.toggleVoz(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saludar por voz («Bienvenido, Juan»)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!pinInput.matches(Regex("""^\d{4,6}$"""))) {
                            pinError = "El PIN debe tener de 4 a 6 números."
                        } else {
                            viewModel.iniciarQuiosco(pinInput)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iniciar modo quiosco")
                }
            }
        }

        // Card Explicativa "Cómo funciona"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Cómo funciona",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "• Pantalla completa y sin menús. Solo se sale con el PIN de seguridad configurado.\n" +
                        "• Conmuta entre lectura con cámara frontal y QR rotativo de ingreso para escaneo con la app LUMMER.\n" +
                        "• Acepta el QR del carnet estudiantil institucional (incluyendo QR dinámico seguro).\n" +
                        "• En modo automático: el primer pase marca ingreso; la salida solo se habilita tras cumplirse la permanencia mínima del horario (evita salidas prematuras).\n" +
                        "• Muestra foto y nombre (apellidos protegidos), puntual o tardanza, con feedback háptico y sonoro.\n" +
                        "• Sin internet sigue funcionando: encola las asistencias en la base de datos local y las sincroniza en segundo plano al recuperar la conexión.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
