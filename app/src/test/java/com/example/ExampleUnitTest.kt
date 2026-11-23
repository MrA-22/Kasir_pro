package com.example

import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionWithItems
import com.example.util.AtomicVoiceCommand
import com.example.util.Code128Encoder
import com.example.util.CurrencyUtils
import com.example.util.LicenseManager
import com.example.util.ReportExporter
import com.example.util.VoiceCommandEngine
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExampleUnitTest {
    private val testProducts = listOf(
        Product(id = 1L, name = "Ayam Goreng", categoryId = 1L, categoryName = "Makanan", price = 15000.0, costPrice = 10000.0, stock = 20),
        Product(id = 2L, name = "Ayam Geprek", categoryId = 1L, categoryName = "Makanan", price = 18000.0, costPrice = 12000.0, stock = 25),
        Product(id = 3L, name = "Es Teh", categoryId = 2L, categoryName = "Minuman", price = 4000.0, costPrice = 2000.0, stock = 50)
    )

    private val testCategories = listOf(
        Category(id = 1L, name = "Makanan"),
        Category(id = 2L, name = "Minuman")
    )

    @Test
    fun currencyUtils_formatRupiah_doesNotDuplicateRp() {
        val formatted = CurrencyUtils.formatRupiah(20000.0)
        assertEquals("Rp 20.000", formatted)
        assertFalse(formatted.contains("Rp Rp"))
        assertFalse(formatted.contains("Rp. Rp"))
    }

    @Test
    fun voiceCommandEngine_parsesMultipleVerbPriceCommands() {
        val input = "Ubah harga ayam goreng 20rb ubah modal ayam geprek 5rb"
        val commands = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = input,
            currentScreen = "KASIR",
            products = testProducts,
            categories = testCategories
        )

        assertEquals(2, commands.size)
        assertTrue(commands[0] is AtomicVoiceCommand.UpdatePrice)
        assertTrue(commands[1] is AtomicVoiceCommand.UpdatePrice)

        val cmd1 = commands[0] as AtomicVoiceCommand.UpdatePrice
        assertEquals("Ayam Goreng", cmd1.product.name)
        assertEquals(20000.0, cmd1.sellingPrice ?: 0.0, 0.01)

        val cmd2 = commands[1] as AtomicVoiceCommand.UpdatePrice
        assertEquals("Ayam Geprek", cmd2.product.name)
        assertEquals(5000.0, cmd2.costPrice ?: 0.0, 0.01)
    }

    @Test
    fun voiceCommandEngine_parsesMultipleProductsInOneSentence() {
        val input = "ubah harga ayam geprek 10rb ayam goreng 25rb"
        val commands = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = input,
            currentScreen = "MENU",
            products = testProducts,
            categories = testCategories
        )

        assertEquals(2, commands.size)
        assertTrue(commands[0] is AtomicVoiceCommand.UpdatePrice)
        assertTrue(commands[1] is AtomicVoiceCommand.UpdatePrice)

        val cmd1 = commands[0] as AtomicVoiceCommand.UpdatePrice
        assertEquals("Ayam Geprek", cmd1.product.name)
        assertEquals(10000.0, cmd1.sellingPrice ?: 0.0, 0.01)

        val cmd2 = commands[1] as AtomicVoiceCommand.UpdatePrice
        assertEquals("Ayam Goreng", cmd2.product.name)
        assertEquals(25000.0, cmd2.sellingPrice ?: 0.0, 0.01)
    }

    @Test
    fun voiceCommandEngine_parsesMultipleCartAdditions() {
        val input = "tambah ayam geprek 5 ayam goreng 5 es teh 5"
        val commands = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = input,
            currentScreen = "KASIR",
            products = testProducts,
            categories = testCategories
        )

        assertEquals(3, commands.size)
        assertTrue(commands.all { it is AtomicVoiceCommand.AddToCart })

        val c1 = commands[0] as AtomicVoiceCommand.AddToCart
        assertEquals("Ayam Geprek", c1.product.name)
        assertEquals(5, c1.quantity)

        val c2 = commands[1] as AtomicVoiceCommand.AddToCart
        assertEquals("Ayam Goreng", c2.product.name)
        assertEquals(5, c2.quantity)

        val c3 = commands[2] as AtomicVoiceCommand.AddToCart
        assertEquals("Es Teh", c3.product.name)
        assertEquals(5, c3.quantity)
    }

    @Test
    fun voiceCommandEngine_parsesCashPayment() {
        val input = "bayar cash 50rb"
        val commands = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = input,
            currentScreen = "KASIR",
            products = testProducts,
            categories = testCategories
        )

        assertEquals(1, commands.size)
        assertTrue(commands[0] is AtomicVoiceCommand.ProcessPayment)

        val pay = commands[0] as AtomicVoiceCommand.ProcessPayment
        assertEquals("TUNAI", pay.method)
        assertEquals(50000.0, pay.amount ?: 0.0, 0.01)
    }

    @Test
    fun voiceCommandEngine_parsesAddMenuProduct() {
        val input = "tambah menu es jeruk kategori minuman harga 7rb"
        val commands = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = input,
            currentScreen = "MENU",
            products = testProducts,
            categories = testCategories
        )

        assertEquals(1, commands.size)
        assertTrue(commands[0] is AtomicVoiceCommand.AddMenuProduct)

        val add = commands[0] as AtomicVoiceCommand.AddMenuProduct
        assertEquals("Es Jeruk", add.name)
        assertEquals("Minuman", add.categoryName)
        assertEquals(7000.0, add.price, 0.01)
    }

    @Test
    fun voiceCommandEngine_parsesExcelAndCsvExport() {
        val excelCmds = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = "ekspor laporan excel",
            currentScreen = "LAPORAN",
            products = testProducts,
            categories = testCategories
        )
        assertEquals(1, excelCmds.size)
        assertTrue(excelCmds[0] is AtomicVoiceCommand.ExportReport)
        assertEquals("EXCEL", (excelCmds[0] as AtomicVoiceCommand.ExportReport).format)

        val csvCmds = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = "ekspor laporan csv",
            currentScreen = "LAPORAN",
            products = testProducts,
            categories = testCategories
        )
        assertEquals(1, csvCmds.size)
        assertTrue(csvCmds[0] is AtomicVoiceCommand.ExportReport)
        assertEquals("CSV", (csvCmds[0] as AtomicVoiceCommand.ExportReport).format)
    }

    @Test
    fun reportExporter_writeExcelWorkbook_createsValidMultiSheetXlsx() {
        val storeConfig = StoreConfig(
            storeName = "Warung Ikan & Sambal <Maju>",
            storeAddress = "Jalan Kwek Kwek Kwek",
            storePhone = "0812-3456-7890"
        )
        val tx = Transaction(
            id = 10L,
            invoiceNumber = "INV-20261121-0001",
            totalAmount = 90000.0,
            paymentMethod = "TUNAI",
            amountReceived = 100000.0,
            changeAmount = 10000.0,
            customerName = "Budi & Ani",
            note = "Pedas <sedang>",
            totalItemsCount = 6
        )
        val items = listOf(
            TransactionItem(
                id = 1L,
                transactionId = 10L,
                productId = 1L,
                productName = "Ayam Goreng",
                categoryName = "Makanan",
                quantity = 3,
                pricePerUnit = 15000.0,
                subtotal = 45000.0
            ),
            TransactionItem(
                id = 2L,
                transactionId = 10L,
                productId = 2L,
                productName = "Ayam Geprek",
                categoryName = "Makanan",
                quantity = 3,
                pricePerUnit = 15000.0,
                subtotal = 45000.0
            )
        )

        val baos = ByteArrayOutputStream()
        ReportExporter.writeExcelWorkbook(
            outputStream = baos,
            reportTitle = "Harian (21 Nov 2026)",
            storeConfig = storeConfig,
            transactions = listOf(TransactionWithItems(tx, items)),
            products = testProducts
        )

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet3.xml"))

        val sheet1 = entries["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet1.contains("WARUNG IKAN &amp; SAMBAL &lt;MAJU&gt;"))
        assertTrue(sheet1.contains("Budi &amp; Ani"))
        assertTrue(sheet1.contains("<f>SUM("))
        assertTrue(sheet1.contains("<mergeCells"))
        assertTrue(sheet1.contains("<autoFilter"))
        assertTrue(sheet1.contains("<pane "))
        assertTrue(sheet1.contains("orientation=\"landscape\""))
        assertTrue(entries["xl/styles.xml"]!!.contains("wrapText=\"1\""))

        val sheet2 = entries["xl/worksheets/sheet2.xml"]!!
        assertTrue(sheet2.contains("Ayam Goreng"))
        assertTrue(sheet2.contains("Ayam Geprek"))
        assertTrue(sheet2.contains("<mergeCells"))

        val sheet3 = entries["xl/worksheets/sheet3.xml"]!!
        assertTrue(sheet3.contains("PRODUK TERLARIS"))
        assertTrue(sheet3.contains("<mergeCells"))
    }

    @Test
    fun reportExporter_buildCsvString_usesCleanSemicolonColumnsWithoutDecimalDotZero() {
        val storeConfig = StoreConfig(storeName = "Warung Enak")
        val tx = Transaction(
            id = 5L,
            invoiceNumber = "INV-20261121-040228",
            totalAmount = 155000.0,
            paymentMethod = "TUNAI",
            amountReceived = 200000.0,
            changeAmount = 45000.0,
            customerName = "Budi",
            note = "Tanpa sambal; bungkus rapi\ncepat",
            totalItemsCount = 2
        )
        val items = listOf(
            TransactionItem(
                id = 1L,
                transactionId = 5L,
                productId = 1L,
                productName = "Ayam Goreng",
                categoryName = "Makanan",
                quantity = 2,
                pricePerUnit = 77500.0,
                subtotal = 155000.0
            )
        )

        val csv = ReportExporter.buildCsvString(
            reportTitle = "Harian",
            storeConfig = storeConfig,
            transactions = listOf(TransactionWithItems(tx, items)),
            products = testProducts
        )

        assertTrue(csv.startsWith("\uFEFF"))
        assertFalse(csv.contains("155000.0"))
        assertFalse(csv.contains("200000.0"))
        assertTrue(csv.contains("155000;200000;45000"))

        val txRow = csv.lines().firstOrNull { it.contains("INV-20261121-040228") }
        assertNotNull(txRow)
        // 10 columns => 9 semicolons (no raw embedded semicolons or newlines)
        assertEquals(9, txRow!!.count { it == ';' })
    }

    @Test
    fun code128Encoder_hasValidIsoPatternsAndEncodesInvoice() {
        assertEquals(107, Code128Encoder.PATTERNS.size)
        val seen = HashSet<String>()
        for (i in 0..105) {
            val pat = Code128Encoder.PATTERNS[i]
            assertEquals("Pattern $i length", 6, pat.size)
            assertEquals("Pattern $i sum", 11, pat.sum())
            val barSum = pat[0] + pat[2] + pat[4]
            assertEquals("Pattern $i even bar parity", 0, barSum % 2)
            assertTrue("Unique pattern $i", seen.add(pat.joinToString("")))
        }
        assertArrayEquals(intArrayOf(2, 3, 3, 1, 1, 1, 2), Code128Encoder.PATTERNS[106])

        val invoice = "INV-20261121-040228"
        val widths = Code128Encoder.encodeToBarWidths(invoice)
        // 19 chars + Start-B + Checksum = 21 symbols * 6 + 7 stop = 133 elements, 244 modules
        assertEquals(133, widths.size)
        assertEquals(244, widths.sum())
        assertEquals("INV-20261121-040228", Code128Encoder.normalizeScannedInvoice(" No. Nota : inv-20261121-040228 "))
    }

    @Test
    fun licenseManager_generatesAndVerifiesDeviceBoundKey() {
        val deviceA = "KSR-1A2B-3C4D"
        val deviceB = "KSR-9999-8888"

        val keyA = LicenseManager.generateLicenseKeyForDevice(deviceA)
        assertTrue(keyA.startsWith("KPRO-"))
        assertEquals(19, keyA.length)

        // Exact match
        assertTrue(LicenseManager.verifyLicenseKey(deviceA, keyA))
        // Lowercase and without dashes should also verify
        assertTrue(LicenseManager.verifyLicenseKey(deviceA, keyA.lowercase().replace("-", "")))
        // Wrong device must reject
        assertFalse(LicenseManager.verifyLicenseKey(deviceB, keyA))
        // Wrong key must reject
        assertFalse(LicenseManager.verifyLicenseKey(deviceA, "KPRO-0000-0000-0000"))
    }

    @Test
    fun licenseManager_formatsCountdownCorrectly() {
        assertEquals("05:00", LicenseManager.formatCountdown(300))
        assertEquals("00:59", LicenseManager.formatCountdown(59))
        assertEquals("00:00", LicenseManager.formatCountdown(0))
        assertEquals("00:00", LicenseManager.formatCountdown(-5))
    }

    @Test
    fun licenseManager_extractsDeviceIdAndLicenseKeyFromFullWhatsAppChat() {
        val buyerWaChat = """
            Halo Admin, saya ingin membeli lisensi permanen aplikasi Kasir Pro.
            Nama Usaha: Warung Maju
            ID Perangkat: KSR-B250-B0b5

            Mohon info pembayaran & Kode API Aktivasi. Terima kasih.
        """.trimIndent()

        val extractedId = LicenseManager.extractDeviceIdFromText(buyerWaChat)
        assertEquals("KSR-B250-B0B5", extractedId)
        assertEquals("KSR-B250-B0B5", LicenseManager.extractDeviceIdFromText("ksr b250 b0b5"))
        assertNull(LicenseManager.extractDeviceIdFromText("Halo admin mau tanya harga"))

        val expectedKey = LicenseManager.generateLicenseKeyForDevice(extractedId!!)
        val adminReply = LicenseManager.buildAdminReplyToBuyerMessage(extractedId, expectedKey)
        assertEquals(expectedKey, LicenseManager.extractLicenseKeyFromText(adminReply))
        assertTrue(LicenseManager.verifyLicenseKey(extractedId, expectedKey))
    }

    @Test
    fun licenseManager_buyerWaMessageNeverLeaksKeyWhileAdminAutoMessageIncludesValidKey() {
        val deviceId = "KSR-B250-B0B5"
        val storeName = "Toko Berkah"

        val buyerMsg = LicenseManager.buildWhatsAppPurchaseMessage(deviceId, storeName)
        assertTrue(buyerMsg.contains(deviceId))
        assertFalse(buyerMsg.contains("KPRO-"))
        assertNull(LicenseManager.extractLicenseKeyFromText(buyerMsg))

        val adminAutoMsg = LicenseManager.buildAdminAutoMessageWithKey(deviceId, storeName)
        assertTrue(adminAutoMsg.contains(deviceId))
        assertTrue(adminAutoMsg.contains(storeName))
        val autoGeneratedKey = LicenseManager.extractLicenseKeyFromText(adminAutoMsg)
        assertNotNull(autoGeneratedKey)
        assertTrue(LicenseManager.verifyLicenseKey(deviceId, autoGeneratedKey!!))
    }

    @Test
    fun licenseManager_normalizesHttpsGatewayUrlsAndRejectsHttp() {
        assertEquals(
            "https://webhook.mrapro.site",
            LicenseManager.normalizeHttpsGatewayUrl("webhook.mrapro.site")
        )
        assertEquals(
            "https://webhook.mrapro.site/api/license",
            LicenseManager.normalizeHttpsGatewayUrl(" https://webhook.mrapro.site/api/license ")
        )
        assertNull(LicenseManager.normalizeHttpsGatewayUrl("http://webhook.mrapro.site"))
        assertNull(LicenseManager.normalizeHttpsGatewayUrl(""))

        val base = LicenseManager.normalizeHttpsGatewayUrl("https://webhook.mrapro.site/")!!
        assertEquals("https://webhook.mrapro.site/api/license/session", LicenseManager.buildSessionEndpoint(base))
        assertEquals("https://webhook.mrapro.site/api/license/request", LicenseManager.buildRequestEndpoint(base))
        assertEquals("https://webhook.mrapro.site/api/license/verify", LicenseManager.buildVerifyEndpoint(base))
        assertEquals("https://webhook.mrapro.site/api/license/status", LicenseManager.buildStatusEndpoint(base))
    }
}
