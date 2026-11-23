package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionWithItems
import com.example.data.repository.KasirRepository
import com.example.printer.BluetoothDeviceItem
import com.example.printer.ThermalPrinterManager
import com.example.util.AtomicVoiceCommand
import com.example.util.Code128Encoder
import com.example.util.CurrencyUtils
import com.example.util.GeminiClient
import com.example.util.LicenseManager
import com.example.util.ReceiptBitmapGenerator
import com.example.util.ReportExporter
import com.example.util.TrialStatus
import com.example.util.VoiceCommandEngine
import com.example.util.VoiceParserState
import com.example.util.VoiceToTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class CartItem(
    val product: Product,
    val quantity: Int,
    val discountValue: Double = 0.0,
    val isDiscountPercentage: Boolean = false
) {
    val subtotal: Double get() {
        val baseSubtotal = product.price * quantity
        val discountAmount = if (isDiscountPercentage) {
            baseSubtotal * (discountValue / 100.0)
        } else {
            discountValue
        }
        return (baseSubtotal - discountAmount).coerceAtLeast(0.0)
    }
}

data class DashboardSummary(
    val totalRevenueToday: Double = 0.0,
    val totalTransactionsToday: Int = 0,
    val totalItemsSoldToday: Int = 0,
    val totalTunaiToday: Double = 0.0,
    val totalQrisToday: Double = 0.0,
    val topSellingToday: List<TopProductSummary> = emptyList(),
    val recentTransactionsToday: List<TransactionWithItems> = emptyList(),
    val lowStockAlertCount: Int = 0,
    val lowStockProducts: List<Product> = emptyList()
)

data class TopProductSummary(
    val productId: Long,
    val productName: String,
    val totalQuantity: Int,
    val totalRevenue: Double,
    val unit: String = "Porsi"
)

data class WeeklyPeriodSummary(
    val weekNumber: Int,
    val dateRangeText: String,
    val totalRevenue: Double,
    val totalTransactions: Int,
    val totalItemsSold: Int
)

data class ChangePopupData(
    val totalAmount: Double,
    val amountReceived: Double,
    val changeAmount: Double,
    val invoiceNumber: String,
    val customerName: String = "Pelanggan AI"
)

enum class ReportPeriodType {
    HARIAN, MINGGUAN, BULANAN
}

class KasirViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: KasirRepository
    private val printerManager: ThermalPrinterManager = ThermalPrinterManager(application)

    // Store Config
    val storeConfig: StateFlow<StoreConfig?>
    val allCategories: StateFlow<List<Category>>
    val allProducts: StateFlow<List<Product>>
    val lowStockProducts: StateFlow<List<Product>>
    val allTransactions: StateFlow<List<TransactionWithItems>>

    // Change Popup State (10-second auto-hide)
    private val _changePopupData = MutableStateFlow<ChangePopupData?>(null)
    val changePopupData: StateFlow<ChangePopupData?> = _changePopupData.asStateFlow()

    fun dismissChangePopup() {
        _changePopupData.value = null
        finishPayment()
    }

    // Cart state
    private val _cart = MutableStateFlow<Map<Long, CartItem>>(emptyMap())
    val cart: StateFlow<Map<Long, CartItem>> = _cart.asStateFlow()

    val cartTotal: StateFlow<Double> = _cart.combine(MutableStateFlow(0)) { cartMap, _ ->
        cartMap.values.sumOf { it.subtotal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartItemCount: StateFlow<Int> = _cart.combine(MutableStateFlow(0)) { cartMap, _ ->
        cartMap.values.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // UI Toast/Event messages
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // Active Receipt Dialog state
    private val _activeReceiptTransaction = MutableStateFlow<TransactionWithItems?>(null)
    val activeReceiptTransaction: StateFlow<TransactionWithItems?> = _activeReceiptTransaction.asStateFlow()

    // Bluetooth Devices list
    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceItem>> = _pairedDevices.asStateFlow()

    // Report Period states
    val selectedReportPeriod = MutableStateFlow(ReportPeriodType.HARIAN)
    val selectedDailyTimestamp = MutableStateFlow(System.currentTimeMillis())
    val selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))

    // Calculator / Hitung Cepat state
    val quickCalcExpression = MutableStateFlow("0")
    val quickCalcNote = MutableStateFlow("")

    // AI Assistant state
    private val voiceParser = VoiceToTextParser(application)
    private val ttsEngine = com.example.util.AiTtsEngine(application)
    val voiceState: StateFlow<VoiceParserState> = voiceParser.state

    private val _isContinuousListening = MutableStateFlow(false)
    val isContinuousListening: StateFlow<Boolean> = _isContinuousListening.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _aiLastResponse = MutableStateFlow<String?>(null)
    val aiLastResponse: StateFlow<String?> = _aiLastResponse.asStateFlow()

    // Navigation triggers for AI (handled in UI)
    private val _aiNavigationTrigger = MutableSharedFlow<String>()
    val aiNavigationTrigger = _aiNavigationTrigger.asSharedFlow()

    private val _qrisPaymentTrigger = MutableSharedFlow<Boolean>()
    val qrisPaymentTrigger = _qrisPaymentTrigger.asSharedFlow()

    val showMasterResetDialog = MutableStateFlow(false)

    fun requestMasterReset() {
        showMasterResetDialog.value = true
    }

    fun dismissMasterReset() {
        showMasterResetDialog.value = false
    }

    fun confirmMasterReset() {
        showMasterResetDialog.value = false
        viewModelScope.launch {
            repository.deleteAllData()
            clearCart()
            _aiLastResponse.value = "Semua data aplikasi telah berhasil dihapus/direset total."
            emitToast("Aplikasi telah di-reset total!")
        }
    }

    private val _aiCheckoutTrigger = MutableSharedFlow<Pair<String, Double>>() // paymentMethod, amountReceived
    val aiCheckoutTrigger = _aiCheckoutTrigger.asSharedFlow()

    // License & 5-Minute Trial Status
    private val _licenseStatus = MutableStateFlow(LicenseManager.getTrialState(application))
    val licenseStatus: StateFlow<TrialStatus> = _licenseStatus.asStateFlow()

    private val _isCheckingServerActivation = MutableStateFlow(false)
    val isCheckingServerActivation: StateFlow<Boolean> = _isCheckingServerActivation.asStateFlow()

    private val _serverActivationStatusMessage = MutableStateFlow<String?>(null)
    val serverActivationStatusMessage: StateFlow<String?> = _serverActivationStatusMessage.asStateFlow()

    fun refreshLicenseStatus() {
        _licenseStatus.value = LicenseManager.getTrialState(getApplication())
        if (LicenseManager.isSilentWaGatewayConfigured()) {
            checkServerActivationStatus(silent = true)
        }
    }

    fun checkServerActivationStatus(silent: Boolean = false) {
        if (_isCheckingServerActivation.value) return
        viewModelScope.launch {
            _isCheckingServerActivation.value = true
            try {
                val result = LicenseManager.checkServerActivationStatus(getApplication())
                if (result.isActivated) {
                    _licenseStatus.value = LicenseManager.getTrialState(getApplication())
                    _serverActivationStatusMessage.value = null
                    emitToast(result.message)
                } else if (result.isRevoked) {
                    _licenseStatus.value = LicenseManager.getTrialState(getApplication())
                    _serverActivationStatusMessage.value = result.message
                    if (!silent) emitToast(result.message)
                } else {
                    if (result.reachable) {
                        _serverActivationStatusMessage.value = result.message
                    }
                    if (!silent) {
                        emitToast(result.message)
                    }
                }
            } finally {
                _isCheckingServerActivation.value = false
            }
        }
    }

    fun activateLicense(key: String): Boolean {
        val success = LicenseManager.activateWithKey(getApplication(), key)
        if (success) {
            _licenseStatus.value = LicenseManager.getTrialState(getApplication())
            emitToast("Lisensi aktif permanen! Terima kasih telah membeli Kasir Pro.")
        } else {
            emitToast("Kode API tidak valid untuk perangkat ini.")
        }
        return success
    }

    suspend fun activateLicenseAsync(key: String): LicenseManager.ActivationResult {
        val result = LicenseManager.verifyAndActivateLicense(getApplication(), key)
        if (result.success) {
            _licenseStatus.value = LicenseManager.getTrialState(getApplication())
            _serverActivationStatusMessage.value = null
        }
        emitToast(result.message)
        return result
    }

    fun updateAdminWhatsappNumber(waNumber: String) {
        LicenseManager.setAdminWhatsapp(getApplication(), waNumber)
        refreshLicenseStatus()
        emitToast("Nomor WhatsApp Admin diperbarui.")
    }

    fun resetTrialForTesting() {
        LicenseManager.resetTrialForAdminTesting(getApplication())
        refreshLicenseStatus()
        startTrialCountdownLoop()
        emitToast("Timer uji coba 5 menit berhasil direset.")
    }

    fun generateLicenseKeyForBuyer(buyerDeviceId: String): String {
        val cleanId = LicenseManager.extractDeviceIdFromText(buyerDeviceId) ?: buyerDeviceId
        return LicenseManager.generateLicenseKeyForDevice(cleanId)
    }

    private var lastSilentRequestDispatchMs: Long = 0L

    fun notifyAdminSilentPurchaseRequest(deviceId: String, storeName: String) {
        val now = System.currentTimeMillis()
        if (now - lastSilentRequestDispatchMs < 30_000L) return
        lastSilentRequestDispatchMs = now

        val adminWa = _licenseStatus.value.adminWhatsappNumber
        val pkgName = getApplication<Application>().packageName
        viewModelScope.launch(Dispatchers.IO) {
            LicenseManager.sendSilentLicenseRequestToAdminWa(
                deviceId = deviceId,
                storeName = storeName,
                adminWaNumber = adminWa,
                packageName = pkgName
            )
            com.example.util.FirebaseSyncManager.logLicenseRequest(deviceId, storeName)
        }
    }

    private val _cloudLicenseRequests = MutableStateFlow<List<com.example.util.FirebaseSyncManager.LicenseRequestRecord>>(emptyList())
    val cloudLicenseRequests: StateFlow<List<com.example.util.FirebaseSyncManager.LicenseRequestRecord>> = _cloudLicenseRequests.asStateFlow()

    fun refreshCloudLicenseRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            _cloudLicenseRequests.value = com.example.util.FirebaseSyncManager.fetchRecentLicenseRequests()
        }
    }

    fun testAdminWebhookConnection() {
        val adminWa = _licenseStatus.value.adminWhatsappNumber
        val storeName = storeConfig.value?.storeName ?: "Tes Kasir Pro"
        val pkgName = getApplication<Application>().packageName
        viewModelScope.launch {
            val result = LicenseManager.sendSilentLicenseRequestDetailed(
                deviceId = "KSR-TEST-0001",
                storeName = storeName,
                adminWaNumber = adminWa,
                packageName = pkgName
            )
            val statusText = if (result.statusCode > 0) "HTTP ${result.statusCode}" else "Error"
            if (result.success) {
                emitToast("Webhook OK ($statusText)! Token sesi & permintaan tes berhasil.")
            } else {
                emitToast("Webhook gagal ($statusText): ${result.detail}")
            }
        }
    }

    private var trialTickerJob: kotlinx.coroutines.Job? = null

    private fun startTrialCountdownLoop() {
        trialTickerJob?.cancel()
        trialTickerJob = viewModelScope.launch {
            while (true) {
                val status = LicenseManager.getTrialState(getApplication())
                _licenseStatus.value = status
                if (status.isActivated || status.isTrialExpired) {
                    break
                }
                delay(1000L)
            }
        }
    }

    init {
        val db = AppDatabase.getDatabase(application, viewModelScope)
        repository = KasirRepository(
            categoryDao = db.categoryDao(),
            productDao = db.productDao(),
            transactionDao = db.transactionDao(),
            storeConfigDao = db.storeConfigDao()
        )

        // Restore Google Services JSON from public storage if Room got cleared or reset
        viewModelScope.launch {
            try {
                val current = repository.getStoreConfigSync()
                if (current != null && current.googleServicesJson.isBlank()) {
                    val sdFile = java.io.File(getApplication<Application>().filesDir, "google-services-pos.json")
                    if (sdFile.exists()) {
                        val sdJson = sdFile.readText()
                        if (sdJson.isNotBlank()) {
                            repository.updateStoreConfig(current.copy(googleServicesJson = sdJson))
                        }
                    }
                }
            } catch (err: Exception) {
                err.printStackTrace()
            }
        }

        storeConfig = repository.storeConfig.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StoreConfig()
        )

        allCategories = repository.allCategories.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allProducts = repository.allProducts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        lowStockProducts = repository.lowStockProducts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allTransactions = repository.allTransactions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        loadPairedBluetoothDevices()
        startTrialCountdownLoop()

        voiceParser.onResultListener = { spoken ->
            submitAiCommand(spoken)
        }

        // Auto restart speech recognizer in Continuous Listening Mode when idle or after error
        viewModelScope.launch {
            voiceState.collect { state ->
                if (!state.isSpeaking && _isContinuousListening.value && !_isAiThinking.value) {
                    delay(1500)
                    if (_isContinuousListening.value && !voiceState.value.isSpeaking && !_isAiThinking.value) {
                        voiceParser.startListening()
                    }
                }
            }
        }
    }

    // Dashboard dynamic computation
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        allTransactions,
        lowStockProducts
    ) { transactions, lowStockList ->
        val startOfToday = CurrencyUtils.getStartOfDay()
        val endOfToday = CurrencyUtils.getEndOfDay()

        val todayTxList = transactions.filter {
            it.transaction.timestamp in startOfToday..endOfToday
        }

        val totalRev = todayTxList.sumOf { it.transaction.totalAmount }
        val totalTx = todayTxList.size
        val totalItems = todayTxList.sumOf { it.items.sumOf { item -> item.quantity } }
        val totalTunai = todayTxList.filter { it.transaction.paymentMethod == "TUNAI" }
            .sumOf { it.transaction.totalAmount }
        val totalQris = todayTxList.filter { it.transaction.paymentMethod == "QRIS" }
            .sumOf { it.transaction.totalAmount }

        // Top products today
        val productSalesMap = mutableMapOf<Long, Pair<String, Pair<Int, Double>>>()
        todayTxList.forEach { txWithItems ->
            txWithItems.items.forEach { item ->
                val current = productSalesMap[item.productId]
                val prevQty = current?.second?.first ?: 0
                val prevRev = current?.second?.second ?: 0.0
                productSalesMap[item.productId] = Pair(
                    item.productName,
                    Pair(prevQty + item.quantity, prevRev + item.subtotal)
                )
            }
        }

        val topProducts = productSalesMap.map { (prodId, pair) ->
            TopProductSummary(
                productId = prodId,
                productName = pair.first,
                totalQuantity = pair.second.first,
                totalRevenue = pair.second.second
            )
        }.sortedByDescending { it.totalQuantity }.take(5)

        DashboardSummary(
            totalRevenueToday = totalRev,
            totalTransactionsToday = totalTx,
            totalItemsSoldToday = totalItems,
            totalTunaiToday = totalTunai,
            totalQrisToday = totalQris,
            topSellingToday = topProducts,
            recentTransactionsToday = todayTxList.take(5),
            lowStockAlertCount = lowStockList.size,
            lowStockProducts = lowStockList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // Cart Operations
    fun addToCart(product: Product, quantity: Int = 1) {
        val currentCart = _cart.value.toMutableMap()
        val existingItem = currentCart[product.id]
        val currentQty = existingItem?.quantity ?: 0

        if (product.stock <= 0) {
            emitToast("Stok ${product.name} habis! Tambahkan stok terlebih dahulu.")
            return
        }

        val targetQty = currentQty + quantity
        if (targetQty > product.stock) {
            emitToast("Jumlah melebihi stok yang tersedia (${product.stock} ${product.unit}).")
            return
        }

        currentCart[product.id] = CartItem(product = product, quantity = targetQty)
        _cart.value = currentCart
    }

    fun decrementFromCart(product: Product) {
        val currentCart = _cart.value.toMutableMap()
        val existingItem = currentCart[product.id] ?: return

        if (existingItem.quantity > 1) {
            currentCart[product.id] = existingItem.copy(quantity = existingItem.quantity - 1)
        } else {
            currentCart.remove(product.id)
        }
        _cart.value = currentCart
    }

    fun removeProductFromCart(productId: Long) {
        val currentCart = _cart.value.toMutableMap()
        currentCart.remove(productId)
        _cart.value = currentCart
    }

    fun clearCart() {
        _cart.value = emptyMap()
    }

    fun updateItemDiscount(productId: Long, discountValue: Double, isPercentage: Boolean) {
        val currentCart = _cart.value.toMutableMap()
        val existingItem = currentCart[productId] ?: return
        currentCart[productId] = existingItem.copy(
            discountValue = discountValue,
            isDiscountPercentage = isPercentage
        )
        _cart.value = currentCart
    }

    // Checkout
    fun processCheckout(
        paymentMethod: String, // "TUNAI" or "QRIS"
        amountReceived: Double,
        customerName: String,
        note: String,
        showReceiptDialog: Boolean = true,
        onSuccess: (TransactionWithItems) -> Unit
    ) {
        val currentCart = _cart.value.values.toList()
        if (currentCart.isEmpty()) {
            emitToast("Keranjang belanja masih kosong!")
            return
        }

        val totalAmount = currentCart.sumOf { it.subtotal }
        val changeAmount = if (paymentMethod == "TUNAI") {
            if (amountReceived < totalAmount) {
                emitToast("Uang yang diterima kurang dari total belanja!")
                return
            }
            amountReceived - totalAmount
        } else {
            0.0
        }

        val invoiceNumber = CurrencyUtils.generateInvoiceNumber()
        val timestamp = System.currentTimeMillis()

        val transaction = Transaction(
            invoiceNumber = invoiceNumber,
            timestamp = timestamp,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod,
            amountReceived = if (paymentMethod == "TUNAI") amountReceived else totalAmount,
            changeAmount = changeAmount,
            customerName = customerName.trim(),
            note = note.trim(),
            totalItemsCount = currentCart.sumOf { it.quantity },
            status = "COMPLETED"
        )

        val items = currentCart.map {
            TransactionItem(
                transactionId = 0, // Assigned on insert
                productId = it.product.id,
                productName = it.product.name,
                categoryName = it.product.categoryName,
                quantity = it.quantity,
                pricePerUnit = it.product.price,
                discountValue = it.discountValue,
                isDiscountPercentage = it.isDiscountPercentage,
                subtotal = it.subtotal
            )
        }

        viewModelScope.launch {
            try {
                val txId = repository.createTransaction(transaction, items)
                val createdTx = repository.getTransactionById(txId)
                if (createdTx != null) {
                    clearCart()
                    if (showReceiptDialog) {
                        _activeReceiptTransaction.value = createdTx
                    }

                    // Auto-print thermal bluetooth if configured
                    val config = storeConfig.value
                    if (config != null && config.isBluetoothAutoPrint && config.printerMacAddress.isNotBlank()) {
                        printerManager.printReceipt(config, createdTx.transaction, createdTx.items)
                    }

                    emitToast("Transaksi berhasil disimpan!")
                    onSuccess(createdTx)
                }
            } catch (e: Exception) {
                emitToast("Gagal menyimpan transaksi: ${e.localizedMessage}")
            }
        }
    }

    // Direct Quick Checkout from Calculator / Hitung Cepat
    fun processQuickCheckout(
        nominal: Double,
        note: String,
        paymentMethod: String,
        amountReceived: Double,
        manualItems: List<Pair<String, Double>> = emptyList(),
        onSuccess: (TransactionWithItems) -> Unit
    ) {
        if (nominal <= 0) {
            emitToast("Nominal belanja harus lebih dari 0!")
            return
        }

        val changeAmount = if (paymentMethod == "TUNAI") {
            if (amountReceived < nominal) {
                emitToast("Uang yang diterima kurang!")
                return
            }
            amountReceived - nominal
        } else 0.0

        val invoiceNumber = CurrencyUtils.generateInvoiceNumber()
        val timestamp = System.currentTimeMillis()

        val itemsList = if (manualItems.isNotEmpty()) {
            manualItems.mapIndexed { index, (itemName, itemPrice) ->
                TransactionItem(
                    transactionId = 0,
                    productId = 0,
                    productName = itemName.ifBlank { "Item ${index + 1}" },
                    categoryName = "Hitung Cepat",
                    quantity = 1,
                    pricePerUnit = itemPrice,
                    subtotal = itemPrice
                )
            }
        } else {
            listOf(
                TransactionItem(
                    transactionId = 0,
                    productId = 0,
                    productName = note.ifBlank { "Transaksi Cepat" },
                    categoryName = "Hitung Cepat",
                    quantity = 1,
                    pricePerUnit = nominal,
                    subtotal = nominal
                )
            )
        }

        val transaction = Transaction(
            invoiceNumber = invoiceNumber,
            timestamp = timestamp,
            totalAmount = nominal,
            paymentMethod = paymentMethod,
            amountReceived = if (paymentMethod == "TUNAI") amountReceived else nominal,
            changeAmount = changeAmount,
            customerName = "Hitung Cepat",
            note = note.ifBlank { "Transaksi Kasir Cepat" },
            totalItemsCount = itemsList.size,
            status = "COMPLETED"
        )

        viewModelScope.launch {
            try {
                val txId = repository.createTransaction(transaction, itemsList)
                val createdTx = repository.getTransactionById(txId)
                if (createdTx != null) {
                    _activeReceiptTransaction.value = createdTx
                    quickCalcExpression.value = "0"
                    quickCalcNote.value = ""

                    val config = storeConfig.value
                    if (config != null && config.isBluetoothAutoPrint && config.printerMacAddress.isNotBlank()) {
                        printerManager.printReceipt(config, createdTx.transaction, createdTx.items)
                    }

                    emitToast("Transaksi cepat berhasil disimpan!")
                    onSuccess(createdTx)
                }
            } catch (e: Exception) {
                emitToast("Gagal menyimpan transaksi: ${e.localizedMessage}")
            }
        }
    }

    // Receipt Actions
    private val _showReceiptScanner = MutableStateFlow(false)
    val showReceiptScanner: StateFlow<Boolean> = _showReceiptScanner.asStateFlow()

    fun openReceiptScanner() {
        _showReceiptScanner.value = true
    }

    fun closeReceiptScanner() {
        _showReceiptScanner.value = false
    }

    suspend fun findAndOpenReceiptByBarcode(rawBarcode: String): TransactionWithItems? {
        val normalized = Code128Encoder.normalizeScannedInvoice(rawBarcode)
        if (normalized.isBlank()) return null

        val cached = allTransactions.value
        val matched = cached.firstOrNull {
            it.transaction.invoiceNumber.equals(normalized, ignoreCase = true)
        } ?: cached.firstOrNull {
            val invUpper = it.transaction.invoiceNumber.uppercase()
            invUpper.contains(normalized) || normalized.contains(invUpper)
        } ?: repository.getTransactionByInvoice(normalized)
            ?: normalized.toLongOrNull()?.let { id -> repository.getTransactionById(id) }

        if (matched != null) {
            _showReceiptScanner.value = false
            _activeReceiptTransaction.value = matched
            emitToast("Struk ${matched.transaction.invoiceNumber} ditemukan!")
        }
        return matched
    }

    fun openReceipt(transactionWithItems: TransactionWithItems) {
        _activeReceiptTransaction.value = transactionWithItems
    }

    fun closeReceipt() {
        _activeReceiptTransaction.value = null
    }

    fun deleteTransaction(
        transactionWithItems: TransactionWithItems,
        restoreStock: Boolean = true,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val invoice = transactionWithItems.transaction.invoiceNumber
                repository.deleteTransaction(transactionWithItems.transaction.id, restoreStock)
                if (_activeReceiptTransaction.value?.transaction?.id == transactionWithItems.transaction.id) {
                    _activeReceiptTransaction.value = null
                }
                emitToast("Struk #$invoice berhasil dihapus")
                onSuccess?.invoke()
            } catch (e: Exception) {
                emitToast("Gagal menghapus struk: ${e.localizedMessage}")
            }
        }
    }

    fun printThermalReceipt(transactionWithItems: TransactionWithItems) {
        val config = storeConfig.value ?: return
        if (config.printerMacAddress.isBlank()) {
            emitToast("Pilih printer thermal bluetooth di menu Pengaturan terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            val result = printerManager.printReceipt(config, transactionWithItems.transaction, transactionWithItems.items)
            result.onSuccess { emitToast(it) }
                .onFailure { emitToast(it.message ?: "Gagal mencetak struk") }
        }
    }

    fun shareReceiptAsImage(transactionWithItems: TransactionWithItems) {
        val config = storeConfig.value ?: return
        viewModelScope.launch {
            val uri = ReceiptBitmapGenerator.saveReceiptToCacheAndGetUri(
                getApplication(),
                config,
                transactionWithItems.transaction,
                transactionWithItems.items
            )
            if (uri != null) {
                ReceiptBitmapGenerator.shareReceiptImage(
                    getApplication(),
                    uri,
                    transactionWithItems.transaction.invoiceNumber
                )
            } else {
                emitToast("Gagal membuat foto nota.")
            }
        }
    }

    fun printSystemReceipt(context: Context, transactionWithItems: TransactionWithItems) {
        val config = storeConfig.value ?: return
        ReceiptBitmapGenerator.printViaAndroidSystem(
            context,
            config,
            transactionWithItems.transaction,
            transactionWithItems.items
        )
    }

    // Inventory & Category CRUD
    fun saveProduct(
        id: Long,
        name: String,
        categoryId: Long,
        categoryName: String,
        price: Double,
        costPrice: Double,
        stock: Int,
        minStockAlert: Int,
        unit: String
    ) {
        if (name.isBlank()) {
            emitToast("Nama produk tidak boleh kosong!")
            return
        }
        viewModelScope.launch {
            val product = Product(
                id = id,
                name = name.trim(),
                categoryId = categoryId,
                categoryName = categoryName,
                price = price,
                costPrice = costPrice,
                stock = stock,
                minStockAlert = minStockAlert,
                unit = unit.trim().ifBlank { "Porsi" },
                updatedAt = System.currentTimeMillis()
            )
            if (id == 0L) {
                repository.insertProduct(product)
                emitToast("Produk berhasil ditambahkan")
            } else {
                repository.updateProduct(product)
                emitToast("Produk berhasil diperbarui")
            }
        }
    }

    fun adjustStock(productId: Long, newStock: Int) {
        viewModelScope.launch {
            repository.updateStock(productId, newStock.coerceAtLeast(0))
            emitToast("Stok berhasil diperbarui menjadi $newStock")
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            emitToast("Produk '${product.name}' dihapus.")
        }
    }

    fun clearAllProductStocks() {
        viewModelScope.launch {
            val products = allProducts.value
            products.forEach { prod ->
                repository.updateStock(prod.id, 0)
            }
            emitToast("Semua stok produk berhasil di-reset ke 0")
        }
    }

    fun deleteAllProducts() {
        viewModelScope.launch {
            val products = allProducts.value
            products.forEach { prod ->
                repository.deleteProduct(prod)
            }
            emitToast("Semua menu/produk telah dihapus")
        }
    }

    fun saveCategory(id: Long, name: String, iconName: String, colorHex: String) {
        if (name.isBlank()) {
            emitToast("Nama kategori tidak boleh kosong!")
            return
        }
        viewModelScope.launch {
            val category = Category(
                id = id,
                name = name.trim(),
                iconName = iconName,
                colorHex = colorHex
            )
            if (id == 0L) {
                repository.insertCategory(category)
                emitToast("Kategori '${name}' berhasil ditambahkan")
            } else {
                repository.updateCategory(category)
                emitToast("Kategori '${name}' diperbarui")
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            emitToast("Kategori '${category.name}' dihapus.")
        }
    }

    fun stopAllVoiceAndCloseAi() {
        _isContinuousListening.value = false
        voiceParser.stopListening()
        voiceParser.clearState()
        _aiLastResponse.value = null
    }

    fun triggerTwoWayCloudSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = com.example.util.FirebaseSyncManager.syncAllDataWithCloud(getApplication<Application>(), repository)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (success) {
                    emitToast("Sinkronisasi dua-arah Cloud berhasil!")
                } else {
                    emitToast("Gagal melakukan sinkronisasi!")
                }
            }
        }
    }

    // Store & Printer Settings
    fun updateStoreConfig(config: StoreConfig) {
        viewModelScope.launch {
            repository.updateStoreConfig(config)
            emitToast("Pengaturan toko berhasil disimpan")
        }
    }

    fun updateThemeMode(themeMode: String) {
        val current = storeConfig.value ?: StoreConfig()
        updateStoreConfig(current.copy(themeMode = themeMode))
    }

    // AI Assistant Logic
    fun toggleContinuousListening() {
        if (storeConfig.value?.isAiAssistantEnabled == false) {
            emitToast("AI Assistant dinonaktifkan di pengaturan.")
            return
        }
        val nextState = !_isContinuousListening.value
        _isContinuousListening.value = nextState
        if (nextState) {
            voiceParser.startListening()
            emitToast("Mode Suara Terus Aktif Dinyalakan. Siap mendengarkan...")
        } else {
            voiceParser.stopListening()
            emitToast("Mode Suara Terus Dimatikan.")
        }
    }

    fun startListening() {
        if (storeConfig.value?.isAiAssistantEnabled == false) {
            emitToast("AI Assistant dinonaktifkan di pengaturan.")
            return
        }
        voiceParser.startListening()
    }

    fun stopListeningAndProcess() {
        _isContinuousListening.value = false
        voiceParser.stopListening()
        viewModelScope.launch {
            delay(300)
            val text = voiceState.value.spokenText
            if (text.isNotBlank()) {
                processAiCommand(text)
            }
        }
    }

    fun submitAiCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            processAiCommand(trimmed)
        }
    }

    // Active Screen Tracking
    val currentScreen = MutableStateFlow("KASIR")

    fun setCurrentScreen(screenName: String) {
        currentScreen.value = screenName
    }

    fun splitCompoundCommand(rawInput: String): List<String> {
        val text = rawInput.trim()
        if (text.isBlank()) return emptyList()

        // 1. Replace conjunctions with standard delimiter " | "
        var normalized = text
            .replace(Regex("""\b(?:dan\s+juga|dan|lalu|kemudian)\b""", RegexOption.IGNORE_CASE), " | ")
            .replace(",", " | ")
            .replace(";", " | ")

        // 2. Insert " | " before action verb triggers if preceded by non-whitespace
        val verbRegex = Regex("""(?<=\S)\s+(?=\b(?:tambah|tambahkan|kurangi|kurangkan|ubah|ganti|set|hapus|buang|pesan|beli)\b)""", RegexOption.IGNORE_CASE)
        normalized = normalized.replace(verbRegex, " | ")

        val chunks = normalized.split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (chunks.isEmpty()) return listOf(text)

        // 3. Inherit action verb from previous chunk if missing in current chunk (e.g. "tambah 2 es teh" + "1 nasi goreng")
        val verbPrefixRegex = Regex("""^\b(tambah|tambahkan|kurangi|kurangkan|ubah|ganti|set|hapus|buang|pesan|beli)\b""", RegexOption.IGNORE_CASE)
        val result = mutableListOf<String>()
        var activeVerb = ""

        for (chunk in chunks) {
            val match = verbPrefixRegex.find(chunk)
            if (match != null) {
                activeVerb = match.value
                result.add(chunk)
            } else if (activeVerb.isNotBlank()) {
                result.add("$activeVerb $chunk")
            } else {
                result.add(chunk)
            }
        }

        return result
    }

    suspend fun ensureCategory(catNameText: String): Category {
        val nameClean = catNameText.trim().replaceFirstChar { it.uppercase() }
        val existing = allCategories.value.find { it.name.equals(nameClean, ignoreCase = true) }
        if (existing != null) return existing

        val newCat = Category(id = 0L, name = nameClean, iconName = "Category", colorHex = "#3B82F6")
        val id = repository.insertCategory(newCat)
        return newCat.copy(id = id)
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
            "januari" to 0, "jan" to 0,
            "februari" to 1, "feb" to 1,
            "maret" to 2, "mar" to 2,
            "april" to 3, "apr" to 3,
            "mei" to 4,
            "juni" to 5, "jun" to 5,
            "juli" to 6, "jul" to 6,
            "agustus" to 7, "agu" to 7, "agt" to 7,
            "september" to 8, "sep" to 8,
            "oktober" to 9, "okt" to 9,
            "november" to 10, "nov" to 10,
            "desember" to 11, "des" to 11
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

        var day = digits[0]
        var year = now.get(Calendar.YEAR)

        if (digits.size >= 2 && digits[1] > 2000) {
            year = digits[1]
        } else if (day > 2000) {
            year = day
            day = if (digits.size >= 2) digits[1] else 1
        }

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

    private fun extractIndonesianPrice(rawInput: String): Pair<Double, String> {
        var text = rawInput.trim()

        val wordNominals = listOf(
            Pair(Regex("""seratus\s*ribu""", RegexOption.IGNORE_CASE), 100000.0),
            Pair(Regex("""dua\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 200000.0),
            Pair(Regex("""tiga\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 300000.0),
            Pair(Regex("""empat\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 400000.0),
            Pair(Regex("""lima\s*ratus\s*ribu""", RegexOption.IGNORE_CASE), 500000.0),
            Pair(Regex("""sembilan\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 90000.0),
            Pair(Regex("""delapan\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 80000.0),
            Pair(Regex("""tujuh\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 70000.0),
            Pair(Regex("""enam\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 60000.0),
            Pair(Regex("""lima\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 50000.0),
            Pair(Regex("""empat\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 40000.0),
            Pair(Regex("""tiga\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 30000.0),
            Pair(Regex("""dua\s*puluh\s*lima\s*ribu""", RegexOption.IGNORE_CASE), 25000.0),
            Pair(Regex("""dua\s*puluh\s*ribu""", RegexOption.IGNORE_CASE), 20000.0),
            Pair(Regex("""lima\s*belas\s*ribu""", RegexOption.IGNORE_CASE), 15000.0),
            Pair(Regex("""sepuluh\s*ribu""", RegexOption.IGNORE_CASE), 10000.0),
            Pair(Regex("""delapan\s*ribu""", RegexOption.IGNORE_CASE), 8000.0),
            Pair(Regex("""tujuh\s*ribu""", RegexOption.IGNORE_CASE), 7000.0),
            Pair(Regex("""enam\s*ribu""", RegexOption.IGNORE_CASE), 6000.0),
            Pair(Regex("""lima\s*ribu""", RegexOption.IGNORE_CASE), 5000.0),
            Pair(Regex("""empat\s*ribu""", RegexOption.IGNORE_CASE), 4000.0),
            Pair(Regex("""tiga\s*ribu""", RegexOption.IGNORE_CASE), 3000.0),
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
                if (num < 1000) {
                    calculatedPrice = num * 1000.0
                }
            } else if (suffix in listOf("juta", "jt")) {
                if (num < 1000000) {
                    calculatedPrice = num * 1000000.0
                }
            } else {
                if (num in 1.0..499.0) {
                    calculatedPrice = num * 1000.0
                }
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

    private suspend fun parseLocalCommand(rawCommand: String): Boolean {
        val cmd = rawCommand.lowercase().trim()
        val products = allProducts.value

        // 1. Stock Management (Relative addition vs Absolute set)
        if (cmd.contains("stok")) {
            val digits = Regex("""(\d+)""").findAll(cmd).map { it.value.toIntOrNull() ?: 0 }.toList()
            val num = digits.lastOrNull()
            val isClearAll = cmd.contains("hapus semua") || cmd.contains("kosongkan semua") || cmd.contains("reset semua") || cmd.contains("nolkan semua")

            if (isClearAll) {
                clearAllProductStocks()
                _aiLastResponse.value = "Semua stok produk telah dikosongkan (di-reset ke 0)."
                return true
            } else if (num != null) {
                var searchName = cmd
                    .replace("tambah stok", "")
                    .replace("tambahkan stok", "")
                    .replace("isi stok", "")
                    .replace("tambah", "")
                    .replace("ubah stok", "")
                    .replace("ganti stok", "")
                    .replace("set stok", "")
                    .replace("hapus stok", "")
                    .replace("kosongkan stok", "")
                    .replace("stoknya", "")
                    .replace("stok", "")
                    .replace("menjadi", "")
                    .replace("jadi", "")
                    .replace("ke", "")
                    .replace(num.toString(), "")
                    .replace("pcs", "")
                    .replace("porsi", "")
                    .replace("buah", "")
                    .trim()

                var prod = if (searchName.isNotBlank()) {
                    products.find { p -> p.name.lowercase().contains(searchName) || searchName.contains(p.name.lowercase()) }
                } else null

                if (prod == null) {
                    prod = products.find { p -> cmd.contains(p.name.lowercase()) }
                }

                if (prod != null) {
                    val isRelativeAdd = cmd.contains("tambah stok") || cmd.contains("tambahkan stok") || cmd.contains("isi stok") || (cmd.contains("tambah") && !cmd.contains("ubah") && !cmd.contains("ganti") && !cmd.contains("set") && !cmd.contains("menjadi"))
                    
                    val finalStock = if (isRelativeAdd) {
                        prod.stock + num
                    } else {
                        num
                    }

                    adjustStock(prod.id, finalStock)
                    _aiLastResponse.value = "Stok '${prod.name}' berhasil diperbarui menjadi $finalStock (stok awal: ${prod.stock})."
                    emitToast("Stok '${prod.name}': $finalStock")
                    return true
                }
            } else if (cmd.contains("hapus stok") || cmd.contains("kosongkan stok") || cmd.contains("reset stok")) {
                clearAllProductStocks()
                _aiLastResponse.value = "Semua stok produk telah dikosongkan (di-reset ke 0)."
                return true
            }
        }

        // 2. Add Category Alone or Delete Category
        if ((cmd.contains("tambah kategori") || cmd.contains("buat kategori") || cmd.contains("kategori baru")) && !cmd.contains("harga") && !cmd.contains("rp") && !cmd.contains("ribu")) {
            var catName = cmd
                .replace("tambah kategori", "")
                .replace("buat kategori", "")
                .replace("kategori baru", "")
                .replace("kategori", "")
                .trim()

            if (catName.isNotBlank()) {
                val cat = ensureCategory(catName)
                _aiLastResponse.value = "Kategori '${cat.name}' berhasil ditambahkan/siap digunakan."
                emitToast("Kategori: ${cat.name}")
                return true
            }
        }

        if ((cmd.contains("hapus kategori") || cmd.contains("buang kategori") || cmd.contains("delete kategori")) && !cmd.contains("menu") && !cmd.contains("produk")) {
            val searchName = cmd
                .replace("hapus kategori", "")
                .replace("buang kategori", "")
                .replace("delete kategori", "")
                .replace("kategori", "")
                .trim()

            if (searchName.isNotBlank()) {
                val cat = allCategories.value.find { it.name.lowercase().contains(searchName) || searchName.contains(it.name.lowercase()) }
                if (cat != null) {
                    deleteCategory(cat)
                    _aiLastResponse.value = "Kategori '${cat.name}' berhasil dihapus."
                    emitToast("Hapus Kategori: ${cat.name}")
                    return true
                }
            }
        }

        // 3. Delete Transaction by Nominal / Amount
        if (cmd.contains("hapus transaksi") || cmd.contains("batal transaksi") || cmd.contains("buang transaksi")) {
            val (nominal, _) = extractIndonesianPrice(cmd)
            val txs = allTransactions.value

            if (nominal >= 500) {
                val targetTx = txs.find { Math.abs(it.transaction.totalAmount - nominal) < 1.0 }
                if (targetTx != null) {
                    deleteTransaction(targetTx)
                    val formatted = CurrencyUtils.formatRupiah(nominal)
                    _aiLastResponse.value = "Transaksi #${targetTx.transaction.invoiceNumber} sebesar $formatted berhasil dihapus."
                    emitToast("Hapus Transaksi $formatted")
                    return true
                } else {
                    val formatted = CurrencyUtils.formatRupiah(nominal)
                    _aiLastResponse.value = "Tidak ditemukan transaksi dengan nominal $formatted."
                    return true
                }
            } else if (cmd.contains("terakhir") || cmd.contains("terbaru")) {
                val lastTx = txs.firstOrNull()
                if (lastTx != null) {
                    deleteTransaction(lastTx)
                    _aiLastResponse.value = "Transaksi terakhir #${lastTx.transaction.invoiceNumber} berhasil dihapus."
                    return true
                }
            }
        }

        // 4. Store Config Settings (Name, Address, Phone, Receipt Footer)
        if (cmd.contains("nama toko") || cmd.contains("alamat toko") || cmd.contains("nomor hp") || cmd.contains("telepon toko") || cmd.contains("catatan struk") || cmd.contains("footer struk") || cmd.contains("footer nota")) {
            val currentCfg = storeConfig.value ?: StoreConfig()

            if (cmd.contains("nama toko")) {
                val newName = rawCommand
                    .replace(Regex("""(?i)(?:ubah|ganti|set|edit)\s+nama\s+toko\s*(?:menjadi|jadi|ke)?\s*"""), "")
                    .replace(Regex("""(?i)nama\s+toko\s*"""), "")
                    .trim()
                    .replaceFirstChar { it.uppercase() }

                if (newName.isNotBlank()) {
                    updateStoreConfig(currentCfg.copy(storeName = newName, qrisMerchantName = newName))
                    _aiLastResponse.value = "Nama toko berhasil diubah menjadi '$newName'."
                    emitToast("Nama Toko: $newName")
                    return true
                }
            }

            if (cmd.contains("alamat toko")) {
                val newAddr = rawCommand
                    .replace(Regex("""(?i)(?:ubah|ganti|set|edit)\s+alamat\s+toko\s*(?:menjadi|jadi|ke)?\s*"""), "")
                    .replace(Regex("""(?i)alamat\s+toko\s*"""), "")
                    .trim()

                if (newAddr.isNotBlank()) {
                    updateStoreConfig(currentCfg.copy(storeAddress = newAddr))
                    _aiLastResponse.value = "Alamat toko berhasil diubah menjadi '$newAddr'."
                    emitToast("Alamat Toko Diperbarui")
                    return true
                }
            }

            if (cmd.contains("nomor hp") || cmd.contains("telepon toko") || cmd.contains("no hp")) {
                val newPhone = rawCommand
                    .replace(Regex("""(?i)(?:ubah|ganti|set|edit)\s+(?:nomor\s+hp|telepon\s+toko|no\s+hp)\s*(?:menjadi|jadi|ke)?\s*"""), "")
                    .trim()

                if (newPhone.isNotBlank()) {
                    updateStoreConfig(currentCfg.copy(storePhone = newPhone))
                    _aiLastResponse.value = "Nomor kontak toko berhasil diubah menjadi '$newPhone'."
                    emitToast("Telepon Toko: $newPhone")
                    return true
                }
            }

            if (cmd.contains("catatan struk") || cmd.contains("footer struk") || cmd.contains("footer nota") || cmd.contains("catatan nota")) {
                val newFooter = rawCommand
                    .replace(Regex("""(?i)(?:ubah|ganti|set|edit)\s+(?:catatan\s+struk|footer\s+struk|footer\s+nota|catatan\s+nota)\s*(?:menjadi|jadi|ke)?\s*"""), "")
                    .trim()

                if (newFooter.isNotBlank()) {
                    updateStoreConfig(currentCfg.copy(receiptFooter = newFooter))
                    _aiLastResponse.value = "Catatan kaki struk berhasil diperbarui."
                    emitToast("Catatan Struk Diperbarui")
                    return true
                }
            }
        }

        // 3. Cart Reduction / Removal (e.g. "kurangi 5 ayam goreng", "hapus 5 ayam goreng", "kurangi ayam goreng 5", "hapus ayam goreng dari keranjang")
        val isCartReduceAction = cmd.contains("kurang") || cmd.contains("kurangi") || cmd.contains("kurangkan") || cmd.contains("potong") ||
                (cmd.contains("hapus") && (cmd.contains("keranjang") || currentScreen.value == "KASIR") && !cmd.contains("hapus menu") && !cmd.contains("hapus produk") && !cmd.contains("hapus stok") && !cmd.contains("hapus semua"))

        if (isCartReduceAction) {
            val digits = Regex("""(\d+)""").findAll(cmd).map { it.value.toIntOrNull() ?: 0 }.toList()
            var qtyToReduce = digits.lastOrNull() ?: 1

            if (qtyToReduce <= 0) {
                val wordMap = mapOf("satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5)
                for ((w, n) in wordMap) {
                    if (cmd.contains(w)) {
                        qtyToReduce = n
                        break
                    }
                }
            }

            var cleanText = cmd
                .replace(Regex("""\d+"""), "")
                .replace("kurangi", "")
                .replace("kurangkan", "")
                .replace("kurang", "")
                .replace("potong", "")
                .replace("hapus", "")
                .replace("buang", "")
                .replace("keluarkan", "")
                .replace("dari keranjang", "")
                .replace("keranjang", "")
                .replace("porsi", "")
                .replace("buah", "")
                .replace("pcs", "")
                .replace("satu", "")
                .replace("dua", "")
                .replace("tiga", "")
                .replace("empat", "")
                .replace("lima", "")
                .trim()

            var prod = if (cleanText.isNotBlank()) {
                products.find { p -> p.name.lowercase().contains(cleanText) || cleanText.contains(p.name.lowercase()) }
            } else null

            if (prod == null) {
                prod = products.find { p -> cmd.contains(p.name.lowercase()) }
            }

            if (prod != null) {
                val existingCartItem = cart.value[prod.id]
                if (existingCartItem != null) {
                    val currentQty = existingCartItem.quantity
                    val isExplicitDelete = cmd.contains("hapus") || cmd.contains("buang") || cmd.contains("keluarkan")
                    val isZeroOrLess = (currentQty - qtyToReduce) <= 0

                    if (isExplicitDelete && !cmd.contains(Regex("""\d+"""))) {
                        removeProductFromCart(prod.id)
                        _aiLastResponse.value = "Item '${prod.name}' telah dihapus dari keranjang."
                        emitToast("Hapus '${prod.name}' dari keranjang")
                    } else if (isZeroOrLess) {
                        removeProductFromCart(prod.id)
                        _aiLastResponse.value = "Item '${prod.name}' telah dihapus dari keranjang."
                        emitToast("Hapus '${prod.name}' dari keranjang")
                    } else {
                        val newQty = currentQty - qtyToReduce
                        val updatedCart = cart.value.toMutableMap()
                        updatedCart[prod.id] = existingCartItem.copy(quantity = newQty)
                        _cart.value = updatedCart
                        _aiLastResponse.value = "Mengurangi $qtyToReduce '${prod.name}' dari keranjang. Sisa: $newQty."
                        emitToast("Sisa '${prod.name}' di keranjang: $newQty")
                    }
                    return true
                } else {
                    _aiLastResponse.value = "Item '${prod.name}' tidak ada di dalam keranjang."
                    return true
                }
            }
        }

        // 4. Delete All Products / Clear Menu List
        if (cmd.contains("hapus semua menu") || cmd.contains("hapus semua produk") || cmd.contains("bersihkan menu") || cmd.contains("kosongkan menu") || cmd.contains("bersihkan daftar menu")) {
            deleteAllProducts()
            _aiLastResponse.value = "Semua menu/produk berhasil dihapus dari daftar."
            return true
        }

        // 5. Delete Specific Product
        if ((cmd.contains("hapus menu") || cmd.contains("hapus produk") || cmd.contains("buang menu") || cmd.contains("hapus item")) && !cmd.contains("semua") && !cmd.contains("keranjang")) {
            var searchName = cmd
                .replace("hapus menu", "")
                .replace("hapus produk", "")
                .replace("buang menu", "")
                .replace("hapus item", "")
                .trim()

            val prod = products.find { p -> p.name.lowercase().contains(searchName) || searchName.contains(p.name.lowercase()) }
            if (prod != null) {
                deleteProduct(prod)
                _aiLastResponse.value = "Menu '${prod.name}' berhasil dihapus."
                return true
            }
        }

        // 5b. Rename Product / Item Stock Name (e.g. "ubah ayam geprek jadi ayam gepuk", "ubah nama stok ayam geprek jadi ayam gepuk", "ganti nama es teh menjadi es teh manis")
        val isExplicitRename = cmd.contains("ubah nama") || cmd.contains("ganti nama") || cmd.contains("edit nama") || cmd.contains("set nama")
        val isDelimitedRename = (cmd.contains("ubah") || cmd.contains("ganti")) && (cmd.contains(" jadi ") || cmd.contains(" menjadi ") || cmd.contains(" ke "))
        val isRenameAction = (isExplicitRename || isDelimitedRename) &&
                !cmd.contains("harga") && !cmd.contains("modal") && !cmd.contains("kategori") && !cmd.contains("toko") && !cmd.contains("alamat") && !cmd.contains("hp") &&
                !cmd.contains(Regex("""stok\s*\d+"""))

        if (isRenameAction) {
            val delimiter = when {
                cmd.contains(" menjadi ") -> " menjadi "
                cmd.contains(" jadi ") -> " jadi "
                cmd.contains(" ke ") -> " ke "
                else -> ""
            }

            if (delimiter.isNotBlank()) {
                val parts = rawCommand.split(Regex("(?i)$delimiter"), limit = 2)
                if (parts.size == 2) {
                    val leftPart = parts[0].lowercase().trim()
                    val newName = parts[1].trim().replaceFirstChar { it.uppercase() }

                    val cleanOldSearch = leftPart
                        .replace("ubah nama stok", "")
                        .replace("ganti nama stok", "")
                        .replace("ubah nama menu", "")
                        .replace("ganti nama menu", "")
                        .replace("ubah nama produk", "")
                        .replace("ganti nama produk", "")
                        .replace("ubah nama barang", "")
                        .replace("ganti nama barang", "")
                        .replace("ubah nama item", "")
                        .replace("ganti nama item", "")
                        .replace("ubah nama", "")
                        .replace("ganti nama", "")
                        .replace("edit nama", "")
                        .replace("set nama", "")
                        .replace("ubah menu", "")
                        .replace("ganti menu", "")
                        .replace("ubah produk", "")
                        .replace("ganti produk", "")
                        .replace("ubah item", "")
                        .replace("ganti item", "")
                        .replace("ubah barang", "")
                        .replace("ganti barang", "")
                        .replace("ubah stok", "")
                        .replace("ganti stok", "")
                        .replace("stok", "")
                        .replace("ubah", "")
                        .replace("ganti", "")
                        .replace("nama", "")
                        .replace("menu", "")
                        .replace("produk", "")
                        .trim()

                    var prod = if (cleanOldSearch.isNotBlank()) {
                        products.find { p -> p.name.lowercase().contains(cleanOldSearch) || cleanOldSearch.contains(p.name.lowercase()) }
                    } else null

                    if (prod == null) {
                        prod = products.find { p -> leftPart.contains(p.name.lowercase()) }
                    }

                    if (prod != null && newName.isNotBlank()) {
                        val oldName = prod.name
                        val updated = prod.copy(name = newName, updatedAt = System.currentTimeMillis())
                        viewModelScope.launch { repository.updateProduct(updated) }

                        _aiLastResponse.value = "Nama menu '$oldName' berhasil diubah menjadi '$newName'."
                        emitToast("Nama Menu: $newName")
                        return true
                    }
                }
            }
        }

        // 5c. Change Product Category (e.g. "ubah kategori ayam geprek ke makanan", "ganti kategori es teh jadi minuman")
        val isChangeCategoryAction = cmd.contains("kategori") &&
                (cmd.contains("ubah") || cmd.contains("ganti") || cmd.contains("set") || cmd.contains("pindah")) &&
                !cmd.contains("hapus") && !cmd.contains("buang") && !cmd.contains("tambah menu") && !cmd.contains("buat menu") && !cmd.contains("tambah produk") && !cmd.contains("buat produk")

        if (isChangeCategoryAction) {
            val delimiter = when {
                cmd.contains(" ke ") -> " ke "
                cmd.contains(" menjadi ") -> " menjadi "
                cmd.contains(" jadi ") -> " jadi "
                else -> ""
            }

            if (delimiter.isNotBlank()) {
                val parts = rawCommand.split(Regex("(?i)$delimiter"), limit = 2)
                if (parts.size == 2) {
                    val leftPart = parts[0].lowercase().trim()
                    val targetCatName = parts[1].trim()

                    val cleanOldSearch = leftPart
                        .replace("ubah kategori menu", "")
                        .replace("ganti kategori menu", "")
                        .replace("ubah kategori produk", "")
                        .replace("ganti kategori produk", "")
                        .replace("ubah kategori", "")
                        .replace("ganti kategori", "")
                        .replace("set kategori", "")
                        .replace("pindah kategori", "")
                        .replace("kategori", "")
                        .replace("ubah", "")
                        .replace("ganti", "")
                        .trim()

                    var prod = if (cleanOldSearch.isNotBlank()) {
                        products.find { p -> p.name.lowercase().contains(cleanOldSearch) || cleanOldSearch.contains(p.name.lowercase()) }
                    } else null

                    if (prod == null) {
                        prod = products.find { p -> leftPart.contains(p.name.lowercase()) }
                    }

                    if (prod != null && targetCatName.isNotBlank()) {
                        val category = ensureCategory(targetCatName)
                        val updated = prod.copy(
                            categoryId = category.id,
                            categoryName = category.name,
                            updatedAt = System.currentTimeMillis()
                        )
                        viewModelScope.launch { repository.updateProduct(updated) }

                        _aiLastResponse.value = "Kategori untuk menu '${prod.name}' berhasil diubah ke '${category.name}'."
                        emitToast("Kategori '${prod.name}' -> '${category.name}'")
                        return true
                    }
                }
            }
        }

        // 6. Update Product Selling Price (Harga Jual) & Cost Price (Harga Modal) - Single or Multi-product
        val isPriceAction = cmd.contains("harga") || cmd.contains("modal") || cmd.contains("ubah harga") || cmd.contains("ganti harga") || cmd.contains("ubah modal")
        if (isPriceAction && !cmd.contains("tambah menu") && !cmd.contains("tambah produk") && !cmd.contains("buat menu") && !cmd.contains("buat produk") && !cmd.contains("kategori")) {
            val lowerCmd = cmd

            // Find all product occurrences in sentence
            val rawMatches = mutableListOf<Triple<Int, Int, Product>>() // startIdx, endIdx, product
            for (prod in products) {
                val pName = prod.name.lowercase()
                var idx = lowerCmd.indexOf(pName)
                while (idx >= 0) {
                    rawMatches.add(Triple(idx, idx + pName.length, prod))
                    idx = lowerCmd.indexOf(pName, idx + pName.length)
                }
            }

            // Remove overlapping product matches (keep longer product names)
            rawMatches.sortByDescending { it.second - it.first }
            val distinctMatches = mutableListOf<Triple<Int, Int, Product>>()
            for (match in rawMatches) {
                val overlaps = distinctMatches.any { existing ->
                    (match.first >= existing.first && match.first < existing.second) ||
                    (match.second > existing.first && match.second <= existing.second)
                }
                if (!overlaps) {
                    distinctMatches.add(match)
                }
            }

            distinctMatches.sortBy { it.first } // sort by start index in sentence

            if (distinctMatches.isNotEmpty()) {
                val updatedSummaries = mutableListOf<String>()
                val isDefaultModalOnly = cmd.contains("modal") && !cmd.contains("harga")

                for (i in distinctMatches.indices) {
                    val (startIdx, endIdx, prod) = distinctMatches[i]
                    val nextStartIdx = if (i + 1 < distinctMatches.size) distinctMatches[i + 1].first else lowerCmd.length
                    val segmentText = lowerCmd.substring(endIdx, nextStartIdx).trim()

                    var sellingPrice: Double? = null
                    var modalPrice: Double? = null
                    var cleanSegment = segmentText

                    val modalRegex = Regex("""modal\s*(?:harga\s*)?(?:jadi\s*|sebesar\s*|ke\s*)?([0-9\sA-Za-z]+)""", RegexOption.IGNORE_CASE)
                    val modalMatch = modalRegex.find(cleanSegment)
                    if (modalMatch != null) {
                        val (mPrice, _) = extractIndonesianPrice(modalMatch.groupValues[1])
                        if (mPrice >= 0) modalPrice = mPrice
                        cleanSegment = cleanSegment.replace(modalMatch.value, "").trim()
                    }

                    val (extractedPrice, _) = extractIndonesianPrice(cleanSegment)

                    if (isDefaultModalOnly && modalPrice == null) {
                        if (extractedPrice >= 0) modalPrice = extractedPrice
                    } else {
                        if (extractedPrice >= 0) sellingPrice = extractedPrice
                    }

                    if (sellingPrice != null || modalPrice != null) {
                        val newSell = sellingPrice ?: prod.price
                        val newModal = modalPrice ?: prod.costPrice

                        val updated = prod.copy(price = newSell, costPrice = newModal, updatedAt = System.currentTimeMillis())
                        viewModelScope.launch { repository.updateProduct(updated) }

                        val formattedSell = CurrencyUtils.formatRupiah(newSell)
                        val formattedModal = CurrencyUtils.formatRupiah(newModal)

                        if (sellingPrice != null && modalPrice != null) {
                            updatedSummaries.add("${prod.name} (Jual: $formattedSell, Modal: $formattedModal)")
                        } else if (modalPrice != null) {
                            updatedSummaries.add("${prod.name} (Modal: $formattedModal)")
                        } else {
                            updatedSummaries.add("${prod.name} (Jual: $formattedSell)")
                        }
                    }
                }

                if (updatedSummaries.isNotEmpty()) {
                    val fullMsg = "Berhasil memperbarui harga: " + updatedSummaries.joinToString(", ") + "."
                    _aiLastResponse.value = fullMsg
                    emitToast("Harga Produk Diperbarui")
                    return true
                }
            }
        }

        // 7. Add New Product / Menu with Category support (e.g. "tambah menu es jeruk kategori minuman harga 7rb")
        val isAddProductAction = cmd.contains("tambah menu") || cmd.contains("tambah produk") || cmd.contains("buat menu") || cmd.contains("buat produk") || cmd.contains("menu baru") ||
                (cmd.contains("tambah") && (cmd.contains("kategori") || cmd.contains("harga") || cmd.contains("ribu") || cmd.contains("rb")) && !cmd.contains("stok") && currentScreen.value == "MENU")

        if (isAddProductAction) {
            var rawText = cmd
                .replace("tambah menu", "")
                .replace("tambah produk", "")
                .replace("buat menu", "")
                .replace("buat produk", "")
                .replace("menu baru", "")
                .replace("tambah", "")
                .trim()

            // 1. Extract Name (assuming name comes after 'tambah'/'buat' and before any keyword)
            val keywords = listOf("kategori", "harga", "modal", "stok")
            var prodName = rawText
            for (kw in keywords) {
                val idx = prodName.indexOf(kw)
                if (idx != -1) prodName = prodName.substring(0, idx).trim()
            }
            
            // 2. Extract Category
            var targetCatName = "Makanan"
            val catRegex = Regex("""(?:kategori|kat)\s+([a-zA-Z0-9\s]+?)(\s+(?:harga|modal|stok|$))""", RegexOption.IGNORE_CASE)
            val catMatch = catRegex.find(rawText)
            if (catMatch != null) {
                targetCatName = catMatch.groupValues[1].trim()
            }

            // 3. Extract Price, CostPrice, Stock
            val (price, _) = extractIndonesianPrice(rawText.substringAfter("harga", "0"))
            val (costPrice, _) = extractIndonesianPrice(rawText.substringAfter("modal", "0"))
            
            val stockMatch = Regex("""stok\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
            val stock = stockMatch?.groupValues?.get(1)?.toIntOrNull() ?: 20

            if (prodName.isNotBlank()) {
                val category = ensureCategory(targetCatName)
                val existing = products.find { it.name.equals(prodName, ignoreCase = true) }

                saveProduct(
                    id = existing?.id ?: 0L,
                    name = prodName.replaceFirstChar { it.uppercase() },
                    categoryId = category.id,
                    categoryName = category.name,
                    price = if (price > 0) price else (existing?.price ?: 0.0),
                    costPrice = if (costPrice > 0) costPrice else (existing?.costPrice ?: 0.0),
                    stock = if (stock > 0 || existing == null) stock else existing.stock,
                    minStockAlert = existing?.minStockAlert ?: 5,
                    unit = existing?.unit ?: "Porsi"
                )
                val msg = "Menu '${prodName.replaceFirstChar { it.uppercase() }}' (Harga: ${CurrencyUtils.formatRupiah(price)}, Modal: ${CurrencyUtils.formatRupiah(costPrice)}, Stok: $stock) berhasil disimpan."
                _aiLastResponse.value = msg
                emitToast("Menu '$prodName' disimpan!")
                return true
            }
        }

        // 7. Change Theme (Support all 3 theme modes: DARK, SAGE, LIGHT)
        if (cmd.contains("tema") || cmd.contains("mode gelap") || cmd.contains("mode terang") || cmd.contains("mode sage") || cmd.contains("dark mode") || cmd.contains("light mode") || cmd.contains("sage mode") || cmd.contains("hijau")) {
            if (cmd.contains("sage") || cmd.contains("hijau")) {
                updateThemeMode("SAGE")
                _aiLastResponse.value = "Tema berhasil diubah ke Mode Sage (Hijau Soft)."
            } else if (cmd.contains("gelap") || cmd.contains("dark")) {
                updateThemeMode("DARK")
                _aiLastResponse.value = "Tema berhasil diubah ke Mode Gelap."
            } else if (cmd.contains("terang") || cmd.contains("light")) {
                updateThemeMode("LIGHT")
                _aiLastResponse.value = "Tema berhasil diubah ke Mode Terang."
            } else {
                val current = storeConfig.value?.themeMode ?: "DARK"
                val newTheme = when (current) {
                    "DARK" -> "SAGE"
                    "SAGE" -> "LIGHT"
                    else -> "DARK"
                }
                updateThemeMode(newTheme)
                val themeLabel = when (newTheme) {
                    "SAGE" -> "Sage (Hijau Soft)"
                    "LIGHT" -> "Mode Terang"
                    else -> "Mode Gelap"
                }
                _aiLastResponse.value = "Tema berhasil diubah ke $themeLabel."
            }
            return true
        }

        // 8. Navigation & Specific Report Date Switch
        if (cmd.contains("laporan") && (cmd.contains("tanggal") || cmd.contains("tgl") || cmd.contains("bulan") || cmd.contains("kemarin") || cmd.contains("hari ini"))) {
            val cal = extractIndonesianDate(cmd)
            if (cal != null) {
                selectedDailyTimestamp.value = cal.timeInMillis
                selectedReportPeriod.value = ReportPeriodType.HARIAN
                _aiNavigationTrigger.emit("LAPORAN")
                currentScreen.value = "LAPORAN"

                val dateFormat = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID"))
                val dateText = dateFormat.format(cal.time)
                _aiLastResponse.value = "Membuka Laporan Penjualan untuk tanggal $dateText."
                emitToast("Laporan: $dateText")
                return true
            }
        }

        if (cmd.contains("laporan") && !cmd.contains("pdf") && !cmd.contains("csv") && !cmd.contains("ekspor") && !cmd.contains("export")) {
            _aiNavigationTrigger.emit("LAPORAN")
            currentScreen.value = "LAPORAN"
            _aiLastResponse.value = "Membuka halaman Laporan Penjualan."
            emitToast("Navigasi: Laporan")
            return true
        }
        if (cmd.contains("kasir") || cmd.contains("penjualan") || cmd.contains("pos")) {
            _aiNavigationTrigger.emit("KASIR")
            currentScreen.value = "KASIR"
            _aiLastResponse.value = "Membuka layar Kasir."
            emitToast("Navigasi: Kasir")
            return true
        }
        if (cmd.contains("beranda") || cmd.contains("dashboard") || cmd.contains("home")) {
            _aiNavigationTrigger.emit("DASHBOARD")
            currentScreen.value = "DASHBOARD"
            _aiLastResponse.value = "Membuka Beranda Utama."
            emitToast("Navigasi: Beranda")
            return true
        }
        if (cmd.contains("hitung") || cmd.contains("kalkulator") || cmd.contains("cepat")) {
            _aiNavigationTrigger.emit("HITUNG")
            currentScreen.value = "HITUNG"
            _aiLastResponse.value = "Membuka Kalkulator Hitung Cepat."
            emitToast("Navigasi: Hitung Cepat")
            return true
        }
        if (cmd.contains("pengaturan") || cmd.contains("setting")) {
            _aiNavigationTrigger.emit("SETTINGS")
            currentScreen.value = "SETTINGS"
            _aiLastResponse.value = "Membuka Pengaturan Toko."
            emitToast("Navigasi: Pengaturan")
            return true
        }

        val isModifyingMenu = cmd.contains("tambah") || cmd.contains("buat") || cmd.contains("ubah") || cmd.contains("hapus") || cmd.contains("edit") || cmd.contains("ganti")
        if (!isModifyingMenu && (cmd.contains("kelola menu") || cmd.contains("daftar menu") || cmd.contains("buka menu") || cmd.contains("masuk menu") || cmd.contains("layar menu") || cmd.contains("halaman menu") || cmd == "menu")) {
            _aiNavigationTrigger.emit("MENU")
            currentScreen.value = "MENU"
            _aiLastResponse.value = "Membuka halaman Kelola Menu & Stok."
            emitToast("Navigasi: Kelola Menu")
            return true
        }

        // 9. Export Report (PDF / Excel / CSV)
        if (cmd.contains("pdf") || (cmd.contains("laporan") && cmd.contains("cetak"))) {
            exportReportPdf("Penjualan Hari Ini", allTransactions.value)
            _aiLastResponse.value = "Mengekspor laporan penjualan ke format PDF..."
            return true
        }
        if (cmd.contains("excel") || cmd.contains("xlsx") || cmd.contains("xls")) {
            exportReportExcel("Penjualan Hari Ini", allTransactions.value)
            _aiLastResponse.value = "Mengekspor laporan penjualan ke format Excel (.xlsx)..."
            return true
        }
        if (cmd.contains("csv") || cmd.contains("sheet")) {
            exportReportCsv("Penjualan Hari Ini", allTransactions.value)
            _aiLastResponse.value = "Mengekspor laporan penjualan ke format CSV..."
            return true
        }

        // 10. Add Category
        if (cmd.contains("tambah kategori") || cmd.contains("buat kategori")) {
            val catName = cmd
                .replace("tambah kategori", "")
                .replace("buat kategori", "")
                .replace("kategori baru", "")
                .trim()
                .replaceFirstChar { it.uppercase() }

            if (catName.isNotBlank()) {
                saveCategory(0L, catName, "Category", "#3B82F6")
                _aiLastResponse.value = "Berhasil membuat kategori baru '$catName'."
                return true
            }
        }

        // 11. Clear Cart
        if (cmd.contains("kosongkan keranjang") || cmd.contains("hapus keranjang") || cmd.contains("reset keranjang") || cmd.contains("bersihkan keranjang")) {
            clearCart()
            _aiLastResponse.value = "Keranjang belanja berhasil dikosongkan."
            emitToast("Keranjang dikosongkan")
            return true
        }

        // 12. Checkout / Payment ("bayar cash 50rb", "bayar tunai 100000", "bayar cash pas", "bayar cash")
        if (cmd.contains("bayar") || cmd.contains("checkout") || cmd.contains("selesai") || cmd.contains("cash") || cmd.contains("tunai") || cmd.contains("qris") || cmd.contains("kris")) {
            val isQris = cmd.contains("qris") || cmd.contains("kris")
            val method = if (isQris) "QRIS" else "TUNAI"

            if (cart.value.isEmpty()) {
                _aiLastResponse.value = "Keranjang belanja masih kosong, tidak ada item untuk dibayar."
                emitToast("Keranjang kosong!")
                return true
            }

            val total = cartTotal.value
            val (extractedAmount, _) = extractIndonesianPrice(cmd)

            val cashReceived = if (method == "TUNAI") {
                if (cmd.contains("pas") || cmd.contains("uang pas")) {
                    total
                } else if (extractedAmount >= total) {
                    extractedAmount
                } else if (extractedAmount in 1.0..<total) {
                    _aiLastResponse.value = "Uang tunai ${CurrencyUtils.formatRupiah(extractedAmount)} kurang dari total tagihan ${CurrencyUtils.formatRupiah(total)}."
                    emitToast("Uang tunai kurang!")
                    return true
                } else {
                    total // Default to exact amount if no explicit nominal stated
                }
            } else {
                total
            }

            val change = if (method == "TUNAI") (cashReceived - total).coerceAtLeast(0.0) else 0.0

            // Direct checkout process: clears cart, saves transaction to DB/Report, skips receipt popup, triggers 10s change popup
            processCheckout(
                paymentMethod = method,
                amountReceived = cashReceived,
                customerName = "Pelanggan AI",
                note = "Transaksi Suara AI",
                showReceiptDialog = false,
                onSuccess = { createdTx ->
                    payCash(cashReceived, total)
                    
                    val formattedTotal = CurrencyUtils.formatRupiah(total)
                    val formattedCash = CurrencyUtils.formatRupiah(cashReceived)
                    val formattedChange = CurrencyUtils.formatRupiah(change)

                    _changePopupData.value = ChangePopupData(
                        totalAmount = total,
                        amountReceived = cashReceived,
                        changeAmount = change,
                        invoiceNumber = createdTx.transaction.invoiceNumber
                    )

                    // Auto-hide change popup after 10 seconds
                    viewModelScope.launch {
                        delay(10000)
                        if (_changePopupData.value?.invoiceNumber == createdTx.transaction.invoiceNumber) {
                            _changePopupData.value = null
                        }
                    }

                    val responseMsg = if (change > 0) {
                        "Pembayaran $method $formattedTotal Selesai! Tunai: $formattedCash, Kembalian: $formattedChange."
                    } else {
                        "Pembayaran $method $formattedTotal Selesai dengan Uang Pas!"
                    }
                    _aiLastResponse.value = responseMsg
                    emitToast("Bayar Selesai: Kembalian $formattedChange")
                }
            )
            return true
        }

        // 13. Add Product to Cart (e.g. "tambah ayam geprek 5 ayam goreng 5 esteh 5", "pesan 2 es teh")
        val isAddToCart = cmd.contains("tambah") || cmd.contains("tambahkan") || cmd.contains("pesan") || cmd.contains("beli") || cmd.contains("masukkan") || currentScreen.value == "KASIR"
        val isNotMenuAction = !cmd.contains("stok") && !cmd.contains("kategori") && !cmd.contains("toko") && !cmd.contains("alamat") && !cmd.contains("hp") && !cmd.contains("harga") && !cmd.contains("modal") && !cmd.contains("hapus") && !cmd.contains("kurang")

        if (isAddToCart && isNotMenuAction) {
            val lowerCmd = cmd
            val occurrences = mutableListOf<Triple<Int, Product, Int>>()

            for (prod in products) {
                val pName = prod.name.lowercase()
                val idx = lowerCmd.indexOf(pName)
                if (idx >= 0) {
                    val afterText = lowerCmd.substring(idx + pName.length).trim()
                    val qtyMatch = Regex("""^\s*(\d+)""").find(afterText)
                    var qty = qtyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                    if (qtyMatch == null) {
                        val wordMap = mapOf("satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5)
                        for ((w, n) in wordMap) {
                            if (afterText.startsWith(w) || afterText.startsWith(" $w")) {
                                qty = n
                                break
                            }
                        }
                    }

                    occurrences.add(Triple(idx, prod, qty))
                }
            }

            occurrences.sortBy { it.first }

            val nonOverlapping = mutableListOf<Triple<Int, Product, Int>>()
            for (occ in occurrences) {
                val overlaps = nonOverlapping.any { Math.abs(it.first - occ.first) < 3 }
                if (!overlaps) {
                    nonOverlapping.add(occ)
                }
            }

            if (nonOverlapping.size >= 2) {
                val addedSummaries = mutableListOf<String>()
                for ((_, prod, qty) in nonOverlapping) {
                    repeat(qty) { addToCart(prod) }
                    addedSummaries.add("$qty ${prod.name}")
                }

                val combinedText = addedSummaries.joinToString(", ")
                val msg = "Menambahkan $combinedText ke keranjang."
                _aiLastResponse.value = msg
                emitToast(msg)
                return true
            }
        }

        var matchedProduct: Product? = null
        var matchedQty = 1

        val qtyMatch = Regex("""(\d+)""").find(cmd)
        if (qtyMatch != null) {
            matchedQty = qtyMatch.value.toIntOrNull() ?: 1
        } else {
            val wordMap = mapOf("satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5)
            for ((w, n) in wordMap) {
                if (cmd.contains(w)) {
                    matchedQty = n
                    break
                }
            }
        }

        var cleanCmd = cmd
            .replace(Regex("""\d+"""), "")
            .replace("tambah", "")
            .replace("tambahkan", "")
            .replace("pesan", "")
            .replace("beli", "")
            .replace("masukkan", "")
            .replace("keranjang", "")
            .replace("porsi", "")
            .replace("buah", "")
            .replace("pcs", "")
            .replace("satu", "")
            .replace("dua", "")
            .replace("tiga", "")
            .replace("empat", "")
            .replace("lima", "")
            .trim()

        if (cleanCmd.length >= 2) {
            matchedProduct = products.find { prod ->
                val pName = prod.name.lowercase()
                pName.contains(cleanCmd) || cleanCmd.contains(pName)
            }
        }

        if (matchedProduct == null) {
            matchedProduct = products.find { prod -> cmd.contains(prod.name.lowercase()) }
        }

        if (matchedProduct != null) {
            repeat(matchedQty) { addToCart(matchedProduct) }
            val msg = "Menambahkan $matchedQty ${matchedProduct.name} ke keranjang."
            _aiLastResponse.value = msg
            emitToast(msg)
            return true
        }

        return false
    }

    private suspend fun executeAtomicVoiceCommand(cmd: AtomicVoiceCommand): String? {
        return when (cmd) {
            is AtomicVoiceCommand.UpdatePrice -> {
                val newSell = cmd.sellingPrice ?: cmd.product.price
                val newCost = cmd.costPrice ?: cmd.product.costPrice
                val updated = cmd.product.copy(price = newSell, costPrice = newCost, updatedAt = System.currentTimeMillis())
                repository.updateProduct(updated)
                val formattedSell = CurrencyUtils.formatRupiah(newSell)
                val formattedCost = CurrencyUtils.formatRupiah(newCost)
                when {
                    cmd.sellingPrice != null && cmd.costPrice != null ->
                        "Mengubah Harga Jual ${cmd.product.name} menjadi $formattedSell & Modal menjadi $formattedCost"
                    cmd.costPrice != null ->
                        "Mengubah Harga Modal ${cmd.product.name} menjadi $formattedCost"
                    else ->
                        "Mengubah Harga Jual ${cmd.product.name} menjadi $formattedSell"
                }
            }

            is AtomicVoiceCommand.AddToCart -> {
                if (cmd.product.stock <= 0) {
                    "Stok ${cmd.product.name} habis, tidak dapat ditambahkan"
                } else {
                    addToCart(cmd.product, cmd.quantity)
                    "Menambahkan ${cmd.quantity}x ${cmd.product.name} ke keranjang"
                }
            }

            is AtomicVoiceCommand.ReduceFromCart -> {
                val currentCart = _cart.value.toMutableMap()
                val existing = currentCart[cmd.product.id]
                if (existing == null) {
                    "${cmd.product.name} tidak ada di keranjang"
                } else if (cmd.isExplicitRemove || existing.quantity <= cmd.quantity) {
                    currentCart.remove(cmd.product.id)
                    _cart.value = currentCart
                    "Menghapus ${cmd.product.name} dari keranjang"
                } else {
                    currentCart[cmd.product.id] = existing.copy(quantity = existing.quantity - cmd.quantity)
                    _cart.value = currentCart
                    "Mengurangi ${cmd.quantity}x ${cmd.product.name} dari keranjang"
                }
            }

            is AtomicVoiceCommand.UpdateStock -> {
                val finalStock = if (cmd.isRelative) (cmd.product.stock + cmd.newStock).coerceAtLeast(0) else cmd.newStock.coerceAtLeast(0)
                adjustStock(cmd.product.id, finalStock)
                "Mengubah Stok ${cmd.product.name} menjadi $finalStock"
            }

            is AtomicVoiceCommand.ClearAllStocks -> {
                clearAllProductStocks()
                "Mengosongkan semua stok produk ke 0"
            }

            is AtomicVoiceCommand.RenameProduct -> {
                val updated = cmd.product.copy(name = cmd.newName, updatedAt = System.currentTimeMillis())
                repository.updateProduct(updated)
                "Mengubah nama '${cmd.product.name}' menjadi '${cmd.newName}'"
            }

            is AtomicVoiceCommand.ChangeCategory -> {
                val category = ensureCategory(cmd.targetCategoryName)
                val updated = cmd.product.copy(categoryId = category.id, categoryName = category.name, updatedAt = System.currentTimeMillis())
                repository.updateProduct(updated)
                "Mengubah kategori '${cmd.product.name}' ke '${category.name}'"
            }

            is AtomicVoiceCommand.AddMenuProduct -> {
                val category = ensureCategory(cmd.categoryName)
                val newProduct = Product(
                    id = 0L,
                    name = cmd.name,
                    categoryId = category.id,
                    categoryName = category.name,
                    price = cmd.price,
                    costPrice = cmd.costPrice ?: 0.0,
                    stock = cmd.stock,
                    unit = "porsi"
                )
                repository.insertProduct(newProduct)
                "Menambahkan menu '${cmd.name}' (${CurrencyUtils.formatRupiah(cmd.price)}) ke kategori '${category.name}'"
            }

            is AtomicVoiceCommand.DeleteProduct -> {
                repository.deleteProduct(cmd.product)
                "Menghapus menu '${cmd.product.name}'"
            }

            is AtomicVoiceCommand.AddCategory -> {
                val cat = ensureCategory(cmd.categoryName)
                "Menambahkan kategori '${cat.name}'"
            }

            is AtomicVoiceCommand.DeleteCategory -> {
                val cat = allCategories.value.find { it.name.contains(cmd.categoryName, ignoreCase = true) || cmd.categoryName.contains(it.name, ignoreCase = true) }
                if (cat != null) {
                    deleteCategory(cat)
                    "Menghapus kategori '${cat.name}'"
                } else {
                    "Kategori '${cmd.categoryName}' tidak ditemukan"
                }
            }

            is AtomicVoiceCommand.DeleteTransaction -> {
                val txs = allTransactions.value
                val txToDelete = if (cmd.isLast || cmd.nominal == null) {
                    txs.maxByOrNull { it.transaction.timestamp }
                } else {
                    txs.find { Math.abs(it.transaction.totalAmount - cmd.nominal) < 1.0 }
                }
                if (txToDelete != null) {
                    deleteTransaction(txToDelete)
                    "Menghapus transaksi #${txToDelete.transaction.invoiceNumber} (${CurrencyUtils.formatRupiah(txToDelete.transaction.totalAmount)})"
                } else {
                    "Transaksi tidak ditemukan untuk dibatalkan"
                }
            }

            is AtomicVoiceCommand.ProcessPayment -> {
                val total = cartTotal.value
                if (_cart.value.isEmpty() || total <= 0) {
                    "Keranjang belanja masih kosong, tidak ada transaksi untuk dibayar"
                } else {
                    if (cmd.method == "QRIS") {
                        _qrisPaymentTrigger.emit(true)
                        "Membuka QRIS Toko untuk pembayaran sebesar ${CurrencyUtils.formatRupiah(total)}"
                    } else {
                        val cashReceived = cmd.amount ?: total
                        if (cashReceived < total) {
                            "Uang tunai ${CurrencyUtils.formatRupiah(cashReceived)} kurang dari total tagihan ${CurrencyUtils.formatRupiah(total)}"
                        } else {
                            val change = (cashReceived - total).coerceAtLeast(0.0)
                            processCheckout(
                                paymentMethod = "TUNAI",
                                amountReceived = cashReceived,
                                customerName = "Pelanggan AI",
                                note = "Transaksi Suara AI",
                                showReceiptDialog = false,
                                onSuccess = { createdTx ->
                                    payCash(cashReceived, total)
                                    _changePopupData.value = ChangePopupData(
                                        totalAmount = total,
                                        amountReceived = cashReceived,
                                        changeAmount = change,
                                        invoiceNumber = createdTx.transaction.invoiceNumber
                                    )
                                    viewModelScope.launch {
                                        delay(10000)
                                        if (_changePopupData.value?.invoiceNumber == createdTx.transaction.invoiceNumber) {
                                            _changePopupData.value = null
                                            finishPayment()
                                        }
                                    }
                                }
                            )
                            if (change > 0) {
                                "Pembayaran TUNAI berhasil diproses."
                            } else {
                                "Pembayaran TUNAI berhasil diproses dengan Uang Pas."
                            }
                        }
                    }
                }
            }

            is AtomicVoiceCommand.MasterReset -> {
                requestMasterReset()
                "Membuka dialog konfirmasi reset seluruh data aplikasi..."
            }

            is AtomicVoiceCommand.Navigate -> {
                if (cmd.dateCalendar != null) {
                    selectedDailyTimestamp.value = cmd.dateCalendar.timeInMillis
                    selectedReportPeriod.value = ReportPeriodType.HARIAN
                }
                _aiNavigationTrigger.emit(cmd.targetScreen)
                currentScreen.value = cmd.targetScreen
                "Membuka layar ${cmd.targetScreen.lowercase().replaceFirstChar { it.uppercase() }}"
            }

            is AtomicVoiceCommand.UpdateStoreConfig -> {
                val currentConfig = storeConfig.value ?: StoreConfig()
                val updated = when (cmd.field) {
                    "NAME" -> currentConfig.copy(storeName = cmd.value, qrisMerchantName = cmd.value)
                    "ADDRESS" -> currentConfig.copy(storeAddress = cmd.value)
                    "PHONE" -> currentConfig.copy(storePhone = cmd.value)
                    else -> currentConfig.copy(receiptFooter = cmd.value)
                }
                viewModelScope.launch { repository.updateStoreConfig(updated) }
                "Pengaturan toko (${cmd.field.lowercase()}) berhasil diperbarui"
            }

            is AtomicVoiceCommand.ChangeTheme -> {
                val mode = when (cmd.themeMode) {
                    "SAGE" -> "SAGE"
                    "DARK" -> "DARK"
                    "LIGHT" -> "LIGHT"
                    else -> {
                        val curr = storeConfig.value?.themeMode ?: "DARK"
                        when (curr) {
                            "DARK" -> "SAGE"
                            "SAGE" -> "LIGHT"
                            else -> "DARK"
                        }
                    }
                }
                updateThemeMode(mode)
                "Tema aplikasi diubah ke mode $mode"
            }

            is AtomicVoiceCommand.ExportReport -> {
                when (cmd.format) {
                    "PDF" -> {
                        exportReportPdf("Laporan_Kasir", allTransactions.value)
                        "Mengekspor laporan ke format PDF"
                    }
                    "EXCEL" -> {
                        exportReportExcel("Laporan_Kasir", allTransactions.value)
                        "Mengekspor laporan ke format Excel (.xlsx)"
                    }
                    else -> {
                        exportReportCsv("Laporan_Kasir", allTransactions.value)
                        "Mengekspor laporan ke format CSV"
                    }
                }
            }

            is AtomicVoiceCommand.ClearCart -> {
                clearCart()
                "Keranjang belanja telah dikosongkan"
            }
        }
    }

    private suspend fun processAiCommand(command: String) {
        _isAiThinking.value = true
        _aiLastResponse.value = "Memproses: \"$command\"..."

        // 1. Try Pattern-Matching VoiceCommandEngine (supports multi-command utterances)
        val atomicCommands = VoiceCommandEngine.parseToAtomicCommands(
            rawInput = command,
            currentScreen = currentScreen.value,
            products = allProducts.value,
            categories = allCategories.value
        )

        if (atomicCommands.isNotEmpty()) {
            val results = mutableListOf<String>()
            for (atomic in atomicCommands) {
                val res = executeAtomicVoiceCommand(atomic)
                if (res != null) {
                    results.add(res)
                }
            }

            if (results.isNotEmpty()) {
                if (results.size == 1) {
                    _aiLastResponse.value = results.first()
                    emitToast(results.first())
                } else {
                    val formattedSummary = buildString {
                        append("Berhasil ${results.size} perintah:\n")
                        results.forEachIndexed { index, msg ->
                            append("Sub-perintah ${index + 1}: $msg\n")
                        }
                    }.trimEnd()
                    _aiLastResponse.value = formattedSummary
                    emitToast("Berhasil ${results.size} perintah!")
                }
                _isAiThinking.value = false
                return
            }
        }

        // 2. Fallback to compound / single command parser
        val subCommands = splitCompoundCommand(command)

        if (subCommands.size > 1) {
            val executedMessages = mutableListOf<String>()
            var successCount = 0

            for (subCmd in subCommands) {
                val ok = parseLocalCommand(subCmd)
                if (ok) {
                    successCount++
                    val resp = _aiLastResponse.value
                    if (!resp.isNullOrBlank()) {
                        executedMessages.add(resp)
                    }
                }
            }

            if (successCount > 0) {
                val combined = executedMessages.distinct().joinToString("\n• ")
                val summary = "Berhasil $successCount perintah:\n• $combined"
                _aiLastResponse.value = summary
                emitToast("Berhasil $successCount perintah!")
                _isAiThinking.value = false
                return
            }
        }

        // Single command local fast execution
        if (parseLocalCommand(command)) {
            _isAiThinking.value = false
            return
        }

        val products = allProducts.value
        val productListInfo = products.joinToString("\n") { 
            "- ID: ${it.id}, Nama: ${it.name}, Harga: ${it.price}, Stok: ${it.stock}" 
        }

        val systemPrompt = """
            Anda adalah asisten kasir pintar untuk aplikasi 'Kasir Pro'.
            Tugas Anda adalah menafsirkan perintah suara pengguna dan mengubahnya menjadi tindakan terstruktur (JSON).
            
            Daftar produk yang tersedia saat ini:
            $productListInfo
            
            Anda HARUS merespons dalam format JSON sesuai skema yang diminta.
            
            Aturan Pemisahan Kategori Perintah (PENTING):
            - PERINTAH NAVIGASI TAMPILAN ('NAVIGATE'):
              Gunakan HANYA ketika pengguna meminta membuka/pindah ke layar tertentu. Contoh: "buka menu", "lihat laporan", "layar kasir", "buka pengaturan".
              Field 'target' diisi: 'KASIR', 'DASHBOARD', 'LAPORAN', 'SETTINGS', 'MENU', atau 'HITUNG'.
            
            - PERINTAH MANIPULASI DATA (JANGAN GUNAKAN 'NAVIGATE' UNTUK MANIPULASI DATA):
              * 'ADD_PRODUCT': Membuat menu baru. Contoh: "tambah menu ayam bakar 25 ribu".
              * 'UPDATE_PRODUCT_PRICE': Mengubah harga produk. Contoh: "ubah harga ayam goreng jadi 20 ribu".
              * 'UPDATE_STOCK': Mengubah stok. Contoh: "ubah stok nasi goreng 50".
              * 'DELETE_PRODUCT': Menghapus menu. Contoh: "hapus menu es teh".
              * 'CLEAR_ALL_STOCKS': Kosongkan semua stok. Contoh: "hapus semua stok menu".
              * 'DELETE_ALL_PRODUCTS': Hapus seluruh daftar menu. Contoh: "hapus semua menu".
              * 'ADD_TO_CART': Menambah transaksi keranjang. Contoh: "pesan 2 nasi goreng".
              * 'CLEAR_CART': Kosongkan keranjang belanja.
              * 'CHECKOUT': Membayar pesanan.
              * 'CHANGE_THEME': Ganti tema aplikasi. 'target' diisi 'DARK' atau 'LIGHT'.
            
            Berikan pesan balasan singkat dalam bahasa Indonesia di field 'message'.
        """.trimIndent()

        val responseSchema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("action") { 
                    put("type", "STRING") 
                    put("enum", buildJsonObject {
                        putJsonArray("enum") { 
                            add("NAVIGATE")
                            add("ADD_PRODUCT")
                            add("UPDATE_PRODUCT_PRICE")
                            add("UPDATE_STOCK")
                            add("DELETE_PRODUCT")
                            add("CLEAR_ALL_STOCKS")
                            add("DELETE_ALL_PRODUCTS")
                            add("ADD_TO_CART")
                            add("CLEAR_CART")
                            add("CHECKOUT")
                            add("CHANGE_THEME")
                            add("UNKNOWN") 
                        }
                    })
                }
                putJsonObject("productId") { put("type", "NUMBER") }
                putJsonObject("quantity") { put("type", "NUMBER") }
                putJsonObject("price") { put("type", "NUMBER") }
                putJsonObject("productName") { put("type", "STRING") }
                putJsonObject("target") { put("type", "STRING") }
                putJsonObject("paymentMethod") { put("type", "STRING") }
                putJsonObject("amountReceived") { put("type", "NUMBER") }
                putJsonObject("message") { put("type", "STRING") }
            }
            putJsonArray("required") { add("action"); add("message") }
        }

        val resultJson = GeminiClient.callGeminiJson(systemPrompt, command, responseSchema)
        
        _isAiThinking.value = false
        if (resultJson != null) {
            try {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(resultJson).let { 
                    if (it is kotlinx.serialization.json.JsonObject) it else null
                }
                
                if (json != null) {
                    val action = json["action"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                    val message = json["message"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null } ?: "Tugas selesai"
                    
                    _aiLastResponse.value = message
                    
                    when (action) {
                        "NAVIGATE" -> {
                            val target = json["target"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                            if (target != null) {
                                _aiNavigationTrigger.emit(target)
                            }
                        }
                        "ADD_PRODUCT" -> {
                            val pName = json["productName"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                            val price = json["price"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toDoubleOrNull() else null } ?: 10000.0
                            if (!pName.isNullOrBlank()) {
                                val defaultCat = allCategories.value.firstOrNull()
                                saveProduct(
                                    id = 0L,
                                    name = pName,
                                    categoryId = defaultCat?.id ?: 1L,
                                    categoryName = defaultCat?.name ?: "Makanan",
                                    price = price,
                                    costPrice = price * 0.7,
                                    stock = 20,
                                    minStockAlert = 5,
                                    unit = "Porsi"
                                )
                            }
                            emitToast(message)
                        }
                        "UPDATE_PRODUCT_PRICE" -> {
                            val pId = json["productId"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLongOrNull() else null }
                            val pName = json["productName"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
                            val price = json["price"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toDoubleOrNull() else null }
                            val prod = products.find { it.id == pId } ?: products.find { pName != null && it.name.contains(pName, ignoreCase = true) }
                            if (prod != null && price != null) {
                                saveProduct(
                                    id = prod.id,
                                    name = prod.name,
                                    categoryId = prod.categoryId,
                                    categoryName = prod.categoryName,
                                    price = price,
                                    costPrice = prod.costPrice,
                                    stock = prod.stock,
                                    minStockAlert = prod.minStockAlert,
                                    unit = prod.unit
                                )
                            }
                            emitToast(message)
                        }
                        "UPDATE_STOCK" -> {
                            val pId = json["productId"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLongOrNull() else null }
                            val qty = json["quantity"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toIntOrNull() else null } ?: 0
                            if (pId != null) {
                                adjustStock(pId, qty)
                            }
                            emitToast(message)
                        }
                        "DELETE_PRODUCT" -> {
                            val pId = json["productId"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLongOrNull() else null }
                            val prod = products.find { it.id == pId }
                            if (prod != null) {
                                deleteProduct(prod)
                            }
                            emitToast(message)
                        }
                        "ADD_TO_CART" -> {
                            val pId = json["productId"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLongOrNull() else null }
                            val qty = json["quantity"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toIntOrNull() else null } ?: 1
                            val product = products.find { it.id == pId }
                            if (product != null) {
                                repeat(qty) { addToCart(product) }
                                emitToast(message)
                            }
                        }
                        "CLEAR_CART" -> {
                            clearCart()
                            emitToast(message)
                        }
                        "CHECKOUT" -> {
                            val method = json["paymentMethod"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null } ?: "TUNAI"
                            val amount = json["amountReceived"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toDoubleOrNull() else null } ?: 0.0
                            _aiCheckoutTrigger.emit(Pair(method, amount))
                            emitToast(message)
                        }
                        "CLEAR_ALL_STOCKS" -> {
                            clearAllProductStocks()
                            emitToast(message)
                        }
                        "DELETE_ALL_PRODUCTS" -> {
                            deleteAllProducts()
                            emitToast(message)
                        }
                        "CHANGE_THEME" -> {
                            val target = json["target"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null } ?: "DARK"
                            updateThemeMode(target)
                            emitToast(message)
                        }
                        "UNKNOWN" -> emitToast(message)
                    }
                }
            } catch (e: Exception) {
                _aiLastResponse.value = "Gagal memproses perintah AI."
            }
        } else {
            _aiLastResponse.value = "Perintah tidak dikenali atau koneksi AI belum terhubung."
        }
    }

    fun clearAiResponse() {
        _aiLastResponse.value = null
    }

    fun saveQrisImageUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val qrisFile = File(context.filesDir, "qris_merchant_code.png")
                    val outputStream = FileOutputStream(qrisFile)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    val current = storeConfig.value ?: StoreConfig()
                    val bytes = qrisFile.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val updated = current.copy(
                        qrisImagePath = qrisFile.absolutePath,
                        qrisBase64 = base64
                    )
                    repository.updateStoreConfig(updated)
                    emitToast("Foto QRIS Toko berhasil disimpan!")
                } else {
                    emitToast("Gagal membaca file foto QRIS.")
                }
            } catch (e: Exception) {
                emitToast("Gagal menyimpan foto: ${e.localizedMessage}")
            }
        }
    }

    fun removeQrisImage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val qrisFile = File(context.filesDir, "qris_merchant_code.png")
                if (qrisFile.exists()) {
                    qrisFile.delete()
                }
                val current = storeConfig.value ?: StoreConfig()
                val updated = current.copy(qrisImagePath = "")
                repository.updateStoreConfig(updated)
                emitToast("Foto QRIS dihapus.")
            } catch (e: Exception) {
                emitToast("Gagal menghapus foto: ${e.localizedMessage}")
            }
        }
    }

    fun loadPairedBluetoothDevices() {
        _pairedDevices.value = printerManager.getPairedDevices()
    }

    fun testPrinterConnection() {
        val config = storeConfig.value ?: return
        if (config.printerMacAddress.isBlank()) {
            emitToast("Pilih printer Bluetooth terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            val result = printerManager.testPrint(config)
            result.onSuccess { emitToast(it) }
                .onFailure { emitToast(it.message ?: "Gagal test print") }
        }
    }

    // Reports export
    fun exportReportCsv(reportTitle: String, transactions: List<TransactionWithItems>) {
        val config = storeConfig.value ?: return
        val productsSnapshot = allProducts.value
        viewModelScope.launch {
            val uri = ReportExporter.exportToCsv(getApplication(), reportTitle, config, transactions, productsSnapshot)
            if (uri != null) {
                ReportExporter.shareExportedFile(getApplication(), uri, ReportExporter.MIME_CSV, "Laporan $reportTitle")
            } else {
                emitToast("Gagal mengekspor laporan CSV.")
            }
        }
    }

    fun exportReportExcel(reportTitle: String, transactions: List<TransactionWithItems>) {
        val config = storeConfig.value ?: return
        val productsSnapshot = allProducts.value
        viewModelScope.launch {
            val uri = ReportExporter.exportToExcel(getApplication(), reportTitle, config, transactions, productsSnapshot)
            if (uri != null) {
                ReportExporter.shareExportedFile(getApplication(), uri, ReportExporter.MIME_EXCEL, "Laporan $reportTitle (Excel)")
            } else {
                emitToast("Gagal mengekspor laporan Excel (.xlsx).")
            }
        }
    }

    fun exportReportPdf(reportTitle: String, transactions: List<TransactionWithItems>) {
        val config = storeConfig.value ?: return
        viewModelScope.launch {
            val uri = ReportExporter.exportToPdf(getApplication(), reportTitle, config, transactions)
            if (uri != null) {
                ReportExporter.shareExportedFile(getApplication(), uri, ReportExporter.MIME_PDF, "Laporan $reportTitle")
            } else {
                emitToast("Gagal mengekspor laporan PDF.")
            }
        }
    }

    fun emitToast(message: String) {
        viewModelScope.launch {
            _toastEvent.emit(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.shutdown()
    }

    // AI voice triggers
    private fun speak(text: String) {
        ttsEngine.speak(text)
    }

    fun openKasir() {
        _aiNavigationTrigger.tryEmit("KASIR")
        speak("Membuka kasir")
    }

    fun payCash(amount: Double, total: Double) {
        val change = (amount - total).coerceAtLeast(0.0)
        speak("Uang cash ${CurrencyUtils.formatRupiah(amount)}, kembalian ${CurrencyUtils.formatRupiah(change)}")
    }

    fun finishPayment() {
        speak("Pembayaran berhasil")
    }
}
