package com.life.app.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.life.app.data.ThemeOptions

val IncomeGreen = Color(0xFFA3BE8C)
val ExpenseRed = Color(0xFFBF616A)

private val LifeTypography = Typography()

@Composable
fun LifeTheme(
    backgroundId: String = "system",
    accentId: String = "default",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val bg = ThemeOptions.background(backgroundId)
    val accent = Color(ThemeOptions.accent(accentId).colorArgb)
    val onAccent = Color(0xFF1A1B26)
    var scheme = when {
        backgroundId == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && night ->
            dynamicDarkColorScheme(context)
        backgroundId == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        backgroundId == "system" && night -> darkColorScheme()
        backgroundId == "system" -> lightColorScheme()
        bg.isLight -> lightColorScheme(
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
        else -> darkColorScheme(
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
    if (backgroundId != "system" && accentId == "default" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dyn = if (bg.isLight) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
        scheme = scheme.copy(
            primary = dyn.primary,
            onPrimary = dyn.onPrimary,
            secondary = dyn.secondary,
            onSecondary = dyn.onSecondary
        )
    }
    if (accentId != "default") {
        scheme = scheme.copy(
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent
        )
    }
    MaterialTheme(colorScheme = scheme, typography = LifeTypography, content = content)
}
