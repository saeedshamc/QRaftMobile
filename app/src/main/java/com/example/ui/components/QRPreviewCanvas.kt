package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.QRStyle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import kotlinx.coroutines.delay

@Composable
fun QRPreviewCanvas(
    bitmap: Bitmap?,
    payload: String,
    style: QRStyle,
    language: AppLanguage,
    scannabilityRatingKey: String = "contrast_excellent",
    scannabilityScore: String = "100%",
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExportPng: () -> Unit,
    onExportJpg: () -> Unit,
    onExportSvg: () -> Unit,
    onExportPdf: () -> Unit,
    onAddToHistory: () -> Unit,
    onSavePreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showExportMenu by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var showSuccessFeedbackBadge by remember { mutableStateOf(false) }
    var triggerFlash by remember { mutableStateOf(false) }

    // Trigger subtle success animation feedback whenever a new valid QR code bitmap is generated
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            triggerFlash = true
            showSuccessFeedbackBadge = true
            delay(1800)
            showSuccessFeedbackBadge = false
            triggerFlash = false
        }
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (triggerFlash) 0.8f else 0.0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "glowAlpha"
    )

    // Live preview pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Live Indicator + Error Correction & Size Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Preview Pill
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Text(
                            text = Strings.get("live_preview", language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Scannability Score Indicator
                val scannabilityColor = when (scannabilityRatingKey) {
                    "contrast_excellent" -> Color(0xFF10B981)
                    "contrast_good" -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
                Surface(
                    color = scannabilityColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (scannabilityRatingKey == "contrast_warning") Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = scannabilityColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${Strings.get(scannabilityRatingKey, language)} ($scannabilityScore)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = scannabilityColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info Sub-row: EC & Resolution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EC: ${style.errorCorrection.label} (${style.errorCorrection.tolerance})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${style.sizePx} × ${style.sizePx} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // QR Bitmap Container with subtle animated glowing border & smooth AnimatedContent transition
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(style.bgColor.toInt()))
                    .border(
                        width = if (triggerFlash) 2.5.dp else 1.dp,
                        color = if (triggerFlash) MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { showZoomDialog = true }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = bitmap,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(280)) + scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )) togetherWith (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.96f))
                    },
                    label = "qr_bitmap_transition"
                ) { targetBitmap ->
                    if (targetBitmap != null) {
                        Image(
                            bitmap = targetBitmap.asImageBitmap(),
                            contentDescription = "Generated QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Temporary Animated Success Badge Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSuccessFeedbackBadge,
                    enter = fadeIn(tween(200)) + slideInVertically(
                        initialOffsetY = { -it / 2 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -it / 2 }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = Strings.get("qr_generated_success", language),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row (Uniform 20dp icons with 48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy Action
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("copy_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = Strings.get("copy", language),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Share Action
                FilledTonalIconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("share_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = Strings.get("share", language),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Export Options Dropdown Button
                Box {
                    FilledTonalIconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("export_menu_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export Formats",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(Strings.get("export_png", language)) },
                            onClick = {
                                showExportMenu = false
                                onExportPng()
                            },
                            modifier = Modifier.testTag("export_png_item")
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.get("export_jpg", language)) },
                            onClick = {
                                showExportMenu = false
                                onExportJpg()
                            },
                            modifier = Modifier.testTag("export_jpg_item")
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.get("export_svg", language)) },
                            onClick = {
                                showExportMenu = false
                                onExportSvg()
                            },
                            modifier = Modifier.testTag("export_svg_item")
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.get("export_pdf", language)) },
                            onClick = {
                                showExportMenu = false
                                onExportPdf()
                            },
                            modifier = Modifier.testTag("export_pdf_item")
                        )
                    }
                }

                // Save to History
                FilledTonalIconButton(
                    onClick = onAddToHistory,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("add_to_history_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = Strings.get("add_to_history", language),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Save Style Preset
                FilledTonalIconButton(
                    onClick = onSavePreset,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("save_preset_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = Strings.get("save_as_preset", language),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Fullscreen Zoom Dialog Trigger
                FilledTonalIconButton(
                    onClick = { showZoomDialog = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("zoom_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Zoom Dialog for High-Res View
    if (showZoomDialog && bitmap != null) {
        Dialog(onDismissRequest = { showZoomDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Zoomed QR Code",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

