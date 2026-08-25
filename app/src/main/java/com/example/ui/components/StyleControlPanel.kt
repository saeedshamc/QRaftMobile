package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ColorPalettePreset
import com.example.domain.model.ColorPalettes
import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.QRStyle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings

@Composable
fun StyleControlPanel(
    style: QRStyle,
    language: AppLanguage,
    onStyleChange: ((QRStyle) -> QRStyle) -> Unit,
    onApplyPalette: (ColorPalettePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onStyleChange {
                it.copy(
                    logoUri = uri.toString(),
                    errorCorrection = ErrorCorrection.H // bump to H for scannability
                )
            }
        }
    }

    val frameOptions = listOf("None", "Scan Me", "Website", "Visit Website", "Add Contact")
    var frameDropdownExpanded by remember { mutableStateOf(false) }

    val commonColors = listOf(
        0xFF000000, 0xFF1E293B, 0xFF1D4ED8, 0xFF047857,
        0xFFBE123C, 0xFF6D28D9, 0xFF0D9488, 0xFF78350F,
        0xFFFFFFFF, 0xFFF8FAFC, 0xFFF0FDF4, 0xFFFFF1F2
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = Strings.get("style_heading", language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. Color Palette Presets Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Strings.get("color_palettes", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ColorPalettes.presets.forEach { palette ->
                        val isSelected = style.activePaletteId == palette.id
                        val name = if (language == AppLanguage.FA) palette.nameFa else palette.nameEn

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onApplyPalette(palette) }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(palette.fgColor.toInt()),
                                                Color(palette.gradientEndColor.toInt())
                                            )
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(palette.bgColor.toInt()),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (palette.id == "cyber") Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Error Correction Segmented Control
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Strings.get("error_correction", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ErrorCorrection.values().forEach { ec ->
                        val isSelected = style.errorCorrection == ec
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onStyleChange { it.copy(errorCorrection = ec, activePaletteId = null) }
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = ec.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = ec.tolerance,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Dot Style Selection (Square, Rounded, Dots)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Strings.get("dot_style", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        DotStyle.SQUARE to Strings.get("dot_square", language),
                        DotStyle.ROUNDED to Strings.get("dot_rounded", language),
                        DotStyle.DOTS to Strings.get("dot_dots", language)
                    ).forEach { (dot, label) ->
                        val isSelected = style.dotStyle == dot
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange { it.copy(dotStyle = dot) } },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // 4. Output Size Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Strings.get("size_px", language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${style.sizePx} px",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = style.sizePx.toFloat(),
                    onValueChange = { onStyleChange { s -> s.copy(sizePx = it.toInt()) } },
                    valueRange = 150f..1000f,
                    steps = 17
                )
            }

            // 5. Quiet Zone Margin Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Strings.get("margin_quiet", language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${style.margin} mod",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = style.margin.toFloat(),
                    onValueChange = { onStyleChange { s -> s.copy(margin = it.toInt()) } },
                    valueRange = 0f..10f,
                    steps = 10
                )
            }

            // 6. Gradient Mode Toggle & Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Strings.get("gradient_mode", language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = style.gradientMode,
                        onCheckedChange = { onStyleChange { s -> s.copy(gradientMode = it, activePaletteId = null) } }
                    )
                }

                // Foreground & Gradient End Color Pickers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    commonColors.take(8).forEach { colorHex ->
                        val color = Color(colorHex.toInt())
                        val isSelected = style.fgColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onStyleChange {
                                        it.copy(
                                            fgColor = colorHex,
                                            activePaletteId = null
                                        )
                                    }
                                }
                        )
                    }
                }
            }

            // 7. Background Color & Transparent Background Toggle
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Strings.get("transparent_bg", language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = style.transparentBg,
                        onCheckedChange = { onStyleChange { s -> s.copy(transparentBg = it) } }
                    )
                }

                if (!style.transparentBg) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        commonColors.takeLast(8).forEach { colorHex ->
                            val color = Color(colorHex.toInt())
                            val isSelected = style.bgColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onStyleChange {
                                            it.copy(
                                                bgColor = colorHex,
                                                activePaletteId = null
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // 8. Center Logo Overlay
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Strings.get("center_logo", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { logoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = if (style.logoUri == null) Strings.get("choose_logo", language) else "Replace Logo",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    if (style.logoUri != null) {
                        IconButton(
                            onClick = { onStyleChange { it.copy(logoUri = null) } }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = Strings.get("remove_logo", language),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (style.logoUri != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Strings.get("logo_size", language),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "${style.logoSizePercent}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Slider(
                            value = style.logoSizePercent.toFloat(),
                            onValueChange = { onStyleChange { s -> s.copy(logoSizePercent = it.toInt()) } },
                            valueRange = 10f..35f,
                            steps = 5
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = Strings.get("logo_tip", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // 9. Frame & Label Banner
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Strings.get("frame_banner", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box {
                    OutlinedButton(
                        onClick = { frameDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (style.frameLabel) {
                                "None" -> Strings.get("frame_none", language)
                                "Scan Me" -> Strings.get("frame_scan_me", language)
                                "Website" -> Strings.get("frame_website", language)
                                "Visit Website" -> Strings.get("frame_visit_website", language)
                                "Add Contact" -> Strings.get("frame_add_contact", language)
                                else -> style.frameLabel
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = frameDropdownExpanded,
                        onDismissRequest = { frameDropdownExpanded = false }
                    ) {
                        frameOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (opt) {
                                            "None" -> Strings.get("frame_none", language)
                                            "Scan Me" -> Strings.get("frame_scan_me", language)
                                            "Website" -> Strings.get("frame_website", language)
                                            "Visit Website" -> Strings.get("frame_visit_website", language)
                                            "Add Contact" -> Strings.get("frame_add_contact", language)
                                            else -> opt
                                        }
                                    )
                                },
                                onClick = {
                                    frameDropdownExpanded = false
                                    onStyleChange { it.copy(frameLabel = opt) }
                                }
                            )
                        }
                    }
                }
            }

            // 10. Watermark Stamp
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Strings.get("watermark", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = style.watermarkText,
                    onValueChange = { txt -> onStyleChange { it.copy(watermarkText = txt) } },
                    label = { Text(Strings.get("watermark_text", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (style.watermarkText.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Strings.get("watermark_opacity", language),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${(style.watermarkOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Slider(
                        value = style.watermarkOpacity,
                        onValueChange = { op -> onStyleChange { it.copy(watermarkOpacity = op) } },
                        valueRange = 0.1f..1.0f
                    )
                }
            }
        }
    }
}
