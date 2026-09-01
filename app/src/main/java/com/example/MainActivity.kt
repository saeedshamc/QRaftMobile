package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppHeader
import com.example.ui.components.SettingsDialog
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.Strings
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AnimatedQRScreen
import com.example.ui.screens.BatchScreen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.theme.QRaftTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val language by viewModel.language.collectAsState()

            QRaftTheme(themeMode = themeMode) {
                val layoutDir = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                    QRaftAppRoot(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val initialTab = intent?.getStringExtra("EXTRA_INITIAL_TAB")
        if (initialTab == "SCAN") {
            viewModel.setTab(AppTab.SCAN)
        }
    }
}

@Composable
fun QRaftAppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val memoryAllocatedMb by viewModel.memoryAllocatedMb.collectAsState()
    val memoryMaxMb by viewModel.memoryMaxMb.collectAsState()
    val lastCloudSyncTime by viewModel.lastCloudSyncTime.collectAsState()

    val navItems = listOf(
        AppTab.CREATE to Triple(Strings.get("tab_create", language), Icons.Default.AddBox, "tab_create"),
        AppTab.SCAN to Triple(Strings.get("tab_scan", language), Icons.Default.QrCodeScanner, "tab_scan"),
        AppTab.BATCH to Triple(Strings.get("tab_batch", language), Icons.Default.DynamicFeed, "tab_batch"),
        AppTab.HISTORY to Triple(Strings.get("tab_history", language), Icons.Default.History, "tab_history"),
        AppTab.ANIMATED_QR to Triple(Strings.get("tab_animated", language), Icons.Default.MovieFilter, "tab_animated"),
        AppTab.ANALYTICS to Triple(Strings.get("tab_analytics", language), Icons.Default.BarChart, "tab_analytics")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppHeader(
                language = language,
                onOpenSettings = { viewModel.isSettingsOpen.value = true }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                navItems.forEach { (tab, triple) ->
                    val (title, icon, testTag) = triple
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.setTab(tab) },
                        icon = { Icon(imageVector = icon, contentDescription = title) },
                        label = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_switch"
            ) { tab ->
                when (tab) {
                    AppTab.CREATE -> CreateScreen(viewModel = viewModel)
                    AppTab.SCAN -> ScanScreen(viewModel = viewModel)
                    AppTab.BATCH -> BatchScreen(viewModel = viewModel)
                    AppTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                    AppTab.ANIMATED_QR -> AnimatedQRScreen(viewModel = viewModel)
                    AppTab.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (isSettingsOpen) {
        SettingsDialog(
            language = language,
            themeMode = themeMode,
            cacheSizeBytes = cacheSizeBytes,
            memoryAllocatedMb = memoryAllocatedMb,
            memoryMaxMb = memoryMaxMb,
            lastCloudSyncTime = lastCloudSyncTime,
            onLanguageChange = { viewModel.setLanguage(it) },
            onThemeChange = { viewModel.setThemeMode(it) },
            onClearCache = { viewModel.clearCache(context) },
            onRefreshDiagnostics = { viewModel.refreshDiagnostics(context) },
            onCloudSync = { viewModel.backupToCloud(context) },
            onCloudRestore = { uri -> viewModel.restoreFromCloud(context, uri) },
            onClearAllData = { viewModel.clearAllLocalData(context) },
            onDismiss = { viewModel.isSettingsOpen.value = false }
        )
    }
}
