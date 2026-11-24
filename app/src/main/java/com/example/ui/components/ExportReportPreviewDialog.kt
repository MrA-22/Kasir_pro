package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.data.model.TransactionWithItems
import com.example.util.CurrencyUtils

/**
 * Pilihan format file ekspor laporan.
 */
enum class ExportFileFormat(
    val label: String,
    val badgeLabel: String,
    val description: String,
    val icon: ImageVector
) {
    EXCEL(
        label = "Excel (.xlsx)",
        badgeLabel = "XLSX Rapi",
        description = "3 Sheet: Ringkasan, Transaksi, Terlaris + Rumus SUM",
        icon = Icons.Default.GridOn
    ),
    PDF(
        label = "PDF (.pdf)",
        badgeLabel = "Siap Cetak A4",
        description = "Format cetak resmi / kirim dokumen ke WhatsApp",
        icon = Icons.Default.PictureAsPdf
    ),
    CSV(
        label = "CSV (.csv)",
        badgeLabel = "UTF-8 Standar",
        description = "Format ringkas untuk Excel Mobile & Google Sheets",
        icon = Icons.Default.TableChart
    )
}

// Skema Warna Hijau Spreadsheet Klasik (Independen dari tema aplikasi)
private val ExcelPrimaryGreen = Color(0xFF1D6F42)
private val ExcelDeepGreen = Color(0xFF0E5A30)
private val ExcelSoftMint = Color(0xFFE8F5E9)
private val ExcelCardBg = Color(0xFFF6FAF7)
private val ExcelBorderGreen = Color(0xFFC8E6C9)
private val ExcelTextDark = Color(0xFF142417)
private val ExcelTextMuted = Color(0xFF4A6150)
private val ExcelZebraOdd = Color(0xFFF4F9F5)

/**
 * Dialog Pratinjau Ekspor Laporan bernuansa hijau spreadsheet klasik (Excel style).
 * Tampilan dirancang konsisten dalam warna hijau elegan tanpa terpengaruh tema aplikasi.
 */
@Composable
fun ExportReportPreviewDialog(
    initialFormat: ExportFileFormat,
    reportTitle: String,
    storeConfig: StoreConfig,
    transactions: List<TransactionWithItems>,
    products: List<Product>,
    onDismiss: () -> Unit,
    onConfirmExport: (ExportFileFormat) -> Unit
) {
    var selectedFormat by remember(initialFormat) { mutableStateOf(initialFormat) }

    val totalOmset = remember(transactions) { transactions.sumOf { it.transaction.totalAmount } }
    val totalItems = remember(transactions) { transactions.sumOf { it.items.sumOf { i -> i.quantity } } }
    val totalTunai = remember(transactions) {
        transactions.filter { it.transaction.paymentMethod == "TUNAI" }.sumOf { it.transaction.totalAmount }
    }
    val totalQris = remember(transactions) {
        transactions.filter { it.transaction.paymentMethod == "QRIS" }.sumOf { it.transaction.totalAmount }
    }
    val totalLaba = remember(transactions, products) {
        val costMap = products.associateBy { it.id }
        transactions.sumOf { txWithItems ->
            txWithItems.items.sumOf { item ->
                item.subtotal - (item.quantity * (costMap[item.productId]?.costPrice ?: 0.0))
            }
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
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .heightIn(max = 780.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Header Banner Hijau Emerald Elegan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ExcelDeepGreen, ExcelPrimaryGreen)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pratinjau Ekspor Laporan",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()} • Periode: $reportTitle",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 2. Isi Scrollable: Pilihan Format + Ringkasan KPI + Tabel Spreadsheet Hijau
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFFAFCFA)),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Pilih Format Berkas
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Pilih Format Berkas",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ExcelTextDark
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ExportFileFormat.values().forEach { fmt ->
                                        FormatOptionCard(
                                            format = fmt,
                                            isSelected = selectedFormat == fmt,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedFormat = fmt }
                                        )
                                    }
                                }
                            }
                        }

                        // Ringkasan KPI Hijau
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ExcelCardBg)
                                    .border(BorderStroke(1.dp, ExcelBorderGreen), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Ringkasan Penjualan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ExcelDeepGreen
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GreenKpiPill(
                                        title = "Total Omset",
                                        value = CurrencyUtils.formatRupiah(totalOmset),
                                        modifier = Modifier.weight(1f)
                                    )
                                    GreenKpiPill(
                                        title = "Total Transaksi",
                                        value = "${transactions.size} Struk",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GreenKpiPill(
                                        title = "Item Terjual",
                                        value = "$totalItems Porsi/Pcs",
                                        modifier = Modifier.weight(1f)
                                    )
                                    GreenKpiPill(
                                        title = "Estimasi Laba",
                                        value = CurrencyUtils.formatRupiah(totalLaba),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Pratinjau Lembar Kerja Spreadsheet Hijau
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, ExcelBorderGreen), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ExcelPrimaryGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Pratinjau Lembar Kerja (Excel Style)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ExcelTextDark
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${transactions.size} Baris Data",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ExcelPrimaryGreen
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Spreadsheet Table Horizontal Scroll
                                val scrollState = rememberScrollState()
                                val colNoWidth = 44.dp
                                val colTimeWidth = 84.dp
                                val colInvoiceWidth = 110.dp
                                val colMethodWidth = 84.dp
                                val colItemsWidth = 140.dp
                                val colTotalWidth = 110.dp

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, ExcelBorderGreen), RoundedCornerShape(8.dp))
                                        .horizontalScroll(scrollState)
                                ) {
                                    Column {
                                        // Header Baris Excel Hijau
                                        Row(
                                            modifier = Modifier
                                                .background(ExcelPrimaryGreen)
                                                .padding(vertical = 8.dp)
                                        ) {
                                            TableCellHeader("No", colNoWidth, TextAlign.Center)
                                            TableCellHeader("Waktu", colTimeWidth, TextAlign.Center)
                                            TableCellHeader("No Struk", colInvoiceWidth, TextAlign.Left)
                                            TableCellHeader("Metode", colMethodWidth, TextAlign.Center)
                                            TableCellHeader("Rincian Item", colItemsWidth, TextAlign.Left)
                                            TableCellHeader("Total", colTotalWidth, TextAlign.Right)
                                        }

                                        if (transactions.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Belum ada riwayat transaksi pada periode ini.",
                                                    fontSize = 12.sp,
                                                    color = ExcelTextMuted
                                                )
                                            }
                                        } else {
                                            transactions.take(25).forEachIndexed { idx, txWithItems ->
                                                val tx = txWithItems.transaction
                                                val isEven = idx % 2 == 0
                                                val rowBg = if (isEven) Color.White else ExcelZebraOdd
                                                val itemSummary = txWithItems.items.joinToString(", ") {
                                                    "${it.productName} (${it.quantity})"
                                                }.ifBlank { "-" }

                                                Row(
                                                    modifier = Modifier
                                                        .background(rowBg)
                                                        .border(
                                                            BorderStroke(
                                                                0.5.dp,
                                                                Color(0xFFE0EAE2)
                                                            )
                                                        )
                                                        .padding(vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TableCellData("${idx + 1}", colNoWidth, TextAlign.Center)
                                                    TableCellData(CurrencyUtils.formatTimeOnly(tx.timestamp), colTimeWidth, TextAlign.Center)
                                                    TableCellData(tx.invoiceNumber, colInvoiceWidth, TextAlign.Left, isMonospace = true)
                                                    TableCellData(tx.paymentMethod, colMethodWidth, TextAlign.Center, isBadge = true)
                                                    TableCellData(itemSummary, colItemsWidth, TextAlign.Left)
                                                    TableCellData(CurrencyUtils.formatRupiah(tx.totalAmount), colTotalWidth, TextAlign.Right, isBold = true, isGreen = true)
                                                }
                                            }

                                            // Total Footer Row
                                            Row(
                                                modifier = Modifier
                                                    .background(ExcelSoftMint)
                                                    .border(BorderStroke(1.dp, ExcelBorderGreen))
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(colNoWidth + colTimeWidth + colInvoiceWidth + colMethodWidth + colItemsWidth)
                                                        .padding(horizontal = 8.dp)
                                                ) {
                                                    Text(
                                                        text = "TOTAL KESELURUHAN OMSET",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ExcelDeepGreen
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .width(colTotalWidth)
                                                        .padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Text(
                                                        text = CurrencyUtils.formatRupiah(totalOmset),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = ExcelDeepGreen
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (transactions.size > 25) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "* Pratinjau menampilkan 25 transaksi pertama. File hasil ekspor menyertakan seluruh ${transactions.size} transaksi lengkap.",
                                        fontSize = 10.5.sp,
                                        color = ExcelTextMuted
                                    )
                                }
                            }
                        }
                    }

                    // 3. Tombol Aksi Bawah
                    HorizontalDivider(color = Color(0xFFE2EBE4))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFD0DDD3)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ExcelTextMuted
                            )
                        ) {
                            Text("Batal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { onConfirmExport(selectedFormat) },
                            modifier = Modifier
                                .weight(1.6f)
                                .minimumInteractiveComponentSize()
                                .testTag("confirm_export_preview_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExcelPrimaryGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ekspor & Bagikan ${selectedFormat.label}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    format: ExportFileFormat,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) ExcelPrimaryGreen else Color(0xFFD5E3D8)
    val bgColor = if (isSelected) ExcelSoftMint else Color.White

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = format.icon,
                contentDescription = null,
                tint = if (isSelected) ExcelPrimaryGreen else ExcelTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = format.badgeLabel,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) ExcelDeepGreen else ExcelTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GreenKpiPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE2EBE4)), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 10.5.sp,
                color = ExcelTextMuted,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                color = ExcelDeepGreen,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TableCellHeader(text: String, width: Dp, align: TextAlign) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        contentAlignment = when (align) {
            TextAlign.Right -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = align
        )
    }
}

@Composable
private fun TableCellData(
    text: String,
    width: Dp,
    align: TextAlign,
    isBold: Boolean = false,
    isGreen: Boolean = false,
    isMonospace: Boolean = false,
    isBadge: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        contentAlignment = when (align) {
            TextAlign.Right -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        }
    ) {
        if (isBadge) {
            val isQris = text.contains("QRIS", ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isQris) Color(0xFFE3F2FD) else ExcelSoftMint)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isQris) Color(0xFF0D47A1) else ExcelDeepGreen
                )
            }
        } else {
            Text(
                text = text,
                color = if (isGreen) ExcelPrimaryGreen else ExcelTextDark,
                fontSize = 11.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
