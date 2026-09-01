package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.QRStyle
import java.io.BufferedOutputStream
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

    /**
     * Encodes an image from URI into a sequence of animated QR frames.
     * Uses bounds-only pre-decoding and sub-sampling to prevent out-of-memory errors on large images.
     */
    fun encodeImageToFrames(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 220,
        quality: Float = 0.55f,
        reliabilityPreset: ReliabilityPreset = ReliabilityPreset.BALANCED
    ): AnimatedEncodeResult? {
        return try {
            // 1. Read image bounds first without allocating full bitmap memory
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            } ?: return null

            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return null

            // 2. Compute inSampleSize to downscale at decode time
            var sampleSize = 1
            val targetSize = maxDimension * 2 // Downscale safely to close to maxDimension
            while (origW / (sampleSize * 2) >= targetSize && origH / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2
            }

            // 3. Decode sub-sampled bitmap with RGB_565 (50% memory savings)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val srcBitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            val result = encodeBitmapToFrames(srcBitmap, maxDimension, quality, reliabilityPreset)
            srcBitmap.recycle()
            result
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
            if (scaledBitmap !== srcBitmap) {
                scaledBitmap.recycle()
            }
            val jpegBytes = baos.toByteArray()

            // Base64 encode without newlines
            val base64Payload = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val totalCrc32Hex = CRC32Helper.computeHex(base64Payload)
            val sessionId = generateSessionId()

            val chunkSize = reliabilityPreset.chunkSize
            val chunks = base64Payload.chunked(chunkSize)
            val totalChunks = chunks.size

            // Generate crisp 320x320 QR frames (minimal heap consumption)
            val style = QRStyle(
                errorCorrection = ErrorCorrection.M,
                sizePx = 320,
                dotStyle = DotStyle.SQUARE,
                margin = 2
            )

            val frames = mutableListOf<EncodedQRFrame>()
            for (i in chunks.indices) {
                val chunk = chunks[i]
                val chunkCrcHex = CRC32Helper.computeHex(chunk)
                val payloadString = "Q1|$sessionId|$i|$totalChunks|$chunkCrcHex|$totalCrc32Hex|$chunk"
                val qrBitmap = QRGeneratorEngine.generateQRBitmap(payloadString, style, null, 320) ?: continue
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
     * Automated cleanup utility to remove temporary frame files, zip archives,
     * and partial GIF files from cache directory.
     */
    fun cleanupTemporaryFiles(context: Context, olderThanMs: Long = 0L): Int {
        return AnimationCleanupWorker.triggerSyncCleanup(context, olderThanMs)
    }

    /**
     * Export all frames as a single ZIP archive using streaming buffers
     */
    fun exportFramesToZip(
        context: Context,
        frames: List<EncodedQRFrame>,
        sessionId: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): File? {
        val exportDir = QRFileExportManager.getExportCacheDir(context)
        val zipFile = File(exportDir, "qraft_animated_${sessionId}.zip")
        return try {
            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 65536))
            for (i in frames.indices) {
                if (isCancelled?.invoke() == true) {
                    zos.close()
                    zipFile.delete()
                    AnimationCleanupWorker.triggerSyncCleanup(context, 0L)
                    return null
                }
                val frame = frames[i]
                val entry = ZipEntry("frame_${String.format("%03d", frame.index + 1)}_of_${frame.total}.png")
                zos.putNextEntry(entry)
                val baos = ByteArrayOutputStream()
                frame.qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                zos.write(baos.toByteArray())
                zos.closeEntry()
                onProgress?.invoke(i + 1, frames.size)
            }
            zos.flush()
            zos.close()
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            zipFile.delete()
            AnimationCleanupWorker.triggerSyncCleanup(context, 0L)
            null
        } finally {
            AnimationCleanupWorker.triggerSyncCleanup(context, 120_000L, zipFile)
        }
    }

    /**
     * Export all frames as a single animated GIF file using a streaming buffer pipeline.
     * Uses downscaling for input frames (300x300 px) to minimize memory heap usage and maximize stability.
     * Includes progress reporting and cancellation support with automated cleanup of partial files.
     */
    fun exportFramesToGif(
        context: Context,
        frames: List<EncodedQRFrame>,
        sessionId: String,
        delayMs: Int = 200,
        targetDimension: Int = 300,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): File? {
        if (frames.isEmpty()) return null
        val exportDir = QRFileExportManager.getExportCacheDir(context)
        val gifFile = File(exportDir, "qraft_animated_${sessionId}.gif")

        return try {
            val bos = BufferedOutputStream(FileOutputStream(gifFile), 65536) // 64KB streaming buffer
            val encoder = StreamingGifEncoder()
            val safeDelay = delayMs.coerceIn(50, 2000)

            val outWidth = targetDimension.coerceIn(120, StreamingGifEncoder.MAX_SAFE_DIMENSION)
            val outHeight = targetDimension.coerceIn(120, StreamingGifEncoder.MAX_SAFE_DIMENSION)

            if (!encoder.start(bos, outWidth, outHeight, safeDelay)) {
                bos.close()
                gifFile.delete()
                AnimationCleanupWorker.triggerSyncCleanup(context, 0L)
                return null
            }

            for (i in frames.indices) {
                if (isCancelled?.invoke() == true) {
                    encoder.finish()
                    bos.close()
                    gifFile.delete()
                    AnimationCleanupWorker.triggerSyncCleanup(context, 0L)
                    return null
                }

                val frame = frames[i]
                val bmp = frame.qrBitmap
                val scaledBmp = if (bmp.width != outWidth || bmp.height != outHeight) {
                    Bitmap.createScaledBitmap(bmp, outWidth, outHeight, false)
                } else {
                    bmp
                }

                encoder.addFrame(scaledBmp, safeDelay)
                if (scaledBmp !== bmp) {
                    scaledBmp.recycle()
                }

                onProgress?.invoke(i + 1, frames.size)
            }

            encoder.finish()
            bos.flush()
            bos.close()

            if (gifFile.exists() && gifFile.length() > 0) {
                gifFile
            } else {
                gifFile.delete()
                AnimationCleanupWorker.triggerSyncCleanup(context, 0L)
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            gifFile.delete()
            AnimationCleanupWorker.triggerSyncCleanup(context, 0L)
            null
        } finally {
            // Trigger cleanup worker on export finish or failure to remove frame fragments
            AnimationCleanupWorker.triggerSyncCleanup(context, 120_000L, gifFile)
        }
    }
}
