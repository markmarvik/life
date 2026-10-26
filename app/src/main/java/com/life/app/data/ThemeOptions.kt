package com.life.app.data

data class AccentOption(
    val id: String,
    val label: String,
    val colorArgb: Int
)

data class BackgroundOption(
    val id: String,
    val label: String,
    val backgroundArgb: Int,
    val surfaceArgb: Int,
    val surfaceVariantArgb: Int,
    val isLight: Boolean
)

object ThemeOptions {
    val accents: List<AccentOption> = listOf(
        AccentOption("default", "Green", 0xFFA6E3A1.toInt()),
        AccentOption("nord", "Nord frost", 0xFF88C0D0.toInt()),
        AccentOption("tokyonight", "Tokyo blue", 0xFF7AA2F7.toInt()),
        AccentOption("gruvbox", "Gruvbox", 0xFFFE8019.toInt()),
        AccentOption("mauve", "Mauve", 0xFFCBA6F7.toInt()),
        AccentOption("rosepine", "Rosé", 0xFFEBBCBA.toInt()),
        AccentOption("teal", "Teal", 0xFF94E2D5.toInt())
    )

    val backgrounds: List<BackgroundOption> = listOf(
        BackgroundOption("system", "System", 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), false),
        BackgroundOption("dark", "Nord", 0xFF2E3440.toInt(), 0xFF3B4252.toInt(), 0xFF434C5E.toInt(), false),
        BackgroundOption("amoled", "OLED", 0xFF000000.toInt(), 0xFF0A0A0A.toInt(), 0xFF1A1A1A.toInt(), false),
        BackgroundOption("light", "Light", 0xFFEFF1F5.toInt(), 0xFFFFFFFF.toInt(), 0xFFCCD0DA.toInt(), true)
    )

    fun accent(id: String): AccentOption =
        accents.firstOrNull { it.id == id } ?: accents.first()

    fun background(id: String): BackgroundOption =
        backgrounds.firstOrNull { it.id == id } ?: backgrounds.first { it.id == "dark" }

    fun usesLightBars(backgroundId: String, systemInDarkTheme: Boolean): Boolean {
        if (backgroundId == "system") return !systemInDarkTheme
        return background(backgroundId).isLight
    }
}
