package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StoreConfig
import com.example.data.model.TransactionWithItems
import com.example.ui.components.ExportFileFormat
import com.example.ui.components.ExportReportPreviewDialog
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.StatusQris
import com.example.ui.theme.StatusTunai
import com.example.ui.viewmodel.KasirViewModel
import com.example.ui.viewmodel.ReportPeriodType
import com.example.ui.viewmodel.TopProductSummary
import com.example.ui.viewmodel.WeeklyPeriodSummary
import com.example.util.CurrencyUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaporanScreen(
    viewModel: KasirViewModel
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedReportPeriod.collectAsStateWithLifecycle()
    val selectedDailyTimestamp by viewModel.selectedDailyTimestamp.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current
    var txToDelete by remember { mutableStateOf<TransactionWithItems?>(null) }
    var previewExportFormat by remember { mutableStateOf<ExportFileFormat?>(null) }

    txToDelete?.let { txWithItems ->
        val tx = txWithItems.transaction
        AlertDialog(
            onDismissRequest = { txToDelete = null },
            title = {
                Text(
                    text = "Hapus Struk Transaksi?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Struk ${tx.invoiceNumber} senilai ${CurrencyUtils.formatRupiah(tx.totalAmount)} akan dihapus dari riwayat transaksi. Stok produk terkait akan otomatis dikembalikan.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = txWithItems
                        txToDelete = null
                        viewModel.deleteTransaction(target)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Ya, Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { txToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    val monthsList = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    // Filter transactions based on active period
    val periodTransactions = remember(allTransactions, selectedPeriod, selectedDailyTimestamp, selectedMonth, selectedYear) {
        when (selectedPeriod) {
            ReportPeriodType.HARIAN -> {
                val start = CurrencyUtils.getStartOfDay(selectedDailyTimestamp)
                val end = CurrencyUtils.getEndOfDay(selectedDailyTimestamp)
                allTransactions.filter { it.transaction.timestamp in start..end }
            }
            ReportPeriodType.MINGGUAN, ReportPeriodType.BULANAN -> {
                val range = CurrencyUtils.getMonthRange(selectedMonth, selectedYear)
                val start = range.first
                val end = range.second
                allTransactions.filter { it.transaction.timestamp in start..end }
            }
        }
    }

    // Dynamic stats
    val totalOmset = periodTransactions.sumOf { it.transaction.totalAmount }
    val totalTransactions = periodTransactions.size
    val totalItemsSold = periodTransactions.sumOf { it.items.sumOf { item -> item.quantity } }
    val totalTunai = periodTransactions.filter { it.transaction.paymentMethod == "TUNAI" }.sumOf { it.transaction.totalAmount }
    val totalQris = periodTransactions.filter { it.transaction.paymentMethod == "QRIS" }.sumOf { it.transaction.totalAmount }

    val totalLaba = remember(periodTransactions, allProducts) {
        periodTransactions.sumOf { txWithItems ->
            txWithItems.items.sumOf { item ->
                val prod = allProducts.find { it.id == item.productId }
                item.subtotal - (item.quantity * (prod?.costPrice ?: 0.0))
            }
        }
    }

    // Best Sellers calculation
    val topProducts = remember(periodTransactions) {
        val map = mutableMapOf<Long, Pair<String, Pair<Int, Double>>>()
        periodTransactions.forEach { txWithItems ->
            txWithItems.items.forEach { item ->
                val curr = map[item.productId]
                val prevQty = curr?.second?.first ?: 0
                val prevRev = curr?.second?.second ?: 0.0
                map[item.productId] = Pair(item.productName, Pair(prevQty + item.quantity, prevRev + item.subtotal))
            }
        }
        map.map { (prodId, p) ->
            TopProductSummary(
                productId = prodId,
                productName = p.first,
                totalQuantity = p.second.first,
                totalRevenue = p.second.second
            )
        }.sortedByDescending { it.totalQuantity }.take(5)
    }

    // Weekly Breakdown Calculation (Week 1: 1-7, Week 2: 8-14, Week 3: 15-21, Week 4: 22-end)
    val weeklyBreakdown = remember(periodTransactions, selectedMonth, selectedYear) {
        val weeks = mutableListOf<WeeklyPeriodSummary>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val ranges = listOf(
            Triple(1, 1, 7),
            Triple(2, 8, 14),
            Triple(3, 15, 21),
            Triple(4, 22, maxDays)
        )

        for ((wNum, startDay, endDay) in ranges) {
            val startCal = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, startDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, endDay, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val weekTxList = periodTransactions.filter {
                it.transaction.timestamp in startCal.timeInMillis..endCal.timeInMillis
            }

            weeks.add(
                WeeklyPeriodSummary(
                    weekNumber = wNum,
                    dateRangeText = "Tgl $startDay - $endDay ${monthsList[selectedMonth]}",
                    totalRevenue = weekTxList.sumOf { it.transaction.totalAmount },
                    totalTransactions = weekTxList.size,
                    totalItemsSold = weekTxList.sumOf { it.items.sumOf { i -> i.quantity } }
                )
            )
        }
        weeks
    }

    val reportTitle = when (selectedPeriod) {
        ReportPeriodType.HARIAN -> "Harian (${CurrencyUtils.formatDateIndonesian(selectedDailyTimestamp)})"
        ReportPeriodType.MINGGUAN -> "Mingguan (${monthsList[selectedMonth]} $selectedYear)"
        ReportPeriodType.BULANAN -> "Bulanan (${monthsList[selectedMonth]} $selectedYear)"
    }

    previewExportFormat?.let { initialFormat ->
        ExportReportPreviewDialog(
            initialFormat = initialFormat,
            reportTitle = reportTitle,
            storeConfig = storeConfig ?: StoreConfig(),
            transactions = periodTransactions,
            products = allProducts,
            onDismiss = { previewExportFormat = null },
            onConfirmExport = { chosenFormat ->
                previewExportFormat = null
                when (chosenFormat) {
                    ExportFileFormat.EXCEL -> viewModel.exportReportExcel(reportTitle, periodTransactions)
                    ExportFileFormat.CSV -> viewModel.exportReportCsv(reportTitle, periodTransactions)
                    ExportFileFormat.PDF -> viewModel.exportReportPdf(reportTitle, periodTransactions)
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth >= 720.dp

        @Composable
        fun LeftPane() {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Period Selector Tabs (Harian, Mingguan, Bulanan)
                PrimaryTabRow(
                    selectedTabIndex = selectedPeriod.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
            Tab(
                selected = selectedPeriod == ReportPeriodType.HARIAN,
                onClick = { viewModel.selectedReportPeriod.value = ReportPeriodType.HARIAN },
                text = { Text("Harian", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedPeriod == ReportPeriodType.MINGGUAN,
                onClick = { viewModel.selectedReportPeriod.value = ReportPeriodType.MINGGUAN },
                text = { Text("Mingguan", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedPeriod == ReportPeriodType.BULANAN,
                onClick = { viewModel.selectedReportPeriod.value = ReportPeriodType.BULANAN },
                text = { Text("Bulanan", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Period Date Navigation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedPeriod == ReportPeriodType.HARIAN) {
                    // Previous Day
                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = selectedDailyTimestamp
                                add(Calendar.DAY_OF_YEAR, -1)
                            }
                            viewModel.selectedDailyTimestamp.value = cal.timeInMillis
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Hari Sebelumnya")
                    }

                    // Selected Date Display (Clickable for DatePicker)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDailyTimestamp }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply { set(y, m, d) }
                                    viewModel.selectedDailyTimestamp.value = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = CurrencyUtils.formatDateIndonesian(selectedDailyTimestamp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Next Day
                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = selectedDailyTimestamp
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                            viewModel.selectedDailyTimestamp.value = cal.timeInMillis
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Hari Berikutnya")
                    }
                } else {
                    // Month Navigator for Mingguan & Bulanan
                    IconButton(
                        onClick = {
                            if (selectedMonth == 0) {
                                viewModel.selectedMonth.value = 11
                                viewModel.selectedYear.value = selectedYear - 1
                            } else {
                                viewModel.selectedMonth.value = selectedMonth - 1
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Bulan Sebelumnya")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${monthsList[selectedMonth]} $selectedYear",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedMonth == 11) {
                                viewModel.selectedMonth.value = 0
                                viewModel.selectedYear.value = selectedYear + 1
                            } else {
                                viewModel.selectedMonth.value = selectedMonth + 1
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Bulan Berikutnya")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Total Revenue Card for Selected Period
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = when (selectedPeriod) {
                        ReportPeriodType.HARIAN -> "Total Pendapatan Hari Ini"
                        ReportPeriodType.MINGGUAN -> "Total Pendapatan Bulan Ini"
                        ReportPeriodType.BULANAN -> "Total Pendapatan Sebulan (${monthsList[selectedMonth]})"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyUtils.formatRupiah(totalOmset),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = customColors.heroAmountColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2x2 Metric Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportStatBox(
                        title = "Total Transaksi",
                        value = "$totalTransactions Struk",
                        modifier = Modifier.weight(1f),
                        bgColor = customColors.subCardBg
                    )
                    ReportStatBox(
                        title = "Item Terjual",
                        value = "$totalItemsSold Porsi/Pcs",
                        modifier = Modifier.weight(1f),
                        bgColor = customColors.subCardBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportStatBox(
                        title = "Tunai",
                        value = CurrencyUtils.formatRupiah(totalTunai),
                        modifier = Modifier.weight(1f),
                        dotColor = StatusTunai,
                        bgColor = customColors.subCardBg
                    )
                    ReportStatBox(
                        title = "QRIS",
                        value = CurrencyUtils.formatRupiah(totalQris),
                        modifier = Modifier.weight(1f),
                        dotColor = StatusQris,
                        bgColor = customColors.subCardBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ReportStatBox(
                        title = "Estimasi Laba Bersih (Net Profit)",
                        value = CurrencyUtils.formatRupiah(totalLaba),
                        modifier = Modifier.fillMaxWidth(),
                        dotColor = Color(0xFF4CAF50),
                        bgColor = customColors.actionPrimaryBg.copy(alpha = 0.08f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Export Actions (Opens styled spreadsheet preview & format exporter)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ekspor Laporan & Pratinjau",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${periodTransactions.size} Struk",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { previewExportFormat = ExportFileFormat.PDF },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { previewExportFormat = ExportFileFormat.EXCEL },
                        modifier = Modifier
                            .weight(1.1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_excel_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D6F42),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel .xlsx", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { previewExportFormat = ExportFileFormat.CSV },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0277BD),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: MINGGUAN BREAKDOWN (When MINGGUAN is active)
        if (selectedPeriod == ReportPeriodType.MINGGUAN) {
            Text(
                text = "Rincian Penjualan Mingguan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            weeklyBreakdown.forEach { week ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Minggu Ke-${week.weekNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = week.dateRangeText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${week.totalTransactions} Transaksi | ${week.totalItemsSold} Item",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = CurrencyUtils.formatRupiah(week.totalRevenue),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = customColors.heroAmountColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

        @Composable
        fun RightPane() {
            Column(modifier = Modifier.fillMaxWidth()) {
                // SECTION: ITEM TERLARIS
                Text(
                    text = "Menu / Item Terlaris ($reportTitle)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
        Spacer(modifier = Modifier.height(10.dp))

        if (topProducts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg)
            ) {
                Text(
                    text = "Belum ada data penjualan pada periode ini.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            topProducts.forEachIndexed { index, topItem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(customColors.actionPrimaryBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = topItem.productName.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${topItem.totalQuantity} porsi",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = CurrencyUtils.formatRupiah(topItem.totalRevenue),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: RIWAYAT TRANSAKSI
        Text(
            text = "Riwayat Transaksi (${periodTransactions.size} Transaksi)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (periodTransactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg)
            ) {
                Text(
                    text = "Tidak ada riwayat transaksi pada periode ini.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            periodTransactions.forEachIndexed { index, txWithItems ->
                val tx = txWithItems.transaction
                val itemsSummary = txWithItems.items.joinToString(", ") { "${it.quantity}x ${it.productName}" }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
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
                                Text(
                                    text = "#${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (tx.paymentMethod == "QRIS") Color(0xFF2E3A52) else Color(0xFF1E3A20)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tx.paymentMethod,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.paymentMethod == "QRIS") Color(0xFF64B5F6) else StatusTunai
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = CurrencyUtils.formatDateTime(tx.timestamp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = CurrencyUtils.formatRupiah(tx.totalAmount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = itemsSummary,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lihat Struk",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        viewModel.openReceipt(txWithItems)
                                    }
                                )
                                IconButton(
                                    onClick = { txToDelete = txWithItems },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus Struk",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isTablet) 1200.dp else 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 12.dp)
                    .padding(bottom = 84.dp)
            ) {
                // Header
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
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Laporan Penjualan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.openReceiptScanner() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("laporan_scan_receipt_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode Struk",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan Struk",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LeftPane()
                        }
                        Column(modifier = Modifier.weight(1.05f)) {
                            RightPane()
                        }
                    }
                } else {
                    LeftPane()
                    Spacer(modifier = Modifier.height(16.dp))
                    RightPane()
                }
            }
        }
    }
}

@Composable
private fun ReportStatBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    bgColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
