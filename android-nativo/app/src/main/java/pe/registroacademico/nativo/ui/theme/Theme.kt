package pe.registroacademico.nativo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = OnAmber,
    primaryContainer = AmberSoftLight,
    onPrimaryContainer = AmberDark,
    secondary = NavySecondaryLight,
    onSecondary = PanelLight,
    secondaryContainer = MutedBgLight,
    onSecondaryContainer = InkLight,
    tertiary = TealSuccessLight,
    onTertiary = PanelLight,
    tertiaryContainer = TealSoftLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = MutedBgLight,
    onSurfaceVariant = InkSoftLight,
    outline = LineStrongLight,
    outlineVariant = LineLight,
    error = RedErrorLight,
    onError = PanelLight,
    errorContainer = RedSoftLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AmberDarkAccent,
    onPrimary = OnAmber,
    primaryContainer = AmberSoftDark,
    onPrimaryContainer = AmberDarkAccent,
    secondary = NavyDark,
    onSecondary = InkDark,
    secondaryContainer = MutedBgDark,
    onSecondaryContainer = InkDark,
    tertiary = TealSuccessDark,
    onTertiary = PaperDark,
    tertiaryContainer = TealSoftDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = MutedBgDark,
    onSurfaceVariant = InkSoftDark,
    outline = LineStrongDark,
    outlineVariant = LineDark,
    error = RedErrorDark,
    onError = PaperDark,
    errorContainer = RedSoftDark
)

@Composable
fun RegistroAcademicoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
