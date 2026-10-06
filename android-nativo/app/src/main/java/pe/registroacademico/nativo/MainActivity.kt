package pe.registroacademico.nativo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.ui.AppNav
import pe.registroacademico.nativo.ui.theme.LocalTemaOscuro
import pe.registroacademico.nativo.ui.theme.LocalToggleTema
import pe.registroacademico.nativo.ui.theme.RegistroAcademicoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val sistemaOscuro = isSystemInDarkTheme()
            var temaManual by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val oscuro = temaManual ?: sistemaOscuro

            CompositionLocalProvider(
                LocalTemaOscuro provides oscuro,
                LocalToggleTema provides { temaManual = !oscuro }
            ) {
                RegistroAcademicoTheme(darkTheme = oscuro) {
                    AppNav(authRepository = authRepository)
                }
            }
        }
    }
}
