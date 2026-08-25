package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.domain.model.QRStyle
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CsvBatchRow(
    val rowIndex: Int,
    val content: String,
    val filename: String?,
    val label: String?,
    val logoUrl: String?,
    val isValid: Boolean,
    val errorMessage: String?
)

data class BatchGenerationResult(
    val totalProcessed: Int,
    val successCount: Int,
    val zipFile: File?,
    val outputName: String
)

object CsvBatchParser {

    val SAMPLE_CSV = """
content,filename,label,logoUrl
https://example.com/promo1,summer_sale_qr,Summer Sale,
https://example.com/product/42,product_42_qr,Scan For Specs,
https://example.com/contact,contact_card_qr,Contact Us,
mailto:support@example.com?subject=Help,support_email_qr,Get Support,
WIFI:T:WPA;S:OfficeGuest;P:Welcome2026;H:false;;,guest_wifi_qr,Guest WiFi,
tel:+18005550199,toll_free_qr,Call Us,
geo:37.7749,-122.4194?q=HQ,hq_location_qr,Visit HQ,
    """.trimIndent()

    fun parseCsv(csvText: String): List<CsvBatchRow> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = parseCsvLine(lines.first()).map { it.lowercase().trim() }
        val contentCol = header.indexOfFirst { it == "content" || it == "url" || it == "data" || it == "text" }
        val filenameCol = header.indexOfFirst { it == "filename" || it == "name" || it == "file" }
        val labelCol = header.indexOfFirst { it == "label" || it == "title" }
        val logoCol = header.indexOfFirst { it == "logourl" || it == "logo" }

        val actualContentCol = if (contentCol >= 0) contentCol else 0

        val result = mutableListOf<CsvBatchRow>()
        val dataLines = if (contentCol >= 0) lines.drop(1) else lines

        for ((index, line) in dataLines.withIndex()) {
            val cols = parseCsvLine(line)
            val content = if (actualContentCol < cols.size) cols[actualContentCol].trim() else ""
            val filename = if (filenameCol >= 0 && filenameCol < cols.size) cols[filenameCol].trim().ifBlank { null } else null
            val label = if (labelCol >= 0 && labelCol < cols.size) cols[labelCol].trim().ifBlank { null } else null
            val logoUrl = if (logoCol >= 0 && logoCol < cols.size) cols[logoCol].trim().ifBlank { null } else null

            val isValid = content.isNotBlank()
            val error = if (!isValid) "Content column is empty" else null

            result.add(
                CsvBatchRow(
                    rowIndex = index + 1,
                    content = content,
                    filename = filename,
                    label = label,
                    logoUrl = logoUrl,
                    isValid = isValid,
                    errorMessage = error
                )
            )
        }
        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            } else if (c == ',' && !insideQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    suspend fun generateBatchZip(
        context: Context,
        rows: List<CsvBatchRow>,
        baseStyle: QRStyle,
        delayMs: Long,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchGenerationResult {
        val validRows = rows.filter { it.isValid }
        val total = validRows.size
        if (total == 0) return BatchGenerationResult(0, 0, null, "")

        val sessionId = System.currentTimeMillis()
        val zipFile = File(context.cacheDir, "qraft_batch_${sessionId}.zip")
        val zos = ZipOutputStream(FileOutputStream(zipFile))
        var successCount = 0

        for ((index, row) in validRows.withIndex()) {
            val rowStyle = baseStyle.copy(
                frameLabel = row.label ?: baseStyle.frameLabel
            )
            val bitmap = QRGeneratorEngine.generateQRBitmap(row.content, rowStyle, context)
            if (bitmap != null) {
                val cleanName = (row.filename ?: "qr_${String.format("%03d", index + 1)}")
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val entryName = if (cleanName.endsWith(".png", true)) cleanName else "$cleanName.png"

                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                zos.write(baos.toByteArray())
                zos.closeEntry()
                successCount++
            }

            onProgress(index + 1, total)
            if (delayMs > 0) {
                delay(delayMs)
            }
        }

        zos.flush()
        zos.close()

        return BatchGenerationResult(
            totalProcessed = total,
            successCount = successCount,
            zipFile = if (successCount > 0) zipFile else null,
            outputName = "qraft_batch_${sessionId}.zip"
        )
    }
}
