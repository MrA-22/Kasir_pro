package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun ReceiptBarcodeScannerDialog(
    onDismiss: () -> Unit,
    onLookupBarcode: suspend (String) -> Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequestedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var lastUnmatchedCode by remember { mutableStateOf<String?>(null) }
    var lastScannedText by remember { mutableStateOf("") }
    var lastScannedTimeMs by remember { mutableLongStateOf(0L) }
    var manualInvoiceQuery by remember { mutableStateOf("") }
    var isProcessingScan by remember { mutableStateOf(false) }
    val scanSuccessFlag = remember { AtomicBoolean(false) }

    fun handleDetectedValue(rawValue: String) {
        val clean = rawValue.trim()
        if (clean.isBlank() || scanSuccessFlag.get() || isProcessingScan) return

        val now = System.currentTimeMillis()
        if (clean == lastScannedText && (now - lastScannedTimeMs) < 1800L) {
            return
        }
        lastScannedText = clean
        lastScannedTimeMs = now

        isProcessingScan = true
        coroutineScope.launch {
            val found = onLookupBarcode(clean)
            if (found) {
                scanSuccessFlag.set(true)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                lastUnmatchedCode = clean
            }
            isProcessingScan = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0D0B))
        ) {
            if (hasCameraPermission) {
                val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                val barcodeScanner = remember {
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39,
                            Barcode.FORMAT_CODE_93,
                            Barcode.FORMAT_CODABAR,
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_QR_CODE
                        )
                        .build()
                    BarcodeScanning.getClient(options)
                }

                var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

                DisposableEffect(Unit) {
                    onDispose {
                        try {
                            cameraProviderRef?.unbindAll()
                        } catch (_: Exception) {
                        }
                        try {
                            barcodeScanner.close()
                        } catch (_: Exception) {
                        }
                        analysisExecutor.shutdown()
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }

                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                cameraProviderRef = provider

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val resolutionSelector = ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            android.util.Size(1280, 720),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .build()

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(resolutionSelector)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !scanSuccessFlag.get()) {
                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                val firstMatch = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                                firstMatch?.rawValue?.let { code ->
                                                    ContextCompat.getMainExecutor(ctx).execute {
                                                        handleDetectedValue(code)
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                boundCamera = camera
                                cameraError = null
                            } catch (e: Exception) {
                                cameraError = "Kamera tidak dapat dimulai: ${e.localizedMessage ?: "Periksa perangkat Anda"}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // Dark vignette overlay with clear viewfinder cutout and laser beam
                ScannerViewfinderOverlay()
            } else {
                // Permission Denied / Not Granted State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 420.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF18201B),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1D6F42).copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Izin Kamera Diperlukan",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aplikasi membutuhkan akses kamera biasa untuk membaca barcode pada struk belanja secara langsung.",
                                fontSize = 13.sp,
                                color = Color(0xFFB8C4BC),
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_camera_permission_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text("Izinkan Kamera", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            if (permissionRequestedOnce) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Buka Pengaturan Izin", color = Color(0xFFA5D6A7))
                                }
                            }
                        }
                    }
                }
            }

            // Top & Bottom Floating Scanner Controls (Respecting safeDrawing insets)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.62f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF66BB6A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Scan Barcode Struk",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Arahkan kamera ke barcode di bawah nota",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD0DBD4)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasCameraPermission && boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                            FilledTonalIconButton(
                                onClick = {
                                    val next = !isTorchOn
                                    isTorchOn = next
                                    boundCamera?.cameraControl?.enableTorch(next)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("scanner_torch_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isTorchOn) Color(0xFF2E7D32) else Color.Black.copy(alpha = 0.62f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = if (isTorchOn) "Matikan Lampu Kilat" else "Nyalakan Lampu Kilat"
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("scanner_close_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.62f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Pemindai"
                            )
                        }
                    }
                }

                // Bottom Status & Manual Invoice Search Fallback
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (cameraError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFB71C1C).copy(alpha = 0.9f),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = cameraError!!,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (lastUnmatchedCode != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF3E2723).copy(alpha = 0.92f),
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "Barcode \"$lastUnmatchedCode\" tidak cocok dengan struk tersimpan.",
                                color = Color(0xFFFFE0B2),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Manual fallback search card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF141B17).copy(alpha = 0.94f),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualInvoiceQuery,
                                onValueChange = { manualInvoiceQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_invoice_input"),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = "Atau ketik No. Nota (cth: INV-...)",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (manualInvoiceQuery.isNotBlank()) {
                                            handleDetectedValue(manualInvoiceQuery)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF66BB6A),
                                    unfocusedBorderColor = Color(0xFF3A4740),
                                    focusedPlaceholderColor = Color(0xFF8FA096),
                                    unfocusedPlaceholderColor = Color(0xFF8FA096)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (manualInvoiceQuery.isNotBlank()) {
                                        handleDetectedValue(manualInvoiceQuery)
                                    }
                                },
                                enabled = manualInvoiceQuery.isNotBlank() && !isProcessingScan,
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("manual_invoice_search_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Cari Nota",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cek", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerViewfinderOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanLaser")
    val laserFraction by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPosition"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawRect(color = Color.Black.copy(alpha = 0.56f))

        val boxWidth = (size.width * 0.80f).coerceAtMost(380.dp.toPx())
        val boxHeight = (boxWidth * 0.55f).coerceIn(140.dp.toPx(), 230.dp.toPx())
        val left = (size.width - boxWidth) / 2f
        val top = (size.height - boxHeight) / 2f
        val cornerRadius = 18.dp.toPx()

        // Punch transparent scan window
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            blendMode = BlendMode.Clear
        )

        // Subtle inner guide border
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )

        // Bright Emerald Corner Brackets
        val bracketLen = 28.dp.toPx()
        val strokeW = 4.dp.toPx()
        val bracketColor = Color(0xFF66BB6A)

        // Top-left
        drawLine(bracketColor, Offset(left, top + bracketLen), Offset(left, top + cornerRadius * 0.4f), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + cornerRadius * 0.4f, top), Offset(left + bracketLen, top), strokeW, StrokeCap.Round)
        // Top-right
        drawLine(bracketColor, Offset(left + boxWidth - bracketLen, top), Offset(left + boxWidth - cornerRadius * 0.4f, top), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + boxWidth, top + cornerRadius * 0.4f), Offset(left + boxWidth, top + bracketLen), strokeW, StrokeCap.Round)
        // Bottom-left
        drawLine(bracketColor, Offset(left, top + boxHeight - bracketLen), Offset(left, top + boxHeight - cornerRadius * 0.4f), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + cornerRadius * 0.4f, top + boxHeight), Offset(left + bracketLen, top + boxHeight), strokeW, StrokeCap.Round)
        // Bottom-right
        drawLine(bracketColor, Offset(left + boxWidth - bracketLen, top + boxHeight), Offset(left + boxWidth - cornerRadius * 0.4f, top + boxHeight), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + boxWidth, top + boxHeight - bracketLen), Offset(left + boxWidth, top + boxHeight - cornerRadius * 0.4f), strokeW, StrokeCap.Round)

        // Animated laser line inside scan window
        val laserY = top + (boxHeight * laserFraction)
        drawLine(
            color = Color(0xFF4CAF50).copy(alpha = 0.9f),
            start = Offset(left + 16.dp.toPx(), laserY),
            end = Offset(left + boxWidth - 16.dp.toPx(), laserY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
