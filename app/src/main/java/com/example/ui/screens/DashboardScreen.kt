package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.StatusQris
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusTunai
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.KasirViewModel
import com.example.util.CurrencyUtils

@Composable
fun DashboardScreen(
    viewModel: KasirViewModel,
    onNavigateToKasir: () -> Unit,
    onNavigateToHitung: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToLaporan: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val storeConfig by viewModel.storeConfig.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val customColors = LocalCustomColors.current
    var txToDelete by remember { mutableStateOf<com.example.data.model.TransactionWithItems?>(null) }

    val totalLabaToday = remember(summary.recentTransactionsToday, allProducts) {
        summary.recentTransactionsToday.sumOf { txWithItems ->
            txWithItems.items.sumOf { item ->
                val prod = allProducts.find { it.id == item.productId }
                item.subtotal - (item.quantity * (prod?.costPrice ?: 0.0))
            }
        }
    }

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth >= 720.dp

        @Composable
        fun LeftPane() {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 2. Hero Sales Card (Total Penjualan Hari Ini)
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
                // Header of Hero Card
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Total Penjualan Hari Ini",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Amount Text
                Text(
                    text = CurrencyUtils.formatRupiah(summary.totalRevenueToday),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = customColors.heroAmountColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2x2 Metric Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSubCard(
                        title = "Transaksi",
                        value = "${summary.totalTransactionsToday} Struk",
                        modifier = Modifier.weight(1f),
                        bgColor = customColors.subCardBg
                    )
                    MetricSubCard(
                        title = "Porsi Terjual",
                        value = "${summary.totalItemsSoldToday} Porsi",
                        modifier = Modifier.weight(1f),
                        bgColor = customColors.subCardBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSubCard(
                        title = "Tunai",
                        value = CurrencyUtils.formatRupiah(summary.totalTunaiToday),
                        modifier = Modifier.weight(1f),
                        dotColor = StatusTunai,
                        bgColor = customColors.subCardBg
                    )
                    MetricSubCard(
                        title = "QRIS",
                        value = CurrencyUtils.formatRupiah(summary.totalQrisToday),
                        modifier = Modifier.weight(1f),
                        dotColor = StatusQris,
                        bgColor = customColors.subCardBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricSubCard(
                        title = "Estimasi Laba Bersih Hari Ini",
                        value = CurrencyUtils.formatRupiah(totalLabaToday),
                        modifier = Modifier.fillMaxWidth(),
                        dotColor = Color(0xFF4CAF50),
                        bgColor = customColors.actionPrimaryBg.copy(alpha = 0.08f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Quick Action Buttons Row: [ Buka Kasir ] & [ Hitung Cepat ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToKasir,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("open_cashier_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColors.actionPrimaryBg,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PointOfSale,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buka Kasir",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = onNavigateToHitung,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("quick_calc_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColors.actionSecondaryBg,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hitung Cepat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Peringatan Stok Menipis Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (summary.lowStockAlertCount > 0) StatusWarning else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Peringatan Stok Menipis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (summary.lowStockAlertCount == 0) {
            // Safe Stock Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.borderStroke)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A20)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Semua Stok Menu Aman",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tidak ada menu yang stoknya menipis saat ini.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Low Stock Warning List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusWarning.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Perlu Ditambah (${summary.lowStockAlertCount} Item):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = StatusWarning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    summary.lowStockProducts.take(3).forEach { prod ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = prod.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Sisa stok: ${prod.stock} ${prod.unit}",
                                    fontSize = 11.sp,
                                    color = StatusWarning
                                )
                            }
                            OutlinedButton(
                                onClick = onNavigateToMenu,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("+ Atur Stok", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

            }
        }

        @Composable
        fun RightPane() {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 5. Menu Terlaris Hari Ini Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
            Text(
                text = "Menu Terlaris Hari Ini",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onNavigateToLaporan() }
            ) {
                Text(
                    text = "Laporan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Buka Laporan",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (summary.topSellingToday.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg)
            ) {
                Text(
                    text = "Belum ada transaksi hari ini.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            summary.topSellingToday.forEachIndexed { index, topItem ->
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
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                            Spacer(modifier = Modifier.width(12.dp))
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
                                text = "${topItem.totalQuantity} ${topItem.unit}",
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

        // 6. Transaksi Terkini Hari Ini Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transaksi Terkini Hari Ini",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Semua (${summary.totalTransactionsToday})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onNavigateToLaporan() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (summary.recentTransactionsToday.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.heroCardBg)
            ) {
                Text(
                    text = "Belum ada transaksi hari ini.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            summary.recentTransactionsToday.forEachIndexed { index, txWithItems ->
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.paymentMethod == "QRIS") Color(0xFF64B5F6) else StatusTunai
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = CurrencyUtils.formatTimeOnly(tx.timestamp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = CurrencyUtils.formatRupiah(tx.totalAmount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = itemsSummary,
                                fontSize = 13.sp,
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
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isTablet) 1200.dp else 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 24.dp else 18.dp, vertical = 14.dp)
            ) {
                // 1. Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = storeConfig?.storeName?.takeIf { it.isNotBlank() } ?: "Kasir Pro",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyUtils.formatDateIndonesian(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cloudSyncState.mode != com.example.util.FirebaseSyncManager.CloudConnectionMode.NOT_CONFIGURED) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val (statusLabel, badgeColor) = when (cloudSyncState.mode) {
                                com.example.util.FirebaseSyncManager.CloudConnectionMode.ONLINE_SYNCED ->
                                    "Cloud Online • ${cloudSyncState.projectId}" to Color(0xFF10B981)
                                com.example.util.FirebaseSyncManager.CloudConnectionMode.UPLOADING_QUEUE ->
                                    "Mengunggah antrean ke Cloud..." to MaterialTheme.colorScheme.primary
                                com.example.util.FirebaseSyncManager.CloudConnectionMode.OFFLINE_QUEUED ->
                                    "Offline • Antrean aman, otomatis upload saat online" to Color(0xFFF59E0B)
                                com.example.util.FirebaseSyncManager.CloudConnectionMode.ERROR ->
                                    "Cloud Terkendala • Cek Pengaturan" to MaterialTheme.colorScheme.error
                                else -> "" to Color.Transparent
                            }
                            if (statusLabel.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = badgeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Scan Receipt Barcode Button
                        IconButton(
                            onClick = { viewModel.openReceiptScanner() },
                            modifier = Modifier.testTag("dashboard_scan_receipt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode Struk",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Theme Toggle Quick Button (Cycle Dark -> Sage -> Light)
                        IconButton(
                            onClick = {
                                val currentMode = storeConfig?.themeMode ?: "DARK"
                                val nextMode = when (currentMode) {
                                    "DARK" -> "SAGE"
                                    "SAGE" -> "LIGHT"
                                    else -> "DARK"
                                }
                                viewModel.updateThemeMode(nextMode)
                            },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            val currentMode = storeConfig?.themeMode ?: "DARK"
                            when (currentMode) {
                                "SAGE" -> Icon(
                                    imageVector = Icons.Default.Spa,
                                    contentDescription = "Tema Sage",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                "LIGHT" -> Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = "Tema Terang",
                                    tint = Color(0xFFE2542B)
                                )
                                else -> Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = "Tema Gelap",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Store Settings Button
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Pengaturan Toko",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    Spacer(modifier = Modifier.height(20.dp))
                    RightPane()
                }

                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }
}

@Composable
private fun MetricSubCard(
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
