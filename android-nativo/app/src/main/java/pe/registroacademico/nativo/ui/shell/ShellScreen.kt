package pe.registroacademico.nativo.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.domain.Permisos
import pe.registroacademico.nativo.ui.theme.LocalTemaOscuro
import pe.registroacademico.nativo.ui.theme.LocalToggleTema

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    onSignOut: () -> Unit,
    viewModel: ShellViewModel = hiltViewModel()
) {
    val sesionEstado by viewModel.sesionEstado.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isOnline by rememberIsOnline()

    val isDarkTheme = LocalTemaOscuro.current
    val toggleTema = LocalToggleTema.current

    val shellNavController = rememberNavController()
    val navBackStackEntry by shellNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    val todasPantallas = Pantallas.todas
    val pantallaActual = todasPantallas.find { it.id == currentRoute } ?: todasPantallas.first()

    // Grupos colapsables: colapsados por defecto salvo el del ítem activo
    var expandedGroups by rememberSaveable {
        mutableStateOf(setOf(pantallaActual.grupo))
    }

    LaunchedEffect(pantallaActual.grupo) {
        if (pantallaActual.grupo !in expandedGroups) {
            expandedGroups = expandedGroups + pantallaActual.grupo
        }
    }

    // Filtrar ítems visibles por permisos
    val esAdmin = Permisos.esAdmin(sesionEstado.rol)
    val esSuper = sesionEstado.esSuperadmin
    val pantallasVisibles = todasPantallas.filter { p ->
        (!p.soloAdmin || esAdmin) && (!p.soloSuper || esSuper)
    }

    val grupos = listOf("Principal", "Registro", "Consultas", "Gestión", "Sistema")
        .filter { g -> pantallasVisibles.any { it.grupo == g } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                windowInsets = WindowInsets.systemBars
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    // Cabecera: Marca = Nombre del instituto (sin logo)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = sesionEstado.nombreInstituto.ifBlank { "Registro Académico" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Indicador de rol y controles de tema / sesión
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = Permisos.ETIQUETA_ROL[sesionEstado.rol] ?: sesionEstado.rol.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (sesionEstado.esSuperadmin) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "Superadmin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { toggleTema() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (isDarkTheme) "Cambiar a tema claro" else "Cambiar a tema oscuro",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.cerrarSesion(onSignOut)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Cerrar sesión",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Menú por grupos plegables
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        grupos.forEach { grupo ->
                            val itemsGrupo = pantallasVisibles.filter { it.grupo == grupo }
                            val estaAbierto = grupo in expandedGroups

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        expandedGroups = if (estaAbierto) {
                                            expandedGroups - grupo
                                        } else {
                                            expandedGroups + grupo
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = grupo,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (estaAbierto) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (estaAbierto) "Colapsar $grupo" else "Expandir $grupo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = estaAbierto,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                ) {
                                    itemsGrupo.forEach { p ->
                                        val seleccionado = p.id == currentRoute
                                        NavigationDrawerItem(
                                            icon = {
                                                Icon(
                                                    imageVector = p.icono,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = p.titulo,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            },
                                            selected = seleccionado,
                                            onClick = {
                                                scope.launch { drawerState.close() }
                                                shellNavController.navigate(p.id) {
                                                    popUpTo("dashboard") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            modifier = Modifier
                                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                                .height(44.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "${pantallaActual.grupo} / ${pantallaActual.titulo}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Abrir menú de navegación"
                            )
                        }
                    },
                    actions = {
                        // Chip de estado de conexión: En línea / Sin conexión
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828))
                                )
                                Text(
                                    text = if (isOnline) "En línea" else "Sin conexión",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isOnline) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.systemBars
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavHost(
                    navController = shellNavController,
                    startDestination = "dashboard",
                    modifier = Modifier.fillMaxSize()
                ) {
                    todasPantallas.forEach { pantalla ->
                        composable(pantalla.id) {
                            val ctx = PantallaCtx(
                                snackbarHostState = snackbarHostState,
                                navegar = { destino ->
                                    shellNavController.navigate(destino) {
                                        popUpTo("dashboard") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                sesion = sesionEstado,
                                scope = scope
                            )
                            pantalla.contenido(ctx)
                        }
                    }
                }
            }
        }
    }
}
