package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ColorPalettePreset
import com.example.domain.model.ColorPalettes
import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.EyeFrameStyle
import com.example.domain.model.EyeInnerStyle
import com.example.domain.model.GradientType
import com.example.domain.model.QRDesignProfile
import com.example.domain.model.QRDesignTemplate
import com.example.domain.model.QRDesignTemplates
import com.example.domain.model.QRStyle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.AutoAwesome

@Composable
fun StyleControlPanel(
    style: QRStyle,
    language: AppLanguage,
    customProfiles: List<QRDesignProfile> = emptyList(),
    onStyleChange: ((QRStyle) -> QRStyle) -> Unit,
    onApplyPalette: (ColorPalettePreset) -> Unit,
    onApplyTemplate: (QRDesignTemplate) -> Unit = {},
    onSaveProfile: (String) -> Unit = {},
    onApplyProfile: (QRDesignProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileNameInput by remember { mutableStateOf("") }

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

    // Calculate contrast ratio between FG and BG for scannability
    val fgLum = calculateLuminance(style.fgColor)
    val bgLum = if (style.transparentBg) 1.0 else calculateLuminance(style.bgColor)
    val contrastRatio = (Math.max(fgLum, bgLum) + 0.05) / (Math.min(fgLum, bgLum) + 0.05)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Strings.get("style_heading", language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Scannability badge
                val (badgeColor, badgeText) = when {
                    contrastRatio >= 4.5 -> Pair(Color(0xFF10B981), Strings.get("contrast_excellent", language))
                    contrastRatio >= 2.8 -> Pair(Color(0xFFF59E0B), Strings.get("contrast_good", language))
                    else -> Pair(Color(0xFFEF4444), Strings.get("contrast_low", language))
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                        Text(text = badgeText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = badgeColor)
                    }
                }
            }

            // --- SAVED QR DESIGN PROFILES (THEME ENGINE) ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = Strings.get("design_profiles_title", language),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledTonalButton(
                        onClick = { showSaveProfileDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("save_design_profile_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Strings.get("save_current_style", language),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (customProfiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        customProfiles.forEach { profile ->
                            Card(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable { onApplyProfile(profile) }
                                    .testTag("profile_${profile.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Visual color strip
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(profile.bgColor.toInt())),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.fgColor.toInt()))
                                        )
                                        if (profile.gradientMode) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(profile.gradientEndColor.toInt()))
                                            )
                                        }
                                    }

                                    val pName = if (language == AppLanguage.FA) profile.nameFa else profile.nameEn
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = { onDeleteProfile(profile.id) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${profile.dotStyle.name} • EC-${profile.errorCorrection.name}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 0. Predefined Design Templates Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Strings.get("design_templates", language),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Strings.get("quick_styles", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QRDesignTemplates.templates.forEach { template ->
                        val isSelected = style.activePaletteId == template.id
                        val name = if (language == AppLanguage.FA) template.nameFa else template.nameEn
                        val desc = if (language == AppLanguage.FA) template.descriptionFa else template.descriptionEn

                        Card(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onApplyTemplate(template) }
                                .testTag("template_${template.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            ) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Miniature QR pattern representation preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(template.bgColor.toInt()))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Simulated stylized QR finder eyes and modules
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(
                                                    when (template.eyeFrameStyle) {
                                                        EyeFrameStyle.CIRCLE -> CircleShape
                                                        EyeFrameStyle.ROUNDED -> RoundedCornerShape(4.dp)
                                                        EyeFrameStyle.LEAF -> RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                                                        EyeFrameStyle.SQUARE -> RoundedCornerShape(0.dp)
                                                    }
                                                )
                                                .background(
                                                    if (template.gradientMode) Brush.linearGradient(
                                                        listOf(
                                                            Color(template.fgColor.toInt()),
                                                            Color(template.gradientEndColor.toInt())
                                                        )
                                                    ) else Brush.linearGradient(
                                                        listOf(
                                                            Color(template.fgColor.toInt()),
                                                            Color(template.fgColor.toInt())
                                                        )
                                                    )
                                                )
                                                .padding(3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(template.bgColor.toInt()))
                                                    .padding(2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(
                                                            when (template.eyeInnerStyle) {
                                                                EyeInnerStyle.DOT -> CircleShape
                                                                EyeInnerStyle.ROUNDED -> RoundedCornerShape(2.dp)
                                                                EyeInnerStyle.DIAMOND -> RoundedCornerShape(1.dp)
                                                                EyeInnerStyle.SQUARE -> RoundedCornerShape(0.dp)
                                                            }
                                                        )
                                                        .background(Color(template.fgColor.toInt()))
                                                )
                                            }
                                        }

                                        // Central dots
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(
                                                        if (template.dotStyle == DotStyle.ROUNDED || template.dotStyle == DotStyle.DOTS) CircleShape
                                                        else RoundedCornerShape(1.dp)
                                                    )
                                                    .background(Color(template.fgColor.toInt()))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(
                                                        if (template.dotStyle == DotStyle.ROUNDED || template.dotStyle == DotStyle.DOTS) CircleShape
                                                        else RoundedCornerShape(1.dp)
                                                    )
                                                    .background(Color(template.gradientEndColor.toInt()))
                                            )
                                        }

                                        // Top right eye
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(
                                                    when (template.eyeFrameStyle) {
                                                        EyeFrameStyle.CIRCLE -> CircleShape
                                                        EyeFrameStyle.ROUNDED -> RoundedCornerShape(4.dp)
                                                        EyeFrameStyle.LEAF -> RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                                                        EyeFrameStyle.SQUARE -> RoundedCornerShape(0.dp)
                                                    }
                                                )
                                                .background(
                                                    if (template.gradientMode) Brush.linearGradient(
                                                        listOf(
                                                            Color(template.fgColor.toInt()),
                                                            Color(template.gradientEndColor.toInt())
                                                        )
                                                    ) else Brush.linearGradient(
                                                        listOf(
                                                            Color(template.fgColor.toInt()),
                                                            Color(template.fgColor.toInt())
                                                        )
                                                    )
                                                )
                                                .padding(3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(template.bgColor.toInt()))
                                                    .padding(2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(
                                                            when (template.eyeInnerStyle) {
                                                                EyeInnerStyle.DOT -> CircleShape
                                                                EyeInnerStyle.ROUNDED -> RoundedCornerShape(2.dp)
                                                                EyeInnerStyle.DIAMOND -> RoundedCornerShape(1.dp)
                                                                EyeInnerStyle.SQUARE -> RoundedCornerShape(0.dp)
                                                            }
                                                        )
                                                        .background(Color(template.fgColor.toInt()))
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = template.badge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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

            // 3. Dot Pattern Style (Square, Rounded, Dots, Classy Diamond)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Strings.get("dot_style", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        DotStyle.SQUARE to Strings.get("dot_square", language),
                        DotStyle.ROUNDED to Strings.get("dot_rounded", language),
                        DotStyle.DOTS to Strings.get("dot_dots", language),
                        DotStyle.CLASSY to Strings.get("dot_classy", language)
                    ).forEach { (dot, label) ->
                        val isSelected = style.dotStyle == dot
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange { it.copy(dotStyle = dot) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // 4. Finder Eye Frame Style (Square, Rounded, Circle, Leaf)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Strings.get("eye_frame_style", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        EyeFrameStyle.SQUARE to Strings.get("eye_frame_square", language),
                        EyeFrameStyle.ROUNDED to Strings.get("eye_frame_rounded", language),
                        EyeFrameStyle.CIRCLE to Strings.get("eye_frame_circle", language),
                        EyeFrameStyle.LEAF to Strings.get("eye_frame_leaf", language)
                    ).forEach { (frameStyle, label) ->
                        val isSelected = style.eyeFrameStyle == frameStyle
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange { it.copy(eyeFrameStyle = frameStyle) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // 5. Eye Center Dot Style (Square, Rounded, Dot, Diamond)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Strings.get("eye_inner_style", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        EyeInnerStyle.SQUARE to Strings.get("eye_inner_square", language),
                        EyeInnerStyle.ROUNDED to Strings.get("eye_inner_rounded", language),
                        EyeInnerStyle.DOT to Strings.get("eye_inner_dot", language),
                        EyeInnerStyle.DIAMOND to Strings.get("eye_inner_diamond", language)
                    ).forEach { (innerStyle, label) ->
                        val isSelected = style.eyeInnerStyle == innerStyle
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange { it.copy(eyeInnerStyle = innerStyle) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // 6. Gradient Mode & Directions
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

                if (style.gradientMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            GradientType.DIAGONAL to Strings.get("grad_diag", language),
                            GradientType.HORIZONTAL to Strings.get("grad_horiz", language),
                            GradientType.VERTICAL to Strings.get("grad_vert", language),
                            GradientType.RADIAL to Strings.get("grad_radial", language)
                        ).forEach { (gType, label) ->
                            val isSelected = style.gradientType == gType
                            FilterChip(
                                selected = isSelected,
                                onClick = { onStyleChange { it.copy(gradientType = gType) } },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                )
                            )
                        }
                    }
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

            // 8. Brand Preset Logos & Center Logo Overlay
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = Strings.get("preset_brand_logos", language),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Horizontal Preset Logos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        Triple("preset:qraft_logo", "QRaft", R.drawable.ic_qraft_logo),
                        Triple("preset:whatsapp", "WhatsApp", R.drawable.ic_brand_whatsapp),
                        Triple("preset:telegram", "Telegram", R.drawable.ic_brand_telegram),
                        Triple("preset:instagram", "Instagram", R.drawable.ic_brand_instagram),
                        Triple("preset:youtube", "YouTube", R.drawable.ic_brand_youtube),
                        Triple("preset:crypto", "Crypto", R.drawable.ic_brand_crypto),
                        Triple("preset:wifi", "Wi-Fi", R.drawable.ic_brand_wifi)
                    )

                    presets.forEach { (presetUri, label, iconRes) ->
                        val isSelected = style.logoUri == presetUri
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onStyleChange { it.copy(logoUri = null) }
                                } else {
                                    onStyleChange { it.copy(logoUri = presetUri, errorCorrection = ErrorCorrection.H) }
                                }
                            },
                            leadingIcon = {
                                Image(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                // Custom Logo File Picker & Remove
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isCustom = style.logoUri != null && !style.logoUri.startsWith("preset:")
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
                            text = if (isCustom) "Change Image" else Strings.get("choose_logo", language),
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

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text(Strings.get("save_design_profile_title", language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.get("enter_profile_name", language))
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { profileNameInput = it },
                        label = { Text(Strings.get("profile_name", language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (profileNameInput.isNotBlank()) {
                            onSaveProfile(profileNameInput)
                            profileNameInput = ""
                            showSaveProfileDialog = false
                        }
                    }
                ) {
                    Text(Strings.get("save", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text(Strings.get("cancel", language))
                }
            }
        )
    }
}

private fun calculateLuminance(colorLong: Long): Double {
    val r = ((colorLong shr 16) and 0xFF) / 255.0
    val g = ((colorLong shr 8) and 0xFF) / 255.0
    val b = (colorLong and 0xFF) / 255.0

    fun channel(c: Double): Double {
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}
