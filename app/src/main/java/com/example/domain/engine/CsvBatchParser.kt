package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.domain.model.QRStyle
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val outputDirectory: File?,
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

    val SAMPLE_PLAIN_TEXT = """
https://example.com/promo1
https://example.com/product/42
https://example.com/contact
mailto:support@example.com?subject=Help
WIFI:T:WPA;S:OfficeGuest;P:Welcome2026;H:false;;
tel:+18005550199
geo:37.7749,-122.4194
    """.trimIndent()

    /**
     * Parses CSV or raw multi-line text input into structured batch records.
     * Supports comma-separated, tab-separated, semicolon-separated, or single-item per line.
     */
    fun parseCsv(input: String): List<CsvBatchRow> {
        val lines = input.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val firstLine = lines.first()
        val isCsvHeader = firstLine.contains(",") && (
                firstLine.contains("content", ignoreCase = true) ||
                firstLine.contains("url", ignoreCase = true) ||
                firstLine.contains("data", ignoreCase = true) ||
                firstLine.contains("text", ignoreCase = true)
        )

        val result = mutableListOf<CsvBatchRow>()

        if (isCsvHeader) {
            val header = parseCsvLine(firstLine).map { it.lowercase().trim() }
            val contentCol = header.indexOfFirst { it == "content" || it == "url" || it == "data" || it == "text" }
            val filenameCol = header.indexOfFirst { it == "filename" || it == "name" || it == "file" }
            val labelCol = header.indexOfFirst { it == "label" || it == "title" }
            val logoCol = header.indexOfFirst { it == "logourl" || it == "logo" }

            val actualContentCol = if (contentCol >= 0) contentCol else 0
            val dataLines = lines.drop(1)

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
        } else {
            // Line-by-line or delimiter-based plain lines
            for ((index, line) in lines.withIndex()) {
                val delimiter = when {
                    line.contains(",") -> ","
                    line.contains("\t") -> "\t"
                    line.contains(";") -> ";"
                    else -> null
                }

                if (delimiter != null) {
                    val cols = if (delimiter == ",") parseCsvLine(line) else line.split(delimiter)
                    val content = cols.getOrNull(0)?.trim() ?: ""
                    val filename = cols.getOrNull(1)?.trim()?.ifBlank { null }
                    val label = cols.getOrNull(2)?.trim()?.ifBlank { null }

                    val isValid = content.isNotBlank()
                    result.add(
                        CsvBatchRow(
                            rowIndex = index + 1,
                            content = content,
                            filename = filename,
                            label = label,
                            logoUrl = null,
                            isValid = isValid,
                            errorMessage = if (!isValid) "Content is empty" else null
                        )
                    )
                } else {
                    // Raw single line item
                    val content = line.trim()
                    val isValid = content.isNotBlank()
                    val suggestedFilename = "qr_${String.format(Locale.US, "%03d", index + 1)}"
                    val suggestedLabel = when {
                        content.startsWith("http", ignoreCase = true) -> "Website"
                        content.startsWith("WIFI", ignoreCase = true) -> "Wi-Fi"
                        content.startsWith("mailto", ignoreCase = true) -> "Email"
                        content.startsWith("tel", ignoreCase = true) -> "Phone"
                        else -> null
                    }
                    result.add(
                        CsvBatchRow(
                            rowIndex = index + 1,
                            content = content,
                            filename = suggestedFilename,
                            label = suggestedLabel,
                            logoUrl = null,
                            isValid = isValid,
                            errorMessage = if (!isValid) "Content is empty" else null
                        )
                    )
                }
            }
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

    /**
     * Generates multiple QR codes simultaneously, saving them into both a structured local
     * directory and a compressed ZIP archive with manifest metadata.
     */
    suspend fun generateBatchZip(
        context: Context,
        rows: List<CsvBatchRow>,
        baseStyle: QRStyle,
        delayMs: Long,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchGenerationResult {
        val validRows = rows.filter { it.isValid }
        val total = validRows.size
        if (total == 0) return BatchGenerationResult(0, 0, null, null, "")

        val sessionId = System.currentTimeMillis()
        val batchDirName = "batch_${sessionId}"

        // Structured local directory
        val structuredDir = File(context.cacheDir, "structured_batches/$batchDirName").apply {
            mkdirs()
        }

        val zipFile = File(context.cacheDir, "qraft_batch_${sessionId}.zip")
        val zos = ZipOutputStream(FileOutputStream(zipFile))
        var successCount = 0

        val manifestItems = JSONArray()
        val metadataCsvBuilder = StringBuilder("index,filename,content,label,dimensions,timestamp\n")
        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        for ((index, row) in validRows.withIndex()) {
            val rowStyle = baseStyle.copy(
                frameLabel = row.label ?: baseStyle.frameLabel
            )
            val bitmap = QRGeneratorEngine.generateQRBitmap(row.content, rowStyle, context)
            if (bitmap != null) {
                val cleanName = (row.filename ?: "qr_${String.format(Locale.US, "%03d", index + 1)}")
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val entryName = if (cleanName.endsWith(".png", true)) cleanName else "$cleanName.png"

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val pngBytes = baos.toByteArray()

                // Save to local structured directory
                val dirFile = File(structuredDir, entryName)
                dirFile.writeBytes(pngBytes)

                // Add to ZIP stream
                val entry = ZipEntry("images/$entryName")
                zos.putNextEntry(entry)
                zos.write(pngBytes)
                zos.closeEntry()

                // Manifest entry
                val itemObj = JSONObject().apply {
                    put("index", index + 1)
                    put("filename", entryName)
                    put("content", row.content)
                    put("label", row.label ?: "")
                    put("dimensions", "${bitmap.width}x${bitmap.height}")
                    put("dotStyle", rowStyle.dotStyle.name)
                    put("eyeFrameStyle", rowStyle.eyeFrameStyle.name)
                }
                manifestItems.put(itemObj)

                val escapedContent = row.content.replace("\"", "\"\"")
                metadataCsvBuilder.append("${index + 1},\"$entryName\",\"$escapedContent\",\"${row.label ?: ""}\",\"${bitmap.width}x${bitmap.height}\",\"$timestampStr\"\n")

                successCount++
            }

            onProgress(index + 1, total)
            if (delayMs > 0) {
                delay(delayMs)
            }
        }

        // Write structured manifest.json to ZIP and directory
        val manifestObj = JSONObject().apply {
            put("appName", "QRaft Studio")
            put("batchId", sessionId)
            put("generatedAt", timestampStr)
            put("totalItems", successCount)
            put("items", manifestItems)
        }
        val manifestBytes = manifestObj.toString(2).toByteArray(Charsets.UTF_8)

        // Save manifest to directory
        File(structuredDir, "manifest.json").writeBytes(manifestBytes)

        // Write manifest to ZIP
        zos.putNextEntry(ZipEntry("manifest.json"))
        zos.write(manifestBytes)
        zos.closeEntry()

        // Write metadata.csv to ZIP and directory
        val csvBytes = metadataCsvBuilder.toString().toByteArray(Charsets.UTF_8)
        File(structuredDir, "metadata.csv").writeBytes(csvBytes)

        zos.putNextEntry(ZipEntry("metadata.csv"))
        zos.write(csvBytes)
        zos.closeEntry()

        zos.flush()
        zos.close()

        return BatchGenerationResult(
            totalProcessed = total,
            successCount = successCount,
            zipFile = if (successCount > 0) zipFile else null,
            outputDirectory = if (successCount > 0) structuredDir else null,
            outputName = "qraft_batch_${sessionId}.zip"
        )
    }
}
