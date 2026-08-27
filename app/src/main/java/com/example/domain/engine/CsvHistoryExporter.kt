package com.example.domain.engine

import com.example.data.local.entity.QRHistoryEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvHistoryExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun exportToCsv(items: List<QRHistoryEntity>): String {
        val sb = StringBuilder()
        // UTF-8 BOM for Microsoft Excel / Google Sheets compatibility
        sb.append("\uFEFF")

        // CSV Header
        sb.append("ID,ContentType,Label,Content,Notes,CreatedDate,Timestamp,ErrorCorrection,DotStyle,EyeFrameStyle,EyeInnerStyle,ForegroundColor,BackgroundColor,GradientEnabled\n")

        for (item in items) {
            val dateStr = dateFormat.format(Date(item.timestamp))
            var errorCorrection = "M"
            var dotStyle = "SQUARE"
            var eyeFrame = "SQUARE"
            var eyeInner = "SQUARE"
            var fgColor = "#000000"
            var bgColor = "#FFFFFF"
            var gradient = "false"

            try {
                if (item.styleJson.isNotBlank()) {
                    val obj = JSONObject(item.styleJson)
                    errorCorrection = obj.optString("errorCorrection", "M")
                    dotStyle = obj.optString("dotStyle", "SQUARE")
                    eyeFrame = obj.optString("eyeFrameStyle", "SQUARE")
                    eyeInner = obj.optString("eyeInnerStyle", "SQUARE")
                    val fg = obj.optLong("fgColor", 0xFF000000)
                    val bg = obj.optLong("bgColor", 0xFFFFFFFF)
                    fgColor = String.format("#%08X", fg)
                    bgColor = String.format("#%08X", bg)
                    gradient = obj.optBoolean("gradientMode", false).toString()
                }
            } catch (e: Exception) {
                // Ignore parse errors, use defaults
            }

            sb.append(escapeCsv(item.id.toString())).append(",")
            sb.append(escapeCsv(item.contentType)).append(",")
            sb.append(escapeCsv(item.label)).append(",")
            sb.append(escapeCsv(item.content)).append(",")
            sb.append(escapeCsv(item.notes)).append(",")
            sb.append(escapeCsv(dateStr)).append(",")
            sb.append(escapeCsv(item.timestamp.toString())).append(",")
            sb.append(escapeCsv(errorCorrection)).append(",")
            sb.append(escapeCsv(dotStyle)).append(",")
            sb.append(escapeCsv(eyeFrame)).append(",")
            sb.append(escapeCsv(eyeInner)).append(",")
            sb.append(escapeCsv(fgColor)).append(",")
            sb.append(escapeCsv(bgColor)).append(",")
            sb.append(escapeCsv(gradient)).append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        var str = value.replace("\r\n", " ").replace("\n", " ")
        if (str.contains(",") || str.contains("\"") || str.contains(";") || str.contains("\t")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }
}
