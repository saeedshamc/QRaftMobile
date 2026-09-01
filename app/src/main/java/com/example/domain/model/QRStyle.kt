package com.example.domain.model

enum class DotStyle(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    DOTS("Dots"),
    CLASSY("Classy Diamond")
}

enum class EyeFrameStyle(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    LEAF("Leaf")
}

enum class EyeInnerStyle(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    DOT("Dot"),
    DIAMOND("Diamond")
}

enum class GradientType(val label: String) {
    DIAGONAL("Diagonal 45°"),
    HORIZONTAL("Horizontal"),
    VERTICAL("Vertical"),
    RADIAL("Radial Glow")
}

enum class ErrorCorrection(val level: String, val label: String, val tolerance: String) {
    L("L", "L", "7%"),
    M("M", "M", "15%"),
    Q("Q", "Q", "25%"),
    H("H", "H", "30%")
}

data class ColorPalettePreset(
    val id: String,
    val nameEn: String,
    val nameFa: String,
    val fgColor: Long,
    val gradientEndColor: Long,
    val bgColor: Long
)

object ColorPalettes {
    val presets = listOf(
        ColorPalettePreset("classic", "Classic Dark", "کلاسیک تیره", 0xFF000000, 0xFF1A1A1A, 0xFFFFFFFF),
        ColorPalettePreset("slate", "Midnight Slate", "خاکستری نیمه‌شب", 0xFF1E293B, 0xFF334155, 0xFFF8FAFC),
        ColorPalettePreset("ocean", "Ocean Blue", "آبی اقیانوس", 0xFF1D4ED8, 0xFF3B82F6, 0xFFF0F9FF),
        ColorPalettePreset("emerald", "Forest Emerald", "زمرد جنگلی", 0xFF047857, 0xFF10B981, 0xFFF0FDF4),
        ColorPalettePreset("rose", "Sunset Rose", "گل سرخ غروب", 0xFFBE123C, 0xFFF43F5E, 0xFFFFF1F2),
        ColorPalettePreset("purple", "Purple Dream", "رویای بنفش", 0xFF6D28D9, 0xFF8B5CF6, 0xFFFAF5FF),
        ColorPalettePreset("cyber", "Cyber Neon", "سایبر نئون", 0xFF06B6D4, 0xFF0D9488, 0xFF0F172A),
        ColorPalettePreset("mocha", "Warm Mocha", "موکای گرم", 0xFF78350F, 0xFFB45309, 0xFFFDF8F6)
    )
}

data class QRStyle(
    val errorCorrection: ErrorCorrection = ErrorCorrection.M,
    val sizePx: Int = 300,
    val fgColor: Long = 0xFF000000,
    val gradientMode: Boolean = false,
    val gradientEndColor: Long = 0xFF1A1A1A,
    val gradientType: GradientType = GradientType.DIAGONAL,
    val bgColor: Long = 0xFFFFFFFF,
    val transparentBg: Boolean = false,
    val margin: Int = 2,
    val dotStyle: DotStyle = DotStyle.SQUARE,
    val eyeFrameStyle: EyeFrameStyle = EyeFrameStyle.SQUARE,
    val eyeInnerStyle: EyeInnerStyle = EyeInnerStyle.SQUARE,
    val logoUri: String? = null,
    val logoSizePercent: Int = 20, // 10..35%
    val frameLabel: String = "None", // None, "Scan Me", "Website", "Visit Website", "Add Contact", "Custom"
    val customBannerText: String = "",
    val bannerPositionTop: Boolean = true,
    val watermarkText: String = "",
    val watermarkOpacity: Float = 0.5f,
    val activePaletteId: String? = "classic",
    val exportResolution: Int = 1024 // 512, 1024, 2048, 4096
)
