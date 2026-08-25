package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Movie
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream

object GifFrameExtractor {

    fun decodeFromImageOrGif(context: Context, uri: Uri): DecodeSessionState {
        var state = DecodeSessionState()
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return state
            val bytes = inputStream.readBytes()
            inputStream.close()

            // Try decoding as static bitmap first
            val staticBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (staticBitmap != null) {
                val qrText = scanBitmapForQR(staticBitmap)
                if (qrText != null) {
                    state = AnimatedQREngine.processIncomingFrame(state, qrText)
                }
            }

            // Also try extracting frames if it's a GIF
            @Suppress("DEPRECATION")
            val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
            if (movie != null && movie.duration() > 0) {
                val duration = movie.duration()
                val width = movie.width().coerceAtLeast(200)
                val height = movie.height().coerceAtLeast(200)
                val stepMs = 150
                var currentMs = 0
                val reader = MultiFormatReader()

                while (currentMs <= duration && !state.isComplete) {
                    movie.setTime(currentMs)
                    val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(frameBitmap)
                    movie.draw(canvas, 0f, 0f)

                    val qrText = scanBitmapForQR(frameBitmap)
                    if (qrText != null) {
                        state = AnimatedQREngine.processIncomingFrame(state, qrText)
                    }

                    currentMs += stepMs
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            state = state.copy(errorMessage = "Failed to process GIF: ${e.message}")
        }
        return state
    }

    private fun scanBitmapForQR(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}
