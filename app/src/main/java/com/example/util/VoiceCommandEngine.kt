package com.example.util

import com.example.data.model.Category
import com.example.data.model.Product
import java.util.Calendar

/**
 * Atomic Voice Command Representations
 * Enables sequentially executing multi-command utterances parsed by the pattern-matching engine.
 */
sealed class AtomicVoiceCommand(val rawChunk: String, val verbKeyword: String) {
    data class UpdatePrice(
        val raw: String,
        val product: Product,
        val sellingPrice: Double?,
        val costPrice: Double?
    ) : AtomicVoiceCommand(raw, "ubah_harga")

    data class AddToCart(
        val raw: String,
        val product: Product,
        val quantity: Int
    ) : AtomicVoiceCommand(raw, "tambah_keranjang")

    data class ReduceFromCart(
        val raw: String,
        val product: Product,
        val quantity: Int,
        val isExplicitRemove: Boolean = false
    ) : AtomicVoiceCommand(raw, "kurang_keranjang")

    data class UpdateStock(
        val raw: String,
        val product: Product,
        val newStock: Int,
        val isRelative: Boolean
    ) : AtomicVoiceCommand(raw, "ubah_stok")

    data class ClearAllStocks(
        val raw: String
    ) : AtomicVoiceCommand(raw, "reset_stok")

    data class RenameProduct(
        val raw: String,
        val product: Product,
        val newName: String
    ) : AtomicVoiceCommand(raw, "ubah_nama")

    data class ChangeCategory(
        val raw: String,
        val product: Product,
        val targetCategoryName: String
    ) : AtomicVoiceCommand(raw, "ubah_kategori")

    data class AddMenuProduct(
        val raw: String,
        val name: String,
        val categoryName: String,
        val price: Double,
        val costPrice: Double?,
        val stock: Int
    ) : AtomicVoiceCommand(raw, "tambah_menu")

    data class DeleteProduct(
        val raw: String,
        val product: Product
    ) : AtomicVoiceCommand(raw, "hapus_menu")

    data class AddCategory(
        val raw: String,
        val categoryName: String
    ) : AtomicVoiceCommand(raw, "tambah_kategori")

    data class DeleteCategory(
        val raw: String,
        val categoryName: String
    ) : AtomicVoiceCommand(raw, "hapus_kategori")

    data class DeleteTransaction(
        val raw: String,
        val nominal: Double?,
        val isLast: Boolean
    ) : AtomicVoiceCommand(raw, "hapus_transaksi")

    data class ProcessPayment(
        val raw: String,
        val method: String, // "TUNAI" or "QRIS"
        val amount: Double?
    ) : AtomicVoiceCommand(raw, "bayar")

    data class Navigate(
        val raw: String,
        val targetScreen: String,
        val dateCalendar: Calendar? = null
    ) : AtomicVoiceCommand(raw, "navigasi")

    data class UpdateStoreConfig(
        val raw: String,
        val field: String, // "NAME", "ADDRESS", "PHONE", "FOOTER"
        val value: String
    ) : AtomicVoiceCommand(raw, "ubah_toko")

    data class ChangeTheme(
        val raw: String,
        val themeMode: String // "DARK", "SAGE", "LIGHT", "CYCLE"
    ) : AtomicVoiceCommand(raw, "ubah_tema")

    data class ExportReport(
        val raw: String,
        val format: String // "PDF", "CSV"
    ) : AtomicVoiceCommand(raw, "ekspor_laporan")

    data class ClearCart(
        val raw: String
    ) : AtomicVoiceCommand(raw, "kosongkan_keranjang")

    data class MasterReset(
        val raw: String
    ) : AtomicVoiceCommand(raw, "master_reset")
}

/**
 * Pattern-Matching Engine for parsing complex, multi-command voice strings
 * into an array of atomic command objects.
 */
object VoiceCommandEngine {

    // Verb keyword groups for tokenizing complex compound commands
    private val VERB_TRIGGERS = listOf(
        "tambah menu", "tambahkan menu", "buat menu",
        "tambah kategori", "buat kategori",
        "hapus kategori", "buang kategori",
        "tambah stok", "tambahkan stok", "ubah stok", "ganti stok", "set stok", "isi stok", "reset stok", "hapus stok",
        "ubah harga jual", "ganti harga jual", "ubah harga modal", "ganti harga modal", "ubah harga", "ganti harga", "set harga", "ubah modal", "ganti modal",
        "ubah kategori", "ganti kategori", "pindah kategori",
        "ubah nama", "ganti nama", "edit nama", "set nama",
        "hapus transaksi", "batal transaksi",
        "hapus menu", "hapus produk", "buang menu",
        "kosongkan keranjang", "hapus keranjang", "reset keranjang",
        "kurangi", "kurangkan", "kurang", "potong",
        "tambahkan", "tambah", "pesan", "beli", "masukkan",
        "bayar cash", "bayar tunai", "bayar qris", "bayar kris", "bayar", "checkout",
        "buka laporan", "lihat laporan", "buka kasir", "buka beranda", "buka menu", "buka pengaturan", "buka hitung",
        "mode sage", "mode gelap", "mode terang", "ubah tema", "ganti tema"
    )

    fun splitIntoAtomicChunks(rawInput: String, products: List<Product>): List<String> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return emptyList()

        // Step 1: Normalize conjunctions into standard delimiter " | "
        var normalized = trimmed
            .replace(Regex("""\b(?:dan\s+juga|dan|lalu|kemudian|terus|serta)\b""", RegexOption.IGNORE_CASE), " | ")
            .replace(",", " | ")
            .replace(";", " | ")

        // Step 2: Insert delimiter before secondary verb triggers
        val verbBoundaryRegex = Regex("""(?<=\S)\s+(?=\b(?:tambah|tambahkan|kurangi|kurangkan|ubah|ganti|set|hapus|buang|pesan|beli|bayar|buka|ekspor)\b)""", RegexOption.IGNORE_CASE)
        normalized = normalized.replace(verbBoundaryRegex, " | ")

        val initialChunks = normalized.split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Step 3: Inspect each chunk for implicit multiple product operations
        // (e.g., "ubah harga ayam geprek 10rb ayam goreng 25rb" or "tambah ayam geprek 5 ayam goreng 10")
        val expandedChunks = mutableListOf<String>()

        for (chunk in initialChunks) {
            val lowerChunk = chunk.lowercase()
            val isMultiPrice = (lowerChunk.contains("harga") || lowerChunk.contains("modal")) &&
                    !lowerChunk.contains("tambah menu") && !lowerChunk.contains("buat menu")

            val isMultiStock = lowerChunk.contains("stok") && !lowerChunk.contains("hapus") && !lowerChunk.contains("kosongkan") && !lowerChunk.contains("reset")

            val isMultiCart = (lowerChunk.startsWith("tambah") || lowerChunk.startsWith("pesan") || lowerChunk.startsWith("beli")) &&
                    !lowerChunk.contains("menu") && !lowerChunk.contains("kategori") && !lowerChunk.contains("stok")

            if (isMultiPrice) {
                val matchedProds = findDistinctProductsInText(lowerChunk, products)
                if (matchedProds.size > 1) {
                    // Split into discrete atomic price chunks per product
                    val baseVerb = if (lowerChunk.contains("modal") && !lowerChunk.contains("harga")) "ubah modal" else "ubah harga"
                    for (i in matchedProds.indices) {
                        val (start, end, prod) = matchedProds[i]
                        val nextStart = if (i + 1 < matchedProds.size) matchedProds[i + 1].first else lowerChunk.length
                        val segment = lowerChunk.substring(end, nextStart).trim()
                        expandedChunks.add("$baseVerb ${prod.name} $segment")
                    }
                    continue
                }
            } else if (isMultiStock) {
                val matchedProds = findDistinctProductsInText(lowerChunk, products)
                if (matchedProds.size > 1) {
                    for (i in matchedProds.indices) {
                        val (start, end, prod) = matchedProds[i]
                        val nextStart = if (i + 1 < matchedProds.size) matchedProds[i + 1].first else lowerChunk.length
                        val segment = lowerChunk.substring(end, nextStart).trim()
                        val num = extractDigits(segment).firstOrNull() ?: 0
                        expandedChunks.add("ubah stok ${prod.name} $num")
                    }
                    continue
                }
            } else if (isMultiCart) {
                val matchedProds = findDistinctProductsInText(lowerChunk, products)
                if (matchedProds.size > 1) {
                    for (i in matchedProds.indices) {
                        val (start, end, prod) = matchedProds[i]
                        val nextStart = if (i + 1 < matchedProds.size) matchedProds[i + 1].first else lowerChunk.length
                        val segment = lowerChunk.substring(end, nextStart).trim()
                        val qty = extractDigits(segment).firstOrNull() ?: 1
                        expandedChunks.add("tambah ${prod.name} $qty")
                    }
                    continue
                }
            }

            expandedChunks.add(chunk)
        }

        return expandedChunks
    }

    /**
     * Parses a raw voice input into an array of atomic command objects
     */
    fun parseToAtomicCommands(
        rawInput: String,
        currentScreen: String,
        products: List<Product>,
        categories: List<Category>
    ): List<AtomicVoiceCommand> {
        val chunks = splitIntoAtomicChunks(rawInput, products)
        val result = mutableListOf<AtomicVoiceCommand>()

        for (chunk in chunks) {
            val cmd = chunk.lowercase().trim()

            // 0. Master App Reset
            if (cmd.contains("hapus semua isi aplikasi") || cmd.contains("reset semua aplikasi") || cmd.contains("reset semua data") || cmd.contains("hapus seluruh data") || cmd.contains("reset pabrik") || (cmd.contains("hapus semua") && cmd.contains("aplikasi"))) {
                result.add(AtomicVoiceCommand.MasterReset(chunk))
                continue
            }

            // 1. Stock Operations
            if (cmd.contains("stok")) {
                if (cmd.contains("hapus semua") || cmd.contains("kosongkan semua") || cmd.contains("reset semua") || cmd.contains("reset stok")) {
                    result.add(AtomicVoiceCommand.ClearAllStocks(chunk))
                    continue
                }
                val digits = extractDigits(cmd)
                val num = digits.lastOrNull()
                if (num != null) {
                    val prod = findProductInText(cmd, products)
                    if (prod != null) {
                        val isRelative = cmd.contains("tambah") && !cmd.contains("ubah") && !cmd.contains("ganti") && !cmd.contains("set") && !cmd.contains("menjadi")
                        result.add(AtomicVoiceCommand.UpdateStock(chunk, prod, num, isRelative))
                        continue
                    }
                }
            }

            // 2. Add / Delete Category
            if ((cmd.contains("tambah kategori") || cmd.contains("buat kategori")) && !cmd.contains("harga") && !cmd.contains("rp")) {
                val catName = cmd.replace("tambah kategori", "").replace("buat kategori", "").replace("kategori baru", "").trim()
                if (catName.isNotBlank()) {
                    result.add(AtomicVoiceCommand.AddCategory(chunk, catName.replaceFirstChar { it.uppercase() }))
                    continue
                }
            }
            if ((cmd.contains("hapus kategori") || cmd.contains("buang kategori")) && !cmd.contains("menu")) {
                val catName = cmd.replace("hapus kategori", "").replace("buang kategori", "").replace("kategori", "").trim()
                if (catName.isNotBlank()) {
                    result.add(AtomicVoiceCommand.DeleteCategory(chunk, catName))
                    continue
                }
            }

            // 3. Delete Transaction
            if (cmd.contains("hapus transaksi") || cmd.contains("batal transaksi")) {
                val (nominal, _) = extractIndonesianPrice(cmd)
                val isLast = cmd.contains("terakhir") || cmd.contains("terbaru")
                result.add(AtomicVoiceCommand.DeleteTransaction(chunk, if (nominal >= 500) nominal else null, isLast))
                continue
            }

            // 4. Store Config Settings
            if (cmd.contains("nama toko") || cmd.contains("alamat toko") || cmd.contains("nomor hp") || cmd.contains("telepon toko") || cmd.contains("catatan struk") || cmd.contains("footer struk")) {
                when {
                    cmd.contains("nama toko") -> {
                        val clean = chunk.replace(Regex("""(?i)(?:ubah|ganti|set|edit)?\s*nama\s+toko\s*(?:menjadi|jadi|ke)?\s*"""), "").trim()
                        result.add(AtomicVoiceCommand.UpdateStoreConfig(chunk, "NAME", clean))
                        continue
                    }
                    cmd.contains("alamat toko") -> {
                        val clean = chunk.replace(Regex("""(?i)(?:ubah|ganti|set|edit)?\s*alamat\s+toko\s*(?:menjadi|jadi|ke)?\s*"""), "").trim()
                        result.add(AtomicVoiceCommand.UpdateStoreConfig(chunk, "ADDRESS", clean))
                        continue
                    }
                    cmd.contains("nomor hp") || cmd.contains("telepon toko") -> {
                        val clean = chunk.replace(Regex("""(?i)(?:ubah|ganti|set|edit)?\s*(?:nomor\s+hp|telepon\s+toko)\s*(?:menjadi|jadi|ke)?\s*"""), "").trim()
                        result.add(AtomicVoiceCommand.UpdateStoreConfig(chunk, "PHONE", clean))
                        continue
                    }
                    else -> {
                        val clean = chunk.replace(Regex("""(?i)(?:ubah|ganti|set|edit)?\s*(?:catatan|footer)\s*(?:struk|nota)\s*(?:menjadi|jadi|ke)?\s*"""), "").trim()
                        result.add(AtomicVoiceCommand.UpdateStoreConfig(chunk, "FOOTER", clean))
                        continue
                    }
                }
            }

            // 5. Change Theme
            if (cmd.contains("tema") || cmd.contains("mode gelap") || cmd.contains("mode terang") || cmd.contains("mode sage") || cmd.contains("dark mode") || cmd.contains("light mode")) {
                val mode = when {
                    cmd.contains("sage") || cmd.contains("hijau") -> "SAGE"
                    cmd.contains("gelap") || cmd.contains("dark") -> "DARK"
                    cmd.contains("terang") || cmd.contains("light") -> "LIGHT"
                    else -> "CYCLE"
                }
                result.add(AtomicVoiceCommand.ChangeTheme(chunk, mode))
                continue
            }

            // 6. Navigation
            if (cmd.contains("laporan") && (cmd.contains("tanggal") || cmd.contains("tgl") || cmd.contains("bulan") || cmd.contains("kemarin") || cmd.contains("hari ini"))) {
                val cal = extractIndonesianDate(cmd)
                result.add(AtomicVoiceCommand.Navigate(chunk, "LAPORAN", cal))
                continue
            }
            if (cmd.contains("buka laporan") || cmd.contains("lihat laporan") || cmd == "laporan") {
                result.add(AtomicVoiceCommand.Navigate(chunk, "LAPORAN"))
                continue
            }
            if (cmd.contains("buka kasir") || cmd.contains("layar kasir") || cmd == "kasir") {
                result.add(AtomicVoiceCommand.Navigate(chunk, "KASIR"))
                continue
            }
            if (cmd.contains("buka menu") || cmd.contains("kelola menu") || cmd == "menu") {
                result.add(AtomicVoiceCommand.Navigate(chunk, "MENU"))
                continue
            }
            if (cmd.contains("buka beranda") || cmd.contains("dashboard") || cmd == "beranda") {
                result.add(AtomicVoiceCommand.Navigate(chunk, "DASHBOARD"))
                continue
            }
            if (cmd.contains("buka pengaturan") || cmd.contains("setting") || cmd == "pengaturan") {
                result.add(AtomicVoiceCommand.Navigate(chunk, "SETTINGS"))
                continue
            }
            if (cmd.contains("buka hitung") || cmd.contains("kalkulator") || cmd == "hitung") {
                result.add(AtomicVoiceCommand.Navigate(chunk, "HITUNG"))
                continue
            }

            // 7. Export Report
            if (cmd.contains("pdf") || (cmd.contains("cetak") && cmd.contains("laporan"))) {
                result.add(AtomicVoiceCommand.ExportReport(chunk, "PDF"))
                continue
            }
            if (cmd.contains("excel") || cmd.contains("xlsx") || cmd.contains("xls")) {
                result.add(AtomicVoiceCommand.ExportReport(chunk, "EXCEL"))
                continue
            }
            if (cmd.contains("csv")) {
                result.add(AtomicVoiceCommand.ExportReport(chunk, "CSV"))
                continue
            }

            // 8. Clear Cart
            if (cmd.contains("kosongkan keranjang") || cmd.contains("hapus keranjang") || cmd.contains("reset keranjang") || cmd.contains("bersihkan keranjang")) {
                result.add(AtomicVoiceCommand.ClearCart(chunk))
                continue
            }

            // 9. Payment / Checkout
            if (cmd.contains("bayar") || cmd.contains("checkout") || cmd.contains("cash") || cmd.contains("tunai") || cmd.contains("qris") || cmd.contains("kris")) {
                val method = if (cmd.contains("qris") || cmd.contains("kris")) "QRIS" else "TUNAI"
                val (amt, _) = extractIndonesianPrice(cmd)
                val finalAmt = if (cmd.contains("pas")) null else if (amt >= 500) amt else null
                result.add(AtomicVoiceCommand.ProcessPayment(chunk, method, finalAmt))
                continue
            }

            // 10. Rename Product (e.g. "ubah ayam geprek jadi ayam gepuk")
            val isRename = (cmd.contains("ubah nama") || cmd.contains("ganti nama") ||
                    ((cmd.contains("ubah") || cmd.contains("ganti")) && (cmd.contains(" jadi ") || cmd.contains(" menjadi ") || cmd.contains(" ke ")))) &&
                    !cmd.contains("harga") && !cmd.contains("modal") && !cmd.contains("kategori") && !cmd.contains("stok")

            if (isRename) {
                val delimiter = when {
                    cmd.contains(" menjadi ") -> " menjadi "
                    cmd.contains(" jadi ") -> " jadi "
                    cmd.contains(" ke ") -> " ke "
                    else -> ""
                }
                if (delimiter.isNotBlank()) {
                    val parts = chunk.split(Regex("(?i)$delimiter"), limit = 2)
                    if (parts.size == 2) {
                        val leftPart = parts[0].lowercase().trim()
                        val newName = parts[1].trim().replaceFirstChar { it.uppercase() }
                        val prod = findProductInText(leftPart, products)
                        if (prod != null && newName.isNotBlank()) {
                            result.add(AtomicVoiceCommand.RenameProduct(chunk, prod, newName))
                            continue
                        }
                    }
                }
            }

            // 11. Change Product Category (e.g. "ubah kategori ayam geprek ke makanan")
            val isChangeCat = cmd.contains("kategori") && (cmd.contains("ubah") || cmd.contains("ganti") || cmd.contains("pindah")) &&
                    !cmd.contains("tambah") && !cmd.contains("buat") && !cmd.contains("hapus")

            if (isChangeCat) {
                val delimiter = when {
                    cmd.contains(" ke ") -> " ke "
                    cmd.contains(" menjadi ") -> " menjadi "
                    cmd.contains(" jadi ") -> " jadi "
                    else -> ""
                }
                if (delimiter.isNotBlank()) {
                    val parts = chunk.split(Regex("(?i)$delimiter"), limit = 2)
                    if (parts.size == 2) {
                        val leftPart = parts[0].lowercase().trim()
                        val targetCat = parts[1].trim().replaceFirstChar { it.uppercase() }
                        val prod = findProductInText(leftPart, products)
                        if (prod != null && targetCat.isNotBlank()) {
                            result.add(AtomicVoiceCommand.ChangeCategory(chunk, prod, targetCat))
                            continue
                        }
                    }
                }
            }

            // 12. Add New Menu Product (e.g. "tambah menu es jeruk kategori minuman harga 7rb")
            val isAddMenu = cmd.contains("tambah menu") || cmd.contains("buat menu") ||
                    (cmd.contains("tambah") && (cmd.contains("kategori") || cmd.contains("harga")) && currentScreen == "MENU")

            if (isAddMenu) {
                var clean = cmd.replace("tambah menu", "").replace("buat menu", "").trim()
                var targetCat = "Makanan"
                val catRegex = Regex("""(?:ke\s+kategori|kategori|kat)\s+([a-zA-Z0-9\s]+?)(?=\s+(?:harga|modal|stok|rp|ribu|rb|k|sebesar|jadi)|$)""", RegexOption.IGNORE_CASE)
                val catMatch = catRegex.find(clean)
                if (catMatch != null) {
                    targetCat = catMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
                    clean = clean.replace(catMatch.value, "").trim()
                }

                var stock = 20
                val stockMatch = Regex("""stok\s*(\d+)""", RegexOption.IGNORE_CASE).find(clean)
                if (stockMatch != null) {
                    stock = stockMatch.groupValues[1].toIntOrNull() ?: 20
                    clean = clean.replace(stockMatch.value, "").trim()
                }

                val (price, remaining) = extractIndonesianPrice(clean)
                val rawProdName = remaining.replace("tambah", "").replace("buat", "")
                    .replace(Regex("""\b(?:harga|modal|stok|rp|rupiah|ribu|rb|k|ke|kategori)\b""", RegexOption.IGNORE_CASE), "")
                    .trim()
                val prodName = rawProdName.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }

                if (prodName.isNotBlank()) {
                    result.add(AtomicVoiceCommand.AddMenuProduct(chunk, prodName, targetCat, price, null, stock))
                    continue
                }
            }

            // 13. Update Selling Price & Cost Price
            val isPrice = cmd.contains("harga") || cmd.contains("modal") || cmd.contains("ubah harga") || cmd.contains("ganti harga")
            if (isPrice && !cmd.contains("tambah menu") && !cmd.contains("buat menu")) {
                val matchedProds = findDistinctProductsInText(cmd, products)
                if (matchedProds.isNotEmpty()) {
                    for (i in matchedProds.indices) {
                        val (_, end, prod) = matchedProds[i]
                        val nextStart = if (i + 1 < matchedProds.size) matchedProds[i + 1].first else cmd.length
                        val segment = cmd.substring(end, nextStart).trim()

                        var sellingPrice: Double? = null
                        var modalPrice: Double? = null
                        var cleanSegment = segment

                        val modalRegex = Regex("""modal\s*(?:harga\s*)?(?:jadi\s*|sebesar\s*|ke\s*)?([0-9\sA-Za-z]+)""", RegexOption.IGNORE_CASE)
                        val modalMatch = modalRegex.find(cleanSegment)
                        if (modalMatch != null) {
                            val (mPrice, _) = extractIndonesianPrice(modalMatch.groupValues[1])
                            if (mPrice >= 0) modalPrice = mPrice
                            cleanSegment = cleanSegment.replace(modalMatch.value, "").trim()
                        }

                        val (extractedPrice, _) = extractIndonesianPrice(cleanSegment)
                        if (cmd.contains("modal") && !cmd.contains("harga") && modalPrice == null) {
                            if (extractedPrice >= 0) modalPrice = extractedPrice
                        } else {
                            if (extractedPrice >= 0) sellingPrice = extractedPrice
                        }

                        if (sellingPrice != null || modalPrice != null) {
                            result.add(AtomicVoiceCommand.UpdatePrice(chunk, prod, sellingPrice, modalPrice))
                        }
                    }
                    continue
                }
            }

            // 14. Cart Reduction / Removal
            val isReduceCart = cmd.contains("kurang") || cmd.contains("kurangi") || cmd.contains("potong") ||
                    (cmd.contains("hapus") && (currentScreen == "KASIR" || cmd.contains("keranjang")))

            if (isReduceCart) {
                val digits = extractDigits(cmd)
                val qty = digits.lastOrNull() ?: 1
                val isExplicitRemove = cmd.contains("hapus") || cmd.contains("buang")
                val prod = findProductInText(cmd, products)
                if (prod != null) {
                    result.add(AtomicVoiceCommand.ReduceFromCart(chunk, prod, qty, isExplicitRemove && digits.isEmpty()))
                    continue
                }
            }

            // 15. Add To Cart (e.g. "tambah ayam goreng 10", "2 es teh")
            val isAddToCart = cmd.contains("tambah") || cmd.contains("pesan") || cmd.contains("beli") || currentScreen == "KASIR"
            if (isAddToCart) {
                val matchedProds = findDistinctProductsInText(cmd, products)
                if (matchedProds.isNotEmpty()) {
                    for (i in matchedProds.indices) {
                        val (_, end, prod) = matchedProds[i]
                        val nextStart = if (i + 1 < matchedProds.size) matchedProds[i + 1].first else cmd.length
                        val segment = cmd.substring(end, nextStart).trim()
                        val qty = extractDigits(segment).firstOrNull() ?: 1
                        result.add(AtomicVoiceCommand.AddToCart(chunk, prod, qty))
                    }
                    continue
                }
            }
        }

        return result
    }

    private fun findDistinctProductsInText(text: String, products: List<Product>): List<Triple<Int, Int, Product>> {
        val rawMatches = mutableListOf<Triple<Int, Int, Product>>()
        for (prod in products) {
            val pName = prod.name.lowercase()
            var idx = text.indexOf(pName)
            while (idx >= 0) {
                rawMatches.add(Triple(idx, idx + pName.length, prod))
                idx = text.indexOf(pName, idx + pName.length)
            }
        }
        rawMatches.sortByDescending { it.second - it.first } // longer names first
        val distinct = mutableListOf<Triple<Int, Int, Product>>()
        for (match in rawMatches) {
            val overlaps = distinct.any { existing ->
                (match.first >= existing.first && match.first < existing.second) ||
                (match.second > existing.first && match.second <= existing.second)
            }
            if (!overlaps) distinct.add(match)
        }
        distinct.sortBy { it.first }
        return distinct
    }

    private fun findProductInText(text: String, products: List<Product>): Product? {
        val matches = findDistinctProductsInText(text, products)
        return matches.firstOrNull()?.third
    }

    private fun extractDigits(text: String): List<Int> {
        val digits = Regex("""(\d+)""").findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (digits.isNotEmpty()) return digits

        val wordMap = mapOf("satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5)
        for ((w, n) in wordMap) {
            if (text.contains(w)) return listOf(n)
        }
        return emptyList()
    }

    fun extractIndonesianPrice(rawInput: String): Pair<Double, String> {
        val text = rawInput.trim()

        val wordNominals = listOf(
            Pair(Regex("""seratus\s*ribu""", RegexOption.IGNORE_CASE), 100000.0),
            Pair(Regex("""dua\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 200000.0),
            Pair(Regex("""tiga\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 300000.0),
            Pair(Regex("""lima\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 500000.0),
            Pair(Regex("""lima\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 50000.0),
            Pair(Regex("""dua\s*puluh\s*lima\s*ribu""", RegexOption.IGNORE_CASE), 25000.0),
            Pair(Regex("""dua\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 20000.0),
            Pair(Regex("""lima\s*belas\s*ribu""", RegexOption.IGNORE_CASE), 15000.0),
            Pair(Regex("""sepuluh\s*ribu""", RegexOption.IGNORE_CASE), 10000.0),
            Pair(Regex("""lima\s*ribu""", RegexOption.IGNORE_CASE), 5000.0),
            Pair(Regex("""dua\s*ribu""", RegexOption.IGNORE_CASE), 2000.0),
            Pair(Regex("""seribu""", RegexOption.IGNORE_CASE), 1000.0)
        )

        for ((regex, valRupiah) in wordNominals) {
            if (regex.containsMatchIn(text)) {
                val remaining = text.replace(regex, "").trim()
                return Pair(valRupiah, remaining)
            }
        }

        val priceRegex = Regex("""(?:harga|rp\.?|rupiah)?\s*([\d\.\,]+)\s*(ribu|rb|k|juta|jt|rupiah)?""", RegexOption.IGNORE_CASE)
        val matches = priceRegex.findAll(text)

        for (match in matches) {
            val rawNumStr = match.groupValues[1].replace(".", "").replace(",", "").trim()
            val num = rawNumStr.toDoubleOrNull() ?: continue
            val suffix = match.groupValues[2].lowercase()

            var calculatedPrice = num
            if (suffix in listOf("ribu", "rb", "k")) {
                if (num < 1000) calculatedPrice = num * 1000.0
            } else if (suffix in listOf("juta", "jt")) {
                if (num < 1000000) calculatedPrice = num * 1000000.0
            } else {
                if (num in 1.0..499.0) calculatedPrice = num * 1000.0
            }

            if (calculatedPrice >= 500.0) {
                val remainingText = text.replace(match.value, "")
                    .replace(Regex("""\b(?:harga|rp\.?|rupiah|ribu|rb|k)\b""", RegexOption.IGNORE_CASE), "")
                    .trim()
                return Pair(calculatedPrice, remainingText)
            }
        }

        return Pair(10000.0, text)
    }

    private fun extractIndonesianDate(cmd: String): Calendar? {
        val now = Calendar.getInstance()
        if (cmd.contains("kemarin")) {
            now.add(Calendar.DAY_OF_YEAR, -1)
            return now
        }
        if (cmd.contains("hari ini")) {
            return now
        }

        val months = mapOf(
            "januari" to 0, "februari" to 1, "maret" to 2, "april" to 3,
            "mei" to 4, "juni" to 5, "juli" to 6, "agustus" to 7,
            "september" to 8, "oktober" to 9, "november" to 10, "desember" to 11
        )

        var foundMonth = now.get(Calendar.MONTH)
        for ((mName, mIdx) in months) {
            if (cmd.contains(mName)) {
                foundMonth = mIdx
                break
            }
        }

        val digits = Regex("""(\d+)""").findAll(cmd).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (digits.isEmpty()) return null

        val day = digits[0]
        val year = now.get(Calendar.YEAR)

        if (day in 1..31) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, foundMonth)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.set(Calendar.MINUTE, 0)
            return cal
        }
        return null
    }
}
