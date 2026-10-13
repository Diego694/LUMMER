package pe.registroacademico.nativo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.push.PushRegistrar
import pe.registroacademico.nativo.push.crearCanalAvisos
import pe.registroacademico.nativo.ui.AppNav
import pe.registroacademico.nativo.ui.estudiante.AppNavEstudiante
import pe.registroacademico.nativo.ui.theme.LocalTemaOscuro
import pe.registroacademico.nativo.ui.theme.LocalToggleTema
import pe.registroacademico.nativo.ui.theme.RegistroAcademicoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var pushRegistrar: PushRegistrar

    // Pedir POST_NOTIFICATIONS una vez en Android 13+; no insistir si se rechaza
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No insistir si el usuario la niega */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // (a) Crear canal de notificaciones para avisos
        crearCanalAvisos(this)

        // (b) Solicitar permiso POST_NOTIFICATIONS una vez en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // (c) Sincronizar token FCM al iniciar sesión (una vez por sesión autenticada; sin bloquear UI)
        lifecycleScope.launch {
            var yaSincronizado = false
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepository.sessionStatus.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        if (!yaSincronizado) {
                            yaSincronizado = true
                            launch {
                                pushRegistrar.sincronizar()
                            }
                        }
                    } else {
                        yaSincronizado = false
                    }
                }
            }
        }

        setContent {
            val sistemaOscuro = isSystemInDarkTheme()
            var temaManual by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val oscuro = temaManual ?: sistemaOscuro

            CompositionLocalProvider(
                LocalTemaOscuro provides oscuro,
                LocalToggleTema provides { temaManual = !oscuro }
            ) {
                RegistroAcademicoTheme(darkTheme = oscuro) {
                    if (BuildConfig.PERFIL == "estudiante") {
                        AppNavEstudiante(authRepository = authRepository)
                    } else {
                        AppNav(authRepository = authRepository)
                    }
                }
            }
        }
    }
}
