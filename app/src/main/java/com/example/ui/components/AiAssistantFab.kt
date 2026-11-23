package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.LocalCustomColors
import com.example.ui.viewmodel.KasirViewModel
import kotlin.math.roundToInt

@Composable
fun AiAssistantFab(
    viewModel: KasirViewModel,
    modifier: Modifier = Modifier
) {
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val aiLastResponse by viewModel.aiLastResponse.collectAsStateWithLifecycle()
    val isContinuousListening by viewModel.isContinuousListening.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current
    val context = LocalContext.current

    var isExpanded by remember { mutableStateOf(false) }
    var hasUserManuallyClosed by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showFullGuideDialog by remember { mutableStateOf(false) }

    // Position offset for dragging the mic button anywhere on screen
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Pulsing animations for visual indicator when listening vs processing
    val infiniteTransition = rememberInfiniteTransition(label = "AiStatePulse")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val pulseBorderWidth = (1.5f + (2.0f * pulseAlpha)).dp

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasUserManuallyClosed = false
            viewModel.toggleContinuousListening()
        } else {
            viewModel.emitToast("Izin mikrofon diperlukan untuk perintah suara.")
        }
    }

    // Auto expand card when speaking, thinking, lastResponse, or error arrives
    LaunchedEffect(voiceState.isSpeaking, isAiThinking, aiLastResponse, voiceState.error) {
        if (!hasUserManuallyClosed) {
            if (voiceState.isSpeaking || isAiThinking || aiLastResponse != null || voiceState.error != null) {
                isExpanded = true
            }
        }
    }

    // Contextual voice commands based on current screen
    val (screenTitle, sampleCommands) = when (currentScreen) {
        "KASIR" -> Pair(
            "Kasir",
            listOf(
                "💡 Panduan Suara",
                "Tambah ayam goreng 2",
                "Tambah 2 es teh 1 nasi goreng",
                "Ubah harga ayam goreng 20rb ubah modal ayam geprek 5rb",
                "Kurangi 1 es teh",
                "Bayar cash 50rb",
                "Bayar cash pas",
                "Ubah harga ayam geprek 10rb ayam goreng 25rb"
            )
        )
        "MENU" -> Pair(
            "Menu & Stok",
            listOf(
                "💡 Panduan Suara",
                "Ubah harga ayam goreng 20rb ubah modal ayam geprek 5rb",
                "Tambah menu es jeruk kategori minuman harga 7rb",
                "Ubah kategori ayam geprek ke makanan",
                "Ubah nama stok ayam geprek jadi ayam gepuk",
                "Tambah stok ayam goreng 50",
                "Ubah harga ayam geprek 20rb modal 10rb"
            )
        )
        "LAPORAN" -> Pair(
            "Laporan",
            listOf(
                "💡 Panduan Suara",
                "Buka laporan tanggal 15",
                "Hapus transaksi 25 ribu",
                "Ekspor laporan Excel",
                "Ekspor laporan PDF",
                "Ekspor laporan CSV"
            )
        )
        "HITUNG" -> Pair(
            "Hitung Cepat",
            listOf(
                "💡 Panduan Suara",
                "Bayar tunai 100 ribu",
                "15000 tambah 25000",
                "Buka kasir"
            )
        )
        "SETTINGS" -> Pair(
            "Pengaturan",
            listOf(
                "💡 Panduan Suara",
                "Ubah nama toko Ayam Geprek Bunda",
                "Ubah alamat toko Jalan Mawar No 12",
                "Ubah nomor hp 08123456789",
                "Mode sage",
                "Mode gelap"
            )
        )
        else -> Pair(
            "Beranda",
            listOf(
                "💡 Panduan Suara",
                "Buka kasir",
                "Buka laporan",
                "Buka menu",
                "Ubah tema mode sage"
            )
        )
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
        ) {
            // Expanded AI Card Bubble
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                // Card Border dynamically changes color & pulses based on state
                val cardBorder = when {
                    isAiThinking -> BorderStroke(
                        pulseBorderWidth,
                        customColors.actionPrimaryBg.copy(alpha = pulseAlpha)
                    )
                    voiceState.isSpeaking || isContinuousListening -> BorderStroke(
                        pulseBorderWidth,
                        Color(0xFFFF1744).copy(alpha = pulseAlpha)
                    )
                    else -> BorderStroke(1.dp, customColors.borderStroke)
                }

                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .widthIn(max = 320.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = customColors.heroCardBg
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = cardBorder
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with Drag Handle & Dynamic Status Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            offsetX += dragAmount.x
                                            offsetY += dragAmount.y
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Geser tombol",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = customColors.actionPrimaryBg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Kasir Pro AI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.actionPrimaryBg
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Status Chip Badge
                                when {
                                    isAiThinking -> {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(customColors.actionPrimaryBg)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .scale(pulseScale)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "MEMPROSES...",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    voiceState.isSpeaking || isContinuousListening -> {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFF1744))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .scale(pulseScale)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (voiceState.isSpeaking) "MENDENGARKAN" else "HANDS-FREE ON",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        hasUserManuallyClosed = true
                                        isExpanded = false
                                        viewModel.stopAllVoiceAndCloseAi()
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Content / Speech Recognition Live Feedback
                        when {
                            isAiThinking -> {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = customColors.actionPrimaryBg,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sedang memahami perintah suara Anda...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = customColors.actionPrimaryBg
                                        )
                                    }
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .height(3.dp)
                                            .clip(CircleShape),
                                        color = customColors.actionPrimaryBg
                                    )
                                }
                            }
                            voiceState.isSpeaking -> {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .scale(pulseScale)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF1744))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Silakan bicara sekarang...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF1744)
                                        )
                                    }
                                    if (voiceState.spokenText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = Color(0xFFFF1744).copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "\"${voiceState.spokenText}\"",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            voiceState.error != null -> {
                                Text(
                                    text = "⚠️ ${voiceState.error}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    lineHeight = 16.sp
                                )
                            }
                            aiLastResponse != null -> {
                                Text(
                                    text = aiLastResponse ?: "",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            else -> {
                                Text(
                                    text = if (isContinuousListening) 
                                        "🎙️ Mode Hands-Free Aktif! Bicara kapan saja tanpa tekan tombol lagi." 
                                    else 
                                        "Tekan mikrofon sekali untuk mengaktifkan mode suara terus-menerus.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Command Suggestion Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sampleCommands) { cmd ->
                                val isHelpBtn = cmd.contains("Panduan Suara")
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isHelpBtn) customColors.actionPrimaryBg.copy(alpha = 0.15f) else customColors.subCardBg)
                                        .clickable {
                                            if (isHelpBtn) {
                                                showFullGuideDialog = true
                                            } else {
                                                hasUserManuallyClosed = false
                                                viewModel.submitAiCommand(cmd)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = cmd,
                                        fontSize = 10.sp,
                                        fontWeight = if (isHelpBtn) FontWeight.Bold else FontWeight.Medium,
                                        color = customColors.actionPrimaryBg
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Manual Text Input Fallback Field
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Ketik perintah...", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        hasUserManuallyClosed = false
                                        viewModel.submitAiCommand(textInput)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(customColors.actionPrimaryBg)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Kirim Perintah",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Draggable Floating Action Button (FAB) with Pulsing Outer Glowing Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
            ) {
                // Outer Pulsing Halo/Ring when Listening or Processing
                if (isAiThinking || voiceState.isSpeaking || isContinuousListening) {
                    val ringColor = if (isAiThinking) customColors.actionPrimaryBg else Color(0xFFFF1744)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = pulseAlpha * 0.35f))
                    )
                }

                FloatingActionButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            if (isContinuousListening || voiceState.isSpeaking) {
                                hasUserManuallyClosed = true
                                isExpanded = false
                                viewModel.stopAllVoiceAndCloseAi()
                            } else {
                                hasUserManuallyClosed = false
                                isExpanded = true
                                viewModel.toggleContinuousListening()
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    containerColor = if (isContinuousListening || voiceState.isSpeaking) Color(0xFFFF1744) else customColors.actionPrimaryBg,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isContinuousListening || voiceState.isSpeaking) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "AI Assistant Continuous Voice Mode"
                    )
                }
            }
        }
    }
}
