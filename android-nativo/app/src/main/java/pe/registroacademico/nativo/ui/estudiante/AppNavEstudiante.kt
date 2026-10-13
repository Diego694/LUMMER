package pe.registroacademico.nativo.ui.estudiante

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.ui.login.LoginScreen
import pe.registroacademico.nativo.ui.theme.LocalTemaOscuro
import pe.registroacademico.nativo.ui.theme.LocalToggleTema

internal sealed class DestinoEstudiante(
    val ruta: String,
    val titulo: String,
    val icono: ImageVector
) {
    data object Inicio : DestinoEstudiante("inicio", "Carnet", Icons.Default.Badge)
    data object Escanear : DestinoEstudiante("escanear", "Escanear", Icons.Default.QrCodeScanner)
    data object Cursos : DestinoEstudiante("cursos", "Cursos", Icons.Default.MenuBook)
    data object Asistencia : DestinoEstudiante("asistencia", "Asistencia", Icons.Default.FactCheck)
    data object Avisos : DestinoEstudiante("avisos", "Avisos", Icons.Default.Campaign)

    companion object {
        val items: List<DestinoEstudiante> get() = listOf(Inicio, Escanear, Cursos, Asistencia, Avisos)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellEstudiante(
    onSignOut: () -> Unit
) {
    val shellNavController = rememberNavController()
    val navBackStackEntry by shellNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: DestinoEstudiante.Inicio.ruta

    val destinoActual = DestinoEstudiante.items.find { it.ruta == currentRoute } ?: DestinoEstudiante.Inicio
    val isDarkTheme = LocalTemaOscuro.current
    val toggleTema = LocalToggleTema.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = destinoActual.titulo,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = { toggleTema() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "Cambiar a tema claro" else "Cambiar a tema oscuro"
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Cerrar sesión",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                DestinoEstudiante.items.forEach { destino ->
                    val seleccionado = currentRoute == destino.ruta
                    NavigationBarItem(
                        selected = seleccionado,
                        onClick = {
                            if (currentRoute != destino.ruta) {
                                shellNavController.navigate(destino.ruta) {
                                    popUpTo(shellNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(imageVector = destino.icono, contentDescription = destino.titulo)
                        },
                        label = {
                            Text(text = destino.titulo)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = shellNavController,
            startDestination = DestinoEstudiante.Inicio.ruta,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(DestinoEstudiante.Inicio.ruta) {
                InicioEstScreen()
            }
            composable(DestinoEstudiante.Escanear.ruta) {
                EscanearEstScreen()
            }
            composable(DestinoEstudiante.Cursos.ruta) {
                CursosEstScreen()
            }
            composable(DestinoEstudiante.Asistencia.ruta) {
                AsistenciaEstScreen()
            }
            composable(DestinoEstudiante.Avisos.ruta) {
                AvisosEstScreen()
            }
        }
    }
}

@Composable
fun AppNavEstudiante(authRepository: AuthRepository) {
    val sessionStatus by authRepository.sessionStatus.collectAsState()
    val scope = rememberCoroutineScope()

    when (sessionStatus) {
        is SessionStatus.Initializing -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        else -> {
            val isAuthenticated = sessionStatus is SessionStatus.Authenticated || authRepository.currentSession != null
            val startDestination = if (isAuthenticated) "shell" else "login"

            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable("login") {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate("shell") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    )
                }
                composable("shell") {
                    ShellEstudiante(
                        onSignOut = {
                            scope.launch {
                                authRepository.signOut()
                                navController.navigate("login") {
                                    popUpTo("shell") { inclusive = true }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
