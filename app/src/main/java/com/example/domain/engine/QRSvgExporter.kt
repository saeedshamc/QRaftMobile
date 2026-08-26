package com.example.domain.engine

import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.EyeFrameStyle
import com.example.domain.model.EyeInnerStyle
import com.example.domain.model.GradientType
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
            when (style.gradientType) {
                GradientType.HORIZONTAL -> {
                    sb.append("""  <linearGradient id="qrGrad" x1="0%" y1="0%" x2="100%" y2="0%">""").append("\n")
                }
                GradientType.VERTICAL -> {
                    sb.append("""  <linearGradient id="qrGrad" x1="0%" y1="0%" x2="0%" y2="100%">""").append("\n")
                }
                GradientType.RADIAL -> {
                    sb.append("""  <radialGradient id="qrGrad" cx="50%" cy="50%" r="50%">""").append("\n")
                }
                GradientType.DIAGONAL -> {
                    sb.append("""  <linearGradient id="qrGrad" x1="0%" y1="0%" x2="100%" y2="100%">""").append("\n")
                }
            }
            sb.append("""    <stop offset="0%" stop-color="$fgHex" />""").append("\n")
            sb.append("""    <stop offset="100%" stop-color="$fgGradEndHex" />""").append("\n")
            if (style.gradientType == GradientType.RADIAL) {
                sb.append("  </radialGradient>\n")
            } else {
                sb.append("  </linearGradient>\n")
            }
        }
        sb.append("</defs>\n")

        // Background
        if (!style.transparentBg) {
            sb.append("""<rect width="$size" height="$totalHeight" fill="$bgHex" />""").append("\n")
        }

        val fillAttr = if (style.gradientMode) """fill="url(#qrGrad)"""" else """fill="$fgHex""""

        val m = style.margin
        fun inEye(x: Int, y: Int): Boolean {
            val inTL = x >= m && x < m + 7 && y >= m && y < m + 7
            val inTR = x >= matrixWidth - m - 7 && x < matrixWidth - m && y >= m && y < m + 7
            val inBL = x >= m && x < m + 7 && y >= matrixHeight - m - 7 && y < matrixHeight - m
            return inTL || inTR || inBL
        }

        // Draw Data modules
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                if (inEye(x, y)) continue
                if (bitMatrix.get(x, y)) {
                    val px = x * moduleW
                    val py = qrTop + (y * moduleH)
                    when (style.dotStyle) {
                        DotStyle.SQUARE -> {
                            sb.append("""<rect x="$px" y="$py" width="$moduleW" height="$moduleH" $fillAttr />""").append("\n")
                        }
                        DotStyle.ROUNDED -> {
                            val r = moduleW * 0.38f
                            sb.append("""<rect x="$px" y="$py" width="$moduleW" height="$moduleH" rx="$r" ry="$r" $fillAttr />""").append("\n")
                        }
                        DotStyle.DOTS -> {
                            val cx = px + moduleW / 2f
                            val cy = py + moduleH / 2f
                            val r = (moduleW.coerceAtMost(moduleH) / 2f) * 0.92f
                            sb.append("""<circle cx="$cx" cy="$cy" r="$r" $fillAttr />""").append("\n")
                        }
                        DotStyle.CLASSY -> {
                            val cx = px + moduleW / 2f
                            val cy = py + moduleH / 2f
                            sb.append("""<polygon points="$cx,$py ${px + moduleW},$cy $cx,${py + moduleH} $px,$cy" $fillAttr />""").append("\n")
                        }
                    }
                }
            }
        }

        // Draw Finder Eyes SVG
        fun appendFinderEyeSvg(gx: Int, gy: Int) {
            val ox = gx * moduleW
            val oy = qrTop + gy * moduleH
            val oW = 7 * moduleW
            val oH = 7 * moduleH
            val iW = 5 * moduleW
            val iH = 5 * moduleH
            val cW = 3 * moduleW
            val cH = 3 * moduleH

            val rO = if (style.eyeFrameStyle == EyeFrameStyle.ROUNDED) moduleW * 2.2f else if (style.eyeFrameStyle == EyeFrameStyle.CIRCLE) oW / 2f else 0f
            val rI = if (style.eyeFrameStyle == EyeFrameStyle.ROUNDED) moduleW * 1.6f else if (style.eyeFrameStyle == EyeFrameStyle.CIRCLE) iW / 2f else 0f
            val rC = if (style.eyeInnerStyle == EyeInnerStyle.ROUNDED) moduleW * 1.0f else if (style.eyeInnerStyle == EyeInnerStyle.DOT) cW / 2f else 0f

            sb.append("""<rect x="$ox" y="$oy" width="$oW" height="$oH" rx="$rO" ry="$rO" $fillAttr />""").append("\n")
            sb.append("""<rect x="${ox + moduleW}" y="${oy + moduleH}" width="$iW" height="$iH" rx="$rI" ry="$rI" fill="$bgHex" />""").append("\n")
            if (style.eyeInnerStyle == EyeInnerStyle.DIAMOND) {
                val cx = ox + oW / 2f
                val cy = oy + oH / 2f
                sb.append("""<polygon points="$cx,${cy - cH/2f} ${cx + cW/2f},$cy $cx,${cy + cH/2f} ${cx - cW/2f},$cy" $fillAttr />""").append("\n")
            } else {
                sb.append("""<rect x="${ox + 2*moduleW}" y="${oy + 2*moduleH}" width="$cW" height="$cH" rx="$rC" ry="$rC" $fillAttr />""").append("\n")
            }
        }

        appendFinderEyeSvg(m, m)
        appendFinderEyeSvg(matrixWidth - m - 7, m)
        appendFinderEyeSvg(m, matrixHeight - m - 7)

        // Center Logo
        if (style.logoUri != null) {
            val logoSize = size * (style.logoSizePercent.coerceIn(10, 35) / 100f)
            val cx = size / 2f
            val cy = qrTop + size / 2f
            val badgeMargin = logoSize * 0.14f
            val badgeSize = logoSize + badgeMargin * 2f
            val badgeX = cx - badgeSize / 2f
            val badgeY = cy - badgeSize / 2f
            val badgeR = badgeSize * 0.24f

            sb.append("""<rect x="$badgeX" y="$badgeY" width="$badgeSize" height="$badgeSize" rx="$badgeR" ry="$badgeR" fill="$bgHex" stroke="#22000000" stroke-width="2" />""").append("\n")

            val logoX = cx - logoSize / 2f
            val logoY = cy - logoSize / 2f
            val logoR = logoSize * 0.22f
            sb.append("""<g transform="translate($logoX, $logoY)">""").append("\n")
            sb.append("""  <rect width="$logoSize" height="$logoSize" rx="$logoR" ry="$logoR" fill="#0B0F19" />""").append("\n")
            sb.append("""  <circle cx="${logoSize * 0.32f}" cy="${logoSize * 0.32f}" r="${logoSize * 0.12f}" fill="#38BDF8" />""").append("\n")
            sb.append("""  <circle cx="${logoSize * 0.68f}" cy="${logoSize * 0.32f}" r="${logoSize * 0.12f}" fill="#818CF8" />""").append("\n")
            sb.append("""  <circle cx="${logoSize * 0.32f}" cy="${logoSize * 0.68f}" r="${logoSize * 0.12f}" fill="#34D399" />""").append("\n")
            sb.append("""  <circle cx="${logoSize * 0.5f}" cy="${logoSize * 0.5f}" r="${logoSize * 0.2f}" fill="none" stroke="#C084FC" stroke-width="${logoSize * 0.08f}" />""").append("\n")
            sb.append("""  <polygon points="${logoSize * 0.62f},${logoSize * 0.62f} ${logoSize * 0.85f},${logoSize * 0.85f} ${logoSize * 0.78f},${logoSize * 0.92f} ${logoSize * 0.55f},${logoSize * 0.68f}" fill="#EC4899" />""").append("\n")
            sb.append("""</g>""").append("\n")
        }

        // Frame
        if (hasFrame) {
            val bannerY = if (isTop) 6f else (totalHeight - frameHeight + 6f)
            val pillH = frameHeight - 12f
            val pillR = pillH / 2f
            val textY = bannerY + pillH / 2f + 5f
            sb.append("""<rect x="12" y="$bannerY" width="${size - 24}" height="$pillH" rx="$pillR" ry="$pillR" fill="$fgHex" />""").append("\n")
            sb.append("""<text x="${size / 2}" y="$textY" font-family="sans-serif" font-weight="bold" font-size="${frameHeight * 0.42f}" fill="#ffffff" text-anchor="middle">${style.frameLabel}</text>""").append("\n")
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
