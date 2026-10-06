package pe.registroacademico.nativo.ui.gestion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AlumnosRepo
import pe.registroacademico.nativo.data.model.Alumno
import pe.registroacademico.nativo.domain.CiclosUtils
import pe.registroacademico.nativo.domain.StringUtils
import pe.registroacademico.nativo.ui.components.EmptyState
import pe.registroacademico.nativo.ui.components.ErrorState
import pe.registroacademico.nativo.ui.components.PageHeader
import pe.registroacademico.nativo.ui.components.SectionCard
import pe.registroacademico.nativo.ui.components.SkeletonList
import pe.registroacademico.nativo.ui.gestion.qr.QrCodeGenerator
import pe.registroacademico.nativo.ui.gestion.qr.QrSeguro
import pe.registroacademico.nativo.ui.shell.PantallaCtx
import javax.inject.Inject

data class CarnetUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val alumnos: List<Alumno> = emptyList(),
    val alumnoSeleccionado: Alumno? = null
)

@HiltViewModel
class CarnetViewModel @Inject constructor(
    private val alumnosRepo: AlumnosRepo
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarnetUiState())
    val uiState: StateFlow<CarnetUiState> = _uiState.asStateFlow()

    fun cargar(colegioId: String, alumnoIdInicial: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            try {
                val lista = alumnosRepo.listar(colegioId)
                val sel = if (!alumnoIdInicial.isNullOrBlank()) {
                    lista.find { it.id == alumnoIdInicial } ?: lista.firstOrNull()
                } else {
                    _uiState.value.alumnoSeleccionado ?: lista.firstOrNull()
                }

                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    alumnos = lista,
                    alumnoSeleccionado = sel
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = e.message ?: "Error al cargar alumnos para carnet"
                )
            }
        }
    }

    fun seleccionarAlumno(alumno: Alumno) {
        _uiState.value = _uiState.value.copy(alumnoSeleccionado = alumno)
    }
}

@Composable
fun CarnetScreen(
    ctx: PantallaCtx,
    viewModel: CarnetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val cid = ctx.sesion.colegioId.orEmpty()
    val qrModo = ctx.sesion.qrModo ?: "off"

    LaunchedEffect(cid) {
        if (cid.isNotBlank()) {
            viewModel.cargar(cid)
        }
    }

    var busqueda by remember { mutableStateOf("") }

    val alumnosFiltrados = remember(busqueda, state.alumnos) {
        val q = StringUtils.norm(busqueda)
        if (q.isBlank()) state.alumnos
        else state.alumnos.filter {
            StringUtils.norm("${it.nombre} ${it.codigo}").contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            titulo = "Carnet",
            subtitulo = "Genera y visualiza el carnet con código QR de cada alumno."
        )

        if (qrModo == "obligatorio") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Con QR obligatorio los carnets impresos con QR fijo no se aceptan por cámara; usa NFC/código o pide al estudiante abrir su carnet dinámico en la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when {
            state.cargando -> {
                SkeletonList(cantidad = 4)
            }

            state.error != null -> {
                ErrorState(
                    mensaje = state.error ?: "Error inesperado",
                    onReintentar = { viewModel.cargar(cid) }
                )
            }

            state.alumnos.isEmpty() -> {
                EmptyState(
                    titulo = "Sin alumnos",
                    texto = "Primero registra alumnos en la sección Gestión → Alumnos.",
                    icono = Icons.Default.ContactPage
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Vista previa del Carnet
                    val al = state.alumnoSeleccionado
                    if (al != null) {
                        SectionCard(titulo = "Vista previa") {
                            TarjetaCarnet(
                                alumno = al,
                                nombreInstituto = ctx.sesion.nombreInstituto.ifBlank { "Instituto de Educación Superior" },
                                qrModo = qrModo
                            )
                        }
                    }

                    // Selector de Alumnos
                    SectionCard(titulo = "Seleccionar alumno") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = busqueda,
                                onValueChange = { busqueda = it },
                                label = { Text("Buscar por nombre o código…") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (alumnosFiltrados.isEmpty()) {
                                Text(
                                    text = "No se encontraron alumnos con ese criterio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    alumnosFiltrados.take(40).forEach { a ->
                                        val seleccionado = a.id == state.alumnoSeleccionado?.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.seleccionarAlumno(a) },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (seleccionado) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (seleccionado) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.secondary
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = StringUtils.initials(a.nombre),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = a.nombre,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${a.codigo} · ${CiclosUtils.etiquetaCiclo(a.nivel, a.grado)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TarjetaCarnet(
    alumno: Alumno,
    nombreInstituto: String,
    qrModo: String,
    modifier: Modifier = Modifier
) {
    var ahoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var segundosRestantes by remember { mutableIntStateOf(30) }

    val esDinamico = qrModo in listOf("opcional", "obligatorio") && !alumno.qrSecreto.isNullOrBlank()

    LaunchedEffect(esDinamico, alumno.id) {
        if (esDinamico) {
            while (true) {
                val ms = System.currentTimeMillis()
                ahoraMs = ms
                segundosRestantes = QrSeguro.segundosRestantes(ms)
                delay(1000)
            }
        }
    }

    val contenidoQr = remember(alumno.id, ahoraMs, qrModo) {
        if (esDinamico) {
            QrSeguro.generarCodigo(
                codigo = alumno.codigo,
                secreto = alumno.qrSecreto,
                ms = ahoraMs,
                qrModo = qrModo
            )
        } else {
            alumno.codigo
        }
    }

    val qrMatrix = remember(contenidoQr) {
        try {
            QrCodeGenerator.encode(contenidoQr)
        } catch (e: Exception) {
            null
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF16223D), Color(0xFF22335A))
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header institucional
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nombreInstituto.uppercase(),
                        color = Color(0xFFE8A33D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "CARNET",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Nombre y código
                Column {
                    Text(
                        text = alumno.nombre,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CiclosUtils.etiquetaCiclo(alumno.nivel, alumno.grado),
                        color = Color(0xFFCFD7EA),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Fila Inferior: Código y QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "CÓDIGO DE ACCESO",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = alumno.codigo,
                            color = Color(0xFFE8A33D),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (esDinamico) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "QR dinámico · ${segundosRestantes}s",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Contenedor QR blanco
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrMatrix != null) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val moduleSize = size.minDimension / qrMatrix.size
                                for (r in 0 until qrMatrix.size) {
                                    for (c in 0 until qrMatrix.size) {
                                        if (qrMatrix.isDark(r, c)) {
                                            drawRect(
                                                color = Color.Black,
                                                topLeft = Offset(c * moduleSize, r * moduleSize),
                                                size = Size(moduleSize, moduleSize)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
