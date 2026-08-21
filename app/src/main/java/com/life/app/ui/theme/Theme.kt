package com.life.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.life.app.data.ThemeOptions

val IncomeGreen = Color(0xFFA3BE8C)
val ExpenseRed = Color(0xFFBF616A)

@Composable
fun LifeTheme(
    backgroundId: String = "dark",
    accentId: String = "default",
    content: @Composable () -> Unit
) {
    val bg = ThemeOptions.background(backgroundId)
    val accent = Color(ThemeOptions.accent(accentId).colorArgb)
    val onAccent = Color(0xFF1A1B26)
    val scheme = if (bg.isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = Color(bg.backgroundArgb),
            surface = Color(bg.surfaceArgb),
            surfaceVariant = Color(bg.surfaceVariantArgb),
            onBackground = Color(0xFF2E3440),
            onSurface = Color(0xFF2E3440),
            onSurfaceVariant = Color(0xFF4C566A),
            secondary = accent,
            onSecondary = onAccent
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = Color(bg.backgroundArgb),
            surface = Color(bg.surfaceArgb),
            surfaceVariant = Color(bg.surfaceVariantArgb),
            onBackground = Color(0xFFECEFF4),
            onSurface = Color(0xFFECEFF4),
            onSurfaceVariant = Color(0xFFD8DEE9),
            secondary = accent,
            onSecondary = onAccent
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
