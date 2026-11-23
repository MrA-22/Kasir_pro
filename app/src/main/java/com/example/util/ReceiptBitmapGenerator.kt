package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

object ReceiptBitmapGenerator {

    private const val TAG = "ReceiptBitmapGen"

    /**
     * Generates a high-resolution, properly centered and non-clipped receipt bitmap
     * matching the thermal/digital receipt layout.
     */
    fun generateReceiptBitmap(
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ): Bitmap {
        val width = 600
        val leftMargin = 36f
        val rightMargin = (width - 36).toFloat()
        val contentWidth = (rightMargin - leftMargin).toInt()
        val centerX = width / 2f

        // IMPORTANT: All TextPaints passed into StaticLayout MUST use Paint.Align.LEFT.
        // Centering is handled solely via Layout.Alignment.ALIGN_CENTER; combining
        // Paint.Align.CENTER with StaticLayout causes double-offset left clipping.
        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444")
            textSize = 19f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        val metaTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val metaTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555")
            textSize = 19f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        val colHeaderLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val colHeaderRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val itemBoldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val itemBoldRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val itemSubLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666")
            textSize = 17f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        val itemSubRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D35400")
            textSize = 16f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }

        val totalOrangeLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D35400")
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val totalOrangeRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D35400")
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val paymentRegularLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 19f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        val paymentRegularRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 19f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }

        val footerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            textSize = 19f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        val footerCaptionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#777777")
            textSize = 15f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }

        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E")
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
        }

        val barcodeBarPaint = Paint().apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.FILL
        }

        // Exact layout constants shared between height measurement and drawing
        val topPadding = 42f
        val bottomPadding = 44f
        val dividerBeforeGap = 16f
        val dividerAfterGap = 24f
        val dividerTotal = dividerBeforeGap + dividerAfterGap

        val storeNameText = storeConfig.storeName.ifBlank { "KASIR PRO" }.uppercase()
        val noNotaText = "No. Nota : ${transaction.invoiceNumber}"
        val tanggalText = "Tanggal  : ${CurrencyUtils.formatDateTime(transaction.timestamp)}"
        val paymentDisplay = if (transaction.paymentMethod == "TUNAI") "Tunai" else "QRIS"
        val metodeText = "Metode   : $paymentDisplay"
        val customerText = if (transaction.customerName.isNotBlank()) "Pelanggan: ${transaction.customerName}" else ""
        val noteText = if (transaction.note.isNotBlank()) "Catatan  : ${transaction.note}" else ""

        val subtotalColWidth = 175
        val productNameWidth = (contentWidth - subtotalColWidth - 12).coerceAtLeast(160)
        val greeting = storeConfig.receiptFooter.ifBlank { "Terima kasih atas kunjungan Anda!" }
        val caption = "Simpan nota ini sebagai bukti transaksi yang sah"
        val barcodeHeight = 56f
        val barcodeHriHeight = 20f

        // --- PASS 1: Exact Height Computation ---
        var calcHeight = topPadding

        // 1. Header
        calcHeight += getWrappedTextHeight(storeNameText, headerTitlePaint, contentWidth)
        if (storeConfig.storeAddress.isNotBlank()) {
            calcHeight += 6f
            calcHeight += getWrappedTextHeight(storeConfig.storeAddress, headerSubPaint, contentWidth)
        }
        if (storeConfig.storePhone.isNotBlank()) {
            calcHeight += 4f
            calcHeight += getWrappedTextHeight("Telp / WA: ${storeConfig.storePhone}", headerSubPaint, contentWidth)
        }
        calcHeight += dividerTotal

        // 2. Meta
        calcHeight += getWrappedTextHeight(noNotaText, metaTitlePaint, contentWidth)
        calcHeight += 6f + getWrappedTextHeight(tanggalText, metaTextPaint, contentWidth)
        calcHeight += 6f + getWrappedTextHeight(metodeText, metaTextPaint, contentWidth)
        if (customerText.isNotBlank()) {
            calcHeight += 6f + getWrappedTextHeight(customerText, metaTextPaint, contentWidth)
        }
        if (noteText.isNotBlank()) {
            calcHeight += 6f + getWrappedTextHeight(noteText, metaTextPaint, contentWidth)
        }
        calcHeight += dividerTotal

        // 3. Table Column Header
        calcHeight += getWrappedTextHeight("PRODUK / ITEM", colHeaderLeft, contentWidth)
        calcHeight += dividerTotal

        // 4. Items
        items.forEach { item ->
            val nameHeight = getWrappedTextHeight(item.productName.uppercase(), itemBoldLeft, productNameWidth)
                .coerceAtLeast(24f)
            calcHeight += nameHeight
            calcHeight += 4f // gap between name and qty x price
            calcHeight += 22f // qty x price line height
            calcHeight += 10f // bottom gap after item
        }
        calcHeight += dividerTotal

        // 5. Total Block
        calcHeight += 32f
        if (transaction.paymentMethod == "TUNAI") {
            calcHeight += 26f // Tunai Diterima
            calcHeight += 26f // Kembalian
        } else {
            calcHeight += 26f // Status Pembayaran
        }
        calcHeight += dividerTotal

        // 6. Footer
        calcHeight += getWrappedTextHeight(greeting, footerTextPaint, contentWidth)
        calcHeight += 18f
        calcHeight += barcodeHeight + 6f + barcodeHriHeight
        calcHeight += 14f
        calcHeight += getWrappedTextHeight(caption, footerCaptionPaint, contentWidth)
        calcHeight += bottomPadding

        val height = ceil(calcHeight).toInt().coerceAtLeast(320)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Pure Crisp White Background + subtle outer border
        canvas.drawColor(Color.WHITE)
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(1f, 1f, (width - 1).toFloat(), (height - 1).toFloat(), borderPaint)

        // --- PASS 2: Drawing with identical y-increments ---
        var y = topPadding

        // 1. Header Drawing
        y += drawWrappedText(canvas, storeNameText, leftMargin, y, headerTitlePaint, contentWidth, Layout.Alignment.ALIGN_CENTER)
        if (storeConfig.storeAddress.isNotBlank()) {
            y += 6f
            y += drawWrappedText(canvas, storeConfig.storeAddress, leftMargin, y, headerSubPaint, contentWidth, Layout.Alignment.ALIGN_CENTER)
        }
        if (storeConfig.storePhone.isNotBlank()) {
            y += 4f
            y += drawWrappedText(canvas, "Telp / WA: ${storeConfig.storePhone}", leftMargin, y, headerSubPaint, contentWidth, Layout.Alignment.ALIGN_CENTER)
        }

        // Divider 1
        y += dividerBeforeGap
        canvas.drawLine(leftMargin, y, rightMargin, y, dashPaint)
        y += dividerAfterGap

        // 2. Transaction Meta Drawing
        y += drawWrappedText(canvas, noNotaText, leftMargin, y, metaTitlePaint, contentWidth)
        y += 6f
        y += drawWrappedText(canvas, tanggalText, leftMargin, y, metaTextPaint, contentWidth)
        y += 6f
        y += drawWrappedText(canvas, metodeText, leftMargin, y, metaTextPaint, contentWidth)
        if (customerText.isNotBlank()) {
            y += 6f
            y += drawWrappedText(canvas, customerText, leftMargin, y, metaTextPaint, contentWidth)
        }
        if (noteText.isNotBlank()) {
            y += 6f
            y += drawWrappedText(canvas, noteText, leftMargin, y, metaTextPaint, contentWidth)
        }

        // Divider 2
        y += dividerBeforeGap
        canvas.drawLine(leftMargin, y, rightMargin, y, dashPaint)
        y += dividerAfterGap

        // 3. Table Column Headers
        val colHeaderHeight = getWrappedTextHeight("PRODUK / ITEM", colHeaderLeft, contentWidth)
        val headerBaseline = y - colHeaderLeft.fontMetrics.ascent
        canvas.drawText("PRODUK / ITEM", leftMargin, headerBaseline, colHeaderLeft)
        canvas.drawText("SUBTOTAL", rightMargin, headerBaseline, colHeaderRight)
        y += colHeaderHeight

        // Divider 3
        y += dividerBeforeGap
        canvas.drawLine(leftMargin, y, rightMargin, y, dashPaint)
        y += dividerAfterGap

        // 4. Items List Drawing
        for (item in items) {
            val nameText = item.productName.uppercase()
            val nameHeight = drawWrappedText(canvas, nameText, leftMargin, y, itemBoldLeft, productNameWidth)
                .coerceAtLeast(24f)

            // Draw subtotal aligned cleanly to the first line of the item name
            val subtotalBaseline = y - itemBoldRight.fontMetrics.ascent
            canvas.drawText(CurrencyUtils.formatRupiah(item.subtotal), rightMargin, subtotalBaseline, itemBoldRight)

            y += nameHeight + 4f

            val qtyPrice = "${item.quantity} x ${CurrencyUtils.formatRupiah(item.pricePerUnit)}"
            val subBaseline = y - itemSubLeft.fontMetrics.ascent
            canvas.drawText(qtyPrice, leftMargin, subBaseline, itemSubLeft)

            if (item.discountValue > 0) {
                val discText = if (item.isDiscountPercentage) {
                    "Disc ${item.discountValue.toInt()}%"
                } else {
                    "Disc ${CurrencyUtils.formatRupiah(item.discountValue)}"
                }
                canvas.drawText("($discText)", rightMargin, subBaseline, itemSubRight)
            }

            y += 22f + 10f
        }

        // Divider 4
        y += dividerBeforeGap
        canvas.drawLine(leftMargin, y, rightMargin, y, dashPaint)
        y += dividerAfterGap

        // 5. Total Block Drawing
        val totalBaseline = y - totalOrangeRight.fontMetrics.ascent
        canvas.drawText("TOTAL TRANSAKSI", leftMargin, totalBaseline, totalOrangeLeft)
        canvas.drawText(CurrencyUtils.formatRupiah(transaction.totalAmount), rightMargin, totalBaseline, totalOrangeRight)
        y += 32f

        if (transaction.paymentMethod == "TUNAI") {
            val payBaseline = y - paymentRegularLeft.fontMetrics.ascent
            canvas.drawText("Tunai Diterima", leftMargin, payBaseline, paymentRegularLeft)
            canvas.drawText(CurrencyUtils.formatRupiah(transaction.amountReceived), rightMargin, payBaseline, paymentRegularRight)
            y += 26f

            val changeBaseline = y - itemBoldLeft.fontMetrics.ascent
            canvas.drawText("Kembalian", leftMargin, changeBaseline, itemBoldLeft)
            canvas.drawText(CurrencyUtils.formatRupiah(transaction.changeAmount), rightMargin, changeBaseline, itemBoldRight)
            y += 26f
        } else {
            val statusBaseline = y - paymentRegularLeft.fontMetrics.ascent
            canvas.drawText("Status Pembayaran", leftMargin, statusBaseline, paymentRegularLeft)
            canvas.drawText("LUNAS (QRIS)", rightMargin, statusBaseline, itemBoldRight)
            y += 26f
        }

        // Divider 5
        y += dividerBeforeGap
        canvas.drawLine(leftMargin, y, rightMargin, y, dashPaint)
        y += dividerAfterGap

        // 6. Footer Drawing
        y += drawWrappedText(canvas, greeting, leftMargin, y, footerTextPaint, contentWidth, Layout.Alignment.ALIGN_CENTER)
        y += 18f

        drawBarcodeGraphic(
            canvas = canvas,
            paint = barcodeBarPaint,
            hriPaint = footerCaptionPaint,
            centerX = centerX,
            topY = y,
            maxWidth = contentWidth.toFloat(),
            height = barcodeHeight,
            data = transaction.invoiceNumber
        )
        y += barcodeHeight + 6f + barcodeHriHeight + 14f

        drawWrappedText(canvas, caption, leftMargin, y, footerCaptionPaint, contentWidth, Layout.Alignment.ALIGN_CENTER)

        return bitmap
    }

    private fun getWrappedTextHeight(text: String, paint: TextPaint, width: Int): Float {
        if (text.isBlank()) return 0f
        val safeWidth = width.coerceAtLeast(1)
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.05f)
            .setIncludePad(false)
            .build()
        return staticLayout.height.toFloat()
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        leftX: Float,
        topY: Float,
        paint: TextPaint,
        width: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        if (text.isBlank()) return 0f
        val safeWidth = width.coerceAtLeast(1)
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(align)
            .setLineSpacing(2f, 1.05f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(leftX, topY)
        staticLayout.draw(canvas)
        canvas.restore()

        return staticLayout.height.toFloat()
    }

    private fun drawBarcodeGraphic(
        canvas: Canvas,
        paint: Paint,
        hriPaint: TextPaint,
        centerX: Float,
        topY: Float,
        maxWidth: Float,
        height: Float,
        data: String
    ) {
        val sanitized = Code128Encoder.sanitizeCode128BText(data)
        val pattern = Code128Encoder.encodeToBarWidths(sanitized)
        val totalModules = pattern.sum().coerceAtLeast(1)
        val quietModules = 20
        val unitWidth = ((maxWidth * 0.94f) / (totalModules + quietModules)).coerceIn(1.2f, 2.2f)
        val totalBarcodeWidth = totalModules * unitWidth

        var currentX = centerX - (totalBarcodeWidth / 2f)
        for (i in pattern.indices) {
            val barW = pattern[i] * unitWidth
            if (i % 2 == 0) {
                canvas.drawRect(currentX, topY, currentX + barW, topY + height, paint)
            }
            currentX += barW
        }

        drawWrappedText(
            canvas = canvas,
            text = sanitized,
            leftX = centerX - (maxWidth / 2f),
            topY = topY + height + 6f,
            paint = hriPaint,
            width = maxWidth.toInt(),
            align = Layout.Alignment.ALIGN_CENTER
        )
    }

    suspend fun saveReceiptToCacheAndGetUri(
        context: Context,
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateReceiptBitmap(storeConfig, transaction, items)
            val cacheDir = File(context.cacheDir, "receipts")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, "Nota_${transaction.invoiceNumber}.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt image: ${e.message}", e)
            null
        }
    }

    fun shareReceiptImage(
        context: Context,
        uri: Uri,
        invoiceNumber: String
    ) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Nota Pembayaran $invoiceNumber")
                putExtra(Intent.EXTRA_TEXT, "Berikut adalah nota pembayaran Anda ($invoiceNumber). Terima kasih atas kunjungan Anda!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = context.packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Foto Nota").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing receipt image: ${e.message}", e)
        }
    }

    /**
     * Native Android System Printing using PrintedPdfDocument & PrintDocumentAdapter.
     * Dynamically adapts to any chosen paper size (A6, A5, A4, Letter) without clipping.
     */
    fun printViaAndroidSystem(
        context: Context,
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Log.e(TAG, "PrintManager not available on this device")
                return
            }

            val jobName = "Nota_${transaction.invoiceNumber}"
            val printAdapter = object : PrintDocumentAdapter() {
                private var mPdfDocument: PrintedPdfDocument? = null
                private var pageWidthPts = 298
                private var pageHeightPts = 421
                private var cachedBitmap: Bitmap? = null
                private var totalPages = 1

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val effectiveAttrs = newAttributes ?: PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    mPdfDocument = PrintedPdfDocument(context, effectiveAttrs)

                    val mediaSize = effectiveAttrs.mediaSize
                    if (mediaSize != null) {
                        pageWidthPts = ((mediaSize.widthMils * 72f) / 1000f).toInt().coerceAtLeast(200)
                        pageHeightPts = ((mediaSize.heightMils * 72f) / 1000f).toInt().coerceAtLeast(280)
                    }

                    val bitmap = generateReceiptBitmap(storeConfig, transaction, items)
                    cachedBitmap = bitmap

                    val marginX = 18f
                    val marginY = 20f
                    val drawWidth = (pageWidthPts - marginX * 2f).coerceAtMost(320f)
                    val scale = drawWidth / bitmap.width.toFloat()
                    val scaledHeight = bitmap.height * scale
                    val contentPageHeight = (pageHeightPts - marginY * 2f).coerceAtLeast(100f)

                    totalPages = ceil((scaledHeight / contentPageHeight).toDouble()).toInt().coerceAtLeast(1)

                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(totalPages)
                        .build()

                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onWriteCancelled()
                        return
                    }

                    val pdfDoc = mPdfDocument ?: return
                    val receiptBitmap = cachedBitmap ?: generateReceiptBitmap(storeConfig, transaction, items)

                    val marginY = 20f
                    val maxReceiptDrawWidth = (pageWidthPts - 36f).coerceAtMost(320f)
                    val leftOffset = ((pageWidthPts - maxReceiptDrawWidth) / 2f).coerceAtLeast(18f)
                    val scale = maxReceiptDrawWidth / receiptBitmap.width.toFloat()
                    val contentPageHeight = (pageHeightPts - marginY * 2f).coerceAtLeast(100f)
                    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                    try {
                        for (i in 0 until totalPages) {
                            val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPts, pageHeightPts, i + 1).create()
                            val page = pdfDoc.startPage(pageInfo)
                            val canvas = page.canvas
                            canvas.drawColor(Color.WHITE)

                            val srcTop = ((i * contentPageHeight) / scale).toInt().coerceIn(0, receiptBitmap.height)
                            val srcBottom = (((i + 1) * contentPageHeight) / scale).toInt().coerceIn(srcTop, receiptBitmap.height)

                            if (srcBottom > srcTop) {
                                val srcRect = Rect(0, srcTop, receiptBitmap.width, srcBottom)
                                val dstBottom = marginY + (srcBottom - srcTop) * scale
                                val dstRect = RectF(leftOffset, marginY, leftOffset + maxReceiptDrawWidth, dstBottom)
                                canvas.drawBitmap(receiptBitmap, srcRect, dstRect, bitmapPaint)
                            }
                            pdfDoc.finishPage(page)
                        }

                        destination?.fileDescriptor?.let { fd ->
                            FileOutputStream(fd).use { out ->
                                pdfDoc.writeTo(out)
                                out.flush()
                            }
                        }

                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing print document: ${e.message}", e)
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfDoc.close()
                        mPdfDocument = null
                        cachedBitmap = null
                    }
                }
            }

            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating system print: ${e.message}", e)
        }
    }
}
