package com.example.domain.engine

import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.QRStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QRSvgExporter {

    fun generateSvgString(content: String, style: QRStyle): String {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = style.margin

        val ecLevel = when (style.errorCorrection) {
            ErrorCorrection.L -> ErrorCorrectionLevel.L
            ErrorCorrection.M -> ErrorCorrectionLevel.M
            ErrorCorrection.Q -> ErrorCorrectionLevel.Q
            ErrorCorrection.H -> ErrorCorrectionLevel.H
        }
        hints[EncodeHintType.ERROR_CORRECTION] = ecLevel

        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val size = style.sizePx
        val hasFrame = style.frameLabel != "None" && style.frameLabel.isNotBlank()
        val frameHeight = if (hasFrame) (size * 0.18f).toInt() else 0
        val totalHeight = size + frameHeight

        val fgHex = String.format("#%06X", (0xFFFFFF and style.fgColor.toInt()))
        val fgGradEndHex = String.format("#%06X", (0xFFFFFF and style.gradientEndColor.toInt()))
        val bgHex = String.format("#%06X", (0xFFFFFF and style.bgColor.toInt()))

        val isTop = style.frameLabel.equals("Scan Me", ignoreCase = true)
        val qrTop = if (hasFrame && isTop) frameHeight else 0

        val moduleW = size.toFloat() / matrixWidth
        val moduleH = size.toFloat() / matrixHeight

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $totalHeight" width="$size" height="$totalHeight">""").append("\n")

        // Defs for gradient
        sb.append("<defs>\n")
        if (style.gradientMode) {
            sb.append("""  <linearGradient id="qrGrad" x1="0%" y1="0%" x2="100%" y2="100%">""").append("\n")
            sb.append("""    <stop offset="0%" stop-color="$fgHex" />""").append("\n")
            sb.append("""    <stop offset="100%" stop-color="$fgGradEndHex" />""").append("\n")
            sb.append("  </linearGradient>\n")
        }
        sb.append("</defs>\n")

        // Background
        if (!style.transparentBg) {
            sb.append("""<rect width="$size" height="$totalHeight" fill="$bgHex" />""").append("\n")
        }

        val fillAttr = if (style.gradientMode) """fill="url(#qrGrad)"""" else """fill="$fgHex""""

        // Draw modules
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                if (bitMatrix.get(x, y)) {
                    val px = x * moduleW
                    val py = qrTop + (y * moduleH)
                    when (style.dotStyle) {
                        DotStyle.SQUARE -> {
                            sb.append("""<rect x="$px" y="$py" width="$moduleW" height="$moduleH" $fillAttr />""").append("\n")
                        }
                        DotStyle.ROUNDED -> {
                            val r = moduleW * 0.35f
                            sb.append("""<rect x="$px" y="$py" width="$moduleW" height="$moduleH" rx="$r" ry="$r" $fillAttr />""").append("\n")
                        }
                        DotStyle.DOTS -> {
                            val cx = px + moduleW / 2f
                            val cy = py + moduleH / 2f
                            val r = (moduleW.coerceAtMost(moduleH) / 2f) * 0.9f
                            sb.append("""<circle cx="$cx" cy="$cy" r="$r" $fillAttr />""").append("\n")
                        }
                    }
                }
            }
        }

        // Frame
        if (hasFrame) {
            val bannerY = if (isTop) 6f else (totalHeight - frameHeight + 6f)
            val pillH = frameHeight - 12f
            val pillR = pillH / 2f
            val textY = bannerY + pillH / 2f + 5f
            sb.append("""<rect x="12" y="$bannerY" width="${size - 24}" height="$pillH" rx="$pillR" ry="$pillR" fill="$fgHex" />""").append("\n")
            sb.append("""<text x="${size / 2}" y="$textY" font-family="sans-serif" font-weight="bold" font-size="${frameHeight * 0.42f}" fill="#ffffff" text-anchor="middle">$style.frameLabel</text>""").append("\n")
        }

        // Watermark
        if (style.watermarkText.isNotBlank()) {
            val wmY = totalHeight - 12f
            val wmX = size - 16f
            sb.append("""<text x="$wmX" y="$wmY" font-family="sans-serif" font-style="italic" font-size="${size * 0.032f}" fill="$fgHex" opacity="${style.watermarkOpacity}" text-anchor="end">${style.watermarkText}</text>""").append("\n")
        }

        sb.append("</svg>")
        return sb.toString()
    }
}
