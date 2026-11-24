package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.Transaction
import com.example.data.model.TransactionItem
import com.example.data.model.StoreConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private const val CUSTOM_CLOUD_APP_NAME = "kasir_pro_cloud"

    private var isInitialized = false
    private var activeApp: FirebaseApp? = null
    private var activeSignature: String = ""
    private var activeProjectId: String = ""
    private var activeClientPackageName: String = ""
    private var configuredSettingsSignature: String = ""

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerSignature: String = ""
    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var registeredConnectivityManager: ConnectivityManager? = null

    private var storePendingWrites = false
    private var catPendingWrites = false
    private var prodPendingWrites = false
    private var txPendingWrites = false
    private var isOnlineFlag = true

    enum class CloudConnectionMode {
        NOT_CONFIGURED,
        ONLINE_SYNCED,
        UPLOADING_QUEUE,
        OFFLINE_QUEUED,
        ERROR
    }

    data class CloudSyncStatus(
        val mode: CloudConnectionMode = CloudConnectionMode.NOT_CONFIGURED,
        val isNetworkOnline: Boolean = true,
        val hasPendingWrites: Boolean = false,
        val projectId: String = "",
        val detail: String = ""
    )

    private val _cloudSyncState = MutableStateFlow(CloudSyncStatus())
    val cloudSyncState: StateFlow<CloudSyncStatus> = _cloudSyncState.asStateFlow()

    data class InitResult(
        val success: Boolean,
        val projectId: String = "",
        val clientPackageName: String = "",
        val message: String = ""
    )

    data class SyncResult(
        val success: Boolean,
        val message: String
    )

    fun initialize(context: Context, jsonStr: String): Boolean =
        initializeDetailed(context, jsonStr).success

    @Synchronized
    fun initializeDetailed(context: Context, jsonStr: String): InitResult {
        val cleanedJson = jsonStr.trim().removePrefix("\uFEFF")
        if (cleanedJson.isBlank()) {
            return InitResult(success = false, message = "Isi google-services.json masih kosong.")
        }
        return try {
            val root = JSONObject(cleanedJson)
            val projectInfo = root.optJSONObject("project_info")
                ?: return InitResult(success = false, message = "Format JSON tidak valid: 'project_info' tidak ditemukan.")
            val projectId = projectInfo.optString("project_id", "").trim()
            if (projectId.isBlank()) {
                return InitResult(success = false, message = "Format JSON tidak valid: 'project_id' kosong.")
            }
            val storageBucket = projectInfo.optString("storage_bucket", "").trim()

            val clients = root.optJSONArray("client")
            if (clients == null || clients.length() == 0) {
                return InitResult(success = false, message = "Format JSON tidak valid: array 'client' kosong.")
            }

            // Prefer client matching current packageName, otherwise fall back to client[0]
            val appPkg = context.packageName
            var selectedClient = clients.getJSONObject(0)
            for (i in 0 until clients.length()) {
                val candidate = clients.optJSONObject(i) ?: continue
                val candidatePkg = candidate.optJSONObject("client_info")
                    ?.optJSONObject("android_client_info")
                    ?.optString("package_name", "")
                    .orEmpty()
                if (candidatePkg == appPkg) {
                    selectedClient = candidate
                    break
                }
            }

            val clientInfo = selectedClient.optJSONObject("client_info")
                ?: return InitResult(success = false, message = "Format JSON tidak valid: 'client_info' tidak ditemukan.")
            val appId = clientInfo.optString("mobilesdk_app_id", "").trim()
            if (appId.isBlank()) {
                return InitResult(success = false, message = "Format JSON tidak valid: 'mobilesdk_app_id' kosong.")
            }
            val clientPkg = clientInfo.optJSONObject("android_client_info")
                ?.optString("package_name", "")
                ?.trim()
                .orEmpty()

            val apiKeys = selectedClient.optJSONArray("api_key")
            if (apiKeys == null || apiKeys.length() == 0) {
                return InitResult(success = false, message = "Format JSON tidak valid: 'api_key' kosong.")
            }
            val apiKey = apiKeys.getJSONObject(0).optString("current_key", "").trim()
            if (apiKey.isBlank()) {
                return InitResult(success = false, message = "Format JSON tidak valid: 'current_key' kosong.")
            }

            val signature = "$projectId|$appId|$apiKey"
            val existingNamed = FirebaseApp.getApps(context).firstOrNull { it.name == CUSTOM_CLOUD_APP_NAME }
            if (isInitialized && signature == activeSignature && existingNamed != null) {
                activeApp = existingNamed
                return InitResult(
                    success = true,
                    projectId = activeProjectId,
                    clientPackageName = activeClientPackageName,
                    message = "Firebase terhubung ke project $projectId."
                )
            }

            if (existingNamed != null) {
                stopRealtimeSync()
                try {
                    existingNamed.delete()
                } catch (delErr: Exception) {
                    Log.w(TAG, "Failed deleting previous custom FirebaseApp: ${delErr.localizedMessage}")
                }
            }

            val optionsBuilder = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
            if (storageBucket.isNotBlank()) {
                optionsBuilder.setStorageBucket(storageBucket)
            }

            val appInstance = FirebaseApp.initializeApp(
                context.applicationContext,
                optionsBuilder.build(),
                CUSTOM_CLOUD_APP_NAME
            )
            activeApp = appInstance
            activeSignature = signature
            activeProjectId = projectId
            activeClientPackageName = clientPkg
            isInitialized = true
            isOnlineFlag = isNetworkAvailable(context)
            updateSyncStateSnapshot()

            // Persist JSON to internal app storage so it survives app lifecycle
            try {
                val persistentFile = java.io.File(context.filesDir, "google-services-pos.json")
                persistentFile.writeText(cleanedJson)
            } catch (err: Exception) {
                Log.e(TAG, "Failed to persist to internal storage: ${err.localizedMessage}")
            }

            InitResult(
                success = true,
                projectId = projectId,
                clientPackageName = clientPkg,
                message = "Firebase terhubung ke project $projectId."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.localizedMessage}")
            isInitialized = false
            activeApp = null
            _cloudSyncState.value = CloudSyncStatus(
                mode = CloudConnectionMode.ERROR,
                isNetworkOnline = isNetworkAvailable(context),
                detail = "Gagal membaca google-services.json: ${e.localizedMessage ?: "Format tidak valid"}"
            )
            InitResult(
                success = false,
                message = "Gagal membaca google-services.json: ${e.localizedMessage ?: "Format tidak valid"}"
            )
        }
    }

    @Synchronized
    fun disconnect(context: Context) {
        stopRealtimeSync()
        try {
            FirebaseApp.getApps(context)
                .firstOrNull { it.name == CUSTOM_CLOUD_APP_NAME }
                ?.delete()
        } catch (_: Exception) {
        }
        try {
            val persistentFile = java.io.File(context.filesDir, "google-services-pos.json")
            if (persistentFile.exists()) persistentFile.delete()
        } catch (_: Exception) {
        }
        activeApp = null
        activeSignature = ""
        activeProjectId = ""
        activeClientPackageName = ""
        configuredSettingsSignature = ""
        isInitialized = false
        storePendingWrites = false
        catPendingWrites = false
        prodPendingWrites = false
        txPendingWrites = false
        _cloudSyncState.value = CloudSyncStatus()
    }

    fun isReady(): Boolean = isInitialized && activeApp != null

    fun getActiveProjectId(): String = activeProjectId

    fun getActiveClientPackageName(): String = activeClientPackageName

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun updateSyncStateSnapshot(errorDetail: String? = null) {
        if (!isReady()) {
            _cloudSyncState.value = CloudSyncStatus()
            return
        }
        val pending = storePendingWrites || catPendingWrites || prodPendingWrites || txPendingWrites
        val mode = when {
            !errorDetail.isNullOrBlank() -> CloudConnectionMode.ERROR
            !isOnlineFlag -> CloudConnectionMode.OFFLINE_QUEUED
            pending -> CloudConnectionMode.UPLOADING_QUEUE
            else -> CloudConnectionMode.ONLINE_SYNCED
        }
        val detail = when (mode) {
            CloudConnectionMode.ERROR -> errorDetail.orEmpty()
            CloudConnectionMode.OFFLINE_QUEUED -> if (pending) {
                "Offline • Data disimpan di antrean lokal, otomatis terupload saat online"
            } else {
                "Offline • Mode cache lokal aktif, otomatis sinkron saat online"
            }
            CloudConnectionMode.UPLOADING_QUEUE -> "Online • Mengunggah antrean data ke Firestore ($activeProjectId)..."
            CloudConnectionMode.ONLINE_SYNCED -> "Online • Tersinkron real-time ($activeProjectId)"
            CloudConnectionMode.NOT_CONFIGURED -> ""
        }
        _cloudSyncState.value = CloudSyncStatus(
            mode = mode,
            isNetworkOnline = isOnlineFlag,
            hasPendingWrites = pending,
            projectId = activeProjectId,
            detail = detail
        )
    }

    fun getFirestore(): FirebaseFirestore? {
        val app = activeApp ?: return null
        if (!isInitialized) return null
        return try {
            val db = FirebaseFirestore.getInstance(app)
            if (configuredSettingsSignature != activeSignature) {
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(
                            PersistentCacheSettings.newBuilder()
                                .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                .build()
                        )
                        .build()
                    db.firestoreSettings = settings
                } catch (_: IllegalStateException) {
                    // Settings already locked after first Firestore operation
                }
                configuredSettingsSignature = activeSignature
            }
            db
        } catch (e: Exception) {
            Log.e(TAG, "Failed getting Firestore for $activeProjectId: ${e.localizedMessage}")
            null
        }
    }

    @Synchronized
    fun startRealtimeSync(
        context: Context,
        repository: com.example.data.repository.KasirRepository
    ) {
        val db = getFirestore() ?: return
        if (listenerSignature == activeSignature && activeListeners.isNotEmpty()) {
            return
        }
        stopRealtimeSync()

        val appContext = context.applicationContext
        listenerSignature = activeSignature
        isOnlineFlag = isNetworkAvailable(appContext)
        updateSyncStateSnapshot()

        // Monitor OS network transitions so queued offline writes flush immediately on reconnect
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        isOnlineFlag = true
                        updateSyncStateSnapshot()
                        syncScope.launch {
                            try {
                                db.enableNetwork().await()
                                db.waitForPendingWrites().await()
                                storePendingWrites = false
                                catPendingWrites = false
                                prodPendingWrites = false
                                txPendingWrites = false
                                updateSyncStateSnapshot()
                            } catch (e: Exception) {
                                Log.w(TAG, "Pending writes flush notice: ${e.localizedMessage}")
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        isOnlineFlag = isNetworkAvailable(appContext)
                        updateSyncStateSnapshot()
                    }
                }
                cm.registerDefaultNetworkCallback(callback)
                networkCallback = callback
                registeredConnectivityManager = cm
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register NetworkCallback: ${e.localizedMessage}")
        }

        // 1. Real-time Store Config listener
        activeListeners += db.collection("store_config")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, err ->
                if (err != null) {
                    updateSyncStateSnapshot(formatFirestoreError(appContext, err))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                storePendingWrites = snapshot.metadata.hasPendingWrites()
                updateSyncStateSnapshot()

                val doc = snapshot.documents.firstOrNull { it.id == "1" }
                    ?: snapshot.documents.firstOrNull()
                if (doc != null && doc.exists()) {
                    syncScope.launch {
                        val localCfg = repository.getStoreConfigSync()
                        val merged = buildMergedStoreConfig(
                            context = appContext,
                            cloudData = doc.data.orEmpty(),
                            localCfg = localCfg
                        )
                        repository.updateStoreConfigLocalOnly(merged)
                    }
                }
            }

        // 2. Real-time Categories listener
        activeListeners += db.collection("categories")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, err ->
                if (err != null) {
                    updateSyncStateSnapshot(formatFirestoreError(appContext, err))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                catPendingWrites = snapshot.metadata.hasPendingWrites()
                updateSyncStateSnapshot()

                val parsed = parseCategories(snapshot.documents)
                val shouldApply = parsed.isNotEmpty() || !snapshot.metadata.isFromCache
                if (shouldApply) {
                    syncScope.launch {
                        repository.replaceCategoriesFromCloud(parsed)
                    }
                }
            }

        // 3. Real-time Products listener
        activeListeners += db.collection("products")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, err ->
                if (err != null) {
                    updateSyncStateSnapshot(formatFirestoreError(appContext, err))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                prodPendingWrites = snapshot.metadata.hasPendingWrites()
                updateSyncStateSnapshot()

                val parsed = parseProducts(snapshot.documents)
                val shouldApply = parsed.isNotEmpty() || !snapshot.metadata.isFromCache
                if (shouldApply) {
                    syncScope.launch {
                        repository.replaceProductsFromCloud(parsed)
                    }
                }
            }

        // 4. Real-time Transactions listener
        activeListeners += db.collection("transactions")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, err ->
                if (err != null) {
                    updateSyncStateSnapshot(formatFirestoreError(appContext, err))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                txPendingWrites = snapshot.metadata.hasPendingWrites()
                updateSyncStateSnapshot()

                val parsed = parseTransactions(snapshot.documents)
                val shouldApply = parsed.isNotEmpty() || !snapshot.metadata.isFromCache
                if (shouldApply) {
                    syncScope.launch {
                        repository.replaceTransactionsFromCloud(parsed)
                    }
                }
            }
    }

    @Synchronized
    fun stopRealtimeSync() {
        activeListeners.forEach {
            try {
                it.remove()
            } catch (_: Exception) {
            }
        }
        activeListeners.clear()
        listenerSignature = ""
        try {
            val cb = networkCallback
            val cm = registeredConnectivityManager
            if (cb != null && cm != null) {
                cm.unregisterNetworkCallback(cb)
            }
        } catch (_: Exception) {
        }
        networkCallback = null
        registeredConnectivityManager = null
    }

    // --- SYNC OPERATIONS ---

    fun syncCategory(category: Category) {
        val db = getFirestore() ?: return
        val data = hashMapOf(
            "id" to category.id,
            "name" to category.name,
            "iconName" to category.iconName,
            "colorHex" to category.colorHex
        )
        db.collection("categories")
            .document(category.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync category: ${it.localizedMessage}") }
    }

    fun deleteCategory(categoryId: Long) {
        val db = getFirestore() ?: return
        db.collection("categories")
            .document(categoryId.toString())
            .delete()
    }

    fun syncProduct(product: Product) {
        val db = getFirestore() ?: return
        val data = hashMapOf(
            "id" to product.id,
            "name" to product.name,
            "categoryId" to product.categoryId,
            "categoryName" to product.categoryName,
            "price" to product.price,
            "costPrice" to product.costPrice,
            "stock" to product.stock,
            "minStockAlert" to product.minStockAlert,
            "unit" to product.unit,
            "barcode" to product.barcode,
            "isAvailable" to product.isAvailable,
            "updatedAt" to product.updatedAt
        )
        db.collection("products")
            .document(product.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync product: ${it.localizedMessage}") }
    }

    fun deleteProduct(productId: Long) {
        val db = getFirestore() ?: return
        db.collection("products")
            .document(productId.toString())
            .delete()
    }

    fun syncTransaction(transaction: Transaction, items: List<TransactionItem>) {
        val db = getFirestore() ?: return
        val itemsData = items.map { item ->
            hashMapOf(
                "productId" to item.productId,
                "productName" to item.productName,
                "categoryName" to item.categoryName,
                "quantity" to item.quantity,
                "pricePerUnit" to item.pricePerUnit,
                "subtotal" to item.subtotal
            )
        }
        val data = hashMapOf(
            "id" to transaction.id,
            "invoiceNumber" to transaction.invoiceNumber,
            "timestamp" to transaction.timestamp,
            "totalAmount" to transaction.totalAmount,
            "paymentMethod" to transaction.paymentMethod,
            "amountReceived" to transaction.amountReceived,
            "changeAmount" to transaction.changeAmount,
            "customerName" to transaction.customerName,
            "note" to transaction.note,
            "totalItemsCount" to transaction.totalItemsCount,
            "status" to transaction.status,
            "items" to itemsData
        )
        db.collection("transactions")
            .document(transaction.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync transaction: ${it.localizedMessage}") }
    }

    fun deleteTransaction(transactionId: Long) {
        val db = getFirestore() ?: return
        db.collection("transactions")
            .document(transactionId.toString())
            .delete()
    }

    fun syncStoreConfig(config: StoreConfig) {
        val db = getFirestore() ?: return
        val data = hashMapOf(
            "id" to config.id,
            "storeName" to config.storeName,
            "storeAddress" to config.storeAddress,
            "storePhone" to config.storePhone,
            "receiptFooter" to config.receiptFooter,
            "qrisMerchantName" to config.qrisMerchantName,
            "qrisPayload" to config.qrisPayload,
            "qrisImagePath" to config.qrisImagePath,
            "isBluetoothAutoPrint" to config.isBluetoothAutoPrint,
            "printerMacAddress" to config.printerMacAddress,
            "printerName" to config.printerName,
            "isAiAssistantEnabled" to config.isAiAssistantEnabled,
            "themeMode" to config.themeMode,
            "noiseCancellationLevel" to config.noiseCancellationLevel,
            "voiceSensitivity" to config.voiceSensitivity,
            "isSmartCurrencyFilterEnabled" to config.isSmartCurrencyFilterEnabled,
            "googleServicesJson" to config.googleServicesJson,
            "qrisBase64" to config.qrisBase64
        )
        db.collection("store_config")
            .document(config.id.toString())
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to sync store config: ${it.localizedMessage}") }
    }

    suspend fun syncAllDataWithCloud(context: Context, repository: com.example.data.repository.KasirRepository): Boolean =
        syncAllDataWithCloudDetailed(context, repository).success

    suspend fun syncAllDataWithCloudDetailed(
        context: Context,
        repository: com.example.data.repository.KasirRepository
    ): SyncResult {
        val db = getFirestore()
            ?: return SyncResult(
                success = false,
                message = "Firebase belum terhubung. Tekan 'Simpan & Hubungkan Firebase' terlebih dahulu."
            )
        return try {
            val categoriesSnapshot = db.collection("categories").get().await()
            val cloudCategories = parseCategories(categoriesSnapshot.documents)

            val productsSnapshot = db.collection("products").get().await()
            val cloudProducts = parseProducts(productsSnapshot.documents)

            val transactionsSnapshot = db.collection("transactions").get().await()
            val cloudTransactions = parseTransactions(transactionsSnapshot.documents)

            // Sync Store Configuration (never overwrite cloud store_config with local defaults before pulling)
            val pullCfgResult = pullStoreConfigFromCloudIfPresent(context, repository)
            if (pullCfgResult.errorMessage != null) {
                return SyncResult(success = false, message = pullCfgResult.errorMessage)
            }
            if (!pullCfgResult.found) {
                repository.getStoreConfigSync()?.let { localCfg ->
                    var configToSync = localCfg
                    if (localCfg.qrisImagePath.isNotBlank()) {
                        try {
                            val qrisFile = java.io.File(localCfg.qrisImagePath)
                            if (qrisFile.exists()) {
                                val bytes = qrisFile.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                configToSync = localCfg.copy(qrisBase64 = base64)
                                repository.updateStoreConfigLocalOnly(configToSync)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    syncStoreConfig(configToSync)
                }
            }

            if (cloudCategories.isNotEmpty() || cloudProducts.isNotEmpty() || cloudTransactions.isNotEmpty()) {
                repository.replaceCategoriesFromCloud(cloudCategories)
                repository.replaceProductsFromCloud(cloudProducts)
                repository.replaceTransactionsFromCloud(cloudTransactions)
                startRealtimeSync(context, repository)
                SyncResult(
                    success = true,
                    message = "Sinkronisasi berhasil! Data diambil dari Cloud ($activeProjectId)."
                )
            } else {
                val localCats = repository.allCategories.firstOrNull() ?: emptyList()
                val localProds = repository.allProducts.firstOrNull() ?: emptyList()
                val localTxs = repository.allTransactions.firstOrNull() ?: emptyList()
                
                for (cat in localCats) {
                    syncCategory(cat)
                }
                for (prod in localProds) {
                    syncProduct(prod)
                }
                for (txWithItems in localTxs) {
                    syncTransaction(txWithItems.transaction, txWithItems.items)
                }
                startRealtimeSync(context, repository)
                SyncResult(
                    success = true,
                    message = if (isNetworkAvailable(context)) {
                        "Terhubung & tersinkron ke Firestore ($activeProjectId)!"
                    } else {
                        "Offline: perubahan masuk antrean lokal & otomatis terupload saat online!"
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.localizedMessage}")
            val errMsg = formatFirestoreError(context, e)
            updateSyncStateSnapshot(errMsg)
            SyncResult(success = false, message = errMsg)
        }
    }

    private fun parseCategories(documents: List<DocumentSnapshot>): List<Category> =
        documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val name = doc.getString("name") ?: ""
            val iconName = doc.getString("iconName") ?: ""
            val colorHex = doc.getString("colorHex") ?: ""
            Category(id = id, name = name, iconName = iconName, colorHex = colorHex)
        }

    private fun parseProducts(documents: List<DocumentSnapshot>): List<Product> =
        documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val name = doc.getString("name") ?: ""
            val categoryId = doc.getLong("categoryId") ?: 0L
            val categoryName = doc.getString("categoryName") ?: ""
            val price = doc.getDouble("price") ?: 0.0
            val costPrice = doc.getDouble("costPrice") ?: 0.0
            val stock = doc.getLong("stock")?.toInt() ?: 0
            val minStockAlert = doc.getLong("minStockAlert")?.toInt() ?: 5
            val unit = doc.getString("unit") ?: "Porsi"
            val barcode = doc.getString("barcode") ?: ""
            val isAvailable = doc.getBoolean("isAvailable") ?: true
            val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

            Product(
                id = id,
                name = name,
                categoryId = categoryId,
                categoryName = categoryName,
                price = price,
                costPrice = costPrice,
                stock = stock,
                minStockAlert = minStockAlert,
                unit = unit,
                barcode = barcode,
                isAvailable = isAvailable,
                updatedAt = updatedAt
            )
        }

    private fun parseTransactions(documents: List<DocumentSnapshot>): List<Pair<Transaction, List<TransactionItem>>> =
        documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val invoiceNumber = doc.getString("invoiceNumber") ?: ""
            val timestamp = doc.getLong("timestamp") ?: 0L
            val totalAmount = doc.getDouble("totalAmount") ?: 0.0
            val paymentMethod = doc.getString("paymentMethod") ?: "TUNAI"
            val amountReceived = doc.getDouble("amountReceived") ?: 0.0
            val changeAmount = doc.getDouble("changeAmount") ?: 0.0
            val customerName = doc.getString("customerName") ?: ""
            val note = doc.getString("note") ?: ""
            val totalItemsCount = doc.getLong("totalItemsCount")?.toInt() ?: 0
            val status = doc.getString("status") ?: "COMPLETED"

            val transaction = Transaction(
                id = id,
                invoiceNumber = invoiceNumber,
                timestamp = timestamp,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                amountReceived = amountReceived,
                changeAmount = changeAmount,
                customerName = customerName,
                note = note,
                totalItemsCount = totalItemsCount,
                status = status
            )

            val itemsList = mutableListOf<TransactionItem>()
            @Suppress("UNCHECKED_CAST")
            val itemsArray = doc.get("items") as? List<Map<String, Any>>
            if (itemsArray != null) {
                for (itemMap in itemsArray) {
                    val prodId = (itemMap["productId"] as? Number)?.toLong() ?: 0L
                    val prodName = itemMap["productName"] as? String ?: ""
                    val catName = itemMap["categoryName"] as? String ?: ""
                    val qty = (itemMap["quantity"] as? Number)?.toInt() ?: 0
                    val pricePer = (itemMap["pricePerUnit"] as? Number)?.toDouble() ?: 0.0
                    val sub = (itemMap["subtotal"] as? Number)?.toDouble() ?: 0.0

                    itemsList.add(
                        TransactionItem(
                            transactionId = id,
                            productId = prodId,
                            productName = prodName,
                            categoryName = catName,
                            quantity = qty,
                            pricePerUnit = pricePer,
                            subtotal = sub
                        )
                    )
                }
            }
            Pair(transaction, itemsList)
        }

    data class PullStoreConfigResult(
        val found: Boolean,
        val storeName: String? = null,
        val errorMessage: String? = null
    )

    suspend fun pullStoreConfigFromCloudIfPresent(
        context: Context,
        repository: com.example.data.repository.KasirRepository
    ): PullStoreConfigResult {
        val db = getFirestore() ?: return PullStoreConfigResult(found = false)
        return try {
            val primarySnap = db.collection("store_config").document("1").get().await()
            val configSnapshot = if (primarySnap.exists()) {
                primarySnap
            } else {
                db.collection("store_config").limit(1).get().await().documents.firstOrNull()
            }

            if (configSnapshot == null || !configSnapshot.exists()) {
                return PullStoreConfigResult(found = false)
            }

            val localCfg = repository.getStoreConfigSync()
            val mergedConfig = buildMergedStoreConfig(
                context = context,
                cloudData = configSnapshot.data.orEmpty(),
                localCfg = localCfg
            )
            repository.updateStoreConfigLocalOnly(mergedConfig)
            if (!mergedConfig.storeName.isBlank()) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val deviceId = LicenseManager.getOrCreateDeviceId(context)
                        val effectiveBase = LicenseManager.getEffectiveGatewayBaseUrl(context)
                        LicenseManager.sendBusinessNameUpdateToServer(
                            deviceId = deviceId,
                            storeName = mergedConfig.storeName,
                            baseUrlOverride = effectiveBase
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            PullStoreConfigResult(found = true, storeName = mergedConfig.storeName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed pulling store_config from cloud: ${e.localizedMessage}")
            PullStoreConfigResult(found = false, errorMessage = formatFirestoreError(context, e))
        }
    }

    internal fun buildMergedStoreConfig(
        context: Context,
        cloudData: Map<String, Any?>,
        localCfg: StoreConfig?
    ): StoreConfig {
        fun str(key: String, fallback: String): String {
            val cloudVal = (cloudData[key] as? String)?.trim().orEmpty()
            return cloudVal.ifBlank { fallback }
        }
        fun boolVal(key: String, fallback: Boolean): Boolean =
            (cloudData[key] as? Boolean) ?: fallback
        fun floatVal(key: String, fallback: Float): Float =
            (cloudData[key] as? Number)?.toFloat() ?: fallback

        val storeName = str("storeName", localCfg?.storeName?.takeIf { it.isNotBlank() } ?: "Warung Makan")
        val storeAddress = str("storeAddress", localCfg?.storeAddress?.takeIf { it.isNotBlank() } ?: "Jl. Raya Barokah No. 88")
        val storePhone = str("storePhone", localCfg?.storePhone?.takeIf { it.isNotBlank() } ?: "0812-3456-7890")
        val receiptFooter = str("receiptFooter", localCfg?.receiptFooter.orEmpty())
        val qrisMerchantName = str("qrisMerchantName", localCfg?.qrisMerchantName?.takeIf { it.isNotBlank() } ?: storeName)
        val qrisPayload = str("qrisPayload", localCfg?.qrisPayload.orEmpty())
        val isBluetoothAutoPrint = boolVal("isBluetoothAutoPrint", localCfg?.isBluetoothAutoPrint ?: false)
        val printerMacAddress = str("printerMacAddress", localCfg?.printerMacAddress.orEmpty())
        val printerName = str("printerName", localCfg?.printerName.orEmpty())
        val isAiAssistantEnabled = boolVal("isAiAssistantEnabled", localCfg?.isAiAssistantEnabled ?: true)
        val themeMode = str("themeMode", localCfg?.themeMode?.takeIf { it.isNotBlank() } ?: "DARK")
        val noiseCancellationLevel = floatVal("noiseCancellationLevel", localCfg?.noiseCancellationLevel ?: 0.8f)
        val voiceSensitivity = floatVal("voiceSensitivity", localCfg?.voiceSensitivity ?: 0.7f)
        val isSmartCurrencyFilterEnabled = boolVal("isSmartCurrencyFilterEnabled", localCfg?.isSmartCurrencyFilterEnabled ?: true)
        val googleServicesJson = str("googleServicesJson", localCfg?.googleServicesJson.orEmpty())
        val qrisBase64 = str("qrisBase64", localCfg?.qrisBase64.orEmpty())

        var localQrisPath = localCfg?.qrisImagePath.orEmpty()
        if (qrisBase64.isNotBlank()) {
            try {
                val qrisFile = java.io.File(context.filesDir, "qris_merchant_code.png")
                val bytes = android.util.Base64.decode(qrisBase64, android.util.Base64.NO_WRAP)
                qrisFile.writeBytes(bytes)
                localQrisPath = qrisFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return StoreConfig(
            id = 1,
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            receiptFooter = receiptFooter,
            qrisMerchantName = qrisMerchantName,
            qrisPayload = qrisPayload,
            qrisImagePath = localQrisPath,
            isBluetoothAutoPrint = isBluetoothAutoPrint,
            printerMacAddress = printerMacAddress,
            printerName = printerName,
            isAiAssistantEnabled = isAiAssistantEnabled,
            themeMode = themeMode,
            noiseCancellationLevel = noiseCancellationLevel,
            voiceSensitivity = voiceSensitivity,
            isSmartCurrencyFilterEnabled = isSmartCurrencyFilterEnabled,
            googleServicesJson = googleServicesJson,
            qrisBase64 = qrisBase64
        )
    }

    private fun formatFirestoreError(context: Context, e: Exception): String {
        return when (e) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        val pkgHint = if (activeClientPackageName.isNotBlank() && activeClientPackageName != context.packageName) {
                            " Package di JSON ($activeClientPackageName) berbeda dengan aplikasi ini (${context.packageName}) — cek pembatasan API Key & Firestore Rules di project $activeProjectId."
                        } else {
                            " Periksa Firestore Rules (izinkan baca/tulis pada project $activeProjectId)."
                        }
                        "Akses Firestore ditolak (PERMISSION_DENIED).$pkgHint"
                    }
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
                        "Database Cloud Firestore belum dibuat di project $activeProjectId. Buka Firebase Console → Firestore Database → Create database."
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Tidak dapat terhubung ke server Firestore ($activeProjectId). Periksa koneksi internet."
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        "Autentikasi gagal pada project $activeProjectId. Periksa API Key atau Firestore Rules."
                    else ->
                        "Firestore error (${e.code.name}): ${e.localizedMessage ?: "Gagal sinkronisasi"}"
                }
            }
            else -> "Gagal sinkronisasi: ${e.localizedMessage ?: e::class.java.simpleName}"
        }
    }

    data class LicenseRequestRecord(
        val deviceId: String,
        val storeName: String,
        val requestedAt: Long
    )

    fun logLicenseRequest(deviceId: String, storeName: String) {
        val db = getFirestore() ?: return
        val normalizedId = LicenseManager.extractDeviceIdFromText(deviceId) ?: deviceId.trim().uppercase()
        if (normalizedId.isBlank()) return
        val data = hashMapOf(
            "deviceId" to normalizedId,
            "storeName" to storeName.trim(),
            "requestedAt" to System.currentTimeMillis(),
            "status" to "PENDING"
        )
        db.collection("license_requests")
            .document(normalizedId)
            .set(data)
            .addOnFailureListener { Log.e(TAG, "Failed to log license request: ${it.localizedMessage}") }
    }

    suspend fun fetchRecentLicenseRequests(limitCount: Long = 20): List<LicenseRequestRecord> {
        val db = getFirestore() ?: return emptyList()
        return try {
            val snap = db.collection("license_requests")
                .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val id = doc.getString("deviceId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LicenseRequestRecord(
                    deviceId = id,
                    storeName = doc.getString("storeName") ?: "",
                    requestedAt = doc.getLong("requestedAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch license requests: ${e.localizedMessage}")
            emptyList()
        }
    }
}
