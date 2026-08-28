import SwiftUI

struct AccentOption: Equatable, Identifiable {
    var id: String
    var label: String
    var colorArgb: UInt32
}

struct BackgroundOption: Equatable, Identifiable {
    var id: String
    var label: String
    var backgroundArgb: UInt32
    var surfaceArgb: UInt32
    var surfaceVariantArgb: UInt32
    var isLight: Bool
}

enum ThemeOptions {
    static let accents: [AccentOption] = [
        AccentOption(id: "default", label: "Green", colorArgb: 0xFFA6E3A1),
        AccentOption(id: "nord", label: "Nord frost", colorArgb: 0xFF88C0D0),
        AccentOption(id: "tokyonight", label: "Tokyo blue", colorArgb: 0xFF7AA2F7),
        AccentOption(id: "gruvbox", label: "Gruvbox", colorArgb: 0xFFFE8019),
        AccentOption(id: "mauve", label: "Mauve", colorArgb: 0xFFCBA6F7),
        AccentOption(id: "rosepine", label: "Rosé", colorArgb: 0xFFEBBCBA),
        AccentOption(id: "teal", label: "Teal", colorArgb: 0xFF94E2D5)
    ]

    static let backgrounds: [BackgroundOption] = [
        BackgroundOption(id: "dark", label: "Nord", backgroundArgb: 0xFF2E3440, surfaceArgb: 0xFF3B4252, surfaceVariantArgb: 0xFF434C5E, isLight: false),
        BackgroundOption(id: "amoled", label: "OLED", backgroundArgb: 0xFF000000, surfaceArgb: 0xFF0A0A0A, surfaceVariantArgb: 0xFF1A1A1A, isLight: false),
        BackgroundOption(id: "light", label: "Light", backgroundArgb: 0xFFEFF1F5, surfaceArgb: 0xFFFFFFFF, surfaceVariantArgb: 0xFFCCD0DA, isLight: true)
    ]

    static func accent(_ id: String) -> AccentOption {
        accents.first { $0.id == id } ?? accents[0]
    }

    static func background(_ id: String) -> BackgroundOption {
        backgrounds.first { $0.id == id } ?? backgrounds[0]
    }
}

extension Color {
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255.0
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

struct LifePalette {
    var background: Color
    var surface: Color
    var surfaceVariant: Color
    var onBackground: Color
    var onSurface: Color
    var onSurfaceVariant: Color
    var primary: Color
    var onPrimary: Color
    var error: Color
    var isLight: Bool

    static let incomeGreen = Color(argb: 0xFFA3BE8C)
    static let expenseRed = Color(argb: 0xFFBF616A)

    static func from(backgroundId: String, accentId: String) -> LifePalette {
        let bg = ThemeOptions.background(backgroundId)
        let accent = Color(argb: ThemeOptions.accent(accentId).colorArgb)
        let onAccent = Color(argb: 0xFF1A1B26)
        if bg.isLight {
            return LifePalette(
                background: Color(argb: bg.backgroundArgb),
                surface: Color(argb: bg.surfaceArgb),
                surfaceVariant: Color(argb: bg.surfaceVariantArgb),
                onBackground: Color(argb: 0xFF2E3440),
                onSurface: Color(argb: 0xFF2E3440),
                onSurfaceVariant: Color(argb: 0xFF4C566A),
                primary: accent,
                onPrimary: onAccent,
                error: Color(argb: 0xFFBF616A),
                isLight: true
            )
        }
        return LifePalette(
            background: Color(argb: bg.backgroundArgb),
            surface: Color(argb: bg.surfaceArgb),
            surfaceVariant: Color(argb: bg.surfaceVariantArgb),
            onBackground: Color(argb: 0xFFECEFF4),
            onSurface: Color(argb: 0xFFECEFF4),
            onSurfaceVariant: Color(argb: 0xFFD8DEE9),
            primary: accent,
            onPrimary: onAccent,
            error: Color(argb: 0xFFBF616A),
            isLight: false
        )
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = LifePalette.from(backgroundId: "dark", accentId: "default")
}

extension EnvironmentValues {
    var lifePalette: LifePalette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}
