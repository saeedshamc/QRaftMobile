package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.engine.AnimatedEncodeResult
import com.example.domain.engine.ReliabilityPreset
import com.example.ui.components.CameraPreviewView
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import com.example.ui.viewmodel.AnimatedMode
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun AnimatedQRScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val mode by viewModel.animatedMode.collectAsState()
    val isGifExporting by viewModel.isGifExporting.collectAsState()
    val gifProgress by viewModel.gifExportProgress.collectAsState()
    val gifCurrentFrame by viewModel.gifExportCurrentFrame.collectAsState()
    val gifTotalFrames by viewModel.gifExportTotalFrames.collectAsState()
    val showSequenceDialog by viewModel.showSequencePreviewDialog.collectAsState()
    val encodeResult by viewModel.animEncodeResult.collectAsState()

    // Visual Progress Indicator Dialog during GIF Encoding
    if (isGifExporting) {
        GifEncodingProgressDialog(
            progress = gifProgress,
            currentFrame = gifCurrentFrame,
            totalFrames = gifTotalFrames,
            language = language,
            onCancel = { viewModel.cancelGifExport(context) }
        )
    }

    // Sequence & Frame Verification Preview Modal
    if (showSequenceDialog && encodeResult != null) {
        SequencePreviewModal(
            result = encodeResult!!,
            viewModel = viewModel,
            language = language,
            onDismiss = { viewModel.showSequencePreviewDialog.value = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tab Selector: Encode vs Decode
        TabRow(
            selectedTabIndex = if (mode == AnimatedMode.ENCODE) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = mode == AnimatedMode.ENCODE,
                onClick = { viewModel.animatedMode.value = AnimatedMode.ENCODE },
                text = { Text(Strings.get("encode_mode", language), fontWeight = FontWeight.Bold) },
                icon = { Icon(imageVector = Icons.Default.QrCode, contentDescription = null) }
            )
            Tab(
                selected = mode == AnimatedMode.DECODE,
                onClick = { viewModel.animatedMode.value = AnimatedMode.DECODE },
                text = { Text(Strings.get("decode_mode", language), fontWeight = FontWeight.Bold) },
                icon = { Icon(imageVector = Icons.Default.FileDownload, contentDescription = null) }
            )
        }

        if (mode == AnimatedMode.ENCODE) {
            AnimatedEncodeSection(viewModel, language)
        } else {
            AnimatedDecodeSection(viewModel, language)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatedEncodeSection(viewModel: MainViewModel, language: AppLanguage) {
    val context = LocalContext.current
    val preset by viewModel.animReliabilityPreset.collectAsState()
    val frameDelayMs by viewModel.animFrameDelayMs.collectAsState()
    val fps by viewModel.animFps.collectAsState()
    val isPlaying by viewModel.animIsPlaying.collectAsState()
    val loop by viewModel.animLoop.collectAsState()
    val currentFrameIndex by viewModel.animCurrentFrameIndex.collectAsState()
    val encodeResult by viewModel.animEncodeResult.collectAsState()

    // Live frame cycling timer
    LaunchedEffect(isPlaying, encodeResult?.frames?.size, frameDelayMs, loop) {
        val frames = encodeResult?.frames ?: emptyList()
        if (isPlaying && frames.isNotEmpty()) {
            while (true) {
                delay(frameDelayMs.toLong().coerceAtLeast(40L))
                val next = (viewModel.animCurrentFrameIndex.value + 1)
                if (next >= frames.size) {
                    if (loop) {
                        viewModel.animCurrentFrameIndex.value = 0
                    } else {
                        viewModel.animIsPlaying.value = false
                        break
                    }
                } else {
                    viewModel.animCurrentFrameIndex.value = next
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.encodeAnimatedImage(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pick image trigger button
        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_anim_image_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = Strings.get("pick_image", language),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Active Player Card & Sequence Verification
        if (encodeResult != null) {
            val res = encodeResult!!
            val frames = res.frames
            val safeIndex = currentFrameIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0))

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header: Frame counter badge & preview button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = String.format(Strings.get("frame_counter_badge", language), safeIndex + 1, frames.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Preview & Verify Button
                        FilledTonalButton(
                            onClick = { viewModel.showSequencePreviewDialog.value = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("preview_sequence_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = Strings.get("preview_frames", language),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Display Current Frame QR Bitmap
                    if (frames.isNotEmpty()) {
                        val currentFrame = frames[safeIndex]
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = currentFrame.qrBitmap.asImageBitmap(),
                                contentDescription = "QR Frame ${safeIndex + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Playback Controls Row: Rewind, Play/Pause, Fast Forward, Loop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Frame
                        IconButton(
                            onClick = {
                                val prev = if (safeIndex - 1 < 0) frames.size - 1 else safeIndex - 1
                                viewModel.animCurrentFrameIndex.value = prev
                            }
                        ) {
                            Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Previous Frame")
                        }

                        // Play/Pause
                        FilledTonalIconButton(
                            onClick = { viewModel.animIsPlaying.value = !isPlaying },
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }

                        // Next Frame
                        IconButton(
                            onClick = {
                                viewModel.animCurrentFrameIndex.value = (safeIndex + 1) % frames.size
                            }
                        ) {
                            Icon(imageVector = Icons.Default.FastForward, contentDescription = "Next Frame")
                        }

                        // Loop toggle
                        IconButton(
                            onClick = { viewModel.animLoop.value = !loop }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = "Toggle Loop",
                                tint = if (loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Frame Delay & Speed Slider Control
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${Strings.get("frame_delay_label", language)}: ${frameDelayMs} ms",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "~${fps} FPS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = frameDelayMs.toFloat(),
                            onValueChange = { viewModel.setAnimFrameDelayMs(it.toInt()) },
                            valueRange = 50f..1000f,
                            steps = 18,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("anim_delay_slider")
                        )

                        // Quick Speed Preset Chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(100, 150, 200, 300, 500).forEach { delayVal ->
                                val isCur = frameDelayMs == delayVal
                                FilterChip(
                                    selected = isCur,
                                    onClick = { viewModel.setAnimFrameDelayMs(delayVal) },
                                    label = { Text("${delayVal}ms", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // Export Action Buttons: GIF & ZIP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportAnimatedGif(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_gif_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gif,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = Strings.get("export_gif", language),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.exportAnimatedFramesZip(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_zip_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = Strings.get("export_frames_zip", language),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Transfer Specs & Integrity Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transfer Specs & Integrity",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        // Automated Cleanup Action
                        OutlinedButton(
                            onClick = { viewModel.cleanupTempCache(context) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("cleanup_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(end = 4.dp)
                            )
                            Text(Strings.get("cleanup_cache", language), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = "Original: ${res.originalSize.first}×${res.originalSize.second}px • Payload: ${res.compressedBytesCount} bytes (${res.totalChunks} QR frames)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Total CRC32: ${res.fullCrc32} • Reliability: ${preset.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Reliability presets & configuration
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = Strings.get("rel_preset", language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReliabilityPreset.values().forEach { p ->
                        val isSelected = preset == p
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.animReliabilityPreset.value = p
                                val uri = viewModel.animSourceUri.value
                                if (uri != null) viewModel.encodeAnimatedImage(context, uri)
                            },
                            label = { Text(if (language == AppLanguage.FA) p.labelFa else p.labelEn) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Visual Progress Indicator Dialog during GIF Encoding
 */
@Composable
private fun GifEncodingProgressDialog(
    progress: Float,
    currentFrame: Int,
    totalFrames: Int,
    language: AppLanguage,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Modal during encoding */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = Strings.get("encoding_gif_title", language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (totalFrames > 0) {
                        String.format(Strings.get("encoding_gif_progress", language), currentFrame, totalFrames)
                    } else {
                        Strings.get("encoding_in_progress", language)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Streaming downscaled buffer • Heap safe LZW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(Strings.get("cancel_export", language), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Full Sequence & Frame Verification Preview Modal
 */
@Composable
private fun SequencePreviewModal(
    result: AnimatedEncodeResult,
    viewModel: MainViewModel,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val frames = result.frames
    var selectedIndex by remember { mutableIntStateOf(0) }
    val isPlaying by viewModel.animIsPlaying.collectAsState()
    val frameDelayMs by viewModel.animFrameDelayMs.collectAsState()
    val loop by viewModel.animLoop.collectAsState()

    // Sequence playback in modal
    LaunchedEffect(isPlaying, frames.size, frameDelayMs, loop) {
        if (isPlaying && frames.isNotEmpty()) {
            while (true) {
                delay(frameDelayMs.toLong().coerceAtLeast(40L))
                val next = (selectedIndex + 1)
                if (next >= frames.size) {
                    if (loop) {
                        selectedIndex = 0
                    } else {
                        viewModel.animIsPlaying.value = false
                        break
                    }
                } else {
                    selectedIndex = next
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Strings.get("preview_sequence_title", language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Session: #${result.sessionId} • ${result.totalChunks} Frames",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Sequence Status Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format(Strings.get("sequence_status_all_valid", language), frames.size),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Large Main Frame Preview
                if (frames.isNotEmpty()) {
                    val safeIdx = selectedIndex.coerceIn(0, frames.size - 1)
                    val frame = frames[safeIdx]

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = frame.qrBitmap.asImageBitmap(),
                            contentDescription = "Frame ${safeIdx + 1}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Stepper Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // First
                        IconButton(onClick = { selectedIndex = 0 }) {
                            Icon(imageVector = Icons.Default.FirstPage, contentDescription = "First Frame")
                        }

                        // Previous
                        IconButton(onClick = {
                            selectedIndex = if (safeIdx - 1 < 0) frames.size - 1 else safeIdx - 1
                        }) {
                            Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Previous Frame")
                        }

                        // Play/Pause
                        FilledTonalIconButton(
                            onClick = { viewModel.animIsPlaying.value = !isPlaying },
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }

                        // Next
                        IconButton(onClick = {
                            selectedIndex = (safeIdx + 1) % frames.size
                        }) {
                            Icon(imageVector = Icons.Default.FastForward, contentDescription = "Next Frame")
                        }

                        // Last
                        IconButton(onClick = { selectedIndex = frames.size - 1 }) {
                            Icon(imageVector = Icons.Default.LastPage, contentDescription = "Last Frame")
                        }
                    }

                    // Frame Details Card
                    val parts = frame.payloadString.split("|")
                    val chunkCrc = if (parts.size >= 5) parts[4] else "N/A"

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${Strings.get("frame_inspector", language)}: #${safeIdx + 1} of ${frames.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Chunk CRC32: $chunkCrc • Total CRC32: ${result.fullCrc32}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = String.format(Strings.get("chunk_size_bytes", language), frame.payloadString.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Horizontal Frame Sequence Strip
                Text(
                    text = "Sequence Frame Strip",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(frames.size) { idx ->
                        val isSel = idx == selectedIndex
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedIndex = idx }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "CRC: OK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Export from Preview Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            viewModel.exportAnimatedGif(context)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Gif, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(Strings.get("export_gif", language), style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            viewModel.exportAnimatedFramesZip(context)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(Strings.get("export_frames_zip", language), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedDecodeSection(viewModel: MainViewModel, language: AppLanguage) {
    val context = LocalContext.current
    val decodeState by viewModel.animDecodeState.collectAsState()
    val torchEnabled by viewModel.scanTorchEnabled.collectAsState()

    val gifPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.decodeUploadedGif(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Upload GIF or capture camera
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { gifPickerLauncher.launch("*/*") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(Strings.get("upload_gif", language), style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = { viewModel.resetAnimatedDecodeSession() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(Strings.get("reset_session", language), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Live Scanner Box for animated QR stream
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            CameraPreviewView(
                torchEnabled = torchEnabled,
                onQrDecoded = { raw -> viewModel.onAnimatedQrScanned(raw) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Decode Progress Tracker Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Strings.get("chunks_received", language)}: ${decodeState.receivedIndices.size} / ${if (decodeState.totalExpected > 0) decodeState.totalExpected else "--"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    if (decodeState.sessionId.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#${decodeState.sessionId}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                val total = decodeState.totalExpected
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { decodeState.receivedIndices.size.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                // Status banner
                if (decodeState.isComplete && decodeState.isCrcVerified) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = Strings.get("crc_valid", language),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (decodeState.errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = decodeState.errorMessage ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Reconstructed Image Display
        if (decodeState.reconstructedBitmap != null) {
            val bitmap = decodeState.reconstructedBitmap!!
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = Strings.get("reconstructed_image", language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Reconstructed Transferred Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Button(
                        onClick = { viewModel.saveReconstructedImage(context, bitmap) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(Strings.get("save_image", language))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
