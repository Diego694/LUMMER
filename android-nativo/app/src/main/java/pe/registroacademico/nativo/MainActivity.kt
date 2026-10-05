package pe.registroacademico.nativo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pe.registroacademico.nativo.data.AuthRepository
import pe.registroacademico.nativo.ui.AppNav
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
            RegistroAcademicoTheme {
                AppNav(authRepository = authRepository)
            }
        }
    }
}
