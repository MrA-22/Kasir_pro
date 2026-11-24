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
    fun `go webhook request payload and status query url match server contract`() {
        val raw = LicenseManager.buildLicenseRequestPayload("ksr-b250-b0b5", "Warung Maju")
        val json = org.json.JSONObject(raw)
        assertEquals("KSR-B250-B0B5", json.getString("device_id"))
        assertEquals("Warung Maju", json.getString("business_name"))

        val blankStoreJson = org.json.JSONObject(LicenseManager.buildLicenseRequestPayload("KSR-B250-B0B5", ""))
        assertEquals("-", blankStoreJson.getString("business_name"))

        assertEquals(
            "https://webhook.mrapro.site/api/license/status?device_id=KSR-B250-B0B5",
            LicenseManager.buildStatusQueryUrl("https://webhook.mrapro.site", "KSR-B250-B0B5")
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val overridden = LicenseManager.setCustomWebhookBaseUrl(context, "webhook.mrapro.site/admin")
        assertEquals("https://webhook.mrapro.site", overridden)
        assertEquals("https://webhook.mrapro.site", LicenseManager.getEffectiveGatewayBaseUrl(context))
        LicenseManager.setCustomWebhookBaseUrl(context, "")
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

    @Test
    fun `firebase sync manager dynamically initializes, switches configs, rejects invalid json, and disconnects`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.util.FirebaseSyncManager.disconnect(context)

        val firstJson = """
            {
              "project_info": {
                "project_number": "111222333444",
                "project_id": "demo-pos-first",
                "storage_bucket": "demo-pos-first.firebasestorage.app"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:111222333444:android:aabbccdd11223344",
                    "android_client_info": {
                      "package_name": "com.example.otherapp"
                    }
                  },
                  "api_key": [
                    {
                      "current_key": "AIzaSyDummyTestKeyOne1234567890"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val firstResult = com.example.util.FirebaseSyncManager.initializeDetailed(context, firstJson)
        assertTrue(firstResult.success)
        assertEquals("demo-pos-first", firstResult.projectId)
        assertEquals("com.example.otherapp", firstResult.clientPackageName)
        assertTrue(com.example.util.FirebaseSyncManager.isReady())
        assertEquals("demo-pos-first", com.example.util.FirebaseSyncManager.getActiveProjectId())
        assertNotNull(com.example.util.FirebaseSyncManager.getFirestore())

        val secondJson = """
            {
              "project_info": {
                "project_number": "999888777666",
                "project_id": "demo-pos-second",
                "storage_bucket": "demo-pos-second.firebasestorage.app"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:999888777666:android:9988776655443322",
                    "android_client_info": {
                      "package_name": "${context.packageName}"
                    }
                  },
                  "api_key": [
                    {
                      "current_key": "AIzaSyDummyTestKeyTwo0987654321"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val switchResult = com.example.util.FirebaseSyncManager.initializeDetailed(context, secondJson)
        assertTrue(switchResult.success)
        assertEquals("demo-pos-second", switchResult.projectId)
        assertEquals(context.packageName, switchResult.clientPackageName)
        assertEquals("demo-pos-second", com.example.util.FirebaseSyncManager.getActiveProjectId())

        val invalidResult = com.example.util.FirebaseSyncManager.initializeDetailed(context, "{invalid")
        assertFalse(invalidResult.success)
        assertFalse(com.example.util.FirebaseSyncManager.isReady())

        com.example.util.FirebaseSyncManager.disconnect(context)
        assertFalse(com.example.util.FirebaseSyncManager.isReady())
        assertEquals("", com.example.util.FirebaseSyncManager.getActiveProjectId())
    }

    @Test
    fun `merged cloud store config preserves cloud store name and local googleServicesJson`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localCfg = StoreConfig(
            id = 1,
            storeName = "Warung Makan",
            storeAddress = "Jl. Merdeka No. 45, Jakarta",
            googleServicesJson = "{\"project_info\":{\"project_id\":\"demo-pos\"}}"
        )
        val cloudData = mapOf<String, Any?>(
            "storeName" to "Esteh daun",
            "storeAddress" to "Jl. Melati No. 10",
            "storePhone" to "08123456789",
            "qrisMerchantName" to "Esteh daun QRIS",
            "googleServicesJson" to ""
        )

        val merged = com.example.util.FirebaseSyncManager.buildMergedStoreConfig(
            context = context,
            cloudData = cloudData,
            localCfg = localCfg
        )

        assertEquals("Esteh daun", merged.storeName)
        assertEquals("Jl. Melati No. 10", merged.storeAddress)
        assertEquals("08123456789", merged.storePhone)
        assertEquals("Esteh daun QRIS", merged.qrisMerchantName)
        assertEquals("{\"project_info\":{\"project_id\":\"demo-pos\"}}", merged.googleServicesJson)
    }

    @Test
    fun `cloud mirror replacement does not double deduct stock and generates unique offline ids`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.database.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val repo = com.example.data.repository.KasirRepository(
                categoryDao = db.categoryDao(),
                productDao = db.productDao(),
                transactionDao = db.transactionDao(),
                storeConfigDao = db.storeConfigDao()
            )

            val id1 = repo.nextUniqueCloudId()
            val id2 = repo.nextUniqueCloudId()
            assertTrue(id2 > id1)

            val product = com.example.data.model.Product(
                id = 10L,
                name = "Es Teh Daun",
                categoryId = 2L,
                categoryName = "Minuman",
                price = 5000.0,
                stock = 20
            )
            repo.replaceProductsFromCloud(listOf(product))

            val tx = Transaction(
                id = 100L,
                invoiceNumber = "INV-CLOUD-100",
                totalAmount = 15000.0,
                totalItemsCount = 3
            )
            val txItem = TransactionItem(
                transactionId = 100L,
                productId = 10L,
                productName = "Es Teh Daun",
                quantity = 3,
                pricePerUnit = 5000.0,
                subtotal = 15000.0
            )
            repo.replaceTransactionsFromCloud(listOf(tx to listOf(txItem)))

            // Stock must remain 20 (already reflected in Cloud product doc, not deducted again)
            assertEquals(20, repo.getProductById(10L)?.stock)
            assertEquals("INV-CLOUD-100", repo.getTransactionById(100L)?.transaction?.invoiceNumber)

            // Disconnected FirebaseSyncManager always reports NOT_CONFIGURED
            com.example.util.FirebaseSyncManager.disconnect(context)
            assertEquals(
                com.example.util.FirebaseSyncManager.CloudConnectionMode.NOT_CONFIGURED,
                com.example.util.FirebaseSyncManager.cloudSyncState.value.mode
            )
        } finally {
            db.close()
        }
    }
}
