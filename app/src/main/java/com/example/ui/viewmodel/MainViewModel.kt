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
import com.example.domain.engine.CsvBatchParser
import com.example.domain.engine.CsvBatchRow
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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

    init {
        updateStatsMap()
        parseBatchCsv()
        triggerQRGeneration()
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
        triggerQRGeneration()
    }

    fun applyColorPalette(palette: ColorPalettePreset) {
        _qrStyle.value = _qrStyle.value.copy(
            fgColor = palette.fgColor,
            gradientEndColor = palette.gradientEndColor,
            bgColor = palette.bgColor,
            gradientMode = true,
            activePaletteId = palette.id
        )
        triggerQRGeneration()
    }

    fun onFormChanged() {
        triggerQRGeneration()
    }

    private fun triggerQRGeneration() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch(Dispatchers.Default) {
            delay(200)
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
            val count = AnimatedQREngine.cleanupTemporaryFiles(context, 0L)
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
            AnimatedQREngine.cleanupTemporaryFiles(context, 0L)
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
            try {
                val file = AnimatedQREngine.exportFramesToGif(
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

                // Cleanup stale temp files (> 3 minutes old) to keep cache lean
                AnimatedQREngine.cleanupTemporaryFiles(context, 180_000L)

                withContext(Dispatchers.Main) {
                    isGifExporting.value = false
                    if (file != null && file.exists() && file.length() > 0) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
                AnimatedQREngine.cleanupTemporaryFiles(context, 0L)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGifExporting.value = false
                    showToast(context, "Export error: ${e.localizedMessage}")
                }
            } finally {
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
