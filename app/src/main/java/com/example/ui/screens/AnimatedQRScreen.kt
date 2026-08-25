package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ReliabilityPreset
import com.example.ui.components.CameraPreviewView
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import com.example.ui.viewmodel.AnimatedMode
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AnimatedQRScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val mode by viewModel.animatedMode.collectAsState()

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
                text = { Text(Strings.get("anim_encode_tab", language), fontWeight = FontWeight.Bold) },
                icon = { Icon(imageVector = Icons.Default.QrCode, contentDescription = null) }
            )
            Tab(
                selected = mode == AnimatedMode.DECODE,
                onClick = { viewModel.animatedMode.value = AnimatedMode.DECODE },
                text = { Text(Strings.get("anim_decode_tab", language), fontWeight = FontWeight.Bold) },
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

@Composable
private fun AnimatedEncodeSection(viewModel: MainViewModel, language: AppLanguage) {
    val context = LocalContext.current
    val maxDim by viewModel.animMaxDim.collectAsState()
    val quality by viewModel.animQuality.collectAsState()
    val preset by viewModel.animReliabilityPreset.collectAsState()
    val fps by viewModel.animFps.collectAsState()
    val isPlaying by viewModel.animIsPlaying.collectAsState()
    val loop by viewModel.animLoop.collectAsState()
    val currentFrameIndex by viewModel.animCurrentFrameIndex.collectAsState()
    val encodeResult by viewModel.animEncodeResult.collectAsState()

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
            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(
                if (encodeResult == null) Strings.get("choose_image_to_encode", language) else Strings.get("change_image", language),
                fontWeight = FontWeight.Bold
            )
        }

        if (encodeResult != null) {
            val res = encodeResult!!
            val frames = res.frames
            val activeFrame = frames.getOrNull(currentFrameIndex) ?: frames.firstOrNull()

            // Animated Frame Player Canvas
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Frame counter & Session ID badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Session: #${res.sessionId}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${Strings.get("frame_counter", language)} ${currentFrameIndex + 1} / ${res.totalChunks}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Active QR frame image
                    if (activeFrame != null) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = activeFrame.qrBitmap.asImageBitmap(),
                                contentDescription = "Animated QR Frame",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { if (res.totalChunks > 0) (currentFrameIndex + 1).toFloat() / res.totalChunks else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    // Playback Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prev
                        IconButton(
                            onClick = {
                                viewModel.animCurrentFrameIndex.value = (currentFrameIndex - 1 + frames.size) % frames.size
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

                        // Next
                        IconButton(
                            onClick = {
                                viewModel.animCurrentFrameIndex.value = (currentFrameIndex + 1) % frames.size
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

                    // FPS Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${Strings.get("fps_label", language)}: $fps FPS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Slider(
                            value = fps.toFloat(),
                            onValueChange = { viewModel.animFps.value = it.toInt() },
                            valueRange = 1f..15f,
                            steps = 13,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                    }

                    // Export Buttons: GIF & ZIP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.exportAnimatedGif(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Gif, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(Strings.get("export_gif", language), style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = { viewModel.exportAnimatedFramesZip(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(Strings.get("export_zip", language), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Specs Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Transfer Specs & Integrity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
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
                    text = Strings.get("reliability_preset", language),
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
                                text = Strings.get("crc_verified", language),
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
                        Text(Strings.get("save_reconstructed", language))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
