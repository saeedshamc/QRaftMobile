package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun CameraPreviewView(
    torchEnabled: Boolean,
    onQrDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
    ecoModeEnabled: Boolean = true,
    onEcoStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isEcoActive by remember { mutableStateOf(false) }
    var lastActivityTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastAnalyzedTime by remember { mutableStateOf(0L) }

    LaunchedEffect(torchEnabled, camera) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val laserDuration = if (isEcoActive) 3500 else 2000
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(laserDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_anim"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val reader = MultiFormatReader()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            val idleDuration = now - lastActivityTime
                            val isIdle = ecoModeEnabled && idleDuration > 4000L

                            if (isIdle != isEcoActive) {
                                isEcoActive = isIdle
                                onEcoStateChanged(isIdle)
                            }

                            // If Eco Mode is active, throttle analysis to ~2.2 FPS (every 450ms) to conserve CPU & battery
                            if (isIdle && (now - lastAnalyzedTime < 450L)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            lastAnalyzedTime = now

                            try {
                                val buffer = imageProxy.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val width = imageProxy.width
                                val height = imageProxy.height

                                val source = PlanarYUVLuminanceSource(
                                    bytes, width, height, 0, 0, width, height, false
                                )
                                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                val result = reader.decodeWithState(binaryBitmap)
                                if (result != null && result.text.isNotBlank()) {
                                    lastActivityTime = System.currentTimeMillis()
                                    if (isEcoActive) {
                                        isEcoActive = false
                                        onEcoStateChanged(false)
                                    }
                                    onQrDecoded(result.text)
                                }
                            } catch (e: Exception) {
                                // Scanning frame without QR
                            } finally {
                                reader.reset()
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Reticle / Target Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val reticleSize = (canvasWidth * 0.72f).coerceAtMost(320.dp.toPx())
            val left = (canvasWidth - reticleSize) / 2
            val top = (canvasHeight - reticleSize) / 2
            val cornerLen = 28.dp.toPx()
            val strokeW = 4.dp.toPx()
            val cornerColor = Color(0xFF3B82F6)

            // Dark semi-transparent scrim around reticle
            drawRect(Color(0x66000000))

            // Clear center
            drawRect(
                Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(reticleSize, reticleSize),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )

            // Reticle Frame Corners
            // Top-Left
            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
            drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)

            // Top-Right
            drawLine(cornerColor, Offset(left + reticleSize, top), Offset(left + reticleSize - cornerLen, top), strokeW)
            drawLine(cornerColor, Offset(left + reticleSize, top), Offset(left + reticleSize, top + cornerLen), strokeW)

            // Bottom-Left
            drawLine(cornerColor, Offset(left, top + reticleSize), Offset(left + cornerLen, top + reticleSize), strokeW)
            drawLine(cornerColor, Offset(left, top + reticleSize), Offset(left, top + reticleSize - cornerLen), strokeW)

            // Bottom-Right
            drawLine(cornerColor, Offset(left + reticleSize, top + reticleSize), Offset(left + reticleSize - cornerLen, top + reticleSize), strokeW)
            drawLine(cornerColor, Offset(left + reticleSize, top + reticleSize), Offset(left + reticleSize, top + reticleSize - cornerLen), strokeW)

            // Laser beam
            val laserY = top + (reticleSize * laserPosition)
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFF00E5FF), Color.White, Color(0xFF00E5FF), Color.Transparent),
                    startX = left,
                    endX = left + reticleSize
                ),
                start = Offset(left, laserY),
                end = Offset(left + reticleSize, laserY),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}
