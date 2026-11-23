package com.example

import android.content.Context
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionWithItems
import com.example.util.CurrencyUtils
import com.example.util.LicenseManager
import com.example.util.ReceiptBitmapGenerator
import com.example.util.ReportExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `currency formatting contains expected value`() {
        val formatted = CurrencyUtils.formatRupiah(50000.0)
        assertTrue(formatted.contains("50.000"))
    }

    @Test
    fun `trial expires permanently after 5 minutes until valid API key is entered`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val t0 = 1_700_000_000_000L

        LicenseManager.resetTrialForAdminTesting(context, nowMs = t0)

        // 1. Baru dibuka: uji coba aktif 300 detik (5 menit)
        val initial = LicenseManager.getTrialState(context, nowMs = t0)
        assertFalse(initial.isActivated)
        assertFalse(initial.isTrialExpired)
        assertEquals(300, initial.remainingSeconds)
        assertTrue(initial.deviceId.startsWith("KSR-"))

        // 2. Berjalan 4 menit 59 detik: masih aktif 1 detik
        val almostExpired = LicenseManager.getTrialState(context, nowMs = t0 + 299_000L)
        assertFalse(almostExpired.isTrialExpired)
        assertEquals(1, almostExpired.remainingSeconds)

        // 3. Tepat 5 menit: terkunci permanen
        val expired = LicenseManager.getTrialState(context, nowMs = t0 + 300_000L)
        assertFalse(expired.isActivated)
        assertTrue(expired.isTrialExpired)
        assertEquals(0, expired.remainingSeconds)

        // 4. App ditutup/dibuka lagi (atau jam dimundurkan): tetap terkunci!
        val reopenedLater = LicenseManager.getTrialState(context, nowMs = t0 + 10_000L)
        assertFalse(reopenedLater.isActivated)
        assertTrue(reopenedLater.isTrialExpired)

        // 5. Kode API salah ditolak
        assertFalse(LicenseManager.activateWithKey(context, "KPRO-AAAA-BBBB-CCCC"))
        assertTrue(LicenseManager.getTrialState(context, nowMs = t0 + 310_000L).isTrialExpired)

        // 6. Kode API khusus untuk deviceId ini membuka kunci permanen
        val validKey = LicenseManager.generateLicenseKeyForDevice(expired.deviceId)
        assertTrue(LicenseManager.activateWithKey(context, validKey))

        val unlocked = LicenseManager.getTrialState(context, nowMs = t0 + 999_999_999L)
        assertTrue(unlocked.isActivated)
        assertFalse(unlocked.isTrialExpired)
    }

    @Test
    fun `server verified key persists activated state even with server-only secret`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val t0 = 1_700_000_000_000L

        LicenseManager.resetTrialForAdminTesting(context, nowMs = t0)
        val expired = LicenseManager.getTrialState(context, nowMs = t0 + 300_000L)
        assertTrue(expired.isTrialExpired)

        // Kunci yang dihasilkan oleh secret khusus server (berbeda dari kunci lokal)
        val serverOnlyKey = "KPRO-SERV-9988-7766"
        assertFalse(LicenseManager.verifyLicenseKey(expired.deviceId, serverOnlyKey))

        assertTrue(LicenseManager.activateFromServerVerification(context, serverOnlyKey))
        val afterServerVerify = LicenseManager.getTrialState(context, nowMs = t0 + 500_000L)
        assertTrue(afterServerVerify.isActivated)
        assertFalse(afterServerVerify.isTrialExpired)

        // Jika Admin menonaktifkan lisensi dari panel website, aplikasi terkunci kembali
        LicenseManager.revokeLocalActivation(context)
        val afterRevoke = LicenseManager.getTrialState(context, nowMs = t0 + 505_000L)
        assertFalse(afterRevoke.isActivated)
        assertTrue(afterRevoke.isTrialExpired)
    }

    @Test
    fun `receipt bitmap scales cleanly for notes, multi-line store headers, and long footers`() {
        val baseConfig = StoreConfig(
            storeName = "AYAM GORENG",
            storeAddress = "Jl. Merdeka 1",
            storePhone = "0812-3456-7890",
            receiptFooter = "Terima Kasih"
        )
        val longConfig = StoreConfig(
            storeName = "AYAM & IKAN GORENG BALADO KHAS NUSANTARA",
            storeAddress = "Jalan Kwek Kwek Kwek No. 88 Kelurahan Merdeka",
            storePhone = "0812-3456-7890",
            receiptFooter = "Terima Kasih Atas Kunjungan Anda!\nBarang yang sudah dibeli tidak dapat ditukar atau dikembalikan."
        )
        val baseTx = Transaction(
            id = 1L,
            invoiceNumber = "INV-20261121-040228",
            totalAmount = 90000.0,
            paymentMethod = "TUNAI",
            amountReceived = 100000.0,
            changeAmount = 10000.0,
            totalItemsCount = 6
        )
        val richTx = baseTx.copy(
            customerName = "Pelanggan Tetap",
            note = "Sambal dipisah dan es batu sedikit"
        )
        val items = listOf(
            TransactionItem(
                transactionId = 1L,
                productId = 1L,
                productName = "AYAM GEPREK",
                quantity = 3,
                pricePerUnit = 15000.0,
                subtotal = 45000.0
            ),
            TransactionItem(
                transactionId = 1L,
                productId = 2L,
                productName = "NASI GORENG",
                quantity = 3,
                pricePerUnit = 15000.0,
                subtotal = 45000.0
            )
        )

        val baseBitmap = ReceiptBitmapGenerator.generateReceiptBitmap(baseConfig, baseTx, items)
        val richBitmap = ReceiptBitmapGenerator.generateReceiptBitmap(longConfig, richTx, items)

        assertEquals(600, baseBitmap.width)
        assertEquals(600, richBitmap.width)
        assertTrue(baseBitmap.height > 400)
        assertTrue(richBitmap.height > baseBitmap.height)

        // Verify centered StaticLayout with Paint.Align.LEFT never shifts line bounds negative
        val paint = TextPaint().apply {
            textSize = 29f
            textAlign = Paint.Align.LEFT
        }
        val contentWidth = 528
        val layout = StaticLayout.Builder.obtain("AYAM & IKAN GORENG BALADO", 0, 25, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        assertTrue(layout.getLineLeft(0) >= 0f)
        assertTrue(layout.getLineRight(0) <= contentWidth.toFloat())

        baseBitmap.recycle()
        richBitmap.recycle()
    }

    @Test
    fun `report exporter creates non-empty xlsx file via FileProvider`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storeConfig = StoreConfig(storeName = "Toko Sukses")
        val tx = Transaction(
            id = 1L,
            invoiceNumber = "INV-EXCEL-001",
            totalAmount = 30000.0,
            paymentMethod = "QRIS",
            amountReceived = 30000.0,
            totalItemsCount = 2
        )
        val items = listOf(
            TransactionItem(
                transactionId = 1L,
                productId = 1L,
                productName = "Ayam Bakar",
                categoryName = "Makanan",
                quantity = 2,
                pricePerUnit = 15000.0,
                subtotal = 30000.0
            )
        )

        val uri = ReportExporter.exportToExcel(
            context = context,
            reportTitle = "Harian",
            storeConfig = storeConfig,
            transactions = listOf(TransactionWithItems(tx, items))
        )

        assertNotNull(uri)
        val reportsDir = File(context.cacheDir, "reports")
        val xlsxFiles = reportsDir.listFiles { _, name -> name.endsWith(".xlsx") }
        assertNotNull(xlsxFiles)
        assertTrue(xlsxFiles!!.isNotEmpty())
        assertTrue(xlsxFiles.first().length() > 1000L)

        val csvUri = ReportExporter.exportToCsv(
            context = context,
            reportTitle = "Harian",
            storeConfig = storeConfig,
            transactions = listOf(TransactionWithItems(tx, items))
        )
        assertNotNull(csvUri)
        val csvFiles = reportsDir.listFiles { _, name -> name.endsWith(".csv") }
        assertNotNull(csvFiles)
        assertTrue(csvFiles!!.isNotEmpty())
        val csvContent = csvFiles.first().readText(Charsets.UTF_8)
        assertTrue(csvContent.contains("INV-EXCEL-001"))
        assertFalse(csvContent.contains("30000.0"))
    }
}
