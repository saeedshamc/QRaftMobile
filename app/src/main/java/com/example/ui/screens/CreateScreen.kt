package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ContentType
import com.example.domain.model.CryptoCoin
import com.example.ui.components.QRPreviewCanvas
import com.example.ui.components.StyleControlPanel
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import com.example.ui.viewmodel.MainViewModel

@Composable
fun CreateScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val selectedType by viewModel.selectedContentType.collectAsState()
    val qrStyle by viewModel.qrStyle.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val payload by viewModel.encodedPayload.collectAsState()
    val customProfiles by viewModel.customProfiles.collectAsState()

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val contentTypes = listOf(
        ContentType.URL to Pair(Strings.get("type_url", language), Icons.Default.Link),
        ContentType.TEXT to Pair(Strings.get("type_text", language), Icons.Default.TextFields),
        ContentType.WHATSAPP to Pair(Strings.get("type_whatsapp", language), Icons.Default.Chat),
        ContentType.TELEGRAM to Pair(Strings.get("type_telegram", language), Icons.Default.Send),
        ContentType.INSTAGRAM to Pair(Strings.get("type_instagram", language), Icons.Default.CameraAlt),
        ContentType.EMAIL to Pair(Strings.get("type_email", language), Icons.Default.Email),
        ContentType.PHONE to Pair(Strings.get("type_phone", language), Icons.Default.Phone),
        ContentType.SMS to Pair(Strings.get("type_sms", language), Icons.Default.Message),
        ContentType.WIFI to Pair(Strings.get("type_wifi", language), Icons.Default.Wifi),
        ContentType.VCARD to Pair(Strings.get("type_vcard", language), Icons.Default.ContactPage),
        ContentType.CRYPTO to Pair(Strings.get("type_crypto", language), Icons.Default.AccountBalanceWallet),
        ContentType.PAYPAL to Pair(Strings.get("type_paypal", language), Icons.Default.Payment),
        ContentType.LOCATION to Pair(Strings.get("type_location", language), Icons.Default.LocationOn),
        ContentType.CALENDAR to Pair(Strings.get("type_calendar", language), Icons.Default.CalendarToday)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Content Type Horizontal Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            contentTypes.forEach { (type, pair) ->
                val (label, icon) = pair
                val isSelected = selectedType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setContentType(type) },
                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("type_${type.id}")
                )
            }
        }

        // Live QR Preview Canvas
        QRPreviewCanvas(
            bitmap = previewBitmap,
            payload = payload,
            style = qrStyle,
            language = language,
            onCopy = { viewModel.copyPayloadToClipboard(context) },
            onShare = { viewModel.shareGeneratedQR(context) },
            onExportPng = { viewModel.exportPng(context) },
            onExportJpg = { viewModel.exportJpg(context) },
            onExportSvg = { viewModel.exportSvg(context) },
            onExportPdf = { viewModel.exportPdf(context) },
            onAddToHistory = { viewModel.addToHistory(context) },
            onSavePreset = { showSavePresetDialog = true }
        )

        // Dynamic Form Fields Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${Strings.get("type_" + selectedType.id, language)} Content",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                when (selectedType) {
                    ContentType.URL -> UrlFormSection(viewModel, language)
                    ContentType.TEXT -> TextFormSection(viewModel, language)
                    ContentType.WHATSAPP -> WhatsAppFormSection(viewModel, language)
                    ContentType.TELEGRAM -> TelegramFormSection(viewModel, language)
                    ContentType.INSTAGRAM -> InstagramFormSection(viewModel, language)
                    ContentType.CRYPTO -> CryptoFormSection(viewModel, language)
                    ContentType.PAYPAL -> PayPalFormSection(viewModel, language)
                    ContentType.EMAIL -> EmailFormSection(viewModel, language)
                    ContentType.PHONE -> PhoneFormSection(viewModel, language)
                    ContentType.SMS -> SmsFormSection(viewModel, language)
                    ContentType.WIFI -> WifiFormSection(viewModel, language)
                    ContentType.VCARD -> VCardFormSection(viewModel, language)
                    ContentType.LOCATION -> LocationFormSection(viewModel, language)
                    ContentType.CALENDAR -> CalendarFormSection(viewModel, language)
                }
            }
        }

        // Style & Customization Panel
        StyleControlPanel(
            style = qrStyle,
            language = language,
            customProfiles = customProfiles,
            onStyleChange = { viewModel.updateStyle(it) },
            onApplyPalette = { viewModel.applyColorPalette(it) },
            onApplyTemplate = { viewModel.applyDesignTemplate(it, context) },
            onSaveProfile = { name -> viewModel.saveDesignProfile(name, context) },
            onApplyProfile = { profile -> viewModel.applyDesignProfile(profile, context) },
            onDeleteProfile = { id -> viewModel.deleteDesignProfile(id, context) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Save Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(Strings.get("save_as_preset", language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.get("enter_preset_name", language))
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        label = { Text(Strings.get("preset_name", language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            viewModel.savePreset(presetNameInput, context)
                            presetNameInput = ""
                            showSavePresetDialog = false
                        }
                    }
                ) {
                    Text(Strings.get("save", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text(Strings.get("cancel", language))
                }
            }
        )
    }
}

@Composable
private fun UrlFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.urlForm.collectAsState()
    OutlinedTextField(
        value = form.url,
        onValueChange = {
            viewModel.urlForm.value = form.copy(url = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("url_label", language)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("url_input"),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun TextFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.textForm.collectAsState()
    OutlinedTextField(
        value = form.text,
        onValueChange = {
            viewModel.textForm.value = form.copy(text = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("text_label", language)) },
        minLines = 3,
        maxLines = 6,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("text_input"),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun WhatsAppFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.whatsappForm.collectAsState()
    OutlinedTextField(
        value = form.phoneNumber,
        onValueChange = {
            viewModel.whatsappForm.value = form.copy(phoneNumber = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("whatsapp_phone", language)) },
        placeholder = { Text("+1234567890") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.message,
        onValueChange = {
            viewModel.whatsappForm.value = form.copy(message = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("whatsapp_message", language)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun TelegramFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.telegramForm.collectAsState()
    OutlinedTextField(
        value = form.username,
        onValueChange = {
            viewModel.telegramForm.value = form.copy(username = it.trim().removePrefix("@"))
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("telegram_username", language)) },
        placeholder = { Text("username (without @)") },
        leadingIcon = { Text("@", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun InstagramFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.instagramForm.collectAsState()
    OutlinedTextField(
        value = form.username,
        onValueChange = {
            viewModel.instagramForm.value = form.copy(username = it.trim().removePrefix("@"))
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("instagram_username", language)) },
        placeholder = { Text("username") },
        leadingIcon = { Text("@", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CryptoFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.cryptoForm.collectAsState()
    var coinExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = coinExpanded,
        onExpandedChange = { coinExpanded = !coinExpanded }
    ) {
        OutlinedTextField(
            value = "${form.coin.title} (${form.coin.code})",
            onValueChange = {},
            readOnly = true,
            label = { Text(Strings.get("crypto_coin", language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = coinExpanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = coinExpanded,
            onDismissRequest = { coinExpanded = false }
        ) {
            CryptoCoin.values().forEach { coin ->
                DropdownMenuItem(
                    text = { Text("${coin.title} (${coin.code})") },
                    onClick = {
                        viewModel.cryptoForm.value = form.copy(coin = coin)
                        viewModel.onFormChanged()
                        coinExpanded = false
                    }
                )
            }
        }
    }

    OutlinedTextField(
        value = form.address,
        onValueChange = {
            viewModel.cryptoForm.value = form.copy(address = it.trim())
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("crypto_address", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )

    OutlinedTextField(
        value = form.amount,
        onValueChange = {
            viewModel.cryptoForm.value = form.copy(amount = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("crypto_amount", language)) },
        placeholder = { Text("0.05") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun PayPalFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.paypalForm.collectAsState()
    OutlinedTextField(
        value = form.username,
        onValueChange = {
            viewModel.paypalForm.value = form.copy(username = it.trim())
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("paypal_username", language)) },
        placeholder = { Text("username") },
        leadingIcon = { Text("paypal.me/", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = form.amount,
            onValueChange = {
                viewModel.paypalForm.value = form.copy(amount = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("paypal_amount", language)) },
            placeholder = { Text("25") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.currency,
            onValueChange = {
                viewModel.paypalForm.value = form.copy(currency = it.uppercase())
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("paypal_currency", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun EmailFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.emailForm.collectAsState()
    OutlinedTextField(
        value = form.to,
        onValueChange = {
            viewModel.emailForm.value = form.copy(to = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("email_to", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.subject,
        onValueChange = {
            viewModel.emailForm.value = form.copy(subject = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("email_subject", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.body,
        onValueChange = {
            viewModel.emailForm.value = form.copy(body = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("email_body", language)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun PhoneFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.phoneForm.collectAsState()
    OutlinedTextField(
        value = form.phoneNumber,
        onValueChange = {
            viewModel.phoneForm.value = form.copy(phoneNumber = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("phone_number", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SmsFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.smsForm.collectAsState()
    OutlinedTextField(
        value = form.phoneNumber,
        onValueChange = {
            viewModel.smsForm.value = form.copy(phoneNumber = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("phone_number", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.message,
        onValueChange = {
            viewModel.smsForm.value = form.copy(message = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("sms_message", language)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.wifiForm.collectAsState()
    var encExpanded by remember { mutableStateOf(false) }
    val encTypes = listOf("WPA", "WEP", "nopass")

    OutlinedTextField(
        value = form.ssid,
        onValueChange = {
            viewModel.wifiForm.value = form.copy(ssid = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("wifi_ssid", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.password,
        onValueChange = {
            viewModel.wifiForm.value = form.copy(password = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("wifi_password", language)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )

    ExposedDropdownMenuBox(
        expanded = encExpanded,
        onExpandedChange = { encExpanded = !encExpanded }
    ) {
        OutlinedTextField(
            value = form.encryption,
            onValueChange = {},
            readOnly = true,
            label = { Text(Strings.get("wifi_encryption", language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encExpanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = encExpanded,
            onDismissRequest = { encExpanded = false }
        ) {
            encTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type) },
                    onClick = {
                        viewModel.wifiForm.value = form.copy(encryption = type)
                        viewModel.onFormChanged()
                        encExpanded = false
                    }
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(Strings.get("wifi_hidden", language))
        Switch(
            checked = form.hidden,
            onCheckedChange = {
                viewModel.wifiForm.value = form.copy(hidden = it)
                viewModel.onFormChanged()
            }
        )
    }
}

@Composable
private fun VCardFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.vcardForm.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = form.firstName,
            onValueChange = {
                viewModel.vcardForm.value = form.copy(firstName = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("vcard_fname", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.lastName,
            onValueChange = {
                viewModel.vcardForm.value = form.copy(lastName = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("vcard_lname", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
    }
    OutlinedTextField(
        value = form.organization,
        onValueChange = {
            viewModel.vcardForm.value = form.copy(organization = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("vcard_org", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.phone,
        onValueChange = {
            viewModel.vcardForm.value = form.copy(phone = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("vcard_phone", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.email,
        onValueChange = {
            viewModel.vcardForm.value = form.copy(email = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("vcard_email", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.website,
        onValueChange = {
            viewModel.vcardForm.value = form.copy(website = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("vcard_website", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun LocationFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.locationForm.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = form.latitude,
            onValueChange = {
                viewModel.locationForm.value = form.copy(latitude = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("loc_lat", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.longitude,
            onValueChange = {
                viewModel.locationForm.value = form.copy(longitude = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("loc_lng", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
    }
    OutlinedTextField(
        value = form.address,
        onValueChange = {
            viewModel.locationForm.value = form.copy(address = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("loc_addr", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CalendarFormSection(viewModel: MainViewModel, language: AppLanguage) {
    val form by viewModel.calendarForm.collectAsState()
    OutlinedTextField(
        value = form.title,
        onValueChange = {
            viewModel.calendarForm.value = form.copy(title = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("cal_title", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = form.startDateTime,
            onValueChange = {
                viewModel.calendarForm.value = form.copy(startDateTime = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("cal_start", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.endDateTime,
            onValueChange = {
                viewModel.calendarForm.value = form.copy(endDateTime = it)
                viewModel.onFormChanged()
            },
            label = { Text(Strings.get("cal_end", language)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )
    }
    OutlinedTextField(
        value = form.location,
        onValueChange = {
            viewModel.calendarForm.value = form.copy(location = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("cal_loc", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = form.description,
        onValueChange = {
            viewModel.calendarForm.value = form.copy(description = it)
            viewModel.onFormChanged()
        },
        label = { Text(Strings.get("cal_desc", language)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
