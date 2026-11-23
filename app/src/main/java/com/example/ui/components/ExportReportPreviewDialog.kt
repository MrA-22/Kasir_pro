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
import androidx.compose.material3.MaterialTheme
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

enum class ExportFileFormat(
    val label: String,
    val extensionLabel: String,
    val subtitle: String,
    val badgeColor: Color
) {
    EXCEL(
        label = "Excel (.xlsx)",
        extensionLabel = "XLSX Multi-Sheet",
        subtitle = "3 Sheet rapi: Ringkasan & Tabel, Rincian Item, Produk Terlaris + Rumus SUM",
        badgeColor = Color(0xFF1D6F42)
    ),
    CSV(
        label = "CSV (.csv)",
        extensionLabel = "UTF-8 Semicolon",
        subtitle = "Pemisah titik-koma rapi untuk Excel Mobile & Google Sheets tanpa angka desimal .0",
        badgeColor = Color(0xFF0277BD)
    ),
    PDF(
        label = "PDF (.pdf)",
        extensionLabel = "Siap Cetak A4",
        subtitle = "Dokumen laporan rapi siap bagikan atau arsip cetak",
        badgeColor = Color(0xFFC62828)
    )
}

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
    val totalReceived = remember(transactions) { transactions.sumOf { it.transaction.amountReceived } }
    val totalChange = remember(transactions) { transactions.sumOf { it.transaction.changeAmount } }
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
                .padding(horizontal = 12.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 960.dp)
                    .heightIn(max = 820.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Rich Emerald Store Banner Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF145A32), Color(0xFF1D6F42))
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Pratinjau & Ekspor Laporan",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()} • $reportTitle",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD5F0DE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_preview_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup Pratinjau",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // 2. Scrollable Body: Format Selector + KPI + Formatted Spreadsheet Table Preview
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Pilih Format Ekspor",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val wide = maxWidth >= 560.dp
                                if (wide) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ExportFormatOptionCard(
                                            format = ExportFileFormat.EXCEL,
                                            icon = Icons.Default.GridOn,
                                            selected = selectedFormat == ExportFileFormat.EXCEL,
                                            onClick = { selectedFormat = ExportFileFormat.EXCEL },
                                            modifier = Modifier.weight(1f).testTag("preview_format_excel")
                                        )
                                        ExportFormatOptionCard(
                                            format = ExportFileFormat.CSV,
                                            icon = Icons.Default.TableChart,
                                            selected = selectedFormat == ExportFileFormat.CSV,
                                            onClick = { selectedFormat = ExportFileFormat.CSV },
                                            modifier = Modifier.weight(1f).testTag("preview_format_csv")
                                        )
                                        ExportFormatOptionCard(
                                            format = ExportFileFormat.PDF,
                                            icon = Icons.Default.PictureAsPdf,
                                            selected = selectedFormat == ExportFileFormat.PDF,
                                            onClick = { selectedFormat = ExportFileFormat.PDF },
                                            modifier = Modifier.weight(1f).testTag("preview_format_pdf")
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ExportFormatOptionCard(
                                                format = ExportFileFormat.EXCEL,
                                                icon = Icons.Default.GridOn,
                                                selected = selectedFormat == ExportFileFormat.EXCEL,
                                                onClick = { selectedFormat = ExportFileFormat.EXCEL },
                                                modifier = Modifier.weight(1f).testTag("preview_format_excel")
                                            )
                                            ExportFormatOptionCard(
                                                format = ExportFileFormat.CSV,
                                                icon = Icons.Default.TableChart,
                                                selected = selectedFormat == ExportFileFormat.CSV,
                                                onClick = { selectedFormat = ExportFileFormat.CSV },
                                                modifier = Modifier.weight(1f).testTag("preview_format_csv")
                                            )
                                        }
                                        ExportFormatOptionCard(
                                            format = ExportFileFormat.PDF,
                                            icon = Icons.Default.PictureAsPdf,
                                            selected = selectedFormat == ExportFileFormat.PDF,
                                            onClick = { selectedFormat = ExportFileFormat.PDF },
                                            modifier = Modifier.fillMaxWidth().testTag("preview_format_pdf")
                                        )
                                    }
                                }
                            }
                        }

                        // Summary KPI Strip matching Spreadsheet Ringkasan
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF3F8F4),
                                border = BorderStroke(1.dp, Color(0xFFD0E3D6))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RINGKASAN PENJUALAN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1B5E20),
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = selectedFormat.extensionLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = selectedFormat.badgeColor,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(selectedFormat.badgeColor.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = selectedFormat.subtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF3E5647)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PreviewKpiTile(
                                            label = "Total Omset",
                                            value = CurrencyUtils.formatRupiah(totalOmset),
                                            sub = "${transactions.size} Struk",
                                            modifier = Modifier.weight(1f)
                                        )
                                        PreviewKpiTile(
                                            label = "Laba Bersih",
                                            value = CurrencyUtils.formatRupiah(totalLaba),
                                            sub = "$totalItems Porsi",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PreviewKpiTile(
                                            label = "Tunai",
                                            value = CurrencyUtils.formatRupiah(totalTunai),
                                            sub = null,
                                            modifier = Modifier.weight(1f)
                                        )
                                        PreviewKpiTile(
                                            label = "QRIS",
                                            value = CurrencyUtils.formatRupiah(totalQris),
                                            sub = null,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Formatted Spreadsheet Table Preview (mirrors exact Excel / CSV columns cleanly)
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pratinjau Tabel Transaksi",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Geser tabel ke kanan ↔",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (transactions.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ) {
                                        Text(
                                            text = "Belum ada transaksi pada periode ini untuk pratinjau tabel.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(18.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    val hScroll = rememberScrollState()
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFC8D7CE)),
                                        color = Color.White
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(hScroll)
                                        ) {
                                            // Table Header Row (Forest Emerald)
                                            Row(
                                                modifier = Modifier
                                                    .background(Color(0xFF1D6F42))
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TableCellText("No", 44.dp, Color.White, FontWeight.Bold, TextAlign.Center)
                                                TableCellText("No Nota", 155.dp, Color.White, FontWeight.Bold)
                                                TableCellText("Waktu", 125.dp, Color.White, FontWeight.Bold, TextAlign.Center)
                                                TableCellText("Metode", 78.dp, Color.White, FontWeight.Bold, TextAlign.Center)
                                                TableCellText("Pelanggan", 110.dp, Color.White, FontWeight.Bold)
                                                TableCellText("Rincian Produk / Menu", 230.dp, Color.White, FontWeight.Bold)
                                                TableCellText("Total (Rp)", 115.dp, Color.White, FontWeight.Bold, TextAlign.End)
                                                TableCellText("Tunai (Rp)", 115.dp, Color.White, FontWeight.Bold, TextAlign.End)
                                                TableCellText("Kembali", 105.dp, Color.White, FontWeight.Bold, TextAlign.End)
                                            }

                                            transactions.forEachIndexed { idx, txWithItems ->
                                                val tx = txWithItems.transaction
                                                val rowBg = if (idx % 2 == 1) Color(0xFFF6FAF7) else Color.White
                                                val itemsText = txWithItems.items.joinToString("\n") {
                                                    "• ${it.quantity}x ${it.productName}"
                                                }.ifBlank { "-" }

                                                Row(
                                                    modifier = Modifier
                                                        .background(rowBg)
                                                        .padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TableCellText("${idx + 1}", 44.dp, Color(0xFF26322B), FontWeight.Medium, TextAlign.Center)
                                                    TableCellText(tx.invoiceNumber, 155.dp, Color(0xFF1F2421), FontWeight.SemiBold)
                                                    TableCellText(CurrencyUtils.formatDateTime(tx.timestamp), 125.dp, Color(0xFF45524B), FontWeight.Normal, TextAlign.Center)
                                                    Box(
                                                        modifier = Modifier.width(78.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        val isQris = tx.paymentMethod == "QRIS"
                                                        Text(
                                                            text = tx.paymentMethod,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isQris) Color(0xFF0D47A1) else Color(0xFF1B5E20),
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isQris) Color(0xFFE3F2FD) else Color(0xFFE8F5E9))
                                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    TableCellText(tx.customerName.ifBlank { "-" }, 110.dp, Color(0xFF333D37), FontWeight.Normal)
                                                    TableCellText(itemsText, 230.dp, Color(0xFF26322B), FontWeight.Normal)
                                                    TableCellText(CurrencyUtils.formatRupiah(tx.totalAmount), 115.dp, Color(0xFF145A32), FontWeight.Bold, TextAlign.End)
                                                    TableCellText(CurrencyUtils.formatRupiah(tx.amountReceived), 115.dp, Color(0xFF333D37), FontWeight.Normal, TextAlign.End)
                                                    TableCellText(CurrencyUtils.formatRupiah(tx.changeAmount), 105.dp, Color(0xFF333D37), FontWeight.Normal, TextAlign.End)
                                                }
                                                HorizontalDivider(color = Color(0xFFE3ECE6), thickness = 1.dp)
                                            }

                                            // Warm Amber Grand Total Footer Row
                                            Row(
                                                modifier = Modifier
                                                    .background(Color(0xFFFFF3E0))
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TableCellText("", 44.dp, Color(0xFF4A2C00), FontWeight.Bold)
                                                TableCellText("TOTAL KESELURUHAN", 155.dp, Color(0xFF4A2C00), FontWeight.ExtraBold)
                                                TableCellText("${transactions.size} Struk", 125.dp, Color(0xFF4A2C00), FontWeight.Bold, TextAlign.Center)
                                                TableCellText("", 78.dp, Color(0xFF4A2C00), FontWeight.Bold)
                                                TableCellText("", 110.dp, Color(0xFF4A2C00), FontWeight.Bold)
                                                TableCellText("$totalItems item terjual", 230.dp, Color(0xFF6D4C00), FontWeight.SemiBold)
                                                TableCellText(CurrencyUtils.formatRupiah(totalOmset), 115.dp, Color(0xFF4A2C00), FontWeight.ExtraBold, TextAlign.End)
                                                TableCellText(CurrencyUtils.formatRupiah(totalReceived), 115.dp, Color(0xFF4A2C00), FontWeight.Bold, TextAlign.End)
                                                TableCellText(CurrencyUtils.formatRupiah(totalChange), 105.dp, Color(0xFF4A2C00), FontWeight.Bold, TextAlign.End)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Sticky Bottom Export CTA Bar
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("export_preview_cancel_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { onConfirmExport(selectedFormat) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_preview_confirm_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = selectedFormat.badgeColor,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ekspor & Bagikan ${selectedFormat.label}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
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
private fun ExportFormatOptionCard(
    format: ExportFileFormat,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) format.badgeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) format.badgeColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(format.badgeColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = format.badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = format.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = format.extensionLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Terpilih",
                    tint = format.badgeColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviewKpiTile(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFDCE8DF))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF53695A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF145A32),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    color = Color(0xFF6A7D70),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TableCellText(
    text: String,
    width: Dp,
    color: Color,
    fontWeight: FontWeight,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = fontWeight,
        color = color,
        textAlign = align,
        lineHeight = 15.sp,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp)
    )
}
