package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.StoreConfig
import com.example.data.model.TransactionWithItems
import com.example.util.Code128Encoder
import com.example.util.CurrencyUtils

@Composable
fun ReceiptDialog(
    transactionWithItems: TransactionWithItems,
    storeConfig: StoreConfig,
    onDismiss: () -> Unit,
    onPrintThermal: () -> Unit,
    onPrintSystem: (Context) -> Unit,
    onShareImage: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tx = transactionWithItems.transaction
    val items = transactionWithItems.items
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Hapus Struk Transaksi?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Struk ${tx.invoiceNumber} senilai ${CurrencyUtils.formatRupiah(tx.totalAmount)} akan dihapus permanen. Stok produk terkait akan otomatis dikembalikan.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Ya, Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar with Delete & Close Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Nota",
                            tint = Color(0xFFD35400),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nota Pembayaran",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) {
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus Struk",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Realistic Paper Receipt View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Store Header
                        Text(
                            text = storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111111),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (storeConfig.storeAddress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = storeConfig.storeAddress,
                                fontSize = 12.sp,
                                color = Color(0xFF444444),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (storeConfig.storePhone.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Telp / WA: ${storeConfig.storePhone}",
                                fontSize = 12.sp,
                                color = Color(0xFF444444),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ReceiptDashedDivider()

                        // 2. Transaction Meta
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No. Nota : ${tx.invoiceNumber}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111111)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tanggal  : ${CurrencyUtils.formatDateTime(tx.timestamp)}",
                                fontSize = 12.sp,
                                color = Color(0xFF555555)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val paymentName = if (tx.paymentMethod == "TUNAI") "Tunai" else "QRIS"
                            Text(
                                text = "Metode   : $paymentName",
                                fontSize = 12.sp,
                                color = Color(0xFF555555)
                            )
                            if (tx.customerName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Pelanggan: ${tx.customerName}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF555555)
                                )
                            }
                            if (tx.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Catatan  : ${tx.note}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF555555)
                                )
                            }
                        }

                        ReceiptDashedDivider()

                        // 3. Table Column Headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "PRODUK / ITEM",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111111)
                            )
                            Text(
                                text = "SUBTOTAL",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111111)
                            )
                        }

                        ReceiptDashedDivider()

                        // 4. Items List
                        items.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = item.productName.uppercase(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111111),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Text(
                                        text = CurrencyUtils.formatRupiah(item.subtotal),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111111)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.quantity} x ${CurrencyUtils.formatRupiah(item.pricePerUnit)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF666666)
                                    )
                                    if (item.discountValue > 0) {
                                        val discText = if (item.isDiscountPercentage) "Disc ${item.discountValue.toInt()}%" else "Disc ${CurrencyUtils.formatRupiah(item.discountValue)}"
                                        Text(
                                            text = "($discText)",
                                            fontSize = 10.sp,
                                            color = Color(0xFFD35400)
                                        )
                                    }
                                }
                            }
                        }

                        ReceiptDashedDivider()

                        // 5. Total Block (with Warm Orange Highlight)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL TRANSAKSI",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD35400),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyUtils.formatRupiah(tx.totalAmount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD35400)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (tx.paymentMethod == "TUNAI") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tunai Diterima",
                                    fontSize = 12.sp,
                                    color = Color(0xFF333333)
                                )
                                Text(
                                    text = CurrencyUtils.formatRupiah(tx.amountReceived),
                                    fontSize = 12.sp,
                                    color = Color(0xFF333333)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Kembalian",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111111)
                                )
                                Text(
                                    text = CurrencyUtils.formatRupiah(tx.changeAmount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111111)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Status Pembayaran",
                                    fontSize = 12.sp,
                                    color = Color(0xFF333333)
                                )
                                Text(
                                    text = "LUNAS (QRIS)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        ReceiptDashedDivider()

                        // 6. Footer (Greeting, Barcode Graphic, Safe Transaction Note)
                        val footerGreeting = storeConfig.receiptFooter.ifBlank { "Terima kasih atas kunjungan Anda!" }
                        Text(
                            text = footerGreeting,
                            fontSize = 12.sp,
                            color = Color(0xFF111111),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Visual Barcode Graphic (real scannable Code-128B encoding invoiceNumber)
                        ReceiptBarcodeView(data = tx.invoiceNumber)

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Simpan nota ini sebagai bukti transaksi yang sah",
                            fontSize = 10.sp,
                            color = Color(0xFF777777),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Bluetooth & Foto Nota
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPrintThermal,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Bluetooth", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = onShareImage,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Foto Nota", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // System Print & Done Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPrintSystem(context) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cetak Sistem", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Selesai", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptDashedDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(2.dp)
    ) {
        drawLine(
            color = Color(0xFF9E9E9E),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
        )
    }
}

@Composable
private fun ReceiptBarcodeView(data: String) {
    val sanitized = remember(data) { Code128Encoder.sanitizeCode128BText(data) }
    val pattern = remember(sanitized) { Code128Encoder.encodeToBarWidths(sanitized) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val totalModules = pattern.sum()
            val quietZoneModules = 20 // 10 modules on each side for reliable camera scanning
            val fullModules = totalModules + quietZoneModules
            val maxBarcodeWidth = size.width * 0.92f
            val unitWidth = (maxBarcodeWidth / fullModules).coerceAtLeast(1.0f)
            val actualWidth = totalModules * unitWidth
            var x = (size.width - actualWidth) / 2f

            pattern.forEachIndexed { index, weight ->
                val barW = weight * unitWidth
                if (index % 2 == 0) {
                    drawRect(
                        color = Color(0xFF111111),
                        topLeft = Offset(x, 0f),
                        size = Size(barW, size.height)
                    )
                }
                x += barW
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = sanitized,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
    }
}
