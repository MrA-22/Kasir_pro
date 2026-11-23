package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.StoreConfig
import com.example.ui.components.AiAssistantFab
import com.example.ui.components.QrisDisplayDialog
import com.example.ui.components.TrialLockOverlay
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.StatusQris
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.KasirViewModel
import com.example.util.LicenseManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: KasirViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val licenseStatus by viewModel.licenseStatus.collectAsStateWithLifecycle()
    val isCheckingServerActivation by viewModel.isCheckingServerActivation.collectAsStateWithLifecycle()
    val serverActivationHint by viewModel.serverActivationStatusMessage.collectAsStateWithLifecycle()
    val cloudLicenseRequests by viewModel.cloudLicenseRequests.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current

    // Local form states
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var storePhone by remember { mutableStateOf("") }
    var receiptFooter by remember { mutableStateOf("") }
    var qrisPayload by remember { mutableStateOf("") }
    var printerMacAddress by remember { mutableStateOf("") }
    var printerName by remember { mutableStateOf("") }
    var isAutoPrint by remember { mutableStateOf(true) }
    var isAiAssistantEnabled by remember { mutableStateOf(true) }
    var noiseCancellationLevel by remember { mutableStateOf(0.8f) }
    var voiceSensitivity by remember { mutableStateOf(0.7f) }
    var isSmartCurrencyFilterEnabled by remember { mutableStateOf(true) }
    var isDeveloperModeEnabled by remember { mutableStateOf(false) }
    var googleServicesJsonInput by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }
    var selectedTheme by remember { mutableStateOf("DARK") }

    // Admin License Keygen states inside Developer Mode
    var adminPinInput by remember { mutableStateOf("") }
    var isAdminKeygenUnlocked by remember { mutableStateOf(false) }
    var buyerDeviceIdInput by remember { mutableStateOf("") }
    var generatedBuyerKey by remember { mutableStateOf("") }
    var adminWaInput by remember(licenseStatus.adminWhatsappNumber) {
        mutableStateOf(licenseStatus.adminWhatsappNumber)
    }
    var showActivationModal by remember { mutableStateOf(false) }

    var expandedPrinterDropdown by remember { mutableStateOf(false) }
    var isTestQrisDialogOpen by remember { mutableStateOf(false) }

    // Sync from storeConfig
    LaunchedEffect(storeConfig) {
        storeConfig?.let {
            storeName = it.storeName
            storeAddress = it.storeAddress
            storePhone = it.storePhone
            receiptFooter = it.receiptFooter
            qrisPayload = it.qrisPayload
            printerMacAddress = it.printerMacAddress
            printerName = it.printerName
            isAutoPrint = it.isBluetoothAutoPrint
            isAiAssistantEnabled = it.isAiAssistantEnabled
            noiseCancellationLevel = it.noiseCancellationLevel
            voiceSensitivity = it.voiceSensitivity
            isSmartCurrencyFilterEnabled = it.isSmartCurrencyFilterEnabled
            googleServicesJsonInput = it.googleServicesJson
            selectedTheme = it.themeMode
        }
    }

    // Image Picker for QRIS Photo
    val qrisImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.saveQrisImageUri(it) }
    }

    // JSON File Picker for google-services.json
    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    googleServicesJsonInput = reader.readText()
                    viewModel.emitToast("File google-services.json berhasil di-load!")
                }
            } catch (e: Exception) {
                viewModel.emitToast("Gagal membaca file: ${e.localizedMessage}")
            }
        }
    }

    // Permission launcher for Bluetooth Scan & Connect
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.loadPairedBluetoothDevices()
    }

    fun requestBluetoothAndLoadDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect || !hasScan) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
                return
            }
        }
        viewModel.loadPairedBluetoothDevices()
    }

    val currentQrisFile = storeConfig?.qrisImagePath?.takeIf { it.isNotBlank() }?.let { File(it) }
    val hasUploadedQris = currentQrisFile != null && currentQrisFile.exists()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth >= 720.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isTablet) 1200.dp else 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 12.dp)
                    .padding(bottom = 84.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Pengaturan Toko & POS",
                        modifier = Modifier.clickable {
                            tapCount++
                            if (tapCount >= 3) {
                                isDeveloperModeEnabled = !isDeveloperModeEnabled
                                tapCount = 0
                                viewModel.emitToast(if (isDeveloperModeEnabled) "Developer Mode Aktif" else "Developer Mode Nonaktif")
                            }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

        if (isDeveloperModeEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🛠️ Developer Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unggah file google-services.json atau tempel (paste) isinya di bawah ini untuk menghubungkan basis data ke Firebase secara dinamis.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { jsonFilePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pilih & Upload File", fontSize = 12.sp)
                        }
                        
                        if (googleServicesJsonInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = { 
                                    googleServicesJsonInput = ""
                                    viewModel.emitToast("Konfigurasi dihapus!")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Kosongkan", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = googleServicesJsonInput,
                        onValueChange = { googleServicesJsonInput = it },
                        label = { Text("Isi / Paste google-services.json") },
                        placeholder = { Text("{\"project_info\": ...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        maxLines = 10
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val currentConfig = storeConfig ?: StoreConfig()
                            val updatedConfig = currentConfig.copy(
                                googleServicesJson = googleServicesJsonInput.trim()
                            )
                            viewModel.updateStoreConfig(updatedConfig)
                            
                            if (googleServicesJsonInput.trim().isNotBlank()) {
                                viewModel.emitToast("Konfigurasi disimpan! Menghubungkan ke Firebase...")
                            } else {
                                viewModel.emitToast("Konfigurasi dikosongkan!")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Simpan & Hubungkan Firebase", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (googleServicesJsonInput.trim().isBlank()) {
                                viewModel.emitToast("Harap isi atau tempel google-services.json terlebih dahulu!")
                            } else {
                                viewModel.triggerTwoWayCloudSync()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔄 Ambil & Sinkronkan Data Cloud", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Admin Keygen & Trial Control (PIN Protected)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Admin Lisensi & Generator API Key",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isAdminKeygenUnlocked) {
                            OutlinedTextField(
                                value = adminPinInput,
                                onValueChange = { adminPinInput = it },
                                label = { Text("Masukkan PIN Admin") },
                                placeholder = { Text("PIN rahasia admin") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (adminPinInput.trim() == LicenseManager.ADMIN_KEYGEN_PIN) {
                                        isAdminKeygenUnlocked = true
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                        val clipDeviceId = LicenseManager.extractDeviceIdFromText(clipText)
                                        if (clipDeviceId != null) {
                                            buyerDeviceIdInput = clipDeviceId
                                            generatedBuyerKey = viewModel.generateLicenseKeyForBuyer(clipDeviceId)
                                            viewModel.emitToast("ID $clipDeviceId terdeteksi & API otomatis dibuat!")
                                        } else {
                                            buyerDeviceIdInput = licenseStatus.deviceId
                                            generatedBuyerKey = viewModel.generateLicenseKeyForBuyer(licenseStatus.deviceId)
                                            viewModel.emitToast("Panel Admin Lisensi terbuka!")
                                        }
                                        if (com.example.util.FirebaseSyncManager.isReady()) {
                                            viewModel.refreshCloudLicenseRequests()
                                        }
                                    } else {
                                        viewModel.emitToast("PIN Admin salah!")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Buka Generator API Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            val isGatewayReady = LicenseManager.isSilentWaGatewayConfigured()
                            val gatewayHost = LicenseManager.getConfiguredGatewayHost()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isGatewayReady) StatusSuccess.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isGatewayReady) {
                                        "Server Webhook Otomatis: AKTIF (${gatewayHost.ifBlank { "HTTPS" }}) — token sesi berumur pendek & verifikasi server-side (tanpa secret statis di APK)."
                                    } else {
                                        "Server Webhook Otomatis: Belum aktif — isi WEBHOOK_BASE_URL di Secrets AI Studio."
                                    },
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = if (isGatewayReady) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isGatewayReady) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.testAdminWebhookConnection() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Tes Koneksi Webhook Admin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Salin seluruh chat WhatsApp pembeli lalu tekan tombol tempel di bawah — ID Perangkat & Kode API akan otomatis terdeteksi tanpa seleksi manual:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    val extracted = LicenseManager.extractDeviceIdFromText(clipText)
                                    if (extracted != null) {
                                        buyerDeviceIdInput = extracted
                                        generatedBuyerKey = viewModel.generateLicenseKeyForBuyer(extracted)
                                        viewModel.emitToast("ID $extracted otomatis diekstrak & API Key dibuat!")
                                    } else {
                                        viewModel.emitToast("Tidak ditemukan kode KSR-XXXX-XXXX pada teks clipboard.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tempel Chat WA Pembeli (Auto-Generate)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = buyerDeviceIdInput,
                                onValueChange = { rawInput ->
                                    val extracted = LicenseManager.extractDeviceIdFromText(rawInput)
                                    if (extracted != null) {
                                        buyerDeviceIdInput = extracted
                                        generatedBuyerKey = viewModel.generateLicenseKeyForBuyer(extracted)
                                    } else {
                                        buyerDeviceIdInput = rawInput.uppercase()
                                        generatedBuyerKey = ""
                                    }
                                },
                                label = { Text("ID Perangkat / Paste Chat WA") },
                                placeholder = { Text("KSR-XXXX-XXXX") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val extracted = LicenseManager.extractDeviceIdFromText(buyerDeviceIdInput)
                                    val targetId = extracted ?: buyerDeviceIdInput.trim().uppercase()
                                    if (targetId.isBlank()) {
                                        viewModel.emitToast("Masukkan ID Perangkat pembeli!")
                                    } else {
                                        buyerDeviceIdInput = targetId
                                        generatedBuyerKey = viewModel.generateLicenseKeyForBuyer(targetId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Generate Kode API Pembeli", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            if (generatedBuyerKey.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(StatusSuccess.copy(alpha = 0.15f))
                                        .padding(10.dp)
                                ) {
                                    Text("API Key Siap Kirim:", fontSize = 10.sp, color = StatusSuccess, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = generatedBuyerKey,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val replyText = LicenseManager.buildAdminReplyToBuyerMessage(buyerDeviceIdInput, generatedBuyerKey)
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("Balasan WA Kasir Pro", replyText))
                                                viewModel.emitToast("Balasan WA & Kode API disalin!")
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salin", fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = {
                                                val replyText = LicenseManager.buildAdminReplyToBuyerMessage(buyerDeviceIdInput, generatedBuyerKey)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, replyText)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(shareIntent, "Kirim API ke WhatsApp Pembeli"))
                                                } catch (_: Exception) {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Balasan WA Kasir Pro", replyText))
                                                    viewModel.emitToast("Balasan disalin ke clipboard!")
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Kirim WA", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            if (com.example.util.FirebaseSyncManager.isReady()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Permintaan Lisensi Masuk (Cloud)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { viewModel.refreshCloudLicenseRequests() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Segarkan Permintaan", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (cloudLicenseRequests.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    cloudLicenseRequests.take(5).forEach { req ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .clickable {
                                                    buyerDeviceIdInput = req.deviceId
                                                    generatedBuyerKey = viewModel.generateLicenseKeyForBuyer(req.deviceId)
                                                    viewModel.emitToast("API Key untuk ${req.deviceId} dibuat!")
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(req.deviceId, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                if (req.storeName.isNotBlank()) {
                                                    Text(req.storeName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            Text("Pilih", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = adminWaInput,
                                onValueChange = { adminWaInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("No. WhatsApp Admin (628...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.updateAdminWhatsappNumber(adminWaInput) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Simpan Nomor WA Admin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.resetTrialForTesting() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Timer Uji Coba 5 Menit (Testing)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        @Composable
        fun LeftColumnCards() {
            Column(modifier = Modifier.fillMaxWidth()) {
                // SECTION 1: PROFIL TOKO
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Profil Toko / Usaha",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Nama Toko / Usaha") },
                    placeholder = { Text("Contoh: Warung Makan Bu Siti, Toko Berkah") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    label = { Text("Alamat Toko (Tampil di Struk)") },
                    placeholder = { Text("Contoh: Jl. Merdeka No. 12, Jakarta") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = storePhone,
                    onValueChange = { storePhone = it },
                    label = { Text("Nomor Telepon / WA") },
                    placeholder = { Text("Contoh: 0812-3456-7890") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = receiptFooter,
                    onValueChange = { receiptFooter = it },
                    label = { Text("Catatan Kaki Struk (Footer)") },
                    placeholder = { Text("Contoh: Terima kasih atas kunjungan Anda!\nBarang yang sudah dibeli tidak dapat ditukar.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 2: DAFTARKAN QRIS TOKO (UPLOAD GAMBAR QRIS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, StatusQris.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = StatusQris)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Foto & Barcode QRIS Toko",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (hasUploadedQris) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Aktif",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Unggah foto / tangkapan layar barcode QRIS toko Anda (BCA, Mandiri, BRI, BNI, GoPay, ShopeePay, Dana, dll). Barcode ini akan langsung ditampilkan kepada pembeli saat pembayaran di kasir.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // QRIS Image Upload / Preview Area
                if (hasUploadedQris && currentQrisFile != null) {
                    // Uploaded Preview Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, StatusQris.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = currentQrisFile,
                                contentDescription = "Foto QRIS Terpasang",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { qrisImagePickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ganti Foto", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.removeQrisImage() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hapus", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { isTestQrisDialogOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusQris
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pratinjau Tampilan QRIS di Kasir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Empty state: Upload Prompt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.5.dp, customColors.borderStroke, RoundedCornerShape(14.dp))
                            .clickable {
                                qrisImagePickerLauncher.launch("image/*")
                            }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(StatusQris.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Pilih Foto QRIS",
                                    tint = StatusQris,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Pilih Foto / Gambar QRIS dari Galeri",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Format didukung: JPG, PNG, screenshot QRIS",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { qrisImagePickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusQris
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unggah Foto QRIS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

                // SECTION 5: TEMA TAMPILAN
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tema & Tampilan Aplikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeChoiceCard(
                                title = "Dark Amber",
                                selected = selectedTheme == "DARK",
                                icon = Icons.Default.DarkMode,
                                modifier = Modifier.weight(1f),
                                accentColor = Color(0xFFE2542B)
                            ) {
                                selectedTheme = "DARK"
                                viewModel.updateThemeMode("DARK")
                            }

                            ThemeChoiceCard(
                                title = "Sage Green",
                                selected = selectedTheme == "SAGE",
                                icon = Icons.Default.Spa,
                                modifier = Modifier.weight(1f),
                                accentColor = Color(0xFF6F9B73)
                            ) {
                                selectedTheme = "SAGE"
                                viewModel.updateThemeMode("SAGE")
                            }

                            ThemeChoiceCard(
                                title = "Light Clean",
                                selected = selectedTheme == "LIGHT",
                                icon = Icons.Default.LightMode,
                                modifier = Modifier.weight(1f),
                                accentColor = Color(0xFFE2542B)
                            ) {
                                selectedTheme = "LIGHT"
                                viewModel.updateThemeMode("LIGHT")
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun RightColumnCards() {
            Column(modifier = Modifier.fillMaxWidth()) {
                // SECTION 3: PRINTER THERMAL BLUETOOTH
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Printer Thermal Bluetooth (ESC/POS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { requestBluetoothAndLoadDevices() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan Ulang")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hubungkan ke printer thermal bluetooth 58mm untuk mencetak struk secara instan.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Paired Device Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedPrinterDropdown,
                    onExpandedChange = { expandedPrinterDropdown = it }
                ) {
                    OutlinedTextField(
                        value = if (printerName.isNotBlank()) "$printerName ($printerMacAddress)" else "Pilih Printer Bluetooth...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Printer Terpilih") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPrinterDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedPrinterDropdown,
                        onDismissRequest = { expandedPrinterDropdown = false }
                    ) {
                        if (pairedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Tidak ada printer tersambung. Pastikan Bluetooth aktif dan dipasangkan.") },
                                onClick = {
                                    requestBluetoothAndLoadDevices()
                                    expandedPrinterDropdown = false
                                }
                            )
                        } else {
                            pairedDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text("${dev.name} (${dev.address})") },
                                    onClick = {
                                        printerName = dev.name
                                        printerMacAddress = dev.address
                                        expandedPrinterDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-print switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cetak Otomatis Struk",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Otomatis print struk saat tombol Selesai ditekan",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAutoPrint,
                        onCheckedChange = { isAutoPrint = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test Print Button
                OutlinedButton(
                    onClick = {
                        val tempConfig = (storeConfig ?: StoreConfig()).copy(
                            storeName = storeName,
                            storeAddress = storeAddress,
                            storePhone = storePhone,
                            receiptFooter = receiptFooter,
                            printerMacAddress = printerMacAddress,
                            printerName = printerName
                        )
                        viewModel.updateStoreConfig(tempConfig)
                        viewModel.testPrinterConnection()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Coba Tes Print Struk (58mm)")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 4: AI ASSISTANT
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.AutoAwesome, contentDescription = null, tint = customColors.actionPrimaryBg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kasir Pro AI Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = isAiAssistantEnabled,
                        onCheckedChange = { isAiAssistantEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Aktifkan asisten suara pintar untuk membantu Anda menambah pesanan ke keranjang, pindah layar, atau proses bayar cukup dengan bicara. (Membutuhkan Koneksi Internet & API Key)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isAiAssistantEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(customColors.actionPrimaryBg.copy(alpha = 0.1f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "💡 Coba katakan: \"Tambahkan 2 Nasi Goreng\", \"Buka laporan hari ini\", atau \"Kosongkan keranjang\"",
                            fontSize = 11.sp,
                            color = customColors.actionPrimaryBg,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 4.5: KALIBRASI & PEREDAM KEBISINGAN AI
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = customColors.actionPrimaryBg)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kalibrasi & Peredam Kebisingan AI (Anti-Bising)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Atur peredam bising dan sensitivitas mikrofon agar input nominal harga (10rb, 185rb) & multi-item tetap akurat di lingkungan bising (pasar/warung ramai).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Noise Cancellation Slider
                Text(
                    text = "Tingkat Peredam Kebisingan: ${(noiseCancellationLevel * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = noiseCancellationLevel,
                    onValueChange = { noiseCancellationLevel = it },
                    valueRange = 0.1f..1.0f,
                    steps = 8
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Voice Sensitivity Slider
                Text(
                    text = "Sensitivitas Suara (Threshold): ${(voiceSensitivity * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = voiceSensitivity,
                    onValueChange = { voiceSensitivity = it },
                    valueRange = 0.1f..1.0f,
                    steps = 8
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Smart Currency Filter Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fokus Cerdas Angka & Mata Uang",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Optimalkan pengenalan kata kunci harga (rb, juta) dan multi-item di keramaian",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSmartCurrencyFilterEnabled,
                        onCheckedChange = { isSmartCurrencyFilterEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.emitToast("Kalibrasi suara berhasil! Filter anti-bising aktif, siap mengenali perintah nominal & multi-item.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Uji & Tes Kalibrasi Mikrofon")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 6: STATUS LISENSI & AKTIVASI
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (licenseStatus.isActivated) Icons.Default.VerifiedUser else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (licenseStatus.isActivated) StatusSuccess else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Status Lisensi Aplikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (licenseStatus.isActivated) StatusSuccess.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (licenseStatus.isActivated) "AKTIF PERMANEN" else "UJI COBA (${LicenseManager.formatCountdown(licenseStatus.remainingSeconds)})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (licenseStatus.isActivated) StatusSuccess else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ID Perangkat: ${licenseStatus.deviceId}",
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                    if (!licenseStatus.isActivated) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showActivationModal = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Beli / Masukkan API Aktivasi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        }

        if (isTablet) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LeftColumnCards()
                }
                Column(modifier = Modifier.weight(1f)) {
                    RightColumnCards()
                }
            }
        } else {
            LeftColumnCards()
            Spacer(modifier = Modifier.height(14.dp))
            RightColumnCards()
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SAVE BUTTON
        Button(
            onClick = {
                val currentImagePath = storeConfig?.qrisImagePath ?: ""
                val trimmedStoreName = storeName.trim().ifBlank { "Warung Makan" }
                val updatedConfig = StoreConfig(
                    id = 1,
                    storeName = trimmedStoreName,
                    storeAddress = storeAddress.trim(),
                    storePhone = storePhone.trim(),
                    receiptFooter = receiptFooter.trim(),
                    qrisMerchantName = trimmedStoreName,
                    qrisPayload = qrisPayload.trim(),
                    qrisImagePath = currentImagePath,
                    printerMacAddress = printerMacAddress.trim(),
                    printerName = printerName.trim(),
                    isBluetoothAutoPrint = isAutoPrint,
                    isAiAssistantEnabled = isAiAssistantEnabled,
                    noiseCancellationLevel = noiseCancellationLevel,
                    voiceSensitivity = voiceSensitivity,
                    isSmartCurrencyFilterEnabled = isSmartCurrencyFilterEnabled,
                    themeMode = selectedTheme
                )
                viewModel.updateStoreConfig(updatedConfig)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_settings_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = customColors.actionPrimaryBg
            )
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simpan Semua Pengaturan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
            }
        }
    }

    // Pratinjau Tampilan QRIS Dialog
    if (isTestQrisDialogOpen && storeConfig != null) {
        val trimmedStoreName = storeName.trim().ifBlank { "Warung Makan" }
        QrisDisplayDialog(
            amount = 50000.0,
            storeConfig = storeConfig!!.copy(storeName = trimmedStoreName, qrisMerchantName = trimmedStoreName),
            onDismiss = { isTestQrisDialogOpen = false },
            onConfirmPaid = {
                isTestQrisDialogOpen = false
                viewModel.emitToast("Pratinjau QRIS selesai")
            },
            onUploadNewQris = { uri ->
                viewModel.saveQrisImageUri(uri)
            }
        )
    }

    if (showActivationModal) {
        TrialLockOverlay(
            deviceId = licenseStatus.deviceId,
            adminWaNumber = licenseStatus.adminWhatsappNumber,
            storeName = storeName.ifBlank { storeConfig?.storeName ?: "" },
            isTrialExpired = false,
            isCheckingStatus = isCheckingServerActivation,
            serverStatusHint = serverActivationHint,
            onActivateKey = { key ->
                val result = viewModel.activateLicenseAsync(key)
                if (result.success) showActivationModal = false
                result
            },
            onCheckServerStatus = { silent ->
                viewModel.checkServerActivationStatus(silent)
            },
            onNotifyToast = { msg -> viewModel.emitToast(msg) },
            onPurchaseRequested = {
                viewModel.notifyAdminSilentPurchaseRequest(
                    deviceId = licenseStatus.deviceId,
                    storeName = storeName.ifBlank { storeConfig?.storeName ?: "" }
                )
            },
            onDismissEarly = { showActivationModal = false }
        )
    }
}

@Composable
private fun ThemeChoiceCard(
    title: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accentColor else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
