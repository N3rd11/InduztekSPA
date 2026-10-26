package cl.induztek.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta fija azul y negro, independiente de los colores del dispositivo. */
private val AzulNegro = darkColorScheme(
    primary = Color(0xFF78B2FF), onPrimary = Color(0xFF001B3C),
    primaryContainer = Color(0xFF102D55), onPrimaryContainer = Color(0xFFD8E9FF),
    secondary = Color(0xFF97C3FF), onSecondary = Color(0xFF061A34),
    secondaryContainer = Color(0xFF10243F), onSecondaryContainer = Color(0xFFD8E9FF),
    tertiary = Color(0xFFBDD6FF), onTertiary = Color(0xFF071C38),
    tertiaryContainer = Color(0xFF19345B), onTertiaryContainer = Color(0xFFE0EBFF),
    background = Color.Black, onBackground = Color(0xFFF0F4FA),
    surface = Color(0xFF0A0E15), onSurface = Color(0xFFF0F4FA),
    surfaceVariant = Color(0xFF172234), onSurfaceVariant = Color(0xFFBDCADC),
    surfaceDim = Color(0xFF06080D), surfaceBright = Color(0xFF273346),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0D131E),
    surfaceContainer = Color(0xFF121B29), surfaceContainerHigh = Color(0xFF1A2537),
    surfaceContainerHighest = Color(0xFF243247), surfaceTint = Color(0xFF78B2FF),
    outline = Color(0xFF7E90AA), outlineVariant = Color(0xFF36465F),
    inverseSurface = Color(0xFFE2EAF5), inverseOnSurface = Color(0xFF101A2B),
    inversePrimary = Color(0xFF185BAD), scrim = Color.Black
)

@Composable
fun InduztekSPATheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AzulNegro, typography = Typography, content = content)
}
