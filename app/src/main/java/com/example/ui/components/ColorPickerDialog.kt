package com.example.ui.components

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings

enum class ColorPickerTarget {
    FOREGROUND,
    BACKGROUND,
    GRADIENT_END
}

@Composable
fun QRColorPickerDialog(
    target: ColorPickerTarget,
    initialColorHex: Long,
    oppositeColorHex: Long? = null,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    val initialColor = Color(initialColorHex.toInt())
    var red by remember { mutableIntStateOf((initialColor.red * 255).toInt().coerceIn(0, 255)) }
    var green by remember { mutableIntStateOf((initialColor.green * 255).toInt().coerceIn(0, 255)) }
    var blue by remember { mutableIntStateOf((initialColor.blue * 255).toInt().coerceIn(0, 255)) }

    // HSV representation
    val hsv = remember {
        val array = FloatArray(3)
        android.graphics.Color.RGBToHSV(red, green, blue, array)
        array
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    var selectedModeTab by remember { mutableIntStateOf(0) } // 0 = HSV & Presets, 1 = RGB Channels

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        red = r.coerceIn(0, 255)
        green = g.coerceIn(0, 255)
        blue = b.coerceIn(0, 255)
        val array = FloatArray(3)
        android.graphics.Color.RGBToHSV(red, green, blue, array)
        hue = array[0]
        saturation = array[1]
        value = array[2]
    }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        saturation = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        red = (colorInt shr 16) and 0xFF
        green = (colorInt shr 8) and 0xFF
        blue = colorInt and 0xFF
    }

    val currentColorInt = android.graphics.Color.rgb(red, green, blue)
    val currentColorLong = (0xFF000000L or (currentColorInt.toLong() and 0xFFFFFFL))
    val hexString = String.format("#%02X%02X%02X", red, green, blue)
    var hexInputText by remember(hexString) { mutableStateOf(hexString) }

    val dialogTitle = when (target) {
        ColorPickerTarget.FOREGROUND -> Strings.get("pick_fg_color", language)
        ColorPickerTarget.BACKGROUND -> Strings.get("pick_bg_color", language)
        ColorPickerTarget.GRADIENT_END -> Strings.get("pick_gradient_color", language)
    }

    val popularSwatches = listOf(
        0xFF000000L, 0xFF1E293BL, 0xFF0F172AL, 0xFF1D4ED8L, 0xFF2563EBL,
        0xFF047857L, 0xFF10B981L, 0xFFBE123CL, 0xFFF43F5EL, 0xFF6D28D9L,
        0xFF8B5CF6L, 0xFF0D9488L, 0xFF06B6D4L, 0xFF78350FL, 0xFFB45309L,
        0xFF475569L, 0xFF64748BL, 0xFFF8FAFCL, 0xFFF1F5F9L, 0xFFFFFFFFL
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Color Preview Card with HEX string and Contrast Badge
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Large Color Swatch
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(currentColorLong.toInt()))
                                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Strings.get("hex_code", language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = hexString,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Contrast Scannability preview if opposite color is provided
                        if (oppositeColorHex != null) {
                            val fgLum = calculateLum(if (target == ColorPickerTarget.BACKGROUND) oppositeColorHex else currentColorLong)
                            val bgLum = calculateLum(if (target == ColorPickerTarget.BACKGROUND) currentColorLong else oppositeColorHex)
                            val ratio = if (fgLum > bgLum) (fgLum + 0.05) / (bgLum + 0.05) else (bgLum + 0.05) / (fgLum + 0.05)

                            val isContrastGood = ratio >= 2.5
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isContrastGood) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFEF4444).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = if (ratio >= 4.5) "100% OK" else if (ratio >= 2.5) "Good" else "Low Contrast",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isContrastGood) Color(0xFF059669) else Color(0xFFDC2626),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Palette Swatches
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = Strings.get("color_palettes", language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        popularSwatches.forEach { swatchHex ->
                            val isSelected = (currentColorLong and 0xFFFFFFL) == (swatchHex and 0xFFFFFFL)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatchHex.toInt()))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val c = Color(swatchHex.toInt())
                                        updateFromRgb(
                                            (c.red * 255).toInt(),
                                            (c.green * 255).toInt(),
                                            (c.blue * 255).toInt()
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (Color(swatchHex.toInt()).red < 0.5f) Color.White else Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Mode Tabs: Hue/Saturation vs RGB Channels
                TabRow(
                    selectedTabIndex = selectedModeTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedModeTab == 0,
                        onClick = { selectedModeTab = 0 },
                        text = { Text("HSV Wheel", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedModeTab == 1,
                        onClick = { selectedModeTab = 1 },
                        text = { Text("RGB Sliders", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                if (selectedModeTab == 0) {
                    // HSV Mode Sliders
                    // 1. Hue Slider (Rainbow Gradient)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = Strings.get("hue", language), style = MaterialTheme.typography.labelSmall)
                            Text(text = "${hue.toInt()}°", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Red, Color.Yellow, Color.Green,
                                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                        )
                                    )
                                )
                        )
                        Slider(
                            value = hue,
                            onValueChange = { updateFromHsv(it, saturation, value) },
                            valueRange = 0f..360f,
                            modifier = Modifier.testTag("hue_slider")
                        )
                    }

                    // 2. Saturation Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = Strings.get("saturation", language), style = MaterialTheme.typography.labelSmall)
                            Text(text = "${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = saturation,
                            onValueChange = { updateFromHsv(hue, it, value) },
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("saturation_slider")
                        )
                    }

                    // 3. Brightness / Value Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = Strings.get("brightness", language), style = MaterialTheme.typography.labelSmall)
                            Text(text = "${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = value,
                            onValueChange = { updateFromHsv(hue, saturation, it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("brightness_slider")
                        )
                    }
                } else {
                    // RGB Mode Sliders
                    // Red Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${Strings.get("red", language)} (R)", style = MaterialTheme.typography.labelSmall)
                            Text(text = "$red", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = red.toFloat(),
                            onValueChange = { updateFromRgb(it.toInt(), green, blue) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFEF4444),
                                activeTrackColor = Color(0xFFEF4444)
                            ),
                            modifier = Modifier.testTag("red_slider")
                        )
                    }

                    // Green Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${Strings.get("green", language)} (G)", style = MaterialTheme.typography.labelSmall)
                            Text(text = "$green", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = green.toFloat(),
                            onValueChange = { updateFromRgb(red, it.toInt(), blue) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.testTag("green_slider")
                        )
                    }

                    // Blue Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${Strings.get("blue", language)} (B)", style = MaterialTheme.typography.labelSmall)
                            Text(text = "$blue", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = blue.toFloat(),
                            onValueChange = { updateFromRgb(red, green, it.toInt()) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3B82F6),
                                activeTrackColor = Color(0xFF3B82F6)
                            ),
                            modifier = Modifier.testTag("blue_slider")
                        )
                    }
                }

                // Direct Hex Input TextField
                OutlinedTextField(
                    value = hexInputText,
                    onValueChange = { input ->
                        hexInputText = input
                        val cleaned = input.removePrefix("#").trim()
                        if (cleaned.length == 6) {
                            try {
                                val parsed = cleaned.toLong(16)
                                val r = ((parsed shr 16) and 0xFF).toInt()
                                val g = ((parsed shr 8) and 0xFF).toInt()
                                val b = (parsed and 0xFF).toInt()
                                updateFromRgb(r, g, b)
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text(Strings.get("hex_code", language)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hex_color_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(currentColorLong)
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_color_button")
            ) {
                Text(Strings.get("select_color", language), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("cancel_color_button")
            ) {
                Text(Strings.get("cancel", language))
            }
        }
    )
}

private fun calculateLum(colorHex: Long): Double {
    val r = ((colorHex shr 16) and 0xFF) / 255.0
    val g = ((colorHex shr 8) and 0xFF) / 255.0
    val b = (colorHex and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
