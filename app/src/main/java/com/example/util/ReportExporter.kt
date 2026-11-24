package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.data.model.TransactionWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

object ReportExporter {

    private const val TAG = "ReportExporter"

    const val MIME_PDF = "application/pdf"
    const val MIME_CSV = "text/csv"
    const val MIME_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun csvSafe(raw: String): String {
        val cleaned = raw
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(";", ",")
            .replace("\"", "'")
            .trim()
        return if (cleaned.contains(",") || cleaned.contains(" ")) "\"$cleaned\"" else cleaned
    }

    /**
     * Generates clean UTF-8 BOM semicolon-separated CSV formatted for Indonesian & international
     * Excel Mobile / Google Sheets without raw decimal ".0" suffixes or broken rows.
     */
    fun writeCsvReport(
        outputStream: OutputStream,
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>,
        products: List<Product> = emptyList()
    ) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            // UTF-8 BOM for Microsoft Excel Mobile / Desktop UTF-8 detection
            writer.write("\uFEFF")

            val storeName = storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()
            val exportTime = CurrencyUtils.formatDateTime(System.currentTimeMillis())

            // Store & Report Metadata (structured 2-column key-value pairs)
            writer.append("LAPORAN PENJUALAN;${csvSafe(storeName)}\r\n")
            writer.append("Periode;${csvSafe(reportTitle)}\r\n")
            writer.append("Waktu Ekspor;${csvSafe(exportTime)}\r\n")
            if (storeConfig.storeAddress.isNotBlank()) {
                writer.append("Alamat Toko;${csvSafe(storeConfig.storeAddress)}\r\n")
            }
            if (storeConfig.storePhone.isNotBlank()) {
                writer.append("Telepon / WA;${csvSafe(storeConfig.storePhone)}\r\n")
            }
            writer.append("\r\n")

            // Sales Summary Block
            val totalOmset = transactions.sumOf { it.transaction.totalAmount }.toLong()
            val totalItems = transactions.sumOf { it.items.sumOf { item -> item.quantity } }
            val tunaiTx = transactions.filter { it.transaction.paymentMethod == "TUNAI" }
            val qrisTx = transactions.filter { it.transaction.paymentMethod == "QRIS" }
            val totalTunaiNominal = tunaiTx.sumOf { it.transaction.totalAmount }.toLong()
            val totalQrisNominal = qrisTx.sumOf { it.transaction.totalAmount }.toLong()
            val productCostMap = products.associateBy { it.id }
            val totalLaba = transactions.sumOf { txWithItems ->
                txWithItems.items.sumOf { item ->
                    val cost = productCostMap[item.productId]?.costPrice ?: 0.0
                    item.subtotal - (item.quantity * cost)
                }
            }.toLong()

            writer.append("RINGKASAN PENJUALAN;Nilai;Keterangan\r\n")
            writer.append("Total Pendapatan;$totalOmset;${transactions.size} Transaksi\r\n")
            writer.append("Estimasi Laba Bersih;$totalLaba;Omset dikurangi modal\r\n")
            writer.append("Total Item Terjual;$totalItems;Porsi / Pcs\r\n")
            writer.append("Pendapatan Tunai;$totalTunaiNominal;${tunaiTx.size} Transaksi\r\n")
            writer.append("Pendapatan QRIS;$totalQrisNominal;${qrisTx.size} Transaksi\r\n")
            writer.append("\r\n")

            // Structured 10-column Transaction Table
            writer.append("No;No Nota;Tanggal & Jam;Metode Bayar;Pelanggan;Total Belanja (Rp);Tunai Diterima (Rp);Kembalian (Rp);Catatan;Rincian Produk / Menu\r\n")

            var grandReceived = 0L
            var grandChange = 0L

            transactions.forEachIndexed { index, item ->
                val tx = item.transaction
                val totalLong = tx.totalAmount.toLong()
                val receivedLong = tx.amountReceived.toLong()
                val changeLong = tx.changeAmount.toLong()
                grandReceived += receivedLong
                grandChange += changeLong

                val itemsSummary = item.items.joinToString(" | ") {
                    val disc = if (it.discountValue > 0) {
                        if (it.isDiscountPercentage) " (Disc ${it.discountValue.toInt()}%)"
                        else " (Disc ${CurrencyUtils.formatRupiah(it.discountValue)})"
                    } else ""
                    "${it.quantity}x ${it.productName}$disc (${CurrencyUtils.formatRupiah(it.subtotal)})"
                }

                val dateStr = CurrencyUtils.formatDateTime(tx.timestamp)
                val customer = tx.customerName.ifBlank { "-" }
                val note = tx.note.ifBlank { "-" }

                writer.append(
                    listOf(
                        (index + 1).toString(),
                        csvSafe(tx.invoiceNumber),
                        csvSafe(dateStr),
                        csvSafe(tx.paymentMethod),
                        csvSafe(customer),
                        totalLong.toString(),
                        receivedLong.toString(),
                        changeLong.toString(),
                        csvSafe(note),
                        csvSafe(itemsSummary)
                    ).joinToString(";")
                )
                writer.append("\r\n")
            }

            if (transactions.isNotEmpty()) {
                writer.append(
                    ";TOTAL KESELURUHAN;;${transactions.size} Struk;;$totalOmset;$grandReceived;$grandChange;;\r\n"
                )
            } else {
                writer.append("-;Belum ada transaksi pada periode ini;;;;;;;;\r\n")
            }

            writer.flush()
        }
    }

    fun buildCsvString(
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>,
        products: List<Product> = emptyList()
    ): String {
        val baos = ByteArrayOutputStream()
        writeCsvReport(baos, reportTitle, storeConfig, transactions, products)
        return baos.toString(Charsets.UTF_8.name())
    }

    suspend fun exportToCsv(
        context: Context,
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>,
        products: List<Product> = emptyList()
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "reports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val sanitizedTitle = reportTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val fileName = "Laporan_${sanitizedTitle}_${System.currentTimeMillis()}.csv"
            val file = File(exportDir, fileName)

            FileOutputStream(file).use { fos ->
                writeCsvReport(fos, reportTitle, storeConfig, transactions, products)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting CSV: ${e.message}", e)
            null
        }
    }

    suspend fun exportToExcel(
        context: Context,
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>,
        products: List<Product> = emptyList()
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "reports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val sanitizedTitle = reportTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val fileName = "Laporan_${sanitizedTitle}_${System.currentTimeMillis()}.xlsx"
            val file = File(exportDir, fileName)

            FileOutputStream(file).use { fos ->
                writeExcelWorkbook(fos, reportTitle, storeConfig, transactions, products)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting Excel (.xlsx): ${e.message}", e)
            null
        }
    }

    /**
     * Generates a polished multi-sheet OOXML Excel (.xlsx) workbook with:
     * - Merged dark-emerald title & period banners
     * - Custom row heights & wrapped multi-line item cells
     * - Zebra row striping & color-coded payment method pills (TUNAI vs QRIS)
     * - Frozen column header panes & AutoFilter dropdowns
     * - Live SUM formulas & Landscape fit-to-width page setup
     */
    fun writeExcelWorkbook(
        outputStream: OutputStream,
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>,
        products: List<Product> = emptyList()
    ) {
        val themeMode = storeConfig.themeMode.uppercase()
        val sheet1Xml = buildSummaryAndTransactionsSheetXml(reportTitle, storeConfig, transactions, products)
        val sheet2Xml = buildItemDetailsSheetXml(reportTitle, storeConfig, transactions)
        val sheet3Xml = buildTopProductsSheetXml(reportTitle, storeConfig, transactions)
        val stylesXml = getStylesXml(themeMode)

        ZipOutputStream(outputStream).use { zos ->
            putZipEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML)
            putZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            putZipEntry(zos, "xl/workbook.xml", WORKBOOK_XML)
            putZipEntry(zos, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            putZipEntry(zos, "xl/styles.xml", stylesXml)
            putZipEntry(zos, "xl/worksheets/sheet1.xml", sheet1Xml)
            putZipEntry(zos, "xl/worksheets/sheet2.xml", sheet2Xml)
            putZipEntry(zos, "xl/worksheets/sheet3.xml", sheet3Xml)
            zos.finish()
        }
    }

    private fun putZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildSummaryAndTransactionsSheetXml(
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>,
        products: List<Product>
    ): String {
        val rows = mutableListOf<ExcelRow>()
        val merges = mutableListOf<String>()
        val exportTime = CurrencyUtils.formatDateTime(System.currentTimeMillis())
        val storeTitle = storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()

        // Banner Rows 1..3 (merged across A..J)
        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("LAPORAN PENJUALAN - $storeTitle", style = STYLE_BANNER_TITLE)) +
                    List(9) { ExcelCell.empty(style = STYLE_BANNER_TITLE) },
                height = 34.0
            )
        )
        merges.add("A1:J1")

        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("Periode: $reportTitle   |   Diekspor: $exportTime", style = STYLE_BANNER_SUB)) +
                    List(9) { ExcelCell.empty(style = STYLE_BANNER_SUB) },
                height = 22.0
            )
        )
        merges.add("A2:J2")

        var currentRow = 3
        if (storeConfig.storeAddress.isNotBlank() || storeConfig.storePhone.isNotBlank()) {
            val info = listOfNotNull(
                storeConfig.storeAddress.takeIf { it.isNotBlank() },
                storeConfig.storePhone.takeIf { it.isNotBlank() }?.let { "Telp / WA: $it" }
            ).joinToString("   |   ")
            rows.add(
                ExcelRow(
                    cells = listOf(ExcelCell.text(info, style = STYLE_BANNER_META)) +
                        List(9) { ExcelCell.empty(style = STYLE_BANNER_META) },
                    height = 20.0
                )
            )
            merges.add("A$currentRow:J$currentRow")
            currentRow++
        }

        // Blank separator row
        rows.add(ExcelRow(emptyList(), height = 10.0))
        currentRow++

        // Sales summary block
        val totalOmset = transactions.sumOf { it.transaction.totalAmount }
        val totalItems = transactions.sumOf { it.items.sumOf { item -> item.quantity } }
        val tunaiTx = transactions.filter { it.transaction.paymentMethod == "TUNAI" }
        val qrisTx = transactions.filter { it.transaction.paymentMethod == "QRIS" }
        val totalTunaiNominal = tunaiTx.sumOf { it.transaction.totalAmount }
        val totalQrisNominal = qrisTx.sumOf { it.transaction.totalAmount }
        val productCostMap = products.associateBy { it.id }
        val totalLaba = transactions.sumOf { txWithItems ->
            txWithItems.items.sumOf { item ->
                val cost = productCostMap[item.productId]?.costPrice ?: 0.0
                item.subtotal - (item.quantity * cost)
            }
        }

        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("RINGKASAN PENJUALAN", style = STYLE_SECTION_LABEL)) +
                    List(3) { ExcelCell.empty(style = STYLE_SECTION_LABEL) },
                height = 24.0
            )
        )
        merges.add("A$currentRow:D$currentRow")
        currentRow++

        rows.add(
            ExcelRow(
                cells = listOf(
                    ExcelCell.text("Keterangan", style = STYLE_TABLE_HEADER),
                    ExcelCell.empty(style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Nilai", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Detail", style = STYLE_TABLE_HEADER)
                ),
                height = 24.0
            )
        )
        merges.add("A$currentRow:B$currentRow")
        currentRow++

        fun addKpiRow(label: String, valueCell: ExcelCell, detail: String, highlight: Boolean = false) {
            val labelStyle = if (highlight) STYLE_KPI_LABEL_ACCENT else STYLE_KPI_LABEL
            val detailStyle = if (highlight) STYLE_KPI_DETAIL_ACCENT else STYLE_KPI_DETAIL
            rows.add(
                ExcelRow(
                    cells = listOf(
                        ExcelCell.text(label, style = labelStyle),
                        ExcelCell.empty(style = labelStyle),
                        valueCell,
                        ExcelCell.text(detail, style = detailStyle)
                    ),
                    height = 22.0
                )
            )
            merges.add("A$currentRow:B$currentRow")
            currentRow++
        }

        addKpiRow(
            "Total Pendapatan (Omset)",
            ExcelCell.number(totalOmset, style = STYLE_KPI_CURRENCY_ACCENT),
            "${transactions.size} Transaksi",
            highlight = true
        )
        addKpiRow(
            "Estimasi Laba Bersih",
            ExcelCell.number(totalLaba, style = STYLE_KPI_CURRENCY),
            "Omset dikurangi HPP / Modal"
        )
        addKpiRow(
            "Total Item Terjual",
            ExcelCell.number(totalItems.toDouble(), style = STYLE_KPI_INT),
            "Porsi / Pcs"
        )
        addKpiRow(
            "Pendapatan Tunai",
            ExcelCell.number(totalTunaiNominal, style = STYLE_KPI_CURRENCY),
            "${tunaiTx.size} Transaksi"
        )
        addKpiRow(
            "Pendapatan QRIS",
            ExcelCell.number(totalQrisNominal, style = STYLE_KPI_CURRENCY),
            "${qrisTx.size} Transaksi"
        )

        // Blank separator
        rows.add(ExcelRow(emptyList(), height = 12.0))
        currentRow++

        // Transactions Table Section
        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("DAFTAR TRANSAKSI", style = STYLE_SECTION_LABEL)) +
                    List(9) { ExcelCell.empty(style = STYLE_SECTION_LABEL) },
                height = 24.0
            )
        )
        merges.add("A$currentRow:J$currentRow")
        currentRow++

        val headerRowNum = currentRow
        rows.add(
            ExcelRow(
                cells = listOf(
                    ExcelCell.text("No", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("No Nota", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Tanggal & Jam", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Metode Bayar", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Pelanggan", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Total (Rp)", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Tunai Diterima (Rp)", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Kembalian (Rp)", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Catatan", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Rincian Produk / Menu", style = STYLE_TABLE_HEADER)
                ),
                height = 28.0
            )
        )
        currentRow++

        val firstDataRowNum = currentRow
        transactions.forEachIndexed { index, txWithItems ->
            val tx = txWithItems.transaction
            val isAlt = index % 2 == 1
            val textStyle = if (isAlt) STYLE_CELL_TEXT_ALT else STYLE_CELL_TEXT
            val centerStyle = if (isAlt) STYLE_CELL_CENTER_ALT else STYLE_CELL_CENTER
            val currencyStyle = if (isAlt) STYLE_CELL_CURRENCY_ALT else STYLE_CELL_CURRENCY
            val badgeStyle = if (tx.paymentMethod == "QRIS") STYLE_BADGE_QRIS else STYLE_BADGE_TUNAI

            val itemsSummary = txWithItems.items.joinToString("\n") {
                val disc = if (it.discountValue > 0) {
                    if (it.isDiscountPercentage) " [Disc ${it.discountValue.toInt()}%]"
                    else " [Disc ${CurrencyUtils.formatRupiah(it.discountValue)}]"
                } else ""
                "• ${it.quantity}x ${it.productName}$disc — ${CurrencyUtils.formatRupiah(it.subtotal)}"
            }.ifBlank { "-" }

            val lineCount = maxOf(
                txWithItems.items.size.coerceAtLeast(1),
                ceil(itemsSummary.length / 44.0).toInt()
            )
            val rowHeight = (12.0 + lineCount * 15.0).coerceIn(24.0, 120.0)

            rows.add(
                ExcelRow(
                    cells = listOf(
                        ExcelCell.number((index + 1).toDouble(), style = centerStyle),
                        ExcelCell.text(tx.invoiceNumber, style = textStyle),
                        ExcelCell.text(CurrencyUtils.formatDateTime(tx.timestamp), style = centerStyle),
                        ExcelCell.text(tx.paymentMethod, style = badgeStyle),
                        ExcelCell.text(tx.customerName.ifBlank { "-" }, style = textStyle),
                        ExcelCell.number(tx.totalAmount, style = currencyStyle),
                        ExcelCell.number(tx.amountReceived, style = currencyStyle),
                        ExcelCell.number(tx.changeAmount, style = currencyStyle),
                        ExcelCell.text(tx.note.ifBlank { "-" }, style = textStyle),
                        ExcelCell.text(itemsSummary, style = textStyle)
                    ),
                    height = rowHeight
                )
            )
            currentRow++
        }

        val lastDataRowNum = (currentRow - 1).coerceAtLeast(headerRowNum)
        if (transactions.isNotEmpty()) {
            val totalReceived = transactions.sumOf { it.transaction.amountReceived }
            val totalChange = transactions.sumOf { it.transaction.changeAmount }
            rows.add(
                ExcelRow(
                    cells = listOf(
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.text("TOTAL KESELURUHAN", style = STYLE_TOTAL_TEXT),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.text("${transactions.size} Struk", style = STYLE_TOTAL_CENTER),
                        ExcelCell.formula("SUM(F$firstDataRowNum:F$lastDataRowNum)", totalOmset, style = STYLE_TOTAL_CURRENCY),
                        ExcelCell.formula("SUM(G$firstDataRowNum:G$lastDataRowNum)", totalReceived, style = STYLE_TOTAL_CURRENCY),
                        ExcelCell.formula("SUM(H$firstDataRowNum:H$lastDataRowNum)", totalChange, style = STYLE_TOTAL_CURRENCY),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT)
                    ),
                    height = 26.0
                )
            )
        } else {
            rows.add(
                ExcelRow(
                    cells = listOf(ExcelCell.text("Belum ada transaksi pada periode ini.", style = STYLE_CELL_CENTER)) +
                        List(9) { ExcelCell.empty(style = STYLE_CELL_CENTER) },
                    height = 28.0
                )
            )
            merges.add("A$currentRow:J$currentRow")
        }

        val minWidths = doubleArrayOf(6.0, 22.0, 19.0, 14.0, 18.0, 18.0, 20.0, 18.0, 20.0, 46.0)
        return buildWorksheetXml(
            rows = rows,
            minWidths = minWidths,
            merges = merges,
            freezeAtRow = headerRowNum,
            autoFilterRange = if (transactions.isNotEmpty()) "A$headerRowNum:J$lastDataRowNum" else null,
            isDefaultSelected = true,
            bannerRowsSkipCount = 4
        )
    }

    private fun buildItemDetailsSheetXml(
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>
    ): String {
        val rows = mutableListOf<ExcelRow>()
        val merges = mutableListOf<String>()
        val storeTitle = storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()

        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("RINCIAN ITEM TERJUAL - $storeTitle", style = STYLE_BANNER_TITLE)) +
                    List(7) { ExcelCell.empty(style = STYLE_BANNER_TITLE) },
                height = 32.0
            )
        )
        merges.add("A1:H1")

        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("Periode: $reportTitle", style = STYLE_BANNER_SUB)) +
                    List(7) { ExcelCell.empty(style = STYLE_BANNER_SUB) },
                height = 22.0
            )
        )
        merges.add("A2:H2")

        rows.add(ExcelRow(emptyList(), height = 10.0))

        val headerRowNum = 4
        rows.add(
            ExcelRow(
                cells = listOf(
                    ExcelCell.text("No", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("No Nota", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Tanggal & Jam", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Nama Menu / Produk", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Qty", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Harga Satuan (Rp)", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Diskon", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Subtotal (Rp)", style = STYLE_TABLE_HEADER)
                ),
                height = 28.0
            )
        )

        val firstDataRowNum = 5
        var lineNo = 0
        var grandQty = 0
        var grandSubtotal = 0.0
        transactions.forEach { txWithItems ->
            val tx = txWithItems.transaction
            val dateStr = CurrencyUtils.formatDateTime(tx.timestamp)
            txWithItems.items.forEach { item ->
                val isAlt = lineNo % 2 == 1
                val textStyle = if (isAlt) STYLE_CELL_TEXT_ALT else STYLE_CELL_TEXT
                val centerStyle = if (isAlt) STYLE_CELL_CENTER_ALT else STYLE_CELL_CENTER
                val currencyStyle = if (isAlt) STYLE_CELL_CURRENCY_ALT else STYLE_CELL_CURRENCY

                lineNo++
                grandQty += item.quantity
                grandSubtotal += item.subtotal
                val discText = if (item.discountValue > 0) {
                    if (item.isDiscountPercentage) "${item.discountValue.toInt()}%" else CurrencyUtils.formatRupiah(item.discountValue)
                } else "-"

                rows.add(
                    ExcelRow(
                        cells = listOf(
                            ExcelCell.number(lineNo.toDouble(), style = centerStyle),
                            ExcelCell.text(tx.invoiceNumber, style = textStyle),
                            ExcelCell.text(dateStr, style = centerStyle),
                            ExcelCell.text(item.productName, style = textStyle),
                            ExcelCell.number(item.quantity.toDouble(), style = centerStyle),
                            ExcelCell.number(item.pricePerUnit, style = currencyStyle),
                            ExcelCell.text(discText, style = centerStyle),
                            ExcelCell.number(item.subtotal, style = currencyStyle)
                        ),
                        height = 22.0
                    )
                )
            }
        }

        val lastDataRowNum = (4 + lineNo).coerceAtLeast(headerRowNum)
        if (lineNo > 0) {
            rows.add(
                ExcelRow(
                    cells = listOf(
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.text("TOTAL ITEM", style = STYLE_TOTAL_TEXT),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.formula("SUM(E$firstDataRowNum:E$lastDataRowNum)", grandQty.toDouble(), style = STYLE_TOTAL_CENTER),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.formula("SUM(H$firstDataRowNum:H$lastDataRowNum)", grandSubtotal, style = STYLE_TOTAL_CURRENCY)
                    ),
                    height = 26.0
                )
            )
        } else {
            rows.add(
                ExcelRow(
                    cells = listOf(ExcelCell.text("Belum ada item terjual pada periode ini.", style = STYLE_CELL_CENTER)) +
                        List(7) { ExcelCell.empty(style = STYLE_CELL_CENTER) },
                    height = 26.0
                )
            )
            merges.add("A5:H5")
        }

        val minWidths = doubleArrayOf(6.0, 22.0, 20.0, 30.0, 10.0, 18.0, 14.0, 20.0)
        return buildWorksheetXml(
            rows = rows,
            minWidths = minWidths,
            merges = merges,
            freezeAtRow = headerRowNum,
            autoFilterRange = if (lineNo > 0) "A$headerRowNum:H$lastDataRowNum" else null,
            isDefaultSelected = false,
            bannerRowsSkipCount = 2
        )
    }

    private fun buildTopProductsSheetXml(
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>
    ): String {
        val rows = mutableListOf<ExcelRow>()
        val merges = mutableListOf<String>()
        val storeTitle = storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()

        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("REKAP PRODUK TERLARIS - $storeTitle", style = STYLE_BANNER_TITLE)) +
                    List(4) { ExcelCell.empty(style = STYLE_BANNER_TITLE) },
                height = 32.0
            )
        )
        merges.add("A1:E1")

        rows.add(
            ExcelRow(
                cells = listOf(ExcelCell.text("Periode: $reportTitle", style = STYLE_BANNER_SUB)) +
                    List(4) { ExcelCell.empty(style = STYLE_BANNER_SUB) },
                height = 22.0
            )
        )
        merges.add("A2:E2")

        rows.add(ExcelRow(emptyList(), height = 10.0))

        val headerRowNum = 4
        rows.add(
            ExcelRow(
                cells = listOf(
                    ExcelCell.text("Peringkat", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Nama Menu / Produk", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Total Qty Terjual", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Total Omset (Rp)", style = STYLE_TABLE_HEADER),
                    ExcelCell.text("Kontribusi Omset", style = STYLE_TABLE_HEADER)
                ),
                height = 28.0
            )
        )

        val aggregated = mutableMapOf<String, Pair<Int, Double>>()
        transactions.forEach { txWithItems ->
            txWithItems.items.forEach { item ->
                val current = aggregated[item.productName] ?: (0 to 0.0)
                aggregated[item.productName] = (current.first + item.quantity) to (current.second + item.subtotal)
            }
        }

        val sorted = aggregated.entries.sortedByDescending { it.value.first }
        val totalAllRevenue = sorted.sumOf { it.value.second }.coerceAtLeast(1.0)

        val firstDataRowNum = 5
        var sumQty = 0
        var sumRev = 0.0
        sorted.forEachIndexed { index, (name, stats) ->
            val isAlt = index % 2 == 1
            val textStyle = if (isAlt) STYLE_CELL_TEXT_ALT else STYLE_CELL_TEXT
            val centerStyle = if (isAlt) STYLE_CELL_CENTER_ALT else STYLE_CELL_CENTER
            val currencyStyle = if (isAlt) STYLE_CELL_CURRENCY_ALT else STYLE_CELL_CURRENCY

            sumQty += stats.first
            sumRev += stats.second
            val pct = String.format(Locale.US, "%.1f%%", (stats.second / totalAllRevenue) * 100.0)
            rows.add(
                ExcelRow(
                    cells = listOf(
                        ExcelCell.number((index + 1).toDouble(), style = centerStyle),
                        ExcelCell.text(name, style = textStyle),
                        ExcelCell.number(stats.first.toDouble(), style = centerStyle),
                        ExcelCell.number(stats.second, style = currencyStyle),
                        ExcelCell.text(pct, style = centerStyle)
                    ),
                    height = 22.0
                )
            )
        }

        val lastDataRowNum = (4 + sorted.size).coerceAtLeast(headerRowNum)
        if (sorted.isNotEmpty()) {
            rows.add(
                ExcelRow(
                    cells = listOf(
                        ExcelCell.empty(style = STYLE_TOTAL_TEXT),
                        ExcelCell.text("TOTAL", style = STYLE_TOTAL_TEXT),
                        ExcelCell.formula("SUM(C$firstDataRowNum:C$lastDataRowNum)", sumQty.toDouble(), style = STYLE_TOTAL_CENTER),
                        ExcelCell.formula("SUM(D$firstDataRowNum:D$lastDataRowNum)", sumRev, style = STYLE_TOTAL_CURRENCY),
                        ExcelCell.text("100%", style = STYLE_TOTAL_CENTER)
                    ),
                    height = 26.0
                )
            )
        } else {
            rows.add(
                ExcelRow(
                    cells = listOf(ExcelCell.text("Belum ada data penjualan produk.", style = STYLE_CELL_CENTER)) +
                        List(4) { ExcelCell.empty(style = STYLE_CELL_CENTER) },
                    height = 26.0
                )
            )
            merges.add("A5:E5")
        }

        val minWidths = doubleArrayOf(12.0, 32.0, 18.0, 22.0, 18.0)
        return buildWorksheetXml(
            rows = rows,
            minWidths = minWidths,
            merges = merges,
            freezeAtRow = headerRowNum,
            autoFilterRange = if (sorted.isNotEmpty()) "A$headerRowNum:E$lastDataRowNum" else null,
            isDefaultSelected = false,
            bannerRowsSkipCount = 2
        )
    }

    private fun buildWorksheetXml(
        rows: List<ExcelRow>,
        minWidths: DoubleArray,
        merges: List<String>,
        freezeAtRow: Int,
        autoFilterRange: String?,
        isDefaultSelected: Boolean,
        bannerRowsSkipCount: Int
    ): String {
        val sb = StringBuilder(8192)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")

        val selectedAttr = if (isDefaultSelected) "tabSelected=\"1\" " else ""
        sb.append("<sheetViews><sheetView ${selectedAttr}workbookViewId=\"0\">")
        if (freezeAtRow > 0) {
            sb.append("<pane ySplit=\"$freezeAtRow\" topLeftCell=\"A${freezeAtRow + 1}\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        }
        sb.append("</sheetView></sheetViews>")

        sb.append("<sheetFormatPr defaultRowHeight=\"21\"/>")

        // Compute dynamic column widths skipping decorative banner rows
        val colCount = maxOf(minWidths.size, rows.maxOfOrNull { it.cells.size } ?: 1)
        val widths = DoubleArray(colCount) { idx ->
            minWidths.getOrElse(idx) { 14.0 }
        }
        rows.forEachIndexed { rowIdx, row ->
            if (rowIdx < bannerRowsSkipCount) return@forEachIndexed
            row.cells.forEachIndexed { colIdx, cell ->
                val longestLine = cell.textLengthHint
                val len = (longestLine + 4).toDouble()
                if (len > widths[colIdx]) {
                    widths[colIdx] = len.coerceAtMost(54.0)
                }
            }
        }

        sb.append("<cols>")
        for (i in widths.indices) {
            val colNum = i + 1
            sb.append("<col min=\"$colNum\" max=\"$colNum\" width=\"${"%.1f".format(Locale.US, widths[i])}\" customWidth=\"1\"/>")
        }
        sb.append("</cols>")

        sb.append("<sheetData>")
        rows.forEachIndexed { rowIdx, row ->
            val excelRowNum = rowIdx + 1
            val htAttr = row.height?.let { " ht=\"${"%.1f".format(Locale.US, it)}\" customHeight=\"1\"" } ?: ""
            sb.append("<row r=\"$excelRowNum\"$htAttr>")
            row.cells.forEachIndexed { colIdx, cell ->
                val cellRef = "${columnLetter(colIdx)}$excelRowNum"
                when (cell) {
                    is ExcelCell.EmptyCell -> {
                        if (cell.style != 0) {
                            sb.append("<c r=\"$cellRef\" s=\"${cell.style}\"/>")
                        }
                    }
                    is ExcelCell.TextCell -> {
                        val safe = escapeXml(cell.value)
                        sb.append("<c r=\"$cellRef\" s=\"${cell.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">$safe</t></is></c>")
                    }
                    is ExcelCell.NumberCell -> {
                        val numStr = if (cell.value % 1.0 == 0.0) {
                            cell.value.toLong().toString()
                        } else {
                            cell.value.toString()
                        }
                        sb.append("<c r=\"$cellRef\" s=\"${cell.style}\" t=\"n\"><v>$numStr</v></c>")
                    }
                    is ExcelCell.FormulaCell -> {
                        val cachedStr = if (cell.cachedValue % 1.0 == 0.0) {
                            cell.cachedValue.toLong().toString()
                        } else {
                            cell.cachedValue.toString()
                        }
                        sb.append("<c r=\"$cellRef\" s=\"${cell.style}\"><f>${escapeXml(cell.formula)}</f><v>$cachedStr</v></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")

        if (!autoFilterRange.isNullOrBlank()) {
            sb.append("<autoFilter ref=\"$autoFilterRange\"/>")
        }

        if (merges.isNotEmpty()) {
            sb.append("<mergeCells count=\"${merges.size}\">")
            merges.forEach { ref ->
                sb.append("<mergeCell ref=\"$ref\"/>")
            }
            sb.append("</mergeCells>")
        }

        sb.append("<pageMargins left=\"0.35\" right=\"0.35\" top=\"0.45\" bottom=\"0.45\" header=\"0.2\" footer=\"0.2\"/>")
        sb.append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\" paperSize=\"9\"/>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun columnLetter(indexZeroBased: Int): String {
        var n = indexZeroBased
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = ch.code
                    val isValidXmlChar = code == 0x9 || code == 0xA || code == 0xD || (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
                    if (isValidXmlChar) sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private data class ExcelRow(
        val cells: List<ExcelCell>,
        val height: Double? = null
    )

    private sealed class ExcelCell(val style: Int, val textLengthHint: Int) {
        class EmptyCell(style: Int) : ExcelCell(style, 0)
        class TextCell(val value: String, style: Int) :
            ExcelCell(style, value.lineSequence().maxOfOrNull { it.length } ?: 0)
        class NumberCell(val value: Double, style: Int) : ExcelCell(style, 14)
        class FormulaCell(val formula: String, val cachedValue: Double, style: Int) : ExcelCell(style, 16)

        companion object {
            fun empty(style: Int = 0) = EmptyCell(style)
            fun text(value: String, style: Int = 0) = TextCell(value, style)
            fun number(value: Double, style: Int = 0) = NumberCell(value, style)
            fun formula(formula: String, cachedValue: Double, style: Int = 0) = FormulaCell(formula, cachedValue, style)
        }
    }

    suspend fun exportToPdf(
        context: Context,
        reportTitle: String,
        storeConfig: StoreConfig,
        transactions: List<TransactionWithItems>
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "reports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val sanitizedTitle = reportTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val fileName = "Laporan_${sanitizedTitle}_${System.currentTimeMillis()}.pdf"
            val file = File(exportDir, fileName)

            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val marginX = 36f
            val marginY = 42f

            // PDF styling: Selalu putih bersih dengan aksen hijau emerald spreadsheet profesional (tidak terpengaruh tema)
            val pageBgColor = Color.WHITE
            val titleColor = Color.parseColor("#0E5A30")
            val subtitleColor = Color.parseColor("#4A6150")
            val summaryBgColor = Color.parseColor("#F1F8F4")
            val tableHeaderBgColor = Color.parseColor("#1D6F42")
            val tableHeaderTextColor = Color.WHITE
            val rowBgEven = Color.WHITE
            val rowBgOdd = Color.parseColor("#F6FAF7")
            val textColor = Color.parseColor("#142417")
            val textBoldColor = Color.parseColor("#0E5A30")
            val amountColor = Color.parseColor("#1D6F42")
            val lineColor = Color.parseColor("#D5E3D8")
            val totalBgColor = Color.parseColor("#E8F5E9")
            val totalTextColor = Color.parseColor("#0E5A30")

            // Paints
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = titleColor
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subtitleColor
                textSize = 10f
            }
            val headerBgPaint = Paint().apply {
                color = summaryBgColor
                style = Paint.Style.FILL
            }
            val tableHeaderBgPaint = Paint().apply {
                color = tableHeaderBgColor
                style = Paint.Style.FILL
            }
            val tableHeaderTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tableHeaderTextColor
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 9f
            }
            val textBold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textBoldColor
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val amountPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amountColor
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val linePaint = Paint().apply {
                color = lineColor
                strokeWidth = 0.8f
            }
            val rowBgPaint = Paint().apply {
                style = Paint.Style.FILL
            }
            val totalBgPaint = Paint().apply {
                color = totalBgColor
                style = Paint.Style.FILL
            }
            val totalTextBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = totalTextColor
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val totalTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = totalTextColor
                textSize = 9f
            }

            var currentPageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            canvas.drawColor(pageBgColor)
            var y = marginY

            fun startNewPage() {
                pdfDocument.finishPage(page)
                currentPageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                canvas.drawColor(pageBgColor)
                y = marginY

                canvas.drawText(storeConfig.storeName.uppercase(), marginX, y, titlePaint)
                y += 16f
                canvas.drawText("Laporan Penjualan: $reportTitle (Hal $currentPageNum)", marginX, y, subtitlePaint)
                y += 18f

                canvas.drawRect(marginX, y, 559f, y + 20f, tableHeaderBgPaint)
                canvas.drawText("No", 42f, y + 14f, tableHeaderTextPaint)
                canvas.drawText("No Nota", 65f, y + 14f, tableHeaderTextPaint)
                canvas.drawText("Waktu", 185f, y + 14f, tableHeaderTextPaint)
                canvas.drawText("Metode", 260f, y + 14f, tableHeaderTextPaint)
                canvas.drawText("Menu / Item", 320f, y + 14f, tableHeaderTextPaint)
                canvas.drawText("Total", 495f, y + 14f, tableHeaderTextPaint)
                y += 20f
            }

            // 1. Initial Header (First Page)
            canvas.drawText(storeConfig.storeName.uppercase(), marginX, y, titlePaint)
            y += 16f
            canvas.drawText("Laporan Penjualan: $reportTitle", marginX, y, subtitlePaint)
            y += 14f
            val addressText = if (storeConfig.storeAddress.isNotBlank()) " | ${storeConfig.storeAddress}" else ""
            canvas.drawText("Waktu Cetak: ${CurrencyUtils.formatDateTime(System.currentTimeMillis())}$addressText", marginX, y, subtitlePaint)
            y += 18f

            // 2. Summary Box
            val totalOmset = transactions.sumOf { it.transaction.totalAmount }
            val totalItems = transactions.sumOf { it.items.sumOf { item -> item.quantity } }
            val tunaiCount = transactions.count { it.transaction.paymentMethod == "TUNAI" }
            val qrisCount = transactions.count { it.transaction.paymentMethod == "QRIS" }

            canvas.drawRoundRect(marginX, y, 559f, y + 48f, 6f, 6f, headerBgPaint)
            canvas.drawText("Total Pendapatan: ${CurrencyUtils.formatRupiah(totalOmset)}", 48f, y + 18f, textBold)
            canvas.drawText("Total Transaksi: ${transactions.size} Struk", 48f, y + 34f, textPaint)
            canvas.drawText("Total Item Terjual: $totalItems Porsi/Pcs", 310f, y + 18f, textPaint)
            canvas.drawText("Metode: $tunaiCount Tunai, $qrisCount QRIS", 310f, y + 34f, textPaint)
            y += 66f

            // 3. Table Header
            canvas.drawRect(marginX, y, 559f, y + 20f, tableHeaderBgPaint)
            canvas.drawText("No", 42f, y + 14f, tableHeaderTextPaint)
            canvas.drawText("No Nota", 65f, y + 14f, tableHeaderTextPaint)
            canvas.drawText("Waktu", 185f, y + 14f, tableHeaderTextPaint)
            canvas.drawText("Metode", 260f, y + 14f, tableHeaderTextPaint)
            canvas.drawText("Menu / Item", 320f, y + 14f, tableHeaderTextPaint)
            canvas.drawText("Total", 495f, y + 14f, tableHeaderTextPaint)
            y += 20f

            // 4. Rows (Dynamic Multi-page)
            val itemsColWidth = 170
            for ((index, item) in transactions.withIndex()) {
                val tx = item.transaction
                val itemsSummary = item.items.joinToString(", ") { "${it.productName} (${it.quantity}x)" }

                val wrappedItemsLayout = StaticLayout.Builder.obtain(itemsSummary, 0, itemsSummary.length, textPaint, itemsColWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                val rowHeight = wrappedItemsLayout.height.toFloat().coerceAtLeast(17f) + 6f

                if (y + rowHeight > pageHeight - marginY) {
                    startNewPage()
                }

                val rowBg = if (index % 2 == 1) rowBgOdd else rowBgEven
                rowBgPaint.color = rowBg
                canvas.drawRect(marginX, y, 559f, y + rowHeight, rowBgPaint)

                canvas.drawText("${index + 1}", 42f, y + 13f, textPaint)
                canvas.drawText(tx.invoiceNumber, 65f, y + 13f, textPaint)
                canvas.drawText(CurrencyUtils.formatTimeOnly(tx.timestamp), 185f, y + 13f, textPaint)
                canvas.drawText(tx.paymentMethod, 260f, y + 13f, textPaint)

                canvas.save()
                canvas.translate(320f, y + 4f)
                wrappedItemsLayout.draw(canvas)
                canvas.restore()

                canvas.drawText(CurrencyUtils.formatRupiah(tx.totalAmount), 495f, y + 13f, amountPaint)

                y += rowHeight
                canvas.drawLine(marginX, y, 559f, y, linePaint)
            }

            // 5. Grand Total Summary Row
            if (transactions.isNotEmpty()) {
                if (y + 24f > pageHeight - marginY) {
                    startNewPage()
                }
                canvas.drawRect(marginX, y, 559f, y + 22f, totalBgPaint)
                canvas.drawText("TOTAL KESELURUHAN", 65f, y + 15f, totalTextBoldPaint)
                canvas.drawText("${transactions.size} Struk", 185f, y + 15f, totalTextPaint)
                canvas.drawText("$totalItems item", 320f, y + 15f, totalTextPaint)
                canvas.drawText(CurrencyUtils.formatRupiah(totalOmset), 495f, y + 15f, totalTextBoldPaint)
                y += 24f
            }

            canvas.drawText("Halaman $currentPageNum / $currentPageNum", 260f, pageHeight - 20f, subtitlePaint)

            pdfDocument.finishPage(page)

            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            }
            pdfDocument.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting PDF: ${e.message}", e)
            null
        }
    }

    fun shareExportedFile(context: Context, uri: Uri, mimeType: String, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Berikut adalah $title yang diekspor dari aplikasi kasir.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Bagikan / Simpan Laporan").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing exported file: ${e.message}", e)
        }
    }

    // Style indices mapped 1-to-1 with <cellXfs> in STYLES_XML
    private const val STYLE_BANNER_TITLE = 1
    private const val STYLE_BANNER_SUB = 2
    private const val STYLE_BANNER_META = 3
    private const val STYLE_SECTION_LABEL = 4
    private const val STYLE_TABLE_HEADER = 5
    private const val STYLE_CELL_TEXT = 6
    private const val STYLE_CELL_CENTER = 7
    private const val STYLE_CELL_CURRENCY = 8
    private const val STYLE_CELL_TEXT_ALT = 9
    private const val STYLE_CELL_CENTER_ALT = 10
    private const val STYLE_CELL_CURRENCY_ALT = 11
    private const val STYLE_BADGE_TUNAI = 12
    private const val STYLE_BADGE_QRIS = 13
    private const val STYLE_KPI_LABEL = 14
    private const val STYLE_KPI_CURRENCY = 15
    private const val STYLE_KPI_INT = 16
    private const val STYLE_KPI_DETAIL = 17
    private const val STYLE_KPI_LABEL_ACCENT = 18
    private const val STYLE_KPI_CURRENCY_ACCENT = 19
    private const val STYLE_KPI_DETAIL_ACCENT = 20
    private const val STYLE_TOTAL_TEXT = 21
    private const val STYLE_TOTAL_CURRENCY = 22
    private const val STYLE_TOTAL_CENTER = 23

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Laporan Penjualan" sheetId="1" r:id="rId1"/>
    <sheet name="Rincian Item" sheetId="2" r:id="rId2"/>
    <sheet name="Produk Terlaris" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun getStylesXml(themeMode: String = ""): String {
        // Professional Excel Green Theme (selalu hijau klasik spreadsheet, konsisten & independen dari tema)
        val primaryHex = "FF1D6F42"        // Rich Emerald Green
        val primaryAltHex = "FF0E5A30"     // Deep Forest Green
        val headerFontColor = "FF1D6F42"   // Dark Emerald Green text
        val subLabelFill = "FFE8F5E9"      // Soft Mint Green pill background
        val zebraFill = "FFF4F9F5"         // Soft alternate row zebra tint
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="&quot;Rp &quot;#,##0"/>
  </numFmts>
  <fonts count="8">
    <font><sz val="11"/><color rgb="FF1F2421"/><name val="Calibri"/></font>
    <font><b/><sz val="15"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="$headerFontColor"/><name val="Calibri"/></font>
    <font><sz val="10"/><color rgb="$headerFontColor"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF1F2421"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="$headerFontColor"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FF0D47A1"/><name val="Calibri"/></font>
  </fonts>
  <fills count="10">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="$primaryAltHex"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="$primaryHex"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="$subLabelFill"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="$subLabelFill"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="$zebraFill"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE3F2FD"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFF3E0"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFFFFF"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="4">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD5E0D9"/></left>
      <right style="thin"><color rgb="FFD5E0D9"/></right>
      <top style="thin"><color rgb="FFD5E0D9"/></top>
      <bottom style="thin"><color rgb="FFD5E0D9"/></bottom>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="$primaryHex"/></left>
      <right style="thin"><color rgb="$primaryHex"/></right>
      <top style="medium"><color rgb="$primaryHex"/></top>
      <bottom style="medium"><color rgb="$primaryHex"/></bottom>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFE0C088"/></left>
      <right style="thin"><color rgb="FFE0C088"/></right>
      <top style="medium"><color rgb="FFB26A00"/></top>
      <bottom style="medium"><color rgb="FFB26A00"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="24">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="left" vertical="center" indent="1"/></xf>
    <xf numFmtId="0" fontId="4" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="left" vertical="center" indent="1"/></xf>
    <xf numFmtId="0" fontId="4" fillId="5" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="left" vertical="center" indent="1"/></xf>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" indent="1"/></xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="9" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="9" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="0" fillId="9" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="6" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="6" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="0" fillId="6" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="6" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="7" fillId="7" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="5" fillId="9" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" indent="1"/></xf>
    <xf numFmtId="164" fontId="5" fillId="9" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="5" fillId="9" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="4" fillId="9" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" indent="1"/></xf>
    <xf numFmtId="164" fontId="3" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="0" fontId="5" fillId="8" borderId="3" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="164" fontId="5" fillId="8" borderId="3" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="5" fillId="8" borderId="3" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
    }
}
