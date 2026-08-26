package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
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
            val outputSize = (forcedSize ?: style.sizePx).coerceIn(150, 2000)

            // Determine if frame is present
            val hasFrame = style.frameLabel != "None" && style.frameLabel.isNotBlank()
            val frameExtraHeight = if (hasFrame) (outputSize * 0.18f).toInt() else 0
            val totalBitmapHeight = outputSize + frameExtraHeight

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

            // QR area
            val qrAreaTop = if (hasFrame && isTopBanner(style.frameLabel)) frameExtraHeight.toFloat() else 0f
            val qrAreaRect = RectF(0f, qrAreaTop, outputSize.toFloat(), qrAreaTop + outputSize)

            // Foreground Paint (Color or Linear Gradient)
            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                if (style.gradientMode) {
                    shader = LinearGradient(
                        0f, qrAreaTop,
                        outputSize.toFloat(), qrAreaTop + outputSize,
                        style.fgColor.toInt(),
                        style.gradientEndColor.toInt(),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    color = style.fgColor.toInt()
                }
            }

            val moduleSizeX = outputSize.toFloat() / matrixWidth
            val moduleSizeY = outputSize.toFloat() / matrixHeight

            // Draw Modules
            for (y in 0 until matrixHeight) {
                for (x in 0 until matrixWidth) {
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
                                val radius = moduleSizeX * 0.35f
                                canvas.drawRoundRect(left, top, right, bottom, radius, radius, fgPaint)
                            }
                            DotStyle.DOTS -> {
                                val cx = left + moduleSizeX / 2f
                                val cy = top + moduleSizeY / 2f
                                val radius = (moduleSizeX.coerceAtMost(moduleSizeY) / 2f) * 0.9f
                                canvas.drawCircle(cx, cy, radius, fgPaint)
                            }
                        }
                    }
                }
            }

            // Center Logo Overlay
            if (!style.logoUri.isNullOrBlank() && context != null) {
                drawCenterLogo(canvas, context, style.logoUri, qrAreaRect, style.logoSizePercent, style.bgColor.toInt())
            }

            // Frame and Banner Label
            if (hasFrame) {
                drawFrameBanner(canvas, style.frameLabel, outputSize, totalBitmapHeight, style.fgColor.toInt(), style.bgColor.toInt(), qrAreaTop > 0)
            }

            // Watermark
            if (style.watermarkText.isNotBlank()) {
                drawWatermark(canvas, style.watermarkText, style.watermarkOpacity, outputSize, totalBitmapHeight, style.fgColor.toInt())
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
            val srcBitmap: Bitmap? = if (uriString == "preset:qraft_logo") {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.example.R.drawable.ic_qraft_logo)
                if (drawable != null) {
                    val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    drawable.setBounds(0, 0, 256, 256)
                    drawable.draw(c)
                    bmp
                } else null
            } else {
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = if (inputStream != null) {
                    val b = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    b
                } else null
                bmp
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

            // Draw white/bg protective badge around logo
            val badgeMargin = logoSizePx * 0.12f
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
            val cornerR = badgeMargin * 1.5f
            canvas.drawRoundRect(badgeRect, cornerR, cornerR, badgePaint)

            // Border around badge
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22000000
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(badgeRect, cornerR, cornerR, borderPaint)

            // Draw logo image scaled
            val destRect = Rect(logoRect.left.toInt(), logoRect.top.toInt(), logoRect.right.toInt(), logoRect.bottom.toInt())
            canvas.drawBitmap(srcBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun drawFrameBanner(
        canvas: Canvas,
        label: String,
        width: Int,
        totalHeight: Int,
        fgColor: Int,
        bgColor: Int,
        isTop: Boolean
    ) {
        val bannerHeight = (width * 0.18f)
        val bannerRect = if (isTop) {
            RectF(0f, 0f, width.toFloat(), bannerHeight)
        } else {
            RectF(0f, totalHeight - bannerHeight, width.toFloat(), totalHeight.toFloat())
        }

        // Banner background pill
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            style = Paint.Style.FILL
        }
        val pillMargin = 12f
        val pillRect = RectF(
            pillMargin,
            bannerRect.top + 6f,
            width - pillMargin,
            bannerRect.bottom - 6f
        )
        val r = pillRect.height() / 2f
        canvas.drawRoundRect(pillRect, r, r, bannerPaint)

        // Banner text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Color.luminance(fgColor) > 0.6f) Color.BLACK else Color.WHITE
            textSize = bannerHeight * 0.42f
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
        height: Int,
        fgColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
            textSize = (width * 0.032f).coerceAtLeast(14f)
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText(text, width - 16f, height - 16f, paint)
    }
}
