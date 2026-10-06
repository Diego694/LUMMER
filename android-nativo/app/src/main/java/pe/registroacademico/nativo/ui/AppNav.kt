package pe.registroacademico.nativo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.auth.status.SessionStatus
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.ui.login.LoginScreen
import pe.registroacademico.nativo.ui.shell.ShellScreen

@Composable
fun AppNav(authRepository: AuthRepository) {
    val sessionStatus by authRepository.sessionStatus.collectAsState()

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
                    ShellScreen(
                        onSignOut = {
                            navController.navigate("login") {
                                popUpTo("shell") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
