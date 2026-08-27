package com.example.domain.model

import org.json.JSONObject

data class QRDesignProfile(
    val id: String = System.currentTimeMillis().toString(),
    val nameEn: String,
    val nameFa: String,
    val descriptionEn: String = "",
    val descriptionFa: String = "",
    val errorCorrection: ErrorCorrection = ErrorCorrection.M,
    val dotStyle: DotStyle = DotStyle.ROUNDED,
    val eyeFrameStyle: EyeFrameStyle = EyeFrameStyle.ROUNDED,
    val eyeInnerStyle: EyeInnerStyle = EyeInnerStyle.ROUNDED,
    val fgColor: Long = 0xFF1D4ED8,
    val gradientMode: Boolean = true,
    val gradientEndColor: Long = 0xFF3B82F6,
    val gradientType: GradientType = GradientType.DIAGONAL,
    val bgColor: Long = 0xFFF0F9FF,
    val transparentBg: Boolean = false,
    val margin: Int = 2,
    val frameLabel: String = "None",
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun applyTo(currentStyle: QRStyle): QRStyle {
        return currentStyle.copy(
            errorCorrection = errorCorrection,
            dotStyle = dotStyle,
            eyeFrameStyle = eyeFrameStyle,
            eyeInnerStyle = eyeInnerStyle,
            fgColor = fgColor,
            gradientMode = gradientMode,
            gradientEndColor = gradientEndColor,
            gradientType = gradientType,
            bgColor = bgColor,
            transparentBg = transparentBg,
            margin = margin,
            frameLabel = frameLabel,
            activePaletteId = id
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("nameEn", nameEn)
            put("nameFa", nameFa)
            put("descriptionEn", descriptionEn)
            put("descriptionFa", descriptionFa)
            put("errorCorrection", errorCorrection.name)
            put("dotStyle", dotStyle.name)
            put("eyeFrameStyle", eyeFrameStyle.name)
            put("eyeInnerStyle", eyeInnerStyle.name)
            put("fgColor", fgColor)
            put("gradientMode", gradientMode)
            put("gradientEndColor", gradientEndColor)
            put("gradientType", gradientType.name)
            put("bgColor", bgColor)
            put("transparentBg", transparentBg)
            put("margin", margin)
            put("frameLabel", frameLabel)
            put("isCustom", isCustom)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromStyle(style: QRStyle, name: String): QRDesignProfile {
            val now = System.currentTimeMillis()
            return QRDesignProfile(
                id = "custom_$now",
                nameEn = name,
                nameFa = name,
                descriptionEn = "Custom User Design Profile",
                descriptionFa = "پروفایل طراحی سفارشی کاربر",
                errorCorrection = style.errorCorrection,
                dotStyle = style.dotStyle,
                eyeFrameStyle = style.eyeFrameStyle,
                eyeInnerStyle = style.eyeInnerStyle,
                fgColor = style.fgColor,
                gradientMode = style.gradientMode,
                gradientEndColor = style.gradientEndColor,
                gradientType = style.gradientType,
                bgColor = style.bgColor,
                transparentBg = style.transparentBg,
                margin = style.margin,
                frameLabel = style.frameLabel,
                isCustom = true,
                createdAt = now
            )
        }

        fun fromJson(obj: JSONObject): QRDesignProfile {
            return QRDesignProfile(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                nameEn = obj.optString("nameEn", "Custom Profile"),
                nameFa = obj.optString("nameFa", "پروفایل سفارشی"),
                descriptionEn = obj.optString("descriptionEn", ""),
                descriptionFa = obj.optString("descriptionFa", ""),
                errorCorrection = try {
                    ErrorCorrection.valueOf(obj.optString("errorCorrection", "M"))
                } catch (e: Exception) { ErrorCorrection.M },
                dotStyle = try {
                    DotStyle.valueOf(obj.optString("dotStyle", "ROUNDED"))
                } catch (e: Exception) { DotStyle.ROUNDED },
                eyeFrameStyle = try {
                    EyeFrameStyle.valueOf(obj.optString("eyeFrameStyle", "ROUNDED"))
                } catch (e: Exception) { EyeFrameStyle.ROUNDED },
                eyeInnerStyle = try {
                    EyeInnerStyle.valueOf(obj.optString("eyeInnerStyle", "ROUNDED"))
                } catch (e: Exception) { EyeInnerStyle.ROUNDED },
                fgColor = obj.optLong("fgColor", 0xFF1D4ED8),
                gradientMode = obj.optBoolean("gradientMode", true),
                gradientEndColor = obj.optLong("gradientEndColor", 0xFF3B82F6),
                gradientType = try {
                    GradientType.valueOf(obj.optString("gradientType", "DIAGONAL"))
                } catch (e: Exception) { GradientType.DIAGONAL },
                bgColor = obj.optLong("bgColor", 0xFFF0F9FF),
                transparentBg = obj.optBoolean("transparentBg", false),
                margin = obj.optInt("margin", 2),
                frameLabel = obj.optString("frameLabel", "None"),
                isCustom = obj.optBoolean("isCustom", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }

        val defaultProfiles = listOf(
            QRDesignProfile(
                id = "prof_corporate_blue",
                nameEn = "Corporate Indigo",
                nameFa = "نیلی سازمانی",
                descriptionEn = "Clean high-contrast corporate identity with rounded eyes",
                descriptionFa = "هویت سازمانی با کنتراست بالا و گوشه‌های گرد",
                errorCorrection = ErrorCorrection.H,
                dotStyle = DotStyle.ROUNDED,
                eyeFrameStyle = EyeFrameStyle.ROUNDED,
                eyeInnerStyle = EyeInnerStyle.ROUNDED,
                fgColor = 0xFF1E3A8A,
                gradientMode = true,
                gradientEndColor = 0xFF3B82F6,
                bgColor = 0xFFF8FAFC,
                margin = 2,
                isCustom = false
            ),
            QRDesignProfile(
                id = "prof_cyber_neon",
                nameEn = "Cyberpunk Glow",
                nameFa = "سایبرپانک نئونی",
                descriptionEn = "High-tech cyan-teal dots on dark background",
                descriptionFa = "طراحی مدرن فیروزه‌ای روی پس‌زمینه تیره",
                errorCorrection = ErrorCorrection.Q,
                dotStyle = DotStyle.DOTS,
                eyeFrameStyle = EyeFrameStyle.CIRCLE,
                eyeInnerStyle = EyeInnerStyle.DOT,
                fgColor = 0xFF06B6D4,
                gradientMode = true,
                gradientEndColor = 0xFF10B981,
                bgColor = 0xFF0F172A,
                margin = 2,
                isCustom = false
            ),
            QRDesignProfile(
                id = "prof_royal_gold",
                nameEn = "Royal Luxury Gold",
                nameFa = "طلای سلطنتی",
                descriptionEn = "Refined warm gold palette with diamond accents",
                descriptionFa = "پالت طلایی گرم و مجلل با زاویه‌های الماسی",
                errorCorrection = ErrorCorrection.H,
                dotStyle = DotStyle.CLASSY,
                eyeFrameStyle = EyeFrameStyle.LEAF,
                eyeInnerStyle = EyeInnerStyle.DIAMOND,
                fgColor = 0xFFB45309,
                gradientMode = true,
                gradientEndColor = 0xFFF59E0B,
                bgColor = 0xFFFFFBEB,
                margin = 2,
                isCustom = false
            ),
            QRDesignProfile(
                id = "prof_emerald_nature",
                nameEn = "Emerald Eco",
                nameFa = "سبز زمردین طبیعت",
                descriptionEn = "Organic leafy green gradients for sustainable brands",
                descriptionFa = "گرادینت سبز برگ طبیعت مناسب برندهای پایدار",
                errorCorrection = ErrorCorrection.M,
                dotStyle = DotStyle.ROUNDED,
                eyeFrameStyle = EyeFrameStyle.LEAF,
                eyeInnerStyle = EyeInnerStyle.ROUNDED,
                fgColor = 0xFF065F46,
                gradientMode = true,
                gradientEndColor = 0xFF10B981,
                bgColor = 0xFFF0FDF4,
                margin = 2,
                isCustom = false
            ),
            QRDesignProfile(
                id = "prof_minimal_dark",
                nameEn = "Minimal Slate",
                nameFa = "خاکستری مینیمال",
                descriptionEn = "Sharp ultra-clean monochrome for modern prints",
                descriptionFa = "سیاه و سفید فوق‌العاده تمیز برای چاپ مدرن",
                errorCorrection = ErrorCorrection.L,
                dotStyle = DotStyle.SQUARE,
                eyeFrameStyle = EyeFrameStyle.SQUARE,
                eyeInnerStyle = EyeInnerStyle.SQUARE,
                fgColor = 0xFF0F172A,
                gradientMode = false,
                gradientEndColor = 0xFF0F172A,
                bgColor = 0xFFFFFFFF,
                margin = 2,
                isCustom = false
            )
        )
    }
}
