package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StoreConfig
import com.example.ui.components.AiAssistantFab
import com.example.ui.components.ChangePopupDialog
import com.example.ui.components.ReceiptBarcodeScannerDialog
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.TrialCountdownBanner
import com.example.ui.components.TrialLockOverlay
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HitungCepatScreen
import com.example.ui.screens.KasirScreen
import com.example.ui.screens.LaporanScreen
import com.example.ui.screens.MenuManagementScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KasirProTheme
import com.example.ui.theme.LocalCustomColors
import com.example.ui.viewmodel.KasirViewModel
import com.example.util.AiTtsEngine
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider

val LocalAiTtsEngine = staticCompositionLocalOf<AiTtsEngine?> { null }

sealed class NavTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    object Dashboard : NavTab("dashboard", "Beranda", Icons.Filled.Home, Icons.Outlined.Home, "nav_dashboard")
    object Kasir : NavTab("kasir", "Kasir", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale, "nav_kasir")
    object Hitung : NavTab("hitung", "Hitung", Icons.Filled.Calculate, Icons.Outlined.Calculate, "nav_hitung")
    object Menu : NavTab("menu", "Menu", Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu, "nav_menu")
    object Laporan : NavTab("laporan", "Laporan", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_laporan")
}

class MainActivity : ComponentActivity() {
    private val viewModel: KasirViewModel by viewModels()
    private lateinit var ttsEngine: AiTtsEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsEngine = AiTtsEngine(this)
        enableEdgeToEdge()

        setContent {
            val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
            val themeMode = storeConfig?.themeMode ?: "DARK"

            CompositionLocalProvider(LocalAiTtsEngine provides ttsEngine) {
                KasirProTheme(themeMode = themeMode) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.refreshLicenseStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsEngine.shutdown()
    }
}

@Composable
fun MainAppScreen(viewModel: KasirViewModel) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
    val activeReceipt by viewModel.activeReceiptTransaction.collectAsStateWithLifecycle()
    val changePopupData by viewModel.changePopupData.collectAsStateWithLifecycle()
    val licenseStatus by viewModel.licenseStatus.collectAsStateWithLifecycle()
    val isCheckingServerActivation by viewModel.isCheckingServerActivation.collectAsStateWithLifecycle()
    val serverActivationHint by viewModel.serverActivationStatusMessage.collectAsStateWithLifecycle()
    val showReceiptScanner by viewModel.showReceiptScanner.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current
    var showEarlyActivationDialog by rememberSaveable { mutableStateOf(false) }

    val isLockedOut = licenseStatus.isTrialExpired && !licenseStatus.isActivated
    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            showEarlyActivationDialog = false
            viewModel.closeReceiptScanner()
            viewModel.closeReceipt()
            viewModel.dismissChangePopup(speakOnDismiss = false)
            viewModel.dismissMasterReset()
        }
    }

    // Initialize Firebase & real-time cloud sync if config is loaded and has JSON payload
    LaunchedEffect(storeConfig?.googleServicesJson) {
        storeConfig?.let {
            if (it.googleServicesJson.isNotBlank()) {
                viewModel.startCloudRealtimeSyncIfReady(it.googleServicesJson)
            }
        }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

    // 5 primary tabs (Pengaturan is accessed via the button next to Theme)
    val navTabs = listOf(
        NavTab.Dashboard,
        NavTab.Kasir,
        NavTab.Hitung,
        NavTab.Menu,
        NavTab.Laporan
    )

    // Sync currentScreen state to ViewModel
    LaunchedEffect(selectedTabIndex, isSettingsOpen) {
        val screen = if (isSettingsOpen) "SETTINGS" else when (selectedTabIndex) {
            0 -> "DASHBOARD"
            1 -> "KASIR"
            2 -> "HITUNG"
            3 -> "MENU"
            4 -> "LAPORAN"
            else -> "KASIR"
        }
        viewModel.setCurrentScreen(screen)
    }

    // Listen to Toast Events
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Listen to AI Navigation triggers
    LaunchedEffect(Unit) {
        viewModel.aiNavigationTrigger.collectLatest { target ->
            when (target) {
                "KASIR" -> {
                    isSettingsOpen = false
                    selectedTabIndex = 1
                }
                "DASHBOARD" -> {
                    isSettingsOpen = false
                    selectedTabIndex = 0
                }
                "LAPORAN" -> {
                    isSettingsOpen = false
                    selectedTabIndex = 4
                }
                "SETTINGS" -> {
                    isSettingsOpen = true
                }
                "HITUNG" -> {
                    isSettingsOpen = false
                    selectedTabIndex = 2
                }
                "MENU", "KELOLA_MENU", "DAFTAR_MENU" -> {
                    isSettingsOpen = false
                    selectedTabIndex = 3
                }
            }
        }
    }

    val cart by viewModel.cart.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val isSinglePaneKasirWithCart = !useNavigationRail && !isSettingsOpen && selectedTabIndex == 1 && cart.isNotEmpty()

        // Common screen content renderer
        @Composable
        fun ScreenContent(contentModifier: Modifier) {
            Column(modifier = contentModifier.fillMaxSize()) {
                if (!licenseStatus.isActivated && !licenseStatus.isTrialExpired) {
                    TrialCountdownBanner(
                        remainingSeconds = licenseStatus.remainingSeconds,
                        onActivateClick = { showEarlyActivationDialog = true }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (isSettingsOpen) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { isSettingsOpen = false }
                        )
                    } else {
                        when (selectedTabIndex) {
                            0 -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToKasir = { selectedTabIndex = 1 },
                                onNavigateToHitung = { selectedTabIndex = 2 },
                                onNavigateToMenu = { selectedTabIndex = 3 },
                                onNavigateToLaporan = { selectedTabIndex = 4 },
                                onNavigateToSettings = { isSettingsOpen = true }
                            )
                            1 -> KasirScreen(
                                viewModel = viewModel,
                                onNavigateBack = { selectedTabIndex = 0 }
                            )
                            2 -> HitungCepatScreen(
                                viewModel = viewModel
                            )
                            3 -> MenuManagementScreen(
                                viewModel = viewModel
                            )
                            4 -> LaporanScreen(
                                viewModel = viewModel
                            )
                        }
                    }

                    // AI Assistant FAB (lifted cleanly above Kasir bottom cart bar when needed)
                    if (storeConfig?.isAiAssistantEnabled != false) {
                        AiAssistantFab(
                            viewModel = viewModel,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    bottom = if (isSinglePaneKasirWithCart) 88.dp else 12.dp,
                                    end = 8.dp
                                )
                        )
                    }
                }
            }
        }

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    header = {
                        Spacer(modifier = Modifier.height(10.dp))
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = "Kasir Pro",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        navTabs.forEachIndexed { index, tab ->
                            val selected = !isSettingsOpen && selectedTabIndex == index
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    isSettingsOpen = false
                                    selectedTabIndex = index
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = customColors.actionPrimaryBg,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f, fill = true))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Settings quick button at rail bottom
                        NavigationRailItem(
                            selected = isSettingsOpen,
                            onClick = { isSettingsOpen = true },
                            icon = {
                                Icon(
                                    imageVector = if (isSettingsOpen) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Pengaturan",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Setting",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSettingsOpen) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = customColors.actionPrimaryBg,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("tab_settings_rail")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ScreenContent(
                    contentModifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Vertical + WindowInsetsSides.End
                            )
                        )
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (!isSettingsOpen) {
                        NavigationBar(
                            modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            windowInsets = NavigationBarDefaults.windowInsets
                        ) {
                            navTabs.forEachIndexed { index, tab ->
                                val selected = selectedTabIndex == index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        isSettingsOpen = false
                                        selectedTabIndex = index
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontSize = 10.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = customColors.actionPrimaryBg,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                ScreenContent(contentModifier = Modifier.padding(innerPadding))
            }
        }

        // 10-second Change Popup Dialog (Displays upon voice cash payment)
        changePopupData?.let { data ->
            ChangePopupDialog(
                data = data,
                onDismiss = { viewModel.dismissChangePopup() }
            )
        }

        // Active Receipt Dialog (Displays upon manual checkout, "Lihat Nota", or barcode scan)
        activeReceipt?.let { txWithItems ->
            val config = storeConfig ?: StoreConfig()
            ReceiptDialog(
                transactionWithItems = txWithItems,
                storeConfig = config,
                onDismiss = { viewModel.closeReceipt() },
                onPrintThermal = { viewModel.printThermalReceipt(txWithItems) },
                onPrintSystem = { activityContext -> viewModel.printSystemReceipt(activityContext, txWithItems) },
                onShareImage = { viewModel.shareReceiptAsImage(txWithItems) },
                onDelete = { viewModel.deleteTransaction(txWithItems) }
            )
        }

        // Camera Barcode Scanner Dialog to lookup & view receipts
        if (showReceiptScanner && !isLockedOut) {
            ReceiptBarcodeScannerDialog(
                onDismiss = { viewModel.closeReceiptScanner() },
                onLookupBarcode = { code -> viewModel.findAndOpenReceiptByBarcode(code) != null }
            )
        }

        // Master Reset Confirmation Dialog
        val showMasterReset by viewModel.showMasterResetDialog.collectAsStateWithLifecycle()
        if (showMasterReset) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMasterReset() },
                title = { Text("Konfirmasi Reset Total Aplikasi") },
                text = { Text("Apakah Anda yakin ingin menghapus seluruh data aplikasi (menu, kategori, transaksi, dan pengaturan)? Tindakan ini tidak dapat dibatalkan.") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmMasterReset() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ya, Hapus Semua", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissMasterReset() }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Unclosable 5-Minute Trial Expired Lock Overlay OR Early Activation Dialog
        if (isLockedOut || showEarlyActivationDialog) {
            if (isLockedOut) {
                BackHandler(enabled = true) {}
            }
            TrialLockOverlay(
                deviceId = licenseStatus.deviceId,
                adminWaNumber = licenseStatus.adminWhatsappNumber,
                storeName = storeConfig?.storeName ?: "",
                isTrialExpired = isLockedOut,
                isCheckingStatus = isCheckingServerActivation,
                serverStatusHint = serverActivationHint,
                onActivateKey = { key ->
                    val result = viewModel.activateLicenseAsync(key)
                    if (result.success) {
                        showEarlyActivationDialog = false
                    }
                    result
                },
                onCheckServerStatus = { silent ->
                    viewModel.checkServerActivationStatus(silent)
                },
                onNotifyToast = { msg -> viewModel.emitToast(msg) },
                onPurchaseRequested = {
                    viewModel.notifyAdminSilentPurchaseRequest(
                        deviceId = licenseStatus.deviceId,
                        storeName = storeConfig?.storeName ?: ""
                    )
                },
                onDismissEarly = if (isLockedOut) null else { { showEarlyActivationDialog = false } }
            )
        }
    }
}
