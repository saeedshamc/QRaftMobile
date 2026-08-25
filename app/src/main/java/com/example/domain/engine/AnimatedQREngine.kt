package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.QRStyle
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

enum class ReliabilityPreset(val labelEn: String, val labelFa: String, val chunkSize: Int) {
    FAST("Fast (~220B/frame)", "سریع (~۲۲۰ بایت/فریم)", 220),
    BALANCED("Balanced (~140B/frame)", "متعادل (~۱۴۰ بایت/فریم)", 140),
    RELIABLE("Reliable (~90B/frame)", "بسیار مطمئن (~۹۰ بایت/فریم)", 90)
}

data class EncodedQRFrame(
    val index: Int,
    val total: Int,
    val payloadString: String,
    val qrBitmap: Bitmap
)

data class AnimatedEncodeResult(
    val sessionId: String,
    val totalChunks: Int,
    val originalSize: Pair<Int, Int>,
    val compressedBytesCount: Int,
    val frames: List<EncodedQRFrame>,
    val fullCrc32: String
)

data class DecodeSessionState(
    val sessionId: String = "",
    val totalExpected: Int = 0,
    val receivedIndices: Set<Int> = emptySet(),
    val receivedChunks: Map<Int, String> = emptyMap(),
    val expectedTotalCrc32: String = "",
    val isComplete: Boolean = false,
    val isCrcVerified: Boolean = false,
    val reconstructedBitmap: Bitmap? = null,
    val errorMessage: String? = null
)

object AnimatedQREngine {

    private val CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateSessionId(): String {
        return (1..5)
            .map { Random.nextInt(0, CHAR_POOL.length) }
            .map(CHAR_POOL::get)
            .joinToString("")
    }

    fun encodeImageToFrames(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 220,
        quality: Float = 0.55f,
        reliabilityPreset: ReliabilityPreset = ReliabilityPreset.BALANCED
    ): AnimatedEncodeResult? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val srcBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (srcBitmap == null) return null

            encodeBitmapToFrames(srcBitmap, maxDimension, quality, reliabilityPreset)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun encodeBitmapToFrames(
        srcBitmap: Bitmap,
        maxDimension: Int = 220,
        quality: Float = 0.55f,
        reliabilityPreset: ReliabilityPreset = ReliabilityPreset.BALANCED
    ): AnimatedEncodeResult? {
        try {
            // Resize image to maxDimension preserving aspect ratio
            val srcW = srcBitmap.width
            val srcH = srcBitmap.height
            val scale = if (srcW > srcH) {
                maxDimension.toFloat() / srcW
            } else {
                maxDimension.toFloat() / srcH
            }.coerceAtMost(1.0f)

            val targetW = (srcW * scale).toInt().coerceAtLeast(1)
            val targetH = (srcH * scale).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, targetW, targetH, true)

            // Compress to JPEG
            val baos = ByteArrayOutputStream()
            val jpegQuality = (quality.coerceIn(0.1f, 1.0f) * 100).toInt()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
            val jpegBytes = baos.toByteArray()

            // Base64 encode without newlines
            val base64Payload = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val totalCrc32Hex = CRC32Helper.computeHex(base64Payload)
            val sessionId = generateSessionId()

            val chunkSize = reliabilityPreset.chunkSize
            val chunks = base64Payload.chunked(chunkSize)
            val totalChunks = chunks.size

            val style = QRStyle(
                errorCorrection = ErrorCorrection.M,
                sizePx = 380,
                dotStyle = DotStyle.SQUARE,
                margin = 2
            )

            val frames = mutableListOf<EncodedQRFrame>()
            for (i in chunks.indices) {
                val chunk = chunks[i]
                val chunkCrcHex = CRC32Helper.computeHex(chunk)
                val payloadString = "Q1|$sessionId|$i|$totalChunks|$chunkCrcHex|$totalCrc32Hex|$chunk"
                val qrBitmap = QRGeneratorEngine.generateQRBitmap(payloadString, style, null, 380) ?: continue
                frames.add(EncodedQRFrame(i, totalChunks, payloadString, qrBitmap))
            }

            return AnimatedEncodeResult(
                sessionId = sessionId,
                totalChunks = totalChunks,
                originalSize = Pair(srcW, srcH),
                compressedBytesCount = jpegBytes.size,
                frames = frames,
                fullCrc32 = totalCrc32Hex
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parses an incoming raw QR string payload:
     * Q1|<sessionId>|<index>|<total>|<chunkCrc32Hex>|<totalCrc32Hex>|<base64Chunk>
     */
    fun processIncomingFrame(currentState: DecodeSessionState, rawPayload: String): DecodeSessionState {
        val trimmed = rawPayload.trim()
        if (!trimmed.startsWith("Q1|")) {
            return currentState
        }

        val parts = trimmed.split("|")
        if (parts.size < 7) {
            return currentState
        }

        val sessionId = parts[1]
        val index = parts[2].toIntOrNull() ?: return currentState
        val total = parts[3].toIntOrNull() ?: return currentState
        val chunkCrc = parts[4]
        val totalCrc = parts[5]
        val base64Chunk = parts.subList(6, parts.size).joinToString("|")

        // Verify chunk CRC32
        val computedChunkCrc = CRC32Helper.computeHex(base64Chunk)
        if (!computedChunkCrc.equals(chunkCrc, ignoreCase = true)) {
            // Discard corrupted chunk silently
            return currentState
        }

        // If session ID differs, start a new session
        val workingState = if (currentState.sessionId != sessionId || currentState.totalExpected != total) {
            DecodeSessionState(
                sessionId = sessionId,
                totalExpected = total,
                receivedIndices = emptySet(),
                receivedChunks = emptyMap(),
                expectedTotalCrc32 = totalCrc
            )
        } else {
            currentState
        }

        if (workingState.isComplete) {
            return workingState
        }

        val newIndices = workingState.receivedIndices + index
        val newChunks = workingState.receivedChunks + (index to base64Chunk)

        // Check if all chunks received
        if (newIndices.size >= total && (0 until total).all { newChunks.containsKey(it) }) {
            val fullBase64 = buildString {
                for (i in 0 until total) {
                    append(newChunks[i] ?: "")
                }
            }
            val computedTotalCrc = CRC32Helper.computeHex(fullBase64)
            if (computedTotalCrc.equals(totalCrc, ignoreCase = true)) {
                return try {
                    val bytes = Base64.decode(fullBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    workingState.copy(
                        receivedIndices = newIndices,
                        receivedChunks = newChunks,
                        isComplete = true,
                        isCrcVerified = true,
                        reconstructedBitmap = bitmap,
                        errorMessage = null
                    )
                } catch (e: Exception) {
                    workingState.copy(
                        receivedIndices = newIndices,
                        receivedChunks = newChunks,
                        isComplete = true,
                        isCrcVerified = false,
                        errorMessage = "Failed to decode reconstructed image: ${e.message}"
                    )
                }
            } else {
                return workingState.copy(
                    receivedIndices = newIndices,
                    receivedChunks = newChunks,
                    isComplete = false,
                    isCrcVerified = false,
                    errorMessage = "CRC32 total mismatch: expected $totalCrc but got $computedTotalCrc"
                )
            }
        }

        return workingState.copy(
            receivedIndices = newIndices,
            receivedChunks = newChunks
        )
    }

    /**
     * Export all frames as a single ZIP archive
     */
    fun exportFramesToZip(context: Context, frames: List<EncodedQRFrame>, sessionId: String): File? {
        return try {
            val zipFile = File(context.cacheDir, "qraft_animated_${sessionId}.zip")
            val zos = ZipOutputStream(FileOutputStream(zipFile))
            for (frame in frames) {
                val entry = ZipEntry("frame_${String.format("%03d", frame.index + 1)}_of_${frame.total}.png")
                zos.putNextEntry(entry)
                val baos = ByteArrayOutputStream()
                frame.qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                zos.write(baos.toByteArray())
                zos.closeEntry()
            }
            zos.flush()
            zos.close()
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Export all frames as a single animated GIF file
     */
    fun exportFramesToGif(context: Context, frames: List<EncodedQRFrame>, sessionId: String, fps: Int): File? {
        return try {
            val gifFile = File(context.cacheDir, "qraft_animated_${sessionId}.gif")
            val fos = FileOutputStream(gifFile)
            val encoder = GifEncoder()
            val delayMs = (1000 / fps.coerceIn(1, 20))
            encoder.setDelay(delayMs)
            encoder.setRepeat(0) // loop forever
            for (f in frames) {
                encoder.addFrame(f.qrBitmap)
            }
            encoder.encode(fos)
            fos.close()
            gifFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
