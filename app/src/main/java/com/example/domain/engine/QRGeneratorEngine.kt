package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.R
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

object QRGeneratorEngine {

    fun generateQRBitmap(
        content: String,
        style: QRStyle,
        context: Context? = null,
        forcedSize: Int? = null
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
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

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val outputSize = (forcedSize ?: style.sizePx).coerceIn(150, 4096)

            val effectiveBannerText = if (style.customBannerText.isNotBlank()) {
                style.customBannerText.trim()
            } else if (style.frameLabel != "None" && style.frameLabel.isNotBlank()) {
                style.frameLabel.trim()
            } else {
                ""
            }
            val hasFrame = effectiveBannerText.isNotBlank()
            val isBannerTop = style.bannerPositionTop || isTopBanner(style.frameLabel)
            val frameExtraHeight = if (hasFrame) (outputSize * 0.16f).toInt().coerceAtLeast(36) else 0

            val hasWatermark = style.watermarkText.isNotBlank()
            val watermarkExtraHeight = if (hasWatermark) (outputSize * 0.08f).toInt().coerceAtLeast(28) else 0

            val totalBitmapHeight = outputSize + frameExtraHeight + watermarkExtraHeight

            val bitmap = Bitmap.createBitmap(outputSize, totalBitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (style.transparentBg) Color.TRANSPARENT else style.bgColor.toInt()
                this.style = Paint.Style.FILL
            }
            if (!style.transparentBg) {
                canvas.drawRect(0f, 0f, outputSize.toFloat(), totalBitmapHeight.toFloat(), bgPaint)
            }

            // Calculate Vertical Positions
            val qrAreaTop = if (hasFrame && isBannerTop) frameExtraHeight.toFloat() else 0f
            val qrAreaRect = RectF(0f, qrAreaTop, outputSize.toFloat(), qrAreaTop + outputSize)

            // Foreground Paint (Color or Gradient)
            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                if (style.gradientMode) {
                    shader = when (style.gradientType) {
                        GradientType.HORIZONTAL -> LinearGradient(
                            0f, qrAreaTop,
                            outputSize.toFloat(), qrAreaTop,
                            style.fgColor.toInt(),
                            style.gradientEndColor.toInt(),
                            Shader.TileMode.CLAMP
                        )
                        GradientType.VERTICAL -> LinearGradient(
                            0f, qrAreaTop,
                            0f, qrAreaTop + outputSize,
                            style.fgColor.toInt(),
                            style.gradientEndColor.toInt(),
                            Shader.TileMode.CLAMP
                        )
                        GradientType.RADIAL -> RadialGradient(
                            outputSize / 2f, qrAreaTop + outputSize / 2f,
                            outputSize / 1.4f,
                            style.fgColor.toInt(),
                            style.gradientEndColor.toInt(),
                            Shader.TileMode.CLAMP
                        )
                        GradientType.DIAGONAL -> LinearGradient(
                            0f, qrAreaTop,
                            outputSize.toFloat(), qrAreaTop + outputSize,
                            style.fgColor.toInt(),
                            style.gradientEndColor.toInt(),
                            Shader.TileMode.CLAMP
                        )
                    }
                } else {
                    color = style.fgColor.toInt()
                }
            }

            val moduleSizeX = outputSize.toFloat() / matrixWidth
            val moduleSizeY = outputSize.toFloat() / matrixHeight

            // Track eye regions (7x7 modules each)
            val m = style.margin
            val tlEye = Rect(m, m, m + 7, m + 7)
            val trEye = Rect(matrixWidth - m - 7, m, matrixWidth - m, m + 7)
            val blEye = Rect(m, matrixHeight - m - 7, m + 7, matrixHeight - m)

            // Draw Data Modules (skipping finder eyes)
            for (y in 0 until matrixHeight) {
                for (x in 0 until matrixWidth) {
                    val inEye = isInside(x, y, tlEye) || isInside(x, y, trEye) || isInside(x, y, blEye)
                    if (inEye) continue

                    if (bitMatrix.get(x, y)) {
                        val left = x * moduleSizeX
                        val top = qrAreaTop + (y * moduleSizeY)
                        val right = left + moduleSizeX
                        val bottom = top + moduleSizeY

                        when (style.dotStyle) {
                            DotStyle.SQUARE -> {
                                canvas.drawRect(left, top, right, bottom, fgPaint)
                            }
                            DotStyle.ROUNDED -> {
                                val radius = moduleSizeX * 0.38f
                                canvas.drawRoundRect(left, top, right, bottom, radius, radius, fgPaint)
                            }
                            DotStyle.DOTS -> {
                                val cx = left + moduleSizeX / 2f
                                val cy = top + moduleSizeY / 2f
                                val radius = (moduleSizeX.coerceAtMost(moduleSizeY) / 2f) * 0.92f
                                canvas.drawCircle(cx, cy, radius, fgPaint)
                            }
                            DotStyle.CLASSY -> {
                                val cx = left + moduleSizeX / 2f
                                val cy = top + moduleSizeY / 2f
                                val path = Path().apply {
                                    moveTo(cx, top + 1f)
                                    lineTo(right - 1f, cy)
                                    lineTo(cx, bottom - 1f)
                                    lineTo(left + 1f, cy)
                                    close()
                                }
                                canvas.drawPath(path, fgPaint)
                            }
                        }
                    }
                }
            }

            // Draw Custom Finder Eyes (Top-Left, Top-Right, Bottom-Left)
            drawFinderEye(canvas, tlEye, moduleSizeX, moduleSizeY, qrAreaTop, style.eyeFrameStyle, style.eyeInnerStyle, fgPaint, bgPaint, 0)
            drawFinderEye(canvas, trEye, moduleSizeX, moduleSizeY, qrAreaTop, style.eyeFrameStyle, style.eyeInnerStyle, fgPaint, bgPaint, 1)
            drawFinderEye(canvas, blEye, moduleSizeX, moduleSizeY, qrAreaTop, style.eyeFrameStyle, style.eyeInnerStyle, fgPaint, bgPaint, 2)

            // Center Logo Overlay
            if (!style.logoUri.isNullOrBlank() && context != null) {
                drawCenterLogo(canvas, context, style.logoUri, qrAreaRect, style.logoSizePercent, style.bgColor.toInt())
            }

            // Frame and Banner Label (Positioned Top or Bottom)
            if (hasFrame) {
                val bannerTop = if (isBannerTop) 0f else qrAreaTop + outputSize
                drawFrameBanner(canvas, effectiveBannerText, outputSize, frameExtraHeight, bannerTop, style.fgColor.toInt(), style.bgColor.toInt())
            }

            // Watermark (Positioned Cleanly at Bottom below QR Code / Bottom Banner)
            if (hasWatermark) {
                val watermarkTop = if (hasFrame && !isBannerTop) {
                    qrAreaTop + outputSize + frameExtraHeight
                } else {
                    qrAreaTop + outputSize
                }
                drawWatermark(canvas, style.watermarkText, style.watermarkOpacity, outputSize, watermarkTop, watermarkExtraHeight.toFloat(), style.fgColor.toInt())
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isInside(x: Int, y: Int, r: Rect): Boolean {
        return x >= r.left && x < r.right && y >= r.top && y < r.bottom
    }

    private fun drawFinderEye(
        canvas: Canvas,
        eyeGridRect: Rect,
        modX: Float,
        modY: Float,
        qrAreaTop: Float,
        frameStyle: EyeFrameStyle,
        innerStyle: EyeInnerStyle,
        fgPaint: Paint,
        bgPaint: Paint,
        cornerIndex: Int // 0=TL, 1=TR, 2=BL
    ) {
        val outerLeft = eyeGridRect.left * modX
        val outerTop = qrAreaTop + (eyeGridRect.top * modY)
        val outerRight = outerLeft + (7 * modX)
        val outerBottom = outerTop + (7 * modY)
        val outerRect = RectF(outerLeft, outerTop, outerRight, outerBottom)

        val innerSpaceLeft = outerLeft + modX
        val innerSpaceTop = outerTop + modY
        val innerSpaceRight = outerRight - modX
        val innerSpaceBottom = outerBottom - modY
        val innerSpaceRect = RectF(innerSpaceLeft, innerSpaceTop, innerSpaceRight, innerSpaceBottom)

        val centerLeft = outerLeft + (2 * modX)
        val centerTop = outerTop + (2 * modY)
        val centerRight = outerRight - (2 * modX)
        val centerBottom = outerBottom - (2 * modY)
        val centerRect = RectF(centerLeft, centerTop, centerRight, centerBottom)

        // 1. Draw Outer 7x7 Shape
        when (frameStyle) {
            EyeFrameStyle.SQUARE -> {
                canvas.drawRect(outerRect, fgPaint)
            }
            EyeFrameStyle.ROUNDED -> {
                val r = 2.2f * modX
                canvas.drawRoundRect(outerRect, r, r, fgPaint)
            }
            EyeFrameStyle.CIRCLE -> {
                canvas.drawOval(outerRect, fgPaint)
            }
            EyeFrameStyle.LEAF -> {
                val path = Path()
                val r = 3.5f * modX
                val radii = when (cornerIndex) {
                    0 -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, 0f, 0f) // TL rounded
                    1 -> floatArrayOf(0f, 0f, r, r, 0f, 0f, 0f, 0f) // TR rounded
                    else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, r, r) // BL rounded
                }
                path.addRoundRect(outerRect, radii, Path.Direction.CW)
                canvas.drawPath(path, fgPaint)
            }
        }

        // 2. Clear 5x5 Inner Background Space
        when (frameStyle) {
            EyeFrameStyle.SQUARE -> {
                canvas.drawRect(innerSpaceRect, bgPaint)
            }
            EyeFrameStyle.ROUNDED -> {
                val r = 1.6f * modX
                canvas.drawRoundRect(innerSpaceRect, r, r, bgPaint)
            }
            EyeFrameStyle.CIRCLE -> {
                canvas.drawOval(innerSpaceRect, bgPaint)
            }
            EyeFrameStyle.LEAF -> {
                val path = Path()
                val r = 2.5f * modX
                val radii = when (cornerIndex) {
                    0 -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, 0f, 0f)
                    1 -> floatArrayOf(0f, 0f, r, r, 0f, 0f, 0f, 0f)
                    else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, r, r)
                }
                path.addRoundRect(innerSpaceRect, radii, Path.Direction.CW)
                canvas.drawPath(path, bgPaint)
            }
        }

        // 3. Draw 3x3 Center Dot
        when (innerStyle) {
            EyeInnerStyle.SQUARE -> {
                canvas.drawRect(centerRect, fgPaint)
            }
            EyeInnerStyle.ROUNDED -> {
                val r = 1.0f * modX
                canvas.drawRoundRect(centerRect, r, r, fgPaint)
            }
            EyeInnerStyle.DOT -> {
                canvas.drawOval(centerRect, fgPaint)
            }
            EyeInnerStyle.DIAMOND -> {
                val cx = centerRect.centerX()
                val cy = centerRect.centerY()
                val path = Path().apply {
                    moveTo(cx, centerRect.top)
                    lineTo(centerRect.right, cy)
                    lineTo(cx, centerRect.bottom)
                    lineTo(centerRect.left, cy)
                    close()
                }
                canvas.drawPath(path, fgPaint)
            }
        }
    }

    private fun isTopBanner(label: String): Boolean {
        return label.equals("Scan Me", ignoreCase = true)
    }

    private fun drawCenterLogo(
        canvas: Canvas,
        context: Context,
        uriString: String,
        qrRect: RectF,
        sizePercent: Int,
        badgeBgColor: Int
    ) {
        try {
            val srcBitmap: Bitmap? = when (uriString) {
                "preset:qraft_logo" -> loadDrawableBitmap(context, R.drawable.ic_qraft_logo)
                "preset:whatsapp" -> loadDrawableBitmap(context, R.drawable.ic_brand_whatsapp)
                "preset:telegram" -> loadDrawableBitmap(context, R.drawable.ic_brand_telegram)
                "preset:instagram" -> loadDrawableBitmap(context, R.drawable.ic_brand_instagram)
                "preset:youtube" -> loadDrawableBitmap(context, R.drawable.ic_brand_youtube)
                "preset:crypto" -> loadDrawableBitmap(context, R.drawable.ic_brand_crypto)
                "preset:wifi" -> loadDrawableBitmap(context, R.drawable.ic_brand_wifi)
                else -> {
                    val uri = Uri.parse(uriString)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bmp = if (inputStream != null) {
                        val b = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        b
                    } else null
                    bmp
                }
            }
            if (srcBitmap == null) return

            val logoSizePx = qrRect.width() * (sizePercent.coerceIn(10, 35) / 100f)
            val cx = qrRect.centerX()
            val cy = qrRect.centerY()
            val logoRect = RectF(
                cx - logoSizePx / 2f,
                cy - logoSizePx / 2f,
                cx + logoSizePx / 2f,
                cy + logoSizePx / 2f
            )

            val badgeMargin = logoSizePx * 0.14f
            val badgeRect = RectF(
                logoRect.left - badgeMargin,
                logoRect.top - badgeMargin,
                logoRect.right + badgeMargin,
                logoRect.bottom + badgeMargin
            )
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeBgColor
                style = Paint.Style.FILL
                setShadowLayer(badgeMargin, 0f, 2f, 0x40000000)
            }
            val cornerR = badgeMargin * 1.6f
            canvas.drawRoundRect(badgeRect, cornerR, cornerR, badgePaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22000000
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(badgeRect, cornerR, cornerR, borderPaint)

            val destRect = Rect(logoRect.left.toInt(), logoRect.top.toInt(), logoRect.right.toInt(), logoRect.bottom.toInt())
            canvas.drawBitmap(srcBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDrawableBitmap(context: Context, resId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, 256, 256)
        drawable.draw(c)
        return bmp
    }

    private fun drawFrameBanner(
        canvas: Canvas,
        label: String,
        width: Int,
        bannerHeight: Int,
        bannerTop: Float,
        fgColor: Int,
        bgColor: Int
    ) {
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            style = Paint.Style.FILL
        }
        val pillMargin = 14f
        val pillRect = RectF(
            pillMargin,
            bannerTop + 6f,
            width - pillMargin,
            bannerTop + bannerHeight - 6f
        )
        val r = pillRect.height() / 2f
        canvas.drawRoundRect(pillRect, r, r, bannerPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Color.luminance(fgColor) > 0.6f) Color.BLACK else Color.WHITE
            textSize = bannerHeight * 0.44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textY = pillRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(label, pillRect.centerX(), textY, textPaint)
    }

    private fun drawWatermark(
        canvas: Canvas,
        text: String,
        opacity: Float,
        width: Int,
        watermarkTop: Float,
        watermarkHeight: Float,
        fgColor: Int
    ) {
        val watermarkRect = RectF(0f, watermarkTop, width.toFloat(), watermarkTop + watermarkHeight)
        
        // Subtle divider or subtle pill background
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            alpha = (opacity.coerceIn(0.1f, 1f) * 40).toInt()
            style = Paint.Style.FILL
        }
        val pill = RectF(
            width * 0.08f,
            watermarkRect.top + 4f,
            width * 0.92f,
            watermarkRect.bottom - 4f
        )
        val radius = pill.height() / 2f
        canvas.drawRoundRect(pill, radius, radius, pillPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            alpha = (opacity.coerceIn(0.2f, 1f) * 255).toInt()
            textSize = (watermarkHeight * 0.42f).coerceAtLeast(13f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        }
        val textY = watermarkRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(text, watermarkRect.centerX(), textY, textPaint)
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            val reader = com.google.zxing.MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}
