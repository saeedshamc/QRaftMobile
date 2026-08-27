package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BatchSessionEntity
import com.example.data.local.entity.QRHistoryEntity
import com.example.data.local.entity.QRPresetEntity
import com.example.data.local.repository.QRRepository
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.UserPreferences
import com.example.domain.engine.AnimatedEncodeResult
import com.example.domain.engine.AnimatedQREngine
import com.example.domain.engine.AnimationCleanupWorker
import com.example.domain.engine.CsvBatchParser
import com.example.domain.engine.CsvBatchRow
import com.example.domain.engine.CsvHistoryExporter
import com.example.domain.engine.DecodeSessionState
import com.example.domain.engine.GifFrameExtractor
import com.example.domain.engine.QRGeneratorEngine
import com.example.domain.engine.QRPdfExporter
import com.example.domain.engine.QRSvgExporter
import com.example.domain.engine.ReliabilityPreset
import com.example.domain.model.CalendarContent
import com.example.domain.model.ColorPalettePreset
import com.example.domain.model.ColorPalettes
import com.example.domain.model.ContentEncoder
import com.example.domain.model.ContentType
import com.example.domain.model.CryptoCoin
import com.example.domain.model.CryptoContent
import com.example.domain.model.DotStyle
import com.example.domain.model.EmailContent
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.EyeFrameStyle
import com.example.domain.model.EyeInnerStyle
import com.example.domain.model.GradientType
import com.example.domain.model.InstagramContent
import com.example.domain.model.LocationContent
import com.example.domain.model.PayPalContent
import com.example.domain.model.PhoneContent
import com.example.domain.model.QRDesignProfile
import com.example.domain.model.QRDesignTemplate
import com.example.domain.model.QRDesignTemplates
import com.example.domain.model.QRStyle
import com.example.domain.model.SmsContent
import com.example.domain.model.TelegramContent
import com.example.domain.model.TextContent
import com.example.domain.model.UrlContent
import com.example.domain.model.VCardContent
import com.example.domain.model.WhatsAppContent
import com.example.domain.model.WifiContent
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab {
    CREATE,
    SCAN,
    BATCH,
    HISTORY,
    ANIMATED_QR,
    ANALYTICS
}

enum class AnimatedMode {
    ENCODE,
    DECODE
}

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val userPrefs = UserPreferences(application)
    private val repository = QRRepository(AppDatabase.getDatabase(application).qrDao())

    val isSettingsOpen = MutableStateFlow(false)

    // Language & Theme State
    private val _language = MutableStateFlow(userPrefs.getLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(userPrefs.getThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // Navigation State
    private val _currentTab = MutableStateFlow(AppTab.CREATE)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Create Screen State
    private val _selectedContentType = MutableStateFlow(
        try { ContentType.valueOf(userPrefs.getLastContentType()) } catch (e: Exception) { ContentType.URL }
    )
    val selectedContentType: StateFlow<ContentType> = _selectedContentType.asStateFlow()

    // Form inputs
    val urlForm = MutableStateFlow(UrlContent())
    val textForm = MutableStateFlow(TextContent())
    val emailForm = MutableStateFlow(EmailContent())
    val phoneForm = MutableStateFlow(PhoneContent())
    val smsForm = MutableStateFlow(SmsContent())
    val wifiForm = MutableStateFlow(WifiContent())
    val vcardForm = MutableStateFlow(VCardContent())
    val locationForm = MutableStateFlow(LocationContent())
    val calendarForm = MutableStateFlow(CalendarContent())
    val whatsappForm = MutableStateFlow(WhatsAppContent())
    val telegramForm = MutableStateFlow(TelegramContent())
    val instagramForm = MutableStateFlow(InstagramContent())
    val cryptoForm = MutableStateFlow(CryptoContent())
    val paypalForm = MutableStateFlow(PayPalContent())

    // QR Style State
    private val _qrStyle = MutableStateFlow(QRStyle())
    val qrStyle: StateFlow<QRStyle> = _qrStyle.asStateFlow()

    // Live Generated QR Bitmap
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _encodedPayload = MutableStateFlow("")
    val encodedPayload: StateFlow<String> = _encodedPayload.asStateFlow()

    private var debounceJob: Job? = null

    // Room Flows
    val historyList: StateFlow<List<QRHistoryEntity>> = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val presetsList: StateFlow<List<QRPresetEntity>> = repository.allPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentBatchSessions: StateFlow<List<BatchSessionEntity>> = repository.recentBatches.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Batch Generation State
    val batchCsvText = MutableStateFlow(CsvBatchParser.SAMPLE_CSV)
    private val _batchParsedRows = MutableStateFlow<List<CsvBatchRow>>(emptyList())
    val batchParsedRows: StateFlow<List<CsvBatchRow>> = _batchParsedRows.asStateFlow()

    val batchDelayMs = MutableStateFlow(50L)
    private val _batchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchProgress: StateFlow<Pair<Int, Int>?> = _batchProgress.asStateFlow()

    private val _lastBatchZipFile = MutableStateFlow<File?>(null)
    val lastBatchZipFile: StateFlow<File?> = _lastBatchZipFile.asStateFlow()

    // Scanner State
    val scanTorchEnabled = MutableStateFlow(false)
    private val _scannedResultText = MutableStateFlow<String?>(null)
    val scannedResultText: StateFlow<String?> = _scannedResultText.asStateFlow()

    // Animated QR State
    val animatedMode = MutableStateFlow(AnimatedMode.ENCODE)
    val animSourceUri = MutableStateFlow<Uri?>(null)
    val animMaxDim = MutableStateFlow(220)
    val animQuality = MutableStateFlow(0.55f)
    val animReliabilityPreset = MutableStateFlow(ReliabilityPreset.BALANCED)
    val animFrameDelayMs = MutableStateFlow(200)
    val animFps = MutableStateFlow(5)
    val animIsPlaying = MutableStateFlow(true)
    val animLoop = MutableStateFlow(true)
    val animCurrentFrameIndex = MutableStateFlow(0)
    private val _animEncodeResult = MutableStateFlow<AnimatedEncodeResult?>(null)
    val animEncodeResult: StateFlow<AnimatedEncodeResult?> = _animEncodeResult.asStateFlow()

    // Animated GIF Export Progress & Preview State
    val isGifExporting = MutableStateFlow(false)
    val gifExportProgress = MutableStateFlow(0f)
    val gifExportCurrentFrame = MutableStateFlow(0)
    val gifExportTotalFrames = MutableStateFlow(0)
    val showSequencePreviewDialog = MutableStateFlow(false)
    private var gifExportJob: Job? = null

    // Animated QR Decode State
    private val _animDecodeState = MutableStateFlow(DecodeSessionState())
    val animDecodeState: StateFlow<DecodeSessionState> = _animDecodeState.asStateFlow()

    // Local Analytics State
    private val _sessionGeneratedCount = MutableStateFlow(0)
    val sessionGeneratedCount: StateFlow<Int> = _sessionGeneratedCount.asStateFlow()

    private val _totalGeneratedCount = MutableStateFlow(userPrefs.getTotalGeneratedCount())
    val totalGeneratedCount: StateFlow<Int> = _totalGeneratedCount.asStateFlow()

    private val _typeStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val typeStats: StateFlow<Map<String, Int>> = _typeStats.asStateFlow()

    // Diagnostics & Storage Maintenance State
    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _memoryAllocatedMb = MutableStateFlow(0L)
    val memoryAllocatedMb: StateFlow<Long> = _memoryAllocatedMb.asStateFlow()

    private val _memoryMaxMb = MutableStateFlow(0L)
    val memoryMaxMb: StateFlow<Long> = _memoryMaxMb.asStateFlow()

    // QR Design Profiles State
    private val _customDesignProfiles = MutableStateFlow<List<QRDesignProfile>>(emptyList())
    val customDesignProfiles: StateFlow<List<QRDesignProfile>> = _customDesignProfiles.asStateFlow()
    val customProfiles: StateFlow<List<QRDesignProfile>> = _customDesignProfiles.asStateFlow()

    // Google Drive Cloud Sync State
    private val _lastCloudSyncTime = MutableStateFlow(userPrefs.getLastCloudSyncTime())
    val lastCloudSyncTime: StateFlow<Long> = _lastCloudSyncTime.asStateFlow()

    init {
        updateStatsMap()
        parseBatchCsv()
        loadCustomDesignProfiles()
        refreshDiagnostics(application)
        triggerQRGeneration(instant = true)
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        userPrefs.setLanguage(lang)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        userPrefs.setThemeMode(mode)
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setContentType(type: ContentType) {
        _selectedContentType.value = type
        userPrefs.setLastContentType(type.name)
        triggerQRGeneration()
    }

    fun updateStyle(transform: (QRStyle) -> QRStyle) {
        _qrStyle.value = transform(_qrStyle.value)
        triggerQRGeneration(instant = true)
    }

    fun applyColorPalette(palette: ColorPalettePreset) {
        _qrStyle.value = _qrStyle.value.copy(
            fgColor = palette.fgColor,
            gradientEndColor = palette.gradientEndColor,
            bgColor = palette.bgColor,
            gradientMode = true,
            activePaletteId = palette.id
        )
        triggerQRGeneration(instant = true)
    }

    fun applyDesignTemplate(template: QRDesignTemplate, context: Context) {
        _qrStyle.value = template.applyTo(_qrStyle.value)
        triggerQRGeneration(instant = true)
        val templateName = if (_language.value == AppLanguage.FA) template.nameFa else template.nameEn
        showToast(context, String.format(Strings.get("template_applied", _language.value), templateName))
    }

    fun onFormChanged() {
        triggerQRGeneration(instant = false)
    }

    private fun triggerQRGeneration(instant: Boolean = false) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch(Dispatchers.Default) {
            if (!instant) {
                delay(120)
            }
            val payload = ContentEncoder.encode(
                type = _selectedContentType.value,
                url = urlForm.value,
                text = textForm.value,
                email = emailForm.value,
                phone = phoneForm.value,
                sms = smsForm.value,
                wifi = wifiForm.value,
                vcard = vcardForm.value,
                location = locationForm.value,
                calendar = calendarForm.value,
                whatsapp = whatsappForm.value,
                telegram = telegramForm.value,
                instagram = instagramForm.value,
                crypto = cryptoForm.value,
                paypal = paypalForm.value
            )
            _encodedPayload.value = payload
            val bitmap = QRGeneratorEngine.generateQRBitmap(payload, _qrStyle.value, getApplication())
            _previewBitmap.value = bitmap
        }
    }

    fun getScannabilityInfo(): Pair<String, String> {
        val style = _qrStyle.value
        val fg = style.fgColor
        val bg = if (style.transparentBg) 0xFFFFFFFF else style.bgColor

        val fgR = ((fg shr 16) and 0xFF) / 255.0
        val fgG = ((fg shr 8) and 0xFF) / 255.0
        val fgB = (fg and 0xFF) / 255.0
        val bgR = ((bg shr 16) and 0xFF) / 255.0
        val bgG = ((bg shr 8) and 0xFF) / 255.0
        val bgB = (bg and 0xFF) / 255.0

        val lumFg = 0.2126 * fgR + 0.7152 * fgG + 0.0722 * fgB
        val lumBg = 0.2126 * bgR + 0.7152 * bgG + 0.0722 * bgB

        val ratio = if (lumFg > lumBg) (lumFg + 0.05) / (lumBg + 0.05) else (lumBg + 0.05) / (lumFg + 0.05)
        return when {
            ratio >= 4.5 -> "contrast_excellent" to "100%"
            ratio >= 2.2 -> "contrast_good" to "85%"
            else -> "contrast_warning" to "45%"
        }
    }

    fun recordGeneration() {
        _sessionGeneratedCount.value += 1
        val newTotal = userPrefs.incrementGeneratedCount()
        _totalGeneratedCount.value = newTotal
        userPrefs.incrementCountForType(_selectedContentType.value.name)
        updateStatsMap()
    }

    private fun updateStatsMap() {
        val map = mutableMapOf<String, Int>()
        for (type in ContentType.values()) {
            val count = userPrefs.getCountForType(type.name)
            if (count > 0) {
                map[type.name] = count
            }
        }
        _typeStats.value = map
    }

    fun copyPayloadToClipboard(context: Context) {
        val payload = _encodedPayload.value
        if (payload.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QRaft Content", payload))
        showToast(context, Strings.get("copied", _language.value))
    }

    fun shareGeneratedQR(context: Context) {
        val bitmap = _previewBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val file = saveBitmapToCache(context, bitmap, "qraft_share_${System.currentTimeMillis()}.png")
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, _encodedPayload.value)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Share QR Code")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                recordGeneration()
            }
        }
    }

    fun exportPng(context: Context) {
        val payload = _encodedPayload.value
        if (payload.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val exportBmp = QRGeneratorEngine.generateQRBitmap(
                content = payload,
                style = _qrStyle.value,
                context = context,
                forcedSize = _qrStyle.value.exportResolution
            ) ?: _previewBitmap.value ?: return@launch

            val file = saveBitmapToCache(context, exportBmp, "qraft_${System.currentTimeMillis()}.png", Bitmap.CompressFormat.PNG)
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Save or Export PNG")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                recordGeneration()
            }
        }
    }

    fun exportJpg(context: Context) {
        val payload = _encodedPayload.value
        if (payload.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val exportBmp = QRGeneratorEngine.generateQRBitmap(
                content = payload,
                style = _qrStyle.value,
                context = context,
                forcedSize = _qrStyle.value.exportResolution
            ) ?: _previewBitmap.value ?: return@launch

            val file = saveBitmapToCache(context, exportBmp, "qraft_${System.currentTimeMillis()}.jpg", Bitmap.CompressFormat.JPEG)
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Save or Export JPG")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                recordGeneration()
            }
        }
    }

    fun exportSvg(context: Context) {
        val payload = _encodedPayload.value
        if (payload.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val svgStr = QRSvgExporter.generateSvgString(payload, _qrStyle.value)
            val file = File(context.cacheDir, "qraft_${System.currentTimeMillis()}.svg")
            file.writeText(svgStr)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/svg+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Export SVG Vector")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            recordGeneration()
        }
    }

    fun exportPdf(context: Context) {
        val bitmap = _previewBitmap.value ?: return
        val payload = _encodedPayload.value
        viewModelScope.launch(Dispatchers.IO) {
            val pdfFile = QRPdfExporter.exportToPdfFile(
                context,
                bitmap,
                _selectedContentType.value.name,
                payload
            )
            if (pdfFile != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Export PDF Document")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                recordGeneration()
            }
        }
    }

    fun addToHistory(context: Context, notes: String = "") {
        val payload = _encodedPayload.value
        if (payload.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val styleJson = serializeStyleToJson(_qrStyle.value)
            val label = when (_selectedContentType.value) {
                ContentType.URL -> urlForm.value.url.take(30)
                ContentType.TEXT -> textForm.value.text.take(30)
                ContentType.WIFI -> "WiFi: ${wifiForm.value.ssid}"
                ContentType.VCARD -> "${vcardForm.value.firstName} ${vcardForm.value.lastName}"
                ContentType.EMAIL -> "Email: ${emailForm.value.to}"
                ContentType.PHONE -> "Phone: ${phoneForm.value.phoneNumber}"
                ContentType.SMS -> "SMS: ${smsForm.value.phoneNumber}"
                ContentType.LOCATION -> "Loc: ${locationForm.value.address}"
                ContentType.CALENDAR -> "Event: ${calendarForm.value.title}"
                ContentType.WHATSAPP -> "WA: ${whatsappForm.value.phoneNumber}"
                ContentType.TELEGRAM -> "TG: @${telegramForm.value.username}"
                ContentType.INSTAGRAM -> "IG: @${instagramForm.value.username}"
                ContentType.CRYPTO -> "${cryptoForm.value.coin.code}: ${cryptoForm.value.address.take(12)}"
                ContentType.PAYPAL -> "PayPal: ${paypalForm.value.username}"
            }
            repository.addHistory(
                QRHistoryEntity(
                    content = payload,
                    contentType = _selectedContentType.value.name,
                    styleJson = styleJson,
                    label = label,
                    notes = notes
                )
            )
            recordGeneration()
            withContext(Dispatchers.Main) {
                showToast(context, Strings.get("saved_to_gallery", _language.value))
            }
        }
    }

    fun savePreset(name: String, context: Context) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val json = serializeStyleToJson(_qrStyle.value)
            repository.addPreset(
                QRPresetEntity(
                    name = name.trim(),
                    styleJson = json
                )
            )
            withContext(Dispatchers.Main) {
                showToast(context, Strings.get("preset_applied", _language.value))
            }
        }
    }

    fun applyPreset(preset: QRPresetEntity, context: Context) {
        val style = deserializeStyleFromJson(preset.styleJson)
        if (style != null) {
            _qrStyle.value = style
            triggerQRGeneration()
            showToast(context, Strings.get("preset_applied", _language.value))
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deletePreset(id) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteHistory(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    fun clearAllPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearPresets()
        }
    }

    /**
     * Exports all local history items to a formatted JSON backup file
     * and opens an intent to save/share to Google Drive, Dropbox, Local Storage, etc.
     */
    fun exportHistoryToJson(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = historyList.value
            val root = JSONObject()
            root.put("appName", "QRaft Studio")
            root.put("schemaVersion", 1)
            root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            root.put("totalRecords", items.size)

            val array = JSONArray()
            for (item in items) {
                val itemObj = JSONObject().apply {
                    put("id", item.id)
                    put("content", item.content)
                    put("contentType", item.contentType)
                    put("label", item.label)
                    put("notes", item.notes)
                    put("styleJson", item.styleJson)
                    put("timestamp", item.timestamp)
                }
                array.put(itemObj)
            }
            root.put("history", array)

            val timestamp = System.currentTimeMillis()
            val backupFile = File(context.cacheDir, "qraft_history_backup_${timestamp}.json")
            backupFile.writeText(root.toString(2), Charsets.UTF_8)

            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "QRaft History Backup (JSON)")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, Strings.get("export_json_backup", _language.value))
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    showToast(context, Strings.get("backup_export_success", _language.value))
                } catch (e: Exception) {
                    showToast(context, "Export error: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Imports history items from a user-selected JSON backup file.
     */
    fun importHistoryFromJson(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("Cannot open file stream")

                val root = JSONObject(jsonString)
                val historyArray = root.optJSONArray("history") ?: throw IllegalArgumentException("Missing 'history' JSON array")

                var importedCount = 0
                for (i in 0 until historyArray.length()) {
                    val obj = historyArray.getJSONObject(i)
                    val content = obj.optString("content")
                    if (content.isNotBlank()) {
                        val contentType = obj.optString("contentType", "TEXT")
                        val label = obj.optString("label", "Restored QR")
                        val notes = obj.optString("notes", "Imported from JSON backup")
                        val styleJson = obj.optString("styleJson", "{}")
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

                        repository.addHistory(
                            QRHistoryEntity(
                                content = content,
                                contentType = contentType,
                                label = label,
                                notes = notes,
                                styleJson = styleJson,
                                timestamp = timestamp
                            )
                        )
                        importedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    showToast(
                        context,
                        String.format(Strings.get("backup_import_success", _language.value), importedCount)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast(context, "${Strings.get("backup_import_error", _language.value)}: ${e.localizedMessage}")
                }
            }
        }
    }

    fun clearAllLocalData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            repository.clearPresets()
            repository.clearBatchSessions()
            userPrefs.clearAllStats()
            withContext(Dispatchers.Main) {
                updateStatsMap()
                _totalGeneratedCount.value = 0
                _sessionGeneratedCount.value = 0
                showToast(context, Strings.get("clear_data", _language.value))
            }
        }
    }

    fun restoreHistoryItem(item: QRHistoryEntity) {
        val type = try { ContentType.valueOf(item.contentType) } catch (e: Exception) { ContentType.TEXT }
        _selectedContentType.value = type
        if (type == ContentType.TEXT) {
            textForm.value = TextContent(text = item.content)
        } else if (type == ContentType.URL) {
            urlForm.value = UrlContent(url = item.content)
        }
        val restoredStyle = deserializeStyleFromJson(item.styleJson)
        if (restoredStyle != null) {
            _qrStyle.value = restoredStyle
        }
        _currentTab.value = AppTab.CREATE
        triggerQRGeneration()
    }

    fun restoreHistoryItemToCreator(item: QRHistoryEntity) = restoreHistoryItem(item)

    // ==========================================
    // DIAGNOSTICS & STORAGE MAINTENANCE ENGINE
    // ==========================================
    fun refreshDiagnostics(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheSize = calculateDirectorySize(context.cacheDir) +
                (context.externalCacheDir?.let { calculateDirectorySize(it) } ?: 0L)
            _cacheSizeBytes.value = cacheSize

            val runtime = Runtime.getRuntime()
            val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val maxMem = runtime.maxMemory() / (1024 * 1024)
            _memoryAllocatedMb.value = usedMem
            _memoryMaxMb.value = maxMem
        }
    }

    private fun calculateDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }

    fun clearAppCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val initialSize = _cacheSizeBytes.value
            clearDirectory(context.cacheDir)
            context.externalCacheDir?.let { clearDirectory(it) }
            refreshDiagnostics(context)
            withContext(Dispatchers.Main) {
                val freedStr = formatBytes(initialSize)
                showToast(context, String.format(Strings.get("cache_cleared_success", _language.value), freedStr))
            }
        }
    }

    private fun clearDirectory(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) clearDirectory(file)
            file.delete()
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    // ==========================================
    // STRUCTURED CSV HISTORY EXPORT (RFC 4180)
    // ==========================================
    fun exportHistoryToCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = historyList.value
            if (items.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showToast(context, Strings.get("no_history", _language.value))
                }
                return@launch
            }
            val csvData = CsvHistoryExporter.exportToCsv(items)
            val timestamp = System.currentTimeMillis()
            val file = File(context.cacheDir, "qraft_history_${timestamp}.csv")
            file.writeText(csvData, Charsets.UTF_8)

            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "QRaft History Export (CSV)")
                        putExtra(Intent.EXTRA_TEXT, "Exported QR History from QRaft Studio (${items.size} records)")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, Strings.get("export_csv", _language.value))
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    showToast(context, Strings.get("csv_export_success", _language.value))
                } catch (e: Exception) {
                    showToast(context, "Export error: ${e.localizedMessage}")
                }
            }
        }
    }

    // ==========================================
    // QR DESIGN PROFILES THEME ENGINE
    // ==========================================
    fun loadCustomDesignProfiles() {
        try {
            val jsonStr = userPrefs.getCustomProfilesJson()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<QRDesignProfile>()
            for (i in 0 until array.length()) {
                list.add(QRDesignProfile.fromJson(array.getJSONObject(i)))
            }
            _customDesignProfiles.value = list
        } catch (e: Exception) {
            _customDesignProfiles.value = emptyList()
        }
    }

    fun saveCurrentStyleAsProfile(name: String, context: Context) {
        val cleanName = name.trim().ifBlank { "Custom Profile ${System.currentTimeMillis() % 1000}" }
        val newProfile = QRDesignProfile.fromStyle(_qrStyle.value, cleanName)
        val current = _customDesignProfiles.value.toMutableList()
        current.add(0, newProfile)
        _customDesignProfiles.value = current

        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        userPrefs.setCustomProfilesJson(array.toString())

        showToast(context, String.format(Strings.get("profile_saved", _language.value), cleanName))
    }

    fun applyDesignProfile(profile: QRDesignProfile, context: Context) {
        _qrStyle.value = profile.applyTo(_qrStyle.value)
        triggerQRGeneration(instant = true)
        val name = if (_language.value == AppLanguage.FA) profile.nameFa else profile.nameEn
        showToast(context, String.format(Strings.get("profile_loaded", _language.value), name))
    }

    fun saveDesignProfile(name: String, context: Context) = saveCurrentStyleAsProfile(name, context)

    fun deleteDesignProfile(profileId: String, context: Context) {
        val current = _customDesignProfiles.value.toMutableList()
        current.removeAll { it.id == profileId }
        _customDesignProfiles.value = current

        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        userPrefs.setCustomProfilesJson(array.toString())

        showToast(context, Strings.get("profile_deleted", _language.value))
    }

    fun deleteDesignProfile(profile: QRDesignProfile, context: Context) = deleteDesignProfile(profile.id, context)

    // Aliases for SettingsDialog
    fun clearCache(context: Context) = clearAppCache(context)
    fun backupToCloud(context: Context) = syncWithGoogleDrive(context)
    fun restoreFromCloud(context: Context, uri: Uri) = restoreFromGoogleDrive(context, uri)

    // ==========================================
    // GOOGLE DRIVE & CLOUD SYNCHRONIZATION ENGINE
    // ==========================================
    fun syncWithGoogleDrive(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val historyItems = historyList.value
            val customProfiles = _customDesignProfiles.value

            val root = JSONObject()
            root.put("appName", "QRaft Studio Cloud Sync")
            root.put("syncVersion", 2)
            val now = System.currentTimeMillis()
            root.put("timestamp", now)
            root.put("syncDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now)))

            // History
            val histArray = JSONArray()
            for (item in historyItems) {
                histArray.put(JSONObject().apply {
                    put("content", item.content)
                    put("contentType", item.contentType)
                    put("label", item.label)
                    put("notes", item.notes)
                    put("styleJson", item.styleJson)
                    put("timestamp", item.timestamp)
                })
            }
            root.put("history", histArray)

            // Custom Profiles
            val profArray = JSONArray()
            for (prof in customProfiles) {
                profArray.put(prof.toJson())
            }
            root.put("profiles", profArray)

            // User Preferences
            root.put("totalGeneratedCount", _totalGeneratedCount.value)
            root.put("themeMode", _themeMode.value.name)
            root.put("language", _language.value.name)

            val file = File(context.cacheDir, "qraft_gdrive_sync_backup_${now}.json")
            file.writeText(root.toString(2), Charsets.UTF_8)

            userPrefs.setLastCloudSyncTime(now)
            _lastCloudSyncTime.value = now

            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "QRaft Google Drive Cloud Backup")
                        putExtra(Intent.EXTRA_TEXT, "QRaft Studio complete cloud synchronization archive with ${historyItems.size} history items and ${customProfiles.size} design profiles.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, Strings.get("cloud_sync_title", _language.value))
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    showToast(context, Strings.get("cloud_sync_success", _language.value))
                } catch (e: Exception) {
                    showToast(context, "Cloud sync error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun restoreFromGoogleDrive(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("Cannot open cloud file stream")

                val root = JSONObject(jsonString)
                var restoredCount = 0

                // Restore History
                val historyArray = root.optJSONArray("history")
                if (historyArray != null) {
                    for (i in 0 until historyArray.length()) {
                        val obj = historyArray.getJSONObject(i)
                        val content = obj.optString("content")
                        if (content.isNotBlank()) {
                            repository.addHistory(
                                QRHistoryEntity(
                                    content = content,
                                    contentType = obj.optString("contentType", "TEXT"),
                                    label = obj.optString("label", "Cloud Restored QR"),
                                    notes = obj.optString("notes", "Restored from Google Drive Sync"),
                                    styleJson = obj.optString("styleJson", "{}"),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                                )
                            )
                            restoredCount++
                        }
                    }
                }

                // Restore Profiles
                val profArray = root.optJSONArray("profiles")
                if (profArray != null) {
                    val list = _customDesignProfiles.value.toMutableList()
                    for (i in 0 until profArray.length()) {
                        val profile = QRDesignProfile.fromJson(profArray.getJSONObject(i))
                        if (list.none { it.id == profile.id || (it.nameEn == profile.nameEn && it.nameFa == profile.nameFa) }) {
                            list.add(profile)
                            restoredCount++
                        }
                    }
                    _customDesignProfiles.value = list
                    val newArray = JSONArray()
                    list.forEach { newArray.put(it.toJson()) }
                    userPrefs.setCustomProfilesJson(newArray.toString())
                }

                userPrefs.setLastCloudSyncTime(System.currentTimeMillis())
                _lastCloudSyncTime.value = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    showToast(
                        context,
                        String.format(Strings.get("cloud_restore_success", _language.value), restoredCount)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast(context, "Cloud restore failed: ${e.localizedMessage}")
                }
            }
        }
    }

    // Scanner actions
    fun onQrScanned(resultText: String) {
        if (_scannedResultText.value == resultText) return
        _scannedResultText.value = resultText
        triggerHapticFeedback()

        // Log scanned QR code into Room Database history
        viewModelScope.launch(Dispatchers.IO) {
            val isUrl = resultText.startsWith("http://", true) || resultText.startsWith("https://", true)
            val type = if (isUrl) ContentType.URL.name else ContentType.TEXT.name
            val styleJson = serializeStyleToJson(_qrStyle.value)
            val label = if (isUrl) "Scanned URL: ${resultText.take(24)}" else "Scanned: ${resultText.take(24)}"
            repository.addHistory(
                QRHistoryEntity(
                    content = resultText,
                    contentType = type,
                    styleJson = styleJson,
                    label = label,
                    notes = "Scanned with camera"
                )
            )
        }
    }

    private fun triggerHapticFeedback() {
        try {
            val app = getApplication<Application>()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearScannedResult() {
        _scannedResultText.value = null
    }

    fun openScannedLink(context: Context, url: String) {
        try {
            val parsedUri = if (url.startsWith("http://") || url.startsWith("https://")) {
                Uri.parse(url)
            } else {
                Uri.parse("https://$url")
            }
            val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast(context, "Cannot open link: ${e.localizedMessage}")
        }
    }

    fun loadScannedIntoCreate(scannedText: String) {
        val isUrl = scannedText.startsWith("http://", true) || scannedText.startsWith("https://", true)
        if (isUrl) {
            _selectedContentType.value = ContentType.URL
            urlForm.value = UrlContent(url = scannedText)
        } else {
            _selectedContentType.value = ContentType.TEXT
            textForm.value = TextContent(text = scannedText)
        }
        _scannedResultText.value = null
        _currentTab.value = AppTab.CREATE
        triggerQRGeneration()
    }

    fun editScannedInCreator(scannedText: String) = loadScannedIntoCreate(scannedText)

    // Batch Generation Functions
    fun parseBatchCsv() {
        val rows = CsvBatchParser.parseCsv(batchCsvText.value)
        _batchParsedRows.value = rows
    }

    fun loadSampleCsv() {
        batchCsvText.value = CsvBatchParser.SAMPLE_CSV
        parseBatchCsv()
    }

    fun loadSamplePlainText() {
        batchCsvText.value = CsvBatchParser.SAMPLE_PLAIN_TEXT
        parseBatchCsv()
    }

    fun startBatchGeneration(context: Context) {
        val rows = _batchParsedRows.value.filter { it.isValid }
        if (rows.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            _batchProgress.value = Pair(0, rows.size)
            val result = CsvBatchParser.generateBatchZip(
                context = context,
                rows = rows,
                baseStyle = _qrStyle.value,
                delayMs = batchDelayMs.value,
                onProgress = { current, total ->
                    _batchProgress.value = Pair(current, total)
                }
            )
            _lastBatchZipFile.value = result.zipFile
            _batchProgress.value = null

            if (result.zipFile != null) {
                repository.addBatchSession(
                    BatchSessionEntity(
                        name = "Batch_${System.currentTimeMillis()}",
                        totalCount = result.successCount,
                        zipFilePath = result.zipFile.absolutePath
                    )
                )
                withContext(Dispatchers.Main) {
                    showToast(context, Strings.get("batch_success", _language.value))
                }
            }
        }
    }

    fun shareBatchZip(context: Context, file: File) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Batch ZIP Archive")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // Animated QR Encode Functions
    fun onSelectAnimSourceImage(uri: Uri) {
        animSourceUri.value = uri
        encodeAnimatedImage(getApplication(), uri)
    }

    fun encodeAnimatedImage(context: Context, uri: Uri) {
        animSourceUri.value = uri
        viewModelScope.launch(Dispatchers.Default) {
            val result = AnimatedQREngine.encodeImageToFrames(
                context = context,
                imageUri = uri,
                maxDimension = animMaxDim.value,
                quality = animQuality.value,
                reliabilityPreset = animReliabilityPreset.value
            )
            _animEncodeResult.value = result
            animCurrentFrameIndex.value = 0
        }
    }

    fun setAnimFrameDelayMs(delayMs: Int) {
        val safe = delayMs.coerceIn(50, 1000)
        animFrameDelayMs.value = safe
        animFps.value = (1000 / safe).coerceIn(1, 20)
    }

    fun setAnimFps(fps: Int) {
        val safeFps = fps.coerceIn(1, 20)
        animFps.value = safeFps
        animFrameDelayMs.value = (1000 / safeFps).coerceIn(50, 1000)
    }

    fun cleanupTempCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = AnimationCleanupWorker.cleanupFrameFragments(context, 0L)
            withContext(Dispatchers.Main) {
                showToast(context, Strings.get("cache_cleared", _language.value))
            }
        }
    }

    fun cancelGifExport(context: Context) {
        gifExportJob?.cancel()
        gifExportJob = null
        isGifExporting.value = false
        gifExportProgress.value = 0f
        viewModelScope.launch(Dispatchers.IO) {
            AnimationCleanupWorker.cleanupFrameFragments(context, 0L)
            withContext(Dispatchers.Main) {
                showToast(context, Strings.get("export_cancelled", _language.value))
            }
        }
    }

    fun exportAnimatedGif(context: Context) {
        val result = _animEncodeResult.value ?: run {
            showToast(context, "No animated frames available.")
            return
        }
        if (isGifExporting.value) return

        isGifExporting.value = true
        gifExportProgress.value = 0f
        gifExportCurrentFrame.value = 0
        gifExportTotalFrames.value = result.frames.size

        gifExportJob = viewModelScope.launch(Dispatchers.IO) {
            var exportedFile: File? = null
            try {
                exportedFile = AnimatedQREngine.exportFramesToGif(
                    context = context,
                    frames = result.frames,
                    sessionId = result.sessionId,
                    delayMs = animFrameDelayMs.value,
                    onProgress = { cur, total ->
                        gifExportCurrentFrame.value = cur
                        gifExportTotalFrames.value = total
                        gifExportProgress.value = if (total > 0) cur.toFloat() / total else 0f
                    },
                    isCancelled = { !isActive }
                )

                withContext(Dispatchers.Main) {
                    isGifExporting.value = false
                    if (exportedFile != null && exportedFile.exists() && exportedFile.length() > 0) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportedFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/gif"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(intent, "Export Animated GIF")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    } else {
                        showToast(context, "Failed to export GIF")
                    }
                }
            } catch (e: CancellationException) {
                AnimationCleanupWorker.cleanupFrameFragments(context, 0L)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGifExporting.value = false
                    showToast(context, "Export error: ${e.localizedMessage}")
                }
            } finally {
                // Post-export cleanup worker scan: purge temp frame fragments while preserving active export file
                AnimationCleanupWorker.cleanupFrameFragments(context, 60_000L, preserveFile = exportedFile)
                withContext(Dispatchers.Main) {
                    isGifExporting.value = false
                }
            }
        }
    }

    fun exportAnimatedFramesZip(context: Context) {
        val result = _animEncodeResult.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val file = AnimatedQREngine.exportFramesToZip(
                context = context,
                frames = result.frames,
                sessionId = result.sessionId
            )
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Export Frames ZIP")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }

    // Animated QR Decode Functions
    fun onAnimatedQrScanned(rawPayload: String) {
        val updated = AnimatedQREngine.processIncomingFrame(_animDecodeState.value, rawPayload)
        _animDecodeState.value = updated
    }

    fun onAnimatedQrFrameScanned(qrContent: String) = onAnimatedQrScanned(qrContent)

    fun decodeUploadedGif(context: Context, gifUri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            val state = GifFrameExtractor.decodeFromImageOrGif(context, gifUri)
            _animDecodeState.value = state
        }
    }

    fun decodeGifFile(context: Context, gifUri: Uri) = decodeUploadedGif(context, gifUri)

    fun resetAnimatedDecodeSession() {
        _animDecodeState.value = DecodeSessionState()
    }

    fun saveReconstructedImage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = saveBitmapToCache(context, bitmap, "qraft_reconstructed_${System.currentTimeMillis()}.jpg")
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Save Reconstructed Image")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }

    fun saveReconstructedImage(context: Context) {
        val bmp = _animDecodeState.value.reconstructedBitmap ?: return
        saveReconstructedImage(context, bmp)
    }

    private fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            val fos = FileOutputStream(file)
            bitmap.compress(format, 100, fos)
            fos.flush()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // JSON style serialization
    private fun serializeStyleToJson(style: QRStyle): String {
        val obj = JSONObject()
        obj.put("ec", style.errorCorrection.name)
        obj.put("size", style.sizePx)
        obj.put("fg", style.fgColor)
        obj.put("grad", style.gradientMode)
        obj.put("gradEnd", style.gradientEndColor)
        obj.put("gradType", style.gradientType.name)
        obj.put("bg", style.bgColor)
        obj.put("trans", style.transparentBg)
        obj.put("margin", style.margin)
        obj.put("dot", style.dotStyle.name)
        obj.put("eyeFrame", style.eyeFrameStyle.name)
        obj.put("eyeInner", style.eyeInnerStyle.name)
        obj.put("logoUri", style.logoUri ?: "")
        obj.put("logoSize", style.logoSizePercent)
        obj.put("frame", style.frameLabel)
        obj.put("wm", style.watermarkText)
        obj.put("wmAlpha", style.watermarkOpacity.toDouble())
        obj.put("palette", style.activePaletteId ?: "")
        obj.put("exportRes", style.exportResolution)
        return obj.toString()
    }

    private fun deserializeStyleFromJson(json: String): QRStyle? {
        return try {
            val obj = JSONObject(json)
            QRStyle(
                errorCorrection = ErrorCorrection.valueOf(obj.optString("ec", "M")),
                sizePx = obj.optInt("size", 300),
                fgColor = obj.optLong("fg", 0xFF000000),
                gradientMode = obj.optBoolean("grad", false),
                gradientEndColor = obj.optLong("gradEnd", 0xFF1A1A1A),
                gradientType = try { GradientType.valueOf(obj.optString("gradType", "DIAGONAL")) } catch (e: Exception) { GradientType.DIAGONAL },
                bgColor = obj.optLong("bg", 0xFFFFFFFF),
                transparentBg = obj.optBoolean("trans", false),
                margin = obj.optInt("margin", 2),
                dotStyle = try { DotStyle.valueOf(obj.optString("dot", "SQUARE")) } catch (e: Exception) { DotStyle.SQUARE },
                eyeFrameStyle = try { EyeFrameStyle.valueOf(obj.optString("eyeFrame", "SQUARE")) } catch (e: Exception) { EyeFrameStyle.SQUARE },
                eyeInnerStyle = try { EyeInnerStyle.valueOf(obj.optString("eyeInner", "SQUARE")) } catch (e: Exception) { EyeInnerStyle.SQUARE },
                logoUri = obj.optString("logoUri").ifEmpty { null },
                logoSizePercent = obj.optInt("logoSize", 20),
                frameLabel = obj.optString("frame", "None"),
                watermarkText = obj.optString("wm", ""),
                watermarkOpacity = obj.optDouble("wmAlpha", 0.5).toFloat(),
                activePaletteId = obj.optString("palette").ifEmpty { null },
                exportResolution = obj.optInt("exportRes", 1024)
            )
        } catch (e: Exception) {
            null
        }
    }
}
