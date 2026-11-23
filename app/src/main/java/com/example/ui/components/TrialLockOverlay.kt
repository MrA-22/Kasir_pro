package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.util.LicenseManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrialCountdownBanner(
    remainingSeconds: Int,
    onActivateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUrgent = remainingSeconds <= 60
    val accentColor = if (isUrgent) StatusWarning else MaterialTheme.colorScheme.primary
    val formattedTime = LicenseManager.formatCountdown(remainingSeconds)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isUrgent) Icons.Default.Warning else Icons.Default.Schedule,
                    contentDescription = "Sisa Waktu Uji Coba",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Uji Coba Gratis • $formattedTime",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isUrgent) "Waktu hampir habis! Segera masukkan API aktivasi." else "Semua fitur aktif selama masa percobaan 5 menit.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onActivateClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("banner_activate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Aktivasi",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun TrialLockOverlay(
    deviceId: String,
    adminWaNumber: String,
    storeName: String,
    isTrialExpired: Boolean,
    isCheckingStatus: Boolean = false,
    serverStatusHint: String? = null,
    onActivateKey: suspend (String) -> LicenseManager.ActivationResult,
    onCheckServerStatus: (silent: Boolean) -> Unit = {},
    onNotifyToast: (String) -> Unit,
    onPurchaseRequested: () -> Unit = {},
    onDismissEarly: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val customColors = LocalCustomColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var apiKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isActivating by remember { mutableStateOf(false) }
    var showManualKeyInput by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) {
        while (true) {
            onCheckServerStatus(true)
            delay(5000L)
        }
    }

    fun submitActivation() {
        if (isActivating) return
        keyboardController?.hide()
        if (apiKeyInput.isBlank()) {
            errorMessage = "Harap masukkan Kode API terlebih dahulu."
            return
        }
        isActivating = true
        errorMessage = null
        coroutineScope.launch {
            val result = onActivateKey(apiKeyInput)
            isActivating = false
            if (!result.success) {
                errorMessage = result.message
            }
        }
    }

    val primaryGlow = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    Dialog(
        onDismissRequest = {
            if (!isTrialExpired && onDismissEarly != null) {
                onDismissEarly()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isTrialExpired,
            dismissOnClickOutside = !isTrialExpired,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Blokir tombol Back sistem saat masa uji coba habis
        if (isTrialExpired) {
            BackHandler(enabled = true) { /* Tidak bisa ditutup */ }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.22f),
                                bgColor.copy(alpha = 0.96f),
                                bgColor
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.16f),
                            radius = size.maxDimension * 0.72f
                        )
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!isTrialExpired && onDismissEarly != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismissEarly,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(customColors.subCardBg)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Lock Icon Badge
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    primaryGlow.copy(alpha = 0.25f),
                                    primaryGlow.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTrialExpired) Icons.Default.Lock else Icons.Default.VerifiedUser,
                        contentDescription = if (isTrialExpired) "Aplikasi Terkunci" else "Aktivasi Lisensi",
                        tint = primaryGlow,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isTrialExpired) "Masa Uji Coba 5 Menit Berakhir" else "Aktivasi Lisensi Kasir Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isTrialExpired) {
                        "Waktu percobaan aplikasi telah habis. Seluruh data menu & transaksi Anda tetap aman tersimpan. Tekan Beli via WhatsApp — begitu Admin mengaktifkan dari website, aplikasi ini akan langsung terbuka otomatis."
                    } else {
                        "Aktifkan akses permanen Kasir Pro melalui WhatsApp Admin. Begitu pembayaran dikonfirmasi di panel website Admin, aplikasi terbuka otomatis."
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Card 1: ID Perangkat Pembeli
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. ID Perangkat Anda (Kirim ke Admin)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(customColors.subCardBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = deviceId,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = customColors.heroAmountColor,
                                letterSpacing = 1.sp
                            )
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "ID Perangkat Kasir Pro", deviceId)
                                    onNotifyToast("ID Perangkat $deviceId disalin!")
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_device_id_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Salin ID Perangkat",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Salin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // WhatsApp Purchase Button
                        val waGreen = Color(0xFF25D366)
                        Button(
                            onClick = {
                                onPurchaseRequested()
                                val waMessage = LicenseManager.buildWhatsAppPurchaseMessage(deviceId, storeName)
                                val url = "https://wa.me/$adminWaNumber?text=${Uri.encode(waMessage)}"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    copyToClipboard(context, "Pesan Pembelian Kasir Pro", waMessage)
                                    onNotifyToast("WhatsApp tidak ditemukan. Pesan & ID Perangkat disalin ke clipboard!")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("buy_whatsapp_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = waGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = Color(0xFF0A2E17),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Beli App & Aktifkan via WhatsApp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0A2E17)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Klik tombol di atas untuk mengajukan aktivasi ke Admin. Tidak perlu memasukkan kode secara manual jika diaktifkan via website.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card 2: Aktivasi Otomatis dari Panel Website Admin + Fallback Manual Opsional
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. Aktivasi Otomatis (Tanpa Ketik Kode)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(customColors.subCardBg)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCheckingStatus) {
                                CircularProgressIndicator(
                                    color = primaryGlow,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = primaryGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = serverStatusHint?.takeIf { it.isNotBlank() }
                                    ?: "Memeriksa status aktivasi otomatis setiap 5 detik...",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onCheckServerStatus(false) },
                            enabled = !isCheckingStatus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("check_auto_activation_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.actionPrimaryBg
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCheckingStatus) "Memeriksa Server..." else "Cek Status Aktivasi Sekarang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { showManualKeyInput = !showManualKeyInput },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = if (showManualKeyInput) "Sembunyikan Input Kode Manual" else "Atau masukkan Kode API manual",
                                fontSize = 11.sp
                            )
                        }

                        AnimatedVisibility(visible = showManualKeyInput) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { rawInput ->
                                        apiKeyInput = LicenseManager.extractLicenseKeyFromText(rawInput) ?: rawInput.uppercase()
                                        errorMessage = null
                                    },
                                    label = { Text("Kode API Khusus") },
                                    placeholder = { Text("KPRO-XXXX-XXXX-XXXX") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = primaryGlow
                                        )
                                    },
                                    isError = errorMessage != null,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submitActivation() }
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("license_key_input")
                                )

                                AnimatedVisibility(
                                    visible = errorMessage != null,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = StatusError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = errorMessage ?: "",
                                            fontSize = 11.sp,
                                            color = StatusError,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = { submitActivation() },
                                    enabled = apiKeyInput.isNotBlank() && !isActivating,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("activate_license_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isActivating) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Memverifikasi...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Aktifkan dengan Kode Manual", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        if (!isTrialExpired && onDismissEarly != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = onDismissEarly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text("Lanjutkan Uji Coba Gratis", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "1 Lisensi berlaku permanen untuk 1 perangkat (${deviceId}).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
