package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object QRPdfExporter {
    fun exportToPdfFile(
        context: Context,
        qrBitmap: Bitmap,
        title: String,
        content: String
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            // Header
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1E293B.toInt()
                textSize = 24f
                isFakeBoldText = true
            }
            canvas.drawText("QRaft — $title", 48f, 64f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 12f
            }
            canvas.drawText("Generated on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}", 48f, 84f, subPaint)

            // Line
            val linePaint = Paint().apply { color = 0xFFE2E8F0.toInt(); strokeWidth = 1.5f }
            canvas.drawLine(48f, 100f, 547f, 100f, linePaint)

            // QR Image centered
            val qrSize = 340
            val left = (595 - qrSize) / 2
            val top = 140
            val destRect = Rect(left, top, left + qrSize, top + qrSize)
            canvas.drawBitmap(qrBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            // Encoded content preview at bottom
            val contentTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF334155.toInt()
                textSize = 13f
                isFakeBoldText = true
            }
            canvas.drawText("Encoded Payload:", 48f, (top + qrSize + 48).toFloat(), contentTitlePaint)

            val payloadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF475569.toInt()
                textSize = 10f
            }
            val wrappedContent = if (content.length > 200) content.take(200) + "..." else content
            canvas.drawText(wrappedContent, 48f, (top + qrSize + 68).toFloat(), payloadPaint)

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "qraft_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
